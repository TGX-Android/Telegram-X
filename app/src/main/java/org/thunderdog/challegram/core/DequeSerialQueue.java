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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * SerialQueue of its own: work without a delay in a deque, added at its end whatever waits, and
 * delayed work in a heap by time, under one lock. Work without a delay has the time it was sent,
 * so delayed work that is due by then runs before it, as a Looper orders them. Nodes of work are
 * reused, as messages of a Looper are
 */
public final class DequeSerialQueue extends SerialQueue {
  private static final int POOL_SIZE = 50;

  private static final class Work {
    private long when, order;
    private @Nullable Runnable runnable;
    private int what, arg1, arg2;
    private @Nullable Object obj;
    private @Nullable Work nextFree;
  }

  private static final Comparator<Work> BY_TIME = (a, b) ->
    a.when != b.when ? Long.compare(a.when, b.when) : Long.compare(a.order, b.order);

  private final Object lock = new Object();
  private final ArrayDeque<Work> now = new ArrayDeque<>();
  private final PriorityQueue<Work> later = new PriorityQueue<>(16, BY_TIME);
  private final Thread thread;
  private @Nullable Work free;
  private int freeCount;
  private long order;
  private boolean quitting;

  public DequeSerialQueue (@NonNull String name, int priority,
                           @Nullable Callback callback) {
    super(name, priority, callback);
    this.thread = new Thread(this::loop, name);
    thread.setPriority(priority);
    thread.start();
  }

  private void enqueue (@Nullable Runnable runnable, int what, int arg1, int arg2,
                        @Nullable Object obj, long delayMillis) {
    synchronized (lock) {
      if (quitting) {
        return;
      }
      Work work = free;
      if (work != null) {
        free = work.nextFree;
        work.nextFree = null;
        freeCount--;
      } else {
        work = new Work();
      }
      work.runnable = runnable;
      work.what = what;
      work.arg1 = arg1;
      work.arg2 = arg2;
      work.obj = obj;
      work.order = order++;
      work.when = System.nanoTime();
      if (delayMillis > 0) {
        work.when += delayMillis * 1_000_000L;
        later.add(work);
      } else {
        now.addLast(work);
      }
      lock.notify();
    }
  }

  private void recycle (Work work) {
    work.runnable = null;
    work.obj = null;
    if (freeCount < POOL_SIZE) {
      work.nextFree = free;
      free = work;
      freeCount++;
    }
  }

  private void loop () {
    while (true) {
      Runnable runnable;
      int what, arg1, arg2;
      Object obj;
      synchronized (lock) {
        Work work;
        while (true) {
          Work first = now.peekFirst();
          Work delayed = later.peek();
          long time = System.nanoTime();
          if (delayed != null && delayed.when <= time &&
            (first == null || BY_TIME.compare(delayed, first) < 0)) {
            work = later.poll();
            break;
          }
          if (first != null) {
            work = now.pollFirst();
            break;
          }
          if (quitting) {
            return;
          }
          try {
            if (delayed == null) {
              lock.wait();
            } else {
              long wait = delayed.when - time;
              lock.wait(wait / 1_000_000L, (int) (wait % 1_000_000L));
            }
          } catch (InterruptedException ignored) {
            // Nothing interrupts the thread: quit() wakes it by notify()
          }
        }
        runnable = work.runnable;
        what = work.what;
        arg1 = work.arg1;
        arg2 = work.arg2;
        obj = work.obj;
        recycle(work);
      }
      if (runnable != null) {
        runnable.run();
      } else if (callback != null) {
        callback.handle(what, arg1, arg2, obj);
      }
    }
  }

  private boolean matches (Work work, int what, @Nullable Object obj) {
    return work.runnable == null && work.what == what && (obj == null || work.obj == obj);
  }

  private void remove (Iterator<Work> iterator, @Nullable Runnable runnable, int what,
                       @Nullable Object obj) {
    while (iterator.hasNext()) {
      Work work = iterator.next();
      if (runnable != null ? work.runnable == runnable : matches(work, what, obj)) {
        iterator.remove();
        recycle(work);
      }
    }
  }

  @Override
  public void post (@NonNull Runnable work, long delayMillis) {
    enqueue(work, 0, 0, 0, null, delayMillis);
  }

  @Override
  public void send (int what, int arg1, int arg2, @Nullable Object obj, long delayMillis) {
    enqueue(null, what, arg1, arg2, obj, delayMillis);
  }

  @Override
  public void cancel (@NonNull Runnable work) {
    synchronized (lock) {
      remove(now.iterator(), work, 0, null);
      remove(later.iterator(), work, 0, null);
    }
  }

  @Override
  public void cancel (int what, @Nullable Object obj) {
    synchronized (lock) {
      remove(now.iterator(), null, what, obj);
      remove(later.iterator(), null, what, obj);
    }
  }

  @Override
  public boolean has (int what, @Nullable Object obj) {
    synchronized (lock) {
      for (Work work : now) {
        if (matches(work, what, obj)) {
          return true;
        }
      }
      for (Work work : later) {
        if (matches(work, what, obj)) {
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
    synchronized (lock) {
      quitting = true;
      if (!safely) {
        now.clear();
        later.clear();
      } else {
        // Work that is due by now still runs
        long time = System.nanoTime();
        Iterator<Work> iterator = later.iterator();
        while (iterator.hasNext()) {
          if (iterator.next().when > time) {
            iterator.remove();
          }
        }
      }
      lock.notify();
    }
  }
}
