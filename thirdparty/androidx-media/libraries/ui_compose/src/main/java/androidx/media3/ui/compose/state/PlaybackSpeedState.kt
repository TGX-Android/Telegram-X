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

package androidx.media3.ui.compose.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

/**
 * Remember the value of [PlaybackSpeedState] created based on the passed [Player] and launch a
 * coroutine to listen to [Player's][Player] changes. If the [Player] instance changes between
 * compositions, produce and remember a new value.
 */
@UnstableApi
@Composable
fun rememberPlaybackSpeedState(player: Player): PlaybackSpeedState {
  val playbackSpeedState = remember(player) { PlaybackSpeedState(player) }
  LaunchedEffect(player) { playbackSpeedState.observe() }
  return playbackSpeedState
}

/**
 * State that holds all interactions to correctly deal with a UI component representing a playback
 * speed controller.
 *
 * In most cases, this will be created via [rememberPlaybackSpeedState].
 *
 * @param[player] [Player] object that operates as a state provider.
 * @property[isEnabled] determined by `isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)`
 * @property[playbackSpeed] determined by
 *   [Player.playbackParameters.speed][androidx.media3.common.PlaybackParameters.speed].
 */
@UnstableApi
class PlaybackSpeedState(private val player: Player) {
  var isEnabled by mutableStateOf(player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH))
    private set

  var playbackSpeed by mutableFloatStateOf(player.playbackParameters.speed)
    private set

  /** Updates the playback speed of the [Player] backing this state. */
  fun updatePlaybackSpeed(speed: Float) {
    player.playbackParameters = player.playbackParameters.withSpeed(speed)
  }

  /**
   * Subscribes to updates from [Player.Events] and listens to
   * * [Player.EVENT_PLAYBACK_PARAMETERS_CHANGED] in order to determine the latest playback speed.
   * * [Player.EVENT_AVAILABLE_COMMANDS_CHANGED] in order to determine whether the UI element
   *   responsible for setting the playback speed should be enabled, i.e. respond to user input.
   */
  suspend fun observe(): Nothing =
    player.listen { events ->
      if (
        events.containsAny(
          Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
        )
      ) {
        playbackSpeed = playbackParameters.speed
        isEnabled = isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)
      }
    }
}
