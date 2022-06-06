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
 * File created on 01/03/2016 at 13:48
 */
package org.thunderdog.challegram.loader.gif;

import android.graphics.Bitmap;

import org.thunderdog.challegram.U;

import java.util.ArrayDeque;
import java.util.Queue;

import me.vkryl.core.BitwiseUtils;

public class GifState {
  static final int DEFAULT_QUEUE_SIZE = 3;

  public static class Frame {
    public final Bitmap bitmap;
    public long no;

    public Frame (Bitmap bitmap) {
      this.bitmap = bitmap;
    }

    public boolean isRecycled () {
      return bitmap.isRecycled();
    }

    public void recycle () {
      bitmap.recycle();
    }

    public int getWidth () {
      return bitmap.getWidth();
    }

    public int getHeight () {
      return bitmap.getHeight();
    }
  }

  private final ArrayDeque<Frame> busy;
  private final ArrayDeque<Frame> free;

  private static final int FLAG_APPLY_NEXT = 1;
  private static final int FLAG_FROZEN = 1 << 1;

  private int width, height, rotation;
  private Callback callback;
  private int flags;
  private final int queueSize;

  public GifState (int width, int height, int rotation, Callback callback, int queueSize) {
    this.width = width;
    this.height = height;
    this.rotation = rotation;
    this.busy = new ArrayDeque<>(queueSize);
    this.free = new ArrayDeque<>(queueSize);
    this.queueSize = queueSize;
    this.callback = callback;
  }

  public interface Callback {
    void onDrawNextFrame ();
    void onApplyNextFrame (long no);
    boolean onDraw ();
  }

  public interface FrameReader {
    boolean readNextFrame (Frame bitmap);
  }

  public boolean init (FrameReader reader, int numInit, Bitmap.Config config) {
    synchronized (busy) {
      for (int i = 0; i < queueSize; i++) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, config);
        if (bitmap == null) {
          return false;
        }
        Frame frame = new Frame(bitmap);
        if (i < numInit) {
          if (!reader.readNextFrame(frame)) {
            return false;
          }
          busy.offer(frame);
        } else {
          free.offer(frame);
        }
      }
    }
    return true;
  }

  public Frame takeFree () {
    synchronized (busy) {
      return free.isEmpty() ? null : free.poll();
    }
  }

  public void addFree (Frame busy) {
    synchronized (this.busy) {
      free.offer(busy);
    }
  }

  public Queue<Frame> getBusyList () {
    return busy;
  }

  public void addBusy (Frame free) {
    synchronized (busy) {
      busy.offer(free);
    }
  }

  public void clearBusy () {
    synchronized (busy) {
      while (busy.size() > 1) {
        free.offer(busy.removeLast());
      }
    }
  }

  public void recycle () {
    synchronized (busy) {
      recycleImpl();
    }
  }

  private void recycleImpl () {
    for (Frame bitmap : busy) {
      if (bitmap != null && !bitmap.isRecycled()) {
        bitmap.recycle();
      }
    }
    for (Frame bitmap : free) {
      if (bitmap != null && !bitmap.isRecycled()) {
        bitmap.recycle();
      }
    }
    busy.clear();
    free.clear();
  }

  public void setCanApplyNext () {
    flags |= FLAG_APPLY_NEXT;
  }

  public void setFrozen (boolean isFrozen) {
    synchronized (busy) {
      this.flags = BitwiseUtils.setFlag(this.flags, FLAG_FROZEN, isFrozen);
    }
  }

  public boolean isFrozen () {
    return (flags & FLAG_FROZEN) != 0;
  }

  public void applyNext () {
    synchronized (busy) {
      final boolean canApplyNext = (flags & FLAG_APPLY_NEXT) != 0;
      if (canApplyNext) {
        if (busy.size() > 1) {
          if ((flags & FLAG_FROZEN) == 0 || free.isEmpty()) {
            Frame busy = this.busy.poll();
            if (busy != null) {
              callback.onApplyNextFrame(busy.no);
            }
            free.offer(busy);
          }
          callback.onDrawNextFrame();
        }
        flags &= ~FLAG_APPLY_NEXT;
      }
    }
  }

  public boolean hasNext () {
    return busy.size() > 1;
  }

  public int width () {
    return width;
  }

  public int height () {
    return height;
  }

  public boolean isRotated () {
    return rotation == 90 || rotation == 270;
  }

  public int getRotation () {
    return rotation;
  }

  public boolean hasBitmap () {
    synchronized (busy) {
      Frame frame = getFrame();
      return frame != null && U.isValidBitmap(frame.bitmap);
    }
  }

  public Frame getFrame () {
    return busy.peek();
  }

  public Bitmap getBitmap (boolean willDraw) {
    Frame frame = getFrame();
    if (frame != null) {
      if (willDraw) {
        onDraw();
      }
      return frame.bitmap;
    }
    return null;
  }

  public void onDraw () {
    if (callback.onDraw()) {
      synchronized (busy) {
        callback.onDrawNextFrame();
      }
    }
  }
}
