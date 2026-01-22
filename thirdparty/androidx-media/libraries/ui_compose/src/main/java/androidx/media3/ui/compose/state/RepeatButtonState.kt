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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

/**
 * Remember the value of [RepeatButtonState] created based on the passed [Player] and launch a
 * coroutine to listen to [Player's][Player] changes. If the [Player] instance changes between
 * compositions, produce and remember a new value.
 */
@UnstableApi
@Composable
fun rememberRepeatButtonState(
  player: Player,
  toggleModeSequence: List<@Player.RepeatMode Int> =
    listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL),
): RepeatButtonState {
  val repeatButtonState = remember(player) { RepeatButtonState(player, toggleModeSequence) }
  LaunchedEffect(player) { repeatButtonState.observe() }
  return repeatButtonState
}

/**
 * State that holds all interactions to correctly deal with a UI component representing a Repeat
 * On/All/Off button.
 *
 * @param[player] [Player] object that operates as a state provider and can be control via clicking
 * @param[toggleModeSequence] An ordered list of [Player.RepeatMode]s to cycle through when the
 *   button is clicked. Defaults to [Player.REPEAT_MODE_OFF], [Player.REPEAT_MODE_ONE],
 *   [Player.REPEAT_MODE_ALL].
 * @property[isEnabled] determined by `isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)`
 * @property[repeatModeState] determined by [Player's][Player] `repeatMode`. Note that there is no
 *   guarantee for this state to be one from [toggleModeSequence]. A button click in such case will
 *   toggle the mode into the first one of [toggleModeSequence].
 */
@UnstableApi
class RepeatButtonState(
  private val player: Player,
  private val toggleModeSequence: List<@Player.RepeatMode Int> =
    listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL),
) {
  var isEnabled by
    mutableStateOf(
      player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) && toggleModeSequence.isNotEmpty()
    )
    private set

  var repeatModeState by mutableIntStateOf(player.repeatMode)
    private set

  /**
   * Cycles to the next repeat mode in the [toggleModeSequence]. If the current repeat mode from the
   * [Player] is not among the modes in the provided [toggleModeSequence], pick the first one.
   */
  fun onClick() {
    player.repeatMode = getNextRepeatModeInSequence()
  }

  /**
   * Subscribes to updates from [Player.Events] and listens to
   * * [Player.EVENT_REPEAT_MODE_CHANGED] in order to determine the latest [Player.getRepeatMode].
   * * [Player.EVENT_AVAILABLE_COMMANDS_CHANGED] in order to determine whether the button should be
   *   enabled, i.e. respond to user input.
   */
  suspend fun observe(): Nothing =
    player.listen { events ->
      if (
        events.containsAny(
          Player.EVENT_REPEAT_MODE_CHANGED,
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
        )
      ) {
        repeatModeState = repeatMode
        isEnabled = isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)
      }
    }

  private fun getNextRepeatModeInSequence(): @Player.RepeatMode Int {
    val currRepeatModeIndex = toggleModeSequence.indexOf(player.repeatMode)
    // -1 (i.e. not found) and the last element both loop back to 0
    return toggleModeSequence[(currRepeatModeIndex + 1) % toggleModeSequence.size]
  }
}
