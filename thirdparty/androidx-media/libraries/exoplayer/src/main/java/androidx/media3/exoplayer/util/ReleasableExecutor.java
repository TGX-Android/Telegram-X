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
package androidx.media3.exoplayer.util;

import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import java.util.concurrent.Executor;

/**
 * An {@link Executor} with a dedicated {@link #release} method to signal when it is not longer
 * needed.
 */
@UnstableApi
public interface ReleasableExecutor extends Executor {

  /**
   * Releases the {@link Executor}, indicating that the caller no longer requires it for executing
   * new commands.
   *
   * <p>When calling this method, there may still be pending commands that are currently executed.
   */
  void release();

  /**
   * Creates a {@link ReleasableExecutor} from an {@link Executor} and a release callback.
   *
   * @param executor The {@link Executor}
   * @param releaseCallback The release callback, accepting the {@code executor} as an argument.
   * @return The releasable executor.
   * @param <T> The type of {@link Executor}.
   */
  static <T extends Executor> ReleasableExecutor from(T executor, Consumer<T> releaseCallback) {
    return new ReleasableExecutor() {
      @Override
      public void execute(Runnable command) {
        executor.execute(command);
      }

      @Override
      public void release() {
        releaseCallback.accept(executor);
      }
    };
  }
}
