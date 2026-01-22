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
package androidx.media3.exoplayer.audio;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Utility methods for PCM audio data. */
@UnstableApi
public final class PcmAudioUtil {

  /**
   * Returns a new {@link ByteBuffer} with linear volume ramping applied.
   *
   * @param buffer The input buffer containing PCM frames. The buffer will be fully consumed by this
   *     method.
   * @param pcmEncoding The {@link C.Encoding} of the PCM frames.
   * @param pcmFrameSize The overall frame size of one PCM frame (including all channels).
   * @param startFrameIndex The index of the first frame within the audio ramp duration (as
   *     specified by {@code rampFrameCount}).
   * @param rampFrameCount The overall ramp duration in number of frames.
   * @return The {@link ByteBuffer} containing the modified PCM data.
   */
  public static ByteBuffer rampUpVolume(
      ByteBuffer buffer,
      @C.Encoding int pcmEncoding,
      int pcmFrameSize,
      int startFrameIndex,
      int rampFrameCount) {
    ByteBuffer outputBuffer =
        ByteBuffer.allocateDirect(buffer.remaining()).order(ByteOrder.nativeOrder());
    int frameIndex = startFrameIndex;
    int frameStartPosition = buffer.position();
    while (buffer.hasRemaining() && frameIndex < rampFrameCount) {
      long pcm32Bit = readAs32BitIntPcm(buffer, pcmEncoding);
      pcm32Bit = pcm32Bit * frameIndex / rampFrameCount;
      write32BitIntPcm(outputBuffer, (int) pcm32Bit, pcmEncoding);
      if (buffer.position() == frameStartPosition + pcmFrameSize) {
        frameIndex++;
        frameStartPosition = buffer.position();
      }
    }
    outputBuffer.put(buffer);
    outputBuffer.flip();
    return outputBuffer;
  }

  /**
   * Reads a single-channel PCM value from the buffer and returns it as a 32-bit integer PCM value.
   *
   * @param buffer The {@link ByteBuffer} to read from.
   * @param pcmEncoding The {@link C.Encoding} of the PCM data in the buffer.
   * @return The 32-bit PCM value of the read buffer.
   */
  public static int readAs32BitIntPcm(ByteBuffer buffer, @C.Encoding int pcmEncoding) {
    switch (pcmEncoding) {
      case C.ENCODING_PCM_8BIT:
        return (buffer.get() & 0xFF) << 24;
      case C.ENCODING_PCM_16BIT:
        return ((buffer.get() & 0xFF) << 16) | ((buffer.get() & 0xFF) << 24);
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
        return ((buffer.get() & 0xFF) << 24) | ((buffer.get() & 0xFF) << 16);
      case C.ENCODING_PCM_24BIT:
        return ((buffer.get() & 0xFF) << 8)
            | ((buffer.get() & 0xFF) << 16)
            | ((buffer.get() & 0xFF) << 24);
      case C.ENCODING_PCM_24BIT_BIG_ENDIAN:
        return ((buffer.get() & 0xFF) << 24)
            | ((buffer.get() & 0xFF) << 16)
            | ((buffer.get() & 0xFF) << 8);
      case C.ENCODING_PCM_32BIT:
        return (buffer.get() & 0xFF)
            | ((buffer.get() & 0xFF) << 8)
            | ((buffer.get() & 0xFF) << 16)
            | ((buffer.get() & 0xFF) << 24);
      case C.ENCODING_PCM_32BIT_BIG_ENDIAN:
        return ((buffer.get() & 0xFF) << 24)
            | ((buffer.get() & 0xFF) << 16)
            | ((buffer.get() & 0xFF) << 8)
            | (buffer.get() & 0xFF);
      case C.ENCODING_PCM_FLOAT:
        float floatValue = Util.constrainValue(buffer.getFloat(), /* min= */ -1f, /* max= */ 1f);
        if (floatValue < 0) {
          return (int) (-floatValue * Integer.MIN_VALUE);
        } else {
          return (int) (floatValue * Integer.MAX_VALUE);
        }
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Writes a 32-bit integer PCM value to a buffer in the given target PCM encoding.
   *
   * @param buffer The {@link ByteBuffer} to write to.
   * @param pcm32bit The 32-bit PCM value.
   * @param pcmEncoding The target {@link C.Encoding} of the PCM data in the buffer.
   */
  public static void write32BitIntPcm(
      ByteBuffer buffer, int pcm32bit, @C.Encoding int pcmEncoding) {
    switch (pcmEncoding) {
      case C.ENCODING_PCM_32BIT:
        buffer.put((byte) pcm32bit);
        buffer.put((byte) (pcm32bit >> 8));
        buffer.put((byte) (pcm32bit >> 16));
        buffer.put((byte) (pcm32bit >> 24));
        return;
      case C.ENCODING_PCM_24BIT:
        buffer.put((byte) (pcm32bit >> 8));
        buffer.put((byte) (pcm32bit >> 16));
        buffer.put((byte) (pcm32bit >> 24));
        return;
      case C.ENCODING_PCM_16BIT:
        buffer.put((byte) (pcm32bit >> 16));
        buffer.put((byte) (pcm32bit >> 24));
        return;
      case C.ENCODING_PCM_8BIT:
        buffer.put((byte) (pcm32bit >> 24));
        return;
      case C.ENCODING_PCM_32BIT_BIG_ENDIAN:
        buffer.put((byte) (pcm32bit >> 24));
        buffer.put((byte) (pcm32bit >> 16));
        buffer.put((byte) (pcm32bit >> 8));
        buffer.put((byte) pcm32bit);
        return;
      case C.ENCODING_PCM_24BIT_BIG_ENDIAN:
        buffer.put((byte) (pcm32bit >> 24));
        buffer.put((byte) (pcm32bit >> 16));
        buffer.put((byte) (pcm32bit >> 8));
        return;
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
        buffer.put((byte) (pcm32bit >> 24));
        buffer.put((byte) (pcm32bit >> 16));
        return;
      case C.ENCODING_PCM_FLOAT:
        if (pcm32bit < 0) {
          buffer.putFloat(-((float) pcm32bit) / Integer.MIN_VALUE);
        } else {
          buffer.putFloat((float) pcm32bit / Integer.MAX_VALUE);
        }
        return;
      default:
        throw new IllegalStateException();
    }
  }

  private PcmAudioUtil() {}
}
