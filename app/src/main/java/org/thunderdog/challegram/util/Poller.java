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
 * File created on 19/01/2024
 */
package org.thunderdog.challegram.util;

import android.os.Handler;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.FutureLong;

public class Poller<T extends TdApi.Object> {

  private static final long MIN_POLLING_DELAY_MILLIS = 1000;

  private final Handler handler;
  private final Tdlib tdlib;
  private final Tdlib.ResultHandler<T> resultHandler;
  private final Future<TdApi.Function<T>> requestFactory;
  private final FutureLong pollingDelayMillis;

  private @Nullable Runnable runnable;

  public Poller (Tdlib tdlib, FutureLong pollingDelayMillis, Future<TdApi.Function<T>> requestFactory, Tdlib.ResultHandler<T> resultHandler) {
    this(tdlib, UI.getAppHandler(), pollingDelayMillis, requestFactory, resultHandler);
  }

  public Poller (Tdlib tdlib, Handler handler, FutureLong pollingDelayMillis, Future<TdApi.Function<T>> requestFactory, Tdlib.ResultHandler<T> resultHandler) {
    this.tdlib = tdlib;
    this.handler = handler;
    this.pollingDelayMillis = pollingDelayMillis;
    this.resultHandler = resultHandler;
    this.requestFactory = requestFactory;
  }

  public void start () {
    checkCallingThread();
    if (runnable == null) {
      runnable = createRunnable();
      handler.post(runnable);
    }
  }

  public void stop () {
    checkCallingThread();
    if (runnable != null) {
      handler.removeCallbacks(runnable);
      runnable = null;
    }
  }

  public void restart () {
    stop();
    start();
  }

  public boolean isStarted () {
    checkCallingThread();
    return runnable != null;
  }

  private void checkCallingThread () {
    if (handler.getLooper().getThread() != Thread.currentThread()) {
      throw new IllegalStateException();
    }
  }

  private Runnable createRunnable() {
    return new Runnable() {
      @Override
      public void run () {
        TdApi.Function<T> request = requestFactory.getValue();
        tdlib.send(request, (result, error) -> handler.post(() -> {
          if (runnable != this) {
            return;
          }
          resultHandler.onResult(result, error);
          if (runnable == this) {
            long delayMillis = Math.max(MIN_POLLING_DELAY_MILLIS, pollingDelayMillis.getLongValue());
            handler.postDelayed(runnable, delayMillis);
          }
        }));
      }
    };
  }
}
