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
@file:JvmName("VideoDecoders")

package tgx.player

import android.os.SystemClock
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.vp9.VpxLibrary
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import tgx.flavor.claimsVideoSize
import tgx.flavor.prepareVideoSizeChecks
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val MIME_TYPES = arrayOf(
  MimeTypes.VIDEO_H264,
  MimeTypes.VIDEO_H265,
  MimeTypes.VIDEO_AV1,
  MimeTypes.VIDEO_VP9,
  MimeTypes.VIDEO_VP8
)

private const val MAX_FAILURES = 3
private const val RETRY_DELAY_MS = 30_000L
private const val IDLE_THREAD_MS = 1_000L

fun interface VideoDecoderSupport {
  fun claimsSize(mimeType: String, width: Int, height: Int, inHardware: Boolean): Boolean
}

private val videoDecoders = VideoDecoderCache(
  queryDecoders = {
    try {
      MediaCodecUtil.getDecoderInfos(it, false, false)
    } catch (_: MediaCodecUtil.DecoderQueryException) {
      null
    }
  }
)

val videoDecoderSupport: VideoDecoderSupport
  get() = videoDecoders

fun areVideoDecodersQueried(): Boolean = videoDecoders.isQueried

fun prefetchVideoDecoders() {
  videoDecoders.prefetch()
}

fun whenVideoDecodersQueried(callback: Runnable) {
  videoDecoders.whenQueried(callback)
}

fun isSoftwareVideoDecoder(decoderName: String): Boolean = videoDecoders.isSoftware(decoderName)

class VideoDecoderCache(
  private val queryDecoders: (mimeType: String) -> List<MediaCodecInfo>?,
  private val uptimeMs: () -> Long = SystemClock::uptimeMillis
) : VideoDecoderSupport {
  private val decodersByType = MIME_TYPES.associateWith {
    CachedQuery<List<MediaCodecInfo>>(emptyList(), uptimeMs)
  }

  // ExoPlayer's limit of sizes of H.264 below Android 5, which only legacy checks
  internal val maxH264FrameSize = CachedQuery(0, uptimeMs)

  private val isPrefetching = AtomicBoolean()

  private val executor = ThreadPoolExecutor(
    1,
    1,
    IDLE_THREAD_MS,
    TimeUnit.MILLISECONDS,
    LinkedBlockingQueue()
  ) {
    Thread(it, "VideoDecoders")
  }.apply {
    allowCoreThreadTimeOut(true)
  }

  @Volatile
  private var nextPrefetchMs = 0L

  @Volatile
  var isQueried = false
    private set

  fun prefetch() {
    // The first query of a process reads all codecs of the phone, too slow for the main thread
    if (!isQueried && uptimeMs() >= nextPrefetchMs && isPrefetching.compareAndSet(false, true)) {
      executor.execute {
        isQueried = query()
        if (!isQueried) {
          nextPrefetchMs = uptimeMs() + RETRY_DELAY_MS
        }
        isPrefetching.set(false)
      }
    }
  }

  fun whenQueried(callback: Runnable) {
    // After the queries of a prefetch that runs or waits, on the same thread
    executor.execute {
      if (!isQueried && query()) {
        isQueried = true
      }
      callback.run()
    }
  }

  internal fun query(): Boolean {
    // Whether all that checks of sizes need is known, so that the main thread makes no queries
    val decoders = MIME_TYPES.map {
      decodersOf(it, isPrefetch = true)
    }
    if (decoders.contains(null)) {
      return false
    }
    return prepareVideoSizeChecks(decoders.filterNotNull().flatten())
  }

  private fun decodersOf(mimeType: String, isPrefetch: Boolean = false): List<MediaCodecInfo>? {
    // Other types aren't queried at all, so that a check on the main thread never queries
    val cachedDecoders = decodersByType[mimeType] ?: return emptyList()
    return cachedDecoders.get(isPrefetch) {
      queryDecoders(mimeType)
    }
  }

  fun isSoftware(decoderName: String): Boolean {
    // Only decoders queried before, so that the main thread never queries
    return decodersByType.values.any { cachedDecoders ->
      cachedDecoders.result.orEmpty().any {
        it.name == decoderName && !it.hardwareAccelerated
      }
    }
  }

  override fun claimsSize(
    mimeType: String,
    width: Int,
    height: Int,
    inHardware: Boolean
  ): Boolean {
    if (!inHardware && mimeType == MimeTypes.VIDEO_VP9 && VpxLibrary.isAvailable()) {
      return true
    }
    return decodersOf(mimeType).orEmpty().any {
      (it.hardwareAccelerated || !inHardware) && claimsSize(it, width, height)
    }
  }

  private fun claimsSize(decoder: MediaCodecInfo, width: Int, height: Int): Boolean =
    width <= 0 || height <= 0 || claimsVideoSize(decoder, width, height)
}

internal class CachedQuery<T : Any>(private val noResult: T, private val uptimeMs: () -> Long) {
  private var failureCount = 0
  private var failureTimeMs = 0L

  @Volatile
  var result: T? = null
    private set

  fun get(isPrefetch: Boolean, load: () -> T?): T? {
    // A failed query may pass later, as ExoPlayer expects; only the prefetch tries it again, so
    // that building a playlist never waits for a query that keeps failing
    result?.let {
      return it
    }
    synchronized(this) {
      result?.let {
        return it
      }
      if (failureCount > 0 && (!isPrefetch || uptimeMs() - failureTimeMs < RETRY_DELAY_MS)) {
        return null
      }
      val loaded = load()
      if (loaded == null && ++failureCount < MAX_FAILURES) {
        failureTimeMs = uptimeMs()
        return null
      }
      return (loaded ?: noResult).also {
        result = it
      }
    }
  }
}
