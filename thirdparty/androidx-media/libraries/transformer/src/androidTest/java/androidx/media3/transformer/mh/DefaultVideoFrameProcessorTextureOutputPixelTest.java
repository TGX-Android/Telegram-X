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
package androidx.media3.transformer.mh;

import static androidx.media3.common.ColorInfo.SDR_BT709_LIMITED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.test.utils.TestUtil.assertBitmapsAreSimilar;
import static androidx.media3.test.utils.VideoFrameProcessorTestRunner.VIDEO_FRAME_PROCESSING_WAIT_MS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.SequenceEffectTestUtil.PSNR_THRESHOLD;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsHdrEditing;
import static androidx.media3.transformer.mh.UnoptimizedGlEffect.NO_OP_EFFECT;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.DefaultGlObjectsProvider;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.GaussianBlur;
import androidx.media3.effect.GlTextureProducer;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.json.JSONException;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Pixel test for video frame processing, outputting to a texture, via {@link
 * DefaultVideoFrameProcessor}.
 *
 * <p>Uses a {@link DefaultVideoFrameProcessor} to process one frame, and checks that the actual
 * output matches expected output, either from a golden file or from another edit.
 */
// TODO: b/263395272 - Move this test to effects/mh tests, and remove @TestOnly dependencies.
@RunWith(AndroidJUnit4.class)
public final class DefaultVideoFrameProcessorTextureOutputPixelTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String BITMAP_OVERLAY_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/overlay_bitmap_FrameProcessor.png";
  private static final String GAUSSIAN_BLUR_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/gaussian_blur.png";

  private static final String OVERLAY_PNG_ASSET_PATH = "media/png/media3test.png";
  private static final String ULTRA_HDR_ASSET_PATH = "media/jpeg/ultraHDR.jpg";

  private static final String ORIGINAL_HLG10_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original_hlg10.png";
  private static final String ORIGINAL_HDR10_PNG_ASSET_PATH =
      "test-generated-goldens/sample_mp4_first_frame/electrical_colors/original_hdr10.png";
  private static final String ULTRA_HDR_TO_HLG_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/ultrahdr_to_hlg.png";
  private static final String ULTRA_HDR_TO_PQ_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/ultrahdr_to_pq.png";
  private static final String HLG_TO_PQ_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/original_hlg10_to_pq.png";
  private static final String PQ_TO_HLG_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/original_hdr10_to_hlg.png";
  private static final String ULTRA_HDR_OVERLAY_HLG_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/ultrahdr_overlay_hlg.png";
  private static final String ULTRA_HDR_OVERLAY_PQ_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/ultrahdr_overlay_pq.png";
  private static final String ULTRA_HDR_AND_TEXT_OVERLAY_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/ultrahdr_and_text_overlay.png";

  private static final String HDR_TEXT_OVERLAY_PNG_ASSET_PATH =
      "test-generated-goldens/hdr-goldens/text_overlay.png";

  /** Input SDR video of which we only use the first frame. */
  private static final String INPUT_SDR_MP4_ASSET_STRING = "media/mp4/sample.mp4";

  /** Input PQ video of which we only use the first frame. */
  private static final String INPUT_PQ_MP4_ASSET_STRING = "media/mp4/hdr10-720p.mp4";

  /** Input HLG video of which we only use the first frame. */
  private static final String INPUT_HLG10_MP4_ASSET_STRING = "media/mp4/hlg-1080p.mp4";

  public static final float HDR_PSNR_THRESHOLD = 43.5f;

  @Rule public final TestName testName = new TestName();

  private String testId;

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @After
  public void release() {
    if (videoFrameProcessorTestRunner != null) {
      videoFrameProcessorTestRunner.release();
    }
  }

  @Test
  public void noEffects_matchesGoldenFile() throws Exception {
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ null);
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void noEffects_textureInput_matchesGoldenFile() throws Exception {
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ null);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_SDR_MP4_ASSET_STRING,
            SDR_BT709_LIMITED,
            ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void gaussianBlur_matchesGoldenFile() throws Exception {
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ null);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new GaussianBlur(/* sigma= */ 5f))
            .build();
    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();
    Bitmap expectedBitmap = readBitmap(GAUSSIAN_BLUR_PNG_ASSET_PATH);

    assertBitmapsAreSimilar(expectedBitmap, actualBitmap, PSNR_THRESHOLD);
  }

  @Test
  public void bitmapOverlay_matchesGoldenFile() throws Exception {
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ null);
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay)))
            .build();
    Bitmap expectedBitmap = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);
    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void bitmapOverlay_textureInput_matchesGoldenFile() throws Exception {
    assumeFormatsSupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ null);
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    ImmutableList<Effect> effects =
        ImmutableList.of(new OverlayEffect(ImmutableList.of(bitmapOverlay)));
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId, consumersBitmapReader, INPUT_SDR_MP4_ASSET_STRING, SDR_BT709_LIMITED, effects);
    Bitmap expectedBitmap = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  @Ignore("TODO: b/344529901 - Enable this test when fixed.")
  public void ultraHdrBitmapAndTextOverlay_hlg10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsUltraHdrEditing();
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    Bitmap inputBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    inputBitmap =
        Bitmap.createScaledBitmap(
            inputBitmap,
            inputBitmap.getWidth() / 8,
            inputBitmap.getHeight() / 8,
            /* filter= */ true);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(inputBitmap);
    SpannableString overlayText = new SpannableString("W R G B");
    overlayText.setSpan(
        new ForegroundColorSpan(Color.WHITE),
        /* start= */ 0,
        /* end= */ 1,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    overlayText.setSpan(
        new ForegroundColorSpan(Color.RED),
        /* start= */ 2,
        /* end= */ 3,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    overlayText.setSpan(
        new ForegroundColorSpan(Color.GREEN),
        /* start= */ 4,
        /* end= */ 5,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    overlayText.setSpan(
        new ForegroundColorSpan(Color.BLUE),
        /* start= */ 6,
        /* end= */ 7,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    TextOverlay textOverlay =
        TextOverlay.createStaticTextOverlay(
            overlayText, new StaticOverlaySettings.Builder().build());
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay, textOverlay)))
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_AND_TEXT_OVERLAY_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    assertBitmapsAreSimilar(expectedBitmap, actualBitmap, HDR_PSNR_THRESHOLD);
  }

  @Test
  public void ultraHdrBitmapOverlay_hlg10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsUltraHdrEditing();
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    Bitmap inputBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    inputBitmap =
        Bitmap.createScaledBitmap(
            inputBitmap,
            inputBitmap.getWidth() / 8,
            inputBitmap.getHeight() / 8,
            /* filter= */ true);
    Matrix matrix = new Matrix();
    matrix.postRotate(/* degrees= */ 90);
    Bitmap rotatedBitmap =
        Bitmap.createBitmap(
            inputBitmap,
            /* x= */ 0,
            /* y= */ 0,
            inputBitmap.getWidth(),
            inputBitmap.getHeight(),
            matrix,
            /* filter= */ true);
    BitmapOverlay bitmapOverlay1 = BitmapOverlay.createStaticBitmapOverlay(inputBitmap);
    BitmapOverlay bitmapOverlay2 = BitmapOverlay.createStaticBitmapOverlay(rotatedBitmap);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay1, bitmapOverlay2)))
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_OVERLAY_HLG_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    assertBitmapsAreSimilar(expectedBitmap, actualBitmap, HDR_PSNR_THRESHOLD);
  }

  @Test
  public void ultraHdrBitmapOverlay_hdr10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceSupportsUltraHdrEditing();
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    Bitmap overlayBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    overlayBitmap =
        Bitmap.createScaledBitmap(
            overlayBitmap,
            overlayBitmap.getWidth() / 8,
            overlayBitmap.getHeight() / 8,
            /* filter= */ true);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay)))
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_OVERLAY_PQ_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    assertBitmapsAreSimilar(expectedBitmap, actualBitmap, HDR_PSNR_THRESHOLD);
  }

  @Test
  public void textOverlay_hdr10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceSupportsUltraHdrEditing();
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    SpannableString overlayText = new SpannableString("W R G B");
    overlayText.setSpan(
        new ForegroundColorSpan(Color.WHITE),
        /* start= */ 0,
        /* end= */ 1,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    overlayText.setSpan(
        new ForegroundColorSpan(Color.RED),
        /* start= */ 2,
        /* end= */ 3,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    overlayText.setSpan(
        new ForegroundColorSpan(Color.GREEN),
        /* start= */ 4,
        /* end= */ 5,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    overlayText.setSpan(
        new ForegroundColorSpan(Color.BLUE),
        /* start= */ 6,
        /* end= */ 7,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    TextOverlay textOverlay =
        TextOverlay.createStaticTextOverlay(
            overlayText, new StaticOverlaySettings.Builder().setHdrLuminanceMultiplier(3f).build());
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(textOverlay)))
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(HDR_TEXT_OVERLAY_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    assertBitmapsAreSimilar(expectedBitmap, actualBitmap, HDR_PSNR_THRESHOLD);
  }

  @Test
  public void noEffects_hlg10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hlg10InputAndHdr10Output_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format inputFormat = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    Format outputFormat =
        inputFormat
            .buildUpon()
            .setColorInfo(
                new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .build())
            .build();
    assumeDeviceSupportsHdrEditing(testId, inputFormat);
    assumeFormatsSupported(context, testId, inputFormat, outputFormat);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputFormat.colorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(HLG_TO_PQ_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    assertBitmapsAreSimilar(expectedBitmap, actualBitmap, HDR_PSNR_THRESHOLD);
  }

  @Test
  public void noEffects_hlg10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_HLG10_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hlg10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_HLG_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10InputAndHlg10Output_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format inputFormat = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    Format outputFormat =
        inputFormat
            .buildUpon()
            .setColorInfo(
                new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .setColorTransfer(C.COLOR_TRANSFER_HLG)
                    .build())
            .build();
    assumeDeviceSupportsHdrEditing(testId, inputFormat);
    assumeFormatsSupported(context, testId, inputFormat, outputFormat);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputFormat.colorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(PQ_TO_HLG_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_PQ_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_PQ_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hlg10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hlg10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_HLG10_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of(NO_OP_EFFECT));
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hlg10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_HLG_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hdr10Input_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(colorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.processFirstFrameAndEnd();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hdr10TextureInput_matchesGoldenFile() throws Exception {
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10.videoFormat;
    assumeDeviceSupportsHdrEditing(testId, format);
    assumeFormatsSupported(context, testId, /* inputFormat= */ format, /* outputFormat= */ null);
    ColorInfo colorInfo = checkNotNull(format.colorInfo);
    TextureBitmapReader consumersBitmapReader = new TextureBitmapReader();
    VideoFrameProcessorTestRunner texIdProducingVideoFrameProcessorTestRunner =
        getTexIdProducingFrameProcessorTestRunner(
            testId,
            consumersBitmapReader,
            INPUT_PQ_MP4_ASSET_STRING,
            colorInfo,
            ImmutableList.of(NO_OP_EFFECT));
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    texIdProducingVideoFrameProcessorTestRunner.processFirstFrameAndEnd();
    texIdProducingVideoFrameProcessorTestRunner.release();
    Bitmap actualBitmap = consumersBitmapReader.getBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noOpEffect_hdr10UltraHDRImageInput_matchesGoldenFile() throws Exception {
    assumeDeviceSupportsHdrEditing(testId, MP4_ASSET_720P_4_SECOND_HDR10.videoFormat);
    assumeDeviceSupportsUltraHdrEditing();
    ColorInfo outputColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .setColorRange(C.COLOR_RANGE_FULL)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setOutputColorInfo(outputColorInfo)
            .setEffects(NO_OP_EFFECT)
            .build();
    Bitmap originalBitmap = readBitmap(ULTRA_HDR_ASSET_PATH);
    Bitmap expectedBitmap = readBitmap(ULTRA_HDR_TO_PQ_PNG_ASSET_PATH);

    videoFrameProcessorTestRunner.queueInputBitmap(
        originalBitmap,
        /* durationUs= */ C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0L,
        /* frameRate= */ 1);
    videoFrameProcessorTestRunner.endFrameProcessing();
    Bitmap actualBitmap = videoFrameProcessorTestRunner.getOutputBitmap();

    // TODO: b/207848601 - Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  private VideoFrameProcessorTestRunner getTexIdProducingFrameProcessorTestRunner(
      String testId,
      TextureBitmapReader consumersBitmapReader,
      String videoAssetPath,
      ColorInfo colorInfo,
      List<Effect> effects)
      throws VideoFrameProcessingException {
    TextureBitmapReader producersBitmapReader = new TextureBitmapReader();
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTextureProducer, outputTexture, presentationTimeUs, syncObject) -> {
                  try {
                    inputTextureIntoVideoFrameProcessor(
                        testId,
                        consumersBitmapReader,
                        colorInfo,
                        effects,
                        outputTexture,
                        outputTextureProducer,
                        presentationTimeUs,
                        syncObject);
                  } catch (Exception e) {
                    throw VideoFrameProcessingException.from(e, presentationTimeUs);
                  }
                },
                /* textureOutputCapacity= */ 1)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setVideoAssetPath(videoAssetPath)
        .setOutputColorInfo(colorInfo)
        .setBitmapReader(producersBitmapReader)
        .build();
  }

  private void inputTextureIntoVideoFrameProcessor(
      String testId,
      TextureBitmapReader bitmapReader,
      ColorInfo colorInfo,
      List<Effect> effects,
      GlTextureInfo texture,
      GlTextureProducer textureProducer,
      long presentationTimeUs,
      long syncObject)
      throws Exception {
    GlObjectsProvider contextSharingGlObjectsProvider =
        new DefaultGlObjectsProvider(GlUtil.getCurrentContext());
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTextureProducer, outputTexture, presentationTimeUs1, unusedSyncObject) -> {
                  bitmapReader.readBitmap(outputTexture, presentationTimeUs1);
                  outputTextureProducer.releaseOutputTexture(presentationTimeUs1);
                },
                /* textureOutputCapacity= */ 1)
            .setGlObjectsProvider(contextSharingGlObjectsProvider)
            .build();
    videoFrameProcessorTestRunner =
        new VideoFrameProcessorTestRunner.Builder()
            .setTestId(testId)
            .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
            .setOutputColorInfo(colorInfo)
            .setBitmapReader(bitmapReader)
            .setEffects(effects)
            .build();
    GlUtil.awaitSyncObject(syncObject);
    videoFrameProcessorTestRunner.queueInputTexture(texture, presentationTimeUs, colorInfo);
    videoFrameProcessorTestRunner.endFrameProcessing(VIDEO_FRAME_PROCESSING_WAIT_MS / 2);
    textureProducer.releaseOutputTexture(presentationTimeUs);
  }

  private static VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    TextureBitmapReader textureBitmapReader = new TextureBitmapReader();
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setTextureOutput(
                (outputTextureProducer, outputTexture, presentationTimeUs, unusedSyncObject) -> {
                  textureBitmapReader.readBitmap(outputTexture, presentationTimeUs);
                  outputTextureProducer.releaseOutputTexture(presentationTimeUs);
                },
                /* textureOutputCapacity= */ 1)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setVideoAssetPath(INPUT_SDR_MP4_ASSET_STRING)
        .setBitmapReader(textureBitmapReader);
  }

  private void assumeDeviceSupportsUltraHdrEditing() throws JSONException, IOException {
    if (Util.SDK_INT < 34) {
      recordTestSkipped(
          getApplicationContext(), testId, "Ultra HDR is not supported on this API level.");
      throw new AssumptionViolatedException("Ultra HDR is not supported on this API level.");
    }
  }
}
