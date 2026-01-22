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
package androidx.media3.exoplayer.source.preload;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import java.util.Objects;

/** Thrown when a non-recoverable preload failure occurs. */
@UnstableApi
public final class PreloadException extends Exception {

  /** The {@link MediaItem} that this instance is associated with. */
  public final MediaItem mediaItem;

  /**
   * Creates an instance.
   *
   * @param mediaItem The {@link MediaItem} that this instance is associated with.
   * @param message See {@link #getMessage()}.
   * @param cause See {@link #getCause()}.
   */
  public PreloadException(
      MediaItem mediaItem, @Nullable String message, @Nullable Throwable cause) {
    super(message, cause);
    this.mediaItem = mediaItem;
  }

  /**
   * Returns whether the error data associated to this exception equals the error data associated to
   * {@code other}.
   *
   * <p>Note that this method does not compare the exceptions' stack traces.
   */
  public boolean errorInfoEquals(@Nullable PreloadException other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    @Nullable Throwable thisCause = getCause();
    @Nullable Throwable thatCause = other.getCause();
    if (thisCause != null && thatCause != null) {
      if (!Objects.equals(thisCause.getMessage(), thatCause.getMessage())) {
        return false;
      }
      if (!Objects.equals(thisCause.getClass(), thatCause.getClass())) {
        return false;
      }
    } else if (thisCause != null || thatCause != null) {
      return false;
    }
    return Objects.equals(mediaItem, other.mediaItem)
        && Objects.equals(getMessage(), other.getMessage());
  }
}
