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

package androidx.media3.ui.compose.utils

import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A fake [Player] that uses [SimpleBasePlayer]'s minimal number of default methods implementations
 * to build upon to simulate realistic playback scenarios for testing.
 */
internal class TestPlayer : SimpleBasePlayer(Looper.myLooper()!!) {
  private var state =
    State.Builder()
      .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
      .setPlaylist(
        ImmutableList.of(
          MediaItemData.Builder(/* uid= */ "First").build(),
          MediaItemData.Builder(/* uid= */ "Second").build(),
        )
      )
      .setPlayWhenReady(true, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      .build()

  var videoOutput: Any? = null
    private set

  override fun getState(): State {
    return state
  }

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    state =
      state
        .buildUpon()
        .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .build()
    return Futures.immediateVoidFuture()
  }

  override fun handlePrepare(): ListenableFuture<*> {
    state =
      state
        .buildUpon()
        .setPlayerError(null)
        .setPlaybackState(if (state.timeline.isEmpty) STATE_ENDED else STATE_BUFFERING)
        .build()
    return Futures.immediateVoidFuture()
  }

  override fun handleSeek(
    mediaItemIndex: Int,
    positionMs: Long,
    seekCommand: @Player.Command Int,
  ): ListenableFuture<*> {
    state =
      state
        .buildUpon()
        .setPlaybackState(STATE_BUFFERING)
        .setCurrentMediaItemIndex(mediaItemIndex)
        .setContentPositionMs(positionMs)
        .build()
    if (mediaItemIndex == state.playlist.size - 1) {
      removeCommands(Player.COMMAND_SEEK_TO_NEXT)
    }
    return Futures.immediateVoidFuture()
  }

  override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
    state = state.buildUpon().setShuffleModeEnabled(shuffleModeEnabled).build()
    return Futures.immediateVoidFuture()
  }

  override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
    state = state.buildUpon().setRepeatMode(repeatMode).build()
    return Futures.immediateVoidFuture()
  }

  override fun handleSetPlaybackParameters(
    playbackParameters: PlaybackParameters
  ): ListenableFuture<*> {
    state = state.buildUpon().setPlaybackParameters(playbackParameters).build()
    return Futures.immediateVoidFuture()
  }

  override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
    this.videoOutput = videoOutput
    return Futures.immediateVoidFuture()
  }

  override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
    if (videoOutput == null || videoOutput == this.videoOutput) {
      this.videoOutput = null
    }
    return Futures.immediateVoidFuture()
  }

  fun setPlaybackState(playbackState: @Player.State Int) {
    state = state.buildUpon().setPlaybackState(playbackState).build()
    invalidateState()
  }

  fun setPosition(positionMs: Long) {
    state = state.buildUpon().setContentPositionMs(positionMs).build()
    invalidateState()
  }

  fun removeCommands(vararg commands: @Player.Command Int) {
    // It doesn't seem possible to propagate the @IntDef annotation through Kotlin's spread operator
    // in a way that lint understands.
    @SuppressWarnings("WrongConstant")
    state =
      state
        .buildUpon()
        .setAvailableCommands(
          Player.Commands.Builder().addAllCommands().removeAll(*commands).build()
        )
        .build()
    invalidateState()
  }

  fun addCommands(vararg commands: @Player.Command Int) {
    // It doesn't seem possible to propagate the @IntDef annotation through Kotlin's spread operator
    // in a way that lint understands.
    @SuppressWarnings("WrongConstant")
    state =
      state
        .buildUpon()
        .setAvailableCommands(Player.Commands.Builder().addAll(*commands).build())
        .build()
    invalidateState()
  }
}
