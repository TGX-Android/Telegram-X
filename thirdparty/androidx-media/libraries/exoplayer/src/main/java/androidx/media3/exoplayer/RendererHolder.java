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
package androidx.media3.exoplayer;

import static androidx.media3.common.C.TRACK_TYPE_AUDIO;
import static androidx.media3.common.C.TRACK_TYPE_VIDEO;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.exoplayer.Renderer.MSG_TRANSFER_RESOURCES;
import static androidx.media3.exoplayer.Renderer.STATE_DISABLED;
import static androidx.media3.exoplayer.Renderer.STATE_ENABLED;
import static androidx.media3.exoplayer.Renderer.STATE_STARTED;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.metadata.MetadataRenderer;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/** Holds a {@link Renderer renderer}. */
/* package */ class RendererHolder {
  private final Renderer primaryRenderer;
  // Index of renderer in renderer list held by the {@link Player}.
  private final int index;
  @Nullable private final Renderer secondaryRenderer;
  private @RendererPrewarmingState int prewarmingState;
  private boolean primaryRequiresReset;
  private boolean secondaryRequiresReset;

  public RendererHolder(Renderer renderer, @Nullable Renderer secondaryRenderer, int index) {
    this.primaryRenderer = renderer;
    this.index = index;
    this.secondaryRenderer = secondaryRenderer;
    prewarmingState = RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY;
    primaryRequiresReset = false;
    secondaryRequiresReset = false;
  }

  public boolean hasSecondary() {
    return secondaryRenderer != null;
  }

  public void startPrewarming() {
    checkState(!isPrewarming());
    prewarmingState =
        isRendererEnabled(primaryRenderer)
            ? RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY
            : secondaryRenderer != null && isRendererEnabled(secondaryRenderer)
                ? RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY
                : RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY;
  }

  public boolean isPrewarming() {
    return isPrimaryRendererPrewarming() || isSecondaryRendererPrewarming();
  }

  public boolean isRendererPrewarming(int id) {
    boolean isPrewarmingPrimaryRenderer = isPrimaryRendererPrewarming() && id == index;
    boolean isPrewarmingSecondaryRenderer = isSecondaryRendererPrewarming() && id != index;
    return isPrewarmingPrimaryRenderer || isPrewarmingSecondaryRenderer;
  }

  private boolean isPrimaryRendererPrewarming() {
    return prewarmingState == RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY
        || prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY;
  }

  private boolean isSecondaryRendererPrewarming() {
    return prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY;
  }

  public int getEnabledRendererCount() {
    int result = 0;
    result += isRendererEnabled(primaryRenderer) ? 1 : 0;
    result += secondaryRenderer != null && isRendererEnabled(secondaryRenderer) ? 1 : 0;
    return result;
  }

  /**
   * Returns the track type that the renderer handles.
   *
   * @see Renderer#getTrackType()
   */
  public @C.TrackType int getTrackType() {
    return primaryRenderer.getTrackType();
  }

  /**
   * Returns reading position from the {@link Renderer} enabled on the {@link MediaPeriodHolder
   * media period}.
   *
   * <p>Call requires that {@link Renderer} is enabled on the provided {@link MediaPeriodHolder
   * media period}.
   *
   * @param period The {@link MediaPeriodHolder media period}
   * @return The {@link Renderer#getReadingPositionUs()} from the {@link Renderer} enabled on the
   *     {@link MediaPeriodHolder media period}.
   */
  public long getReadingPositionUs(@Nullable MediaPeriodHolder period) {
    return Objects.requireNonNull(getRendererReadingFromPeriod(period)).getReadingPositionUs();
  }

  /**
   * Invokes {@link Renderer#hasReadStreamToEnd()}.
   *
   * @see Renderer#hasReadStreamToEnd()
   */
  public boolean hasReadPeriodToEnd(MediaPeriodHolder mediaPeriodHolder) {
    Renderer renderer = checkNotNull(getRendererReadingFromPeriod(mediaPeriodHolder));
    return renderer.hasReadStreamToEnd();
  }

  /**
   * Signals to the renderer that the current {@link SampleStream} will be the final one supplied
   * before it is next disabled or reset.
   *
   * @see Renderer#setCurrentStreamFinal()
   * @param mediaPeriodHolder The {@link MediaPeriodHolder media period} containing the current
   *     stream.
   * @param streamEndPositionUs The position to stop rendering at or {@link C#LENGTH_UNSET} to
   *     render until the end of the current stream.
   */
  public void setCurrentStreamFinal(MediaPeriodHolder mediaPeriodHolder, long streamEndPositionUs) {
    Renderer renderer = checkNotNull(getRendererReadingFromPeriod(mediaPeriodHolder));
    setCurrentStreamFinalInternal(renderer, streamEndPositionUs);
  }

  /**
   * Maybe signal to the renderer that the old {@link SampleStream} will be the final one supplied
   * before it is next disabled or reset.
   *
   * @param oldTrackSelectorResult {@link TrackSelectorResult} containing the previous {@link
   *     SampleStream}.
   * @param newTrackSelectorResult {@link TrackSelectorResult} containing the next {@link
   *     SampleStream}.
   * @param streamEndPositionUs The position to stop rendering at or {@link C#LENGTH_UNSET} to
   *     render until the end of the current stream.
   */
  public void maybeSetOldStreamToFinal(
      TrackSelectorResult oldTrackSelectorResult,
      TrackSelectorResult newTrackSelectorResult,
      long streamEndPositionUs) {
    boolean oldRendererEnabled = oldTrackSelectorResult.isRendererEnabled(index);
    boolean newRendererEnabled = newTrackSelectorResult.isRendererEnabled(index);
    boolean isPrimaryOldRenderer =
        secondaryRenderer == null
            || prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY
            || (prewarmingState == RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY
                && isRendererEnabled(primaryRenderer));
    Renderer oldRenderer = isPrimaryOldRenderer ? primaryRenderer : checkNotNull(secondaryRenderer);
    if (oldRendererEnabled && !oldRenderer.isCurrentStreamFinal()) {
      boolean isNoSampleRenderer = getTrackType() == C.TRACK_TYPE_NONE;
      RendererConfiguration oldConfig = oldTrackSelectorResult.rendererConfigurations[index];
      RendererConfiguration newConfig = newTrackSelectorResult.rendererConfigurations[index];
      if (!newRendererEnabled
          || !Objects.equals(newConfig, oldConfig)
          || isNoSampleRenderer
          || isPrewarming()) {
        // The renderer will be disabled when transitioning to playing the next period, because
        // there's no new selection, or because a configuration change is required, or because
        // it's a no-sample renderer for which rendererOffsetUs should be updated only when
        // starting to play the next period, or there is a backup renderer that has already been
        // enabled for the following media item. Mark the SampleStream as final to play out any
        // remaining data.
        setCurrentStreamFinalInternal(oldRenderer, streamEndPositionUs);
      }
    }
  }

  /**
   * Calls {@link Renderer#setCurrentStreamFinal} on enabled {@link Renderer renderers} that are not
   * pre-warming.
   *
   * @see Renderer#setCurrentStreamFinal
   */
  public void setAllNonPrewarmingRendererStreamsFinal(long streamEndPositionUs) {
    if (isRendererEnabled(primaryRenderer)
        && prewarmingState != RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY
        && prewarmingState != RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY) {
      setCurrentStreamFinalInternal(primaryRenderer, streamEndPositionUs);
    }
    if (secondaryRenderer != null
        && isRendererEnabled(secondaryRenderer)
        && prewarmingState != RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY) {
      setCurrentStreamFinalInternal(secondaryRenderer, streamEndPositionUs);
    }
  }

  private void setCurrentStreamFinalInternal(Renderer renderer, long streamEndPositionUs) {
    renderer.setCurrentStreamFinal();
    if (renderer instanceof TextRenderer) {
      ((TextRenderer) renderer).setFinalStreamEndPositionUs(streamEndPositionUs);
    }
  }

  /**
   * Returns minimum amount of playback clock time that must pass in order for the {@link #render}
   * call to make progress.
   *
   * <p>Returns {@code Long.MAX_VALUE} if {@link Renderer renderers} are not enabled.
   *
   * @see Renderer#getDurationToProgressUs
   * @param rendererPositionUs The current render position in microseconds, measured at the start of
   *     the current iteration of the rendering loop.
   * @param rendererPositionElapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in
   *     microseconds, measured at the start of the current iteration of the rendering loop.
   * @return Minimum amount of playback clock time that must pass before renderer is able to make
   *     progress.
   */
  public long getMinDurationToProgressUs(
      long rendererPositionUs, long rendererPositionElapsedRealtimeUs) {
    long minDurationToProgress =
        isRendererEnabled(primaryRenderer)
            ? primaryRenderer.getDurationToProgressUs(
                rendererPositionUs, rendererPositionElapsedRealtimeUs)
            : Long.MAX_VALUE;
    if (secondaryRenderer != null && isRendererEnabled(secondaryRenderer)) {
      minDurationToProgress =
          min(
              minDurationToProgress,
              secondaryRenderer.getDurationToProgressUs(
                  rendererPositionUs, rendererPositionElapsedRealtimeUs));
    }
    return minDurationToProgress;
  }

  /**
   * Calls {@link Renderer#enableMayRenderStartOfStream} on enabled {@link Renderer renderers}.
   *
   * @see Renderer#enableMayRenderStartOfStream
   */
  public void enableMayRenderStartOfStream() {
    if (isRendererEnabled(primaryRenderer)) {
      primaryRenderer.enableMayRenderStartOfStream();
    } else if (secondaryRenderer != null && isRendererEnabled(secondaryRenderer)) {
      secondaryRenderer.enableMayRenderStartOfStream();
    }
  }

  /**
   * Calls {@link Renderer#setPlaybackSpeed} on the {@link Renderer renderers}.
   *
   * @see Renderer#setPlaybackSpeed
   */
  public void setPlaybackSpeed(float currentPlaybackSpeed, float targetPlaybackSpeed)
      throws ExoPlaybackException {
    primaryRenderer.setPlaybackSpeed(currentPlaybackSpeed, targetPlaybackSpeed);
    if (secondaryRenderer != null) {
      secondaryRenderer.setPlaybackSpeed(currentPlaybackSpeed, targetPlaybackSpeed);
    }
  }

  /**
   * Calls {@link Renderer#setTimeline} on the {@link Renderer renderers}.
   *
   * @see Renderer#setTimeline
   */
  public void setTimeline(Timeline timeline) {
    primaryRenderer.setTimeline(timeline);
    if (secondaryRenderer != null) {
      secondaryRenderer.setTimeline(timeline);
    }
  }

  /**
   * Returns true if all renderers have {@link Renderer#isEnded() ended}.
   *
   * @see Renderer#isEnded()
   * @return if all renderers have {@link Renderer#isEnded() ended}.
   */
  public boolean isEnded() {
    boolean renderersEnded = true;
    if (isRendererEnabled(primaryRenderer)) {
      renderersEnded &= primaryRenderer.isEnded();
    }
    if (secondaryRenderer != null && isRendererEnabled(secondaryRenderer)) {
      renderersEnded &= secondaryRenderer.isEnded();
    }
    return renderersEnded;
  }

  /**
   * Returns whether {@link Renderer} is enabled on a {@link MediaPeriodHolder media period}.
   *
   * @param period The {@link MediaPeriodHolder media period} to check.
   * @return Whether {@link Renderer} is enabled on a {@link MediaPeriodHolder media period}.
   */
  public boolean isReadingFromPeriod(@Nullable MediaPeriodHolder period) {
    return getRendererReadingFromPeriod(period) != null;
  }

  /**
   * Returns whether the {@link Renderer renderers} are still reading a {@link MediaPeriodHolder
   * media period}.
   *
   * @param periodHolder The {@link MediaPeriodHolder media period} to check.
   * @return true if {@link Renderer renderers} are reading the current reading period.
   */
  public boolean hasFinishedReadingFromPeriod(MediaPeriodHolder periodHolder) {
    return hasFinishedReadingFromPeriodInternal(periodHolder, primaryRenderer)
        && hasFinishedReadingFromPeriodInternal(periodHolder, secondaryRenderer);
  }

  private boolean hasFinishedReadingFromPeriodInternal(
      MediaPeriodHolder readingPeriodHolder, @Nullable Renderer renderer) {
    if (renderer == null) {
      return true;
    }
    SampleStream sampleStream = readingPeriodHolder.sampleStreams[index];
    if (renderer.getStream() != null
        && (renderer.getStream() != sampleStream
            || (sampleStream != null
                && !renderer.hasReadStreamToEnd()
                && !hasReachedServerSideInsertedAdsTransition(renderer, readingPeriodHolder)))) {
      // The current reading period is still being read by at least one renderer.
      MediaPeriodHolder followingPeriod = readingPeriodHolder.getNext();
      // If renderer is reading ahead as it was enabled early, then it is not 'reading' the
      // current reading period.
      return followingPeriod != null
          && followingPeriod.sampleStreams[index] == renderer.getStream();
    }
    return true;
  }

  private boolean hasReachedServerSideInsertedAdsTransition(
      Renderer renderer, MediaPeriodHolder reading) {
    MediaPeriodHolder nextPeriod = reading.getNext();
    // We can advance the reading period early once we read beyond the transition point in a
    // server-side inserted ads stream because we know the samples are read from the same underlying
    // stream. This shortcut is helpful in case the transition point moved and renderers already
    // read beyond the new transition point. But wait until the next period is actually prepared to
    // allow a seamless transition.
    return reading.info.isFollowedByTransitionToSameStream
        && nextPeriod != null
        && nextPeriod.prepared
        && (renderer instanceof TextRenderer // [internal: b/181312195]
            || renderer instanceof MetadataRenderer
            || renderer.getReadingPositionUs() >= nextPeriod.getStartPositionRendererTime());
  }

  /**
   * Calls {@link Renderer#render} on all enabled {@link Renderer renderers}.
   *
   * @param rendererPositionUs The current media time in microseconds, measured at the start of the
   *     current iteration of the rendering loop.
   * @param rendererPositionElapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in
   *     microseconds, measured at the start of the current iteration of the rendering loop.
   * @throws ExoPlaybackException If an error occurs.
   */
  public void render(long rendererPositionUs, long rendererPositionElapsedRealtimeUs)
      throws ExoPlaybackException {
    if (isRendererEnabled(primaryRenderer)) {
      primaryRenderer.render(rendererPositionUs, rendererPositionElapsedRealtimeUs);
    }
    if (secondaryRenderer != null && isRendererEnabled(secondaryRenderer)) {
      secondaryRenderer.render(rendererPositionUs, rendererPositionElapsedRealtimeUs);
    }
  }

  /**
   * Returns whether the renderers allow playback to continue.
   *
   * <p>Determine whether the renderer allows playback to continue. Playback can continue if the
   * renderer is ready or ended. Also continue playback if the renderer is reading ahead into the
   * next stream or is waiting for the next stream. This is to avoid getting stuck if tracks in the
   * current period have uneven durations and are still being read by another renderer. See:
   * https://github.com/google/ExoPlayer/issues/1874.
   *
   * @param playingPeriodHolder The currently playing media {@link MediaPeriodHolder period}.
   * @return whether renderer allows playback.
   */
  public boolean allowsPlayback(MediaPeriodHolder playingPeriodHolder) {
    Renderer renderer = getRendererReadingFromPeriod(playingPeriodHolder);
    return renderer == null
        || renderer.hasReadStreamToEnd()
        || renderer.isReady()
        || renderer.isEnded();
  }

  /**
   * Invokes {@link Renderer#maybeThrowStreamError()} for {@link Renderer} enabled on {@link
   * MediaPeriodHolder media period}.
   *
   * @see Renderer#maybeThrowStreamError()
   */
  public void maybeThrowStreamError(MediaPeriodHolder mediaPeriodHolder) throws IOException {
    checkNotNull(getRendererReadingFromPeriod(mediaPeriodHolder)).maybeThrowStreamError();
  }

  /**
   * Calls {@link Renderer#start()} on all enabled {@link Renderer renderers}.
   *
   * @throws ExoPlaybackException If an error occurs.
   */
  public void start() throws ExoPlaybackException {
    if (primaryRenderer.getState() == STATE_ENABLED
        && (prewarmingState != RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY)) {
      primaryRenderer.start();
    } else if (secondaryRenderer != null
        && secondaryRenderer.getState() == STATE_ENABLED
        && prewarmingState != RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY) {
      secondaryRenderer.start();
    }
  }

  /** Calls {@link Renderer#stop()} on all enabled {@link Renderer renderers}. */
  public void stop() {
    if (isRendererEnabled(primaryRenderer)) {
      ensureStopped(primaryRenderer);
    }
    if (secondaryRenderer != null && isRendererEnabled(secondaryRenderer)) {
      ensureStopped(secondaryRenderer);
    }
  }

  private void ensureStopped(Renderer renderer) {
    if (renderer.getState() == STATE_STARTED) {
      renderer.stop();
    }
  }

  /**
   * Enables the renderer to consume from the specified {@link SampleStream}.
   *
   * @see Renderer#enable
   * @param configuration The renderer configuration.
   * @param trackSelection The track selection for the {@link Renderer}.
   * @param stream The {@link SampleStream} from which the renderer should consume.
   * @param positionUs The player's current position.
   * @param joining Whether this renderer is being enabled to join an ongoing playback.
   * @param mayRenderStartOfStream Whether this renderer is allowed to render the start of the
   *     stream even if the state is not {@link Renderer#STATE_STARTED} yet.
   * @param startPositionUs The start position of the stream in renderer time (microseconds).
   * @param offsetUs The offset to be added to timestamps of buffers read from {@code stream} before
   *     they are rendered.
   * @param mediaPeriodId The {@link MediaSource.MediaPeriodId} of the {@link MediaPeriod} producing
   *     the {@code stream}.
   * @param mediaClock The {@link DefaultMediaClock} with which to call {@link
   *     DefaultMediaClock#onRendererEnabled(Renderer)}.
   * @throws ExoPlaybackException If an error occurs.
   */
  public void enable(
      RendererConfiguration configuration,
      ExoTrackSelection trackSelection,
      SampleStream stream,
      long positionUs,
      boolean joining,
      boolean mayRenderStartOfStream,
      long startPositionUs,
      long offsetUs,
      MediaSource.MediaPeriodId mediaPeriodId,
      DefaultMediaClock mediaClock)
      throws ExoPlaybackException {
    Format[] formats = getFormats(trackSelection);
    boolean enablePrimary =
        prewarmingState == RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY
            || prewarmingState == RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY
            || prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY;
    if (enablePrimary) {
      primaryRequiresReset = true;
      primaryRenderer.enable(
          configuration,
          formats,
          stream,
          positionUs,
          joining,
          mayRenderStartOfStream,
          startPositionUs,
          offsetUs,
          mediaPeriodId);
      mediaClock.onRendererEnabled(primaryRenderer);
    } else {
      secondaryRequiresReset = true;
      checkNotNull(secondaryRenderer)
          .enable(
              configuration,
              formats,
              stream,
              positionUs,
              joining,
              mayRenderStartOfStream,
              startPositionUs,
              offsetUs,
              mediaPeriodId);
      mediaClock.onRendererEnabled(secondaryRenderer);
    }
  }

  /**
   * Invokes {@link Renderer#handleMessage} on the {@link Renderer} enabled on the {@link
   * MediaPeriodHolder media period}.
   *
   * @see Renderer#handleMessage(int, Object)
   */
  public void handleMessage(
      @Renderer.MessageType int messageType,
      @Nullable Object message,
      MediaPeriodHolder mediaPeriod)
      throws ExoPlaybackException {
    Renderer renderer = checkNotNull(getRendererReadingFromPeriod(mediaPeriod));
    renderer.handleMessage(messageType, message);
  }

  /**
   * Stops and disables all {@link Renderer renderers}.
   *
   * @param mediaClock To call {@link DefaultMediaClock#onRendererDisabled} if disabling a {@link
   *     Renderer}.
   */
  public void disable(DefaultMediaClock mediaClock) throws ExoPlaybackException {
    disableRenderer(primaryRenderer, mediaClock);
    if (secondaryRenderer != null) {
      boolean shouldTransferResources =
          isRendererEnabled(secondaryRenderer)
              && prewarmingState != RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY;
      disableRenderer(secondaryRenderer, mediaClock);
      maybeResetRenderer(/* resetPrimary= */ false);
      if (shouldTransferResources) {
        transferResources(/* transferToPrimary= */ true);
      }
    }
    prewarmingState = RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY;
  }

  /** Handles transition of pre-warming state and resources. */
  public void maybeHandlePrewarmingTransition() throws ExoPlaybackException {
    if (prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY
        || prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY) {
      transferResources(
          /* transferToPrimary= */ prewarmingState
              == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY);
      prewarmingState =
          prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY
              ? RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY
              : RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_SECONDARY;
    } else if (prewarmingState == RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY) {
      prewarmingState = RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY;
    }
  }

  private void transferResources(boolean transferToPrimary) throws ExoPlaybackException {
    if (transferToPrimary) {
      checkNotNull(secondaryRenderer).handleMessage(MSG_TRANSFER_RESOURCES, primaryRenderer);
    } else {
      primaryRenderer.handleMessage(MSG_TRANSFER_RESOURCES, checkNotNull(secondaryRenderer));
    }
  }

  public void disablePrewarming(DefaultMediaClock mediaClock) {
    if (!isPrewarming()) {
      return;
    }
    boolean isPrewarmingPrimary =
        prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY
            || prewarmingState == RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY;
    boolean isSecondaryActiveRenderer =
        prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY;
    disableRenderer(
        isPrewarmingPrimary ? primaryRenderer : checkNotNull(secondaryRenderer), mediaClock);
    maybeResetRenderer(/* resetPrimary= */ isPrewarmingPrimary);
    prewarmingState =
        isSecondaryActiveRenderer
            ? RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_SECONDARY
            : RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY;
  }

  public void maybeDisableOrResetPosition(
      SampleStream sampleStream,
      DefaultMediaClock mediaClock,
      long rendererPositionUs,
      boolean streamReset)
      throws ExoPlaybackException {
    maybeDisableOrResetPositionInternal(
        primaryRenderer, sampleStream, mediaClock, rendererPositionUs, streamReset);
    if (secondaryRenderer != null) {
      maybeDisableOrResetPositionInternal(
          secondaryRenderer, sampleStream, mediaClock, rendererPositionUs, streamReset);
    }
  }

  private void maybeDisableOrResetPositionInternal(
      Renderer renderer,
      SampleStream sampleStream,
      DefaultMediaClock mediaClock,
      long rendererPositionUs,
      boolean streamReset)
      throws ExoPlaybackException {
    if (isRendererEnabled(renderer)) {
      if (sampleStream != renderer.getStream()) {
        // We need to disable the renderer.
        disableRenderer(renderer, mediaClock);
      } else if (streamReset) {
        // The renderer will continue to consume from its current stream, but needs to be reset.
        renderer.resetPosition(rendererPositionUs);
      }
    }
  }

  /**
   * Disable a {@link Renderer} if its enabled.
   *
   * <p>The {@link DefaultMediaClock#onRendererDisabled} callback will be invoked if the renderer is
   * disabled.
   *
   * @param renderer The {@link Renderer} to disable.
   * @param mediaClock The {@link DefaultMediaClock} to invoke {@link
   *     DefaultMediaClock#onRendererDisabled onRendererDisabled} with the provided {@code
   *     renderer}.
   */
  private void disableRenderer(Renderer renderer, DefaultMediaClock mediaClock) {
    checkState(primaryRenderer == renderer || secondaryRenderer == renderer);
    if (!isRendererEnabled(renderer)) {
      return;
    }
    mediaClock.onRendererDisabled(renderer);
    ensureStopped(renderer);
    renderer.disable();
  }

  /**
   * Invokes {@link Renderer#resetPosition} on the {@link Renderer} that is enabled on the provided
   * {@link MediaPeriodHolder media period}.
   *
   * @see Renderer#resetPosition
   */
  public void resetPosition(MediaPeriodHolder playingPeriod, long positionUs)
      throws ExoPlaybackException {
    Renderer renderer = getRendererReadingFromPeriod(playingPeriod);
    if (renderer != null) {
      renderer.resetPosition(positionUs);
    }
  }

  /**
   * Calls {@link Renderer#reset()} on all disabled {@link Renderer renderers} that must be reset.
   */
  public void reset() {
    if (!isRendererEnabled(primaryRenderer)) {
      maybeResetRenderer(/* resetPrimary= */ true);
    }
    if (secondaryRenderer != null && !isRendererEnabled(secondaryRenderer)) {
      maybeResetRenderer(/* resetPrimary= */ false);
    }
  }

  private void maybeResetRenderer(boolean resetPrimary) {
    if (resetPrimary) {
      if (primaryRequiresReset) {
        primaryRenderer.reset();
        primaryRequiresReset = false;
      }
    } else if (secondaryRequiresReset) {
      checkNotNull(secondaryRenderer).reset();
      secondaryRequiresReset = false;
    }
  }

  public int replaceStreamsOrDisableRendererForTransition(
      MediaPeriodHolder readingPeriodHolder,
      TrackSelectorResult newTrackSelectorResult,
      DefaultMediaClock mediaClock)
      throws ExoPlaybackException {
    int primaryRendererResult =
        replaceStreamsOrDisableRendererForTransitionInternal(
            primaryRenderer, readingPeriodHolder, newTrackSelectorResult, mediaClock);
    int secondaryRendererResult =
        replaceStreamsOrDisableRendererForTransitionInternal(
            secondaryRenderer, readingPeriodHolder, newTrackSelectorResult, mediaClock);
    return primaryRendererResult == REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED
        ? secondaryRendererResult
        : primaryRendererResult;
  }

  private int replaceStreamsOrDisableRendererForTransitionInternal(
      @Nullable Renderer renderer,
      MediaPeriodHolder readingPeriodHolder,
      TrackSelectorResult newTrackSelectorResult,
      DefaultMediaClock mediaClock)
      throws ExoPlaybackException {
    if (renderer == null
        || !isRendererEnabled(renderer)
        || (renderer == primaryRenderer && isPrimaryRendererPrewarming())
        || (renderer == secondaryRenderer && isSecondaryRendererPrewarming())) {
      return REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED;
    }
    boolean rendererIsReadingOldStream =
        renderer.getStream() != readingPeriodHolder.sampleStreams[index];
    boolean rendererShouldBeEnabled = newTrackSelectorResult.isRendererEnabled(index);
    if (rendererShouldBeEnabled && !rendererIsReadingOldStream) {
      // All done.
      return REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED;
    }
    if (!renderer.isCurrentStreamFinal()) {
      // The renderer stream is not final, so we can replace the sample streams immediately.
      Format[] formats = getFormats(newTrackSelectorResult.selections[index]);
      renderer.replaceStream(
          formats,
          checkNotNull(readingPeriodHolder.sampleStreams[index]),
          readingPeriodHolder.getStartPositionRendererTime(),
          readingPeriodHolder.getRendererOffset(),
          readingPeriodHolder.info.id);
      // Prevent sleeping across offload track transition else position won't get updated.
      return REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED
          | REPLACE_STREAMS_DISABLE_RENDERERS_DISABLE_OFFLOAD_SCHEDULING;
    } else if (renderer.isEnded()) {
      // The renderer has finished playback, so we can disable it now.
      disableRenderer(renderer, mediaClock);
      if (!rendererShouldBeEnabled || isPrewarming()) {
        maybeResetRenderer(/* resetPrimary= */ renderer == primaryRenderer);
      }
      return REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED;
    } else {
      // Need to wait until rendering of current item is finished.
      return 0;
    }
  }

  private static Format[] getFormats(@Nullable ExoTrackSelection newSelection) {
    // Build an array of formats contained by the selection.
    int length = newSelection != null ? newSelection.length() : 0;
    Format[] formats = new Format[length];
    for (int i = 0; i < length; i++) {
      formats[i] = checkNotNull(newSelection).getFormat(i);
    }
    return formats;
  }

  /** Calls {@link Renderer#release()} on all {@link Renderer renderers}. */
  public void release() {
    primaryRenderer.release();
    primaryRequiresReset = false;
    if (secondaryRenderer != null) {
      secondaryRenderer.release();
      secondaryRequiresReset = false;
    }
  }

  public void setVideoOutput(@Nullable Object videoOutput) throws ExoPlaybackException {
    if (getTrackType() != TRACK_TYPE_VIDEO) {
      return;
    }
    if (prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY
        || prewarmingState == RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_SECONDARY) {
      checkNotNull(secondaryRenderer).handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, videoOutput);
    } else {
      primaryRenderer.handleMessage(Renderer.MSG_SET_VIDEO_OUTPUT, videoOutput);
    }
  }

  /** Sets the volume on the renderer. */
  public void setVolume(float volume) throws ExoPlaybackException {
    if (getTrackType() != TRACK_TYPE_AUDIO) {
      return;
    }
    primaryRenderer.handleMessage(Renderer.MSG_SET_VOLUME, volume);
    if (secondaryRenderer != null) {
      secondaryRenderer.handleMessage(Renderer.MSG_SET_VOLUME, volume);
    }
  }

  public boolean isRendererEnabled() {
    boolean checkPrimary =
        prewarmingState == RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY
            || prewarmingState == RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY
            || prewarmingState == RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY;
    return checkPrimary
        ? isRendererEnabled(primaryRenderer)
        : isRendererEnabled(checkNotNull(secondaryRenderer));
  }

  private static boolean isRendererEnabled(Renderer renderer) {
    return renderer.getState() != STATE_DISABLED;
  }

  /**
   * Returns the {@link Renderer} that is enabled on the provided media {@link MediaPeriodHolder
   * period}.
   *
   * <p>Returns null if the renderer is not enabled on the requested period.
   *
   * @param period The {@link MediaPeriodHolder period} with which to retrieve the linked {@link
   *     Renderer}
   * @return {@link Renderer} enabled on the {@link MediaPeriodHolder period} or {@code null} if the
   *     renderer is not enabled on the provided period.
   */
  @Nullable
  private Renderer getRendererReadingFromPeriod(@Nullable MediaPeriodHolder period) {
    if (period == null || period.sampleStreams[index] == null) {
      return null;
    }
    if (primaryRenderer.getStream() == period.sampleStreams[index]) {
      return primaryRenderer;
    } else if (secondaryRenderer != null
        && secondaryRenderer.getStream() == period.sampleStreams[index]) {
      return secondaryRenderer;
    }
    return null;
  }

  /** Possible pre-warming states for primary and secondary renderers. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY,
    RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_SECONDARY,
    RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY,
    RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY,
    RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY
  })
  @interface RendererPrewarmingState {}

  /**
   * ExoPlayer is not currently transitioning between two enabled renderers for subsequent media
   * items and is using the primary renderer.
   */
  /* package */ static final int RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_PRIMARY = 0;

  /**
   * ExoPlayer is not currently transitioning between two enabled renderers for subsequent media
   * items and is using the secondary renderer.
   */
  /* package */ static final int RENDERER_PREWARMING_STATE_NOT_PREWARMING_USING_SECONDARY = 1;

  /**
   * ExoPlayer is currently pre-warming the primary renderer that is not being used for the current
   * media item for a subsequent media item.
   */
  /* package */ static final int RENDERER_PREWARMING_STATE_PREWARMING_PRIMARY = 2;

  /**
   * Both a primary and secondary renderer are enabled and ExoPlayer is transitioning to a media
   * item using the secondary renderer.
   */
  /* package */ static final int RENDERER_PREWARMING_STATE_TRANSITIONING_TO_SECONDARY = 3;

  /**
   * Both a primary and secondary renderer are enabled and ExoPlayer is transitioning to a media
   * item using the primary renderer.
   */
  /* package */ static final int RENDERER_PREWARMING_STATE_TRANSITIONING_TO_PRIMARY = 4;

  /** Results for calls to {@link #replaceStreamsOrDisableRendererForTransition}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
        REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED,
        REPLACE_STREAMS_DISABLE_RENDERERS_DISABLE_OFFLOAD_SCHEDULING
      })
  /* package */ @interface ReplaceStreamsOrDisableRendererResult {}

  /**
   * The call to {@link #replaceStreamsOrDisableRendererForTransition} has completed processing
   * {@link Renderer#replaceStream} or {@link Renderer#disable()} on all renderers enabled on the
   * current playing period.
   */
  /* package */ static final int REPLACE_STREAMS_DISABLE_RENDERERS_COMPLETED = 1;

  /**
   * The call to {@link #replaceStreamsOrDisableRendererForTransition} invoked {@link
   * Renderer#replaceStream} and so therefore offload should be disabled until after the media
   * transition.
   */
  /* package */ static final int REPLACE_STREAMS_DISABLE_RENDERERS_DISABLE_OFFLOAD_SCHEDULING =
      1 << 1;
}
