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
package androidx.media3.exoplayer.hls.playlist;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.MediaSourceEventListener;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.test.utils.robolectric.RobolectricUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultHlsPlaylistTracker}. */
@RunWith(AndroidJUnit4.class)
public class DefaultHlsPlaylistTrackerTest {

  private static final String SAMPLE_M3U8_LIVE_MULTIVARIANT =
      "media/m3u8/live_low_latency_multivariant";
  private static final String SAMPLE_M3U8_LIVE_MULTIVARIANT_MEDIA_URI_WITH_PARAM =
      "media/m3u8/live_low_latency_multivariant_media_uri_with_param";
  private static final String SAMPLE_M3U8_LIVE_MULTIVARIANT_WITH_AUDIO_RENDITIONS =
      "media/m3u8/live_low_latency_multivariant_with_audio_renditions";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL =
      "media/m3u8/live_low_latency_media_can_skip_until";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_FULL_RELOAD_AFTER_ERROR =
      "media/m3u8/live_low_latency_media_can_skip_until_full_reload_after_error";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_DATERANGES =
      "media/m3u8/live_low_latency_media_can_skip_dateranges";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED =
      "media/m3u8/live_low_latency_media_can_skip_skipped";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED_MEDIA_SEQUENCE_NO_OVERLAPPING =
          "media/m3u8/live_low_latency_media_can_skip_skipped_media_sequence_no_overlapping";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP =
      "media/m3u8/live_low_latency_media_can_not_skip";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP_NEXT =
      "media/m3u8/live_low_latency_media_can_not_skip_next";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD =
      "media/m3u8/live_low_latency_media_can_block_reload";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_NEXT =
      "media/m3u8/live_low_latency_media_can_block_reload_next";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY =
      "media/m3u8/live_low_latency_media_can_block_reload_low_latency";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_NEXT =
      "media/m3u8/live_low_latency_media_can_block_reload_low_latency_next";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT =
      "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_AUDIO =
          "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment_audio";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_NEXT =
          "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment_next";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_NEXT2 =
          "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment_next2";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_AUDIO_NEXT =
          "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment_audio_next";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_AUDIO_NEXT2 =
          "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment_audio_next2";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD =
          "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment_preload";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD_NEXT =
          "media/m3u8/live_low_latency_media_can_block_reload_low_latency_full_segment_preload_next";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_AND_BLOCK_RELOAD =
      "media/m3u8/live_low_latency_media_can_skip_until_and_block_reload";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_AND_BLOCK_RELOAD_NEXT =
      "media/m3u8/live_low_latency_media_can_skip_until_and_block_reload_next";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_AND_BLOCK_RELOAD_NEXT_SKIPPED =
      "media/m3u8/live_low_latency_media_can_skip_until_and_block_reload_next_skipped";

  private MockWebServer mockWebServer;
  private int enqueueCounter;
  private int assertedRequestCounter;

  @Before
  public void setUp() {
    mockWebServer = new MockWebServer();
    enqueueCounter = 0;
    assertedRequestCounter = 0;
  }

  @After
  public void tearDown() throws IOException {
    assertThat(assertedRequestCounter).isEqualTo(enqueueCounter);
    mockWebServer.shutdown();
  }

