package org.thunderdog.challegram.filegen;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.Nullable;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.googlecode.mp4parser.util.Matrix;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageReader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.SparseLongArray;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.unit.BitwiseUtils;

public class VideoData {
  private final String sourcePath;

  @Nullable
  private MediaExtractor extractor;

  @Nullable
  private Movie movie;
  private List<Track> originalTracks;
  private double[] timeOfSyncSamples;
  private double accurateDuration, width, height;
  private int frameRate;
  private long bitrate;

  private boolean hasMinimumSampleCount;
  private int videoTrackIndex = -1, selectedTrackIndex = -1;

  @Nullable
  private MediaMetadataRetriever retriever;

  private void selectTrack (int index) {
    if (this.selectedTrackIndex != index && extractor != null) {
      if (this.selectedTrackIndex != -1) {
        extractor.unselectTrack(this.selectedTrackIndex);
      }
      this.selectedTrackIndex = index;
      if (index != -1) {
        extractor.selectTrack(index);
      }
    }
  }

  private static int getRotationForMatrix (Matrix matrix) {
    if (matrix == null || matrix.equals(Matrix.ROTATE_0)) {
      return 0;
    } else if (matrix.equals(Matrix.ROTATE_90)) {
      return 90;
    } else if (matrix.equals(Matrix.ROTATE_180)) {
      return 180;
    } else if (matrix.equals(Matrix.ROTATE_270)) {
      return 270;
    }
    throw new IllegalArgumentException();
  }

  private static Matrix matrixForRotation (int rotation) {
    rotation = MathUtils.modulo(rotation, 360);
    switch (rotation) {
      case 0:
        return Matrix.ROTATE_0;
      case 90:
        return Matrix.ROTATE_90;
      case 180:
        return Matrix.ROTATE_180;
      case 270:
        return Matrix.ROTATE_270;
    }
    throw new IllegalArgumentException();
  }

