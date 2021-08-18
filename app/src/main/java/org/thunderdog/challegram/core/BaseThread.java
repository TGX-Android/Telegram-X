/**
 * File created on 05/04/15 at 08:45
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.core;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

import java.util.concurrent.CountDownLatch;

public class BaseThread extends Thread {
  private volatile Handler handler, customHandler;
  private final CountDownLatch syncLatch;

  public BaseThread (String name) {
    syncLatch = new CountDownLatch(1);
    setName(name);
    start();
  }

  public Handler getHandler () {
    try {
      syncLatch.await();
    } catch (InterruptedException e) {
      Log.e(e);
    }
    return handler;
  }

  public Handler getCustomHandler () {
    try {
      syncLatch.await();
    } catch (InterruptedException e) {
      Log.e(e);
    }
    return customHandler;
  }

  public void quitLooper (boolean safely) {
    Looper looper = Looper.myLooper();
    if (looper != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && safely) {
        looper.quitSafely();
      } else {
        looper.quit();
      }
    }
  }

  public void sendMessage (@NonNull Message msg, long delay) {
    try {
      syncLatch.await();
    } catch (InterruptedException e) {
      Log.e(e);
    }
    if (delay <= 0)
      handler.sendMessage(msg);
    else
      handler.sendMessageDelayed(msg, delay);
  }

  public void post (@NonNull Runnable r, long delay) {
    try {
      syncLatch.await();
    } catch (InterruptedException e) {
      Log.e(e);
    }
    if (delay <= 0)
      handler.post(r);
    else
      handler.postDelayed(r, delay);
  }

  public void cancel (@Nullable Runnable r) {
    try {
      syncLatch.await();
    } catch (InterruptedException e) {
      Log.e(e);
    }
    handler.removeCallbacks(r);
  }

  protected Handler createCustomHandler () {
    return null;
  }

  @Override
  public void run () {
    Looper.prepare();
    handler = new BaseThreadHandler(this);
    customHandler = createCustomHandler();
    syncLatch.countDown();
    Looper.loop();
  }

  private static class BaseThreadHandler extends Handler {
    private BaseThread thread;

    public BaseThreadHandler (BaseThread thread) {
      this.thread = thread;
    }

    @Override
    public void handleMessage (@NonNull Message msg) {
      thread.process(msg);
    }
  }

  /* Should be overridden */
  protected void process (Message msg) { }
}