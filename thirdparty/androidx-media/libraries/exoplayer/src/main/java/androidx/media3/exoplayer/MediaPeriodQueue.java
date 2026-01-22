/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Handler;
import android.util.Pair;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.C;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.exoplayer.ExoPlayer.PreloadConfiguration;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds a queue of media periods, from the currently playing media period at the front to the
 * loading media period at the end of the queue, with methods for controlling loading and updating
 * the queue. Also has a reference to the media period currently being read.
 */
/* package */ final class MediaPeriodQueue {

  /**
   * Initial renderer position offset used for the first item in the queue, in microseconds.
   *
   * <p>Choosing a positive value, larger than any reasonable single media duration, ensures three
   * things:
   *
   * <ul>
   *   <li>Media that accidentally or intentionally starts with small negative timestamps doesn't
   *       send samples with negative timestamps to decoders. This makes rendering more robust as
   *       many decoders are known to have problems with negative timestamps.
   *   <li>Enqueueing media after the initial item with a non-zero start offset (e.g. content after
   *       ad breaks or live streams) is virtually guaranteed to stay in the positive timestamp
   *       range even when seeking back. This prevents renderer resets that are required if the
   *       allowed timestamp range may become negative.
   *   <li>Choosing a large value with zeros at all relevant digits simplifies debugging as the
   *       original timestamp of the media is still visible.
   * </ul>
   */
  public static final long INITIAL_RENDERER_POSITION_OFFSET_US = 1_000_000_000_000L;

  /**
   * Limits the maximum number of periods to buffer ahead of the current playing period. The
   * buffering policy normally prevents buffering too far ahead, but the policy could allow too many
   * small periods to be buffered if the period count were not limited.
   */
  private static final int MAXIMUM_BUFFER_AHEAD_PERIODS = 100;

  private final Timeline.Period period;
  private final Timeline.Window window;
  private final AnalyticsCollector analyticsCollector;
  private final HandlerWrapper analyticsCollectorHandler;
  private final MediaPeriodHolder.Factory mediaPeriodHolderFactory;

  private long nextWindowSequenceNumber;
  private @RepeatMode int repeatMode;
  private boolean shuffleModeEnabled;
  private PreloadConfiguration preloadConfiguration;
  @Nullable private MediaPeriodHolder playing;
  @Nullable private MediaPeriodHolder reading;
  @Nullable private MediaPeriodHolder prewarming;
  @Nullable private MediaPeriodHolder loading;
  @Nullable private MediaPeriodHolder preloading;
  private int length;
  @Nullable private Object oldFrontPeriodUid;
  private long oldFrontPeriodWindowSequenceNumber;
  private List<MediaPeriodHolder> preloadPriorityList;

  /**
   * Creates a new media period queue.
   *
   * @param analyticsCollector An {@link AnalyticsCollector} to be informed of queue changes.
   * @param analyticsCollectorHandler The {@link Handler} to call {@link AnalyticsCollector} methods
   *     on.
   * @param mediaPeriodHolderFactory A {@link MediaPeriodHolder.Factory} to create holders.
   */
  public MediaPeriodQueue(
      AnalyticsCollector analyticsCollector,
      HandlerWrapper analyticsCollectorHandler,
      MediaPeriodHolder.Factory mediaPeriodHolderFactory,
      PreloadConfiguration preloadConfiguration) {
    this.analyticsCollector = analyticsCollector;
    this.analyticsCollectorHandler = analyticsCollectorHandler;
    this.mediaPeriodHolderFactory = mediaPeriodHolderFactory;
    this.preloadConfiguration = preloadConfiguration;
    period = new Timeline.Period();
    window = new Timeline.Window();
    preloadPriorityList = new ArrayList<>();
  }

  /**
   * Sets the {@link RepeatMode} and returns whether the repeat mode change change has modified the
   * reading or pre-warming media periods. If it has modified the reading period then it is
   * necessary to seek to the current playback position. If it has modified the pre-warming period
   * then it is necessary to reset any pre-warming renderers. A value of {@code 0} is returned if it
   * has neither modified the reading period nor the pre-warming period.
   *
   * @param timeline The current timeline.
   * @param repeatMode The new repeat mode.
   * @return {@link UpdatePeriodQueueResult} with flags denoting if the repeat mode change altered
   *     the current reading or pre-warming media periods.
   */
  public int updateRepeatMode(Timeline timeline, @RepeatMode int repeatMode) {
    this.repeatMode = repeatMode;
    return updateForPlaybackModeChange(timeline);
  }

  /**
   * Sets whether shuffling is enabled and returns whether the shuffle mode change has modified the
   * reading or pre-warming media periods. If it has modified the reading period, then it is
   * necessary to seek to the current playback position. If it has modified the pre-warming period
   * then it is necessary to reset any pre-warming renderers. A value of {@code 0} is returned if it
   * has neither modified the reading period nor the pre-warming period.
   *
   * @param timeline The current timeline.
   * @param shuffleModeEnabled Whether shuffling mode is enabled.
   * @return {@link UpdatePeriodQueueResult} with flags denoting if the shuffle mode change altered
   *     the current reading or pre-warming media periods.
   */
  public @UpdatePeriodQueueResult int updateShuffleModeEnabled(
      Timeline timeline, boolean shuffleModeEnabled) {
    this.shuffleModeEnabled = shuffleModeEnabled;
    return updateForPlaybackModeChange(timeline);
  }

  /**
   * Updates the preload configuration.
   *
   * @param timeline The current timeline.
   * @param preloadConfiguration The new preload configuration.
   */
  public void updatePreloadConfiguration(
      Timeline timeline, PreloadConfiguration preloadConfiguration) {
    this.preloadConfiguration = preloadConfiguration;
    invalidatePreloadPool(timeline);
  }

  /** Returns whether {@code mediaPeriod} is the current loading media period. */
  public boolean isLoading(MediaPeriod mediaPeriod) {
    return loading != null && loading.mediaPeriod == mediaPeriod;
  }

  /** Returns whether {@code mediaPeriod} is the current preloading media period. */
  public boolean isPreloading(MediaPeriod mediaPeriod) {
    return preloading != null && preloading.mediaPeriod == mediaPeriod;
  }

  /**
   * If there is a loading period, reevaluates its buffer.
   *
   * @param rendererPositionUs The current renderer position.
   */
  public void reevaluateBuffer(long rendererPositionUs) {
    if (loading != null) {
      loading.reevaluateBuffer(rendererPositionUs);
    }
  }

  /** Returns whether a new loading media period should be enqueued, if available. */
  public boolean shouldLoadNextMediaPeriod() {
    return loading == null
        || (!loading.info.isFinal
            && loading.isFullyBuffered()
            && loading.info.durationUs != C.TIME_UNSET
            && length < MAXIMUM_BUFFER_AHEAD_PERIODS);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the next media period to load.
   *
   * @param rendererPositionUs The current renderer position.
   * @param playbackInfo The current playback information.
   * @return The {@link MediaPeriodInfo} for the next media period to load, or {@code null} if not
   *     yet known.
   */
  @Nullable
  public MediaPeriodInfo getNextMediaPeriodInfo(
      long rendererPositionUs, PlaybackInfo playbackInfo) {
    return loading == null
        ? getFirstMediaPeriodInfo(playbackInfo)
        : getFollowingMediaPeriodInfo(playbackInfo.timeline, loading, rendererPositionUs);
  }

  /**
   * Enqueues a new media period holder based on the specified information as the new loading media
   * period, and returns it.
   *
   * @param info Information used to identify this media period in its timeline period.
   */
  public MediaPeriodHolder enqueueNextMediaPeriodHolder(MediaPeriodInfo info) {
    long rendererPositionOffsetUs =
        loading == null
            ? INITIAL_RENDERER_POSITION_OFFSET_US
            : (loading.getRendererOffset() + loading.info.durationUs - info.startPositionUs);
    @Nullable MediaPeriodHolder newPeriodHolder = removePreloadedMediaPeriodHolder(info);
    if (newPeriodHolder == null) {
      newPeriodHolder = mediaPeriodHolderFactory.create(info, rendererPositionOffsetUs);
    } else {
      newPeriodHolder.info = info;
      newPeriodHolder.setRendererOffset(rendererPositionOffsetUs);
    }
    if (loading != null) {
      loading.setNext(newPeriodHolder);
    } else {
      playing = newPeriodHolder;
      reading = newPeriodHolder;
      prewarming = newPeriodHolder;
    }
    oldFrontPeriodUid = null;
    loading = newPeriodHolder;
    length++;
    notifyQueueUpdate();
    return newPeriodHolder;
  }

  /** Invalidates the preload pool. */
  public void invalidatePreloadPool(Timeline timeline) {
    if (preloadConfiguration.targetPreloadDurationUs == C.TIME_UNSET || loading == null) {
      releasePreloadPool();
      return;
    }
    MediaPeriodHolder loading = this.loading;
    List<MediaPeriodHolder> newPreloadPriorityList = new ArrayList<>();
    Pair<Object, Long> defaultPositionOfNextWindow =
        getDefaultPeriodPositionOfNextWindow(
            timeline, loading.info.id.periodUid, /* defaultPositionProjectionUs= */ 0L);
    if (defaultPositionOfNextWindow != null
        && !timeline
            .getWindow(
                timeline.getPeriodByUid(defaultPositionOfNextWindow.first, period).windowIndex,
                window)
            .isLive()) {
      long windowSequenceNumber =
          resolvePeriodUidToWindowSequenceNumberInPreloadPeriods(defaultPositionOfNextWindow.first);
      if (windowSequenceNumber == C.INDEX_UNSET) {
        windowSequenceNumber = nextWindowSequenceNumber++;
      }
      @Nullable
      MediaPeriodInfo nextInfo =
          getMediaPeriodInfoForPeriodPosition(
              timeline,
              defaultPositionOfNextWindow.first,
              defaultPositionOfNextWindow.second,
              windowSequenceNumber);
      @Nullable
      MediaPeriodHolder nextMediaPeriodHolder = removePreloadedMediaPeriodHolder(nextInfo);
      if (nextMediaPeriodHolder == null) {
        // The holder's renderer position offset may be different and is reset when enqueuing.
        long rendererPositionOffsetUs =
            loading.getRendererOffset() + loading.info.durationUs - nextInfo.startPositionUs;
        nextMediaPeriodHolder = mediaPeriodHolderFactory.create(nextInfo, rendererPositionOffsetUs);
      }
      newPreloadPriorityList.add(nextMediaPeriodHolder);
    }
    releaseAndResetPreloadPriorityList(newPreloadPriorityList);
  }

  /** Removes all periods from the preload pool and releases them. */
  public void releasePreloadPool() {
    if (!preloadPriorityList.isEmpty()) {
      releaseAndResetPreloadPriorityList(new ArrayList<>());
    }
  }

  @Nullable
  private MediaPeriodHolder removePreloadedMediaPeriodHolder(MediaPeriodInfo info) {
    for (int i = 0; i < preloadPriorityList.size(); i++) {
      MediaPeriodHolder mediaPeriodHolder = preloadPriorityList.get(i);
      if (mediaPeriodHolder.canBeUsedForMediaPeriodInfo(info)) {
        return preloadPriorityList.remove(i);
      }
    }
    return null;
  }

  private void releaseAndResetPreloadPriorityList(List<MediaPeriodHolder> newPriorityList) {
    for (int i = 0; i < preloadPriorityList.size(); i++) {
      preloadPriorityList.get(i).release();
    }
    preloadPriorityList = newPriorityList;
    preloading = null;
    maybeUpdatePreloadMediaPeriodHolder();
  }

  private MediaPeriodInfo getMediaPeriodInfoForPeriodPosition(
      Timeline timeline, Object periodUid, long positionUs, long windowSequenceNumber) {
    MediaPeriodId mediaPeriodId =
        resolveMediaPeriodIdForAds(
            timeline, periodUid, positionUs, windowSequenceNumber, window, period);
    return mediaPeriodId.isAd()
        ? getMediaPeriodInfoForAd(
            timeline,
            mediaPeriodId.periodUid,
            mediaPeriodId.adGroupIndex,
            mediaPeriodId.adIndexInAdGroup,
            /* contentPositionUs= */ positionUs,
            mediaPeriodId.windowSequenceNumber,
            /* isPrecededByTransitionFromSameStream= */ false)
        : getMediaPeriodInfoForContent(
            timeline,
            mediaPeriodId.periodUid,
            /* startPositionUs= */ positionUs,
            /* requestedContentPositionUs= */ C.TIME_UNSET,
            mediaPeriodId.windowSequenceNumber,
            /* isPrecededByTransitionFromSameStream= */ false);
  }

  @Nullable
  private Pair<Object, Long> getDefaultPeriodPositionOfNextWindow(
      Timeline timeline, Object periodUid, long defaultPositionProjectionUs) {
    int nextWindowIndex =
        timeline.getNextWindowIndex(
            timeline.getPeriodByUid(periodUid, period).windowIndex, repeatMode, shuffleModeEnabled);
    return nextWindowIndex != C.INDEX_UNSET
        ? timeline.getPeriodPositionUs(
            window,
            period,
            nextWindowIndex,
            /* windowPositionUs= */ C.TIME_UNSET,
            defaultPositionProjectionUs)
        : null;
  }

  /**
   * Returns the loading period holder which is at the end of the queue, or null if the queue is
   * empty.
   */
  @Nullable
  public MediaPeriodHolder getLoadingPeriod() {
    return loading;
  }

  /** Returns the preloading period holder, or null if there is no preloading period. */
  @Nullable
  public MediaPeriodHolder getPreloadingPeriod() {
    return preloading;
  }

  /**
   * Returns the playing period holder which is at the front of the queue, or null if the queue is
   * empty.
   */
  @Nullable
  public MediaPeriodHolder getPlayingPeriod() {
    return playing;
  }

  /** Returns the reading period holder, or null if the queue is empty. */
  @Nullable
  public MediaPeriodHolder getReadingPeriod() {
    return reading;
  }

  /** Returns the prewarming period holder, or null if the queue is empty. */
  @Nullable
  public MediaPeriodHolder getPrewarmingPeriod() {
    return prewarming;
  }

  /**
   * Continues reading from the next period holder in the queue.
   *
   * @return The updated reading period holder.
   */
  public MediaPeriodHolder advanceReadingPeriod() {
    if (prewarming == reading) {
      prewarming = checkStateNotNull(reading).getNext();
    }
    reading = checkStateNotNull(reading).getNext();
    notifyQueueUpdate();
    return checkStateNotNull(reading);
  }

  /**
   * Continues pre-warming from the next period holder in the queue.
   *
   * @return The updated pre-warming period holder.
   */
  public MediaPeriodHolder advancePrewarmingPeriod() {
    prewarming = checkStateNotNull(prewarming).getNext();
    notifyQueueUpdate();
    return checkStateNotNull(prewarming);
  }

  /**
   * Dequeues the playing period holder from the front of the queue and advances the playing period
   * holder to be the next item in the queue.
   *
   * @return The updated playing period holder, or null if the queue is or becomes empty.
   */
  @Nullable
  public MediaPeriodHolder advancePlayingPeriod() {
    if (playing == null) {
      return null;
    }
    if (playing == reading) {
      reading = playing.getNext();
    }
    if (playing == prewarming) {
      prewarming = playing.getNext();
    }
    playing.release();
    length--;
    if (length == 0) {
      loading = null;
      oldFrontPeriodUid = playing.uid;
      oldFrontPeriodWindowSequenceNumber = playing.info.id.windowSequenceNumber;
    }
    playing = playing.getNext();
    notifyQueueUpdate();
    return playing;
  }

  /**
   * Removes all period holders after the given period holder.
   *
   * <p>This process may remove the currently reading period holder. If that is the case, the
   * reading period holder is set to be the same as the playing period holder at the front of the
   * queue.
   *
   * <p>This process may remove the currently pre-warming period holder. If that is the case, the
   * pre-warming period holder is set to be the same as the reading period holder.
   *
   * <p>A value of {@code 0} is returned if the process has neither removed the reading period nor
   * the pre-warming period.
   *
   * @param mediaPeriodHolder The media period holder that shall be the new end of the queue.
   * @return {@link UpdatePeriodQueueResult} with flags denoting if the reading or pre-warming
   *     periods were removed.
   */
  public int removeAfter(MediaPeriodHolder mediaPeriodHolder) {
    checkStateNotNull(mediaPeriodHolder);
    if (mediaPeriodHolder.equals(loading)) {
      return 0;
    }
    int removedResult = 0;
    loading = mediaPeriodHolder;
    while (mediaPeriodHolder.getNext() != null) {
      mediaPeriodHolder = checkNotNull(mediaPeriodHolder.getNext());
      if (mediaPeriodHolder == reading) {
        reading = playing;
        prewarming = playing;
        removedResult |= UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD;
        removedResult |= UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD;
      }
      if (mediaPeriodHolder == prewarming) {
        prewarming = reading;
        removedResult |= UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD;
      }
      mediaPeriodHolder.release();
      length--;
    }
    checkNotNull(loading).setNext(null);
    notifyQueueUpdate();
    return removedResult;
  }

  /**
   * Sets the preloading period to the next period in the queue to preload or to null, if all
   * periods in the preload pool are fully loaded.
   */
  public void maybeUpdatePreloadMediaPeriodHolder() {
    if (preloading != null && !preloading.isFullyPreloaded()) {
      return;
    }
    preloading = null;
    for (int i = 0; i < preloadPriorityList.size(); i++) {
      MediaPeriodHolder mediaPeriodHolder = preloadPriorityList.get(i);
      if (!mediaPeriodHolder.isFullyPreloaded()) {
        preloading = mediaPeriodHolder;
        break;
      }
    }
  }

  @Nullable
  public MediaPeriodHolder getPreloadHolderByMediaPeriod(MediaPeriod mediaPeriod) {
    for (int i = 0; i < preloadPriorityList.size(); i++) {
      MediaPeriodHolder mediaPeriodHolder = preloadPriorityList.get(i);
      if (mediaPeriodHolder.mediaPeriod == mediaPeriod) {
        return mediaPeriodHolder;
      }
    }
    return null;
  }

  /** Clears the queue. */
  public void clear() {
    if (length == 0) {
      return;
    }
    MediaPeriodHolder front = Assertions.checkStateNotNull(playing);
    oldFrontPeriodUid = front.uid;
    oldFrontPeriodWindowSequenceNumber = front.info.id.windowSequenceNumber;
    while (front != null) {
      front.release();
      front = front.getNext();
    }
    playing = null;
    loading = null;
    reading = null;
    prewarming = null;
    length = 0;
    notifyQueueUpdate();
  }

  /**
   * Updates media periods in the queue to take into account the latest timeline, and returns
   * whether the timeline change has modified the current reading or pre-warming periods. The method
   * returns {@code 0} if all changes have been handled and the reading/pre-warming periods have not
   * been affected. If the reading period has been affected, then it is necessary to seek to the
   * current playback position. If the pre-warming period has been affected, then it is necessary to
   * reset any pre-warming renderers. The method assumes that the first media period in the queue is
   * still consistent with the new timeline.
   *
   * @param timeline The new timeline.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @param maxRendererReadPositionUs The maximum renderer position up to which renderers have read
   *     the current reading media period in microseconds, or {@link C#TIME_END_OF_SOURCE} if they
   *     have read to the end.
   * @param maxRendererPrewarmingPositionUs The maximum renderer position up to which renderers have
   *     read the current pre-warming media period in microseconds, or {@link C#TIME_END_OF_SOURCE}
   *     if they have read to the end.
   * @return {@link UpdatePeriodQueueResult} denoting whether the timeline change has modified the
   *     reading or pre-warming media periods.
   */
  public @MediaPeriodQueue.UpdatePeriodQueueResult int updateQueuedPeriods(
      Timeline timeline,
      long rendererPositionUs,
      long maxRendererReadPositionUs,
      long maxRendererPrewarmingPositionUs) {
    // TODO: Merge this into setTimeline so that the queue gets updated as soon as the new timeline
    // is set, once all cases handled by ExoPlayerImplInternal.handleMediaSourceListInfoRefreshed
    // can be handled here.
    MediaPeriodHolder previousPeriodHolder = null;
    MediaPeriodHolder periodHolder = playing;
    while (periodHolder != null) {
      MediaPeriodInfo oldPeriodInfo = periodHolder.info;

      // Get period info based on new timeline.
      MediaPeriodInfo newPeriodInfo;
      if (previousPeriodHolder == null) {
        // The id and start position of the first period have already been verified by
        // ExoPlayerImplInternal.handleMediaSourceListInfoRefreshed. Just update duration,
        // isLastInTimeline and isLastInPeriod flags.
        newPeriodInfo = getUpdatedMediaPeriodInfo(timeline, oldPeriodInfo);
      } else {
        newPeriodInfo =
            getFollowingMediaPeriodInfo(timeline, previousPeriodHolder, rendererPositionUs);
        if (newPeriodInfo == null || !canKeepMediaPeriodHolder(oldPeriodInfo, newPeriodInfo)) {
          // We've loaded a next media period that is not in the new timeline
          // or the new media period has a different id or start position.
          return removeAfter(previousPeriodHolder);
        }
      }

      // Use the new period info, but keep the old requested content position to avoid overriding it
      // by the default content position generated in getFollowingMediaPeriodInfo.
      periodHolder.info =
          newPeriodInfo.copyWithRequestedContentPositionUs(
              oldPeriodInfo.requestedContentPositionUs);

      if (!areDurationsCompatible(oldPeriodInfo.durationUs, newPeriodInfo.durationUs)) {
        // The period duration changed. Remove all subsequent periods and check whether we read
        // beyond the new duration.
        periodHolder.updateClipping();
        long newDurationInRendererTime =
            newPeriodInfo.durationUs == C.TIME_UNSET
                ? Long.MAX_VALUE
                : periodHolder.toRendererTime(newPeriodInfo.durationUs);
        boolean isReadingAndReadBeyondNewDuration =
            periodHolder == reading
                && !periodHolder.info.isFollowedByTransitionToSameStream
                && (maxRendererReadPositionUs == C.TIME_END_OF_SOURCE
                    || maxRendererReadPositionUs >= newDurationInRendererTime);
        boolean isPrewarmingAndReadBeyondNewDuration =
            periodHolder == prewarming
                && (maxRendererPrewarmingPositionUs == C.TIME_END_OF_SOURCE
                    || maxRendererPrewarmingPositionUs >= newDurationInRendererTime);
        @MediaPeriodQueue.UpdatePeriodQueueResult int removeAfterResult = removeAfter(periodHolder);
        if (removeAfterResult != 0) {
          return removeAfterResult;
        }
        int result = 0;
        if (isReadingAndReadBeyondNewDuration) {
          result |= UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD;
        }
        if (isPrewarmingAndReadBeyondNewDuration) {
          result |= UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD;
        }
        return result;
      }

      previousPeriodHolder = periodHolder;
      periodHolder = periodHolder.getNext();
    }
    return 0;
  }

  /**
   * Returns new media period info based on specified {@code mediaPeriodInfo} but taking into
   * account the current timeline. This method must only be called if the period is still part of
   * the current timeline.
   *
   * @param timeline The current timeline used to update the media period.
   * @param info Media period info for a media period based on an old timeline.
   * @return The updated media period info for the current timeline.
   */
  public MediaPeriodInfo getUpdatedMediaPeriodInfo(Timeline timeline, MediaPeriodInfo info) {
    MediaPeriodId id = info.id;
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInWindow = isLastInWindow(timeline, id);
    boolean isLastInTimeline = isLastInTimeline(timeline, id, isLastInPeriod);
    timeline.getPeriodByUid(info.id.periodUid, period);
    long endPositionUs =
        id.isAd() || id.nextAdGroupIndex == C.INDEX_UNSET
            ? C.TIME_UNSET
            : period.getAdGroupTimeUs(id.nextAdGroupIndex);
    long durationUs =
        id.isAd()
            ? period.getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup)
            : (endPositionUs == C.TIME_UNSET || endPositionUs == C.TIME_END_OF_SOURCE
                ? period.getDurationUs()
                : endPositionUs);
    boolean isFollowedByTransitionToSameStream =
        id.isAd()
            ? period.isServerSideInsertedAdGroup(id.adGroupIndex)
            : (id.nextAdGroupIndex != C.INDEX_UNSET
                && period.isServerSideInsertedAdGroup(id.nextAdGroupIndex));
    return new MediaPeriodInfo(
        id,
        info.startPositionUs,
        info.requestedContentPositionUs,
        endPositionUs,
        durationUs,
        info.isPrecededByTransitionFromSameStream,
        isFollowedByTransitionToSameStream,
        isLastInPeriod,
        isLastInWindow,
        isLastInTimeline);
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  public MediaPeriodId resolveMediaPeriodIdForAds(
      Timeline timeline, Object periodUid, long positionUs) {
    long windowSequenceNumber = resolvePeriodUidToWindowSequenceNumber(timeline, periodUid);
    return resolveMediaPeriodIdForAds(
        timeline, periodUid, positionUs, windowSequenceNumber, window, period);
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played, returning an identifier for an ad group if one needs to be played before the specified
   * position, or an identifier for a content media period if not.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @param windowSequenceNumber The sequence number of the window in the buffered sequence of
   *     windows this period is part of.
   * @param period A scratch {@link Timeline.Period}.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  private static MediaPeriodId resolveMediaPeriodIdForAds(
      Timeline timeline,
      Object periodUid,
      long positionUs,
      long windowSequenceNumber,
      Timeline.Window window,
      Timeline.Period period) {
    timeline.getPeriodByUid(periodUid, period);
    timeline.getWindow(period.windowIndex, window);
    // Skip ignorable server side inserted ad periods.
    for (int periodIndex = timeline.getIndexOfPeriod(periodUid);
        isSkippableAdPeriod(period) && periodIndex <= window.lastPeriodIndex;
        periodIndex++) {
      timeline.getPeriod(periodIndex, period, /* setIds= */ true);
      periodUid = checkNotNull(period.uid);
    }
    timeline.getPeriodByUid(periodUid, period);
    int adGroupIndex = period.getAdGroupIndexForPositionUs(positionUs);
    if (adGroupIndex == C.INDEX_UNSET) {
      int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(positionUs);
      return new MediaPeriodId(periodUid, windowSequenceNumber, nextAdGroupIndex);
    } else {
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(adGroupIndex);
      return new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    }
  }

  private static boolean isSkippableAdPeriod(Timeline.Period period) {
    int adGroupCount = period.getAdGroupCount();
    if (adGroupCount == 0
        || (adGroupCount == 1 && period.isLivePostrollPlaceholder(/* adGroupIndex= */ 0))
        || !period.isServerSideInsertedAdGroup(period.getRemovedAdGroupCount())
        || period.getAdGroupIndexForPositionUs(0L) != C.INDEX_UNSET) {
      return false;
    }
    if (period.durationUs == 0) {
      return true;
    }
    long contentResumeOffsetUs = 0;
    int lastIndexInclusive =
        adGroupCount - (period.isLivePostrollPlaceholder(adGroupCount - 1) ? 2 : 1);
    for (int i = 0; i <= lastIndexInclusive; i++) {
      contentResumeOffsetUs += period.getContentResumeOffsetUs(/* adGroupIndex= */ i);
    }
    return period.durationUs <= contentResumeOffsetUs;
  }

  /**
   * Resolves the specified timeline period and position to a {@link MediaPeriodId} that should be
   * played after a period position change, returning an identifier for an ad group if one needs to
   * be played before the specified position, or an identifier for a content media period if not.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period to play.
   * @param positionUs The next content position in the period to play.
   * @return The identifier for the first media period to play, taking into account unplayed ads.
   */
  public MediaPeriodId resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
      Timeline timeline, Object periodUid, long positionUs) {
    long windowSequenceNumber = resolvePeriodUidToWindowSequenceNumber(timeline, periodUid);
    // Check for preceding ad periods in multi-period window.
    timeline.getPeriodByUid(periodUid, period);
    timeline.getWindow(period.windowIndex, window);
    Object periodUidToPlay = periodUid;
    boolean seenAdPeriod = false;
    for (int i = timeline.getIndexOfPeriod(periodUid); i >= window.firstPeriodIndex; i--) {
      timeline.getPeriod(/* periodIndex= */ i, period, /* setIds= */ true);
      boolean isAdPeriod = period.getAdGroupCount() > 0;
      seenAdPeriod |= isAdPeriod;
      if (period.getAdGroupIndexForPositionUs(period.durationUs) != C.INDEX_UNSET) {
        // Roll forward to preceding un-played ad period.
        periodUidToPlay = checkNotNull(period.uid);
      }
      if (seenAdPeriod && (!isAdPeriod || period.durationUs != 0)) {
        // Stop for any periods except un-played ads with no content.
        break;
      }
    }
    return resolveMediaPeriodIdForAds(
        timeline, periodUidToPlay, positionUs, windowSequenceNumber, window, period);
  }

  // Internal methods.

  private void notifyQueueUpdate() {
    ImmutableList.Builder<MediaPeriodId> builder = ImmutableList.builder();
    @Nullable MediaPeriodHolder period = playing;
    while (period != null) {
      builder.add(period.info.id);
      period = period.getNext();
    }
    @Nullable MediaPeriodId readingPeriodId = reading == null ? null : reading.info.id;
    analyticsCollectorHandler.post(
        () -> analyticsCollector.updateMediaPeriodQueueInfo(builder.build(), readingPeriodId));
  }

  /**
   * Resolves the specified period uid to a corresponding window sequence number. Either by reusing
   * the window sequence number of an existing matching media period or by creating a new window
   * sequence number.
   *
   * @param timeline The timeline the period is part of.
   * @param periodUid The uid of the timeline period.
   * @return A window sequence number for a media period created for this timeline period.
   */
  private long resolvePeriodUidToWindowSequenceNumber(Timeline timeline, Object periodUid) {
    int windowIndex = timeline.getPeriodByUid(periodUid, period).windowIndex;
    if (oldFrontPeriodUid != null) {
      int oldFrontPeriodIndex = timeline.getIndexOfPeriod(oldFrontPeriodUid);
      if (oldFrontPeriodIndex != C.INDEX_UNSET) {
        int oldFrontWindowIndex = timeline.getPeriod(oldFrontPeriodIndex, period).windowIndex;
        if (oldFrontWindowIndex == windowIndex) {
          // Try to match old front uid after the queue has been cleared.
          return oldFrontPeriodWindowSequenceNumber;
        }
      }
    }
    MediaPeriodHolder mediaPeriodHolder = playing;
    while (mediaPeriodHolder != null) {
      if (mediaPeriodHolder.uid.equals(periodUid)) {
        // Reuse window sequence number of first exact period match.
        return mediaPeriodHolder.info.id.windowSequenceNumber;
      }
      mediaPeriodHolder = mediaPeriodHolder.getNext();
    }
    mediaPeriodHolder = playing;
    while (mediaPeriodHolder != null) {
      int indexOfHolderInTimeline = timeline.getIndexOfPeriod(mediaPeriodHolder.uid);
      if (indexOfHolderInTimeline != C.INDEX_UNSET) {
        int holderWindowIndex = timeline.getPeriod(indexOfHolderInTimeline, period).windowIndex;
        if (holderWindowIndex == windowIndex) {
          // As an alternative, try to match other periods of the same window.
          return mediaPeriodHolder.info.id.windowSequenceNumber;
        }
      }
      mediaPeriodHolder = mediaPeriodHolder.getNext();
    }

    long windowSequenceNumber = resolvePeriodUidToWindowSequenceNumberInPreloadPeriods(periodUid);
    if (windowSequenceNumber != C.INDEX_UNSET) {
      return windowSequenceNumber;
    }

    // If no match is found, create new sequence number.
    windowSequenceNumber = nextWindowSequenceNumber++;
    if (playing == null) {
      // If the queue is empty, save it as old front uid to allow later reuse.
      oldFrontPeriodUid = periodUid;
      oldFrontPeriodWindowSequenceNumber = windowSequenceNumber;
    }
    return windowSequenceNumber;
  }

  private long resolvePeriodUidToWindowSequenceNumberInPreloadPeriods(Object periodUid) {
    for (int i = 0; i < preloadPriorityList.size(); i++) {
      MediaPeriodHolder preloadHolder = preloadPriorityList.get(i);
      if (preloadHolder.uid.equals(periodUid)) {
        // Found a match in the preload periods.
        return preloadHolder.info.id.windowSequenceNumber;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * Returns whether a period described by {@code oldInfo} can be kept for playing the media period
   * described by {@code newInfo}.
   */
  private boolean canKeepMediaPeriodHolder(MediaPeriodInfo oldInfo, MediaPeriodInfo newInfo) {
    return oldInfo.startPositionUs == newInfo.startPositionUs && oldInfo.id.equals(newInfo.id);
  }

  /**
   * Returns whether a duration change of a period is compatible with keeping the following periods.
   */
  /* package */ static boolean areDurationsCompatible(long previousDurationUs, long newDurationUs) {
    return previousDurationUs == C.TIME_UNSET || previousDurationUs == newDurationUs;
  }

  /**
   * Updates the queue for any playback mode change, and returns whether the change was fully
   * handled. If not, it is necessary to seek to the current playback position.
   *
   * @param timeline The current timeline.
   * @return {@link UpdatePeriodQueueResult} with flags denoting if the playback mode change altered
   *     the current reading or pre-warming media periods.
   */
  private int updateForPlaybackModeChange(Timeline timeline) {
    // Find the last existing period holder that matches the new period order.
    MediaPeriodHolder lastValidPeriodHolder = playing;
    if (lastValidPeriodHolder == null) {
      return 0;
    }
    int currentPeriodIndex = timeline.getIndexOfPeriod(lastValidPeriodHolder.uid);
    while (true) {
      int nextPeriodIndex =
          timeline.getNextPeriodIndex(
              currentPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
      while (checkNotNull(lastValidPeriodHolder).getNext() != null
          && !lastValidPeriodHolder.info.isLastInTimelinePeriod) {
        lastValidPeriodHolder = lastValidPeriodHolder.getNext();
      }

      MediaPeriodHolder nextMediaPeriodHolder = lastValidPeriodHolder.getNext();
      if (nextPeriodIndex == C.INDEX_UNSET || nextMediaPeriodHolder == null) {
        break;
      }
      int nextPeriodHolderPeriodIndex = timeline.getIndexOfPeriod(nextMediaPeriodHolder.uid);
      if (nextPeriodHolderPeriodIndex != nextPeriodIndex) {
        break;
      }
      lastValidPeriodHolder = nextMediaPeriodHolder;
      currentPeriodIndex = nextPeriodIndex;
    }

    // Release any period holders that don't match the new period order.
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int removeAfterResult = removeAfter(lastValidPeriodHolder);

    // Update the period info for the last holder, as it may now be the last period in the timeline.
    lastValidPeriodHolder.info = getUpdatedMediaPeriodInfo(timeline, lastValidPeriodHolder.info);
    // If renderers may have read from a period that's been removed, it is necessary to restart.
    return removeAfterResult;
  }

  /**
   * Returns the first {@link MediaPeriodInfo} to play, based on the specified playback position.
   */
  @Nullable
  private MediaPeriodInfo getFirstMediaPeriodInfo(PlaybackInfo playbackInfo) {
    return getMediaPeriodInfo(
        playbackInfo.timeline,
        playbackInfo.periodId,
        playbackInfo.requestedContentPositionUs,
        /* startPositionUs= */ playbackInfo.positionUs);
  }

  /**
   * Returns the {@link MediaPeriodInfo} for the media period following {@code mediaPeriodHolder}'s
   * media period.
   *
   * @param timeline The current timeline.
   * @param mediaPeriodHolder The media period holder.
   * @param rendererPositionUs The current renderer position in microseconds.
   * @return The following media period's info, or {@code null} if it is not yet possible to get the
   *     next media period info.
   */
  @Nullable
  private MediaPeriodInfo getFollowingMediaPeriodInfo(
      Timeline timeline, MediaPeriodHolder mediaPeriodHolder, long rendererPositionUs) {
    // TODO: This method is called repeatedly from ExoPlayerImplInternal.maybeUpdateLoadingPeriod
    // but if the timeline is not ready to provide the next period it can't return a non-null value
    // until the timeline is updated. Store whether the next timeline period is ready when the
    // timeline is updated, to avoid repeatedly checking the same timeline.
    MediaPeriodInfo mediaPeriodInfo = mediaPeriodHolder.info;
    // The expected delay until playback transitions to the new period is equal the duration of
    // media that's currently buffered (assuming no interruptions). This is used to project forward
    // the start position for transitions to new windows.
    long bufferedDurationUs =
        mediaPeriodHolder.getRendererOffset() + mediaPeriodInfo.durationUs - rendererPositionUs;
    return mediaPeriodInfo.isLastInTimelinePeriod
        ? getFirstMediaPeriodInfoOfNextPeriod(timeline, mediaPeriodHolder, bufferedDurationUs)
        : getFollowingMediaPeriodInfoOfCurrentPeriod(
            timeline, mediaPeriodHolder, bufferedDurationUs);
  }

  /**
   * Returns the first {@link MediaPeriodInfo} that follows the given {@linkplain MediaPeriodHolder
   * media period holder}, or null if there is no following info. This can be the first info of the
   * next period in the current (multi-period) window, or the first info in the next window in the
   * timeline.
   *
   * @param timeline The timeline with period and window information
   * @param mediaPeriodHolder The media period holder for which to get the following info.
   * @param bufferedDurationUs The buffered duration, in microseconds.
   * @return The first media period info of the next period in the timeline, or null.
   */
  @Nullable
  private MediaPeriodInfo getFirstMediaPeriodInfoOfNextPeriod(
      Timeline timeline, MediaPeriodHolder mediaPeriodHolder, long bufferedDurationUs) {
    MediaPeriodInfo mediaPeriodInfo = mediaPeriodHolder.info;
    int currentPeriodIndex = timeline.getIndexOfPeriod(mediaPeriodInfo.id.periodUid);
    int nextPeriodIndex =
        timeline.getNextPeriodIndex(
            currentPeriodIndex, period, window, repeatMode, shuffleModeEnabled);
    if (nextPeriodIndex == C.INDEX_UNSET) {
      // We can't create a next period yet.
      return null;
    }
    long startPositionUs = 0;
    long contentPositionUs = 0;
    int nextWindowIndex =
        timeline.getPeriod(nextPeriodIndex, period, /* setIds= */ true).windowIndex;
    Object nextPeriodUid = checkNotNull(period.uid);
    long windowSequenceNumber = mediaPeriodInfo.id.windowSequenceNumber;
    if (timeline.getWindow(nextWindowIndex, window).firstPeriodIndex == nextPeriodIndex) {
      // We're starting to buffer a new window. When playback transitions to this window we'll
      // want it to be from its default start position, so project the default start position
      // forward by the duration of the buffer, and start buffering from this point.
      contentPositionUs = C.TIME_UNSET;
      @Nullable
      Pair<Object, Long> defaultPositionUs =
          timeline.getPeriodPositionUs(
              window,
              period,
              nextWindowIndex,
              /* windowPositionUs= */ C.TIME_UNSET,
              /* defaultPositionProjectionUs= */ max(0, bufferedDurationUs));
      if (defaultPositionUs == null) {
        return null;
      }
      nextPeriodUid = defaultPositionUs.first;
      startPositionUs = defaultPositionUs.second;
      @Nullable MediaPeriodHolder nextMediaPeriodHolder = mediaPeriodHolder.getNext();
      if (nextMediaPeriodHolder != null && nextMediaPeriodHolder.uid.equals(nextPeriodUid)) {
        windowSequenceNumber = nextMediaPeriodHolder.info.id.windowSequenceNumber;
      } else {
        long windowSequenceNumberFromPreload =
            resolvePeriodUidToWindowSequenceNumberInPreloadPeriods(nextPeriodUid);
        windowSequenceNumber =
            windowSequenceNumberFromPreload == C.INDEX_UNSET
                ? nextWindowSequenceNumber++
                : windowSequenceNumberFromPreload;
      }
    }

    @Nullable
    MediaPeriodId periodId =
        resolveMediaPeriodIdForAds(
            timeline, nextPeriodUid, startPositionUs, windowSequenceNumber, window, period);
    if (contentPositionUs != C.TIME_UNSET
        && mediaPeriodInfo.requestedContentPositionUs != C.TIME_UNSET) {
      boolean precedingPeriodHasServerSideInsertedAds =
          hasServerSideInsertedAds(mediaPeriodInfo.id.periodUid, timeline);
      // Handle the requested content position for period transitions within the same window.
      if (periodId.isAd() && precedingPeriodHasServerSideInsertedAds) {
        // Propagate the requested position to the following ad period in the same window.
        contentPositionUs = mediaPeriodInfo.requestedContentPositionUs;
      } else if (precedingPeriodHasServerSideInsertedAds) {
        // Use the requested content position of the preceding ad period as the start position.
        startPositionUs = mediaPeriodInfo.requestedContentPositionUs;
      }
    }
    return getMediaPeriodInfo(timeline, periodId, contentPositionUs, startPositionUs);
  }

  /**
   * Gets the {@link MediaPeriodInfo} that follows {@code mediaPeriodHolder} within the current
   * period.
   *
   * @param timeline The timeline with period and window information
   * @param mediaPeriodHolder The media period holder for which to get the following info.
   * @param bufferedDurationUs The buffered duration, in microseconds.
   * @return The following {@link MediaPeriodInfo} in the current period.
   */
  @Nullable
  private MediaPeriodInfo getFollowingMediaPeriodInfoOfCurrentPeriod(
      Timeline timeline, MediaPeriodHolder mediaPeriodHolder, long bufferedDurationUs) {
    MediaPeriodInfo mediaPeriodInfo = mediaPeriodHolder.info;
    MediaPeriodId currentPeriodId = mediaPeriodInfo.id;
    timeline.getPeriodByUid(currentPeriodId.periodUid, period);
    boolean isPrecededByTransitionFromSameStream =
        mediaPeriodInfo.isFollowedByTransitionToSameStream;
    if (currentPeriodId.isAd()) {
      int adGroupIndex = currentPeriodId.adGroupIndex;
      int adCountInCurrentAdGroup = period.getAdCountInAdGroup(adGroupIndex);
      if (adCountInCurrentAdGroup == C.LENGTH_UNSET) {
        return null;
      }
      int nextAdIndexInAdGroup =
          period.getNextAdIndexToPlay(adGroupIndex, currentPeriodId.adIndexInAdGroup);
      if (nextAdIndexInAdGroup < adCountInCurrentAdGroup) {
        // Play the next ad in the ad group if it's available.
        return getMediaPeriodInfoForAd(
            timeline,
            currentPeriodId.periodUid,
            adGroupIndex,
            nextAdIndexInAdGroup,
            mediaPeriodInfo.requestedContentPositionUs,
            currentPeriodId.windowSequenceNumber,
            isPrecededByTransitionFromSameStream);
      } else {
        // Play content from the ad group position.
        long startPositionUs = mediaPeriodInfo.requestedContentPositionUs;
        if (startPositionUs == C.TIME_UNSET) {
          // If we're transitioning from an ad group to content starting from its default position,
          // project the start position forward as if this were a transition to a new window.
          @Nullable
          Pair<Object, Long> defaultPositionUs =
              timeline.getPeriodPositionUs(
                  window,
                  period,
                  period.windowIndex,
                  /* windowPositionUs= */ C.TIME_UNSET,
                  /* defaultPositionProjectionUs= */ max(0, bufferedDurationUs));
          if (defaultPositionUs == null) {
            return null;
          }
          startPositionUs = defaultPositionUs.second;
        }
        long minStartPositionUs =
            getMinStartPositionAfterAdGroupUs(
                timeline, currentPeriodId.periodUid, currentPeriodId.adGroupIndex);
        return getMediaPeriodInfoForContent(
            timeline,
            currentPeriodId.periodUid,
            max(minStartPositionUs, startPositionUs),
            mediaPeriodInfo.requestedContentPositionUs,
            currentPeriodId.windowSequenceNumber,
            isPrecededByTransitionFromSameStream);
      }
    } else if (currentPeriodId.nextAdGroupIndex != C.INDEX_UNSET
        && period.isLivePostrollPlaceholder(currentPeriodId.nextAdGroupIndex)) {
      // The next ad group is the postroll placeholder. Ignore and try the next timeline period.
      return getFirstMediaPeriodInfoOfNextPeriod(timeline, mediaPeriodHolder, bufferedDurationUs);
    } else {
      // Play the next ad group if it's still available.
      int adIndexInAdGroup = period.getFirstAdIndexToPlay(currentPeriodId.nextAdGroupIndex);
      boolean isPlayedServerSideInsertedAd =
          period.isServerSideInsertedAdGroup(currentPeriodId.nextAdGroupIndex)
              && period.getAdState(currentPeriodId.nextAdGroupIndex, adIndexInAdGroup)
                  == AdPlaybackState.AD_STATE_PLAYED;
      if (adIndexInAdGroup == period.getAdCountInAdGroup(currentPeriodId.nextAdGroupIndex)
          || isPlayedServerSideInsertedAd) {
        // The next ad group has no ads left to play or is a played SSAI ad group. Play content from
        // the end position instead.
        long startPositionUs =
            getMinStartPositionAfterAdGroupUs(
                timeline, currentPeriodId.periodUid, currentPeriodId.nextAdGroupIndex);
        return getMediaPeriodInfoForContent(
            timeline,
            currentPeriodId.periodUid,
            startPositionUs,
            /* requestedContentPositionUs= */ mediaPeriodInfo.durationUs,
            currentPeriodId.windowSequenceNumber,
            /* isPrecededByTransitionFromSameStream= */ false);
      }
      return getMediaPeriodInfoForAd(
          timeline,
          currentPeriodId.periodUid,
          /* adGroupIndex= */ currentPeriodId.nextAdGroupIndex,
          adIndexInAdGroup,
          /* contentPositionUs= */ mediaPeriodInfo.durationUs,
          currentPeriodId.windowSequenceNumber,
          isPrecededByTransitionFromSameStream);
    }
  }

  private boolean hasServerSideInsertedAds(Object periodUid, Timeline timeline) {
    int adGroupCount = timeline.getPeriodByUid(periodUid, period).getAdGroupCount();
    int firstAdGroupIndex = period.getRemovedAdGroupCount();
    return adGroupCount > 0
        && period.isServerSideInsertedAdGroup(firstAdGroupIndex)
        && (adGroupCount > 1 || period.getAdGroupTimeUs(firstAdGroupIndex) != C.TIME_END_OF_SOURCE);
  }

  private MediaPeriodInfo getMediaPeriodInfo(
      Timeline timeline, MediaPeriodId id, long requestedContentPositionUs, long startPositionUs) {
    timeline.getPeriodByUid(id.periodUid, period);
    if (id.isAd()) {
      return getMediaPeriodInfoForAd(
          timeline,
          id.periodUid,
          id.adGroupIndex,
          id.adIndexInAdGroup,
          requestedContentPositionUs,
          id.windowSequenceNumber,
          /* isPrecededByTransitionFromSameStream= */ false);
    } else {
      return getMediaPeriodInfoForContent(
          timeline,
          id.periodUid,
          startPositionUs,
          requestedContentPositionUs,
          id.windowSequenceNumber,
          /* isPrecededByTransitionFromSameStream= */ false);
    }
  }

  private MediaPeriodInfo getMediaPeriodInfoForAd(
      Timeline timeline,
      Object periodUid,
      int adGroupIndex,
      int adIndexInAdGroup,
      long contentPositionUs,
      long windowSequenceNumber,
      boolean isPrecededByTransitionFromSameStream) {
    MediaPeriodId id =
        new MediaPeriodId(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber);
    long durationUs =
        timeline
            .getPeriodByUid(id.periodUid, period)
            .getAdDurationUs(id.adGroupIndex, id.adIndexInAdGroup);
    long startPositionUs =
        adIndexInAdGroup == period.getFirstAdIndexToPlay(adGroupIndex)
            ? period.getAdResumePositionUs()
            : 0;
    boolean isFollowedByTransitionToSameStream =
        period.isServerSideInsertedAdGroup(id.adGroupIndex);
    if (durationUs != C.TIME_UNSET && startPositionUs >= durationUs) {
      // Ensure start position doesn't exceed duration.
      startPositionUs = max(0, durationUs - 1);
    }
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        /* requestedContentPositionUs= */ contentPositionUs,
        /* endPositionUs= */ C.TIME_UNSET,
        durationUs,
        isPrecededByTransitionFromSameStream,
        isFollowedByTransitionToSameStream,
        /* isLastInTimelinePeriod= */ false,
        /* isLastInTimelineWindow= */ false,
        /* isFinal= */ false);
  }

  private MediaPeriodInfo getMediaPeriodInfoForContent(
      Timeline timeline,
      Object periodUid,
      long startPositionUs,
      long requestedContentPositionUs,
      long windowSequenceNumber,
      boolean isPrecededByTransitionFromSameStream) {
    timeline.getPeriodByUid(periodUid, period);
    int nextAdGroupIndex = period.getAdGroupIndexAfterPositionUs(startPositionUs);
    boolean isNextAdGroupPostrollPlaceholder =
        nextAdGroupIndex != C.INDEX_UNSET && period.isLivePostrollPlaceholder(nextAdGroupIndex);
    boolean clipPeriodAtContentDuration = false;
    if (nextAdGroupIndex == C.INDEX_UNSET) {
      // Clip SSAI streams when at the end of the period.
      clipPeriodAtContentDuration =
          period.getAdGroupCount() > 0
              && period.isServerSideInsertedAdGroup(period.getRemovedAdGroupCount());
    } else if (period.isServerSideInsertedAdGroup(nextAdGroupIndex)
        && period.getAdGroupTimeUs(nextAdGroupIndex) == period.durationUs
        && period.hasPlayedAdGroup(nextAdGroupIndex)) {
      // Clip period before played SSAI post-rolls.
      nextAdGroupIndex = C.INDEX_UNSET;
      clipPeriodAtContentDuration = true;
    }

    MediaPeriodId id = new MediaPeriodId(periodUid, windowSequenceNumber, nextAdGroupIndex);
    boolean isLastInPeriod = isLastInPeriod(id);
    boolean isLastInWindow = isLastInWindow(timeline, id);
    boolean isLastInTimeline = isLastInTimeline(timeline, id, isLastInPeriod);
    boolean isFollowedByTransitionToSameStream =
        nextAdGroupIndex != C.INDEX_UNSET
            && period.isServerSideInsertedAdGroup(nextAdGroupIndex)
            && !isNextAdGroupPostrollPlaceholder;
    long endPositionUs =
        nextAdGroupIndex != C.INDEX_UNSET && !isNextAdGroupPostrollPlaceholder
            ? period.getAdGroupTimeUs(nextAdGroupIndex)
            : clipPeriodAtContentDuration ? period.durationUs : C.TIME_UNSET;
    long durationUs =
        endPositionUs == C.TIME_UNSET || endPositionUs == C.TIME_END_OF_SOURCE
            ? period.durationUs
            : endPositionUs;
    if (durationUs != C.TIME_UNSET && startPositionUs >= durationUs) {
      // Ensure start position doesn't exceed duration.
      boolean endAtLastFrame = isLastInTimeline || !clipPeriodAtContentDuration;
      startPositionUs = max(0, durationUs - (endAtLastFrame ? 1 : 0));
    }
    return new MediaPeriodInfo(
        id,
        startPositionUs,
        requestedContentPositionUs,
        endPositionUs,
        durationUs,
        isPrecededByTransitionFromSameStream,
        isFollowedByTransitionToSameStream,
        isLastInPeriod,
        isLastInWindow,
        /* isFinal= */ isLastInTimeline);
  }

  private boolean isLastInPeriod(MediaPeriodId id) {
    return !id.isAd() && id.nextAdGroupIndex == C.INDEX_UNSET;
  }

  private boolean isLastInWindow(Timeline timeline, MediaPeriodId id) {
    if (!isLastInPeriod(id)) {
      return false;
    }
    int windowIndex = timeline.getPeriodByUid(id.periodUid, period).windowIndex;
    int periodIndex = timeline.getIndexOfPeriod(id.periodUid);
    return timeline.getWindow(windowIndex, window).lastPeriodIndex == periodIndex;
  }

  private boolean isLastInTimeline(
      Timeline timeline, MediaPeriodId id, boolean isLastMediaPeriodInPeriod) {
    int periodIndex = timeline.getIndexOfPeriod(id.periodUid);
    int windowIndex = timeline.getPeriod(periodIndex, period).windowIndex;
    return !timeline.getWindow(windowIndex, window).isDynamic
        && timeline.isLastPeriod(periodIndex, period, window, repeatMode, shuffleModeEnabled)
        && isLastMediaPeriodInPeriod;
  }

  private long getMinStartPositionAfterAdGroupUs(
      Timeline timeline, Object periodUid, int adGroupIndex) {
    timeline.getPeriodByUid(periodUid, period);
    long startPositionUs = period.getAdGroupTimeUs(adGroupIndex);
    if (startPositionUs == C.TIME_END_OF_SOURCE) {
      return period.durationUs;
    }
    return startPositionUs + period.getContentResumeOffsetUs(adGroupIndex);
  }

  /**
   * Results for calls to {link MediaPeriodQueue} methods that may alter the reading or prewarming
   * periods in the queue like {@link #updateQueuedPeriods}, {@link #removeAfter}, {@link
   * #updateShuffleModeEnabled}, and {@link #updateRepeatMode}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
        UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD,
        UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD
      })
  /* package */ @interface UpdatePeriodQueueResult {}

  /** The update altered the reading period which means that a seek is required. */
  /* package */ static final int UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD = 1;

  /**
   * The update altered the pre-warming period which means that pre-warming renderers should be
   * reset.
   */
  /* package */ static final int UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD = 1 << 1;
}
