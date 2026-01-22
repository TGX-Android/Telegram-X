/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.extractor.mp3;

import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.IndexSeekMap;
import java.math.RoundingMode;

/** MP3 seeker that builds a time-to-byte mapping as the stream is read. */
/* package */ final class IndexSeeker implements Seeker {

  @VisibleForTesting
  /* package */ static final long MIN_TIME_BETWEEN_POINTS_US = C.MICROS_PER_SECOND / 10;

  private final long dataEndPosition;
  private final int averageBitrate;
  private final IndexSeekMap indexSeekMap;

  public IndexSeeker(long durationUs, long dataStartPosition, long dataEndPosition) {
    this.indexSeekMap =
        new IndexSeekMap(
            /* positions= */ new long[] {dataStartPosition},
            /* timesUs= */ new long[] {0L},
            durationUs);
    this.dataEndPosition = dataEndPosition;
    if (durationUs != C.TIME_UNSET) {
      long bitrate =
          Util.scaleLargeValue(
              dataStartPosition - dataEndPosition, 8, durationUs, RoundingMode.HALF_UP);
      this.averageBitrate =
          bitrate > 0 && bitrate <= Integer.MAX_VALUE ? (int) bitrate : C.RATE_UNSET_INT;
    } else {
      this.averageBitrate = C.RATE_UNSET_INT;
    }
  }

  @Override
  public long getTimeUs(long position) {
    return indexSeekMap.getTimeUs(position);
  }

  @Override
  public long getDataEndPosition() {
    return dataEndPosition;
  }

  @Override
  public boolean isSeekable() {
    return indexSeekMap.isSeekable();
  }

  @Override
  public long getDurationUs() {
    return indexSeekMap.getDurationUs();
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    return indexSeekMap.getSeekPoints(timeUs);
  }

  @Override
  public int getAverageBitrate() {
    return averageBitrate;
  }

  /**
   * Adds a seek point to the index if it is sufficiently distant from the other points.
   *
   * <p>Seek points must be added in order.
   *
   * @param timeUs The time corresponding to the seek point to add in microseconds.
   * @param position The position corresponding to the seek point to add in bytes.
   */
  public void maybeAddSeekPoint(long timeUs, long position) {
    if (isTimeUsInIndex(timeUs)) {
      return;
    }
    indexSeekMap.addSeekPoint(timeUs, position);
  }

  /**
   * Returns whether {@code timeUs} (in microseconds) should be considered as part of the index
   * based on its proximity to the last recorded seek point in the index.
   *
   * <p>This method assumes that {@code timeUs} is provided in increasing order, consistent with how
   * points are added to the index in {@link #maybeAddSeekPoint(long, long)}.
   *
   * @param timeUs The time in microseconds to check if it is included in the index.
   */
  public boolean isTimeUsInIndex(long timeUs) {
    return indexSeekMap.isTimeUsInIndex(timeUs, MIN_TIME_BETWEEN_POINTS_US);
  }

  /* package */ void setDurationUs(long durationUs) {
    indexSeekMap.setDurationUs(durationUs);
  }
}
