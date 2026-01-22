/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.msToUs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.upstream.Allocator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;

/**
 * {@link MediaSource} that wraps a source and clips its timeline based on specified start/end
 * positions. The wrapped source must consist of a single period.
 */
@UnstableApi
public final class ClippingMediaSource extends WrappingMediaSource {

  /** A builder for {@link ClippingMediaSource}. */
  public static final class Builder {

    private final MediaSource mediaSource;

    private long startPositionUs;
    private long endPositionUs;
    private boolean enableInitialDiscontinuity;
    private boolean allowDynamicClippingUpdates;
    private boolean relativeToDefaultPosition;
    private boolean allowUnseekableMedia;
    private boolean buildCalled;

    /**
     * Creates the builder.
     *
     * @param mediaSource The {@link MediaSource} to clip.
     */
    public Builder(MediaSource mediaSource) {
      this.mediaSource = checkNotNull(mediaSource);
      this.enableInitialDiscontinuity = true;
      this.endPositionUs = C.TIME_END_OF_SOURCE;
    }

    /**
     * Sets the clip start position.
     *
     * <p>The start position is relative to the wrapped source's {@link Timeline.Window}, unless
     * {@link #setRelativeToDefaultPosition} is set to {@code true}.
     *
     * @param startPositionMs The clip start position in milliseconds.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setStartPositionMs(long startPositionMs) {
      return setStartPositionUs(msToUs(startPositionMs));
    }

    /**
     * Sets the clip start position.
     *
     * <p>The start position is relative to the wrapped source's {@link Timeline.Window}, unless
     * {@link #setRelativeToDefaultPosition} is set to {@code true}.
     *
     * @param startPositionUs The clip start position in microseconds.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setStartPositionUs(long startPositionUs) {
      checkArgument(startPositionUs >= 0);
      checkState(!buildCalled);
      this.startPositionUs = startPositionUs;
      return this;
    }

    /**
     * Sets the clip end position.
     *
     * <p>The end position is relative to the wrapped source's {@link Timeline.Window}, unless
     * {@link #setRelativeToDefaultPosition} is set to {@code true}.
     *
     * <p>Specify {@link C#TIME_END_OF_SOURCE} to provide samples up to the end of the source.
     * Specifying a position that exceeds the wrapped source's duration will also result in the end
     * of the source not being clipped.
     *
     * @param endPositionMs The clip end position in milliseconds, or {@link C#TIME_END_OF_SOURCE}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setEndPositionMs(long endPositionMs) {
      return setEndPositionUs(msToUs(endPositionMs));
    }

    /**
     * Sets the clip end position.
     *
     * <p>The end position is relative to the wrapped source's {@link Timeline.Window}, unless
     * {@link #setRelativeToDefaultPosition} is set to {@code true}.
     *
     * <p>Specify {@link C#TIME_END_OF_SOURCE} to provide samples up to the end of the source.
     * Specifying a position that exceeds the wrapped source's duration will also result in the end
     * of the source not being clipped.
     *
     * @param endPositionUs The clip end position in microseconds, or {@link C#TIME_END_OF_SOURCE}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setEndPositionUs(long endPositionUs) {
      checkState(!buildCalled);
      this.endPositionUs = endPositionUs;
      return this;
    }

    /**
     * Sets whether to enable the initial discontinuity.
     *
     * <p>This discontinuity is needed to handle pre-rolling samples from a previous keyframe if the
     * start position doesn't fall onto a keyframe.
     *
     * <p>When starting from the beginning of the stream or when clipping a format that is
     * guaranteed to have keyframes only, the discontinuity won't be applied even if enabled.
     *
     * <p>The default value is {@code true}.
     *
     * @param enableInitialDiscontinuity Whether to enable the initial discontinuity.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setEnableInitialDiscontinuity(boolean enableInitialDiscontinuity) {
      checkState(!buildCalled);
      this.enableInitialDiscontinuity = enableInitialDiscontinuity;
      return this;
    }

    /**
     * Sets whether the clipping of active media periods moves with a live window.
     *
     * <p>If {@code false}, playback ends when it reaches {@code endPositionUs} in the last reported
     * live window at the time a media period was created.
     *
     * <p>The default value is {@code false}.
     *
     * @param allowDynamicClippingUpdates Whether to allow dynamic clipping updates.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setAllowDynamicClippingUpdates(boolean allowDynamicClippingUpdates) {
      checkState(!buildCalled);
      this.allowDynamicClippingUpdates = allowDynamicClippingUpdates;
      return this;
    }

    /**
     * Sets whether the start and end position are relative to the default position of the wrapped
     * source's {@link Timeline.Window}.
     *
     * <p>The default value is {@code false}.
     *
     * @param relativeToDefaultPosition Whether the start and end positions are relative to the
     *     default position of the wrapped source's {@link Timeline.Window}.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setRelativeToDefaultPosition(boolean relativeToDefaultPosition) {
      checkState(!buildCalled);
      this.relativeToDefaultPosition = relativeToDefaultPosition;
      return this;
    }

    /**
     * Sets whether clipping to a non-zero start position in unseekable media is allowed.
     *
     * <p>Note that this is inefficient because the player needs to read and decode all samples from
     * the beginning of the file and it should only be used if the seek start position is small and
     * the entire data before the start position fits into memory.
     *
     * <p>The default value is {@code false}.
     *
     * @param allowUnseekableMedia Whether a non-zero start position in unseekable media is allowed.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setAllowUnseekableMedia(boolean allowUnseekableMedia) {
      checkState(!buildCalled);
      this.allowUnseekableMedia = allowUnseekableMedia;
      return this;
    }

    /** Builds the {@link ClippingMediaSource}. */
    public ClippingMediaSource build() {
      buildCalled = true;
      return new ClippingMediaSource(this);
    }
  }

