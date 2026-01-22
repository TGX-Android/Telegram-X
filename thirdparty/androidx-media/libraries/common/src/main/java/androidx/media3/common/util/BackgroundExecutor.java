/*
 * Copyright 2025 The Android Open Source Project
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
package androidx.media3.common.util;

import androidx.annotation.Nullable;
import java.util.concurrent.Executor;

/** A utility class to obtain an {@link Executor} for background tasks. */
@UnstableApi
public final class BackgroundExecutor {

  @SuppressWarnings("NonFinalStaticField")
  @Nullable
  private static Executor staticInstance;

  /**
   * Returns an {@link Executor} for background tasks.
   *
   * <p>Must only be used for quick, high-priority tasks to ensure other background tasks are not
   * blocked.
   *
   * <p>The thread is guaranteed to be alive for the lifetime of the application.
   */
  public static synchronized Executor get() {
    if (staticInstance == null) {
      staticInstance = Util.newSingleThreadExecutor("ExoPlayer:BackgroundExecutor");
    }
    return staticInstance;
  }

  /**
   * Sets the {@link Executor} to be returned from {@link #get()}.
   *
   * <p>Note that the thread of the provided {@link Executor} must stay alive for the lifetime of
   * the application.
   *
   * @param executor An {@link Executor} that runs tasks on background threads and should only be
   *     used for quick, high-priority tasks to ensure other background tasks are not blocked.
   */
  public static synchronized void set(Executor executor) {
    staticInstance = executor;
  }

  private BackgroundExecutor() {}
}
