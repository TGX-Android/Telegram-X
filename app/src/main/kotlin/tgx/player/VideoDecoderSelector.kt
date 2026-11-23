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

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class VideoDecoderSelector(
  private val decoders: MediaCodecSelector = MediaCodecSelector.DEFAULT
) : MediaCodecSelector {
  private val failedDecoders = CopyOnWriteArraySet<String>()
  private val decodersByType = ConcurrentHashMap<String, List<MediaCodecInfo>>()

  override fun getDecoderInfos(
    mimeType: String,
    requiresSecureDecoder: Boolean,
    requiresTunnelingDecoder: Boolean
  ): List<MediaCodecInfo> {
    val all = decoders.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    // ExoPlayer also asks for tunneling decoders while it checks a format
    if (!requiresSecureDecoder && !requiresTunnelingDecoder) {
      decodersByType[mimeType] = all
    }
    val left = all.filter {
      it.name !in failedDecoders
    }
    // Without decoders ExoPlayer would play the sound alone
    return left.ifEmpty { all }
  }

  fun leaveOut(decoder: MediaCodecInfo): Boolean {
    // ExoPlayer starts them in this order, so those before it have failed to start
    val decoders = decodersByType[decoder.mimeType].orEmpty()
    val index = decoders.indexOfFirst {
      it.name == decoder.name
    }
    val hasNext = index >= 0 && decoders.drop(index + 1).any {
      it.name !in failedDecoders
    }
    return hasNext && failedDecoders.add(decoder.name)
  }

  fun clear() {
    failedDecoders.clear()
  }
}
