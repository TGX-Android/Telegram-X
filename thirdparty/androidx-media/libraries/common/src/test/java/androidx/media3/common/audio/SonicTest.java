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

import static androidx.media3.common.audio.Sonic.calculateAccumulatedTruncationErrorForResampling;
import static androidx.media3.common.audio.Sonic.getExpectedFrameCountAfterProcessorApplied;
import static androidx.media3.common.audio.Sonic.getExpectedInputFrameCountForOutputFrameCount;
import static androidx.media3.test.utils.TestUtil.getPeriodicSamplesBuffer;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.math.BigDecimal;
import java.nio.ShortBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

/** Unit test for {@link Sonic}. */
@RunWith(AndroidJUnit4.class)
public class SonicTest {

  @Rule public final Timeout globalTimeout = Timeout.millis(1000);

  @Test
  public void resample_toDoubleRate_linearlyInterpolatesSamples() {
    ShortBuffer inputBuffer = ShortBuffer.wrap(new short[] {0, 10, 20, 30, 40, 50});
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ 44100,
            /* channelCount= */ 1,
            /* speed= */ 1,
            /* pitch= */ 1,
            /* outputSampleRateHz= */ 88200);
    sonic.queueInput(inputBuffer);
    sonic.queueEndOfStream();
    ShortBuffer outputBuffer = ShortBuffer.allocate(sonic.getOutputSize() / 2);
    sonic.getOutput(outputBuffer);

