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
 * File created on 13/09/2022, 23:53.
 */

package org.thunderdog.challegram.receiver;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import org.thunderdog.challegram.loader.ComplexReceiverUpdateListener;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.ReceiverUpdateListener;
import org.thunderdog.challegram.tool.Screen;

public class FrameLimiter implements ComplexReceiverUpdateListener, ReceiverUpdateListener {
  private final View target;
  private final Handler handler;
  private final float maxFrameRate;

  public FrameLimiter (View target, float maxFrameRate) {
    this.target = target;
    this.maxFrameRate = maxFrameRate != 0f ? maxFrameRate : 60.0f;
    this.handler = new Handler(Looper.getMainLooper(), msg -> {
      onPerformInvalidate();
      return true;
    });
  }

  private boolean isScheduled;

  private void onPerformInvalidate () {
    isScheduled = false;
    target.invalidate();
    lastInvalidateTime = SystemClock.uptimeMillis();
  }

  private long minRefreshDelay () {
    return (long) (1000.0f / Math.min(Screen.refreshRate(), maxFrameRate));
  }

  private long lastInvalidateTime;

  public void invalidate () {
    if (isScheduled) {
      // Do nothing, invalidate() will happen soon.
      return;
    }
    long now = SystemClock.uptimeMillis();
    long minDelay = minRefreshDelay();
    long timeElapsedSinceLastInvalidate = now - lastInvalidateTime;
    if (lastInvalidateTime == 0 || timeElapsedSinceLastInvalidate >= minDelay) {
      // It's been a while since last invalidate, it's OK to perform it immediately
      onPerformInvalidate();
    } else {
      isScheduled = true;
      handler.sendEmptyMessageDelayed(0, minDelay - timeElapsedSinceLastInvalidate);
    }
  }

  @Override
  public void onRequestInvalidate (Receiver receiver, long key) {
    invalidate();
  }

  @Override
  public void onRequestInvalidate (Receiver receiver) {
    invalidate();
  }
}
