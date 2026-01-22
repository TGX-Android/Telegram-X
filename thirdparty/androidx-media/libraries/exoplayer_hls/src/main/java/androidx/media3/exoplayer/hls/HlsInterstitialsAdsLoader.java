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
package androidx.media3.exoplayer.hls;

import static androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.LocalConfiguration;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Period;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist.Interstitial;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ads.AdsLoader;
import androidx.media3.exoplayer.source.ads.AdsMediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An {@linkplain AdsLoader ads loader} that reads interstitials from the HLS playlist, adds them to
 * the {@link AdPlaybackState} and passes the ad playback state to {@link
 * EventListener#onAdPlaybackState(AdPlaybackState)}.
 *
 * <p>An ads ID must be unique within the playlist of ExoPlayer. If this is the case, a single
 * {@link HlsInterstitialsAdsLoader} instance can be passed to multiple {@linkplain AdsMediaSource
 * ads media sources}. These ad media source can be added to the same playlist as far as each of the
 * sources have a different ads IDs.
 */
@UnstableApi
public final class HlsInterstitialsAdsLoader implements AdsLoader {

  /**
   * A {@link MediaSource.Factory} to create a media source to play HLS streams with interstitials.
   */
  public static final class AdsMediaSourceFactory implements MediaSource.Factory {

    private final MediaSource.Factory mediaSourceFactory;
    private final AdViewProvider adViewProvider;
    private final HlsInterstitialsAdsLoader adsLoader;

    /**
     * Creates an instance with a {@link
     * androidx.media3.exoplayer.source.DefaultMediaSourceFactory}.
     *
     * @param adsLoader The {@link HlsInterstitialsAdsLoader}.
     * @param adViewProvider Provider of views for the ad UI.
     * @param context The {@link Context}.
     */
    public AdsMediaSourceFactory(
        HlsInterstitialsAdsLoader adsLoader, AdViewProvider adViewProvider, Context context) {
      this(adsLoader, context, /* mediaSourceFactory= */ null, adViewProvider);
    }

    /**
     * Creates an instance with a custom {@link MediaSource.Factory}.
     *
     * @param adsLoader The {@link HlsInterstitialsAdsLoader}.
     * @param adViewProvider Provider of views for the ad UI.
     * @param mediaSourceFactory The {@link MediaSource.Factory} used to create content and ad media
     *     sources.
     * @throws IllegalStateException If the provided {@linkplain MediaSource.Factory media source
     *     factory} doesn't support content type {@link C#CONTENT_TYPE_HLS}.
     */
    public AdsMediaSourceFactory(
        HlsInterstitialsAdsLoader adsLoader,
        AdViewProvider adViewProvider,
        MediaSource.Factory mediaSourceFactory) {
      this(adsLoader, /* context= */ null, mediaSourceFactory, adViewProvider);
    }

    private AdsMediaSourceFactory(
        HlsInterstitialsAdsLoader adsLoader,
        @Nullable Context context,
        @Nullable MediaSource.Factory mediaSourceFactory,
        AdViewProvider adViewProvider) {
      checkArgument(context != null || mediaSourceFactory != null);
      this.adsLoader = adsLoader;
      this.mediaSourceFactory =
          mediaSourceFactory != null
              ? mediaSourceFactory
              : new HlsMediaSource.Factory(new DefaultDataSource.Factory(checkNotNull(context)));
      this.adViewProvider = adViewProvider;
      boolean supportsHls = false;
      for (int supportedType : this.mediaSourceFactory.getSupportedTypes()) {
        if (supportedType == C.CONTENT_TYPE_HLS) {
          supportsHls = true;
          break;
        }
      }
      checkState(supportsHls);
    }

    @Override
    public @C.ContentType int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_HLS};
    }

    @Override
    @CanIgnoreReturnValue
    public AdsMediaSourceFactory setDrmSessionManagerProvider(
        DrmSessionManagerProvider drmSessionManagerProvider) {
      mediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
      return this;
    }

    @Override
    @CanIgnoreReturnValue
    public AdsMediaSourceFactory setLoadErrorHandlingPolicy(
        LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
      return this;
    }

    @Override
    public MediaSource createMediaSource(MediaItem mediaItem) {
      checkNotNull(mediaItem.localConfiguration);
      MediaSource contentMediaSource = mediaSourceFactory.createMediaSource(mediaItem);
      if (mediaItem.localConfiguration.adsConfiguration == null) {
        return contentMediaSource;
      } else if (!(mediaItem.localConfiguration.adsConfiguration.adsId instanceof String)) {
        throw new IllegalArgumentException(
            "Please use an AdsConfiguration with an adsId of type String when using"
                + " HlsInterstitialsAdsLoader");
      }
      return new AdsMediaSource(
          contentMediaSource,
          new DataSpec(mediaItem.localConfiguration.adsConfiguration.adTagUri), // unused
          checkNotNull(mediaItem.localConfiguration.adsConfiguration.adsId),
          mediaSourceFactory,
          adsLoader,
          adViewProvider,
          /* useLazyContentSourcePreparation= */ false);
    }
  }

  /** A listener to be notified of events emitted by the ads loader. */
  public interface Listener {

    /**
     * Called when the ads loader was started for the given HLS media item and ads ID.
     *
     * @param mediaItem The {@link MediaItem} of the content media source.
     * @param adsId The ads ID of the ads media source.
     * @param adViewProvider {@linkplain AdViewProvider Provider} of views for the ad UI.
     */
    default void onStart(MediaItem mediaItem, Object adsId, AdViewProvider adViewProvider) {
      // Do nothing.
    }

    /**
     * Called when the timeline of the content media source has changed. The {@link HlsManifest} of
     * the content source can be accessed through {@link Window#manifest}.
     *
     * @param mediaItem The {@link MediaItem} of the content media source.
     * @param adsId The ads ID of the ads media source.
     * @param hlsContentTimeline The latest {@link Timeline}.
     */
    default void onContentTimelineChanged(
        MediaItem mediaItem, Object adsId, Timeline hlsContentTimeline) {
      // Do nothing.
    }

    /**
     * Called when preparation of an ad period has completed successfully.
     *
     * @param mediaItem The {@link MediaItem} of the content media source.
     * @param adsId The ads ID of the ads media source.
     * @param adGroupIndex The index of the ad group in the ad media source.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     */
    default void onPrepareCompleted(
        MediaItem mediaItem, Object adsId, int adGroupIndex, int adIndexInAdGroup) {
      // Do nothing.
    }

    /**
     * Called when preparation of an ad period failed.
     *
     * @param mediaItem The {@link MediaItem} of the content media source.
     * @param adsId The ads ID of the ads media source.
     * @param adGroupIndex The index of the ad group in the ad media source.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     * @param exception The {@link IOException} thrown when preparing.
     */
    default void onPrepareError(
        MediaItem mediaItem,
        Object adsId,
        int adGroupIndex,
        int adIndexInAdGroup,
        IOException exception) {
      // Do nothing.
    }

    /**
     * Called when {@link Metadata} is emitted by the player during an ad period of an active HLS
     * media item.
     *
     * @param mediaItem The {@link MediaItem} of the content media source.
     * @param adsId The ads ID of the ads media source.
     * @param adGroupIndex The index of the ad group in the ad media source.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     * @param metadata The emitted {@link Metadata}.
     */
    default void onMetadata(
        MediaItem mediaItem,
        Object adsId,
        int adGroupIndex,
        int adIndexInAdGroup,
        Metadata metadata) {
      // Do nothing.
    }

    /**
     * Called when an ad period has completed playback and transitioned to the following ad or
     * content period, or the playlist ended.
     *
     * @param mediaItem The {@link MediaItem} of the content media source.
     * @param adsId The ads ID of the ads media source.
     * @param adGroupIndex The index of the ad group in the ad media source.
     * @param adIndexInAdGroup The index of the ad in the ad group.
     */
    default void onAdCompleted(
        MediaItem mediaItem, Object adsId, int adGroupIndex, int adIndexInAdGroup) {
      // Do nothing.
    }

    /**
     * Called when the ads loader was stopped for the given HLS media item.
     *
     * @param mediaItem The {@link MediaItem} of the content media source.
     * @param adsId The ads ID of the ads media source.
     * @param adPlaybackState The {@link AdPlaybackState} after the ad media source was released.
     */
    default void onStop(MediaItem mediaItem, Object adsId, AdPlaybackState adPlaybackState) {
      // Do nothing.
    }
  }

  private static final String TAG = "HlsInterstitiaAdsLoader";

  private final PlayerListener playerListener;
  private final Map<Object, EventListener> activeEventListeners;
  private final Map<Object, AdPlaybackState> activeAdPlaybackStates;
  private final List<Listener> listeners;
  private final Set<Object> unsupportedAdsIds;

  @Nullable private Player player;
  private boolean isReleased;

  /** Creates an instance. */
  public HlsInterstitialsAdsLoader() {
    playerListener = new PlayerListener();
    activeEventListeners = new HashMap<>();
    activeAdPlaybackStates = new HashMap<>();
    listeners = new ArrayList<>();
    unsupportedAdsIds = new HashSet<>();
  }

  /** Adds a {@link Listener}. */
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  /** Removes a {@link Listener}. */
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  // Implementation of AdsLoader methods

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException If an app is attempting to set a new player instance after {@link
   *     #release} was called or while {@linkplain AdsMediaSource ads media sources} started by the
   *     old player are still active, an {@link IllegalStateException} is thrown. Release the old
   *     player first, or remove all ads media sources from it before setting another player
   *     instance.
   */
  @Override
  public void setPlayer(@Nullable Player player) {
    checkState(!isReleased);
    if (Objects.equals(this.player, player)) {
      return;
    }
    if (this.player != null && !activeEventListeners.isEmpty()) {
      this.player.removeListener(playerListener);
    }
    checkState(player == null || activeEventListeners.isEmpty());
    this.player = player;
  }

  @Override
  public void setSupportedContentTypes(@C.ContentType int... contentTypes) {
    for (int contentType : contentTypes) {
      if (contentType == C.CONTENT_TYPE_HLS) {
        return;
      }
    }
    throw new IllegalArgumentException();
  }

  @Override
  public void start(
      AdsMediaSource adsMediaSource,
      DataSpec adTagDataSpec,
      Object adsId,
      AdViewProvider adViewProvider,
      EventListener eventListener) {
    if (isReleased) {
      // Run without ads after release to not interrupt playback.
      eventListener.onAdPlaybackState(new AdPlaybackState(adsId));
      return;
    }
    if (activeAdPlaybackStates.containsKey(adsId) || unsupportedAdsIds.contains(adsId)) {
      throw new IllegalStateException(
          "media item with adsId='"
              + adsId
              + "' already started. Make sure adsIds are unique within the same playlist.");
    }
    if (activeEventListeners.isEmpty()) {
      // Set the player listener when the first ad starts.
      checkStateNotNull(player, "setPlayer(Player) needs to be called").addListener(playerListener);
    }
    activeEventListeners.put(adsId, eventListener);
    MediaItem mediaItem = adsMediaSource.getMediaItem();
    if (player != null && isSupportedMediaItem(mediaItem, player.getCurrentTimeline())) {
      // Mark with NONE. Update and notify later when timeline with interstitials arrives.
      activeAdPlaybackStates.put(adsId, AdPlaybackState.NONE);
      notifyListeners(listener -> listener.onStart(mediaItem, adsId, adViewProvider));
    } else {
      putAndNotifyAdPlaybackStateUpdate(adsId, new AdPlaybackState(adsId));
      if (player != null) {
        Log.w(TAG, "Unsupported media item. Playing without ads for adsId=" + adsId);
        unsupportedAdsIds.add(adsId);
      }
    }
  }

  @Override
  public void handleContentTimelineChanged(AdsMediaSource adsMediaSource, Timeline timeline) {
    Object adsId = adsMediaSource.getAdsId();
    if (isReleased) {
      EventListener eventListener = activeEventListeners.remove(adsId);
      if (eventListener != null) {
        unsupportedAdsIds.remove(adsId);
        AdPlaybackState adPlaybackState = checkNotNull(activeAdPlaybackStates.remove(adsId));
        if (adPlaybackState.equals(AdPlaybackState.NONE)) {
          // Play without ads after release to not interrupt playback.
          eventListener.onAdPlaybackState(new AdPlaybackState(adsId));
        }
      }
      return;
    }
    AdPlaybackState adPlaybackState = checkNotNull(activeAdPlaybackStates.get(adsId));
    if (!adPlaybackState.equals(AdPlaybackState.NONE)) {
      // VOD only. Updating the playback state is not supported yet.
      return;
    }
    adPlaybackState = new AdPlaybackState(adsId);
    Window window = timeline.getWindow(0, new Window());
    if (window.manifest instanceof HlsManifest) {
      adPlaybackState =
          mapHlsInterstitialsToAdPlaybackState(
              ((HlsManifest) window.manifest).mediaPlaylist, adPlaybackState);
    }
    putAndNotifyAdPlaybackStateUpdate(adsId, adPlaybackState);
    if (!unsupportedAdsIds.contains(adsId)) {
      notifyListeners(
          listener ->
              listener.onContentTimelineChanged(adsMediaSource.getMediaItem(), adsId, timeline));
    }
  }

  @Override
  public void handlePrepareComplete(
      AdsMediaSource adsMediaSource, int adGroupIndex, int adIndexInAdGroup) {
    Object adsId = adsMediaSource.getAdsId();
    if (!isReleased && !unsupportedAdsIds.contains(adsId)) {
      notifyListeners(
          listener ->
              listener.onPrepareCompleted(
                  adsMediaSource.getMediaItem(), adsId, adGroupIndex, adIndexInAdGroup));
    }
  }

  @Override
  public void handlePrepareError(
      AdsMediaSource adsMediaSource,
      int adGroupIndex,
      int adIndexInAdGroup,
      IOException exception) {
    Object adsId = adsMediaSource.getAdsId();
    AdPlaybackState adPlaybackState =
        checkNotNull(activeAdPlaybackStates.get(adsId))
            .withAdLoadError(adGroupIndex, adIndexInAdGroup);
    putAndNotifyAdPlaybackStateUpdate(adsId, adPlaybackState);
    if (!isReleased && !unsupportedAdsIds.contains(adsId)) {
      notifyListeners(
          listener ->
              listener.onPrepareError(
                  adsMediaSource.getMediaItem(), adsId, adGroupIndex, adIndexInAdGroup, exception));
    }
  }

  @Override
  public void stop(AdsMediaSource adsMediaSource, EventListener eventListener) {
    Object adsId = adsMediaSource.getAdsId();
    activeEventListeners.remove(adsId);
    @Nullable AdPlaybackState adPlaybackState = activeAdPlaybackStates.remove(adsId);
    if (player != null && activeEventListeners.isEmpty()) {
      player.removeListener(playerListener);
      if (isReleased) {
        player = null;
      }
    }
    if (!isReleased && !unsupportedAdsIds.contains(adsId)) {
      notifyListeners(
          listener ->
              listener.onStop(
                  adsMediaSource.getMediaItem(),
                  adsMediaSource.getAdsId(),
                  checkNotNull(adPlaybackState)));
    }
    unsupportedAdsIds.remove(adsId);
  }

  @Override
  public void release() {
    // Note: Do not clear active resources as media sources still may have references to the loader
    // and we need to ensure sources can complete playback.
    if (activeEventListeners.isEmpty()) {
      player = null;
    }
    isReleased = true;
  }

  // private methods

  private void putAndNotifyAdPlaybackStateUpdate(Object adsId, AdPlaybackState adPlaybackState) {
    @Nullable
    AdPlaybackState oldAdPlaybackState = activeAdPlaybackStates.put(adsId, adPlaybackState);
    if (!adPlaybackState.equals(oldAdPlaybackState)) {
      @Nullable EventListener eventListener = activeEventListeners.get(adsId);
      if (eventListener != null) {
        eventListener.onAdPlaybackState(adPlaybackState);
      } else {
        activeAdPlaybackStates.remove(adsId);
      }
    }
  }

  private void notifyListeners(Consumer<Listener> callable) {
    for (int i = 0; i < listeners.size(); i++) {
      callable.accept(listeners.get(i));
    }
  }

  private static boolean isSupportedMediaItem(MediaItem mediaItem, Timeline timeline) {
    return isHlsMediaItem(mediaItem) && !isLiveMediaItem(mediaItem, timeline);
  }

  private static boolean isLiveMediaItem(MediaItem mediaItem, Timeline timeline) {
    int windowIndex = timeline.getFirstWindowIndex(/* shuffleModeEnabled= */ false);
    Window window = new Window();
    while (windowIndex != C.INDEX_UNSET) {
      timeline.getWindow(windowIndex, window);
      if (window.mediaItem.equals(mediaItem)) {
        return window.isLive();
      }
      windowIndex =
          timeline.getNextWindowIndex(
              windowIndex, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ false);
    }
    return false;
  }

  private static boolean isHlsMediaItem(MediaItem mediaItem) {
    LocalConfiguration localConfiguration = checkNotNull(mediaItem.localConfiguration);
    return Objects.equals(localConfiguration.mimeType, MimeTypes.APPLICATION_M3U8)
        || Util.inferContentType(localConfiguration.uri) == C.CONTENT_TYPE_HLS;
  }

  private static AdPlaybackState mapHlsInterstitialsToAdPlaybackState(
      HlsMediaPlaylist hlsMediaPlaylist, AdPlaybackState adPlaybackState) {
    for (int i = 0; i < hlsMediaPlaylist.interstitials.size(); i++) {
      Interstitial interstitial = hlsMediaPlaylist.interstitials.get(i);
      if (interstitial.assetUri == null) {
        Log.w(TAG, "Ignoring interstitials with X-ASSET-LIST. Not yet supported.");
        continue;
      }
      long positionUs;
      if (interstitial.cue.contains(Interstitial.CUE_TRIGGER_PRE)) {
        positionUs = 0;
      } else if (interstitial.cue.contains(Interstitial.CUE_TRIGGER_POST)) {
        positionUs = C.TIME_END_OF_SOURCE;
      } else {
        positionUs = interstitial.startDateUnixUs - hlsMediaPlaylist.startTimeUs;
      }
      // Check whether and at which index to insert an ad group for the interstitial start time.
      int adGroupIndex =
          adPlaybackState.getAdGroupIndexForPositionUs(
              positionUs, /* periodDurationUs= */ hlsMediaPlaylist.durationUs);
      if (adGroupIndex == C.INDEX_UNSET) {
        // There is no ad group before or at the interstitials position.
        adGroupIndex = 0;
        adPlaybackState = adPlaybackState.withNewAdGroup(0, positionUs);
      } else if (adPlaybackState.getAdGroup(adGroupIndex).timeUs != positionUs) {
        // There is an ad group before the interstitials. Insert after that index.
        adGroupIndex++;
        adPlaybackState = adPlaybackState.withNewAdGroup(adGroupIndex, positionUs);
      }

      int adIndexInAdGroup = max(adPlaybackState.getAdGroup(adGroupIndex).count, 0);

      // Insert duration of new interstitial into existing ad durations.
      long interstitialDurationUs =
          getInterstitialDurationUs(interstitial, /* defaultDurationUs= */ C.TIME_UNSET);
      long[] adDurations;
      if (adIndexInAdGroup == 0) {
        adDurations = new long[1];
      } else {
        long[] previousDurations = adPlaybackState.getAdGroup(adGroupIndex).durationsUs;
        adDurations = new long[previousDurations.length + 1];
        System.arraycopy(previousDurations, 0, adDurations, 0, previousDurations.length);
      }
      adDurations[adDurations.length - 1] = interstitialDurationUs;

      long resumeOffsetIncrementUs =
          interstitial.resumeOffsetUs != C.TIME_UNSET
              ? interstitial.resumeOffsetUs
              : (interstitialDurationUs != C.TIME_UNSET ? interstitialDurationUs : 0L);
      long resumeOffsetUs =
          adPlaybackState.getAdGroup(adGroupIndex).contentResumeOffsetUs + resumeOffsetIncrementUs;
      adPlaybackState =
          adPlaybackState
              .withAdCount(adGroupIndex, /* adCount= */ adIndexInAdGroup + 1)
              .withAdDurationsUs(adGroupIndex, adDurations)
              .withContentResumeOffsetUs(adGroupIndex, resumeOffsetUs)
              .withAvailableAdMediaItem(
                  adGroupIndex, adIndexInAdGroup, MediaItem.fromUri(interstitial.assetUri));
    }
    return adPlaybackState;
  }

  private static long getInterstitialDurationUs(Interstitial interstitial, long defaultDurationUs) {
    if (interstitial.playoutLimitUs != C.TIME_UNSET) {
      return interstitial.playoutLimitUs;
    } else if (interstitial.durationUs != C.TIME_UNSET) {
      return interstitial.durationUs;
    } else if (interstitial.endDateUnixUs != C.TIME_UNSET) {
      return interstitial.endDateUnixUs - interstitial.startDateUnixUs;
    } else if (interstitial.plannedDurationUs != C.TIME_UNSET) {
      return interstitial.plannedDurationUs;
    }
    return defaultDurationUs;
  }

  private class PlayerListener implements Player.Listener {

    private final Period period = new Period();

    @Override
    public void onMetadata(Metadata metadata) {
      @Nullable Player player = HlsInterstitialsAdsLoader.this.player;
      if (player == null || !player.isPlayingAd()) {
        return;
      }
      player.getCurrentTimeline().getPeriod(player.getCurrentPeriodIndex(), period);
      @Nullable Object adsId = period.adPlaybackState.adsId;
      if (adsId == null || !activeAdPlaybackStates.containsKey(adsId)) {
        return;
      }
      MediaItem currentMediaItem = checkNotNull(player.getCurrentMediaItem());
      int currentAdGroupIndex = player.getCurrentAdGroupIndex();
      int currentAdIndexInAdGroup = player.getCurrentAdIndexInAdGroup();
      notifyListeners(
          listener ->
              listener.onMetadata(
                  currentMediaItem, adsId, currentAdGroupIndex, currentAdIndexInAdGroup, metadata));
    }

    @Override
    public void onPositionDiscontinuity(
        Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
      if (reason != DISCONTINUITY_REASON_AUTO_TRANSITION
          || player == null
          || oldPosition.mediaItem == null
          || oldPosition.adGroupIndex == C.INDEX_UNSET) {
        return;
      }
      player.getCurrentTimeline().getPeriod(oldPosition.periodIndex, period);
      @Nullable Object adsId = period.adPlaybackState.adsId;
      if (adsId != null && activeAdPlaybackStates.containsKey(adsId)) {
        markAdAsPlayedAndNotifyListeners(
            oldPosition.mediaItem, adsId, oldPosition.adGroupIndex, oldPosition.adIndexInAdGroup);
      }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
      Player player = HlsInterstitialsAdsLoader.this.player;
      if (playbackState != Player.STATE_ENDED || player == null || !player.isPlayingAd()) {
        return;
      }
      player.getCurrentTimeline().getPeriod(player.getCurrentPeriodIndex(), period);
      @Nullable Object adsId = period.adPlaybackState.adsId;
      if (adsId != null && activeAdPlaybackStates.containsKey(adsId)) {
        markAdAsPlayedAndNotifyListeners(
            checkNotNull(player.getCurrentMediaItem()),
            adsId,
            player.getCurrentAdGroupIndex(),
            player.getCurrentAdIndexInAdGroup());
      }
    }

    private void markAdAsPlayedAndNotifyListeners(
        MediaItem mediaItem, Object adsId, int adGroupIndex, int adIndexInAdGroup) {
      @Nullable AdPlaybackState adPlaybackState = activeAdPlaybackStates.get(adsId);
      if (adPlaybackState != null) {
        adPlaybackState = adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup);
        putAndNotifyAdPlaybackStateUpdate(adsId, adPlaybackState);
        notifyListeners(
            listener -> listener.onAdCompleted(mediaItem, adsId, adGroupIndex, adIndexInAdGroup));
      }
    }
  }
}
