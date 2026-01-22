/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.testapp.controller

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController

/** Helper class which handles shuffle mode changes and the UI surrounding this feature. */
class ShuffleModeHelper(activity: Activity, mediaController: MediaController) {
  private val container: ViewGroup = activity.findViewById(R.id.group_toggle_shuffle)
  private val icon: ImageView = container.findViewById(R.id.shuffle_mode_icon)
  private val shuffleButton: ToggleButton = container.findViewById(R.id.shuffle_mode_button)

  init {
    shuffleButton.setOnClickListener {
      mediaController.shuffleModeEnabled = shuffleButton.isChecked
    }
    val listener: Player.Listener =
      object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
          shuffleButton.isChecked = shuffleModeEnabled
          updateColor(shuffleModeEnabled)
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
          updateBackground(availableCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
      }
    mediaController.addListener(listener)

    val isSupported: Boolean =
      mediaController.availableCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE)
    updateBackground(isSupported)
    val isEnabled: Boolean = mediaController.shuffleModeEnabled
    updateColor(isEnabled)
    shuffleButton.isChecked = isEnabled
  }

  fun updateBackground(isSupported: Boolean) {
    if (isSupported) {
      container.background = null
      shuffleButton.visibility = View.VISIBLE
    } else {
      container.setBackgroundResource(R.drawable.bg_unsupported_action)
      shuffleButton.visibility = View.GONE
    }
  }

  fun updateColor(isEnabled: Boolean) {
    val tint: Int = if (isEnabled) R.color.colorPrimary else R.color.colorInactive
    DrawableCompat.setTint(icon.drawable, ContextCompat.getColor(container.context, tint))
  }
}
