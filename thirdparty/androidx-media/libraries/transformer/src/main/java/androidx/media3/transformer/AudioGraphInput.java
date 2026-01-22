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

import static androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT;
import static androidx.media3.transformer.AudioGraph.isInputAudioFormatValid;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessingPipeline;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import androidx.media3.common.audio.ChannelMixingAudioProcessor;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.common.audio.SpeedChangingAudioProcessor;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processes a single sequential stream of PCM audio samples.
 *
 * <p>Supports changes to the input {@link Format} and {@link Effects} on {@linkplain
 * #onMediaItemChanged item boundaries}.
 *
 * <p>Class has thread-safe support for input and processing happening on different threads. In that
 * case, one is the upstream SampleConsumer "input" thread, and the other is the main internal
 * "processing" thread.
 */
/* package */ final class AudioGraphInput implements GraphInput {
  private static final long MAX_AUDIO_DRIFT_ALLOWED_US = 2000;
  private static final int MAX_INPUT_BUFFER_COUNT = 10;
  private final AudioFormat outputAudioFormat;

  private final Queue<DecoderInputBuffer> availableInputBuffers;
  private final Queue<DecoderInputBuffer> pendingInputBuffers;
  private final Queue<MediaItemChange> pendingMediaItemChanges;
  private final AtomicLong startTimeUs;

  // silentAudioGenerator.audioFormat must match the current media item's input format.
  private SilentAudioGenerator silentAudioGenerator;
  @Nullable private DecoderInputBuffer currentInputBufferBeingOutput;
  private AudioProcessingPipeline audioProcessingPipeline;
  private boolean processedFirstMediaItemChange;
  private boolean receivedEndOfStreamFromInput;
  private boolean queueEndOfStreamAfterSilence;
  private boolean inputBlocked;
  private long currentItemExpectedInputDurationUs;
  private long currentItemInputBytesRead;
  private boolean currentItemSilenceAppended;
  private boolean isCurrentItemLast;

  /**
   * Creates an instance.
   *
   * @param requestedOutputAudioFormat The requested {@linkplain AudioFormat properties} of the
   *     output audio. {@linkplain Format#NO_VALUE Unset} fields are ignored.
   * @param editedMediaItem The initial {@link EditedMediaItem}.
   * @param inputFormat The initial {@link Format} of audio input data.
   */
  public AudioGraphInput(
      AudioFormat requestedOutputAudioFormat, EditedMediaItem editedMediaItem, Format inputFormat)
      throws UnhandledAudioFormatException {
    AudioFormat inputAudioFormat = new AudioFormat(inputFormat);
    checkArgument(isInputAudioFormatValid(inputAudioFormat), /* errorMessage= */ inputAudioFormat);

    // TODO: b/323148735 - Use improved buffer assignment logic.
    availableInputBuffers = new ConcurrentLinkedQueue<>();
    ByteBuffer emptyBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
    for (int i = 0; i < MAX_INPUT_BUFFER_COUNT; i++) {
      DecoderInputBuffer inputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT);
      inputBuffer.data = emptyBuffer;
      availableInputBuffers.add(inputBuffer);
    }
    pendingInputBuffers = new ConcurrentLinkedQueue<>();
    pendingMediaItemChanges = new ConcurrentLinkedQueue<>();
    silentAudioGenerator = new SilentAudioGenerator(inputAudioFormat);
    audioProcessingPipeline =
        configureProcessing(
            editedMediaItem, inputFormat, inputAudioFormat, requestedOutputAudioFormat);
    // APP configuration not active until flush called. getOutputAudioFormat based on active config.
    audioProcessingPipeline.flush();
    outputAudioFormat = audioProcessingPipeline.getOutputAudioFormat();
    checkArgument(
        outputAudioFormat.encoding == C.ENCODING_PCM_16BIT, /* errorMessage= */ outputAudioFormat);
    startTimeUs = new AtomicLong(C.TIME_UNSET);
    currentItemExpectedInputDurationUs = C.TIME_UNSET;
  }

  /** Returns the {@link AudioFormat} of {@linkplain #getOutput() output buffers}. */
  public AudioFormat getOutputAudioFormat() {
    return outputAudioFormat;
  }

  /**
   * Returns a {@link ByteBuffer} of output, in the {@linkplain #getOutputAudioFormat() output audio
   * format}.
   *
   * <p>Should only be called by the processing thread.
   *
   * @throws UnhandledAudioFormatException If the configuration of underlying components fails as a
   *     result of upstream changes.
   */
  public ByteBuffer getOutput() throws UnhandledAudioFormatException {
    ByteBuffer outputBuffer = getOutputInternal();

    if (outputBuffer.hasRemaining()) {
      return outputBuffer;
    }

    if (!hasDataToOutput() && !pendingMediaItemChanges.isEmpty()) {
      configureForPendingMediaItemChange();
    }

    return EMPTY_BUFFER;
  }

  /**
   * {@inheritDoc}
   *
   * <p>When durationUs is {@link C#TIME_UNSET}, silence generation is disabled.
   *
   * <p>Should only be called by the input thread.
   */
  @Override
  public void onMediaItemChanged(
      EditedMediaItem editedMediaItem,
      long durationUs,
      @Nullable Format decodedFormat,
      boolean isLast) {
    if (decodedFormat == null) {
      checkState(
          durationUs != C.TIME_UNSET,
          "Could not generate silent audio because duration is unknown.");
    } else {
      checkState(MimeTypes.isAudio(decodedFormat.sampleMimeType));
      AudioFormat audioFormat = new AudioFormat(decodedFormat);
      checkState(isInputAudioFormatValid(audioFormat), /* errorMessage= */ audioFormat);
    }
    pendingMediaItemChanges.add(
        new MediaItemChange(editedMediaItem, durationUs, decodedFormat, isLast));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Should only be called by the input thread.
   */
  @Override
  @Nullable
  public DecoderInputBuffer getInputBuffer() {
    if (inputBlocked || !pendingMediaItemChanges.isEmpty()) {
      return null;
    }
    return availableInputBuffers.peek();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Should only be called by the input thread.
   */
  @Override
  public boolean queueInputBuffer() {
    if (inputBlocked) {
      return false;
    }
    checkState(pendingMediaItemChanges.isEmpty());
    DecoderInputBuffer inputBuffer = availableInputBuffers.remove();
    pendingInputBuffers.add(inputBuffer);
    startTimeUs.compareAndSet(
        /* expectedValue= */ C.TIME_UNSET, /* newValue= */ inputBuffer.timeUs);
    return true;
  }

  /** Returns the stream start time in microseconds, or {@link C#TIME_UNSET} if unknown. */
  public long getStartTimeUs() {
    return startTimeUs.get();
  }

  /**
   * Instructs the {@code AudioGraphInput} to not queue any input buffer.
   *
   * <p>Should only be called if the input thread and processing thread are the same.
   */
  public void blockInput() {
    inputBlocked = true;
  }

  /**
   * Unblocks incoming data if {@linkplain #blockInput() blocked}.
   *
   * <p>Should only be called if the input thread and processing thread are the same.
   */
  public void unblockInput() {
    inputBlocked = false;
  }

  /**
   * Clears any pending data.
   *
   * <p>If an {@linkplain #getInputBuffer() input buffer} has been retrieved without being queued,
   * it shouldn't be used after calling this method.
   *
   * <p>Should only be called if the input thread and processing thread are the same.
   */
  public void flush() {
    pendingMediaItemChanges.clear();
    processedFirstMediaItemChange = true;
    if (!availableInputBuffers.isEmpty()) {
      // Clear first available buffer in case the caller wrote data in the input buffer without
      // queueing it.
      clearAndAddToAvailableBuffers(availableInputBuffers.remove());
    }
    if (currentInputBufferBeingOutput != null) {
      clearAndAddToAvailableBuffers(currentInputBufferBeingOutput);
      currentInputBufferBeingOutput = null;
    }
    while (!pendingInputBuffers.isEmpty()) {
      clearAndAddToAvailableBuffers(pendingInputBuffers.remove());
    }
    checkState(availableInputBuffers.size() == MAX_INPUT_BUFFER_COUNT);
    silentAudioGenerator.flush();
    audioProcessingPipeline.flush();
    receivedEndOfStreamFromInput = false;
    queueEndOfStreamAfterSilence = false;
    startTimeUs.set(C.TIME_UNSET);
    currentItemExpectedInputDurationUs = C.TIME_UNSET;
    currentItemInputBytesRead = 0;
    currentItemSilenceAppended = false;
    isCurrentItemLast = false;
  }

  /**
   * Releases any underlying resources.
   *
   * <p>Should only be called by the processing thread.
   */
  public void release() {
    audioProcessingPipeline.reset();
  }

  /**
   * Returns whether the input has ended and all queued data has been output.
   *
   * <p>Should only be called on the processing thread.
   */
  public boolean isEnded() {
    if (hasDataToOutput()) {
      return false;
    }
    if (!pendingMediaItemChanges.isEmpty()) {
      return false;
    }
    if (currentItemExpectedInputDurationUs != C.TIME_UNSET) {
      // When exporting a sequence of items, we rely on currentItemExpectedInputDurationUs and
      // receivedEndOfStreamFromInput to determine silence padding.
      // Use isCurrentItemLast to correctly propagate end of stream once for the entire sequence.
      return isCurrentItemLast && (receivedEndOfStreamFromInput || queueEndOfStreamAfterSilence);
    }
    // For a looping sequence, currentItemExpectedInputDurationUs is unset, and
    // there isn't a last item -- end of stream is passed through directly.
    return receivedEndOfStreamFromInput || queueEndOfStreamAfterSilence;
  }

  private ByteBuffer getOutputInternal() {
    if (!processedFirstMediaItemChange) {
      return EMPTY_BUFFER;
    }

    if (!audioProcessingPipeline.isOperational()) {
      return feedOutputFromInput();
    }

    // Ensure APP progresses as much as possible.
    while (feedProcessingPipelineFromInput()) {}
    return audioProcessingPipeline.getOutput();
  }

  private boolean feedProcessingPipelineFromInput() {
    if (silentAudioGenerator.hasRemaining()) {
      ByteBuffer inputData = silentAudioGenerator.getBuffer();
      audioProcessingPipeline.queueInput(inputData);
      if (inputData.hasRemaining()) {
        return false;
      }
      if (!silentAudioGenerator.hasRemaining()) {
        audioProcessingPipeline.queueEndOfStream();
        return false;
      }
      return true;
    }

    @Nullable DecoderInputBuffer pendingInputBuffer = pendingInputBuffers.peek();
    if (pendingInputBuffer == null) {
      if (!pendingMediaItemChanges.isEmpty()) {
        if (shouldAppendSilence()) {
          appendSilence();
          return true;
        }
        audioProcessingPipeline.queueEndOfStream();
      }
      return false;
    }

    if (pendingInputBuffer.isEndOfStream()) {
      if (shouldAppendSilence()) {
        appendSilence();
        clearAndAddToAvailableBuffers(pendingInputBuffers.remove());
        return true;
      }
      audioProcessingPipeline.queueEndOfStream();
      receivedEndOfStreamFromInput = true;
      clearAndAddToAvailableBuffers(pendingInputBuffers.remove());
      return false;
    }

    ByteBuffer inputData = checkNotNull(pendingInputBuffer.data);
    long bytesRemaining = inputData.remaining();
    audioProcessingPipeline.queueInput(inputData);
    long bytesConsumed = bytesRemaining - inputData.remaining();
    currentItemInputBytesRead += bytesConsumed;
    if (inputData.hasRemaining()) {
      return false;
    }
    clearAndAddToAvailableBuffers(pendingInputBuffers.remove());
    return true;
  }

  private ByteBuffer feedOutputFromInput() {
    if (silentAudioGenerator.hasRemaining()) {
      return silentAudioGenerator.getBuffer();
    }

    // When output is fed directly from input, the output ByteBuffer is linked to a specific
    // DecoderInputBuffer. Therefore it must be consumed by the downstream component before it can
    // be used for fresh input.
    if (currentInputBufferBeingOutput != null) {
      ByteBuffer data = checkStateNotNull(currentInputBufferBeingOutput.data);
      if (data.hasRemaining()) {
        // Currently output data has not been consumed, return it.
        return data;
      }
      clearAndAddToAvailableBuffers(checkStateNotNull(currentInputBufferBeingOutput));
      currentInputBufferBeingOutput = null;
    }

    @Nullable DecoderInputBuffer currentInputBuffer = pendingInputBuffers.poll();
    if (currentInputBuffer == null) {
      if (!pendingMediaItemChanges.isEmpty() && shouldAppendSilence()) {
        appendSilence();
      }
      return EMPTY_BUFFER;
    }
    @Nullable ByteBuffer currentInputBufferData = currentInputBuffer.data;
    receivedEndOfStreamFromInput = currentInputBuffer.isEndOfStream();

    // If there is no input data, make buffer available, ensuring underlying data reference is not
    // kept. Data associated with EOS buffer is ignored.
    if (currentInputBufferData == null
        || !currentInputBufferData.hasRemaining()
        || receivedEndOfStreamFromInput) {
      clearAndAddToAvailableBuffers(currentInputBuffer);
      if (receivedEndOfStreamFromInput && shouldAppendSilence()) {
        appendSilence();
      }
      return EMPTY_BUFFER;
    }

    currentInputBufferBeingOutput = currentInputBuffer;
    // Bytes from currentInputBufferBeingOutput will be read over multiple calls to this method.
    // Add all bytes now, this line will be reached only once per input buffer.
    currentItemInputBytesRead += currentInputBufferData.remaining();
    return currentInputBufferData;
  }

  private boolean hasDataToOutput() {
    if (!processedFirstMediaItemChange) {
      return false;
    }

    if (currentInputBufferBeingOutput != null
        && currentInputBufferBeingOutput.data != null
        && currentInputBufferBeingOutput.data.hasRemaining()) {
      return true;
    }
    if (silentAudioGenerator.hasRemaining()) {
      return true;
    }
    if (!pendingInputBuffers.isEmpty()) {
      return true;
    }
    if (audioProcessingPipeline.isOperational() && !audioProcessingPipeline.isEnded()) {
      return true;
    }
    return false;
  }

  private void clearAndAddToAvailableBuffers(DecoderInputBuffer inputBuffer) {
    inputBuffer.clear();
    inputBuffer.timeUs = 0;
    availableInputBuffers.add(inputBuffer);
  }

  /**
   * Configures the graph based on the pending {@linkplain #onMediaItemChanged media item change}.
   *
   * <p>Before configuration, all {@linkplain #hasDataToOutput() pending data} must be consumed
   * through {@link #getOutput()}.
   */
  private void configureForPendingMediaItemChange() throws UnhandledAudioFormatException {
    MediaItemChange pendingChange = checkStateNotNull(pendingMediaItemChanges.poll());

    currentItemInputBytesRead = 0;
    isCurrentItemLast = pendingChange.isLast;
    currentItemSilenceAppended = false;
    AudioFormat pendingAudioFormat;
    if (pendingChange.format != null) {
      currentItemExpectedInputDurationUs = pendingChange.durationUs;
      pendingAudioFormat = new AudioFormat(pendingChange.format);
      silentAudioGenerator = new SilentAudioGenerator(pendingAudioFormat);
    } else { // Generating silence
      // No audio track. Generate silence based on video track duration after applying effects.
      if (pendingChange.editedMediaItem.effects.audioProcessors.isEmpty()) {
        // No audio track and no effects.
        // Generate silence based on video track duration after applying effects.
        currentItemExpectedInputDurationUs =
            pendingChange.editedMediaItem.getDurationAfterEffectsApplied(pendingChange.durationUs);
      } else {
        // No audio track, but effects are present.
        // Generate audio track based on video duration, and apply effects.
        currentItemExpectedInputDurationUs = pendingChange.durationUs;
      }
      pendingAudioFormat = silentAudioGenerator.audioFormat;
      startTimeUs.compareAndSet(/* expectedValue= */ C.TIME_UNSET, /* newValue= */ 0);
      appendSilence();
    }

    if (processedFirstMediaItemChange) {
      // APP is configured in constructor for first media item.
      audioProcessingPipeline =
          configureProcessing(
              pendingChange.editedMediaItem,
              pendingChange.format,
              pendingAudioFormat,
              /* requiredOutputAudioFormat= */ outputAudioFormat);
    }
    audioProcessingPipeline.flush();
    receivedEndOfStreamFromInput = false;
    processedFirstMediaItemChange = true;
  }

  private boolean shouldAppendSilence() {
    return !currentItemSilenceAppended
        && currentItemExpectedInputDurationUs != C.TIME_UNSET
        && currentItemExpectedInputDurationUs - currentItemActualInputDurationUs()
            > MAX_AUDIO_DRIFT_ALLOWED_US;
  }

  private void appendSilence() {
    silentAudioGenerator.addSilence(
        currentItemExpectedInputDurationUs - currentItemActualInputDurationUs());
    currentItemSilenceAppended = true;
    if (isCurrentItemLast) {
      queueEndOfStreamAfterSilence = true;
    }
  }

  private long currentItemActualInputDurationUs() {
    long samplesOutput = currentItemInputBytesRead / silentAudioGenerator.audioFormat.bytesPerFrame;
    return Util.sampleCountToDurationUs(samplesOutput, silentAudioGenerator.audioFormat.sampleRate);
  }

  /**
   * Returns a new configured {@link AudioProcessingPipeline}.
   *
   * <p>Additional {@link AudioProcessor} instances may be added to the returned pipeline that:
   *
   * <ul>
   *   <li>Handle {@linkplain EditedMediaItem#flattenForSlowMotion slow motion flattening}.
   *   <li>Modify the audio stream to match the {@code requiredOutputAudioFormat}.
   * </ul>
   */
  private static AudioProcessingPipeline configureProcessing(
      EditedMediaItem editedMediaItem,
      @Nullable Format inputFormat,
      AudioFormat inputAudioFormat,
      AudioFormat requiredOutputAudioFormat)
      throws UnhandledAudioFormatException {
    ImmutableList.Builder<AudioProcessor> audioProcessors = new ImmutableList.Builder<>();
    if (editedMediaItem.flattenForSlowMotion
        && inputFormat != null
        && inputFormat.metadata != null) {
      audioProcessors.add(
          new SpeedChangingAudioProcessor(new SegmentSpeedProvider(inputFormat.metadata)));
    }
    audioProcessors.addAll(editedMediaItem.effects.audioProcessors);

    if (requiredOutputAudioFormat.sampleRate != Format.NO_VALUE) {
      SonicAudioProcessor sampleRateChanger = new SonicAudioProcessor();
      sampleRateChanger.setOutputSampleRateHz(requiredOutputAudioFormat.sampleRate);
      audioProcessors.add(sampleRateChanger);
    }

    // TODO: b/262706549 - Handle channel mixing with AudioMixer.
    // ChannelMixingMatrix.create only has defaults for mono/stereo input/output.
    if (requiredOutputAudioFormat.channelCount == 1
        || requiredOutputAudioFormat.channelCount == 2) {
      ChannelMixingAudioProcessor channelCountChanger = new ChannelMixingAudioProcessor();
      channelCountChanger.putChannelMixingMatrix(
          ChannelMixingMatrix.create(
              /* inputChannelCount= */ 1, requiredOutputAudioFormat.channelCount));
      channelCountChanger.putChannelMixingMatrix(
          ChannelMixingMatrix.create(
              /* inputChannelCount= */ 2, requiredOutputAudioFormat.channelCount));
      audioProcessors.add(channelCountChanger);
    }

    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(audioProcessors.build());
    AudioFormat outputAudioFormat = audioProcessingPipeline.configure(inputAudioFormat);
    if ((requiredOutputAudioFormat.sampleRate != Format.NO_VALUE
            && requiredOutputAudioFormat.sampleRate != outputAudioFormat.sampleRate)
        || (requiredOutputAudioFormat.channelCount != Format.NO_VALUE
            && requiredOutputAudioFormat.channelCount != outputAudioFormat.channelCount)
        || (requiredOutputAudioFormat.encoding != Format.NO_VALUE
            && requiredOutputAudioFormat.encoding != outputAudioFormat.encoding)) {
      throw new UnhandledAudioFormatException(
          "Audio can not be modified to match downstream format", inputAudioFormat);
    }

    return audioProcessingPipeline;
  }

  private static final class MediaItemChange {
    public final EditedMediaItem editedMediaItem;
    public final long durationUs;
    @Nullable public final Format format;
    public final boolean isLast;

    public MediaItemChange(
        EditedMediaItem editedMediaItem, long durationUs, @Nullable Format format, boolean isLast) {
      this.editedMediaItem = editedMediaItem;
      this.durationUs = durationUs;
      this.format = format;
      this.isLast = isLast;
    }
  }
}
