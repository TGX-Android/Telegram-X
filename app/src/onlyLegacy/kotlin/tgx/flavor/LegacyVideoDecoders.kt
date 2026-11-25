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
package tgx.flavor

import android.os.Build
import androidx.media3.common.Format
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import tgx.player.VideoDecoderCache

internal fun VideoDecoderCache.prepareVideoSizeChecks(decoders: List<MediaCodecInfo>): Boolean =
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ||
    queryMaxH264FrameSize(isPrefetch = true) != null

internal fun VideoDecoderCache.claimsVideoSize(
  decoder: MediaCodecInfo,
  width: Int,
  height: Int
): Boolean {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    return decoder.isVideoSizeAndRateSupportedV21(width, height, Format.NO_VALUE.toDouble())
  } else {
    // ExoPlayer's check of every codec below Android 5
    val maxFrameSize = queryMaxH264FrameSize() ?: return false
    return width * height <= maxFrameSize
  }
}

private fun VideoDecoderCache.queryMaxH264FrameSize(isPrefetch: Boolean = false): Int? =
  maxH264FrameSize.get(isPrefetch) {
    try {
      MediaCodecUtil.maxH264DecodableFrameSize()
    } catch (_: MediaCodecUtil.DecoderQueryException) {
      null
    }
  }
