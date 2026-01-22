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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.SpeedProviderUtil.getNextSpeedChangeSamplePosition;
import static androidx.media3.common.util.SpeedProviderUtil.getSampleAlignedSpeed;
import static androidx.media3.common.util.Util.sampleCountToDurationUs;
import static androidx.media3.common.util.Util.scaleLargeValue;
import static java.lang.Math.min;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.LongArrayQueue;
import androidx.media3.common.util.SpeedProviderUtil;
import androidx.media3.common.util.TimestampConsumer;
import androidx.media3.common.util.UnstableApi;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.LongConsumer;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;

/**
 * An {@link AudioProcessor} that changes the speed of audio samples depending on their timestamp.
 */
@UnstableApi
public final class SpeedChangingAudioProcessor implements AudioProcessor {

  private final Object lock;

  /** The speed provider that provides the speed for each timestamp. */
  private final SpeedProvider speedProvider;

  /**
   * The {@link SonicAudioProcessor} used to change the speed, when needed. If there is no speed
   * change required, the input buffer is copied to the output buffer and this processor is not
   * used.
   */
  private final SynchronizedSonicAudioProcessor sonicAudioProcessor;

  // Elements in the same positions in the queues are associated.

  @GuardedBy("lock")
  private final LongArrayQueue pendingCallbackInputTimesUs;

  @GuardedBy("lock")
  private final Queue<TimestampConsumer> pendingCallbacks;

  private float currentSpeed;
  private long framesRead;
  private boolean endOfStreamQueuedToSonic;

  /** The current input audio format. */
  @GuardedBy("lock")
  private AudioFormat inputAudioFormat;

  private AudioFormat pendingInputAudioFormat;
  private AudioFormat pendingOutputAudioFormat;
  private boolean inputEnded;

  public SpeedChangingAudioProcessor(SpeedProvider speedProvider) {
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    inputAudioFormat = AudioFormat.NOT_SET;

    this.speedProvider = speedProvider;
    lock = new Object();
    sonicAudioProcessor =
        new SynchronizedSonicAudioProcessor(lock, /* keepActiveWithDefaultParameters= */ true);
    pendingCallbackInputTimesUs = new LongArrayQueue();
    pendingCallbacks = new ArrayDeque<>();
    resetInternalState(/* shouldResetSpeed= */ true);
  }

  /** Returns the estimated number of samples output given the provided parameters. */
  public static long getSampleCountAfterProcessorApplied(
      SpeedProvider speedProvider,
      @IntRange(from = 1) int inputSampleRateHz,
      @IntRange(from = 0) long inputSamples) {
    checkArgument(speedProvider != null);
    checkArgument(inputSampleRateHz > 0);
    checkArgument(inputSamples >= 0);

    long outputSamples = 0;
    long positionSamples = 0;

    while (positionSamples < inputSamples) {
      long boundarySamples =
          getNextSpeedChangeSamplePosition(speedProvider, positionSamples, inputSampleRateHz);

      if (boundarySamples == C.INDEX_UNSET || boundarySamples > inputSamples) {
        boundarySamples = inputSamples;
      }

      float speed = getSampleAlignedSpeed(speedProvider, positionSamples, inputSampleRateHz);
      // Input and output sample rates match because SpeedChangingAudioProcessor does not modify the
      // output sample rate.
      outputSamples +=
          Sonic.getExpectedFrameCountAfterProcessorApplied(
              /* inputSampleRateHz= */ inputSampleRateHz,
              /* outputSampleRateHz= */ inputSampleRateHz,
              /* speed= */ speed,
              /* pitch= */ speed,
              /* inputFrameCount= */ boundarySamples - positionSamples);
      positionSamples = boundarySamples;
    }

    return outputSamples;
  }

