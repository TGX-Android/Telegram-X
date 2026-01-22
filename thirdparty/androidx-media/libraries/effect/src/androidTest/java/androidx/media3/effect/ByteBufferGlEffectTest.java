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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.effect.EffectsTestUtil.generateAndProcessFrames;
import static androidx.media3.effect.EffectsTestUtil.getAndAssertOutputBitmaps;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.GlRect;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for {@link ByteBufferGlEffect}. */
@RunWith(AndroidJUnit4.class)
public class ByteBufferGlEffectTest {

  @Rule public final TestName testName = new TestName();

  private static final String ASSET_PATH = "test-generated-goldens/ByteBufferGlEffectTest";

  private static final int INPUT_FRAME_WIDTH = 100;
  private static final int INPUT_FRAME_HEIGHT = 50;
  private static final int EFFECT_INPUT_FRAME_WIDTH = 75;
  private static final int EFFECT_INPUT_FRAME_HEIGHT = 30;
  private static final int EFFECT_OUTPUT_FRAME_WIDTH = 50;
  private static final int EFFECT_OUTPUT_FRAME_HEIGHT = 20;
  private static final Consumer<SpannableString> TEXT_SPAN_CONSUMER =
      (text) -> {
        text.setSpan(
            new ForegroundColorSpan(Color.BLACK),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
            new AbsoluteSizeSpan(/* size= */ 24),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
            new TypefaceSpan(/* family= */ "sans-serif"),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      };

  private @MonotonicNonNull TextureBitmapReader textureBitmapReader;
  private String testId;

  @Before
  public void setUp() {
    textureBitmapReader = new TextureBitmapReader();
    testId = testName.getMethodName();
  }

  @Test
  public void byteBufferEffectImplementation_receivesCorrectBitmapData() throws Exception {
    List<Bitmap> effectInputBitmaps = new ArrayList<>();
    List<Bitmap> effectOutputBitmaps = new ArrayList<>();
    ImmutableList<Long> frameTimesUs = ImmutableList.of(0L, 333_333L, 666_667L);
    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessFrames(
            INPUT_FRAME_WIDTH,
            INPUT_FRAME_HEIGHT,
            frameTimesUs,
            new ByteBufferGlEffect<>(
                new TestByteBufferProcessor(effectInputBitmaps, effectOutputBitmaps)),
            textureBitmapReader,
            TEXT_SPAN_CONSUMER);

    assertThat(actualPresentationTimesUs).containsExactlyElementsIn(frameTimesUs).inOrder();

    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
    assertBitmapsMatchExpected(
        effectInputBitmaps, actualPresentationTimesUs, testId, /* suffix= */ "_input");
    assertBitmapsMatchExpected(
        effectOutputBitmaps, actualPresentationTimesUs, testId, /* suffix= */ "_output");
  }

  private static class TestByteBufferProcessor implements ByteBufferGlEffect.Processor<Bitmap> {

    private final List<Bitmap> inputBitmaps;
    private final List<Bitmap> outputBitmaps;
    private final ListeningExecutorService drawingService;

    TestByteBufferProcessor(List<Bitmap> inputBitmaps, List<Bitmap> outputBitmaps) {
      drawingService =
          MoreExecutors.listeningDecorator(
              Util.newSingleThreadExecutor(/* threadName= */ "TestByteBufferEffect"));
      this.inputBitmaps = inputBitmaps;
      this.outputBitmaps = outputBitmaps;
    }

    @Override
    public Size configure(int inputWidth, int inputHeight) {
      checkState(inputWidth == INPUT_FRAME_WIDTH);
      checkState(inputHeight == INPUT_FRAME_HEIGHT);
      return new Size(EFFECT_INPUT_FRAME_WIDTH, EFFECT_INPUT_FRAME_HEIGHT);
    }

    @Override
    public GlRect getScaledRegion(long presentationTimeUs) {
      return new GlRect(INPUT_FRAME_WIDTH, INPUT_FRAME_HEIGHT);
    }

    @Override
    public ListenableFuture<Bitmap> processImage(
        ByteBufferGlEffect.Image image, long presentationTimeUs) {
      checkState(image.width == EFFECT_INPUT_FRAME_WIDTH);
      checkState(image.height == EFFECT_INPUT_FRAME_HEIGHT);
      checkState(
          image.pixelBuffer.capacity() == EFFECT_INPUT_FRAME_WIDTH * EFFECT_INPUT_FRAME_HEIGHT * 4);
      Bitmap inputBitmap = image.copyToBitmap();
      inputBitmaps.add(inputBitmap);
      return drawingService.submit(
          () ->
              Bitmap.createScaledBitmap(
                  inputBitmap,
                  EFFECT_OUTPUT_FRAME_WIDTH,
                  EFFECT_OUTPUT_FRAME_HEIGHT,
                  /* filter= */ true));
    }

    @Override
    public void finishProcessingAndBlend(
        GlTextureInfo outputFrame, long presentationTimeUs, Bitmap result) {
      outputBitmaps.add(result);
    }

    @Override
    public void release() {}
  }

  private static void assertBitmapsMatchExpected(
      List<Bitmap> bitmaps, List<Long> presentationTimesUs, String testId, String suffix)
      throws IOException {
    checkState(bitmaps.size() == presentationTimesUs.size());
    for (int i = 0; i < presentationTimesUs.size(); i++) {
      long presentationTimeUs = presentationTimesUs.get(i);
      Bitmap actualBitmap = bitmaps.get(i);
      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ presentationTimeUs + suffix, actualBitmap, /* path= */ null);
      Bitmap expectedBitmap =
          readBitmap(
              Util.formatInvariant("%s/pts_%d.png", ASSET_PATH + suffix, presentationTimeUs));
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(
              expectedBitmap, actualBitmap, testId + "_" + i);
      // Golden bitmaps were generated with ffmpeg, use a higher threshold.
      // TODO: b/361286064 - Use PSNR for quality computations.
      assertThat(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
    }
  }
}
