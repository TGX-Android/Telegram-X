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
 *
 * File created on 14/12/2016
 */
package org.thunderdog.challegram.filegen;

import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.FrameDropEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.crop.CropEffectFactory;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.unit.ByteUnit;
import okio.Okio;
import okio.Source;

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
    private Transformer transformer;

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
        context.tdlib.client().send(new TdApi.SetFileGenerationProgress(generationId, expectedSize, uploadBytesCount), context.tdlib.silentHandler());
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
      if (task != null && !task.isDone()) {
        task.cancel(true);
      }
      if (transformer != null) {
        transformer.cancel();
      }
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

    boolean sendOriginalInCaseFileSizeGrows = !Config.MODERN_VIDEO_TRANSCODING_ENABLED && info.canTakeSimplePath();

    long sourceSize = getBytesCount(sourcePath, true);
    ProgressCallback onProgress = new ProgressCallback() {
      @Override
      public void onTranscodeProgress (double progress, long expectedSize) {
        synchronized (entry) {
          if (entry.transcodeFinished.get() || entry.sendOriginal.get() || progress <= 0) {
            return;
          }
          if (sourceSize != 0 && expectedSize > sourceSize && sendOriginalInCaseFileSizeGrows) {
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
          if (sourceSize != 0 && bytesCount > sourceSize && sendOriginalInCaseFileSizeGrows) {
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
    RunnableData<String> onCancel = message -> {
      synchronized (entry) {
        if (!entry.transcodeFinished.getAndSet(true)) {
          if (!entry.canceled.get() && entry.sendOriginal.get()) {
            sendOriginal(info, entry);
          } else {
            StringBuilder b = new StringBuilder("Video conversion has been cancelled");
            if (entry.canceled.get()) {
              b.append(" by TDLib");
            }
            if (!StringUtils.isEmpty(message)) {
              b.append(": ").append(message);
            }
            String error = b.toString();
            tdlib.filegen().failGeneration(info, -1, error);
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
        Log.i("Cancelling video generation");
        entry.cancel();
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
          if (!entry.canceled.get() && sendOriginalInCaseFileSizeGrows) {
            sendOriginal(info, entry);
          } else {
            tdlib.filegen().failGeneration(info, -1, Lang.getString(R.string.SendVideoError));
            entries.remove(destinationPath);
          }
        }
      }
    };

    if (!Config.MODERN_VIDEO_TRANSCODING_ENABLED && info.disableTranscoding()) {
      // Send original file only in case Transformer isn't used.
      sendOriginal(info, entry);
      return;
    }

    try {
      if (Config.MODERN_VIDEO_TRANSCODING_ENABLED) {
        convertVideoComplexV2(sourcePath, destinationPath, info, entry, onProgress, onComplete, onCancel, onFailure);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        convertVideoComplex(sourcePath, destinationPath, info, entry, onProgress, onComplete, onCancel, onFailure);
      } else {
        onFailure.runWithData(new RuntimeException());
      }
    } catch (Throwable t) {
      Log.e(t);
      onFailure.runWithData(t);
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void convertVideoComplexV2 (String sourcePath, String destinationPath, VideoGenerationInfo info, Entry entry, ProgressCallback onProgress, Runnable onComplete, RunnableData<String> onCancel, RunnableData<Throwable> onFailure) throws FileNotFoundException {
    MediaMetadataRetriever retriever = U.openRetriever(sourcePath);
    if (retriever == null)
      throw new NullPointerException();

    int inputVideoWidth = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
    int inputVideoHeight = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
    int inputVideoRotation;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      inputVideoRotation = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
    } else {
      inputVideoRotation = 0;
    }
    long inputVideoBitrate = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
    U.closeRetriever(retriever);

    Settings.VideoLimit videoLimit = info.getVideoLimit();
    if (videoLimit == null)
      videoLimit = new Settings.VideoLimit();

    int inputVideoFrameRate = getFrameRate(sourcePath);
    int outputVideoFrameRate = videoLimit.getOutputFrameRate(inputVideoFrameRate);

    int outputVideoSquare;
    int outputHeightLimit;
    if (!info.disableTranscoding() && !videoLimit.size.isUnlimited() && (inputVideoWidth * inputVideoHeight) > (videoLimit.size.majorSize * videoLimit.size.minorSize)) {
      if (U.isRotated(inputVideoRotation)) {
        outputHeightLimit = inputVideoWidth > inputVideoHeight ? videoLimit.size.majorSize : videoLimit.size.minorSize;
      } else {
        outputHeightLimit = inputVideoHeight > inputVideoWidth ? videoLimit.size.majorSize : videoLimit.size.minorSize;
      }
      outputVideoSquare = videoLimit.size.majorSize * videoLimit.size.minorSize;
    } else {
      outputHeightLimit = 0;
      outputVideoSquare = inputVideoWidth * inputVideoHeight;
    }
    long outputVideoBitrate = Math.min(
      (videoLimit.bitrate != DefaultVideoStrategy.BITRATE_UNKNOWN ? videoLimit.bitrate :
      (int) Math.round(outputVideoSquare * outputVideoFrameRate * Settings.VideoLimit.BITRATE_SCALE)),
      inputVideoBitrate
    );

    MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
      .setUri(sourcePath);
    if (info.needTrim()) {
      mediaItemBuilder.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(TimeUnit.MICROSECONDS.toMillis(info.getStartTimeUs()))
        .setEndPositionMs(info.getEndTimeUs() == -1 ? C.TIME_END_OF_SOURCE : TimeUnit.MICROSECONDS.toMillis(info.getEndTimeUs()))
        .build()
      );
    }
    MediaItem mediaItem = mediaItemBuilder.build();

    EditedMediaItem.Builder editedMediaItemBuilder = new EditedMediaItem.Builder(mediaItem)
      .setRemoveAudio(info.needMute());

    List<Effect> videoEffects = new ArrayList<>();
    videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(outputVideoFrameRate));
    if (outputHeightLimit > 0) {
      videoEffects.add(Presentation.createForHeight(outputHeightLimit));
    }
    CropState cropState = info.getCrop();
    if (CropEffectFactory.needScaleAndRotateEffect(cropState, info.getRotate())) {
      videoEffects.add(CropEffectFactory.createScaleAndRotateEffect(cropState, info.getRotate()));
    }
    if (CropEffectFactory.needCropRegionEffect(cropState)) {
      videoEffects.add(CropEffectFactory.createCropRegionEffect(cropState));
    }

    if (!videoEffects.isEmpty()) {
      editedMediaItemBuilder.setEffects(new Effects(
        Collections.emptyList(),
        videoEffects
      ));
    }

    EditedMediaItem editedMediaItem = editedMediaItemBuilder.build();

    // Transformer
    final AtomicBoolean transformFinished = new AtomicBoolean();
    Transformer.Builder transformerBuilder = new Transformer.Builder(UI.getAppContext())
      .setVideoMimeType(MimeTypes.VIDEO_H264)
      .addListener(new Transformer.Listener() {
        @Override
        public void onCompleted (@NonNull Composition composition, @NonNull ExportResult exportResult) {
          synchronized (transformFinished) {
            transformFinished.set(true);
            onComplete.run();
          }
        }

        @Override
        public void onError (@NonNull Composition composition, @NonNull ExportResult exportResult,
                             @NonNull ExportException exportException) {
          synchronized (transformFinished) {
            transformFinished.set(true);
            onFailure.runWithData(exportException);
          }
        }
      });
    if (!editedMediaItem.removeAudio) {
      transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC);
    }
    if (inputVideoBitrate > outputVideoBitrate) {
      VideoEncoderSettings videoEncoderSettings = new VideoEncoderSettings.Builder()
        .setBitrate((int) outputVideoBitrate)
        .build();
      transformerBuilder.setEncoderFactory(new DefaultEncoderFactory.Builder(UI.getAppContext())
        .setRequestedVideoEncoderSettings(videoEncoderSettings)
        .build());
    }

    entry.transformer = transformerBuilder.build();

    ProgressHolder progressHolder = new ProgressHolder();
    File outFile = new File(destinationPath);
    Runnable progressRunner = new Runnable() {
      @Override
      public void run () {
        if (entry.transcodeFinished.get() || entry.canceled.get()) {
          return;
        }
        synchronized (transformFinished) {
          if (transformFinished.get()) {
            return;
          }
          @Transformer.ProgressState int progressState = entry.transformer.getProgress(progressHolder);
          if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
            double progress = (double) progressHolder.progress / 100.0;
            long fileSize = outFile.length();
            onProgress.onTranscodeProgress(progress, fileSize);
          }
          queue.post(this, 500L);
        }
      }
    };

    entry.transformer.start(editedMediaItem, destinationPath);
    progressRunner.run();
  }

  private static DataSource toDataSource (String sourcePath) {
    if (sourcePath.startsWith("content://")) {
      return new UriDataSource(UI.getAppContext(), Uri.parse(sourcePath));
    } else {
      FilePathDataSource dataSource = new FilePathDataSource(sourcePath);
      dataSource.initialize();
      return dataSource;
    }
  }

  private static int getFrameRate (String sourcePath) {
    DataSource dataSource = toDataSource(sourcePath);
    MediaFormat format = dataSource.getTrackFormat(TrackType.VIDEO);
    return getFrameRate(format);
  }

  private static int getFrameRate (@Nullable MediaFormat format) {
    if (format != null && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      return format.getInteger(MediaFormat.KEY_FRAME_RATE);
    }
    return -1;
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void convertVideoComplex (String sourcePath, String destinationPath, VideoGenerationInfo info, Entry entry, ProgressCallback onProgress, Runnable onComplete, RunnableData<String> onCancel, RunnableData<Throwable> onFailure) {
    if (info.hasCrop()) {
      throw new IllegalArgumentException();
    }

    DataSource dataSource = toDataSource(sourcePath);

    if (info.needTrim()) {
      long trimEnd = info.getEndTimeUs() == -1 ? 0 : dataSource.getDurationUs() - info.getEndTimeUs();
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
      int outputFrameRate = videoLimit.getOutputFrameRate(-1);
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
          int inputFrameRate = getFrameRate(format);
          outputFrameRate = videoLimit.getOutputFrameRate(inputFrameRate);
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
          onCancel.runWithData("Transcode canceled");
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

    if (info.needTrim() || info.needMute() || info.getRotate() != 0 || info.hasCrop()) {
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

    long bytesCount = getBytesCount(sourcePath, true);
    entry.resetProgress(bytesCount);

    tdlib.filegen().getContentExecutor().execute(() -> {
      boolean success = false;
      try {
        File file = new File(sourcePath);
        try (Source in = Okio.source(file)) {
          success = tdlib.filegen().copy(generationId, sourcePath, in, destinationPath, file.length(), entry.canceled);
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
    if (info.hasCrop()) {
      return false;
    }
    VideoData videoData = new VideoData(sourcePath);
    return info.needTrim() ?
        videoData.editMovie(destinationPath, info.needMute(), info.getRotate(), (double) info.getStartTimeUs() / 1_000_000.0, info.getEndTimeUs() == -1 ? -1 : (double) info.getEndTimeUs() / 1_000_000.0, onProgress, isCancelled) :
        videoData.editMovie(destinationPath, info.needMute(), info.getRotate(), onProgress, isCancelled);
  }
}
