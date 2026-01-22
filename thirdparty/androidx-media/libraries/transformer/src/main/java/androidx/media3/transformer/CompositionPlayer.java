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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.usToMs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Pair;
import android.util.SparseBooleanArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.PreviewingVideoGraph;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.Timeline;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoSize;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.effect.PreviewingSingleInputVideoGraph;
import androidx.media3.effect.TimestampAdjustment;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.image.ImageDecoder;
import androidx.media3.exoplayer.source.ClippingMediaSource;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.FilteringMediaSource;
import androidx.media3.exoplayer.source.ForwardingTimeline;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.SilenceMediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.source.WrappingMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper;
import androidx.media3.exoplayer.video.VideoFrameReleaseControl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link Player} implementation that plays {@linkplain Composition compositions} of media assets.
 * The {@link Composition} specifies how the assets should be arranged, and the audio and video
 * effects to apply to them.
 *
 * <p>{@code CompositionPlayer} instances must be accessed from a single application thread. For the
 * vast majority of cases this should be the application's main thread. The thread on which a
 * CompositionPlayer instance must be accessed can be explicitly specified by passing a {@link
 * Looper} when creating the player. If no {@link Looper} is specified, then the {@link Looper} of
 * the thread that the player is created on is used, or if that thread does not have a {@link
 * Looper}, the {@link Looper} of the application's main thread is used. In all cases the {@link
 * Looper} of the thread from which the player must be accessed can be queried using {@link
 * #getApplicationLooper()}.
 *
 * <p>This player only supports setting the {@linkplain #setRepeatMode(int) repeat mode} as
 * {@linkplain Player#REPEAT_MODE_ALL all} of the {@link Composition}, or {@linkplain
 * Player#REPEAT_MODE_OFF off}.
 */
@UnstableApi
@RestrictTo(LIBRARY_GROUP)
public final class CompositionPlayer extends SimpleBasePlayer
    implements CompositionPlayerInternal.Listener,
        PlaybackVideoGraphWrapper.Listener,
        SurfaceHolder.Callback {

  /** A builder for {@link CompositionPlayer} instances. */
  public static final class Builder {
    private final Context context;

    private @MonotonicNonNull Looper looper;
    private @MonotonicNonNull AudioSink audioSink;
    private MediaSource.Factory mediaSourceFactory;
    private ImageDecoder.Factory imageDecoderFactory;
    private boolean videoPrewarmingEnabled;
    private Clock clock;
    private PreviewingVideoGraph.@MonotonicNonNull Factory previewingVideoGraphFactory;
    private boolean built;

    /**
     * Creates an instance
     *
     * @param context The application context.
     */
    public Builder(Context context) {
      this.context = context.getApplicationContext();
      mediaSourceFactory = new DefaultMediaSourceFactory(context);
      imageDecoderFactory = ImageDecoder.Factory.DEFAULT;
      videoPrewarmingEnabled = true;
      clock = Clock.DEFAULT;
    }

    /**
     * Sets the {@link Looper} from which the player can be accessed and {@link Listener} callbacks
     * are dispatched too.
     *
     * <p>By default, the builder uses the looper of the thread that calls {@link #build()}.
     *
     * @param looper The {@link Looper}.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setLooper(Looper looper) {
      this.looper = looper;
      return this;
    }

    /**
     * Sets the {@link AudioSink} that will be used to play out audio.
     *
     * <p>By default, a {@link DefaultAudioSink} with its default configuration is used.
     *
     * @param audioSink The {@link AudioSink}.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setAudioSink(AudioSink audioSink) {
      this.audioSink = audioSink;
      return this;
    }

    /**
     * Sets the {@link MediaSource.Factory} that *creates* the {@link MediaSource} for {@link
     * EditedMediaItem#mediaItem MediaItems} in a {@link Composition}.
     *
     * <p>To use an external image loader, one could create a {@link DefaultMediaSourceFactory},
     * {@linkplain DefaultMediaSourceFactory#setExternalImageLoader set the external image loader},
     * and pass in the {@link DefaultMediaSourceFactory} here.
     *
     * @param mediaSourceFactory The {@link MediaSource.Factory}
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      this.mediaSourceFactory = mediaSourceFactory;
      return this;
    }

    /**
     * Sets an {@link ImageDecoder.Factory} that will create the {@link ImageDecoder} instances to
     * decode images.
     *
     * <p>By default, {@link ImageDecoder.Factory#DEFAULT} is used.
     *
     * @param imageDecoderFactory The {@link ImageDecoder.Factory}.
     * @return This builder, for convenience.
     */
    @CanIgnoreReturnValue
    public Builder setImageDecoderFactory(ImageDecoder.Factory imageDecoderFactory) {
      this.imageDecoderFactory = imageDecoderFactory;
      return this;
    }

    /**
     * Sets whether to enable prewarming of the video renderers.
     *
     * <p>The default value is {@code true}.
     *
     * @param videoPrewarmingEnabled Whether to enable video prewarming.
     * @return This builder, for convenience.
     */
    @VisibleForTesting
    @CanIgnoreReturnValue
    /* package */ Builder setVideoPrewarmingEnabled(boolean videoPrewarmingEnabled) {
      // TODO: b/369817794 - Remove this setter once the tests are run on a device with API < 23.
      this.videoPrewarmingEnabled = videoPrewarmingEnabled;
      return this;
    }

    /**
     * Sets the {@link Clock} that will be used by the player.
     *
     * <p>By default, {@link Clock#DEFAULT} is used.
     *
     * @param clock The {@link Clock}.
     * @return This builder, for convenience.
     */
    @VisibleForTesting
    @CanIgnoreReturnValue
    public Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    /**
     * Sets the {@link PreviewingVideoGraph.Factory} that will be used by the player.
     *
     * <p>By default, a {@link PreviewingSingleInputVideoGraph.Factory} is used.
     *
     * @param previewingVideoGraphFactory The {@link PreviewingVideoGraph.Factory}.
     * @return This builder, for convenience.
     */
    @VisibleForTesting
    @CanIgnoreReturnValue
    public Builder setPreviewingVideoGraphFactory(
        PreviewingVideoGraph.Factory previewingVideoGraphFactory) {
      this.previewingVideoGraphFactory = previewingVideoGraphFactory;
      return this;
    }

    /**
     * Builds the {@link CompositionPlayer} instance. Must be called at most once.
     *
     * <p>If no {@link Looper} has been called with {@link #setLooper(Looper)}, then this method
     * must be called within a {@link Looper} thread which is the thread that can access the player
     * instance and where {@link Listener} callbacks are dispatched.
     */
    public CompositionPlayer build() {
      checkState(!built);
      if (looper == null) {
        looper = checkStateNotNull(Looper.myLooper());
      }
      if (audioSink == null) {
        audioSink = new DefaultAudioSink.Builder(context).build();
      }
      if (previewingVideoGraphFactory == null) {
        previewingVideoGraphFactory = new PreviewingSingleInputVideoGraph.Factory();
      }
      CompositionPlayer compositionPlayer = new CompositionPlayer(this);
      built = true;
      return compositionPlayer;
    }
  }

  private static final Commands AVAILABLE_COMMANDS =
      new Commands.Builder()
          .addAll(
              COMMAND_PLAY_PAUSE,
              COMMAND_PREPARE,
              COMMAND_STOP,
              COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
              COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
              COMMAND_SEEK_TO_DEFAULT_POSITION,
              COMMAND_SEEK_BACK,
              COMMAND_SEEK_FORWARD,
              COMMAND_GET_CURRENT_MEDIA_ITEM,
              COMMAND_GET_TIMELINE,
              COMMAND_SET_REPEAT_MODE,
              COMMAND_SET_VIDEO_SURFACE,
              COMMAND_GET_VOLUME,
              COMMAND_SET_VOLUME,
              COMMAND_RELEASE)
          .build();

  private static final @Event int[] SUPPORTED_LISTENER_EVENTS =
      new int[] {
        EVENT_PLAYBACK_STATE_CHANGED,
        EVENT_PLAY_WHEN_READY_CHANGED,
        EVENT_PLAYER_ERROR,
        EVENT_POSITION_DISCONTINUITY,
        EVENT_MEDIA_ITEM_TRANSITION,
      };

  private static final String TAG = "CompositionPlayer";

  private final Context context;
  private final Clock clock;
  private final HandlerWrapper applicationHandler;
  private final List<ExoPlayer> players;
  private final AudioSink finalAudioSink;
  private final MediaSource.Factory mediaSourceFactory;
  private final ImageDecoder.Factory imageDecoderFactory;
  private final PreviewingVideoGraph.Factory previewingVideoGraphFactory;
  private final boolean videoPrewarmingEnabled;
  private final HandlerWrapper compositionInternalListenerHandler;

  /** Maps from input index to whether the video track is selected in that sequence. */
  private final SparseBooleanArray videoTracksSelected;

  private @MonotonicNonNull HandlerThread playbackThread;
  private @MonotonicNonNull CompositionPlayerInternal compositionPlayerInternal;
  private @MonotonicNonNull ImmutableList<MediaItemData> playlist;
  private @MonotonicNonNull Composition composition;
  private @MonotonicNonNull Size videoOutputSize;
  private @MonotonicNonNull PlaybackVideoGraphWrapper playbackVideoGraphWrapper;

  private long compositionDurationUs;
  private boolean playWhenReady;
  private @PlayWhenReadyChangeReason int playWhenReadyChangeReason;
  private @RepeatMode int repeatMode;
  private float volume;
  private boolean renderedFirstFrame;
  @Nullable private Object videoOutput;
  @Nullable private PlaybackException playbackException;
  private @Player.State int playbackState;
  @Nullable private SurfaceHolder surfaceHolder;
  @Nullable private Surface displaySurface;
  private boolean repeatingCompositionSeekInProgress;
  private LivePositionSupplier positionSupplier;
  private LivePositionSupplier bufferedPositionSupplier;
  private LivePositionSupplier totalBufferedDurationSupplier;
  private boolean isSeeking;

  // "this" reference for position suppliers.
  @SuppressWarnings("initialization:methodref.receiver.bound.invalid")
  private CompositionPlayer(Builder builder) {
    super(checkNotNull(builder.looper), builder.clock);
    context = builder.context;
    clock = builder.clock;
    applicationHandler = clock.createHandler(builder.looper, /* callback= */ null);
    finalAudioSink = checkNotNull(builder.audioSink);
    mediaSourceFactory = builder.mediaSourceFactory;
    imageDecoderFactory = builder.imageDecoderFactory;
    previewingVideoGraphFactory = checkNotNull(builder.previewingVideoGraphFactory);
    videoPrewarmingEnabled = builder.videoPrewarmingEnabled;
    compositionInternalListenerHandler = clock.createHandler(builder.looper, /* callback= */ null);
    videoTracksSelected = new SparseBooleanArray();
    players = new ArrayList<>();
    compositionDurationUs = C.TIME_UNSET;
    playbackState = STATE_IDLE;
    volume = 1.0f;
    positionSupplier = new LivePositionSupplier(this::getContentPositionMs);
    bufferedPositionSupplier = new LivePositionSupplier(this::getBufferedPositionMs);
    totalBufferedDurationSupplier = new LivePositionSupplier(this::getTotalBufferedDurationMs);
  }

  /**
   * Sets the {@link Composition} to play.
   *
   * <p>This method should only be called once.
   *
   * @param composition The {@link Composition} to play. Every {@link EditedMediaItem} in the {@link
   *     Composition} must have its {@link EditedMediaItem#durationUs} set.
   */
  public void setComposition(Composition composition) {
    verifyApplicationThread();
    checkArgument(!composition.sequences.isEmpty());
    checkArgument(!composition.hasGaps());
    checkState(this.composition == null);
    composition = deactivateSpeedAdjustingVideoEffects(composition);

    if (composition.sequences.size() > 1 && !previewingVideoGraphFactory.supportsMultipleInputs()) {
      Log.w(TAG, "Setting multi-sequence Composition with single input video graph.");
    }

    setCompositionInternal(composition);
    if (videoOutput != null) {
      if (videoOutput instanceof SurfaceHolder) {
        setVideoSurfaceHolderInternal((SurfaceHolder) videoOutput);
      } else if (videoOutput instanceof SurfaceView) {
        SurfaceView surfaceView = (SurfaceView) videoOutput;
        setVideoSurfaceHolderInternal(surfaceView.getHolder());
      } else if (videoOutput instanceof Surface) {
        setVideoSurfaceInternal((Surface) videoOutput, checkNotNull(videoOutputSize));
      } else {
        throw new IllegalStateException(videoOutput.getClass().toString());
      }
    }
    // Update the composition field at the end after everything else has been set.
    this.composition = composition;
  }

  /** Sets the {@link Surface} and {@link Size} to render to. */
  @VisibleForTesting
  public void setVideoSurface(Surface surface, Size videoOutputSize) {
    videoOutput = surface;
    this.videoOutputSize = videoOutputSize;
    setVideoSurfaceInternal(surface, videoOutputSize);
  }

  // PlaybackVideoGraphWrapper.Listener methods. Called on playback thread.

  @Override
  public void onFirstFrameRendered(PlaybackVideoGraphWrapper playbackVideoGraphWrapper) {
    applicationHandler.post(
        () -> {
          CompositionPlayer.this.renderedFirstFrame = true;
          invalidateState();
        });
  }

  @Override
  public void onFrameDropped(PlaybackVideoGraphWrapper playbackVideoGraphWrapper) {
    // Do not post to application thread on each dropped frame, because onFrameDropped
    // may be called frequently when resources are already scarce.
  }

  @Override
  public void onVideoSizeChanged(
      PlaybackVideoGraphWrapper playbackVideoGraphWrapper, VideoSize videoSize) {
    // TODO: b/328219481 - Report video size change to app.
  }

  @Override
  public void onError(
      PlaybackVideoGraphWrapper playbackVideoGraphWrapper,
      VideoFrameProcessingException videoFrameProcessingException) {
    // The error will also be surfaced from the underlying ExoPlayer instance via
    // PlayerListener.onPlayerError, and it will arrive to the composition player twice.
    applicationHandler.post(
        () ->
            maybeUpdatePlaybackError(
                "Error processing video frames",
                videoFrameProcessingException,
                PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED));
  }

  // SurfaceHolder.Callback methods. Called on application thread.

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    videoOutputSize = new Size(holder.getSurfaceFrame().width(), holder.getSurfaceFrame().height());
    setVideoSurfaceInternal(holder.getSurface(), videoOutputSize);
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    maybeSetOutputSurfaceInfo(width, height);
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    clearVideoSurfaceInternal();
  }

  // SimpleBasePlayer methods

  @Override
  protected State getState() {
    // TODO: b/328219481 - Report video size change to app.
    State.Builder state =
        new State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaybackState(playbackState)
            .setPlayerError(playbackException)
            .setPlayWhenReady(playWhenReady, playWhenReadyChangeReason)
            .setRepeatMode(repeatMode)
            .setVolume(volume)
            .setContentPositionMs(positionSupplier)
            .setContentBufferedPositionMs(bufferedPositionSupplier)
            .setTotalBufferedDurationMs(totalBufferedDurationSupplier)
            .setNewlyRenderedFirstFrame(getRenderedFirstFrameAndReset());
    if (repeatingCompositionSeekInProgress) {
      state.setPositionDiscontinuity(DISCONTINUITY_REASON_AUTO_TRANSITION, C.TIME_UNSET);
      repeatingCompositionSeekInProgress = false;
    }
    if (playlist != null) {
      // Update the playlist only after it has been set so that SimpleBasePlayer announces a
      // timeline change with reason TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED.
      state.setPlaylist(playlist);
    }
    return state.build();
  }

  @Override
  protected ListenableFuture<?> handlePrepare() {
    checkStateNotNull(composition, "No composition set");

    if (playbackState != Player.STATE_IDLE) {
      // The player has been prepared already.
      return Futures.immediateVoidFuture();
    }
    for (int i = 0; i < players.size(); i++) {
      players.get(i).prepare();
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
    this.playWhenReady = playWhenReady;
    playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
    if (playbackState == STATE_READY) {
      if (playWhenReady) {
        finalAudioSink.play();
      } else {
        finalAudioSink.pause();
      }
      for (int i = 0; i < players.size(); i++) {
        players.get(i).setPlayWhenReady(playWhenReady);
      }
    } // else, wait until all players are ready.
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetRepeatMode(@RepeatMode int repeatMode) {
    // Composition is treated as a single item, so only supports being repeated as a whole.
    checkArgument(repeatMode != REPEAT_MODE_ONE);
    this.repeatMode = repeatMode;
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleStop() {
    for (int i = 0; i < players.size(); i++) {
      players.get(i).stop();
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleRelease() {
    if (composition == null) {
      return Futures.immediateVoidFuture();
    }

    checkState(checkStateNotNull(playbackThread).isAlive());
    // Release the players first so that they stop rendering.
    for (int i = 0; i < players.size(); i++) {
      players.get(i).release();
    }
    checkStateNotNull(compositionPlayerInternal).release();
    removeSurfaceCallbacks();
    // Remove any queued callback from the internal player.
    compositionInternalListenerHandler.removeCallbacksAndMessages(/* token= */ null);
    displaySurface = null;
    checkStateNotNull(playbackThread).quitSafely();
    applicationHandler.removeCallbacksAndMessages(/* token= */ null);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
    checkArgument(Objects.equals(videoOutput, this.videoOutput));

    this.videoOutput = null;
    if (composition == null) {
      return Futures.immediateVoidFuture();
    }
    removeSurfaceCallbacks();
    clearVideoSurfaceInternal();
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
    if (!(videoOutput instanceof SurfaceHolder || videoOutput instanceof SurfaceView)) {
      throw new UnsupportedOperationException(videoOutput.getClass().toString());
    }
    this.videoOutput = videoOutput;
    if (composition == null) {
      return Futures.immediateVoidFuture();
    }
    if (videoOutput instanceof SurfaceHolder) {
      setVideoSurfaceHolderInternal((SurfaceHolder) videoOutput);
    } else {
      setVideoSurfaceHolderInternal(((SurfaceView) videoOutput).getHolder());
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetVolume(float volume) {
    this.volume = Util.constrainValue(volume, /* min= */ 0.0f, /* max= */ 1.0f);
    finalAudioSink.setVolume(this.volume);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSeek(
      int mediaItemIndex, long positionMs, @Command int seekCommand) {
    resetLivePositionSuppliers();
    CompositionPlayerInternal compositionPlayerInternal =
        checkStateNotNull(this.compositionPlayerInternal);
    isSeeking = true;
    compositionPlayerInternal.startSeek(positionMs);
    for (int i = 0; i < players.size(); i++) {
      players.get(i).seekTo(positionMs);
    }
    compositionPlayerInternal.endSeek();
    return Futures.immediateVoidFuture();
  }

  // CompositionPlayerInternal.Listener methods

  @Override
  public void onError(String message, Exception cause, int errorCode) {
    maybeUpdatePlaybackError(message, cause, errorCode);
  }

  // Internal methods

  private static Composition deactivateSpeedAdjustingVideoEffects(Composition composition) {
    List<EditedMediaItemSequence> newSequences = new ArrayList<>();
    for (EditedMediaItemSequence sequence : composition.sequences) {
      List<EditedMediaItem> newEditedMediaItems = new ArrayList<>();
      for (EditedMediaItem editedMediaItem : sequence.editedMediaItems) {
        ImmutableList<Effect> videoEffects = editedMediaItem.effects.videoEffects;
        List<Effect> newVideoEffects = new ArrayList<>();
        for (Effect videoEffect : videoEffects) {
          if (videoEffect instanceof TimestampAdjustment) {
            newVideoEffects.add(
                new InactiveTimestampAdjustment(((TimestampAdjustment) videoEffect).speedProvider));
          } else {
            newVideoEffects.add(videoEffect);
          }
        }
        newEditedMediaItems.add(
            editedMediaItem
                .buildUpon()
                .setEffects(new Effects(editedMediaItem.effects.audioProcessors, newVideoEffects))
                .build());
      }
      newSequences.add(
          new EditedMediaItemSequence.Builder(newEditedMediaItems)
              .setIsLooping(sequence.isLooping)
              .build());
    }
    return composition.buildUpon().setSequences(newSequences).build();
  }

  private void updatePlaybackState() {
    if (players.isEmpty() || playbackException != null) {
      playbackState = STATE_IDLE;
      return;
    }

    @Player.State int oldPlaybackState = playbackState;

    int idleCount = 0;
    int bufferingCount = 0;
    int endedCount = 0;
    for (int i = 0; i < players.size(); i++) {
      @Player.State int playbackState = players.get(i).getPlaybackState();
      switch (playbackState) {
        case STATE_IDLE:
          idleCount++;
          break;
        case STATE_BUFFERING:
          bufferingCount++;
          break;
        case STATE_READY:
          // ignore
          break;
        case STATE_ENDED:
          endedCount++;
          break;
        default:
          throw new IllegalStateException(String.valueOf(playbackState));
      }
    }
    if (idleCount > 0) {
      playbackState = STATE_IDLE;
    } else if (bufferingCount > 0) {
      playbackState = STATE_BUFFERING;
      if (oldPlaybackState == STATE_READY && playWhenReady) {
        // We were playing but a player got in buffering state, pause the players.
        for (int i = 0; i < players.size(); i++) {
          players.get(i).setPlayWhenReady(false);
        }
        if (!isSeeking) {
          // The finalAudioSink cannot be paused more than once. The audio pipeline pauses it during
          // a seek, so don't pause here when seeking.
          finalAudioSink.pause();
        }
      }
    } else if (endedCount == players.size()) {
      playbackState = STATE_ENDED;
    } else {
      playbackState = STATE_READY;
      isSeeking = false;
      if (oldPlaybackState != STATE_READY && playWhenReady) {
        for (int i = 0; i < players.size(); i++) {
          players.get(i).setPlayWhenReady(true);
        }
        finalAudioSink.play();
      }
    }
  }

  @SuppressWarnings("VisibleForTests") // Calls ExoPlayer.Builder.setClock()
  private void setCompositionInternal(Composition composition) {
    compositionDurationUs = getCompositionDurationUs(composition);
    playbackThread = new HandlerThread("CompositionPlaybackThread", Process.THREAD_PRIORITY_AUDIO);
    playbackThread.start();
    // Create the audio and video composition components now in order to setup the audio and video
    // pipelines. Once this method returns, further access to the audio and video graph wrappers
    // must done on the playback thread only, to ensure related components are accessed from one
    // thread only.
    PlaybackAudioGraphWrapper playbackAudioGraphWrapper =
        new PlaybackAudioGraphWrapper(
            new DefaultAudioMixer.Factory(),
            composition.effects.audioProcessors,
            checkNotNull(finalAudioSink));
    VideoFrameReleaseControl videoFrameReleaseControl =
        new VideoFrameReleaseControl(
            context, new CompositionFrameTimingEvaluator(), /* allowedJoiningTimeMs= */ 0);
    playbackVideoGraphWrapper =
        new PlaybackVideoGraphWrapper.Builder(context, videoFrameReleaseControl)
            .setPreviewingVideoGraphFactory(checkNotNull(previewingVideoGraphFactory))
            .setCompositorSettings(composition.videoCompositorSettings)
            .setCompositionEffects(composition.effects.videoEffects)
            .setClock(clock)
            .setRequestOpenGlToneMapping(
                composition.hdrMode == Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build();
    playbackVideoGraphWrapper.addListener(this);

    long primarySequenceDurationUs =
        getSequenceDurationUs(checkNotNull(composition.sequences.get(0)));
    for (int i = 0; i < composition.sequences.size(); i++) {
      EditedMediaItemSequence editedMediaItemSequence = composition.sequences.get(i);
      SequenceRenderersFactory sequenceRenderersFactory =
          SequenceRenderersFactory.create(
              context,
              editedMediaItemSequence,
              playbackAudioGraphWrapper,
              playbackVideoGraphWrapper.getSink(/* inputIndex= */ i),
              imageDecoderFactory,
              /* inputIndex= */ i,
              /* requestToneMapping= */ composition.hdrMode
                  == Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC,
              videoPrewarmingEnabled);

      ExoPlayer.Builder playerBuilder =
          new ExoPlayer.Builder(context)
              .setLooper(getApplicationLooper())
              .setPlaybackLooper(playbackThread.getLooper())
              .setRenderersFactory(sequenceRenderersFactory)
              .setHandleAudioBecomingNoisy(true)
              .setClock(clock)
              // Use dynamic scheduling to show the first video/image frame more promptly when the
              // player is paused (which is common in editing applications).
              .experimentalSetDynamicSchedulingEnabled(true);

      boolean disableVideoPlayback = false;
      for (int j = 0; j < editedMediaItemSequence.editedMediaItems.size(); j++) {
        if (editedMediaItemSequence.editedMediaItems.get(j).removeVideo) {
          disableVideoPlayback = true;
          break;
        }
      }
      playerBuilder.setTrackSelector(
          new CompositionTrackSelector(context, /* sequenceIndex= */ i, disableVideoPlayback));

      ExoPlayer player = playerBuilder.build();
      player.addListener(new PlayerListener(i));
      player.addAnalyticsListener(new EventLogger());
      player.setPauseAtEndOfMediaItems(true);

      if (i == 0) {
        setPrimaryPlayerSequence(player, editedMediaItemSequence);
      } else {
        setSecondaryPlayerSequence(player, editedMediaItemSequence, primarySequenceDurationUs);
      }

      players.add(player);
      if (i == 0) {
        // Invalidate the player state before initializing the playlist to force SimpleBasePlayer
        // to collect a state while the playlist is null. Consequently, once the playlist is
        // initialized, SimpleBasePlayer will raise a timeline change callback with reason
        // TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED.
        invalidateState();
        playlist = createPlaylist();
      }
    }
    // From here after, composition player accessed the audio and video pipelines via the internal
    // player. The internal player ensures access to the components is done on the playback thread.
    compositionPlayerInternal =
        new CompositionPlayerInternal(
            playbackThread.getLooper(),
            clock,
            playbackAudioGraphWrapper,
            playbackVideoGraphWrapper,
            /* listener= */ this,
            compositionInternalListenerHandler);
  }

  private void setPrimaryPlayerSequence(ExoPlayer player, EditedMediaItemSequence sequence) {
    ConcatenatingMediaSource2.Builder mediaSourceBuilder = new ConcatenatingMediaSource2.Builder();

    for (int i = 0; i < sequence.editedMediaItems.size(); i++) {
      EditedMediaItem editedMediaItem = sequence.editedMediaItems.get(i);
      checkArgument(editedMediaItem.durationUs != C.TIME_UNSET);
      long durationUs = editedMediaItem.getPresentationDurationUs();

      MediaSource silenceGeneratedMediaSource =
          createMediaSourceWithSilence(mediaSourceFactory, editedMediaItem);

      MediaSource itemMediaSource =
          wrapWithVideoEffectsBasedMediaSources(
              silenceGeneratedMediaSource, editedMediaItem.effects.videoEffects, durationUs);
      mediaSourceBuilder.add(
          itemMediaSource, /* initialPlaceholderDurationMs= */ usToMs(durationUs));
    }
    player.setMediaSource(mediaSourceBuilder.build());
  }

  private static MediaSource createMediaSourceWithSilence(
      MediaSource.Factory mediaSourceFactory, EditedMediaItem editedMediaItem) {
    // The MediaSource that loads the MediaItem
    MediaSource mainMediaSource = mediaSourceFactory.createMediaSource(editedMediaItem.mediaItem);
    if (editedMediaItem.removeAudio) {
      mainMediaSource =
          new FilteringMediaSource(
              mainMediaSource, ImmutableSet.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_IMAGE));
    }

    MediaSource silenceMediaSource =
        new ClippingMediaSource.Builder(new SilenceMediaSource(editedMediaItem.durationUs))
            .setStartPositionUs(editedMediaItem.mediaItem.clippingConfiguration.startPositionUs)
            .setEndPositionUs(editedMediaItem.mediaItem.clippingConfiguration.endPositionUs)
            .build();

    return new MergingMediaSource(mainMediaSource, silenceMediaSource);
  }

  private void setSecondaryPlayerSequence(
      ExoPlayer player, EditedMediaItemSequence sequence, long primarySequenceDurationUs) {

    ConcatenatingMediaSource2.Builder mediaSourceBuilder = new ConcatenatingMediaSource2.Builder();

    if (!sequence.isLooping) {
      for (int i = 0; i < sequence.editedMediaItems.size(); i++) {
        EditedMediaItem editedMediaItem = sequence.editedMediaItems.get(i);
        mediaSourceBuilder.add(
            createMediaSourceWithSilence(mediaSourceFactory, editedMediaItem),
            /* initialPlaceholderDurationMs= */ usToMs(
                editedMediaItem.getPresentationDurationUs()));
      }
      player.setMediaSource(mediaSourceBuilder.build());
      return;
    }

    long accumulatedDurationUs = 0;
    int i = 0;
    while (accumulatedDurationUs < primarySequenceDurationUs) {
      EditedMediaItem editedMediaItem = sequence.editedMediaItems.get(i);
      long itemPresentationDurationUs = editedMediaItem.getPresentationDurationUs();
      if (accumulatedDurationUs + itemPresentationDurationUs <= primarySequenceDurationUs) {
        mediaSourceBuilder.add(
            createMediaSourceWithSilence(mediaSourceFactory, editedMediaItem),
            /* initialPlaceholderDurationMs= */ usToMs(itemPresentationDurationUs));
        accumulatedDurationUs += itemPresentationDurationUs;
      } else {
        long remainingDurationUs = primarySequenceDurationUs - accumulatedDurationUs;
        // TODO: b/289989542 - Handle already clipped, or speed adjusted media.
        mediaSourceBuilder.add(
            createMediaSourceWithSilence(
                mediaSourceFactory, clipToDuration(editedMediaItem, remainingDurationUs)));
        break;
      }
      i = (i + 1) % sequence.editedMediaItems.size();
    }
    player.setMediaSource(mediaSourceBuilder.build());
  }

  private static EditedMediaItem clipToDuration(EditedMediaItem editedMediaItem, long durationUs) {
    MediaItem.ClippingConfiguration clippingConfiguration =
        editedMediaItem.mediaItem.clippingConfiguration;
    return editedMediaItem
        .buildUpon()
        .setMediaItem(
            editedMediaItem
                .mediaItem
                .buildUpon()
                .setClippingConfiguration(
                    clippingConfiguration
                        .buildUpon()
                        .setEndPositionUs(clippingConfiguration.startPositionUs + durationUs)
                        .build())
                .build())
        .build();
  }

  private MediaSource wrapWithVideoEffectsBasedMediaSources(
      MediaSource mediaSource, ImmutableList<Effect> videoEffects, long durationUs) {
    MediaSource newMediaSource = mediaSource;
    for (Effect videoEffect : videoEffects) {
      if (videoEffect instanceof InactiveTimestampAdjustment) {
        newMediaSource =
            wrapWithSpeedChangingMediaSource(
                newMediaSource,
                ((InactiveTimestampAdjustment) videoEffect).speedProvider,
                durationUs);
      }
    }
    return newMediaSource;
  }

  private MediaSource wrapWithSpeedChangingMediaSource(
      MediaSource mediaSource, SpeedProvider speedProvider, long durationUs) {
    return new WrappingMediaSource(mediaSource) {

      @Override
      public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
        return new SpeedProviderMediaPeriod(
            super.createPeriod(id, allocator, startPositionUs), speedProvider);
      }

      @Override
      public void releasePeriod(MediaPeriod mediaPeriod) {
        MediaPeriod wrappedPeriod = ((SpeedProviderMediaPeriod) mediaPeriod).mediaPeriod;
        super.releasePeriod(wrappedPeriod);
      }

      @Override
      protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
        Timeline timeline =
            new ForwardingTimeline(newTimeline) {
              @Override
              public Window getWindow(
                  int windowIndex, Window window, long defaultPositionProjectionUs) {
                Window wrappedWindow =
                    newTimeline.getWindow(windowIndex, window, defaultPositionProjectionUs);
                wrappedWindow.durationUs = durationUs;
                return wrappedWindow;
              }

              @Override
              public Period getPeriod(int periodIndex, Period period, boolean setIds) {
                Period wrappedPeriod = newTimeline.getPeriod(periodIndex, period, setIds);
                wrappedPeriod.durationUs = durationUs;
                return wrappedPeriod;
              }
            };
        super.onChildSourceInfoRefreshed(timeline);
      }
    };
  }

  private long getContentPositionMs() {
    if (players.isEmpty()) {
      return 0;
    }

    long lastContentPositionMs = 0;
    for (int i = 0; i < players.size(); i++) {
      lastContentPositionMs = max(lastContentPositionMs, players.get(i).getContentPosition());
    }
    return lastContentPositionMs;
  }

  private long getBufferedPositionMs() {
    if (players.isEmpty()) {
      return 0;
    }
    // Return the minimum buffered position among players.
    long minBufferedPositionMs = Integer.MAX_VALUE;
    for (int i = 0; i < players.size(); i++) {
      @Player.State int playbackState = players.get(i).getPlaybackState();
      if (playbackState == STATE_READY || playbackState == STATE_BUFFERING) {
        minBufferedPositionMs = min(minBufferedPositionMs, players.get(i).getBufferedPosition());
      }
    }
    return minBufferedPositionMs == Integer.MAX_VALUE
        // All players are ended or idle.
        ? 0
        : minBufferedPositionMs;
  }

  private long getTotalBufferedDurationMs() {
    if (players.isEmpty()) {
      return 0;
    }
    // Return the minimum total buffered duration among players.
    long minTotalBufferedDurationMs = Integer.MAX_VALUE;
    for (int i = 0; i < players.size(); i++) {
      @Player.State int playbackState = players.get(i).getPlaybackState();
      if (playbackState == STATE_READY || playbackState == STATE_BUFFERING) {
        minTotalBufferedDurationMs =
            min(minTotalBufferedDurationMs, players.get(i).getTotalBufferedDuration());
      }
    }
    return minTotalBufferedDurationMs == Integer.MAX_VALUE
        // All players are ended or idle.
        ? 0
        : minTotalBufferedDurationMs;
  }

  private boolean getRenderedFirstFrameAndReset() {
    boolean value = renderedFirstFrame;
    renderedFirstFrame = false;
    return value;
  }

  private void maybeUpdatePlaybackError(
      String errorMessage, Exception cause, @PlaybackException.ErrorCode int errorCode) {
    if (playbackException == null) {
      playbackException = new PlaybackException(errorMessage, cause, errorCode);
      for (int i = 0; i < players.size(); i++) {
        players.get(i).stop();
      }
      updatePlaybackState();
      // Invalidate the parent class state.
      invalidateState();
    } else {
      Log.w(TAG, errorMessage, cause);
    }
  }

  private void setVideoSurfaceHolderInternal(SurfaceHolder surfaceHolder) {
    removeSurfaceCallbacks();
    this.surfaceHolder = surfaceHolder;
    surfaceHolder.addCallback(this);
    Surface surface = surfaceHolder.getSurface();
    if (surface != null && surface.isValid()) {
      videoOutputSize =
          new Size(
              surfaceHolder.getSurfaceFrame().width(), surfaceHolder.getSurfaceFrame().height());
      setVideoSurfaceInternal(surface, videoOutputSize);
    } else {
      clearVideoSurfaceInternal();
    }
  }

  private void setVideoSurfaceInternal(Surface surface, Size videoOutputSize) {
    displaySurface = surface;
    maybeSetOutputSurfaceInfo(videoOutputSize.getWidth(), videoOutputSize.getHeight());
  }

  private void maybeSetOutputSurfaceInfo(int width, int height) {
    Surface surface = displaySurface;
    if (width == 0 || height == 0 || surface == null || compositionPlayerInternal == null) {
      return;
    }
    compositionPlayerInternal.setOutputSurfaceInfo(surface, new Size(width, height));
  }

  private void clearVideoSurfaceInternal() {
    displaySurface = null;
    if (compositionPlayerInternal != null) {
      compositionPlayerInternal.clearOutputSurface();
    }
  }

  private void removeSurfaceCallbacks() {
    if (surfaceHolder != null) {
      surfaceHolder.removeCallback(this);
      surfaceHolder = null;
    }
  }

  private void repeatCompositionPlayback() {
    repeatingCompositionSeekInProgress = true;
    seekToDefaultPosition();
  }

  private ImmutableList<MediaItemData> createPlaylist() {
    checkNotNull(compositionDurationUs != C.TIME_UNSET);
    return ImmutableList.of(
        new MediaItemData.Builder("CompositionTimeline")
            .setMediaItem(MediaItem.EMPTY)
            .setDurationUs(compositionDurationUs)
            .build());
  }

  private void resetLivePositionSuppliers() {
    positionSupplier.disconnect(getContentPositionMs());
    bufferedPositionSupplier.disconnect(getBufferedPositionMs());
    totalBufferedDurationSupplier.disconnect(getTotalBufferedDurationMs());
    positionSupplier = new LivePositionSupplier(this::getContentPositionMs);
    bufferedPositionSupplier = new LivePositionSupplier(this::getBufferedPositionMs);
    totalBufferedDurationSupplier = new LivePositionSupplier(this::getTotalBufferedDurationMs);
  }

  private static long getCompositionDurationUs(Composition composition) {
    checkState(!composition.sequences.isEmpty());
    long longestSequenceDurationUs = Integer.MIN_VALUE;
    for (int i = 0; i < composition.sequences.size(); i++) {
      longestSequenceDurationUs =
          max(longestSequenceDurationUs, getSequenceDurationUs(composition.sequences.get(i)));
    }
    return longestSequenceDurationUs;
  }

  private static long getSequenceDurationUs(EditedMediaItemSequence sequence) {
    long compositionDurationUs = 0;
    for (int i = 0; i < sequence.editedMediaItems.size(); i++) {
      compositionDurationUs += sequence.editedMediaItems.get(i).getPresentationDurationUs();
    }
    checkState(compositionDurationUs > 0, String.valueOf(compositionDurationUs));
    return compositionDurationUs;
  }

  /**
   * A {@link VideoFrameReleaseControl.FrameTimingEvaluator} for composition frames.
   *
   * <ul>
   *   <li>Signals to {@linkplain
   *       VideoFrameReleaseControl.FrameTimingEvaluator#shouldForceReleaseFrame(long, long) force
   *       release} a frame if the frame is late by more than {@link #FRAME_LATE_THRESHOLD_US} and
   *       the elapsed time since the previous frame release is greater than {@link
   *       #FRAME_RELEASE_THRESHOLD_US}.
   *   <li>Signals to {@linkplain
   *       VideoFrameReleaseControl.FrameTimingEvaluator#shouldDropFrame(long, long, boolean) drop a
   *       frame} if the frame is late by more than {@link #FRAME_LATE_THRESHOLD_US} and the frame
   *       is not marked as the last one.
   *   <li>Signals to never {@linkplain
   *       VideoFrameReleaseControl.FrameTimingEvaluator#shouldIgnoreFrame(long, long, long,
   *       boolean, boolean) ignore} a frame.
   * </ul>
   */
  private static final class CompositionFrameTimingEvaluator
      implements VideoFrameReleaseControl.FrameTimingEvaluator {

    /** The time threshold, in microseconds, after which a frame is considered late. */
    private static final long FRAME_LATE_THRESHOLD_US = -30_000;

    /**
     * The maximum elapsed time threshold, in microseconds, since last releasing a frame after which
     * a frame can be force released.
     */
    private static final long FRAME_RELEASE_THRESHOLD_US = 100_000;

    @Override
    public boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs) {
      return earlyUs < FRAME_LATE_THRESHOLD_US
          && elapsedSinceLastReleaseUs > FRAME_RELEASE_THRESHOLD_US;
    }

    @Override
    public boolean shouldDropFrame(long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
      return earlyUs < FRAME_LATE_THRESHOLD_US && !isLastFrame;
    }

    @Override
    public boolean shouldIgnoreFrame(
        long earlyUs,
        long positionUs,
        long elapsedRealtimeUs,
        boolean isLastFrame,
        boolean treatDroppedBuffersAsSkipped) {
      // TODO: b/293873191 - Handle very late buffers and drop to key frame.
      return false;
    }
  }

  private final class PlayerListener implements Listener {
    private final int playerIndex;

    public PlayerListener(int playerIndex) {
      this.playerIndex = playerIndex;
    }

    @Override
    public void onEvents(Player player, Events events) {
      if (events.containsAny(SUPPORTED_LISTENER_EVENTS)) {
        invalidateState();
      }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
      updatePlaybackState();
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
      playWhenReadyChangeReason = reason;
      if (reason == PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
          && repeatMode != REPEAT_MODE_OFF
          && playerIndex == 0) {
        repeatCompositionPlayback();
      }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      maybeUpdatePlaybackError("error from player " + playerIndex, error, error.errorCode);
    }
  }

  private void onVideoTrackSelection(boolean selected, int inputIndex) {
    videoTracksSelected.put(inputIndex, selected);

    if (videoTracksSelected.size() == checkNotNull(composition).sequences.size()) {
      int selectedVideoTracks = 0;
      for (int i = 0; i < videoTracksSelected.size(); i++) {
        if (videoTracksSelected.get(videoTracksSelected.keyAt(i))) {
          selectedVideoTracks++;
        }
      }

      checkNotNull(playbackVideoGraphWrapper).setTotalVideoInputCount(selectedVideoTracks);
    }
  }

  /**
   * A {@link DefaultTrackSelector} extension to de-select generated audio when the audio from the
   * media is playable.
   */
  private final class CompositionTrackSelector extends DefaultTrackSelector {

    private static final String SILENCE_AUDIO_TRACK_GROUP_ID = "1:";
    private final int sequenceIndex;
    private final boolean disableVideoPlayback;

    public CompositionTrackSelector(
        Context context, int sequenceIndex, boolean disableVideoPlayback) {
      super(context);
      this.sequenceIndex = sequenceIndex;
      this.disableVideoPlayback = disableVideoPlayback;
    }

    @Nullable
    @Override
    protected Pair<ExoTrackSelection.Definition, Integer> selectAudioTrack(
        MappedTrackInfo mappedTrackInfo,
        @RendererCapabilities.Capabilities int[][][] rendererFormatSupports,
        @RendererCapabilities.AdaptiveSupport int[] rendererMixedMimeTypeAdaptationSupports,
        Parameters params)
        throws ExoPlaybackException {
      int audioRenderIndex = C.INDEX_UNSET;
      for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
        if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
          audioRenderIndex = i;
          break;
        }
      }
      checkState(audioRenderIndex != C.INDEX_UNSET);

      TrackGroupArray audioTrackGroups = mappedTrackInfo.getTrackGroups(audioRenderIndex);
      // If there's only one audio TrackGroup, it'll be silence, there's no need to override track
      // selection.
      if (audioTrackGroups.length > 1) {
        boolean mediaAudioIsPlayable = false;
        int silenceAudioTrackGroupIndex = C.INDEX_UNSET;
        for (int i = 0; i < audioTrackGroups.length; i++) {
          if (audioTrackGroups.get(i).id.startsWith(SILENCE_AUDIO_TRACK_GROUP_ID)) {
            silenceAudioTrackGroupIndex = i;
            continue;
          }
          // For non-silence tracks
          for (int j = 0; j < audioTrackGroups.get(i).length; j++) {
            mediaAudioIsPlayable |=
                RendererCapabilities.getFormatSupport(
                        rendererFormatSupports[audioRenderIndex][i][j])
                    == C.FORMAT_HANDLED;
          }
        }
        checkState(silenceAudioTrackGroupIndex != C.INDEX_UNSET);

        if (mediaAudioIsPlayable) {
          // Disable silence if the media's audio track is playable.
          int silenceAudioTrackIndex = audioTrackGroups.length - 1;
          rendererFormatSupports[audioRenderIndex][silenceAudioTrackIndex][0] =
              RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
        }
      }

      return super.selectAudioTrack(
          mappedTrackInfo, rendererFormatSupports, rendererMixedMimeTypeAdaptationSupports, params);
    }

    @Nullable
    @Override
    protected Pair<ExoTrackSelection.Definition, Integer> selectVideoTrack(
        MappedTrackInfo mappedTrackInfo,
        @RendererCapabilities.Capabilities int[][][] rendererFormatSupports,
        @RendererCapabilities.AdaptiveSupport int[] mixedMimeTypeSupports,
        Parameters params,
        @Nullable String selectedAudioLanguage)
        throws ExoPlaybackException {
      @Nullable
      Pair<ExoTrackSelection.Definition, Integer> trackSelection =
          super.selectVideoTrack(
              mappedTrackInfo,
              rendererFormatSupports,
              mixedMimeTypeSupports,
              params,
              selectedAudioLanguage);
      if (disableVideoPlayback) {
        trackSelection = null;
      }
      onVideoTrackSelection(/* selected= */ trackSelection != null, sequenceIndex);
      return trackSelection;
    }

    @Nullable
    @Override
    protected Pair<ExoTrackSelection.Definition, Integer> selectImageTrack(
        MappedTrackInfo mappedTrackInfo,
        @RendererCapabilities.Capabilities int[][][] rendererFormatSupports,
        Parameters params)
        throws ExoPlaybackException {
      @Nullable
      Pair<ExoTrackSelection.Definition, Integer> trackSelection =
          super.selectImageTrack(mappedTrackInfo, rendererFormatSupports, params);
      if (disableVideoPlayback) {
        trackSelection = null;
      }
      // Images are treated as video tracks.
      onVideoTrackSelection(/* selected= */ trackSelection != null, sequenceIndex);
      return trackSelection;
    }
  }
}
