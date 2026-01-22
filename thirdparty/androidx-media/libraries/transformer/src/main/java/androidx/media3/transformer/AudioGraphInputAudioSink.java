/*
 * Copyright 2023 The Android Open Source Project
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
import static androidx.media3.common.util.Util.getPcmFrameSize;
import static androidx.media3.common.util.Util.sampleCountToDurationUs;

import android.media.AudioTrack;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.audio.AudioSink;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An {@link AudioSink} implementation that feeds an {@link AudioGraphInput}.
 *
 * <p>Should be used by {@link PlaybackAudioGraphWrapper}.
 */
/* package */ final class AudioGraphInputAudioSink implements AudioSink {

  /**
   * Controller for {@link AudioGraphInputAudioSink}.
   *
   * <p>All methods will be called on the playback thread of the ExoPlayer instance writing to this
   * sink.
   */
  public interface Controller {

    /**
     * Returns the {@link AudioGraphInput} instance associated with this {@linkplain
     * AudioGraphInputAudioSink sink}.
     *
     * <p>If AudioGraphInput is not available, callers should re-try again later.
     *
     * <p>Data {@linkplain #handleBuffer written} to the sink will be {@linkplain
     * AudioGraphInput#queueInputBuffer() queued} to the {@link AudioGraphInput}.
     *
     * @param editedMediaItem The first {@link EditedMediaItem} queued to the {@link
     *     AudioGraphInput}.
     * @param format The {@link Format} used to {@linkplain AudioGraphInputAudioSink#configure
     *     configure} the {@linkplain AudioGraphInputAudioSink sink}.
     * @return The {@link AudioGraphInput}, or {@code null} if the input is not available yet.
     * @throws ExportException If there is a problem initializing the {@linkplain AudioGraphInput
     *     input}.
     */
    @Nullable
    AudioGraphInput getAudioGraphInput(EditedMediaItem editedMediaItem, Format format)
        throws ExportException;

    /**
     * Returns the position (in microseconds) that should be {@linkplain
     * AudioSink#getCurrentPositionUs returned} by this sink.
     *
     * @param sourceEnded Specify {@code true} if no more input buffers will be provided.
     * @return The playback position relative to the start of playback, in microseconds.
     */
    long getCurrentPositionUs(boolean sourceEnded);
  }

  private final Controller controller;

  @Nullable private AudioGraphInput outputGraphInput;
  @Nullable private Format currentInputFormat;
  private boolean inputStreamEnded;
  private boolean signalledEndOfStream;
  @Nullable private EditedMediaItemInfo currentEditedMediaItemInfo;
  private long offsetToCompositionTimeUs;
  private long inputPositionUs;

  public AudioGraphInputAudioSink(Controller controller) {
    this.controller = controller;
  }

  /**
   * Informs the audio sink there is a change on the {@link EditedMediaItem} currently rendered by
   * the renderer.
   *
   * @param editedMediaItem The {@link EditedMediaItem}.
   * @param offsetToCompositionTimeUs The offset to add to the audio buffer timestamps to convert
   *     them to the composition time, in microseconds.
   * @param isLastInSequence Whether this is the last item in the sequence.
   */
  public void onMediaItemChanged(
      EditedMediaItem editedMediaItem, long offsetToCompositionTimeUs, boolean isLastInSequence) {
    currentEditedMediaItemInfo = new EditedMediaItemInfo(editedMediaItem, isLastInSequence);
    this.offsetToCompositionTimeUs = offsetToCompositionTimeUs;
  }

  // AudioSink methods

  @Override
  public void configure(Format inputFormat, int specifiedBufferSize, @Nullable int[] outputChannels)
      throws ConfigurationException {
    checkArgument(supportsFormat(inputFormat));
    EditedMediaItem editedMediaItem = checkStateNotNull(currentEditedMediaItemInfo).editedMediaItem;
    // TODO: b/303029969 - Evaluate throwing vs ignoring for null outputChannels.
    checkArgument(outputChannels == null);
    currentInputFormat = inputFormat;

    // During playback, AudioGraphInput doesn't know the full media duration upfront due to seeking.
    // Pass in C.TIME_UNSET to AudioGraphInput.onMediaItemChanged.
    if (outputGraphInput != null) {
      outputGraphInput.onMediaItemChanged(
          editedMediaItem, /* durationUs= */ C.TIME_UNSET, currentInputFormat, /* isLast= */ false);
    }
  }

  @Override
  public boolean isEnded() {
    if (currentInputFormat == null) { // Sink not configured.
      return inputStreamEnded;
    }

    return inputStreamEnded && getCompositionPlayerPositionUs() >= inputPositionUs;
  }

  @Override
  public boolean handleBuffer(
      ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount)
      throws InitializationException {
    checkState(!inputStreamEnded);

    EditedMediaItem editedMediaItem = checkStateNotNull(currentEditedMediaItemInfo).editedMediaItem;
    if (outputGraphInput == null) {

      AudioGraphInput outputGraphInput;
      try {
        outputGraphInput =
            controller.getAudioGraphInput(editedMediaItem, checkStateNotNull(currentInputFormat));
      } catch (ExportException e) {
        throw new InitializationException(
            "Error creating AudioGraphInput",
            AudioTrack.STATE_UNINITIALIZED,
            currentInputFormat,
            /* isRecoverable= */ false,
            e);
      }
      if (outputGraphInput == null) {
        return false;
      }

      this.outputGraphInput = outputGraphInput;
      this.outputGraphInput.onMediaItemChanged(
          editedMediaItem, /* durationUs= */ C.TIME_UNSET, currentInputFormat, /* isLast= */ false);
    }

    return handleBufferInternal(buffer, presentationTimeUs, /* flags= */ 0);
  }

  @Override
  public void playToEndOfStream() {
    inputStreamEnded = true;
    if (currentInputFormat == null) { // Sink not configured.
      return;
    }
    // Queue end-of-stream only if playing the last media item in the sequence.
    if (!signalledEndOfStream && checkStateNotNull(currentEditedMediaItemInfo).isLastInSequence) {
      signalledEndOfStream =
          handleBufferInternal(
              EMPTY_BUFFER, C.TIME_END_OF_SOURCE, /* flags= */ C.BUFFER_FLAG_END_OF_STREAM);
    }
  }

  @Override
  public @SinkFormatSupport int getFormatSupport(Format format) {
    if (Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_RAW)
        && format.pcmEncoding == C.ENCODING_PCM_16BIT) {
      return SINK_FORMAT_SUPPORTED_DIRECTLY;
    }

    return SINK_FORMAT_UNSUPPORTED;
  }

  @Override
  public boolean supportsFormat(Format format) {
    return getFormatSupport(format) == SINK_FORMAT_SUPPORTED_DIRECTLY;
  }

  @Override
  public boolean hasPendingData() {
    return false;
  }

  @Override
  public long getCurrentPositionUs(boolean sourceEnded) {
    if (isEnded()) {
      return inputPositionUs;
    }
    return getCompositionPlayerPositionUs();
  }

  @Override
  public void play() {}

  @Override
  public void pause() {}

  @Override
  public void flush() {
    inputStreamEnded = false;
    signalledEndOfStream = false;
  }

  @Override
  public void reset() {
    flush();
    currentInputFormat = null;
    currentEditedMediaItemInfo = null;
  }

  // Unsupported interface functionality.

  @Override
  public void setListener(AudioSink.Listener listener) {}

  @Override
  public void handleDiscontinuity() {}

  @Override
  public void setAudioAttributes(AudioAttributes audioAttributes) {}

  @Nullable
  @Override
  public AudioAttributes getAudioAttributes() {
    return null;
  }

  @Override
  public long getAudioTrackBufferSizeUs() {
    return C.TIME_UNSET;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {}

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return PlaybackParameters.DEFAULT;
  }

  @Override
  public void enableTunnelingV21() {}

  @Override
  public void disableTunneling() {}

  @Override
  public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {}

  @Override
  public boolean getSkipSilenceEnabled() {
    return false;
  }

  @Override
  public void setAudioSessionId(int audioSessionId) {}

  @Override
  public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {}

  @Override
  public void setVolume(float volume) {}

  // Internal methods

  private long getCompositionPlayerPositionUs() {
    long currentPositionUs = controller.getCurrentPositionUs(/* sourceEnded= */ inputStreamEnded);
    if (currentPositionUs != CURRENT_POSITION_NOT_SET) {
      // Reset the position to the one expected by the player.
      currentPositionUs -= offsetToCompositionTimeUs;
    }
    return currentPositionUs;
  }

  private boolean handleBufferInternal(ByteBuffer buffer, long presentationTimeUs, int flags) {
    checkStateNotNull(currentInputFormat);
    checkState(!signalledEndOfStream);
    AudioGraphInput outputGraphInput = checkNotNull(this.outputGraphInput);

    @Nullable DecoderInputBuffer outputBuffer = outputGraphInput.getInputBuffer();
    if (outputBuffer == null) {
      return false;
    }
    int bytesToWrite = buffer.remaining();
    outputBuffer.ensureSpaceForWrite(bytesToWrite);
    checkNotNull(outputBuffer.data).put(buffer).flip();
    outputBuffer.timeUs =
        presentationTimeUs == C.TIME_END_OF_SOURCE
            ? C.TIME_END_OF_SOURCE
            : presentationTimeUs + offsetToCompositionTimeUs;
    outputBuffer.setFlags(flags);

    boolean bufferQueued = outputGraphInput.queueInputBuffer();
    if (bufferQueued) {
      Format currentInputFormat = checkNotNull(this.currentInputFormat);
      inputPositionUs =
          presentationTimeUs
              + sampleCountToDurationUs(
                  /* sampleCount= */ bytesToWrite
                      / getPcmFrameSize(
                          currentInputFormat.pcmEncoding, currentInputFormat.channelCount),
                  /* sampleRate= */ currentInputFormat.sampleRate);
    }
    return bufferQueued;
  }

  private static final class EditedMediaItemInfo {
    public final EditedMediaItem editedMediaItem;
    public final boolean isLastInSequence;

    public EditedMediaItemInfo(EditedMediaItem editedMediaItem, boolean isLastInSequence) {
      this.editedMediaItem = editedMediaItem;
      this.isLastInSequence = isLastInSequence;
    }
  }
}
