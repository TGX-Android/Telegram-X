/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.common.util;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.round;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.media3.common.C;

/**
 * A {@link TimestampIterator} that generates monotonically increasing timestamps (in microseconds)
 * distributed evenly over the given {@code durationUs} based on the given {@code frameRate}.
 */
@UnstableApi
public final class ConstantRateTimestampIterator implements TimestampIterator {

  private final float frameRate;
  private final double framesDurationUs;
  private final int totalNumberOfFramesToAdd;
  private final long startPositionUs;
  private final long endPositionUs;

  private int framesAdded;

  /**
   * Creates an instance that outputs timestamps from {@code 0}.
   *
   * @param durationUs The duration the timestamps should span over, in microseconds.
   * @param frameRate The frame rate in frames per second.
   */
  public ConstantRateTimestampIterator(
      @IntRange(from = 1) long durationUs,
      @FloatRange(from = 0, fromInclusive = false) float frameRate) {
    this(/* startPositionUs= */ 0, /* endPositionUs= */ durationUs, frameRate);
  }

  /**
   * Creates an instance that outputs timestamps from {@code startTimeUs}.
   *
   * @param startPositionUs The start position in microseconds. The first timestamp generated will
   *     be equal to {@code startPositionUs}.
   * @param endPositionUs The end position at which the timestamps finish, in microseconds. The
   *     generated timestamps are less or equal to the end position.
   * @param frameRate The frame rate in frames per second.
   */
  public ConstantRateTimestampIterator(
      @IntRange(from = 0) long startPositionUs,
      @IntRange(from = 1) long endPositionUs,
      @FloatRange(from = 0, fromInclusive = false) float frameRate) {
    checkArgument(endPositionUs > 0);
    checkArgument(frameRate > 0);
    checkArgument(0 <= startPositionUs && startPositionUs < endPositionUs);
    this.startPositionUs = startPositionUs;
    this.endPositionUs = endPositionUs;
    this.frameRate = frameRate;
    float durationSecs = (endPositionUs - startPositionUs) / (float) C.MICROS_PER_SECOND;
    // Generate at least one timestamp so that at least one frame is produced when seeking.
    this.totalNumberOfFramesToAdd = max(round(frameRate * durationSecs), 1);
    framesDurationUs = C.MICROS_PER_SECOND / frameRate;
  }

  @Override
  public boolean hasNext() {
    return framesAdded < totalNumberOfFramesToAdd;
  }

  @Override
  public long next() {
    checkState(hasNext());
    return getTimestampUsAfter(framesAdded++);
  }

  @Override
  public ConstantRateTimestampIterator copyOf() {
    return new ConstantRateTimestampIterator(startPositionUs, endPositionUs, frameRate);
  }

  @Override
  public long getLastTimestampUs() {
    if (totalNumberOfFramesToAdd == 0) {
      return C.TIME_UNSET;
    }
    return getTimestampUsAfter(totalNumberOfFramesToAdd - 1);
  }

  /** Returns the timestamp after {@code numberOfFrames}, in microseconds. */
  private long getTimestampUsAfter(int numberOfFrames) {
    long timestampUs = startPositionUs + round(framesDurationUs * numberOfFrames);
    // Check for possible overflow.
    checkState(timestampUs >= 0);
    return timestampUs;
  }
}
