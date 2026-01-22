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
package androidx.media3.common.audio;

import static androidx.media3.common.audio.SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied;
import static androidx.media3.test.utils.TestUtil.buildTestData;
import static androidx.media3.test.utils.TestUtil.generateFloatInRange;
import static androidx.media3.test.utils.TestUtil.generateLong;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.test.utils.TestSpeedProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;

/** Parameterized Robolectric test for {@link SpeedChangingAudioProcessor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class RandomParameterizedSpeedChangingAudioProcessorTest {
  private static final AudioFormat AUDIO_FORMAT =
      new AudioFormat(/* sampleRate= */ 48000, /* channelCount= */ 1, C.ENCODING_PCM_16BIT);
  private static final int ITERATION_COUNT = 25;
  private static final int MAX_SPEED_COUNT = 10;
  private static final int SPEED_DECIMAL_PRECISION = 2;
  private static final int MAX_FRAME_COUNT = 96000; // 2 mins.
  private static final int BUFFER_SIZE = 8092;

  private static final ImmutableList<Range<Float>> SPEED_RANGES =
      ImmutableList.of(
          Range.closedOpen(0.01f, 0.5f),
          Range.closedOpen(0.5f, 1f),
          Range.closedOpen(1f, 2f),
          Range.closedOpen(2f, 20f));

  private static final Random random = new Random(/* seed */ 0);

  private static final ImmutableList<Object[]> params = initParams();

  @ParameterizedRobolectricTestRunner.Parameters(name = "speeds={0}, frameDurations={1}")
  public static ImmutableList<Object[]> params() {
    // params() is called multiple times, so return cached parameters to avoid regenerating
    // different random parameter values.
    return params;
  }

  private static ImmutableList<Object[]> initParams() {
    ImmutableList.Builder<Object[]> paramsBuilder = new ImmutableList.Builder<>();

    for (int i = 0; i < ITERATION_COUNT; i++) {
      int changeCount =
          (int) generateLong(random, /* origin= */ 2, /* bound= */ MAX_SPEED_COUNT + 1);
      ImmutableList.Builder<BigDecimal> speeds = new ImmutableList.Builder<>();
      ImmutableList.Builder<Integer> frameCounts = new ImmutableList.Builder<>();

      for (int j = 0; j < changeCount; j++) {
        Range<Float> r = SPEED_RANGES.get(j % SPEED_RANGES.size());
        float speed = generateFloatInRange(random, r);
        speeds.add(
            BigDecimal.valueOf(speed).setScale(SPEED_DECIMAL_PRECISION, RoundingMode.HALF_EVEN));
        frameCounts.add((int) generateLong(random, /* origin= */ 1, /* bound= */ MAX_FRAME_COUNT));
      }

      paramsBuilder.add(new Object[] {speeds.build(), frameCounts.build()});
    }

    return paramsBuilder.build();
  }

  @Parameter(0)
  public List<BigDecimal> speeds;

  @Parameter(1)
  public List<Integer> frameCounts;

  @Test
  public void process_withResampling_outputsExpectedFrameCount()
      throws AudioProcessor.UnhandledAudioFormatException {
    ByteBuffer inputBuffer =
        ByteBuffer.wrap(
            buildTestData(/* length= */ BUFFER_SIZE * AUDIO_FORMAT.bytesPerFrame, random));
    ByteBuffer outBuffer;
    long outputFrameCount = 0;
    long totalInputFrameCount = 0;

    for (int i = 0; i < frameCounts.size(); i++) {
      totalInputFrameCount += frameCounts.get(i);
    }

    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT,
            /* frameCounts= */ Ints.toArray(frameCounts),
            /* speeds= */ Floats.toArray(speeds));

    long expectedOutputFrames =
        getSampleCountAfterProcessorApplied(
            speedProvider, AUDIO_FORMAT.sampleRate, totalInputFrameCount);

    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    speedChangingAudioProcessor.configure(AUDIO_FORMAT);
    speedChangingAudioProcessor.flush();

    while (totalInputFrameCount > 0) {
      // To input exact number of bytes, set limit to input buffer.
      if (totalInputFrameCount < BUFFER_SIZE) {
        inputBuffer.limit((int) totalInputFrameCount * AUDIO_FORMAT.bytesPerFrame);
      }

      speedChangingAudioProcessor.queueInput(inputBuffer);
      totalInputFrameCount -= inputBuffer.position() / AUDIO_FORMAT.bytesPerFrame;

      outBuffer = speedChangingAudioProcessor.getOutput();
      outputFrameCount += outBuffer.remaining() / AUDIO_FORMAT.bytesPerFrame;

      inputBuffer.rewind();
    }
    speedChangingAudioProcessor.queueEndOfStream();
    outBuffer = speedChangingAudioProcessor.getOutput();
    outputFrameCount += outBuffer.remaining() / AUDIO_FORMAT.bytesPerFrame;

    // We allow 1 frame of tolerance per speed change.
    assertThat(outputFrameCount).isWithin(frameCounts.size()).of(expectedOutputFrames);
  }
}
