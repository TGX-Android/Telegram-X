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
package tgx.td.data

import android.net.Uri
import android.os.Build
import me.vkryl.core.parseLong
import org.drinkless.tdlib.TdApi.AlternativeVideo
import org.drinkless.tdlib.TdApi.Video

data class HlsPath(
  @JvmField val width: Int,
  @JvmField val height: Int,
  @JvmField val codec: String,
  @JvmField val hlsFileId: Int,
  @JvmField val videoFileId: Int
) {
  constructor(video: AlternativeVideo) : this(
    video.width,
    video.height,
    video.codec,
    video.hlsFile.id,
    video.video.id
  )

  companion object {
    @JvmStatic fun fromUri(uri: Uri): HlsPath {
      val data = uri.schemeSpecificPart.replace(Regex("^//(.+)\\.m3u8$"), "$1")
        .split("_")
      val dimensions = data[0].split("x")
      val width: Int = dimensions[0].toInt()
      val height: Int = dimensions[1].toInt()
      val codec = data[1]
      val hlsFileId: Int = data[2].toInt()
      val videoFileId: Int = data[3].toInt()
      return HlsPath(width, height, codec, hlsFileId, videoFileId)
    }
  }

  override fun toString(): String {
    return "${HlsVideo.SCHEME}://${width}x${height}_${codec}_${hlsFileId}_${videoFileId}.m3u8"
  }
}

data class HlsVideo(
  @JvmField val video: Video,
  @JvmField val alternativeVideos: Array<AlternativeVideo>
) {
  @JvmField val id: Int = video.video.id

  fun findVideoByStreamId (streamId: Long): AlternativeVideo =
    this.alternativeVideos.firstOrNull { alternativeVideo ->
      alternativeVideo.id == streamId
    }!!

  fun hasSupportedCodecs(): Boolean =
    alternativeVideos.firstOrNull {
      it.isSupported(false)
    } != null

  fun findVideoFileIdByStreamId (streamId: Long): Int =
    findVideoByStreamId(streamId).video.id

  companion object {
    const val SCHEME = "tg_hls"
    const val MTPROTO_SCHEME = "mtproto"

    @JvmStatic fun extractStreamId(uri: Uri): Long =
      parseLong(uri.schemeSpecificPart)
  }

  fun AlternativeVideo.isSupported(failOnUnknownCodec: Boolean): Boolean =
    when (this.codec) {
      // https://developer.android.com/media/platform/supported-formats
      "av1", "av01" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q // TODO: bundle av1?
      "h264" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
      else -> if (failOnUnknownCodec) {
        error("Unexpected codec: $codec")
      } else {
        true
      }
    }

  fun AlternativeVideo.appendTo(b: StringBuilder) {
    val bandwidth: Int = if (this@HlsVideo.video.duration != 0) {
      (video.size.toDouble() / this@HlsVideo.video.duration.toDouble()).toInt() * 8
    } else {
      1000000
    }
    b.append("#EXT-X-STREAM-INF:BANDWIDTH=${bandwidth},RESOLUTION=${width}x${height},CODECS=\"${codec}\"\n")
    b.append("${HlsPath(this)}\n")
  }

  fun multivariantPlaylistData(failOnUnknownCodec: Boolean): String {
    val playlist = StringBuilder()
    playlist.append("#EXTM3U\n")
    var videoCount = 0
    for (alternativeVideo in alternativeVideos) {
      if (alternativeVideo.isSupported(failOnUnknownCodec)) {
        alternativeVideo.appendTo(playlist)
        videoCount++
      }
    }
    if (videoCount == 0 && alternativeVideos.isNotEmpty()) {
      error("None of codecs supported: ${alternativeVideos.joinToString { it.codec }}")
    }
    playlist.append("#EXT-X-ENDLIST\n")
    return playlist.toString()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HlsVideo

    if (video != other.video) return false
    if (!alternativeVideos.contentEquals(other.alternativeVideos)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = video.hashCode()
    result = 31 * result + alternativeVideos.contentHashCode()
    return result
  }
}