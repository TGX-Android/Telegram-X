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

package androidx.media3.demo.compose.buttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.demo.compose.R
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState

@Composable
internal fun PlayPauseButton(player: Player, modifier: Modifier = Modifier) {
  val state = rememberPlayPauseButtonState(player)
  val icon = if (state.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause
  val contentDescription =
    if (state.showPlay) stringResource(R.string.playpause_button_play)
    else stringResource(R.string.playpause_button_pause)
  IconButton(onClick = state::onClick, modifier = modifier, enabled = state.isEnabled) {
    Icon(icon, contentDescription = contentDescription, modifier = modifier)
  }
}
