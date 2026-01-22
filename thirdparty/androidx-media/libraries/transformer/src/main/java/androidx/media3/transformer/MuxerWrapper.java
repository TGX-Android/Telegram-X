/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer;

import static androidx.annotation.VisibleForTesting.PRIVATE;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.contains;
import static androidx.media3.common.util.Util.usToMs;
import static androidx.media3.effect.DebugTraceUtil.COMPONENT_MUXER;
import static androidx.media3.effect.DebugTraceUtil.EVENT_ACCEPTED_INPUT;
import static androidx.media3.effect.DebugTraceUtil.EVENT_CAN_WRITE_SAMPLE;
import static androidx.media3.effect.DebugTraceUtil.EVENT_INPUT_ENDED;
import static androidx.media3.effect.DebugTraceUtil.EVENT_OUTPUT_ENDED;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodec.BufferInfo;
import android.util.SparseArray;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.container.NalUnitUtil;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.muxer.MuxerException;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A wrapper around a media muxer.
 *
 * <p>This wrapper can contain at most one video track and one audio track.
 */
/* package */ final class MuxerWrapper {
  private static final String TAG = "MuxerWrapper";

  /**
   * Thrown when video formats fail to match between {@link #MUXER_MODE_MUX_PARTIAL} and {@link
   * #MUXER_MODE_APPEND}.
   */
  public static final class AppendTrackFormatException extends Exception {

    /**
     * Creates an instance.
     *
     * @param message See {@link #getMessage()}.
     */
    public AppendTrackFormatException(String message) {
      super(message);
    }
  }

  /** Different modes for muxing. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({MUXER_MODE_DEFAULT, MUXER_MODE_MUX_PARTIAL, MUXER_MODE_APPEND})
  public @interface MuxerMode {}

  /** The default muxer mode. */
  public static final int MUXER_MODE_DEFAULT = 0;

  /**
   * Used for muxing a partial track(s). The {@link TrackInfo} is kept the same when {@linkplain
   * #changeToAppendMode() transitioning} to {@link #MUXER_MODE_APPEND} after finishing muxing
   * partial tracks.
   */
  public static final int MUXER_MODE_MUX_PARTIAL = 1;

  /** Used for appending the remaining samples with the previously muxed partial file. */
  public static final int MUXER_MODE_APPEND = 2;

  /** Represents a reason for which the muxer is released. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    MUXER_RELEASE_REASON_COMPLETED,
    MUXER_RELEASE_REASON_CANCELLED,
    MUXER_RELEASE_REASON_ERROR
  })
  public @interface MuxerReleaseReason {}

  /** Muxer is released after the export completed successfully. */
  public static final int MUXER_RELEASE_REASON_COMPLETED = 0;

  /** Muxer is released after the export was cancelled. */
  public static final int MUXER_RELEASE_REASON_CANCELLED = 1;

  /** Muxer is released after an error occurred during the export. */
  public static final int MUXER_RELEASE_REASON_ERROR = 2;

  public interface Listener {
    void onTrackEnded(
        @C.TrackType int trackType, Format format, int averageBitrate, int sampleCount);

    void onSampleWrittenOrDropped();

    void onEnded(long durationMs, long fileSizeBytes);

    void onError(ExportException exportException);
  }

  /**
   * The maximum difference between the track positions, in microseconds.
   *
   * <p>The value of this constant has been chosen based on the interleaving observed in a few media
   * files, where continuous chunks of the same track were about 0.5 seconds long.
   */
  private static final long MAX_TRACK_WRITE_AHEAD_US = Util.msToUs(500);

  private final String outputPath;
  private final Muxer.Factory muxerFactory;
  private final Listener listener;
  private final boolean dropSamplesBeforeFirstVideoSample;
  private final SparseArray<TrackInfo> trackTypeToInfo;
  @Nullable private final Format appendVideoFormat;
  private final BufferInfo bufferInfo;

  private boolean isReady;
  private boolean isEnded;
  private @C.TrackType int previousTrackType;
  private long minTrackTimeUs;
  private long minEndedTrackTimeUs;
  private long maxEndedTrackTimeUs;
  private @MonotonicNonNull Muxer muxer;
  private @MuxerMode int muxerMode;
  private boolean muxedPartialVideo;
  private boolean muxedPartialAudio;
  private long firstVideoPresentationTimeUs;

  private volatile int additionalRotationDegrees;
  private volatile int trackCount;

  /**
   * Creates an instance.
   *
   * <p>{@code appendVideoFormat} must be non-{@code null} when using {@link
   * #MUXER_MODE_MUX_PARTIAL}.
   *
   * @param outputPath The output file path to write the media data to.
   * @param muxerFactory A {@link Muxer.Factory} to create a {@link Muxer}.
   * @param listener A {@link MuxerWrapper.Listener}.
   * @param muxerMode The {@link MuxerMode}. The initial mode must be {@link #MUXER_MODE_DEFAULT} or
   *     {@link #MUXER_MODE_MUX_PARTIAL}.
   * @param dropSamplesBeforeFirstVideoSample Whether to drop any non-video samples with
   *     presentation timestamps before the first video sample.
   * @param appendVideoFormat The format which will be used to write samples after transitioning
   *     from {@link #MUXER_MODE_MUX_PARTIAL} to {@link #MUXER_MODE_APPEND}.
   */
  public MuxerWrapper(
      String outputPath,
      Muxer.Factory muxerFactory,
      Listener listener,
      @MuxerMode int muxerMode,
      boolean dropSamplesBeforeFirstVideoSample,
      @Nullable Format appendVideoFormat) {
    this.outputPath = outputPath;
    this.muxerFactory = muxerFactory;
    this.listener = listener;
    checkArgument(muxerMode == MUXER_MODE_DEFAULT || muxerMode == MUXER_MODE_MUX_PARTIAL);
    this.muxerMode = muxerMode;
    this.dropSamplesBeforeFirstVideoSample = dropSamplesBeforeFirstVideoSample;
    checkArgument(
        (muxerMode == MUXER_MODE_DEFAULT && appendVideoFormat == null)
            || (muxerMode == MUXER_MODE_MUX_PARTIAL && appendVideoFormat != null),
        "appendVideoFormat must be present if and only if muxerMode is MUXER_MODE_MUX_PARTIAL.");
    this.appendVideoFormat = appendVideoFormat;
    trackTypeToInfo = new SparseArray<>();
    previousTrackType = C.TRACK_TYPE_NONE;
    firstVideoPresentationTimeUs = C.TIME_UNSET;
    minEndedTrackTimeUs = Long.MAX_VALUE;
    bufferInfo = new BufferInfo();
  }

  /**
   * Returns initialization data that is strict enough for both bitstreams, or {@code null} if the
   * same initialization data cannot represent both bitstreams.
   *
   * @param existingVideoTrackFormat The starting video format to compare.
   * @param newVideoTrackFormat The candidate format of the video bitstream to be appended after the
   *     existing format.
   * @return The initialization data that captures both input formats, or {@code null} if both
   *     formats cannot be represented by the same initialization data.
   */
  @Nullable
  @VisibleForTesting(otherwise = PRIVATE)
  public static List<byte[]> getMostCompatibleInitializationData(
      Format existingVideoTrackFormat, Format newVideoTrackFormat) {
    if (existingVideoTrackFormat.initializationDataEquals(newVideoTrackFormat)) {
      return existingVideoTrackFormat.initializationData;
    }
    if (!Objects.equals(newVideoTrackFormat.sampleMimeType, MimeTypes.VIDEO_H264)
        || !Objects.equals(existingVideoTrackFormat.sampleMimeType, MimeTypes.VIDEO_H264)) {
      return null;
    }
    if (newVideoTrackFormat.initializationData.size() != 2
        || existingVideoTrackFormat.initializationData.size() != 2) {
      return null;
    }
    // Check picture parameter sets match.
    if (!Arrays.equals(
        newVideoTrackFormat.initializationData.get(1),
        existingVideoTrackFormat.initializationData.get(1))) {
      return null;
    }
    // Allow level_idc to be lower in the new stream.
    // Note: the SPS doesn't need to be unescaped because it's not possible to have two
    // consecutive 0 bytes at/before level_idc.
    byte[] newSps = newVideoTrackFormat.initializationData.get(0);
    byte[] existingSps = existingVideoTrackFormat.initializationData.get(0);
    // Skip 3 bytes: NAL unit type, profile, and reserved fields.
    int spsLevelIndex = NalUnitUtil.NAL_START_CODE.length + 3;
    if (spsLevelIndex >= newSps.length) {
      return null;
    }
    if (newSps.length != existingSps.length) {
      return null;
    }
    for (int i = 0; i < newSps.length; i++) {
      if (i != spsLevelIndex && newSps[i] != existingSps[i]) {
        return null;
      }
    }
    for (int i = 0; i < NalUnitUtil.NAL_START_CODE.length; i++) {
      if (newSps[i] != NalUnitUtil.NAL_START_CODE[i]) {
        return null;
      }
    }
    int nalUnitTypeMask = 0x1F;
    if ((newSps[NalUnitUtil.NAL_START_CODE.length] & nalUnitTypeMask)
        != NalUnitUtil.H264_NAL_UNIT_TYPE_SPS) {
      return null;
    }
    // Check that H.264 profile is non-zero.
    if (newSps[NalUnitUtil.NAL_START_CODE.length + 1] == 0) {
      return null;
    }
    return existingSps[spsLevelIndex] >= newSps[spsLevelIndex]
        ? existingVideoTrackFormat.initializationData
        : newVideoTrackFormat.initializationData;
  }

  /**
   * Changes {@link MuxerMode} to {@link #MUXER_MODE_APPEND}.
   *
   * <p>This method must be called only after partial file is muxed using {@link
   * #MUXER_MODE_MUX_PARTIAL}.
   */
  public void changeToAppendMode() {
    checkState(muxerMode == MUXER_MODE_MUX_PARTIAL);

    muxerMode = MUXER_MODE_APPEND;
  }

  /**
   * Sets the clockwise rotation to add to the {@linkplain #addTrackFormat(Format) video track's}
   * rotation, in degrees.
   *
   * <p>This value must be set before any track format is {@linkplain #addTrackFormat(Format)
   * added}.
   *
   * <p>Can be called from any thread.
   *
   * @throws IllegalStateException If a track format was {@linkplain #addTrackFormat(Format) added}
   *     before calling this method.
   */
  public void setAdditionalRotationDegrees(int additionalRotationDegrees) {
    checkState(
        trackTypeToInfo.size() == 0 || this.additionalRotationDegrees == additionalRotationDegrees,
        "The additional rotation cannot be changed after adding track formats.");
    this.additionalRotationDegrees = additionalRotationDegrees;
  }

  /**
   * Sets the number of output tracks.
   *
   * <p>The track count must be set before any track format is {@linkplain #addTrackFormat(Format)
   * added}.
   *
   * <p>When using muxer mode other than {@link #MUXER_MODE_DEFAULT}, the track count must be 1.
   *
   * <p>Can be called from any thread.
   *
   * @throws IllegalStateException If a track format was {@linkplain #addTrackFormat(Format) added}
   *     before calling this method.
   */
  public void setTrackCount(@IntRange(from = 1) int trackCount) {
    if (muxerMode == MUXER_MODE_APPEND) {
      return;
    }
    checkState(
        trackTypeToInfo.size() == 0,
        "The track count cannot be changed after adding track formats.");
    this.trackCount = trackCount;
  }

  /** Returns whether the sample {@linkplain MimeTypes MIME type} is supported. */
  public boolean supportsSampleMimeType(@Nullable String mimeType) {
    @C.TrackType int trackType = MimeTypes.getTrackType(mimeType);
    return getSupportedSampleMimeTypes(trackType).contains(mimeType);
  }

  /**
   * Returns the supported {@linkplain MimeTypes MIME types} for the given {@linkplain C.TrackType
   * track type}.
   */
  public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
    return muxerFactory.getSupportedSampleMimeTypes(trackType);
  }

  /**
   * Adds a track format to the muxer.
   *
   * <p>The number of tracks must be {@linkplain #setTrackCount(int) set} before any format is added
   * and all the formats must be added before any samples can be {@linkplain #writeSample(int,
   * ByteBuffer, boolean, long) written}.
   *
   * <p>{@link Muxer#addMetadataEntry(Metadata.Entry)} is called if the {@link Format#metadata} is
   * present.
   *
   * @param format The {@link Format} to be added. In {@link #MUXER_MODE_APPEND} mode, the added
   *     {@link Format} must match the existing {@link Format} set when the muxer was in {@link
   *     #MUXER_MODE_MUX_PARTIAL} mode.
   * @throws AppendTrackFormatException If the existing {@link Format} does not match the newly
   *     added {@link Format} in {@link #MUXER_MODE_APPEND}.
   * @throws IllegalArgumentException If the format is unsupported or if it does not match the
   *     existing format in {@link #MUXER_MODE_APPEND} mode.
   * @throws IllegalStateException If the number of formats added exceeds the {@linkplain
   *     #setTrackCount track count}, if {@link #setTrackCount(int)} has not been called or if there
   *     is already a track of that {@link C.TrackType}.
   * @throws MuxerException If the underlying {@link Muxer} encounters a problem while adding the
   *     track.
   */
  public void addTrackFormat(Format format) throws AppendTrackFormatException, MuxerException {
    @Nullable String sampleMimeType = format.sampleMimeType;
    @C.TrackType int trackType = MimeTypes.getTrackType(sampleMimeType);
    checkArgument(
        trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_VIDEO,
        "Unsupported track format: " + sampleMimeType);
    if (trackType == C.TRACK_TYPE_VIDEO) {
      format =
          format
              .buildUpon()
              .setRotationDegrees((format.rotationDegrees + additionalRotationDegrees) % 360)
              .build();
      if (muxerMode == MUXER_MODE_MUX_PARTIAL) {
        List<byte[]> mostCompatibleInitializationData =
            getMostCompatibleInitializationData(format, checkNotNull(appendVideoFormat));
        if (mostCompatibleInitializationData == null) {
          throw new AppendTrackFormatException("Switching to MUXER_MODE_APPEND will fail.");
        }
        format = format.buildUpon().setInitializationData(mostCompatibleInitializationData).build();
      }
    }

    if (muxerMode == MUXER_MODE_APPEND) {
      if (trackType == C.TRACK_TYPE_VIDEO) {
        checkState(contains(trackTypeToInfo, C.TRACK_TYPE_VIDEO));
        TrackInfo videoTrackInfo = trackTypeToInfo.get(C.TRACK_TYPE_VIDEO);

        // Ensure that video formats are the same. Some fields like codecs, averageBitrate,
        // framerate, etc, don't match exactly in the Extractor output format and the Encoder output
        // format but these fields can be ignored.
        // TODO: b/308180225 - Compare Format.colorInfo as well.
        Format existingFormat = videoTrackInfo.format;
        if (!Objects.equals(existingFormat.sampleMimeType, format.sampleMimeType)) {
          throw new AppendTrackFormatException(
              "Video format mismatch - sampleMimeType: "
                  + existingFormat.sampleMimeType
                  + " != "
                  + format.sampleMimeType);
        }
        if (existingFormat.width != format.width) {
          throw new AppendTrackFormatException(
              "Video format mismatch - width: " + existingFormat.width + " != " + format.width);
        }
        if (existingFormat.height != format.height) {
          throw new AppendTrackFormatException(
              "Video format mismatch - height: " + existingFormat.height + " != " + format.height);
        }
        if (existingFormat.rotationDegrees != format.rotationDegrees) {
          throw new AppendTrackFormatException(
              "Video format mismatch - rotationDegrees: "
                  + existingFormat.rotationDegrees
                  + " != "
                  + format.rotationDegrees);
        }
        // The initialization data of the existing format is already compatible with
        // appendVideoFormat.
        if (!format.initializationDataEquals(checkNotNull(appendVideoFormat))) {
          throw new AppendTrackFormatException(
              "The initialization data of the newly added track format doesn't match"
                  + " appendVideoFormat.");
        }
      } else if (trackType == C.TRACK_TYPE_AUDIO) {
        checkState(contains(trackTypeToInfo, C.TRACK_TYPE_AUDIO));
        TrackInfo audioTrackInfo = trackTypeToInfo.get(C.TRACK_TYPE_AUDIO);

        Format existingFormat = audioTrackInfo.format;
        if (!Objects.equals(existingFormat.sampleMimeType, format.sampleMimeType)) {
          throw new AppendTrackFormatException(
              "Audio format mismatch - sampleMimeType: "
                  + existingFormat.sampleMimeType
                  + " != "
                  + format.sampleMimeType);
        }
        if (existingFormat.channelCount != format.channelCount) {
          throw new AppendTrackFormatException(
              "Audio format mismatch - channelCount: "
                  + existingFormat.channelCount
                  + " != "
                  + format.channelCount);
        }
        if (existingFormat.sampleRate != format.sampleRate) {
          throw new AppendTrackFormatException(
              "Audio format mismatch - sampleRate: "
                  + existingFormat.sampleRate
                  + " != "
                  + format.sampleRate);
        }
        if (!existingFormat.initializationDataEquals(format)) {
          throw new AppendTrackFormatException("Audio format mismatch - initializationData.");
        }
      }
      return;
    }

    int trackCount = this.trackCount;
    checkState(trackCount > 0, "The track count should be set before the formats are added.");
    checkState(trackTypeToInfo.size() < trackCount, "All track formats have already been added.");
    checkState(
        !contains(trackTypeToInfo, trackType), "There is already a track of type " + trackType);

    ensureMuxerInitialized();
    TrackInfo trackInfo = new TrackInfo(format, muxer.addTrack(format));
    trackTypeToInfo.put(trackType, trackInfo);
    DebugTraceUtil.logEvent(
        COMPONENT_MUXER,
        DebugTraceUtil.EVENT_INPUT_FORMAT,
        C.TIME_UNSET,
        /* extraFormat= */ "%s:%s",
        /* extraArgs...= */ Util.getTrackTypeString(trackType),
        format);

    if (format.metadata != null) {
      for (int i = 0; i < format.metadata.length(); i++) {
        muxer.addMetadataEntry(format.metadata.get(i));
      }
    }

    if (trackTypeToInfo.size() == trackCount) {
      isReady = true;
    }
  }

  /**
   * Returns the {@link Format} of given {@code trackType} that was {@linkplain #addTrackFormat
   * added}.
   *
   * @throws IllegalArgumentException If the {@code trackType} has not been {@linkplain
   *     #addTrackFormat added}.
   */
  public Format getTrackFormat(@C.TrackType int trackType) {
    checkArgument(contains(trackTypeToInfo, trackType));
    return trackTypeToInfo.get(trackType).format;
  }

  /**
   * Attempts to write a sample to the muxer.
   *
   * @param trackType The {@link C.TrackType} of the sample.
   * @param data The sample to write.
   * @param isKeyFrame Whether the sample is a key frame.
   * @param presentationTimeUs The presentation time of the sample in microseconds.
   * @return Whether the sample was successfully written, or dropped if configured to drop the
   *     sample via {@code dropSamplesBeforeFirstVideoSample}. {@code false} if samples of other
   *     {@linkplain C.TrackType track types} should be written first to ensure the files track
   *     interleaving is balanced, or if the muxer hasn't {@linkplain #addTrackFormat(Format)
   *     received a format} for every {@linkplain #setTrackCount(int) track}.
   * @throws IllegalArgumentException If the muxer doesn't have a {@linkplain #endTrack(int)
   *     non-ended} track of the given {@link C.TrackType}.
   * @throws MuxerException If the underlying {@link Muxer} fails to write the sample.
   */
  public boolean writeSample(
      @C.TrackType int trackType, ByteBuffer data, boolean isKeyFrame, long presentationTimeUs)
      throws MuxerException {
    checkArgument(contains(trackTypeToInfo, trackType));
    TrackInfo trackInfo = trackTypeToInfo.get(trackType);
    boolean canWriteSample = canWriteSample(trackType, presentationTimeUs);

    DebugTraceUtil.logEvent(
        COMPONENT_MUXER,
        EVENT_CAN_WRITE_SAMPLE,
        presentationTimeUs,
        /* extraFormat= */ "%s:%s",
        /* extraArgs...= */ Util.getTrackTypeString(trackType),
        canWriteSample);

    if (trackType == C.TRACK_TYPE_VIDEO) {
      if (firstVideoPresentationTimeUs == C.TIME_UNSET) {
        firstVideoPresentationTimeUs = presentationTimeUs;
      }
    } else if (trackType == C.TRACK_TYPE_AUDIO) {
      if (dropSamplesBeforeFirstVideoSample
          && contains(trackTypeToInfo, C.TRACK_TYPE_VIDEO)
          && firstVideoPresentationTimeUs != C.TIME_UNSET
          && presentationTimeUs < firstVideoPresentationTimeUs) {
        // Drop the buffer.
        listener.onSampleWrittenOrDropped();
        return true;
      }
    }
    if (!canWriteSample) {
      return false;
    }

    if (trackInfo.sampleCount == 0) {
      if (trackType == C.TRACK_TYPE_VIDEO
          && contains(trackTypeToInfo, C.TRACK_TYPE_AUDIO)
          && !dropSamplesBeforeFirstVideoSample) {
        checkState(firstVideoPresentationTimeUs != C.TIME_UNSET);
        // Set the presentation timestamp of the first video to zero so that the first video frame
        // is presented when playback starts cross-platform. Moreover, MediaMuxer shifts all video
        // sample times to zero under API30 and it breaks A/V sync.
        // Only apply this when there is audio track added, i.e. when not recording screen.
        // TODO: b/376217254 - Consider removing after switching to InAppMuxer.
        // TODO: b/376217254 - Remove audio dropping logic, use video frame shifting instead.
        Log.w(
            TAG,
            "Applying workarounds for edit list: shifting only the first video timestamp to zero.");
        presentationTimeUs = 0;
      }
      trackInfo.startTimeUs = presentationTimeUs;
    }
    trackInfo.sampleCount++;
    trackInfo.bytesWritten += data.remaining();
    trackInfo.timeUs = max(trackInfo.timeUs, presentationTimeUs);
    listener.onSampleWrittenOrDropped();
    checkStateNotNull(muxer);
    bufferInfo.set(
        data.position(),
        data.remaining(),
        presentationTimeUs,
        TransformerUtil.getMediaCodecFlags(isKeyFrame ? C.BUFFER_FLAG_KEY_FRAME : 0));
    muxer.writeSampleData(trackInfo.trackId, data, bufferInfo);

    DebugTraceUtil.logEvent(
        COMPONENT_MUXER,
        EVENT_ACCEPTED_INPUT,
        presentationTimeUs,
        /* extraFormat= */ "%s",
        /* extraArgs...= */ Util.getTrackTypeString(trackType));
    previousTrackType = trackType;
    return true;
  }

  /**
   * Attempts to notify the muxer that all the samples have been {@linkplain #writeSample(int,
   * ByteBuffer, boolean, long) written} for a given track.
   *
   * @param trackType The {@link C.TrackType}.
   */
  public void endTrack(@C.TrackType int trackType) {
    if (!isReady || !contains(trackTypeToInfo, trackType)) {
      return;
    }

    TrackInfo trackInfo = trackTypeToInfo.get(trackType);
    minEndedTrackTimeUs = max(0, min(minEndedTrackTimeUs, trackInfo.startTimeUs));
    maxEndedTrackTimeUs = max(maxEndedTrackTimeUs, trackInfo.timeUs);
    listener.onTrackEnded(
        trackType, trackInfo.format, trackInfo.getAverageBitrate(), trackInfo.sampleCount);
    DebugTraceUtil.logEvent(
        COMPONENT_MUXER,
        EVENT_INPUT_ENDED,
        trackInfo.timeUs,
        /* extraFormat= */ "%s",
        /* extraArgs...= */ Util.getTrackTypeString(trackType));

    if (muxerMode == MUXER_MODE_MUX_PARTIAL) {
      if (trackType == C.TRACK_TYPE_VIDEO) {
        muxedPartialVideo = true;
      } else if (trackType == C.TRACK_TYPE_AUDIO) {
        muxedPartialAudio = true;
      }
    } else {
      trackTypeToInfo.delete(trackType);
      if (trackTypeToInfo.size() == 0) {
        isEnded = true;
        DebugTraceUtil.logEvent(COMPONENT_MUXER, EVENT_OUTPUT_ENDED, maxEndedTrackTimeUs);
      }
    }

    long durationMs = usToMs(maxEndedTrackTimeUs - minEndedTrackTimeUs);
    if (muxerMode == MUXER_MODE_MUX_PARTIAL
        && muxedPartialVideo
        && (muxedPartialAudio || trackCount == 1)) {
      listener.onEnded(durationMs, getCurrentOutputSizeBytes());
      return;
    }

    if (isEnded) {
      listener.onEnded(durationMs, getCurrentOutputSizeBytes());
    }
  }

  /**
   * Returns whether all the tracks are {@linkplain #endTrack(int) ended} or a partial file is
   * completely muxed using {@link #MUXER_MODE_MUX_PARTIAL}.
   */
  public boolean isEnded() {
    return isEnded
        || (muxerMode == MUXER_MODE_MUX_PARTIAL
            && muxedPartialVideo
            && (muxedPartialAudio || trackCount == 1));
  }

  /**
   * Finishes writing the output and may release any resources associated with muxing.
   *
   * <p>When this method is called in {@link #MUXER_MODE_MUX_PARTIAL} mode, the resources are not
   * released and the {@link MuxerWrapper} can be reused after {@link #changeToAppendMode() changing
   * mode} to {@link #MUXER_MODE_APPEND}. In all other modes the {@link MuxerWrapper} cannot be used
   * anymore once this method has been called.
   *
   * <p>The resources are always released when the {@code releaseReason} is {@link
   * #MUXER_RELEASE_REASON_CANCELLED} or {@link #MUXER_RELEASE_REASON_ERROR}.
   *
   * @param releaseReason The reason to release the muxer.
   * @throws MuxerException If the underlying {@link Muxer} fails to finish writing the output and
   *     the {@code releaseReason} is not {@link #MUXER_RELEASE_REASON_CANCELLED}.
   */
  public void finishWritingAndMaybeRelease(@MuxerReleaseReason int releaseReason)
      throws MuxerException {
    if (releaseReason == MUXER_RELEASE_REASON_COMPLETED && muxerMode == MUXER_MODE_MUX_PARTIAL) {
      return;
    }
    isReady = false;
    if (muxer != null) {
      try {
        muxer.close();
      } catch (MuxerException e) {
        if (releaseReason == MUXER_RELEASE_REASON_CANCELLED
            && checkNotNull(e.getMessage())
                .equals(FrameworkMuxer.MUXER_STOPPING_FAILED_ERROR_MESSAGE)) {
          // When releasing the muxer, FrameworkMuxer may sometimes fail before the actual release.
          // When the release is due to cancellation, swallow this exception.
          return;
        }
        throw e;
      }
    }
  }

  private boolean canWriteSample(@C.TrackType int trackType, long presentationTimeUs) {
    if (dropSamplesBeforeFirstVideoSample
        && trackType != C.TRACK_TYPE_VIDEO
        && contains(trackTypeToInfo, C.TRACK_TYPE_VIDEO)
        && firstVideoPresentationTimeUs == C.TIME_UNSET) {
      // Haven't received the first video sample yet, so can't write any audio.
      return false;
    }
    if (!isReady) {
      return false;
    }
    if (trackTypeToInfo.size() == 1) {
      return true;
    }
    if (presentationTimeUs - trackTypeToInfo.get(trackType).timeUs > MAX_TRACK_WRITE_AHEAD_US) {
      TrackInfo trackInfoWithMinTimeUs = checkNotNull(getTrackInfoWithMinTimeUs(trackTypeToInfo));
      if (MimeTypes.getTrackType(trackInfoWithMinTimeUs.format.sampleMimeType) == trackType) {
        // Unstuck the muxer if consecutive timestamps from the same track are more than
        // MAX_TRACK_WRITE_AHEAD_US apart.
        return true;
      }
    }
    if (trackType != previousTrackType) {
      minTrackTimeUs = checkNotNull(getTrackInfoWithMinTimeUs(trackTypeToInfo)).timeUs;
    }
    return presentationTimeUs - minTrackTimeUs <= MAX_TRACK_WRITE_AHEAD_US;
  }

  @EnsuresNonNull("muxer")
  private void ensureMuxerInitialized() throws MuxerException {
    if (muxer == null) {
      muxer = muxerFactory.create(outputPath);
    }
  }

  /** Returns the current size in bytes of the output, or {@link C#LENGTH_UNSET} if unavailable. */
  private long getCurrentOutputSizeBytes() {
    long fileSize = new File(outputPath).length();
    return fileSize > 0 ? fileSize : C.LENGTH_UNSET;
  }

  @Nullable
  private static TrackInfo getTrackInfoWithMinTimeUs(SparseArray<TrackInfo> trackTypeToInfo) {
    if (trackTypeToInfo.size() == 0) {
      return null;
    }

    TrackInfo trackInfoWithMinTimeUs = trackTypeToInfo.valueAt(0);
    for (int i = 1; i < trackTypeToInfo.size(); i++) {
      TrackInfo trackInfo = trackTypeToInfo.valueAt(i);
      if (trackInfo.timeUs < trackInfoWithMinTimeUs.timeUs) {
        trackInfoWithMinTimeUs = trackInfo;
      }
    }
    return trackInfoWithMinTimeUs;
  }

  private static final class TrackInfo {
    public final Format format;
    public final int trackId;

    public long startTimeUs;
    public long bytesWritten;
    public int sampleCount;
    public long timeUs;

    public TrackInfo(Format format, int trackId) {
      this.format = format;
      this.trackId = trackId;
    }

    /**
     * Returns the average bitrate of data written to the track, or {@link C#RATE_UNSET_INT} if
     * there is no track data.
     */
    public int getAverageBitrate() {
      if (timeUs <= 0 || bytesWritten <= 0 || timeUs == startTimeUs) {
        return C.RATE_UNSET_INT;
      }

      // The number of bytes written is not a timestamp, however this utility method provides
      // overflow-safe multiplication & division.
      return (int)
          Util.scaleLargeTimestamp(
              /* timestamp= */ bytesWritten,
              /* multiplier= */ C.BITS_PER_BYTE * C.MICROS_PER_SECOND,
              /* divisor= */ timeUs - startTimeUs);
    }
  }
}
