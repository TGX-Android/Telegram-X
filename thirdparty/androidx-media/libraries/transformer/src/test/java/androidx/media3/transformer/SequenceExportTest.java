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
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S;
import static androidx.media3.transformer.TestUtil.addAudioDecoders;
import static androidx.media3.transformer.TestUtil.addAudioEncoders;
import static androidx.media3.transformer.TestUtil.createAudioEffects;
import static androidx.media3.transformer.TestUtil.createChannelCountChangingAudioProcessor;
import static androidx.media3.transformer.TestUtil.createPitchChangingAudioProcessor;
import static androidx.media3.transformer.TestUtil.getDumpFileName;
import static androidx.media3.transformer.TestUtil.getSequenceDumpFilePath;
import static androidx.media3.transformer.TestUtil.removeEncodersAndDecoders;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.effect.RgbFilter;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/**
 * End-to-end test for exporting a single {@link EditedMediaItemSequence} containing multiple {@link
 * EditedMediaItem} instances with {@link Transformer}.
 *
 * <p>Video tracks can not be processed by Robolectric, as the muxer audio/video interleaving means
 * it waits for more audio samples before writing video samples. Robolectric decoders (currently)
 * just copy input buffers to the output. Audio timestamps are computed based on the amount of data
 * passed through (see [internal: b/178685617]), so are much smaller than expected because they are
 * based on encoded samples. As a result, input files with video and audio must either remove or
 * transmux the video.
 */
