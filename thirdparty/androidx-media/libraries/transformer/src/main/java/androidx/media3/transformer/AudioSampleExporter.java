/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.effect.DebugTraceUtil;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.checkerframework.dataflow.qual.Pure;

/** Processes, encodes and muxes raw audio samples. */
/* package */ final class AudioSampleExporter extends SampleExporter {

  private final Codec encoder;
  private final AudioFormat encoderInputAudioFormat;
  private final DecoderInputBuffer encoderInputBuffer;
  private final DecoderInputBuffer encoderOutputBuffer;
  private final AudioGraph audioGraph;

  private final AudioGraphInput firstInput;
  private final Format firstInputFormat;

  private boolean returnedFirstInput;
  private long encoderTotalInputBytes;

  public AudioSampleExporter(
      Format firstAssetLoaderTrackFormat,
      Format firstInputFormat,
      TransformationRequest transformationRequest,
      EditedMediaItem firstEditedMediaItem,
      ImmutableList<AudioProcessor> compositionAudioProcessors,
      AudioMixer.Factory mixerFactory,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper,
      FallbackListener fallbackListener)
      throws ExportException {
    super(firstAssetLoaderTrackFormat, muxerWrapper);
    SonicAudioProcessor outputResampler = new SonicAudioProcessor();
    audioGraph =
        new AudioGraph(
            mixerFactory,
            new ImmutableList.Builder<AudioProcessor>()
                .addAll(compositionAudioProcessors)
                .add(outputResampler)
                .build());
    this.firstInputFormat = firstInputFormat;
    AudioGraphInput currentFirstInput =
        audioGraph.registerInput(firstEditedMediaItem, firstInputFormat);
    AudioFormat currentEncoderInputAudioFormat = audioGraph.getOutputAudioFormat();
    checkState(!currentEncoderInputAudioFormat.equals(AudioFormat.NOT_SET));

    Format requestedEncoderFormat =
        new Format.Builder()
            .setSampleMimeType(
                transformationRequest.audioMimeType != null
                    ? transformationRequest.audioMimeType
                    : checkNotNull(firstAssetLoaderTrackFormat.sampleMimeType))
            .setSampleRate(currentEncoderInputAudioFormat.sampleRate)
            .setChannelCount(currentEncoderInputAudioFormat.channelCount)
            .setPcmEncoding(currentEncoderInputAudioFormat.encoding)
            .setCodecs(firstInputFormat.codecs)
            .build();

    // TODO: b/324426022 - Move logic for supported mime types to DefaultEncoderFactory.
    encoder =
        encoderFactory.createForAudioEncoding(
            requestedEncoderFormat
                .buildUpon()
                .setSampleMimeType(
                    findSupportedMimeTypeForEncoderAndMuxer(
                        requestedEncoderFormat,
                        muxerWrapper.getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO)))
                .build());

    AudioFormat actualEncoderAudioFormat = new AudioFormat(encoder.getInputFormat());
    // This occurs when the encoder does not support the requested format. In this case, the audio
    // graph output needs to be resampled to a sample rate matching the encoder input to avoid
    // distorted audio.
    if (actualEncoderAudioFormat.sampleRate != currentEncoderInputAudioFormat.sampleRate) {
      audioGraph.reset();
      outputResampler.setOutputSampleRateHz(actualEncoderAudioFormat.sampleRate);
      currentFirstInput = audioGraph.registerInput(firstEditedMediaItem, firstInputFormat);
      currentEncoderInputAudioFormat = audioGraph.getOutputAudioFormat();
    }
    this.firstInput = currentFirstInput;
    this.encoderInputAudioFormat = currentEncoderInputAudioFormat;

    encoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);

    fallbackListener.onTransformationRequestFinalized(
        createFallbackTransformationRequest(
            transformationRequest,
            requestedEncoderFormat,
            /* actualFormat= */ encoder.getConfigurationFormat()));
  }

  @Override
  public AudioGraphInput getInput(EditedMediaItem editedMediaItem, Format format, int inputIndex)
      throws ExportException {
    if (!returnedFirstInput) {
      // First input initialized in constructor because output AudioFormat is needed.
      returnedFirstInput = true;
      checkState(format.equals(this.firstInputFormat));
      return firstInput;
    }
    return audioGraph.registerInput(editedMediaItem, format);
  }

  @Override
  public void release() {
    audioGraph.reset();
    encoder.release();
  }

  @Override
  protected boolean processDataUpToMuxer() throws ExportException {

    ByteBuffer audioGraphBuffer = audioGraph.getOutput();

    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (audioGraph.isEnded()) {
      DebugTraceUtil.logEvent(
          DebugTraceUtil.COMPONENT_AUDIO_GRAPH,
          DebugTraceUtil.EVENT_OUTPUT_ENDED,
          C.TIME_END_OF_SOURCE);
      queueEndOfStreamToEncoder();
      return false;
    }

    if (!audioGraphBuffer.hasRemaining()) {
      return false;
    }

    feedEncoder(audioGraphBuffer);
    return true;
  }

  @Override
  @Nullable
  protected Format getMuxerInputFormat() throws ExportException {
    return encoder.getOutputFormat();
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() throws ExportException {
    encoderOutputBuffer.data = encoder.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    encoderOutputBuffer.timeUs = checkNotNull(encoder.getOutputBufferInfo()).presentationTimeUs;
    encoderOutputBuffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
    return encoderOutputBuffer;
  }

  @Override
  protected void releaseMuxerInputBuffer() throws ExportException {
    encoder.releaseOutputBuffer(/* render= */ false);
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return encoder.isEnded();
  }

  /**
   * Feeds as much data as possible between the current position and limit of the specified {@link
   * ByteBuffer} to the encoder, and advances its position by the number of bytes fed.
   */
  private void feedEncoder(ByteBuffer inputBuffer) throws ExportException {
    ByteBuffer encoderInputBufferData = checkNotNull(encoderInputBuffer.data);
    int bufferLimit = inputBuffer.limit();
    inputBuffer.limit(min(bufferLimit, inputBuffer.position() + encoderInputBufferData.capacity()));
    encoderInputBufferData.put(inputBuffer);
    encoderInputBuffer.timeUs = getOutputAudioDurationUs();
    encoderTotalInputBytes += encoderInputBufferData.position();
    encoderInputBuffer.setFlags(0);
    encoderInputBuffer.flip();
    inputBuffer.limit(bufferLimit);
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  private void queueEndOfStreamToEncoder() throws ExportException {
    checkState(checkNotNull(encoderInputBuffer.data).position() == 0);
    encoderInputBuffer.timeUs = getOutputAudioDurationUs();
    encoderInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
    encoderInputBuffer.flip();
    // Queuing EOS should only occur with an empty buffer.
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  @Pure
  private static TransformationRequest createFallbackTransformationRequest(
      TransformationRequest transformationRequest, Format requestedFormat, Format actualFormat) {
    // TODO: b/255953153 - Consider including bitrate and other audio characteristics in the revised
    //  fallback.
    if (Objects.equals(requestedFormat.sampleMimeType, actualFormat.sampleMimeType)) {
      return transformationRequest;
    }
    return transformationRequest.buildUpon().setAudioMimeType(actualFormat.sampleMimeType).build();
  }

  private long getOutputAudioDurationUs() {
    long totalFramesWritten = encoderTotalInputBytes / encoderInputAudioFormat.bytesPerFrame;
    return (totalFramesWritten * C.MICROS_PER_SECOND) / encoderInputAudioFormat.sampleRate;
  }
}
