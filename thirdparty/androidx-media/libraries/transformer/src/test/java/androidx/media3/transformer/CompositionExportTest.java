/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_ONLY;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S;
import static androidx.media3.transformer.TestUtil.FILE_VIDEO_ONLY;
import static androidx.media3.transformer.TestUtil.addAudioDecoders;
import static androidx.media3.transformer.TestUtil.addAudioEncoders;
import static androidx.media3.transformer.TestUtil.createAudioEffects;
import static androidx.media3.transformer.TestUtil.createVolumeScalingAudioProcessor;
import static androidx.media3.transformer.TestUtil.getCompositionDumpFilePath;
import static androidx.media3.transformer.TestUtil.getDumpFileName;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/**
 * End-to-end test for exporting a {@link Composition} containing multiple {@link
 * EditedMediaItemSequence} instances with {@link Transformer}.
 */
@RunWith(AndroidJUnit4.class)
public class CompositionExportTest {
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
  public void start_audioVideoTransmuxedFromDifferentSequences_matchesSingleSequenceResult()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);

    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder(videoEditedMediaItem).build())
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();
    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void start_loopingTransmuxedAudio_producesExpectedResult() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ONLY)).build();
    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence.Builder(audioEditedMediaItem).setIsLooping(true).build();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY)).build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence.Builder(
                videoEditedMediaItem, videoEditedMediaItem, videoEditedMediaItem)
            .build();
    Composition composition =
        new Composition.Builder(loopingAudioSequence, videoSequence)
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(6);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_ONLY,
            /* modifications...= */ "looping",
            "mixedWith",
            getFileName(FILE_VIDEO_ONLY)));
  }

  @Test
  public void start_loopingTransmuxedVideo_producesExpectedResult() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_ONLY)).build();
    EditedMediaItemSequence audioSequence =
        new EditedMediaItemSequence.Builder(
                audioEditedMediaItem, audioEditedMediaItem, audioEditedMediaItem)
            .build();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_VIDEO_ONLY)).build();
    EditedMediaItemSequence loopingVideoSequence =
        new EditedMediaItemSequence.Builder(videoEditedMediaItem).setIsLooping(true).build();
    Composition composition =
        new Composition.Builder(audioSequence, loopingVideoSequence)
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(7);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_VIDEO_ONLY,
            /* modifications...= */ "looping",
            "mixedWith",
            getFileName(FILE_AUDIO_ONLY)));
  }

  @Test
  public void start_longVideoCompositionWithLoopingAudio_producesExpectedResult() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence.Builder(
                new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
                    .build())
            .setIsLooping(true)
            .build();
    EditedMediaItem videoEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S))
            .setRemoveAudio(true)
            .build();
    EditedMediaItemSequence videoSequence =
        new EditedMediaItemSequence.Builder(videoEditedMediaItem, videoEditedMediaItem).build();
    Composition composition =
        new Composition.Builder(loopingAudioSequence, videoSequence).setTransmuxVideo(true).build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.durationMs).isEqualTo(31_053);
    // FILE_AUDIO_RAW duration is 1000ms. Input 32 times to cover the 31_053ms duration.
    assertThat(exportResult.processedInputs).hasSize(34);
    assertThat(exportResult.channelCount).isEqualTo(1);
    assertThat(exportResult.fileSizeBytes).isEqualTo(5292662);
  }

  @Test
  public void start_compositionOfConcurrentAudio_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem rawAudioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(rawAudioEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder(rawAudioEditedMediaItem).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(2);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_RAW, /* modifications...= */ "mixed", getFileName(FILE_AUDIO_RAW)));
  }

  @Test
  public void start_audioVideoCompositionWithExtraAudio_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioVideoEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder(audioEditedMediaItem).build())
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(2);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "mixed",
            getFileName(FILE_AUDIO_RAW_STEREO_48000KHZ)));
  }

  @Test
  public void start_audioVideoCompositionWithMutedAudio_matchesSingleSequence() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItem mutedAudioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .setEffects(createAudioEffects(createVolumeScalingAudioProcessor(0f)))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence loopingMutedAudioSequence =
        new EditedMediaItemSequence.Builder(mutedAudioEditedMediaItem).setIsLooping(true).build();

    transformer.start(
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        audioVideoEditedMediaItem,
                        audioVideoEditedMediaItem,
                        audioVideoEditedMediaItem)
                    .build(),
                loopingMutedAudioSequence)
            .setTransmuxVideo(true)
            .build(),
        outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_RAW_VIDEO, /* modifications...= */ "sequence", "repeated3Times"));
  }

  @Test
  public void start_audioVideoCompositionWithLoopingAudio_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItemSequence audioVideoSequence =
        new EditedMediaItemSequence.Builder(
                audioVideoEditedMediaItem, audioVideoEditedMediaItem, audioVideoEditedMediaItem)
            .build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence loopingAudioSequence =
        new EditedMediaItemSequence.Builder(audioEditedMediaItem).setIsLooping(true).build();
    Composition composition =
        new Composition.Builder(audioVideoSequence, loopingAudioSequence)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(7);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "sequence",
            "repeated3Times",
            "mixed",
            "loopingAudio" + getFileName(FILE_AUDIO_RAW_VIDEO)));
  }

  @Test
  public void start_adjustSampleRateWithComposition_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(editedMediaItem).build())
            .setEffects(createAudioEffects(sonicAudioProcessor))
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(/* originalFileName= */ FILE_AUDIO_RAW, /* modifications...= */ "48000hz"));
  }

  @Test
  public void start_compositionOfConcurrentAudio_changesSampleRateWithEffect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48000);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem rawAudioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(rawAudioEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder(rawAudioEditedMediaItem).build())
            .setEffects(createAudioEffects(sonicAudioProcessor))
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(2);
    assertThat(exportResult.sampleRate).isEqualTo(48000);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            FILE_AUDIO_RAW,
            /* modifications...= */ "mixed",
            getFileName(FILE_AUDIO_RAW),
            "48000hz"));
  }

  @Test
  public void start_firstSequenceFinishesEarly_works() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioItem300ms =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
                    .buildUpon()
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(100)
                            .setEndPositionMs(400)
                            .build())
                    .build())
            .build();
    EditedMediaItem audioItem1000ms =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioItem300ms).build(),
                new EditedMediaItemSequence.Builder(audioItem1000ms).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath("seq-sample.wav+seq-sample.wav_clipped_100ms_to_400ms"));
  }

  @Test
  public void start_secondSequenceFinishesEarly_works() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioItem1000ms =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItem audioItem300ms =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
                    .buildUpon()
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(100)
                            .setEndPositionMs(400)
                            .build())
                    .build())
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioItem1000ms).build(),
                new EditedMediaItemSequence.Builder(audioItem300ms).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath("seq-sample.wav+seq-sample.wav_clipped_100ms_to_400ms"));
  }

  @Test
  public void start_audioCompositionWithFirstSequenceAsGap_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioItem1000ms =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder().addGap(1_000_000).build(),
                new EditedMediaItemSequence.Builder(audioItem1000ms).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    // Gaps are 44.1kHz, stereo by default. Sample.wav is 44.1kHz mono, so this test needs its own
    // dump file.
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath("seq-" + "gap_1000ms" + "+seq-" + getFileName(FILE_AUDIO_RAW)));
  }

  @Test
  public void start_audioCompositionWithFirstSequenceOffsetGap_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .build();
    EditedMediaItem otherAudioEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder()
                    .addGap(100_000)
                    .addItem(audioEditedMediaItem)
                    .build(),
                new EditedMediaItemSequence.Builder(otherAudioEditedMediaItem).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(3);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath(
            "seq-"
                + "gap_100ms-"
                + getFileName(FILE_AUDIO_RAW_STEREO_48000KHZ)
                + "+seq-"
                + getFileName(FILE_AUDIO_RAW)));
  }

  @Test
  public void start_audioCompositionWithFirstSequencePaddingGap_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioItem300ms =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
                    .buildUpon()
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(100)
                            .setEndPositionMs(400)
                            .build())
                    .build())
            .build();
    EditedMediaItem audioItem1000ms =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder()
                    .addItem(audioItem300ms)
                    .addGap(700_000)
                    .build(),
                new EditedMediaItemSequence.Builder(audioItem1000ms).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath(
            "seq-"
                + getFileName(FILE_AUDIO_RAW)
                + "+seq-"
                + getFileName(FILE_AUDIO_RAW)
                + "_clipped100msTo400ms-gap_700ms"));
  }

  @Test
  public void start_audioVideoCompositionWithSecondSequenceOffsetGap_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioVideoEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder()
                    .addGap(200_000)
                    .addItem(audioEditedMediaItem)
                    .build())
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(3);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath(
            "seq-"
                + getFileName(FILE_AUDIO_RAW_VIDEO)
                + "+seq-gap_200ms-"
                + getFileName(FILE_AUDIO_RAW_STEREO_48000KHZ)));
  }

  @Test
  public void start_audioVideoCompositionWithSecondSequenceIntervalGap_isCorrect()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItem audio300msEditedMediaItem =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(300).build())
                    .build())
            .setRemoveVideo(true)
            .build();
    EditedMediaItem audio500msEditedMediaItem =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(300)
                            .setEndPositionMs(800)
                            .build())
                    .build())
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioVideoEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder()
                    .addItem(audio300msEditedMediaItem)
                    .addGap(200_000)
                    .addItem(audio500msEditedMediaItem)
                    .build())
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(4);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath(
            "seq-"
                + getFileName(FILE_AUDIO_RAW_VIDEO)
                + "+seq-"
                + getFileName(FILE_AUDIO_RAW_STEREO_48000KHZ)
                + "_clipped0msTo300ms-"
                + "gap_200ms-"
                + getFileName(FILE_AUDIO_RAW_STEREO_48000KHZ)
                + "_clipped300msTo800ms"));
  }

  @Test
  public void start_audioVideoCompositionWithSecondSequencePaddingGap_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioVideoEditedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setRemoveVideo(true)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioVideoEditedMediaItem).build(),
                new EditedMediaItemSequence.Builder()
                    .addItem(audioEditedMediaItem)
                    .addGap(100_000)
                    .build())
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.processedInputs).hasSize(3);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getCompositionDumpFilePath(
            "seq-"
                + getFileName(FILE_AUDIO_RAW_VIDEO)
                + "+seq-"
                + getFileName(FILE_AUDIO_RAW_STEREO_48000KHZ)
                + "-gap_100ms"));
  }

  @Test
  public void start_audioCompositionWithSecondSequenceAsGap_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioItem1000ms =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioItem1000ms).build(),
                new EditedMediaItemSequence.Builder().addGap(1_000_000).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName(FILE_AUDIO_RAW));
  }

  @Test
  public void start_audioCompositionWithBothSequencesAsGaps_isCorrect() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder().addGap(500_000).build(),
                new EditedMediaItemSequence.Builder().addGap(500_000).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName("gap", "500ms"));
  }

  private static String getFileName(String filePath) {
    int lastSeparator = filePath.lastIndexOf("/");
    return filePath.substring(lastSeparator + 1);
  }
}
