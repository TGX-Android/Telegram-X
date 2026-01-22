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

package androidx.media3.extractor;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.C;
import androidx.media3.common.util.LongArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * A {@link SeekMap} implementation based on a mapping between times and positions in the input
 * stream.
 */
@UnstableApi
public final class IndexSeekMap implements SeekMap {

  private final LongArray positions;
  private final LongArray timesUs;

  private long durationUs;

  /**
   * Creates an instance.
   *
   * @param positions The positions in the stream corresponding to {@code timesUs}, in bytes.
   * @param timesUs The times corresponding to {@code positions}, in microseconds.
   * @param durationUs The duration of the input stream, or {@link C#TIME_UNSET} if it is unknown.
   */
  public IndexSeekMap(long[] positions, long[] timesUs, long durationUs) {
    checkArgument(positions.length == timesUs.length);
    int length = timesUs.length;
    if (length > 0 && timesUs[0] > 0) {
      // Add (position = 0, timeUs = 0) as first entry.
      this.positions = new LongArray(length + 1);
      this.timesUs = new LongArray(length + 1);
      this.positions.add(0L);
      this.timesUs.add(0L);
    } else {
      this.positions = new LongArray(length);
      this.timesUs = new LongArray(length);
    }
    this.positions.addAll(positions);
    this.timesUs.addAll(timesUs);
    this.durationUs = durationUs;
  }

  @Override
  public boolean isSeekable() {
    return timesUs.size() > 0;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public SeekMap.SeekPoints getSeekPoints(long timeUs) {
    if (timesUs.size() == 0) {
      return new SeekMap.SeekPoints(SeekPoint.START);
    }
    int targetIndex =
        Util.binarySearchFloor(timesUs, timeUs, /* inclusive= */ true, /* stayInBounds= */ true);
    SeekPoint leftSeekPoint = new SeekPoint(timesUs.get(targetIndex), positions.get(targetIndex));
    if (leftSeekPoint.timeUs == timeUs || targetIndex == timesUs.size() - 1) {
      return new SeekMap.SeekPoints(leftSeekPoint);
    } else {
      SeekPoint rightSeekPoint =
          new SeekPoint(timesUs.get(targetIndex + 1), positions.get(targetIndex + 1));
      return new SeekMap.SeekPoints(leftSeekPoint, rightSeekPoint);
    }
  }

  /**
   * Adds a seek point to the index.
   *
   * <p>Seek points must be added in order.
   *
   * @param timeUs The time of the seek point in microseconds.
   * @param position The position in the stream corresponding to the seek point, in bytes.
   */
  public void addSeekPoint(long timeUs, long position) {
    if (timesUs.size() == 0 && timeUs > 0) {
      // Add (position = 0, timeUs = 0) as first entry.
      this.positions.add(0L);
      this.timesUs.add(0L);
    }
    positions.add(position);
    timesUs.add(timeUs);
  }

  /**
   * Maps a position (byte offset) to a corresponding sample timestamp.
   *
   * @param position A seek position (byte offset) relative to the start of the stream.
   * @return The corresponding timestamp of the seek point at or before the given position, in
   *     microseconds, or {@link C#TIME_UNSET} if no seek points exist.
   */
  public long getTimeUs(long position) {
    if (timesUs.size() == 0) {
      return C.TIME_UNSET;
    }
    int targetIndex =
        Util.binarySearchFloor(
            positions, position, /* inclusive= */ true, /* stayInBounds= */ true);
    return timesUs.get(targetIndex);
  }

  /**
   * Returns whether {@code timeUs} (in microseconds) should be considered as part of the index
   * based on its proximity to the last recorded seek point in the index.
   *
   * <p>This method assumes that {@code timeUs} is provided in increasing order, consistent with how
   * points are added to the index in {@link #addSeekPoint(long, long)}.
   *
   * @param timeUs The time in microseconds to check if it is included in the index.
   * @param minTimeBetweenPointsUs The minimum time in microseconds that should exist between points
   *     for the current time to be considered as part of the index.
   */
  public boolean isTimeUsInIndex(long timeUs, long minTimeBetweenPointsUs) {
    if (timesUs.size() == 0) {
      return false;
    }
    return timeUs - timesUs.get(timesUs.size() - 1) < minTimeBetweenPointsUs;
  }

  /** Sets the duration of the input stream, or {@link C#TIME_UNSET} if it is unknown. */
  public void setDurationUs(long durationUs) {
    this.durationUs = durationUs;
  }
}
