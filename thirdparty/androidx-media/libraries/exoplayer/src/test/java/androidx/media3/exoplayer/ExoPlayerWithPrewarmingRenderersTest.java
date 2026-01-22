/*
 * Copyright (C) 2024 The Android Open Source Project
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
package androidx.media3.exoplayer;

import static androidx.media3.common.Player.REPEAT_MODE_ONE;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance;
import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.runUntilError;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Handler;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.datasource.TransferListener;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.test.utils.ExoPlayerTestRunner;
import androidx.media3.test.utils.FakeAudioRenderer;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.FakeVideoRenderer;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ExoPlayer} with the pre-warming render feature. */
@RunWith(AndroidJUnit4.class)
public class ExoPlayerWithPrewarmingRenderersTest {

  private Context context;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void play_withTwoItemsAndPrewarming_secondaryRendererisEnabledButNotStarted()
      throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is being pre-warmed.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Play until second item is started.
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
  }

  @Test
  public void
      play_withThreeItemsAndPrewarming_playerSuccessfullyPrewarmsAndSwapsBackToPrimaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the secondary renderer is being pre-warmed.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Play until until the primary renderer is being pre-warmed.
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    // Play until past transition back to primary renderer for third media item.
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_STARTED);
    @Renderer.State int videoState3 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState3 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState3).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState3).isEqualTo(Renderer.STATE_DISABLED);
  }

  @Test
  public void prepare_withPeriodBetweenPlayingAndPrewarmingPeriods_playerSuccessfullyPrewarms()
      throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Advance media periods until secondary renderer is being pre-warmed.
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int secondaryVideoState = secondaryVideoRenderer.getState();
    player.release();

    assertThat(secondaryVideoState).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void
      setPlayWhenReady_playFromPauseWithPrewarmingPrimaryRenderer_primaryRendererIsEnabledButNotStarted()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the primary renderer has been enabled, but not yet started.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    player.pause();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    @Renderer.State int videoState3 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState3 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState3).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState3).isEqualTo(Renderer.STATE_STARTED);
  }

  @Test
  public void
      setPlayWhenReady_playFromPauseWithPrewarmingNonTransitioningRenderer_rendererIsEnabledButNotStarted()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the primary renderer has been enabled, but not yet started.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    player.pause();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState3 = videoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState3).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void
      setTrackSelectionParameters_onPlayingPeriodUsingSecondaryAndPrimaryIsPrewarming_renderersNotSwapped()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the primary renderer is being prewarmed, but not yet started.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Disable the Audio track to trigger track reselection.
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build());
    advance(player).untilPendingCommandsAreFullyHandled();
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void
      setTrackSelectionParameters_onPlayingPeriodWithPrewarmingNonTransitioningPrimaryRenderer_swapsToSecondaryRendererForPrewarming()
          throws Exception {
    Format videoFormat1 =
        ExoPlayerTestRunner.VIDEO_FORMAT.buildUpon().setAverageBitrate(800_000).build();
    Format videoFormat2 =
        ExoPlayerTestRunner.VIDEO_FORMAT.buildUpon().setAverageBitrate(500_000).build();
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    DefaultTrackSelector.Parameters defaultTrackSelectorParameters =
        new DefaultTrackSelector.Parameters.Builder(context)
            .setMaxVideoBitrate(videoFormat2.averageBitrate)
            .setExceedVideoConstraintsIfNecessary(false)
            .build();
    DefaultTrackSelector defaultTrackSelector =
        new DefaultTrackSelector(context, defaultTrackSelectorParameters);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setTrackSelector(defaultTrackSelector)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    player.setMediaSources(
        ImmutableList.of(
            // Set media source with a video track with average bitrate above max.
            new FakeMediaSource(new FakeTimeline(), videoFormat1, ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), videoFormat1, videoFormat2)));
    player.prepare();

    // Play a bit until the primary renderer is being pre-warmed, but not yet started.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    SampleStream sampleStream1 = videoRenderer.getStream();
    // Set maximum video bitrate to trigger track reselection and enable video on first media item.
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setMaxVideoBitrate(videoFormat1.averageBitrate)
            .build());
    advance(player).untilPendingCommandsAreFullyHandled();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    SampleStream sampleStream2 = videoRenderer.getStream();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(sampleStream1).isNotEqualTo(sampleStream2);
  }

  @Test
  public void
      setTrackSelectionParameters_onPeriodAfterReadingWithEarlyEnabledSecondaryRenderer_createsNewSampleStreamForPrewarmingRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    Format videoFormat1 =
        ExoPlayerTestRunner.VIDEO_FORMAT.buildUpon().setAverageBitrate(800_000).build();
    Format videoFormat2 =
        ExoPlayerTestRunner.VIDEO_FORMAT.buildUpon().setAverageBitrate(500_000).build();
    Timeline timeline = new FakeTimeline();
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            // Use FakeBlockingMediaSource so the reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(timeline, videoFormat1),
            new FakeMediaSource(timeline, videoFormat1, videoFormat2)));
    player.prepare();

    // Play until the second renderer has been enabled and reading period has not advanced.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    SampleStream videoStream1 = videoRenderer.getStream();
    SampleStream secondaryVideoStream1 = secondaryVideoRenderer.getStream();
    // Set max bitrate to trigger track reselection.
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setMaxVideoBitrate(videoFormat2.averageBitrate)
            .build());
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    advance(player).untilPendingCommandsAreFullyHandled();
    SampleStream videoStream2 = videoRenderer.getStream();
    SampleStream secondaryVideoStream2 = secondaryVideoRenderer.getStream();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoStream1).isEqualTo(videoStream2);
    assertThat(secondaryVideoStream1).isNotEqualTo(secondaryVideoStream2);
  }

  @Test
  public void
      setTrackSelectionParameters_onPeriodAfterReadingWithEarlyEnabledNonTransitioningPrimaryRenderer_createsNewSampleStreamForPrewarmingRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    Format videoFormat1 =
        ExoPlayerTestRunner.VIDEO_FORMAT.buildUpon().setAverageBitrate(800_000).build();
    Format videoFormat2 =
        ExoPlayerTestRunner.VIDEO_FORMAT.buildUpon().setAverageBitrate(500_000).build();
    Timeline timeline = new FakeTimeline();
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            // Use FakeBlockingMediaSource so the reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(timeline, ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(timeline, videoFormat1, videoFormat2)));
    player.prepare();

    // Play until the second renderer has been enabled and reading period has not advanced.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    SampleStream videoStream1 = videoRenderer.getStream();
    // Set max bitrate to trigger track reselection.
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setMaxVideoBitrate(videoFormat2.averageBitrate)
            .build());
    advance(player).untilPendingCommandsAreFullyHandled();
    SampleStream videoStream2 = videoRenderer.getStream();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoStream1).isNotEqualTo(videoStream2);
  }

  @Test
  public void
      setTrackSelectionParameters_onPeriodAfterEarlyEnabledPeriod_prewarmingRendererKeepsSampleStreams()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    AtomicReference<Pair<ExoTrackSelection.Definition, Integer>> selectedAudioTrack =
        new AtomicReference<>();
    DefaultTrackSelector.Parameters trackSelectionParameters =
        new DefaultTrackSelector.Parameters.Builder(context)
            .setExceedAudioConstraintsIfNecessary(false)
            .build();
    DefaultTrackSelector trackSelector =
        new DefaultTrackSelector(context, trackSelectionParameters) {
          @Override
          @Nullable
          protected Pair<ExoTrackSelection.Definition, Integer> selectAudioTrack(
              MappedTrackInfo mappedTrackInfo,
              @RendererCapabilities.Capabilities int[][][] rendererFormatSupports,
              @RendererCapabilities.AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupports,
              Parameters params)
              throws ExoPlaybackException {
            Pair<ExoTrackSelection.Definition, Integer> result =
                super.selectAudioTrack(
                    mappedTrackInfo,
                    rendererFormatSupports,
                    rendererMixedMimeTypeAdaptationSupports,
                    params);
            selectedAudioTrack.set(result);
            return result;
          }
        };
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .setTrackSelector(trackSelector)
            .build();
    Format audioFormat =
        ExoPlayerTestRunner.AUDIO_FORMAT.buildUpon().setAverageBitrate(70_000).build();
    FakeMediaSource mediaSource =
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT, audioFormat);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    Renderer audioRenderer = player.getRenderer(/* index= */ 1);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            mediaSource));
    player.prepare();

    // Play a bit until the final media source has been prepared and gone through track selection.
    player.play();
    advance(player).untilBackgroundThreadCondition(() -> selectedAudioTrack.get() != null);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    SampleStream secondaryVideoStream1 = secondaryVideoRenderer.getStream();
    // Disable the Audio track to trigger track reselection.
    player.setTrackSelectionParameters(
        player.getTrackSelectionParameters().buildUpon().setMaxAudioBitrate(60_000).build());
    advance(player)
        .untilBackgroundThreadCondition(() -> audioRenderer.getState() == Renderer.STATE_DISABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    SampleStream secondaryVideoStream2 = secondaryVideoRenderer.getStream();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoStream1).isEqualTo(secondaryVideoStream2);
    assertThat(selectedAudioTrack.get()).isNull();
  }

  @Test
  public void
      seek_intoCurrentPeriodWithPrimaryBeforeReadingPeriodAdvanced_resetsPrewarmingRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is pre-warming.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    SampleStream secondaryVideoStream1 = secondaryVideoRenderer.getStream();
    // Seek to position in current period.
    player.seekTo(/* mediaItemIndex= */ 0, /* positionMs= */ 3000);
    advance(player).untilPendingCommandsAreFullyHandled();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    SampleStream secondaryVideoStream2 = secondaryVideoRenderer.getStream();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoStream1).isNotEqualTo(secondaryVideoStream2);
  }

  @Test
  public void
      seek_intoCurrentPeriodWithSecondaryBeforeReadingPeriodAdvanced_doesNotSwapToPrimaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started.
    advance(player).untilStartOfMediaItem(/* mediaItemIndex= */ 1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    SampleStream videoStream1 = videoRenderer.getStream();
    // Seek to position in current period.
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 3000);
    advance(player).untilPendingCommandsAreFullyHandled();
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    SampleStream videoStream2 = videoRenderer.getStream();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoStream1).isNotEqualTo(videoStream2);
  }

  @Test
  public void seek_intoCurrentPeriodWithSecondaryAndReadingPeriodAdvanced_swapsToPrimaryRenderer()
      throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Seek to position in current period.
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 500);
    advance(player).untilPendingCommandsAreFullyHandled();
    // Play until secondary renderer is being pre-warmed on third media item.
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void seek_pastReadingPeriodWithSecondaryRendererOnPlayingPeriod_swapsToPrimaryRenderer()
      throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started.
    advance(player).untilStartOfMediaItem(/* mediaItemIndex= */ 1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Seek to position in following period.
    player.seekTo(/* mediaItemIndex= */ 2, /* positionMs= */ 3000);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_DISABLED);
  }

  @Test
  public void
      removeMediaItem_onPlayingPeriodWithSecondaryRendererBeforeReadingPeriodAdvanced_swapsToPrimaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started and primary is pre-warming.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Remove the reading period.
    player.removeMediaItem(1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_DISABLED);
  }

  @Test
  public void
      removeMediaItem_onPlayingPeriodWithSecondaryRendererAfterReadingPeriodAdvanced_swapsToPrimaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started and primary is pre-warming.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    player.removeMediaItem(1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_DISABLED);
  }

  @Test
  public void
      removeMediaItem_onPrewarmingPeriodWithPrewarmingPrimaryRendererAfterReadingPeriodAdvanced_swapsToPrimaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started and primary is pre-warming.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Remove pre-warming media item.
    player.removeMediaItem(2);
    advance(player).untilPendingCommandsAreFullyHandled();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void
      replaceMediaItem_pastPrewarmingPeriodWithSecondaryRendererOnPlayingPeriod_doesNotDisablePrewarming()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started.
    advance(player).untilStartOfMediaItem(/* mediaItemIndex= */ 1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Replace media item past pre-warming period.
    player.replaceMediaItem(
        3,
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT).getMediaItem());
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
  }

  @Test
  public void
      replaceMediaItem_onPrewarmingPeriodWithPrimaryRendererBeforeReadingPeriodAdvanced_disablesPrewarmingRendererOnly()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the primary renderer is pre-warming.
    advance(player).untilStartOfMediaItem(/* mediaItemIndex= */ 1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Replace pre-warming media item.
    player.replaceMediaItem(
        2,
        new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT).getMediaItem());
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
  }

  @Test
  public void
      setRepeatMode_withPrewarmingBeforeReadingPeriodAdvanced_disablesPrewarmingRendererOnly()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock))
            .build();
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT),
            new FakeMediaSource(
                new FakeTimeline(),
                ExoPlayerTestRunner.VIDEO_FORMAT,
                ExoPlayerTestRunner.AUDIO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started and primary is pre-warming.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_STARTED);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    player.setRepeatMode(REPEAT_MODE_ONE);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
  }

  @Test
  public void
      play_errorByPrewarmingSecondaryRendererBeforeAdvancingReadingPeriod_doesNotResetPrimaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean attemptedRenderWithSecondaryRenderer = new AtomicBoolean(false);
    AtomicBoolean shouldSecondaryRendererThrow = new AtomicBoolean(true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRendererThatThrows(
                    fakeClock, attemptedRenderWithSecondaryRenderer, shouldSecondaryRendererThrow))
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is enabled and throws errors.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState = videoRenderer.getState();
    @Renderer.State int secondaryVideoState = secondaryVideoRenderer.getState();
    player.release();

    assertThat(attemptedRenderWithSecondaryRenderer.get()).isTrue();
    verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(videoState).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState).isEqualTo(Renderer.STATE_DISABLED);
  }

  @Test
  public void
      play_errorByPrewarmingSecondaryRendererAfterAdvancingReadingPeriod_doesNotResetPrimaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean attemptedRenderWithSecondaryRenderer = new AtomicBoolean(false);
    AtomicBoolean shouldSecondaryRendererThrow = new AtomicBoolean(true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRendererThatThrows(
                    fakeClock, attemptedRenderWithSecondaryRenderer, shouldSecondaryRendererThrow))
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is enabled and throws error.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    assertThat(attemptedRenderWithSecondaryRenderer.get()).isTrue();

    attemptedRenderWithSecondaryRenderer.set(false);
    // Play a bit so that primary renderer is enabled on second media item.
    advance(player).untilStartOfMediaItem(/* mediaItemIndex= */ 1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    verify(listener).onPositionDiscontinuity(any(), any(), anyInt());
    // Secondary renderer will not be used subsequently after failure.
    assertThat(attemptedRenderWithSecondaryRenderer.get()).isFalse();
    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_DISABLED);
  }

  @Test
  public void play_errorByPrewarmingSecondaryRenderer_primaryRendererIsUsedOnSubsequentMediaItem()
      throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean attemptedRenderWithSecondaryRenderer = new AtomicBoolean(false);
    AtomicBoolean shouldSecondaryRendererThrow = new AtomicBoolean(true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRendererThatThrows(
                    fakeClock, attemptedRenderWithSecondaryRenderer, shouldSecondaryRendererThrow))
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is enabled and throws error.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilBackgroundThreadCondition(attemptedRenderWithSecondaryRenderer::get);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    assertThat(attemptedRenderWithSecondaryRenderer.get()).isTrue();

    shouldSecondaryRendererThrow.set(false);
    advance(player).untilStartOfMediaItem(/* mediaItemIndex= */ 1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    verify(listener).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_DISABLED);
  }

  @Test
  public void
      play_withSecondaryRendererNonRecoverableErrorForMultipleMediaItems_primaryRendererIsUsed()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean attemptedRenderWithSecondaryRenderer = new AtomicBoolean(false);
    AtomicBoolean shouldSecondaryRendererThrow = new AtomicBoolean(true);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRendererThatThrows(
                    fakeClock, attemptedRenderWithSecondaryRenderer, shouldSecondaryRendererThrow))
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is started.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    assertThat(attemptedRenderWithSecondaryRenderer.get()).isTrue();
    advance(player).untilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 500);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    shouldSecondaryRendererThrow.set(false);
    advance(player).untilPosition(/* mediaItemIndex= */ 1, /* positionMs= */ 500);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState3 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState3 = secondaryVideoRenderer.getState();
    player.release();

    verify(listener).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoState3).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState3).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void play_errorWithPrimaryRendererDuringPrewarming_doesNotResetSecondaryRenderer()
      throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean shouldPrimaryRendererThrow = new AtomicBoolean(false);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock) {
                  @Override
                  public Renderer[] createRenderers(
                      Handler eventHandler,
                      VideoRendererEventListener videoRendererEventListener,
                      AudioRendererEventListener audioRendererEventListener,
                      TextOutput textRendererOutput,
                      MetadataOutput metadataRendererOutput) {
                    HandlerWrapper clockAwareHandler =
                        clock.createHandler(eventHandler.getLooper(), /* callback= */ null);
                    return new Renderer[] {
                      new FakeVideoRenderer(clockAwareHandler, videoRendererEventListener) {
                        @Override
                        public void render(long positionUs, long elapsedRealtimeUs)
                            throws ExoPlaybackException {
                          if (!shouldPrimaryRendererThrow.get()) {
                            super.render(positionUs, elapsedRealtimeUs);
                          } else {
                            throw createRendererException(
                                new MediaCodecRenderer.DecoderInitializationException(
                                    new Format.Builder().build(),
                                    new IllegalArgumentException(),
                                    false,
                                    0),
                                this.getFormatHolder().format,
                                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
                          }
                        }
                      },
                      new FakeAudioRenderer(clockAwareHandler, audioRendererEventListener)
                    };
                  }
                })
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is pre-warming.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    shouldPrimaryRendererThrow.set(true);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_DISABLED);
    @Renderer.State int videoState3 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState3 = secondaryVideoRenderer.getState();
    player.release();

    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState3).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(secondaryVideoState3).isEqualTo(Renderer.STATE_STARTED);
  }

  @Test
  public void
      play_errorWithPrimaryWhilePrewarmingSecondaryPriorToAdvancingReadingPeriod_restartingPlaybackWillUseSecondaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean shouldPrimaryRendererThrow = new AtomicBoolean(false);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock) {
                  @Override
                  public Renderer[] createRenderers(
                      Handler eventHandler,
                      VideoRendererEventListener videoRendererEventListener,
                      AudioRendererEventListener audioRendererEventListener,
                      TextOutput textRendererOutput,
                      MetadataOutput metadataRendererOutput) {
                    HandlerWrapper clockAwareHandler =
                        clock.createHandler(eventHandler.getLooper(), /* callback= */ null);
                    return new Renderer[] {
                      new FakeVideoRenderer(clockAwareHandler, videoRendererEventListener) {
                        @Override
                        public void render(long positionUs, long elapsedRealtimeUs)
                            throws ExoPlaybackException {
                          if (!shouldPrimaryRendererThrow.get()) {
                            super.render(positionUs, elapsedRealtimeUs);
                          } else {
                            throw createRendererException(
                                new MediaCodecRenderer.DecoderInitializationException(
                                    new Format.Builder().build(),
                                    new IllegalArgumentException(),
                                    false,
                                    0),
                                this.getFormatHolder().format,
                                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
                          }
                        }
                      },
                      new FakeAudioRenderer(clockAwareHandler, audioRendererEventListener)
                    };
                  }
                })
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is enabled.
    advance(player).untilState(Player.STATE_READY);
    player.play();
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Force primary renderer to error, killing playback.
    shouldPrimaryRendererThrow.set(true);
    advance(player).untilPlayerError();
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    // Restart playback with primary renderer functioning properly.
    shouldPrimaryRendererThrow.set(false);
    player.prepare();
    player.play();
    advance(player).untilPosition(/* mediaItemIndex= */ 0, /* positionMs= */ 500);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState3 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState3 = secondaryVideoRenderer.getState();
    player.release();

    verify(listener, never()).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(videoState3).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState3).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void
      play_errorWithSecondaryWhilePrewarmingPrimaryPriorToAdvancingReadingPeriod_restartingPlaybackWillPrewarmSecondaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean attemptedRenderWithSecondaryRenderer = new AtomicBoolean(false);
    AtomicBoolean shouldSecondaryRendererThrow = new AtomicBoolean(false);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRendererThatThrows(
                    fakeClock, attemptedRenderWithSecondaryRenderer, shouldSecondaryRendererThrow))
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            // Use FakeBlockingMediaSource so that reading period is not advanced when pre-warming.
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until on second media item and the primary renderer is pre-warming.
    advance(player).untilStartOfMediaItem(/* mediaItemIndex= */ 1);
    advance(player).untilPendingCommandsAreFullyHandled();
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    // Force secondary renderer to error, killing playback.
    shouldSecondaryRendererThrow.set(true);
    runUntilError(player);
    // Restart playback with secondary renderer functioning properly.
    shouldSecondaryRendererThrow.set(false);
    player.prepare();
    // Play until secondary renderer is pre-warming.
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    player.release();

    verify(listener).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(videoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_ENABLED);
  }

  @Test
  public void
      play_recoverableErrorWithPrimaryRendererDuringPrewarming_doesNotResetSecondaryRenderer()
          throws Exception {
    Clock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    Player.Listener listener = mock(Player.Listener.class);
    AtomicBoolean shouldPrimaryRendererThrowRecoverable = new AtomicBoolean(false);
    ExoPlayer player =
        new TestExoPlayerBuilder(context)
            .setClock(fakeClock)
            .setRenderersFactory(
                new FakeRenderersFactorySupportingSecondaryVideoRenderer(fakeClock) {
                  @Override
                  public Renderer[] createRenderers(
                      Handler eventHandler,
                      VideoRendererEventListener videoRendererEventListener,
                      AudioRendererEventListener audioRendererEventListener,
                      TextOutput textRendererOutput,
                      MetadataOutput metadataRendererOutput) {
                    HandlerWrapper clockAwareHandler =
                        clock.createHandler(eventHandler.getLooper(), /* callback= */ null);
                    return new Renderer[] {
                      new FakeVideoRenderer(clockAwareHandler, videoRendererEventListener) {
                        @Override
                        public void render(long positionUs, long elapsedRealtimeUs)
                            throws ExoPlaybackException {
                          if (!shouldPrimaryRendererThrowRecoverable.get()) {
                            super.render(positionUs, elapsedRealtimeUs);
                          } else {
                            shouldPrimaryRendererThrowRecoverable.set(false);
                            throw createRendererException(
                                new MediaCodecRenderer.DecoderInitializationException(
                                    new Format.Builder().build(),
                                    new IllegalArgumentException(),
                                    false,
                                    0),
                                this.getFormatHolder().format,
                                true,
                                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
                          }
                        }
                      },
                      new FakeAudioRenderer(clockAwareHandler, audioRendererEventListener)
                    };
                  }
                })
            .build();
    player.addListener(listener);
    Renderer videoRenderer = player.getRenderer(/* index= */ 0);
    Renderer secondaryVideoRenderer = player.getSecondaryRenderer(/* index= */ 0);
    // Set a playlist that allows a new renderer to be enabled early.
    player.setMediaSources(
        ImmutableList.of(
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeBlockingMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT),
            new FakeMediaSource(new FakeTimeline(), ExoPlayerTestRunner.VIDEO_FORMAT)));
    player.prepare();

    // Play a bit until the second renderer is pre-warming.
    player.play();
    advance(player)
        .untilBackgroundThreadCondition(
            () -> secondaryVideoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState1 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState1 = secondaryVideoRenderer.getState();
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_ENABLED);
    @Renderer.State int videoState2 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState2 = secondaryVideoRenderer.getState();
    shouldPrimaryRendererThrowRecoverable.set(true);
    advance(player)
        .untilBackgroundThreadCondition(() -> videoRenderer.getState() == Renderer.STATE_DISABLED);
    @Renderer.State int videoState3 = videoRenderer.getState();
    @Renderer.State int secondaryVideoState3 = secondaryVideoRenderer.getState();
    player.release();

    verify(listener).onPositionDiscontinuity(any(), any(), anyInt());
    assertThat(videoState1).isEqualTo(Renderer.STATE_STARTED);
    assertThat(secondaryVideoState1).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(videoState2).isEqualTo(Renderer.STATE_ENABLED);
    assertThat(secondaryVideoState2).isEqualTo(Renderer.STATE_STARTED);
    assertThat(videoState3).isEqualTo(Renderer.STATE_DISABLED);
    assertThat(secondaryVideoState3).isEqualTo(Renderer.STATE_STARTED);
  }

  /** {@link FakeMediaSource} that prevents any reading of samples off the sample queue. */
  private static final class FakeBlockingMediaSource extends FakeMediaSource {

    public FakeBlockingMediaSource(Timeline timeline, Format format) {
      super(timeline, format);
    }

    @Override
    protected MediaPeriod createMediaPeriod(
        MediaPeriodId id,
        TrackGroupArray trackGroupArray,
        Allocator allocator,
        MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
        DrmSessionManager drmSessionManager,
        DrmSessionEventListener.EventDispatcher drmEventDispatcher,
        @Nullable TransferListener transferListener) {
      long startPositionUs =
          -getTimeline()
              .getPeriodByUid(id.periodUid, new Timeline.Period())
              .getPositionInWindowUs();
      return new FakeMediaPeriod(
          trackGroupArray,
          allocator,
          (format, mediaPeriodId) ->
              ImmutableList.of(
                  oneByteSample(startPositionUs, C.BUFFER_FLAG_KEY_FRAME),
                  oneByteSample(startPositionUs + 10_000),
                  END_OF_STREAM_ITEM),
          mediaSourceEventDispatcher,
          drmSessionManager,
          drmEventDispatcher,
          /* deferOnPrepared= */ false) {
        @Override
        protected FakeSampleStream createSampleStream(
            Allocator allocator,
            @Nullable MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher,
            DrmSessionManager drmSessionManager,
            DrmSessionEventListener.EventDispatcher drmEventDispatcher,
            Format initialFormat,
            List<FakeSampleStream.FakeSampleStreamItem> fakeSampleStreamItems) {
          return new FakeSampleStream(
              allocator,
              mediaSourceEventDispatcher,
              drmSessionManager,
              drmEventDispatcher,
              initialFormat,
              fakeSampleStreamItems) {
            @Override
            public int readData(
                FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
              return C.RESULT_NOTHING_READ;
            }
          };
        }
      };
    }
  }

  private static class FakeRenderersFactorySupportingSecondaryVideoRenderer
      implements RenderersFactory {
    protected final Clock clock;

    public FakeRenderersFactorySupportingSecondaryVideoRenderer(Clock clock) {
      this.clock = clock;
    }

    @Override
    public Renderer[] createRenderers(
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      HandlerWrapper clockAwareHandler =
          clock.createHandler(eventHandler.getLooper(), /* callback= */ null);
      return new Renderer[] {
        new FakeVideoRenderer(clockAwareHandler, videoRendererEventListener),
        new FakeAudioRenderer(clockAwareHandler, audioRendererEventListener)
      };
    }

    @Override
    public Renderer createSecondaryRenderer(
        Renderer renderer,
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      if (renderer instanceof FakeVideoRenderer) {
        return new FakeVideoRenderer(
            clock.createHandler(eventHandler.getLooper(), /* callback= */ null),
            videoRendererEventListener);
      }
      return null;
    }
  }

  private static final class FakeRenderersFactorySupportingSecondaryVideoRendererThatThrows
      extends FakeRenderersFactorySupportingSecondaryVideoRenderer {
    private final AtomicBoolean attemptedRenderWithSecondaryRenderer;
    private final AtomicBoolean shouldSecondaryRendererThrow;

    public FakeRenderersFactorySupportingSecondaryVideoRendererThatThrows(
        Clock clock,
        AtomicBoolean attemptedRenderWithSecondaryRenderer,
        AtomicBoolean shouldSecondaryRendererThrow) {
      super(clock);
      this.attemptedRenderWithSecondaryRenderer = attemptedRenderWithSecondaryRenderer;
      this.shouldSecondaryRendererThrow = shouldSecondaryRendererThrow;
    }

    @Override
    public Renderer createSecondaryRenderer(
        Renderer renderer,
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      if (renderer instanceof FakeVideoRenderer) {
        return new FakeVideoRenderer(
            clock.createHandler(eventHandler.getLooper(), /* callback= */ null),
            videoRendererEventListener) {
          @Override
          public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
            attemptedRenderWithSecondaryRenderer.set(true);
            if (!shouldSecondaryRendererThrow.get()) {
              super.render(positionUs, elapsedRealtimeUs);
            } else {
              throw createRendererException(
                  new MediaCodecRenderer.DecoderInitializationException(
                      new Format.Builder().build(), new IllegalArgumentException(), false, 0),
                  this.getFormatHolder().format,
                  PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
            }
          }
        };
      }
      return null;
    }
  }
}
