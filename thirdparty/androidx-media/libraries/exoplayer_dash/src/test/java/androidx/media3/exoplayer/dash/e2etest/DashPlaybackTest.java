/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.exoplayer.dash.e2etest;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.ParserException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.metadata.MetadataDecoderFactory;
import androidx.media3.exoplayer.metadata.MetadataRenderer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.chunk.BundledChunkExtractor;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.CmcdConfiguration;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.ThrowingSubtitleParserFactory;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/** End-to-end tests using DASH samples. */
@RunWith(AndroidJUnit4.class)
public final class DashPlaybackTest {

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void webvttStandaloneFile() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/standalone-webvtt/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/standalone-webvtt.dump");
  }

  @Test
  public void webvttStandaloneFile_loadError_playbackContinuesErrorReported() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ResolvingDataSource.Factory webvttNotFoundDataSourceFactory =
        new ResolvingDataSource.Factory(
            new DefaultDataSource.Factory(applicationContext),
            dataSpec ->
                dataSpec.uri.getPath().endsWith(".vtt")
                    ? dataSpec.buildUpon().setUri("asset:///file/not/found").build()
                    : dataSpec);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(new DefaultMediaSourceFactory(webvttNotFoundDataSourceFactory))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    AnalyticsListenerImpl analyticsListener = new AnalyticsListenerImpl();
    player.addAnalyticsListener(analyticsListener);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/standalone-webvtt/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).ignoringNonFatalErrors().untilFullyBuffered();
    player.play();
    advance(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    assertThat(analyticsListener.loadErrorEventInfo.uri)
        .isEqualTo(Uri.parse("asset:///file/not/found"));
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/standalone-webvtt_load-error.dump");
  }

  @Test
  public void webvttStandaloneFile_parseError_playbackContinuesErrorReported() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(
                new DefaultMediaSourceFactory(applicationContext)
                    .setSubtitleParserFactory(
                        new ThrowingSubtitleParserFactory(
                            () -> new IllegalStateException("test subtitle parsing error"))))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    AnalyticsListenerImpl analyticsListener = new AnalyticsListenerImpl();
    player.addAnalyticsListener(analyticsListener);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/standalone-webvtt/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).ignoringNonFatalErrors().untilFullyBuffered();
    player.play();
    advance(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    assertThat(analyticsListener.loadError).isInstanceOf(ParserException.class);
    assertThat(analyticsListener.loadError)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("test subtitle parsing error");
    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/dash/standalone-webvtt_parse-error.dump");
  }

  @Test
  public void ttmlStandaloneXmlFile() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/standalone-ttml/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/standalone-ttml.dump");
  }

  // https://github.com/google/ExoPlayer/issues/7985
  @Test
  public void webvttInMp4() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/webvtt-in-mp4/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/webvtt-in-mp4.dump");
  }

  @Test
  public void ttmlInMp4() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/ttml-in-mp4/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/ttml-in-mp4.dump");
  }

  @Test
  public void ttmlInMp4_loadError_playbackContinuesErrorReported() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ResolvingDataSource.Factory ttmlNotFoundDataSourceFactory =
        new ResolvingDataSource.Factory(
            new DefaultDataSource.Factory(applicationContext),
            dataSpec ->
                dataSpec.uri.getPath().endsWith(".text.mp4")
                    ? dataSpec.buildUpon().setUri("asset:///file/not/found").build()
                    : dataSpec);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(new DefaultMediaSourceFactory(ttmlNotFoundDataSourceFactory))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    AnalyticsListenerImpl analyticsListener = new AnalyticsListenerImpl();
    player.addAnalyticsListener(analyticsListener);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/ttml-in-mp4/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).ignoringNonFatalErrors().untilFullyBuffered();
    player.play();
    advance(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    assertThat(analyticsListener.loadErrorEventInfo.uri)
        .isEqualTo(Uri.parse("asset:///file/not/found"));
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/ttml-in-mp4_load-error.dump");
  }

  @Test
  public void ttmlInMp4_parseError_playbackContinuesErrorReported() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(
                new DefaultMediaSourceFactory(applicationContext)
                    .setSubtitleParserFactory(
                        new ThrowingSubtitleParserFactory(
                            () -> new IllegalStateException("test subtitle parsing error"))))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    AnalyticsListenerImpl analyticsListener = new AnalyticsListenerImpl();
    player.addAnalyticsListener(analyticsListener);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/ttml-in-mp4/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).ignoringNonFatalErrors().untilFullyBuffered();
    player.play();
    advance(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    assertThat(analyticsListener.loadError)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("test subtitle parsing error");
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/ttml-in-mp4_parse-error.dump");
  }

  /**
   * This test and {@link #cea608_parseDuringExtraction()} use the same output dump file, to
   * demonstrate the flag has no effect on the resulting subtitles.
   */
  // Using deprecated MediaSource.Factory.experimentalParseSubtitlesDuringExtraction() method to
  // ensure legacy subtitle handling keeps working.
  @SuppressWarnings("deprecation")
  @Test
  public void cea608_parseDuringRendering() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(
                new DashMediaSource.Factory(new DefaultDataSource.Factory(applicationContext))
                    .experimentalParseSubtitlesDuringExtraction(false))
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/cea608/manifest.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/cea608.dump");
  }

  /**
   * This test and {@link #cea608_parseDuringRendering()} use the same output dump file, to
   * demonstrate the flag has no effect on the resulting subtitles.
   */
  // Explicitly enable parsing during extraction (even though a) it's the default and b) currently
  // all CEA-608 parsing happens during rendering) to make this test clearer & more future-proof.
  @SuppressWarnings("deprecation")
  @Test
  public void cea608_parseDuringExtraction() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(
                new DashMediaSource.Factory(new DefaultDataSource.Factory(applicationContext))
                    .experimentalParseSubtitlesDuringExtraction(true))
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    // Ensure the subtitle track is selected.
    DefaultTrackSelector trackSelector =
        checkNotNull((DefaultTrackSelector) player.getTrackSelector());
    trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredTextLanguage("en"));
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/cea608/manifest.mpd"));
    player.prepare();
    // Ensure media is fully buffered so that the first subtitle is ready at the start of playback.
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/cea608.dump");
  }

  // https://github.com/google/ExoPlayer/issues/8710
  @Test
  public void emsgNearToPeriodBoundary() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/emsg/sample.mpd"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/emsg.dump");
  }

  @Test
  public void renderMetadata_withTimelyOutput() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    RenderersFactory renderersFactory =
        (eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput) ->
            new Renderer[] {new MetadataRenderer(metadataRendererOutput, eventHandler.getLooper())};
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/emsg/sample.mpd"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.playUntilPosition(player, /* mediaItemIndex= */ 0, /* positionMs= */ 500);
    player.release();
    surface.release();

    // Ensure output contains metadata up to the playback position.
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/metadata_from_timely_output.dump");
  }

  @Test
  public void renderMetadata_withEarlyOutput() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    RenderersFactory renderersFactory =
        (eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput) ->
            new Renderer[] {
              new MetadataRenderer(
                  metadataRendererOutput,
                  eventHandler.getLooper(),
                  MetadataDecoderFactory.DEFAULT,
                  /* outputMetadataEarly= */ true)
            };
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/emsg/sample.mpd"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.playUntilPosition(player, /* mediaItemIndex= */ 0, /* positionMs= */ 500);
    player.release();
    surface.release();

    // Ensure output contains all metadata irrespective of the playback position.
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/metadata_from_early_output.dump");
  }

  /**
   * This test might be flaky. The {@link ExoPlayer} instantiated in this test uses a {@link
   * FakeClock} that runs much faster than real time. This might cause the {@link ExoPlayer} to skip
   * and not present some images. That will cause the test to fail.
   */
  @Test
  public void playThumbnailGrid() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/thumbnails/sample.mpd"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/loadimage.dump");
  }

  @Test
  public void playThumbnailGrid_withSeekAfterEoS() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/thumbnails/sample.mpd"));
    player.seekTo(55_000L);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    player.seekTo(55_000L);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/image_with_seek_after_eos.dump");
  }

  @Test
  public void playVideo_usingWithinGopSampleDependencies_withSeek() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    BundledChunkExtractor.Factory chunkExtractorFactory =
        new BundledChunkExtractor.Factory()
            .experimentalSetCodecsToParseWithinGopSampleDependencies(C.VIDEO_CODEC_FLAG_H264);
    DataSource.Factory defaultDataSourceFactory = new DefaultDataSource.Factory(applicationContext);
    DashMediaSource.Factory dashMediaSourceFactory =
        new DashMediaSource.Factory(
            /* chunkSourceFactory= */ new DefaultDashChunkSource.Factory(
                chunkExtractorFactory, defaultDataSourceFactory, /* maxSegmentsPerLoad= */ 1),
            /* manifestDataSourceFactory= */ defaultDataSourceFactory);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(dashMediaSourceFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/standalone-webvtt/sample.mpd"));
    player.seekTo(500L);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/optimized_seek.dump");
  }

  @Test
  public void playVideo_usingWithinGopSampleDependencies_withSeekAfterEoS() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    BundledChunkExtractor.Factory chunkExtractorFactory =
        new BundledChunkExtractor.Factory()
            .experimentalSetCodecsToParseWithinGopSampleDependencies(C.VIDEO_CODEC_FLAG_H264);
    DataSource.Factory defaultDataSourceFactory = new DefaultDataSource.Factory(applicationContext);
    DashMediaSource.Factory dashMediaSourceFactory =
        new DashMediaSource.Factory(
            /* chunkSourceFactory= */ new DefaultDashChunkSource.Factory(
                chunkExtractorFactory, defaultDataSourceFactory, /* maxSegmentsPerLoad= */ 1),
            /* manifestDataSourceFactory= */ defaultDataSourceFactory);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(dashMediaSourceFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/standalone-webvtt/sample.mpd"));
    player.seekTo(50_000L);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DecoderCounters decoderCounters = checkNotNull(player.getVideoDecoderCounters());
    assertThat(decoderCounters.skippedInputBufferCount).isEqualTo(13);
    assertThat(decoderCounters.queuedInputBufferCount).isEqualTo(17);
    // TODO: b/352276461 - The last frame might not be rendered. When the bug is fixed,
    //  assert on the full playback dump.
  }

  @Test
  public void playVideo_usingWithinGopSampleDependenciesOnH265_withSeek() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    BundledChunkExtractor.Factory chunkExtractorFactory =
        new BundledChunkExtractor.Factory()
            .experimentalSetCodecsToParseWithinGopSampleDependencies(C.VIDEO_CODEC_FLAG_H265);
    DataSource.Factory defaultDataSourceFactory = new DefaultDataSource.Factory(applicationContext);
    DashMediaSource.Factory dashMediaSourceFactory =
        new DashMediaSource.Factory(
            /* chunkSourceFactory= */ new DefaultDashChunkSource.Factory(
                chunkExtractorFactory, defaultDataSourceFactory, /* maxSegmentsPerLoad= */ 1),
            /* manifestDataSourceFactory= */ defaultDataSourceFactory);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(dashMediaSourceFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setTrackSelectionParameters(
        player.getTrackSelectionParameters().buildUpon().setPreferredTextLanguage("en").build());
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/captions_h265/manifest.mpd"));
    player.seekTo(500L);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/optimized_seek_h265.dump");
  }

  @Test
  public void multiPeriod_withOffsetInSegment() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(
        MediaItem.fromUri("asset:///media/dash/multi-period-with-offset/sample.mpd"));
    player.prepare();
    // Ensure media is fully buffered to avoid flakiness from loading second period too late.
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/dash/multi-period-with-offset.dump");
  }

  @Test
  public void cmcdEnabled_withInitSegment() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(
                new DefaultMediaSourceFactory(applicationContext)
                    .setCmcdConfigurationFactory(CmcdConfiguration.Factory.DEFAULT))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/dash/multi-track/sample.mpd"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/dash/cmcd-enabled-with-init-segment.dump");
  }

  @Test
  public void loadEventsReportedAsExpected() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    AnalyticsListenerImpl analyticsListener = new AnalyticsListenerImpl();
    player.addAnalyticsListener(analyticsListener);
    AnalyticsListener mockAnalyticsListener = mock(AnalyticsListener.class);
    player.addAnalyticsListener(mockAnalyticsListener);
    Uri manifestUri = Uri.parse("asset:///media/dash/emsg/sample.mpd");

    player.setMediaItem(MediaItem.fromUri(manifestUri));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    ArgumentCaptor<LoadEventInfo> loadStartedEventInfoCaptor =
        ArgumentCaptor.forClass(LoadEventInfo.class);
    verify(mockAnalyticsListener, atLeastOnce())
        .onLoadStarted(any(), loadStartedEventInfoCaptor.capture(), any(), anyInt());
    List<Uri> loadStartedUris =
        Lists.transform(loadStartedEventInfoCaptor.getAllValues(), i -> i.uri);
    List<Uri> loadStartedDataSpecUris =
        Lists.transform(loadStartedEventInfoCaptor.getAllValues(), i -> i.dataSpec.uri);
    // Remove duplicates in case the load was split into multiple reads.
    assertThat(ImmutableSet.copyOf(loadStartedUris))
        .containsExactly(manifestUri, Uri.parse("asset:///media/dash/emsg/sample.audio.mp4"));
    // The two sources of URI should match (because there's no redirection).
    assertThat(loadStartedDataSpecUris).containsExactlyElementsIn(loadStartedUris).inOrder();
    ArgumentCaptor<LoadEventInfo> loadCompletedEventInfoCaptor =
        ArgumentCaptor.forClass(LoadEventInfo.class);
    verify(mockAnalyticsListener, atLeastOnce())
        .onLoadCompleted(any(), loadCompletedEventInfoCaptor.capture(), any());
    List<Uri> loadCompletedUris =
        Lists.transform(loadCompletedEventInfoCaptor.getAllValues(), i -> i.uri);
    List<Uri> loadCompletedDataSpecUris =
        Lists.transform(loadCompletedEventInfoCaptor.getAllValues(), i -> i.dataSpec.uri);
    // Every started load should be completed.
    assertThat(loadCompletedUris).containsExactlyElementsIn(loadStartedUris);
    assertThat(loadCompletedDataSpecUris).containsExactlyElementsIn(loadStartedUris);
  }

  private static final class AnalyticsListenerImpl implements AnalyticsListener {

    @Nullable private LoadEventInfo loadErrorEventInfo;
    @Nullable private IOException loadError;

    @Override
    public void onLoadError(
        EventTime eventTime,
        LoadEventInfo loadEventInfo,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      this.loadErrorEventInfo = loadEventInfo;
      this.loadError = error;
    }
  }
}
