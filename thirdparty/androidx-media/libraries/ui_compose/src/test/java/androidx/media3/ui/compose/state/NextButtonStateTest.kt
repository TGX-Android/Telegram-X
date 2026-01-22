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

/** Unit test for [NextButtonState]. */
@RunWith(AndroidJUnit4::class)
class NextButtonStateTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun addSeekNextCommandToPlayer_buttonStateTogglesFromDisabledToEnabled() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.playWhenReady = true
    player.removeCommands(Player.COMMAND_SEEK_TO_NEXT)

    lateinit var state: NextButtonState
    composeTestRule.setContent { state = rememberNextButtonState(player = player) }

    assertThat(state.isEnabled).isFalse()

    player.addCommands(Player.COMMAND_SEEK_TO_NEXT)
    composeTestRule.waitForIdle()

    assertThat(state.isEnabled).isTrue()
  }

  @Test
  fun removeSeekNextCommandToPlayer_buttonStateTogglesFromEnabledToDisabled() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.playWhenReady = true

    lateinit var state: NextButtonState
    composeTestRule.setContent { state = rememberNextButtonState(player = player) }

    assertThat(state.isEnabled).isTrue()

    player.removeCommands(Player.COMMAND_SEEK_TO_NEXT)
    composeTestRule.waitForIdle()

    assertThat(state.isEnabled).isFalse()
  }

  @Test
  fun clickNextOnPenultimateMediaItem_buttonStateTogglesFromEnabledToDisabled() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.playWhenReady = true

    lateinit var state: NextButtonState
    composeTestRule.setContent { state = rememberNextButtonState(player = player) }

    assertThat(state.isEnabled).isTrue()

    player.seekToNext()
    composeTestRule.waitForIdle()

    assertThat(state.isEnabled).isFalse()
  }

  @Test
  fun playerInReadyState_buttonClicked_nextItemPlaying() {
    val player = TestPlayer()
    player.playbackState = Player.STATE_READY
    player.playWhenReady = true
    val state = NextButtonState(player)

    assertThat(player.currentMediaItemIndex).isEqualTo(0)

    state.onClick()

    assertThat(player.currentMediaItemIndex).isEqualTo(1)
  }
}
