/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.Allocator;

/** Controls buffering of media. */
@UnstableApi
public interface LoadControl {

  /**
   * Information about the current playback context and the {@link MediaPeriod} for which {@link
   * LoadControl} methods are called.
   */
  final class Parameters {
    /** The {@linkplain PlayerId ID of the player}. */
    public final PlayerId playerId;

    /** The current {@link Timeline} of the player. */
    public final Timeline timeline;

    /**
     * The {@link MediaPeriodId} of the affected {@link MediaPeriod} in the current {@link
     * #timeline}.
     */
    public final MediaPeriodId mediaPeriodId;

    /**
     * The current playback position in microseconds, relative to the start of the affected {@link
     * MediaPeriod} identified by {@link #mediaPeriodId}. If playback of this period has not yet
     * started, the value will be negative and equal in magnitude to the duration of any media in
     * previous periods still to be played.
     */
    public final long playbackPositionUs;

    /** The total duration of media that's currently buffered. */
    public final long bufferedDurationUs;

    /** The current factor by which playback is sped up. */
    public final float playbackSpeed;

    /** Whether playback should proceed when {@link Player#STATE_READY}. */
    public final boolean playWhenReady;

    /**
     * Whether the player is rebuffering. A rebuffer is defined to be caused by buffer depletion
     * rather than a user action. Hence this parameter is false during initial buffering and when
     * buffering as a result of a seek operation.
     */
    public final boolean rebuffering;

    /**
     * The desired playback position offset to the live edge in microseconds, or {@link
     * C#TIME_UNSET} if the media is not a live stream or no offset is configured.
     */
    public final long targetLiveOffsetUs;

    /**
     * Sets the time at which the last rebuffering occurred, in milliseconds since boot including
     * time spent in sleep.
     *
     * <p>The time base used is the same as that measured by {@link SystemClock#elapsedRealtime}.
     *
     * <p><b>Note:</b> If rebuffer events are not known when the load is started or continued, or if
     * no rebuffering has occurred, or if there have been any user interactions such as seeking or
     * stopping the player, the value will be set to {@link C#TIME_UNSET}.
     */
    public final long lastRebufferRealtimeMs;

    /**
     * Creates parameters for {@link LoadControl} methods.
     *
     * @param playerId See {@link #playerId}.
     * @param timeline See {@link #timeline}.
     * @param mediaPeriodId See {@link #mediaPeriodId}.
     * @param playbackPositionUs See {@link #playbackPositionUs}.
     * @param bufferedDurationUs See {@link #bufferedDurationUs}.
     * @param playbackSpeed See {@link #playbackSpeed}.
     * @param playWhenReady See {@link #playWhenReady}.
     * @param rebuffering See {@link #rebuffering}.
     * @param targetLiveOffsetUs See {@link #targetLiveOffsetUs}.
     * @param lastRebufferRealtimeMs see {@link #lastRebufferRealtimeMs}
     */
    public Parameters(
        PlayerId playerId,
        Timeline timeline,
        MediaPeriodId mediaPeriodId,
        long playbackPositionUs,
        long bufferedDurationUs,
        float playbackSpeed,
        boolean playWhenReady,
        boolean rebuffering,
        long targetLiveOffsetUs,
        long lastRebufferRealtimeMs) {
      this.playerId = playerId;
      this.timeline = timeline;
      this.mediaPeriodId = mediaPeriodId;
      this.playbackPositionUs = playbackPositionUs;
      this.bufferedDurationUs = bufferedDurationUs;
      this.playbackSpeed = playbackSpeed;
      this.playWhenReady = playWhenReady;
      this.rebuffering = rebuffering;
      this.targetLiveOffsetUs = targetLiveOffsetUs;
      this.lastRebufferRealtimeMs = lastRebufferRealtimeMs;
    }
  }

  /**
   * @deprecated Used as a placeholder when MediaPeriodId is unknown. Only used when the deprecated
   *     methods {@link #onTracksSelected(Renderer[], TrackGroupArray, ExoTrackSelection[])} or
   *     {@link #shouldStartPlayback(long, float, boolean, long)} are called.
   */
  @Deprecated
  MediaPeriodId EMPTY_MEDIA_PERIOD_ID = new MediaPeriodId(/* periodUid= */ new Object());

