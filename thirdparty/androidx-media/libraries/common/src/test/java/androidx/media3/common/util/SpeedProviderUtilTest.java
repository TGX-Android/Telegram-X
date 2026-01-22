/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static androidx.media3.common.util.SpeedProviderUtil.getDurationAfterSpeedProviderApplied;
import static androidx.media3.common.util.SpeedProviderUtil.getNextSpeedChangeSamplePosition;
import static androidx.media3.common.util.SpeedProviderUtil.getSampleAlignedSpeed;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SpeedProviderUtil}. */
@RunWith(AndroidJUnit4.class)
public class SpeedProviderUtilTest {

  @Test
  public void getDurationAfterProcessorApplied_returnsCorrectDuration() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 120}, /* speeds= */ new float[] {3, 0.5f});

    assertThat(getDurationAfterSpeedProviderApplied(speedProvider, /* durationUs= */ 150))
        .isEqualTo(100);
  }

  @Test
  public void getDurationAfterProcessorApplied_durationOnSpeedChange_returnsCorrectDuration()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 113}, /* speeds= */ new float[] {2, 1});

    assertThat(getDurationAfterSpeedProviderApplied(speedProvider, /* durationUs= */ 113))
        .isEqualTo(56);
  }

  @Test
  public void getSampleAlignedSpeed_withExactBoundaries_returnsExpectedSpeed() {
    // 50Khz = 20us period.
    int sampleRate = 50000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 20, 40, 60}, /* speeds= */ new float[] {2, 1, 5, 8});

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 0, sampleRate))
        .isEqualTo(2f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 1, sampleRate))
        .isEqualTo(1f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 2, sampleRate))
        .isEqualTo(5f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 3, sampleRate))
        .isEqualTo(8f);
  }

  @Test
  public void getSampleAlignedSpeed_beyondLastSpeedChange_returnsLastSetSpeed() {
    // 48Khz = 20.83us period.
    int sampleRate = 48000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 20}, /* speeds= */ new float[] {2f, 0.5f});

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 2, sampleRate))
        .isEqualTo(0.5f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 500, sampleRate))
        .isEqualTo(0.5f);
  }

  @Test
  public void getSampleAlignedSpeed_withNonAlignedBoundaries_returnsAlignedSpeed() {
    // 48Khz = 20.83us period.
    int sampleRate = 48000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 35, 62}, /* speeds= */ new float[] {2, 8, 10});

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 0, sampleRate))
        .isEqualTo(2f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 1, sampleRate))
        .isEqualTo(2f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 2, sampleRate))
        .isEqualTo(8f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 3, sampleRate))
        .isEqualTo(10f);
  }

  @Test
  public void
      getSampleAlignedSpeed_withMultipleBoundariesBetweenSamples_ignoresIntermediateChanges() {
    // 48Khz = 20.83us period.
    int sampleRate = 48000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 20, 25, 30, 35, 40},
            /* speeds= */ new float[] {2, 0.5f, 20, 5, 3, 9});

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 0, sampleRate))
        .isEqualTo(2f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 1, sampleRate))
        .isEqualTo(0.5f);

    assertThat(getSampleAlignedSpeed(speedProvider, /* samplePosition= */ 2, sampleRate))
        .isEqualTo(9f);
  }

  @Test
  public void getNextSpeedChangeSamplePosition_withExactBoundaries_returnsExpectedPositions() {
    // 50Khz = 20us period.
    int sampleRate = 50000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 20, 40, 60}, /* speeds= */ new float[] {2, 1, 5, 8});

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 0, sampleRate))
        .isEqualTo(1);

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 1, sampleRate))
        .isEqualTo(2);

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 2, sampleRate))
        .isEqualTo(3);
  }

  @Test
  public void getNextSpeedChangeSamplePosition_beyondLastChange_returnsIndexUnset() {
    // 48Khz = 20.83us period.
    int sampleRate = 48000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 20, 41, 62}, /* speeds= */ new float[] {2, 1, 5, 8});

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 3, sampleRate))
        .isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void getNextSpeedChangeSamplePosition_withNonAlignedBoundaries_returnsNextClosestSample() {
    // 48Khz = 20.83us period.
    int sampleRate = 48000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 50}, /* speeds= */ new float[] {2, 1});

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 0, sampleRate))
        .isEqualTo(3);
  }

  @Test
  public void
      getNextSpeedChangeSamplePosition_withMultipleBoundariesBetweenSamples_ignoresIntermediateChanges() {
    // 48Khz = 20.83us period.
    int sampleRate = 48000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 45, 55, 58},
            /* speeds= */ new float[] {2, 3, 0.1f, 9});

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 0, sampleRate))
        .isEqualTo(3);

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 3, sampleRate))
        .isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void
      getNextSpeedChangeSamplePosition_withChangeOneUsAfterBoundary_returnsNextClosestSample() {
    // 48Khz = 20.8us period.
    int sampleRate = 48000;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 63}, /* speeds= */ new float[] {2, 3});

    assertThat(getNextSpeedChangeSamplePosition(speedProvider, /* samplePosition= */ 0, sampleRate))
        .isEqualTo(4);
  }
}
