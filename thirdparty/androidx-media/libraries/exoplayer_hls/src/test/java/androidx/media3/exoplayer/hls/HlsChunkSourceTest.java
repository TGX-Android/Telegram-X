/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.test.utils.ExoPlayerTestRunner;
import androidx.media3.test.utils.FakeDataSource;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit tests for {@link HlsChunkSource}. */
@RunWith(AndroidJUnit4.class)
public class HlsChunkSourceTest {

  private static final String PLAYLIST = "media/m3u8/media_playlist";
  private static final String PLAYLIST_INDEPENDENT_SEGMENTS =
      "media/m3u8/media_playlist_independent_segments";
  private static final String PLAYLIST_LIVE_LOW_LATENCY_SEGEMENTS_ONLY =
      "media/m3u8/live_low_latency_segments_only";
  private static final String PLAYLIST_LIVE_LOW_LATENCY_SEGEMENTS_AND_PARTS =
      "media/m3u8/live_low_latency_segments_and_parts";
  private static final String PLAYLIST_EMPTY = "media/m3u8/media_playlist_empty";
  private static final Uri PLAYLIST_URI = Uri.parse("http://example.com/");
  private static final long PLAYLIST_START_PERIOD_OFFSET_US = 8_000_000L;
  private static final Uri IFRAME_URI = Uri.parse("http://example.com/iframe");
  private static final Format IFRAME_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setAverageBitrate(30_000)
          .setWidth(1280)
          .setHeight(720)
          .setRoleFlags(C.ROLE_FLAG_TRICK_PLAY)
          .build();

  @Mock private HlsPlaylistTracker mockPlaylistTracker;

