package org.thunderdog.challegram.filegen;

import android.media.MediaFormat;
import android.net.Uri;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.otaliastudios.transcoder.Transcoder;
import com.otaliastudios.transcoder.TranscoderListener;
import com.otaliastudios.transcoder.common.TrackType;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.source.FilePathDataSource;
import com.otaliastudios.transcoder.source.TrimDataSource;
import com.otaliastudios.transcoder.source.UriDataSource;
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy;
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy;
import com.otaliastudios.transcoder.strategy.PassThroughTrackStrategy;
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy;
import com.otaliastudios.transcoder.strategy.TrackStrategy;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.unit.ByteUnit;
import okio.Okio;
import okio.Source;

/**
 * Date: 14/12/2016
 * Author: default
 */

public class VideoGen {
  public static final int MESSAGE_START_CONVERSION = 0;

  public static class Queue extends BaseThread {
    private final VideoGen context;

    public Queue (VideoGen context) {
      super("VideoGenQueue");
      this.context = context;
    }

    @Override
    protected void process (Message msg) {
      switch (msg.what) {
        case MESSAGE_START_CONVERSION: {
          context.startConversion((VideoGenerationInfo) msg.obj);
          break;
        }
      }
    }
  }

  public static class Entry {
    private final AtomicBoolean transcodeFinished = new AtomicBoolean(false);
    private final AtomicBoolean sendOriginal = new AtomicBoolean(false);
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    private double transcodeProgress;
    private long readyBytes;
    private long reportedBytesCount, reportedExpectedBytesCount;

    private final VideoGen context;
    private final long generationId;
    private Future<Void> task;
    // private String liTrRequestId;

    private Entry (VideoGen context, long generationId) {
      this.context = context;
      this.generationId = generationId;
    }

    public void onTranscodeProgress (double progress, long expectedSize) {
      if (this.transcodeProgress != progress) {
        this.transcodeProgress = progress;
        reportBytes(expectedSize, this.reportedBytesCount);
      }
    }

    public void reportBytes (long expectedSize, long uploadBytesCount) {
      if (this.reportedExpectedBytesCount != expectedSize || (uploadBytesCount < this.reportedBytesCount || uploadBytesCount - this.reportedBytesCount >= ByteUnit.KIB.toBytes(5))) {
        this.reportedExpectedBytesCount = expectedSize;
        this.reportedBytesCount = uploadBytesCount;
        context.tdlib.client().send(new TdApi.SetFileGenerationProgress(generationId, (int) expectedSize, (int) uploadBytesCount), context.tdlib.silentHandler());
      }
    }

    public void resetProgress (long expectedSize) {
      this.readyBytes = 0;
      reportBytes(expectedSize, 0);
    }

    public long getReadyBytesCount () {
      return readyBytes;
    }

    public long getEstimatedTotalBytesCount () {
      return transcodeProgress == 0 ? -1 : (long) ((double) readyBytes / transcodeProgress);
    }

    public double getProgress () {
      return transcodeProgress;
    }

    public void cancel () {
      if (task != null) {
        task.cancel(true);
      }
      /*if (!Strings.isEmpty(liTrRequestId) && context.mediaTransformer != null) {
        context.mediaTransformer.cancel(liTrRequestId);
      }*/
    }
  }

  private final Tdlib tdlib;
  private final Queue queue;
  private final HashMap<String, Entry> entries;

  public VideoGen (Tdlib tdlib) {
    this.tdlib = tdlib;
    this.entries = new HashMap<>();
    this.queue = new Queue(this);
  }

  public Queue getQueue () {
    return queue;
  }

  // Internal

  private static long getBytesCount (String path, boolean needStream) {
    try {
      RandomAccessFile file = new RandomAccessFile(path, "r");
      return file.length();
    } catch (IOException ignored) {
      if (needStream) {
        try (InputStream stream = U.openInputStream(path)) {
          return stream.available();
        } catch (Throwable ignored2) { }
      }
    }
    return 0;
  }

  private interface ProgressCallback {
    void onTranscodeProgress (double progress, long expectedSize);
    void onReadyToUpload (long bytesCount, long expectedSize);
  }

  @WorkerThread
  private void startConversion (final VideoGenerationInfo info) {
    final Entry entry = new Entry(this, info.generationId);
    entries.put(info.getDestinationPath(), entry);
    convertVideo(info, entry);
  }

