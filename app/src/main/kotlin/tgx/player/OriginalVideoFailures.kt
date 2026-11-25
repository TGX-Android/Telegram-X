/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package tgx.player

import androidx.media3.common.PlaybackException

// Far more than the originals that fail on one phone while the app runs
private const val MAX_ORIGINALS = 64

private class RecentOriginals : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
    size > MAX_ORIGINALS
}

class OriginalVideoFailures {
  private val failedOnce = RecentOriginals()
  private val remembered = RecentOriginals()

  fun isRemembered(uniqueId: String): Boolean = remembered[uniqueId] != null

  fun addSoftwareDecoder(uniqueId: String) {
    add(uniqueId, canPass = true)
  }

  fun addDecoderError(uniqueId: String, errorCode: Int) {
    val isFormatError =
      errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
        errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
    add(uniqueId, canPass = !isFormatError)
  }

  private fun add(uniqueId: String, canPass: Boolean) {
    // A failure that can pass, e.g. of a hardware decoder busy with another player or a call, is
    // remembered only when it happens again with the same original
    if (uniqueId.isNotEmpty() && (!canPass || failedOnce.put(uniqueId, true) != null)) {
      failedOnce.remove(uniqueId)
      remembered[uniqueId] = true
    }
  }
}
