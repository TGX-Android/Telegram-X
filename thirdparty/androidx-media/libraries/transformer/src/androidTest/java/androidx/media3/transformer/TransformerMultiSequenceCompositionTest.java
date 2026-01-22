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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.effect.AlphaScale;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests for using multiple {@link EditedMediaItemSequence} in a composition. */
@RunWith(Parameterized.class)
public final class TransformerMultiSequenceCompositionTest {

  // Bitmaps are generated on a Pixel 6 or 7 Pro instead of an emulator, due to an emulator bug.
  // TODO: b/301242589 - Fix this test on the crow emulator, and re-generate bitmaps using the crow
  //  emulator, for consistency with other pixel tests.
  private static final String PNG_ASSET_BASE_PATH =
      "test-generated-goldens/transformer_multi_sequence_composition_test";

  // The duration of one frame of the 30 FPS test video, in milliseconds.
  private static final long ONE_FRAME_DURATION_MS = 35;
  private static final int EXPORT_WIDTH = 360;
  private static final int EXPORT_HEIGHT = 240;

  @Parameters(name = "{0},maxFramesInEncoder={1}")
  public static ImmutableList<Object[]> parameters() {
    ImmutableList.Builder<Object[]> listBuilder = new ImmutableList.Builder<>();
    for (Boolean workingColorSpaceLinear : new boolean[] {false, true}) {
      for (Integer maxFramesInEncoder : new int[] {C.INDEX_UNSET, 1, 16}) {
        listBuilder.add(new Object[] {workingColorSpaceLinear, maxFramesInEncoder});
      }
    }
    return listBuilder.build();
  }

  private final Context context = ApplicationProvider.getApplicationContext();
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Parameter(0)
  public boolean workingColorSpaceLinear;

  @Parameter(1)
  public int maxFramesInEncoder;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void export_withTwoSequencesEachWithOneVideoMediaItem_succeeds() throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);

    Composition composition =
        createComposition(
            /* compositionEffects= */ ImmutableList.of(
                new Contrast(0.1f),
                Presentation.createForWidthAndHeight(
                    EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)),
            /* firstSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET.uri,
                    /* effects= */ ImmutableList.of(
                        new AlphaScale(0.5f),
                        new ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(180)
                            .build()))),
            /* secondSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(MP4_ASSET.uri, /* effects= */ ImmutableList.of())),
            VideoCompositorSettings.DEFAULT);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, buildTransformer())
            .build()
            .run(testId, composition);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withTwoSequencesOneWithVideoOneWithImage_succeeds() throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);

    Composition composition =
        createComposition(
            /* compositionEffects= */ ImmutableList.of(
                new Contrast(0.1f),
                Presentation.createForWidthAndHeight(
                    EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)),
            /* firstSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET.uri,
                    /* effects= */ ImmutableList.of(
                        new AlphaScale(0.5f),
                        new ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(180)
                            .build()))),
            /* secondSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemOfOneFrameImage(JPG_ASSET.uri, /* effects= */ ImmutableList.of())),
            VideoCompositorSettings.DEFAULT);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, buildTransformer())
            .build()
            .run(testId, composition);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withTwoSequencesWithVideoCompositorSettings_succeeds() throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);

    VideoCompositorSettings pictureInPictureVideoCompositorSettings =
        new VideoCompositorSettings() {
          @Override
          public Size getOutputSize(List<Size> inputSizes) {
            return inputSizes.get(0);
          }

          @Override
          public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
            if (inputId == 0) {
              // This tests all OverlaySettings builder variables.
              return new StaticOverlaySettings.Builder()
                  .setScale(.25f, .25f)
                  .setOverlayFrameAnchor(1, -1)
                  .setBackgroundFrameAnchor(.9f, -.7f)
                  .build();
            } else {
              return new StaticOverlaySettings.Builder().build();
            }
          }
        };

    Composition composition =
        createComposition(
            /* compositionEffects= */ ImmutableList.of(
                new Contrast(0.1f),
                Presentation.createForWidthAndHeight(
                    EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)),
            /* firstSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET.uri,
                    /* effects= */ ImmutableList.of(
                        new AlphaScale(0.5f),
                        new ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(180)
                            .build()))),
            /* secondSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(MP4_ASSET.uri, /* effects= */ ImmutableList.of())),
            pictureInPictureVideoCompositorSettings);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, buildTransformer())
            .build()
            .run(testId, composition);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_completesWithConsistentFrameCount() throws Exception {
    assumeFalse(
        "Skipped due to failing video decoder on API 31 emulator",
        isRunningOnEmulator() && Util.SDK_INT == 31);
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET.videoFormat,
        /* outputFormat= */ MP4_ASSET.videoFormat);
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET.uri));
    ImmutableList<Effect> videoEffects = ImmutableList.of(Presentation.createForHeight(480));
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem).build(),
                new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, buildTransformer())
            .build()
            .run(testId, composition);

    assertThat(result.exportResult.videoFrameCount).isEqualTo(MP4_ASSET.videoFrameCount);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  private Transformer buildTransformer() {
    // Use linear color space for grayscale effects.
    Transformer.Builder builder = new Transformer.Builder(context);
    if (workingColorSpaceLinear) {
      builder.setVideoFrameProcessorFactory(
          new DefaultVideoFrameProcessor.Factory.Builder()
              .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_LINEAR)
              .build());
    }
    builder.experimentalSetMaxFramesInEncoder(maxFramesInEncoder);
    return builder.build();
  }

  private static EditedMediaItem editedMediaItemByClippingVideo(String uri, List<Effect> effects) {
    return new EditedMediaItem.Builder(
            MediaItem.fromUri(uri)
                .buildUpon()
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(ONE_FRAME_DURATION_MS)
                        .build())
                .build())
        .setRemoveAudio(true)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  private static EditedMediaItem editedMediaItemOfOneFrameImage(String uri, List<Effect> effects) {
    return new EditedMediaItem.Builder(
            new MediaItem.Builder().setUri(uri).setImageDurationMs(ONE_FRAME_DURATION_MS).build())
        .setRemoveAudio(true)
        .setFrameRate((int) (1000 / ONE_FRAME_DURATION_MS))
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  private static Composition createComposition(
      List<Effect> compositionEffects,
      List<EditedMediaItem> firstSequenceMediaItems,
      List<EditedMediaItem> secondSequenceMediaItems,
      VideoCompositorSettings videoCompositorSettings) {

    return new Composition.Builder(
            ImmutableList.of(
                new EditedMediaItemSequence.Builder(firstSequenceMediaItems).build(),
                new EditedMediaItemSequence.Builder(secondSequenceMediaItems).build()))
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(), /* videoEffects= */ compositionEffects))
        .setVideoCompositorSettings(videoCompositorSettings)
        .build();
  }

  private static void assertBitmapsMatchExpected(List<Bitmap> actualBitmaps, String testId)
      throws IOException {
    for (int i = 0; i < actualBitmaps.size(); i++) {
      Bitmap actualBitmap = actualBitmaps.get(i);
      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ String.valueOf(i), actualBitmap, /* path= */ null);
      String subTestId = testId.replaceAll(",maxFramesInEncoder=-?\\d+", "") + "_" + i;
      Bitmap expectedBitmap =
          readBitmap(Util.formatInvariant("%s/%s.png", PNG_ASSET_BASE_PATH, subTestId));
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, subTestId);
      assertWithMessage("For expected bitmap %s.png", subTestId)
          .that(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA);
    }
  }
}