@RunWith(AndroidJUnit4.class)
public final class SequenceExportTest {
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
  public void start_concatenateSameMediaItemWithTransmux_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO,
            /* modifications...= */ "original",
            "original",
            "transmux"));
  }

  @Test
  public void start_concatenateSameMediaItemWithEffectsAndTransmux_ignoresEffects()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(
                new Effects(
                    ImmutableList.of(createPitchChangingAudioProcessor(/* pitch= */ 2f)),
                    ImmutableList.of(RgbFilter.createGrayscaleFilter())))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO,
            /* modifications...= */ "original",
            "original",
            "transmux"));
  }

  @Test
  public void start_concatenateClippedMediaItemsWithTransmux_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem.ClippingConfiguration clippingConfiguration1 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(0) // Corresponds to key frame.
            .setEndPositionMs(500)
            .build();
    MediaItem mediaItem1 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration1)
            .build();
    EditedMediaItem editedMediaItem1 = new EditedMediaItem.Builder(mediaItem1).build();
    MediaItem.ClippingConfiguration clippingConfiguration2 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(12_500) // Corresponds to key frame.
            .setEndPositionMs(14_000)
            .build();
    MediaItem mediaItem2 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration2)
            .build();
    EditedMediaItem editedMediaItem2 = new EditedMediaItem.Builder(mediaItem2).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem1, editedMediaItem2).build())
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S,
            /* modifications...= */ "clipped",
            "clipped",
            "transmux"));
  }

  @Test
  public void
      start_trimOptimizationEnabled_concatenateClippedMediaItemsWithTransmux_completesSuccessfully()
          throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ false);
    Transformer transformer =
        new TestTransformerBuilder(context)
            .setMuxerFactory(muxerFactory)
            .experimentalSetTrimOptimizationEnabled(true)
            .build();
    MediaItem.ClippingConfiguration clippingConfiguration1 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(0) // Corresponds to key frame.
            .setEndPositionMs(500)
            .build();
    MediaItem mediaItem1 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration1)
            .build();
    EditedMediaItem editedMediaItem1 = new EditedMediaItem.Builder(mediaItem1).build();
    MediaItem.ClippingConfiguration clippingConfiguration2 =
        new MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(12_500) // Corresponds to key frame.
            .setEndPositionMs(14_000)
            .build();
    MediaItem mediaItem2 =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S)
            .setClippingConfiguration(clippingConfiguration2)
            .build();
    EditedMediaItem editedMediaItem2 = new EditedMediaItem.Builder(mediaItem2).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem1, editedMediaItem2).build())
            .setTransmuxAudio(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.optimizationResult).isEqualTo(ExportResult.OPTIMIZATION_NONE);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_VIDEO_INCREASING_TIMESTAMPS_15S,
            /* modifications...= */ "clipped",
            "clipped",
            "transmux"));
  }

  @Test
  public void concatenateAudioAndSilence_withTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem audioVideoMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    EditedMediaItem videoOnlyMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioVideoMediaItem, videoOnlyMediaItem)
                    .build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "original",
            "silence"));
  }

  @Test
  public void concatenateSilenceAndAudio_withTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem videoOnlyMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    EditedMediaItem audioVideoMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(videoOnlyMediaItem, audioVideoMediaItem)
                    .build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "silence",
            "original"));
  }

  @Test
  public void concatenateAudioAndSilence_withEffectsAndTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    EditedMediaItem noAudioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(audioEditedMediaItem, noAudioEditedMediaItem)
                    .build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "highPitch",
            "silenceHighPitch"));
  }

  @Test
  public void concatenateSilenceAndAudio_withEffectsAndTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem silenceEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    EditedMediaItem audioEditedMediaItem =
        new EditedMediaItem.Builder(mediaItem)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(silenceEditedMediaItem, audioEditedMediaItem)
                    .build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "silenceHighPitch",
            "highPitch"));
  }

  @Test
  public void concatenateSilenceAndSilence_withTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem videoOnlyMediaItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(videoOnlyMediaItem, videoOnlyMediaItem).build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "silence",
            "silence"));
  }

  @Test
  public void concatenateEditedSilenceAndSilence_withTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem silenceWithEffectsItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    EditedMediaItem silenceItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(silenceWithEffectsItem, silenceItem).build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "silenceHighPitch",
            "silence"));
  }

  @Test
  public void concatenateSilenceAndEditedSilence_withTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem silenceItem =
        new EditedMediaItem.Builder(mediaItem).setRemoveAudio(true).build();
    EditedMediaItem silenceWithEffectsItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(silenceItem, silenceWithEffectsItem).build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "silence",
            "silenceHighPitch"));
  }

  @Test
  public void concatenateSilenceAndSilence_withEffectsAndTransmuxVideo_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO);
    EditedMediaItem firstItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    EditedMediaItem secondItem =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence.Builder(firstItem, secondItem).build())
            .experimentalSetForceAudioTrack(true)
            .setTransmuxVideo(true)
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_VIDEO,
            /* modifications...= */ "silenceHighPitch",
            "silenceHighPitch"));
  }

  @Test
  public void transmuxAudio_itemGap_throws() throws Exception {
    Transformer transformer = new TestTransformerBuilder(context).build();
    EditedMediaItem audioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder().addItem(audioItem).addGap(500_000).build();
    Composition composition = new Composition.Builder(sequence).setTransmuxAudio(true).build();

    transformer.start(composition, outputDir.newFile().getPath());

    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(getRootCause(exception)).hasMessageThat().isEqualTo("Gaps can not be transmuxed.");
  }

  // TODO: b/369154363 - Enable test after shouldTranscode inconsistency is resolved.
  @Ignore
  @Test
  public void transmuxAudio_gapItem_throws() throws Exception {
    Transformer transformer = new TestTransformerBuilder(context).build();
    EditedMediaItem audioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setRemoveVideo(true)
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder().addGap(500_000).addItem(audioItem).build();
    Composition composition = new Composition.Builder(sequence).setTransmuxAudio(true).build();

    transformer.start(composition, outputDir.newFile().getPath());

    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(getRootCause(exception)).hasMessageThat().isEqualTo("Gaps can not be transmuxed.");
  }

  @Test
  public void start_videoGap_throws() throws Exception {
    Transformer transformer = new TestTransformerBuilder(context).build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder().addItem(audioVideoItem).addGap(500_000).build();

    transformer.start(new Composition.Builder(sequence).build(), outputDir.newFile().getPath());

    ExportException exception =
        assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
    assertThat(getRootCause(exception))
        .hasMessageThat()
        .isEqualTo("Gaps in video sequences are not supported.");
  }

  @Test
  public void start_gapVideo_throws() throws Exception {
    Transformer transformer = new TestTransformerBuilder(context).build();
    EditedMediaItem audioVideoItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_VIDEO))
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder().addGap(500_000).addItem(audioVideoItem).build();
    Composition composition = new Composition.Builder(sequence).build();

    transformer.start(composition, outputDir.newFile().getPath());

    // Transformer throws because the first item in the sequence (the gap) does not have a video
    // track.
    assertThrows(ExportException.class, () -> TransformerTestRunner.runLooper(transformer));
  }

  @Test
  public void start_gapGap_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder().addGap(300_000).addGap(200_000).build();
    Composition composition = new Composition.Builder(sequence).build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context, muxerFactory.getCreatedMuxer(), getDumpFileName("gap", "500ms"));
  }

  @Test
  public void start_itemGapGap_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem firstAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder()
            .addItem(firstAudioItem)
            .addGap(500_000)
            .addGap(200_000)
            .build();
    Composition composition = new Composition.Builder(sequence).build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getSequenceDumpFilePath(
            ImmutableList.of(FILE_AUDIO_RAW.replace("wav/", ""), "gap_500ms", "gap_200ms")));
  }

  @Test
  public void start_gapGapItem_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem audioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder()
            .addGap(200_000)
            .addGap(500_000)
            .addItem(audioItem)
            .build();
    Composition composition = new Composition.Builder(sequence).build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getSequenceDumpFilePath(
            ImmutableList.of("gap_200ms", "gap_500ms", FILE_AUDIO_RAW.replace("wav/", ""))));
  }

  @Test
  public void start_itemGapGapItem_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem firstAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItem secondAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();

    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder()
            .addItem(firstAudioItem)
            .addGap(500_000)
            .addGap(200_000)
            .addItem(secondAudioItem)
            .build();
    Composition composition = new Composition.Builder(sequence).build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getSequenceDumpFilePath(
            ImmutableList.of(
                FILE_AUDIO_RAW.replace("wav/", ""),
                "gap_500ms",
                "gap_200ms",
                FILE_AUDIO_RAW.replace("wav/", ""))));
  }

  @Test
  public void start_itemGapItemGap_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem firstAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItem secondAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();

    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder()
            .addItem(firstAudioItem)
            .addGap(200_000)
            .addItem(secondAudioItem)
            .addGap(500_000)
            .build();
    Composition composition = new Composition.Builder(sequence).build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getSequenceDumpFilePath(
            ImmutableList.of(
                FILE_AUDIO_RAW.replace("wav/", ""),
                "gap_200ms",
                FILE_AUDIO_RAW.replace("wav/", ""),
                "gap_500ms")));
  }

  @Test
  public void start_gapItemGapItem_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    EditedMediaItem firstAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();
    EditedMediaItem secondAudioItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)).build();

    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder()
            .addGap(500_000)
            .addItem(firstAudioItem)
            .addGap(200_000)
            .addItem(secondAudioItem)
            .build();
    Composition composition = new Composition.Builder(sequence).build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getSequenceDumpFilePath(
            ImmutableList.of(
                "gap_500ms",
                FILE_AUDIO_RAW.replace("wav/", ""),
                "gap_200ms",
                FILE_AUDIO_RAW.replace("wav/", ""))));
  }

  @Test
  public void concatenateTwoAudioItems_withSameFormat_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem audioOnlyMediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(audioOnlyMediaItem).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW, /* modifications...= */
            "original",
            "original"));
  }

  @Test
  public void concatenateTwoAudioItems_withSameFormatAndSameEffects_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem audioOnlyMediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem, editedMediaItem).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW,
            /* modifications...= */ "highPitch",
            "highPitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withSameFormatAndDiffEffects_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem audioOnlyMediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem highPitchMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem)
            .setRemoveVideo(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    EditedMediaItem lowPitchMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem)
            .setRemoveVideo(true)
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 0.5f)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(highPitchMediaItem, lowPitchMediaItem).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW,
            /* modifications...= */ "highPitch",
            "lowPitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffFormat_completesSuccessfully() throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem stereo48000Audio =
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ);
    MediaItem mono44100Audio = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        new EditedMediaItem.Builder(stereo48000Audio).build(),
                        new EditedMediaItem.Builder(mono44100Audio).build())
                    .build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_STEREO_48000KHZ,
            /* modifications...= */ "original",
            "sample.wav"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffFormatAndSameEffects_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();

    EditedMediaItem stereo48000Audio =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    EditedMediaItem mono44100Audio =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(stereo48000Audio, mono44100Audio).build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_STEREO_48000KHZ,
            /* modifications...= */ "highPitch",
            "sample.wavHighPitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffFormatAndDiffEffects_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();

    EditedMediaItem stereo48000AudioHighPitch =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 2f)))
            .build();
    EditedMediaItem mono44100AudioLowPitch =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(/* pitch= */ 0.5f)))
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        stereo48000AudioHighPitch, mono44100AudioLowPitch)
                    .build())
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    TransformerTestRunner.runLooper(transformer);

    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_STEREO_48000KHZ,
            /* modifications...= */ "highPitch",
            "sample.wavLowPitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffFormatAndCompositionEffects_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem stereo48000Audio =
        MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ);
    MediaItem mono44100Audio = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(
                        new EditedMediaItem.Builder(stereo48000Audio).build(),
                        new EditedMediaItem.Builder(mono44100Audio).build())
                    .build())
            .setEffects(createAudioEffects(createPitchChangingAudioProcessor(2f)))
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.sampleRate).isEqualTo(48_000);
    assertThat(exportResult.channelCount).isEqualTo(2);
    DumpFileAsserts.assertOutput(
        context,
        muxerFactory.getCreatedMuxer(),
        getDumpFileName(
            /* originalFileName= */ FILE_AUDIO_RAW_STEREO_48000KHZ,
            /* modifications...= */ "original",
            "sample.wav",
            "highPitch"));
  }

  @Test
  public void concatenateTwoAudioItems_withDiffEffectsAndCompositionEffects_completesSuccessfully()
      throws Exception {
    CapturingMuxer.Factory muxerFactory = new CapturingMuxer.Factory(/* handleAudioAsPcm= */ true);
    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(muxerFactory).build();
    MediaItem audioOnlyMediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW);
    EditedMediaItem twoChannelMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem)
            .setRemoveVideo(true)
            .setEffects(
                createAudioEffects(
                    createChannelCountChangingAudioProcessor(/* outputChannelCount= */ 2)))
            .build();
    EditedMediaItem oneChannelMediaItem =
        new EditedMediaItem.Builder(audioOnlyMediaItem)
            .setRemoveVideo(true)
            .setEffects(
                createAudioEffects(
                    createChannelCountChangingAudioProcessor(/* outputChannelCount= */ 1)))
            .build();
    SonicAudioProcessor sonicAudioProcessor = new SonicAudioProcessor();
    sonicAudioProcessor.setOutputSampleRateHz(48_000);
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(twoChannelMediaItem, oneChannelMediaItem)
                    .build())
            .setEffects(createAudioEffects(sonicAudioProcessor))
            .build();

    transformer.start(composition, outputDir.newFile().getPath());
    ExportResult exportResult = TransformerTestRunner.runLooper(transformer);

    assertThat(exportResult.sampleRate).isEqualTo(48_000);
    assertThat(exportResult.channelCount).isEqualTo(2);
  }

  private Throwable getRootCause(Throwable throwable) {
    @Nullable Throwable node = throwable;
    @Nullable Throwable nodeCause;
    do {
      nodeCause = node.getCause();
      if (nodeCause != null) {
        node = nodeCause;
      }
    } while (nodeCause != null);
    return node;
  }
}
