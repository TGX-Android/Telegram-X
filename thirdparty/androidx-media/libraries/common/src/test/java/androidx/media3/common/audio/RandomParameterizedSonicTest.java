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
package androidx.media3.common.audio;

import static androidx.media3.test.utils.TestUtil.generateFloatInRange;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.max;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Parameterized robolectric test for {@link Sonic}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class RandomParameterizedSonicTest {

  private static final int BLOCK_SIZE = 4096;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int SAMPLE_RATE = 48000;
  // Max 10 min streams.
  private static final long MAX_LENGTH_SAMPLES = 10 * 60 * SAMPLE_RATE;

  /** Defines how many random instances of each parameter the test runner should generate. */
  private static final int PARAM_COUNT = 5;

  private static final int SPEED_DECIMAL_PRECISION = 2;

  /**
   * Allowed error tolerance ratio for number of output samples for Sonic's time stretching
   * algorithm.
   *
   * <p>The actual tolerance is calculated as {@code expectedOutputSampleCount /
   * TIME_STRETCHING_SAMPLE_DRIFT_TOLERANCE}, rounded to the nearest integer value. However, we
   * always allow a minimum tolerance of ±1 samples.
   *
   * <p>This tolerance is roughly equal to an error of 900us/~44 samples/0.000017% for a 90 min mono
   * stream @48KHz. To obtain the value, we ran 100 iterations of {@link
   * #timeStretching_returnsExpectedNumberOfSamples()} (by setting {@link #PARAM_COUNT} to 10) and
   * we calculated the average delta percentage between expected number of samples and actual number
   * of samples (b/366169590).
   */
  private static final BigDecimal TIME_STRETCHING_SAMPLE_DRIFT_TOLERANCE =
      new BigDecimal("0.00000017");

  private static final ImmutableList<Range<Float>> SPEED_RANGES =
      ImmutableList.of(
          Range.closedOpen(0f, 0.5f),
          Range.closedOpen(0.5f, 1f),
          Range.closedOpen(1f, 2f),
          Range.closedOpen(2f, 20f));

  private static final Random random = new Random(/* seed */ 0);

  private static final ImmutableList<Object[]> sParams = initParams();

  @Parameters(name = "speed={0}, streamLength={1}")
  public static ImmutableList<Object[]> params() {
    // params() is called multiple times, so return cached parameters to avoid regenerating
    // different random parameter values.
    return sParams;
  }

  /**
   * Returns a list of random parameter combinations with which to run the tests in this class.
   *
   * <p>Each list item contains a value for {{@link #speed}, {@link #streamLength}} stored within an
   * Object array.
   *
   * <p>The method generates {@link #PARAM_COUNT} random {@link #speed} values and {@link
   * #PARAM_COUNT} random {@link #streamLength} values. These generated values are then grouped into
   * all possible combinations, and every group passed as parameters for each test.
   */
  private static ImmutableList<Object[]> initParams() {
    ImmutableSet.Builder<Object[]> paramsBuilder = new ImmutableSet.Builder<>();
    ImmutableSet.Builder<BigDecimal> speedsBuilder = new ImmutableSet.Builder<>();

    for (int i = 0; i < PARAM_COUNT; i++) {
      Range<Float> range = SPEED_RANGES.get(i % SPEED_RANGES.size());
      BigDecimal speed =
          BigDecimal.valueOf(generateFloatInRange(random, range))
              .setScale(SPEED_DECIMAL_PRECISION, RoundingMode.HALF_EVEN);
      speedsBuilder.add(speed);
    }
    ImmutableSet<BigDecimal> speeds = speedsBuilder.build();

    ImmutableSet<Long> lengths =
        new ImmutableSet.Builder<Long>()
            .addAll(
                random
                    .longs(/* min */ 0, MAX_LENGTH_SAMPLES)
                    .distinct()
                    .limit(PARAM_COUNT)
                    .iterator())
            .build();
    for (long length : lengths) {
      for (BigDecimal speed : speeds) {
        paramsBuilder.add(new Object[] {speed, length});
      }
    }
    return paramsBuilder.build().asList();
  }

  @Parameter(0)
  public BigDecimal speed;

  @Parameter(1)
  public long streamLength;

  @Test
  public void resampling_returnsExpectedNumberOfSamples() {
    byte[] inputBuffer = new byte[BLOCK_SIZE * BYTES_PER_SAMPLE];
    ShortBuffer outBuffer = ShortBuffer.allocate(BLOCK_SIZE);
    // Use same speed and pitch values for Sonic to resample stream.
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ SAMPLE_RATE,
            /* channelCount= */ 1,
            /* speed= */ speed.floatValue(),
            /* pitch= */ speed.floatValue(),
            /* outputSampleRateHz= */ SAMPLE_RATE);
    long readSampleCount = 0;

    for (long samplesLeft = streamLength; samplesLeft > 0; samplesLeft -= BLOCK_SIZE) {
      random.nextBytes(inputBuffer);
      if (samplesLeft >= BLOCK_SIZE) {
        sonic.queueInput(ByteBuffer.wrap(inputBuffer).asShortBuffer());
      } else {
        // The last buffer to queue might have less samples than BLOCK_SIZE, so we should only queue
        // the remaining number of samples (samplesLeft).
        sonic.queueInput(
            ByteBuffer.wrap(inputBuffer, 0, (int) (samplesLeft * BYTES_PER_SAMPLE))
                .asShortBuffer());
        sonic.queueEndOfStream();
      }
      while (sonic.getOutputSize() > 0) {
        sonic.getOutput(outBuffer);
        readSampleCount += outBuffer.position();
        outBuffer.clear();
      }
      assertThat(sonic.getOutputSize()).isAtLeast(0);
    }

    long expectedSamples =
        Sonic.getExpectedFrameCountAfterProcessorApplied(
            SAMPLE_RATE, SAMPLE_RATE, speed.floatValue(), speed.floatValue(), streamLength);
    assertThat(readSampleCount).isWithin(1).of(expectedSamples);
  }

  @Test
  public void timeStretching_returnsExpectedNumberOfSamples() {
    byte[] buf = new byte[BLOCK_SIZE * BYTES_PER_SAMPLE];
    ShortBuffer outBuffer = ShortBuffer.allocate(BLOCK_SIZE);
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ SAMPLE_RATE,
            /* channelCount= */ 1,
            speed.floatValue(),
            /* pitch= */ 1,
            /* outputSampleRateHz= */ SAMPLE_RATE);
    long readSampleCount = 0;

    for (long samplesLeft = streamLength; samplesLeft > 0; samplesLeft -= BLOCK_SIZE) {
      random.nextBytes(buf);
      if (samplesLeft >= BLOCK_SIZE) {
        sonic.queueInput(ByteBuffer.wrap(buf).asShortBuffer());
      } else {
        sonic.queueInput(
            ByteBuffer.wrap(buf, 0, (int) (samplesLeft * BYTES_PER_SAMPLE)).asShortBuffer());
        sonic.queueEndOfStream();
      }
      while (sonic.getOutputSize() > 0) {
        sonic.getOutput(outBuffer);
        readSampleCount += outBuffer.position();
        outBuffer.clear();
      }
      assertThat(sonic.getOutputSize()).isAtLeast(0);
    }

    long expectedSamples =
        Sonic.getExpectedFrameCountAfterProcessorApplied(
            SAMPLE_RATE, SAMPLE_RATE, speed.floatValue(), 1, streamLength);

    // Calculate allowed tolerance and round to nearest integer.
    BigDecimal allowedTolerance =
        TIME_STRETCHING_SAMPLE_DRIFT_TOLERANCE
            .multiply(BigDecimal.valueOf(expectedSamples))
            .setScale(/* newScale= */ 0, RoundingMode.HALF_EVEN);

    // Always allow at least 1 sample of tolerance.
    long tolerance = max(allowedTolerance.longValue(), 1);
    assertThat(readSampleCount).isWithin(tolerance).of(expectedSamples);
  }
}
