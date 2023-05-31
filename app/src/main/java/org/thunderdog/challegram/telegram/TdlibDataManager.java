/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/10/2022, 01:17.
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.util.BatchOperationHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceMap;

abstract class TdlibDataManager<Key, Value extends TdApi.Object, Result extends TdlibDataManager.AbstractEntry<Key, Value>> implements CleanupStartupDelegate {
  protected static abstract class AbstractEntry<K, V extends TdApi.Object> {
    public final K key;
    public final @Nullable V value;
    public final @Nullable TdApi.Error error;

    public AbstractEntry (@NonNull K key, @Nullable V value, @Nullable TdApi.Error error) {
      this.key = key;
      this.value = value;
      this.error = error;
      if (error == null && value == null)
        throw new IllegalStateException();
    }
  }

  protected interface Watcher<Key, Value extends TdApi.Object, Result extends TdlibDataManager.AbstractEntry<Key, Value>> {
    void onEntryLoaded (TdlibDataManager<Key, Value, Result> context, Result entry);
  }

  protected abstract Result newEntry (@NonNull Key key, @Nullable Value value, @Nullable TdApi.Error error);

  protected final Tdlib tdlib;

  private final Object dataLock = new Object();
  private final Map<Key, Result> entries = new HashMap<>();
  private final Set<Key> postponedKeys = new HashSet<>();
  private final Set<Key> loadingKeys = new HashSet<>();
  private final ReferenceMap<Key, Watcher<Key, Value, Result>> watcherReferences = new ReferenceMap<>(true);
  private final Map<Key, List<Watcher<Key, Value, Result>>> watchers = new LinkedHashMap<>();

  private int contextId;

  protected TdlibDataManager (Tdlib tdlib) {
    this.tdlib = tdlib;
    tdlib.listeners().addCleanupListener(this);
  }

  // Listeners

  @Override
  public final void onPerformRestart () {
    synchronized (dataLock) {
      contextId++;
      entries.clear();
      // TODO cancel all pending requests
    }
  }

  // Impl

  @UiThread
  public final Result find (Key key) {
    synchronized (dataLock) {
      return entries.get(key);
    }
  }

  @UiThread
  public final Result findOrRequest (Key key, @Nullable RunnableData<Result> callback) {
    Watcher<Key, Value, Result> watcher;
    if (callback != null) {
      watcher = (context, entry) ->
        callback.runWithData(entry);
    } else {
      watcher = null;
    }
    Result entry = findOrPostponeRequest(key, watcher, true);
    if (entry != null) {
      if (callback != null) {
        callback.runWithData(entry);
      }
    } else {
      performPostponedRequest(key);
    }
    return entry;
  }

  @Nullable
  public final Result findOrPostponeRequest (Key key, Watcher<Key, Value, Result> watcher) {
    return findOrPostponeRequest(key, watcher, false);
  }

  @Nullable
  public final Result findOrPostponeRequest (Key key, Watcher<Key, Value, Result> watcher, boolean strongReference) {
    synchronized (dataLock) {
      Result entry = entries.get(key);
      if (entry != null) {
        return entry;
      }
      if (!loadingKeys.contains(key)) {
        postponedKeys.add(key);
      }
      if (watcher != null) {
        if (strongReference) {
          addWatcherImpl(key, watcher);
        } else {
          watcherReferences.add(key, watcher);
        }
      }
      return null;
    }
  }

  private void addWatcherImpl (Key key, Watcher<Key, Value, Result> watcher) {
    List<Watcher<Key, Value, Result>> list = watchers.get(key);
    if (list != null && !list.contains(watcher)) {
      list.add(watcher);
    } else {
      list = new ArrayList<>();
      list.add(watcher);
      watchers.put(key, list);
    }
  }

  public final void forgetWatcher (Key key, Watcher<Key, Value, Result> watcher) {
    watcherReferences.remove(key, watcher);
  }

  private final BatchOperationHandler delayedHandler = new BatchOperationHandler(this::performPostponedRequests, 10);

  @UiThread
  public void performPostponedRequest (Key key) {
    int contextId;
    synchronized (dataLock) {
      if (postponedKeys.isEmpty() || !postponedKeys.remove(key)) {
        return;
      }
      loadingKeys.add(key);
      contextId = this.contextId;
    }
    requestData(contextId, Set.of(key));
  }

  @UiThread
  public void performPostponedRequestsDelayed () {
    delayedHandler.performOperation();
  }

  @UiThread
  public void performPostponedRequests () {
    final Set<Key> keysToRequest;
    int contextId;
    synchronized (dataLock) {
      if (postponedKeys.isEmpty()) {
        return;
      }
      loadingKeys.addAll(postponedKeys);
      keysToRequest = new HashSet<>(postponedKeys);
      postponedKeys.clear();
      contextId = this.contextId;
    }
    requestData(contextId, keysToRequest);
  }

  protected final boolean isCancelled (int contextId) {
    synchronized (dataLock) {
      return this.contextId != contextId;
    }
  }

  @UiThread
  protected abstract void requestData (int contextId, Collection<Key> keysToRequest);

  @TdlibThread
  protected final void processData (int contextId, @NonNull Key key, @Nullable Value value) {
    if (value != null) {
      processEntry(contextId, newEntry(key, value, null));
    } else {
      processError(contextId, key, new TdApi.Error(404, "Not Found"));
    }
  }

  @TdlibThread
  protected final void processError (int contextId, @NonNull Key key, @NonNull TdApi.Error error) {
    processEntry(contextId, newEntry(key, null, error));
  }

  @TdlibThread
  private void processEntry (int contextId, Result entry) {
    List<Watcher<Key, Value, Result>> watcherList;
    synchronized (dataLock) {
      if (this.contextId != contextId)
        return;
      entries.put(entry.key, entry);
      watcherList = watchers.remove(entry.key);
    }
    ReferenceList<Watcher<Key, Value, Result>> referenceList = watcherReferences.removeAll(entry.key);
    if (referenceList != null) {
      for (Watcher<Key, Value, Result> watcher : referenceList) {
        watcher.onEntryLoaded(this, entry);
      }
      referenceList.clear();
    }
    if (watcherList != null) {
      for (Watcher<Key, Value, Result> watcher : watcherList) {
        watcher.onEntryLoaded(this, entry);
      }
    }
    synchronized (dataLock) {
      if (this.contextId != contextId)
        return;
      loadingKeys.remove(entry.key);
    }
  }

}
