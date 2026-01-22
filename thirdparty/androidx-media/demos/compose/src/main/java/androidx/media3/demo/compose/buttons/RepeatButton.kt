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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.demo.compose.R
import androidx.media3.ui.compose.state.rememberRepeatButtonState

@Composable
internal fun RepeatButton(player: Player, modifier: Modifier = Modifier) {
  val state = rememberRepeatButtonState(player)
  val icon = repeatModeIcon(state.repeatModeState)
  val contentDescription = repeatModeContentDescription(state.repeatModeState)
  IconButton(onClick = state::onClick, modifier = modifier, enabled = state.isEnabled) {
    Icon(icon, contentDescription = contentDescription, modifier = modifier)
  }
}

private fun repeatModeIcon(repeatMode: @Player.RepeatMode Int): ImageVector {
  return when (repeatMode) {
    Player.REPEAT_MODE_OFF -> Icons.Default.Repeat
    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOneOn
    else -> Icons.Default.RepeatOn
  }
}

@Composable
private fun repeatModeContentDescription(repeatMode: @Player.RepeatMode Int): String {
  return when (repeatMode) {
    Player.REPEAT_MODE_OFF -> stringResource(R.string.repeat_button_repeat_off_description)
    Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_button_repeat_one_description)
    else -> stringResource(R.string.repeat_button_repeat_all_description)
  }
}
