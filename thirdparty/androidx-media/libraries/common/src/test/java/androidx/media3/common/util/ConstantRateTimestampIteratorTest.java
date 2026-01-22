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

import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.round;

import androidx.media3.common.C;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ConstantRateTimestampIterator}. */
@RunWith(AndroidJUnit4.class)
public class ConstantRateTimestampIteratorTest {

  @Test
  public void timestampIterator_validArguments_generatesCorrectTimestamps() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator(C.MICROS_PER_SECOND, /* frameRate= */ 2);

    assertThat(generateList(constantRateTimestampIterator))
        .containsExactly(0L, C.MICROS_PER_SECOND / 2);
    assertThat(constantRateTimestampIterator.getLastTimestampUs())
        .isEqualTo(C.MICROS_PER_SECOND / 2);
  }

  @Test
  public void timestampIterator_realisticArguments_generatesCorrectNumberOfTimestamps() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator((long) (2.5 * C.MICROS_PER_SECOND), /* frameRate= */ 30);

    assertThat(generateList(constantRateTimestampIterator)).hasSize(75);
    assertThat(constantRateTimestampIterator.getLastTimestampUs())
        .isEqualTo(round((C.MICROS_PER_SECOND / 30.d) * 74));
  }

  @Test
  public void timestampIterator_realisticArguments_generatesTimestampsInStrictOrder() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator((long) (2.5 * C.MICROS_PER_SECOND), /* frameRate= */ 30);

    assertThat(generateList(constantRateTimestampIterator)).isInStrictOrder();
  }

  @Test
  public void timestampIterator_realisticArguments_doesNotGenerateDuplicates() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator((long) (2.5 * C.MICROS_PER_SECOND), /* frameRate= */ 30);

    assertThat(generateList(constantRateTimestampIterator)).containsNoDuplicates();
  }

  @Test
  public void timestampIterator_smallDuration_generatesOneTimestamp() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator(/* durationUs= */ 1, /* frameRate= */ 2);

    assertThat(generateList(constantRateTimestampIterator)).containsExactly(0L);
    assertThat(constantRateTimestampIterator.getLastTimestampUs()).isEqualTo(0L);
  }

  @Test
  public void timestampIterator_withStartTimeAndEvenFrameRate_generatesCorrectTimestamps() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator(
            /* startPositionUs= */ 500_000L, C.MICROS_PER_SECOND, /* frameRate= */ 2);

    assertThat(generateList(constantRateTimestampIterator)).containsExactly(500_000L);
    assertThat(constantRateTimestampIterator.getLastTimestampUs()).isEqualTo(500_000L);
  }

  @Test
  public void timestampIterator_withStartTimeAndOddFrameRate_generatesCorrectTimestamps() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator(
            /* startPositionUs= */ 500_000L, C.MICROS_PER_SECOND, /* frameRate= */ 3);

    assertThat(generateList(constantRateTimestampIterator))
        .containsExactly(500_000L, 833_333L)
        .inOrder();
    assertThat(constantRateTimestampIterator.getLastTimestampUs()).isEqualTo(833_333L);
  }

  @Test
  public void timestampIterator_withZeroStartTime_generatesCorrectTimestamps() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator(
            /* startPositionUs= */ 0L,
            /* endPositionUs= */ C.MICROS_PER_SECOND,
            /* frameRate= */ 3);

    assertThat(generateList(constantRateTimestampIterator)).containsExactly(0L, 333_333L, 666_667L);
    assertThat(constantRateTimestampIterator.getLastTimestampUs()).isEqualTo(666_667L);
  }

  @Test
  public void timestampIterator_withNoTimestampsWithinParameters_generatesOneTimestamp() {
    ConstantRateTimestampIterator constantRateTimestampIterator =
        new ConstantRateTimestampIterator(
            /* startPositionUs= */ 900_000L,
            /* endPositionUs= */ C.MICROS_PER_SECOND,
            /* frameRate= */ 3);

    assertThat(generateList(constantRateTimestampIterator)).containsExactly(900_000L);
    assertThat(constantRateTimestampIterator.getLastTimestampUs()).isEqualTo(900_000L);
  }

  private static List<Long> generateList(TimestampIterator iterator) {
    ArrayList<Long> list = new ArrayList<>();

    while (iterator.hasNext()) {
      list.add(iterator.next());
    }
    return list;
  }
}
