/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmapUnpremultipliedAlpha;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.ConstantRateTimestampIterator;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link DefaultVideoFrameProcessor}. */
@RunWith(AndroidJUnit4.class)
public class DefaultVideoFrameProcessorTest {

  private static final long INPUT_REGISTRATION_TIMEOUT_MS = 1_000L;
  private static final String ORIGINAL_PNG_ASSET_PATH = "media/png/media3test_srgb.png";
  private static final long TEST_TIMEOUT_MS = 10_000L;

  private DefaultVideoFrameProcessor.@MonotonicNonNull Factory factory;
  private @MonotonicNonNull DefaultVideoFrameProcessor defaultVideoFrameProcessor;

  @Before
  public void setUp() {
    factory = new DefaultVideoFrameProcessor.Factory.Builder().build();
  }

  @After
  public void tearDown() {
    if (defaultVideoFrameProcessor != null) {
      defaultVideoFrameProcessor.release();
    }
  }

  @Test
  public void registerInputStream_withBlockingVideoFrameProcessorConfiguration_succeeds()
      throws Exception {
    AtomicReference<Exception> videoFrameProcessingException = new AtomicReference<>();
    CountDownLatch inputStreamRegisteredCountDownLatch = new CountDownLatch(1);
    defaultVideoFrameProcessor =
        createDefaultVideoFrameProcessor(
            new VideoFrameProcessor.Listener() {
              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  Format format,
                  List<Effect> effects) {
                inputStreamRegisteredCountDownLatch.countDown();
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                videoFrameProcessingException.set(exception);
              }
            });

    CountDownLatch videoFrameProcessorConfigurationCountDownLatch = new CountDownLatch(1);
    // Blocks VideoFrameProcessor configuration.
    defaultVideoFrameProcessor
        .getTaskExecutor()
        .submit(
            () -> {
              try {
                videoFrameProcessorConfigurationCountDownLatch.await();
              } catch (InterruptedException e) {
                throw new VideoFrameProcessingException(e);
              }
            });
    defaultVideoFrameProcessor.registerInputStream(
        VideoFrameProcessor.INPUT_TYPE_BITMAP,
        new Format.Builder()
            .setColorInfo(ColorInfo.SRGB_BT709_FULL)
            .setWidth(100)
            .setHeight(100)
            .build(),
        ImmutableList.of(),
        /* offsetToAddUs= */ 0);

