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

import static androidx.media3.transformer.AndroidTestUtil.MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS;
import static androidx.media3.transformer.AndroidTestUtil.assumeFormatsSupported;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.util.Clock;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.AssetLoader;
import androidx.media3.transformer.Codec;
import androidx.media3.transformer.DefaultAssetLoaderFactory;
import androidx.media3.transformer.DefaultDecoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.SurfaceTestActivity;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Checks transcoding speed when running in foreground. */
@RunWith(AndroidJUnit4.class)
public class TranscodeForegroundSpeedTest {
  private final Context context = ApplicationProvider.getApplicationContext();
  @Rule public final TestName testName = new TestName();

  // Creating a SurfaceTestActivity rule turns the screen on and puts the test app in foreground.
  // This affects transcoding performance as foreground apps are more likely to schedule on the
  // faster CPU cores.
  @Rule
  public ActivityScenarioRule<SurfaceTestActivity> rule =
      new ActivityScenarioRule<>(SurfaceTestActivity.class);

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void
      export1080pWithAudioTo720p_onMediumPerformanceDeviceWithDynamicScheduling_completesWithAtLeast140Fps()
          throws Exception {
    assumeTrue(
        Ascii.toLowerCase(Build.MODEL).contains("pixel 2")
            || Ascii.toLowerCase(Build.MODEL).contains("dn2103")
            || Ascii.toLowerCase(Build.MODEL).contains("sm-g960f")
            || Ascii.toLowerCase(Build.MODEL).contains("g8441"));
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.videoFormat);

    ExportTestResult exportTestResult =
        exportVideoAndAudioTo720pWithDynamicScheduling(
            testId,
            Uri.parse(MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.uri),
            /* durationMs= */ 30_000);

    // Running this without dynamic scheduling runs at under 80 fps.
    assertThat(exportTestResult.throughputFps).isAtLeast(140);
  }

  @Test
  public void
      export1080pWithAudioTo720p_onLowerPerformanceDevicesWithDynamicScheduling_completesWithAtLeast60Fps()
          throws Exception {
    assumeTrue(
        (Ascii.toLowerCase(Build.MODEL).contains("f-01l")
            || Ascii.toLowerCase(Build.MODEL).contains("asus_x00td")
            || Ascii.toLowerCase(Build.MODEL).contains("redmi note 5")
            || Ascii.toLowerCase(Build.MODEL).contains("mha-l29")
            || Ascii.toLowerCase(Build.MODEL).contains("oneplus a6013")
            || Ascii.toLowerCase(Build.MODEL).contains("cph1803")
            || Ascii.toLowerCase(Build.MODEL).contains("mi a2 lite")));
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.videoFormat);

    ExportTestResult exportTestResult =
        exportVideoAndAudioTo720pWithDynamicScheduling(
            testId,
            Uri.parse(MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.uri),
            /* durationMs= */ 15_000);

    // Running this without dynamic scheduling runs at under 40 fps.
    assertThat(exportTestResult.throughputFps).isAtLeast(60);
  }

  @Test
  public void export1080pWithAudioTo720p_withDynamicScheduling_completesWithCorrectNumberOfFrames()
      throws Exception {
    assumeFormatsSupported(
        context,
        testId,
        /* inputFormat= */ MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.videoFormat,
        /* outputFormat= */ MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.videoFormat);

    ExportTestResult exportTestResult =
        exportVideoAndAudioTo720pWithDynamicScheduling(
            testId,
            Uri.parse(MP4_LONG_ASSET_WITH_AUDIO_AND_INCREASING_TIMESTAMPS.uri),
            /* durationMs= */ 5_000);

    assertThat(exportTestResult.exportResult.videoFrameCount).isEqualTo(150);
  }

  private static ExportTestResult exportVideoAndAudioTo720pWithDynamicScheduling(
      String testId, Uri mediaUri, long durationMs) throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Codec.DecoderFactory decoderFactory =
        new DefaultDecoderFactory.Builder(context)
            .experimentalSetDynamicSchedulingEnabled(true)
            .setShouldConfigureOperatingRate(true)
            .build();
    AssetLoader.Factory assetLoaderFactory =
        new DefaultAssetLoaderFactory(context, decoderFactory, Clock.DEFAULT);
    Transformer transformer =
        new Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(new AndroidTestUtil.ForceEncodeEncoderFactory(context))
            .setAssetLoaderFactory(assetLoaderFactory)
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(mediaUri)
            .buildUpon()
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(durationMs).build())
            .build();
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(44_100);
    ChannelMixingAudioProcessor mixingAudioProcessor = new ChannelMixingAudioProcessor();
    mixingAudioProcessor.putChannelMixingMatrix(
        ChannelMixingMatrix.create(/* inputChannelCount= */ 2, /* outputChannelCount= */ 1));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(
                new Effects(
                    ImmutableList.of(sonicAudioProcessor, mixingAudioProcessor),
                    ImmutableList.of(
                        Presentation.createForWidthAndHeight(
                            1280, 720, Presentation.LAYOUT_SCALE_TO_FIT))))
            .build();

    return new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(testId, editedMediaItem);
  }
}
