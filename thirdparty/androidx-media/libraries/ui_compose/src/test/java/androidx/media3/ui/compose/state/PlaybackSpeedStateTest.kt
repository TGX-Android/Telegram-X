/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.media3.ui.compose.utils.TestPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [PlaybackSpeedState]. */
@RunWith(AndroidJUnit4::class)
class PlaybackSpeedStateTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun addSetSpeedAndPitchCommandToPlayer_stateTogglesFromDisabledToEnabled() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.playWhenReady = true
    player.removeCommands(Player.COMMAND_SET_SPEED_AND_PITCH)

    lateinit var state: PlaybackSpeedState
    composeTestRule.setContent { state = rememberPlaybackSpeedState(player = player) }

    assertThat(state.isEnabled).isFalse()

    player.addCommands(Player.COMMAND_SET_SPEED_AND_PITCH)
    composeTestRule.waitForIdle()

    assertThat(state.isEnabled).isTrue()
  }

  @Test
  fun removeSetSpeedAndPitchCommandToPlayer_stateTogglesFromEnabledToDisabled() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.playWhenReady = true

    lateinit var state: PlaybackSpeedState
    composeTestRule.setContent { state = rememberPlaybackSpeedState(player = player) }

    assertThat(state.isEnabled).isTrue()

    player.removeCommands(Player.COMMAND_SET_SPEED_AND_PITCH)
    composeTestRule.waitForIdle()

    assertThat(state.isEnabled).isFalse()
  }

  @Test
  fun playerPlaybackSpeedChanged_statePlaybackSpeedChanged() {
    val player = TestPlayer()

    lateinit var state: PlaybackSpeedState
    composeTestRule.setContent { state = rememberPlaybackSpeedState(player = player) }

    assertThat(state.playbackSpeed).isEqualTo(1f)

    player.playbackParameters = player.playbackParameters.withSpeed(1.5f)
    composeTestRule.waitForIdle()

    assertThat(state.playbackSpeed).isEqualTo(1.5f)
  }

  @Test
  fun stateUpdatePlaybackSpeed_playerPlaybackSpeedChanged() {
    val player = TestPlayer()
    val state = PlaybackSpeedState(player)
    assertThat(state.playbackSpeed).isEqualTo(1f)

    state.updatePlaybackSpeed(2.7f)

    assertThat(player.playbackParameters.speed).isEqualTo(2.7f)
  }
}
