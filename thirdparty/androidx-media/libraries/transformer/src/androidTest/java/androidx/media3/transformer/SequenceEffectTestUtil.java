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
 *
 */
package androidx.media3.transformer;

import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.test.utils.TestUtil.assertBitmapsAreSimilar;
import static androidx.media3.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.effect.Presentation;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

/** Utility class for checking testing {@link EditedMediaItemSequence} instances. */
public final class SequenceEffectTestUtil {
  public static final ImmutableList<Effect> NO_EFFECT = ImmutableList.of();
  public static final long SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS = 50;

  /**
   * Luma PSNR values between 30 and 50 are considered good for lossy compression (See <a
   * href="https://en.wikipedia.org/wiki/Peak_signal-to-noise_ratio#Quality_estimation_with_PSNR">Quality
   * estimation with PSNR</a> ). Other than that, the values in this files are pretty arbitrary -- 1
   * more and tests start failing on some devices.
   */
  public static final float PSNR_THRESHOLD = 35f;

  public static final float PSNR_THRESHOLD_HD = 41f;
  private static final String PNG_ASSET_BASE_PATH =
      "test-generated-goldens/transformer_sequence_effect_test";

  private SequenceEffectTestUtil() {}

  /**
   * Creates a {@link Composition} with the specified {@link Presentation} and {@link
   * EditedMediaItem} instances.
   */
  public static Composition createComposition(
      @Nullable Presentation presentation,
      EditedMediaItem editedMediaItem,
      EditedMediaItem... editedMediaItems) {
    Composition.Builder builder =
        new Composition.Builder(
            new EditedMediaItemSequence.Builder(editedMediaItem)
                .addItems(editedMediaItems)
                .build());
    if (presentation != null) {
      builder.setEffects(
          new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.of(presentation)));
    }
    return builder.build();
  }

  /**
   * Creates an {@link EditedMediaItem} with a video at {@code uri} clipped to the {@code
   * endPositionMs}, with {@code effects} applied.
   *
   * <p>This may be used to, for example, clip to only the first frame of a video.
   */
  public static EditedMediaItem clippedVideo(String uri, List<Effect> effects, long endPositionMs) {
    return new EditedMediaItem.Builder(
            MediaItem.fromUri(uri)
                .buildUpon()
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(endPositionMs)
                        .build())
                .build())
        .setRemoveAudio(true)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  /**
   * Creates an {@link EditedMediaItem} with an image at {@code uri}, shown once, with {@code
   * effects} applied.
   */
  public static EditedMediaItem oneFrameFromImage(String uri, List<Effect> effects) {
    // 50ms for a 20-fps video is one frame.
    return new EditedMediaItem.Builder(
            new MediaItem.Builder().setUri(uri).setImageDurationMs(50).build())
        .setFrameRate(20)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  /**
   * Assert that the bitmaps output in {@link #PNG_ASSET_BASE_PATH} match those written in {code
   * actualBitmaps}.
   *
   * <p>Also saves {@code actualBitmaps} bitmaps, in case they differ from expected bitmaps, stored
   * at {@link #PNG_ASSET_BASE_PATH}/{@code testId}_id.png.
   */
  public static void assertBitmapsMatchExpectedAndSave(List<Bitmap> actualBitmaps, String testId)
      throws IOException {
    for (int i = 0; i < actualBitmaps.size(); i++) {
      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ String.valueOf(i), actualBitmaps.get(i), /* path= */ null);
    }

    for (int i = 0; i < actualBitmaps.size(); i++) {
      String subTestId = testId + "_" + i;
      String expectedPath = Util.formatInvariant("%s/%s.png", PNG_ASSET_BASE_PATH, subTestId);
      Bitmap expectedBitmap = readBitmap(expectedPath);

      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(
              expectedBitmap, actualBitmaps.get(i), subTestId);
      assertWithMessage("For expected bitmap " + expectedPath)
          .that(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA);
    }
  }

  /**
   * Asserts that the first {@code frameCount} frames extracted from the video in {@code filePath}
   * match the expected output in {@link #PNG_ASSET_BASE_PATH}/{@code testId}_num.png.
   *
   * <p>Also saves the first frame as a bitmap, in case they differ from expected.
   */
  public static void assertFramesMatchExpectedPsnrAndSave(
      Context context, String testId, String filePath, float psnrThreshold, int frameCount)
      throws IOException, InterruptedException {
    ImmutableList<Bitmap> frames =
        extractBitmapsFromVideo(context, filePath).subList(0, frameCount);
    assertBitmapsMatchExpectedPsnrAndSave(frames, testId, psnrThreshold);
  }

  private static void assertBitmapsMatchExpectedPsnrAndSave(
      List<Bitmap> actualBitmaps, String testId, float psnrThreshold) throws IOException {
    for (int i = 0; i < actualBitmaps.size(); i++) {
      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ String.valueOf(i), actualBitmaps.get(i), /* path= */ null);
    }

    for (int i = 0; i < actualBitmaps.size(); i++) {
      String subTestId = testId + "_" + i;
      String expectedPath = Util.formatInvariant("%s/%s.png", PNG_ASSET_BASE_PATH, subTestId);
      Bitmap expectedBitmap = readBitmap(expectedPath);

      assertBitmapsAreSimilar(expectedBitmap, actualBitmaps.get(i), psnrThreshold);
    }
  }

  /**
   * Returns whether the MediaCodecInfo decoder is known to produce incorrect colours on this
   * device.
   *
   * <p>Washed out colours are probably caused by incorrect color space assumptions by MediaCodec.
   */
  public static boolean decoderProducesWashedOutColours(MediaCodecInfo mediaCodecInfo) {
    return mediaCodecInfo.name.equals("OMX.google.h264.decoder")
        && (Build.MODEL.equals("ANE-LX1")
            || Build.MODEL.equals("MHA-L29")
            || Build.MODEL.equals("COR-L29"));
  }

  /**
   * Tries to export the {@link Composition} with a high quality {@link Transformer} created via
   * {@link #createHqTransformer} with the requested {@code decoderMediaCodecInfo}.
   *
   * @return The {@link ExportTestResult} when successful, or {@code null} if decoding fails.
   * @throws Exception The cause of the export not completing.
   */
  @Nullable
  public static ExportTestResult tryToExportCompositionWithDecoder(
      String testId, Context context, MediaCodecInfo decoderMediaCodecInfo, Composition composition)
      throws Exception {
    try {
      return new TransformerAndroidTestRunner.Builder(
              context, createHqTransformer(context, decoderMediaCodecInfo))
          .build()
          .run(testId, composition);
    } catch (ExportException exportException) {
      if (exportException.errorCode == ExportException.ERROR_CODE_DECODING_FAILED
          || exportException.errorCode == ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
          || exportException.errorCode == ExportException.ERROR_CODE_DECODER_INIT_FAILED) {
        return null;
      }
      throw exportException;
    }
  }

  /**
   * Creates a high quality {@link Transformer} instance.
   *
   * <p>The {@link Transformer} is configured to select a specific decoder and a large value for
   * {@link VideoEncoderSettings#bitrate}.
   */
  public static Transformer createHqTransformer(
      Context context, MediaCodecInfo decoderMediaCodecInfo) {
    Codec.DecoderFactory decoderFactory =
        new DefaultDecoderFactory.Builder(context)
            .setMediaCodecSelector(
                (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) ->
                    ImmutableList.of(decoderMediaCodecInfo))
            .build();
    AssetLoader.Factory assetLoaderFactory =
        new DefaultAssetLoaderFactory(context, decoderFactory, Clock.DEFAULT);
    Codec.EncoderFactory encoderFactory =
        new DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                new VideoEncoderSettings.Builder().setBitrate(30_000_000).build())
            .build();
    return new Transformer.Builder(context)
        .setAssetLoaderFactory(assetLoaderFactory)
        .setEncoderFactory(new AndroidTestUtil.ForceEncodeEncoderFactory(encoderFactory))
        .build();
  }
}
