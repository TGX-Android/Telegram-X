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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AdViewProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.ads.AdsLoader;
import androidx.media3.exoplayer.source.ads.AdsMediaSource;
import androidx.media3.test.utils.FakeTimeline;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link HlsInterstitialsAdsLoaderTest}. */
@RunWith(AndroidJUnit4.class)
public class HlsInterstitialsAdsLoaderTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AdsLoader.EventListener mockEventListener;
  @Mock private HlsInterstitialsAdsLoader.Listener mockAdsLoaderListener;
  @Mock private AdViewProvider mockAdViewProvider;
  @Mock private Player mockPlayer;

  private HlsInterstitialsAdsLoader adsLoader;
  private MediaItem contentMediaItem;
  private DataSpec adTagDataSpec;
  private AdsMediaSource adsMediaSource;
  private FakeTimeline.TimelineWindowDefinition contentWindowDefinition;
  private FakeTimeline.TimelineWindowDefinition adsMediaSourceWindowDefinition;

  @Before
  public void setUp() {
    adsLoader = new HlsInterstitialsAdsLoader();
    adsLoader.addListener(mockAdsLoaderListener);
    // The HLS URI to play
    contentMediaItem =
        new MediaItem.Builder()
            .setUri("http://example.com/media.m3u8")
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.EMPTY).setAdsId("adsId").build())
            .build();
    adTagDataSpec = new DataSpec(Uri.EMPTY);
    // The ads media source using the ads loader.
    adsMediaSource =
        (AdsMediaSource)
            new HlsInterstitialsAdsLoader.AdsMediaSourceFactory(
                    adsLoader,
                    mockAdViewProvider,
                    (Context) ApplicationProvider.getApplicationContext())
                .createMediaSource(contentMediaItem);
    // The content timeline with empty ad playback state.
    contentWindowDefinition =
        new FakeTimeline.TimelineWindowDefinition(
            /* periodCount= */ 1,
            "windowId",
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 90_000_000L,
            /* defaultPositionUs= */ 0L,
            /* windowOffsetInFirstPeriodUs= */ 0L,
            ImmutableList.of(AdPlaybackState.NONE),
            contentMediaItem);
    // The ads timeline with a minimal ad playback state with the ads ID.
    adsMediaSourceWindowDefinition =
        new FakeTimeline.TimelineWindowDefinition(
            /* periodCount= */ 1,
            "windowId",
            /* isSeekable= */ true,
            /* isDynamic= */ false,
            /* isLive= */ false,
            /* isPlaceholder= */ false,
            /* durationUs= */ 90_000_000L,
            /* defaultPositionUs= */ 0L,
            /* windowOffsetInFirstPeriodUs= */ 0L,
            ImmutableList.of(new AdPlaybackState("adsId")),
            contentMediaItem);
  }

  @Test
  public void setSupportedContentTypes_hlsNotSupported_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> adsLoader.setSupportedContentTypes(C.CONTENT_TYPE_DASH));
  }

  @Test
  public void start_playerNotSet_throwIllegalStateException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            adsLoader.start(
                adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener));
  }

  @Test
  public void start_nonHlsMediaItem_emptyAdPlaybackState() {
    MediaItem mp4MediaItem =
        new MediaItem.Builder()
            .setUri("http:///example.com/media.mp4")
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.EMPTY).setAdsId("adsId").build())
            .build();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    AdsMediaSource adsMediaSource =
        new AdsMediaSource(
            defaultMediaSourceFactory.createMediaSource(mp4MediaItem),
            new DataSpec(Uri.EMPTY),
            "adsId",
            defaultMediaSourceFactory,
            adsLoader,
            mockAdViewProvider);
    when(mockPlayer.getCurrentTimeline())
        .thenReturn(
            new FakeTimeline(
                new FakeTimeline.TimelineWindowDefinition(
                    /* periodCount= */ 1,
                    "windowId",
                    /* isSeekable= */ true,
                    /* isDynamic= */ true,
                    /* isLive= */ false,
                    /* isPlaceholder= */ false,
                    /* durationUs= */ C.TIME_UNSET,
                    /* defaultPositionUs= */ 0L,
                    /* windowOffsetInFirstPeriodUs= */ 0L,
                    ImmutableList.of(AdPlaybackState.NONE),
                    mp4MediaItem)));
    adsLoader.setPlayer(mockPlayer);

    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);

    verify(mockEventListener).onAdPlaybackState(new AdPlaybackState("adsId"));
  }

  @Test
  public void start_liveWindow_emptyAdPlaybackState() throws IOException {
    when(mockPlayer.getCurrentTimeline())
        .thenReturn(
            new FakeTimeline(
                new FakeTimeline.TimelineWindowDefinition(
                    /* periodCount= */ 1,
                    "windowId",
                    /* isSeekable= */ true,
                    /* isDynamic= */ true,
                    /* isLive= */ true,
                    /* isPlaceholder= */ false,
                    /* durationUs= */ C.TIME_UNSET,
                    /* defaultPositionUs= */ 0L,
                    /* windowOffsetInFirstPeriodUs= */ 0L,
                    ImmutableList.of(AdPlaybackState.NONE),
                    contentMediaItem)));
    adsLoader.setPlayer(mockPlayer);

    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);

    verify(mockEventListener).onAdPlaybackState(new AdPlaybackState("adsId"));
  }

  @Test
  public void start_twiceWithIdenticalAdsId_throwIllegalStateException() {
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    verify(mockAdsLoaderListener)
        .onStart(contentMediaItem, adsMediaSource.getAdsId(), mockAdViewProvider);

    assertThrows(
        IllegalStateException.class,
        () ->
            adsLoader.start(
                adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener));

    verifyNoMoreInteractions(mockAdsLoaderListener);
  }

  @Test
  public void start_twiceWithIdenticalUnsupportedAdsId_throwIllegalStateException() {
    MediaItem mp4MediaItem =
        new MediaItem.Builder()
            .setUri("http:///example.com/media.mp4")
            .setAdsConfiguration(
                new MediaItem.AdsConfiguration.Builder(Uri.EMPTY).setAdsId("adsId").build())
            .build();
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    AdsMediaSource adsMediaSource =
        new AdsMediaSource(
            defaultMediaSourceFactory.createMediaSource(mp4MediaItem),
            new DataSpec(Uri.EMPTY),
            "adsId",
            defaultMediaSourceFactory,
            adsLoader,
            mockAdViewProvider);
    when(mockPlayer.getCurrentTimeline())
        .thenReturn(
            new FakeTimeline(
                new FakeTimeline.TimelineWindowDefinition(
                    /* periodCount= */ 1,
                    "windowId",
                    /* isSeekable= */ true,
                    /* isDynamic= */ true,
                    /* isLive= */ false,
                    /* isPlaceholder= */ false,
                    /* durationUs= */ C.TIME_UNSET,
                    /* defaultPositionUs= */ 0L,
                    /* windowOffsetInFirstPeriodUs= */ 0L,
                    ImmutableList.of(AdPlaybackState.NONE),
                    mp4MediaItem)));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);

    assertThrows(
        IllegalStateException.class,
        () ->
            adsLoader.start(
                adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener));

    verifyNoMoreInteractions(mockAdsLoaderListener);
  }

  @Test
  public void handleContentTimelineChanged_preMidAndPostRolls_translatedToAdPlaybackState()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:44.000Z\","
            + "CUE=\"PRE\","
            + "X-ASSET-URI=\"http://example.com/media-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:55.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-1.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad2\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:44.000Z\","
            + "CUE=\"POST\","
            + "X-ASSET-URI=\"http://example.com/media-2.m3u8\"\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", 0L, 15_000_000L, C.TIME_END_OF_SOURCE)
                .withAdDurationsUs(/* adGroupIndex= */ 0, C.TIME_UNSET)
                .withAdDurationsUs(/* adGroupIndex= */ 1, C.TIME_UNSET)
                .withAdDurationsUs(/* adGroupIndex= */ 2, C.TIME_UNSET)
                .withAdCount(/* adGroupIndex= */ 0, 1)
                .withAdCount(/* adGroupIndex= */ 1, 1)
                .withAdCount(/* adGroupIndex= */ 2, 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 0L)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 1, 0L)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 2, 0L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 1,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-1.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 2,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-2.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_3preRolls_mergedIntoSinglePreRollAdGroup()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-2\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:44.000Z\","
            + "CUE=\"PRE\","
            + "X-ASSET-URI=\"http://example.com/media-0-2.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", 0L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET)
                .withAdCount(/* adGroupIndex= */ 0, 3)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 0L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 1,
                    MediaItem.fromUri("http://example.com/media-0-1.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 2,
                    MediaItem.fromUri("http://example.com/media-0-2.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_3midRolls_mergedIntoSingleMidRollAdGroup()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:44.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:44.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-2\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:44.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-2.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 4_000_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET)
                .withAdCount(/* adGroupIndex= */ 0, 3)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 0L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 1,
                    MediaItem.fromUri("http://example.com/media-0-1.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 2,
                    MediaItem.fromUri("http://example.com/media-0-2.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_3postRolls_mergedIntoSinglePostRollAdGroup()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:30.000Z\","
            + "END-DATE=\"2020-01-02T21:55:31.000Z\","
            + "CUE=\"POST\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "CUE=\"POST\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "DURATION=1.1,"
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-2\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:51.000Z\","
            + "CUE=\"POST\","
            + "PLANNED-DURATION=1.2,"
            + "X-ASSET-URI=\"http://example.com/media-0-2.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000_000L, 1_100_000L, 1_200_000L)
                .withAdCount(/* adGroupIndex= */ 0, 3)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 3_300_000L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 1,
                    MediaItem.fromUri("http://example.com/media-0-1.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 2,
                    MediaItem.fromUri("http://example.com/media-0-2.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_midRollAndPostRollNotInOrder_insertedCorrectly()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-2\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.500Z\","
            + "CUE=\"POST\","
            + "DURATION=3,"
            + "X-ASSET-URI=\"http://example.com/media-2-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:42.000Z\","
            + "DURATION=2.0,"
            + "X-ASSET-URI=\"http://example.com/media-1-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.000Z\","
            + "DURATION=1,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState(
                    "adsId", /* adGroupTimesUs...= */ 1_000_000L, 2_000_000L, C.TIME_END_OF_SOURCE)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 1, 2_000_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 2, 3_000_000L)
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 1, /* adCount= */ 1)
                .withAdCount(/* adGroupIndex= */ 2, /* adCount= */ 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000_000L)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 1, 2_000_000L)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 2, 3_000_000L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 1,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-1-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 2,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-2-0.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_resumeOffsetSetToZero_contentResumeOffsetUsIsZero()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "DURATION=1.0,"
            + "CUE=\"PRE\","
            + "X-RESUME-OFFSET=0.0,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "DURATION=1.0,"
            + "CUE=\"PRE\","
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 0L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000_000L, 1_000_000L)
                .withAdCount(/* adGroupIndex= */ 0, 2)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_000_000L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 1,
                    MediaItem.fromUri("http://example.com/media-0-1.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_unknownDuration_handledAsZeroForContentResumeOffsetUs()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "DURATION=1.0,"
            + "CUE=\"PRE\","
            + "X-RESUME-OFFSET=0.0,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "CUE=\"PRE\","
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 0L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 1_000_000L, C.TIME_UNSET)
                .withAdCount(/* adGroupIndex= */ 0, 2)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 0L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 1,
                    MediaItem.fromUri("http://example.com/media-0-1.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_playoutLimitSet_durationSetCorrectly()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "END-DATE=\"2020-01-02T21:55:42.123Z\","
            + "DURATION=2.0,"
            + "PLANNED-DURATION=3.0,"
            + "X-PLAYOUT-LIMIT=4.0,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 1_123_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 4_000_000L)
                .withAdCount(/* adGroupIndex= */ 0, 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 4_000_000L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_withDurationSet_durationSetCorrectly()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "END-DATE=\"2020-01-02T21:55:42.246Z\","
            + "PLANNED-DURATION=2.000,"
            + "DURATION=3.456,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 1_123_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 3_456_000L)
                .withAdCount(/* adGroupIndex= */ 0, 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 3_456_000L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_endDateSet_durationSetCorrectly() throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "END-DATE=\"2020-01-02T21:55:42.246Z\","
            + "PLANNED-DURATION=2.0,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 1_123_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 1_123_000L)
                .withAdCount(/* adGroupIndex= */ 0, 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 1_123_000L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_withPlannedDurationSet_durationSetCorrectly()
      throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "PLANNED-DURATION=2.234,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 1_123_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, 2_234_000L)
                .withAdCount(/* adGroupIndex= */ 0, 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 2_234_000L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8")));
  }

  @Test
  public void handleContentTimelineChanged_noDurationSet_durationTimeUnset() throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";

    assertThat(callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader))
        .isEqualTo(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 1_123_000L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, C.TIME_UNSET)
                .withAdCount(/* adGroupIndex= */ 0, 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, 0L)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8")));
  }

  @Test
  public void onPositionDiscontinuity_marksAdAsPlayed() throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n";
    callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader);
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);
    verify(mockPlayer).addListener(listener.capture());
    Object windowUid = new Object();
    Object periodUid = new Object();

    listener
        .getValue()
        .onPositionDiscontinuity(
            new Player.PositionInfo(
                windowUid,
                /* mediaItemIndex= */ 0,
                contentMediaItem,
                periodUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 10_000L,
                /* contentPositionMs= */ 0L,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 0),
            new Player.PositionInfo(
                windowUid,
                /* mediaItemIndex= */ 0,
                contentMediaItem,
                periodUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 0L,
                /* contentPositionMs= */ 0L,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 1),
            DISCONTINUITY_REASON_AUTO_TRANSITION);
    listener
        .getValue()
        .onPositionDiscontinuity(
            new Player.PositionInfo(
                windowUid,
                /* mediaItemIndex= */ 0,
                contentMediaItem,
                periodUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 10_000L,
                /* contentPositionMs= */ 0L,
                /* adGroupIndex= */ 0,
                /* adIndexInAdGroup= */ 1),
            new Player.PositionInfo(
                windowUid,
                /* mediaItemIndex= */ 0,
                contentMediaItem,
                periodUid,
                /* periodIndex= */ 0,
                /* positionMs= */ 0L,
                /* contentPositionMs= */ 0L,
                /* adGroupIndex= */ -1,
                /* adIndexInAdGroup= */ -1),
            DISCONTINUITY_REASON_AUTO_TRANSITION);

    verify(mockAdsLoaderListener)
        .onAdCompleted(
            contentMediaItem,
            adsMediaSource.getAdsId(),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0);
    verify(mockAdsLoaderListener)
        .onAdCompleted(
            contentMediaItem,
            adsMediaSource.getAdsId(),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 1);
    verify(mockEventListener)
        .onAdPlaybackState(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ 0L)
                .withAdDurationsUs(/* adGroupIndex= */ 0, C.TIME_UNSET, C.TIME_UNSET)
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 2)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, /* contentResumeOffsetUs= */ 0)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 1,
                    MediaItem.fromUri("http://example.com/media-0-1.m3u8"))
                .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0)
                .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 1));
  }

  @Test
  public void onPlaybackStateChanged_stateEndedWhenPlayingAd_marksAdAsPlayed() throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "CUE=\"POST\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";
    callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader);
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);
    when(mockPlayer.getCurrentTimeline())
        .thenReturn(new FakeTimeline(adsMediaSourceWindowDefinition));
    when(mockPlayer.isPlayingAd()).thenReturn(true);
    when(mockPlayer.getCurrentPeriodIndex()).thenReturn(0);
    when(mockPlayer.getCurrentMediaItem()).thenReturn(contentMediaItem);
    when(mockPlayer.getCurrentAdGroupIndex()).thenReturn(0);
    when(mockPlayer.getCurrentAdIndexInAdGroup()).thenReturn(0);
    verify(mockPlayer).addListener(listener.capture());

    listener.getValue().onPlaybackStateChanged(Player.STATE_ENDED);

    verify(mockAdsLoaderListener)
        .onAdCompleted(
            contentMediaItem,
            adsMediaSource.getAdsId(),
            /* adGroupIndex= */ 0,
            /* adIndexInAdGroup= */ 0);
    verify(mockEventListener)
        .onAdPlaybackState(
            new AdPlaybackState("adsId", /* adGroupTimesUs...= */ C.TIME_END_OF_SOURCE)
                .withAdDurationsUs(/* adGroupIndex= */ 0, C.TIME_UNSET)
                .withAdCount(/* adGroupIndex= */ 0, /* adCount= */ 1)
                .withContentResumeOffsetUs(/* adGroupIndex= */ 0, /* contentResumeOffsetUs= */ 0)
                .withAvailableAdMediaItem(
                    /* adGroupIndex= */ 0,
                    /* adIndexInAdGroup= */ 0,
                    MediaItem.fromUri("http://example.com/media-0-0.m3u8"))
                .withPlayedAd(/* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0));
  }

  @Test
  public void handlePrepareError_adPlaybackStateUpdatedAccordingly() throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n";
    callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader);

    adsLoader.handlePrepareError(adsMediaSource, 0, 1, new IOException());
    adsLoader.handlePrepareError(adsMediaSource, 0, 0, new IOException());

    ArgumentCaptor<AdPlaybackState> adPlaybackState =
        ArgumentCaptor.forClass(AdPlaybackState.class);
    verify(mockEventListener, times(3)).onAdPlaybackState(adPlaybackState.capture());
    assertThat(adPlaybackState.getAllValues().get(0).getAdGroup(/* adGroupIndex= */ 0).states)
        .isEqualTo(new int[] {1, 1});
    assertThat(adPlaybackState.getAllValues().get(1).getAdGroup(/* adGroupIndex= */ 0).states)
        .isEqualTo(new int[] {1, 4});
    assertThat(adPlaybackState.getAllValues().get(2).getAdGroup(/* adGroupIndex= */ 0).states)
        .isEqualTo(new int[] {4, 4});
  }

  @Test
  public void onMetadata_listenerCallbackCalled() {
    Metadata metadata = new Metadata(/* presentationTimeUs= */ 0L);
    when(mockPlayer.getCurrentMediaItem()).thenReturn(contentMediaItem);
    when(mockPlayer.isPlayingAd()).thenReturn(true);
    when(mockPlayer.getCurrentAdGroupIndex()).thenReturn(1);
    when(mockPlayer.getCurrentAdIndexInAdGroup()).thenReturn(2);
    when(mockPlayer.getCurrentTimeline())
        .thenReturn(new FakeTimeline(adsMediaSourceWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);
    verify(mockPlayer).addListener(listener.capture());

    listener.getValue().onMetadata(metadata);

    InOrder inOrder = inOrder(mockAdsLoaderListener);
    inOrder
        .verify(mockAdsLoaderListener)
        .onStart(contentMediaItem, adsMediaSource.getAdsId(), mockAdViewProvider);
    inOrder
        .verify(mockAdsLoaderListener)
        .onMetadata(
            contentMediaItem,
            adsMediaSource.getAdsId(),
            /* adGroupIndex= */ 1,
            /* adIndexInAdGroup= */ 2,
            metadata);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void onMetadata_differentMediaItem_listenerCallbackNotCalled() {
    Metadata metadata = new Metadata(/* presentationTimeUs= */ 0L);
    when(mockPlayer.getCurrentMediaItem()).thenReturn(MediaItem.fromUri(Uri.EMPTY));
    when(mockPlayer.isPlayingAd()).thenReturn(true);
    when(mockPlayer.getCurrentAdGroupIndex()).thenReturn(1);
    when(mockPlayer.getCurrentAdIndexInAdGroup()).thenReturn(2);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);
    verify(mockPlayer).addListener(listener.capture());

    listener.getValue().onMetadata(metadata);

    verify(mockAdsLoaderListener)
        .onStart(contentMediaItem, adsMediaSource.getAdsId(), mockAdViewProvider);
    verifyNoMoreInteractions(mockAdsLoaderListener);
  }

  @Test
  public void onMetadata_noAdIsPlaying_listenerCallbackNotCalled() {
    Metadata metadata = new Metadata(/* presentationTimeUs= */ 0L);
    when(mockPlayer.getCurrentMediaItem()).thenReturn(contentMediaItem);
    when(mockPlayer.isPlayingAd()).thenReturn(false);
    when(mockPlayer.getCurrentAdGroupIndex()).thenReturn(-1);
    when(mockPlayer.getCurrentAdIndexInAdGroup()).thenReturn(-1);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);
    verify(mockPlayer).addListener(listener.capture());

    listener.getValue().onMetadata(metadata);

    verify(mockAdsLoaderListener)
        .onStart(contentMediaItem, adsMediaSource.getAdsId(), mockAdViewProvider);
    verifyNoMoreInteractions(mockAdsLoaderListener);
  }

  @Test
  public void stop_playerListenerRemoved() {
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);
    InOrder inOrder = inOrder(mockPlayer);
    inOrder.verify(mockPlayer).addListener(listener.capture());
    inOrder.verify(mockPlayer).getCurrentTimeline();
    inOrder.verifyNoMoreInteractions();
    reset(mockPlayer);

    adsLoader.stop(adsMediaSource, mockEventListener);

    verify(mockPlayer).removeListener(listener.getValue());
    verifyNoMoreInteractions(mockPlayer);
  }

  @Test
  public void release_neverStarted_playerListenerNotAddedNorRemoved() {
    adsLoader.setPlayer(mockPlayer);

    adsLoader.release();

    verifyNoMoreInteractions(mockPlayer);
  }

  @Test
  public void release_afterStartButBeforeStopped_playerListenerRemovedAfterAllSourcesStopped() {
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);
    InOrder inOrder = inOrder(mockPlayer);
    inOrder.verify(mockPlayer).addListener(listener.capture());
    inOrder.verify(mockPlayer).getCurrentTimeline();
    inOrder.verifyNoMoreInteractions();
    reset(mockPlayer);

    adsLoader.release();
    adsLoader.handleContentTimelineChanged(
        adsMediaSource, new FakeTimeline(contentWindowDefinition));
    adsLoader.stop(adsMediaSource, mockEventListener);

    verify(mockPlayer).removeListener(listener.capture());
    verifyNoMoreInteractions(mockPlayer);
  }

  @Test
  public void start_whenReleased_keepsPlaybackOnGoingAndNoListenerCalled() {
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.release();

    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    adsLoader.handleContentTimelineChanged(
        adsMediaSource, new FakeTimeline(contentWindowDefinition));
    adsLoader.stop(adsMediaSource, mockEventListener);

    verifyNoMoreInteractions(mockAdsLoaderListener);
    verify(mockEventListener).onAdPlaybackState(new AdPlaybackState("adsId"));
    verifyNoMoreInteractions(mockEventListener);
  }

  @Test
  public void
      handleContentTimelineChanged_whenReleasedWithStartedSource_keepsPlaybackOnGoingAndNoListenerCalled()
          throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "DURATION=15,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";
    InputStream inputStream = new ByteArrayInputStream(Util.getUtf8Bytes(playlistString));
    HlsMediaPlaylist contentMediaPlaylist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(Uri.EMPTY, inputStream);
    HlsManifest hlsManifest =
        new HlsManifest(/* multivariantPlaylist= */ null, contentMediaPlaylist);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    // Set the player.
    adsLoader.setPlayer(mockPlayer);
    // Start the ad.
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    reset(mockEventListener);
    reset(mockAdsLoaderListener);

    adsLoader.release();
    adsLoader.handleContentTimelineChanged(
        adsMediaSource, new FakeTimeline(new Object[] {hlsManifest}, contentWindowDefinition));
    adsLoader.stop(adsMediaSource, mockEventListener);

    verifyNoMoreInteractions(mockAdsLoaderListener);
    verify(mockEventListener).onAdPlaybackState(new AdPlaybackState("adsId"));
    verifyNoMoreInteractions(mockEventListener);
  }

  @Test
  public void setPlayer_nulledWithStartedSource_doesNotCrashAndListenerCalled() throws IOException {
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:40.000Z\","
            + "DURATION=15,"
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n";
    callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader);
    reset(mockPlayer);
    reset(mockEventListener);
    reset(mockAdsLoaderListener);

    adsLoader.setPlayer(null);
    adsLoader.handleContentTimelineChanged(
        adsMediaSource, new FakeTimeline(contentWindowDefinition));
    adsLoader.handlePrepareError(
        adsMediaSource, /* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, new IOException());
    adsLoader.handlePrepareComplete(
        adsMediaSource, /* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0);
    adsLoader.stop(adsMediaSource, mockEventListener);

    verify(mockPlayer).removeListener(any());
    verifyNoMoreInteractions(mockPlayer);
    verify(mockEventListener).onAdPlaybackState(any());
    verifyNoMoreInteractions(mockEventListener);
    InOrder inOrder = inOrder(mockAdsLoaderListener);
    inOrder.verify(mockAdsLoaderListener).onPrepareError(any(), any(), anyInt(), anyInt(), any());
    inOrder.verify(mockAdsLoaderListener).onPrepareCompleted(any(), any(), anyInt(), anyInt());
    inOrder.verify(mockAdsLoaderListener).onStop(any(), any(), any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void setPlayer_playerAlreadySetWithActiveListeners_throwIllegalArgumentException() {
    Player secondMockPlayer = mock(Player.class);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);

    assertThrows(IllegalStateException.class, () -> adsLoader.setPlayer(secondMockPlayer));
  }

  @Test
  public void setPlayer_playerAlreadySetWithoutActiveListeners_playerSet() {
    Player secondMockPlayer = mock(Player.class);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);

    adsLoader.setPlayer(secondMockPlayer);

    verifyNoMoreInteractions(mockPlayer);
    verifyNoMoreInteractions(secondMockPlayer);
  }

  @Test
  public void setPlayer_setToNullWithActiveListeners_playerListenerRemoved() {
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    reset(mockPlayer);

    adsLoader.setPlayer(null);

    verify(mockPlayer).removeListener(any());
    verifyNoMoreInteractions(mockPlayer);
  }

  @Test
  public void setPlayer_setToNullWithoutActiveListeners_playerSet() {
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);

    adsLoader.setPlayer(/* player= */ null);

    verifyNoMoreInteractions(mockPlayer);
  }

  @Test
  public void addRemoveListener_listenerNotifiedWhenAdded() {
    adsLoader.setPlayer(mockPlayer);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    HlsInterstitialsAdsLoader.Listener mockAdsLoaderListener2 =
        mock(HlsInterstitialsAdsLoader.Listener.class);

    // add a second listener and trigger onStart callback
    adsLoader.addListener(mockAdsLoaderListener2);
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);

    verify(mockAdsLoaderListener2).onStart(any(), any(), any());
    verify(mockAdsLoaderListener).onStart(any(), any(), any());

    // remove the second listener and trigger onStop callback
    adsLoader.removeListener(mockAdsLoaderListener2);
    adsLoader.stop(adsMediaSource, mockEventListener);

    verifyNoMoreInteractions(mockAdsLoaderListener2);
    verify(mockAdsLoaderListener).onStop(any(), any(), any());
    verifyNoMoreInteractions(mockAdsLoaderListener);
  }

  @Test
  public void listener_wholeLifecycle_adsLoaderListenerCallbacksCorrectlyCalled()
      throws IOException {
    Metadata metadata = new Metadata(/* presentationTimeUs= */ 0L);
    when(mockPlayer.isPlayingAd()).thenReturn(true);
    when(mockPlayer.getCurrentMediaItem()).thenReturn(contentMediaItem);
    when(mockPlayer.getCurrentAdGroupIndex()).thenReturn(0);
    when(mockPlayer.getCurrentAdIndexInAdGroup()).thenReturn(1);
    String playlistString =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-02T21:55:40.000Z\n"
            + "#EXTINF:6,\n"
            + "main1.0.ts\n"
            + "#EXT-X-ENDLIST"
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-0\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:41.123Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-0.m3u8\""
            + "\n"
            + "#EXT-X-DATERANGE:"
            + "ID=\"ad0-1\","
            + "CLASS=\"com.apple.hls.interstitial\","
            + "START-DATE=\"2020-01-02T21:55:43.123Z\","
            + "X-ASSET-URI=\"http://example.com/media-0-1.m3u8\""
            + "\n";
    ArgumentCaptor<AdPlaybackState> adPlaybackState =
        ArgumentCaptor.forClass(AdPlaybackState.class);
    IOException exception = new IOException();
    ArgumentCaptor<Player.Listener> listener = ArgumentCaptor.forClass(Player.Listener.class);

    callHandleContentTimelineChangedAndCaptureAdPlaybackState(playlistString, adsLoader);
    adsLoader.handlePrepareError(
        adsMediaSource, /* adGroupIndex= */ 0, /* adIndexInAdGroup= */ 0, exception);
    adsLoader.handlePrepareComplete(
        adsMediaSource, /* adGroupIndex= */ 1, /* adIndexInAdGroup= */ 0);
    verify(mockPlayer).addListener(listener.capture());
    listener.getValue().onMetadata(metadata);
    adsLoader.stop(adsMediaSource, mockEventListener);

    InOrder inOrder = inOrder(mockAdsLoaderListener);
    inOrder
        .verify(mockAdsLoaderListener)
        .onStart(contentMediaItem, adsMediaSource.getAdsId(), mockAdViewProvider);
    inOrder
        .verify(mockAdsLoaderListener)
        .onContentTimelineChanged(eq(contentMediaItem), eq(adsMediaSource.getAdsId()), any());
    inOrder
        .verify(mockAdsLoaderListener)
        .onPrepareError(contentMediaItem, adsMediaSource.getAdsId(), 0, 0, exception);
    inOrder
        .verify(mockAdsLoaderListener)
        .onPrepareCompleted(contentMediaItem, adsMediaSource.getAdsId(), 1, 0);
    inOrder
        .verify(mockAdsLoaderListener)
        .onMetadata(contentMediaItem, adsMediaSource.getAdsId(), 0, 1, metadata);
    inOrder
        .verify(mockAdsLoaderListener)
        .onStop(eq(contentMediaItem), eq(adsMediaSource.getAdsId()), adPlaybackState.capture());
    inOrder.verifyNoMoreInteractions();
    assertThat(adPlaybackState.getValue().getAdGroup(/* adGroupIndex= */ 0).states)
        .isEqualTo(new int[] {4});
    assertThat(adPlaybackState.getValue().getAdGroup(/* adGroupIndex= */ 1).states)
        .isEqualTo(new int[] {1});
  }

  @Test
  public void listener_unsupportedMediaItem_adsLoaderListenerNotCalled() {
    DefaultMediaSourceFactory defaultMediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    AdsMediaSource adsMediaSource =
        new AdsMediaSource(
            defaultMediaSourceFactory.createMediaSource(
                MediaItem.fromUri("https://example.com/media.mp4")),
            new DataSpec(Uri.EMPTY),
            "adsId",
            defaultMediaSourceFactory,
            adsLoader,
            mockAdViewProvider);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    adsLoader.setPlayer(mockPlayer);

    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);
    adsLoader.handleContentTimelineChanged(
        adsMediaSource, new FakeTimeline(contentWindowDefinition));
    adsLoader.stop(adsMediaSource, mockEventListener);

    verifyNoMoreInteractions(mockAdsLoaderListener);
    verify(mockEventListener).onAdPlaybackState(new AdPlaybackState("adsId"));
    verifyNoMoreInteractions(mockEventListener);
  }

  private AdPlaybackState callHandleContentTimelineChangedAndCaptureAdPlaybackState(
      String playlistString, HlsInterstitialsAdsLoader adsLoader) throws IOException {
    InputStream inputStream = new ByteArrayInputStream(Util.getUtf8Bytes(playlistString));
    HlsMediaPlaylist contentMediaPlaylist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(Uri.EMPTY, inputStream);
    when(mockPlayer.getCurrentTimeline()).thenReturn(new FakeTimeline(contentWindowDefinition));
    // Set the player.
    adsLoader.setPlayer(mockPlayer);
    // Start the ad.
    adsLoader.start(adsMediaSource, adTagDataSpec, "adsId", mockAdViewProvider, mockEventListener);

    // Notify ads loader about the media playlist.
    HlsManifest hlsManifest =
        new HlsManifest(/* multivariantPlaylist= */ null, contentMediaPlaylist);
    adsLoader.handleContentTimelineChanged(
        adsMediaSource, new FakeTimeline(new Object[] {hlsManifest}, contentWindowDefinition));

    ArgumentCaptor<AdPlaybackState> adPlaybackState =
        ArgumentCaptor.forClass(AdPlaybackState.class);
    verify(mockEventListener).onAdPlaybackState(adPlaybackState.capture());
    when(mockPlayer.getCurrentTimeline())
        .thenReturn(new FakeTimeline(adsMediaSourceWindowDefinition));
    return adPlaybackState.getValue();
  }
}
