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
package androidx.media3.decoder.iamf;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import android.media.AudioFormat;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.List;

/** IAMF decoder. */
@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
public final class IamfDecoder
    extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, IamfDecoderException> {
  /* package */ static final int OUTPUT_SAMPLE_RATE = 48000;
  /* package */ static final int OUTPUT_PCM_ENCODING = C.ENCODING_PCM_16BIT;
  /* package */ static final int SPATIALIZED_OUTPUT_LAYOUT = AudioFormat.CHANNEL_OUT_5POINT1;

  // Matches IAMF_SoundSystem in IAMF_defines.h
  private static final int SOUND_SYSTEM_STEREO = 0; // SOUND_SYSTEM_A
  private static final int SOUND_SYSTEM_5POINT1 = 1; // SOUND_SYSTEM_B

  private final byte[] initializationData;
  private final int soundSystem;

  private long nativeDecoderPointer;

  /**
   * Creates an IAMF decoder.
   *
   * @param initializationData ConfigOBUs data for the decoder.
   * @param spatializationSupported Whether spatialization is supported and output should be 6
   *     channels in 5.1 layout.
   * @throws IamfDecoderException Thrown if an exception occurs when initializing the decoder.
   */
  public IamfDecoder(List<byte[]> initializationData, boolean spatializationSupported)
      throws IamfDecoderException {
    super(new DecoderInputBuffer[1], new SimpleDecoderOutputBuffer[1]);
    if (!IamfLibrary.isAvailable()) {
      throw new IamfDecoderException("Failed to load decoder native libraries.");
    }
    if (initializationData.size() != 1) {
      throw new IamfDecoderException("Initialization data must contain a single element.");
    }
    soundSystem = spatializationSupported ? SOUND_SYSTEM_5POINT1 : SOUND_SYSTEM_STEREO;
    this.initializationData = initializationData.get(0);
    this.nativeDecoderPointer = iamfOpen();
    int status =
        iamfConfigDecoder(
            this.initializationData,
            Util.getByteDepth(OUTPUT_PCM_ENCODING) * C.BITS_PER_BYTE,
            OUTPUT_SAMPLE_RATE,
            soundSystem,
            nativeDecoderPointer);
    if (status != 0) {
      throw new IamfDecoderException("Failed to configure decoder with returned status: " + status);
    }
  }

  @Override
  public void release() {
    super.release();
    iamfClose(nativeDecoderPointer);
  }

  public int getBinauralLayoutChannelCount() {
    return iamfLayoutBinauralChannelsCount();
  }

  public int getChannelCount() {
    return iamfGetChannelCount(soundSystem);
  }

  @Override
  public String getName() {
    return "libiamf";
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
  protected IamfDecoderException createUnexpectedDecodeException(Throwable error) {
    return new IamfDecoderException("Unexpected decode error", error);
  }

  @Override
  @Nullable
  protected IamfDecoderException decode(
      DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      iamfClose(nativeDecoderPointer);
      nativeDecoderPointer = iamfOpen();
      iamfConfigDecoder(
          initializationData,
          Util.getByteDepth(OUTPUT_PCM_ENCODING) * C.BITS_PER_BYTE,
          OUTPUT_SAMPLE_RATE,
          soundSystem,
          nativeDecoderPointer); // reconfigure
    }
    int bufferSize =
        iamfGetMaxFrameSize(nativeDecoderPointer)
            * getChannelCount()
            * Util.getByteDepth(OUTPUT_PCM_ENCODING);
    outputBuffer.init(inputBuffer.timeUs, bufferSize);
    ByteBuffer outputData = Util.castNonNull(outputBuffer.data);
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int ret = iamfDecode(inputData, inputData.limit(), outputData, nativeDecoderPointer);
    if (ret < 0) {
      return new IamfDecoderException("Failed to decode error= " + ret);
    }
    outputData.position(0);
    outputData.limit(ret * getChannelCount() * Util.getByteDepth(OUTPUT_PCM_ENCODING));
    return null;
  }

  private native int iamfLayoutBinauralChannelsCount();

  private native int iamfConfigDecoder(
      byte[] initializationData,
      int bitDepth,
      int sampleRate,
      int soundSystem,
      long decoderRawPointer);

  private native long iamfOpen();

  private native void iamfClose(long decoderRawPointer);

  private native int iamfDecode(
      ByteBuffer inputBuffer, int inputSize, ByteBuffer outputBuffer, long decoderRawPointer);

  /**
   * Returns the maximum expected number of PCM samples per channel in a compressed audio frame.
   * Used to initialize the output buffer.
   */
  private native int iamfGetMaxFrameSize(long decoderRawPointer);

  private native int iamfGetChannelCount(int soundSystem);
}