    assertThat(defaultVideoFrameProcessor.getPendingInputFrameCount()).isEqualTo(0);
    // Unblocks configuration.
    videoFrameProcessorConfigurationCountDownLatch.countDown();
    assertThat(
            inputStreamRegisteredCountDownLatch.await(INPUT_REGISTRATION_TIMEOUT_MS, MILLISECONDS))
        .isTrue();
    assertThat(videoFrameProcessingException.get()).isNull();
  }

  @Test
  public void
      registerInputStream_threeTimesConsecutively_onInputStreamRegisteredIsInvokedCorrectly()
          throws Exception {
    AtomicReference<Exception> videoFrameProcessingException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(3);
    Queue<InputStreamInfo> registeredInputStreamInfoWidths = new ConcurrentLinkedQueue<>();
    defaultVideoFrameProcessor =
        createDefaultVideoFrameProcessor(
            new VideoFrameProcessor.Listener() {
              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  Format format,
                  List<Effect> effects) {
                registeredInputStreamInfoWidths.add(
                    new InputStreamInfo(inputType, format, effects));
                countDownLatch.countDown();
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                videoFrameProcessingException.set(exception);
              }
            });

    InputStreamInfo stream1 =
        new InputStreamInfo(
            VideoFrameProcessor.INPUT_TYPE_BITMAP,
            new Format.Builder()
                .setColorInfo(ColorInfo.SRGB_BT709_FULL)
                .setWidth(100)
                .setHeight(100)
                .build(),
            ImmutableList.of());
    InputStreamInfo stream2 =
        new InputStreamInfo(
            VideoFrameProcessor.INPUT_TYPE_BITMAP,
            new Format.Builder()
                .setColorInfo(ColorInfo.SRGB_BT709_FULL)
                .setWidth(200)
                .setHeight(200)
                .build(),
            ImmutableList.of(new Contrast(.5f)));
    InputStreamInfo stream3 =
        new InputStreamInfo(
            VideoFrameProcessor.INPUT_TYPE_BITMAP,
            new Format.Builder()
                .setColorInfo(ColorInfo.SRGB_BT709_FULL)
                .setWidth(300)
                .setHeight(300)
                .build(),
            ImmutableList.of());

    registerInputStream(defaultVideoFrameProcessor, stream1);
    registerInputStream(defaultVideoFrameProcessor, stream2);
    registerInputStream(defaultVideoFrameProcessor, stream3);

    assertThat(countDownLatch.await(INPUT_REGISTRATION_TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(videoFrameProcessingException.get()).isNull();
    assertThat(registeredInputStreamInfoWidths)
        .containsExactly(stream1, stream2, stream3)
        .inOrder();
  }

  @Test
  public void
      registerInputStream_withManualFrameRendering_configuresTheSecondStreamAfterRenderingAllFramesFromTheFirst()
          throws Exception {
    AtomicReference<Exception> videoFrameProcessingException = new AtomicReference<>();
    AtomicLong firstStreamLastFrameAvailableTimeMs = new AtomicLong();
    AtomicLong secondStreamConfigurationTimeMs = new AtomicLong();
    ConditionVariable inputStreamRegisteredCondition = new ConditionVariable();
    CountDownLatch frameProcessorEnded = new CountDownLatch(1);
    defaultVideoFrameProcessor =
        factory.create(
            getApplicationContext(),
            DebugViewProvider.NONE,
            /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
            /* renderFramesAutomatically= */ false,
            Util.newSingleThreadExecutor("DVFPTest"),
            new VideoFrameProcessor.Listener() {

              int outputFrameCount = 0;

              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  Format format,
                  List<Effect> effects) {
                inputStreamRegisteredCondition.open();
              }

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                outputFrameCount++;
                if (outputFrameCount == 30) {
                  firstStreamLastFrameAvailableTimeMs.set(SystemClock.DEFAULT.elapsedRealtime());
                }
                defaultVideoFrameProcessor.renderOutputFrame(
                    VideoFrameProcessor.RENDER_OUTPUT_FRAME_IMMEDIATELY);
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                videoFrameProcessingException.set(exception);
              }

              @Override
              public void onEnded() {
                frameProcessorEnded.countDown();
              }
            });

    Bitmap bitmap1 = readBitmapUnpremultipliedAlpha(ORIGINAL_PNG_ASSET_PATH);
    // Needs a different bitmap as the bitmap is recycled after single use.
    Bitmap bitmap2 = readBitmapUnpremultipliedAlpha(ORIGINAL_PNG_ASSET_PATH);

    // First image
    inputStreamRegisteredCondition.close();
    defaultVideoFrameProcessor.registerInputStream(
        VideoFrameProcessor.INPUT_TYPE_BITMAP,
        new Format.Builder()
            .setColorInfo(ColorInfo.SRGB_BT709_FULL)
            .setWidth(bitmap1.getWidth())
            .setHeight(bitmap1.getHeight())
            .build(),
        ImmutableList.of(),
        /* offsetToAddUs= */ 0);
    inputStreamRegisteredCondition.block();
    defaultVideoFrameProcessor.queueInputBitmap(
        bitmap1, new ConstantRateTimestampIterator(C.MICROS_PER_SECOND, 30.f));

    // Second image
    inputStreamRegisteredCondition.close();
    defaultVideoFrameProcessor.registerInputStream(
        VideoFrameProcessor.INPUT_TYPE_BITMAP,
        new Format.Builder()
            .setColorInfo(ColorInfo.SRGB_BT709_FULL)
            .setWidth(bitmap2.getWidth())
            .setHeight(bitmap2.getHeight())
            .build(),
        ImmutableList.of(
            (GlEffect)
                (context, useHdr) -> {
                  secondStreamConfigurationTimeMs.set(SystemClock.DEFAULT.elapsedRealtime());
                  return new PassthroughShaderProgram();
                }),
        /* offsetToAddUs= */ 0);
    inputStreamRegisteredCondition.block();
    defaultVideoFrameProcessor.queueInputBitmap(
        bitmap2, new ConstantRateTimestampIterator(C.MICROS_PER_SECOND, 30.f));

    defaultVideoFrameProcessor.signalEndOfInput();

    if (!frameProcessorEnded.await(TEST_TIMEOUT_MS, MILLISECONDS)) {
      throw new IllegalStateException("Test timeout", videoFrameProcessingException.get());
    }

    assertThat(secondStreamConfigurationTimeMs.get())
        .isAtLeast(firstStreamLastFrameAvailableTimeMs.get());
  }

  @Test
  public void registerInputStreamWithAutomaticFrameRegistration_succeeds() throws Exception {
    CountDownLatch inputStreamRegisteredCountDownLatch = new CountDownLatch(1);
    ConditionVariable outputFrameAvailableConditionVariable = new ConditionVariable();
    AtomicReference<@NullableType Exception> error = new AtomicReference<>();
    CountDownLatch endedCountDownLatch = new CountDownLatch(1);
    defaultVideoFrameProcessor =
        createDefaultVideoFrameProcessor(
            new VideoFrameProcessor.Listener() {
              @Override
              public void onInputStreamRegistered(
                  @VideoFrameProcessor.InputType int inputType,
                  Format format,
                  List<Effect> effects) {
                inputStreamRegisteredCountDownLatch.countDown();
              }

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                outputFrameAvailableConditionVariable.open();
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                error.set(exception);
              }

              @Override
              public void onEnded() {
                endedCountDownLatch.countDown();
              }
            });

    Bitmap bitmap = BitmapPixelTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH);
    defaultVideoFrameProcessor.registerInputStream(
        VideoFrameProcessor.INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION,
        new Format.Builder()
            .setColorInfo(ColorInfo.SRGB_BT709_FULL)
            .setWidth(bitmap.getWidth())
            .setHeight(bitmap.getHeight())
            .build(),
        /* effects= */ ImmutableList.of(),
        /* offsetToAddUs= */ 0);
    inputStreamRegisteredCountDownLatch.await();
    checkState(defaultVideoFrameProcessor.registerInputFrame());

    int inputFrameCount = 2;
    Surface surface = defaultVideoFrameProcessor.getInputSurface();
    for (int i = 0; i < inputFrameCount; i++) {
      Canvas canvas = surface.lockCanvas(/* inOutDirty= */ null);
      // Load the bitmap each time, as it's recycled after each use.
      canvas.drawBitmap(
          BitmapPixelTestUtil.readBitmap(ORIGINAL_PNG_ASSET_PATH),
          /* left= */ 0f,
          /* top= */ 0f,
          /* paint= */ null);
      // This causes a frame to become available on the input surface, which is processed by the
      // video frame processor.
      surface.unlockCanvasAndPost(canvas);
      // Wait for the output frame to be rendered.
      assertThat(outputFrameAvailableConditionVariable.block(TEST_TIMEOUT_MS)).isTrue();
      outputFrameAvailableConditionVariable.close();
    }
    defaultVideoFrameProcessor.signalEndOfInput();

    if (!endedCountDownLatch.await(TEST_TIMEOUT_MS, MILLISECONDS)) {
      throw new IllegalStateException("Test timeout", error.get());
    }
    assertThat(error.get()).isNull();
  }

  private DefaultVideoFrameProcessor createDefaultVideoFrameProcessor(
      VideoFrameProcessor.Listener listener) throws Exception {
    return checkNotNull(factory)
        .create(
            getApplicationContext(),
            DebugViewProvider.NONE,
            /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
            /* renderFramesAutomatically= */ true,
            /* listenerExecutor= */ MoreExecutors.directExecutor(),
            listener);
  }

  private static void registerInputStream(
      DefaultVideoFrameProcessor defaultVideoFrameProcessor, InputStreamInfo inputStreamInfo) {
    defaultVideoFrameProcessor.registerInputStream(
        inputStreamInfo.inputType,
        inputStreamInfo.format,
        inputStreamInfo.effects,
        /* offsetToAddUs= */ 0);
  }

  private static final class InputStreamInfo {
    public final @VideoFrameProcessor.InputType int inputType;
    public final Format format;
    public final List<Effect> effects;

    private InputStreamInfo(
        @VideoFrameProcessor.InputType int inputType, Format format, List<Effect> effects) {
      this.inputType = inputType;
      this.format = format;
      this.effects = effects;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof InputStreamInfo)) {
        return false;
      }
      InputStreamInfo that = (InputStreamInfo) o;
      return inputType == that.inputType
          && Objects.equals(this.format, that.format)
          && Objects.equals(this.effects, that.effects);
    }

    @Override
    public int hashCode() {
      int result = 17;
      result = 31 * result + inputType;
      result = 31 * result + format.hashCode();
      result = 31 * result + effects.hashCode();
      return result;
    }
  }
}
