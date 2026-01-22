/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.media3.common.util.Util;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A watchdog timer.
 *
 * <p>Callers must follow this sequence:
 *
 * <ul>
 *   <li>Call {@link #start()} once to start the timer.
 *   <li>Call {@link #reset()} periodically to reset the timer and prevent a timeout.
 *   <li>Call {@link #stop()} once to stop the timer.
 * </ul>
 */
/* package */ final class WatchdogTimer {
  /** A listener for timeout events. */
  public interface Listener {
    /** Called when a timeout occurs. */
    void onTimeout();
  }

  private final long timeoutDurationMs;
  private final Listener listener;
  private final ScheduledExecutorService watchdogScheduledExecutorService;

  private @MonotonicNonNull ScheduledFuture<?> timeoutScheduledFuture;

  /**
   * Creates an instance.
   *
   * @param timeoutDurationMs The timeout duration in milliseconds.
   * @param listener The {@link Listener} to be notified when a timeout occurs.
   */
  public WatchdogTimer(long timeoutDurationMs, Listener listener) {
    this.timeoutDurationMs = timeoutDurationMs;
    this.listener = listener;
    watchdogScheduledExecutorService = Util.newSingleThreadScheduledExecutor("WatchdogTimer");
  }

  /** Starts the watchdog timer. */
  public void start() {
    scheduleNewTimer();
  }

  /** Resets the watchdog timer. */
  public void reset() {
    cancelExistingTimer();
    scheduleNewTimer();
  }

  /**
   * Stops the watchdog timer.
   *
   * <p>The watchdog timer can not be used after its stopped.
   */
  public void stop() {
    cancelExistingTimer();
    watchdogScheduledExecutorService.shutdownNow();
  }

  private void cancelExistingTimer() {
    checkNotNull(timeoutScheduledFuture).cancel(/* mayInterruptIfRunning= */ false);
  }

  private void scheduleNewTimer() {
    timeoutScheduledFuture =
        watchdogScheduledExecutorService.schedule(
            listener::onTimeout, timeoutDurationMs, MILLISECONDS);
  }
}
