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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.effect.DebugTraceUtil.COMPONENT_ASSET_LOADER;
import static androidx.media3.effect.DebugTraceUtil.EVENT_INPUT_FORMAT;
import static androidx.media3.effect.DebugTraceUtil.EVENT_OUTPUT_FORMAT;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.TransformerUtil.getProcessedTrackType;

import android.graphics.Bitmap;
import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.effect.DebugTraceUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * An {@link AssetLoader} that is composed of a {@linkplain EditedMediaItemSequence sequence} of
 * non-overlapping {@linkplain AssetLoader asset loaders}.
 */
/* package */ final class SequenceAssetLoader implements AssetLoader, AssetLoader.Listener {

  private static final Format FORCE_AUDIO_TRACK_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_AAC)
          .setSampleRate(44100)
          .setChannelCount(2)
          .build();

  private final List<EditedMediaItem> editedMediaItems;
  private final boolean isLooping;
  private final boolean forceAudioTrack;
  private final Factory assetLoaderFactory;
  private final CompositionSettings compositionSettings;
  private final Listener sequenceAssetLoaderListener;
  private final HandlerWrapper handler;

  /**
   * A mapping from track types to {@link SampleConsumer} instances.
   *
   * <p>This map never contains more than 2 entries, as the only track types allowed are audio and
   * video.
   */
  private final Map<Integer, SampleConsumerWrapper> sampleConsumersByTrackType;

  /**
   * A mapping from track types to {@link OnMediaItemChangedListener} instances.
   *
   * <p>This map never contains more than 2 entries, as the only track types allowed are audio and
   * video.
   */
  private final Map<Integer, OnMediaItemChangedListener> mediaItemChangedListenersByTrackType;

  private final ImmutableList.Builder<ExportResult.ProcessedInput> processedInputsBuilder;
  private final AtomicInteger reportedTrackCount;
  private final AtomicInteger nonEndedTrackCount;

  private boolean isCurrentAssetFirstAsset;
  private int currentMediaItemIndex;
  private AssetLoader currentAssetLoader;
  private boolean isTrackCountReported;
  private boolean decodeAudio;
  private boolean decodeVideo;
  private int sequenceLoopCount;
  private int processedInputsSize;
  private @MonotonicNonNull Format currentAudioInputFormat;
  private @MonotonicNonNull Format currentVideoInputFormat;

  // Accessed when switching asset loader.
  private volatile boolean released;

  private volatile long currentAssetDurationUs;
  private volatile long currentAssetDurationAfterEffectsAppliedUs;
  private volatile long maxSequenceDurationUs;
  private volatile boolean isMaxSequenceDurationUsFinal;

  public SequenceAssetLoader(
      EditedMediaItemSequence sequence,
      boolean forceAudioTrack,
      Factory assetLoaderFactory,
      CompositionSettings compositionSettings,
      Listener listener,
      Clock clock,
      Looper looper) {
    editedMediaItems = sequence.editedMediaItems;
    isLooping = sequence.isLooping;
    this.forceAudioTrack = forceAudioTrack;
    this.assetLoaderFactory = new GapInterceptingAssetLoaderFactory(assetLoaderFactory);
    this.compositionSettings = compositionSettings;
    sequenceAssetLoaderListener = listener;
    handler = clock.createHandler(looper, /* callback= */ null);
    sampleConsumersByTrackType = new HashMap<>();
    mediaItemChangedListenersByTrackType = new HashMap<>();
    processedInputsBuilder = new ImmutableList.Builder<>();
    reportedTrackCount = new AtomicInteger();
    nonEndedTrackCount = new AtomicInteger();
    isCurrentAssetFirstAsset = true;
    // It's safe to use "this" because we don't start the AssetLoader before exiting the
    // constructor.
    @SuppressWarnings("nullness:argument.type.incompatible")
    AssetLoader currentAssetLoader =
        this.assetLoaderFactory.createAssetLoader(
            editedMediaItems.get(0), looper, /* listener= */ this, compositionSettings);
    this.currentAssetLoader = currentAssetLoader;
  }

  // Methods called from TransformerInternal thread.

  @Override
  public void start() {
    currentAssetLoader.start();
    if (editedMediaItems.size() > 1 || isLooping) {
      sequenceAssetLoaderListener.onDurationUs(C.TIME_UNSET);
    }
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    if (isLooping) {
      return Transformer.PROGRESS_STATE_UNAVAILABLE;
    }
    int progressState = currentAssetLoader.getProgress(progressHolder);
    int mediaItemCount = editedMediaItems.size();
    if (mediaItemCount == 1 || progressState == PROGRESS_STATE_NOT_STARTED) {
      return progressState;
    }

    int progress = currentMediaItemIndex * 100 / mediaItemCount;
    if (progressState == PROGRESS_STATE_AVAILABLE) {
      progress += progressHolder.progress / mediaItemCount;
    }
    progressHolder.progress = progress;
    return PROGRESS_STATE_AVAILABLE;
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    return currentAssetLoader.getDecoderNames();
  }

  /**
   * Returns the partially or entirely {@linkplain ExportResult.ProcessedInput processed inputs}.
   */
  public ImmutableList<ExportResult.ProcessedInput> getProcessedInputs() {
    addCurrentProcessedInput();
    return processedInputsBuilder.build();
  }

  @Override
  public void release() {
    currentAssetLoader.release();
    released = true;
  }

  private void addCurrentProcessedInput() {
    if ((sequenceLoopCount * editedMediaItems.size() + currentMediaItemIndex)
        >= processedInputsSize) {
      MediaItem mediaItem = editedMediaItems.get(currentMediaItemIndex).mediaItem;
      ImmutableMap<Integer, String> decoders = getDecoderNames();
      processedInputsBuilder.add(
          new ExportResult.ProcessedInput(
              mediaItem,
              currentAssetDurationUs,
              currentAudioInputFormat,
              currentVideoInputFormat,
              decoders.get(C.TRACK_TYPE_AUDIO),
              decoders.get(C.TRACK_TYPE_VIDEO)));
      processedInputsSize++;
    }
  }

  // Methods called from AssetLoader threads.

  /**
   * Adds an {@link OnMediaItemChangedListener} for the given track type.
   *
   * <p>There can't be more than one {@link OnMediaItemChangedListener} for the same track type.
   *
   * <p>Must be called from the thread used by the current {@link AssetLoader} to pass data to the
   * {@link SampleConsumer}.
   *
   * @param onMediaItemChangedListener The {@link OnMediaItemChangedListener}.
   * @param trackType The {@link C.TrackType} for which to listen to {@link MediaItem} change
   *     events. Must be {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}.
   */
  public void addOnMediaItemChangedListener(
      OnMediaItemChangedListener onMediaItemChangedListener, @C.TrackType int trackType) {
    checkArgument(trackType == C.TRACK_TYPE_AUDIO || trackType == C.TRACK_TYPE_VIDEO);
    checkArgument(mediaItemChangedListenersByTrackType.get(trackType) == null);
    mediaItemChangedListenersByTrackType.put(trackType, onMediaItemChangedListener);
  }

  @Override
  public boolean onTrackAdded(Format inputFormat, @SupportedOutputTypes int supportedOutputTypes) {
    boolean isAudio = getProcessedTrackType(inputFormat.sampleMimeType) == C.TRACK_TYPE_AUDIO;
    DebugTraceUtil.logEvent(
        COMPONENT_ASSET_LOADER,
        EVENT_INPUT_FORMAT,
        C.TIME_UNSET,
        "%s:%s",
        isAudio ? "audio" : "video",
        inputFormat);

    if (isAudio) {
      currentAudioInputFormat = inputFormat;
    } else {
      currentVideoInputFormat = inputFormat;
    }

    if (!isCurrentAssetFirstAsset) {
      boolean decode = isAudio ? decodeAudio : decodeVideo;
      if (decode) {
        checkArgument((supportedOutputTypes & SUPPORTED_OUTPUT_TYPE_DECODED) != 0);
      } else {
        checkArgument((supportedOutputTypes & SUPPORTED_OUTPUT_TYPE_ENCODED) != 0);
      }
      return decode;
    }

    boolean addForcedAudioTrack = forceAudioTrack && reportedTrackCount.get() == 1 && !isAudio;

    if (!isTrackCountReported) {
      int trackCount = reportedTrackCount.get() + (addForcedAudioTrack ? 1 : 0);
      sequenceAssetLoaderListener.onTrackCount(trackCount);
      isTrackCountReported = true;
    }

    boolean decodeOutput =
        sequenceAssetLoaderListener.onTrackAdded(inputFormat, supportedOutputTypes);

    if (isAudio) {
      decodeAudio = decodeOutput;
    } else {
      decodeVideo = decodeOutput;
    }

    if (addForcedAudioTrack) {
      sequenceAssetLoaderListener.onTrackAdded(
          FORCE_AUDIO_TRACK_FORMAT, SUPPORTED_OUTPUT_TYPE_DECODED);
      decodeAudio = true;
    }

    return decodeOutput;
  }

  @Nullable
  @Override
  public SampleConsumerWrapper onOutputFormat(Format format) throws ExportException {
    @C.TrackType int trackType = getProcessedTrackType(format.sampleMimeType);
    DebugTraceUtil.logEvent(
        COMPONENT_ASSET_LOADER,
        EVENT_OUTPUT_FORMAT,
        C.TIME_UNSET,
        "%s:%s",
        Util.getTrackTypeString(trackType),
        format);

    SampleConsumerWrapper sampleConsumer;
    if (isCurrentAssetFirstAsset) {
      @Nullable
      SampleConsumer wrappedSampleConsumer = sequenceAssetLoaderListener.onOutputFormat(format);
      if (wrappedSampleConsumer == null) {
        return null;
      }
      sampleConsumer = new SampleConsumerWrapper(wrappedSampleConsumer, trackType);
      sampleConsumersByTrackType.put(trackType, sampleConsumer);

      if (forceAudioTrack && reportedTrackCount.get() == 1 && trackType == C.TRACK_TYPE_VIDEO) {
        SampleConsumer wrappedAudioSampleConsumer =
            checkStateNotNull(
                sequenceAssetLoaderListener.onOutputFormat(
                    FORCE_AUDIO_TRACK_FORMAT
                        .buildUpon()
                        .setSampleMimeType(MimeTypes.AUDIO_RAW)
                        .setPcmEncoding(C.ENCODING_PCM_16BIT)
                        .build()));
        sampleConsumersByTrackType.put(
            C.TRACK_TYPE_AUDIO,
            new SampleConsumerWrapper(wrappedAudioSampleConsumer, C.TRACK_TYPE_AUDIO));
      }
    } else {
      // TODO: b/270533049 - Remove the check below when implementing blank video frames generation.
      boolean videoTrackDisappeared =
          reportedTrackCount.get() == 1
              && trackType == C.TRACK_TYPE_AUDIO
              && sampleConsumersByTrackType.size() == 2;
      checkState(
          !videoTrackDisappeared,
          "Inputs with no video track are not supported when the output contains a video track");
      sampleConsumer =
          checkStateNotNull(
              sampleConsumersByTrackType.get(trackType),
              Util.formatInvariant(
                  "The preceding MediaItem does not contain any track of type %d. If the"
                      + " Composition contains a sequence that starts with items without audio"
                      + " tracks (like images), followed by items with audio tracks,"
                      + " Composition.Builder.experimentalSetForceAudioTrack() needs to be set to"
                      + " true.",
                  trackType));
    }
    onMediaItemChanged(trackType, format);
    if (reportedTrackCount.get() == 1 && sampleConsumersByTrackType.size() == 2) {
      for (Map.Entry<Integer, SampleConsumerWrapper> entry :
          sampleConsumersByTrackType.entrySet()) {
        int outputTrackType = entry.getKey();
        if (trackType != outputTrackType) {
          onMediaItemChanged(outputTrackType, /* outputFormat= */ null);
        }
      }
    }
    return sampleConsumer;
  }

  private void onMediaItemChanged(int trackType, @Nullable Format outputFormat) {
    @Nullable
    OnMediaItemChangedListener onMediaItemChangedListener =
        mediaItemChangedListenersByTrackType.get(trackType);
    if (onMediaItemChangedListener == null) {
      return;
    }

    EditedMediaItem editedMediaItem = editedMediaItems.get(currentMediaItemIndex);

    onMediaItemChangedListener.onMediaItemChanged(
        editedMediaItem,
        /* durationUs= */ (trackType == C.TRACK_TYPE_AUDIO && isLooping && decodeAudio)
            ? C.TIME_UNSET
            : currentAssetDurationUs,
        /* decodedFormat= */ editedMediaItem.isGap() ? null : outputFormat,
        /* isLast= */ isLastMediaItemInSequence());
  }

  // Methods called from any thread.

  /**
   * Sets the maximum {@link EditedMediaItemSequence} duration in the {@link Composition}.
   *
   * <p>The duration passed is the current maximum duration. This method can be called multiple
   * times as this duration increases. Indeed, a sequence duration will increase during an export
   * when a new {@link MediaItem} is loaded, which can increase the maximum sequence duration.
   *
   * <p>Can be called from any thread.
   *
   * @param maxSequenceDurationUs The current maximum sequence duration, in microseconds.
   * @param isFinal Whether the duration passed is final. Setting this value to {@code true} means
   *     that the duration passed will not change anymore during the entire export.
   */
  public void setMaxSequenceDurationUs(long maxSequenceDurationUs, boolean isFinal) {
    this.maxSequenceDurationUs = maxSequenceDurationUs;
    isMaxSequenceDurationUsFinal = isFinal;
  }

  @Override
  public void onDurationUs(long durationUs) {
    checkArgument(
        durationUs != C.TIME_UNSET || isLastMediaItemInSequence(),
        "Could not retrieve required duration for EditedMediaItem " + currentMediaItemIndex);
    currentAssetDurationAfterEffectsAppliedUs =
        editedMediaItems.get(currentMediaItemIndex).getDurationAfterEffectsApplied(durationUs);
    currentAssetDurationUs = durationUs;
    if (editedMediaItems.size() == 1 && !isLooping) {
      sequenceAssetLoaderListener.onDurationUs(currentAssetDurationAfterEffectsAppliedUs);
    }
  }

  @Override
  public void onTrackCount(int trackCount) {
    reportedTrackCount.set(trackCount);
    nonEndedTrackCount.set(trackCount);
  }

  @Override
  public void onError(ExportException exportException) {
    sequenceAssetLoaderListener.onError(exportException);
  }

  private boolean isLastMediaItemInSequence() {
    return currentMediaItemIndex == editedMediaItems.size() - 1;
  }

  // Classes accessed from AssetLoader threads.

  private final class SampleConsumerWrapper implements SampleConsumer {

    private final SampleConsumer sampleConsumer;
    private final @C.TrackType int trackType;

    private long totalDurationUs;
    private boolean audioLoopingEnded;
    private boolean videoLoopingEnded;

    public SampleConsumerWrapper(SampleConsumer sampleConsumer, @C.TrackType int trackType) {
      this.sampleConsumer = sampleConsumer;
      this.trackType = trackType;
    }

    @Nullable
    @Override
    public DecoderInputBuffer getInputBuffer() {
      return sampleConsumer.getInputBuffer();
    }

    @Override
    public boolean queueInputBuffer() {
      DecoderInputBuffer inputBuffer = checkStateNotNull(sampleConsumer.getInputBuffer());
      long globalTimestampUs = totalDurationUs + inputBuffer.timeUs;
      if (isLooping && (globalTimestampUs >= maxSequenceDurationUs || audioLoopingEnded)) {
        if (isMaxSequenceDurationUsFinal && !audioLoopingEnded) {
          checkNotNull(inputBuffer.data).limit(0);
          inputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
          // We know that queueInputBuffer() will always return true for the underlying
          // SampleConsumer so there is no need to handle the case where the sample wasn't queued.
          checkState(sampleConsumer.queueInputBuffer());
          audioLoopingEnded = true;
          nonEndedTrackCount.decrementAndGet();
        }
        return false;
      }

      if (inputBuffer.isEndOfStream()) {
        nonEndedTrackCount.decrementAndGet();
        if (!isLastMediaItemInSequence() || isLooping) {
          if (trackType == C.TRACK_TYPE_AUDIO && !isLooping && decodeAudio) {
            // Trigger silence generation (if needed) for a decoded audio track when end of stream
            // is first encountered. This helps us avoid a muxer deadlock when audio track is
            // shorter than video track. Not applicable for looping sequences.
            checkState(sampleConsumer.queueInputBuffer());
          } else {
            inputBuffer.clear();
            inputBuffer.timeUs = 0;
          }
          if (nonEndedTrackCount.get() == 0) {
            switchAssetLoader();
          }
          return true;
        }
      }

      checkState(sampleConsumer.queueInputBuffer());
      return true;
    }

    @Override
    public @InputResult int queueInputBitmap(
        Bitmap inputBitmap, TimestampIterator timestampIterator) {
      if (isLooping) {
        long lastOffsetUs = C.TIME_UNSET;
        while (timestampIterator.hasNext()) {
          long offsetUs = timestampIterator.next();
          if (totalDurationUs + offsetUs > maxSequenceDurationUs) {
            if (!isMaxSequenceDurationUsFinal) {
              return INPUT_RESULT_TRY_AGAIN_LATER;
            }
            if (lastOffsetUs == C.TIME_UNSET) {
              if (!videoLoopingEnded) {
                videoLoopingEnded = true;
                signalEndOfVideoInput();
                return INPUT_RESULT_END_OF_STREAM;
              }
              return INPUT_RESULT_TRY_AGAIN_LATER;
            }
            timestampIterator = new ClippingIterator(timestampIterator.copyOf(), lastOffsetUs);
            videoLoopingEnded = true;
            break;
          }
          lastOffsetUs = offsetUs;
        }
      }
      return sampleConsumer.queueInputBitmap(inputBitmap, timestampIterator.copyOf());
    }

    @Override
    public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
      sampleConsumer.setOnInputFrameProcessedListener(listener);
    }

    @Override
    public void setOnInputSurfaceReadyListener(Runnable runnable) {
      sampleConsumer.setOnInputSurfaceReadyListener(runnable);
    }

    @Override
    public @InputResult int queueInputTexture(int texId, long presentationTimeUs) {
      long globalTimestampUs = totalDurationUs + presentationTimeUs;
      if (isLooping && globalTimestampUs >= maxSequenceDurationUs) {
        if (isMaxSequenceDurationUsFinal && !videoLoopingEnded) {
          videoLoopingEnded = true;
          signalEndOfVideoInput();
          return INPUT_RESULT_END_OF_STREAM;
        }
        return INPUT_RESULT_TRY_AGAIN_LATER;
      }
      return sampleConsumer.queueInputTexture(texId, presentationTimeUs);
    }

    @Override
    public Surface getInputSurface() {
      return sampleConsumer.getInputSurface();
    }

    @Override
    public int getPendingVideoFrameCount() {
      return sampleConsumer.getPendingVideoFrameCount();
    }

    @Override
    public boolean registerVideoFrame(long presentationTimeUs) {
      long globalTimestampUs = totalDurationUs + presentationTimeUs;
      if (isLooping && globalTimestampUs >= maxSequenceDurationUs) {
        if (isMaxSequenceDurationUsFinal && !videoLoopingEnded) {
          videoLoopingEnded = true;
          signalEndOfVideoInput();
        }
        return false;
      }

      return sampleConsumer.registerVideoFrame(presentationTimeUs);
    }

    @Override
    public void signalEndOfVideoInput() {
      nonEndedTrackCount.decrementAndGet();
      boolean videoEnded = isLooping ? videoLoopingEnded : isLastMediaItemInSequence();
      if (videoEnded) {
        sampleConsumer.signalEndOfVideoInput();
      } else if (nonEndedTrackCount.get() == 0) {
        switchAssetLoader();
      }
    }

    private void onGapSignalled() {
      nonEndedTrackCount.decrementAndGet();
      if (!isLastMediaItemInSequence()) {
        switchAssetLoader();
      }
    }

    private void switchAssetLoader() {
      handler.post(
          () -> {
            try {
              if (released) {
                return;
              }
              addCurrentProcessedInput();
              totalDurationUs += currentAssetDurationAfterEffectsAppliedUs;
              currentAssetLoader.release();
              isCurrentAssetFirstAsset = false;
              currentMediaItemIndex++;
              if (currentMediaItemIndex == editedMediaItems.size()) {
                currentMediaItemIndex = 0;
                sequenceLoopCount++;
              }
              EditedMediaItem editedMediaItem = editedMediaItems.get(currentMediaItemIndex);
              currentAssetLoader =
                  assetLoaderFactory.createAssetLoader(
                      editedMediaItem,
                      checkNotNull(Looper.myLooper()),
                      /* listener= */ SequenceAssetLoader.this,
                      compositionSettings);
              currentAssetLoader.start();
            } catch (RuntimeException e) {
              onError(
                  ExportException.createForAssetLoader(e, ExportException.ERROR_CODE_UNSPECIFIED));
            }
          });
    }
  }

  /**
   * Wraps a {@link TimestampIterator}, providing all the values in the original timestamp iterator
   * (in the same order) up to and including the first occurrence of the {@code clippingValue}.
   */
  private static final class ClippingIterator implements TimestampIterator {

    private final TimestampIterator iterator;
    private final long clippingValue;
    private boolean hasReachedClippingValue;

    public ClippingIterator(TimestampIterator iterator, long clippingValue) {
      this.iterator = iterator;
      this.clippingValue = clippingValue;
    }

    @Override
    public boolean hasNext() {
      return !hasReachedClippingValue && iterator.hasNext();
    }

    @Override
    public long next() {
      checkState(hasNext());
      long next = iterator.next();
      if (clippingValue <= next) {
        hasReachedClippingValue = true;
      }
      return next;
    }

    @Override
    public TimestampIterator copyOf() {
      return new ClippingIterator(iterator.copyOf(), clippingValue);
    }
  }

  /**
   * Internally signals that the current asset is a {@linkplain
   * EditedMediaItemSequence.Builder#addGap(long) gap}, but does no loading or processing of media.
   *
   * <p>This component requires downstream components to handle generation of the gap media.
   */
  private final class GapSignalingAssetLoader implements AssetLoader {

    private static final int OUTPUT_FORMAT_RETRY_DELAY_MS = 10;

    private final long durationUs;
    private final Format trackFormat;
    private final Format decodedFormat;

    private boolean outputtedFormat;

    private GapSignalingAssetLoader(long durationUs) {
      this.durationUs = durationUs;
      this.trackFormat = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).build();
      this.decodedFormat =
          new Format.Builder()
              .setSampleMimeType(MimeTypes.AUDIO_RAW)
              .setSampleRate(44100)
              .setChannelCount(2)
              .setPcmEncoding(C.ENCODING_PCM_16BIT)
              .build();
    }

    @Override
    public void start() {
      onDurationUs(durationUs);
      onTrackCount(1);
      onTrackAdded(trackFormat, SUPPORTED_OUTPUT_TYPE_DECODED);
      outputFormatToSequenceAssetLoader();
    }

    @Override
    public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
      progressHolder.progress = outputtedFormat ? 99 : 0;
      return PROGRESS_STATE_AVAILABLE;
    }

    @Override
    public ImmutableMap<Integer, String> getDecoderNames() {
      return ImmutableMap.of();
    }

    @Override
    public void release() {}

    /** Outputs the gap format, scheduling to try again if unsuccessful. */
    private void outputFormatToSequenceAssetLoader() {
      try {
        if (outputtedFormat) {
          return;
        }

        @Nullable SampleConsumerWrapper sampleConsumerWrapper = onOutputFormat(decodedFormat);
        if (sampleConsumerWrapper != null) {
          outputtedFormat = true;
          sampleConsumerWrapper.onGapSignalled();
        } else {
          handler.postDelayed(
              this::outputFormatToSequenceAssetLoader, OUTPUT_FORMAT_RETRY_DELAY_MS);
        }

      } catch (ExportException e) {
        onError(e);
      } catch (RuntimeException e) {
        onError(ExportException.createForAssetLoader(e, ExportException.ERROR_CODE_UNSPECIFIED));
      }
    }
  }

  /**
   * Intercepts {@link AssetLoader.Factory} calls, when {@linkplain
   * EditedMediaItemSequence.Builder#addGap(long) a gap} is detected, otherwise forwards them to the
   * provided {@link AssetLoader.Factory}.
   *
   * <p>In the case that a gap is detected, a {@link GapSignalingAssetLoader} is returned.
   */
  private final class GapInterceptingAssetLoaderFactory implements AssetLoader.Factory {

    private final AssetLoader.Factory factory;

    public GapInterceptingAssetLoaderFactory(AssetLoader.Factory factory) {
      this.factory = factory;
    }

    @Override
    public AssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem,
        Looper looper,
        Listener listener,
        CompositionSettings compositionSettings) {
      if (editedMediaItem.isGap()) {
        return new GapSignalingAssetLoader(editedMediaItem.durationUs);
      }
      return factory.createAssetLoader(editedMediaItem, looper, listener, compositionSettings);
    }
  }
}
