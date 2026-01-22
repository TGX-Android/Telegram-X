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
import androidx.media3.ui.compose.utils.TestPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** Unit test for [ShuffleButtonState]. */
@RunWith(AndroidJUnit4::class)
class ShuffleButtonStateTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun playerShuffleModeChanged_buttonShuffleModeChanged() {
    val player = TestPlayer()

    lateinit var state: ShuffleButtonState
    composeTestRule.setContent { state = rememberShuffleButtonState(player = player) }

    assertThat(state.shuffleOn).isFalse()

    player.shuffleModeEnabled = true
    composeTestRule.waitForIdle()

    assertThat(state.shuffleOn).isTrue()
  }

  @Test
  fun buttonClicked_playerShuffleModeChanged() {
    val player = TestPlayer()
    val state = ShuffleButtonState(player)
    assertThat(state.shuffleOn).isFalse()

    state.onClick()

    assertThat(player.shuffleModeEnabled).isTrue()
  }

  @Test
  fun playerSetShuffleModeAndOnClick_inTheSameHandlerMessage_uiStateSynchronises() {
    // The UDF model of Compose relies on holding the Player as the single source of truth with
    // ShuffleButtonState changing its state in sync with the relevant Player events. This means
    // that we should never find ourselves in a situation where a button's icon (here: determined by
    // ShuffleButtonState.shuffleOn) is out of sync with the Player's shuffle mode. It can cause
    // confusion for a human user whose intent to toggle the mode will not be fulfilled. The
    // following test tries to simulate this scenario by squeezing the 2 actions together (setter +
    // onClick) into a single Looper iteration. This is a practically unlikely scenario for a human
    // user's tapping to race with a programmatic change to the Player.

    // However, it is possible to achieve by changing the Player and straight away programmatically
    // invoking the tapping operation (via the ButtonState object) that internally sends an inverse
    // setting command to the Player in its new configuration (the onEvents message here is
    // irrelevant because we are operating on the live mutable Player object). The expectation then
    // is that the State object and Player finally synchronise, even if it means the UI interaction
    // would have been confusing.
    val player = TestPlayer()
    lateinit var state: ShuffleButtonState
    composeTestRule.setContent { state = rememberShuffleButtonState(player = player) }
    assertThat(state.shuffleOn).isFalse() // Correct UI state in sync with Player

    player.shuffleModeEnabled = true
    // pretend like State didn't catch the EVENT in observe() by omitting
    // ShadowLooper.idleMainLooper()
    assertThat(state.shuffleOn).isFalse() // Temporarily out-of-sync incorrect UI
    // A click operated on Player's true state at the time (= Shuffle On)
    // A potential human user would have the intention of toggling Off->On
    // But this is a programmatic user who had just set the mode and hence expects the reverse
    // On->Off
    state.onClick()
    ShadowLooper.idleMainLooper()

    assertThat(player.shuffleModeEnabled).isFalse()
    assertThat(state.shuffleOn).isFalse() // UI state synchronises with Player
  }
}
