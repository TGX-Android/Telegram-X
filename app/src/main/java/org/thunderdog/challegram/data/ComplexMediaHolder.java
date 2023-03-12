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
 * File created on 06/09/2022, 00:22.
 */

package org.thunderdog.challegram.data;

import android.view.View;

import androidx.collection.LongSparseArray;

import org.thunderdog.challegram.loader.ComplexReceiver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.Future;

public class ComplexMediaHolder<T> implements Destroyable {
  public interface UpdateListener<T> {
    void onRequestInvalidate (List<T> usages, long displayMediaKey);
  }

  private static class Entry<T> implements Future<List<T>> {
    public final String key;
    public final long mediaKey;
    public final List<T> usages = new ArrayList<>();
    public boolean mediaRequested;

    public Entry (String key, long mediaKey) {
      this.key = key;
      this.mediaKey = mediaKey;
    }

    @Override
    public List<T> get () {
      return usages;
    }
  }

  public final View targetView;
  public final ComplexReceiver receiver;

  private final Map<String, Entry<T>> entries = new HashMap<>();
  private final List<T> defaultLayerUsages = new ArrayList<>();
  private final List<T> topLayerUsages = new ArrayList<>();
  private final LongSparseArray<Entry<T>> mediaKeyToEntry = new LongSparseArray<>();
  private long lastMediaKey;

  public ComplexMediaHolder (View targetView) {
    this.targetView = targetView;
    this.receiver = new ComplexReceiver(targetView);
  }

  public void setUpdateListener (UpdateListener<T> listener) {
    if (listener != null) {
      receiver.setUpdateListener((receiver, key) -> {
        Entry<T> entry = mediaKeyToEntry.get(key);
        if (entry != null) {
          listener.onRequestInvalidate(entry.usages, entry.mediaKey);
        }
      });
    } else {
      receiver.setUpdateListener(null);
    }
  }

  public int getMediaUsageCount (ComplexMediaItem mediaItem) {
    Entry<T> entry = entries.get(mediaItem.getComplexMediaKey());
    return entry != null ? entry.usages.size() : 0;
  }

  public long attachMediaUsage (ComplexMediaItem mediaItem, T usage) {
    String key = mediaItem.getComplexMediaKey();
    Entry<T> entry = entries.get(key);
    if (entry == null) {
      entry = new Entry<>(key, ++lastMediaKey);
      entries.put(key, entry);
      mediaKeyToEntry.put(entry.mediaKey, entry);
    }
    if (!entry.usages.contains(usage)) {
      entry.usages.add(usage);
      (mediaItem.requiresTopLayer() ? topLayerUsages : defaultLayerUsages).add(usage);
    }
    if (!entry.mediaRequested) {
      mediaItem.requestComplexMedia(receiver, entry.mediaKey);
      entry.mediaRequested = true;
    }
    return entry.mediaKey;
  }

  public void detachMediaUsage (ComplexMediaItem mediaItem, T usage, long mediaId) {
    String key = mediaItem.getComplexMediaKey();
    Entry<T> entry = entries.get(key);
    if (entry == null) {
      return;
    }
    if (entry.mediaKey != mediaId) {
      throw new IllegalArgumentException();
    }
    if (entry.usages.remove(usage)) {
      (mediaItem.requiresTopLayer() ? topLayerUsages : defaultLayerUsages).remove(usage);
      if (entry.usages.isEmpty()) {
        receiver.clearReceivers(entry.mediaKey);
        entry.mediaRequested = false;
      }
    }
  }

  @Override
  public void performDestroy () {
    receiver.performDestroy();
    for (Map.Entry<String, Entry<T>> entry : entries.entrySet()) {
      entry.getValue().mediaRequested = false;
    }
    topLayerUsages.clear();
    defaultLayerUsages.clear();
  }

  public List<T> topLayerUsages () {
    return topLayerUsages;
  }

  public List<T> defaultLayerUsages () {
    return defaultLayerUsages;
  }
}
