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
package androidx.media3.exoplayer.e2etest;

import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance;
import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.play;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.RobolectricUtil;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

/** End-to-end tests using side-loaded WebVTT subtitles. */
@Config(sdk = 30) // TODO: b/382017156 - Remove this when the tests pass on API 31+.
@RunWith(ParameterizedRobolectricTestRunner.class)
public class WebvttPlaybackTest {
  @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
  public static ImmutableList<String> mediaSamples() {
    return ImmutableList.of("typical");
  }

  @ParameterizedRobolectricTestRunner.Parameter public String inputFile;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void test() throws Exception {
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
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/preroll-5s.mp4")
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new MediaItem.SubtitleConfiguration.Builder(
                            Uri.parse("asset:///media/webvtt/" + inputFile))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()))
            .build();

    player.setMediaItem(mediaItem);
    player.prepare();
    advance(player).untilState(Player.STATE_READY);
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/webvtt/" + inputFile + ".dump");
  }

  @Test
  public void test_withSeek() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setLoadControl(
                new DefaultLoadControl.Builder()
                    .setBackBuffer(
                        /* backBufferDurationMs= */ 10000, /* retainBackBufferFromKeyframe= */ true)
                    .build())
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/preroll-5s.mp4")
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new MediaItem.SubtitleConfiguration.Builder(
                            Uri.parse("asset:///media/webvtt/" + inputFile))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()))
            .build();
    // Play media fully (with back buffer) to ensure we have all the segment data available.
    player.setMediaItem(mediaItem);
    player.prepare();
    advance(player).untilState(Player.STATE_READY);
    advance(player).untilFullyBuffered();
    player.play();
    advance(player).untilState(Player.STATE_ENDED);

    // Seek back to within first subtitle.
    player.seekTo(1000);
    player.play();
    advance(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/webvtt/" + inputFile + ".seek.dump");
  }

  // Using deprecated TextRenderer.experimentalSetLegacyDecodingEnabled() and
  // MediaSource.Factory.experimentalParseSubtitlesDuringExtraction() methods to ensure legacy
  // subtitle handling keeps working.
  @SuppressWarnings("deprecation")
  @Test
  public void test_legacyParseInRenderer() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext)
            .setTextRendererFactory(
                (textOutput, outputLooper) -> {
                  TextRenderer renderer = new TextRenderer(textOutput, outputLooper);
                  renderer.experimentalSetLegacyDecodingEnabled(true);
                  return renderer;
                });
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(applicationContext)
            .experimentalParseSubtitlesDuringExtraction(false);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(mediaSourceFactory)
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/preroll-5s.mp4")
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new MediaItem.SubtitleConfiguration.Builder(
                            Uri.parse("asset:///media/webvtt/" + inputFile))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()))
            .build();

    // Carefully play and stall until all expected Cues arrived. This is needed because the legacy
    // mode decodes subtitles in a background thread not controlled by our clock and the player also
    // doesn't wait for subtitles to be decoded before making progress.
    player.setMediaItem(mediaItem);
    AtomicBoolean firstCueArrived = createCuesCondition(player, 0, /* cuesEmpty= */ false);
    player.prepare();
    player.play();
    stallPlayerUntilCondition(player, firstCueArrived);
    playUntilCuesArrived(player, 1234000, /* cuesEmpty= */ true);
    playUntilCuesArrived(player, 2345000, /* cuesEmpty= */ false);
    playUntilCuesArrived(player, 3456000, /* cuesEmpty= */ true);
    play(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/webvtt/" + inputFile + ".dump");
  }

  // Using deprecated TextRenderer.experimentalSetLegacyDecodingEnabled() and
  // MediaSource.Factory.experimentalParseSubtitlesDuringExtraction() methods to ensure legacy
  // subtitle handling keeps working.
  @SuppressWarnings("deprecation")
  @Test
  public void test_legacyParseInRendererWithSeek() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext)
            .setTextRendererFactory(
                (textOutput, outputLooper) -> {
                  TextRenderer renderer = new TextRenderer(textOutput, outputLooper);
                  renderer.experimentalSetLegacyDecodingEnabled(true);
                  return renderer;
                });
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(applicationContext)
            .experimentalParseSubtitlesDuringExtraction(false);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(
                new DefaultLoadControl.Builder()
                    .setBackBuffer(
                        /* backBufferDurationMs= */ 10000, /* retainBackBufferFromKeyframe= */ true)
                    .build())
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/preroll-5s.mp4")
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new MediaItem.SubtitleConfiguration.Builder(
                            Uri.parse("asset:///media/webvtt/" + inputFile))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()))
            .build();
    // Play media fully (with back buffer) to ensure we have all the segment data available.
    // Carefully play and stall until all expected Cues arrived. This is needed because the legacy
    // mode decodes subtitles in a background thread not controlled by our clock and the player also
    // doesn't wait for subtitles to be decoded before making progress.
    player.setMediaItem(mediaItem);
    AtomicBoolean firstCueArrived = createCuesCondition(player, 0, /* cuesEmpty= */ false);
    player.prepare();
    player.play();
    stallPlayerUntilCondition(player, firstCueArrived);
    playUntilCuesArrived(player, 1234000, /* cuesEmpty= */ true);
    playUntilCuesArrived(player, 2345000, /* cuesEmpty= */ false);
    playUntilCuesArrived(player, 3456000, /* cuesEmpty= */ true);
    play(player).untilState(Player.STATE_ENDED);

    // Seek back to within first subtitle.
    player.pause();
    AtomicBoolean newFirstCueArrived = createCuesCondition(player, 0, /* cuesEmpty= */ false);
    player.seekTo(1000);
    stallPlayerUntilCondition(player, newFirstCueArrived);
    playUntilCuesArrived(player, 1234000, /* cuesEmpty= */ true);
    playUntilCuesArrived(player, 2345000, /* cuesEmpty= */ false);
    playUntilCuesArrived(player, 3456000, /* cuesEmpty= */ true);
    play(player).untilState(Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/webvtt/" + inputFile + ".seek.dump");
  }

  // Deliberately configuring legacy subtitle handling to check unconfigured TextRenderer fails.
  @SuppressWarnings("deprecation")
  @Test
  public void textRenderer_doesntSupportLegacyDecodingByDefault_playbackFails() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    RenderersFactory renderersFactory = new DefaultRenderersFactory(applicationContext);
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(applicationContext)
            .experimentalParseSubtitlesDuringExtraction(false);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(mediaSourceFactory)
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/preroll-5s.mp4")
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new MediaItem.SubtitleConfiguration.Builder(
                            Uri.parse("asset:///media/webvtt/" + inputFile))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()))
            .build();

    player.setMediaItem(mediaItem);
    player.prepare();
    player.play();
    ExoPlaybackException playbackException = TestPlayerRunHelper.runUntilError(player);
    assertThat(playbackException)
        .hasCauseThat()
        .hasMessageThat()
        .contains("Legacy decoding is disabled");
    player.release();
    surface.release();
  }

  private static void playUntilCuesArrived(ExoPlayer player, long cuesTimeUs, boolean cuesEmpty)
      throws Exception {
    AtomicBoolean cuesFound = createCuesCondition(player, cuesTimeUs, cuesEmpty);
    play(player)
        .untilBackgroundThreadCondition(
            () -> player.getCurrentPosition() >= Util.usToMs(cuesTimeUs));
    player.pause();
    stallPlayerUntilCondition(player, cuesFound);
  }

  private static AtomicBoolean createCuesCondition(
      ExoPlayer player, long cuesTimeUs, boolean cuesEmpty) {
    AtomicBoolean cuesFound = new AtomicBoolean();
    player.addListener(
        new Player.Listener() {
          @Override
          public void onCues(CueGroup cueGroup) {
            if (cueGroup.presentationTimeUs == cuesTimeUs && cuesEmpty == cueGroup.cues.isEmpty()) {
              cuesFound.set(true);
            }
          }
        });
    return cuesFound;
  }

  private static void stallPlayerUntilCondition(ExoPlayer player, AtomicBoolean condition)
      throws Exception {
    long timeoutTimeMs = Clock.DEFAULT.currentTimeMillis() + RobolectricUtil.DEFAULT_TIMEOUT_MS;
    while (!condition.get()
        && (player.getPlaybackState() == Player.STATE_READY
            || player.getPlaybackState() == Player.STATE_BUFFERING)) {
      // Trigger more work at the current time until the condition is fulfilled.
      if (Clock.DEFAULT.currentTimeMillis() >= timeoutTimeMs) {
        throw new TimeoutException();
      }
      player.pause();
      player.play();
      advance(player).untilPendingCommandsAreFullyHandled();
    }
    if (player.getPlayerError() != null) {
      throw player.getPlayerError();
    }
  }
}