  /** Thrown when a {@link ClippingMediaSource} cannot clip its wrapped source. */
  public static final class IllegalClippingException extends IOException {

    /**
     * The reason clipping failed. One of {@link #REASON_INVALID_PERIOD_COUNT}, {@link
     * #REASON_NOT_SEEKABLE_TO_START} or {@link #REASON_START_EXCEEDS_END}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({REASON_INVALID_PERIOD_COUNT, REASON_NOT_SEEKABLE_TO_START, REASON_START_EXCEEDS_END})
    public @interface Reason {}

    /** The wrapped source doesn't consist of a single period. */
    public static final int REASON_INVALID_PERIOD_COUNT = 0;

    /** The wrapped source is not seekable and a non-zero clipping start position was specified. */
    public static final int REASON_NOT_SEEKABLE_TO_START = 1;

    /** The wrapped source ends before the specified clipping start position. */
    public static final int REASON_START_EXCEEDS_END = 2;

    /** The reason clipping failed. */
    public final @Reason int reason;

    /**
     * @param reason The reason clipping failed.
     */
    public IllegalClippingException(@Reason int reason) {
      this(reason, /* startUs= */ C.TIME_UNSET, /* endUs= */ C.TIME_UNSET);
    }

    public IllegalClippingException(@Reason int reason, long startUs, long endUs) {
      super("Illegal clipping: " + getReasonDescription(reason, startUs, endUs));
      this.reason = reason;
    }

    private static String getReasonDescription(@Reason int reason, long startUs, long endUs) {
      switch (reason) {
        case REASON_INVALID_PERIOD_COUNT:
          return "invalid period count";
        case REASON_NOT_SEEKABLE_TO_START:
          return "not seekable to start";
        case REASON_START_EXCEEDS_END:
          checkState(startUs != C.TIME_UNSET && endUs != C.TIME_UNSET);
          return "start exceeds end. Start time: " + startUs + ", End time: " + endUs;
        default:
          return "unknown";
      }
    }
  }

  private final long startUs;
  private final long endUs;
  private final boolean enableInitialDiscontinuity;
  private final boolean allowDynamicClippingUpdates;
  private final boolean relativeToDefaultPosition;
  private final boolean allowUnseekableMedia;
  private final ArrayList<ClippingMediaPeriod> mediaPeriods;
  private final Timeline.Window window;

