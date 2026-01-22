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

import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.exoplayer.MediaPeriodQueue.UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD;
import static androidx.media3.exoplayer.MediaPeriodQueue.UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD;
import static androidx.media3.test.utils.ExoPlayerTestRunner.AUDIO_FORMAT;
import static androidx.media3.test.utils.ExoPlayerTestRunner.VIDEO_FORMAT;
import static androidx.media3.test.utils.FakeMultiPeriodLiveTimeline.AD_PERIOD_DURATION_MS;
import static androidx.media3.test.utils.FakeMultiPeriodLiveTimeline.PERIOD_DURATION_MS;
import static androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US;
import static androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition.DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.exoplayer.ExoPlayer.PreloadConfiguration;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.MediaSource.MediaSourceCaller;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import androidx.media3.exoplayer.source.ads.ServerSideAdInsertionMediaSource;
import androidx.media3.exoplayer.source.ads.SinglePeriodAdTimeline;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelectorResult;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeMultiPeriodLiveTimeline;
import androidx.media3.test.utils.FakeShuffleOrder;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaPeriodQueue}. */
@RunWith(AndroidJUnit4.class)
public final class MediaPeriodQueueTest {

  private static final long CONTENT_DURATION_US = 30 * C.MICROS_PER_SECOND;
  private static final long AD_DURATION_US = 10 * C.MICROS_PER_SECOND;
  private static final long FIRST_AD_START_TIME_US = 10 * C.MICROS_PER_SECOND;
  private static final long SECOND_AD_START_TIME_US = 20 * C.MICROS_PER_SECOND;

  private static final MediaItem AD_MEDIA_ITEM = MediaItem.fromUri("https://google.com/empty");
  private static final Timeline CONTENT_TIMELINE =
      new SinglePeriodTimeline(
          CONTENT_DURATION_US,
          /* isSeekable= */ true,
          /* isDynamic= */ false,
          /* useLiveConfiguration= */ false,
          /* manifest= */ null,
          AD_MEDIA_ITEM);

  private MediaPeriodQueue mediaPeriodQueue;
  private AdPlaybackState adPlaybackState;
  private Object firstPeriodUid;

  private PlaybackInfo playbackInfo;
  private RendererCapabilities[] rendererCapabilities;
  private TrackSelector trackSelector;
  private Allocator allocator;
  private MediaSourceList mediaSourceList;
  private List<FakeMediaSource> fakeMediaSources;
  private ArrayList<MediaPeriodInfo> mediaPeriodHolderFactoryInfos;
  private ArrayList<Long> mediaPeriodHolderFactoryRendererPositionOffsets;

  @Before
  public void setUp() {
    AnalyticsCollector analyticsCollector = new DefaultAnalyticsCollector(Clock.DEFAULT);
    analyticsCollector.setPlayer(
        new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build(),
        Looper.getMainLooper());
    HandlerWrapper handler =
        Clock.DEFAULT.createHandler(Looper.getMainLooper(), /* callback= */ null);
    mediaPeriodHolderFactoryInfos = new ArrayList<>();
    mediaPeriodHolderFactoryRendererPositionOffsets = new ArrayList<>();
    mediaPeriodQueue =
        new MediaPeriodQueue(
            analyticsCollector,
            handler,
            (info, rendererPositionOffsetUs) -> {
              mediaPeriodHolderFactoryInfos.add(info);
              mediaPeriodHolderFactoryRendererPositionOffsets.add(rendererPositionOffsetUs);
              return new MediaPeriodHolder(
                  rendererCapabilities,
                  rendererPositionOffsetUs,
                  trackSelector,
                  allocator,
                  mediaSourceList,
                  info,
                  new TrackSelectorResult(
                      new RendererConfiguration[0],
                      new ExoTrackSelection[0],
                      Tracks.EMPTY,
                      /* info= */ null),
                  /* targetPreloadBufferDurationUs= */ 5_000_000L);
            },
            PreloadConfiguration.DEFAULT);
    mediaSourceList =
        new MediaSourceList(
            mock(MediaSourceList.MediaSourceListInfoRefreshListener.class),
            analyticsCollector,
            handler,
            PlayerId.UNSET);
    rendererCapabilities = new RendererCapabilities[0];
    trackSelector = mock(TrackSelector.class);
    allocator = mock(Allocator.class);
    fakeMediaSources = new ArrayList<>();
  }

