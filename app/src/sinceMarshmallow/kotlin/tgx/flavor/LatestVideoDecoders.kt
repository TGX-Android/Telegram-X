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
import androidx.annotation.RequiresApi
import androidx.media3.common.Format
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import tgx.player.VideoDecoderCache

private const val SIZE_ABOVE_PERFORMANCE_POINTS = 16_384

internal fun VideoDecoderCache.prepareVideoSizeChecks(decoders: List<MediaCodecInfo>): Boolean {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    decidePerformancePoints(decoders)
  }
  return true
}

internal fun VideoDecoderCache.claimsVideoSize(
  decoder: MediaCodecInfo,
  width: Int,
  height: Int
): Boolean =
  decoder.isVideoSizeAndRateSupported(width, height, Format.NO_VALUE.toDouble())

@RequiresApi(Build.VERSION_CODES.Q)
private fun decidePerformancePoints(decoders: List<MediaCodecInfo>) {
  // ExoPlayer decides once per process whether to trust performance points, by queries of H.264
  // decoders, secure ones too, at the first size that the points of a decoder don't cover
  val decoder = decoders.firstOrNull {
    !it.capabilities?.videoCapabilities?.supportedPerformancePoints.isNullOrEmpty()
  }
  if (decoder != null) {
    decoder.isVideoSizeAndRateSupported(
      SIZE_ABOVE_PERFORMANCE_POINTS,
      SIZE_ABOVE_PERFORMANCE_POINTS,
      Format.NO_VALUE.toDouble()
    )
  }
}