  @WorkerThread
  private void convertVideo (final VideoGenerationInfo info, final Entry entry) {
    final String sourcePath = info.getOriginalPath();
    final String destinationPath = info.getDestinationPath();

    boolean canUseSimplePath = info.canTakeSimplePath();

    long sourceSize = getBytesCount(sourcePath, true);
    ProgressCallback onProgress = new ProgressCallback() {
      @Override
      public void onTranscodeProgress (double progress, long expectedSize) {
        synchronized (entry) {
          if (entry.transcodeFinished.get() || entry.sendOriginal.get() || progress <= 0) {
            return;
          }
          if (sourceSize != 0 && expectedSize > sourceSize && canUseSimplePath) {
            if (!entry.sendOriginal.getAndSet(true)) {
              entry.cancel();
            }
          } else {
            entry.onTranscodeProgress(progress, expectedSize);
          }
        }
      }

      @Override
      public void onReadyToUpload (long bytesCount, long expectedSize) {
        synchronized (entry) {
          if (entry.transcodeFinished.get() || entry.sendOriginal.get() || bytesCount <= 0) {
            return;
          }
          if (sourceSize != 0 && bytesCount > sourceSize && canUseSimplePath) {
            if (!entry.sendOriginal.getAndSet(true)) {
              entry.cancel();
            }
          } else {
            entry.reportBytes(0, bytesCount);
          }
        }
      }
    };
    Runnable onComplete = () -> {
      synchronized (entry) {
        if (!entry.transcodeFinished.getAndSet(true)) {
          tdlib.filegen().finishGeneration(info);
          entries.remove(destinationPath);
        }
      }
    };
    Runnable onCancel = () -> {
      synchronized (entry) {
        if (!entry.transcodeFinished.getAndSet(true)) {
          if (!entry.canceled.get() && entry.sendOriginal.get()) {
            sendOriginal(info, entry);
          } else {
            tdlib.filegen().failGeneration(info, -1, "Video conversion has been cancelled");
            entries.remove(destinationPath);
          }
        }
      }
    };
    info.setOnCancel(() -> {
      synchronized (entry) {
        entry.canceled.set(true);
        entry.transcodeFinished.set(true);
        entries.remove(destinationPath);
      }
      try {
        if (entry.task != null && !entry.task.isDone()) {
          Log.i("Cancelling video generation");
          entry.task.cancel(true);
        }
      } catch (Throwable t) {
        Log.i(t);
      }
    });
    RunnableData<Throwable> onFailure = t -> {
      synchronized (entry) {
        if (!entry.transcodeFinished.getAndSet(true)) {
          if (t != null) {
            Log.e("Failed to generate video: %s", t, sourcePath);
          } else {
            Log.i("No need to transcode video: %s", sourcePath);
          }
          if (!entry.canceled.get() && canUseSimplePath) {
            sendOriginal(info, entry);
          } else {
            tdlib.filegen().failGeneration(info, -1, Lang.getString(R.string.SendVideoError));
            entries.remove(destinationPath);
          }
        }
      }
    };

    if (info.disableTranscoding() && canUseSimplePath) {
      sendOriginal(info, entry);
      return;
    }

    try {
      convertVideoComplex(sourcePath, destinationPath, info, entry, onProgress, onComplete, onCancel, onFailure);
    } catch (Throwable t) {
      Log.e(t);
      onFailure.runWithData(t);
    }
  }

