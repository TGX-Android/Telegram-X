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

package androidx.media3.transformer.mh.performance;

import static androidx.media3.common.util.Util.usToMs;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888Image;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET;
import static androidx.media3.transformer.mh.performance.PlaybackTestUtil.createTimestampOverlay;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import androidx.media3.common.MediaItem;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.effect.MatrixTransformation;
import androidx.media3.effect.PreviewingMultipleInputVideoGraph;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.CompositionPlayer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Pixel tests for {@link CompositionPlayer} */
// These tests are in the performance package even though they are not performance tests so that
// they are not run on all devices. This is because they use ImageReader, which has a tendency to
// drop frames.
@RunWith(AndroidJUnit4.class)
public class CompositionPlayerPixelTest {

  private static final String TEST_DIRECTORY = "test-generated-goldens/CompositionPlayerPixelTest";
  private static final long TEST_TIMEOUT_MS = 20_000;

  /** Overlays non-zero-indexed frame as picture in picture. */
  private static final VideoCompositorSettings TEST_COMPOSITOR_SETTINGS =
      new VideoCompositorSettings() {
        @Override
        public Size getOutputSize(List<Size> inputSizes) {
          return inputSizes.get(0);
        }

        @Override
        public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
          if (inputId == 0) {
            return new StaticOverlaySettings.Builder().setAlphaScale(0.5f).build();
          }
          return new StaticOverlaySettings.Builder()
              .setAlphaScale(0.5f)
              .setBackgroundFrameAnchor(+0.9f, -0.3f)
              .setScale(0.4f, 0.4f)
              .setOverlayFrameAnchor(+1f, -1f)
              .build();
        }
      };

  @Rule public final TestName testName = new TestName();

  private final Context context = getInstrumentation().getContext().getApplicationContext();

  private @MonotonicNonNull CompositionPlayer player;
  private @MonotonicNonNull ImageReader outputImageReader;

  private String testId;

  @Before
  public void setUp() {
    testId = testName.getMethodName();
  }

  @After
  public void tearDown() {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              if (player != null) {
                player.release();
              }
              if (outputImageReader != null) {
                outputImageReader.close();
              }
            });
  }

  @Test
  public void compositionPlayerPreviewTest_ensuresFirstFrameRenderedCorrectly() throws Exception {
    AtomicReference<Bitmap> renderedFirstFrameBitmap = new AtomicReference<>();
    AtomicReference<PlaybackException> playerError = new AtomicReference<>();
    ConditionVariable hasRenderedFirstFrameCondition = new ConditionVariable();
    outputImageReader =
        ImageReader.newInstance(
            MP4_ASSET.videoFormat.width,
            MP4_ASSET.videoFormat.height,
            PixelFormat.RGBA_8888,
            /* maxImages= */ 1);

    getInstrumentation()
        .runOnMainSync(
            () -> {
              player = new CompositionPlayer.Builder(context).build();
              outputImageReader.setOnImageAvailableListener(
                  imageReader -> {
                    try (Image image = imageReader.acquireLatestImage()) {
                      renderedFirstFrameBitmap.set(createArgb8888BitmapFromRgba8888Image(image));
                    }
                    hasRenderedFirstFrameCondition.open();
                  },
                  Util.createHandlerForCurrentOrMainLooper());

              player.setVideoSurface(
                  outputImageReader.getSurface(),
                  new Size(MP4_ASSET.videoFormat.width, MP4_ASSET.videoFormat.height));
              player.setComposition(
                  new Composition.Builder(
                          new EditedMediaItemSequence.Builder(
                                  new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
                                      .setEffects(
                                          new Effects(
                                              /* audioProcessors= */ ImmutableList.of(),
                                              /* videoEffects= */ ImmutableList.of(
                                                  createTimestampOverlay())))
                                      .setDurationUs(MP4_ASSET.videoDurationUs)
                                      .build())
                              .build())
                      .build());
              player.addListener(
                  new Player.Listener() {
                    @Override
                    public void onPlayerError(PlaybackException error) {
                      // Unblock the main thread promptly.
                      hasRenderedFirstFrameCondition.open();
                      playerError.set(error);
                    }
                  });
              player.prepare();
            });

    boolean conditionOpened = hasRenderedFirstFrameCondition.block(TEST_TIMEOUT_MS);
    if (playerError.get() != null) {
      throw playerError.get();
    }
    if (!conditionOpened) {
      throw new TimeoutException(
          Util.formatInvariant("First frame not rendered in %d ms.", TEST_TIMEOUT_MS));
    }

    assertWithMessage("First frame is not rendered.")
        .that(renderedFirstFrameBitmap.get())
        .isNotNull();
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            /* expected= */ readBitmap(TEST_DIRECTORY + "/first_frame.png"),
            /* actual= */ renderedFirstFrameBitmap.get(),
            testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    // TODO: b/315800590 - Verify onFirstFrameRendered is invoked only once.
  }

  @Test
  public void
      compositionPlayerPreviewTest_withTwoVideoSequences_ensuresFirstFrameRenderedCorrectly()
          throws Exception {
    AtomicReference<Bitmap> renderedFirstFrameBitmap = new AtomicReference<>();
    AtomicReference<PlaybackException> playerError = new AtomicReference<>();
    ConditionVariable hasRenderedFirstFrameCondition = new ConditionVariable();
    outputImageReader =
        ImageReader.newInstance(
            MP4_ASSET.videoFormat.width,
            MP4_ASSET.videoFormat.height,
            PixelFormat.RGBA_8888,
            /* maxImages= */ 1);

    getInstrumentation()
        .runOnMainSync(
            () -> {
              player =
                  new CompositionPlayer.Builder(context)
                      .setPreviewingVideoGraphFactory(
                          new PreviewingMultipleInputVideoGraph.Factory())
                      .build();
              outputImageReader.setOnImageAvailableListener(
                  imageReader -> {
                    try (Image image = imageReader.acquireLatestImage()) {
                      renderedFirstFrameBitmap.set(createArgb8888BitmapFromRgba8888Image(image));
                    }
                    hasRenderedFirstFrameCondition.open();
                  },
                  Util.createHandlerForCurrentOrMainLooper());

              player.setVideoSurface(
                  outputImageReader.getSurface(),
                  new Size(MP4_ASSET.videoFormat.width, MP4_ASSET.videoFormat.height));
              player.setComposition(
                  new Composition.Builder(
                          new EditedMediaItemSequence.Builder(
                                  new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
                                      .setEffects(
                                          new Effects(
                                              /* audioProcessors= */ ImmutableList.of(),
                                              /* videoEffects= */ ImmutableList.of(
                                                  createTimestampOverlay())))
                                      .setDurationUs(MP4_ASSET.videoDurationUs)
                                      .build())
                              .build(),
                          new EditedMediaItemSequence.Builder(
                                  new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
                                      .setEffects(
                                          new Effects(
                                              /* audioProcessors= */ ImmutableList.of(),
                                              /* videoEffects= */ ImmutableList.of(
                                                  (MatrixTransformation)
                                                      presentationTimeUs -> {
                                                        Matrix rotationMatrix = new Matrix();
                                                        rotationMatrix.postRotate(
                                                            /* degrees= */ 90);
                                                        return rotationMatrix;
                                                      })))
                                      .setDurationUs(MP4_ASSET.videoDurationUs)
                                      .build())
                              .build())
                      .setVideoCompositorSettings(TEST_COMPOSITOR_SETTINGS)
                      .build());
              player.addListener(
                  new Player.Listener() {
                    @Override
                    public void onPlayerError(PlaybackException error) {
                      playerError.set(error);
                      // Unblock the main thread promptly.
                      hasRenderedFirstFrameCondition.open();
                    }
                  });
              player.prepare();
            });

    boolean conditionOpened = hasRenderedFirstFrameCondition.block(TEST_TIMEOUT_MS);
    if (playerError.get() != null) {
      throw playerError.get();
    }
    if (!conditionOpened) {
      throw new TimeoutException(
          Util.formatInvariant("First frame not rendered in %d ms.", TEST_TIMEOUT_MS));
    }

    assertWithMessage("First frame is not rendered.")
        .that(renderedFirstFrameBitmap.get())
        .isNotNull();
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            /* expected= */ readBitmap(
                Util.formatInvariant("%s/%s.png", TEST_DIRECTORY, testName.getMethodName())),
            /* actual= */ renderedFirstFrameBitmap.get(),
            testId);

    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    // TODO: b/315800590 - Verify onFirstFrameRendered is invoked only once.
  }

  @Test
  public void
      compositionPlayerPreviewTest_withTwoImageSequences_ensuresFirstFrameRenderedCorrectly()
          throws Exception {
    AtomicReference<Bitmap> renderedFirstFrameBitmap = new AtomicReference<>();
    AtomicReference<PlaybackException> playerError = new AtomicReference<>();
    ConditionVariable hasRenderedFirstFrameCondition = new ConditionVariable();
    outputImageReader =
        ImageReader.newInstance(
            PNG_ASSET.videoFormat.width,
            PNG_ASSET.videoFormat.height,
            PixelFormat.RGBA_8888,
            /* maxImages= */ 1);

    long imageDurationUs = 200_000L;
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player =
                  new CompositionPlayer.Builder(context)
                      .setPreviewingVideoGraphFactory(
                          new PreviewingMultipleInputVideoGraph.Factory())
                      .build();
              outputImageReader.setOnImageAvailableListener(
                  imageReader -> {
                    try (Image image = imageReader.acquireLatestImage()) {
                      renderedFirstFrameBitmap.set(createArgb8888BitmapFromRgba8888Image(image));
                    }
                    hasRenderedFirstFrameCondition.open();
                  },
                  Util.createHandlerForCurrentOrMainLooper());

              player.setVideoSurface(
                  outputImageReader.getSurface(),
                  new Size(PNG_ASSET.videoFormat.width, PNG_ASSET.videoFormat.height));
              player.setComposition(
                  new Composition.Builder(
                          new EditedMediaItemSequence.Builder(
                                  new EditedMediaItem.Builder(
                                          MediaItem.fromUri(PNG_ASSET.uri)
                                              .buildUpon()
                                              .setImageDurationMs(usToMs(imageDurationUs))
                                              .build())
                                      .setEffects(
                                          new Effects(
                                              /* audioProcessors= */ ImmutableList.of(),
                                              /* videoEffects= */ ImmutableList.of(
                                                  createTimestampOverlay(/* textSize= */ 30))))
                                      .setDurationUs(imageDurationUs)
                                      .setFrameRate(30)
                                      .build())
                              .build(),
                          new EditedMediaItemSequence.Builder(
                                  new EditedMediaItem.Builder(
                                          MediaItem.fromUri(PNG_ASSET.uri)
                                              .buildUpon()
                                              .setImageDurationMs(usToMs(imageDurationUs))
                                              .build())
                                      .setEffects(
                                          new Effects(
                                              /* audioProcessors= */ ImmutableList.of(),
                                              /* videoEffects= */ ImmutableList.of(
                                                  (MatrixTransformation)
                                                      presentationTimeUs -> {
                                                        Matrix rotationMatrix = new Matrix();
                                                        rotationMatrix.postRotate(
                                                            /* degrees= */ 90);
                                                        return rotationMatrix;
                                                      })))
                                      .setDurationUs(imageDurationUs)
                                      .setFrameRate(30)
                                      .build())
                              .build())
                      .setVideoCompositorSettings(TEST_COMPOSITOR_SETTINGS)
                      .build());
              player.addListener(
                  new Player.Listener() {
                    @Override
                    public void onPlayerError(PlaybackException error) {
                      // Unblock the main thread promptly.
                      hasRenderedFirstFrameCondition.open();
                      playerError.set(error);
                    }
                  });
              player.prepare();
            });

    boolean conditionOpened = hasRenderedFirstFrameCondition.block(TEST_TIMEOUT_MS);
    if (playerError.get() != null) {
      throw playerError.get();
    }
    if (!conditionOpened) {
      throw new TimeoutException(
          Util.formatInvariant("First frame not rendered in %d ms.", TEST_TIMEOUT_MS));
    }

    assertWithMessage("First frame is not rendered.")
        .that(renderedFirstFrameBitmap.get())
        .isNotNull();
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            /* expected= */ readBitmap(
                Util.formatInvariant("%s/%s.png", TEST_DIRECTORY, testName.getMethodName())),
            /* actual= */ renderedFirstFrameBitmap.get(),
            testId);

    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    // TODO: b/315800590 - Verify onFirstFrameRendered is invoked only once.
  }
}
