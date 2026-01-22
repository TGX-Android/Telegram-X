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
package androidx.media3.transformer.mh;

import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ULTRA_HDR_ASSET;
import static androidx.media3.transformer.AndroidTestUtil.MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static androidx.media3.transformer.AndroidTestUtil.createFrameCountingEffect;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.AndroidTestUtil.ForceEncodeEncoderFactory;
import androidx.media3.transformer.AssetLoader;
import androidx.media3.transformer.Codec;
import androidx.media3.transformer.DefaultAssetLoaderFactory;
import androidx.media3.transformer.DefaultDecoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExperimentalAnalyzerModeFactory;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Checks transcoding speed. */
@RunWith(AndroidJUnit4.class)
public class TranscodeSpeedTest {
  private final Context context = ApplicationProvider.getApplicationContext();
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void export1920x1080_to1080p_completesWithAtLeast20Fps() throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat);
    Transformer transformer =
        new Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(new ForceEncodeEncoderFactory(context))
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS.uri))
            .buildUpon()
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(45_000).build())
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    assertThat(result.throughputFps).isAtLeast(20);
  }

  @Test
  public void exportImage_to720p_completesWithHighThroughput() throws Exception {
    Format outputFormat =
        new Format.Builder()
            .setSampleMimeType(VIDEO_H264)
            .setFrameRate(30.00f)
            .setCodecs("avc1.42C028")
            .setWidth(1280)
            .setHeight(720)
            .build();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat,
        outputFormat);
    Transformer transformer =
        new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).build();
    boolean isHighPerformance =
        Ascii.toLowerCase(Build.MODEL).contains("pixel")
            && (Ascii.toLowerCase(Build.MODEL).contains("6")
                || Ascii.toLowerCase(Build.MODEL).contains("7")
                || Ascii.toLowerCase(Build.MODEL).contains("8")
                || Ascii.toLowerCase(Build.MODEL).contains("fold")
                || Ascii.toLowerCase(Build.MODEL).contains("tablet"));
    if (Util.SDK_INT == 33 && Ascii.toLowerCase(Build.MODEL).contains("pixel 6")) {
      // Pixel 6 is usually quick, unless it's on API 33. See b/358519058.
      isHighPerformance = false;
    }
    // This test uses ULTRA_HDR_URI_STRING because it's high resolution.
    // Ultra HDR gainmap is ignored.
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(JPG_ULTRA_HDR_ASSET.uri)
                    .setImageDurationMs(isHighPerformance ? 45_000 : 15_000)
                    .build())
            .setFrameRate(30)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    /* videoEffects= */ ImmutableList.of(
                        Presentation.createForWidthAndHeight(
                            720, 1280, Presentation.LAYOUT_SCALE_TO_FIT))))
            .build();

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, editedMediaItem);

    // This test depends on device GPU performance. Sampling high-resolution textures
    // is expensive. If an extra shader program runs on each frame, devices with slow GPU
    // such as moto e5 play will drop to 5 fps.
    // Devices with a fast GPU and encoder will drop under 300 fps.
    assertThat(result.throughputFps).isAtLeast(isHighPerformance ? 400 : 20);
  }

  @Test
  public void
      analyzeVideo_onHighPerformanceDevice_withConfiguredOperatingRate_completesWithHighThroughput()
          throws Exception {
    assumeTrue(
        Ascii.toLowerCase(Build.MODEL).contains("pixel")
            && (Ascii.toLowerCase(Build.MODEL).contains("6")
                || Ascii.toLowerCase(Build.MODEL).contains("7")
                || Ascii.toLowerCase(Build.MODEL).contains("8")
                || Ascii.toLowerCase(Build.MODEL).contains("fold")
                || Ascii.toLowerCase(Build.MODEL).contains("tablet")));
    // Pixel 6 is usually quick, unless it's on API 33. See b/358519058.
    assumeFalse(Util.SDK_INT == 33 && Ascii.toLowerCase(Build.MODEL).contains("pixel 6"));
    AtomicInteger videoFramesSeen = new AtomicInteger(/* initialValue= */ 0);
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS.uri))
            .buildUpon()
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(45_000L).build())
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    ImmutableList.of(createFrameCountingEffect(videoFramesSeen))))
            .build();

    ExportTestResult result = analyzeVideoWithConfiguredOperatingRate(testId, editedMediaItem);
    int expectedFrameCount = 1350;
    checkState(videoFramesSeen.get() == expectedFrameCount);

    float throughputFps = 1000f * videoFramesSeen.get() / result.elapsedTimeMs;
    assertThat(throughputFps).isAtLeast(330);
  }

  @Test
  public void analyzeVideo_withConfiguredOperatingRate_completesWithCorrectNumberOfFrames()
      throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ null);
    AtomicInteger videoFramesSeen = new AtomicInteger(/* initialValue= */ 0);
    MediaItem mediaItem =
        MediaItem.fromUri(Uri.parse(MP4_LONG_ASSET_WITH_INCREASING_TIMESTAMPS.uri))
            .buildUpon()
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(15_000L).build())
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(
                new Effects(
                    /* audioProcessors= */ ImmutableList.of(),
                    ImmutableList.of(createFrameCountingEffect(videoFramesSeen))))
            .build();

    analyzeVideoWithConfiguredOperatingRate(testId, editedMediaItem);
    int expectedFrameCount = 450;

    assertThat(videoFramesSeen.get()).isEqualTo(expectedFrameCount);
  }

  private static ExportTestResult analyzeVideoWithConfiguredOperatingRate(
      String testId, EditedMediaItem editedMediaItem) throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Codec.DecoderFactory decoderFactory =
        new DefaultDecoderFactory.Builder(context).setShouldConfigureOperatingRate(true).build();
    AssetLoader.Factory assetLoaderFactory =
        new DefaultAssetLoaderFactory(context, decoderFactory, Clock.DEFAULT);
    Transformer transformer =
        ExperimentalAnalyzerModeFactory.buildAnalyzer(context)
            .buildUpon()
            .setAssetLoaderFactory(assetLoaderFactory)
            .build();

    return new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(testId, editedMediaItem);
  }
}
