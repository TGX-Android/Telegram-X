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
import org.thunderdog.challegram.receiver.RefreshRateLimiter;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;

public class ComplexReceiver implements Destroyable {
  public interface KeyFilter {
    boolean filterKey (int receiverType, Receiver receiver, long key);
  }

  private final View view;
  private ComplexReceiverUpdateListener updateListener;
  private final LongSparseArray<ImageReceiver> imageReceivers;
  private final LongSparseArray<AvatarReceiver> avatarReceivers;
  private final LongSparseArray<GifReceiver> gifReceivers;
  private final LongSparseArray<DoubleImageReceiver> previews;

  public ComplexReceiver (View view) {
    this.view = view;
    this.imageReceivers = new LongSparseArray<>(10);
    this.avatarReceivers = new LongSparseArray<>(10);
    this.gifReceivers = new LongSparseArray<>(10);
    this.previews = new LongSparseArray<>(10);
  }

  public ComplexReceiver () {
    this(null);
  }

  public ComplexReceiver (View view, float maxRefreshRate) {
    this();
    setUpdateListener(new RefreshRateLimiter(view, maxRefreshRate));
  }

  public ComplexReceiver setUpdateListener (ComplexReceiverUpdateListener listener) {
    this.updateListener = listener;
    setUpdateListener(imageReceivers, listener);
    setUpdateListener(avatarReceivers, listener);
    setUpdateListener(gifReceivers, listener);
    setUpdateListener(previews, listener);
    return this;
  }

  private static <T extends Receiver> void setUpdateListener (LongSparseArray<T> receivers, ComplexReceiverUpdateListener listener) {
    if (listener != null) {
      for (int i = 0; i < receivers.size(); i++) {
        long key = receivers.keyAt(i);
        receivers.valueAt(i).setUpdateListener(receiver ->
          listener.onRequestInvalidate(receiver, key)
        );
      }
    } else {
      for (int i = 0; i < receivers.size(); i++) {
        receivers.valueAt(i).setUpdateListener(null);
      }
    }
  }

  private static <T extends Receiver> void clearReceiversRange (LongSparseArray<T> receivers, long startKey, long endKey) {
    final int size = receivers.size();
    for (int i = 0; i < size; i++) {
      long sparseKey = receivers.keyAt(i);
      if (sparseKey < startKey)
        continue;
      if (sparseKey >= endKey)
        break;
      receivers.valueAt(i).clear();
    }
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
  public static final int RECEIVER_TYPE_AVATAR = 3;

  public void clearReceivers (@Nullable KeyFilter filter) {
    clearReceivers(imageReceivers, RECEIVER_TYPE_IMAGE, filter);
    clearReceivers(gifReceivers, RECEIVER_TYPE_GIF, filter);
    clearReceivers(previews, RECEIVER_TYPE_PREVIEW, filter);
    clearReceivers(avatarReceivers, RECEIVER_TYPE_AVATAR, filter);
  }

  public void clearReceivers (long key) {
    clearReceiver(imageReceivers, key);
    clearReceiver(avatarReceivers, key);
    clearReceiver(gifReceivers, key);
    clearReceiver(previews, key);
  }

  public void clearReceiversWithHigherKey (long key) {
    clearReceiversWithHigherKey(imageReceivers, key);
    clearReceiversWithHigherKey(avatarReceivers, key);
    clearReceiversWithHigherKey(gifReceivers, key);
    clearReceiversWithHigherKey(previews, key);
  }

  public void clearReceiversRange (long startKey, long endKey) {
    clearReceiversRange(imageReceivers, startKey, endKey);
    clearReceiversRange(avatarReceivers, startKey, endKey);
    clearReceiversRange(gifReceivers, startKey, endKey);
    clearReceiversRange(previews, startKey, endKey);
  }

  public DoubleImageReceiver getPreviewReceiver (long key) {
    return getReceiver(previews, view, updateListener, isAttached, animationsDisabled, key, TYPE_DOUBLE);
  }

  private static final int TYPE_DOUBLE = 1;
  private static final int TYPE_IMAGE = 2;
  private static final int TYPE_GIF = 3;
  private static final int TYPE_AVATAR = 4;

  private static <T extends Receiver> T getReceiver (LongSparseArray<T> target, View view, @Nullable ComplexReceiverUpdateListener updateListener, boolean isAttached, boolean animationsDisabled, long key, int type) {
    int i = target.indexOfKey(key);
    if (i >= 0) {
      return target.valueAt(i);
    }
    T receiver;
    switch (type) {
      case TYPE_AVATAR:
        receiver = (T) new AvatarReceiver(view);
        break;
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
    if (updateListener != null) {
      receiver.setUpdateListener((r) ->
        updateListener.onRequestInvalidate(r, key)
      );
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
    return getReceiver(imageReceivers, view, updateListener, isAttached, animationsDisabled, key, TYPE_IMAGE);
  }

  public GifReceiver getGifReceiver (long key) {
    return getReceiver(gifReceivers, view, updateListener, isAttached, animationsDisabled, key, TYPE_GIF);
  }

  public AvatarReceiver getAvatarReceiver (long key) {
    return getReceiver(avatarReceivers, view, updateListener, isAttached, animationsDisabled, key, TYPE_AVATAR);
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
    iterate(avatarReceivers, callback);
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
    attachDetach(avatarReceivers, true);
    attachDetach(gifReceivers, true);
    attachDetach(previews, true);
  }

  public void detach () {
    isAttached = false;
    attachDetach(imageReceivers, false);
    attachDetach(avatarReceivers, false);
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
