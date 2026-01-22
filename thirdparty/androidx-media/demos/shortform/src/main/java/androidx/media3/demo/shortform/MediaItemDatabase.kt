/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.demo.shortform

import androidx.media3.common.MediaItem

class MediaItemDatabase {

  private val mediaUris =
    mutableListOf(
      "https://storage.googleapis.com/exoplayer-test-media-0/shortform_1.mp4",
      "https://storage.googleapis.com/exoplayer-test-media-0/shortform_2.mp4",
      "https://storage.googleapis.com/exoplayer-test-media-0/shortform_3.mp4",
      "https://storage.googleapis.com/exoplayer-test-media-0/shortform_4.mp4",
      "https://storage.googleapis.com/exoplayer-test-media-0/shortform_6.mp4",
    )

  fun get(index: Int): MediaItem {
    val uri = mediaUris.get(index.mod(mediaUris.size))
    return MediaItem.Builder().setUri(uri).setMediaId(index.toString()).build()
  }
}
