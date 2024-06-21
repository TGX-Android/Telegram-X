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
 * File created on 20/06/2024
 */
package org.thunderdog.challegram.util;

import android.os.Handler;

import me.vkryl.core.lambda.CancellableRunnable;

public class ThrottlingRunnable implements Runnable {
  private final Handler handler;
  private final Runnable runnable;
  private final long time;

  public ThrottlingRunnable (Handler handler, Runnable runnable, long time) {
    this.handler = handler;
    this.runnable = runnable;
    this.time = time;
  }

  public void runImmediately () {
    cancel();
    doRun();
  }

  public void runScheduled () {
    final long delay = System.currentTimeMillis() - lastRun;
    if (delay > time) {
      runImmediately();
    } else if (scheduled == null) {
      scheduled = new CancellableRunnable() {
        @Override
        public void act () {
          doRun();
        }
      };
      handler.postDelayed(scheduled, time - delay);
    }
  }

  public void cancel () {
    if (scheduled != null) {
      scheduled.cancel();
      scheduled = null;
    }
  }

  private CancellableRunnable scheduled;
  private long lastRun;

  private void doRun () {
    lastRun = System.currentTimeMillis();
    scheduled = null;
    runnable.run();
  }

  public void run (boolean immediately) {
    if (immediately) {
      runImmediately();
    } else {
      runScheduled();
    }
  }

  @Override
  public final void run () {
    runScheduled();
  }
}