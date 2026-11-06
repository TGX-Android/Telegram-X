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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SerialQueue on ScheduledThreadPoolExecutor of one thread: a heap by time and order of sending.
 * Work that waits is also kept in a set, so that it can be cancelled and found
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public final class ExecutorSerialQueue extends SerialQueue {
  private final ScheduledThreadPoolExecutor executor;
  private final HashSet<Work> waiting = new HashSet<>();
  private volatile Thread thread;

  private final class Work implements Runnable {
    private final @Nullable Runnable runnable;
    private final int what, arg1, arg2;
    private final @Nullable Object obj;
    private ScheduledFuture<?> future;

    private Work (@Nullable Runnable runnable, int what, int arg1, int arg2, @Nullable Object obj) {
      this.runnable = runnable;
      this.what = what;
      this.arg1 = arg1;
      this.arg2 = arg2;
      this.obj = obj;
    }

    private boolean matches (int what, @Nullable Object obj) {
      return runnable == null && this.what == what && (obj == null || this.obj == obj);
    }

    @Override
    public void run () {
      synchronized (waiting) {
        if (!waiting.remove(this)) {
          return;
        }
      }
      try {
        if (runnable != null) {
          runnable.run();
        } else if (callback != null) {
          callback.handle(what, arg1, arg2, obj);
        }
      } catch (Throwable t) {
        // The executor would keep it in the Future of the work, where nobody looks
        Thread current = Thread.currentThread();
        current.getUncaughtExceptionHandler().uncaughtException(current, t);
      }
    }
  }

  public ExecutorSerialQueue (@NonNull String name, int priority,
                              @Nullable Callback callback) {
    super(name, priority, callback);
    this.executor = new ScheduledThreadPoolExecutor(1, runnable -> {
      Thread thread = new Thread(runnable, name);
      thread.setPriority(priority);
      this.thread = thread;
      return thread;
    });
    executor.setRemoveOnCancelPolicy(true);
  }

  private void schedule (Work work, long delayMillis) {
    synchronized (waiting) {
      if (executor.isShutdown()) {
        return;
      }
      waiting.add(work);
      try {
        work.future = executor.schedule(work, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
      } catch (RejectedExecutionException e) {
        waiting.remove(work);
      }
    }
  }

  @Override
  public void post (@NonNull Runnable work, long delayMillis) {
    schedule(new Work(work, 0, 0, 0, null), delayMillis);
  }

  @Override
  public void send (int what, int arg1, int arg2, @Nullable Object obj, long delayMillis) {
    schedule(new Work(null, what, arg1, arg2, obj), delayMillis);
  }

  @Override
  public void cancel (@NonNull Runnable work) {
    synchronized (waiting) {
      Iterator<Work> iterator = waiting.iterator();
      while (iterator.hasNext()) {
        Work waitingWork = iterator.next();
        if (waitingWork.runnable == work) {
          iterator.remove();
          waitingWork.future.cancel(false);
        }
      }
    }
  }

  @Override
  public void cancel (int what, @Nullable Object obj) {
    synchronized (waiting) {
      Iterator<Work> iterator = waiting.iterator();
      while (iterator.hasNext()) {
        Work waitingWork = iterator.next();
        if (waitingWork.matches(what, obj)) {
          iterator.remove();
          waitingWork.future.cancel(false);
        }
      }
    }
  }

  @Override
  public boolean has (int what, @Nullable Object obj) {
    synchronized (waiting) {
      for (Work waitingWork : waiting) {
        if (waitingWork.matches(what, obj)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isCurrentThread () {
    return Thread.currentThread() == thread;
  }

  @Override
  public void quit (boolean safely) {
    synchronized (waiting) {
      if (safely) {
        // Work that is due still runs. The policy of delayed work after shutdown can't keep it
        // apart: on Android it cancels also work whose time has come, as Java 8 did
        executor.shutdown();
        Iterator<Work> iterator = waiting.iterator();
        while (iterator.hasNext()) {
          Work waitingWork = iterator.next();
          if (waitingWork.future.getDelay(TimeUnit.NANOSECONDS) > 0) {
            iterator.remove();
            waitingWork.future.cancel(false);
          }
        }
      } else {
        // As Looper.quit(), without interrupting the work that runs
        executor.shutdown();
        for (Work waitingWork : waiting) {
          waitingWork.future.cancel(false);
        }
        waiting.clear();
      }
    }
  }
}
