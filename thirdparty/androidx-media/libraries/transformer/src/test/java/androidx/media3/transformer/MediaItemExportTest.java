/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.test.utils.robolectric.RobolectricUtil.runLooperUntil;
import static androidx.media3.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_DECODED;
import static androidx.media3.transformer.AssetLoader.SUPPORTED_OUTPUT_TYPE_ENCODED;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_NA;
import static androidx.media3.transformer.ExportResult.CONVERSION_PROCESS_TRANSMUXED;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_FAILED_EXTRACTION_FAILED;
import static androidx.media3.transformer.ExportResult.OPTIMIZATION_NONE;
import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_AMR_NB;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_AMR_WB;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_ELST_SKIP_500MS;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S;
import static androidx.media3.transformer.TestUtil.FILE_UNKNOWN_DURATION;
import static androidx.media3.transformer.TestUtil.FILE_VIDEO_ELST_TRIM_IDR_DURATION;
import static androidx.media3.transformer.TestUtil.FILE_VIDEO_ONLY;
import static androidx.media3.transformer.TestUtil.FILE_WITH_SEF_SLOW_MOTION;
import static androidx.media3.transformer.TestUtil.FILE_WITH_SUBTITLES;
import static androidx.media3.transformer.TestUtil.addAudioDecoders;
import static androidx.media3.transformer.TestUtil.addAudioEncoders;
import static androidx.media3.transformer.TestUtil.createAudioEffects;
import static androidx.media3.transformer.TestUtil.createPitchChangingAudioProcessor;
import static androidx.media3.transformer.TestUtil.getDumpFileName;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.audio.ToInt16PcmAudioProcessor;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaCodec;

/**
 * End-to-end test for exporting a single {@link MediaItem} or {@link EditedMediaItem} with {@link
 * Transformer}.
 *
 * <p>See {@link ParameterizedItemExportTest} for parameterized cases.
 */
@RunWith(AndroidJUnit4.class)
public final class MediaItemExportTest {

  private static final long TEST_TIMEOUT_SECONDS = 10;

  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();

  @Before
  public void setUp() {
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    addAudioEncoders(MimeTypes.AUDIO_AAC);
  }

  @After
  public void tearDown() {
    removeEncodersAndDecoders();
  }

  @Test
  public void start_gapOnlyExport_outputsSilence() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();

    EditedMediaItemSequence gapSequence =
        new EditedMediaItemSequence.Builder().addGap(500_000).build();