  /**
   * Called by the player when prepared with a new source.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that prepared a new source.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default void onPrepared(PlayerId playerId) {
    onPrepared();
  }

  /**
   * @deprecated Use {@link #onPrepared(PlayerId)} instead.
   */
  @Deprecated
  default void onPrepared() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onPrepared not implemented");
  }

  /**
   * Called by the player when a track selection occurs.
   *
   * @param parameters containing the {@linkplain PlayerId ID of the player}, the current {@link
   *     Timeline} in ExoPlayer, and the {@link MediaPeriod} for which the selection was made. Will
   *     be {@link #EMPTY_MEDIA_PERIOD_ID} when {@code timeline} is empty.
   * @param trackGroups The {@link TrackGroup}s from which the selection was made.
   * @param trackSelections The track selections that were made.
   */
  default void onTracksSelected(
      Parameters parameters,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onTracksSelected not implemented");
  }

  /**
   * @deprecated Implement {@link #onTracksSelected(Parameters, TrackGroupArray,
   *     ExoTrackSelection[])} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  @Deprecated
  default void onTracksSelected(
      PlayerId playerId,
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections);
  }

  /**
   * @deprecated Implement {@link #onTracksSelected(Parameters, TrackGroupArray,
   *     ExoTrackSelection[])} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  @Deprecated
  default void onTracksSelected(
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    onTracksSelected(renderers, trackGroups, trackSelections);
  }

  /**
   * @deprecated Implement {@link #onTracksSelected(Parameters, TrackGroupArray,
   *     ExoTrackSelection[])} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  @Deprecated
  default void onTracksSelected(
      Renderer[] renderers,
      TrackGroupArray trackGroups,
      @NullableType ExoTrackSelection[] trackSelections) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onTracksSelected not implemented");
  }

  /**
   * Called by the player when stopped.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that was stopped.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default void onStopped(PlayerId playerId) {
    onStopped();
  }

  /**
   * @deprecated Implement {@link #onStopped(PlayerId)} instead.
   */
  @Deprecated
  default void onStopped() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onStopped not implemented");
  }

  /**
   * Called by the player when released.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that was released.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default void onReleased(PlayerId playerId) {
    onReleased();
  }

  /**
   * @deprecated Implement {@link #onReleased(PlayerId)} instead.
   */
  @Deprecated
  default void onReleased() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("onReleased not implemented");
  }

  /** Returns the {@link Allocator} that should be used to obtain media buffer allocations. */
  Allocator getAllocator();

  /**
   * Returns the duration of media to retain in the buffer prior to the current playback position,
   * for fast backward seeking.
   *
   * <p>Note: If {@link #retainBackBufferFromKeyframe()} is false then seeking in the back-buffer
   * will only be fast if the back-buffer contains a keyframe prior to the seek position.
   *
   * <p>Note: Implementations should return a single value. Dynamic changes to the back-buffer are
   * not currently supported.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that requests the back buffer
   *     duration.
   * @return The duration of media to retain in the buffer prior to the current playback position,
   *     in microseconds.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default long getBackBufferDurationUs(PlayerId playerId) {
    return getBackBufferDurationUs();
  }

  /**
   * @deprecated Implements {@link #getBackBufferDurationUs(PlayerId)} instead.
   */
  @Deprecated
  default long getBackBufferDurationUs() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("getBackBufferDurationUs not implemented");
  }

  /**
   * Returns whether media should be retained from the keyframe before the current playback position
   * minus {@link #getBackBufferDurationUs()}, rather than any sample before or at that position.
   *
   * <p>Warning: Returning true will cause the back-buffer size to depend on the spacing of
   * keyframes in the media being played. Returning true is not recommended unless you control the
   * media and are comfortable with the back-buffer size exceeding {@link
   * #getBackBufferDurationUs()} by as much as the maximum duration between adjacent keyframes in
   * the media.
   *
   * <p>Note: Implementations should return a single value. Dynamic changes to the back-buffer are
   * not currently supported.
   *
   * @param playerId The {@linkplain PlayerId ID of the player} that requests whether to retain the
   *     back buffer from key frame.
   * @return Whether media should be retained from the keyframe before the current playback position
   *     minus {@link #getBackBufferDurationUs()}, rather than any sample before or at that
   *     position.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default boolean retainBackBufferFromKeyframe(PlayerId playerId) {
    return retainBackBufferFromKeyframe();
  }

  /**
   * @deprecated Implements {@link #retainBackBufferFromKeyframe(PlayerId)} instead.
   */
  @Deprecated
  default boolean retainBackBufferFromKeyframe() {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("retainBackBufferFromKeyframe not implemented");
  }

  /**
   * Called by the player to determine whether it should continue to load the source. If this method
   * returns true, the {@link MediaPeriod} identified in the most recent {@link #onTracksSelected}
   * call will continue being loaded.
   *
   * @param parameters Information about the playback context and the {@link MediaPeriod} that will
   *     continue to load if this method returns {@code true}.
   * @return Whether the loading should continue.
   */
  @SuppressWarnings("deprecation")
  default boolean shouldContinueLoading(Parameters parameters) {
    return shouldContinueLoading(
        parameters.playbackPositionUs, parameters.bufferedDurationUs, parameters.playbackSpeed);
  }

  /**
   * @deprecated Implement {@link #shouldContinueLoading(Parameters)} instead.
   */
  @Deprecated
  default boolean shouldContinueLoading(
      long playbackPositionUs, long bufferedDurationUs, float playbackSpeed) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("shouldContinueLoading not implemented");
  }

  /**
   * Called to determine whether preloading should be continued. If this method returns true, the
   * presented period will continue to load media.
   *
   * @param timeline The Timeline containing the preload period that can be looked up with
   *     MediaPeriodId.periodUid.
   * @param mediaPeriodId The MediaPeriodId of the preloading period.
   * @param bufferedDurationUs The duration of media currently buffered by the preload period.
   * @return Whether the preloading should continue for the given period.
   */
  default boolean shouldContinuePreloading(
      Timeline timeline, MediaPeriodId mediaPeriodId, long bufferedDurationUs) {
    Log.w(
        "LoadControl",
        "shouldContinuePreloading needs to be implemented when playlist preloading is enabled");
    return false;
  }

  /**
   * Called repeatedly by the player when it's loading the source, has yet to start playback, and
   * has the minimum amount of data necessary for playback to be started. The value returned
   * determines whether playback is actually started. The load control may opt to return {@code
   * false} until some condition has been met (e.g. a certain amount of media is buffered).
   *
   * @param parameters Information about the playback context and the {@link MediaPeriod} that will
   *     start playing if this method returns {@code true}.
   * @return Whether playback should be allowed to start or resume.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  default boolean shouldStartPlayback(Parameters parameters) {
    return shouldStartPlayback(
        parameters.timeline,
        parameters.mediaPeriodId,
        parameters.bufferedDurationUs,
        parameters.playbackSpeed,
        parameters.rebuffering,
        parameters.targetLiveOffsetUs);
  }

  /**
   * @deprecated Implement {@link #shouldStartPlayback(Parameters)} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated version of this method.
  @Deprecated
  default boolean shouldStartPlayback(
      Timeline timeline,
      MediaPeriodId mediaPeriodId,
      long bufferedDurationUs,
      float playbackSpeed,
      boolean rebuffering,
      long targetLiveOffsetUs) {
    // Media3 ExoPlayer will never call this method. The default implementation is only used to
    // forward to the deprecated version below.
    return shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs);
  }

  /**
   * @deprecated Implement {@link #shouldStartPlayback(Parameters)} instead.
   */
  @Deprecated
  default boolean shouldStartPlayback(
      long bufferedDurationUs, float playbackSpeed, boolean rebuffering, long targetLiveOffsetUs) {
    // Media3 ExoPlayer will never call this method. This default implementation provides an
    // implementation to please the compiler only.
    throw new IllegalStateException("shouldStartPlayback not implemented");
  }
}
