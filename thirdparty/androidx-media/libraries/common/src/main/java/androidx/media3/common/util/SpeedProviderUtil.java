/*
 * Copyright 2024 The Android Open Source Project
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
import static androidx.media3.common.util.Util.durationUsToSampleCount;
import static androidx.media3.common.util.Util.sampleCountToDurationUs;
import static java.lang.Math.floor;
import static java.lang.Math.min;

import androidx.media3.common.C;
import androidx.media3.common.audio.SpeedProvider;

/** Utilities for {@link SpeedProvider}. */
@UnstableApi
public class SpeedProviderUtil {

  private SpeedProviderUtil() {}

  /**
   * Returns the duration of the output when the given {@link SpeedProvider} is applied given an
   * input stream with the given {@code durationUs}.
   */
  public static long getDurationAfterSpeedProviderApplied(
      SpeedProvider speedProvider, long durationUs) {
    long speedChangeTimeUs = 0;
    double outputDurationUs = 0;
    while (speedChangeTimeUs < durationUs) {
      long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(speedChangeTimeUs);
      if (nextSpeedChangeTimeUs == C.TIME_UNSET) {
        nextSpeedChangeTimeUs = Long.MAX_VALUE;
      }
      outputDurationUs +=
          (min(nextSpeedChangeTimeUs, durationUs) - speedChangeTimeUs)
              / (double) speedProvider.getSpeed(speedChangeTimeUs);
      speedChangeTimeUs = nextSpeedChangeTimeUs;
    }
    // Use floor to be consistent with Util#scaleLargeTimestamp().
    return (long) floor(outputDurationUs);
  }

  /**
   * Returns the speed at the specified sample position.
   *
   * <p>This method is consistent with the alignment done by {@link
   * #getNextSpeedChangeSamplePosition}.
   */
  public static float getSampleAlignedSpeed(
      SpeedProvider speedProvider, long samplePosition, int sampleRate) {
    checkArgument(samplePosition >= 0);
    checkArgument(sampleRate > 0);

    long durationUs = sampleCountToDurationUs(samplePosition, sampleRate);
    return speedProvider.getSpeed(durationUs);
  }

  /**
   * Returns the sample position of the next speed change or {@link C#INDEX_UNSET} if none is set.
   *
   * <p>If the next speed change falls between sample boundaries, this method will return the next
   * closest sample position, which ensures that speed regions stay consistent with {@link
   * #getSampleAlignedSpeed}.
   */
  public static long getNextSpeedChangeSamplePosition(
      SpeedProvider speedProvider, long samplePosition, int sampleRate) {
    checkArgument(samplePosition >= 0);
    checkArgument(sampleRate > 0);

    long durationUs = sampleCountToDurationUs(samplePosition, sampleRate);
    long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(durationUs);

    if (nextSpeedChangeTimeUs == C.TIME_UNSET) {
      return C.INDEX_UNSET;
    }

    // Use RoundingMode#UP to return next closest sample if duration falls between samples.
    return durationUsToSampleCount(nextSpeedChangeTimeUs, sampleRate);
  }
}
