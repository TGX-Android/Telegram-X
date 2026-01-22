/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.transformer.mh;

import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel41;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.MediaFormatUtil.createFormatFromMediaFormat;
import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getCodecProfileAndLevel;
import static androidx.media3.transformer.AndroidTestUtil.FORCE_TRANSCODE_VIDEO_EFFECTS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_4K60_PORTRAIT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_8K24;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_BT2020_SDR;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_SEF;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_SEF_H265;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S;
import static androidx.media3.transformer.AndroidTestUtil.MP4_TRIM_OPTIMIZATION_PIXEL;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_SUCCEEDED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.exoplayer.MediaExtractorCompat;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.transformer.AndroidTestUtil.ForceEncodeEncoderFactory;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** {@link Transformer} instrumentation tests. */
@RunWith(AndroidJUnit4.class)
public class ExportTest {
  private static final String TAG = "ExportTest";
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = TAG + "_" + testName.getMethodName();
  }

  @Test
  public void export() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    // Note: throughout this class we only check decoding capability as tests should still run if
    // Transformer is able to succeed by falling back to a lower resolution.
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS.uri));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportWithoutDecodeEncode() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS.uri));
    // No need to calculate SSIM because no decode/encoding, so input frames match output frames.

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportToSpecificBitrate() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new ForceEncodeEncoderFactory(
                    /* wrappedEncoderFactory= */ new DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            new VideoEncoderSettings.Builder().setBitrate(5_000_000).build())
                        .build()))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void export4K60() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_4K60_PORTRAIT.videoFormat,
        /* outputFormat= */ null);
    // Reference: b/262710361
    assumeFalse(
        "Skip due to over-reported encoder capabilities",
        Util.SDK_INT == 29 && Ascii.equalsIgnoreCase(Build.MODEL, "pixel 3"));
    // Reference: b/347635026
    assumeFalse(
        "Skip due to decoder failing to queue input frames",
        Util.SDK_INT == 29 && Ascii.equalsIgnoreCase(Build.MODEL, "pixel 3a"));
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_4K60_PORTRAIT.uri));
    boolean skipCalculateSsim = Util.SDK_INT < 30 && Build.DEVICE.equals("joyeuse");

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(!skipCalculateSsim)
            .setTimeoutSeconds(180)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void export8K24() throws Exception {
    // Reference: b/244711282#comment5
    assumeFalse(
        "Some devices are capable of instantiating only either one 8K decoder or one 8K encoder",
        Ascii.equalsIgnoreCase(Build.MODEL, "tb-q706")
            || Ascii.equalsIgnoreCase(Build.MODEL, "sm-f916u1")
            || Ascii.equalsIgnoreCase(Build.MODEL, "sm-g981u1")
            || Ascii.equalsIgnoreCase(Build.MODEL, "le2121")
            || Ascii.equalsIgnoreCase(Build.MODEL, "seahawk"));
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context, testId, /* inputFormat= */ MP4_ASSET_8K24.videoFormat, /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_8K24.uri));
    // TODO: b/281824052 - have requestCalculateSsim always be true after linked bug is fixed.
    boolean requestCalculateSsim = !Build.MODEL.equals("SM-G991B");

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(requestCalculateSsim)
            .setTimeoutSeconds(120)
            .build()
            .run(testId, mediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void export8K24_withDownscaling() throws Exception {
    // This test is to cover devices that are able to either decode or encode 8K, but not transcode.
    int downscaledWidth = 320;
    int downscaledHeight = 240;
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_8K24.videoFormat,
        /* outputFormat= */ new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(downscaledWidth)
            .setHeight(downscaledHeight)
            .build());

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .setTimeoutSeconds(120)
            .build()
            .run(
                testId,
                new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_8K24.uri)))
                    .setEffects(
                        new Effects(
                            /* audioProcessors= */ ImmutableList.of(),
                            /* videoEffects= */ ImmutableList.of(
                                Presentation.createForWidthAndHeight(
                                    downscaledWidth,
                                    downscaledHeight,
                                    Presentation.LAYOUT_SCALE_TO_FIT))))
                    .build());

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportNoAudio() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ null);
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .setRequestCalculateSsim(true)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportNoVideo() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET.uri)))
            .setRemoveVideo(true)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportSef() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    if (SDK_INT < 25) {
      // TODO: b/210593256 - Remove test skipping after using an in-app muxer that supports B-frames
      //  before API 25.
      recordTestSkipped(context, testId, /* reason= */ "API version lacks muxing support");
      return;
    }
    Transformer transformer = new Transformer.Builder(context).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_SEF.uri)))
            .setFlattenForSlowMotion(true)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.exportResult.durationMs).isGreaterThan(800);
    assertThat(result.exportResult.durationMs).isLessThan(950);
    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportSefH265() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    if (SDK_INT < 25) {
      // TODO: b/210593256 - Remove test skipping after using an in-app muxer that supports B-frames
      //  before API 25.
      recordTestSkipped(context, testId, /* reason= */ "API version lacks muxing support");
      return;
    }
    Transformer transformer = new Transformer.Builder(context).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(MP4_ASSET_SEF_H265.uri)))
            .setFlattenForSlowMotion(true)
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportFrameRotation() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ null);
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_WITH_INCREASING_TIMESTAMPS.uri));
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build());
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void exportTranscodeBt2020Sdr() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    // Reference: b/262732842#comment51
    if (SDK_INT <= 27 && Build.MANUFACTURER.equals("samsung")) {
      String reason = "Some older Samsung encoders report a non-specified error code";
      recordTestSkipped(context, testId, reason);
      throw new AssumptionViolatedException(reason);
    }
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_BT2020_SDR.videoFormat,
        /* outputFormat= */ null);
    // Reference: b/391362064
    assumeFalse(
        "Skip due to over-reported decoder capabilities",
        SDK_INT == 33 && Ascii.equalsIgnoreCase(Build.MODEL, "sm-a325f"));
    Transformer transformer = new Transformer.Builder(context).build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_BT2020_SDR.uri));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(FORCE_TRANSCODE_VIDEO_EFFECTS).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(new File(result.filePath).length()).isGreaterThan(0);
  }

  @Test
  public void clippedMedia_trimOptimizationEnabled_pixel7Pro_completesWithOptimizationApplied()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    // Devices with Tensor G2 & G3 chipsets should work, but Pixel 7a is flaky.
    assumeTrue(
        Ascii.toLowerCase(Build.MODEL).contains("pixel")
            && (Ascii.toLowerCase(Build.MODEL).contains("7")
                || Ascii.toLowerCase(Build.MODEL).contains("8")
                || Ascii.toLowerCase(Build.MODEL).contains("fold")
                || Ascii.toLowerCase(Build.MODEL).contains("tablet")));
    assumeFalse(Ascii.toLowerCase(Build.MODEL).contains("7a"));
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_TRIM_OPTIMIZATION_PIXEL.uri)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(1200)
                    .build())
            .build();
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    Mp4Extractor mp4Extractor = new Mp4Extractor(new DefaultSubtitleParserFactory());
    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(mp4Extractor, result.filePath);
    FakeTrackOutput videoTrack = fakeExtractorOutput.trackOutputs.get(0);
    byte[] sps = videoTrack.lastFormat.initializationData.get(0);
    // Skip 7 bytes: NAL unit start code (4) and NAL unit type, profile, and reserved fields.
    int spsLevelIndex = 7;
    assertThat(result.exportResult.optimizationResult).isEqualTo(OPTIMIZATION_SUCCEEDED);
    assertThat(result.exportResult.durationMs).isAtMost(700);
    assertThat(result.exportResult.videoConversionProcess)
        .isEqualTo(CONVERSION_PROCESS_TRANSMUXED_AND_TRANSCODED);
    int inputVideoLevel = 41;
    assertThat((int) sps[spsLevelIndex]).isAtLeast(inputVideoLevel);
  }

  @Test
  public void export_setEncodingProfileLevel_changesProfileAndLevel() throws Exception {
    assumeTrue(
        "Android encoding guidelines recommend H.264 baseline profile prior to API 25",
        Util.SDK_INT >= 25);
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new ForceEncodeEncoderFactory(
                    new DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            new VideoEncoderSettings.Builder()
                                .setEncodingProfileLevel(AVCProfileHigh, AVCLevel41)
                                .build())
                        .build()))
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(MP4_ASSET_WITH_INCREASING_TIMESTAMPS_320W_240H_15S.uri)
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    MediaExtractorCompat mediaExtractor = new MediaExtractorCompat(context);
    mediaExtractor.setDataSource(Uri.parse(result.filePath), 0);
    checkState(mediaExtractor.getTrackCount() == 1);
    MediaFormat mediaFormat = mediaExtractor.getTrackFormat(0);
    Format format = createFormatFromMediaFormat(mediaFormat);
    Pair<Integer, Integer> profileAndLevel = getCodecProfileAndLevel(format);
    assertThat(profileAndLevel.first).isAtMost(AVCProfileHigh);
    assertThat(profileAndLevel.second).isAtMost(AVCLevel41);
  }
}
