@file:JvmName("VideoTransformer")

package tgx.flavor

import android.annotation.TargetApi
import android.content.Context
import android.media.MediaFormat
import android.os.Build
import androidx.core.net.toUri
import kotlinx.coroutines.Runnable
import me.vkryl.core.lambda.RunnableData
import org.thunderdog.challegram.filegen.VideoGen
import org.thunderdog.challegram.filegen.VideoGenerationInfo

fun setLegacyTranscoderLogLevel(ignored: Int) {
  // Intentionally do nothing
}

private fun MediaFormat.getInt(key: String, defaultValue: Int = 0): Int {
  if (!containsKey(key)) {
    return defaultValue
  }
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    return getNumber(key)?.toInt() ?: defaultValue
  }
  try {
    return getFloat(key).toInt()
  } catch (_: Exception) { }
  try {
    return getLong(key).toInt()
  } catch (_: Exception) { }
  try {
    return getInteger(key)
  } catch (_: Exception) { }
  return defaultValue
}

fun getVideoFrameRate(context: Context, sourcePath: String): Int {
  val extractor = MediaExtractor(context)
  try {
    if (sourcePath.startsWith("content://")) {
      extractor.setDataSource(sourcePath.toUri(), 0)
    } else {
      extractor.setDataSource(sourcePath)
    }
    for (i in 0 until extractor.trackCount) {
      val format = extractor.getTrackFormat(i)
      val frameRate = format.getInt(MediaFormat.KEY_FRAME_RATE)
      if (frameRate > 0) {
        return frameRate
      }
    }
  } finally {
    extractor.release()
  }
  return -1
}

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
fun legacyConvertVideoComplex(
  videoGen: VideoGen,
  context: Context,
  sourcePath: String,
  destinationPath: String,
  info: VideoGenerationInfo,
  entry: VideoGen.Entry,
  onProgress: VideoGen.ProgressCallback,
  onComplete: kotlinx.coroutines.Runnable,
  onCancel: RunnableData<String>,
  onFailure: RunnableData<Throwable>,
  after: Runnable) {
  error("Unsupported")
}