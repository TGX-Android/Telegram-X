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
import android.media.AudioManager
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/** Helper class to manage audio focus requests and the UI surrounding this feature. */
class AudioFocusHelper(activity: Activity) :
  View.OnClickListener,
  AudioManager.OnAudioFocusChangeListener,
  AdapterView.OnItemSelectedListener {
  private val audioManager: AudioManager =
    activity.getSystemService(AppCompatActivity.AUDIO_SERVICE) as AudioManager
  private val toggleButton: ToggleButton = activity.findViewById(R.id.audio_focus_button)
  private val focusTypeSpinner: Spinner = activity.findViewById(R.id.audio_focus_type)

  private val selectedFocusType: Int
    get() = FOCUS_TYPES[focusTypeSpinner.selectedItemPosition]

  companion object {
    // LINT.IfChange
    private val FOCUS_TYPES =
      intArrayOf(
        AudioManager.AUDIOFOCUS_GAIN,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
      )
    // LINT.ThenChange(../../../../../res/values/options.xml)
  }

  init {
    toggleButton.setOnClickListener(this)
    this.focusTypeSpinner.onItemSelectedListener = this
  }

  override fun onClick(v: View) =
    if (toggleButton.isChecked) {
      gainAudioFocus()
    } else {
      abandonAudioFocus()
    }

  override fun onAudioFocusChange(focusChange: Int) =
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN,
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> toggleButton.isChecked = true
      else -> toggleButton.isChecked = false
    }

  override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
    // If we're holding audio focus and the type should change, automatically
    // request the new type of focus.
    if (toggleButton.isChecked) {
      gainAudioFocus()
    }
  }

  override fun onNothingSelected(parent: AdapterView<*>?) {
    // Nothing to do.
  }

  private fun gainAudioFocus() {
    val audioFocusRequest: AudioFocusRequestCompat =
      AudioFocusRequestCompat.Builder(selectedFocusType).setOnAudioFocusChangeListener(this).build()
    AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
  }

  private fun abandonAudioFocus() {
    val audioFocusRequest: AudioFocusRequestCompat =
      AudioFocusRequestCompat.Builder(selectedFocusType).setOnAudioFocusChangeListener(this).build()
    AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
  }
}
