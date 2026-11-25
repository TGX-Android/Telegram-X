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
 * File created on 01/11/2026
 */
package org.thunderdog.challegram.core;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;

/**
 * SerialQueue on a Looper, as BaseThread: a list of messages sorted by time, which walks the
 * messages that wait to add one. Its work can use APIs that deliver callbacks to the Looper of
 * the current thread
 */
public final class LooperSerialQueue extends SerialQueue {
  private final Thread thread;
  private final CountDownLatch ready = new CountDownLatch(1);
  private volatile Handler looperHandler;

  public LooperSerialQueue (@NonNull String name, int priority,
                            @Nullable Callback callback) {
    super(name, priority, callback);
    this.thread = new Thread(() -> {
      Looper.prepare();
      looperHandler = new Handler(Looper.myLooper(), message -> {
        if (callback != null) {
          callback.handle(message.what, message.arg1, message.arg2, message.obj);
        }
        return true;
      });
      ready.countDown();
      Looper.loop();
    }, name);
    thread.setPriority(priority);
    thread.start();
  }

  private Handler looperHandler () {
    boolean interrupted = false;
    while (looperHandler == null) {
      try {
        ready.await();
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    return looperHandler;
  }

  @Override
  public void post (@NonNull Runnable work, long delayMillis) {
    looperHandler().postDelayed(work, Math.max(0, delayMillis));
  }

  @Override
  public void send (int what, int arg1, int arg2, @Nullable Object obj, long delayMillis) {
    Handler target = looperHandler();
    Message message = Message.obtain(target, what, arg1, arg2, obj);
    target.sendMessageDelayed(message, Math.max(0, delayMillis));
  }

  @Override
  public void cancel (@NonNull Runnable work) {
    looperHandler().removeCallbacks(work);
  }

  @Override
  public void cancel (int what, @Nullable Object obj) {
    looperHandler().removeMessages(what, obj);
  }

  @Override
  public boolean has (int what, @Nullable Object obj) {
    return looperHandler().hasMessages(what, obj);
  }

  @Override
  public boolean isCurrentThread () {
    return Thread.currentThread() == thread;
  }

  @Override
  public void quit (boolean safely) {
    Looper looper = looperHandler().getLooper();
    if (safely && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      looper.quitSafely();
    } else {
      looper.quit();
    }
  }
}
