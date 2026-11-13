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

/**
 * Work run one at a time on a thread of its own. Callers don't know the implementation, so it can
 * be changed without them. Guarantees, the same as those of a Looper:
 * - work sent without a delay runs in the order it was sent, also from several threads, after
 *   delayed work that is due by then;
 * - delayed work runs not earlier than its time, by its time and then by the order of sending;
 * - cancelled work doesn't run;
 * - an exception of a work goes to the uncaught exception handler of the thread, which ends the
 *   app, as on a Looper.
 * send() is work for the callback, as Message.obtain(handler, what, arg1, arg2, obj) of a Looper;
 * cancel(what, obj) and has(what, obj) take any obj when it's null, as Handler.removeMessages
 * and Handler.hasMessages. quit() ends the thread when the work that is due has run, or at once
 * when it isn't safely, and work sent after it is dropped.
 */
public abstract class SerialQueue {
  public interface Callback {
    void handle (int what, int arg1, int arg2, @Nullable Object obj);
  }

  protected final String name;
  protected final int priority;
  protected final @Nullable Callback callback;

  protected SerialQueue (@NonNull String name, int priority, @Nullable Callback callback) {
    this.name = name;
    this.priority = priority;
    this.callback = callback;
  }

  public static SerialQueue create (@NonNull String name, int priority,
                                    @Nullable Callback callback) {
    // A send costs the same whatever waits, while the Looper of older Androids walks all its
    // messages, e.g. 264 µs with 100 000 waiting on Android 13 of a Samsung Galaxy S20. Work that
    // needs a Looper on its thread, e.g. for callbacks of the camera, takes LooperSerialQueue
    return new DequeSerialQueue(name, priority, callback);
  }

  public final String getName () {
    return name;
  }

  public final void post (@NonNull Runnable work) {
    post(work, 0);
  }

  public abstract void post (@NonNull Runnable work, long delayMillis);

  public final void send (int what, int arg1, int arg2, @Nullable Object obj) {
    send(what, arg1, arg2, obj, 0);
  }

  public abstract void send (int what, int arg1, int arg2, @Nullable Object obj, long delayMillis);

  public abstract void cancel (@NonNull Runnable work);

  public abstract void cancel (int what, @Nullable Object obj);

  public abstract boolean has (int what, @Nullable Object obj);

  public abstract boolean isCurrentThread ();

  public abstract void quit (boolean safely);
}