  @Before
  public void setup() throws IOException {
    mockPlaylistTracker = Mockito.mock(HlsPlaylistTracker.class);

    InputStream inputStream =
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(), PLAYLIST_INDEPENDENT_SEGMENTS);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    when(mockPlaylistTracker.isSnapshotValid(eq(PLAYLIST_URI))).thenReturn(true);
    // Mock that segments totalling PLAYLIST_START_PERIOD_OFFSET_US in duration have been removed
    // from the start of the playlist.
    when(mockPlaylistTracker.getInitialStartTimeUs())
        .thenReturn(playlist.startTimeUs - PLAYLIST_START_PERIOD_OFFSET_US);
  }

  @Test
  public void getAdjustedSeekPositionUs_previousSync() {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.PREVIOUS_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(16_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_nextSync() {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.NEXT_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(20_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_nextSyncAtEnd() {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(24_000_000), SeekParameters.NEXT_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(24_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_closestSyncBefore() {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.CLOSEST_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(16_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_closestSyncAfter() {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(19_000_000), SeekParameters.CLOSEST_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(20_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_exact() {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.EXACT);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(17_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUsNoIndependentSegments_tryPreviousSync() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), PLAYLIST);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.PREVIOUS_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(16_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUsNoIndependentSegments_notTryNextSync() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), PLAYLIST);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.NEXT_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(17_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUsNoIndependentSegments_alwaysTryClosestSyncBefore()
      throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), PLAYLIST);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    long adjustedPositionUs1 =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(17_000_000), SeekParameters.CLOSEST_SYNC);
    long adjustedPositionUs2 =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(19_000_000), SeekParameters.CLOSEST_SYNC);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs1)).isEqualTo(16_000_000);
    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs2)).isEqualTo(16_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUsNoIndependentSegments_exact() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), PLAYLIST);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(100_000_000), SeekParameters.EXACT);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(100_000_000);
  }

  @Test
  public void getAdjustedSeekPositionUs_emptyPlaylist() throws IOException {
    HlsChunkSource testChunkSource = createHlsChunkSource(/* cmcdConfiguration= */ null);

    InputStream inputStream =
        TestUtil.getInputStream(ApplicationProvider.getApplicationContext(), PLAYLIST_EMPTY);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);

    long adjustedPositionUs =
        testChunkSource.getAdjustedSeekPositionUs(
            playlistTimeToPeriodTimeUs(100_000_000), SeekParameters.EXACT);

    assertThat(periodTimeToPlaylistTimeUs(adjustedPositionUs)).isEqualTo(100_000_000);
  }

  @Test
  public void getNextChunk_chunkSourceWithDefaultCmcdConfiguration_setsCmcdHttpRequestHeaders() {
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(0).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,d=4000,ot=v,tb=800",
            "CMCD-Request",
            "bl=0,dl=0,nor=\"..%2F3.mp4\",nrr=\"0-\",su",
            "CMCD-Session",
            "cid=\"mediaId\",sf=h,sid=\"" + cmcdConfiguration.sessionId + "\",st=v");

    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(3_000_000).setPlaybackSpeed(1.25f).build(),
        /* loadPositionUs= */ 4_000_000,
        /* queue= */ ImmutableList.of((HlsMediaChunk) output.chunk),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,d=4000,ot=v,tb=800",
            "CMCD-Request",
            "bl=1000,dl=800,nor=\"..%2F3.mp4\",nrr=\"0-\"",
            "CMCD-Session",
            "cid=\"mediaId\",pr=1.25,sf=h,sid=\"" + cmcdConfiguration.sessionId + "\",st=v");

    // Playing mid-chunk, where loadPositionUs is less than playbackPositionUs
    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(5_000_000).setPlaybackSpeed(1.25f).build(),
        /* loadPositionUs= */ 4_000_000,
        /* queue= */ ImmutableList.of((HlsMediaChunk) output.chunk),
        /* allowEndOfStream= */ true,
        output);

    // buffer length is set to 0 when bufferedDurationUs is negative
    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,d=4000,ot=v,tb=800",
            "CMCD-Request",
            "bl=0,dl=0,nor=\"..%2F3.mp4\",nrr=\"0-\"",
            "CMCD-Session",
            "cid=\"mediaId\",pr=1.25,sf=h,sid=\"" + cmcdConfiguration.sessionId + "\",st=v");
  }

  @Test
  public void
      getNextChunk_chunkSourceWithDefaultCmcdConfiguration_setsCorrectBufferStarvationKey() {
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();
    LoadingInfo loadingInfo =
        new LoadingInfo.Builder().setPlaybackPositionUs(0).setPlaybackSpeed(1.0f).build();

    testChunkSource.getNextChunk(
        loadingInfo,
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders).doesNotContainKey("CMCD-Status");

    loadingInfo =
        loadingInfo
            .buildUpon()
            .setPlaybackPositionUs(2_000_000)
            .setLastRebufferRealtimeMs(SystemClock.elapsedRealtime())
            .build();
    ShadowSystemClock.advanceBy(Duration.ofMillis(100));

    testChunkSource.getNextChunk(
        loadingInfo,
        /* loadPositionUs= */ 4_000_000,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders).containsEntry("CMCD-Status", "bs");

    loadingInfo = loadingInfo.buildUpon().setPlaybackPositionUs(6_000_000).build();
    ShadowSystemClock.advanceBy(Duration.ofMillis(100));

    testChunkSource.getNextChunk(
        loadingInfo,
        /* loadPositionUs= */ 8_000_000,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders).doesNotContainKey("CMCD-Status");
  }

  @Test
  public void getNextChunk_forLivePlaylistWithSegmentsOnly_setsCorrectNextObjectRequest()
      throws IOException {
    // The live playlist contains 6 segments, each 4 seconds long. With a playlist start offset of 8
    // seconds, the total media time is 8 + 6*4 = 32 seconds.
    InputStream inputStream =
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(), PLAYLIST_LIVE_LOW_LATENCY_SEGEMENTS_ONLY);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    // A request to fetch the chunk at 27 seconds should retrieve the second-to-last segment.
    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(27_000_000).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 27_000_000,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    // The `nor` key should point to the last segment, which is `FileSequence15.ts`.
    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsEntry("CMCD-Request", "bl=0,dl=0,nor=\"..%2FfileSequence15.ts\",nrr=\"0-\",su");

    // A request to fetch the chunk at 31 seconds should retrieve the last segment.
    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(31_000_000).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 31_000_000,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    // Since there are no next segments left, the `nor` key should be absent.
    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsEntry("CMCD-Request", "bl=0,dl=0,su");
  }

  @Test
  public void getNextChunk_forLivePlaylistWithSegmentsAndParts_setsCorrectNextObjectRequest()
      throws IOException {
    // The live playlist contains 6 segments, each 4 seconds long, and two trailing parts of 1
    // second each. With a playlist start offset of 8 seconds, the total media time is 8 + 6*4 + 2*1
    // = 34 seconds.
    InputStream inputStream =
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(),
            PLAYLIST_LIVE_LOW_LATENCY_SEGEMENTS_AND_PARTS);
    HlsMediaPlaylist playlist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(PLAYLIST_URI, inputStream);
    when(mockPlaylistTracker.getPlaylistSnapshot(eq(PLAYLIST_URI), anyBoolean()))
        .thenReturn(playlist);
    CmcdConfiguration.Factory cmcdConfigurationFactory = CmcdConfiguration.Factory.DEFAULT;
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    // A request to fetch the chunk at 31 seconds should retrieve the last segment.
    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(31_000_000).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 31_000_000,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    // The `nor` key should point to the first trailing part, which is `FileSequence16.0.ts`.
    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsEntry("CMCD-Request", "bl=0,dl=0,nor=\"..%2FfileSequence16.0.ts\",nrr=\"0-\",su");

    // A request to fetch the chunk at 34 seconds should retrieve the first trailing part.
    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(34_000_000).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 34_000_000,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    // The `nor` key should point to the second trailing part, which is `FileSequence16.1.ts`.
    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsEntry("CMCD-Request", "bl=0,dl=0,nor=\"..%2FfileSequence16.1.ts\",nrr=\"0-\",su");
  }

  @Test
  public void getNextChunk_chunkSourceWithCustomCmcdConfiguration_setsCmcdHttpRequestHeaders() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem -> {
          CmcdConfiguration.RequestConfig cmcdRequestConfig =
              new CmcdConfiguration.RequestConfig() {
                @Override
                public boolean isKeyAllowed(String key) {
                  return !key.equals(CmcdConfiguration.KEY_SESSION_ID);
                }

                @Override
                public int getRequestedMaximumThroughputKbps(int throughputKbps) {
                  return 5 * throughputKbps;
                }
              };

          return new CmcdConfiguration(
              /* sessionId= */ "sessionId",
              /* contentId= */ mediaItem.mediaId + "contentIdSuffix",
              cmcdRequestConfig);
        };
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(0).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,d=4000,ot=v,tb=800",
            "CMCD-Request",
            "bl=0,dl=0,nor=\"..%2F3.mp4\",nrr=\"0-\",su",
            "CMCD-Session",
            "cid=\"mediaIdcontentIdSuffix\",sf=h,st=v",
            "CMCD-Status",
            "rtp=4000");
  }

  @Test
  public void
      getNextChunk_chunkSourceWithCustomCmcdConfigurationAndCustomData_setsCmcdHttpRequestHeaders() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem -> {
          CmcdConfiguration.RequestConfig cmcdRequestConfig =
              new CmcdConfiguration.RequestConfig() {
                @Override
                public ImmutableListMultimap<@CmcdConfiguration.HeaderKey String, String>
                    getCustomData() {
                  return new ImmutableListMultimap.Builder<
                          @CmcdConfiguration.HeaderKey String, String>()
                      .put(CmcdConfiguration.KEY_CMCD_OBJECT, "key-1=1")
                      .put(CmcdConfiguration.KEY_CMCD_REQUEST, "key-2=\"stringValue\"")
                      .put(CmcdConfiguration.KEY_CMCD_SESSION, "com.example-key3=3")
                      .put(CmcdConfiguration.KEY_CMCD_STATUS, "com.example.test-key4=5.0")
                      .build();
                }
              };

          return new CmcdConfiguration(
              /* sessionId= */ "sessionId", /* contentId= */ mediaItem.mediaId, cmcdRequestConfig);
        };
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(0).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(output.chunk.dataSpec.httpRequestHeaders)
        .containsExactly(
            "CMCD-Object",
            "br=800,d=4000,key-1=1,ot=v,tb=800",
            "CMCD-Request",
            "bl=0,dl=0,key-2=\"stringValue\",nor=\"..%2F3.mp4\",nrr=\"0-\",su",
            "CMCD-Session",
            "cid=\"mediaId\",com.example-key3=3,sf=h,sid=\""
                + cmcdConfiguration.sessionId
                + "\",st=v",
            "CMCD-Status",
            "com.example.test-key4=5.0");
  }

  @Test
  public void
      getNextChunk_chunkSourceWithCustomCmcdConfigurationAndCustomData_setsCmcdHttpQueryParameters() {
    CmcdConfiguration.Factory cmcdConfigurationFactory =
        mediaItem -> {
          CmcdConfiguration.RequestConfig cmcdRequestConfig =
              new CmcdConfiguration.RequestConfig() {
                @Override
                public ImmutableListMultimap<@CmcdConfiguration.HeaderKey String, String>
                    getCustomData() {
                  return new ImmutableListMultimap.Builder<
                          @CmcdConfiguration.HeaderKey String, String>()
                      .put(CmcdConfiguration.KEY_CMCD_OBJECT, "com.example.test-key-1=1")
                      .put(CmcdConfiguration.KEY_CMCD_REQUEST, "key-2=\"stringValue\"")
                      .build();
                }
              };

          return new CmcdConfiguration(
              /* sessionId= */ "sessionId",
              /* contentId= */ mediaItem.mediaId,
              cmcdRequestConfig,
              CmcdConfiguration.MODE_QUERY_PARAMETER);
        };
    MediaItem mediaItem = new MediaItem.Builder().setMediaId("mediaId").build();
    CmcdConfiguration cmcdConfiguration =
        cmcdConfigurationFactory.createCmcdConfiguration(mediaItem);
    HlsChunkSource testChunkSource = createHlsChunkSource(cmcdConfiguration);
    HlsChunkSource.HlsChunkHolder output = new HlsChunkSource.HlsChunkHolder();

    testChunkSource.getNextChunk(
        new LoadingInfo.Builder().setPlaybackPositionUs(0).setPlaybackSpeed(1.0f).build(),
        /* loadPositionUs= */ 0,
        /* queue= */ ImmutableList.of(),
        /* allowEndOfStream= */ true,
        output);

    assertThat(
            output.chunk.dataSpec.uri.getQueryParameter(CmcdConfiguration.CMCD_QUERY_PARAMETER_KEY))
        .isEqualTo(
            "bl=0,br=800,cid=\"mediaId\",com.example.test-key-1=1,d=4000,dl=0,"
                + "key-2=\"stringValue\",nor=\"..%2F3.mp4\",nrr=\"0-\",ot=v,sf=h,"
                + "sid=\"sessionId\",st=v,su,tb=800");
  }

  private HlsChunkSource createHlsChunkSource(@Nullable CmcdConfiguration cmcdConfiguration) {
    return new HlsChunkSource(
        new DefaultHlsExtractorFactory(),
        mockPlaylistTracker,
        new Uri[] {IFRAME_URI, PLAYLIST_URI},
        new Format[] {IFRAME_FORMAT, ExoPlayerTestRunner.VIDEO_FORMAT},
        new DefaultHlsDataSourceFactory(new FakeDataSource.Factory()),
        /* mediaTransferListener= */ null,
        new TimestampAdjusterProvider(),
        /* timestampAdjusterInitializationTimeoutMs= */ 0,
        /* muxedCaptionFormats= */ null,
        PlayerId.UNSET,
        cmcdConfiguration);
  }

  private static long playlistTimeToPeriodTimeUs(long playlistTimeUs) {
    return playlistTimeUs + PLAYLIST_START_PERIOD_OFFSET_US;
  }

  private static long periodTimeToPlaylistTimeUs(long periodTimeUs) {
    return periodTimeUs - PLAYLIST_START_PERIOD_OFFSET_US;
  }
}