  @Test
  public void start_playlistCanNotSkip_requestsFullUpdate()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {"multivariant.m3u8", "/media0/playlist.m3u8", "/media0/playlist.m3u8"},
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP_NEXT));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    HlsMediaPlaylist firstFullPlaylist = mediaPlaylists.get(0);
    assertThat(firstFullPlaylist.mediaSequence).isEqualTo(10);
    assertThat(firstFullPlaylist.segments.get(0).url).isEqualTo("fileSequence10.ts");
    assertThat(firstFullPlaylist.segments.get(5).url).isEqualTo("fileSequence15.ts");
    assertThat(firstFullPlaylist.segments).hasSize(6);
    HlsMediaPlaylist secondFullPlaylist = mediaPlaylists.get(1);
    assertThat(secondFullPlaylist.mediaSequence).isEqualTo(11);
    assertThat(secondFullPlaylist.segments.get(0).url).isEqualTo("fileSequence11.ts");
    assertThat(secondFullPlaylist.segments.get(5).url).isEqualTo("fileSequence16.ts");
    assertThat(secondFullPlaylist.segments).hasSize(6);
    assertThat(secondFullPlaylist.segments).containsNoneIn(firstFullPlaylist.segments);
  }

  @Test
  public void start_playlistCanSkip_requestsDeltaUpdateAndExpandsSkippedSegments()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8", "/media0/playlist.m3u8", "/media0/playlist.m3u8?_HLS_skip=YES"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    HlsMediaPlaylist initialPlaylistWithAllSegments = mediaPlaylists.get(0);
    assertThat(initialPlaylistWithAllSegments.mediaSequence).isEqualTo(10);
    assertThat(initialPlaylistWithAllSegments.segments).hasSize(6);
    HlsMediaPlaylist mergedPlaylist = mediaPlaylists.get(1);
    assertThat(mergedPlaylist.mediaSequence).isEqualTo(11);
    assertThat(mergedPlaylist.segments).hasSize(6);
    // First 2 segments of the merged playlist need to be copied from the previous playlist.
    assertThat(mergedPlaylist.segments.get(0).url)
        .isEqualTo(initialPlaylistWithAllSegments.segments.get(1).url);
    assertThat(mergedPlaylist.segments.get(0).relativeStartTimeUs).isEqualTo(0);
    assertThat(mergedPlaylist.segments.get(1).url)
        .isEqualTo(initialPlaylistWithAllSegments.segments.get(2).url);
    assertThat(mergedPlaylist.segments.get(1).relativeStartTimeUs).isEqualTo(4000000);
  }

  @Test
  public void start_playlistCanSkip_missingSegments_reloadsWithoutSkipping()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/media0/playlist.m3u8?_HLS_skip=YES",
              "/media0/playlist.m3u8"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED_MEDIA_SEQUENCE_NO_OVERLAPPING),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_FULL_RELOAD_AFTER_ERROR));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    HlsMediaPlaylist initialPlaylistWithAllSegments = mediaPlaylists.get(0);
    assertThat(initialPlaylistWithAllSegments.mediaSequence).isEqualTo(10);
    assertThat(initialPlaylistWithAllSegments.segments).hasSize(6);
    HlsMediaPlaylist mergedPlaylist = mediaPlaylists.get(1);
    assertThat(mergedPlaylist.mediaSequence).isEqualTo(20);
    assertThat(mergedPlaylist.segments).hasSize(6);
  }

  @Test
  public void start_playlistCanSkipDataRanges_requestsDeltaUpdateV2()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8", "/media0/playlist.m3u8", "/media0/playlist.m3u8?_HLS_skip=v2"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_DATERANGES),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    // Finding the media sequence of the second playlist request asserts that the second request has
    // been made with the correct uri parameter appended.
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(11);
  }

  @Test
  public void start_playlistCanSkipAndUriWithParams_preservesOriginalParams()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8?param1=1&param2=2",
              "/media0/playlist.m3u8?param1=1&param2=2&_HLS_skip=YES"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT_MEDIA_URI_WITH_PARAM),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    // Finding the media sequence of the second playlist request asserts that the second request has
    // been made with the original uri parameters preserved and the additional param concatenated
    // correctly.
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(11);
  }

  @Test
  public void start_playlistCanBlockReload_requestBlockingReloadWithCorrectMediaSequence()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8", "/media0/playlist.m3u8", "/media0/playlist.m3u8?_HLS_msn=14"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_NEXT));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    assertThat(mediaPlaylists.get(0).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(11);
  }

  @Test
  public void
      start_playlistCanBlockReloadLowLatency_requestBlockingReloadWithCorrectMediaSequenceAndPart()
          throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/media0/playlist.m3u8?_HLS_msn=14&_HLS_part=1"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_NEXT));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    assertThat(mediaPlaylists.get(0).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(0).segments).hasSize(4);
    assertThat(mediaPlaylists.get(0).trailingParts).hasSize(2);
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(1).segments).hasSize(4);
    assertThat(mediaPlaylists.get(1).trailingParts).hasSize(3);
  }

  @Test
  public void start_playlistCanBlockReloadLowLatencyFullSegment_correctMsnAndPartParams()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/media0/playlist.m3u8?_HLS_msn=14&_HLS_part=0"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_NEXT));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    assertThat(mediaPlaylists.get(0).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(0).segments).hasSize(4);
    assertThat(mediaPlaylists.get(0).trailingParts).isEmpty();
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(1).segments).hasSize(4);
    assertThat(mediaPlaylists.get(1).trailingParts).hasSize(1);
  }

  @Test
  public void start_playlistCanBlockReloadLowLatencyFullSegmentWithPreloadPart_ignoresPreloadPart()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/media0/playlist.m3u8?_HLS_msn=14&_HLS_part=0"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD_NEXT));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 2);

    assertRequestUrlsCalled(httpUrls);
    assertThat(mediaPlaylists.get(0).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(0).segments).hasSize(4);
    assertThat(mediaPlaylists.get(0).trailingParts).hasSize(1);
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(1).segments).hasSize(4);
    assertThat(mediaPlaylists.get(1).trailingParts).hasSize(2);
  }

  @Test
  public void start_lowLatencyScheduleReloadForPlayingButNonPrimaryPlaylist() throws Exception {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/english/audio-playlist.m3u8",
              "/english/audio-playlist.m3u8?_HLS_msn=14&_HLS_part=0",
              "/english/audio-playlist.m3u8?_HLS_msn=14&_HLS_part=1",
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT_WITH_AUDIO_RENDITIONS),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_AUDIO),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_AUDIO_NEXT),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_AUDIO_NEXT2));

    DefaultHlsPlaylistTracker defaultHlsPlaylistTracker =
        new DefaultHlsPlaylistTracker(
            dataType -> new DefaultHttpDataSource.Factory().createDataSource(),
            new DefaultLoadErrorHandlingPolicy(),
            new DefaultHlsPlaylistParserFactory(),
            /* cmcdConfiguration= */ null);
    AtomicInteger playlistChangedCounter = new AtomicInteger();
    AtomicReference<TimeoutException> audioPlaylistRefreshExceptionRef = new AtomicReference<>();
    defaultHlsPlaylistTracker.addListener(
        new HlsPlaylistTracker.PlaylistEventListener() {
          @Override
          public void onPlaylistChanged() {
            playlistChangedCounter.addAndGet(1);
            // Upon the first call of onPlaylistChanged(), we simulate the situation that the first
            // audio rendition is chosen for playback.
            Uri url = defaultHlsPlaylistTracker.getMultivariantPlaylist().audios.get(0).url;
            if (!defaultHlsPlaylistTracker.isSnapshotValid(url)) {
              defaultHlsPlaylistTracker.refreshPlaylist(url);
              try {
                // Make sure that the audio playlist has been refreshed and we've got a playlist
                // snapshot of it.
                RobolectricUtil.runMainLooperUntil(
                    () -> defaultHlsPlaylistTracker.isSnapshotValid(url));
              } catch (TimeoutException e) {
                audioPlaylistRefreshExceptionRef.set(e);
              }
              // Simulate the operations in HlsChunkSource where we keep loading and get a playlist
              // snapshot of the given url when there is a valid snapshot available.
              defaultHlsPlaylistTracker.getPlaylistSnapshot(url, /* isForPlayback= */ true);
              // We have to force the expected audio playlists to load in the first call of
              // onPlaylistChanged(), as once this method returned for "/media0/playlist.m3u8", the
              // DefaultHlsPlaylistTracker will continue reloading the primary playlist, and it's
              // hard to make the order of loading primary and audio playlists deterministic. Thus,
              // we will verify if audio playlists are reloaded as expected first, and ignore the
              // reloading of primary playlists, whose behaviour was already verified in the other
              // tests.
              try {
                RobolectricUtil.runMainLooperUntil(() -> playlistChangedCounter.get() >= 4);
              } catch (TimeoutException e) {
                audioPlaylistRefreshExceptionRef.set(e);
              }
            }
          }

          @Override
          public boolean onPlaylistError(
              Uri url, LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo, boolean forceRetry) {
            return false;
          }
        });

    defaultHlsPlaylistTracker.start(
        Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
        new MediaSourceEventListener.EventDispatcher(),
        mediaPlaylist -> {});
    RobolectricUtil.runMainLooperUntil(() -> playlistChangedCounter.get() >= 4);
    defaultHlsPlaylistTracker.stop();

    assertThat(audioPlaylistRefreshExceptionRef.get()).isNull();
    assertRequestUrlsCalled(httpUrls);
  }

  @Test
  public void start_lowLatencyNotScheduleReloadForNonPlayingPlaylist() throws Exception {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/media1/playlist.m3u8",
              "/media1/playlist.m3u8",
              "/media1/playlist.m3u8?_HLS_msn=14&_HLS_part=0",
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_PRELOAD_NEXT));

    DefaultHlsPlaylistTracker defaultHlsPlaylistTracker =
        new DefaultHlsPlaylistTracker(
            dataType -> new DefaultHttpDataSource.Factory().createDataSource(),
            new DefaultLoadErrorHandlingPolicy(),
            new DefaultHlsPlaylistParserFactory(),
            /* cmcdConfiguration= */ null);
    List<HlsMediaPlaylist> mediaPlaylists = new ArrayList<>();
    AtomicInteger playlistCounter = new AtomicInteger();
    AtomicReference<TimeoutException> primaryPlaylistChangeExceptionRef = new AtomicReference<>();
    defaultHlsPlaylistTracker.addListener(
        new HlsPlaylistTracker.PlaylistEventListener() {
          @Override
          public void onPlaylistChanged() {
            // Upon the first call of onPlaylistChanged(), we simulate the situation that the
            // primary playlist url changes.
            Uri url = defaultHlsPlaylistTracker.getMultivariantPlaylist().mediaPlaylistUrls.get(1);
            if (defaultHlsPlaylistTracker.isSnapshotValid(url)) {
              return;
            }
            defaultHlsPlaylistTracker.refreshPlaylist(url);
            try {
              // Make sure that the playlist for the new url has been refreshed and set it as the
              // current primary playlist, before this method returns.
              RobolectricUtil.runMainLooperUntil(
                  () ->
                      defaultHlsPlaylistTracker.getPlaylistSnapshot(url, /* isForPlayback= */ true)
                          != null);
            } catch (TimeoutException e) {
              primaryPlaylistChangeExceptionRef.set(e);
            }
          }

          @Override
          public boolean onPlaylistError(
              Uri url, LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo, boolean forceRetry) {
            return false;
          }
        });

    defaultHlsPlaylistTracker.start(
        Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
        new MediaSourceEventListener.EventDispatcher(),
        mediaPlaylist -> {
          mediaPlaylists.add(mediaPlaylist);
          playlistCounter.addAndGet(1);
        });
    RobolectricUtil.runMainLooperUntil(() -> playlistCounter.get() >= 2);
    defaultHlsPlaylistTracker.stop();

    assertThat(primaryPlaylistChangeExceptionRef.get()).isNull();
    assertRequestUrlsCalled(httpUrls);
    assertThat(mediaPlaylists.get(0).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(0).segments).hasSize(4);
    assertThat(mediaPlaylists.get(0).trailingParts).hasSize(1);
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(1).segments).hasSize(4);
    assertThat(mediaPlaylists.get(1).trailingParts).hasSize(2);
  }

  @Test
  public void
      start_refreshPlaylistWithAllowingDeliveryDirectives_requestWithCorrectDeliveryDirectives()
          throws Exception {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/media0/playlist.m3u8?_HLS_msn=14&_HLS_part=0",
              "/media0/playlist.m3u8?_HLS_msn=14&_HLS_part=1"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_NEXT),
            getMockResponse(
                SAMPLE_M3U8_LIVE_MEDIA_CAN_BLOCK_RELOAD_LOW_LATENCY_FULL_SEGMENT_NEXT2));

    DefaultHlsPlaylistTracker defaultHlsPlaylistTracker =
        new DefaultHlsPlaylistTracker(
            dataType -> new DefaultHttpDataSource.Factory().createDataSource(),
            new DefaultLoadErrorHandlingPolicy(),
            new DefaultHlsPlaylistParserFactory(),
            /* cmcdConfiguration= */ null);
    List<HlsMediaPlaylist> mediaPlaylists = new ArrayList<>();
    AtomicInteger playlistCounter = new AtomicInteger();
    AtomicReference<TimeoutException> playlistRefreshExceptionRef = new AtomicReference<>();
    defaultHlsPlaylistTracker.addListener(
        new HlsPlaylistTracker.PlaylistEventListener() {
          @Override
          public void onPlaylistChanged() {
            // Upon the first call of onPlaylistChanged(), we call refreshPlaylist(Uri) on the
            // same url.
            defaultHlsPlaylistTracker.refreshPlaylist(
                defaultHlsPlaylistTracker.getMultivariantPlaylist().mediaPlaylistUrls.get(0));
            try {
              // Make sure that playlist reload triggered by refreshPlaylist(Uri) call comes before
              // the one triggered by the regular scheduling, to ensure the playlists to be
              // verified are in the expected order.
              RobolectricUtil.runMainLooperUntil(() -> playlistCounter.get() >= 2);
            } catch (TimeoutException e) {
              playlistRefreshExceptionRef.set(e);
            }
          }

          @Override
          public boolean onPlaylistError(
              Uri url, LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo, boolean forceRetry) {
            return false;
          }
        });

    defaultHlsPlaylistTracker.start(
        Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
        new MediaSourceEventListener.EventDispatcher(),
        mediaPlaylist -> {
          mediaPlaylists.add(mediaPlaylist);
          playlistCounter.addAndGet(1);
        });
    RobolectricUtil.runMainLooperUntil(() -> playlistCounter.get() >= 3);
    defaultHlsPlaylistTracker.stop();

    assertThat(playlistRefreshExceptionRef.get()).isNull();
    assertRequestUrlsCalled(httpUrls);
    assertThat(mediaPlaylists.get(0).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(0).segments).hasSize(4);
    assertThat(mediaPlaylists.get(0).trailingParts).isEmpty();
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(1).segments).hasSize(4);
    assertThat(mediaPlaylists.get(1).trailingParts).hasSize(1);
    assertThat(mediaPlaylists.get(2).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(2).segments).hasSize(4);
    assertThat(mediaPlaylists.get(2).trailingParts).hasSize(2);
  }

  @Test
  public void start_httpBadRequest_forcesFullNonBlockingPlaylistRequest()
      throws IOException, TimeoutException, InterruptedException {
    List<HttpUrl> httpUrls =
        enqueueWebServerResponses(
            new String[] {
              "/multivariant.m3u8",
              "/media0/playlist.m3u8",
              "/media0/playlist.m3u8?_HLS_msn=16&_HLS_skip=YES",
              "/media0/playlist.m3u8",
              "/media0/playlist.m3u8?_HLS_msn=17&_HLS_skip=YES"
            },
            getMockResponse(SAMPLE_M3U8_LIVE_MULTIVARIANT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_AND_BLOCK_RELOAD),
            new MockResponse().setResponseCode(400),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_AND_BLOCK_RELOAD_NEXT),
            getMockResponse(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL_AND_BLOCK_RELOAD_NEXT_SKIPPED));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            /* dataSourceFactory= */ new DefaultHttpDataSource.Factory(),
            Uri.parse(mockWebServer.url("/multivariant.m3u8").toString()),
            /* awaitedMediaPlaylistCount= */ 3);

    assertRequestUrlsCalled(httpUrls);
    assertThat(mediaPlaylists.get(0).mediaSequence).isEqualTo(10);
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(11);
    assertThat(mediaPlaylists.get(2).mediaSequence).isEqualTo(12);
  }

  private List<HttpUrl> enqueueWebServerResponses(String[] paths, MockResponse... mockResponses) {
    assertThat(paths).hasLength(mockResponses.length);
    for (MockResponse mockResponse : mockResponses) {
      enqueueCounter++;
      mockWebServer.enqueue(mockResponse);
    }
    List<HttpUrl> urls = new ArrayList<>();
    for (String path : paths) {
      urls.add(mockWebServer.url(path));
    }
    return urls;
  }

  private void assertRequestUrlsCalled(List<HttpUrl> httpUrls) throws InterruptedException {
    for (HttpUrl url : httpUrls) {
      assertedRequestCounter++;
      assertThat(url.toString()).endsWith(mockWebServer.takeRequest().getPath());
    }
  }

  private static List<HlsMediaPlaylist> runPlaylistTrackerAndCollectMediaPlaylists(
      DataSource.Factory dataSourceFactory,
      Uri multivariantPlaylistUri,
      int awaitedMediaPlaylistCount)
      throws TimeoutException {

    DefaultHlsPlaylistTracker defaultHlsPlaylistTracker =
        new DefaultHlsPlaylistTracker(
            dataType -> dataSourceFactory.createDataSource(),
            new DefaultLoadErrorHandlingPolicy(),
            new DefaultHlsPlaylistParserFactory(),
            /* cmcdConfiguration= */ null);

    List<HlsMediaPlaylist> mediaPlaylists = new ArrayList<>();
    AtomicInteger playlistCounter = new AtomicInteger();
    defaultHlsPlaylistTracker.start(
        multivariantPlaylistUri,
        new MediaSourceEventListener.EventDispatcher(),
        mediaPlaylist -> {
          mediaPlaylists.add(mediaPlaylist);
          playlistCounter.addAndGet(1);
        });

    RobolectricUtil.runMainLooperUntil(() -> playlistCounter.get() >= awaitedMediaPlaylistCount);

    defaultHlsPlaylistTracker.stop();
    return mediaPlaylists;
  }

  private static MockResponse getMockResponse(String assetFile) throws IOException {
    return new MockResponse().setResponseCode(200).setBody(new Buffer().write(getBytes(assetFile)));
  }

  private static byte[] getBytes(String filename) throws IOException {
    return TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), filename);
  }
}