  @Test
  public void getNextMediaPeriodInfo_withoutAds_returnsLastMediaPeriodInfo() {
    setupAdTimeline(/* no ad groups */ );
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void getNextMediaPeriodInfo_withPrerollAd_returnsCorrectMediaPeriodInfos() {
    setupAdTimeline(/* adGroupTimesUs...= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 0,
        AD_DURATION_US,
        /* contentPositionUs= */ C.TIME_UNSET,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void getNextMediaPeriodInfo_withMidrollAds_returnsCorrectMediaPeriodInfos() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ FIRST_AD_START_TIME_US,
        /* durationUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 0,
        /* adDurationUs= */ C.TIME_UNSET,
        /* contentPositionUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 0,
        AD_DURATION_US,
        /* contentPositionUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ FIRST_AD_START_TIME_US,
        /* requestedContentPositionUs= */ FIRST_AD_START_TIME_US,
        /* endPositionUs= */ SECOND_AD_START_TIME_US,
        /* durationUs= */ SECOND_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 1);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 1,
        AD_DURATION_US,
        /* contentPositionUs= */ SECOND_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ SECOND_AD_START_TIME_US,
        /* requestedContentPositionUs= */ SECOND_AD_START_TIME_US,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void getNextMediaPeriodInfo_withMidrollAndPostroll_returnsCorrectMediaPeriodInfos() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, C.TIME_END_OF_SOURCE);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ FIRST_AD_START_TIME_US,
        /* durationUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 0,
        AD_DURATION_US,
        /* contentPositionUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ FIRST_AD_START_TIME_US,
        /* requestedContentPositionUs= */ FIRST_AD_START_TIME_US,
        /* endPositionUs= */ C.TIME_END_OF_SOURCE,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 1);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 1,
        AD_DURATION_US,
        /* contentPositionUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ CONTENT_DURATION_US - 1,
        /* requestedContentPositionUs= */ CONTENT_DURATION_US,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void getNextMediaPeriodInfo_withAdGroupResumeOffsets_returnsCorrectMediaPeriodInfos() {
    adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ new Object(),
                /* adGroupTimesUs...= */ 0,
                FIRST_AD_START_TIME_US,
                C.TIME_END_OF_SOURCE)
            .withContentDurationUs(CONTENT_DURATION_US)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, /* contentResumeOffsetUs= */ 2000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 1, /* contentResumeOffsetUs= */ 3000)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 2, /* contentResumeOffsetUs= */ 4000);
    SinglePeriodAdTimeline adTimeline =
        new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    setupTimelines(adTimeline);

    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 0,
        AD_DURATION_US,
        /* contentPositionUs= */ C.TIME_UNSET,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 2000,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ FIRST_AD_START_TIME_US,
        /* durationUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 1);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 1,
        AD_DURATION_US,
        /* contentPositionUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ FIRST_AD_START_TIME_US + 3000,
        /* requestedContentPositionUs= */ FIRST_AD_START_TIME_US,
        /* endPositionUs= */ C.TIME_END_OF_SOURCE,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 2);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 2);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 2,
        AD_DURATION_US,
        /* contentPositionUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ CONTENT_DURATION_US - 1,
        /* requestedContentPositionUs= */ CONTENT_DURATION_US,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void getNextMediaPeriodInfo_withServerSideInsertedAds_returnsCorrectMediaPeriodInfos() {
    adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ new Object(),
                /* adGroupTimesUs...= */ 0,
                FIRST_AD_START_TIME_US,
                SECOND_AD_START_TIME_US)
            .withContentDurationUs(CONTENT_DURATION_US)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 1, /* isServerSideInserted= */ true)
            .withIsServerSideInserted(/* adGroupIndex= */ 2, /* isServerSideInserted= */ true);
    SinglePeriodAdTimeline adTimeline =
        new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    setupTimelines(adTimeline);

    setAdGroupLoaded(/* adGroupIndex= */ 0);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 0,
        AD_DURATION_US,
        /* contentPositionUs= */ C.TIME_UNSET,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ true);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ FIRST_AD_START_TIME_US,
        /* durationUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ true,
        /* isFollowedByTransitionToSameStream= */ true,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 1);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 1,
        AD_DURATION_US,
        /* contentPositionUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ true,
        /* isFollowedByTransitionToSameStream= */ true);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ FIRST_AD_START_TIME_US,
        /* requestedContentPositionUs= */ FIRST_AD_START_TIME_US,
        /* endPositionUs= */ SECOND_AD_START_TIME_US,
        /* durationUs= */ SECOND_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ true,
        /* isFollowedByTransitionToSameStream= */ true,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 2);
    advance();
    setAdGroupLoaded(/* adGroupIndex= */ 2);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 2,
        AD_DURATION_US,
        /* contentPositionUs= */ SECOND_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ true,
        /* isFollowedByTransitionToSameStream= */ true);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ SECOND_AD_START_TIME_US,
        /* requestedContentPositionUs= */ SECOND_AD_START_TIME_US,
        /* endPositionUs= */ CONTENT_DURATION_US,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ true,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getNextMediaPeriodInfo_multiPeriodTimelineWithNoAdsAndNoPostrollPlaceholder() {
    long contentPeriodDurationUs = msToUs(PERIOD_DURATION_MS);
    long adPeriodDurationUs = msToUs(AD_PERIOD_DURATION_MS);
    // Multi period timeline without ad playback state.
    FakeMultiPeriodLiveTimeline multiPeriodLiveTimeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeMs= */ 0,
            /* liveWindowDurationUs= */ 60_000_000,
            /* nowUs= */ 110_000_000,
            /* adSequencePattern= */ new boolean[] {false, true, true},
            /* periodDurationMsPattern= */ new long[] {
              PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS
            },
            /* isContentTimeline= */ true,
            /* populateAds= */ false,
            /* playedAds= */ false);
    setupTimelines(multiPeriodLiveTimeline);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ contentPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-4[a]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ adPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-5[a]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ adPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-6[c]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ C.TIME_UNSET, // last period in live timeline
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ false, // a dynamic window never has a final period
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
    advance();
    assertThat(getNextMediaPeriodInfo()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getNextMediaPeriodInfo_multiPeriodTimelineWithPostrollPlaceHolder() {
    long contentPeriodDurationUs = msToUs(PERIOD_DURATION_MS);
    long adPeriodDurationUs = msToUs(AD_PERIOD_DURATION_MS);
    // Multi period timeline without ad playback state.
    FakeMultiPeriodLiveTimeline multiPeriodLiveTimeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeMs= */ 0,
            /* liveWindowDurationUs= */ 60_000_000,
            /* nowUs= */ 110_000_000,
            /* adSequencePattern= */ new boolean[] {false, true, true},
            /* periodDurationMsPattern= */ new long[] {
              PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS
            },
            /* isContentTimeline= */ false,
            /* populateAds= */ false,
            /* playedAds= */ false);
    setupTimelines(multiPeriodLiveTimeline);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ contentPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-4[a]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ adPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-5[a]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ adPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-6[c]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ C.TIME_UNSET, // last period in live timeline
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertThat(getNextMediaPeriodInfo()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getNextMediaPeriodInfo_multiPeriodTimelineWithAdsAndWithPostRollPlaceHolder() {
    long contentPeriodDurationUs = msToUs(PERIOD_DURATION_MS);
    long adPeriodDurationUs = msToUs(AD_PERIOD_DURATION_MS);
    FakeMultiPeriodLiveTimeline multiPeriodLiveTimeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeMs= */ 0,
            /* liveWindowDurationUs= */ 60_000_000,
            /* nowUs= */ 110_000_000,
            /* adSequencePattern= */ new boolean[] {false, true, true},
            /* periodDurationMsPattern= */ new long[] {
              PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS
            },
            /* isContentTimeline= */ false,
            /* populateAds= */ true,
            /* playedAds= */ false);
    setupTimelines(multiPeriodLiveTimeline);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ contentPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertNextMediaPeriodInfoIsAd(
        /* periodUid= */ new Pair<Object, Object>(
            ((Pair<Object, Object>) firstPeriodUid).first, "uid-4[a]"),
        /* adGroupIndex= */ 0,
        /* adDurationUs= */ adPeriodDurationUs,
        /* contentPositionUs= */ 0,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ true);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-4[a]"),
        /* startPositionUs= */ adPeriodDurationUs,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ adPeriodDurationUs,
        /* durationUs= */ adPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ true,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
    advance();
    assertNextMediaPeriodInfoIsAd(
        /* periodUid= */ new Pair<Object, Object>(
            ((Pair<Object, Object>) firstPeriodUid).first, "uid-5[a]"),
        /* adGroupIndex= */ 0,
        /* adDurationUs= */ adPeriodDurationUs,
        /* contentPositionUs= */ 0,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ true);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-5[a]"),
        /* startPositionUs= */ adPeriodDurationUs,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ adPeriodDurationUs,
        /* durationUs= */ adPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ true,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-6[c]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ C.TIME_UNSET, // Last period in stream.
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertThat(getNextMediaPeriodInfo()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getNextMediaPeriodInfo_multiPeriodTimelineWithPlayedAdsAndWithPostRollPlaceHolder() {
    long contentPeriodDurationUs = msToUs(PERIOD_DURATION_MS);
    FakeMultiPeriodLiveTimeline multiPeriodLiveTimeline =
        new FakeMultiPeriodLiveTimeline(
            /* availabilityStartTimeMs= */ 0,
            /* liveWindowDurationUs= */ 60_000_000,
            /* nowUs= */ 110_000_000,
            /* adSequencePattern= */ new boolean[] {false, true, true},
            /* periodDurationMsPattern= */ new long[] {
              PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS, AD_PERIOD_DURATION_MS
            },
            /* isContentTimeline= */ false,
            /* populateAds= */ true,
            /* playedAds= */ true);
    setupTimelines(multiPeriodLiveTimeline);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ contentPeriodDurationUs,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        new Pair<Object, Object>(((Pair<Object, Object>) firstPeriodUid).first, "uid-6[c]"),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ C.TIME_UNSET, // Last period in stream.
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    assertThat(getNextMediaPeriodInfo()).isNull();
  }

  @Test
  public void getNextMediaPeriodInfo_withPostrollLoadError_returnsEmptyFinalMediaPeriodInfo() {
    setupAdTimeline(/* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_END_OF_SOURCE,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 0);
    advance();
    setAdGroupFailedToLoad(/* adGroupIndex= */ 0);
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ CONTENT_DURATION_US - 1,
        /* requestedContentPositionUs= */ CONTENT_DURATION_US,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void getNextMediaPeriodInfo_withPlayedAdGroups_returnsCorrectMediaPeriodInfos() {
    setupAdTimeline(/* adGroupTimesUs...= */ 0, FIRST_AD_START_TIME_US, C.TIME_END_OF_SOURCE);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    setAdGroupLoaded(/* adGroupIndex= */ 2);
    assertNextMediaPeriodInfoIsAd(
        firstPeriodUid,
        /* adGroupIndex= */ 0,
        AD_DURATION_US,
        /* contentPositionUs= */ C.TIME_UNSET,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false);
    setAdGroupPlayed(/* adGroupIndex= */ 0);
    clear();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ FIRST_AD_START_TIME_US,
        /* durationUs= */ FIRST_AD_START_TIME_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 1);
    setAdGroupPlayed(/* adGroupIndex= */ 1);
    clear();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_END_OF_SOURCE,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ false,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ 2);
    setAdGroupPlayed(/* adGroupIndex= */ 2);
    clear();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ firstPeriodUid,
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void getNextMediaPeriodInfo_inMultiPeriodWindow_returnsCorrectMediaPeriodInfos() {
    setupTimelines(
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 2,
                /* id= */ new Object(),
                /* isSeekable= */ false,
                /* isDynamic= */ false,
                /* durationUs= */ 2 * CONTENT_DURATION_US)));

    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 0),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ C.TIME_UNSET,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US + DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ false,
        /* isFinal= */ false,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
    advance();
    assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
        /* periodUid= */ playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 1),
        /* startPositionUs= */ 0,
        /* requestedContentPositionUs= */ 0,
        /* endPositionUs= */ C.TIME_UNSET,
        /* durationUs= */ CONTENT_DURATION_US,
        /* isPrecededByTransitionFromSameStream= */ false,
        /* isFollowedByTransitionToSameStream= */ false,
        /* isLastInPeriod= */ true,
        /* isLastInWindow= */ true,
        /* isFinal= */ true,
        /* nextAdGroupIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeInPlayingContent_handlesChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    enqueueNext(); // Content before ad.
    enqueueNext(); // Ad.
    enqueueNext(); // Content after ad.

    // Change position of first ad (= change duration of playing content before first ad).
    updateAdPlaybackStateAndTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US - 2000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    long maxRendererReadPositionUs =
        MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US + FIRST_AD_START_TIME_US - 3000;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            maxRendererReadPositionUs,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult).isEqualTo(0);
    assertThat(getQueueLength()).isEqualTo(1);
    assertThat(mediaPeriodQueue.getPlayingPeriod().info.endPositionUs)
        .isEqualTo(FIRST_AD_START_TIME_US - 2000);
    assertThat(mediaPeriodQueue.getPlayingPeriod().info.durationUs)
        .isEqualTo(FIRST_AD_START_TIME_US - 2000);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeInPlayingContentAfterReadingPosition_doesntHandleChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    enqueueNext(); // Content before ad.
    enqueueNext(); // Ad.
    enqueueNext(); // Content after ad.

    // Change position of first ad (= change duration of playing content before first ad).
    updateAdPlaybackStateAndTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US - 2000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    long maxRendererReadPositionUs =
        MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US + FIRST_AD_START_TIME_US - 1000;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            maxRendererReadPositionUs,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult).isEqualTo(UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD);
    assertThat(getQueueLength()).isEqualTo(1);
    assertThat(mediaPeriodQueue.getPlayingPeriod().info.endPositionUs)
        .isEqualTo(FIRST_AD_START_TIME_US - 2000);
    assertThat(mediaPeriodQueue.getPlayingPeriod().info.durationUs)
        .isEqualTo(FIRST_AD_START_TIME_US - 2000);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeInPlayingContentAfterReadingPositionInServerSideInsertedAd_handlesChangeAndRemovesPeriodsAfterChangedPeriod() {
    adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimes... */ FIRST_AD_START_TIME_US)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true);
    SinglePeriodAdTimeline adTimeline =
        new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    setupTimelines(adTimeline);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    enqueueNext(); // Content before ad.
    enqueueNext(); // Ad.
    enqueueNext(); // Content after ad.

    // Change position of first ad (= change duration of playing content before first ad).
    adPlaybackState =
        new AdPlaybackState(
                /* adsId= */ new Object(), /* adGroupTimesUs...= */ FIRST_AD_START_TIME_US - 2000)
            .withIsServerSideInserted(/* adGroupIndex= */ 0, /* isServerSideInserted= */ true);
    updateAdTimeline(/* mediaSourceIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    long maxRendererReadPositionUs =
        MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US + FIRST_AD_START_TIME_US - 1000;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            maxRendererReadPositionUs,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult).isEqualTo(0);
    assertThat(getQueueLength()).isEqualTo(1);
    assertThat(mediaPeriodQueue.getPlayingPeriod().info.endPositionUs)
        .isEqualTo(FIRST_AD_START_TIME_US - 2000);
    assertThat(mediaPeriodQueue.getPlayingPeriod().info.durationUs)
        .isEqualTo(FIRST_AD_START_TIME_US - 2000);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeAfterReadingPeriod_handlesChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    enqueueNext(); // Content before first ad.
    enqueueNext(); // First ad.
    enqueueNext(); // Content between ads.
    enqueueNext(); // Second ad.

    // Change position of second ad (= change duration of content between ads).
    updateAdPlaybackStateAndTimeline(
        /* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US - 1000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            /* maxRendererReadPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult).isEqualTo(0);
    assertThat(getQueueLength()).isEqualTo(3);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeBeforeReadingPeriod_doesntHandleChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    enqueueNext(); // Content before first ad.
    enqueueNext(); // First ad.
    enqueueNext(); // Content between ads.
    enqueueNext(); // Second ad.
    advanceReading(); // Reading first ad.
    advanceReading(); // Reading content between ads.
    advanceReading(); // Reading second ad.

    // Change position of second ad (= change duration of content between ads).
    updateAdPlaybackStateAndTimeline(
        /* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US - 1000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    long maxRendererReadPositionUs =
        MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US + FIRST_AD_START_TIME_US;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            maxRendererReadPositionUs,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult)
        .isEqualTo(
            UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD
                | UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD);
    assertThat(getQueueLength()).isEqualTo(3);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeInReadingPeriodAfterReadingPosition_handlesChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    enqueueNext(); // Content before first ad.
    enqueueNext(); // First ad.
    enqueueNext(); // Content between ads.
    enqueueNext(); // Second ad.
    advanceReading(); // Reading first ad.
    advanceReading(); // Reading content between ads.

    // Change position of second ad (= change duration of content between ads).
    updateAdPlaybackStateAndTimeline(
        /* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US - 1000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    long readingPositionAtStartOfContentBetweenAds =
        MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US
            + FIRST_AD_START_TIME_US
            + AD_DURATION_US;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            /* maxRendererReadPositionUs= */ readingPositionAtStartOfContentBetweenAds,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult).isEqualTo(0);
    assertThat(getQueueLength()).isEqualTo(3);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeInReadingPeriodBeforeReadingPosition_doesntHandleChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    enqueueNext(); // Content before first ad.
    enqueueNext(); // First ad.
    enqueueNext(); // Content between ads.
    enqueueNext(); // Second ad.
    advanceReading(); // Reading first ad.
    advanceReading(); // Reading content between ads.

    // Change position of second ad (= change duration of content between ads).
    updateAdPlaybackStateAndTimeline(
        /* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US - 1000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    long readingPositionAtEndOfContentBetweenAds =
        MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US
            + SECOND_AD_START_TIME_US
            + AD_DURATION_US;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            /* maxRendererReadPositionUs= */ readingPositionAtEndOfContentBetweenAds,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult).isEqualTo(UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD);
    assertThat(getQueueLength()).isEqualTo(3);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeInReadingPeriodReadToEnd_doesntHandleChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    enqueueNext(); // Content before first ad.
    enqueueNext(); // First ad.
    enqueueNext(); // Content between ads.
    enqueueNext(); // Second ad.
    advanceReading(); // Reading first ad.
    advanceReading(); // Reading content between ads.

    // Change position of second ad (= change duration of content between ads).
    updateAdPlaybackStateAndTimeline(
        /* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US - 1000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            /* maxRendererReadPositionUs= */ C.TIME_END_OF_SOURCE,
            /* maxRendererPrewarmingPositionUs= */ 0);

    assertThat(updateQueuedPeriodsResult).isEqualTo(UPDATE_PERIOD_QUEUE_ALTERED_READING_PERIOD);
    assertThat(getQueueLength()).isEqualTo(3);
  }

  @Test
  public void
      updateQueuedPeriods_withDurationChangeInPrewarmingPeriodBeforeReadingPosition_doesntHandleChangeAndRemovesPeriodsAfterChangedPeriod() {
    setupAdTimeline(/* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    enqueueNext(); // Content before first ad.
    enqueueNext(); // First ad.
    enqueueNext(); // Content between ads.
    enqueueNext(); // Second ad.
    advanceReading(); // Reading first ad.
    mediaPeriodQueue.advancePrewarmingPeriod(); // Pre-warming content between ads.

    // Change position of second ad (= change duration of content between ads).
    updateAdPlaybackStateAndTimeline(
        /* adGroupTimesUs...= */ FIRST_AD_START_TIME_US, SECOND_AD_START_TIME_US - 1000);
    setAdGroupLoaded(/* adGroupIndex= */ 0);
    setAdGroupLoaded(/* adGroupIndex= */ 1);
    long readingPositionAtEndOfContentBetweenAds =
        MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US
            + SECOND_AD_START_TIME_US
            + AD_DURATION_US;
    @MediaPeriodQueue.UpdatePeriodQueueResult
    int updateQueuedPeriodsResult =
        mediaPeriodQueue.updateQueuedPeriods(
            playbackInfo.timeline,
            /* rendererPositionUs= */ MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US,
            /* maxRendererReadPositionUs= */ C.TIME_END_OF_SOURCE,
            /* maxRendererPrewarmingPositionUs= */ readingPositionAtEndOfContentBetweenAds);

    assertThat(updateQueuedPeriodsResult).isEqualTo(UPDATE_PERIOD_QUEUE_ALTERED_PREWARMING_PERIOD);
    assertThat(getQueueLength()).isEqualTo(3);
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_behindAdPositionInSinglePeriodTimeline_resolvesToAd() {
    long adPositionUs = DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + 10_000;
    AdPlaybackState adPlaybackState = new AdPlaybackState("adsId", adPositionUs);
    adPlaybackState = adPlaybackState.withAdDurationsUs(/* adGroupIndex= */ 0, 5_000);
    Object windowUid = new Object();
    FakeTimeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ windowUid,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US,
                adPlaybackState));

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, /* periodUid= */ new Pair<>(windowUid, 0), adPositionUs + 1);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowUid, 0));
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_toAdPositionInSinglePeriodTimeline_resolvesToAd() {
    long adPositionUs = DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + 10_000;
    AdPlaybackState adPlaybackState = new AdPlaybackState("adsId", adPositionUs);
    adPlaybackState = adPlaybackState.withAdDurationsUs(/* adGroupIndex= */ 0, 5_000);
    Object windowUid = new Object();
    FakeTimeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ windowUid,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US,
                adPlaybackState));

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, /* periodUid= */ new Pair<>(windowUid, 0), adPositionUs);

    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowUid, 0));
    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_beforeAdPositionInSinglePeriodTimeline_seekNotAdjusted() {
    long adPositionUs = DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US + 10_000;
    AdPlaybackState adPlaybackState =
        new AdPlaybackState("adsId", adPositionUs).withAdDurationsUs(/* adGroupIndex= */ 0, 5_000);
    Object windowUid = new Object();
    FakeTimeline timeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* periodCount= */ 1,
                /* id= */ windowUid,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                TimelineWindowDefinition.DEFAULT_WINDOW_DURATION_US,
                adPlaybackState));

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowUid, 0), adPositionUs - 1);

    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowUid, 0));
    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(0);
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_behindAdInMultiPeriodTimeline_rollForward()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId,
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ true,
            false,
            true,
            true,
            true,
            false);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 1), /* positionUs= */ 1);

    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 0));
    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);

    mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 5), /* positionUs= */ 0);

    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 2));
    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_behindAdInMultiPeriodAllAdsPlayed_seekNotAdjusted()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId,
            /* numberOfPlayedAds= */ 4,
            /* isAdPeriodFlags...= */ true,
            false,
            true,
            true,
            true,
            false);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 1), /* positionUs= */ 11);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 1));

    mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 5), /* positionUs= */ 33);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 5));
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_behindAdInMultiPeriodFirstTwoAdsPlayed_rollForward()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId,
            /* numberOfPlayedAds= */ 2,
            /* isAdPeriodFlags...= */ true,
            false,
            true,
            true,
            true,
            false);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 5), /* positionUs= */ 33);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 3));
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_beforeAdInMultiPeriodTimeline_seekNotAdjusted()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId, /* numberOfPlayedAds= */ 0, /* isAdPeriodFlags...= */ false, true);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 0), /* positionUs= */ 33);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 0));
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_toUnplayedAdInMultiPeriodTimeline_resolvedAsAd()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId, /* numberOfPlayedAds= */ 0, /* isAdPeriodFlags...= */ false, true, false);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 1), /* positionUs= */ 0);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 1));
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_toPlayedAdInMultiPeriodTimeline_skipPlayedAd()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId, /* numberOfPlayedAds= */ 1, /* isAdPeriodFlags...= */ false, true, false);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 1), /* positionUs= */ 0);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 2));
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_toStartOfWindowPlayedAdPreroll_skipsPlayedPrerolls()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId, /* numberOfPlayedAds= */ 2, /* isAdPeriodFlags...= */ true, true, false);
    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 0), /* positionUs= */ 0);

    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 2));
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_toPlayedPostrolls_skipsAllButLastPostroll()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId,
            /* numberOfPlayedAds= */ 4,
            /* isAdPeriodFlags...= */ false,
            true,
            true,
            true,
            true);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 1), /* positionUs= */ 0);

    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 4));
    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_consecutiveContentPeriods_rollForward()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId,
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ true,
            false,
            false,
            false);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 3), /* positionUs= */ 10_000);

    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 0));
    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodId.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodId.nextAdGroupIndex).isEqualTo(-1);
  }

  @Test
  public void
      resolveMediaPeriodIdForAdsAfterPeriodPositionChange_onlyConsecutiveContentPeriods_seekNotAdjusted()
          throws InterruptedException {
    Object windowId = new Object();
    Timeline timeline =
        createMultiPeriodServerSideInsertedTimeline(
            windowId,
            /* numberOfPlayedAds= */ 0,
            /* isAdPeriodFlags...= */ false,
            false,
            false,
            false);

    MediaPeriodId mediaPeriodId =
        mediaPeriodQueue.resolveMediaPeriodIdForAdsAfterPeriodPositionChange(
            timeline, new Pair<>(windowId, 3), /* positionUs= */ 10_000);

    assertThat(mediaPeriodId.periodUid).isEqualTo(new Pair<>(windowId, 3));
    assertThat(mediaPeriodId.adGroupIndex).isEqualTo(-1);
  }

  @Test
  public void invalidatePreloadPool_withThreeWindowsPreloadEnabled_preloadHoldersCreated() {
    setupTimelines(new FakeTimeline(), new FakeTimeline(), new FakeTimeline());
    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, new PreloadConfiguration(/* targetPreloadDurationUs= */ 5_000_000L));

    // Creates period of first window for enqueuing.
    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(1);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets).containsExactly(1_000_000_000_000L);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(0));
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.windowSequenceNumber).isEqualTo(0);

    // Creates period of second window for preloading.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_010_000_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(1));
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.windowSequenceNumber).isEqualTo(1);

    // Enqueue period of second window from preload pool.
    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);

    // Creates period of third window for preloading.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(3);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_010_000_000L, 1_000_020_000_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(2));
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.windowSequenceNumber).isEqualTo(2);

    // Enqueue period of third window from preload pool.
    enqueueNext();
    // No further next window. Invalidating is a no-op.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(3);
  }

  @Test
  public void invalidatePreloadPool_withThreeWindowsPreloadDisabled_preloadHoldersNotCreated() {
    List<MediaPeriod> releasedMediaPeriods = new ArrayList<>();
    FakeMediaSource fakeMediaSource =
        new FakeMediaSource() {
          @Override
          public void releasePeriod(MediaPeriod mediaPeriod) {
            releasedMediaPeriods.add(mediaPeriod);
            super.releasePeriod(mediaPeriod);
          }
        };
    setupMediaSources(fakeMediaSource, fakeMediaSource, fakeMediaSource);
    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, PreloadConfiguration.DEFAULT);

    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(1);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets).containsExactly(1_000_000_000_000L);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(0));
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.windowSequenceNumber).isEqualTo(0);

    // Expect no-op.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(1);

    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_010_000_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(1));
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.windowSequenceNumber).isEqualTo(1);
    assertThat(releasedMediaPeriods).isEmpty();

    // Expect no-op.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);

    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(3);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_010_000_000L, 1_000_020_000_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(2));
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.windowSequenceNumber).isEqualTo(2);
    assertThat(releasedMediaPeriods).isEmpty();

    // Expect no-op.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(3);
  }

  @Test
  public void
      invalidatePreloadPool_secondWindowIsLivePreloadEnabled_preloadHolderForLiveNotCreated() {
    TimelineWindowDefinition liveWindow =
        new TimelineWindowDefinition(
            /* periodCount= */ 1,
            /* id= */ 1234,
            /* isSeekable= */ false,
            /* isDynamic= */ true,
            /* isLive= */ true,
            /* isPlaceholder= */ false,
            /* durationUs= */ DEFAULT_WINDOW_DURATION_US,
            /* defaultPositionUs= */ 0,
            /* windowOffsetInFirstPeriodUs= */ DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            /* adPlaybackStates= */ ImmutableList.of(AdPlaybackState.NONE),
            MediaItem.EMPTY);
    setupTimelines(new FakeTimeline(), new FakeTimeline(liveWindow));
    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, new PreloadConfiguration(/* targetPreloadDurationUs= */ 5_000_000L));

    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(1);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets).containsExactly(1_000_000_000_000L);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(0));
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.windowSequenceNumber).isEqualTo(0);

    // Expected to be a no-op for live.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(1);

    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_010_000_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(1));
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.windowSequenceNumber).isEqualTo(1);

    // Expected to be a no-op for last window.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);
  }

  @Test
  public void
      invalidatePreloadPool_windowWithTwoPeriodsPreloadEnabled_preloadHolderForThirdPeriodCreated() {
    TimelineWindowDefinition window1 =
        new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ 1234);
    setupTimelines(new FakeTimeline(window1), new FakeTimeline());
    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, new PreloadConfiguration(/* targetPreloadDurationUs= */ 5_000_000L));

    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(1);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets).containsExactly(1_000_000_000_000L);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 0));
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.windowSequenceNumber).isEqualTo(0);

    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_005_000_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 2));
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.windowSequenceNumber).isEqualTo(1);
  }

  @Test
  public void
      invalidatePreloadPool_withThreeWindowsWithAdsInSecondPreloadEnabled_preloadHolderCreatedForPreroll() {
    AdPlaybackState adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), 0L, 5_000_000L)
            .withAdCount(/* adGroupIndex= */ 0, 1)
            .withAdCount(/* adGroupIndex= */ 1, 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 2_000L)
            .withAdDurationsUs(/* adGroupIndex= */ 1, 1_000L)
            .withContentDurationUs(CONTENT_DURATION_US);
    SinglePeriodAdTimeline adTimeline =
        new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    setupMediaSources(
        new FakeMediaSource(), new FakeMediaSource(adTimeline), new FakeMediaSource());
    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, new PreloadConfiguration(/* targetPreloadDurationUs= */ 5_000_000L));

    // Creates the first and only period of the first window for enqueuing.
    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(1);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets).containsExactly(1_000_000_000_000L);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.windowSequenceNumber).isEqualTo(0);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(0));
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(0).id.nextAdGroupIndex).isEqualTo(-1);

    // Creates the pre-roll period of the 2nd window for preload.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_133_000_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.windowSequenceNumber).isEqualTo(1);
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.adGroupIndex).isEqualTo(0);
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodHolderFactoryInfos.get(1).id.nextAdGroupIndex).isEqualTo(-1);

    // Enqueue the pre-roll period from pool.
    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(2);

    // Creates the first content period of the 3rd window for preloading.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(3);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(
            1_000_000_000_000L,
            1_000_133_000_000L,
            1_000_133_000_000L + 2_000 - DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 2));
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(2).id.windowSequenceNumber).isEqualTo(2);

    // Creates the first content period of the 2nd window for enqueueing.
    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(4);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(
            1_000_000_000_000L,
            1_000_133_000_000L,
            1_000_133_000_000L + 2_000L - DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            1_000_133_000_000L + 2_000L)
        .inOrder();
    assertThat(mediaPeriodHolderFactoryInfos.get(3).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaPeriodHolderFactoryInfos.get(3).id.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(3).id.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(3).id.nextAdGroupIndex).isEqualTo(1);
    assertThat(mediaPeriodHolderFactoryInfos.get(3).id.windowSequenceNumber).isEqualTo(1);

    // Invalidating does keep the same state and does not create further periods.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(4);

    // Creates the mid-roll ad period of the 2nd window for enqueueing.
    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(5);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(
            1_000_000_000_000L,
            1_000_133_000_000L,
            1_000_133_002_000L - DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            1_000_133_002_000L,
            1_000_138_002_000L);
    assertThat(mediaPeriodHolderFactoryInfos.get(4).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaPeriodHolderFactoryInfos.get(4).id.adGroupIndex).isEqualTo(1);
    assertThat(mediaPeriodHolderFactoryInfos.get(4).id.adIndexInAdGroup).isEqualTo(0);
    assertThat(mediaPeriodHolderFactoryInfos.get(4).id.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(4).id.windowSequenceNumber).isEqualTo(1);

    // Invalidating does keep the same state and does not create further periods.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(5);

    // Creates the last content period of the 2nd window for enqueueing.
    enqueueNext();

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(6);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(
            1_000_000_000_000L,
            1_000_133_000_000L,
            1_000_133_002_000L - DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
            1_000_133_002_000L,
            1_000_138_002_000L,
            1_000_133_003_000L);
    assertThat(mediaPeriodHolderFactoryInfos.get(5).id.periodUid)
        .isEqualTo(playbackInfo.timeline.getUidOfPeriod(/* periodIndex= */ 1));
    assertThat(mediaPeriodHolderFactoryInfos.get(5).id.adGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(5).id.adIndexInAdGroup).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(5).id.nextAdGroupIndex).isEqualTo(-1);
    assertThat(mediaPeriodHolderFactoryInfos.get(5).id.windowSequenceNumber).isEqualTo(1);

    // Invalidating does keep the same state and does not create further periods.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);
    // Enqueue the first and only content period of the 3rd and last window from pool.
    enqueueNext();
    // No further next window. Invalidating is a no-op.
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);

    assertThat(mediaPeriodHolderFactoryInfos).hasSize(6);
  }

  @Test
  public void setPreloadConfiguration_disablePreloading_releasesPreloadHolders() {
    AtomicBoolean releaseCalled = new AtomicBoolean();
    FakeMediaSource preloadedSource =
        new FakeMediaSource(
            new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 1, "1234"))) {
          @Override
          public void releasePeriod(MediaPeriod mediaPeriod) {
            releaseCalled.set(true);
            super.releasePeriod(mediaPeriod);
          }
        };
    setupMediaSources(new FakeMediaSource(), preloadedSource);
    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, new PreloadConfiguration(/* targetPreloadDurationUs= */ 5_000_000L));
    enqueueNext();
    mediaPeriodQueue.invalidatePreloadPool(playbackInfo.timeline);
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_010_000_000L)
        .inOrder();
    assertThat(releaseCalled.get()).isFalse();

    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, PreloadConfiguration.DEFAULT);

    assertThat(releaseCalled.get()).isTrue();
  }

  @Test
  public void setPreloadConfiguration_enablePreloading_preloadHolderCreated() {
    setupTimelines(new FakeTimeline(), new FakeTimeline());
    enqueueNext();
    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets).containsExactly(1_000_000_000_000L);

    mediaPeriodQueue.updatePreloadConfiguration(
        playbackInfo.timeline, new PreloadConfiguration(/* targetPreloadDurationUs= */ 5_000_000L));

    assertThat(mediaPeriodHolderFactoryRendererPositionOffsets)
        .containsExactly(1_000_000_000_000L, 1_000_010_000_000L)
        .inOrder();
  }

  private void setupAdTimeline(long... adGroupTimesUs) {
    adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), adGroupTimesUs)
            .withContentDurationUs(CONTENT_DURATION_US);
    SinglePeriodAdTimeline adTimeline =
        new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    setupTimelines(adTimeline);
  }

  private void setupTimelines(Timeline... timelines) {
    FakeMediaSource[] sources = new FakeMediaSource[timelines.length];
    for (int i = 0; i < timelines.length; i++) {
      sources[i] = new FakeMediaSource(timelines[i]);
    }
    setupMediaSources(sources);
  }

  private void setupMediaSources(FakeMediaSource... mediaSources) {
    ImmutableList.Builder<MediaSourceList.MediaSourceHolder> mediaSourceHolders =
        new ImmutableList.Builder<>();
    for (FakeMediaSource source : mediaSources) {
      fakeMediaSources.add(source);
      MediaSourceList.MediaSourceHolder mediaSourceHolder =
          new MediaSourceList.MediaSourceHolder(source, /* useLazyPreparation= */ false);
      mediaSourceHolder.mediaSource.prepareSource(
          mock(MediaSourceCaller.class), /* mediaTransferListener= */ null, PlayerId.UNSET);
      mediaSourceHolders.add(mediaSourceHolder);
    }
    ImmutableList<MediaSourceList.MediaSourceHolder> holders = mediaSourceHolders.build();
    mediaSourceList.setMediaSources(holders, new FakeShuffleOrder(/* length= */ holders.size()));

    Timeline playlistTimeline = mediaSourceList.createTimeline();
    firstPeriodUid = playlistTimeline.getUidOfPeriod(/* periodIndex= */ 0);

    playbackInfo =
        new PlaybackInfo(
            playlistTimeline,
            mediaPeriodQueue.resolveMediaPeriodIdForAds(
                playlistTimeline, firstPeriodUid, /* positionUs= */ 0),
            /* requestedContentPositionUs= */ C.TIME_UNSET,
            /* discontinuityStartPositionUs= */ 0,
            Player.STATE_READY,
            /* playbackError= */ null,
            /* isLoading= */ false,
            /* trackGroups= */ null,
            /* trackSelectorResult= */ null,
            /* staticMetadata= */ ImmutableList.of(),
            /* loadingMediaPeriodId= */ null,
            /* playWhenReady= */ false,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            /* playbackParameters= */ PlaybackParameters.DEFAULT,
            /* bufferedPositionUs= */ 0,
            /* totalBufferedDurationUs= */ 0,
            /* positionUs= */ 0,
            /* positionUpdateTimeMs= */ 0,
            /* sleepingForOffload= */ false);
  }

  private void advance() {
    enqueueNext();
    if (mediaPeriodQueue.getLoadingPeriod() != mediaPeriodQueue.getPlayingPeriod()) {
      advancePlaying();
    }
  }

  private void advancePlaying() {
    mediaPeriodQueue.advancePlayingPeriod();
  }

  private void advanceReading() {
    mediaPeriodQueue.advanceReadingPeriod();
  }

  private void enqueueNext() {
    mediaPeriodQueue.enqueueNextMediaPeriodHolder(getNextMediaPeriodInfo());
  }

  private void clear() {
    mediaPeriodQueue.clear();
    playbackInfo =
        playbackInfo.copyWithNewPosition(
            mediaPeriodQueue.resolveMediaPeriodIdForAds(
                mediaSourceList.createTimeline(), firstPeriodUid, /* positionUs= */ 0),
            /* positionUs= */ 0,
            /* requestedContentPositionUs= */ C.TIME_UNSET,
            /* discontinuityStartPositionUs= */ 0,
            /* totalBufferedDurationUs= */ 0,
            /* trackGroups= */ null,
            /* trackSelectorResult= */ null,
            /* staticMetadata= */ ImmutableList.of());
  }

  @Nullable
  private MediaPeriodInfo getNextMediaPeriodInfo() {
    return mediaPeriodQueue.getNextMediaPeriodInfo(/* rendererPositionUs= */ 0, playbackInfo);
  }

  private void setAdGroupLoaded(int adGroupIndex) {
    long[][] newDurations = new long[adPlaybackState.adGroupCount][];
    for (int i = 0; i < adPlaybackState.adGroupCount; i++) {
      newDurations[i] =
          i == adGroupIndex
              ? new long[] {AD_DURATION_US}
              : adPlaybackState.getAdGroup(i).durationsUs;
    }
    adPlaybackState =
        adPlaybackState
            .withAdCount(adGroupIndex, /* adCount= */ 1)
            .withAvailableAdMediaItem(adGroupIndex, /* adIndexInAdGroup= */ 0, AD_MEDIA_ITEM)
            .withAdDurationsUs(newDurations);
    updateAdTimeline(/* mediaSourceIndex= */ 0);
  }

  private void setAdGroupPlayed(int adGroupIndex) {
    for (int i = 0; i < adPlaybackState.getAdGroup(adGroupIndex).count; i++) {
      adPlaybackState = adPlaybackState.withPlayedAd(adGroupIndex, /* adIndexInAdGroup= */ i);
    }
    updateAdTimeline(/* mediaSourceIndex= */ 0);
  }

  private void setAdGroupFailedToLoad(int adGroupIndex) {
    adPlaybackState =
        adPlaybackState
            .withAdCount(adGroupIndex, /* adCount= */ 1)
            .withAdLoadError(adGroupIndex, /* adIndexInAdGroup= */ 0);
    updateAdTimeline(/* mediaSourceIndex= */ 0);
  }

  private void updateAdPlaybackStateAndTimeline(long... adGroupTimesUs) {
    adPlaybackState =
        new AdPlaybackState(/* adsId= */ new Object(), adGroupTimesUs)
            .withContentDurationUs(CONTENT_DURATION_US);
    updateAdTimeline(/* mediaSourceIndex= */ 0);
  }

  private void updateAdTimeline(int mediaSourceIndex) {
    SinglePeriodAdTimeline adTimeline =
        new SinglePeriodAdTimeline(CONTENT_TIMELINE, adPlaybackState);
    fakeMediaSources.get(mediaSourceIndex).setNewSourceInfo(adTimeline);
    // Progress the looper so that the source info events have been executed.
    shadowOf(Looper.getMainLooper()).idle();
    playbackInfo = playbackInfo.copyWithTimeline(mediaSourceList.createTimeline());
  }

  private void assertGetNextMediaPeriodInfoReturnsContentMediaPeriod(
      Object periodUid,
      long startPositionUs,
      long requestedContentPositionUs,
      long endPositionUs,
      long durationUs,
      boolean isPrecededByTransitionFromSameStream,
      boolean isFollowedByTransitionToSameStream,
      boolean isLastInPeriod,
      boolean isLastInWindow,
      boolean isFinal,
      int nextAdGroupIndex) {
    assertThat(getNextMediaPeriodInfo())
        .isEqualTo(
            new MediaPeriodInfo(
                new MediaPeriodId(periodUid, /* windowSequenceNumber= */ 0, nextAdGroupIndex),
                startPositionUs,
                requestedContentPositionUs,
                endPositionUs,
                durationUs,
                isPrecededByTransitionFromSameStream,
                isFollowedByTransitionToSameStream,
                isLastInPeriod,
                isLastInWindow,
                isFinal));
  }

  private void assertNextMediaPeriodInfoIsAd(
      Object periodUid,
      int adGroupIndex,
      long adDurationUs,
      long contentPositionUs,
      boolean isPrecededByTransitionFromSameStream,
      boolean isFollowedByTransitionToSameStream) {
    assertThat(getNextMediaPeriodInfo())
        .isEqualTo(
            new MediaPeriodInfo(
                new MediaPeriodId(
                    periodUid,
                    adGroupIndex,
                    /* adIndexInAdGroup= */ 0,
                    /* windowSequenceNumber= */ 0),
                /* startPositionUs= */ 0,
                /* requestedContentPositionUs= */ contentPositionUs,
                /* endPositionUs= */ C.TIME_UNSET,
                adDurationUs,
                isPrecededByTransitionFromSameStream,
                isFollowedByTransitionToSameStream,
                /* isLastInTimelinePeriod= */ false,
                /* isLastInTimelineWindow= */ false,
                /* isFinal= */ false));
  }

  private int getQueueLength() {
    int length = 0;
    MediaPeriodHolder periodHolder = mediaPeriodQueue.getPlayingPeriod();
    while (periodHolder != null) {
      length++;
      periodHolder = periodHolder.getNext();
    }
    return length;
  }

  private static Timeline createMultiPeriodServerSideInsertedTimeline(
      Object windowId, int numberOfPlayedAds, boolean... isAdPeriodFlags)
      throws InterruptedException {
    FakeTimeline fakeContentTimeline =
        new FakeTimeline(
            new TimelineWindowDefinition(
                isAdPeriodFlags.length,
                windowId,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* isLive= */ false,
                /* isPlaceholder= */ false,
                /* durationUs= */ DEFAULT_WINDOW_DURATION_US,
                /* defaultPositionUs= */ 0,
                /* windowOffsetInFirstPeriodUs= */ DEFAULT_WINDOW_OFFSET_IN_FIRST_PERIOD_US,
                /* adPlaybackStates= */ ImmutableList.of(AdPlaybackState.NONE),
                MediaItem.EMPTY));
    ImmutableMap<Object, AdPlaybackState> adPlaybackStates =
        FakeTimeline.createMultiPeriodAdTimeline(windowId, numberOfPlayedAds, isAdPeriodFlags)
            .getAdPlaybackStates(/* windowIndex= */ 0);
    ServerSideAdInsertionMediaSource serverSideAdInsertionMediaSource =
        new ServerSideAdInsertionMediaSource(
            new FakeMediaSource(fakeContentTimeline, VIDEO_FORMAT, AUDIO_FORMAT),
            contentTimeline -> false);
    serverSideAdInsertionMediaSource.setAdPlaybackStates(adPlaybackStates, fakeContentTimeline);
    AtomicReference<Timeline> serverSideAdInsertionTimelineRef = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(/* count= */ 1);
    serverSideAdInsertionMediaSource.prepareSource(
        (source, serverSideInsertedAdTimeline) -> {
          serverSideAdInsertionTimelineRef.set(serverSideInsertedAdTimeline);
          countDownLatch.countDown();
        },
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    if (!countDownLatch.await(/* timeout= */ 2, SECONDS)) {
      fail();
    }
    return serverSideAdInsertionTimelineRef.get();
  }
}