  @Override
  public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
    pendingInputAudioFormat = inputAudioFormat;
    pendingOutputAudioFormat = sonicAudioProcessor.configure(inputAudioFormat);
    return pendingOutputAudioFormat;
  }

  @Override
  public boolean isActive() {
    return !pendingOutputAudioFormat.equals(AudioFormat.NOT_SET);
  }

  @Override
  public long getDurationAfterProcessorApplied(long durationUs) {
    return SpeedProviderUtil.getDurationAfterSpeedProviderApplied(speedProvider, durationUs);
  }

  @Override
  public void queueInput(ByteBuffer inputBuffer) {
    AudioFormat format;
    synchronized (lock) {
      format = inputAudioFormat;
    }

    float newSpeed = getSampleAlignedSpeed(speedProvider, framesRead, format.sampleRate);
    long nextSpeedChangeSamplePosition =
        getNextSpeedChangeSamplePosition(speedProvider, framesRead, format.sampleRate);

    updateSpeed(newSpeed);

    int inputBufferLimit = inputBuffer.limit();
    int bytesToNextSpeedChange;
    if (nextSpeedChangeSamplePosition != C.INDEX_UNSET) {
      bytesToNextSpeedChange =
          (int) ((nextSpeedChangeSamplePosition - framesRead) * format.bytesPerFrame);
      // Update the input buffer limit to make sure that all samples processed have the same speed.
      inputBuffer.limit(min(inputBufferLimit, inputBuffer.position() + bytesToNextSpeedChange));
    } else {
      bytesToNextSpeedChange = C.LENGTH_UNSET;
    }

    long startPosition = inputBuffer.position();
    sonicAudioProcessor.queueInput(inputBuffer);
    if (bytesToNextSpeedChange != C.LENGTH_UNSET
        && (inputBuffer.position() - startPosition) == bytesToNextSpeedChange) {
      sonicAudioProcessor.queueEndOfStream();
      endOfStreamQueuedToSonic = true;
    }
    long bytesRead = inputBuffer.position() - startPosition;
    checkState(bytesRead % format.bytesPerFrame == 0, "A frame was not queued completely.");
    framesRead += bytesRead / format.bytesPerFrame;
    inputBuffer.limit(inputBufferLimit);
  }

  @Override
  public void queueEndOfStream() {
    inputEnded = true;
    if (!endOfStreamQueuedToSonic) {
      sonicAudioProcessor.queueEndOfStream();
      endOfStreamQueuedToSonic = true;
    }
  }

  @Override
  public ByteBuffer getOutput() {
    return sonicAudioProcessor.getOutput();
  }

  @Override
  public boolean isEnded() {
    return inputEnded && sonicAudioProcessor.isEnded();
  }

  @Override
  public void flush() {
    inputEnded = false;
    resetInternalState(/* shouldResetSpeed= */ false);
    synchronized (lock) {
      inputAudioFormat = pendingInputAudioFormat;
      sonicAudioProcessor.flush();
      processPendingCallbacks();
    }
  }

  @Override
  public void reset() {
    flush();
    pendingInputAudioFormat = AudioFormat.NOT_SET;
    pendingOutputAudioFormat = AudioFormat.NOT_SET;
    synchronized (lock) {
      inputAudioFormat = AudioFormat.NOT_SET;
      pendingCallbackInputTimesUs.clear();
      pendingCallbacks.clear();
    }
    resetInternalState(/* shouldResetSpeed= */ true);
    sonicAudioProcessor.reset();
  }

  /**
   * Calculates the time at which the {@code inputTimeUs} is outputted at after the speed changes
   * has been applied.
   *
   * <p>Calls {@linkplain LongConsumer#accept(long) the callback} with the output time as soon as
   * enough audio has been processed to calculate it.
   *
   * <p>If the audio processor has ended, speeds will come out at the last processed speed of the
   * audio processor.
   *
   * <p>Successive calls must have monotonically increasing {@code inputTimeUs}.
   *
   * <p>Can be called from any thread.
   *
   * @param inputTimeUs The input time, in microseconds.
   * @param callback The callback called with the output time. May be called on a different thread
   *     from the caller of this method.
   */
  // TODO(b/381553948): Accept an executor on which to dispatch the callback.
  public void getSpeedAdjustedTimeAsync(long inputTimeUs, TimestampConsumer callback) {
    int sampleRate;
    synchronized (lock) {
      sampleRate = inputAudioFormat.sampleRate;

      if (sampleRate == Format.NO_VALUE) {
        pendingCallbackInputTimesUs.add(inputTimeUs);
        pendingCallbacks.add(callback);
        return;
      }
    }
    // TODO(b/381553948): Use an executor to invoke callback.
    callback.onTimestamp(
        getDurationUsAfterProcessorApplied(speedProvider, sampleRate, inputTimeUs));
  }

  /**
   * Returns the input media duration in microseconds for the given playout duration.
   *
   * <p>This method returns the inverse of {@link #getSpeedAdjustedTimeAsync} when the instance has
   * been configured and flushed. Otherwise, it returns {@code playoutDurationUs}.
   *
   * @param playoutDurationUs The playout duration in microseconds.
   */
  public long getMediaDurationUs(long playoutDurationUs) {
    int sampleRate;
    synchronized (lock) {
      sampleRate = inputAudioFormat.sampleRate;
    }
    if (sampleRate == Format.NO_VALUE) {
      return playoutDurationUs;
    }
    long outputSamples =
        scaleLargeValue(playoutDurationUs, sampleRate, C.MICROS_PER_SECOND, RoundingMode.HALF_EVEN);
    long inputSamples = getInputFrameCountForOutput(speedProvider, sampleRate, outputSamples);
    return sampleCountToDurationUs(inputSamples, sampleRate);
  }

  /**
   * Returns the number of input frames needed to output a specific number of frames, given a speed
   * provider, input sample rate, and number of output frames.
   *
   * <p>This is the inverse operation of {@link #getSampleCountAfterProcessorApplied}.
   */
  @VisibleForTesting
  /* package */ static long getInputFrameCountForOutput(
      SpeedProvider speedProvider,
      @IntRange(from = 1) int inputSampleRate,
      @IntRange(from = 0) long outputFrameCount) {
    checkArgument(inputSampleRate > 0);
    checkArgument(outputFrameCount >= 0);

    long inputSampleCount = 0;
    while (outputFrameCount > 0) {
      long boundarySamples =
          getNextSpeedChangeSamplePosition(speedProvider, inputSampleCount, inputSampleRate);
      float speed = getSampleAlignedSpeed(speedProvider, inputSampleCount, inputSampleRate);

      long outputSamplesForSection =
          Sonic.getExpectedFrameCountAfterProcessorApplied(
              /* inputSampleRateHz= */ inputSampleRate,
              /* outputSampleRateHz= */ inputSampleRate,
              /* speed= */ speed,
              /* pitch= */ speed,
              /* inputFrameCount= */ boundarySamples - inputSampleCount);

      if (boundarySamples == C.INDEX_UNSET || outputSamplesForSection > outputFrameCount) {
        inputSampleCount +=
            Sonic.getExpectedInputFrameCountForOutputFrameCount(
                /* inputSampleRateHz= */ inputSampleRate,
                /* outputSampleRateHz= */ inputSampleRate,
                /* speed= */ speed,
                /* pitch= */ speed,
                outputFrameCount);
        outputFrameCount = 0;
      } else {
        outputFrameCount -= outputSamplesForSection;
        inputSampleCount = boundarySamples;
      }
    }

    return inputSampleCount;
  }

  private static long getDurationUsAfterProcessorApplied(
      SpeedProvider speedProvider, int sampleRate, long inputDurationUs) {
    long inputSamples =
        scaleLargeValue(inputDurationUs, sampleRate, C.MICROS_PER_SECOND, RoundingMode.HALF_EVEN);
    long outputSamples =
        getSampleCountAfterProcessorApplied(speedProvider, sampleRate, inputSamples);
    return sampleCountToDurationUs(outputSamples, sampleRate);
  }

  private void processPendingCallbacks() {
    synchronized (lock) {
      if (inputAudioFormat.sampleRate == Format.NO_VALUE) {
        return;
      }

      while (!pendingCallbacks.isEmpty()) {
        long inputTimeUs = pendingCallbackInputTimesUs.remove();
        TimestampConsumer consumer = pendingCallbacks.remove();
        // TODO(b/381553948): Use an executor to invoke callback.
        consumer.onTimestamp(
            getDurationUsAfterProcessorApplied(
                speedProvider, inputAudioFormat.sampleRate, inputTimeUs));
      }
    }
  }

  private void updateSpeed(float newSpeed) {
    if (newSpeed != currentSpeed) {
      currentSpeed = newSpeed;
      sonicAudioProcessor.setSpeed(newSpeed);
      sonicAudioProcessor.setPitch(newSpeed);
      // Invalidate any previously created buffers in SonicAudioProcessor and the base class.
      sonicAudioProcessor.flush();
      endOfStreamQueuedToSonic = false;
    }
  }

  /**
   * Resets internal fields to their default value.
   *
   * <p>When setting {@code shouldResetSpeed} to {@code true}, {@link #sonicAudioProcessor}'s speed
   * and pitch must also be updated.
   *
   * @param shouldResetSpeed Whether {@link #currentSpeed} should be reset to its default value.
   */
  private void resetInternalState(
      @UnknownInitialization SpeedChangingAudioProcessor this, boolean shouldResetSpeed) {
    if (shouldResetSpeed) {
      currentSpeed = 1f;
    }
    framesRead = 0;
    endOfStreamQueuedToSonic = false;
  }
}
