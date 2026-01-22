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
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController

/** Helper class which handles prepare and play actions. */
class PreparePlayHelper(activity: Activity, private val mediaController: MediaController) :
  View.OnClickListener {
  private val inputType: Spinner = activity.findViewById(R.id.input_type)
  private val uriInputText: EditText = activity.findViewById(R.id.uri_id_query)
  private val prepareButton: Button = activity.findViewById(R.id.action_prepare)
  private val playButton: Button = activity.findViewById(R.id.action_play)

  init {
    prepareButton.setOnClickListener(this)
    playButton.setOnClickListener(this)
  }

  companion object {
    // Indices of the values in the "input_options" string array.
    // LINT.IfChange
    private const val INDEX_SEARCH = 0
    private const val INDEX_MEDIA_ID = 1
    private const val INDEX_URI = 2
    // LINT.ThenChange(../../../../../res/values/options.xml)
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  override fun onClick(v: View) {
    mediaController.apply {
      setMediaItem(buildMediaItem())
      playWhenReady = v.id == R.id.action_play
      prepare()
    }
  }

  private fun buildMediaItem(): MediaItem {
    val value: String = uriInputText.text.toString()
    val mediaItemBuilder = MediaItem.Builder()
    when (inputType.selectedItemPosition) {
      INDEX_MEDIA_ID -> mediaItemBuilder.setMediaId(value)
      INDEX_SEARCH ->
        mediaItemBuilder.setRequestMetadata(
          MediaItem.RequestMetadata.Builder().setSearchQuery(value).build()
        )
      INDEX_URI ->
        mediaItemBuilder.setRequestMetadata(
          MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(value)).build()
        )
    }
    return mediaItemBuilder.build()
  }
}
