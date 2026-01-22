/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.exoplayer.source.ads;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MaskingMediaSource;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.MediaSource.MediaSourceCaller;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import androidx.media3.exoplayer.source.ads.AdsLoader.EventListener;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.test.utils.robolectric.RobolectricUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AdsMediaSource}. */
@RunWith(AndroidJUnit4.class)
public final class AdsMediaSourceTest {

  private static final long PREROLL_AD_DURATION_US = 10 * C.MICROS_PER_SECOND;
  private static final Timeline PREROLL_AD_TIMELINE =
      new SinglePeriodTimeline(
          PREROLL_AD_DURATION_US,
          /* isSeekable= */ true,
          /* isDynamic= */ false,
          /* useLiveConfiguration= */ false,
          /* manifest= */ null,
          MediaItem.fromUri(Uri.parse("https://google.com/empty")));
  private static final Object PREROLL_AD_PERIOD_UID =
      PREROLL_AD_TIMELINE.getUidOfPeriod(/* periodIndex= */ 0);

  private static final long CONTENT_DURATION_US = 30 * C.MICROS_PER_SECOND;
  private static final Timeline CONTENT_TIMELINE =
      new SinglePeriodTimeline(
          CONTENT_DURATION_US,
          /* isSeekable= */ true,
          /* isDynamic= */ false,
          /* useLiveConfiguration= */ false,
          /* manifest= */ null,
          FakeMediaSource.FAKE_MEDIA_ITEM);
  private static final Timeline PLACEHOLDER_CONTENT_TIMELINE =
      new MaskingMediaSource.PlaceholderTimeline(FakeMediaSource.FAKE_MEDIA_ITEM);
  private static final Object CONTENT_PERIOD_UID =
      CONTENT_TIMELINE.getUidOfPeriod(/* periodIndex= */ 0);

