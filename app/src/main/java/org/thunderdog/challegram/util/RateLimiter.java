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
 * File created on 26/10/2023
 */
package org.thunderdog.challegram.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;

public final class RateLimiter implements Runnable {
  private final Runnable act;
  private final long delayMs;
  private final Handler handler;

  private long lastExecutionTime;
  private boolean isScheduled;

  public RateLimiter (Runnable act, long delayMs, @Nullable Looper looper) {
    this.act = act;
    this.delayMs = delayMs;
    this.handler = new Handler(looper != null ? looper : Looper.getMainLooper(), (msg) -> {
      runImpl();
      return true;
    });
  }

  @Override
  public void run () {
    long now = SystemClock.uptimeMillis();
    if (lastExecutionTime == 0 || (now - lastExecutionTime) >= delayMs) {
      runImpl();
    } else if (!isScheduled) {
      long nextExecutionTimeMs = lastExecutionTime + delayMs;
      long delayMs = nextExecutionTimeMs - now;
      handler.sendMessageDelayed(handler.obtainMessage(), delayMs);
    }
  }

  private void runImpl () {
    act.run();
    lastExecutionTime = SystemClock.uptimeMillis();
    isScheduled = false;
  }
}
