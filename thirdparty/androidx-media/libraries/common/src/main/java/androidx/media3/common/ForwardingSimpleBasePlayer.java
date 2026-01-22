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

import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/**
 * A {@link SimpleBasePlayer} that forwards all calls to another {@link Player} instance.
 *
 * <p>The class can be used to selectively override {@link #getState()} or {@code handle{Action}}
 * methods:
 *
 * <pre>{@code
 * new ForwardingSimpleBasePlayer(player) {
 *   @Override
 *   protected State getState() {
 *     State state = super.getState();
 *     // Modify current state as required:
 *     return state.buildUpon().setAvailableCommands(filteredCommands).build();
 *   }
 *
 *   @Override
 *   protected ListenableFuture<?> handleSetRepeatMode(int repeatMode) {
 *     // Modify actions by directly calling the underlying player as needed:
 *     getPlayer().setShuffleModeEnabled(true);
 *     // ..or forward to the default handling with modified parameters:
 *     return super.handleSetRepeatMode(Player.REPEAT_MODE_ALL);
 *   }
 * }
 * }</pre>
 *
 * This base class handles many aspect of the player implementation to simplify the subclass, for
 * example listener handling. See the documentation of {@link SimpleBasePlayer} for a more detailed
 * description.
 */
@UnstableApi
public class ForwardingSimpleBasePlayer extends SimpleBasePlayer {

  private final Player player;

  private LivePositionSuppliers livePositionSuppliers;
  private Metadata lastTimedMetadata;
  private @Player.PlayWhenReadyChangeReason int playWhenReadyChangeReason;
  private @Player.DiscontinuityReason int pendingDiscontinuityReason;
  private long pendingPositionDiscontinuityNewPositionMs;
  private boolean pendingFirstFrameRendered;