  private void convertVideoComplex (String sourcePath, String destinationPath, VideoGenerationInfo info, Entry entry, ProgressCallback onProgress, Runnable onComplete, Runnable onCancel, RunnableData<Throwable> onFailure) {
    DataSource dataSource;
    if (sourcePath.startsWith("content://")) {
      dataSource = new UriDataSource(UI.getAppContext(), Uri.parse(sourcePath));
    } else {
      dataSource = new FilePathDataSource(sourcePath);
      dataSource.initialize();
    }

    if (info.needTrim()) {
      long trimEnd = dataSource.getDurationUs() - info.getEndTimeUs();
      dataSource = new TrimDataSource(dataSource, info.getStartTimeUs(), trimEnd < 1000 ? 0 : trimEnd);
    }

    TrackStrategy videoTrackStrategy;
    if (info.disableTranscoding()) {
      videoTrackStrategy = new PassThroughTrackStrategy();
    } else {
      Settings.VideoLimit videoLimit = info.getVideoLimit();
      if (videoLimit == null)
        videoLimit = new Settings.VideoLimit();
      long outputBitrate = videoLimit.bitrate;
      int outputFrameRate = videoLimit.fps != 0 ? videoLimit.fps : Settings.DEFAULT_FRAME_RATE;
      int maxTextureSize = U.getMaxTextureSize();
      if (maxTextureSize > 0 && videoLimit.size.majorSize > maxTextureSize) {
        float scale = (float) maxTextureSize / (float) videoLimit.size.majorSize;
        int majorSize = (int) ((float) videoLimit.size.majorSize * scale);
        majorSize -= majorSize % 2;
        int minorSize = (int) ((float) videoLimit.size.minorSize * scale);
        minorSize -= majorSize % 2;
        videoLimit = videoLimit.changeSize(new Settings.VideoSize(majorSize, minorSize));
      }
      if (outputBitrate == DefaultVideoStrategy.BITRATE_UNKNOWN) {
        MediaFormat format = dataSource.getTrackFormat(TrackType.VIDEO);
        if (format != null) {
          Settings.VideoSize outputSize = videoLimit.getOutputSize(
            format.getInteger(MediaFormat.KEY_WIDTH),
            format.getInteger(MediaFormat.KEY_HEIGHT)
          );
          outputFrameRate = videoLimit.getOutputFrameRate(format.getInteger(MediaFormat.KEY_FRAME_RATE));
          outputBitrate = videoLimit.getOutputBitrate(outputSize, outputFrameRate, videoLimit.bitrate);
        }
      }
      videoTrackStrategy = DefaultVideoStrategy
        .atMost(videoLimit.size.minorSize, videoLimit.size.majorSize)
        .frameRate(outputFrameRate)
        .bitRate(outputBitrate)
        .build();
    }

    int rotation = info.getRotate();

    File outFile = new File(destinationPath);

    entry.task = Transcoder
      .into(destinationPath)
      .addDataSource(dataSource)
      .setVideoTrackStrategy(videoTrackStrategy)
      .setAudioTrackStrategy(
        info.needMute() ? new RemoveTrackStrategy() :
        info.disableTranscoding() || Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_AUDIO_COMPRESSION) ? new PassThroughTrackStrategy() :
          new DefaultAudioStrategy.Builder()
            .sampleRate(44100)
            .bitRate(62000)
            .channels(2)
            .build()
      )
      .setVideoRotation(rotation)
      .setListener(new TranscoderListener() {
        @Override
        public void onTranscodeProgress (double progress) {
          onProgress.onTranscodeProgress(progress, outFile.exists() ? outFile.length() : 0);
        }

        @Override
        public void onTranscodeCompleted (int successCode) {
          switch (successCode) {
            case Transcoder.SUCCESS_TRANSCODED:
              onComplete.run();
              break;
            case Transcoder.SUCCESS_NOT_NEEDED:
              sendOriginal(info, entry);
              break;
          }
        }

        @Override
        public void onTranscodeCanceled () {
          onCancel.run();
        }

        @Override
        public void onTranscodeFailed (@NonNull Throwable exception) {
          onFailure.runWithData(exception);
        }
      })
      .transcode();
  }

  private void sendOriginal (VideoGenerationInfo info, Entry entry) {
    final long generationId = info.getGenerationId();
    final String sourcePath = info.getOriginalPath();
    final String destinationPath = info.getDestinationPath();

    entry.transcodeFinished.set(false);

    if (info.needTrim() || info.needMute() || info.getRotate() != 0) {
      entry.resetProgress(0);
      boolean success = false;
      try {
        success = convertVideoSimple(sourcePath, destinationPath, info, entry.canceled, null);
      } catch (Throwable t) {
        Log.w(Log.TAG_VIDEO, "Cannot trim video", t);
      }
      synchronized (entry) {
        if (!entry.transcodeFinished.getAndSet(true)) {
          if (success) {
            tdlib.filegen().finishGeneration(info);
          } else {
            tdlib.filegen().failGeneration(info, -1, Lang.getString(R.string.SendVideoError));
          }
          entries.remove(destinationPath);
        }
      }
      return;
    }

    int bytesCount = (int) getBytesCount(sourcePath, true);
    entry.resetProgress(bytesCount);

    tdlib.filegen().getContentExecutor().execute(() -> {
      boolean success = false;
      try {
        File file = new File(sourcePath);
        try (Source in = Okio.source(file)) {
          success = tdlib.filegen().copy(generationId, sourcePath, in, destinationPath, (int) file.length(), entry.canceled);
        }
      } catch (Throwable t) {
        Log.e("Cannot copy file, fromPath: %s", t, sourcePath);
      }
      synchronized (entry) {
        if (!entry.canceled.get()) {
          if (success) {
            tdlib.filegen().finishGeneration(info);
          } else {
            tdlib.filegen().failGeneration(info, -1, "Failed to copy file, make sure there's enough disk space");
          }
        }
        entries.remove(destinationPath);
      }
    });
  }

  public Entry getProgressEntry (String path) {
    return entries.get(path);
  }

  private boolean convertVideoSimple (String sourcePath, String destinationPath, VideoGenerationInfo info, @Nullable AtomicBoolean isCancelled, RunnableLong onProgress) throws Throwable {
    VideoData videoData = new VideoData(sourcePath);
    return info.needTrim() ?
        videoData.editMovie(destinationPath, info.needMute(), info.getRotate(), (double) info.getStartTimeUs() / 1_000_000.0, (double) info.getEndTimeUs() / 1_000_000.0, onProgress, isCancelled) :
        videoData.editMovie(destinationPath, info.needMute(), info.getRotate(), onProgress, isCancelled);
  }
}
