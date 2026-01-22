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
 */
package androidx.media3.common;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Size;
import androidx.media3.test.utils.FakeMetadataEntry;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSurfaceView;

/** Unit test for {@link ForwardingSimpleBasePlayer}. */
@RunWith(AndroidJUnit4.class)
public final class ForwardingSimpleBasePlayerTest {

  @Test
  public void forwardingSimpleBasePlayer_overridesAllSimpleBasePlayerMethods() throws Exception {
    // Check with reflection that ForwardingSimpleBasePlayer overrides all overridable
    // SimpleBasePlayer methods. This guards against accidentally forgetting to forward a method.
    for (Method method : SimpleBasePlayer.class.getDeclaredMethods()) {
      int modifiers = method.getModifiers();
      if (!Modifier.isPrivate(modifiers)
          && !Modifier.isFinal(modifiers)
          && !method.isSynthetic()
          && !method.getName().equals("getPlaceholderState")
          && !method.getName().equals("getPlaceholderMediaItemData")) {
        assertThat(
                ForwardingSimpleBasePlayer.class
                    .getDeclaredMethod(method.getName(), method.getParameterTypes())
                    .getDeclaringClass())
            .isEqualTo(ForwardingSimpleBasePlayer.class);
      }
    }
  }

  @Test
  public void getterMethods_noOtherMethodCalls_returnCurrentStateFromWrappedPlayer() {
    Player.Commands commands =
        new Player.Commands.Builder()
            .addAll(Player.COMMAND_GET_DEVICE_VOLUME, Player.COMMAND_GET_TIMELINE)
            .build();
    PlaybackException error =
        new PlaybackException(
            /* message= */ null, /* cause= */ null, PlaybackException.ERROR_CODE_DECODING_FAILED);
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 2f);
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT.buildUpon().setMaxVideoBitrate(1000).build();
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build();
    VideoSize videoSize = new VideoSize(/* width= */ 200, /* height= */ 400);
    CueGroup cueGroup =
        new CueGroup(
            ImmutableList.of(new Cue.Builder().setText("text").build()),
            /* presentationTimeUs= */ 123);
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).setMaxVolume(7).build();
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setArtist("artist").build();
    SimpleBasePlayer.PositionSupplier contentPositionSupplier = () -> 456;
    SimpleBasePlayer.PositionSupplier contentBufferedPositionSupplier = () -> 499;
    SimpleBasePlayer.PositionSupplier totalBufferedPositionSupplier = () -> 567;
    Object mediaItemUid = new Object();
    Object periodUid = new Object();
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().build()),
                    /* adaptiveSupported= */ true,
                    /* trackSupport= */ new int[] {C.FORMAT_HANDLED},
                    /* trackSelected= */ new boolean[] {true})));
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("id").build();
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    Object manifest = new Object();
    Size surfaceSize = new Size(480, 360);
    MediaItem.LiveConfiguration liveConfiguration =
        new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2000).build();
    ImmutableList<SimpleBasePlayer.MediaItemData> playlist =
        ImmutableList.of(
            new SimpleBasePlayer.MediaItemData.Builder(/* uid= */ new Object()).build(),
            new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid)
                .setTracks(tracks)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaMetadata)
                .setManifest(manifest)
                .setLiveConfiguration(liveConfiguration)
                .setPresentationStartTimeMs(12)
                .setWindowStartTimeMs(23)
                .setElapsedRealtimeEpochOffsetMs(10234)
                .setIsSeekable(true)
                .setIsDynamic(true)
                .setDefaultPositionUs(456_789)
                .setDurationUs(500_000)
                .setPositionInFirstPeriodUs(100_000)
                .setIsPlaceholder(true)
                .setPeriods(
                    ImmutableList.of(
                        new SimpleBasePlayer.PeriodData.Builder(periodUid)
                            .setIsPlaceholder(true)
                            .setDurationUs(600_000)
                            .setAdPlaybackState(
                                new AdPlaybackState(
                                    /* adsId= */ new Object(), /* adGroupTimesUs...= */ 555, 666))
                            .build()))
                .build());
    SimpleBasePlayer.State state =
        new SimpleBasePlayer.State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .setPlayerError(error)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(true)
            .setIsLoading(false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(4000)
            .setMaxSeekToPreviousPositionMs(3000)
            .setPlaybackParameters(playbackParameters)
            .setTrackSelectionParameters(trackSelectionParameters)
            .setAudioAttributes(audioAttributes)
            .setVolume(0.5f)
            .setVideoSize(videoSize)
            .setCurrentCues(cueGroup)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(5)
            .setIsDeviceMuted(true)
            .setSurfaceSize(surfaceSize)
            .setPlaylist(playlist)
            .setPlaylistMetadata(playlistMetadata)
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(contentPositionSupplier)
            .setContentBufferedPositionMs(contentBufferedPositionSupplier)
            .setTotalBufferedDurationMs(totalBufferedPositionSupplier)
            .build();
    Player wrappedPlayer =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return state;
          }
        };

    Player forwardingPlayer = new ForwardingPlayer(wrappedPlayer);

    assertThat(forwardingPlayer.getApplicationLooper()).isEqualTo(Looper.myLooper());
    assertThat(forwardingPlayer.getAvailableCommands()).isEqualTo(commands);
    assertThat(forwardingPlayer.getPlayWhenReady()).isTrue();
    assertThat(forwardingPlayer.getPlaybackState()).isEqualTo(Player.STATE_IDLE);
    assertThat(forwardingPlayer.getPlaybackSuppressionReason())
        .isEqualTo(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    assertThat(forwardingPlayer.getPlayerError()).isEqualTo(error);
    assertThat(forwardingPlayer.getRepeatMode()).isEqualTo(Player.REPEAT_MODE_ALL);
    assertThat(forwardingPlayer.getShuffleModeEnabled()).isTrue();
    assertThat(forwardingPlayer.isLoading()).isFalse();
    assertThat(forwardingPlayer.getSeekBackIncrement()).isEqualTo(5000);
    assertThat(forwardingPlayer.getSeekForwardIncrement()).isEqualTo(4000);
    assertThat(forwardingPlayer.getMaxSeekToPreviousPosition()).isEqualTo(3000);
    assertThat(forwardingPlayer.getPlaybackParameters()).isEqualTo(playbackParameters);
    assertThat(forwardingPlayer.getCurrentTracks()).isEqualTo(tracks);
    assertThat(forwardingPlayer.getTrackSelectionParameters()).isEqualTo(trackSelectionParameters);
    assertThat(forwardingPlayer.getMediaMetadata()).isEqualTo(mediaMetadata);
    assertThat(forwardingPlayer.getPlaylistMetadata()).isEqualTo(playlistMetadata);
    assertThat(forwardingPlayer.getCurrentPeriodIndex()).isEqualTo(1);
    assertThat(forwardingPlayer.getCurrentMediaItemIndex()).isEqualTo(1);
    assertThat(forwardingPlayer.getDuration()).isEqualTo(500);
    assertThat(forwardingPlayer.getCurrentPosition()).isEqualTo(456);
    assertThat(forwardingPlayer.getBufferedPosition()).isEqualTo(499);
    assertThat(forwardingPlayer.getTotalBufferedDuration()).isEqualTo(567);
    assertThat(forwardingPlayer.isPlayingAd()).isFalse();
    assertThat(forwardingPlayer.getCurrentAdGroupIndex()).isEqualTo(C.INDEX_UNSET);
    assertThat(forwardingPlayer.getCurrentAdIndexInAdGroup()).isEqualTo(C.INDEX_UNSET);
    assertThat(forwardingPlayer.getContentPosition()).isEqualTo(456);
    assertThat(forwardingPlayer.getContentBufferedPosition()).isEqualTo(499);
    assertThat(forwardingPlayer.getAudioAttributes()).isEqualTo(audioAttributes);
    assertThat(forwardingPlayer.getVolume()).isEqualTo(0.5f);
    assertThat(forwardingPlayer.getVideoSize()).isEqualTo(videoSize);
    assertThat(forwardingPlayer.getCurrentCues()).isEqualTo(cueGroup);
    assertThat(forwardingPlayer.getDeviceInfo()).isEqualTo(deviceInfo);
    assertThat(forwardingPlayer.getDeviceVolume()).isEqualTo(5);
    assertThat(forwardingPlayer.isDeviceMuted()).isTrue();
    assertThat(forwardingPlayer.getSurfaceSize()).isEqualTo(surfaceSize);
    Timeline timeline = forwardingPlayer.getCurrentTimeline();
    assertThat(timeline.getPeriodCount()).isEqualTo(2);
    assertThat(timeline.getWindowCount()).isEqualTo(2);
    Timeline.Window window = timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window());
    assertThat(window.defaultPositionUs).isEqualTo(0);
    assertThat(window.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(window.elapsedRealtimeEpochOffsetMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.firstPeriodIndex).isEqualTo(0);
    assertThat(window.isDynamic).isFalse();
    assertThat(window.isPlaceholder).isFalse();
    assertThat(window.isSeekable).isFalse();
    assertThat(window.lastPeriodIndex).isEqualTo(0);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(0);
    assertThat(window.presentationStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.windowStartTimeMs).isEqualTo(C.TIME_UNSET);
    assertThat(window.liveConfiguration).isNull();
    assertThat(window.manifest).isNull();
    assertThat(window.mediaItem).isEqualTo(MediaItem.EMPTY);
    window = timeline.getWindow(/* windowIndex= */ 1, new Timeline.Window());
    assertThat(window.defaultPositionUs).isEqualTo(456_789);
    assertThat(window.durationUs).isEqualTo(500_000);
    assertThat(window.elapsedRealtimeEpochOffsetMs).isEqualTo(10234);
    assertThat(window.firstPeriodIndex).isEqualTo(1);
    assertThat(window.isDynamic).isTrue();
    assertThat(window.isPlaceholder).isTrue();
    assertThat(window.isSeekable).isTrue();
    assertThat(window.lastPeriodIndex).isEqualTo(1);
    assertThat(window.positionInFirstPeriodUs).isEqualTo(100_000);
    assertThat(window.presentationStartTimeMs).isEqualTo(12);
    assertThat(window.windowStartTimeMs).isEqualTo(23);
    assertThat(window.liveConfiguration).isEqualTo(liveConfiguration);
    assertThat(window.manifest).isEqualTo(manifest);
    assertThat(window.mediaItem).isEqualTo(mediaItem);
    assertThat(window.uid).isEqualTo(mediaItemUid);
    Timeline.Period period =
        timeline.getPeriod(/* periodIndex= */ 0, new Timeline.Period(), /* setIds= */ true);
    assertThat(period.durationUs).isEqualTo(C.TIME_UNSET);
    assertThat(period.isPlaceholder).isFalse();
    assertThat(period.positionInWindowUs).isEqualTo(0);
    assertThat(period.windowIndex).isEqualTo(0);
    assertThat(period.getAdGroupCount()).isEqualTo(0);
    period = timeline.getPeriod(/* periodIndex= */ 1, new Timeline.Period(), /* setIds= */ true);
    assertThat(period.durationUs).isEqualTo(600_000);
    assertThat(period.isPlaceholder).isTrue();
    assertThat(period.positionInWindowUs).isEqualTo(-100_000);
    assertThat(period.windowIndex).isEqualTo(1);
    assertThat(period.id).isEqualTo(periodUid);
    assertThat(period.getAdGroupCount()).isEqualTo(2);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 0)).isEqualTo(555);
    assertThat(period.getAdGroupTimeUs(/* adGroupIndex= */ 1)).isEqualTo(666);
  }

  @SuppressWarnings("deprecation") // Verifying deprecated listener call.
  @Test
  public void stateChangesInWrappedPlayer_areForwardedToListeners() throws Exception {
    Object mediaItemUid0 = new Object();
    MediaItem mediaItem0 = new MediaItem.Builder().setMediaId("0").build();
    SimpleBasePlayer.MediaItemData mediaItemData0 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid0).setMediaItem(mediaItem0).build();
    SimpleBasePlayer.State state1 =
        new SimpleBasePlayer.State.Builder()
            .setAvailableCommands(
                new Player.Commands.Builder()
                    .addAllCommands()
                    .remove(Player.COMMAND_SET_MEDIA_ITEM)
                    .build())
            .setPlayWhenReady(
                /* playWhenReady= */ true,
                /* playWhenReadyChangeReason= */ Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(Player.STATE_READY)
            .setPlaybackSuppressionReason(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            .setPlayerError(null)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .setShuffleModeEnabled(false)
            .setIsLoading(true)
            .setSeekBackIncrementMs(7000)
            .setSeekForwardIncrementMs(2000)
            .setMaxSeekToPreviousPositionMs(8000)
            .setPlaybackParameters(PlaybackParameters.DEFAULT)
            .setTrackSelectionParameters(TrackSelectionParameters.DEFAULT)
            .setAudioAttributes(AudioAttributes.DEFAULT)
            .setVolume(1f)
            .setVideoSize(VideoSize.UNKNOWN)
            .setCurrentCues(CueGroup.EMPTY_TIME_ZERO)
            .setDeviceInfo(DeviceInfo.UNKNOWN)
            .setDeviceVolume(0)
            .setIsDeviceMuted(false)
            .setPlaylist(ImmutableList.of(mediaItemData0))
            .setPlaylistMetadata(MediaMetadata.EMPTY)
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(8_000)
            .build();
    Object mediaItemUid1 = new Object();
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    MediaMetadata mediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    Player.Commands commands =
        new Player.Commands.Builder()
            .addAllCommands()
            .remove(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
            .build();
    Tracks tracks =
        new Tracks(
            ImmutableList.of(
                new Tracks.Group(
                    new TrackGroup(new Format.Builder().build()),
                    /* adaptiveSupported= */ true,
                    /* trackSupport= */ new int[] {C.FORMAT_HANDLED},
                    /* trackSelected= */ new boolean[] {true})));
    SimpleBasePlayer.MediaItemData mediaItemData1 =
        new SimpleBasePlayer.MediaItemData.Builder(mediaItemUid1)
            .setMediaItem(mediaItem1)
            .setMediaMetadata(mediaMetadata)
            .setTracks(tracks)
            .build();
    PlaybackException error =
        new PlaybackException(
            /* message= */ null, /* cause= */ null, PlaybackException.ERROR_CODE_DECODING_FAILED);
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 2f);
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT.buildUpon().setMaxVideoBitrate(1000).build();
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build();
    VideoSize videoSize = new VideoSize(/* width= */ 200, /* height= */ 400);
    CueGroup cueGroup =
        new CueGroup(
            ImmutableList.of(new Cue.Builder().setText("text").build()),
            /* presentationTimeUs= */ 123);
    Metadata timedMetadata =
        new Metadata(/* presentationTimeUs= */ 42, new FakeMetadataEntry("data"));
    Size surfaceSize = new Size(480, 360);
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).setMaxVolume(7).build();
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setArtist("artist").build();
    SimpleBasePlayer.State state2 =
        new SimpleBasePlayer.State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                /* playWhenReady= */ false,
                /* playWhenReadyChangeReason= */ Player
                    .PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
            .setPlaybackState(Player.STATE_IDLE)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .setPlayerError(error)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(true)
            .setIsLoading(false)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(4000)
            .setMaxSeekToPreviousPositionMs(3000)
            .setPlaybackParameters(playbackParameters)
            .setTrackSelectionParameters(trackSelectionParameters)
            .setAudioAttributes(audioAttributes)
            .setVolume(0.5f)
            .setVideoSize(videoSize)
            .setCurrentCues(cueGroup)
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(5)
            .setIsDeviceMuted(true)
            .setSurfaceSize(surfaceSize)
            .setNewlyRenderedFirstFrame(true)
            .setTimedMetadata(timedMetadata)
            .setPlaylist(ImmutableList.of(mediaItemData0, mediaItemData1))
            .setPlaylistMetadata(playlistMetadata)
            .setCurrentMediaItemIndex(1)
            .setContentPositionMs(12_000)
            .setPositionDiscontinuity(
                Player.DISCONTINUITY_REASON_SEEK, /* discontinuityPositionMs= */ 11_500)
            .build();
    AtomicBoolean returnState2 = new AtomicBoolean();
    SimpleBasePlayer innerPlayer =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnState2.get() ? state2 : state1;
          }
        };
    ForwardingSimpleBasePlayer forwardingPlayer = new ForwardingSimpleBasePlayer(innerPlayer);
    // Ensure state1 is used.
    assertThat(forwardingPlayer.getPlayWhenReady()).isTrue();
    Player.Listener listener = mock(Player.Listener.class);
    forwardingPlayer.addListener(listener);

    returnState2.set(true);
    innerPlayer.invalidateState();
    // Idle Looper to ensure all callbacks (including onEvents) are delivered.
    ShadowLooper.idleMainLooper();

    verify(listener).onAvailableCommandsChanged(commands);
    verify(listener)
        .onPlayWhenReadyChanged(
            /* playWhenReady= */ false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS);
    verify(listener)
        .onPlayerStateChanged(/* playWhenReady= */ false, /* playbackState= */ Player.STATE_IDLE);
    verify(listener).onPlaybackStateChanged(Player.STATE_IDLE);
    verify(listener)
        .onPlaybackSuppressionReasonChanged(
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    verify(listener).onIsPlayingChanged(false);
    verify(listener).onPlayerError(error);
    verify(listener).onPlayerErrorChanged(error);
    verify(listener).onRepeatModeChanged(Player.REPEAT_MODE_ALL);
    verify(listener).onShuffleModeEnabledChanged(true);
    verify(listener).onLoadingChanged(false);
    verify(listener).onIsLoadingChanged(false);
    verify(listener).onSeekBackIncrementChanged(5000);
    verify(listener).onSeekForwardIncrementChanged(4000);
    verify(listener).onMaxSeekToPreviousPositionChanged(3000);
    verify(listener).onPlaybackParametersChanged(playbackParameters);
    verify(listener).onTrackSelectionParametersChanged(trackSelectionParameters);
    verify(listener).onAudioAttributesChanged(audioAttributes);
    verify(listener).onVolumeChanged(0.5f);
    verify(listener).onVideoSizeChanged(videoSize);
    verify(listener).onCues(cueGroup.cues);
    verify(listener).onCues(cueGroup);
    verify(listener).onDeviceInfoChanged(deviceInfo);
    verify(listener).onDeviceVolumeChanged(/* volume= */ 5, /* muted= */ true);
    verify(listener)
        .onTimelineChanged(state2.timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    verify(listener).onMediaMetadataChanged(mediaMetadata);
    verify(listener).onTracksChanged(tracks);
    verify(listener).onPlaylistMetadataChanged(playlistMetadata);
    verify(listener).onRenderedFirstFrame();
    verify(listener).onMetadata(timedMetadata);
    verify(listener).onSurfaceSizeChanged(surfaceSize.getWidth(), surfaceSize.getHeight());
    verify(listener).onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    verify(listener)
        .onPositionDiscontinuity(
            /* oldPosition= */ new Player.PositionInfo(
                mediaItemUid0,
                /* mediaItemIndex= */ 0,
                mediaItem0,
                /* periodUid= */ mediaItemUid0,
                /* periodIndex= */ 0,
                /* positionMs= */ 8_000,
                /* contentPositionMs= */ 8_000,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            /* newPosition= */ new Player.PositionInfo(
                mediaItemUid1,
                /* mediaItemIndex= */ 1,
                mediaItem1,
                /* periodUid= */ mediaItemUid1,
                /* periodIndex= */ 1,
                /* positionMs= */ 11_500,
                /* contentPositionMs= */ 11_500,
                /* adGroupIndex= */ C.INDEX_UNSET,
                /* adIndexInAdGroup= */ C.INDEX_UNSET),
            Player.DISCONTINUITY_REASON_SEEK);
    verify(listener).onMediaItemTransition(mediaItem1, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);
    verify(listener)
        .onEvents(
            forwardingPlayer,
            new Player.Events(
                new FlagSet.Builder()
                    .addAll(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TRACKS_CHANGED,
                        Player.EVENT_IS_LOADING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_PLAYER_ERROR,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                        Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_PLAYLIST_METADATA_CHANGED,
                        Player.EVENT_SEEK_BACK_INCREMENT_CHANGED,
                        Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
                        Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
                        Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
                        Player.EVENT_AUDIO_ATTRIBUTES_CHANGED,
                        Player.EVENT_VOLUME_CHANGED,
                        Player.EVENT_SURFACE_SIZE_CHANGED,
                        Player.EVENT_VIDEO_SIZE_CHANGED,
                        Player.EVENT_RENDERED_FIRST_FRAME,
                        Player.EVENT_CUES,
                        Player.EVENT_METADATA,
                        Player.EVENT_DEVICE_INFO_CHANGED,
                        Player.EVENT_DEVICE_VOLUME_CHANGED)
                    .build()));
    verifyNoMoreInteractions(listener);
    // Assert that we actually called all listeners. This guards against forgetting a State setter
    // when forwarding state in ForwardingSimpleBasePlayer.getState().
    for (Method method : TestUtil.getPublicMethods(Player.Listener.class)) {
      if (method.getName().equals("onAudioSessionIdChanged")
          || method.getName().equals("onSkipSilenceEnabledChanged")) {
        // Skip listeners for ExoPlayer-specific states
        continue;
      }
      method.invoke(verify(listener), getAnyArguments(method));
    }
  }

  @Test
  public void setPlayWhenReady_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.setPlayWhenReady(true);
    forwardingPlayer.play();
    forwardingPlayer.pause();

    verify(wrappedPlayer, times(2)).setPlayWhenReady(true);
    verify(wrappedPlayer).setPlayWhenReady(false);
  }

  @Test
  public void prepare_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_PREPARE).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handlePrepare() {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.prepare();

    verify(wrappedPlayer).prepare();
  }

  @Test
  public void stop_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_STOP).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleStop() {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.stop();

    verify(wrappedPlayer).stop();
  }

  @Test
  public void release_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_RELEASE).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleRelease() {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.release();

    verify(wrappedPlayer).release();
  }

  @Test
  public void setRepeatMode_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SET_REPEAT_MODE).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetRepeatMode(
                      @Player.RepeatMode int repeatMode) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

    verify(wrappedPlayer).setRepeatMode(Player.REPEAT_MODE_ONE);
  }

  @Test
  public void setShuffleModeEnabled_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SET_SHUFFLE_MODE).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetShuffleModeEnabled(
                      boolean shuffleModeEnabled) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.setShuffleModeEnabled(true);

    verify(wrappedPlayer).setShuffleModeEnabled(true);
  }

  @Test
  public void setPlaybackParameters_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SET_SPEED_AND_PITCH).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetPlaybackParameters(
                      PlaybackParameters playbackParameters) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.setPlaybackParameters(
        new PlaybackParameters(/* speed= */ 2f, /* pitch= */ 3f));
    forwardingPlayer.setPlaybackSpeed(5f);

    verify(wrappedPlayer)
        .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2f, /* pitch= */ 3f));
    verify(wrappedPlayer).setPlaybackParameters(new PlaybackParameters(/* speed= */ 5f));
  }

  @Test
  public void setTrackSelectionParameters_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetTrackSelectionParameters(
                      TrackSelectionParameters trackSelectionParameters) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    TrackSelectionParameters parameters =
        new TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext())
            .setMaxVideoBitrate(1000)
            .build();

    forwardingPlayer.setTrackSelectionParameters(parameters);

    verify(wrappedPlayer).setTrackSelectionParameters(parameters);
  }

  @Test
  public void setPlaylistMetadata_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_SET_PLAYLIST_METADATA)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetPlaylistMetadata(
                      MediaMetadata playlistMetadata) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    MediaMetadata metadata = new MediaMetadata.Builder().setTitle("title").build();

    forwardingPlayer.setPlaylistMetadata(metadata);

    verify(wrappedPlayer).setPlaylistMetadata(metadata);
  }

  @Test
  public void setVolume_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SET_VOLUME).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetVolume(float volume) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.setVolume(0.5f);

    verify(wrappedPlayer).setVolume(0.5f);
  }

  @Test
  public void setDeviceVolume_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetDeviceVolume(int deviceVolume, int flags) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.setDeviceVolume(50, C.VOLUME_FLAG_SHOW_UI);

    verify(wrappedPlayer).setDeviceVolume(50, C.VOLUME_FLAG_SHOW_UI);
  }

  @Test
  public void increaseDeviceVolume_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleIncreaseDeviceVolume(
                      @C.VolumeFlags int flags) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.increaseDeviceVolume(C.VOLUME_FLAG_PLAY_SOUND);

    verify(wrappedPlayer).increaseDeviceVolume(C.VOLUME_FLAG_PLAY_SOUND);
  }

  @Test
  public void decreaseDeviceVolume_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleDecreaseDeviceVolume(
                      @C.VolumeFlags int flags) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.decreaseDeviceVolume(C.VOLUME_FLAG_PLAY_SOUND);

    verify(wrappedPlayer).decreaseDeviceVolume(C.VOLUME_FLAG_PLAY_SOUND);
  }

  @Test
  public void setDeviceMuted_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetDeviceMuted(
                      boolean muted, @C.VolumeFlags int flags) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.setDeviceMuted(true, C.VOLUME_FLAG_PLAY_SOUND);

    verify(wrappedPlayer).setDeviceMuted(true, C.VOLUME_FLAG_PLAY_SOUND);
  }

  @Test
  public void setAudioAttributes_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SET_AUDIO_ATTRIBUTES).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetAudioAttributes(
                      AudioAttributes audioAttributes, boolean handleAudioFocus) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build();

    forwardingPlayer.setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true);

    verify(wrappedPlayer).setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true);
  }

  @Test
  public void setVideoSurface_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SET_VIDEO_SURFACE).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    TextureView textureView = new TextureView(ApplicationProvider.getApplicationContext());
    SurfaceView surfaceView = new SurfaceView(ApplicationProvider.getApplicationContext());
    ShadowSurfaceView.FakeSurfaceHolder surfaceHolder = new ShadowSurfaceView.FakeSurfaceHolder();

    forwardingPlayer.setVideoSurface(surface);
    forwardingPlayer.setVideoTextureView(textureView);
    forwardingPlayer.setVideoSurfaceView(surfaceView);
    forwardingPlayer.setVideoSurfaceHolder(surfaceHolder);

    verify(wrappedPlayer).setVideoSurface(surface);
    verify(wrappedPlayer).setVideoTextureView(textureView);
    verify(wrappedPlayer).setVideoSurfaceView(surfaceView);
    verify(wrappedPlayer).setVideoSurfaceHolder(surfaceHolder);
  }

  @Test
  public void clearVideoSurface_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SET_VIDEO_SURFACE).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleClearVideoOutput(
                      @Nullable Object videoOutput) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    TextureView textureView = new TextureView(ApplicationProvider.getApplicationContext());
    SurfaceView surfaceView = new SurfaceView(ApplicationProvider.getApplicationContext());
    ShadowSurfaceView.FakeSurfaceHolder surfaceHolder = new ShadowSurfaceView.FakeSurfaceHolder();

    forwardingPlayer.clearVideoSurface();
    forwardingPlayer.clearVideoSurface(surface);
    forwardingPlayer.clearVideoTextureView(textureView);
    forwardingPlayer.clearVideoSurfaceView(surfaceView);
    forwardingPlayer.clearVideoSurfaceHolder(surfaceHolder);

    verify(wrappedPlayer).clearVideoSurface();
    verify(wrappedPlayer).clearVideoSurface(surface);
    verify(wrappedPlayer).clearVideoTextureView(textureView);
    verify(wrappedPlayer).clearVideoSurfaceView(surfaceView);
    verify(wrappedPlayer).clearVideoSurfaceHolder(surfaceHolder);
  }

  @Test
  public void setMediaItems_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .addAll(
                                    Player.COMMAND_SET_MEDIA_ITEM,
                                    Player.COMMAND_CHANGE_MEDIA_ITEMS)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetMediaItems(
                      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    MediaItem mediaItem2 = new MediaItem.Builder().setMediaId("2").build();

    forwardingPlayer.setMediaItem(mediaItem1);
    forwardingPlayer.setMediaItem(mediaItem1, /* startPositionMs= */ 500);
    forwardingPlayer.setMediaItems(ImmutableList.of(mediaItem1, mediaItem2));
    forwardingPlayer.setMediaItems(
        ImmutableList.of(mediaItem1, mediaItem2), /* startIndex= */ 1, /* startPositionMs= */ 600);

    verify(wrappedPlayer).setMediaItem(mediaItem1);
    verify(wrappedPlayer).setMediaItem(mediaItem1, /* startPositionMs= */ 500);
    verify(wrappedPlayer).setMediaItems(ImmutableList.of(mediaItem1, mediaItem2));
    verify(wrappedPlayer)
        .setMediaItems(
            ImmutableList.of(mediaItem1, mediaItem2),
            /* startIndex= */ 1,
            /* startPositionMs= */ 600);
  }

  @Test
  public void addMediaItems_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .addAll(
                                    Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_GET_TIMELINE)
                                .build())
                        .setPlaylist(
                            ImmutableList.of(
                                new MediaItemData.Builder(new Object()).build(),
                                new MediaItemData.Builder(new Object()).build()))
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleAddMediaItems(
                      int index, List<MediaItem> mediaItems) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    MediaItem mediaItem2 = new MediaItem.Builder().setMediaId("2").build();

    forwardingPlayer.addMediaItem(/* index= */ 1, mediaItem1);
    forwardingPlayer.addMediaItems(/* index= */ 2, ImmutableList.of(mediaItem1, mediaItem2));

    verify(wrappedPlayer).addMediaItem(/* index= */ 1, mediaItem1);
    verify(wrappedPlayer).addMediaItems(/* index= */ 2, ImmutableList.of(mediaItem1, mediaItem2));
  }

  @Test
  public void moveMediaItems_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .addAll(
                                    Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_GET_TIMELINE)
                                .build())
                        .setPlaylist(
                            ImmutableList.of(
                                new MediaItemData.Builder(new Object()).build(),
                                new MediaItemData.Builder(new Object()).build(),
                                new MediaItemData.Builder(new Object()).build()))
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleMoveMediaItems(
                      int fromIndex, int toIndex, int newIndex) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 0);
    forwardingPlayer.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2, /* newIndex= */ 1);

    verify(wrappedPlayer).moveMediaItem(/* currentIndex= */ 1, /* newIndex= */ 0);
    verify(wrappedPlayer).moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2, /* newIndex= */ 1);
  }

  @Test
  public void replaceMediaItems_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .addAll(
                                    Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_GET_TIMELINE)
                                .build())
                        .setPlaylist(
                            ImmutableList.of(
                                new MediaItemData.Builder(new Object()).build(),
                                new MediaItemData.Builder(new Object()).build(),
                                new MediaItemData.Builder(new Object()).build()))
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleReplaceMediaItems(
                      int fromIndex, int toIndex, List<MediaItem> mediaItems) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);
    MediaItem mediaItem1 = new MediaItem.Builder().setMediaId("1").build();
    MediaItem mediaItem2 = new MediaItem.Builder().setMediaId("2").build();

    forwardingPlayer.replaceMediaItem(/* index= */ 1, mediaItem1);
    forwardingPlayer.replaceMediaItems(
        /* fromIndex= */ 0, /* toIndex= */ 2, ImmutableList.of(mediaItem1, mediaItem2));

    verify(wrappedPlayer).replaceMediaItem(/* index= */ 1, mediaItem1);
    verify(wrappedPlayer)
        .replaceMediaItems(
            /* fromIndex= */ 0, /* toIndex= */ 2, ImmutableList.of(mediaItem1, mediaItem2));
  }

  @Test
  public void removeMediaItems_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .addAll(
                                    Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_GET_TIMELINE)
                                .build())
                        .setPlaylist(
                            ImmutableList.of(
                                new MediaItemData.Builder(new Object()).build(),
                                new MediaItemData.Builder(new Object()).build(),
                                new MediaItemData.Builder(new Object()).build()))
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.removeMediaItem(/* index= */ 1);
    forwardingPlayer.removeMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2);

    verify(wrappedPlayer).removeMediaItem(/* index= */ 1);
    verify(wrappedPlayer).removeMediaItems(/* fromIndex= */ 0, /* toIndex= */ 2);
  }

  @Test
  public void seekBack_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SEEK_BACK).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekBack();

    verify(wrappedPlayer).seekBack();
  }

  @Test
  public void seekForward_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SEEK_FORWARD).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekForward();

    verify(wrappedPlayer).seekForward();
  }

  @Test
  public void seekInCurrentItem_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekTo(/* positionMs= */ 5000);

    verify(wrappedPlayer).seekTo(/* positionMs= */ 5000);
  }

  @Test
  public void seekToDefaultPosition_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekToDefaultPosition();

    verify(wrappedPlayer).seekToDefaultPosition();
  }

  @Test
  public void seekToMediaItem_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SEEK_TO_MEDIA_ITEM).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 3000);

    verify(wrappedPlayer).seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 3000);
  }

  @Test
  public void seekToNext_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SEEK_TO_NEXT).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekToNext();

    verify(wrappedPlayer).seekToNext();
  }

  @Test
  public void seekToPrevious_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder().add(Player.COMMAND_SEEK_TO_PREVIOUS).build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekToPrevious();

    verify(wrappedPlayer).seekToPrevious();
  }

  @Test
  public void seekToNextMediaItem_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekToNextMediaItem();

    verify(wrappedPlayer).seekToNextMediaItem();
  }

  @Test
  public void seekToPreviousMediaItem_isForwardedToWrappedPlayer() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(
                            new Commands.Builder()
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer = new ForwardingSimpleBasePlayer(wrappedPlayer);

    forwardingPlayer.seekToPreviousMediaItem();

    verify(wrappedPlayer).seekToPreviousMediaItem();
  }

  @Test
  public void overrideSetters_forwardsOverriddenCallsOnly() {
    Player wrappedPlayer =
        spy(
            new ForwardingPlayer(
                new SimpleBasePlayer(Looper.myLooper()) {
                  @Override
                  protected State getState() {
                    return new State.Builder()
                        .setAvailableCommands(new Commands.Builder().addAllCommands().build())
                        .build();
                  }

                  @Override
                  protected ListenableFuture<?> handleSeek(
                      int mediaItemIndex, long positionMs, @Command int seekCommand) {
                    return Futures.immediateVoidFuture();
                  }

                  @Override
                  protected ListenableFuture<?> handleSetShuffleModeEnabled(
                      boolean shuffleModeEnabled) {
                    return Futures.immediateVoidFuture();
                  }
                }));
    Player forwardingPlayer =
        new ForwardingSimpleBasePlayer(wrappedPlayer) {
          @Override
          protected ListenableFuture<?> handleSetRepeatMode(int repeatMode) {
            // Usage variant 1: Directly call player.
            getPlayer().setShuffleModeEnabled(true);
            return Futures.immediateVoidFuture();
          }

          @Override
          protected ListenableFuture<?> handleSeek(
              int mediaItemIndex, long positionMs, int seekCommand) {
            // Usage variant 2: Call super method with different parameters.
            return super.handleSeek(
                /* mediaItemIndex= */ 1, /* positionMs= */ 2000, Player.COMMAND_SEEK_TO_MEDIA_ITEM);
          }
        };

    forwardingPlayer.seekToNext();
    forwardingPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

    verify(wrappedPlayer).seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 2000);
    verify(wrappedPlayer).setShuffleModeEnabled(true);
    verify(wrappedPlayer, never()).seekToNext();
    verify(wrappedPlayer, never()).setRepeatMode(anyInt());
  }

  @Test
  public void overrideState_triggersListenersAccordingToOverridenState() {
    SimpleBasePlayer.State wrappedState1 =
        new SimpleBasePlayer.State.Builder()
            .setAvailableCommands(
                new Player.Commands.Builder().add(Player.COMMAND_GET_TIMELINE).build())
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(
                ImmutableList.of(new SimpleBasePlayer.MediaItemData.Builder(new Object()).build()))
            .build();
    SimpleBasePlayer.State wrappedState2 =
        wrappedState1.buildUpon().setPlaybackState(Player.STATE_BUFFERING).build();
    AtomicBoolean returnWrappedState2 = new AtomicBoolean();
    SimpleBasePlayer wrappedPlayer =
        new SimpleBasePlayer(Looper.myLooper()) {
          @Override
          protected State getState() {
            return returnWrappedState2.get() ? wrappedState2 : wrappedState1;
          }
        };
    Player forwardingPlayer =
        new ForwardingSimpleBasePlayer(wrappedPlayer) {
          @Override
          protected State getState() {
            State state = super.getState();
            if (state.playbackState == Player.STATE_BUFFERING) {
              // Suppress changes in the wrapped player and add new changes.
              state =
                  state
                      .buildUpon()
                      .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                      .setPlaybackState(Player.STATE_READY)
                      .build();
            }
            return state;
          }
        };
    Player.Listener listener = mock(Player.Listener.class);
    forwardingPlayer.addListener(listener);

    @Player.State int playbackState1 = forwardingPlayer.getPlaybackState();
    boolean playWhenReady1 = forwardingPlayer.getPlayWhenReady();
    returnWrappedState2.set(true);
    wrappedPlayer.invalidateState();
    // Idle Looper to ensure all callbacks (including onEvents) are delivered.
    ShadowLooper.idleMainLooper();
    @Player.State int playbackState2 = forwardingPlayer.getPlaybackState();
    boolean playWhenReady2 = forwardingPlayer.getPlayWhenReady();

    assertThat(playbackState1).isEqualTo(Player.STATE_READY);
    assertThat(playbackState2).isEqualTo(Player.STATE_READY);
    assertThat(playWhenReady1).isTrue();
    assertThat(playWhenReady2).isFalse();
    verify(listener, never()).onPlaybackStateChanged(anyInt());
    verify(listener).onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE);
  }

  private static Object[] getAnyArguments(Method method) {
    Object[] arguments = new Object[method.getParameterCount()];
    Class<?>[] argumentTypes = method.getParameterTypes();
    for (int i = 0; i < arguments.length; i++) {
      if (argumentTypes[i].equals(Integer.TYPE)) {
        arguments[i] = anyInt();
      } else if (argumentTypes[i].equals(Long.TYPE)) {
        arguments[i] = anyLong();
      } else if (argumentTypes[i].equals(Float.TYPE)) {
        arguments[i] = anyFloat();
      } else if (argumentTypes[i].equals(Boolean.TYPE)) {
        arguments[i] = anyBoolean();
      } else {
        arguments[i] = any();
      }
    }
    return arguments;
  }
}
