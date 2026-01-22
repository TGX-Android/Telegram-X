/*
 * Copyright (C) 2024 The Android Open Source Project
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
package androidx.media3.exoplayer;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.os.Looper;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;

/** Provides methods to check the suitability of selected media outputs. */
@RestrictTo(LIBRARY_GROUP)
@UnstableApi
public interface SuitableOutputChecker {

  /** Callback to notify changes in the suitability of the selected media output. */
  interface Callback {

    /**
     * Called when suitability of the selected output has changed.
     *
     * @param isSelectedOutputSuitableForPlayback true when selected output is suitable for
     *     playback.
     */
    void onSelectedOutputSuitabilityChanged(boolean isSelectedOutputSuitableForPlayback);
  }

  /**
   * Enables the current instance to receive updates on the selected media outputs and sets the
   * {@link Callback} to notify the updates on the suitability of the selected output.
   *
   * <p>When the caller no longer requires updates on suitable outputs, they must call {@link
   * #disable()}.
   *
   * @param callback To receive notifications of changes in suitable media output changes.
   * @param context A {@link Context}.
   * @param callbackLooper The {@link Looper} to call {@link Callback} methods on.
   * @param backgroundLooper The {@link Looper} to run background operations on.
   * @param clock The {@link Clock}.
   */
  void enable(
      Callback callback,
      Context context,
      Looper callbackLooper,
      Looper backgroundLooper,
      Clock clock);

  /**
   * Disables the current instance to receive updates on the selected media outputs and clears the
   * {@link Callback}.
   *
   * @throws IllegalStateException if this instance is not enabled to receive the updates on
   *     suitable media outputs.
   */
  void disable();

  /**
   * Returns whether any audio output is suitable for the media playback.
   *
   * @throws IllegalStateException if this instance is not enabled to receive the updates on
   *     suitable media outputs.
   */
  boolean isSelectedOutputSuitableForPlayback();
}