    // End of stream is padded with silence, so last sample will be interpolated between (50; 0).
    assertThat(outputBuffer.array())
        .isEqualTo(new short[] {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 25});
  }

  @Test
  public void resample_toHalfRate_linearlyInterpolatesSamples() {
    ShortBuffer inputBuffer =
        ShortBuffer.wrap(new short[] {-40, -30, -20, -10, 0, 10, 20, 30, 40, 50});
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ 44100,
            /* channelCount= */ 1,
            /* speed= */ 1,
            /* pitch= */ 1,
            /* outputSampleRateHz= */ 22050);
    sonic.queueInput(inputBuffer);
    sonic.queueEndOfStream();
    ShortBuffer outputBuffer = ShortBuffer.allocate(sonic.getOutputSize() / 2);
    sonic.getOutput(outputBuffer);

    // TODO (b/361768785): Remove this unexpected last sample when Sonic's resampler returns the
    //  right number of samples.
    assertThat(outputBuffer.array()).isEqualTo(new short[] {-40, -20, 0, 20, 40, 0});
  }

  @Test
  public void resample_withOneSample_doesNotHang() {
    ShortBuffer inputBuffer = ShortBuffer.wrap(new short[] {10});
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ 44100,
            /* channelCount= */ 1,
            /* speed= */ 1,
            /* pitch= */ 1,
            /* outputSampleRateHz= */ 88200);
    sonic.queueInput(inputBuffer);
    sonic.queueEndOfStream();
    ShortBuffer outputBuffer = ShortBuffer.allocate(sonic.getOutputSize() / 2);
    sonic.getOutput(outputBuffer);

    // End of stream is padded with silence, so last sample will be interpolated between (10; 0).
    assertThat(outputBuffer.array()).isEqualTo(new short[] {10, 5});
  }

  @Test
  public void resample_withFractionalOutputSampleCount_roundsNumberOfOutputSamples() {
    ShortBuffer inputBuffer = ShortBuffer.wrap(new short[] {0, 2, 4, 6, 8});
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ 44100,
            /* channelCount= */ 1,
            /* speed= */ 1,
            /* pitch= */ 1,
            /* outputSampleRateHz= */ 22050);
    sonic.queueInput(inputBuffer);
    sonic.queueEndOfStream();
    ShortBuffer outputBuffer = ShortBuffer.allocate(sonic.getOutputSize() / 2);
    sonic.getOutput(outputBuffer);

    assertThat(outputBuffer.array()).isEqualTo(new short[] {0, 4, 8});
  }

  @Test
  public void queueEndOfStream_withOutputCountUnderflow_setsNonNegativeOutputSize() {
    // For speed ranges [0.5; 1) and (1; 1.5], Sonic might need to copy more input frames onto its
    // output buffer than are available in the input buffer. Sonic keeps track of this "borrowed
    // frames" number in #remainingInputToCopyFrameCount. When we call #queueEndOfStream(), then
    // Sonic outputs a final number of frames based roughly on pendingOutputFrameCount +
    // (inputFrameCount - remainingInputToCopyFrameCount) / speed + remainingInputToCopyFrameCount,
    // which could result in a negative number if inputFrameCount < remainingInputToCopyFrameCount
    // and 0.5 <= speed < 1. #getOutputSize() should still always return a non-negative number.
    ShortBuffer inputBuffer =
        getPeriodicSamplesBuffer(/* sampleCount= */ 1700, /* period= */ 192).asShortBuffer();
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ 48000,
            /* channelCount= */ 1,
            /* speed= */ 0.95f,
            /* pitch= */ 1,
            /* outputSampleRateHz= */ 48000);

    sonic.queueInput(inputBuffer);
    ShortBuffer outputBuffer = ShortBuffer.allocate(sonic.getOutputSize() / 2);
    // Drain output, so that pending output frame count is 0.
    sonic.getOutput(outputBuffer);
    assertThat(sonic.getOutputSize()).isEqualTo(0);
    // Queue EOS with empty pending input and output.
    sonic.queueEndOfStream();

    assertThat(sonic.getOutputSize()).isEqualTo(0);
  }

  @Test
  public void queueEndOfStream_withNoInput_setsNonNegativeOutputSize() {
    Sonic sonic =
        new Sonic(
            /* inputSampleRateHz= */ 48000,
            /* channelCount= */ 1,
            /* speed= */ 0.95f,
            /* pitch= */ 1,
            /* outputSampleRateHz= */ 48000);
    ShortBuffer outputBuffer = ShortBuffer.allocate(sonic.getOutputSize() / 2);

    sonic.getOutput(outputBuffer);
    sonic.queueEndOfStream();

    assertThat(sonic.getOutputSize()).isAtLeast(0);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_timeStretchingFaster_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 44100,
            /* outputSampleRateHz= */ 44100,
            /* speed= */ 2,
            /* pitch= */ 1,
            /* inputFrameCount= */ 88200);
    assertThat(samples).isEqualTo(44100);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_timeStretchingSlower_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 44100,
            /* outputSampleRateHz= */ 44100,
            /* speed= */ 0.5f,
            /* pitch= */ 1,
            /* inputFrameCount= */ 88200);
    assertThat(samples).isEqualTo(176400);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_resamplingHigherSampleRate_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 44100,
            /* outputSampleRateHz= */ 88200,
            /* speed= */ 1f,
            /* pitch= */ 1,
            /* inputFrameCount= */ 88200);
    assertThat(samples).isEqualTo(176400);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_resamplingLowerSampleRate_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 44100,
            /* outputSampleRateHz= */ 22050,
            /* speed= */ 1f,
            /* pitch= */ 1,
            /* inputFrameCount= */ 88200);
    assertThat(samples).isEqualTo(44100);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_resamplingLowerPitch_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 44100,
            /* outputSampleRateHz= */ 44100,
            /* speed= */ 0.5f,
            /* pitch= */ 0.5f,
            /* inputFrameCount= */ 88200);
    assertThat(samples).isEqualTo(176400);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_resamplingHigherPitch_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 44100,
            /* outputSampleRateHz= */ 44100,
            /* speed= */ 2f,
            /* pitch= */ 2f,
            /* inputFrameCount= */ 88200);
    assertThat(samples).isEqualTo(44100);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_resamplePitchAndSampleRateChange_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 44100,
            /* outputSampleRateHz= */ 88200,
            /* speed= */ 1f,
            /* pitch= */ 2f,
            /* inputFrameCount= */ 88200);
    // First time stretch at speed / pitch = 0.5.
    // Then resample at (inputSampleRateHz / outputSampleRateHz) * pitch = 0.5 * 2.
    // Final sample count is 88200 / 0.5 / (0.5 * 2) = 176400.
    assertThat(samples).isEqualTo(176400);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_pitchSpeedAndSampleRateChange_returnsExpectedSampleCount() {
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 48000,
            /* outputSampleRateHz= */ 192000,
            /* speed= */ 5f,
            /* pitch= */ 0.5f,
            /* inputFrameCount= */ 88200);
    // First time stretch at speed / pitch = 10.
    // Then resample at (inputSampleRateHz / outputSampleRateHz) * pitch = 0.25 * 0.5.
    // Final sample count is 88200 / 10 / (0.25 * 0.5) = 176400.
    assertThat(samples).isEqualTo(70560);
  }

  @Test
  public void
      getExpectedFrameCountAfterProcessorApplied_withPeriodicResamplingRate_adjustsForTruncationError() {
    long length = 26902000;
    float resamplingRate = 0.33f;
    long samples =
        getExpectedFrameCountAfterProcessorApplied(
            /* inputSampleRateHz= */ 48000,
            /* outputSampleRateHz= */ 48000,
            /* speed= */ resamplingRate,
            /* pitch= */ resamplingRate,
            /* inputFrameCount= */ length);

    long truncationError =
        calculateAccumulatedTruncationErrorForResampling(
            BigDecimal.valueOf(length),
            BigDecimal.valueOf(48000),
            new BigDecimal(String.valueOf(resamplingRate)));
    // Sonic incurs on accumulated truncation errors when the input sample rate is not exactly
    // divisible by the resampling rate (pitch * inputSampleRateHz / outputSampleRateHz). This error
    // is more prominent on larger stream lengths and inputSampleRateHz + resamplingRate
    // combinations that result in higher truncated decimal values.
    assertThat(samples).isEqualTo(81521212 - truncationError);
  }

  @Test
  public void calculateAccumulatedTruncationErrorForResampling_returnsExpectedSampleCount() {
    long error =
        calculateAccumulatedTruncationErrorForResampling(
            /* length= */ BigDecimal.valueOf(26902000),
            /* sampleRate= */ BigDecimal.valueOf(48000),
            /* resamplingRate= */ new BigDecimal(String.valueOf(0.33f)));

    // Individual error = fractional part of (sampleRate / resamplingRate) = 0.54 (periodic)
    // Error count = length / sampleRate = 560.4583.
    // Accumulated error = error count * individual error = 560.4583 * 0.54 = 305.
    // (All calculations are done on BigDecimal rounded to 20 decimal places, unless indicated).
    assertThat(error).isEqualTo(305);
  }

  @Test
  public void getExpectedInputFrameCountForOutputFrameCount_fasterSpeed_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 48_000,
            /* speed= */ 5,
            /* pitch= */ 1,
            /* outputFrameCount= */ 20);
    assertThat(inputSamples).isEqualTo(100);
  }

  @Test
  public void
      getExpectedInputFrameCountForOutputFrameCount_fasterSpeedAndPitch_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 48_000,
            /* speed= */ 5,
            /* pitch= */ 5,
            /* outputFrameCount= */ 20);
    assertThat(inputSamples).isEqualTo(100);
  }

  @Test
  public void getExpectedInputFrameCountForOutputFrameCount_higherPitch_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 48_000,
            /* speed= */ 1,
            /* pitch= */ 5,
            /* outputFrameCount= */ 20);
    assertThat(inputSamples).isEqualTo(20);
  }

  @Test
  public void getExpectedInputFrameCountForOutputFrameCount_slowerSpeed_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 48_000,
            /* speed= */ 0.25f,
            /* pitch= */ 1,
            /* outputFrameCount= */ 100);
    assertThat(inputSamples).isEqualTo(25);
  }

  @Test
  public void
      getExpectedInputFrameCountForOutputFrameCount_slowerSpeedAndPitch_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 48_000,
            /* speed= */ 0.25f,
            /* pitch= */ 0.25f,
            /* outputFrameCount= */ 100);
    assertThat(inputSamples).isEqualTo(25);
  }

  @Test
  public void getExpectedInputFrameCountForOutputFrameCount_lowerPitch_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 48_000,
            /* speed= */ 1,
            /* pitch= */ 0.75f,
            /* outputFrameCount= */ 100);
    assertThat(inputSamples).isEqualTo(100);
  }

  @Test
  public void
      getExpectedInputFrameCountForOutputFrameCount_differentSamplingRates_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 96_000,
            /* speed= */ 1,
            /* pitch= */ 1,
            /* outputFrameCount= */ 100);
    assertThat(inputSamples).isEqualTo(50);
  }

  @Test
  public void
      getExpectedInputFrameCountForOutputFrameCount_differentPitchSpeedAndSamplingRates_returnsExpectedCount() {
    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 96_000,
            /* speed= */ 5,
            /* pitch= */ 2,
            /* outputFrameCount= */ 40);
    assertThat(inputSamples).isEqualTo(100);
  }

  @Test
  public void
      getExpectedInputFrameCountForOutputFrameCount_withPeriodicResamplingRate_adjustsForTruncationError() {
    float resamplingRate = 0.33f;
    long outputLength = 81_521_212;
    long truncationError = 305;

    long inputSamples =
        getExpectedInputFrameCountForOutputFrameCount(
            /* inputSampleRateHz= */ 48_000,
            /* outputSampleRateHz= */ 48_000,
            /* speed= */ resamplingRate,
            /* pitch= */ resamplingRate,
            /* outputFrameCount= */ outputLength - truncationError);

    assertThat(inputSamples).isEqualTo(26_902_000);
  }
}
