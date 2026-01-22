/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.decoder.mpegh;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.Objects;

/** MPEG-H decoder. */
@UnstableApi
public final class MpeghDecoder
    extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, MpeghDecoderException> {

  /** The default input buffer size. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 2048 * 6;

  private static final int TARGET_LAYOUT_CICP = 2;

  private final ByteBuffer tmpOutputBuffer;

  private MpeghDecoderJni decoder;
  private long outPtsUs;
  private int outChannels;
  private int outSampleRate;

  /**
   * Creates an MPEG-H decoder.
   *
   * @param format The input {@link Format}.
   * @param numInputBuffers The number of input buffers.
   * @param numOutputBuffers The number of output buffers.
   * @throws MpeghDecoderException If an exception occurs when initializing the decoder.
   */
  public MpeghDecoder(Format format, int numInputBuffers, int numOutputBuffers)
      throws MpeghDecoderException {
    super(new DecoderInputBuffer[numInputBuffers], new SimpleDecoderOutputBuffer[numOutputBuffers]);
    if (!MpeghLibrary.isAvailable()) {
      throw new MpeghDecoderException("Failed to load decoder native libraries.");
    }

    byte[] configData = new byte[0];
    if (!format.initializationData.isEmpty()
        && Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_MPEGH_MHA1)) {
      configData = format.initializationData.get(0);
    }

    // Initialize the native MPEG-H decoder.
    decoder = new MpeghDecoderJni();
    decoder.init(TARGET_LAYOUT_CICP, configData, configData.length);

    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    setInitialInputBufferSize(initialInputBufferSize);

    // Allocate memory for the temporary output of the native MPEG-H decoder.
    tmpOutputBuffer =
        ByteBuffer.allocateDirect(
            3072 * 24 * 6
                * 2); // MAX_FRAME_LENGTH * MAX_NUM_CHANNELS * MAX_NUM_FRAMES * BYTES_PER_SAMPLE
  }

  @Override
  public String getName() {
    return "libmpegh";
  }

  @Override
  protected DecoderInputBuffer createInputBuffer() {
    return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  protected SimpleDecoderOutputBuffer createOutputBuffer() {
    return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected MpeghDecoderException createUnexpectedDecodeException(Throwable error) {
    return new MpeghDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected MpeghDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      try {
        decoder.flush();
      } catch (MpeghDecoderException e) {
        return e;
      }
    }

    // Get the data from the input buffer.
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    long inputPtsUs = inputBuffer.timeUs;

    // Process/decode the incoming data.
    try {
      decoder.process(inputData, inputSize, inputPtsUs);
    } catch (MpeghDecoderException e) {
      return e;
    }

    // Get as many decoded samples as possible.
    int outputSize = 0;
    int numBytes = 0;
    int cnt = 0;
    tmpOutputBuffer.clear();
    do {
      try {
        outputSize = decoder.getSamples(tmpOutputBuffer, numBytes);
      } catch (MpeghDecoderException e) {
        return e;
      }
      // To concatenate possible additional audio frames, increase the write position.
      numBytes += outputSize;

      if (cnt == 0 && outputSize > 0) {
        // Only use the first frame for info about PTS, number of channels and sample rate.
        outPtsUs = decoder.getPts();
        outChannels = decoder.getNumChannels();
        outSampleRate = decoder.getSamplerate();
      }

      cnt++;
    } while (outputSize > 0);

    int outputSizeTotal = numBytes;
    tmpOutputBuffer.limit(outputSizeTotal);

    if (outputSizeTotal > 0) {
      // There is output data available

      // initialize the output buffer
      outputBuffer.clear();
      outputBuffer.init(outPtsUs, outputSizeTotal);

      // copy temporary output to output buffer
      outputBuffer.data.asShortBuffer().put(tmpOutputBuffer.asShortBuffer());
      outputBuffer.data.rewind();
    } else {
      // if no output data is available signalize that only decoding/processing was possible
      outputBuffer.shouldBeSkipped = true;
    }
    return null;
  }

  @Override
  public void release() {
    super.release();
    if (decoder != null) {
      decoder.destroy();
      decoder = null;
    }
  }

  /** Returns the channel count of output audio. */
  public int getChannelCount() {
    return outChannels;
  }

  /** Returns the sample rate of output audio. */
  public int getSampleRate() {
    return outSampleRate;
  }
}