  private static final AdPlaybackState PREROLL_AD_PLAYBACK_STATE =
      new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0)
          .withContentDurationUs(CONTENT_DURATION_US)
          .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
          .withAvailableAdMediaItem(
              /* adGroupIndex= */ 0,
              /* adIndexInAdGroup= */ 0,
              MediaItem.fromUri("https://google.com/ad"))
          .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
          .withAdResumePositionUs(/* adResumePositionUs= */ 0);

  private static final DataSpec TEST_ADS_DATA_SPEC = new DataSpec(Uri.EMPTY);
  private static final Object TEST_ADS_ID = new Object();
  private static final long TIMEOUT_MS = 5_000L;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private FakeMediaSource contentMediaSource;
  private FakeMediaSource prerollAdMediaSource;
  @Mock private MediaSourceCaller mockMediaSourceCaller;
  private AdsMediaSource adsMediaSource;
  private EventListener adsLoaderEventListener;

  @Before
  public void setUp() {
    // Set up content and ad media sources, passing a null timeline so tests can simulate setting it
    // later.
    contentMediaSource = new FakeMediaSource(/* timeline= */ null);
    prerollAdMediaSource = new FakeMediaSource(/* timeline= */ null);
    MediaSource.Factory adMediaSourceFactory = mock(MediaSource.Factory.class);
    when(adMediaSourceFactory.createMediaSource(any(MediaItem.class)))
        .thenReturn(prerollAdMediaSource);

    // Prepare the AdsMediaSource and capture its ads loader listener.
    AdsLoader mockAdsLoader = mock(AdsLoader.class);
    AdViewProvider mockAdViewProvider = mock(AdViewProvider.class);
    ArgumentCaptor<EventListener> eventListenerArgumentCaptor =
        ArgumentCaptor.forClass(AdsLoader.EventListener.class);
    adsMediaSource =
        new AdsMediaSource(
            contentMediaSource,
            TEST_ADS_DATA_SPEC,
            TEST_ADS_ID,
            adMediaSourceFactory,
            mockAdsLoader,
            mockAdViewProvider,
            /* useLazyContentSourcePreparation= */ true);
    adsMediaSource.prepareSource(
        mockMediaSourceCaller, /* mediaTransferListener= */ null, PlayerId.UNSET);
    shadowOf(Looper.getMainLooper()).idle();
    verify(mockAdsLoader)
        .start(
            eq(adsMediaSource),
            eq(TEST_ADS_DATA_SPEC),
            eq(TEST_ADS_ID),
            eq(mockAdViewProvider),
            eventListenerArgumentCaptor.capture());
    adsLoaderEventListener = eventListenerArgumentCaptor.getValue();
  }

  private void setAdPlaybackState(AdPlaybackState adPlaybackState) {
    adsLoaderEventListener.onAdPlaybackState(adPlaybackState);
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void createPeriod_forPreroll_preparesChildAdMediaSourceAndRefreshesSourceInfo() {
    setAdPlaybackState(PREROLL_AD_PLAYBACK_STATE);
    // This should be unused if we only create the preroll period.
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE);
    adsMediaSource.createPeriod(
        new MediaPeriodId(
            CONTENT_PERIOD_UID,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(prerollAdMediaSource.isPrepared()).isTrue();
    verify(mockMediaSourceCaller)
        .onSourceInfoRefreshed(
            adsMediaSource,
            new SinglePeriodAdTimeline(PLACEHOLDER_CONTENT_TIMELINE, PREROLL_AD_PLAYBACK_STATE));
  }

  @Test
  public void
      createPeriod_forPreroll_preparesChildAdMediaSourceAndRefreshesSourceInfoWithAdMediaSourceInfo() {
    setAdPlaybackState(PREROLL_AD_PLAYBACK_STATE);
    // This should be unused if we only create the preroll period.
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE);
    adsMediaSource.createPeriod(
        new MediaPeriodId(
            CONTENT_PERIOD_UID,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);
    prerollAdMediaSource.setNewSourceInfo(PREROLL_AD_TIMELINE);
    shadowOf(Looper.getMainLooper()).idle();

    verify(mockMediaSourceCaller)
        .onSourceInfoRefreshed(
            adsMediaSource,
            new SinglePeriodAdTimeline(
                PLACEHOLDER_CONTENT_TIMELINE,
                PREROLL_AD_PLAYBACK_STATE.withAdDurationsUs(
                    new long[][] {{PREROLL_AD_DURATION_US}})));
  }

  @Test
  public void createPeriod_forPreroll_createsChildPrerollAdMediaPeriod() {
    setAdPlaybackState(PREROLL_AD_PLAYBACK_STATE);
    adsMediaSource.createPeriod(
        new MediaPeriodId(
            CONTENT_PERIOD_UID,
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0,
            /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);
    prerollAdMediaSource.setNewSourceInfo(PREROLL_AD_TIMELINE);
    shadowOf(Looper.getMainLooper()).idle();

    prerollAdMediaSource.assertMediaPeriodCreated(
        new MediaPeriodId(PREROLL_AD_PERIOD_UID, /* windowSequenceNumber= */ 0));
  }

  @Test
  public void createPeriod_forContent_createsChildContentMediaPeriodAndLoadsContentTimeline() {
    setAdPlaybackState(PREROLL_AD_PLAYBACK_STATE);
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE);
    shadowOf(Looper.getMainLooper()).idle();
    adsMediaSource.createPeriod(
        new MediaPeriodId(CONTENT_PERIOD_UID, /* windowSequenceNumber= */ 0),
        mock(Allocator.class),
        /* startPositionUs= */ 0);

    contentMediaSource.assertMediaPeriodCreated(
        new MediaPeriodId(CONTENT_PERIOD_UID, /* windowSequenceNumber= */ 0));
    ArgumentCaptor<Timeline> adsTimelineCaptor = ArgumentCaptor.forClass(Timeline.class);
    verify(mockMediaSourceCaller, times(2))
        .onSourceInfoRefreshed(eq(adsMediaSource), adsTimelineCaptor.capture());
    TestUtil.timelinesAreSame(
        adsTimelineCaptor.getValue(),
        new SinglePeriodAdTimeline(CONTENT_TIMELINE, PREROLL_AD_PLAYBACK_STATE));
  }

  @Test
  public void releasePeriod_releasesChildMediaPeriodsAndSources() {
    setAdPlaybackState(PREROLL_AD_PLAYBACK_STATE);
    contentMediaSource.setNewSourceInfo(CONTENT_TIMELINE);
    MediaPeriod prerollAdMediaPeriod =
        adsMediaSource.createPeriod(
            new MediaPeriodId(
                CONTENT_PERIOD_UID,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0),
            mock(Allocator.class),
            /* startPositionUs= */ 0);
    prerollAdMediaSource.setNewSourceInfo(PREROLL_AD_TIMELINE);
    shadowOf(Looper.getMainLooper()).idle();
    MediaPeriod contentMediaPeriod =
        adsMediaSource.createPeriod(
            new MediaPeriodId(CONTENT_PERIOD_UID, /* windowSequenceNumber= */ 0),
            mock(Allocator.class),
            /* startPositionUs= */ 0);
    adsMediaSource.releasePeriod(prerollAdMediaPeriod);

    prerollAdMediaSource.assertReleased();

    adsMediaSource.releasePeriod(contentMediaPeriod);
    adsMediaSource.releaseSource(mockMediaSourceCaller);
    shadowOf(Looper.getMainLooper()).idle();
    prerollAdMediaSource.assertReleased();
    contentMediaSource.assertReleased();
  }

  @Test
  public void canUpdateMediaItem_withIrrelevantFieldsChanged_returnsTrue() {
    MediaItem initialMediaItem =
        new MediaItem.Builder()
            .setUri("http://test.uri")
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.parse("http://ad.tag.test")).build())
            .build();
    MediaItem updatedMediaItem =
        TestUtil.buildFullyCustomizedMediaItem()
            .buildUpon()
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.parse("http://ad.tag.test")).build())
            .build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);

    boolean canUpdateMediaItem = mediaSource.canUpdateMediaItem(updatedMediaItem);

    assertThat(canUpdateMediaItem).isTrue();
  }

  @Test
  public void canUpdateMediaItem_withChangedAdsConfiguration_returnsFalse() {
    MediaItem initialMediaItem =
        new MediaItem.Builder()
            .setUri("http://test.uri")
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.parse("http://ad.tag.test")).build())
            .build();
    MediaItem updatedMediaItem =
        new MediaItem.Builder()
            .setUri("http://test.uri")
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.parse("http://other.tag.test")).build())
            .build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);

    boolean canUpdateMediaItem = mediaSource.canUpdateMediaItem(updatedMediaItem);

    assertThat(canUpdateMediaItem).isFalse();
  }

  @Test
  public void updateMediaItem_createsTimelineWithUpdatedItem() throws Exception {
    MediaItem initialMediaItem = new MediaItem.Builder().setUri("http://test.test").build();
    MediaItem updatedMediaItem = new MediaItem.Builder().setUri("http://test2.test").build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);
    AtomicReference<Timeline> timelineReference = new AtomicReference<>();

    mediaSource.updateMediaItem(updatedMediaItem);
    mediaSource.prepareSource(
        (source, timeline) -> timelineReference.set(timeline),
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    RobolectricUtil.runMainLooperUntil(() -> timelineReference.get() != null);

    assertThat(
            timelineReference
                .get()
                .getWindow(/* windowIndex= */ 0, new Timeline.Window())
                .mediaItem)
        .isEqualTo(updatedMediaItem);
  }

  @Test
  public void
      prepare_withPrerollUsingLazyContentSourcePreparationFalse_allExternalTimelinesWithAds()
          throws InterruptedException {
    AtomicBoolean contentMediaPeriodCreated = new AtomicBoolean();
    MediaSource fakeContentMediaSource =
        new FakeMediaSource() {
          @Override
          public MediaPeriod createPeriod(
              MediaPeriodId id, Allocator allocator, long startPositionUs) {
            contentMediaPeriodCreated.set(true);
            return super.createPeriod(id, allocator, startPositionUs);
          }
        };
    CountDownLatch adSourcePreparedLatch = new CountDownLatch(1);
    AtomicInteger adSourcePreparedCounter = new AtomicInteger();
    List<MediaPeriodId> createdAdMediaPeriodIds = new ArrayList<>();
    MediaSource fakeAdMediaSource =
        new FakeMediaSource() {
          @Override
          public synchronized void prepareSourceInternal(
              @Nullable TransferListener mediaTransferListener) {
            adSourcePreparedLatch.countDown();
            adSourcePreparedCounter.incrementAndGet();
            super.prepareSourceInternal(mediaTransferListener);
          }

          @Override
          public MediaPeriod createPeriod(
              MediaPeriodId id, Allocator allocator, long startPositionUs) {
            createdAdMediaPeriodIds.add(id);
            return super.createPeriod(id, allocator, startPositionUs);
          }
        };
    CountDownLatch contentTimelineChangedCalledLatch = new CountDownLatch(1);
    AtomicReference<EventListener> eventListenerRef = new AtomicReference<>();
    AdsLoader fakeAdsLoader =
        new NoOpAdsLoader() {
          @Override
          public void start(
              AdsMediaSource adsMediaSource,
              DataSpec adTagDataSpec,
              Object adsId,
              AdViewProvider adViewProvider,
              EventListener eventListener) {
            eventListenerRef.set(eventListener);
          }

          @Override
          public void handleContentTimelineChanged(
              AdsMediaSource adsMediaSource, Timeline timeline) {
            contentTimelineChangedCalledLatch.countDown();
          }
        };
    MediaSource.Factory adMediaSourceFactory = mock(MediaSource.Factory.class);
    when(adMediaSourceFactory.createMediaSource(any(MediaItem.class)))
        .thenReturn(fakeAdMediaSource);
    // Prepare the AdsMediaSource and capture the event listener the ads loader receives.
    AdsMediaSource adsMediaSource =
        new AdsMediaSource(
            fakeContentMediaSource,
            TEST_ADS_DATA_SPEC,
            TEST_ADS_ID,
            adMediaSourceFactory,
            fakeAdsLoader,
            mock(AdViewProvider.class),
            /* useLazyContentSourcePreparation= */ false);
    AtomicInteger mediaSourceCallerCallCounter = new AtomicInteger();
    List<Timeline> externallyReceivedTimelines = new ArrayList<>();
    List<MediaPeriodId> externallyRequestedPeriods = new ArrayList<>();
    MediaSource.MediaSourceCaller fakeMediaSourceCaller =
        (source, timeline) -> {
          // The caller creates a media period at position 0 according to the timeline.
          mediaSourceCallerCallCounter.incrementAndGet();
          externallyReceivedTimelines.add(timeline);
          Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
          Timeline.Period period =
              timeline.getPeriod(
                  window.firstPeriodIndex, new Timeline.Period(), /* setIds= */ true);
          // Search for pre roll ad group if any.
          int adGroupIndex =
              period.adPlaybackState.getAdGroupIndexForPositionUs(
                  window.positionInFirstPeriodUs, period.durationUs);
          MediaPeriodId mediaPeriodId =
              adGroupIndex == C.INDEX_UNSET
                  ? new MediaPeriodId(period.uid, /* windowSequenceNumber= */ 0L)
                  : new MediaPeriodId(
                      123L,
                      /* adGroupIndex= */ adGroupIndex,
                      /* adIndexInAdGroup= */ 0,
                      /* windowSequenceNumber= */ 0L);
          externallyRequestedPeriods.add(mediaPeriodId);
          // Create a media period immediately regardless whether it is the same as before.
          source.createPeriod(mediaPeriodId, mock(Allocator.class), /* startPositionUs= */ 0L);
        };

    // Prepare the source which must not notify the caller with a timeline yet.
    adsMediaSource.prepareSource(
        fakeMediaSourceCaller, /* mediaTransferListener= */ null, PlayerId.UNSET);
    shadowOf(Looper.getMainLooper()).idle();

    // Verify ads loader was called with the content timeline to allow populating the ads.
    assertThat(contentTimelineChangedCalledLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    // Verify external caller not yet notified even when content timeline available.
    assertThat(mediaSourceCallerCallCounter.get()).isEqualTo(0);
    // Verify no content media period has been created.
    assertThat(contentMediaPeriodCreated.get()).isFalse();
    // Verify ad source not yet prepared.
    assertThat(adSourcePreparedCounter.get()).isEqualTo(0);

    // Setting the ad playback state allows the outer AdsMediaSource to complete
    // preparation of the AdsMediaSource that makes the external caller create the first period
    // according to the timeline.
    eventListenerRef
        .get()
        .onAdPlaybackState(
            new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0)
                .withContentDurationUs(CONTENT_DURATION_US)
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("https://google.com/ad"))
                .withAdResumePositionUs(/* adResumePositionUs= */ 0)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 10_000_000L));
    shadowOf(Looper.getMainLooper()).idle();

    // Ad source was prepared once.
    assertThat(adSourcePreparedCounter.get()).isEqualTo(1);
    // Verify that no content period was created. Content source prepared only to get the playlist.
    assertThat(contentMediaPeriodCreated.get()).isFalse();
    // Verify the caller got two timeline updates.
    assertThat(mediaSourceCallerCallCounter.get()).isEqualTo(2);
    // Verify whether every externally exposed timeline was augmented with ad data.
    assertThat(externallyRequestedPeriods)
        .containsExactly(
            new MediaPeriodId(
                123L,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0L),
            new MediaPeriodId(
                123L,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0L))
        .inOrder();
    // Verify the requested media ID in the child ad sources without ad data.
    assertThat(createdAdMediaPeriodIds)
        .containsExactly(
            new MediaPeriodId(
                new Pair<>(0, 0),
                /* adGroupIndex= */ -1,
                /* adIndexInAdGroup= */ -1,
                /* windowSequenceNumber= */ 0L),
            new MediaPeriodId(
                new Pair<>(0, 0),
                /* adGroupIndex= */ -1,
                /* adIndexInAdGroup= */ -1,
                /* windowSequenceNumber= */ 0L))
        .inOrder();
    // Verify all external exposed timelines contained ad data with the duration updated according
    // to the actual duration of the ad sources.
    assertThat(externallyReceivedTimelines).hasSize(2);
    assertThat(
            externallyReceivedTimelines
                .get(0)
                .getPeriod(0, new Timeline.Period())
                .getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(C.TIME_UNSET); // Overridden by AdsMediaSource before the source was prepared.
    assertThat(
            externallyReceivedTimelines
                .get(1)
                .getPeriod(0, new Timeline.Period())
                .getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(133_000_000); // Overridden by AdsMediaSource with the actual source duration.
  }

  @Test
  public void prepare_withPrerollUsingLazyContentSourcePreparationTrue_allExternalTimelinesWithAds()
      throws InterruptedException {
    AtomicBoolean contentMediaPeriodCreated = new AtomicBoolean();
    MediaSource fakeContentMediaSource =
        new FakeMediaSource() {
          @Override
          public MediaPeriod createPeriod(
              MediaPeriodId id, Allocator allocator, long startPositionUs) {
            contentMediaPeriodCreated.set(true);
            return super.createPeriod(id, allocator, startPositionUs);
          }
        };
    CountDownLatch adSourcePreparedLatch = new CountDownLatch(1);
    AtomicInteger adSourcePreparedCounter = new AtomicInteger();
    List<MediaPeriodId> createdAdMediaPeriodIds = new ArrayList<>();
    MediaSource fakeAdMediaSource =
        new FakeMediaSource() {
          @Override
          public synchronized void prepareSourceInternal(
              @Nullable TransferListener mediaTransferListener) {
            adSourcePreparedLatch.countDown();
            adSourcePreparedCounter.incrementAndGet();
            super.prepareSourceInternal(mediaTransferListener);
          }

          @Override
          public MediaPeriod createPeriod(
              MediaPeriodId id, Allocator allocator, long startPositionUs) {
            createdAdMediaPeriodIds.add(id);
            return super.createPeriod(id, allocator, startPositionUs);
          }
        };
    AtomicInteger contentTimelineChangedCallCount = new AtomicInteger();
    AtomicReference<EventListener> eventListenerRef = new AtomicReference<>();
    AdsLoader fakeAdsLoader =
        new NoOpAdsLoader() {
          @Override
          public void start(
              AdsMediaSource adsMediaSource,
              DataSpec adTagDataSpec,
              Object adsId,
              AdViewProvider adViewProvider,
              EventListener eventListener) {
            eventListenerRef.set(eventListener);
          }

          @Override
          public void handleContentTimelineChanged(
              AdsMediaSource adsMediaSource, Timeline timeline) {
            contentTimelineChangedCallCount.incrementAndGet();
          }
        };
    MediaSource.Factory adMediaSourceFactory = mock(MediaSource.Factory.class);
    when(adMediaSourceFactory.createMediaSource(any(MediaItem.class)))
        .thenReturn(fakeAdMediaSource);
    // Prepare the AdsMediaSource and capture the event listener the ads loader receives.
    AdsMediaSource adsMediaSource =
        new AdsMediaSource(
            fakeContentMediaSource,
            TEST_ADS_DATA_SPEC,
            TEST_ADS_ID,
            adMediaSourceFactory,
            fakeAdsLoader,
            mock(AdViewProvider.class),
            /* useLazyContentSourcePreparation= */ true);
    AtomicInteger mediaSourceCallerCallCounter = new AtomicInteger();
    List<Timeline> externallyReceivedTimelines = new ArrayList<>();
    List<MediaPeriodId> externallyRequestedPeriods = new ArrayList<>();
    MediaSource.MediaSourceCaller fakeMediaSourceCaller =
        (source, timeline) -> {
          mediaSourceCallerCallCounter.incrementAndGet();
          externallyReceivedTimelines.add(timeline);
          Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
          Timeline.Period period =
              timeline.getPeriod(
                  window.firstPeriodIndex, new Timeline.Period(), /* setIds= */ true);
          // Search for the preroll ad group.
          int adGroupIndex =
              period.adPlaybackState.getAdGroupIndexForPositionUs(
                  window.positionInFirstPeriodUs, period.durationUs);
          MediaPeriodId mediaPeriodId =
              adGroupIndex == C.INDEX_UNSET
                  ? new MediaPeriodId(period.uid, /* windowSequenceNumber= */ 0L)
                  : new MediaPeriodId(
                      123L,
                      /* adGroupIndex= */ adGroupIndex,
                      /* adIndexInAdGroup= */ 0,
                      /* windowSequenceNumber= */ 0L);
          externallyRequestedPeriods.add(mediaPeriodId);
          // Create a media period immediately.
          source.createPeriod(mediaPeriodId, mock(Allocator.class), /* startPositionUs= */ 0L);
        };

    // Prepare the source that must not result in an external timeline without ad data.
    adsMediaSource.prepareSource(
        fakeMediaSourceCaller, /* mediaTransferListener= */ null, PlayerId.UNSET);
    shadowOf(Looper.getMainLooper()).idle();

    // External caller not yet notified.
    assertThat(mediaSourceCallerCallCounter.get()).isEqualTo(0);
    // Verify that the content source is not prepared. Must never happen.
    assertThat(contentTimelineChangedCallCount.get()).isEqualTo(0);
    // Verify that th ad source is not yet prepared.
    assertThat(adSourcePreparedCounter.get()).isEqualTo(0);

    // Setting the ad playback state allows the outer AdsMediaSource to complete
    // preparation of the AdsMediaSource that makes the external caller create the first period
    // according to the timeline.
    eventListenerRef
        .get()
        .onAdPlaybackState(
            new AdPlaybackState(/* adsId= */ new Object(), /* adGroupTimesUs...= */ 0)
                .withContentDurationUs(CONTENT_DURATION_US)
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("https://google.com/ad"))
                .withAdResumePositionUs(/* adResumePositionUs= */ 0)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 10_000_000L));
    shadowOf(Looper.getMainLooper()).idle();

    // Content source not prepared.
    assertThat(contentTimelineChangedCallCount.get()).isEqualTo(0);
    // Verify that the ad source was prepared once.
    assertThat(adSourcePreparedCounter.get()).isEqualTo(1);
    // Verify that no content period was created.
    assertThat(contentMediaPeriodCreated.get()).isFalse();
    // Verify the caller got two timeline updates.
    assertThat(mediaSourceCallerCallCounter.get()).isEqualTo(2);
    // Verify whether every externally exposed timeline was augmented with ad data.
    assertThat(externallyRequestedPeriods)
        .containsExactly(
            new MediaPeriodId(
                123L,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0L),
            new MediaPeriodId(
                123L,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                /* windowSequenceNumber= */ 0L))
        .inOrder();
    // Verify the requested media ID in the child ad sources without ad data.
    assertThat(createdAdMediaPeriodIds)
        .containsExactly(
            new MediaPeriodId(
                new Pair<>(0, 0),
                /* adGroupIndex= */ -1,
                /* adIndexInAdGroup= */ -1,
                /* windowSequenceNumber= */ 0L),
            new MediaPeriodId(
                new Pair<>(0, 0),
                /* adGroupIndex= */ -1,
                /* adIndexInAdGroup= */ -1,
                /* windowSequenceNumber= */ 0L))
        .inOrder();
    // Verify all external exposed timeline contained ad data with the duration updated according
    // to the actual duration of the ad sources.
    assertThat(externallyReceivedTimelines).hasSize(2);
    assertThat(
            externallyReceivedTimelines
                .get(0)
                .getPeriod(0, new Timeline.Period())
                .getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(C.TIME_UNSET); // Overridden by AdsMediaSource before the source was prepared.
    assertThat(
            externallyReceivedTimelines
                .get(1)
                .getPeriod(0, new Timeline.Period())
                .getAdDurationUs(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0))
        .isEqualTo(133_000_000); // Overridden by AdsMediaSource with the actual source duration.
  }

  @Test
  public void onAdPlaybackState_correctAdPlaybackStateInTimeline() {
    ArgumentCaptor<Timeline> timelineCaptor = ArgumentCaptor.forClass(Timeline.class);

    setAdPlaybackState(PREROLL_AD_PLAYBACK_STATE);

    verify(mockMediaSourceCaller).onSourceInfoRefreshed(any(), timelineCaptor.capture());
    assertThat(
            timelineCaptor
                .getValue()
                .getPeriod(/* periodIndex= */ 0, new Timeline.Period())
                .adPlaybackState)
        .isEqualTo(PREROLL_AD_PLAYBACK_STATE);
  }

  @Test
  public void onAdPlaybackState_growingLiveAdPlaybackState_correctAdPlaybackStateInTimeline() {
    AdPlaybackState initialLiveAdPlaybackState =
        new AdPlaybackState("adsId")
            .withLivePostrollPlaceholderAppended(/* isServerSideInserted= */ false);
    AdPlaybackState singleAdInFirstAdGroup =
        initialLiveAdPlaybackState
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0L)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                MediaItem.fromUri("https://example.com/ad0-0"));
    AdPlaybackState twoAdsInFirstAdGroup =
        singleAdInFirstAdGroup
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000L, 2_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 3_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 1,
                MediaItem.fromUri("https://example.com/ad0-1"));
    AdPlaybackState singleAdInSecondAdGroup =
        twoAdsInFirstAdGroup
            .withNewAdGroup(/* adGroupIndex= */ 1, /* adGroupTimeUs= */ 10_000L)
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 1, 10_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 1, 10_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 1,
                /* adIndexInAdGroup= */ 0,
                MediaItem.fromUri("https://example.com/ad1-0"));
    AdPlaybackState twoAdsInSecondAdGroup =
        singleAdInSecondAdGroup
            .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 2)
            .withAdDurationsUs(/* adGroupIndex= */ 1, 10_000L, 20_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 1, 30_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 1,
                /* adIndexInAdGroup= */ 1,
                MediaItem.fromUri("https://example.com/ad1-1"));
    ArgumentCaptor<Timeline> timelineCaptor = ArgumentCaptor.forClass(Timeline.class);

    setAdPlaybackState(initialLiveAdPlaybackState);
    setAdPlaybackState(singleAdInFirstAdGroup);
    setAdPlaybackState(twoAdsInFirstAdGroup);
    setAdPlaybackState(singleAdInSecondAdGroup);
    setAdPlaybackState(twoAdsInSecondAdGroup);

    verify(mockMediaSourceCaller, times(5)).onSourceInfoRefreshed(any(), timelineCaptor.capture());
    assertThat(
            timelineCaptor
                .getAllValues()
                .get(0)
                .getPeriod(0, new Timeline.Period())
                .adPlaybackState)
        .isEqualTo(initialLiveAdPlaybackState);
    assertThat(
            timelineCaptor
                .getAllValues()
                .get(1)
                .getPeriod(0, new Timeline.Period())
                .adPlaybackState)
        .isEqualTo(
            singleAdInFirstAdGroup.withAdDurationsUs(
                /* adGroupIndex= */ 0,
                /* adDurationsUs...= */ C.TIME_UNSET)); // durations are overridden by ads source
    assertThat(
            timelineCaptor
                .getAllValues()
                .get(2)
                .getPeriod(0, new Timeline.Period())
                .adPlaybackState)
        .isEqualTo(
            twoAdsInFirstAdGroup.withAdDurationsUs(
                /* adGroupIndex= */ 0, /* adDurationsUs...= */ C.TIME_UNSET, C.TIME_UNSET));
    assertThat(
            timelineCaptor
                .getAllValues()
                .get(3)
                .getPeriod(0, new Timeline.Period())
                .adPlaybackState)
        .isEqualTo(
            singleAdInSecondAdGroup
                .withAdDurationsUs(
                    /* adGroupIndex= */ 0, /* adDurationsUs...= */ C.TIME_UNSET, C.TIME_UNSET)
                .withAdDurationsUs(/* adGroupIndex= */ 1, /* adDurationsUs...= */ C.TIME_UNSET));
    assertThat(
            timelineCaptor
                .getAllValues()
                .get(4)
                .getPeriod(0, new Timeline.Period())
                .adPlaybackState)
        .isEqualTo(
            twoAdsInSecondAdGroup
                .withAdDurationsUs(
                    /* adGroupIndex= */ 0, /* adDurationsUs...= */ C.TIME_UNSET, C.TIME_UNSET)
                .withAdDurationsUs(
                    /* adGroupIndex= */ 1, /* adDurationsUs...= */ C.TIME_UNSET, C.TIME_UNSET));
  }

  @Test
  public void
      onAdPlaybackState_shrinkingAdPlaybackStateForLiveStream_throwsIllegalStateException() {
    AdPlaybackState initialLiveAdPlaybackState =
        new AdPlaybackState("adsId")
            .withLivePostrollPlaceholderAppended(/* isServerSideInserted= */ false)
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0L)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                MediaItem.fromUri("https://example.com/ad0-0"));
    setAdPlaybackState(initialLiveAdPlaybackState);

    assertThrows(
        IllegalStateException.class,
        () ->
            setAdPlaybackState(
                new AdPlaybackState("adsId")
                    .withLivePostrollPlaceholderAppended(/* isServerSideInserted= */ false)));
  }

  @Test
  public void onAdPlaybackState_timeUsOfAdGroupChanged_throwsIllegalStateException() {
    AdPlaybackState initialLiveAdPlaybackState =
        new AdPlaybackState("adsId")
            .withLivePostrollPlaceholderAppended(/* isServerSideInserted= */ false)
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0L)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                MediaItem.fromUri("https://example.com/ad0-0"));
    setAdPlaybackState(initialLiveAdPlaybackState);

    assertThrows(
        IllegalStateException.class,
        () ->
            setAdPlaybackState(
                initialLiveAdPlaybackState.withAdGroupTimeUs(
                    /* adGroupIndex= */ 0, /* adGroupTimeUs= */ 1234L)));
  }

  @Test
  public void onAdPlaybackState_mediaItemOfAdChanged_throwsIllegalStateException() {
    AdPlaybackState initialLiveAdPlaybackState =
        new AdPlaybackState("adsId")
            .withLivePostrollPlaceholderAppended(/* isServerSideInserted= */ false)
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0L)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                MediaItem.fromUri("https://example.com/ad0-0"));
    setAdPlaybackState(initialLiveAdPlaybackState);

    assertThrows(
        IllegalStateException.class,
        () ->
            setAdPlaybackState(
                initialLiveAdPlaybackState.withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("https://example.com/ad0-1"))));
  }

  @Test
  public void onAdPlaybackState_postRollAdded_throwsIllegalStateException() {
    AdPlaybackState withoutLivePostRollPlaceholder =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0L)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                MediaItem.fromUri("https://example.com/ad0-0"));

    setAdPlaybackState(withoutLivePostRollPlaceholder);

    assertThrows(
        IllegalStateException.class,
        () ->
            setAdPlaybackState(
                withoutLivePostRollPlaceholder.withLivePostrollPlaceholderAppended(
                    /* isServerSideInserted= */ false)));
  }

  @Test
  public void onAdPlaybackState_postRollRemoved_throwsIllegalStateException() {
    AdPlaybackState withoutLivePostRollPlaceholder =
        new AdPlaybackState("adsId")
            .withNewAdGroup(/* adGroupIndex= */ 0, /* adGroupTimeUs= */ 0L)
            .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
            .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000L)
            .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000L)
            .withAvailableAdMediaItem(
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0,
                MediaItem.fromUri("https://example.com/ad0-0"));
    setAdPlaybackState(
        withoutLivePostRollPlaceholder.withLivePostrollPlaceholderAppended(
            /* isServerSideInserted= */ false));

    assertThrows(
        IllegalStateException.class, () -> setAdPlaybackState(withoutLivePostRollPlaceholder));
  }

  private static class NoOpAdsLoader implements AdsLoader {

    @Override
    public void setPlayer(@Nullable Player player) {}

    @Override
    public void release() {}

    @Override
    public void setSupportedContentTypes(@C.ContentType int... contentTypes) {}

    @Override
    public void start(
        AdsMediaSource adsMediaSource,
        DataSpec adTagDataSpec,
        Object adsId,
        AdViewProvider adViewProvider,
        EventListener eventListener) {}

    @Override
    public void stop(AdsMediaSource adsMediaSource, EventListener eventListener) {}

    @Override
    public void handlePrepareComplete(
        AdsMediaSource adsMediaSource, int adGroupIndex, int adIndexInAdGroup) {}

    @Override
    public void handlePrepareError(
        AdsMediaSource adsMediaSource,
        int adGroupIndex,
        int adIndexInAdGroup,
        IOException exception) {}

    @Override
    public void handleContentTimelineChanged(AdsMediaSource adsMediaSource, Timeline timeline) {}
  }

  private static MediaSource buildMediaSource(MediaItem mediaItem) {
    FakeMediaSource fakeMediaSource = new FakeMediaSource();
    fakeMediaSource.setCanUpdateMediaItems(true);
    fakeMediaSource.updateMediaItem(mediaItem);
    AdsLoader adsLoader = mock(AdsLoader.class);
    doAnswer(
            method -> {
              ((EventListener) method.getArgument(4))
                  .onAdPlaybackState(new AdPlaybackState(TEST_ADS_ID));
              return null;
            })
        .when(adsLoader)
        .start(any(), any(), any(), any(), any());
    return new AdsMediaSource(
        fakeMediaSource,
        TEST_ADS_DATA_SPEC,
        TEST_ADS_ID,
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext()),
        adsLoader,
        /* adViewProvider= */ () -> null,
        /* useLazyContentSourcePreparation= */ true);
  }
}