  @Nullable private ClippingTimeline clippingTimeline;
  @Nullable private IllegalClippingException clippingError;
  private long periodStartUs;
  private long periodEndUs;

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs) {
    this(
        new Builder(mediaSource)
            .setStartPositionUs(startPositionUs)
            .setEndPositionUs(endPositionUs));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public ClippingMediaSource(MediaSource mediaSource, long durationUs) {
    this(new Builder(mediaSource).setEndPositionUs(durationUs).setRelativeToDefaultPosition(true));
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public ClippingMediaSource(
      MediaSource mediaSource,
      long startPositionUs,
      long endPositionUs,
      boolean enableInitialDiscontinuity,
      boolean allowDynamicClippingUpdates,
      boolean relativeToDefaultPosition) {
    this(
        new Builder(mediaSource)
            .setStartPositionUs(startPositionUs)
            .setEndPositionUs(endPositionUs)
            .setEnableInitialDiscontinuity(enableInitialDiscontinuity)
            .setAllowDynamicClippingUpdates(allowDynamicClippingUpdates)
            .setRelativeToDefaultPosition(relativeToDefaultPosition));
  }

  private ClippingMediaSource(Builder builder) {
    super(builder.mediaSource);
    this.startUs = builder.startPositionUs;
    this.endUs = builder.endPositionUs;
    this.enableInitialDiscontinuity = builder.enableInitialDiscontinuity;
    this.allowDynamicClippingUpdates = builder.allowDynamicClippingUpdates;
    this.relativeToDefaultPosition = builder.relativeToDefaultPosition;
    this.allowUnseekableMedia = builder.allowUnseekableMedia;
    mediaPeriods = new ArrayList<>();
    window = new Timeline.Window();
  }

  @Override
  public boolean canUpdateMediaItem(MediaItem mediaItem) {
    return getMediaItem().clippingConfiguration.equals(mediaItem.clippingConfiguration)
        && mediaSource.canUpdateMediaItem(mediaItem);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (clippingError != null) {
      throw clippingError;
    }
    super.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    ClippingMediaPeriod mediaPeriod =
        new ClippingMediaPeriod(
            mediaSource.createPeriod(id, allocator, startPositionUs),
            enableInitialDiscontinuity,
            periodStartUs,
            periodEndUs);
    mediaPeriods.add(mediaPeriod);
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    checkState(mediaPeriods.remove(mediaPeriod));
    mediaSource.releasePeriod(((ClippingMediaPeriod) mediaPeriod).mediaPeriod);
    if (mediaPeriods.isEmpty() && !allowDynamicClippingUpdates) {
      refreshClippedTimeline(Assertions.checkNotNull(clippingTimeline).timeline);
    }
  }

  @Override
  protected void releaseSourceInternal() {
    super.releaseSourceInternal();
    clippingError = null;
    clippingTimeline = null;
  }

  @Override
  protected void onChildSourceInfoRefreshed(Timeline newTimeline) {
    if (clippingError != null) {
      return;
    }
    refreshClippedTimeline(newTimeline);
  }

  private void refreshClippedTimeline(Timeline timeline) {
    long windowStartUs;
    long windowEndUs;
    timeline.getWindow(/* windowIndex= */ 0, window);
    long windowPositionInPeriodUs = window.getPositionInFirstPeriodUs();
    if (clippingTimeline == null || mediaPeriods.isEmpty() || allowDynamicClippingUpdates) {
      windowStartUs = startUs;
      windowEndUs = endUs;
      if (relativeToDefaultPosition) {
        long windowDefaultPositionUs = window.getDefaultPositionUs();
        windowStartUs += windowDefaultPositionUs;
        windowEndUs += windowDefaultPositionUs;
      }
      periodStartUs = windowPositionInPeriodUs + windowStartUs;
      periodEndUs =
          endUs == C.TIME_END_OF_SOURCE
              ? C.TIME_END_OF_SOURCE
              : windowPositionInPeriodUs + windowEndUs;
      int count = mediaPeriods.size();
      for (int i = 0; i < count; i++) {
        mediaPeriods.get(i).updateClipping(periodStartUs, periodEndUs);
      }
    } else {
      // Keep window fixed at previous period position.
      windowStartUs = periodStartUs - windowPositionInPeriodUs;
      windowEndUs =
          endUs == C.TIME_END_OF_SOURCE
              ? C.TIME_END_OF_SOURCE
              : periodEndUs - windowPositionInPeriodUs;
    }
    try {
      clippingTimeline =
          new ClippingTimeline(timeline, windowStartUs, windowEndUs, allowUnseekableMedia);
    } catch (IllegalClippingException e) {
      clippingError = e;
      // The clipping error won't be propagated while we have existing MediaPeriods. Setting the
      // error at the MediaPeriods ensures it will be thrown as soon as possible.
      for (int i = 0; i < mediaPeriods.size(); i++) {
        mediaPeriods.get(i).setClippingError(clippingError);
      }
      return;
    }
    refreshSourceInfo(clippingTimeline);
  }

  /** Provides a clipped view of a specified timeline. */
  private static final class ClippingTimeline extends ForwardingTimeline {

    private final long startUs;
    private final long endUs;
    private final long durationUs;
    private final boolean isDynamic;

    /**
     * Creates a new clipping timeline that wraps the specified timeline.
     *
     * @param timeline The timeline to clip.
     * @param startUs The number of microseconds to clip from the start of {@code timeline}.
     * @param endUs The end position in microseconds for the clipped timeline relative to the start
     *     of {@code timeline}, or {@link C#TIME_END_OF_SOURCE} to clip no samples from the end.
     * @param allowUnseekableMedia Whether to allow non-zero start positions in unseekable media.
     * @throws IllegalClippingException If the timeline could not be clipped.
     */
    public ClippingTimeline(
        Timeline timeline, long startUs, long endUs, boolean allowUnseekableMedia)
        throws IllegalClippingException {
      super(timeline);
      if (endUs != C.TIME_END_OF_SOURCE && endUs < startUs) {
        throw new IllegalClippingException(
            IllegalClippingException.REASON_START_EXCEEDS_END, startUs, endUs);
      }
      if (timeline.getPeriodCount() != 1) {
        throw new IllegalClippingException(IllegalClippingException.REASON_INVALID_PERIOD_COUNT);
      }
      Window window = timeline.getWindow(0, new Window());
      startUs = max(0, startUs);
      if (!allowUnseekableMedia && !window.isPlaceholder && startUs != 0 && !window.isSeekable) {
        throw new IllegalClippingException(IllegalClippingException.REASON_NOT_SEEKABLE_TO_START);
      }
      endUs = endUs == C.TIME_END_OF_SOURCE ? window.durationUs : max(0, endUs);
      if (window.durationUs != C.TIME_UNSET) {
        if (endUs > window.durationUs) {
          endUs = window.durationUs;
        }
        if (startUs > endUs) {
          startUs = endUs;
        }
      }
      this.startUs = startUs;
      this.endUs = endUs;
      durationUs = endUs == C.TIME_UNSET ? C.TIME_UNSET : (endUs - startUs);
      isDynamic =
          window.isDynamic
              && (endUs == C.TIME_UNSET
                  || (window.durationUs != C.TIME_UNSET && endUs == window.durationUs));
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      timeline.getWindow(/* windowIndex= */ 0, window, /* defaultPositionProjectionUs= */ 0);
      window.positionInFirstPeriodUs += startUs;
      window.durationUs = durationUs;
      window.isDynamic = isDynamic;
      if (window.defaultPositionUs != C.TIME_UNSET) {
        window.defaultPositionUs = max(window.defaultPositionUs, startUs);
        window.defaultPositionUs =
            endUs == C.TIME_UNSET ? window.defaultPositionUs : min(window.defaultPositionUs, endUs);
        window.defaultPositionUs -= startUs;
      }
      long startMs = Util.usToMs(startUs);
      if (window.presentationStartTimeMs != C.TIME_UNSET) {
        window.presentationStartTimeMs += startMs;
      }
      if (window.windowStartTimeMs != C.TIME_UNSET) {
        window.windowStartTimeMs += startMs;
      }
      return window;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(/* periodIndex= */ 0, period, setIds);
      long positionInClippedWindowUs = period.getPositionInWindowUs() - startUs;
      long periodDurationUs =
          durationUs == C.TIME_UNSET ? C.TIME_UNSET : durationUs - positionInClippedWindowUs;
      return period.set(
          period.id, period.uid, /* windowIndex= */ 0, periodDurationUs, positionInClippedWindowUs);
    }
  }
}