  public VideoData (String sourcePath) throws Throwable {
    this.sourcePath = sourcePath;

    try {
      boolean success;
      // Set up MediaExtractor to read from the source.
      MediaExtractor extractor = new MediaExtractor();
      boolean foundVideo = false;
      try {
        extractor.setDataSource(sourcePath);
        for (int trackIndex = 0; trackIndex < extractor.getTrackCount(); trackIndex++) {
          MediaFormat format = extractor.getTrackFormat(trackIndex);
          String mime = format.getString(MediaFormat.KEY_MIME);
          if (!StringUtils.isEmpty(mime) && mime.startsWith("video/")) {
            foundVideo = true;
            long duration = format.getLong(MediaFormat.KEY_DURATION);
            accurateDuration = (double) duration / 1_000_000.0;
            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
              frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
            }
            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
              bitrate = format.getLong(MediaFormat.KEY_BIT_RATE);
            }
            if (format.containsKey(MediaFormat.KEY_WIDTH)) {
              width = format.getInteger(MediaFormat.KEY_WIDTH);
            }
            if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
              height = format.getInteger(MediaFormat.KEY_HEIGHT);
            }
            if (selectedTrackIndex != -1) {
              extractor.unselectTrack(selectedTrackIndex);
              selectedTrackIndex = -1;
            }
            extractor.selectTrack(videoTrackIndex = selectedTrackIndex = trackIndex);
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            long firstSampleTime = extractor.getSampleTime();
            if (extractor.advance()) {
              long nextSampleTime = extractor.getSampleTime();
              if (!BitwiseUtils.getFlag(extractor.getSampleFlags(), MediaExtractor.SAMPLE_FLAG_SYNC)) {
                extractor.seekTo(nextSampleTime, MediaExtractor.SEEK_TO_NEXT_SYNC);
                nextSampleTime = extractor.getSampleTime();
              }
              hasMinimumSampleCount = nextSampleTime > firstSampleTime && duration - nextSampleTime >= 1000;
            }
          }
        }
        success = foundVideo;
      } catch (Throwable t) {
        extractor.release();
        throw t;
      }
      if (success) {
        this.extractor = extractor;
        return;
      } else {
        extractor.release();
      }
    } catch (Throwable t) {
      Log.i(Log.TAG_VIDEO, "Unable to create MediaExtractor", t);
    }

    openMp4Movie();

    if (movie == null)
      throw new IllegalArgumentException("Unsupported video format");
  }

  private void openMp4Movie () {
    Movie movie = null;
    try {
      movie = MovieCreator.build(sourcePath);
    } catch (Throwable t) {
      Log.i(Log.TAG_VIDEO, "Unable to create mp4 movie", t);
    }
    if (movie != null) {
      this.movie = movie;
      this.originalTracks = movie.getTracks();
      for (Track track : originalTracks) {
        long[] syncSamples = track.getSyncSamples();
        if (syncSamples == null || syncSamples.length == 0)
          continue;
        double[] timeOfSyncSamples = new double[syncSamples.length];
        long timescale = track.getTrackMetaData().getTimescale();
        this.width = track.getTrackMetaData().getWidth();
        this.height = track.getTrackMetaData().getHeight();
        long currentSample = 0;
        double currentTime = 0;

        for (long delta : track.getSampleDurations()) {
          int index = Arrays.binarySearch(syncSamples, currentSample + 1);
          if (index >= 0) {
            // samples always start with 1 but we start with zero therefore +1
            timeOfSyncSamples[index] = currentTime;
          }
          currentTime += (double) delta / (double) timescale;
          currentSample++;
        }
        final double totalTime = currentTime;
        if (this.timeOfSyncSamples != null && !Arrays.equals(this.timeOfSyncSamples, timeOfSyncSamples)) {
          // Unsupported
          this.timeOfSyncSamples = null;
          this.accurateDuration = 0;
          break;
        }
        this.timeOfSyncSamples = timeOfSyncSamples;
        this.accurateDuration = totalTime;
      }
      this.hasMinimumSampleCount = timeOfSyncSamples != null && timeOfSyncSamples.length + (timeOfSyncSamples.length > 0 && accurateDuration > timeOfSyncSamples[timeOfSyncSamples.length - 1] ? 1 : 0) > 2;
    }
  }

  public double getTotalDuration () {
    return accurateDuration;
  }

  public double getWidth () {
    return width;
  }

  public double getHeight () {
    return height;
  }

  public long getBitrate () {
    return bitrate;
  }

  public int getFrameRate () {
    return frameRate;
  }

  public boolean canTrim () {
    return accurateDuration > 0 && hasMinimumSampleCount;
  }

  public boolean needCorrectTrimToSyncSample (boolean withTranscoding) {
    return true; // !withTranscoding;
  }

  public double findClosestSync (double time) {
    return correctTimeToSyncSample(time, TRIM_MODE_CLOSEST);
  }

  public double correctStartTime (double startTime, double endTime, boolean withTranscoding) {
    if (needCorrectTrimToSyncSample(withTranscoding)) {
      startTime = Math.min(endTime, correctTimeToSyncSample(startTime, TRIM_MODE_CLOSEST));
      while (startTime != 0 && (startTime == endTime || (endTime - startTime) < 0.5)) {
        double newStartTime = correctTimeToSyncSample(startTime, TRIM_MODE_PREV);
        if (newStartTime == startTime)
          break;
        startTime = newStartTime;
      }
    }
    return startTime;
  }

  public double correctEndTime (double startTime, double endTime, boolean withTranscoding) {
    if (needCorrectTrimToSyncSample(withTranscoding)) {
      endTime = Math.max(startTime, correctTimeToSyncSample(endTime, TRIM_MODE_CLOSEST));
      while (startTime == endTime || (endTime - startTime) < 0.5) {
        double newEndTime = correctTimeToSyncSample(endTime, TRIM_MODE_NEXT);
        if (endTime == newEndTime)
          break;
        endTime = newEndTime;
      }
    }
    return endTime;
  }

  public boolean editMovie (String outPath, boolean mute, int rotateBy, RunnableLong onProgress, @Nullable AtomicBoolean isCancelled) {
    return editMovie(outPath, mute, rotateBy, -1, -1, onProgress, isCancelled);
  }

  public boolean editMovie (String outPath, boolean mute, int rotateBy, double startTime, double endTime, @Nullable RunnableLong onProgress, @Nullable AtomicBoolean isCancelled) {
    if (extractor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      boolean success = false;
      MediaMuxer muxer = null;
      try {
        SparseLongArray trackMap = new SparseLongArray();
        int orientationHint = rotateBy;
        boolean abort = false;
        for (int inputTrackIndex = 0; inputTrackIndex < extractor.getTrackCount(); inputTrackIndex++) {
          MediaFormat format = extractor.getTrackFormat(inputTrackIndex);
          String mimeType = format.getString(MediaFormat.KEY_MIME);
          if (StringUtils.isEmpty(mimeType))
            continue;
          if (mute && mimeType.startsWith("audio/")) {
            continue;
          }
          if (isCancelled != null && isCancelled.get()) {
            abort = true;
            break;
          }
          if (muxer == null) {
            int outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && ("video/x-vnd.on2.vp8".equals(mimeType) || "video/x-vnd.on2.vp9".equals(mimeType))) {
              outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
            }
            muxer = new MediaMuxer(outPath, outputFormat);
          }
          boolean isVideo = mimeType.startsWith("video/");
          if (rotateBy != 0 && isVideo) {
            final String rotationKey = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? MediaFormat.KEY_ROTATION : "rotation-degrees";
            int currentRotation = 0;
            try {
              currentRotation = format.getInteger(rotationKey);
            } catch (Throwable ignored) { }
            format.setInteger(rotationKey, orientationHint = MathUtils.modulo(currentRotation + rotateBy, 360));
          }
          int bufferSize;
          try {
            bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
          } catch (Throwable t) {
            Log.i(Log.TAG_VIDEO, "Cannot retrieve max input size", t);
            bufferSize = -1;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
              throw t;
          }
          int outputTrackIndex = muxer.addTrack(format);
          trackMap.put(inputTrackIndex, BitwiseUtils.mergeLong(outputTrackIndex, bufferSize));
        }

        if (muxer != null && !abort) {
          ByteBuffer buffer = null;
          MediaCodec.BufferInfo bufferInfo = null;
          if (orientationHint != 0) {
            muxer.setOrientationHint(orientationHint);
          }
          muxer.start();
          for (int i = 0; i < trackMap.size(); i++) {
            if (isCancelled != null && isCancelled.get()) {
              abort = true;
              break;
            }
            final int inputTrackIndex = trackMap.keyAt(i);
            final long outputTrackInfo = trackMap.valueAt(i);
            final int outputTrackIndex = BitwiseUtils.splitLongToFirstInt(outputTrackInfo);
            final int outputBufferSize = BitwiseUtils.splitLongToSecondInt(outputTrackInfo);
            int currentBufferSize = outputBufferSize;

            selectTrack(inputTrackIndex);

            long startTimeUs = startTime == -1 ? 0 : (long) (startTime * 1_000_000.0);
            long endTimeUs = endTime == -1 ? -1 : (long) (endTime * 1_000_000.0);
            extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            long sampleTime;
            while ((sampleTime = extractor.getSampleTime()) != -1 && (endTimeUs == -1 || sampleTime < endTimeUs)) {
              if (isCancelled != null && isCancelled.get()) {
                abort = true;
                break;
              }
              long minBufferSize = outputBufferSize == -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? extractor.getSampleSize() : outputBufferSize;
              if (buffer == null || currentBufferSize < minBufferSize) {
                buffer = ByteBuffer.allocateDirect(currentBufferSize = (int) minBufferSize).order(ByteOrder.nativeOrder());
              }
              int sampleFlags = extractor.getSampleFlags();
              int readBytes;
              if ((readBytes = extractor.readSampleData(buffer, 0)) > 0) {
                if (bufferInfo == null)
                  bufferInfo = new MediaCodec.BufferInfo();
                bufferInfo.set(0, readBytes, sampleTime, sampleFlags);
                muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo);
                buffer.position(0);
              }
              if (!extractor.advance())
                break;
            }
          }
          muxer.stop();
          success = !abort;
        }
      } catch (Throwable t) {
        Log.i(Log.TAG_VIDEO, "Cannot mux video", t);
      }
      if (muxer != null) {
        try {
          muxer.release();
        } catch (Throwable ignored) { }
      }
      selectTrack(videoTrackIndex);
      if (success) {
        return true;
      }
    }

    try {
      Movie editedMovie = newMp4Movie(mute, rotateBy, startTime, endTime);
      Container out = new DefaultMp4Builder().build(editedMovie);
      try (FileOutputStream fos = new FileOutputStream(outPath); FileChannel fc = fos.getChannel()) {
        out.writeContainer(fc);
        return true;
      }
    } catch (Throwable t) {
      Log.w(Log.TAG_VIDEO, "Cannot mux mp4", t);
    }

    // TODO software callback like jcodec?

    return false;
  }

  private static final int TRIM_MODE_NEXT = 1;
  private static final int TRIM_MODE_PREV = 2;
  private static final int TRIM_MODE_CLOSEST = 3;

  private double correctTimeToSyncSample (final double cutHere, int mode) {
    if (extractor != null) {
      try {
        extractor.seekTo((long) (cutHere * 1_000_000.0), mode == TRIM_MODE_PREV ? MediaExtractor.SEEK_TO_PREVIOUS_SYNC : mode == TRIM_MODE_NEXT ? MediaExtractor.SEEK_TO_NEXT_SYNC : MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        long sampleTime = extractor.getSampleTime();
        if (sampleTime == -1)
          return accurateDuration;
        double selectedSync = (double) sampleTime / 1_000_000.0;
        switch (mode) {
          case TRIM_MODE_CLOSEST:
            return Math.abs(accurateDuration - cutHere) < Math.abs(selectedSync - cutHere) ? accurateDuration : selectedSync;
          case TRIM_MODE_NEXT: {
            if (selectedSync <= cutHere) {
              if (!extractor.advance())
                return accurateDuration;
              long nextSampleTime = extractor.getSampleTime();
              if (nextSampleTime == -1)
                return accurateDuration;
              if (!BitwiseUtils.getFlag(extractor.getSampleFlags(), MediaExtractor.SAMPLE_FLAG_SYNC)) {
                extractor.seekTo(nextSampleTime, MediaExtractor.SEEK_TO_NEXT_SYNC);
                nextSampleTime = extractor.getSampleTime();
                if (nextSampleTime == -1)
                  return accurateDuration;
              }
              double nextSyncTime = (double) nextSampleTime / 1_000_000;
              if (nextSyncTime <= selectedSync) {
                return accurateDuration;
              }
              selectedSync = nextSyncTime;
            }
            break;
          }
          case TRIM_MODE_PREV: {
            if (selectedSync >= cutHere) {
              extractor.seekTo(sampleTime - 1, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
              long prevSampleTime = extractor.getSampleTime();
              if (prevSampleTime == -1)
                return 0;
              selectedSync = (double) prevSampleTime / 1_000_000.0;
            }
            break;
          }
        }
        return selectedSync;
      } catch (Throwable t) {
        Log.w(Log.TAG_VIDEO, "Unable to seek", t);
        return cutHere;
      }
    }
    if (timeOfSyncSamples == null) {
      return cutHere;
    }
    double morePrevious = 0;
    double previous = 0;
    int index = 0;
    for (double timeOfSyncSample : timeOfSyncSamples) {
      if (timeOfSyncSample > cutHere) {
        if (mode == TRIM_MODE_CLOSEST) {
          double diffNext = Math.abs(timeOfSyncSample - cutHere);
          double diffPrev = Math.abs(previous - cutHere);
          if (diffPrev < diffNext) {
            return previous;
          } else {
            return timeOfSyncSample;
          }
        } else if (mode == TRIM_MODE_PREV) {
          return cutHere == timeOfSyncSample ? previous : morePrevious;
        } else if (mode == TRIM_MODE_NEXT) {
          return cutHere == timeOfSyncSample && index + 1 < timeOfSyncSamples.length ? timeOfSyncSamples[index + 1] : timeOfSyncSample;
        } else {
          throw new IllegalArgumentException();
        }
      }
      morePrevious = previous;
      previous = timeOfSyncSample;
      index++;
    }
    if (accurateDuration != previous) {
      switch (mode) {
        case TRIM_MODE_CLOSEST: {
          return Math.abs(accurateDuration - cutHere) < Math.abs(previous - cutHere) ? accurateDuration : previous;
        }
        case TRIM_MODE_NEXT: {
          return accurateDuration;
        }
      }
    }
    return previous == cutHere && (mode == TRIM_MODE_PREV) ? morePrevious : previous;
  }

  private MediaMetadataRetriever getRetriever () {
    if (retriever == null) {
      MediaMetadataRetriever retriever = null;
      try {
        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(sourcePath);
        this.retriever = retriever;
        return retriever;
      } catch (Throwable t) {
        Log.i(Log.TAG_VIDEO, "Unable to open retriever", t);
        if (retriever != null) {
          try { retriever.release(); } catch (Throwable ignored) { }
        }
        return null;
      }
    }
    return retriever;
  }

  @Nullable
  public Bitmap getFrameAtTime (double time, int maxSize) {
    MediaMetadataRetriever retriever = getRetriever();
    if (retriever == null)
      return null;
    Bitmap bitmap = retriever.getFrameAtTime((long) (time * 1_000_000.0), MediaMetadataRetriever.OPTION_CLOSEST);
    if (U.isValidBitmap(bitmap)) {
      float scale = Math.max((float) maxSize / (float) bitmap.getWidth(), (float) maxSize / (float) bitmap.getHeight());
      if (scale < 1f) {
        int maxWidth = (int) (bitmap.getWidth() * scale);
        int maxHeight = (int) (bitmap.getHeight() * scale);
        return ImageReader.resizeBitmap(bitmap, maxWidth, maxHeight, false);
      }
      return bitmap;
    }
    return null;
  }

  public void release () {
    if (extractor != null) {
      try {
        extractor.release();
      } catch (Throwable ignored) { }
      extractor = null;
    }
    if (retriever != null) {
      try {
        retriever.release();
      } catch (Throwable ignored) { }
      retriever = null;
    }
  }

  // MP4-specific

  private Movie newMp4Movie (boolean mute, int rotateBy, double startTime, double endTime) throws IOException {
    openMp4Movie();
    if (movie == null)
      throw new IllegalStateException();
    if (!mute && rotateBy == 0 && startTime == -1 && endTime == -1)
      return null;
    movie.setTracks(new LinkedList<>());
    for (Track track : originalTracks) {
      if (mute && "soun".equals(track.getHandler()))
        continue;
      Track outputTrack;
      if (startTime != -1 && endTime != -1) {
        long currentSample = 0;
        double currentTime = 0;
        double lastTime = -1;
        long startSample = -1;
        long endSample = -1;
        long timescale = track.getTrackMetaData().getTimescale();
        for (long delta : track.getSampleDurations()) {
          if (currentTime > lastTime && currentTime <= startTime) {
            // current sample is still before the new starttime
            startSample = currentSample;
          }
          if (currentTime > lastTime && currentTime <= endTime) {
            // current sample is after the new start time and still before the new endtime
            endSample = currentSample;
          }
          lastTime = currentTime;
          currentTime += (double) delta / (double) timescale;
          currentSample++;
        }
        if (startSample != -1 && endSample == -1) {
          endSample = startSample + 1;
        }
        if (startSample == -1 || endSample == -1)
          throw new IllegalArgumentException();
        outputTrack = new AppendTrack(new CroppedTrack(track, startSample, endSample));
      } else {
        outputTrack = new AppendTrack(track);
      }
      if (rotateBy != 0) {
        int originalRotation = getRotationForMatrix(outputTrack.getTrackMetaData().getMatrix());
        outputTrack.getTrackMetaData().setMatrix(matrixForRotation(originalRotation + rotateBy));
      }
      movie.addTrack(outputTrack);
    }
    return movie;
  }
}