    transformer.start(new Composition.Builder(gapSequence).build(), outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // TODO: b/355201372 - Assert 500ms duration.
    assertThat(result.durationMs).isAtLeast(487);
    assertThat(result.durationMs).isAtMost(500);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName("gap", "500ms"));
  }

  @Test
  public void start_audioAndVideoPassthrough_withClippingStartAtKeyFrame_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(12_500)
                    .setEndPositionMs(14_000)
                    .setStartsAtKeyFrame(true)
                    .build())
            .build();

    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S,
            /* modifications...= */ "clipped"));
  }

  @Test
  public void start_withClippingStartAndEndEqual_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(0)
                    .build())
            .build();

    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S,
            /* modifications...= */ "clipped_to_empty"));
  }

  @Test
  public void start_trimOptimizationEnabled_clippingConfigurationUnset_doesNotOptimize()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .experimentalSetTrimOptimizationEnabled(true)
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .build();

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    assertThat(result.optimizationResult).isEqualTo(OPTIMIZATION_NONE);
  }

  @Test
  public void start_trimOptimizationEnabled_withClippingStartAtKeyFrame_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);

    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .experimentalSetTrimOptimizationEnabled(true)
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(12_500)
                    .setEndPositionMs(14_000)
                    .build())
            .build();

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    assertThat(result.optimizationResult)
        .isEqualTo(OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S,
            /* modifications...= */ "clipped"));
    assertThat(result.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
    assertThat(result.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void start_trimOptimizationEnabled_fileNotMp4_fallbackToNormalExport() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .experimentalSetTrimOptimizationEnabled(true)
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(500_000)
                    .setEndPositionUs(900_000)
                    .build())
            .build();

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    assertThat(result.optimizationResult).isEqualTo(OPTIMIZATION_FAILED_EXTRACTION_FAILED);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(/* originalFileName= */ FILE_AUDIO_RAW, /* modifications...= */ "clipped"));
    assertThat(result.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_NA);
    assertThat(result.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void start_withSubtitlesVideoOnly_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_WITH_SUBTITLES))
            .setRemoveAudio(true)
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_WITH_SUBTITLES, /* modifications...= */ "noaudio"));
  }

  @Test
  public void start_successiveExports_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    // Transform first media item.
    transformer.start(mediaItem, outputDir.newFile("first").getPath());
    TransformerTestRunner.runLooper(transformer);

    // Transform second media item.
    transformer.start(mediaItem, outputDir.newFile("second").getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_concurrentExports_throwsError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.start(mediaItem, outputDir.newFile("first").getPath());

    assertThrows(
        IllegalStateException.class,
        () -> transformer.start(mediaItem, outputDir.newFile("second").getPath()));
  }

  @Test
  public void start_removeAudio_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveAudio(true)
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */ "noaudio"));
  }

  @Test
  public void start_removeVideo_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveVideo(true)
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */ "novideo"));
  }

  @Test
  public void start_forceAudioTrackOnAudioOnly_isIgnored() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_AMR_NB);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .experimentalSetForceAudioTrack(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_AMR_NB));
  }

  @Test
  public void start_forceAudioTrackOnAudioVideo_isIgnored() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .experimentalSetForceAudioTrack(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_forceAudioTrackAndRemoveAudioWithEffects_generatesSilentAudio()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveAudio(true)
            .setEffects(createAudioEffects(sonicAudioProcessor))
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .experimentalSetForceAudioTrack(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */
            "silence",
            "48000hz"));
  }

  @Test
  public void start_forceAudioTrackAndRemoveVideo_isIgnored() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .experimentalSetForceAudioTrack(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */ "novideo"));
  }

  @Test
  public void start_forceAudioTrackOnVideoOnly_generatesSilentAudio() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .experimentalSetForceAudioTrack(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_VIDEO_ONLY, /* modifications...= */ "silence"));
  }

  @Test
  public void exportAudio_muxerReceivesExpectedNumberOfBytes() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    AtomicInteger bytesSeenByEffect = new AtomicInteger();
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(createAudioEffects(createByteCountingAudioProcessor(bytesSeenByEffect)))
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    assertThat(muxerFactory.getCreatedMuxer().getTotalBytesForTrack(C.TRACK_TYPE_AUDIO))
        .isEqualTo(bytesSeenByEffect.get());
  }

  @Test
  public void start_adjustSampleRate_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    AtomicInteger bytesRead = new AtomicInteger();

    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(
                createAudioEffects(
                    sonicAudioProcessor, createByteCountingAudioProcessor(bytesRead)))
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    // Resampling 1 second @ 44100Hz into 48000Hz.
    assertThat(bytesRead.get() / 2).isEqualTo(48000);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(/* originalFileName= */ FILE_AUDIO_RAW, /* modifications...= */ "48000hz"));
  }

  @Test
  public void adjustAudioSpeed_toDoubleSpeed_returnsExpectedNumberOfSamples() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setSpeed(2f);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    AtomicInteger bytesRead = new AtomicInteger();

    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(
                createAudioEffects(
                    sonicAudioProcessor, createByteCountingAudioProcessor(bytesRead)))
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    // Time stretching 1 second @ 44100Hz into 22050 samples.
    assertThat(bytesRead.get() / 2).isEqualTo(22050);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW, /* modifications...= */ "doubleSpeed"));
  }

  @Test
  public void start_withRawBigEndianAudioInput_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    ToInt16PcmAudioProcessor toInt16PcmAudioProcessor = new ToInt16PcmAudioProcessor();
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + "mp4/sample_twos_pcm.mp4");

    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(createAudioEffects(toInt16PcmAudioProcessor))
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ "mp4/sample_twos_pcm.mp4", /* modifications...= */ "toInt16"));
  }

  @Test
  public void start_singleMediaItemAndTransmux_ignoresTransmux() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(createAudioEffects(sonicAudioProcessor))
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .setTransmuxAudio(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(/* originalFileName= */ FILE_AUDIO_RAW, /* modifications...= */ "48000hz"));
  }

  @Test
  public void start_withMultipleListeners_callsEachOnCompletion() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    ArgumentCaptor<Composition> compositionArgumentCaptor =
        ArgumentCaptor.forClass(Composition.class);
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    verify(mockListener1).onCompleted(compositionArgumentCaptor.capture(), any());
    Composition composition = compositionArgumentCaptor.getValue();
    verify(mockListener2).onCompleted(eq(composition), any());
    verify(mockListener3).onCompleted(eq(composition), any());
  }

  @Test
  public void start_withMultipleListeners_callsEachOnError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    ArgumentCaptor<Composition> compositionArgumentCaptor =
        ArgumentCaptor.forClass(Composition.class);
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .setAudioMimeType(
                MimeTypes.AUDIO_AAC) // Request transcoding so AMR_WB decoder is needed.
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_AMR_WB);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));

    verify(mockListener1).onError(compositionArgumentCaptor.capture(), any(), eq(exception));
    Composition composition = compositionArgumentCaptor.getValue();
    verify(mockListener2).onError(eq(composition), any(), eq(exception));
    verify(mockListener3).onError(eq(composition), any(), eq(exception));
  }

  @Test
  public void start_withMultipleListeners_callsEachOnFallback() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    ArgumentCaptor<Composition> compositionArgumentCaptor =
        ArgumentCaptor.forClass(Composition.class);
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setFallbackEnabled(true)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();

    // No RAW encoder/muxer support, so fallback.
    transformer.start(
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW), outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    verify(mockListener1)
        .onFallbackApplied(
            compositionArgumentCaptor.capture(),
            eq(originalTransformationRequest),
            eq(fallbackTransformationRequest));
    Composition composition = compositionArgumentCaptor.getValue();
    verify(mockListener2)
        .onFallbackApplied(
            composition, originalTransformationRequest, fallbackTransformationRequest);
    verify(mockListener3)
        .onFallbackApplied(
            composition, originalTransformationRequest, fallbackTransformationRequest);
  }

  @Test
  public void start_afterBuildUponWithListenerRemoved_onlyCallsRemainingListeners()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    ArgumentCaptor<Composition> compositionArgumentCaptor =
        ArgumentCaptor.forClass(Composition.class);
    Transformer.Listener mockListener1 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener2 = mock(Transformer.Listener.class);
    Transformer.Listener mockListener3 = mock(Transformer.Listener.class);
    Transformer transformer1 =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .addListener(mockListener1)
            .addListener(mockListener2)
            .addListener(mockListener3)
            .build();
    Transformer transformer2 = transformer1.buildUpon().removeListener(mockListener2).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer2.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer2);

    verify(mockListener1).onCompleted(compositionArgumentCaptor.capture(), any());
    verify(mockListener2, never()).onCompleted(any(Composition.class), any());
    verify(mockListener3).onCompleted(eq(compositionArgumentCaptor.getValue()), any());
  }

  @Test
  public void start_flattenForSlowMotionVideoOnly_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_WITH_SEF_SLOW_MOTION))
            .setFlattenForSlowMotion(true)
            .setRemoveAudio(true)
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_WITH_SEF_SLOW_MOTION, /* modifications...= */ "noaudio"));
  }

  @Test
  public void start_completesWithValidBitrate() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.averageAudioBitrate).isGreaterThan(0);
    assertThat(exportResult.averageVideoBitrate).isGreaterThan(0);
  }

  @Test
  public void start_whenCodecFailsToConfigure_completesWithError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    String expectedFailureMessage = "Format not valid. AMR NB (3gpp)";
    ShadowMediaCodec.CodecConfig throwOnConfigureCodecConfig =
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 100_000,
            /* outputBufferSize= */ 100_000,
            /* codec= */ new ShadowMediaCodec.CodecConfig.Codec() {
              @Override
              public void process(ByteBuffer in, ByteBuffer out) {
                out.put(in);
              }

              @Override
              public void onConfigured(
                  MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {
                // MediaCodec#configure documented to throw IAE if format is invalid.
                throw new IllegalArgumentException(expectedFailureMessage);
              }
            });

    // Add the AMR_NB encoder that throws when configured.
    addAudioEncoders(throwOnConfigureCodecConfig, MimeTypes.AUDIO_AMR_NB);

    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setFallbackEnabled(true)
            .setAudioMimeType(MimeTypes.AUDIO_AMR_NB)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception.errorCode)
        .isEqualTo(ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED);
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception).hasCauseThat().hasMessageThat().isEqualTo(expectedFailureMessage);
  }

  @Test
  public void start_withAudioFormatUnsupportedByDecoder_completesWithError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setAudioMimeType(MimeTypes.AUDIO_AAC) // supported by encoder and muxer
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_AMR_WB);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(exception.errorCode)
        .isEqualTo(ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED);
  }

  @Test
  public void
      start_withAudioFormatUnsupportedByMuxer_ignoresDisabledFallbackAndCompletesSuccessfully()
          throws Exception {
    removeEncodersAndDecoders();
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    // RAW supported by encoder, unsupported by muxer.
    // AAC supported by encoder and muxer.
    addAudioEncoders(MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_AAC);

    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    // MIME type fallback is mandatory.
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .addListener(mockListener)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_RAW));
    verify(mockListener)
        .onFallbackApplied(
            any(Composition.class),
            eq(originalTransformationRequest),
            eq(fallbackTransformationRequest));
  }

  @Test
  public void start_withAudioFormatUnsupportedByMuxer_fallsBackAndCompletesSuccessfully()
      throws Exception {
    removeEncodersAndDecoders();
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    // RAW supported by encoder, unsupported by muxer.
    // AAC supported by encoder and muxer.
    addAudioEncoders(MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_AAC);

    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer.Listener mockListener = mock(Transformer.Listener.class);
    TransformationRequest originalTransformationRequest =
        new TransformationRequest.Builder().build();
    TransformationRequest fallbackTransformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_AAC).build();
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setFallbackEnabled(true)
            .addListener(mockListener)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_RAW));
    verify(mockListener)
        .onFallbackApplied(
            any(Composition.class),
            eq(originalTransformationRequest),
            eq(fallbackTransformationRequest));
  }

  @Test
  public void start_withIoError_completesWithError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri("asset:///non-existing-path.mp4");

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception).hasCauseThat().hasCauseThat().isInstanceOf(IOException.class);
    assertThat(exception.errorCode).isEqualTo(ExportException.ERROR_CODE_IO_FILE_NOT_FOUND);
  }

  @Test
  public void start_withSlowOutputSampleRate_completesWithError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(
            context, new SlowExtractorsFactory(/* delayBetweenReadsMs= */ 10));
    Codec.DecoderFactory decoderFactory = new DefaultDecoderFactory.Builder(context).build();
    AssetLoader.Factory assetLoaderFactory =
        new ExoPlayerAssetLoader.Factory(
            context,
            decoderFactory,
            new FakeClock(/* isAutoAdvancing= */ true),
            mediaSourceFactory);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setMaxDelayBetweenMuxerSamplesMs(1)
            .setAssetLoaderFactory(assetLoaderFactory)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(exception.errorCode).isEqualTo(ExportException.ERROR_CODE_MUXING_TIMEOUT);
  }

  @Test
  public void start_withUnsetMaxDelayBetweenSamples_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_afterCancellation_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputDir.newFile("first").getPath());
    transformer.cancel();

    // This would throw if the previous export had not been cancelled.
    transformer.start(mediaItem, outputDir.newFile("second").getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    // TODO: b/264974805 - Make export output deterministic and check it against dump file.
    assertThat(exportResult.exportException).isNull();
  }

  @Test
  public void start_fromSpecifiedThread_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    anotherThread.start();
    Looper looper = anotherThread.getLooper();
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).setLooper(looper).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    AtomicReference<Exception> exception = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    new Handler(looper)
        .post(
            () -> {
              try {
                transformer.start(mediaItem, outputDir.newFile().getPath());
                TransformerTestRunner.runLooper(transformer);
              } catch (Exception e) {
                exception.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    if (!countDownLatch.await(TEST_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException();
    }

    assertThat(exception.get()).isNull();
    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_fromWrongThread_throwsError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    String outputPath = outputDir.newFile().getPath();
    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.start(mediaItem, outputPath);
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    if (!countDownLatch.await(TEST_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException();
    }

    assertThat(illegalStateException.get()).isNotNull();
  }

  @Test
  @Ignore("TODO: b/294389961 - Add valid assertion for whether exporter expects decoded/encoded.")
  public void start_withAssetLoaderAlwaysDecoding_exporterExpectsDecoded() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    AtomicReference<SampleConsumer> sampleConsumerRef = new AtomicReference<>();
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setAssetLoaderFactory(
                new FakeAssetLoader.Factory(SUPPORTED_OUTPUT_TYPE_DECODED, sampleConsumerRef))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    runLooperUntil(transformer.getApplicationLooper(), () -> sampleConsumerRef.get() != null);

    // Can never be false.
    assertThat(sampleConsumerRef.get()).isNotInstanceOf(EncodedSampleExporter.class);
  }

  @Test
  public void start_withAssetLoaderNotDecodingAndDecodingNeeded_completesWithError()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .setAssetLoaderFactory(
                new FakeAssetLoader.Factory(
                    SUPPORTED_OUTPUT_TYPE_ENCODED, /* sampleConsumerRef= */ null))
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    ExportException exportException =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));

    assertThat(exportException).hasCauseThat().isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void start_withNoOpEffects_transmuxes() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    int mediaItemHeightPixels = 720;
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            Presentation.createForHeight(mediaItemHeightPixels),
            new ScaleAndRotateTransformation.Builder().build());
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // Video transcoding in unit tests is not supported.
    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_VIDEO_ONLY));
    assertThat(result.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
    assertThat(result.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_NA);
  }

  @Test
  public void start_withOnlyRegularRotationEffect_transmuxesAndRotates() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            new ScaleAndRotateTransformation.Builder().setRotationDegrees(270).build());
    Effects effects = new Effects(/* audioProcessors= */ ImmutableList.of(), videoEffects);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setEffects(effects).build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // Video transcoding in unit tests is not supported.
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */ "rotated"));
    assertThat(result.videoConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
    assertThat(result.audioConversionProcess).isEqualTo(CONVERSION_PROCESS_TRANSMUXED);
  }

  @Test
  public void start_regularRotationsAndNoOps_transmuxes() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    // Total rotation is 270.
    ImmutableList<Effect> videoEffects =
        ImmutableList.of(
            new ScaleAndRotateTransformation.Builder().setRotationDegrees(90).build(),
            new Contrast(0f),
            new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build(),
            Presentation.createForHeight(1080));
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO))
            .setEffects(new Effects(ImmutableList.of(), videoEffects))
            .build();

    transformer.start(editedMediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    // Video transcoding in unit tests is not supported.
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO, /* modifications...= */ "rotated"));
  }

  @Test
  public void analyze_audioOnlyWithItemEffect_completesSuccessfully() throws Exception {
    removeEncodersAndDecoders();
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    addThrowingAudioEncoder(MimeTypes.AUDIO_AAC);
    Transformer transformer =
        ExperimentalAnalyzerModeFactory.buildAnalyzer(
            getApplicationContext(), new TestTransformerBuilder(getApplicationContext()).build());
    AtomicInteger bytesSeen = new AtomicInteger(0);
    EditedMediaItem item =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setEffects(createAudioEffects(createByteCountingAudioProcessor(bytesSeen)))
            .build();

    transformer.start(item, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // Confirm that all the data was seen and no output file was created.
    assertThat(bytesSeen.get()).isEqualTo(88200);
    assertThat(result.fileSizeBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void analyze_audioOnlyWithCompositionEffect_completesSuccessfully() throws Exception {
    removeEncodersAndDecoders();
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    addThrowingAudioEncoder(MimeTypes.AUDIO_AAC);
    Transformer transformer =
        ExperimentalAnalyzerModeFactory.buildAnalyzer(
            getApplicationContext(), new TestTransformerBuilder(getApplicationContext()).build());
    AtomicInteger bytesSeen = new AtomicInteger(0);
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        new EditedMediaItem.Builder(
                                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
                            .build())
                    .build())
            .setEffects(createAudioEffects(createByteCountingAudioProcessor(bytesSeen)))
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // Confirm that all the data was seen and no output file was created.
    assertThat(bytesSeen.get()).isEqualTo(88200);
    assertThat(result.fileSizeBytes).isEqualTo(C.LENGTH_UNSET);
  }

  @Test
  public void analyze_audioOnly_itemAndMixerOutputMatch() throws Exception {
    removeEncodersAndDecoders();
    addAudioDecoders(MimeTypes.AUDIO_RAW);
    addThrowingAudioEncoder(MimeTypes.AUDIO_AAC);
    Transformer transformer =
        ExperimentalAnalyzerModeFactory.buildAnalyzer(
            getApplicationContext(), new TestTransformerBuilder(getApplicationContext()).build());
    AtomicInteger itemEffectBytesSeen = new AtomicInteger(0);
    AtomicInteger compositionEffectBytesSeen = new AtomicInteger(0);
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        new EditedMediaItem.Builder(
                                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
                            .setEffects(
                                createAudioEffects(
                                    createByteCountingAudioProcessor(itemEffectBytesSeen)))
                            .build())
                    .build())
            .setEffects(
                createAudioEffects(createByteCountingAudioProcessor(compositionEffectBytesSeen)))
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    assertThat(itemEffectBytesSeen.get()).isGreaterThan(0);
    assertThat(itemEffectBytesSeen.get()).isEqualTo(compositionEffectBytesSeen.get());
  }

  @Test
  public void getProgress_unknownDuration_returnsConsistentStates() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_UNKNOWN_DURATION);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    Pair<ImmutableList<@Transformer.ProgressState Integer>, ImmutableList<Integer>>
        progressStatesAndValues = runTransformerForProgressStateAndValueUpdates(transformer);
    ImmutableList<@Transformer.ProgressState Integer> progressStates =
        progressStatesAndValues.first;

    assertThat(progressStates).isNotEmpty();
    assertThat(progressStates)
        .containsExactly(
            PROGRESS_STATE_WAITING_FOR_AVAILABILITY,
            PROGRESS_STATE_UNAVAILABLE,
            PROGRESS_STATE_NOT_STARTED)
        .inOrder();
  }

  @Test
  public void getProgress_knownDuration_returnsConsistentStates() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem =
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    Pair<ImmutableList<@Transformer.ProgressState Integer>, ImmutableList<Integer>>
        progressStatesAndValues = runTransformerForProgressStateAndValueUpdates(transformer);
    ImmutableList<@Transformer.ProgressState Integer> progressStates =
        progressStatesAndValues.first;

    assertThat(progressStates)
        .containsExactly(
            PROGRESS_STATE_WAITING_FOR_AVAILABILITY,
            PROGRESS_STATE_AVAILABLE,
            PROGRESS_STATE_NOT_STARTED)
        .inOrder();
  }

  @Test
  public void getProgress_knownDuration_givesIncreasingPercentages() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem =
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    Pair<ImmutableList<@Transformer.ProgressState Integer>, ImmutableList<Integer>>
        progressStatesAndValues = runTransformerForProgressStateAndValueUpdates(transformer);
    ImmutableList<Integer> progressValues = progressStatesAndValues.second;

    assertThat(progressValues.size()).isAtLeast(2);
    assertThat(progressValues.get(0)).isAtLeast(0);
    assertThat(progressValues).isInStrictOrder();
    assertThat(Iterables.getLast(progressValues)).isAtMost(100);
  }

  @Test
  public void getProgress_noCurrentExport_returnsNotStarted() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    ProgressHolder progressHolder = new ProgressHolder();
    @Transformer.ProgressState int stateBeforeTransform = transformer.getProgress(progressHolder);
    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);
    @Transformer.ProgressState int stateAfterTransform = transformer.getProgress(progressHolder);

    assertThat(stateBeforeTransform).isEqualTo(PROGRESS_STATE_NOT_STARTED);
    assertThat(stateAfterTransform).isEqualTo(PROGRESS_STATE_NOT_STARTED);
  }

  @Test
  public void getProgress_fromWrongThread_throwsError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.getProgress(new ProgressHolder());
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    if (!countDownLatch.await(TEST_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException();
    }

    assertThat(illegalStateException.get()).isNotNull();
  }

  @Test
  public void
      getProgress_trimOptimizationEnabledButNotApplied_withClippingConfigurationUnset_givesIncreasingPercentages()
          throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .experimentalSetTrimOptimizationEnabled(true)
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    Pair<ImmutableList<@Transformer.ProgressState Integer>, ImmutableList<Integer>>
        progressStatesAndValues = runTransformerForProgressStateAndValueUpdates(transformer);
    ImmutableList<Integer> progressValues = progressStatesAndValues.second;

    assertThat(progressValues.size()).isAtLeast(2);
    assertThat(progressValues.get(0)).isAtLeast(0);
    assertThat(progressValues).isInStrictOrder();
    assertThat(Iterables.getLast(progressValues)).isAtMost(100);
  }

  @Test
  public void
      getProgress_trimOptimizationEnabledButNotApplied_withClippingConfigurationUnset_returnsConsistentStates()
          throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .experimentalSetTrimOptimizationEnabled(true)
            .build();
    MediaItem mediaItem =
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    Pair<ImmutableList<@Transformer.ProgressState Integer>, ImmutableList<Integer>>
        progressStatesAndValues = runTransformerForProgressStateAndValueUpdates(transformer);
    ImmutableList<@Transformer.ProgressState Integer> progressStates =
        progressStatesAndValues.first;

    assertThat(progressStates)
        .containsExactly(
            PROGRESS_STATE_WAITING_FOR_AVAILABILITY,
            PROGRESS_STATE_AVAILABLE,
            PROGRESS_STATE_NOT_STARTED)
        .inOrder();
  }

  @Test
  public void
      getProgress_trimOptimizationEnabledButNotApplied_withClippingConfigurationUnset_noCurrentExport_returnsNotStarted()
          throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .experimentalSetTrimOptimizationEnabled(true)
            .build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);
    ProgressHolder progressHolder = new ProgressHolder();
    @Transformer.ProgressState int stateBeforeTransform = transformer.getProgress(progressHolder);
    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);
    @Transformer.ProgressState int stateAfterTransform = transformer.getProgress(progressHolder);

    assertThat(stateBeforeTransform).isEqualTo(PROGRESS_STATE_NOT_STARTED);
    assertThat(stateAfterTransform).isEqualTo(PROGRESS_STATE_NOT_STARTED);
  }

  @Test
  public void cancel_afterCompletion_doesNotThrow() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);
    transformer.cancel();
  }

  @Test
  public void cancel_fromWrongThread_throwsError() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.cancel();
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    if (!countDownLatch.await(TEST_TIMEOUT_SECONDS, SECONDS)) {
      throw new TimeoutException();
    }

    assertThat(illegalStateException.get()).isNotNull();
  }

  @Test
  @Config(minSdk = 30)
  // This test requires Android SDK >= 30 for MediaMuxer negative PTS support.
  public void transmux_audioWithEditList_api30_correctDuration() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ELST_SKIP_500MS);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // TODO: b/324245196 - Update this test when bugs are fixed.
    //  Duration is actually 68267 / 44100 = 1548ms.
    //  Last frame PTS is 67866 / 44100 = 1.53891 which rounds down to 1538ms.
    assertThat(result.durationMs).isEqualTo(1538);
    // TODO: b/325020444 - Update this test when bugs are fixed.
    //  Dump incorrectly includes the last clipped audio sample from input file.
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_ELST_SKIP_500MS,
            /* modifications...= */ "transmuxed"));
  }

  @Test
  @Config(minSdk = 21, maxSdk = 29)
  // This test requires Android SDK < 30 with no MediaMuxer negative PTS support.
  public void transmux_audioWithEditList_api29_frameworkMuxerDoesNotThrow() throws Exception {
    // Do not use CapturingMuxer.Factory(), as this test checks for a workaround in
    // FrameworkMuxer.
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(new FrameworkMuxer.Factory()).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ELST_SKIP_500MS);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // TODO: b/324842222 - Update this test when bugs are fixed.
    //  The result.durationMs is incorrect in this test because
    //  FrameworkMuxer workaround doesn't propagate changed timestamps to MuxerWrapper.
    assertThat(result.durationMs).isEqualTo(1538);
    assertThat(result.exportException).isNull();
  }

  @Test
  @Config(minSdk = 25)
  // This test requires Android SDK < 30 for lack of MediaMuxer negative PTS support
  // and SDK >= 25 for B-frame support.
  public void transmux_trimsFirstIDRDuration() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ELST_TRIM_IDR_DURATION);

    transformer.start(mediaItem, outputDir.newFile().getPath());
    ExportResult result = TransformerTestRunner.runLooper(transformer);

    // TODO: b/324245196 - Update this test when bugs are fixed.
    //  Duration is actually 12_500. Last frame PTS is 11_500.
    assertThat(result.durationMs).isEqualTo(11_500);
    int inputFrameCount = 13;
    assertThat(result.videoFrameCount).isEqualTo(inputFrameCount);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_VIDEO_ELST_TRIM_IDR_DURATION,
            /* modifications...= */ "transmuxed"));
  }

  private static void addThrowingAudioEncoder(String mimeType) {
    ShadowMediaCodec.CodecConfig.Codec codec =
        new ShadowMediaCodec.CodecConfig.Codec() {
          @Override
          public void process(ByteBuffer byteBuffer, ByteBuffer byteBuffer1) {
            throw new IllegalStateException();
          }

          @Override
          public void onConfigured(
              MediaFormat format, Surface surface, MediaCrypto crypto, int flags) {
            throw new IllegalStateException();
          }
        };

    addAudioEncoders(
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 100_000, /* outputBufferSize= */ 100_000, codec),
        mimeType);
  }

  private static AudioProcessor createByteCountingAudioProcessor(AtomicInteger byteCount) {
    return new TeeAudioProcessor(
        new TeeAudioProcessor.AudioBufferSink() {
          @Override
          public void flush(int sampleRateHz, int channelCount, @C.PcmEncoding int encoding) {}

          @Override
          public void handleBuffer(ByteBuffer buffer) {
            byteCount.addAndGet(buffer.remaining());
          }
        });
  }

  private Pair<ImmutableList<@Transformer.ProgressState Integer>, ImmutableList<Integer>>
      runTransformerForProgressStateAndValueUpdates(Transformer transformer)
          throws ExportException, TimeoutException {
    ConcurrentLinkedDeque<@Transformer.ProgressState Integer> progressStates =
        new ConcurrentLinkedDeque<>();
    ConcurrentLinkedDeque<Integer> progressValues = new ConcurrentLinkedDeque<>();
    ProgressHolder progressHolder = new ProgressHolder();

    TransformerTestRunner.runLooperWithListener(
        transformer,
        () -> {
          @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
          if (progressStates.isEmpty() || progressState != progressStates.getLast()) {
            progressStates.add(progressState);
          }

          if (progressState == PROGRESS_STATE_AVAILABLE
              && (progressValues.isEmpty()
                  || progressHolder.progress != progressValues.getLast())) {
            progressValues.add(progressHolder.progress);
          }
        });

    // Do once more when transformer has finished running.
    @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
    if (progressStates.isEmpty() || progressState != progressStates.getLast()) {
      progressStates.add(progressState);
    }
    if (progressState == PROGRESS_STATE_AVAILABLE
        && (progressValues.isEmpty() || progressHolder.progress != progressValues.getLast())) {
      progressValues.add(progressHolder.progress);
    }

    return new Pair<>(ImmutableList.copyOf(progressStates), ImmutableList.copyOf(progressValues));
  }

  private static final class SlowExtractorsFactory implements ExtractorsFactory {

    private final long delayBetweenReadsMs;
    private final ExtractorsFactory defaultExtractorsFactory;

    public SlowExtractorsFactory(long delayBetweenReadsMs) {
      this.delayBetweenReadsMs = delayBetweenReadsMs;
      this.defaultExtractorsFactory = new DefaultExtractorsFactory();
    }

    @Override
    public Extractor[] createExtractors() {
      return slowDownExtractors(defaultExtractorsFactory.createExtractors());
    }

    @Override
    public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
      return slowDownExtractors(defaultExtractorsFactory.createExtractors(uri, responseHeaders));
    }

    private Extractor[] slowDownExtractors(Extractor[] extractors) {
      Extractor[] slowExtractors = new Extractor[extractors.length];
      Arrays.setAll(slowExtractors, i -> new SlowExtractor(extractors[i], delayBetweenReadsMs));
      return slowExtractors;
    }

    private static final class SlowExtractor implements Extractor {

      private final Extractor extractor;
      private final long delayBetweenReadsMs;

      public SlowExtractor(Extractor extractor, long delayBetweenReadsMs) {
        this.extractor = extractor;
        this.delayBetweenReadsMs = delayBetweenReadsMs;
      }

      @Override
      public boolean sniff(ExtractorInput input) throws IOException {
        return extractor.sniff(input);
      }

      @Override
      public void init(ExtractorOutput output) {
        extractor.init(output);
      }

      @Override
      public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
          throws IOException {
        try {
          Thread.sleep(delayBetweenReadsMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
        return extractor.read(input, seekPosition);
      }

      @Override
      public void seek(long position, long timeUs) {
        extractor.seek(position, timeUs);
      }

      @Override
      public void release() {
        extractor.release();
      }
    }
  }

  private static final class FakeAssetLoader implements AssetLoader {

    public static final class Factory implements AssetLoader.Factory {

      private final @SupportedOutputTypes int supportedOutputTypes;
      @Nullable private final AtomicReference<SampleConsumer> sampleConsumerRef;

      public Factory(
          @SupportedOutputTypes int supportedOutputTypes,
          @Nullable AtomicReference<SampleConsumer> sampleConsumerRef) {
        this.supportedOutputTypes = supportedOutputTypes;
        this.sampleConsumerRef = sampleConsumerRef;
      }

      @Override
      public AssetLoader createAssetLoader(
          EditedMediaItem editedMediaItem,
          Looper looper,
          Listener listener,
          CompositionSettings compositionSettings) {
        return new FakeAssetLoader(listener, supportedOutputTypes, sampleConsumerRef);
      }
    }

    private final AssetLoader.Listener listener;
    private final @SupportedOutputTypes int supportedOutputTypes;
    @Nullable private final AtomicReference<SampleConsumer> sampleConsumerRef;

    public FakeAssetLoader(
        Listener listener,
        @SupportedOutputTypes int supportedOutputTypes,
        @Nullable AtomicReference<SampleConsumer> sampleConsumerRef) {
      this.listener = listener;
      this.supportedOutputTypes = supportedOutputTypes;
      this.sampleConsumerRef = sampleConsumerRef;
    }

    @Override
    public void start() {
      listener.onDurationUs(10_000_000);
      listener.onTrackCount(1);
      Format format =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_AAC)
              .setSampleRate(44100)
              .setChannelCount(2)
              .build();
      try {
        if (listener.onTrackAdded(format, supportedOutputTypes)) {
          format = format.buildUpon().setPcmEncoding(C.ENCODING_PCM_16BIT).build();
        }

        SampleConsumer sampleConsumer = listener.onOutputFormat(format);
        if (sampleConsumerRef != null) {
          sampleConsumerRef.set(sampleConsumer);
        }
      } catch (ExportException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
      return 0;
    }

    @Override
    public ImmutableMap<Integer, String> getDecoderNames() {
      return ImmutableMap.of();
    }

    @Override
    public void release() {}
  }
}
