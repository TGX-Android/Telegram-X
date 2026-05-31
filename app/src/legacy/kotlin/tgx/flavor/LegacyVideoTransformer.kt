@file:JvmName("VideoTransformer")

package tgx.flavor

import android.annotation.TargetApi
import android.content.Context
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.source.FilePathDataSource
import com.otaliastudios.transcoder.source.TrimDataSource
import com.otaliastudios.transcoder.source.UriDataSource
import com.otaliastudios.transcoder.strategy.*
import kotlinx.coroutines.Runnable
import me.vkryl.core.lambda.RunnableData
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.U
import org.thunderdog.challegram.filegen.VideoGen
import org.thunderdog.challegram.filegen.VideoGenerationInfo
import org.thunderdog.challegram.unsorted.Settings
import java.io.File

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
fun setLegacyTranscoderLogLevel(level: Int) {
  val loggerLevel = when (level) {
    Log.LEVEL_WARNING -> Logger.LEVEL_WARNING
    Log.LEVEL_INFO, Log.LEVEL_DEBUG -> Logger.LEVEL_INFO
    Log.LEVEL_VERBOSE -> Logger.LEVEL_VERBOSE
    else -> Logger.LEVEL_ERROR
  }
  Logger.setLogLevel(loggerLevel)
}

private fun toLegacyDataSource(context: Context, sourcePath: String): DataSource {
  return if (sourcePath.startsWith("content://")) {
    UriDataSource(context, sourcePath.toUri())
  } else {
    FilePathDataSource(sourcePath).apply {
      initialize()
    }
  }
}

private fun getFrameRate(format: MediaFormat): Int {
  return if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
    format.getInteger(MediaFormat.KEY_FRAME_RATE)
  } else {
    -1
  }
}

fun getVideoFrameRate(context: Context, sourcePath: String): Int {
  val dataSource = toLegacyDataSource(context, sourcePath)
  val format = dataSource.getTrackFormat(TrackType.VIDEO)
  return if (format != null) getFrameRate(format) else -1
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
  onComplete: Runnable,
  onCancel: RunnableData<String>,
  onFailure: RunnableData<Throwable>,
  after: Runnable) {
  if (info.hasCrop()) {
    error("Crop unsupported.")
  }

  var dataSource = toLegacyDataSource(context, sourcePath)
  if (info.needTrim()) {
    val trimEnd = if (info.endTimeUs == -1L)
      0
    else {
      dataSource.durationUs - info.endTimeUs
    }
    dataSource = TrimDataSource(dataSource, info.startTimeUs, if (trimEnd < 1000) 0 else trimEnd)
  }

  val videoTrackStrategy: TrackStrategy = if (info.disableTranscoding()) {
    PassThroughTrackStrategy()
  } else {
    var videoLimit = info.videoLimit ?: Settings.VideoLimit()
    var outputBitrate = videoLimit.bitrate
    var outputFrameRate = videoLimit.getOutputFrameRate(-1)
    val maxTextureSize = U.getMaxTextureSize()
    if (maxTextureSize > 0 && videoLimit.size.majorSize > maxTextureSize) {
      val scale = maxTextureSize.toFloat() / videoLimit.size.majorSize.toFloat()
      var majorSize = (videoLimit.size.majorSize.toFloat() * scale).toInt()
      majorSize -= majorSize % 2
      var minorSize = (videoLimit.size.minorSize.toFloat() * scale).toInt()
      minorSize -= majorSize % 2
      videoLimit = videoLimit.changeSize(Settings.VideoSize(majorSize, minorSize))
    }
    if (outputBitrate == DefaultVideoStrategy.BITRATE_UNKNOWN) {
      val format = dataSource.getTrackFormat(TrackType.VIDEO)
      if (format != null) {
        val outputSize = videoLimit.getOutputSize(
          format.getInteger(MediaFormat.KEY_WIDTH),
        format.getInteger(MediaFormat.KEY_HEIGHT)
        )
        val inputFrameRate = getFrameRate(format)
        outputFrameRate = videoLimit.getOutputFrameRate(inputFrameRate)
        outputBitrate = videoLimit.getOutputBitrate(outputSize, outputFrameRate, videoLimit.bitrate)
      }
    }
    DefaultVideoStrategy
      .atMost(videoLimit.size.minorSize, videoLimit.size.majorSize)
      .frameRate(outputFrameRate)
      .bitRate(outputBitrate)
      .build()
  }

  val rotation = info.rotate
  val outFile = File(destinationPath)

  val audioTrackStrategy: TrackStrategy = if (info.needMute()) {
    RemoveTrackStrategy()
  } else if (info.disableTranscoding() || Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_AUDIO_COMPRESSION)) {
    PassThroughTrackStrategy()
  } else {
    DefaultAudioStrategy.Builder()
      .sampleRate(44100)
      .bitRate(62000)
      .channels(2)
      .build()
  }

  val listener = object : TranscoderListener {
    override fun onTranscodeProgress (progress: Double) {
      val expectedSize = if (outFile.exists())
        outFile.length()
      else
        0
      onProgress.onTranscodeProgress(progress, expectedSize)
    }

    override fun onTranscodeCompleted (successCode: Int) {
      when (successCode) {
        Transcoder.SUCCESS_TRANSCODED -> {
          onComplete.run()
        }
        Transcoder.SUCCESS_NOT_NEEDED -> {
          videoGen.sendOriginal(info, entry)
        }
      }
      U.run(after)
    }

    override fun onTranscodeCanceled () {
      onCancel.runWithData("Transcode canceled")
      U.run(after)
    }

    override fun onTranscodeFailed (exception: Throwable) {
      onFailure.runWithData(exception)
      U.run(after)
    }
  }

  val task = Transcoder
    .into(destinationPath)
    .addDataSource(dataSource)
    .setVideoTrackStrategy(videoTrackStrategy)
    .setAudioTrackStrategy(audioTrackStrategy)
    .setVideoRotation(rotation)
    .setListener(listener)
    .transcode()

  entry.setTask(task)
}