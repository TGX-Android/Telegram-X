/*
 * Copyright 2022 The Android Open Source Project
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

import static androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER;
import static androidx.media3.common.audio.SpeedChangingAudioProcessor.getInputFrameCountForOutput;
import static androidx.media3.test.utils.TestUtil.getNonRandomByteBuffer;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.annotation.SuppressLint;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link SpeedChangingAudioProcessor}. */
@RunWith(AndroidJUnit4.class)
public class SpeedChangingAudioProcessorTest {

  private static final AudioFormat AUDIO_FORMAT_44_100HZ =
      new AudioFormat(
          /* sampleRate= */ 44_100, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);

  private static final AudioFormat AUDIO_FORMAT_50_000HZ =
      new AudioFormat(
          /* sampleRate= */ 50_000, /* channelCount= */ 2, /* encoding= */ C.ENCODING_PCM_16BIT);

  @Test
  public void queueInput_noSpeedChange_doesNotOverwriteInput() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);

    inputBuffer.rewind();
    assertThat(inputBuffer)
        .isEqualTo(
            getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame));
  }

  @Test
  public void queueInput_speedChange_doesNotOverwriteInput() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);

    inputBuffer.rewind();
    assertThat(inputBuffer)
        .isEqualTo(
            getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame));
  }

  @Test
  public void queueInput_noSpeedChange_copiesSamples() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    inputBuffer.rewind();
    assertThat(outputBuffer).isEqualTo(inputBuffer);
  }

  @Test
  public void queueInput_speedChange_modifiesSamples() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    inputBuffer.rewind();
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isNotEqualTo(inputBuffer);
  }

  @Test
  public void queueInput_noSpeedChangeAfterSpeedChange_copiesSamples() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {5, 5},
            /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    inputBuffer.rewind();
    assertThat(outputBuffer).isEqualTo(inputBuffer);
  }

  @Test
  public void queueInput_speedChangeAfterNoSpeedChange_producesSameOutputAsSingleSpeedChange()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {5, 5},
            /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    speedChangingAudioProcessor = getConfiguredSpeedChangingAudioProcessor(speedProvider);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer expectedOutputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isEqualTo(expectedOutputBuffer);
  }

  @Test
  public void queueInput_speedChangeAfterSpeedChange_producesSameOutputAsSingleSpeedChange()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {5, 5},
            /* speeds= */ new float[] {3, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    speedChangingAudioProcessor = getConfiguredSpeedChangingAudioProcessor(speedProvider);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer expectedOutputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isEqualTo(expectedOutputBuffer);
  }

  @Test
  public void queueInput_speedChangeBeforeSpeedChange_producesSameOutputAsSingleSpeedChange()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {5, 5},
            /* speeds= */ new float[] {2, 3});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    ByteBuffer outputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);

    speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    speedChangingAudioProcessor = getConfiguredSpeedChangingAudioProcessor(speedProvider);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    ByteBuffer expectedOutputBuffer = getAudioProcessorOutput(speedChangingAudioProcessor);
    assertThat(outputBuffer.hasRemaining()).isTrue();
    assertThat(outputBuffer).isEqualTo(expectedOutputBuffer);
  }

  @Test
  public void queueInput_multipleSpeedsInBufferWithLimitAtFrameBoundary_readsDataUntilSpeedLimit()
      throws Exception {
    long speedChangeTimeUs = 4 * C.MICROS_PER_SECOND / AUDIO_FORMAT_44_100HZ.sampleRate;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0L, speedChangeTimeUs},
            /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);
    int inputBufferLimit = inputBuffer.limit();

    speedChangingAudioProcessor.queueInput(inputBuffer);

    assertThat(inputBuffer.position()).isEqualTo(4 * AUDIO_FORMAT_44_100HZ.bytesPerFrame);
    assertThat(inputBuffer.limit()).isEqualTo(inputBufferLimit);
  }

  @Test
  public void queueInput_multipleSpeedsInBufferWithLimitInsideFrame_readsDataUntilSpeedLimit()
      throws Exception {
    long speedChangeTimeUs = (long) (3.5 * C.MICROS_PER_SECOND / AUDIO_FORMAT_44_100HZ.sampleRate);
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0L, speedChangeTimeUs},
            /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);
    int inputBufferLimit = inputBuffer.limit();

    speedChangingAudioProcessor.queueInput(inputBuffer);

    assertThat(inputBuffer.position()).isEqualTo(4 * AUDIO_FORMAT_44_100HZ.bytesPerFrame);
    assertThat(inputBuffer.limit()).isEqualTo(inputBufferLimit);
  }

  @Test
  public void queueInput_multipleSpeedsInBufferWithLimitVeryClose_doesNotHang() throws Exception {
    long speedChangeTimeUs = 1; // Change speed very close to current position at 1us.
    int outputFrames = 0;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0L, speedChangeTimeUs},
            /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    // SpeedChangingAudioProcessor only queues samples until the next speed change.
    while (inputBuffer.hasRemaining()) {
      speedChangingAudioProcessor.queueInput(inputBuffer);
      outputFrames +=
          speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    }

    speedChangingAudioProcessor.queueEndOfStream();
    outputFrames +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    // We allow 1 sample of tolerance per speed change.
    assertThat(outputFrames).isWithin(1).of(3);
  }

  @Test
  public void queueEndOfStream_afterNoSpeedChangeAndWithOutputRetrieved_endsProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {5, 5},
            /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isTrue();
  }

  @Test
  public void queueEndOfStream_afterSpeedChangeAndWithOutputRetrieved_endsProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {5, 5},
            /* speeds= */ new float[] {1, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    inputBuffer.rewind();
    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isTrue();
  }

  @Test
  public void queueEndOfStream_afterNoSpeedChangeAndWithOutputNotRetrieved_doesNotEndProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void queueEndOfStream_afterSpeedChangeAndWithOutputNotRetrieved_doesNotEndProcessor()
      throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    speedChangingAudioProcessor.queueEndOfStream();

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void queueEndOfStream_noInputQueued_endsProcessor() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);

    speedChangingAudioProcessor.queueEndOfStream();

    assertThat(speedChangingAudioProcessor.isEnded()).isTrue();
  }

  @Test
  public void isEnded_afterNoSpeedChangeAndOutputRetrieved_isFalse() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void isEnded_afterSpeedChangeAndOutputRetrieved_isFalse() throws Exception {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ, /* frameCounts= */ new int[] {5}, /* speeds= */ new float[] {2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer inputBuffer =
        getNonRandomByteBuffer(/* frameCount= */ 5, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(inputBuffer);
    getAudioProcessorOutput(speedChangingAudioProcessor);

    assertThat(speedChangingAudioProcessor.isEnded()).isFalse();
  }

  @Test
  public void getSpeedAdjustedTimeAsync_beforeFlush_callbacksCalledWithCorrectParametersAfterFlush()
      throws Exception {
    ArrayList<Long> outputTimesUs = new ArrayList<>();
    // Sample period = 20us.
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_50_000HZ,
            /* frameCounts= */ new int[] {6, 6},
            /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    speedChangingAudioProcessor.configure(AUDIO_FORMAT_50_000HZ);

    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 40L, outputTimesUs::add);
    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 80L, outputTimesUs::add);
    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 160L, outputTimesUs::add);

    assertThat(outputTimesUs).isEmpty();
    speedChangingAudioProcessor.flush();
    assertThat(outputTimesUs).containsExactly(20L, 40L, 100L);
  }

  @Test
  public void getSpeedAdjustedTimeAsync_afterCallToFlush_callbacksCalledWithCorrectParameters()
      throws Exception {
    ArrayList<Long> outputTimesUs = new ArrayList<>();
    // Sample period = 20us.
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_50_000HZ,
            /* frameCounts= */ new int[] {6, 6},
            /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    speedChangingAudioProcessor.configure(AUDIO_FORMAT_50_000HZ);
    speedChangingAudioProcessor.flush();

    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 40L, outputTimesUs::add);
    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 80L, outputTimesUs::add);
    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 160L, outputTimesUs::add);

    assertThat(outputTimesUs).containsExactly(20L, 40L, 100L);
  }

  @Test
  public void getSpeedAdjustedTimeAsync_timeAfterEndTime_callbacksCalledWithCorrectParameters()
      throws Exception {
    ArrayList<Long> outputTimesUs = new ArrayList<>();
    // The speed change is at 120Us (6*MICROS_PER_SECOND/sampleRate).
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_50_000HZ,
            /* frameCounts= */ new int[] {6, 6},
            /* speeds= */ new float[] {2, 1});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    speedChangingAudioProcessor.configure(AUDIO_FORMAT_50_000HZ);
    speedChangingAudioProcessor.flush();

    speedChangingAudioProcessor.getSpeedAdjustedTimeAsync(
        /* inputTimeUs= */ 300L, outputTimesUs::add);

    assertThat(outputTimesUs).containsExactly(240L);
  }

  @Test
  public void getMediaDurationUs_returnsCorrectValues() throws Exception {
    // The speed changes happen every 10ms (500 samples @ 50.KHz)
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_50_000HZ,
            /* frameCounts= */ new int[] {500, 500, 500, 500},
            /* speeds= */ new float[] {2, 1, 5, 2});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    speedChangingAudioProcessor.configure(AUDIO_FORMAT_50_000HZ);
    speedChangingAudioProcessor.flush();

    // input (in ms) (0, 10, 20, 30, 40) ->
    // output (in ms) (0, 10/2, 10/2 + 10, 10/2 + 10 + 10/5, 10/2 + 10 + 10/5 + 10/2)
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 0))
        .isEqualTo(0);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 3_000))
        .isEqualTo(6_000);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 5_000))
        .isEqualTo(10_000);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 10_000))
        .isEqualTo(15_000);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 15_000))
        .isEqualTo(20_000);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 16_000))
        .isEqualTo(25_000);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 17_000))
        .isEqualTo(30_000);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 18_000))
        .isEqualTo(32_000);
    assertThat(speedChangingAudioProcessor.getMediaDurationUs(/* playoutDurationUs= */ 22_000))
        .isEqualTo(40_000);
  }

  @Test
  public void queueInput_exactlyUpToSpeedBoundary_outputsExpectedNumberOfSamples()
      throws AudioProcessor.UnhandledAudioFormatException {
    int outputFrameCount = 0;
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 1000, 1000},
            /* speeds= */ new float[] {2, 4, 2}); // 500, 250, 500 = 1250
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer input = getNonRandomByteBuffer(1000, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    speedChangingAudioProcessor.queueInput(input);
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    input.rewind();

    speedChangingAudioProcessor.queueInput(input);
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    input.rewind();

    speedChangingAudioProcessor.queueInput(input);
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;

    speedChangingAudioProcessor.queueEndOfStream();
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    assertThat(outputFrameCount).isWithin(2).of(1250);
  }

  @Test
  public void queueInput_withUnalignedSpeedStartTimes_skipsMidSampleSpeedChanges()
      throws AudioProcessor.UnhandledAudioFormatException {
    int outputFrameCount = 0;
    // Sample duration @44.1KHz is 22.67573696145125us. The last three speed changes fall between
    // samples 4 and 5, so only the speed change at 105us should be used. We expect an output of
    // 4 / 2  + 8 / 4 = 4 samples.
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithStartTimes(
            /* startTimesUs= */ new long[] {0, 95, 100, 105},
            /* speeds= */ new float[] {2, 3, 8, 4});
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    ByteBuffer input = getNonRandomByteBuffer(12, AUDIO_FORMAT_44_100HZ.bytesPerFrame);

    while (input.hasRemaining()) {
      speedChangingAudioProcessor.queueInput(input);
      outputFrameCount +=
          speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    }

    speedChangingAudioProcessor.queueEndOfStream();
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;

    // Allow one sample of tolerance per effectively applied speed change.
    assertThat(outputFrameCount).isWithin(1).of(4);
  }

  @Test
  public void flush_withInitialSpeedSetToDefault_returnsToInitialSpeedAfterFlush()
      throws AudioProcessor.UnhandledAudioFormatException {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 1000},
            /* speeds= */ new float[] {1, 2}); // 1000, 500.
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    // 1500 input frames falls in the middle of the 2x region.
    ByteBuffer input = getNonRandomByteBuffer(1500, AUDIO_FORMAT_44_100HZ.bytesPerFrame);
    int outputFrameCount = 0;

    while (input.hasRemaining()) {
      speedChangingAudioProcessor.queueInput(input);
      outputFrameCount +=
          speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    }
    speedChangingAudioProcessor.flush();
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    assertThat(outputFrameCount).isEqualTo(1250);
    input.rewind();

    // After flush, SpeedChangingAudioProcessor's position should go back to the beginning and use
    // the first speed region. This means that even if we flushed during 2x, the initial 1000
    // samples fed to SpeedChangingAudioProcessor after the flush should be output at 1x.
    while (input.hasRemaining()) {
      speedChangingAudioProcessor.queueInput(input);
      outputFrameCount +=
          speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    }
    speedChangingAudioProcessor.queueEndOfStream();
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    assertThat(outputFrameCount).isWithin(1).of(2500); // 1250 * 2.
  }

  @Test
  public void flush_withInitialSpeedSetToNonDefault_returnsToInitialSpeedAfterFlush()
      throws AudioProcessor.UnhandledAudioFormatException {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 1000},
            /* speeds= */ new float[] {2, 4}); // 500, 250.
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        getConfiguredSpeedChangingAudioProcessor(speedProvider);
    // 1500 input frames falls in the middle of the 2x region.
    ByteBuffer input = getNonRandomByteBuffer(1500, AUDIO_FORMAT_44_100HZ.bytesPerFrame);
    int outputFrameCount = 0;

    while (input.hasRemaining()) {
      speedChangingAudioProcessor.queueInput(input);
      outputFrameCount +=
          speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    }
    speedChangingAudioProcessor.flush();
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    assertThat(outputFrameCount).isWithin(1).of(625);
    input.rewind();

    // After flush, SpeedChangingAudioProcessor's position should go back to the beginning and use
    // the first speed region. This means that even if we flushed during 4x, the initial 1000
    // samples fed to SpeedChangingAudioProcessor after the flush should be output at 2x.
    while (input.hasRemaining()) {
      speedChangingAudioProcessor.queueInput(input);
      outputFrameCount +=
          speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    }
    speedChangingAudioProcessor.queueEndOfStream();
    outputFrameCount +=
        speedChangingAudioProcessor.getOutput().remaining() / AUDIO_FORMAT_44_100HZ.bytesPerFrame;
    assertThat(outputFrameCount).isWithin(2).of(1250); // 625 * 2.
  }

  @Test
  public void getSampleCountAfterProcessorApplied_withConstantSpeed_outputsExpectedSamples() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            new AudioFormat(/* sampleRate= */ 48000, /* channelCount= */ 1, C.ENCODING_PCM_16BIT),
            /* frameCounts= */ new int[] {100},
            /* speeds= */ new float[] {2.f});

    long sampleCountAfterProcessorApplied =
        SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
            speedProvider, AUDIO_FORMAT_44_100HZ.sampleRate, /* inputSamples= */ 100);
    assertThat(sampleCountAfterProcessorApplied).isEqualTo(50);
  }

  @Test
  public void getSampleCountAfterProcessorApplied_withMultipleSpeeds_outputsExpectedSamples() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {100, 400, 50},
            /* speeds= */ new float[] {2.f, 4f, 0.5f});

    long sampleCountAfterProcessorApplied =
        SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
            speedProvider, AUDIO_FORMAT_44_100HZ.sampleRate, /* inputSamples= */ 550);
    assertThat(sampleCountAfterProcessorApplied).isEqualTo(250);
  }

  @Test
  public void
      getSampleCountAfterProcessorApplied_beyondLastSpeedRegion_stillAppliesLastSpeedValue() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {100, 400, 50},
            /* speeds= */ new float[] {2.f, 4f, 0.5f});

    long sampleCountAfterProcessorApplied =
        SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
            speedProvider, AUDIO_FORMAT_44_100HZ.sampleRate, /* inputSamples= */ 3000);
    assertThat(sampleCountAfterProcessorApplied).isEqualTo(5150);
  }

  @Test
  public void
      getSampleCountAfterProcessorApplied_withInputCountBeyondIntRange_outputsExpectedSamples() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 10000, 8200},
            /* speeds= */ new float[] {0.2f, 8f, 0.5f});
    long sampleCountAfterProcessorApplied =
        SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
            speedProvider, AUDIO_FORMAT_44_100HZ.sampleRate, /* inputSamples= */ 3_000_000_000L);
    assertThat(sampleCountAfterProcessorApplied).isEqualTo(5_999_984_250L);
  }

  // Testing range validation.
  @SuppressLint("Range")
  @Test
  public void getSampleCountAfterProcessorApplied_withNegativeFrameCount_throws() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 10000, 8200},
            /* speeds= */ new float[] {0.2f, 8f, 0.5f});
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
                speedProvider, AUDIO_FORMAT_44_100HZ.sampleRate, /* inputSamples= */ -2L));
  }

  // Testing range validation.
  @SuppressLint("Range")
  @Test
  public void getSampleCountAfterProcessorApplied_withZeroFrameRate_throws() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 10000, 8200},
            /* speeds= */ new float[] {0.2f, 8f, 0.5f});
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
                speedProvider, /* inputSampleRateHz= */ 0, /* inputSamples= */ 1000L));
  }

  @Test
  public void getSampleCountAfterProcessorApplied_withNullSpeedProvider_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
                /* speedProvider= */ null,
                AUDIO_FORMAT_44_100HZ.sampleRate,
                /* inputSamples= */ 1000L));
  }

  @Test
  public void getSampleCountAfterProcessorApplied_withZeroInputFrames_returnsZero() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 10000, 8200},
            /* speeds= */ new float[] {0.2f, 8f, 0.5f});

    long sampleCountAfterProcessorApplied =
        SpeedChangingAudioProcessor.getSampleCountAfterProcessorApplied(
            speedProvider, AUDIO_FORMAT_44_100HZ.sampleRate, /* inputSamples= */ 0L);
    assertThat(sampleCountAfterProcessorApplied).isEqualTo(0L);
  }

  @Test
  public void isActive_beforeConfigure_returnsFalse() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000},
            /* speeds= */ new float[] {2f});

    SpeedChangingAudioProcessor processor = new SpeedChangingAudioProcessor(speedProvider);
    assertThat(processor.isActive()).isFalse();
  }

  @Test
  public void isActive_afterConfigure_returnsTrue()
      throws AudioProcessor.UnhandledAudioFormatException {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000},
            /* speeds= */ new float[] {2f});

    SpeedChangingAudioProcessor processor = new SpeedChangingAudioProcessor(speedProvider);
    processor.configure(AUDIO_FORMAT_44_100HZ);
    assertThat(processor.isActive()).isTrue();
  }

  @Test
  public void getInputFrameCountForOutput_withZeroOutputFrames_returnsZero() {
    SpeedProvider speedProvider =
        TestSpeedProvider.createWithFrameCounts(
            AUDIO_FORMAT_44_100HZ,
            /* frameCounts= */ new int[] {1000, 10000, 8200},
            /* speeds= */ new float[] {0.2f, 8f, 0.5f});

    long inputFrames =
        getInputFrameCountForOutput(
            speedProvider, AUDIO_FORMAT_44_100HZ.sampleRate, /* outputFrameCount= */ 0L);
    assertThat(inputFrames).isEqualTo(0L);
  }

  private static SpeedChangingAudioProcessor getConfiguredSpeedChangingAudioProcessor(
      SpeedProvider speedProvider) throws AudioProcessor.UnhandledAudioFormatException {
    SpeedChangingAudioProcessor speedChangingAudioProcessor =
        new SpeedChangingAudioProcessor(speedProvider);
    speedChangingAudioProcessor.configure(AUDIO_FORMAT_44_100HZ);
    speedChangingAudioProcessor.flush();
    return speedChangingAudioProcessor;
  }

  private static ByteBuffer getAudioProcessorOutput(AudioProcessor audioProcessor) {
    ByteBuffer concatenatedOutputBuffers = EMPTY_BUFFER;
    while (true) {
      ByteBuffer outputBuffer = audioProcessor.getOutput();
      if (!outputBuffer.hasRemaining()) {
        break;
      }
      ByteBuffer temp =
          ByteBuffer.allocateDirect(
                  concatenatedOutputBuffers.remaining() + outputBuffer.remaining())
              .order(ByteOrder.nativeOrder());
      temp.put(concatenatedOutputBuffers);
      temp.put(outputBuffer);
      temp.rewind();
      concatenatedOutputBuffers = temp;
    }
    return concatenatedOutputBuffers;
  }
}
