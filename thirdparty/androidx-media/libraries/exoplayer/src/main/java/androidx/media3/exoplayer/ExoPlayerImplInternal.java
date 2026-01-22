/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.exoplayer.MediaPeriodQueue.UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD;
import static androidx.media3.exoplayer.MediaPeriodQueue.UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD;
import static androidx.media3.exoplayer.RendererHolder.REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED;
import static androidx.media3.exoplayer.RendererHolder.REPLACE_STREAMS_DISABLE_RENDERERS_DISABLE_OFFLOAD_SCHEDULING;
import static androidx.media3.exoplayer.audio.AudioSink.OFFLOAD_MODE_DISABLED;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.IllegalSeekPositionException;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackException.ErrorCode;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.PlaybackSuppressionReason;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.exoplayer.DefaultMediaClock.PlaybackParametersListener;
import androidx.media3.exoplayer.ExoPlayer.PreloadConfiguration;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.source.BehindLiveWindowException;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Implements the internal behavior of {@link ExoPlayerImpl}. */
/* package */ final class ExoPlayerImplInternal
    implements Handler.Callback,
        MediaPeriod.Callback,
        TrackSelector.InvalidationListener,
        MediaSourceList.MediaSourceListInfoRefreshListener,
        PlaybackParametersListener,
        PlayerMessage.Sender,
        AudioFocusManager.PlayerControl {

  private static final String TAG = "ExoPlayerImplInternal";

  public static final class PlaybackInfoUpdate {

    private boolean hasPendingChange;

    public PlaybackInfo playbackInfo;
    public int operationAcks;
    public boolean positionDiscontinuity;
    public @DiscontinuityReason int discontinuityReason;

    public PlaybackInfoUpdate(PlaybackInfo playbackInfo) {
      this.playbackInfo = playbackInfo;
    }

    public void incrementPendingOperationAcks(int operationAcks) {
      hasPendingChange |= operationAcks > 0;
      this.operationAcks += operationAcks;
    }

    public void setPlaybackInfo(PlaybackInfo playbackInfo) {
      hasPendingChange |= this.playbackInfo != playbackInfo;
      this.playbackInfo = playbackInfo;
    }

    public void setPositionDiscontinuity(@DiscontinuityReason int discontinuityReason) {
      if (positionDiscontinuity
          && this.discontinuityReason != Player.DISCONTINUITY_REASON_INTERNAL) {
        // We always prefer non-internal discontinuity reasons. We also assume that we won't report
        // more than one non-internal discontinuity per message iteration.
        Assertions.checkArgument(discontinuityReason == Player.DISCONTINUITY_REASON_INTERNAL);
        return;
      }
      hasPendingChange = true;
      positionDiscontinuity = true;
      this.discontinuityReason = discontinuityReason;
    }
  }

  public interface PlaybackInfoUpdateListener {
    void onPlaybackInfoUpdate(ExoPlayerImplInternal.PlaybackInfoUpdate playbackInfo);
  }

  // Internal messages
  private static final int MSG_SET_PLAY_WHEN_READY = 1;
  private static final int MSG_DO_SOME_WORK = 2;
  private static final int MSG_SEEK_TO = 3;
  private static final int MSG_SET_PLAYBACK_PARAMETERS = 4;
  private static final int MSG_SET_SEEK_PARAMETERS = 5;
  private static final int MSG_STOP = 6;
  private static final int MSG_RELEASE = 7;
  private static final int MSG_PERIOD_PREPARED = 8;
  private static final int MSG_SOURCE_CONTINUE_LOADING_REQUESTED = 9;
  private static final int MSG_TRACK_SELECTION_INVALIDATED = 10;
  private static final int MSG_SET_REPEAT_MODE = 11;
  private static final int MSG_SET_SHUFFLE_ENABLED = 12;
  private static final int MSG_SET_FOREGROUND_MODE = 13;
  private static final int MSG_SEND_MESSAGE = 14;
  private static final int MSG_SEND_MESSAGE_TO_TARGET_THREAD = 15;
  private static final int MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL = 16;
  private static final int MSG_SET_MEDIA_SOURCES = 17;
  private static final int MSG_ADD_MEDIA_SOURCES = 18;
  private static final int MSG_MOVE_MEDIA_SOURCES = 19;
  private static final int MSG_REMOVE_MEDIA_SOURCES = 20;
  private static final int MSG_SET_SHUFFLE_ORDER = 21;
  private static final int MSG_PLAYLIST_UPDATE_REQUESTED = 22;
  private static final int MSG_SET_PAUSE_AT_END_OF_WINDOW = 23;
  private static final int MSG_ATTEMPT_RENDERER_ERROR_RECOVERY = 25;
  private static final int MSG_RENDERER_CAPABILITIES_CHANGED = 26;
  private static final int MSG_UPDATE_MEDIA_SOURCES_WITH_MEDIA_ITEMS = 27;
  private static final int MSG_SET_PRELOAD_CONFIGURATION = 28;
  private static final int MSG_PREPARE = 29;
  private static final int MSG_SET_VIDEO_OUTPUT = 30;
  private static final int MSG_SET_AUDIO_ATTRIBUTES = 31;
  private static final int MSG_SET_VOLUME = 32;
  private static final int MSG_AUDIO_FOCUS_PLAYER_COMMAND = 33;
  private static final int MSG_AUDIO_FOCUS_VOLUME_MULTIPLIER = 34;

  private static final long BUFFERING_MAXIMUM_INTERVAL_MS =
      Util.usToMs(Renderer.DEFAULT_DURATION_TO_PROGRESS_US);
  private static final long READY_MAXIMUM_INTERVAL_MS = 1000;

  /**
   * Duration for which the player needs to appear stuck before the playback is failed on the
   * assumption that no further progress will be made. To appear stuck, the player's renderers must
   * not be ready, there must be more media available to load, and the LoadControl must be refusing
   * to load it.
   */
  private static final long PLAYBACK_STUCK_AFTER_MS = 4000;

  /**
   * Threshold under which a buffered duration is assumed to be empty. We cannot use zero to account
   * for buffers currently hold but not played by the renderer.
   */
  private static final long PLAYBACK_BUFFER_EMPTY_THRESHOLD_US = 500_000;

  private final RendererHolder[] renderers;
  private final RendererCapabilities[] rendererCapabilities;
  private final boolean[] rendererReportedReady;
  private final TrackSelector trackSelector;
  private final TrackSelectorResult emptyTrackSelectorResult;
  private final LoadControl loadControl;
  private final BandwidthMeter bandwidthMeter;
  private final HandlerWrapper handler;
  private final PlaybackLooperProvider playbackLooperProvider;
  private final Looper playbackLooper;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final long backBufferDurationUs;
  private final boolean retainBackBufferFromKeyframe;
  private final DefaultMediaClock mediaClock;
  private final ArrayList<PendingMessageInfo> pendingMessages;
  private final Clock clock;
  private final PlaybackInfoUpdateListener playbackInfoUpdateListener;
  private final MediaPeriodQueue queue;
  private final MediaSourceList mediaSourceList;
  private final LivePlaybackSpeedControl livePlaybackSpeedControl;
  private final long releaseTimeoutMs;
  private final PlayerId playerId;
  private final boolean dynamicSchedulingEnabled;
  private final AnalyticsCollector analyticsCollector;
  private final HandlerWrapper applicationLooperHandler;
  private final boolean hasSecondaryRenderers;
  private final AudioFocusManager audioFocusManager;

  @SuppressWarnings("unused")
  private SeekParameters seekParameters;

  private PlaybackInfo playbackInfo;
  private PlaybackInfoUpdate playbackInfoUpdate;
  private boolean released;
  private boolean pauseAtEndOfWindow;
  private boolean pendingPauseAtEndOfPeriod;
  private boolean isRebuffering;
  private long lastRebufferRealtimeMs;
  private boolean shouldContinueLoading;
  private @Player.RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  private boolean foregroundMode;
  private boolean requestForRendererSleep;
  private boolean offloadSchedulingEnabled;
  private int enabledRendererCount;
  @Nullable private SeekPosition pendingInitialSeekPosition;
  private long rendererPositionUs;
  private long rendererPositionElapsedRealtimeUs;
  private int nextPendingMessageIndexHint;
  private boolean deliverPendingMessageAtStartPositionRequired;
  @Nullable private ExoPlaybackException pendingRecoverableRendererError;
  private long setForegroundModeTimeoutMs;
  private long playbackMaybeBecameStuckAtMs;
  private PreloadConfiguration preloadConfiguration;
  private Timeline lastPreloadPoolInvalidationTimeline;
  private long prewarmingMediaPeriodDiscontinuity = C.TIME_UNSET;
  private boolean isPrewarmingDisabledUntilNextTransition;
  private float volume;

  public ExoPlayerImplInternal(
      Context context,
      Renderer[] renderers,
      Renderer[] secondaryRenderers,
      TrackSelector trackSelector,
      TrackSelectorResult emptyTrackSelectorResult,
      LoadControl loadControl,
      BandwidthMeter bandwidthMeter,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      AnalyticsCollector analyticsCollector,
      SeekParameters seekParameters,
      LivePlaybackSpeedControl livePlaybackSpeedControl,
      long releaseTimeoutMs,
      boolean pauseAtEndOfWindow,
      boolean dynamicSchedulingEnabled,
      Looper applicationLooper,
      Clock clock,
      PlaybackInfoUpdateListener playbackInfoUpdateListener,
      PlayerId playerId,
      @Nullable PlaybackLooperProvider playbackLooperProvider,
      PreloadConfiguration preloadConfiguration) {
    this.playbackInfoUpdateListener = playbackInfoUpdateListener;
    this.trackSelector = trackSelector;
    this.emptyTrackSelectorResult = emptyTrackSelectorResult;
    this.loadControl = loadControl;
    this.bandwidthMeter = bandwidthMeter;
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.seekParameters = seekParameters;
    this.livePlaybackSpeedControl = livePlaybackSpeedControl;
    this.releaseTimeoutMs = releaseTimeoutMs;
    this.setForegroundModeTimeoutMs = releaseTimeoutMs;
    this.pauseAtEndOfWindow = pauseAtEndOfWindow;
    this.dynamicSchedulingEnabled = dynamicSchedulingEnabled;
    this.clock = clock;
    this.playerId = playerId;
    this.preloadConfiguration = preloadConfiguration;
    this.analyticsCollector = analyticsCollector;
    this.volume = 1f;

    playbackMaybeBecameStuckAtMs = C.TIME_UNSET;
    lastRebufferRealtimeMs = C.TIME_UNSET;
    backBufferDurationUs = loadControl.getBackBufferDurationUs(playerId);
    retainBackBufferFromKeyframe = loadControl.retainBackBufferFromKeyframe(playerId);
    lastPreloadPoolInvalidationTimeline = Timeline.EMPTY;

    playbackInfo = PlaybackInfo.createDummy(emptyTrackSelectorResult);
    playbackInfoUpdate = new PlaybackInfoUpdate(playbackInfo);
    rendererCapabilities = new RendererCapabilities[renderers.length];
    rendererReportedReady = new boolean[renderers.length];
    @Nullable
    RendererCapabilities.Listener rendererCapabilitiesListener =
        trackSelector.getRendererCapabilitiesListener();

    boolean hasSecondaryRenderers = false;
    this.renderers = new RendererHolder[renderers.length];
    for (int i = 0; i < renderers.length; i++) {
      renderers[i].init(/* index= */ i, playerId, clock);
      rendererCapabilities[i] = renderers[i].getCapabilities();
      if (rendererCapabilitiesListener != null) {
        rendererCapabilities[i].setListener(rendererCapabilitiesListener);
      }
      if (secondaryRenderers[i] != null) {
        secondaryRenderers[i].init(/* index= */ i + renderers.length, playerId, clock);
        hasSecondaryRenderers = true;
      }
      this.renderers[i] = new RendererHolder(renderers[i], secondaryRenderers[i], /* index= */ i);
    }
    this.hasSecondaryRenderers = hasSecondaryRenderers;

    mediaClock = new DefaultMediaClock(this, clock);
    pendingMessages = new ArrayList<>();
    window = new Timeline.Window();
    period = new Timeline.Period();
    trackSelector.init(/* listener= */ this, bandwidthMeter);

    deliverPendingMessageAtStartPositionRequired = true;

    applicationLooperHandler = clock.createHandler(applicationLooper, /* callback= */ null);
    queue =
        new MediaPeriodQueue(
            analyticsCollector,
            applicationLooperHandler,
            this::createMediaPeriodHolder,
            preloadConfiguration);
    mediaSourceList =
        new MediaSourceList(
            /* listener= */ this, analyticsCollector, applicationLooperHandler, playerId);

    this.playbackLooperProvider =
        (playbackLooperProvider == null) ? new PlaybackLooperProvider() : playbackLooperProvider;
    this.playbackLooper = this.playbackLooperProvider.obtainLooper();
    handler = clock.createHandler(this.playbackLooper, this);

    audioFocusManager = new AudioFocusManager(context, playbackLooper, /* playerControl= */ this);
  }

  private MediaPeriodHolder createMediaPeriodHolder(
      MediaPeriodInfo mediaPeriodInfo, long rendererPositionOffsetUs) {
    return new MediaPeriodHolder(
        rendererCapabilities,
        rendererPositionOffsetUs,
        trackSelector,
        loadControl.getAllocator(),
        mediaSourceList,
        mediaPeriodInfo,
        emptyTrackSelectorResult,
        preloadConfiguration.targetPreloadDurationUs);
  }

  public void experimentalSetForegroundModeTimeoutMs(long setForegroundModeTimeoutMs) {
    this.setForegroundModeTimeoutMs = setForegroundModeTimeoutMs;
  }

  public void prepare() {
    handler.obtainMessage(MSG_PREPARE).sendToTarget();
  }

  public void setPlayWhenReady(
      boolean playWhenReady,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason,
      @PlaybackSuppressionReason int playbackSuppressionReason) {
    int combinedReasons = playbackSuppressionReason << 4 | playWhenReadyChangeReason;
    handler
        .obtainMessage(MSG_SET_PLAY_WHEN_READY, playWhenReady ? 1 : 0, combinedReasons)
        .sendToTarget();
  }

  public void setPauseAtEndOfWindow(boolean pauseAtEndOfWindow) {
    handler
        .obtainMessage(MSG_SET_PAUSE_AT_END_OF_WINDOW, pauseAtEndOfWindow ? 1 : 0, /* ignored */ 0)
        .sendToTarget();
  }

  public void setRepeatMode(@Player.RepeatMode int repeatMode) {
    handler.obtainMessage(MSG_SET_REPEAT_MODE, repeatMode, 0).sendToTarget();
  }

  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    handler.obtainMessage(MSG_SET_SHUFFLE_ENABLED, shuffleModeEnabled ? 1 : 0, 0).sendToTarget();
  }

  public void setPreloadConfiguration(PreloadConfiguration preloadConfiguration) {
    handler.obtainMessage(MSG_SET_PRELOAD_CONFIGURATION, preloadConfiguration).sendToTarget();
  }

  public void seekTo(Timeline timeline, int windowIndex, long positionUs) {
    handler
        .obtainMessage(MSG_SEEK_TO, new SeekPosition(timeline, windowIndex, positionUs))
        .sendToTarget();
  }

  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    handler.obtainMessage(MSG_SET_PLAYBACK_PARAMETERS, playbackParameters).sendToTarget();
  }

  public void setSeekParameters(SeekParameters seekParameters) {
    handler.obtainMessage(MSG_SET_SEEK_PARAMETERS, seekParameters).sendToTarget();
  }

  public void stop() {
    handler.obtainMessage(MSG_STOP).sendToTarget();
  }

  public void setMediaSources(
      List<MediaSourceList.MediaSourceHolder> mediaSources,
      int windowIndex,
      long positionUs,
      ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(
            MSG_SET_MEDIA_SOURCES,
            new MediaSourceListUpdateMessage(mediaSources, shuffleOrder, windowIndex, positionUs))
        .sendToTarget();
  }

  public void addMediaSources(
      int index, List<MediaSourceList.MediaSourceHolder> mediaSources, ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(
            MSG_ADD_MEDIA_SOURCES,
            index,
            /* ignored */ 0,
            new MediaSourceListUpdateMessage(
                mediaSources,
                shuffleOrder,
                /* windowIndex= */ C.INDEX_UNSET,
                /* positionUs= */ C.TIME_UNSET))
        .sendToTarget();
  }

  public void removeMediaSources(int fromIndex, int toIndex, ShuffleOrder shuffleOrder) {
    handler
        .obtainMessage(MSG_REMOVE_MEDIA_SOURCES, fromIndex, toIndex, shuffleOrder)
        .sendToTarget();
  }

  public void moveMediaSources(
      int fromIndex, int toIndex, int newFromIndex, ShuffleOrder shuffleOrder) {
    MoveMediaItemsMessage moveMediaItemsMessage =
        new MoveMediaItemsMessage(fromIndex, toIndex, newFromIndex, shuffleOrder);
    handler.obtainMessage(MSG_MOVE_MEDIA_SOURCES, moveMediaItemsMessage).sendToTarget();
  }

  public void setShuffleOrder(ShuffleOrder shuffleOrder) {
    handler.obtainMessage(MSG_SET_SHUFFLE_ORDER, shuffleOrder).sendToTarget();
  }

  public void updateMediaSourcesWithMediaItems(
      int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    handler
        .obtainMessage(MSG_UPDATE_MEDIA_SOURCES_WITH_MEDIA_ITEMS, fromIndex, toIndex, mediaItems)
        .sendToTarget();
  }

  public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    handler
        .obtainMessage(MSG_SET_AUDIO_ATTRIBUTES, handleAudioFocus ? 1 : 0, 0, audioAttributes)
        .sendToTarget();
  }

  public void setVolume(float volume) {
    handler.obtainMessage(MSG_SET_VOLUME, volume).sendToTarget();
  }

  private void handleAudioFocusPlayerCommandInternal(
      @AudioFocusManager.PlayerCommand int playerCommand) throws ExoPlaybackException {
    updatePlayWhenReadyWithAudioFocus(
        playbackInfo.playWhenReady,
        playerCommand,
        playbackInfo.playbackSuppressionReason,
        playbackInfo.playWhenReadyChangeReason);
  }

  private void handleAudioFocusVolumeMultiplierChange() throws ExoPlaybackException {
    setVolumeInternal(volume);
  }

  @Override
  public synchronized void sendMessage(PlayerMessage message) {
    if (released || !playbackLooper.getThread().isAlive()) {
      Log.w(TAG, "Ignoring messages sent after release.");
      message.markAsProcessed(/* isDelivered= */ false);
      return;
    }
    handler.obtainMessage(MSG_SEND_MESSAGE, message).sendToTarget();
  }

  /**
   * Sets the foreground mode.
   *
   * @param foregroundMode Whether foreground mode should be enabled.
   * @return Whether the operations succeeded. If false, the operation timed out.
   */
  public synchronized boolean setForegroundMode(boolean foregroundMode) {
    if (released || !playbackLooper.getThread().isAlive()) {
      return true;
    }
    if (foregroundMode) {
      handler.obtainMessage(MSG_SET_FOREGROUND_MODE, /* foregroundMode */ 1, 0).sendToTarget();
      return true;
    } else {
      AtomicBoolean processedFlag = new AtomicBoolean();
      handler
          .obtainMessage(MSG_SET_FOREGROUND_MODE, /* foregroundMode */ 0, 0, processedFlag)
          .sendToTarget();
      waitUninterruptibly(/* condition= */ processedFlag::get, setForegroundModeTimeoutMs);
      return processedFlag.get();
    }
  }

  /**
   * Sets the video output.
   *
   * <p>If the provided {@code timeoutMs} is {@link C#TIME_UNSET} then this method will not wait on
   * the message delivery.
   *
   * @param videoOutput Surface onto which which video will be rendered.
   * @param timeoutMs Timeout duration to wait for successful message delivery. If {@link
   *     C#TIME_UNSET} then the method will not block on the message delivery.
   * @return Whether the operation succeeded. If false, the operation timed out.
   */
  public synchronized boolean setVideoOutput(@Nullable Object videoOutput, long timeoutMs) {
    if (released || !playbackLooper.getThread().isAlive()) {
      return true;
    }
    AtomicBoolean processedFlag = new AtomicBoolean();
    handler
        .obtainMessage(MSG_SET_VIDEO_OUTPUT, new Pair<>(videoOutput, processedFlag))
        .sendToTarget();
    if (timeoutMs != C.TIME_UNSET) {
      waitUninterruptibly(/* condition= */ processedFlag::get, timeoutMs);
      return processedFlag.get();
    }
    return true;
  }

  /**
   * Releases the player.
   *
   * @return Whether the release succeeded. If false, the release timed out.
   */
  public synchronized boolean release() {
    if (released || !playbackLooper.getThread().isAlive()) {
      return true;
    }
    handler.sendEmptyMessage(MSG_RELEASE);
    waitUninterruptibly(/* condition= */ () -> released, releaseTimeoutMs);
    return released;
  }

  public Looper getPlaybackLooper() {
    return playbackLooper;
  }

  // Playlist.PlaylistInfoRefreshListener implementation.

  @Override
  public void onPlaylistUpdateRequested() {
    handler.removeMessages(MSG_DO_SOME_WORK);
    handler.sendEmptyMessage(MSG_PLAYLIST_UPDATE_REQUESTED);
  }

  // MediaPeriod.Callback implementation.

  @Override
  public void onPrepared(MediaPeriod source) {
    handler.obtainMessage(MSG_PERIOD_PREPARED, source).sendToTarget();
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    handler.obtainMessage(MSG_SOURCE_CONTINUE_LOADING_REQUESTED, source).sendToTarget();
  }

  // TrackSelector.InvalidationListener implementation.

  @Override
  public void onTrackSelectionsInvalidated() {
    handler.sendEmptyMessage(MSG_TRACK_SELECTION_INVALIDATED);
  }

  @Override
  public void onRendererCapabilitiesChanged(Renderer renderer) {
    handler.sendEmptyMessage(MSG_RENDERER_CAPABILITIES_CHANGED);
  }

  // DefaultMediaClock.PlaybackParametersListener implementation.

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters newPlaybackParameters) {
    handler
        .obtainMessage(MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL, newPlaybackParameters)
        .sendToTarget();
  }

  // AudioFocusManager.PlayerControl implementation

  @Override
  public void setVolumeMultiplier(float volumeMultiplier) {
    handler.sendEmptyMessage(MSG_AUDIO_FOCUS_VOLUME_MULTIPLIER);
  }

  @Override
  public void executePlayerCommand(@AudioFocusManager.PlayerCommand int playerCommand) {
    handler.obtainMessage(MSG_AUDIO_FOCUS_PLAYER_COMMAND, playerCommand, 0).sendToTarget();
  }

  // Handler.Callback implementation.

  @SuppressWarnings({"unchecked", "WrongConstant"}) // Casting message payload types and IntDef.
  @Override
  public boolean handleMessage(Message msg) {
    try {
      switch (msg.what) {
        case MSG_PREPARE:
          prepareInternal();
          break;
        case MSG_SET_PLAY_WHEN_READY:
          setPlayWhenReadyInternal(
              /* playWhenReady= */ msg.arg1 != 0,
              /* playbackSuppressionReason= */ msg.arg2 >> 4,
              /* operationAck= */ true,
              /* reason= */ msg.arg2 & 0x0F);
          break;
        case MSG_SET_REPEAT_MODE:
          setRepeatModeInternal(msg.arg1);
          break;
        case MSG_SET_SHUFFLE_ENABLED:
          setShuffleModeEnabledInternal(msg.arg1 != 0);
          break;
        case MSG_SET_PRELOAD_CONFIGURATION:
          setPreloadConfigurationInternal((PreloadConfiguration) msg.obj);
          break;
        case MSG_DO_SOME_WORK:
          doSomeWork();
          break;
        case MSG_SEEK_TO:
          seekToInternal((SeekPosition) msg.obj);
          break;
        case MSG_SET_PLAYBACK_PARAMETERS:
          setPlaybackParametersInternal((PlaybackParameters) msg.obj);
          break;
        case MSG_SET_SEEK_PARAMETERS:
          setSeekParametersInternal((SeekParameters) msg.obj);
          break;
        case MSG_SET_FOREGROUND_MODE:
          setForegroundModeInternal(
              /* foregroundMode= */ msg.arg1 != 0, /* processedFlag= */ (AtomicBoolean) msg.obj);
          break;
        case MSG_SET_VIDEO_OUTPUT:
          Pair<Object, AtomicBoolean> setVideoOutputPayload = (Pair<Object, AtomicBoolean>) msg.obj;
          setVideoOutputInternal(
              /* videoOutput= */ setVideoOutputPayload.first,
              /* processedFlag= */ setVideoOutputPayload.second);
          break;
        case MSG_STOP:
          stopInternal(/* forceResetRenderers= */ false, /* acknowledgeStop= */ true);
          break;
        case MSG_PERIOD_PREPARED:
          handlePeriodPrepared((MediaPeriod) msg.obj);
          break;
        case MSG_SOURCE_CONTINUE_LOADING_REQUESTED:
          handleContinueLoadingRequested((MediaPeriod) msg.obj);
          break;
        case MSG_TRACK_SELECTION_INVALIDATED:
          reselectTracksInternal();
          break;
        case MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL:
          handlePlaybackParameters((PlaybackParameters) msg.obj, /* acknowledgeCommand= */ false);
          break;
        case MSG_SEND_MESSAGE:
          sendMessageInternal((PlayerMessage) msg.obj);
          break;
        case MSG_SEND_MESSAGE_TO_TARGET_THREAD:
          sendMessageToTargetThread((PlayerMessage) msg.obj);
          break;
        case MSG_SET_MEDIA_SOURCES:
          setMediaItemsInternal((MediaSourceListUpdateMessage) msg.obj);
          break;
        case MSG_ADD_MEDIA_SOURCES:
          addMediaItemsInternal((MediaSourceListUpdateMessage) msg.obj, msg.arg1);
          break;
        case MSG_MOVE_MEDIA_SOURCES:
          moveMediaItemsInternal((MoveMediaItemsMessage) msg.obj);
          break;
        case MSG_REMOVE_MEDIA_SOURCES:
          removeMediaItemsInternal(msg.arg1, msg.arg2, (ShuffleOrder) msg.obj);
          break;
        case MSG_SET_SHUFFLE_ORDER:
          setShuffleOrderInternal((ShuffleOrder) msg.obj);
          break;
        case MSG_PLAYLIST_UPDATE_REQUESTED:
          mediaSourceListUpdateRequestedInternal();
          break;
        case MSG_SET_PAUSE_AT_END_OF_WINDOW:
          setPauseAtEndOfWindowInternal(msg.arg1 != 0);
          break;
        case MSG_ATTEMPT_RENDERER_ERROR_RECOVERY:
          attemptRendererErrorRecovery();
          break;
        case MSG_RENDERER_CAPABILITIES_CHANGED:
          reselectTracksInternalAndSeek();
          break;
        case MSG_UPDATE_MEDIA_SOURCES_WITH_MEDIA_ITEMS:
          updateMediaSourcesWithMediaItemsInternal(msg.arg1, msg.arg2, (List<MediaItem>) msg.obj);
          break;
        case MSG_SET_AUDIO_ATTRIBUTES:
          setAudioAttributesInternal(
              (AudioAttributes) msg.obj, /* handleAudioFocus= */ msg.arg1 != 0);
          break;
        case MSG_SET_VOLUME:
          setVolumeInternal((Float) msg.obj);
          break;
        case MSG_AUDIO_FOCUS_PLAYER_COMMAND:
          handleAudioFocusPlayerCommandInternal(/* playerCommand= */ msg.arg1);
          break;
        case MSG_AUDIO_FOCUS_VOLUME_MULTIPLIER:
          handleAudioFocusVolumeMultiplierChange();
          break;
        case MSG_RELEASE:
          releaseInternal();
          // Return immediately to not send playback info updates after release.
          return true;
        default:
          return false;
      }
    } catch (ExoPlaybackException e) {
      if (e.type == ExoPlaybackException.TYPE_RENDERER) {
        @Nullable MediaPeriodHolder readingPeriod = queue.getReadingPeriod();
        if (readingPeriod != null) {
          // We can assume that all renderer errors happen in the context of the reading period. See
          // [internal: b/150584930#comment4] for exceptions that aren't covered by this assumption.
          e =
              e.copyWithMediaPeriodId(
                  (renderers[e.rendererIndex % renderers.length].isRendererPrewarming(
                              e.rendererIndex)
                          && readingPeriod.getNext() != null)
                      ? readingPeriod.getNext().info.id
                      : readingPeriod.info.id);
        }
      }
      if (e.type == ExoPlaybackException.TYPE_RENDERER
          && renderers[e.rendererIndex % renderers.length].isRendererPrewarming(
              /* id= */ e.rendererIndex)) {
        // TODO(b/380273486): Investigate recovery for pre-warming renderer errors
        isPrewarmingDisabledUntilNextTransition = true;
        disableAndResetPrewarmingRenderers();
        // Remove periods from the queue starting at the pre-warming period.
        MediaPeriodHolder prewarmingPeriod = queue.getPrewarmingPeriod();
        MediaPeriodHolder periodToRemoveAfter = queue.getPlayingPeriod();
        if (queue.getPlayingPeriod() != prewarmingPeriod) {
          while (periodToRemoveAfter != null && periodToRemoveAfter.getNext() != prewarmingPeriod) {
            periodToRemoveAfter = periodToRemoveAfter.getNext();
          }
        }
        queue.removeAfter(periodToRemoveAfter);
        if (playbackInfo.playbackState != Player.STATE_ENDED) {
          maybeContinueLoading();
          handler.sendEmptyMessage(MSG_DO_SOME_WORK);
        }
      } else {
        if (pendingRecoverableRendererError != null) {
          pendingRecoverableRendererError.addSuppressed(e);
          e = pendingRecoverableRendererError;
        }

        if (e.type == ExoPlaybackException.TYPE_RENDERER
            && queue.getPlayingPeriod() != queue.getReadingPeriod()) {
          // We encountered a renderer error while reading ahead. Force-update the playback position
          // to the failing item to ensure correct retry or that the user-visible error is reported
          // after the transition.
          while (queue.getPlayingPeriod() != queue.getReadingPeriod()) {
            queue.advancePlayingPeriod();
          }
          MediaPeriodHolder newPlayingPeriodHolder = checkNotNull(queue.getPlayingPeriod());
          // Send already pending updates if needed before making further changes to PlaybackInfo.
          maybeNotifyPlaybackInfoChanged();
          playbackInfo =
              handlePositionDiscontinuity(
                  newPlayingPeriodHolder.info.id,
                  newPlayingPeriodHolder.info.startPositionUs,
                  newPlayingPeriodHolder.info.requestedContentPositionUs,
                  /* discontinuityStartPositionUs= */ newPlayingPeriodHolder.info.startPositionUs,
                  /* reportDiscontinuity= */ true,
                  Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
        }

        if (e.isRecoverable
            && (pendingRecoverableRendererError == null
                || e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED
                || e.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED)) {
          // Given that the player is now in an unhandled exception state, the error needs to be
          // recovered or the player stopped before any other message is handled.
          Log.w(TAG, "Recoverable renderer error", e);
          if (pendingRecoverableRendererError == null) {
            pendingRecoverableRendererError = e;
          }
          handler.sendMessageAtFrontOfQueue(
              handler.obtainMessage(MSG_ATTEMPT_RENDERER_ERROR_RECOVERY, e));
        } else {
          Log.e(TAG, "Playback error", e);
          stopInternal(/* forceResetRenderers= */ true, /* acknowledgeStop= */ false);
          playbackInfo = playbackInfo.copyWithPlaybackError(e);
        }
      }
    } catch (DrmSession.DrmSessionException e) {
      handleIoException(e, e.errorCode);
    } catch (ParserException e) {
      @ErrorCode int errorCode;
      if (e.dataType == C.DATA_TYPE_MEDIA) {
        errorCode =
            e.contentIsMalformed
                ? PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                : PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
      } else if (e.dataType == C.DATA_TYPE_MANIFEST) {
        errorCode =
            e.contentIsMalformed
                ? PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                : PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED;
      } else {
        errorCode = PlaybackException.ERROR_CODE_UNSPECIFIED;
      }
      handleIoException(e, errorCode);
    } catch (DataSourceException e) {
      handleIoException(e, e.reason);
    } catch (BehindLiveWindowException e) {
      handleIoException(e, PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW);
    } catch (IOException e) {
      handleIoException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    } catch (RuntimeException e) {
      @ErrorCode int errorCode;
      if (e instanceof IllegalStateException || e instanceof IllegalArgumentException) {
        errorCode = PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK;
      } else {
        errorCode = PlaybackException.ERROR_CODE_UNSPECIFIED;
      }
      ExoPlaybackException error = ExoPlaybackException.createForUnexpected(e, errorCode);
      Log.e(TAG, "Playback error", error);
      stopInternal(/* forceResetRenderers= */ true, /* acknowledgeStop= */ false);
      playbackInfo = playbackInfo.copyWithPlaybackError(error);
    }
    maybeNotifyPlaybackInfoChanged();
    return true;
  }

  // Private methods.

  private void handleIoException(IOException e, @ErrorCode int errorCode) {
    ExoPlaybackException error = ExoPlaybackException.createForSource(e, errorCode);
    @Nullable MediaPeriodHolder playingPeriod = queue.getPlayingPeriod();
    if (playingPeriod != null) {
      // We ensure that all IOException throwing methods are only executed for the playing period.
      error = error.copyWithMediaPeriodId(playingPeriod.info.id);
    }
    Log.e(TAG, "Playback error", error);
    stopInternal(/* forceResetRenderers= */ false, /* acknowledgeStop= */ false);
    playbackInfo = playbackInfo.copyWithPlaybackError(error);
  }

  /**
   * Blocks the current thread until a condition becomes true or the specified amount of time has
   * elapsed.
   *
   * <p>If the current thread is interrupted while waiting for the condition to become true, this
   * method will restore the interrupt <b>after</b> the condition became true or the operation times
   * out.
   *
   * @param condition The condition.
   * @param timeoutMs The time in milliseconds to wait for the condition to become true.
   */
  private synchronized void waitUninterruptibly(Supplier<Boolean> condition, long timeoutMs) {
    long deadlineMs = clock.elapsedRealtime() + timeoutMs;
    long remainingMs = timeoutMs;
    boolean wasInterrupted = false;
    while (!condition.get() && remainingMs > 0) {
      try {
        clock.onThreadBlocked();
        wait(remainingMs);
      } catch (InterruptedException e) {
        wasInterrupted = true;
      }
      remainingMs = deadlineMs - clock.elapsedRealtime();
    }
    if (wasInterrupted) {
      // Restore the interrupted status.
      Thread.currentThread().interrupt();
    }
  }

  private void setState(int state) {
    if (playbackInfo.playbackState != state) {
      if (state != Player.STATE_BUFFERING) {
        playbackMaybeBecameStuckAtMs = C.TIME_UNSET;
      }
      playbackInfo = playbackInfo.copyWithPlaybackState(state);
    }
  }

  private void maybeNotifyPlaybackInfoChanged() {
    playbackInfoUpdate.setPlaybackInfo(playbackInfo);
    if (playbackInfoUpdate.hasPendingChange) {
      playbackInfoUpdateListener.onPlaybackInfoUpdate(playbackInfoUpdate);
      playbackInfoUpdate = new PlaybackInfoUpdate(playbackInfo);
    }
  }

  private void prepareInternal() throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    resetInternal(
        /* resetRenderers= */ false,
        /* resetPosition= */ false,
        /* releaseMediaSourceList= */ false,
        /* resetError= */ true);
    loadControl.onPrepared(playerId);
    setState(playbackInfo.timeline.isEmpty() ? Player.STATE_ENDED : Player.STATE_BUFFERING);
    updatePlayWhenReadyWithAudioFocus();
    mediaSourceList.prepare(bandwidthMeter.getTransferListener());
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
  }

  private void setMediaItemsInternal(MediaSourceListUpdateMessage mediaSourceListUpdateMessage)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    if (mediaSourceListUpdateMessage.windowIndex != C.INDEX_UNSET) {
      pendingInitialSeekPosition =
          new SeekPosition(
              new PlaylistTimeline(
                  mediaSourceListUpdateMessage.mediaSourceHolders,
                  mediaSourceListUpdateMessage.shuffleOrder),
              mediaSourceListUpdateMessage.windowIndex,
              mediaSourceListUpdateMessage.positionUs);
    }
    Timeline timeline =
        mediaSourceList.setMediaSources(
            mediaSourceListUpdateMessage.mediaSourceHolders,
            mediaSourceListUpdateMessage.shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline, /* isSourceRefresh= */ false);
  }

  private void addMediaItemsInternal(MediaSourceListUpdateMessage addMessage, int insertionIndex)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline =
        mediaSourceList.addMediaSources(
            insertionIndex == C.INDEX_UNSET ? mediaSourceList.getSize() : insertionIndex,
            addMessage.mediaSourceHolders,
            addMessage.shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline, /* isSourceRefresh= */ false);
  }

  private void moveMediaItemsInternal(MoveMediaItemsMessage moveMediaItemsMessage)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline =
        mediaSourceList.moveMediaSourceRange(
            moveMediaItemsMessage.fromIndex,
            moveMediaItemsMessage.toIndex,
            moveMediaItemsMessage.newFromIndex,
            moveMediaItemsMessage.shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline, /* isSourceRefresh= */ false);
  }

  private void removeMediaItemsInternal(int fromIndex, int toIndex, ShuffleOrder shuffleOrder)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline = mediaSourceList.removeMediaSourceRange(fromIndex, toIndex, shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline, /* isSourceRefresh= */ false);
  }

  private void mediaSourceListUpdateRequestedInternal() throws ExoPlaybackException {
    handleMediaSourceListInfoRefreshed(
        mediaSourceList.createTimeline(), /* isSourceRefresh= */ true);
  }

  private void setShuffleOrderInternal(ShuffleOrder shuffleOrder) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline = mediaSourceList.setShuffleOrder(shuffleOrder);
    handleMediaSourceListInfoRefreshed(timeline, /* isSourceRefresh= */ false);
  }

  private void updateMediaSourcesWithMediaItemsInternal(
      int fromIndex, int toIndex, List<MediaItem> mediaItems) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);
    Timeline timeline =
        mediaSourceList.updateMediaSourcesWithMediaItems(fromIndex, toIndex, mediaItems);
    handleMediaSourceListInfoRefreshed(timeline, /* isSourceRefresh= */ false);
  }

  private void setAudioAttributesInternal(AudioAttributes audioAttributes, boolean handleAudioFocus)
      throws ExoPlaybackException {
    trackSelector.setAudioAttributes(audioAttributes);
    audioFocusManager.setAudioAttributes(handleAudioFocus ? audioAttributes : null);
    updatePlayWhenReadyWithAudioFocus();
  }

  private void setVolumeInternal(float volume) throws ExoPlaybackException {
    this.volume = volume;
    float scaledVolume = volume * audioFocusManager.getVolumeMultiplier();
    for (RendererHolder renderer : renderers) {
      renderer.setVolume(scaledVolume);
    }
  }

  private void notifyTrackSelectionPlayWhenReadyChanged(boolean playWhenReady) {
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    while (periodHolder != null) {
      for (ExoTrackSelection trackSelection : periodHolder.getTrackSelectorResult().selections) {
        if (trackSelection != null) {
          trackSelection.onPlayWhenReadyChanged(playWhenReady);
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private void setPlayWhenReadyInternal(
      boolean playWhenReady,
      @PlaybackSuppressionReason int playbackSuppressionReason,
      boolean operationAck,
      @Player.PlayWhenReadyChangeReason int reason)
      throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(operationAck ? 1 : 0);
    updatePlayWhenReadyWithAudioFocus(playWhenReady, playbackSuppressionReason, reason);
  }

  private void updatePlayWhenReadyWithAudioFocus() throws ExoPlaybackException {
    updatePlayWhenReadyWithAudioFocus(
        playbackInfo.playWhenReady,
        playbackInfo.playbackSuppressionReason,
        playbackInfo.playWhenReadyChangeReason);
  }

  private void updatePlayWhenReadyWithAudioFocus(
      boolean playWhenReady,
      @PlaybackSuppressionReason int playbackSuppressionReason,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason)
      throws ExoPlaybackException {
    @AudioFocusManager.PlayerCommand
    int playerCommand =
        audioFocusManager.updateAudioFocus(playWhenReady, playbackInfo.playbackState);
    updatePlayWhenReadyWithAudioFocus(
        playWhenReady, playerCommand, playbackSuppressionReason, playWhenReadyChangeReason);
  }

  private void updatePlayWhenReadyWithAudioFocus(
      boolean playWhenReady,
      @AudioFocusManager.PlayerCommand int playerCommand,
      @PlaybackSuppressionReason int playbackSuppressionReason,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason)
      throws ExoPlaybackException {
    playWhenReady = playWhenReady && playerCommand != AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY;
    playWhenReadyChangeReason =
        updatePlayWhenReadyChangeReason(playerCommand, playWhenReadyChangeReason);
    playbackSuppressionReason =
        updatePlaybackSuppressionReason(playerCommand, playbackSuppressionReason);
    if (playbackInfo.playWhenReady == playWhenReady
        && playbackInfo.playbackSuppressionReason == playbackSuppressionReason
        && playbackInfo.playWhenReadyChangeReason == playWhenReadyChangeReason) {
      return;
    }
    playbackInfo =
        playbackInfo.copyWithPlayWhenReady(
            playWhenReady, playWhenReadyChangeReason, playbackSuppressionReason);
    updateRebufferingState(/* isRebuffering= */ false, /* resetLastRebufferRealtimeMs= */ false);
    notifyTrackSelectionPlayWhenReadyChanged(playWhenReady);
    if (!shouldPlayWhenReady()) {
      stopRenderers();
      updatePlaybackPositions();
      queue.reevaluateBuffer(rendererPositionUs);
    } else {
      if (playbackInfo.playbackState == Player.STATE_READY) {
        mediaClock.start();
        startRenderers();
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      } else if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    }
  }

  private void setPauseAtEndOfWindowInternal(boolean pauseAtEndOfWindow)
      throws ExoPlaybackException {
    this.pauseAtEndOfWindow = pauseAtEndOfWindow;
    resetPendingPauseAtEndOfPeriod();
    if (pendingPauseAtEndOfPeriod && queue.getReadingPeriod() != queue.getPlayingPeriod()) {
      // When pausing is required, we need to set the streams of the playing period final. If we
      // already started reading the next period, we need to flush the renderers.
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
      handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
    }
  }

  private void setOffloadSchedulingEnabled(boolean offloadSchedulingEnabled) {
    if (offloadSchedulingEnabled == this.offloadSchedulingEnabled) {
      return;
    }
    this.offloadSchedulingEnabled = offloadSchedulingEnabled;
    if (!offloadSchedulingEnabled && playbackInfo.sleepingForOffload) {
      // We need to wake the player up if offload scheduling is disabled and we are sleeping.
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void setRepeatModeInternal(@Player.RepeatMode int repeatMode)
      throws ExoPlaybackException {
    this.repeatMode = repeatMode;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int result = queue.updateRepeatMode(playbackInfo.timeline, repeatMode);
    if ((result & UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD) != 0) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    } else if ((result & UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD) != 0) {
      disableAndResetPrewarmingRenderers();
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void setShuffleModeEnabledInternal(boolean shuffleModeEnabled)
      throws ExoPlaybackException {
    this.shuffleModeEnabled = shuffleModeEnabled;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int result = queue.updateShuffleModeEnabled(playbackInfo.timeline, shuffleModeEnabled);
    if ((result & UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD) != 0) {
      seekToCurrentPosition(/* sendDiscontinuity= */ true);
    } else if ((result & UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD) != 0) {
      disableAndResetPrewarmingRenderers();
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
  }

  private void setPreloadConfigurationInternal(PreloadConfiguration preloadConfiguration) {
    this.preloadConfiguration = preloadConfiguration;
    queue.updatePreloadConfiguration(playbackInfo.timeline, preloadConfiguration);
  }

  private void seekToCurrentPosition(boolean sendDiscontinuity) throws ExoPlaybackException {
    // Renderers may have read from a period that's been removed. Seek back to the current
    // position of the playing period to make sure none of the removed period is played.
    MediaPeriodId periodId = queue.getPlayingPeriod().info.id;
    long newPositionUs =
        seekToPeriodPosition(
            periodId,
            playbackInfo.positionUs,
            /* forceDisableRenderers= */ true,
            /* forceBufferingState= */ false);
    if (newPositionUs != playbackInfo.positionUs) {
      playbackInfo =
          handlePositionDiscontinuity(
              periodId,
              newPositionUs,
              playbackInfo.requestedContentPositionUs,
              playbackInfo.discontinuityStartPositionUs,
              sendDiscontinuity,
              Player.DISCONTINUITY_REASON_INTERNAL);
    }
  }

  private void startRenderers() throws ExoPlaybackException {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      return;
    }
    TrackSelectorResult trackSelectorResult = playingPeriodHolder.getTrackSelectorResult();
    for (int i = 0; i < renderers.length; i++) {
      if (!trackSelectorResult.isRendererEnabled(i)) {
        continue;
      }
      renderers[i].start();
    }
  }

  private void stopRenderers() throws ExoPlaybackException {
    mediaClock.stop();
    for (RendererHolder rendererHolder : renderers) {
      rendererHolder.stop();
    }
  }

  private void attemptRendererErrorRecovery() throws ExoPlaybackException {
    reselectTracksInternalAndSeek();
  }

  private void updatePlaybackPositions() throws ExoPlaybackException {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      return;
    }

    // Update the playback position.
    long discontinuityPositionUs =
        playingPeriodHolder.prepared
            ? playingPeriodHolder.mediaPeriod.readDiscontinuity()
            : C.TIME_UNSET;
    if (discontinuityPositionUs != C.TIME_UNSET) {
      if (!playingPeriodHolder.isFullyBuffered()) {
        // The discontinuity caused the period to not be fully buffered. Continue loading from this
        // period again and discard all other periods we already started loading.
        queue.removeAfter(playingPeriodHolder);
        handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
        maybeContinueLoading();
      }
      resetRendererPosition(discontinuityPositionUs);
      // A MediaPeriod may report a discontinuity at the current playback position to ensure the
      // renderers are flushed. Only report the discontinuity externally if the position changed.
      if (discontinuityPositionUs != playbackInfo.positionUs) {
        playbackInfo =
            handlePositionDiscontinuity(
                playbackInfo.periodId,
                /* positionUs= */ discontinuityPositionUs,
                playbackInfo.requestedContentPositionUs,
                /* discontinuityStartPositionUs= */ discontinuityPositionUs,
                /* reportDiscontinuity= */ true,
                Player.DISCONTINUITY_REASON_INTERNAL);
      }
    } else {
      rendererPositionUs =
          mediaClock.syncAndGetPositionUs(
              /* isReadingAhead= */ playingPeriodHolder != queue.getReadingPeriod());
      long periodPositionUs = playingPeriodHolder.toPeriodTime(rendererPositionUs);
      maybeTriggerPendingMessages(playbackInfo.positionUs, periodPositionUs);
      if (mediaClock.hasSkippedSilenceSinceLastCall()) {
        // Only report silence skipping if there isn't already another discontinuity.
        boolean reportSilenceSkip = !playbackInfoUpdate.positionDiscontinuity;
        playbackInfo =
            handlePositionDiscontinuity(
                playbackInfo.periodId,
                /* positionUs= */ periodPositionUs,
                playbackInfo.requestedContentPositionUs,
                /* discontinuityStartPositionUs= */ periodPositionUs,
                /* reportDiscontinuity= */ reportSilenceSkip,
                Player.DISCONTINUITY_REASON_SILENCE_SKIP);
      } else {
        playbackInfo.updatePositionUs(periodPositionUs);
      }
    }

    // Update the buffered position and total buffered duration.
    MediaPeriodHolder loadingPeriod = queue.getLoadingPeriod();
    playbackInfo.bufferedPositionUs = loadingPeriod.getBufferedPositionUs();
    playbackInfo.totalBufferedDurationUs = getTotalBufferedDurationUs();

    // Adjust live playback speed to new position.
    if (playbackInfo.playWhenReady
        && playbackInfo.playbackState == Player.STATE_READY
        && shouldUseLivePlaybackSpeedControl(playbackInfo.timeline, playbackInfo.periodId)
        && playbackInfo.playbackParameters.speed == 1f) {
      float adjustedSpeed =
          livePlaybackSpeedControl.getAdjustedPlaybackSpeed(
              getCurrentLiveOffsetUs(), playbackInfo.totalBufferedDurationUs);
      if (mediaClock.getPlaybackParameters().speed != adjustedSpeed) {
        setMediaClockPlaybackParameters(playbackInfo.playbackParameters.withSpeed(adjustedSpeed));
        handlePlaybackParameters(
            playbackInfo.playbackParameters,
            /* currentPlaybackSpeed= */ mediaClock.getPlaybackParameters().speed,
            /* updatePlaybackInfo= */ false,
            /* acknowledgeCommand= */ false);
      }
    }
  }

  private void setMediaClockPlaybackParameters(PlaybackParameters playbackParameters) {
    // Previously sent speed updates from the media clock now become stale.
    handler.removeMessages(MSG_PLAYBACK_PARAMETERS_CHANGED_INTERNAL);
    mediaClock.setPlaybackParameters(playbackParameters);
  }

  private void notifyTrackSelectionRebuffer() {
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    while (periodHolder != null) {
      for (ExoTrackSelection trackSelection : periodHolder.getTrackSelectorResult().selections) {
        if (trackSelection != null) {
          trackSelection.onRebuffer();
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private void doSomeWork() throws ExoPlaybackException, IOException {
    long operationStartTimeMs = clock.uptimeMillis();
    // Remove other pending DO_SOME_WORK requests that are handled by this invocation.
    handler.removeMessages(MSG_DO_SOME_WORK);

    updatePeriods();

    if (playbackInfo.playbackState == Player.STATE_IDLE
        || playbackInfo.playbackState == Player.STATE_ENDED) {
      // Nothing to do. Prepare (in case of IDLE) or seek (in case of ENDED) will resume.
      return;
    }

    @Nullable MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      // We're still waiting until the playing period is available.
      scheduleNextWork(operationStartTimeMs);
      return;
    }

    TraceUtil.beginSection("doSomeWork");

    updatePlaybackPositions();

    boolean renderersEnded = true;
    boolean renderersAllowPlayback = true;
    if (playingPeriodHolder.prepared) {
      rendererPositionElapsedRealtimeUs = msToUs(clock.elapsedRealtime());
      playingPeriodHolder.mediaPeriod.discardBuffer(
          playbackInfo.positionUs - backBufferDurationUs, retainBackBufferFromKeyframe);
      for (int i = 0; i < renderers.length; i++) {
        RendererHolder renderer = renderers[i];
        if (renderer.getEnabledRendererCount() == 0) {
          maybeTriggerOnRendererReadyChanged(/* rendererIndex= */ i, /* allowsPlayback= */ false);
          continue;
        }
        // TODO: Each renderer should return the maximum delay before which it wishes to be called
        // again. The minimum of these values should then be used as the delay before the next
        // invocation of this method.
        renderer.render(rendererPositionUs, rendererPositionElapsedRealtimeUs);
        // Determine whether the renderer allows playback to continue. Playback can
        // continue if the renderer is ready or ended. Also continue playback if the renderer is
        // reading ahead into the next stream or is waiting for the next stream. This is to avoid
        // getting stuck if tracks in the current period have uneven durations and are still being
        // read by another renderer. See: https://github.com/google/ExoPlayer/issues/1874.
        renderersEnded = renderersEnded && renderer.isEnded();
        boolean allowsPlayback = renderer.allowsPlayback(playingPeriodHolder);
        maybeTriggerOnRendererReadyChanged(/* rendererIndex= */ i, allowsPlayback);
        renderersAllowPlayback = renderersAllowPlayback && allowsPlayback;
        if (!allowsPlayback) {
          maybeThrowRendererStreamError(/* rendererIndex= */ i);
        }
      }
    } else {
      playingPeriodHolder.mediaPeriod.maybeThrowPrepareError();
    }

    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    boolean finishedRendering =
        renderersEnded
            && playingPeriodHolder.prepared
            && (playingPeriodDurationUs == C.TIME_UNSET
                || playingPeriodDurationUs <= playbackInfo.positionUs);
    if (finishedRendering && pendingPauseAtEndOfPeriod) {
      pendingPauseAtEndOfPeriod = false;
      setPlayWhenReadyInternal(
          /* playWhenReady= */ false,
          playbackInfo.playbackSuppressionReason,
          /* operationAck= */ false,
          Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM);
    }
    if (finishedRendering && playingPeriodHolder.info.isFinal) {
      setState(Player.STATE_ENDED);
      stopRenderers();
    } else if (playbackInfo.playbackState == Player.STATE_BUFFERING
        && shouldTransitionToReadyState(renderersAllowPlayback)) {
      setState(Player.STATE_READY);
      pendingRecoverableRendererError = null; // Any pending error was successfully recovered from.
      if (shouldPlayWhenReady()) {
        updateRebufferingState(
            /* isRebuffering= */ false, /* resetLastRebufferRealtimeMs= */ false);
        mediaClock.start();
        startRenderers();
      }
    } else if (playbackInfo.playbackState == Player.STATE_READY
        && !(enabledRendererCount == 0 ? isTimelineReady() : renderersAllowPlayback)) {
      updateRebufferingState(
          /* isRebuffering= */ shouldPlayWhenReady(), /* resetLastRebufferRealtimeMs= */ false);
      setState(Player.STATE_BUFFERING);
      if (isRebuffering) {
        notifyTrackSelectionRebuffer();
        livePlaybackSpeedControl.notifyRebuffer();
      }
      stopRenderers();
    }

    boolean playbackMaybeStuck = false;
    if (playbackInfo.playbackState == Player.STATE_BUFFERING) {
      for (int i = 0; i < renderers.length; i++) {
        if (renderers[i].isReadingFromPeriod(playingPeriodHolder)) {
          maybeThrowRendererStreamError(/* rendererIndex= */ i);
        }
      }
      if (!playbackInfo.isLoading
          && playbackInfo.totalBufferedDurationUs < PLAYBACK_BUFFER_EMPTY_THRESHOLD_US
          && isLoadingPossible(queue.getLoadingPeriod())
          && shouldPlayWhenReady()) {
        // The renderers are not ready, there is more media available to load, and the LoadControl
        // is refusing to load it (indicated by !playbackInfo.isLoading). This could be because the
        // renderers are still transitioning to their ready states, but it could also indicate a
        // stuck playback. The playbackInfo.totalBufferedDurationUs check further isolates the
        // cause to a lack of media for the renderers to consume, to avoid classifying playbacks as
        // stuck when they're waiting for other reasons (in particular, loading DRM keys).
        playbackMaybeStuck = true;
      }
    }

    if (!playbackMaybeStuck) {
      playbackMaybeBecameStuckAtMs = C.TIME_UNSET;
    } else if (playbackMaybeBecameStuckAtMs == C.TIME_UNSET) {
      playbackMaybeBecameStuckAtMs = clock.elapsedRealtime();
    } else if (clock.elapsedRealtime() - playbackMaybeBecameStuckAtMs >= PLAYBACK_STUCK_AFTER_MS) {
      throw new IllegalStateException("Playback stuck buffering and not loading");
    }

    boolean isPlaying = shouldPlayWhenReady() && playbackInfo.playbackState == Player.STATE_READY;
    boolean sleepingForOffload = offloadSchedulingEnabled && requestForRendererSleep && isPlaying;
    if (playbackInfo.sleepingForOffload != sleepingForOffload) {
      playbackInfo = playbackInfo.copyWithSleepingForOffload(sleepingForOffload);
    }
    requestForRendererSleep = false; // A sleep request is only valid for the current doSomeWork.

    if (sleepingForOffload || playbackInfo.playbackState == Player.STATE_ENDED) {
      // No need to schedule next work.
    } else if ((isPlaying || playbackInfo.playbackState == Player.STATE_BUFFERING)
        || (playbackInfo.playbackState == Player.STATE_READY && enabledRendererCount != 0)) {
      // Schedule next work as either we are actively playing, buffering, or we
      // are ready but not playing.
      scheduleNextWork(operationStartTimeMs);
    }

    TraceUtil.endSection();
  }

  private void maybeTriggerOnRendererReadyChanged(int rendererIndex, boolean allowsPlayback) {
    if (rendererReportedReady[rendererIndex] != allowsPlayback) {
      rendererReportedReady[rendererIndex] = allowsPlayback;
      applicationLooperHandler.post(
          () ->
              analyticsCollector.onRendererReadyChanged(
                  rendererIndex, renderers[rendererIndex].getTrackType(), allowsPlayback));
    }
  }

  private long getCurrentLiveOffsetUs() {
    return getLiveOffsetUs(
        playbackInfo.timeline, playbackInfo.periodId.periodUid, playbackInfo.positionUs);
  }

  private long getLiveOffsetUs(Timeline timeline, Object periodUid, long periodPositionUs) {
    int windowIndex = timeline.getPeriodByUid(periodUid, period).windowIndex;
    timeline.getWindow(windowIndex, window);
    if (window.windowStartTimeMs == C.TIME_UNSET || !window.isLive() || !window.isDynamic) {
      return C.TIME_UNSET;
    }
    return Util.msToUs(window.getCurrentUnixTimeMs() - window.windowStartTimeMs)
        - (periodPositionUs + period.getPositionInWindowUs());
  }

  private boolean shouldUseLivePlaybackSpeedControl(
      Timeline timeline, MediaPeriodId mediaPeriodId) {
    if (mediaPeriodId.isAd() || timeline.isEmpty()) {
      return false;
    }
    int windowIndex = timeline.getPeriodByUid(mediaPeriodId.periodUid, period).windowIndex;
    timeline.getWindow(windowIndex, window);
    return window.isLive() && window.isDynamic && window.windowStartTimeMs != C.TIME_UNSET;
  }

  private void scheduleNextWork(long thisOperationStartTimeMs) {
    long wakeUpTimeIntervalMs =
        playbackInfo.playbackState == Player.STATE_READY
                && (dynamicSchedulingEnabled || !shouldPlayWhenReady())
            ? READY_MAXIMUM_INTERVAL_MS
            : BUFFERING_MAXIMUM_INTERVAL_MS;
    if (dynamicSchedulingEnabled && shouldPlayWhenReady()) {
      for (RendererHolder rendererHolder : renderers) {
        wakeUpTimeIntervalMs =
            min(
                wakeUpTimeIntervalMs,
                Util.usToMs(
                    rendererHolder.getMinDurationToProgressUs(
                        rendererPositionUs, rendererPositionElapsedRealtimeUs)));
      }

      // Do not schedule next doSomeWork past the playing period transition point.
      MediaPeriodHolder nextPlayingPeriodHolder =
          queue.getPlayingPeriod() != null ? queue.getPlayingPeriod().getNext() : null;
      if (nextPlayingPeriodHolder != null
          && rendererPositionUs
                  + msToUs(wakeUpTimeIntervalMs) * playbackInfo.playbackParameters.speed
              >= nextPlayingPeriodHolder.getStartPositionRendererTime()) {
        wakeUpTimeIntervalMs = min(wakeUpTimeIntervalMs, BUFFERING_MAXIMUM_INTERVAL_MS);
      }
    }
    handler.sendEmptyMessageAtTime(
        MSG_DO_SOME_WORK, thisOperationStartTimeMs + wakeUpTimeIntervalMs);
  }

  private void seekToInternal(SeekPosition seekPosition) throws ExoPlaybackException {
    playbackInfoUpdate.incrementPendingOperationAcks(/* operationAcks= */ 1);

    MediaPeriodId periodId;
    long periodPositionUs;
    long requestedContentPositionUs;
    boolean seekPositionAdjusted;
    @Nullable
    Pair<Object, Long> resolvedSeekPosition =
        resolveSeekPositionUs(
            playbackInfo.timeline,
            seekPosition,
            /* trySubsequentPeriods= */ true,
            repeatMode,
            shuffleModeEnabled,
            window,
            period);
    if (resolvedSeekPosition == null) {
      // The seek position was valid for the timeline that it was performed into, but the
      // timeline has changed or is not ready and a suitable seek position could not be resolved.
      Pair<MediaPeriodId, Long> firstPeriodAndPositionUs =
          getPlaceholderFirstMediaPeriodPositionUs(playbackInfo.timeline);
      periodId = firstPeriodAndPositionUs.first;
      periodPositionUs = firstPeriodAndPositionUs.second;
      requestedContentPositionUs = C.TIME_UNSET;
      seekPositionAdjusted = !playbackInfo.timeline.isEmpty();
    } else {
      // Update the resolved seek position to take ads into account.
      Object periodUid = resolvedSeekPosition.first;
      long resolvedContentPositionUs = resolvedSeekPosition.second;
      requestedContentPositionUs =
          seekPosition.windowPositionUs == C.TIME_UNSET ? C.TIME_UNSET : resolvedContentPositionUs;
      periodId =
          queue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
              playbackInfo.timeline, periodUid, resolvedContentPositionUs);
      if (periodId.isAd()) {
        playbackInfo.timeline.getPeriodByUid(periodId.periodUid, period);
        periodPositionUs =
            period.getFirstAdIndexToPlay(periodId.adGroupIndex) == periodId.adIndexInAdGroup
                ? period.getAdResumePositionUs()
                : 0;
        seekPositionAdjusted = true;
      } else {
        periodPositionUs = resolvedContentPositionUs;
        seekPositionAdjusted = seekPosition.windowPositionUs == C.TIME_UNSET;
      }
    }

    try {
      if (playbackInfo.timeline.isEmpty()) {
        // Save seek position for later, as we are still waiting for a prepared source.
        pendingInitialSeekPosition = seekPosition;
      } else if (resolvedSeekPosition == null) {
        // End playback, as we didn't manage to find a valid seek position.
        if (playbackInfo.playbackState != Player.STATE_IDLE) {
          setState(Player.STATE_ENDED);
        }
        resetInternal(
            /* resetRenderers= */ false,
            /* resetPosition= */ true,
            /* releaseMediaSourceList= */ false,
            /* resetError= */ true);
      } else {
        // Execute the seek in the current media periods.
        long newPeriodPositionUs = periodPositionUs;
        if (periodId.equals(playbackInfo.periodId)) {
          MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
          if (playingPeriodHolder != null
              && playingPeriodHolder.prepared
              && newPeriodPositionUs != 0) {
            newPeriodPositionUs =
                playingPeriodHolder.mediaPeriod.getAdjustedSeekPositionUs(
                    newPeriodPositionUs, seekParameters);
          }
          if (Util.usToMs(newPeriodPositionUs) == Util.usToMs(playbackInfo.positionUs)
              && (playbackInfo.playbackState == Player.STATE_BUFFERING
                  || playbackInfo.playbackState == Player.STATE_READY)) {
            // Seek will be performed to the current position. Do nothing.
            periodPositionUs = playbackInfo.positionUs;
            return;
          }
        }
        newPeriodPositionUs =
            seekToPeriodPosition(
                periodId,
                newPeriodPositionUs,
                /* forceBufferingState= */ playbackInfo.playbackState == Player.STATE_ENDED);
        seekPositionAdjusted |= periodPositionUs != newPeriodPositionUs;
        periodPositionUs = newPeriodPositionUs;
        updatePlaybackSpeedSettingsForNewPeriod(
            /* newTimeline= */ playbackInfo.timeline,
            /* newPeriodId= */ periodId,
            /* oldTimeline= */ playbackInfo.timeline,
            /* oldPeriodId= */ playbackInfo.periodId,
            /* positionForTargetOffsetOverrideUs= */ requestedContentPositionUs,
            /* forceSetTargetOffsetOverride= */ true);
      }
    } finally {
      playbackInfo =
          handlePositionDiscontinuity(
              periodId,
              periodPositionUs,
              requestedContentPositionUs,
              /* discontinuityStartPositionUs= */ periodPositionUs,
              /* reportDiscontinuity= */ seekPositionAdjusted,
              Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT);
    }
  }

  private long seekToPeriodPosition(
      MediaPeriodId periodId, long periodPositionUs, boolean forceBufferingState)
      throws ExoPlaybackException {
    // Force disable renderers if they are reading from a period other than the one being played.
    return seekToPeriodPosition(
        periodId,
        periodPositionUs,
        queue.getPlayingPeriod() != queue.getReadingPeriod(),
        forceBufferingState);
  }

  private long seekToPeriodPosition(
      MediaPeriodId periodId,
      long periodPositionUs,
      boolean forceDisableRenderers,
      boolean forceBufferingState)
      throws ExoPlaybackException {
    stopRenderers();
    updateRebufferingState(/* isRebuffering= */ false, /* resetLastRebufferRealtimeMs= */ true);
    if (forceBufferingState || playbackInfo.playbackState == Player.STATE_READY) {
      setState(Player.STATE_BUFFERING);
    }

    // Find the requested period if it already exists.
    @Nullable MediaPeriodHolder oldPlayingPeriodHolder = queue.getPlayingPeriod();
    @Nullable MediaPeriodHolder newPlayingPeriodHolder = oldPlayingPeriodHolder;
    while (newPlayingPeriodHolder != null) {
      if (periodId.equals(newPlayingPeriodHolder.info.id)) {
        break;
      }
      newPlayingPeriodHolder = newPlayingPeriodHolder.getNext();
    }

    // Disable all renderers if the period being played is changing, if the seek results in negative
    // renderer timestamps, or if forced.
    if (forceDisableRenderers
        || oldPlayingPeriodHolder != newPlayingPeriodHolder
        || (newPlayingPeriodHolder != null
            && newPlayingPeriodHolder.toRendererTime(periodPositionUs) < 0)) {
      disableRenderers();
      if (newPlayingPeriodHolder != null) {
        // Update the queue and reenable renderers if the requested media period already exists.
        while (queue.getPlayingPeriod() != newPlayingPeriodHolder) {
          queue.advancePlayingPeriod();
        }
        queue.removeAfter(newPlayingPeriodHolder);
        newPlayingPeriodHolder.setRendererOffset(
            MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US);
        enableRenderers();
        newPlayingPeriodHolder.allRenderersInCorrectState = true;
      }
    }

    // Disable pre-warming as following logic will reset any pre-warming media periods.
    disableAndResetPrewarmingRenderers();

    // Do the actual seeking.
    if (newPlayingPeriodHolder != null) {
      queue.removeAfter(newPlayingPeriodHolder);
      if (!newPlayingPeriodHolder.prepared) {
        newPlayingPeriodHolder.info =
            newPlayingPeriodHolder.info.copyWithStartPositionUs(periodPositionUs);
      } else if (newPlayingPeriodHolder.hasEnabledTracks) {
        periodPositionUs = newPlayingPeriodHolder.mediaPeriod.seekToUs(periodPositionUs);
        newPlayingPeriodHolder.mediaPeriod.discardBuffer(
            periodPositionUs - backBufferDurationUs, retainBackBufferFromKeyframe);
      }
      resetRendererPosition(periodPositionUs);
      maybeContinueLoading();
    } else {
      // New period has not been prepared.
      queue.clear();
      resetRendererPosition(periodPositionUs);
    }

    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
    handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    return periodPositionUs;
  }

  private void resetRendererPosition(long periodPositionUs) throws ExoPlaybackException {
    MediaPeriodHolder playingMediaPeriod = queue.getPlayingPeriod();
    rendererPositionUs =
        playingMediaPeriod == null
            ? MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US + periodPositionUs
            : playingMediaPeriod.toRendererTime(periodPositionUs);
    mediaClock.resetPosition(rendererPositionUs);
    for (RendererHolder rendererHolder : renderers) {
      rendererHolder.resetPosition(playingMediaPeriod, rendererPositionUs);
    }
    notifyTrackSelectionDiscontinuity();
  }

  private void setPlaybackParametersInternal(PlaybackParameters playbackParameters)
      throws ExoPlaybackException {
    setMediaClockPlaybackParameters(playbackParameters);
    handlePlaybackParameters(mediaClock.getPlaybackParameters(), /* acknowledgeCommand= */ true);
  }

  private void setSeekParametersInternal(SeekParameters seekParameters) {
    this.seekParameters = seekParameters;
  }

  private void setForegroundModeInternal(
      boolean foregroundMode, @Nullable AtomicBoolean processedFlag) {
    if (this.foregroundMode != foregroundMode) {
      this.foregroundMode = foregroundMode;
      if (!foregroundMode) {
        for (RendererHolder rendererHolder : renderers) {
          rendererHolder.reset();
        }
      }
    }
    if (processedFlag != null) {
      synchronized (this) {
        processedFlag.set(true);
        notifyAll();
      }
    }
  }

  private void setVideoOutputInternal(
      @Nullable Object videoOutput, @Nullable AtomicBoolean processedFlag)
      throws ExoPlaybackException {
    for (RendererHolder renderer : renderers) {
      renderer.setVideoOutput(videoOutput);
    }
    if (playbackInfo.playbackState == Player.STATE_READY
        || playbackInfo.playbackState == Player.STATE_BUFFERING) {
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
    if (processedFlag != null) {
      synchronized (this) {
        processedFlag.set(true);
        notifyAll();
      }
    }
  }

  private void stopInternal(boolean forceResetRenderers, boolean acknowledgeStop) {
    resetInternal(
        /* resetRenderers= */ forceResetRenderers || !foregroundMode,
        /* resetPosition= */ false,
        /* releaseMediaSourceList= */ true,
        /* resetError= */ false);
    playbackInfoUpdate.incrementPendingOperationAcks(acknowledgeStop ? 1 : 0);
    loadControl.onStopped(playerId);
    audioFocusManager.updateAudioFocus(playbackInfo.playWhenReady, Player.STATE_IDLE);
    setState(Player.STATE_IDLE);
  }

  private void releaseInternal() {
    try {
      resetInternal(
          /* resetRenderers= */ true,
          /* resetPosition= */ false,
          /* releaseMediaSourceList= */ true,
          /* resetError= */ false);
      releaseRenderers();
      loadControl.onReleased(playerId);
      audioFocusManager.release();
      trackSelector.release();
      setState(Player.STATE_IDLE);
    } finally {
      playbackLooperProvider.releaseLooper();
      synchronized (this) {
        released = true;
        notifyAll();
      }
    }
  }

  private void resetInternal(
      boolean resetRenderers,
      boolean resetPosition,
      boolean releaseMediaSourceList,
      boolean resetError) {
    handler.removeMessages(MSG_DO_SOME_WORK);
    pendingRecoverableRendererError = null;
    updateRebufferingState(/* isRebuffering= */ false, /* resetLastRebufferRealtimeMs= */ true);
    mediaClock.stop();
    rendererPositionUs = MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US;
    try {
      disableRenderers();
    } catch (RuntimeException | ExoPlaybackException e) {
      // There's nothing we can do.
      Log.e(TAG, "Disable failed.", e);
    }
    if (resetRenderers) {
      for (RendererHolder rendererHolder : renderers) {
        try {
          rendererHolder.reset();
        } catch (RuntimeException e) {
          // There's nothing we can do.
          Log.e(TAG, "Reset failed.", e);
        }
      }
    }
    enabledRendererCount = 0;

    MediaPeriodId mediaPeriodId = playbackInfo.periodId;
    long startPositionUs = playbackInfo.positionUs;
    long requestedContentPositionUs =
        playbackInfo.periodId.isAd() || isUsingPlaceholderPeriod(playbackInfo, period)
            ? playbackInfo.requestedContentPositionUs
            : playbackInfo.positionUs;
    boolean resetTrackInfo = false;
    if (resetPosition) {
      pendingInitialSeekPosition = null;
      Pair<MediaPeriodId, Long> firstPeriodAndPositionUs =
          getPlaceholderFirstMediaPeriodPositionUs(playbackInfo.timeline);
      mediaPeriodId = firstPeriodAndPositionUs.first;
      startPositionUs = firstPeriodAndPositionUs.second;
      requestedContentPositionUs = C.TIME_UNSET;
      if (!mediaPeriodId.equals(playbackInfo.periodId)) {
        resetTrackInfo = true;
      }
    }

    queue.clear();
    shouldContinueLoading = false;

    Timeline timeline = playbackInfo.timeline;
    if (releaseMediaSourceList && timeline instanceof PlaylistTimeline) {
      // Wrap the current timeline to make sure the current period is marked as a placeholder to
      // force resolving the default start position with the next timeline refresh.
      timeline =
          ((PlaylistTimeline) playbackInfo.timeline)
              .copyWithPlaceholderTimeline(mediaSourceList.getShuffleOrder());
      if (mediaPeriodId.adGroupIndex != C.INDEX_UNSET) {
        timeline.getPeriodByUid(mediaPeriodId.periodUid, period);
        if (timeline.getWindow(period.windowIndex, window).isLive()) {
          // Drop ad metadata to allow live streams to reset the ad playback state. In case the ad
          // playback state is not reset by the source, the first timeline refresh after
          // re-preparation will add the ad metadata to the period again.
          mediaPeriodId =
              new MediaPeriodId(mediaPeriodId.periodUid, mediaPeriodId.windowSequenceNumber);
        }
      }
    }
    playbackInfo =
        new PlaybackInfo(
            timeline,
            mediaPeriodId,
            requestedContentPositionUs,
            /* discontinuityStartPositionUs= */ startPositionUs,
            playbackInfo.playbackState,
            resetError ? null : playbackInfo.playbackError,
            /* isLoading= */ false,
            resetTrackInfo ? TrackGroupArray.EMPTY : playbackInfo.trackGroups,
            resetTrackInfo ? emptyTrackSelectorResult : playbackInfo.trackSelectorResult,
            resetTrackInfo ? ImmutableList.of() : playbackInfo.staticMetadata,
            mediaPeriodId,
            playbackInfo.playWhenReady,
            playbackInfo.playWhenReadyChangeReason,
            playbackInfo.playbackSuppressionReason,
            playbackInfo.playbackParameters,
            /* bufferedPositionUs= */ startPositionUs,
            /* totalBufferedDurationUs= */ 0,
            /* positionUs= */ startPositionUs,
            /* positionUpdateTimeMs= */ 0,
            /* sleepingForOffload= */ false);
    if (releaseMediaSourceList) {
      queue.releasePreloadPool();
      mediaSourceList.release();
    }
  }

  private Pair<MediaPeriodId, Long> getPlaceholderFirstMediaPeriodPositionUs(Timeline timeline) {
    if (timeline.isEmpty()) {
      return Pair.create(PlaybackInfo.getDummyPeriodForEmptyTimeline(), 0L);
    }
    int firstWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
    Pair<Object, Long> firstPeriodAndPositionUs =
        timeline.getPeriodPositionUs(
            window, period, firstWindowIndex, /* windowPositionUs= */ C.TIME_UNSET);
    // Add ad metadata if any and propagate the window sequence number to new period id.
    MediaPeriodId firstPeriodId =
        queue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, firstPeriodAndPositionUs.first, /* positionUs= */ 0);
    long positionUs = firstPeriodAndPositionUs.second;
    if (firstPeriodId.isAd()) {
      timeline.getPeriodByUid(firstPeriodId.periodUid, period);
      positionUs =
          firstPeriodId.adIndexInAdGroup == period.getFirstAdIndexToPlay(firstPeriodId.adGroupIndex)
              ? period.getAdResumePositionUs()
              : 0;
    }
    return Pair.create(firstPeriodId, positionUs);
  }

  private void sendMessageInternal(PlayerMessage message) throws ExoPlaybackException {
    if (message.getPositionMs() == C.TIME_UNSET) {
      // If no delivery time is specified, trigger immediate message delivery.
      sendMessageToTarget(message);
    } else if (playbackInfo.timeline.isEmpty()) {
      // Still waiting for initial timeline to resolve position.
      pendingMessages.add(new PendingMessageInfo(message));
    } else {
      PendingMessageInfo pendingMessageInfo = new PendingMessageInfo(message);
      if (resolvePendingMessagePosition(
          pendingMessageInfo,
          /* newTimeline= */ playbackInfo.timeline,
          /* previousTimeline= */ playbackInfo.timeline,
          repeatMode,
          shuffleModeEnabled,
          window,
          period)) {
        pendingMessages.add(pendingMessageInfo);
        // Ensure new message is inserted according to playback order.
        Collections.sort(pendingMessages);
      } else {
        message.markAsProcessed(/* isDelivered= */ false);
      }
    }
  }

  private void sendMessageToTarget(PlayerMessage message) throws ExoPlaybackException {
    if (message.getLooper() == playbackLooper) {
      deliverMessage(message);
      if (playbackInfo.playbackState == Player.STATE_READY
          || playbackInfo.playbackState == Player.STATE_BUFFERING) {
        // The message may have caused something to change that now requires us to do work.
        handler.sendEmptyMessage(MSG_DO_SOME_WORK);
      }
    } else {
      handler.obtainMessage(MSG_SEND_MESSAGE_TO_TARGET_THREAD, message).sendToTarget();
    }
  }

  private void sendMessageToTargetThread(final PlayerMessage message) {
    Looper looper = message.getLooper();
    if (!looper.getThread().isAlive()) {
      Log.w("TAG", "Trying to send message on a dead thread.");
      message.markAsProcessed(/* isDelivered= */ false);
      return;
    }
    clock
        .createHandler(looper, /* callback= */ null)
        .post(
            () -> {
              try {
                deliverMessage(message);
              } catch (ExoPlaybackException e) {
                Log.e(TAG, "Unexpected error delivering message on external thread.", e);
                throw new RuntimeException(e);
              }
            });
  }

  private void deliverMessage(PlayerMessage message) throws ExoPlaybackException {
    if (message.isCanceled()) {
      return;
    }
    try {
      message.getTarget().handleMessage(message.getType(), message.getPayload());
    } finally {
      message.markAsProcessed(/* isDelivered= */ true);
    }
  }

  private void resolvePendingMessagePositions(Timeline newTimeline, Timeline previousTimeline) {
    if (newTimeline.isEmpty() && previousTimeline.isEmpty()) {
      // Keep all messages unresolved until we have a non-empty timeline.
      return;
    }
    for (int i = pendingMessages.size() - 1; i >= 0; i--) {
      if (!resolvePendingMessagePosition(
          pendingMessages.get(i),
          newTimeline,
          previousTimeline,
          repeatMode,
          shuffleModeEnabled,
          window,
          period)) {
        // Unable to resolve a new position for the message. Remove it.
        pendingMessages.get(i).message.markAsProcessed(/* isDelivered= */ false);
        pendingMessages.remove(i);
      }
    }
    // Re-sort messages by playback order.
    Collections.sort(pendingMessages);
  }

  private void maybeTriggerPendingMessages(long oldPeriodPositionUs, long newPeriodPositionUs)
      throws ExoPlaybackException {
    if (pendingMessages.isEmpty() || playbackInfo.periodId.isAd()) {
      return;
    }
    // If this is the first call after resetting the renderer position, include oldPeriodPositionUs
    // in potential trigger positions, but make sure we deliver it only once.
    if (deliverPendingMessageAtStartPositionRequired) {
      oldPeriodPositionUs--;
      deliverPendingMessageAtStartPositionRequired = false;
    }

    // Correct next index if necessary (e.g. after seeking, timeline changes, or new messages)
    int currentPeriodIndex =
        playbackInfo.timeline.getIndexOfPeriod(playbackInfo.periodId.periodUid);
    int nextPendingMessageIndex = min(nextPendingMessageIndexHint, pendingMessages.size());
    PendingMessageInfo previousInfo =
        nextPendingMessageIndex > 0 ? pendingMessages.get(nextPendingMessageIndex - 1) : null;
    while (previousInfo != null
        && (previousInfo.resolvedPeriodIndex > currentPeriodIndex
            || (previousInfo.resolvedPeriodIndex == currentPeriodIndex
                && previousInfo.resolvedPeriodTimeUs > oldPeriodPositionUs))) {
      nextPendingMessageIndex--;
      previousInfo =
          nextPendingMessageIndex > 0 ? pendingMessages.get(nextPendingMessageIndex - 1) : null;
    }
    PendingMessageInfo nextInfo =
        nextPendingMessageIndex < pendingMessages.size()
            ? pendingMessages.get(nextPendingMessageIndex)
            : null;
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && (nextInfo.resolvedPeriodIndex < currentPeriodIndex
            || (nextInfo.resolvedPeriodIndex == currentPeriodIndex
                && nextInfo.resolvedPeriodTimeUs <= oldPeriodPositionUs))) {
      nextPendingMessageIndex++;
      nextInfo =
          nextPendingMessageIndex < pendingMessages.size()
              ? pendingMessages.get(nextPendingMessageIndex)
              : null;
    }
    // Check if any message falls within the covered time span.
    while (nextInfo != null
        && nextInfo.resolvedPeriodUid != null
        && nextInfo.resolvedPeriodIndex == currentPeriodIndex
        && nextInfo.resolvedPeriodTimeUs > oldPeriodPositionUs
        && nextInfo.resolvedPeriodTimeUs <= newPeriodPositionUs) {
      try {
        sendMessageToTarget(nextInfo.message);
      } finally {
        if (nextInfo.message.getDeleteAfterDelivery() || nextInfo.message.isCanceled()) {
          pendingMessages.remove(nextPendingMessageIndex);
        } else {
          nextPendingMessageIndex++;
        }
      }
      nextInfo =
          nextPendingMessageIndex < pendingMessages.size()
              ? pendingMessages.get(nextPendingMessageIndex)
              : null;
    }
    nextPendingMessageIndexHint = nextPendingMessageIndex;
  }

  private void disableRenderers() throws ExoPlaybackException {
    for (int i = 0; i < renderers.length; i++) {
      disableRenderer(/* rendererIndex= */ i);
    }
    prewarmingMediaPeriodDiscontinuity = C.TIME_UNSET;
  }

  private void disableRenderer(int rendererIndex) throws ExoPlaybackException {
    int enabledRendererCountBeforeDisabling = renderers[rendererIndex].getEnabledRendererCount();
    renderers[rendererIndex].disable(mediaClock);
    maybeTriggerOnRendererReadyChanged(rendererIndex, /* allowsPlayback= */ false);
    enabledRendererCount -= enabledRendererCountBeforeDisabling;
  }

  private void disableAndResetPrewarmingRenderers() {
    if (!hasSecondaryRenderers || !areRenderersPrewarming()) {
      return;
    }
    for (RendererHolder renderer : renderers) {
      int enabledRendererCountBeforeDisabling = renderer.getEnabledRendererCount();
      renderer.disablePrewarming(mediaClock);
      enabledRendererCount -=
          enabledRendererCountBeforeDisabling - renderer.getEnabledRendererCount();
    }
    prewarmingMediaPeriodDiscontinuity = C.TIME_UNSET;
  }

  private void reselectTracksInternalAndSeek() throws ExoPlaybackException {
    reselectTracksInternal();
    seekToCurrentPosition(/* sendDiscontinuity= */ true);
  }

  private void reselectTracksInternal() throws ExoPlaybackException {
    float playbackSpeed = mediaClock.getPlaybackParameters().speed;
    // Reselect tracks on each period in turn, until the selection changes.
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    boolean selectionsChangedForReadPeriod = true;
    TrackSelectorResult newTrackSelectorResult;
    // Keep playing period result in case of track selection change for reading period only.
    TrackSelectorResult newPlayingPeriodTrackSelectorResult = null;
    while (true) {
      if (periodHolder == null || !periodHolder.prepared) {
        // The reselection did not change any prepared periods.
        return;
      }
      newTrackSelectorResult =
          periodHolder.selectTracks(
              playbackSpeed, playbackInfo.timeline, playbackInfo.playWhenReady);
      if (periodHolder == queue.getPlayingPeriod()) {
        newPlayingPeriodTrackSelectorResult = newTrackSelectorResult;
      }
      if (!newTrackSelectorResult.isEquivalent(periodHolder.getTrackSelectorResult())) {
        // Selected tracks have changed for this period.
        break;
      }
      if (periodHolder == readingPeriodHolder) {
        // The track reselection didn't affect any period that has been read.
        selectionsChangedForReadPeriod = false;
      }
      periodHolder = periodHolder.getNext();
    }

    if (selectionsChangedForReadPeriod) {
      // Update streams and rebuffer for the new selection, recreating all streams if reading ahead.
      MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
      @MediaPeriodQueue.UpdatePeriodQueueResult
      int removeAfterResult = queue.removeAfter(playingPeriodHolder);
      boolean recreateStreams =
          (removeAfterResult & UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD) != 0;

      boolean[] streamResetFlags = new boolean[renderers.length];
      long periodPositionUs =
          playingPeriodHolder.applyTrackSelection(
              checkNotNull(newPlayingPeriodTrackSelectorResult),
              playbackInfo.positionUs,
              recreateStreams,
              streamResetFlags);
      boolean hasDiscontinuity =
          playbackInfo.playbackState != Player.STATE_ENDED
              && periodPositionUs != playbackInfo.positionUs;
      playbackInfo =
          handlePositionDiscontinuity(
              playbackInfo.periodId,
              periodPositionUs,
              playbackInfo.requestedContentPositionUs,
              playbackInfo.discontinuityStartPositionUs,
              hasDiscontinuity,
              Player.DISCONTINUITY_REASON_INTERNAL);
      if (hasDiscontinuity) {
        resetRendererPosition(periodPositionUs);
      }

      // Disable pre-warming renderers.
      disableAndResetPrewarmingRenderers();

      boolean[] rendererWasEnabledFlags = new boolean[renderers.length];
      for (int i = 0; i < renderers.length; i++) {
        int enabledRendererCountBeforeDisabling = renderers[i].getEnabledRendererCount();
        rendererWasEnabledFlags[i] = renderers[i].isRendererEnabled();

        renderers[i].maybeDisableOrResetPosition(
            playingPeriodHolder.sampleStreams[i],
            mediaClock,
            rendererPositionUs,
            streamResetFlags[i]);
        if (enabledRendererCountBeforeDisabling - renderers[i].getEnabledRendererCount() > 0) {
          maybeTriggerOnRendererReadyChanged(i, /* allowsPlayback= */ false);
        }
        enabledRendererCount -=
            enabledRendererCountBeforeDisabling - renderers[i].getEnabledRendererCount();
      }

      enableRenderers(rendererWasEnabledFlags, /* startPositionUs= */ rendererPositionUs);
      playingPeriodHolder.allRenderersInCorrectState = true;
    } else {
      // Release and re-prepare/buffer periods after the one whose selection changed.
      queue.removeAfter(periodHolder);
      if (periodHolder.prepared) {
        long loadingPeriodPositionUs =
            max(periodHolder.info.startPositionUs, periodHolder.toPeriodTime(rendererPositionUs));
        if (hasSecondaryRenderers
            && areRenderersPrewarming()
            && queue.getPrewarmingPeriod() == periodHolder) {
          // If renderers are enabled early and track reselection is on the enabled-early period
          // then there is a need to disable those renderers. Must be done prior to call to
          // applyTrackSelection.
          // TODO: Only disable pre-warming renderers for those whose streams will be changed by
          // track reselection. Will require allowing partial maybePrewarmRenderersForNextPeriod.
          disableAndResetPrewarmingRenderers();
        }
        periodHolder.applyTrackSelection(newTrackSelectorResult, loadingPeriodPositionUs, false);
      }
    }
    handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ true);
    if (playbackInfo.playbackState != Player.STATE_ENDED) {
      maybeContinueLoading();
      updatePlaybackPositions();
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void updateTrackSelectionPlaybackSpeed(float playbackSpeed) {
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    while (periodHolder != null) {
      for (ExoTrackSelection trackSelection : periodHolder.getTrackSelectorResult().selections) {
        if (trackSelection != null) {
          trackSelection.onPlaybackSpeed(playbackSpeed);
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private void notifyTrackSelectionDiscontinuity() {
    MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
    while (periodHolder != null) {
      for (ExoTrackSelection trackSelection : periodHolder.getTrackSelectorResult().selections) {
        if (trackSelection != null) {
          trackSelection.onDiscontinuity();
        }
      }
      periodHolder = periodHolder.getNext();
    }
  }

  private boolean shouldTransitionToReadyState(boolean renderersReadyOrEnded) {
    if (enabledRendererCount == 0) {
      // If there are no enabled renderers, determine whether we're ready based on the timeline.
      return isTimelineReady();
    }
    if (!renderersReadyOrEnded) {
      return false;
    }
    if (!playbackInfo.isLoading) {
      // Renderers are ready and we're not loading. Transition to ready, since the alternative is
      // getting stuck waiting for additional media that's not being loaded.
      return true;
    }
    // Renderers are ready and we're loading. Ask the LoadControl whether to transition.
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    long targetLiveOffsetUs =
        shouldUseLivePlaybackSpeedControl(playbackInfo.timeline, playingPeriodHolder.info.id)
            ? livePlaybackSpeedControl.getTargetLiveOffsetUs()
            : C.TIME_UNSET;
    MediaPeriodHolder loadingHolder = queue.getLoadingPeriod();
    boolean isBufferedToEnd = loadingHolder.isFullyBuffered() && loadingHolder.info.isFinal;
    // Ad loader implementations may only load ad media once playback has nearly reached the ad, but
    // it is possible for playback to be stuck buffering waiting for this. Therefore, we start
    // playback regardless of buffered duration if we are waiting for an ad media period to prepare.
    boolean isAdPendingPreparation = loadingHolder.info.id.isAd() && !loadingHolder.prepared;
    if (isBufferedToEnd || isAdPendingPreparation) {
      return true;
    }
    // Get updated buffered duration as it may have changed since the start of the renderer loop.
    long bufferedDurationUs = getTotalBufferedDurationUs(loadingHolder.getBufferedPositionUs());

    return loadControl.shouldStartPlayback(
        new LoadControl.Parameters(
            playerId,
            playbackInfo.timeline,
            playingPeriodHolder.info.id,
            playingPeriodHolder.toPeriodTime(rendererPositionUs),
            bufferedDurationUs,
            mediaClock.getPlaybackParameters().speed,
            playbackInfo.playWhenReady,
            isRebuffering,
            targetLiveOffsetUs,
            lastRebufferRealtimeMs));
  }

  private boolean isTimelineReady() {
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    long playingPeriodDurationUs = playingPeriodHolder.info.durationUs;
    return playingPeriodHolder.prepared
        && (playingPeriodDurationUs == C.TIME_UNSET
            || playbackInfo.positionUs < playingPeriodDurationUs
            || !shouldPlayWhenReady());
  }

  private void handleMediaSourceListInfoRefreshed(Timeline timeline, boolean isSourceRefresh)
      throws ExoPlaybackException {
    PositionUpdateForPlaylistChange positionUpdate =
        resolvePositionForPlaylistChange(
            timeline,
            playbackInfo,
            pendingInitialSeekPosition,
            queue,
            repeatMode,
            shuffleModeEnabled,
            window,
            period);
    MediaPeriodId newPeriodId = positionUpdate.periodId;
    long newRequestedContentPositionUs = positionUpdate.requestedContentPositionUs;
    boolean forceBufferingState = positionUpdate.forceBufferingState;
    long newPositionUs = positionUpdate.periodPositionUs;
    boolean periodPositionChanged =
        !playbackInfo.periodId.equals(newPeriodId) || newPositionUs != playbackInfo.positionUs;
    try {
      if (positionUpdate.endPlayback) {
        if (playbackInfo.playbackState != Player.STATE_IDLE) {
          setState(Player.STATE_ENDED);
        }
        resetInternal(
            /* resetRenderers= */ false,
            /* resetPosition= */ false,
            /* releaseMediaSourceList= */ false,
            /* resetError= */ true);
      }
      for (RendererHolder rendererHolder : renderers) {
        rendererHolder.setTimeline(timeline);
      }
      if (!periodPositionChanged) {
        // We can keep the current playing period. Update the rest of the queued periods.
        long maxRendererReadPositionUs =
            queue.getReadingPeriod() == null
                ? 0
                : getMaxRendererReadPositionUs(queue.getReadingPeriod());
        long maxRendererPrewarmingPositionUs =
            !areRenderersPrewarming() || queue.getPrewarmingPeriod() == null
                ? 0
                : getMaxRendererReadPositionUs(queue.getPrewarmingPeriod());
        @MediaPeriodQueue.UpdatePeriodQueueResult
        int updateQueuedPeriodsResult =
            queue.updateQueuedPeriods(
                timeline,
                rendererPositionUs,
                maxRendererReadPositionUs,
                maxRendererPrewarmingPositionUs);
        if ((updateQueuedPeriodsResult & UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD) != 0) {
          seekToCurrentPosition(/* sendDiscontinuity= */ false);
        } else if ((updateQueuedPeriodsResult & UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD)
            != 0) {
          disableAndResetPrewarmingRenderers();
        }
      } else if (!timeline.isEmpty()) {
        // Something changed. Seek to new start position.
        @Nullable MediaPeriodHolder periodHolder = queue.getPlayingPeriod();
        while (periodHolder != null) {
          // Update the new playing media period info if it already exists.
          if (periodHolder.info.id.equals(newPeriodId)) {
            periodHolder.info = queue.getUpdatedMediaPeriodInfo(timeline, periodHolder.info);
            periodHolder.updateClipping();
          }
          periodHolder = periodHolder.getNext();
        }
        newPositionUs = seekToPeriodPosition(newPeriodId, newPositionUs, forceBufferingState);
      }
    } finally {
      updatePlaybackSpeedSettingsForNewPeriod(
          /* newTimeline= */ timeline,
          newPeriodId,
          /* oldTimeline= */ playbackInfo.timeline,
          /* oldPeriodId= */ playbackInfo.periodId,
          /* positionForTargetOffsetOverrideUs */ positionUpdate.setTargetLiveOffset
              ? newPositionUs
              : C.TIME_UNSET,
          /* forceSetTargetOffsetOverride= */ false);
      if (periodPositionChanged
          || newRequestedContentPositionUs != playbackInfo.requestedContentPositionUs) {
        Object oldPeriodUid = playbackInfo.periodId.periodUid;
        Timeline oldTimeline = playbackInfo.timeline;
        boolean reportDiscontinuity =
            periodPositionChanged
                && isSourceRefresh
                && !oldTimeline.isEmpty()
                && !oldTimeline.getPeriodByUid(oldPeriodUid, period).isPlaceholder;
        playbackInfo =
            handlePositionDiscontinuity(
                newPeriodId,
                newPositionUs,
                newRequestedContentPositionUs,
                playbackInfo.discontinuityStartPositionUs,
                reportDiscontinuity,
                timeline.getIndexOfPeriod(oldPeriodUid) == C.INDEX_UNSET
                    ? Player.DISCONTINUITY_REASON_REMOVE
                    : Player.DISCONTINUITY_REASON_SKIP);
      }
      resetPendingPauseAtEndOfPeriod();
      resolvePendingMessagePositions(
          /* newTimeline= */ timeline, /* previousTimeline= */ playbackInfo.timeline);
      playbackInfo = playbackInfo.copyWithTimeline(timeline);
      if (!timeline.isEmpty()) {
        // Retain pending seek position only while the timeline is still empty.
        pendingInitialSeekPosition = null;
      }
      handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
      handler.sendEmptyMessage(MSG_DO_SOME_WORK);
    }
  }

  private void updatePlaybackSpeedSettingsForNewPeriod(
      Timeline newTimeline,
      MediaPeriodId newPeriodId,
      Timeline oldTimeline,
      MediaPeriodId oldPeriodId,
      long positionForTargetOffsetOverrideUs,
      boolean forceSetTargetOffsetOverride)
      throws ExoPlaybackException {
    if (!shouldUseLivePlaybackSpeedControl(newTimeline, newPeriodId)) {
      // Live playback speed control is unused for the current period, reset speed to user-defined
      // playback parameters or 1.0 for ad playback.
      PlaybackParameters targetPlaybackParameters =
          newPeriodId.isAd() ? PlaybackParameters.DEFAULT : playbackInfo.playbackParameters;
      if (!mediaClock.getPlaybackParameters().equals(targetPlaybackParameters)) {
        setMediaClockPlaybackParameters(targetPlaybackParameters);
        handlePlaybackParameters(
            playbackInfo.playbackParameters,
            targetPlaybackParameters.speed,
            /* updatePlaybackInfo= */ false,
            /* acknowledgeCommand= */ false);
      }
      return;
    }
    int windowIndex = newTimeline.getPeriodByUid(newPeriodId.periodUid, period).windowIndex;
    newTimeline.getWindow(windowIndex, window);
    livePlaybackSpeedControl.setLiveConfiguration(castNonNull(window.liveConfiguration));
    if (positionForTargetOffsetOverrideUs != C.TIME_UNSET) {
      livePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(
          getLiveOffsetUs(newTimeline, newPeriodId.periodUid, positionForTargetOffsetOverrideUs));
    } else {
      Object windowUid = window.uid;
      @Nullable Object oldWindowUid = null;
      if (!oldTimeline.isEmpty()) {
        int oldWindowIndex = oldTimeline.getPeriodByUid(oldPeriodId.periodUid, period).windowIndex;
        oldWindowUid = oldTimeline.getWindow(oldWindowIndex, window).uid;
      }
      if (!Objects.equals(oldWindowUid, windowUid) || forceSetTargetOffsetOverride) {
        // Reset overridden target live offset to media values if window changes or if seekTo
        // default live position.
        livePlaybackSpeedControl.setTargetLiveOffsetOverrideUs(C.TIME_UNSET);
      }
    }
  }

  private long getMaxRendererReadPositionUs(MediaPeriodHolder periodHolder) {
    if (periodHolder == null) {
      return 0;
    }
    long maxReadPositionUs = periodHolder.getRendererOffset();
    if (!periodHolder.prepared) {
      return maxReadPositionUs;
    }
    for (int i = 0; i < renderers.length; i++) {
      if (!renderers[i].isReadingFromPeriod(periodHolder)) {
        // Ignore disabled renderers and renderers with sample streams from previous periods.
        continue;
      }
      long readingPositionUs = renderers[i].getReadingPositionUs(periodHolder);
      if (readingPositionUs == C.TIME_END_OF_SOURCE) {
        return C.TIME_END_OF_SOURCE;
      } else {
        maxReadPositionUs = max(readingPositionUs, maxReadPositionUs);
      }
    }
    return maxReadPositionUs;
  }

  private void updatePeriods() throws ExoPlaybackException {
    if (playbackInfo.timeline.isEmpty() || !mediaSourceList.isPrepared()) {
      // No periods available.
      return;
    }
    boolean loadingPeriodChanged = maybeUpdateLoadingPeriod();
    maybeUpdatePrewarmingPeriod();
    maybeUpdateReadingPeriod();
    maybeUpdateReadingRenderers();
    maybeUpdatePlayingPeriod();
    maybeUpdatePreloadPeriods(loadingPeriodChanged);
  }

  private boolean maybeUpdateLoadingPeriod() throws ExoPlaybackException {
    boolean loadingPeriodChanged = false;
    queue.reevaluateBuffer(rendererPositionUs);
    if (queue.shouldLoadNextMediaPeriod()) {
      @Nullable
      MediaPeriodInfo info = queue.getNextMediaPeriodInfo(rendererPositionUs, playbackInfo);
      if (info != null) {
        MediaPeriodHolder mediaPeriodHolder = queue.enqueueNextMediaPeriodHolder(info);
        if (!mediaPeriodHolder.prepareCalled) {
          mediaPeriodHolder.prepare(this, info.startPositionUs);
        } else if (mediaPeriodHolder.prepared) {
          handler.obtainMessage(MSG_PERIOD_PREPARED, mediaPeriodHolder.mediaPeriod).sendToTarget();
        }
        if (queue.getPlayingPeriod() == mediaPeriodHolder) {
          resetRendererPosition(info.startPositionUs);
        }
        handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
        loadingPeriodChanged = true;
      }
    }
    if (shouldContinueLoading) {
      // We should still be loading, except when there is nothing to load or we have fully loaded
      // the current period.
      shouldContinueLoading = isLoadingPossible(queue.getLoadingPeriod());
      updateIsLoading();
    } else {
      maybeContinueLoading();
    }
    return loadingPeriodChanged;
  }

  private void maybeUpdatePrewarmingPeriod() throws ExoPlaybackException {
    // TODO: Add limit as to not enable waiting renderer too early
    if (pendingPauseAtEndOfPeriod
        || !hasSecondaryRenderers
        || isPrewarmingDisabledUntilNextTransition
        || areRenderersPrewarming()) {
      return;
    }
    @Nullable MediaPeriodHolder prewarmingPeriodHolder = queue.getPrewarmingPeriod();
    if (prewarmingPeriodHolder == null
        || prewarmingPeriodHolder != queue.getReadingPeriod()
        || prewarmingPeriodHolder.getNext() == null
        || !prewarmingPeriodHolder.getNext().prepared) {
      return;
    }

    queue.advancePrewarmingPeriod();
    maybePrewarmRenderers();
  }

  private void maybePrewarmRenderers() throws ExoPlaybackException {
    @Nullable MediaPeriodHolder prewarmingPeriod = queue.getPrewarmingPeriod();
    if (prewarmingPeriod == null) {
      return;
    }
    TrackSelectorResult trackSelectorResult = prewarmingPeriod.getTrackSelectorResult();
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelectorResult.isRendererEnabled(i)
          && renderers[i].hasSecondary()
          && !renderers[i].isPrewarming()) {
        renderers[i].startPrewarming();
        enableRenderer(
            prewarmingPeriod,
            /* rendererIndex= */ i,
            /* wasRendererEnabled= */ false,
            prewarmingPeriod.getStartPositionRendererTime());
      }
    }
    // Handle any media period discontinuities.
    if (areRenderersPrewarming()) {
      prewarmingMediaPeriodDiscontinuity = prewarmingPeriod.mediaPeriod.readDiscontinuity();
      if (!prewarmingPeriod.isFullyBuffered()) {
        // The discontinuity caused the period to not be fully buffered. Continue loading from
        // this period again and discard all other periods we already started loading.
        queue.removeAfter(prewarmingPeriod);
        handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
        maybeContinueLoading();
      }
    }
  }

  private void maybeUpdateReadingPeriod() throws ExoPlaybackException {
    @Nullable MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    if (readingPeriodHolder == null) {
      return;
    }

    if (readingPeriodHolder.getNext() == null || pendingPauseAtEndOfPeriod) {
      // We don't have a successor to advance the reading period to or we want to let them end
      // intentionally to pause at the end of the period.
      if (readingPeriodHolder.info.isFinal || pendingPauseAtEndOfPeriod) {
        for (RendererHolder renderer : renderers) {
          if (!renderer.isReadingFromPeriod(readingPeriodHolder)) {
            continue;
          }
          // Defer setting the stream as final until the renderer has actually consumed the whole
          // stream in case of playlist changes that cause the stream to be no longer final.
          if (renderer.hasReadPeriodToEnd(readingPeriodHolder)) {
            long streamEndPositionUs =
                readingPeriodHolder.info.durationUs != C.TIME_UNSET
                        && readingPeriodHolder.info.durationUs != C.TIME_END_OF_SOURCE
                    ? readingPeriodHolder.getRendererOffset() + readingPeriodHolder.info.durationUs
                    : C.TIME_UNSET;
            renderer.setCurrentStreamFinal(readingPeriodHolder, streamEndPositionUs);
          }
        }
      }
      return;
    }

    if (!hasReadingPeriodFinishedReading()) {
      return;
    }

    if (areRenderersPrewarming() && queue.getPrewarmingPeriod() == queue.getReadingPeriod()) {
      // Reading period has already advanced to pre-warming period.
      return;
    }

    if (!readingPeriodHolder.getNext().prepared
        && rendererPositionUs < readingPeriodHolder.getNext().getStartPositionRendererTime()) {
      // The successor is not prepared yet and playback hasn't reached the transition point.
      return;
    }

    MediaPeriodHolder oldReadingPeriodHolder = readingPeriodHolder;
    TrackSelectorResult oldTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();
    readingPeriodHolder = queue.advanceReadingPeriod();
    TrackSelectorResult newTrackSelectorResult = readingPeriodHolder.getTrackSelectorResult();

    updatePlaybackSpeedSettingsForNewPeriod(
        /* newTimeline= */ playbackInfo.timeline,
        /* newPeriodId= */ readingPeriodHolder.info.id,
        /* oldTimeline= */ playbackInfo.timeline,
        /* oldPeriodId= */ oldReadingPeriodHolder.info.id,
        /* positionForTargetOffsetOverrideUs= */ C.TIME_UNSET,
        /* forceSetTargetOffsetOverride= */ false);

    if (readingPeriodHolder.prepared
        && ((hasSecondaryRenderers && prewarmingMediaPeriodDiscontinuity != C.TIME_UNSET)
            || readingPeriodHolder.mediaPeriod.readDiscontinuity() != C.TIME_UNSET)) {
      prewarmingMediaPeriodDiscontinuity = C.TIME_UNSET;
      // The new period starts with a discontinuity, so unless a pre-warming renderer is handling
      // the discontinuity, the renderers will play out all data, then
      // be disabled and re-enabled when they start playing the next period.
      boolean arePrewarmingRenderersHandlingDiscontinuity =
          hasSecondaryRenderers && !isPrewarmingDisabledUntilNextTransition;
      if (arePrewarmingRenderersHandlingDiscontinuity) {
        for (int i = 0; i < renderers.length; i++) {
          if (!newTrackSelectorResult.isRendererEnabled(i)) {
            continue;
          }
          // TODO: This check should ideally be replaced by a per-stream discontinuity check
          // done by the MediaPeriod itself.
          if (!MimeTypes.allSamplesAreSyncSamples(
                  newTrackSelectorResult.selections[i].getSelectedFormat().sampleMimeType,
                  newTrackSelectorResult.selections[i].getSelectedFormat().codecs)
              && !renderers[i].isPrewarming()) {
            arePrewarmingRenderersHandlingDiscontinuity = false;
            break;
          }
        }
      }
      if (!arePrewarmingRenderersHandlingDiscontinuity) {
        setAllNonPrewarmingRendererStreamsFinal(
            /* streamEndPositionUs= */ readingPeriodHolder.getStartPositionRendererTime());
        if (!readingPeriodHolder.isFullyBuffered()) {
          // The discontinuity caused the period to not be fully buffered. Continue loading from
          // this period again and discard all other periods we already started loading.
          queue.removeAfter(readingPeriodHolder);
          handleLoadingMediaPeriodChanged(/* loadingTrackSelectionChanged= */ false);
          maybeContinueLoading();
        }
        return;
      }
    }

    for (RendererHolder renderer : renderers) {
      renderer.maybeSetOldStreamToFinal(
          oldTrackSelectorResult,
          newTrackSelectorResult,
          readingPeriodHolder.getStartPositionRendererTime());
    }
  }

  private void maybeUpdateReadingRenderers() throws ExoPlaybackException {
    @Nullable MediaPeriodHolder readingPeriod = queue.getReadingPeriod();
    if (readingPeriod == null
        || queue.getPlayingPeriod() == readingPeriod
        || readingPeriod.allRenderersInCorrectState) {
      // Not reading ahead or all renderers updated.
      return;
    }
    boolean allUpdated = updateRenderersForTransition();
    if (allUpdated) {
      queue.getReadingPeriod().allRenderersInCorrectState = true;
    }
  }

  private boolean updateRenderersForTransition() throws ExoPlaybackException {
    MediaPeriodHolder readingMediaPeriod = queue.getReadingPeriod();
    TrackSelectorResult newTrackSelectorResult = readingMediaPeriod.getTrackSelectorResult();
    boolean allUpdated = true;
    for (int i = 0; i < renderers.length; i++) {
      int enabledRendererCountPreTransition = renderers[i].getEnabledRendererCount();
      int result =
          renderers[i].replaceStreamsOrDisableRendererForTransition(
              readingMediaPeriod, newTrackSelectorResult, mediaClock);
      if ((result & REPLACE_STREAMS_DISABLE_RENDERERS_DISABLE_OFFLOAD_SCHEDULING) != 0
          && offloadSchedulingEnabled) {
        // Prevent sleeping across offload track transition else position won't get updated.
        // TODO: (b/183635183) Optimize Offload End-Of-Stream: Sleep to just before end of track
        setOffloadSchedulingEnabled(false);
      }
      enabledRendererCount -=
          enabledRendererCountPreTransition - renderers[i].getEnabledRendererCount();

      boolean completedUpdate = (result & REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED) != 0;
      allUpdated &= completedUpdate;
    }
    if (allUpdated) {
      for (int i = 0; i < renderers.length; i++) {
        if (newTrackSelectorResult.isRendererEnabled(i)
            && !renderers[i].isReadingFromPeriod(readingMediaPeriod)) {
          enableRenderer(
              readingMediaPeriod,
              /* rendererIndex= */ i,
              /* wasRendererEnabled= */ false,
              readingMediaPeriod.getStartPositionRendererTime());
        }
      }
    }
    return allUpdated;
  }

  private void maybeUpdatePreloadPeriods(boolean loadingPeriodChanged) {
    if (preloadConfiguration.targetPreloadDurationUs == C.TIME_UNSET) {
      // Do nothing if preloading disabled.
      return;
    }
    if (loadingPeriodChanged
        || !playbackInfo.timeline.equals(lastPreloadPoolInvalidationTimeline)) {
      // invalidate the pool when the loading period or the timeline changed.
      lastPreloadPoolInvalidationTimeline = playbackInfo.timeline;
      queue.invalidatePreloadPool(playbackInfo.timeline);
    }
    maybeContinuePreloading();
  }

  private void maybeContinuePreloading() {
    queue.maybeUpdatePreloadMediaPeriodHolder();
    MediaPeriodHolder preloading = queue.getPreloadingPeriod();
    if (preloading == null
        || (preloading.prepareCalled && !preloading.prepared)
        || preloading.mediaPeriod.isLoading()
        || !loadControl.shouldContinuePreloading(
            playbackInfo.timeline,
            preloading.info.id,
            preloading.prepared ? preloading.mediaPeriod.getBufferedPositionUs() : 0L)) {
      return;
    }
    if (!preloading.prepareCalled) {
      preloading.prepare(/* callback= */ this, preloading.info.startPositionUs);
    } else {
      preloading.continueLoading(
          new LoadingInfo.Builder()
              .setPlaybackPositionUs(preloading.toPeriodTime(rendererPositionUs))
              .setPlaybackSpeed(mediaClock.getPlaybackParameters().speed)
              .setLastRebufferRealtimeMs(lastRebufferRealtimeMs)
              .build());
    }
  }

  private void maybeUpdatePlayingPeriod() throws ExoPlaybackException {
    boolean advancedPlayingPeriod = false;
    while (shouldAdvancePlayingPeriod()) {
      if (advancedPlayingPeriod) {
        // If we advance more than one period at a time, notify listeners after each update.
        maybeNotifyPlaybackInfoChanged();
      }
      isPrewarmingDisabledUntilNextTransition = false;
      MediaPeriodHolder newPlayingPeriodHolder = checkNotNull(queue.advancePlayingPeriod());
      boolean isCancelledSSAIAdTransition =
          playbackInfo.periodId.periodUid.equals(newPlayingPeriodHolder.info.id.periodUid)
              && playbackInfo.periodId.adGroupIndex == C.INDEX_UNSET
              && newPlayingPeriodHolder.info.id.adGroupIndex == C.INDEX_UNSET
              && playbackInfo.periodId.nextAdGroupIndex
                  != newPlayingPeriodHolder.info.id.nextAdGroupIndex;
      playbackInfo =
          handlePositionDiscontinuity(
              newPlayingPeriodHolder.info.id,
              newPlayingPeriodHolder.info.startPositionUs,
              newPlayingPeriodHolder.info.requestedContentPositionUs,
              /* discontinuityStartPositionUs= */ newPlayingPeriodHolder.info.startPositionUs,
              /* reportDiscontinuity= */ !isCancelledSSAIAdTransition,
              Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
      resetPendingPauseAtEndOfPeriod();
      updatePlaybackPositions();
      if (areRenderersPrewarming() && newPlayingPeriodHolder == queue.getPrewarmingPeriod()) {
        maybeHandlePrewarmingTransition();
      }
      if (playbackInfo.playbackState == Player.STATE_READY) {
        startRenderers();
      }
      allowRenderersToRenderStartOfStreams();
      advancedPlayingPeriod = true;
    }
  }

  private void maybeHandlePrewarmingTransition() throws ExoPlaybackException {
    for (RendererHolder renderer : renderers) {
      renderer.maybeHandlePrewarmingTransition();
    }
  }

  private void maybeUpdateOffloadScheduling() {
    // If playing period is audio-only with offload mode preference to enable, then offload
    // scheduling should be enabled.
    if (queue.getPlayingPeriod() != queue.getReadingPeriod()) {
      // Do not enable offload scheduling when starting to process the next media item.
      return;
    }
    @Nullable MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder != null) {
      TrackSelectorResult trackSelectorResult = playingPeriodHolder.getTrackSelectorResult();
      boolean isAudioRendererEnabledAndOffloadPreferred = false;
      boolean isAudioOnly = true;
      for (int i = 0; i < renderers.length; i++) {
        if (trackSelectorResult.isRendererEnabled(i)) {
          if (renderers[i].getTrackType() != C.TRACK_TYPE_AUDIO) {
            isAudioOnly = false;
            break;
          }
          if (trackSelectorResult.rendererConfigurations[i].offloadModePreferred
              != OFFLOAD_MODE_DISABLED) {
            isAudioRendererEnabledAndOffloadPreferred = true;
          }
        }
      }
      setOffloadSchedulingEnabled(isAudioRendererEnabledAndOffloadPreferred && isAudioOnly);
    }
  }

  private void allowRenderersToRenderStartOfStreams() {
    TrackSelectorResult playingTracks = queue.getPlayingPeriod().getTrackSelectorResult();
    for (int i = 0; i < renderers.length; i++) {
      if (!playingTracks.isRendererEnabled(i)) {
        continue;
      }
      renderers[i].enableMayRenderStartOfStream();
    }
  }

  private void resetPendingPauseAtEndOfPeriod() {
    @Nullable MediaPeriodHolder playingPeriod = queue.getPlayingPeriod();
    pendingPauseAtEndOfPeriod =
        playingPeriod != null && playingPeriod.info.isLastInTimelineWindow && pauseAtEndOfWindow;
  }

  private boolean shouldAdvancePlayingPeriod() {
    if (!shouldPlayWhenReady()) {
      return false;
    }
    if (pendingPauseAtEndOfPeriod) {
      return false;
    }
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (playingPeriodHolder == null) {
      return false;
    }
    MediaPeriodHolder nextPlayingPeriodHolder = playingPeriodHolder.getNext();
    return nextPlayingPeriodHolder != null
        && rendererPositionUs >= nextPlayingPeriodHolder.getStartPositionRendererTime()
        && nextPlayingPeriodHolder.allRenderersInCorrectState;
  }

  private boolean hasReadingPeriodFinishedReading() {
    MediaPeriodHolder readingPeriodHolder = queue.getReadingPeriod();
    if (!readingPeriodHolder.prepared) {
      return false;
    }
    for (int i = 0; i < renderers.length; i++) {
      if (!renderers[i].hasFinishedReadingFromPeriod(readingPeriodHolder)) {
        return false;
      }
    }
    return true;
  }

  private void setAllNonPrewarmingRendererStreamsFinal(long streamEndPositionUs) {
    for (RendererHolder renderer : renderers) {
      renderer.setAllNonPrewarmingRendererStreamsFinal(streamEndPositionUs);
    }
  }

  private void handlePeriodPrepared(MediaPeriod mediaPeriod) throws ExoPlaybackException {
    if (queue.isLoading(mediaPeriod)) {
      handleLoadingPeriodPrepared(checkNotNull(queue.getLoadingPeriod()));
    } else {
      @Nullable MediaPeriodHolder preloadHolder = queue.getPreloadHolderByMediaPeriod(mediaPeriod);
      if (preloadHolder != null) {
        checkState(!preloadHolder.prepared);
        preloadHolder.handlePrepared(
            mediaClock.getPlaybackParameters().speed,
            playbackInfo.timeline,
            playbackInfo.playWhenReady);
        if (queue.isPreloading(mediaPeriod)) {
          maybeContinuePreloading();
        }
      }
    }
  }

  private void handleLoadingPeriodPrepared(MediaPeriodHolder loadingPeriodHolder)
      throws ExoPlaybackException {
    if (!loadingPeriodHolder.prepared) {
      loadingPeriodHolder.handlePrepared(
          mediaClock.getPlaybackParameters().speed,
          playbackInfo.timeline,
          playbackInfo.playWhenReady);
    }
    updateLoadControlTrackSelection(
        loadingPeriodHolder.info.id,
        loadingPeriodHolder.getTrackGroups(),
        loadingPeriodHolder.getTrackSelectorResult());
    if (loadingPeriodHolder == queue.getPlayingPeriod()) {
      // This is the first prepared period, so update the position and the renderers.
      resetRendererPosition(loadingPeriodHolder.info.startPositionUs);
      enableRenderers();
      loadingPeriodHolder.allRenderersInCorrectState = true;
      playbackInfo =
          handlePositionDiscontinuity(
              playbackInfo.periodId,
              loadingPeriodHolder.info.startPositionUs,
              playbackInfo.requestedContentPositionUs,
              loadingPeriodHolder.info.startPositionUs,
              /* reportDiscontinuity= */ false,
              /* ignored */ Player.DISCONTINUITY_REASON_INTERNAL);
    }
    maybeContinueLoading();
  }

  private void handleContinueLoadingRequested(MediaPeriod mediaPeriod) {
    if (queue.isLoading(mediaPeriod)) {
      queue.reevaluateBuffer(rendererPositionUs);
      maybeContinueLoading();
    } else if (queue.isPreloading(mediaPeriod)) {
      maybeContinuePreloading();
    }
  }

  private void handlePlaybackParameters(
      PlaybackParameters playbackParameters, boolean acknowledgeCommand)
      throws ExoPlaybackException {
    handlePlaybackParameters(
        playbackParameters,
        playbackParameters.speed,
        /* updatePlaybackInfo= */ true,
        acknowledgeCommand);
  }

  private void handlePlaybackParameters(
      PlaybackParameters playbackParameters,
      float currentPlaybackSpeed,
      boolean updatePlaybackInfo,
      boolean acknowledgeCommand)
      throws ExoPlaybackException {
    if (updatePlaybackInfo) {
      if (acknowledgeCommand) {
        playbackInfoUpdate.incrementPendingOperationAcks(1);
      }
      playbackInfo = playbackInfo.copyWithPlaybackParameters(playbackParameters);
    }
    updateTrackSelectionPlaybackSpeed(playbackParameters.speed);
    for (RendererHolder rendererHolder : renderers) {
      rendererHolder.setPlaybackSpeed(
          currentPlaybackSpeed, /* targetPlaybackSpeed= */ playbackParameters.speed);
    }
  }

  private void maybeContinueLoading() {
    shouldContinueLoading = shouldContinueLoading();
    if (shouldContinueLoading) {
      MediaPeriodHolder loadingPeriod = checkNotNull(queue.getLoadingPeriod());
      loadingPeriod.continueLoading(
          new LoadingInfo.Builder()
              .setPlaybackPositionUs(loadingPeriod.toPeriodTime(rendererPositionUs))
              .setPlaybackSpeed(mediaClock.getPlaybackParameters().speed)
              .setLastRebufferRealtimeMs(lastRebufferRealtimeMs)
              .build());
    }
    updateIsLoading();
  }

  private boolean shouldContinueLoading() {
    if (!isLoadingPossible(queue.getLoadingPeriod())) {
      return false;
    }
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    long bufferedDurationUs =
        getTotalBufferedDurationUs(loadingPeriodHolder.getNextLoadPositionUs());
    long playbackPositionUs =
        loadingPeriodHolder == queue.getPlayingPeriod()
            ? loadingPeriodHolder.toPeriodTime(rendererPositionUs)
            : loadingPeriodHolder.toPeriodTime(rendererPositionUs)
                - loadingPeriodHolder.info.startPositionUs;
    long targetLiveOffsetUs =
        shouldUseLivePlaybackSpeedControl(playbackInfo.timeline, loadingPeriodHolder.info.id)
            ? livePlaybackSpeedControl.getTargetLiveOffsetUs()
            : C.TIME_UNSET;
    LoadControl.Parameters loadParameters =
        new LoadControl.Parameters(
            playerId,
            playbackInfo.timeline,
            loadingPeriodHolder.info.id,
            playbackPositionUs,
            bufferedDurationUs,
            mediaClock.getPlaybackParameters().speed,
            playbackInfo.playWhenReady,
            isRebuffering,
            targetLiveOffsetUs,
            lastRebufferRealtimeMs);
    boolean shouldContinueLoading = loadControl.shouldContinueLoading(loadParameters);
    MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
    if (!shouldContinueLoading
        && playingPeriodHolder.prepared
        && bufferedDurationUs < PLAYBACK_BUFFER_EMPTY_THRESHOLD_US
        && (backBufferDurationUs > 0 || retainBackBufferFromKeyframe)) {
      // LoadControl doesn't want to continue loading despite no buffered data. Clear back buffer
      // and try again in case it's blocked on memory usage of the back buffer.
      playingPeriodHolder.mediaPeriod.discardBuffer(
          playbackInfo.positionUs, /* toKeyframe= */ false);
      shouldContinueLoading = loadControl.shouldContinueLoading(loadParameters);
    }
    return shouldContinueLoading;
  }

  private boolean isLoadingPossible(@Nullable MediaPeriodHolder mediaPeriodHolder) {
    return mediaPeriodHolder != null
        && !mediaPeriodHolder.hasLoadingError()
        && mediaPeriodHolder.getNextLoadPositionUs() != C.TIME_END_OF_SOURCE;
  }

  private void updateIsLoading() {
    MediaPeriodHolder loadingPeriod = queue.getLoadingPeriod();
    boolean isLoading =
        shouldContinueLoading || (loadingPeriod != null && loadingPeriod.mediaPeriod.isLoading());
    if (isLoading != playbackInfo.isLoading) {
      playbackInfo = playbackInfo.copyWithIsLoading(isLoading);
    }
  }

  @CheckResult
  private PlaybackInfo handlePositionDiscontinuity(
      MediaPeriodId mediaPeriodId,
      long positionUs,
      long requestedContentPositionUs,
      long discontinuityStartPositionUs,
      boolean reportDiscontinuity,
      @DiscontinuityReason int discontinuityReason) {
    deliverPendingMessageAtStartPositionRequired =
        deliverPendingMessageAtStartPositionRequired
            || positionUs != playbackInfo.positionUs
            || !mediaPeriodId.equals(playbackInfo.periodId);
    resetPendingPauseAtEndOfPeriod();
    TrackGroupArray trackGroupArray = playbackInfo.trackGroups;
    TrackSelectorResult trackSelectorResult = playbackInfo.trackSelectorResult;
    List<Metadata> staticMetadata = playbackInfo.staticMetadata;
    if (mediaSourceList.isPrepared()) {
      @Nullable MediaPeriodHolder playingPeriodHolder = queue.getPlayingPeriod();
      trackGroupArray =
          playingPeriodHolder == null
              ? TrackGroupArray.EMPTY
              : playingPeriodHolder.getTrackGroups();
      trackSelectorResult =
          playingPeriodHolder == null
              ? emptyTrackSelectorResult
              : playingPeriodHolder.getTrackSelectorResult();
      staticMetadata = extractMetadataFromTrackSelectionArray(trackSelectorResult.selections);
      // Ensure the media period queue requested content position matches the new playback info.
      if (playingPeriodHolder != null
          && playingPeriodHolder.info.requestedContentPositionUs != requestedContentPositionUs) {
        playingPeriodHolder.info =
            playingPeriodHolder.info.copyWithRequestedContentPositionUs(requestedContentPositionUs);
      }
      maybeUpdateOffloadScheduling();
    } else if (!mediaPeriodId.equals(playbackInfo.periodId)) {
      // Reset previously kept track info if unprepared and the period changes.
      trackGroupArray = TrackGroupArray.EMPTY;
      trackSelectorResult = emptyTrackSelectorResult;
      staticMetadata = ImmutableList.of();
    }
    if (reportDiscontinuity) {
      playbackInfoUpdate.setPositionDiscontinuity(discontinuityReason);
    }
    return playbackInfo.copyWithNewPosition(
        mediaPeriodId,
        positionUs,
        requestedContentPositionUs,
        discontinuityStartPositionUs,
        getTotalBufferedDurationUs(),
        trackGroupArray,
        trackSelectorResult,
        staticMetadata);
  }

  private ImmutableList<Metadata> extractMetadataFromTrackSelectionArray(
      ExoTrackSelection[] trackSelections) {
    ImmutableList.Builder<Metadata> result = new ImmutableList.Builder<>();
    boolean seenNonEmptyMetadata = false;
    for (ExoTrackSelection trackSelection : trackSelections) {
      if (trackSelection != null) {
        Format format = trackSelection.getFormat(/* index= */ 0);
        if (format.metadata == null) {
          result.add(new Metadata());
        } else {
          result.add(format.metadata);
          seenNonEmptyMetadata = true;
        }
      }
    }
    return seenNonEmptyMetadata ? result.build() : ImmutableList.of();
  }

  private void enableRenderers() throws ExoPlaybackException {
    enableRenderers(
        /* rendererWasEnabledFlags= */ new boolean[renderers.length],
        queue.getReadingPeriod().getStartPositionRendererTime());
  }

  private void enableRenderers(boolean[] rendererWasEnabledFlags, long startPositionUs)
      throws ExoPlaybackException {
    MediaPeriodHolder readingMediaPeriod = queue.getReadingPeriod();
    TrackSelectorResult trackSelectorResult = readingMediaPeriod.getTrackSelectorResult();
    // Reset all disabled renderers before enabling any new ones. This makes sure resources released
    // by the disabled renderers will be available to renderers that are being enabled.
    for (int i = 0; i < renderers.length; i++) {
      if (!trackSelectorResult.isRendererEnabled(i)) {
        renderers[i].reset();
      }
    }
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelectorResult.isRendererEnabled(i)
          && !renderers[i].isReadingFromPeriod(readingMediaPeriod)) {
        enableRenderer(
            readingMediaPeriod,
            /* rendererIndex= */ i,
            rendererWasEnabledFlags[i],
            startPositionUs);
      }
    }
  }

  private void enableRenderer(
      MediaPeriodHolder periodHolder,
      int rendererIndex,
      boolean wasRendererEnabled,
      long startPositionUs)
      throws ExoPlaybackException {
    RendererHolder renderer = renderers[rendererIndex];
    if (renderer.isRendererEnabled()) {
      return;
    }
    boolean arePlayingAndReadingTheSamePeriod = periodHolder == queue.getPlayingPeriod();
    TrackSelectorResult trackSelectorResult = periodHolder.getTrackSelectorResult();
    RendererConfiguration rendererConfiguration =
        trackSelectorResult.rendererConfigurations[rendererIndex];
    ExoTrackSelection newSelection = trackSelectorResult.selections[rendererIndex];
    // The renderer needs enabling with its new track selection.
    boolean playing = shouldPlayWhenReady() && playbackInfo.playbackState == Player.STATE_READY;
    // Consider as joining only if the renderer was previously disabled and being enabled on the
    // playing period.
    boolean joining = !wasRendererEnabled && playing;
    // Enable the renderer.
    enabledRendererCount++;
    renderer.enable(
        rendererConfiguration,
        newSelection,
        periodHolder.sampleStreams[rendererIndex],
        rendererPositionUs,
        joining,
        /* mayRenderStartOfStream= */ arePlayingAndReadingTheSamePeriod,
        startPositionUs,
        periodHolder.getRendererOffset(),
        periodHolder.info.id,
        mediaClock);
    renderer.handleMessage(
        Renderer.MSG_SET_WAKEUP_LISTENER,
        new Renderer.WakeupListener() {
          @Override
          public void onSleep() {
            requestForRendererSleep = true;
          }

          @Override
          public void onWakeup() {
            if (dynamicSchedulingEnabled || offloadSchedulingEnabled) {
              handler.sendEmptyMessage(MSG_DO_SOME_WORK);
            }
          }
        },
        /* mediaPeriod= */ periodHolder);
    // Start the renderer if playing and the Playing and Reading periods are the same.
    if (playing && arePlayingAndReadingTheSamePeriod) {
      renderer.start();
    }
  }

  private void releaseRenderers() {
    for (int i = 0; i < renderers.length; i++) {
      rendererCapabilities[i].clearListener();
      renderers[i].release();
    }
  }

  private void handleLoadingMediaPeriodChanged(boolean loadingTrackSelectionChanged) {
    MediaPeriodHolder loadingMediaPeriodHolder = queue.getLoadingPeriod();
    MediaPeriodId loadingMediaPeriodId =
        loadingMediaPeriodHolder == null ? playbackInfo.periodId : loadingMediaPeriodHolder.info.id;
    boolean loadingMediaPeriodChanged =
        !playbackInfo.loadingMediaPeriodId.equals(loadingMediaPeriodId);
    if (loadingMediaPeriodChanged) {
      playbackInfo = playbackInfo.copyWithLoadingMediaPeriodId(loadingMediaPeriodId);
    }
    playbackInfo.bufferedPositionUs =
        loadingMediaPeriodHolder == null
            ? playbackInfo.positionUs
            : loadingMediaPeriodHolder.getBufferedPositionUs();
    playbackInfo.totalBufferedDurationUs = getTotalBufferedDurationUs();
    if ((loadingMediaPeriodChanged || loadingTrackSelectionChanged)
        && loadingMediaPeriodHolder != null
        && loadingMediaPeriodHolder.prepared) {
      updateLoadControlTrackSelection(
          loadingMediaPeriodHolder.info.id,
          loadingMediaPeriodHolder.getTrackGroups(),
          loadingMediaPeriodHolder.getTrackSelectorResult());
    }
  }

  private long getTotalBufferedDurationUs() {
    return getTotalBufferedDurationUs(playbackInfo.bufferedPositionUs);
  }

  private long getTotalBufferedDurationUs(long bufferedPositionInLoadingPeriodUs) {
    MediaPeriodHolder loadingPeriodHolder = queue.getLoadingPeriod();
    if (loadingPeriodHolder == null) {
      return 0;
    }
    long totalBufferedDurationUs =
        bufferedPositionInLoadingPeriodUs - loadingPeriodHolder.toPeriodTime(rendererPositionUs);
    return max(0, totalBufferedDurationUs);
  }

  private void updateLoadControlTrackSelection(
      MediaPeriodId mediaPeriodId,
      TrackGroupArray trackGroups,
      TrackSelectorResult trackSelectorResult) {
    MediaPeriodHolder loadingPeriodHolder = checkNotNull(queue.getLoadingPeriod());
    long playbackPositionUs =
        loadingPeriodHolder == queue.getPlayingPeriod()
            ? loadingPeriodHolder.toPeriodTime(rendererPositionUs)
            : loadingPeriodHolder.toPeriodTime(rendererPositionUs)
                - loadingPeriodHolder.info.startPositionUs;
    long bufferedDurationUs =
        getTotalBufferedDurationUs(loadingPeriodHolder.getBufferedPositionUs());
    long targetLiveOffsetUs =
        shouldUseLivePlaybackSpeedControl(playbackInfo.timeline, loadingPeriodHolder.info.id)
            ? livePlaybackSpeedControl.getTargetLiveOffsetUs()
            : C.TIME_UNSET;
    loadControl.onTracksSelected(
        new LoadControl.Parameters(
            playerId,
            playbackInfo.timeline,
            mediaPeriodId,
            playbackPositionUs,
            bufferedDurationUs,
            mediaClock.getPlaybackParameters().speed,
            playbackInfo.playWhenReady,
            isRebuffering,
            targetLiveOffsetUs,
            lastRebufferRealtimeMs),
        trackGroups,
        trackSelectorResult.selections);
  }

  private boolean shouldPlayWhenReady() {
    return playbackInfo.playWhenReady
        && playbackInfo.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  private void maybeThrowRendererStreamError(int rendererIndex)
      throws IOException, ExoPlaybackException {
    RendererHolder renderer = renderers[rendererIndex];
    try {
      renderer.maybeThrowStreamError(checkNotNull(queue.getPlayingPeriod()));
    } catch (IOException | RuntimeException e) {
      switch (renderer.getTrackType()) {
        case C.TRACK_TYPE_TEXT:
        case C.TRACK_TYPE_METADATA:
          TrackSelectorResult currentTrackSelectorResult =
              queue.getPlayingPeriod().getTrackSelectorResult();
          Log.e(
              TAG,
              "Disabling track due to error: "
                  + Format.toLogString(
                      currentTrackSelectorResult.selections[rendererIndex].getSelectedFormat()),
              e);

          TrackSelectorResult newTrackSelectorResult =
              new TrackSelectorResult(
                  currentTrackSelectorResult.rendererConfigurations.clone(),
                  currentTrackSelectorResult.selections.clone(),
                  currentTrackSelectorResult.tracks,
                  currentTrackSelectorResult.info);
          newTrackSelectorResult.rendererConfigurations[rendererIndex] = null;
          newTrackSelectorResult.selections[rendererIndex] = null;
          disableRenderer(rendererIndex);
          queue
              .getPlayingPeriod()
              .applyTrackSelection(
                  newTrackSelectorResult,
                  playbackInfo.positionUs,
                  /* forceRecreateStreams= */ false);
          break;
        default:
          throw e;
      }
    }
  }

  private boolean areRenderersPrewarming() {
    if (!hasSecondaryRenderers) {
      return false;
    }
    for (RendererHolder renderer : renderers) {
      if (renderer.isPrewarming()) {
        return true;
      }
    }
    return false;
  }

  private static PositionUpdateForPlaylistChange resolvePositionForPlaylistChange(
      Timeline timeline,
      PlaybackInfo playbackInfo,
      @Nullable SeekPosition pendingInitialSeekPosition,
      MediaPeriodQueue queue,
      @RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Timeline.Window window,
      Timeline.Period period) {
    if (timeline.isEmpty()) {
      return new PositionUpdateForPlaylistChange(
          PlaybackInfo.getDummyPeriodForEmptyTimeline(),
          /* periodPositionUs= */ 0,
          /* requestedContentPositionUs= */ C.TIME_UNSET,
          /* forceBufferingState= */ false,
          /* endPlayback= */ true,
          /* setTargetLiveOffset= */ false);
    }
    MediaPeriodId oldPeriodId = playbackInfo.periodId;
    Object newPeriodUid = oldPeriodId.periodUid;
    boolean isUsingPlaceholderPeriod = isUsingPlaceholderPeriod(playbackInfo, period);
    long oldContentPositionUs =
        playbackInfo.periodId.isAd() || isUsingPlaceholderPeriod
            ? playbackInfo.requestedContentPositionUs
            : playbackInfo.positionUs;
    long newContentPositionUs = oldContentPositionUs;
    int startAtDefaultPositionWindowIndex = C.INDEX_UNSET;
    boolean forceBufferingState = false;
    boolean endPlayback = false;
    boolean setTargetLiveOffset = false;
    if (pendingInitialSeekPosition != null) {
      // Resolve initial seek position.
      @Nullable
      Pair<Object, Long> periodPosition =
          resolveSeekPositionUs(
              timeline,
              pendingInitialSeekPosition,
              /* trySubsequentPeriods= */ true,
              repeatMode,
              shuffleModeEnabled,
              window,
              period);
      if (periodPosition == null) {
        // The initial seek in the empty old timeline is invalid in the new timeline.
        endPlayback = true;
        startAtDefaultPositionWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      } else {
        // The pending seek has been resolved successfully in the new timeline.
        if (pendingInitialSeekPosition.windowPositionUs == C.TIME_UNSET) {
          startAtDefaultPositionWindowIndex =
              timeline.getPeriodByUid(periodPosition.first, period).windowIndex;
        } else {
          newPeriodUid = periodPosition.first;
          newContentPositionUs = periodPosition.second;
          // Use explicit initial seek as new target live offset.
          setTargetLiveOffset = true;
        }
        forceBufferingState = playbackInfo.playbackState == Player.STATE_ENDED;
      }
    } else if (playbackInfo.timeline.isEmpty()) {
      // Resolve to default position if the old timeline is empty and no seek is requested above.
      startAtDefaultPositionWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
    } else if (timeline.getIndexOfPeriod(newPeriodUid) == C.INDEX_UNSET) {
      // The current period isn't in the new timeline. Attempt to resolve a subsequent period whose
      // window we can restart from.
      int newWindowIndex =
          resolveSubsequentPeriod(
              window,
              period,
              repeatMode,
              shuffleModeEnabled,
              newPeriodUid,
              playbackInfo.timeline,
              timeline);
      if (newWindowIndex == C.INDEX_UNSET) {
        // We failed to resolve a suitable restart position but the timeline is not empty.
        endPlayback = true;
        startAtDefaultPositionWindowIndex = timeline.getFirstWindowIndex(shuffleModeEnabled);
      } else {
        // We resolved a subsequent period. Start at the default position in the corresponding
        // window.
        startAtDefaultPositionWindowIndex = newWindowIndex;
      }
    } else if (oldContentPositionUs == C.TIME_UNSET) {
      // The content was requested to start from its default position and we haven't used the
      // resolved position yet. Re-resolve in case the default position changed.
      startAtDefaultPositionWindowIndex = timeline.getPeriodByUid(newPeriodUid, period).windowIndex;
    } else if (isUsingPlaceholderPeriod) {
      // We previously requested a content position for a placeholder period, but haven't used it
      // yet. Re-resolve the requested window position to the period position in case it changed.
      playbackInfo.timeline.getPeriodByUid(oldPeriodId.periodUid, period);
      if (playbackInfo.timeline.getWindow(period.windowIndex, window).firstPeriodIndex
          == playbackInfo.timeline.getIndexOfPeriod(oldPeriodId.periodUid)) {
        // Only need to resolve the first period in a window because subsequent periods must start
        // at position 0 and don't need to be resolved.
        long windowPositionUs = oldContentPositionUs + period.getPositionInWindowUs();
        int windowIndex = timeline.getPeriodByUid(newPeriodUid, period).windowIndex;
        Pair<Object, Long> periodPositionUs =
            timeline.getPeriodPositionUs(window, period, windowIndex, windowPositionUs);
        newPeriodUid = periodPositionUs.first;
        newContentPositionUs = periodPositionUs.second;
      }
      // Use an explicitly requested content position as new target live offset.
      setTargetLiveOffset = true;
    }

    // Set period uid for default positions and resolve position for ad resolution.
    long contentPositionForAdResolutionUs = newContentPositionUs;
    if (startAtDefaultPositionWindowIndex != C.INDEX_UNSET) {
      Pair<Object, Long> defaultPositionUs =
          timeline.getPeriodPositionUs(
              window,
              period,
              startAtDefaultPositionWindowIndex,
              /* windowPositionUs= */ C.TIME_UNSET);
      newPeriodUid = defaultPositionUs.first;
      contentPositionForAdResolutionUs = defaultPositionUs.second;
      newContentPositionUs = C.TIME_UNSET;
    }

    // Ensure ad insertion metadata is up to date.
    MediaPeriodId periodIdWithAds =
        queue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, newPeriodUid, contentPositionForAdResolutionUs);
    boolean earliestCuePointIsUnchangedOrLater =
        periodIdWithAds.nextAdGroupIndex == C.INDEX_UNSET
            || (oldPeriodId.nextAdGroupIndex != C.INDEX_UNSET
                && periodIdWithAds.nextAdGroupIndex >= oldPeriodId.nextAdGroupIndex);
    // Drop update if we keep playing the same content (MediaPeriod.periodUid are identical) and
    // the only change is that MediaPeriodId.nextAdGroupIndex increased. This postpones a potential
    // discontinuity until we reach the former next ad group position.
    boolean sameOldAndNewPeriodUid = oldPeriodId.periodUid.equals(newPeriodUid);
    boolean onlyNextAdGroupIndexIncreased =
        sameOldAndNewPeriodUid
            && !oldPeriodId.isAd()
            && !periodIdWithAds.isAd()
            && earliestCuePointIsUnchangedOrLater;
    // Drop update if the change is from/to server-side inserted ads at the same content position to
    // avoid any unintentional renderer reset.
    boolean isInStreamAdChange =
        isIgnorableServerSideAdInsertionPeriodChange(
            isUsingPlaceholderPeriod,
            oldPeriodId,
            oldContentPositionUs,
            periodIdWithAds,
            timeline.getPeriodByUid(newPeriodUid, period),
            newContentPositionUs);
    MediaPeriodId newPeriodId =
        onlyNextAdGroupIndexIncreased || isInStreamAdChange ? oldPeriodId : periodIdWithAds;

    long periodPositionUs = contentPositionForAdResolutionUs;
    if (newPeriodId.isAd()) {
      if (newPeriodId.equals(oldPeriodId)) {
        periodPositionUs = playbackInfo.positionUs;
      } else {
        timeline.getPeriodByUid(newPeriodId.periodUid, period);
        periodPositionUs =
            newPeriodId.adIndexInAdGroup == period.getFirstAdIndexToPlay(newPeriodId.adGroupIndex)
                ? period.getAdResumePositionUs()
                : 0;
      }
    }

    return new PositionUpdateForPlaylistChange(
        newPeriodId,
        periodPositionUs,
        newContentPositionUs,
        forceBufferingState,
        endPlayback,
        setTargetLiveOffset);
  }

  private static boolean isIgnorableServerSideAdInsertionPeriodChange(
      boolean isUsingPlaceholderPeriod,
      MediaPeriodId oldPeriodId,
      long oldContentPositionUs,
      MediaPeriodId newPeriodId,
      Timeline.Period newPeriod,
      long newContentPositionUs) {
    if (isUsingPlaceholderPeriod
        || oldContentPositionUs != newContentPositionUs
        || !oldPeriodId.periodUid.equals(newPeriodId.periodUid)) {
      // The period position changed.
      return false;
    }
    if (oldPeriodId.isAd() && newPeriod.isServerSideInsertedAdGroup(oldPeriodId.adGroupIndex)) {
      // Whether the old period was a server side ad that doesn't need skipping to the content.
      return newPeriod.getAdState(oldPeriodId.adGroupIndex, oldPeriodId.adIndexInAdGroup)
              != AdPlaybackState.AD_STATE_ERROR
          && newPeriod.getAdState(oldPeriodId.adGroupIndex, oldPeriodId.adIndexInAdGroup)
              != AdPlaybackState.AD_STATE_SKIPPED;
    }
    // If the new period is a server side inserted ad, we can just continue playing.
    return newPeriodId.isAd() && newPeriod.isServerSideInsertedAdGroup(newPeriodId.adGroupIndex);
  }

  private static boolean isUsingPlaceholderPeriod(
      PlaybackInfo playbackInfo, Timeline.Period period) {
    MediaPeriodId periodId = playbackInfo.periodId;
    Timeline timeline = playbackInfo.timeline;
    return timeline.isEmpty() || timeline.getPeriodByUid(periodId.periodUid, period).isPlaceholder;
  }

  /**
   * Updates the {@link #isRebuffering} state and the timestamp of the last rebuffering event.
   *
   * @param isRebuffering A boolean indicating whether the media playback is currently rebuffering.
   * @param resetLastRebufferRealtimeMs A boolean indicating whether {@link #lastRebufferRealtimeMs}
   *     should be reset.<br>
   *     If set to {@code true}, the method resets the {@link #lastRebufferRealtimeMs} to {@link
   *     C#TIME_UNSET}.<br>
   *     If set to {@code false}, the method updates the {@link #lastRebufferRealtimeMs} with the
   *     current value of {@link Clock#elapsedRealtime()}.
   */
  private void updateRebufferingState(boolean isRebuffering, boolean resetLastRebufferRealtimeMs) {
    this.isRebuffering = isRebuffering;
    this.lastRebufferRealtimeMs =
        isRebuffering && !resetLastRebufferRealtimeMs ? clock.elapsedRealtime() : C.TIME_UNSET;
  }

  /**
   * Updates pending message to a new timeline.
   *
   * @param pendingMessageInfo The pending message.
   * @param newTimeline The new timeline.
   * @param previousTimeline The previous timeline used to set the message positions.
   * @param repeatMode The current repeat mode.
   * @param shuffleModeEnabled The current shuffle mode.
   * @param window A scratch window.
   * @param period A scratch period.
   * @return Whether the message position could be resolved to the current timeline.
   */
  private static boolean resolvePendingMessagePosition(
      PendingMessageInfo pendingMessageInfo,
      Timeline newTimeline,
      Timeline previousTimeline,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Timeline.Window window,
      Timeline.Period period) {
    if (pendingMessageInfo.resolvedPeriodUid == null) {
      // Position is still unresolved. Try to find window in new timeline.
      long requestPositionUs =
          pendingMessageInfo.message.getPositionMs() == C.TIME_END_OF_SOURCE
              ? C.TIME_UNSET
              : Util.msToUs(pendingMessageInfo.message.getPositionMs());
      @Nullable
      Pair<Object, Long> periodPosition =
          resolveSeekPositionUs(
              newTimeline,
              new SeekPosition(
                  pendingMessageInfo.message.getTimeline(),
                  pendingMessageInfo.message.getMediaItemIndex(),
                  requestPositionUs),
              /* trySubsequentPeriods= */ false,
              repeatMode,
              shuffleModeEnabled,
              window,
              period);
      if (periodPosition == null) {
        return false;
      }
      pendingMessageInfo.setResolvedPosition(
          /* periodIndex= */ newTimeline.getIndexOfPeriod(periodPosition.first),
          /* periodTimeUs= */ periodPosition.second,
          /* periodUid= */ periodPosition.first);
      if (pendingMessageInfo.message.getPositionMs() == C.TIME_END_OF_SOURCE) {
        resolvePendingMessageEndOfStreamPosition(newTimeline, pendingMessageInfo, window, period);
      }
      return true;
    }
    // Position has been resolved for a previous timeline. Try to find the updated period index.
    int index = newTimeline.getIndexOfPeriod(pendingMessageInfo.resolvedPeriodUid);
    if (index == C.INDEX_UNSET) {
      return false;
    }
    if (pendingMessageInfo.message.getPositionMs() == C.TIME_END_OF_SOURCE) {
      // Re-resolve end of stream in case the duration changed.
      resolvePendingMessageEndOfStreamPosition(newTimeline, pendingMessageInfo, window, period);
      return true;
    }
    pendingMessageInfo.resolvedPeriodIndex = index;
    previousTimeline.getPeriodByUid(pendingMessageInfo.resolvedPeriodUid, period);
    if (period.isPlaceholder
        && previousTimeline.getWindow(period.windowIndex, window).firstPeriodIndex
            == previousTimeline.getIndexOfPeriod(pendingMessageInfo.resolvedPeriodUid)) {
      // The position needs to be re-resolved because the window in the previous timeline wasn't
      // fully prepared. Only resolve the first period in a window because subsequent periods must
      // start at position 0 and don't need to be resolved.
      long windowPositionUs =
          pendingMessageInfo.resolvedPeriodTimeUs + period.getPositionInWindowUs();
      int windowIndex =
          newTimeline.getPeriodByUid(pendingMessageInfo.resolvedPeriodUid, period).windowIndex;
      Pair<Object, Long> periodPositionUs =
          newTimeline.getPeriodPositionUs(window, period, windowIndex, windowPositionUs);
      pendingMessageInfo.setResolvedPosition(
          /* periodIndex= */ newTimeline.getIndexOfPeriod(periodPositionUs.first),
          /* periodTimeUs= */ periodPositionUs.second,
          /* periodUid= */ periodPositionUs.first);
    }
    return true;
  }

  private static void resolvePendingMessageEndOfStreamPosition(
      Timeline timeline,
      PendingMessageInfo messageInfo,
      Timeline.Window window,
      Timeline.Period period) {
    int windowIndex = timeline.getPeriodByUid(messageInfo.resolvedPeriodUid, period).windowIndex;
    int lastPeriodIndex = timeline.getWindow(windowIndex, window).lastPeriodIndex;
    Object lastPeriodUid = timeline.getPeriod(lastPeriodIndex, period, /* setIds= */ true).uid;
    long positionUs = period.durationUs != C.TIME_UNSET ? period.durationUs - 1 : Long.MAX_VALUE;
    messageInfo.setResolvedPosition(lastPeriodIndex, positionUs, lastPeriodUid);
  }

  /**
   * Converts a {@link SeekPosition} into the corresponding (periodUid, periodPositionUs) for the
   * internal timeline.
   *
   * @param seekPosition The position to resolve.
   * @param trySubsequentPeriods Whether the position can be resolved to a subsequent matching
   *     period if the original period is no longer available.
   * @return The resolved position, or null if resolution was not successful.
   * @throws IllegalSeekPositionException If the window index of the seek position is outside the
   *     bounds of the timeline.
   */
  @Nullable
  private static Pair<Object, Long> resolveSeekPositionUs(
      Timeline timeline,
      SeekPosition seekPosition,
      boolean trySubsequentPeriods,
      @RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Timeline.Window window,
      Timeline.Period period) {
    Timeline seekTimeline = seekPosition.timeline;
    if (timeline.isEmpty()) {
      // We don't have a valid timeline yet, so we can't resolve the position.
      return null;
    }
    if (seekTimeline.isEmpty()) {
      // The application performed a blind seek with an empty timeline (most likely based on
      // knowledge of what the future timeline will be). Use the internal timeline.
      seekTimeline = timeline;
    }
    // Map the SeekPosition to a position in the corresponding timeline.
    Pair<Object, Long> periodPositionUs;
    try {
      periodPositionUs =
          seekTimeline.getPeriodPositionUs(
              window, period, seekPosition.windowIndex, seekPosition.windowPositionUs);
    } catch (IndexOutOfBoundsException e) {
      // The window index of the seek position was outside the bounds of the timeline.
      return null;
    }
    if (timeline.equals(seekTimeline)) {
      // Our internal timeline is the seek timeline, so the mapped position is correct.
      return periodPositionUs;
    }
    // Attempt to find the mapped period in the internal timeline.
    int periodIndex = timeline.getIndexOfPeriod(periodPositionUs.first);
    if (periodIndex != C.INDEX_UNSET) {
      // We successfully located the period in the internal timeline.
      if (seekTimeline.getPeriodByUid(periodPositionUs.first, period).isPlaceholder
          && seekTimeline.getWindow(period.windowIndex, window).firstPeriodIndex
              == seekTimeline.getIndexOfPeriod(periodPositionUs.first)) {
        // The seek timeline was using a placeholder, so we need to re-resolve using the updated
        // timeline in case the resolved position changed. Only resolve the first period in a window
        // because subsequent periods must start at position 0 and don't need to be resolved.
        int newWindowIndex = timeline.getPeriodByUid(periodPositionUs.first, period).windowIndex;
        periodPositionUs =
            timeline.getPeriodPositionUs(
                window, period, newWindowIndex, seekPosition.windowPositionUs);
      }
      return periodPositionUs;
    }
    if (trySubsequentPeriods) {
      // Try and find a subsequent period from the seek timeline in the internal timeline.
      int newWindowIndex =
          resolveSubsequentPeriod(
              window,
              period,
              repeatMode,
              shuffleModeEnabled,
              periodPositionUs.first,
              seekTimeline,
              timeline);
      if (newWindowIndex != C.INDEX_UNSET) {
        // We found one. Use the default position of the corresponding window.
        return timeline.getPeriodPositionUs(
            window, period, newWindowIndex, /* windowPositionUs= */ C.TIME_UNSET);
      }
    }
    // We didn't find one. Give up.
    return null;
  }

  /**
   * Given a period index into an old timeline, searches for suitable subsequent periods in the new
   * timeline and returns their window index if found.
   *
   * @param window A {@link Timeline.Window} to be used internally.
   * @param period A {@link Timeline.Period} to be used internally.
   * @param repeatMode The repeat mode to use.
   * @param shuffleModeEnabled Whether the shuffle mode is enabled.
   * @param oldPeriodUid The index of the period in the old timeline.
   * @param oldTimeline The old timeline.
   * @param newTimeline The new timeline.
   * @return The most suitable window index in the new timeline to continue playing from, or {@link
   *     C#INDEX_UNSET} if none was found.
   */
  /* package */ static int resolveSubsequentPeriod(
      Timeline.Window window,
      Timeline.Period period,
      @Player.RepeatMode int repeatMode,
      boolean shuffleModeEnabled,
      Object oldPeriodUid,
      Timeline oldTimeline,
      Timeline newTimeline) {
    int oldWindowIndex = oldTimeline.getPeriodByUid(oldPeriodUid, period).windowIndex;
    Object oldWindowUid = oldTimeline.getWindow(oldWindowIndex, window).uid;
    // TODO: b/341049911 - Use more efficient UID based access rather than a full search.
    for (int i = 0; i < newTimeline.getWindowCount(); i++) {
      if (newTimeline.getWindow(/* windowIndex= */ i, window).uid.equals(oldWindowUid)) {
        // Window still exists, resume from there.
        return i;
      }
    }
    int oldPeriodIndex = oldTimeline.getIndexOfPeriod(oldPeriodUid);
    int newPeriodIndex = C.INDEX_UNSET;
    int maxIterations = oldTimeline.getPeriodCount();
    for (int i = 0; i < maxIterations && newPeriodIndex == C.INDEX_UNSET; i++) {
      oldPeriodIndex =
          oldTimeline.getNextPeriodIndex(
              oldPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
      if (oldPeriodIndex == C.INDEX_UNSET) {
        // We've reached the end of the old timeline.
        break;
      }
      newPeriodIndex = newTimeline.getIndexOfPeriod(oldTimeline.getUidOfPeriod(oldPeriodIndex));
    }
    return newPeriodIndex == C.INDEX_UNSET
        ? C.INDEX_UNSET
        : newTimeline.getPeriod(newPeriodIndex, period).windowIndex;
  }

  private static @Player.PlayWhenReadyChangeReason int updatePlayWhenReadyChangeReason(
      @AudioFocusManager.PlayerCommand int playerCommand,
      @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
    if (playerCommand == AudioFocusManager.PLAYER_COMMAND_DO_NOT_PLAY) {
      return Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS;
    }
    if (playWhenReadyChangeReason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
      return Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
    }
    return playWhenReadyChangeReason;
  }

  private static @Player.PlaybackSuppressionReason int updatePlaybackSuppressionReason(
      @AudioFocusManager.PlayerCommand int playerCommand,
      @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
    if (playerCommand == AudioFocusManager.PLAYER_COMMAND_WAIT_FOR_CALLBACK) {
      return Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    }
    if (playbackSuppressionReason
        == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
      return Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    }
    return playbackSuppressionReason;
  }

  private static final class SeekPosition {

    public final Timeline timeline;
    public final int windowIndex;
    public final long windowPositionUs;

    public SeekPosition(Timeline timeline, int windowIndex, long windowPositionUs) {
      this.timeline = timeline;
      this.windowIndex = windowIndex;
      this.windowPositionUs = windowPositionUs;
    }
  }

  private static final class PositionUpdateForPlaylistChange {
    public final MediaPeriodId periodId;
    public final long periodPositionUs;
    public final long requestedContentPositionUs;
    public final boolean forceBufferingState;
    public final boolean endPlayback;
    public final boolean setTargetLiveOffset;

    public PositionUpdateForPlaylistChange(
        MediaPeriodId periodId,
        long periodPositionUs,
        long requestedContentPositionUs,
        boolean forceBufferingState,
        boolean endPlayback,
        boolean setTargetLiveOffset) {
      this.periodId = periodId;
      this.periodPositionUs = periodPositionUs;
      this.requestedContentPositionUs = requestedContentPositionUs;
      this.forceBufferingState = forceBufferingState;
      this.endPlayback = endPlayback;
      this.setTargetLiveOffset = setTargetLiveOffset;
    }
  }

  private static final class PendingMessageInfo implements Comparable<PendingMessageInfo> {

    public final PlayerMessage message;

    public int resolvedPeriodIndex;
    public long resolvedPeriodTimeUs;
    @Nullable public Object resolvedPeriodUid;

    public PendingMessageInfo(PlayerMessage message) {
      this.message = message;
    }

    public void setResolvedPosition(int periodIndex, long periodTimeUs, Object periodUid) {
      resolvedPeriodIndex = periodIndex;
      resolvedPeriodTimeUs = periodTimeUs;
      resolvedPeriodUid = periodUid;
    }

    @Override
    public int compareTo(PendingMessageInfo other) {
      if ((resolvedPeriodUid == null) != (other.resolvedPeriodUid == null)) {
        // PendingMessageInfos with a resolved period position are always smaller.
        return resolvedPeriodUid != null ? -1 : 1;
      }
      if (resolvedPeriodUid == null) {
        // Don't sort message with unresolved positions.
        return 0;
      }
      // Sort resolved media times by period index and then by period position.
      int comparePeriodIndex = resolvedPeriodIndex - other.resolvedPeriodIndex;
      if (comparePeriodIndex != 0) {
        return comparePeriodIndex;
      }
      return Util.compareLong(resolvedPeriodTimeUs, other.resolvedPeriodTimeUs);
    }
  }

  private static final class MediaSourceListUpdateMessage {

    private final List<MediaSourceList.MediaSourceHolder> mediaSourceHolders;
    private final ShuffleOrder shuffleOrder;
    private final int windowIndex;
    private final long positionUs;

    private MediaSourceListUpdateMessage(
        List<MediaSourceList.MediaSourceHolder> mediaSourceHolders,
        ShuffleOrder shuffleOrder,
        int windowIndex,
        long positionUs) {
      this.mediaSourceHolders = mediaSourceHolders;
      this.shuffleOrder = shuffleOrder;
      this.windowIndex = windowIndex;
      this.positionUs = positionUs;
    }
  }

  private static class MoveMediaItemsMessage {

    public final int fromIndex;
    public final int toIndex;
    public final int newFromIndex;
    public final ShuffleOrder shuffleOrder;

    public MoveMediaItemsMessage(
        int fromIndex, int toIndex, int newFromIndex, ShuffleOrder shuffleOrder) {
      this.fromIndex = fromIndex;
      this.toIndex = toIndex;
      this.newFromIndex = newFromIndex;
      this.shuffleOrder = shuffleOrder;
    }
  }
}
