/*
 * Copyright (C) 2017 The Android Open Source Project
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

import java.nio.ByteBuffer;

/**
 * A thread safe version {@link SonicAudioProcessor} that synchronizes calls before forwarding them
 * to {@link SonicAudioProcessor}.
 */
/* package */ class SynchronizedSonicAudioProcessor implements AudioProcessor {

  private final Object lock;
  private final SonicAudioProcessor sonicAudioProcessor;

  public SynchronizedSonicAudioProcessor(Object lock, boolean keepActiveWithDefaultParameters) {
    this.lock = lock;
    sonicAudioProcessor = new SonicAudioProcessor(keepActiveWithDefaultParameters);
  }

  public final void setSpeed(float speed) {
    synchronized (lock) {
      sonicAudioProcessor.setSpeed(speed);
    }
  }

  public final void setPitch(float pitch) {
    synchronized (lock) {
      sonicAudioProcessor.setPitch(pitch);
    }
  }

  public final void setOutputSampleRateHz(int sampleRateHz) {
    synchronized (lock) {
      sonicAudioProcessor.setOutputSampleRateHz(sampleRateHz);
    }
  }

  public final long getMediaDuration(long playoutDuration) {
    synchronized (lock) {
      return sonicAudioProcessor.getMediaDuration(playoutDuration);
    }
  }

  public final long getPlayoutDuration(long mediaDuration) {
    synchronized (lock) {
      return sonicAudioProcessor.getPlayoutDuration(mediaDuration);
    }
  }

  public final long getProcessedInputBytes() {
    synchronized (lock) {
      return sonicAudioProcessor.getProcessedInputBytes();
    }
  }

  @Override
  public long getDurationAfterProcessorApplied(long durationUs) {
    return getPlayoutDuration(durationUs);
  }

  @Override
  public final AudioFormat configure(AudioFormat inputAudioFormat)
      throws UnhandledAudioFormatException {
    synchronized (lock) {
      return sonicAudioProcessor.configure(inputAudioFormat);
    }
  }

  @Override
  public final boolean isActive() {
    synchronized (lock) {
      return sonicAudioProcessor.isActive();
    }
  }

  @Override
  public final void queueInput(ByteBuffer inputBuffer) {
    synchronized (lock) {
      sonicAudioProcessor.queueInput(inputBuffer);
    }
  }

  @Override
  public final void queueEndOfStream() {
    synchronized (lock) {
      sonicAudioProcessor.queueEndOfStream();
    }
  }

  @Override
  public final ByteBuffer getOutput() {
    synchronized (lock) {
      return sonicAudioProcessor.getOutput();
    }
  }

  @Override
  public final boolean isEnded() {
    synchronized (lock) {
      return sonicAudioProcessor.isEnded();
    }
  }

  @Override
  public final void flush() {
    synchronized (lock) {
      sonicAudioProcessor.flush();
    }
  }

  @Override
  public final void reset() {
    synchronized (lock) {
      sonicAudioProcessor.reset();
    }
  }
}
