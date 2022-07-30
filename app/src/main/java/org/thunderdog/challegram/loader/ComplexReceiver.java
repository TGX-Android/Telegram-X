/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/02/2017
 */
package org.thunderdog.challegram.loader;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import org.thunderdog.challegram.loader.gif.GifReceiver;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;

public class ComplexReceiver implements Destroyable {
  public interface KeyFilter {
    boolean filterKey (int receiverType, Receiver receiver, long key);
  }

  private final View view;
  private final LongSparseArray<ImageReceiver> imageReceivers;
  private final LongSparseArray<GifReceiver> gifReceivers;
  private final LongSparseArray<DoubleImageReceiver> previews;

  public ComplexReceiver(View view) {
    this.view = view;
    this.imageReceivers = new LongSparseArray<>(10);
    this.gifReceivers = new LongSparseArray<>(10);
    this.previews = new LongSparseArray<>(10);
  }

  private static <T extends Receiver> void clearReceiversWithHigherKey (LongSparseArray<T> receivers, long key) {
    final int size = receivers.size();
    for (int i = 0; i < size; i++) {
      long sparseKey = receivers.keyAt(i);
      if (sparseKey >= key) {
        receivers.valueAt(i).clear();
      }
    }
  }

  private static <T extends Receiver> void clearReceiver (LongSparseArray<T> target, long key) {
    Receiver receiver = target.get(key);
    if (receiver != null) {
      receiver.clear();
    }
  }

  private static <T extends Receiver> void clearReceivers (LongSparseArray<T> target, int receiverType, @Nullable KeyFilter filter) {
    int size = target.size();
    for (int i = 0; i < size; i++) {
      Receiver receiver = target.valueAt(i);
      if (filter == null || !filter.filterKey(receiverType, receiver, target.keyAt(i))) {
        receiver.clear();
      }
    }
  }

  public static final int RECEIVER_TYPE_PREVIEW = 0;
  public static final int RECEIVER_TYPE_IMAGE = 1;
  public static final int RECEIVER_TYPE_GIF = 2;

  public void clearReceivers (@Nullable KeyFilter filter) {
    clearReceivers(imageReceivers, RECEIVER_TYPE_IMAGE, filter);
    clearReceivers(gifReceivers, RECEIVER_TYPE_GIF, filter);
    clearReceivers(previews, RECEIVER_TYPE_PREVIEW, filter);
  }

  public void clearReceivers (int key) {
    clearReceiver(imageReceivers, key);
    clearReceiver(gifReceivers, key);
    clearReceiver(previews, key);
  }

  public void clearReceiversWithHigherKey (int key) {
    clearReceiversWithHigherKey(imageReceivers, key);
    clearReceiversWithHigherKey(gifReceivers, key);
    clearReceiversWithHigherKey(previews, key);
  }

  public DoubleImageReceiver getPreviewReceiver (int key) {
    return getReceiver(previews, view, isAttached, animationsDisabled, key, TYPE_DOUBLE);
  }

  private static final int TYPE_DOUBLE = 1;
  private static final int TYPE_IMAGE = 2;
  private static final int TYPE_GIF = 3;

  private static <T extends Receiver> T getReceiver (LongSparseArray<T> target, View view, boolean isAttached, boolean animationsDisabled, long key, int type) {
    int i = target.indexOfKey(key);
    if (i >= 0) {
      return target.valueAt(i);
    }
    T receiver;
    switch (type) {
      case TYPE_DOUBLE:
        receiver = (T) new DoubleImageReceiver(view, 0);
        break;
      case TYPE_GIF:
        receiver = (T) new GifReceiver(view);
        break;
      case TYPE_IMAGE:
        receiver = (T) new ImageReceiver(view, 0);
        break;
      default:
        throw new IllegalArgumentException("type == " + type);
    }
    if (!isAttached) {
      receiver.detach();
    }
    if (animationsDisabled) {
      receiver.setAnimationDisabled(animationsDisabled);
    }
    target.append(key, receiver);
    return receiver;
  }

  public Receiver getReceiver (long key, boolean isGif) {
    if (isGif)
      return getGifReceiver(key);
    else
      return getImageReceiver(key);
  }

  public ImageReceiver getImageReceiver (long key) {
    return getReceiver(imageReceivers, view, isAttached, animationsDisabled, key, TYPE_IMAGE);
  }

  public GifReceiver getGifReceiver (long key) {
    return getReceiver(gifReceivers, view, isAttached, animationsDisabled, key, TYPE_GIF);
  }

  private boolean isAttached = true;

  private boolean animationsDisabled;

  public void setAnimationDisabled (boolean animationsDisabled) {
    if (this.animationsDisabled != animationsDisabled) {
      this.animationsDisabled = animationsDisabled;
      iterate(receiver ->
        receiver.setAnimationDisabled(animationsDisabled)
      );
    }
  }

  private void iterate (RunnableData<Receiver> callback) {
    iterate(imageReceivers, callback);
    iterate(gifReceivers, callback);
    iterate(previews, callback);
  }

  private static <T extends Receiver> void iterate (LongSparseArray<T> receivers, RunnableData<Receiver> callback) {
    final int count = receivers.size();
    for (int i = 0; i < count; i++) {
      callback.runWithData(receivers.valueAt(i));
    }
  }

  private static <T extends Receiver> void attachDetach (LongSparseArray<T> receivers, boolean attach) {
    final int count = receivers.size();
    if (attach) {
      for (int i = 0; i < count; i++) {
        Receiver receiver = receivers.valueAt(i);
        if (receiver != null) {
          receiver.attach();
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        Receiver receiver = receivers.valueAt(i);
        if (receiver != null) {
          receiver.detach();
        }
      }
    }
  }

  public void attach () {
    isAttached = true;
    attachDetach(imageReceivers, true);
    attachDetach(gifReceivers, true);
    attachDetach(previews, true);
  }

  public void detach () {
    isAttached = false;
    attachDetach(imageReceivers, false);
    attachDetach(gifReceivers, false);
    attachDetach(previews, false);
  }

  public void clear () {
    clearReceivers(null);
  }

  @Override
  public void performDestroy () {
    clear();
  }
}
