package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.data.TD;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.RunnableData;

public class TdlibSingleton<T extends TdApi.Object> implements CleanupStartupDelegate {
  private final Tdlib tdlib;
  private final Future<TdApi.Function<T>> getterCreator;
  private T data;
  private boolean isLoading;
  private final Object dataLock = new Object();
  private final List<RunnableData<T>> pendingCallbacks = new ArrayList<>();
  private int contextId;

  public TdlibSingleton (Tdlib tdlib, Future<TdApi.Function<T>> getterCreator) {
    this.tdlib = tdlib;
    this.getterCreator = getterCreator;
    tdlib.listeners().addCleanupListener(this);
  }

  @Override
  public void onPerformRestart () {
    List<RunnableData<T>> callbacksList;
    synchronized (dataLock) {
      contextId++;
      data = null;
      callbacksList = callbacksListUnsafe();
    }
    if (callbacksList != null) {
      for (RunnableData<T> callback : callbacksList) {
        callback.runWithData(null);
      }
    }
  }

  public void get (RunnableData<T> after) {
    T data;
    boolean needRequest = false;
    int contextId;
    synchronized (dataLock) {
      data = this.data;
      contextId = this.contextId;
      if (data == null) {
        if (after != null) {
          pendingCallbacks.add(after);
        }
        if (!isLoading) {
          isLoading = true;
          needRequest = true;
        }
      }
    }
    if (data != null) {
      if (after != null) {
        after.runWithData(data);
      }
      return;
    }
    if (needRequest) {
      TdApi.Function<T> request = getterCreator.getValue();
      tdlib.client().send(request, result -> {
        if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          Log.e("TdlibSingleton failed for request: %s, error: %s", request, TD.toErrorString(result));
          dispatchResult(contextId, null);
        } else {
          //noinspection unchecked
          dispatchResult(contextId, (T) result);
        }
      });
    }
  }

  private List<RunnableData<T>> callbacksListUnsafe () {
    if (!this.pendingCallbacks.isEmpty()) {
      List<RunnableData<T>> callbacks = new ArrayList<>(this.pendingCallbacks);
      this.pendingCallbacks.clear();
      return callbacks;
    }
    return null;
  }

  private void dispatchResult (int contextId, T result) {
    List<RunnableData<T>> callbacksCopy;
    synchronized (dataLock) {
      if (this.contextId != contextId)
        return;
      this.isLoading = false;
      this.data = result;
      callbacksCopy = callbacksListUnsafe();
    }
    if (callbacksCopy != null) {
      for (RunnableData<T> callback : callbacksCopy) {
        callback.runWithData(result);
      }
    }
  }
}
