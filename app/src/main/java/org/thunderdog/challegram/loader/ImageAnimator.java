/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/05/2015 at 00:42
 */
package org.thunderdog.challegram.loader;

import android.os.Build;
import android.os.Message;

import org.thunderdog.challegram.N;

public class ImageAnimator  {
  private static final long DURATION = 120l;
  private static final long SLOW_DURATION = 100l;
  private static ImageAnimatorHandler handler;

  private static final long FRAME_DELAY = 12l;
  private static final long SLOW_FRAME_DELAY = Build.VERSION.SDK_INT > 20 ? 14l : 17l;

  private ImageReceiver receiver;

  private int tick;
  private long animationStart;
  private boolean isSlow;

  public ImageAnimator (ImageReceiver receiver) {
    if (handler == null) {
      handler = new ImageAnimatorHandler();
    }
    this.receiver = receiver;
  }

  public ImageAnimator (ImageReceiver receiver, boolean isSlow) {
    if (handler == null) {
      handler = new ImageAnimatorHandler();
    }
    this.receiver = receiver;
    this.isSlow = isSlow;
  }

  public void cancel () {
    tick++;
  }

  public void start () {
    tick++;
    animationStart = System.currentTimeMillis();
    handler.sendMessage(Message.obtain(handler, tick, this));
  }

  public void onFrame (int tick) {
    if (this.tick == tick) {
      float input;

      if (isSlow) {
        input = (float) (System.currentTimeMillis() - animationStart) / (float) SLOW_DURATION;
      } else {
        input = (float) (System.currentTimeMillis() - animationStart) / (float) DURATION;
      }

      if (input <= 0f) {
        receiver.setAlpha(0f);
        if (isSlow) {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), SLOW_FRAME_DELAY);
        } else {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), FRAME_DELAY);
        }
      } else if (input >= 1f) {
        receiver.setAlpha(1f);
      } else {
        receiver.setAlpha(N.iimg(input));
        if (isSlow) {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), SLOW_FRAME_DELAY);
        } else {
          handler.sendMessageDelayed(Message.obtain(handler, tick, this), FRAME_DELAY);
        }
      }
    }
  }

}
