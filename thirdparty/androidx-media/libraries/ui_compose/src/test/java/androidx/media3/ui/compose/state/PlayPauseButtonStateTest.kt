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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.media3.ui.compose.utils.TestPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [PlayPauseButtonState]. */
@RunWith(AndroidJUnit4::class)
class PlayPauseButtonStateTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun playerIsBuffering_pausePlayer_playIconShowing() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_BUFFERING
    player.play()

    lateinit var state: PlayPauseButtonState
    composeTestRule.setContent { state = rememberPlayPauseButtonState(player = player) }

    assertThat(state.showPlay).isFalse()

    player.pause()
    composeTestRule.waitForIdle()

    assertThat(state.showPlay).isTrue()
  }

  @Test
  fun playerIsIdling_preparePlayer_pauseIconShowing() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_IDLE
    player.play()

    lateinit var state: PlayPauseButtonState
    composeTestRule.setContent { state = rememberPlayPauseButtonState(player = player) }

    assertThat(state.showPlay).isTrue()

    player.prepare()
    composeTestRule.waitForIdle()

    assertThat(state.showPlay).isFalse()
  }

  @Test
  fun addPlayPauseCommandToPlayer_buttonStateTogglesFromDisabledToEnabled() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.play()
    player.removeCommands(Player.COMMAND_PLAY_PAUSE)

    lateinit var state: PlayPauseButtonState
    composeTestRule.setContent { state = rememberPlayPauseButtonState(player = player) }

    assertThat(state.isEnabled).isFalse()

    player.addCommands(Player.COMMAND_PLAY_PAUSE)
    composeTestRule.waitForIdle()

    assertThat(state.isEnabled).isTrue()
  }

  @Test
  fun playerInReadyState_buttonClicked_playerPaused() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.play()

    val state = PlayPauseButtonState(player)

    assertThat(state.showPlay).isFalse()
    assertThat(player.playWhenReady).isTrue()

    state.onClick() // Player pauses

    assertThat(player.playWhenReady).isFalse()
  }

  @Test
  fun playerInEndedState_buttonClicked_playerBuffersAndPlays() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_ENDED
    player.setPosition(456)
    val state = PlayPauseButtonState(player)

    assertThat(state.showPlay).isTrue()

    state.onClick() // Player seeks to default position and plays

    assertThat(player.contentPosition).isEqualTo(0)
    assertThat(player.playWhenReady).isTrue()
    assertThat(player.playbackState).isEqualTo(Player.STATE_BUFFERING)
  }

  @Test
  fun playerInIdleState_buttonClicked_playerBuffersAndPlays() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_IDLE
    val state = PlayPauseButtonState(player)

    assertThat(state.showPlay).isTrue() // Player not prepared, Play icon

    state.onClick() // Player prepares and goes into buffering

    assertThat(player.playWhenReady).isTrue()
    assertThat(player.playbackState).isEqualTo(Player.STATE_BUFFERING)
  }
}
