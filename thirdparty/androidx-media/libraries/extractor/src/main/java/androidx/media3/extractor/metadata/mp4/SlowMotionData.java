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
package androidx.media3.extractor.metadata.mp4;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ComparisonChain;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Holds information about the segments of slow motion playback within a track. */
@UnstableApi
public final class SlowMotionData implements Metadata.Entry {

  /** Holds information about a single segment of slow motion playback within a track. */
  public static final class Segment {

    public static final Comparator<Segment> BY_START_THEN_END_THEN_DIVISOR =
        (s1, s2) ->
            ComparisonChain.start()
                .compare(s1.startTimeMs, s2.startTimeMs)
                .compare(s1.endTimeMs, s2.endTimeMs)
                .compare(s1.speedDivisor, s2.speedDivisor)
                .result();

    /** The start time, in milliseconds, of the track segment that is intended to be slow motion. */
    public final long startTimeMs;

    /** The end time, in milliseconds, of the track segment that is intended to be slow motion. */
    public final long endTimeMs;

    /**
     * The speed reduction factor.
     *
     * <p>For example, 4 would mean the segment should be played at a quarter (1/4) of the normal
     * speed.
     */
    public final int speedDivisor;

    /**
     * Creates an instance.
     *
     * @param startTimeMs See {@link #startTimeMs}. Must be less than endTimeMs.
     * @param endTimeMs See {@link #endTimeMs}.
     * @param speedDivisor See {@link #speedDivisor}.
     */
    public Segment(long startTimeMs, long endTimeMs, int speedDivisor) {
      checkArgument(startTimeMs < endTimeMs);
      this.startTimeMs = startTimeMs;
      this.endTimeMs = endTimeMs;
      this.speedDivisor = speedDivisor;
    }

    @Override
    public String toString() {
      return Util.formatInvariant(
          "Segment: startTimeMs=%d, endTimeMs=%d, speedDivisor=%d",
          startTimeMs, endTimeMs, speedDivisor);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Segment segment = (Segment) o;
      return startTimeMs == segment.startTimeMs
          && endTimeMs == segment.endTimeMs
          && speedDivisor == segment.speedDivisor;
    }

    @Override
    public int hashCode() {
      return Objects.hash(startTimeMs, endTimeMs, speedDivisor);
    }
  }

  public final List<Segment> segments;

  /**
   * Creates an instance with a list of {@link Segment}s.
   *
   * <p>The segments must not overlap, that is that the start time of a segment can not be between
   * the start and end time of another segment.
   */
  public SlowMotionData(List<Segment> segments) {
    this.segments = segments;
    checkArgument(!doSegmentsOverlap(segments));
  }

  @Override
  public String toString() {
    return "SlowMotion: segments=" + segments;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SlowMotionData that = (SlowMotionData) o;
    return segments.equals(that.segments);
  }

  @Override
  public int hashCode() {
    return segments.hashCode();
  }

  private static boolean doSegmentsOverlap(List<Segment> segments) {
    if (segments.isEmpty()) {
      return false;
    }
    long previousEndTimeMs = segments.get(0).endTimeMs;
    for (int i = 1; i < segments.size(); i++) {
      if (segments.get(i).startTimeMs < previousEndTimeMs) {
        return true;
      }
      previousEndTimeMs = segments.get(i).endTimeMs;
    }

    return false;
  }
}
