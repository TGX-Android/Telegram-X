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

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.MediaItem;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link PreloadException}. */
@RunWith(AndroidJUnit4.class)
public final class PreloadExceptionTest {

  @Test
  public void errorInfoEquals_withSameInstance_shouldReturnTrue() {
    PreloadException preloadException =
        new PreloadException(
            new MediaItem.Builder().setMediaId("testId").build(),
            "Preload error",
            new IOException("cause message"));

    assertThat(preloadException.errorInfoEquals(preloadException)).isTrue();
  }

  @Test
  public void errorInfoEquals_withSameInfo_shouldReturnTrue() {
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("testId").build();
    IOException exception1 = new IOException("cause message");
    PreloadException preloadException1 =
        new PreloadException(mediaItem, "Preload error", exception1);
    IOException exception2 = new IOException("cause message");
    PreloadException preloadException2 =
        new PreloadException(mediaItem, "Preload error", exception2);

    assertThat(preloadException1.errorInfoEquals(preloadException2)).isTrue();
  }

  @Test
  public void errorInfoEquals_withNull_shouldReturnFalse() {
    PreloadException preloadException =
        new PreloadException(
            new MediaItem.Builder().setMediaId("testId").build(),
            "Preload error",
            new Exception("Cause message"));

    assertThat(preloadException.errorInfoEquals(null)).isFalse();
  }

  @Test
  public void errorInfoEquals_withNonEqualCauseMessage_shouldReturnFalse() {
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("testId").build();
    IOException exception1 = new IOException("cause message");
    PreloadException preloadException1 =
        new PreloadException(mediaItem, "Preload error", exception1);
    IOException exception2 = new IOException("other cause message");
    PreloadException preloadException2 =
        new PreloadException(mediaItem, "Preload error", exception2);

    assertThat(preloadException1.errorInfoEquals(preloadException2)).isFalse();
  }

  @Test
  public void errorInfoEquals_withDifferentCauseExceptionType_shouldReturnFalse() {
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("testId").build();
    IOException exception1 = new IOException("cause message");
    PreloadException preloadException1 =
        new PreloadException(mediaItem, "Preload error", exception1);
    TimeoutException exception2 = new TimeoutException("cause message");
    PreloadException preloadException2 =
        new PreloadException(mediaItem, "Preload error", exception2);

    assertThat(preloadException1.errorInfoEquals(preloadException2)).isFalse();
  }

  @Test
  public void errorInfoEquals_withNullCause_shouldReturnFalse() {
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("testId").build();
    IOException exception1 = new IOException("cause message");
    PreloadException preloadException1 =
        new PreloadException(mediaItem, "Preload error", exception1);
    PreloadException preloadException2 =
        new PreloadException(mediaItem, "Preload error", /* cause= */ null);

    assertThat(preloadException1.errorInfoEquals(preloadException2)).isFalse();
  }

  @Test
  public void errorInfoEquals_withNonEqualMediaItem_shouldReturnFalse() {
    PreloadException preloadException1 =
        new PreloadException(
            new MediaItem.Builder().setMediaId("testId1").build(),
            "Preload error",
            new IOException("cause message"));
    PreloadException preloadException2 =
        new PreloadException(
            new MediaItem.Builder().setMediaId("testId2").build(),
            "Preload error",
            new IOException("cause message"));

    assertThat(preloadException1.errorInfoEquals(preloadException2)).isFalse();
  }

  @Test
  public void errorInfoEquals_withNonEqualMessage_shouldReturnFalse() {
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("testId").build();
    PreloadException preloadException1 =
        new PreloadException(mediaItem, "Preload error", new IOException("cause message"));
    PreloadException preloadException2 =
        new PreloadException(mediaItem, "Other preload error", new IOException("cause message"));

    assertThat(preloadException1.errorInfoEquals(preloadException2)).isFalse();
  }
}
