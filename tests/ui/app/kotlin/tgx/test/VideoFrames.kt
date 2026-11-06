package tgx.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import org.thunderdog.challegram.N
import java.nio.ByteBuffer
import java.util.zip.CRC32

private const val TAG = "VideoFrames"

// A clip that never comes to its end stops there too
private const val MAX_FRAMES = 2_000

/**
 * Decodes a video by the decoder of animations of the app, `N.createDecoder` of `GifActor` and
 * `ImageReader`, which runs FFmpeg of the app, so that builds can be compared on the same clips,
 * e.g. before and after an update of FFmpeg: `--es action decode --es path <file>` logs "decoded
 * <file>: <width>x<height>, frames <count>, broken <bool>, crc <CRC32 of the pixels of all
 * frames>", or "failed <file>: <why>", with tag `VideoFrames`. The file is one that the app can
 * read, e.g. in /sdcard/Android/media/<application id>/
 */
class VideoFramesReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val path = intent.getStringExtra("path").orEmpty()
    if (intent.getStringExtra("action") != "decode" || path.isEmpty()) {
      Log.i(TAG, "failed: no action or path")
      return
    }
    val result = goAsync()
    Thread {
      try {
        Log.i(TAG, decode(path))
      } finally {
        result.finish()
      }
    }.start()
  }

  private fun decode(path: String): String {
    val metadata = LongArray(N.DECODER_METADATA_ARRAY_SIZE)
    val decoder = N.createDecoder(path, metadata, 0.0)
    if (decoder == 0L) {
      return "failed $path: no decoder"
    }
    try {
      val width = metadata[0].toInt()
      val height = metadata[1].toInt()
      if (width <= 0 || height <= 0) {
        return "failed $path: size ${width}x$height"
      }
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val pixels = ByteBuffer.allocate(bitmap.byteCount)
      val crc = CRC32()
      var frames = 0
      while (frames < MAX_FRAMES) {
        // 1 is a frame, 2 the last one before the clip starts again, 0 an error
        val frame = N.getVideoFrame(decoder, bitmap, metadata)
        if (frame == 0) {
          break
        }
        pixels.clear()
        bitmap.copyPixelsToBuffer(pixels)
        crc.update(pixels.array(), 0, pixels.position())
        frames++
        if (frame == 2) {
          break
        }
      }
      return "decoded $path: ${width}x$height, frames $frames, " +
        "broken ${N.isVideoBroken(decoder)}, crc ${crc.value.toString(16)}"
    } finally {
      N.destroyDecoder(decoder)
    }
  }
}