  /**
   * Creates the forwarding player.
   *
   * @param player The {@link Player} to forward to.
   */
  public ForwardingSimpleBasePlayer(Player player) {
    super(player.getApplicationLooper());
    this.player = player;
    this.lastTimedMetadata = new Metadata(/* presentationTimeUs= */ C.TIME_UNSET);
    this.playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
    this.pendingDiscontinuityReason = Player.DISCONTINUITY_REASON_INTERNAL;
    this.livePositionSuppliers = new LivePositionSuppliers(player);
    player.addListener(
        new Listener() {
          @Override
          public void onMetadata(Metadata metadata) {
            lastTimedMetadata = metadata;
          }

          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            playWhenReadyChangeReason = reason;
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            pendingDiscontinuityReason = reason;
            pendingPositionDiscontinuityNewPositionMs = newPosition.positionMs;
            livePositionSuppliers.disconnect(oldPosition.positionMs, oldPosition.contentPositionMs);
            livePositionSuppliers = new LivePositionSuppliers(player);
          }

          @Override
          public void onRenderedFirstFrame() {
            pendingFirstFrameRendered = true;
          }

          @SuppressWarnings("method.invocation.invalid") // Calling method from constructor.
          @Override
          public void onEvents(Player player, Events events) {
            invalidateState();
          }
        });
  }

  /** Returns the wrapped player. */
  protected final Player getPlayer() {
    return player;
  }

  @Override
  protected State getState() {
    // Ordered alphabetically by State.Builder setters.
    State.Builder state = new State.Builder();
    LivePositionSuppliers positionSuppliers = livePositionSuppliers;
    if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      state.setAdBufferedPositionMs(positionSuppliers.bufferedPositionSupplier);
      state.setAdPositionMs(positionSuppliers.currentPositionSupplier);
    }
    if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_ATTRIBUTES)) {
      state.setAudioAttributes(player.getAudioAttributes());
    }
    state.setAvailableCommands(player.getAvailableCommands());
    if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      state.setContentBufferedPositionMs(positionSuppliers.contentBufferedPositionSupplier);
      state.setContentPositionMs(positionSuppliers.contentPositionSupplier);
      if (player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)) {
        state.setCurrentAd(player.getCurrentAdGroupIndex(), player.getCurrentAdIndexInAdGroup());
      }
    }
    if (player.isCommandAvailable(Player.COMMAND_GET_TEXT)) {
      state.setCurrentCues(player.getCurrentCues());
    }
    if (player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)) {
      state.setCurrentMediaItemIndex(player.getCurrentMediaItemIndex());
    }
    state.setDeviceInfo(player.getDeviceInfo());
    if (player.isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME)) {
      state.setDeviceVolume(player.getDeviceVolume());
      state.setIsDeviceMuted(player.isDeviceMuted());
    }
    state.setIsLoading(player.isLoading());
    state.setMaxSeekToPreviousPositionMs(player.getMaxSeekToPreviousPosition());
    if (pendingFirstFrameRendered) {
      state.setNewlyRenderedFirstFrame(true);
      pendingFirstFrameRendered = false;
    }
    state.setPlaybackParameters(player.getPlaybackParameters());
    state.setPlaybackState(player.getPlaybackState());
    state.setPlaybackSuppressionReason(player.getPlaybackSuppressionReason());
    state.setPlayerError(player.getPlayerError());
    if (player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)) {
      Tracks tracks =
          player.isCommandAvailable(Player.COMMAND_GET_TRACKS)
              ? player.getCurrentTracks()
              : Tracks.EMPTY;
      MediaMetadata mediaMetadata =
          player.isCommandAvailable(Player.COMMAND_GET_METADATA) ? player.getMediaMetadata() : null;
      state.setPlaylist(player.getCurrentTimeline(), tracks, mediaMetadata);
    }
    if (player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
      state.setPlaylistMetadata(player.getPlaylistMetadata());
    }
    state.setPlayWhenReady(player.getPlayWhenReady(), playWhenReadyChangeReason);
    if (pendingPositionDiscontinuityNewPositionMs != C.TIME_UNSET) {
      state.setPositionDiscontinuity(
          pendingDiscontinuityReason, pendingPositionDiscontinuityNewPositionMs);
      pendingPositionDiscontinuityNewPositionMs = C.TIME_UNSET;
    }
    state.setRepeatMode(player.getRepeatMode());
    state.setSeekBackIncrementMs(player.getSeekBackIncrement());
    state.setSeekForwardIncrementMs(player.getSeekForwardIncrement());
    state.setShuffleModeEnabled(player.getShuffleModeEnabled());
    state.setSurfaceSize(player.getSurfaceSize());
    state.setTimedMetadata(lastTimedMetadata);
    if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      state.setTotalBufferedDurationMs(positionSuppliers.totalBufferedPositionSupplier);
    }
    state.setTrackSelectionParameters(player.getTrackSelectionParameters());
    state.setVideoSize(player.getVideoSize());
    if (player.isCommandAvailable(Player.COMMAND_GET_VOLUME)) {
      state.setVolume(player.getVolume());
    }
    return state.build();
  }

  @Override
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
    player.setPlayWhenReady(playWhenReady);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handlePrepare() {
    player.prepare();
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleStop() {
    player.stop();
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleRelease() {
    player.release();
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetRepeatMode(@Player.RepeatMode int repeatMode) {
    player.setRepeatMode(repeatMode);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
    player.setShuffleModeEnabled(shuffleModeEnabled);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
    player.setPlaybackParameters(playbackParameters);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetTrackSelectionParameters(
      TrackSelectionParameters trackSelectionParameters) {
    player.setTrackSelectionParameters(trackSelectionParameters);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetPlaylistMetadata(MediaMetadata playlistMetadata) {
    player.setPlaylistMetadata(playlistMetadata);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetVolume(float volume) {
    player.setVolume(volume);
    return Futures.immediateVoidFuture();
  }

  @SuppressWarnings("deprecation") // Calling deprecated method if updated command not available.
  @Override
  protected ListenableFuture<?> handleSetDeviceVolume(int deviceVolume, int flags) {
    if (player.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) {
      player.setDeviceVolume(deviceVolume, flags);
    } else {
      player.setDeviceVolume(deviceVolume);
    }
    return Futures.immediateVoidFuture();
  }

  @SuppressWarnings("deprecation") // Calling deprecated method if updated command not available.
  @Override
  protected ListenableFuture<?> handleIncreaseDeviceVolume(@C.VolumeFlags int flags) {
    if (player.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
      player.increaseDeviceVolume(flags);
    } else {
      player.increaseDeviceVolume();
    }
    return Futures.immediateVoidFuture();
  }

  @SuppressWarnings("deprecation") // Calling deprecated method if updated command not available.
  @Override
  protected ListenableFuture<?> handleDecreaseDeviceVolume(@C.VolumeFlags int flags) {
    if (player.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
      player.decreaseDeviceVolume(flags);
    } else {
      player.decreaseDeviceVolume();
    }
    return Futures.immediateVoidFuture();
  }

  @SuppressWarnings("deprecation") // Calling deprecated method if updated command not available.
  @Override
  protected ListenableFuture<?> handleSetDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    if (player.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
      player.setDeviceMuted(muted, flags);
    } else {
      player.setDeviceMuted(muted);
    }

    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetAudioAttributes(
      AudioAttributes audioAttributes, boolean handleAudioFocus) {
    player.setAudioAttributes(audioAttributes, handleAudioFocus);
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
    if (videoOutput instanceof SurfaceView) {
      player.setVideoSurfaceView((SurfaceView) videoOutput);
    } else if (videoOutput instanceof TextureView) {
      player.setVideoTextureView((TextureView) videoOutput);
    } else if (videoOutput instanceof SurfaceHolder) {
      player.setVideoSurfaceHolder((SurfaceHolder) videoOutput);
    } else if (videoOutput instanceof Surface) {
      player.setVideoSurface((Surface) videoOutput);
    } else {
      throw new IllegalStateException();
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
    if (videoOutput instanceof SurfaceView) {
      player.clearVideoSurfaceView((SurfaceView) videoOutput);
    } else if (videoOutput instanceof TextureView) {
      player.clearVideoTextureView((TextureView) videoOutput);
    } else if (videoOutput instanceof SurfaceHolder) {
      player.clearVideoSurfaceHolder((SurfaceHolder) videoOutput);
    } else if (videoOutput instanceof Surface) {
      player.clearVideoSurface((Surface) videoOutput);
    } else if (videoOutput == null) {
      player.clearVideoSurface();
    } else {
      throw new IllegalStateException();
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSetMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    boolean useSingleItemCall =
        mediaItems.size() == 1 && player.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM);
    if (startIndex == C.INDEX_UNSET) {
      if (useSingleItemCall) {
        player.setMediaItem(mediaItems.get(0));
      } else {
        player.setMediaItems(mediaItems);
      }
    } else {
      if (useSingleItemCall) {
        player.setMediaItem(mediaItems.get(0), startPositionMs);
      } else {
        player.setMediaItems(mediaItems, startIndex, startPositionMs);
      }
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
    if (mediaItems.size() == 1) {
      player.addMediaItem(index, mediaItems.get(0));
    } else {
      player.addMediaItems(index, mediaItems);
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleMoveMediaItems(int fromIndex, int toIndex, int newIndex) {
    if (toIndex == fromIndex + 1) {
      player.moveMediaItem(fromIndex, newIndex);
    } else {
      player.moveMediaItems(fromIndex, toIndex, newIndex);
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleReplaceMediaItems(
      int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    if (toIndex == fromIndex + 1 && mediaItems.size() == 1) {
      player.replaceMediaItem(fromIndex, mediaItems.get(0));
    } else {
      player.replaceMediaItems(fromIndex, toIndex, mediaItems);
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
    if (toIndex == fromIndex + 1) {
      player.removeMediaItem(fromIndex);
    } else {
      player.removeMediaItems(fromIndex, toIndex);
    }
    return Futures.immediateVoidFuture();
  }

  @Override
  protected ListenableFuture<?> handleSeek(
      int mediaItemIndex, long positionMs, @Command int seekCommand) {
    switch (seekCommand) {
      case Player.COMMAND_SEEK_BACK:
        player.seekBack();
        break;
      case Player.COMMAND_SEEK_FORWARD:
        player.seekForward();
        break;
      case Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM:
        player.seekTo(positionMs);
        break;
      case Player.COMMAND_SEEK_TO_DEFAULT_POSITION:
        player.seekToDefaultPosition();
        break;
      case Player.COMMAND_SEEK_TO_MEDIA_ITEM:
        if (mediaItemIndex != C.INDEX_UNSET) {
          player.seekTo(mediaItemIndex, positionMs);
        }
        break;
      case Player.COMMAND_SEEK_TO_NEXT:
        player.seekToNext();
        break;
      case Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM:
        player.seekToNextMediaItem();
        break;
      case Player.COMMAND_SEEK_TO_PREVIOUS:
        player.seekToPrevious();
        break;
      case Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM:
        player.seekToPreviousMediaItem();
        break;
      default:
        throw new IllegalStateException();
    }
    return Futures.immediateVoidFuture();
  }

  /**
   * Forwards to the changing position values of the wrapped player until the forwarding is
   * deactivated with constant values.
   */
  private static final class LivePositionSuppliers {

    public final LivePositionSupplier currentPositionSupplier;
    public final LivePositionSupplier bufferedPositionSupplier;
    public final LivePositionSupplier contentPositionSupplier;
    public final LivePositionSupplier contentBufferedPositionSupplier;
    public final LivePositionSupplier totalBufferedPositionSupplier;

    public LivePositionSuppliers(Player player) {
      currentPositionSupplier = new LivePositionSupplier(player::getCurrentPosition);
      bufferedPositionSupplier = new LivePositionSupplier(player::getBufferedPosition);
      contentPositionSupplier = new LivePositionSupplier(player::getContentPosition);
      contentBufferedPositionSupplier =
          new LivePositionSupplier(player::getContentBufferedPosition);
      totalBufferedPositionSupplier = new LivePositionSupplier(player::getTotalBufferedDuration);
    }

    public void disconnect(long positionMs, long contentPositionMs) {
      currentPositionSupplier.disconnect(positionMs);
      bufferedPositionSupplier.disconnect(positionMs);
      contentPositionSupplier.disconnect(contentPositionMs);
      contentBufferedPositionSupplier.disconnect(contentPositionMs);
      totalBufferedPositionSupplier.disconnect(/* finalValue= */ 0);
    }
  }
}
