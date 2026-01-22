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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.ExportException.ERROR_CODE_UNSPECIFIED;
import static androidx.media3.transformer.SampleConsumer.INPUT_RESULT_END_OF_STREAM;
import static androidx.media3.transformer.SampleConsumer.INPUT_RESULT_TRY_AGAIN_LATER;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static androidx.media3.transformer.TransformerUtil.getValidColor;
import static java.lang.Math.min;
import static java.lang.Math.round;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import com.google.common.collect.ImmutableMap;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * An {@link AssetLoader} implementation that loads raw audio and/or video data.
 *
 * <p>Typically instantiated in a custom {@link AssetLoader.Factory} saving a reference to the
 * created {@link RawAssetLoader}.
 *
 * <p>Provide raw audio data as input by calling {@link #queueAudioData}. This method must always be
 * called from the same thread, which can be any thread.
 *
 * <p>Provide video frames as input by calling {@link #queueInputTexture}, then {@link
 * #signalEndOfVideoInput() signal the end of input} when finished. These two methods must be called
 * from the same thread, which can be any thread.
 *
 * <p>All other methods are for internal use only and must never be called.
 */
@UnstableApi
public final class RawAssetLoader implements AssetLoader {
  private final EditedMediaItem editedMediaItem;
  private final Listener assetLoaderListener;
  @Nullable private final Format audioFormat;
  @Nullable private final Format videoFormat;
  @Nullable private final OnInputFrameProcessedListener frameProcessedListener;

  private @MonotonicNonNull SampleConsumer audioSampleConsumer;
  private @MonotonicNonNull SampleConsumer videoSampleConsumer;
  private @Transformer.ProgressState int progressState;
  private boolean isVideoTrackAdded;
  private boolean isAudioTrackAdded;
  private boolean isAudioEndOfStreamSignaled;
  private boolean isVideoEndOfStreamSignaled;

  // Read on app's thread and written on internal thread.
  private volatile boolean isStarted;
  // Read on internal thread and written on app's thread.
  private volatile long lastQueuedAudioPresentationTimeUs;
  // Read on internal thread and written on app's thread.
  private volatile long lastQueuedVideoPresentationTimeUs;

  /**
   * Creates an instance.
   *
   * @param editedMediaItem The {@link EditedMediaItem} for which raw data is provided. The {@link
   *     EditedMediaItem#durationUs} must be set.
   * @param assetLoaderListener Listener for asset loading events.
   * @param audioFormat The audio format, or {@code null} if only video data is provided.
   * @param videoFormat The video format, or {@code null} if only audio data is provided. The {@link
   *     Format#width} and the {@link Format#height} must be set.
   * @param frameProcessedListener Listener for the event when a frame has been processed, or {@code
   *     null} if only audio data is provided. The listener receives a GL sync object (if supported)
   *     to allow reusing the texture after it's no longer in use.
   */
  public RawAssetLoader(
      EditedMediaItem editedMediaItem,
      Listener assetLoaderListener,
      @Nullable Format audioFormat,
      @Nullable Format videoFormat,
      @Nullable OnInputFrameProcessedListener frameProcessedListener) {
    checkArgument(audioFormat != null || videoFormat != null);
    checkArgument(
        videoFormat == null
            || (videoFormat.height != Format.NO_VALUE && videoFormat.width != Format.NO_VALUE));
    this.editedMediaItem = editedMediaItem;
    this.assetLoaderListener = assetLoaderListener;
    this.audioFormat = audioFormat;
    this.videoFormat =
        videoFormat != null
            ? videoFormat
                .buildUpon()
                .setColorInfo(getValidColor(videoFormat.colorInfo))
                .setSampleMimeType(MimeTypes.VIDEO_RAW)
                .build()
            : null;
    this.frameProcessedListener = frameProcessedListener;
    progressState = PROGRESS_STATE_NOT_STARTED;
    lastQueuedAudioPresentationTimeUs = Long.MAX_VALUE;
    lastQueuedVideoPresentationTimeUs = Long.MAX_VALUE;
  }

  @Override
  public void start() {
    progressState =
        editedMediaItem.durationUs == C.TIME_UNSET
            ? PROGRESS_STATE_UNAVAILABLE
            : PROGRESS_STATE_AVAILABLE;
    assetLoaderListener.onDurationUs(editedMediaItem.durationUs);
    // The constructor guarantees at least one track is present.
    int trackCount = 1;
    if (audioFormat != null && videoFormat != null) {
      trackCount = 2;
    }
    assetLoaderListener.onTrackCount(trackCount);
    isStarted = true;
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      long lastTimestampUs =
          min(lastQueuedAudioPresentationTimeUs, lastQueuedVideoPresentationTimeUs);
      if (lastTimestampUs == Long.MAX_VALUE) {
        lastTimestampUs = 0;
      }
      progressHolder.progress = round((lastTimestampUs / (float) editedMediaItem.durationUs) * 100);
    }
    return progressState;
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    return ImmutableMap.of();
  }

  @Override
  public void release() {
    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  /**
   * Attempts to provide an input texture.
   *
   * <p>Must be called on the same thread as {@link #signalEndOfVideoInput}.
   *
   * @param texId The ID of the texture to queue.
   * @param presentationTimeUs The presentation time for the texture, in microseconds.
   * @return Whether the texture was successfully queued. If {@code false}, the caller should try
   *     again later.
   */
  public boolean queueInputTexture(int texId, long presentationTimeUs) {
    checkState(!isVideoEndOfStreamSignaled);
    try {
      if (!isVideoTrackAdded) {
        if (!isStarted) {
          return false;
        }
        assetLoaderListener.onTrackAdded(checkNotNull(videoFormat), SUPPORTED_OUTPUT_TYPE_DECODED);
        isVideoTrackAdded = true;
      }
      if (videoSampleConsumer == null) {
        @Nullable
        SampleConsumer videoSampleConsumer =
            assetLoaderListener.onOutputFormat(checkNotNull(videoFormat));
        if (videoSampleConsumer == null) {
          return false;
        } else {
          this.videoSampleConsumer = videoSampleConsumer;
          videoSampleConsumer.setOnInputFrameProcessedListener(
              checkNotNull(frameProcessedListener));
        }
      }
      @SampleConsumer.InputResult
      int result = videoSampleConsumer.queueInputTexture(texId, presentationTimeUs);
      if (result == INPUT_RESULT_TRY_AGAIN_LATER) {
        return false;
      }
      if (result == INPUT_RESULT_END_OF_STREAM) {
        isVideoEndOfStreamSignaled = true;
      }
      lastQueuedVideoPresentationTimeUs = presentationTimeUs;
      return true;
    } catch (ExportException e) {
      assetLoaderListener.onError(e);
    } catch (RuntimeException e) {
      assetLoaderListener.onError(ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
    }
    return false;
  }

  /**
   * Signals that no further input frames will be rendered.
   *
   * <p>Must be called on the same thread as {@link #queueInputTexture}.
   */
  public void signalEndOfVideoInput() {
    try {
      if (!isVideoEndOfStreamSignaled) {
        isVideoEndOfStreamSignaled = true;
        checkNotNull(videoSampleConsumer).signalEndOfVideoInput();
      }
    } catch (RuntimeException e) {
      assetLoaderListener.onError(ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
    }
  }

  /**
   * Attempts to provide raw audio data.
   *
   * @param audioData The raw audio data. The {@link ByteBuffer} can be reused after calling this
   *     method.
   * @param presentationTimeUs The presentation time for the raw audio data, in microseconds.
   * @param isLast Signals the last audio data.
   * @return Whether the raw audio data was successfully queued. If {@code false}, the caller should
   *     try again later.
   */
  public boolean queueAudioData(ByteBuffer audioData, long presentationTimeUs, boolean isLast) {
    checkState(!isAudioEndOfStreamSignaled);
    if (!isStarted) {
      return false;
    }
    try {
      if (!isAudioTrackAdded) {
        assetLoaderListener.onTrackAdded(checkNotNull(audioFormat), SUPPORTED_OUTPUT_TYPE_DECODED);
        isAudioTrackAdded = true;
      }
      if (audioSampleConsumer == null) {
        @Nullable
        SampleConsumer audioSampleConsumer =
            assetLoaderListener.onOutputFormat(checkNotNull(audioFormat));
        if (audioSampleConsumer == null) {
          return false;
        } else {
          this.audioSampleConsumer = audioSampleConsumer;
        }
      }
      DecoderInputBuffer decoderInputBuffer = audioSampleConsumer.getInputBuffer();
      if (decoderInputBuffer == null) {
        return false;
      }
      decoderInputBuffer.ensureSpaceForWrite(audioData.remaining());
      decoderInputBuffer.data.put(audioData).flip();
      if (isLast) {
        decoderInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
      }

      if (audioSampleConsumer.queueInputBuffer()) {
        lastQueuedAudioPresentationTimeUs = presentationTimeUs;
        isAudioEndOfStreamSignaled = isLast;
        return true;
      }
    } catch (ExportException e) {
      assetLoaderListener.onError(e);
    } catch (RuntimeException e) {
      assetLoaderListener.onError(ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
    }
    return false;
  }
}
