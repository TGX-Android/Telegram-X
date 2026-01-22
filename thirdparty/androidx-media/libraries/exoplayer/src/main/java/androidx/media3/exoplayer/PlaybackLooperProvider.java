/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.media3.common.util.Assertions.checkState;

import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import androidx.annotation.GuardedBy;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides a {@link Looper} for multiple {@link ExoPlayer} instances with reference counting in
 * order to properly manage the lifecycle of the thread that the {@link Looper} is associated with.
 */
@RestrictTo(LIBRARY_GROUP)
@UnstableApi
public final class PlaybackLooperProvider {

  private final Object lock;

  @GuardedBy("lock")
  private @Nullable Looper playbackLooper;

  @GuardedBy("lock")
  private @Nullable HandlerThread internalPlaybackThread;

  @GuardedBy("lock")
  private int referenceCount;

  /**
   * Creates an instance.
   *
   * <p>The {@link PlaybackLooperProvider} instance will create a {@link HandlerThread} internally
   * and manage its lifecycle.
   */
  public PlaybackLooperProvider() {
    this(/* looper= */ null);
  }

  /**
   * Creates an instance.
   *
   * @param looper The {@linkplain Looper playback looper}. If non-null, the caller is responsible
   *     for managing the lifecycle of the thread that the {@code looper} is associated with.
   *     Otherwise, the {@link PlaybackLooperProvider} instance will create a {@linkplain
   *     HandlerThread playback thread} internally and manage its lifecycle.
   */
  public PlaybackLooperProvider(@Nullable Looper looper) {
    lock = new Object();
    playbackLooper = looper;
    internalPlaybackThread = null;
    referenceCount = 0;
  }

  /**
   * Obtains the {@linkplain Looper playback looper} by increasing the reference count.
   *
   * @return The playback looper. It will either be the {@code looper} injected via the constructor,
   *     or the {@linkplain HandlerThread#getLooper() looper} associated with the internal {@link
   *     HandlerThread playback thread}.
   */
  public Looper obtainLooper() {
    synchronized (lock) {
      if (playbackLooper == null) {
        checkState(referenceCount == 0 && internalPlaybackThread == null);
        // Note: The documentation for Process.THREAD_PRIORITY_AUDIO that states "Applications can
        // not normally change to this priority" is incorrect.
        internalPlaybackThread =
            new HandlerThread("ExoPlayer:Playback", Process.THREAD_PRIORITY_AUDIO);
        internalPlaybackThread.start();
        playbackLooper = internalPlaybackThread.getLooper();
      }
      referenceCount++;
      return playbackLooper;
    }
  }

  /**
   * Releases the {@linkplain Looper playback looper} by decreasing the reference count.
   *
   * <p>If the playback looper was not provided by the caller, the {@link PlaybackLooperProvider}
   * instance will automatically stop the internal {@link HandlerThread playback thread}.
   */
  public void releaseLooper() {
    synchronized (lock) {
      checkState(referenceCount > 0);
      referenceCount--;
      if (referenceCount == 0 && internalPlaybackThread != null) {
        internalPlaybackThread.quit();
        internalPlaybackThread = null;
        playbackLooper = null;
      }
    }
  }
}
