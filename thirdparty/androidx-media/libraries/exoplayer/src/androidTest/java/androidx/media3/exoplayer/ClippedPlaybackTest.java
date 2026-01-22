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
package androidx.media3.exoplayer;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.SubtitleConfiguration;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.exoplayer.source.ClippingMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.test.filters.SdkSuppress;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Instrumentation tests for playback of clipped items using {@link MediaItem#clippingConfiguration}
 * or {@link ClippingMediaSource} directly.
 */
@RunWith(Parameterized.class)
public final class ClippedPlaybackTest {

  @Parameters(name = "parseSubtitlesDuringExtraction={0}")
  public static ImmutableList<Boolean> params() {
    return ImmutableList.of(true, false);
  }

  /**
   * We deliberately test both forms of subtitle parsing, to ensure that both work correctly with
   * clipping configurations. Parsing during rendering (i.e. not during extraction) can be a little
   * bit flaky, because playback can complete before the rendering/parsing is finished, and there is
   * no easy way to delay the completion of playback in this case. The parse-during-rendering flow
   * will be removed in a later release (b/289983417), which will resolve this flakiness.
   */
  @Parameter public boolean parseSubtitlesDuringExtraction;

  @Test
  public void subtitlesRespectClipping_singlePeriod() throws Exception {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/sample.mp4")
            .setSubtitleConfigurations(
                ImmutableList.of(
                    new SubtitleConfiguration.Builder(Uri.parse("asset:///media/webvtt/typical"))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()))
            // Expect the clipping to affect both subtitles and video.
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(1000).build())
            .build();
    AtomicReference<ExoPlayer> player = new AtomicReference<>();
    TextCapturingPlaybackListener textCapturer = new TextCapturingPlaybackListener();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.set(
                  buildPlayer(getInstrumentation().getContext(), parseSubtitlesDuringExtraction));
              player.get().addListener(textCapturer);
              player.get().setMediaItem(mediaItem);
              player.get().prepare();
              playWhenLoadingIsDone(player.get());
            });

    textCapturer.block();

    getInstrumentation().runOnMainSync(() -> player.get().release());
    getInstrumentation().waitForIdleSync();
    assertThat(Iterables.getOnlyElement(Iterables.concat(textCapturer.cues)).text.toString())
        .isEqualTo("This is the first subtitle.");
  }

  @Test
  // TODO: b/399815346 - This test is flaky on API 21 even when parsing subtitles during extraction.
  @SdkSuppress(minSdkVersion = 22)
  public void subtitlesRespectClipping_multiplePeriods() throws Exception {
    // Parsing subtitles during rendering is flaky (see comment above), so restrict that
    // configuration to a single API level to reduce the chance of seeing a flaky failure.
    assumeTrue(parseSubtitlesDuringExtraction || Build.VERSION.SDK_INT >= 34);
    ImmutableList<MediaItem> mediaItems =
        ImmutableList.of(
            new MediaItem.Builder()
                .setUri("asset:///media/mp4/sample.mp4")
                .setSubtitleConfigurations(
                    ImmutableList.of(
                        new SubtitleConfiguration.Builder(
                                Uri.parse("asset:///media/webvtt/typical"))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage("en")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()))
                // Expect the clipping to affect both subtitles and video.
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(1000).build())
                .build(),
            new MediaItem.Builder()
                .setUri("asset:///media/mp4/sample.mp4")
                // Not needed for correctness, just makes test run faster. Must be longer than the
                // subtitle content (3.5s).
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(4_000).build())
                .build());
    AtomicReference<ExoPlayer> player = new AtomicReference<>();
    TextCapturingPlaybackListener textCapturer = new TextCapturingPlaybackListener();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              player.set(
                  buildPlayer(getInstrumentation().getContext(), parseSubtitlesDuringExtraction));
              player.get().addListener(textCapturer);
              player.get().setMediaItems(mediaItems);
              player.get().prepare();
              // We don't need playWhenLoadingIsDone here because playback already waits at the end
              // of the first period for subtitles to be fully loaded beforetransitioning to the
              // second period.
              player.get().play();
            });

    textCapturer.block();

    getInstrumentation().runOnMainSync(() -> player.get().release());
    getInstrumentation().waitForIdleSync();
    assertThat(Iterables.getOnlyElement(Iterables.concat(textCapturer.cues)).text.toString())
        .isEqualTo("This is the first subtitle.");
  }

  // Using deprecated TextRenderer.experimentalSetLegacyDecodingEnabled() and
  // MediaSource.Factory.experimentalParseSubtitlesDuringExtraction() methods to ensure legacy
  // subtitle handling keeps working.
  @SuppressWarnings("deprecation")
  private static ExoPlayer buildPlayer(Context context, boolean parseSubtitlesDuringExtraction) {
    return new ExoPlayer.Builder(context)
        .setRenderersFactory(
            new DefaultRenderersFactory(context) {

              @Override
              protected void buildTextRenderers(
                  Context context,
                  TextOutput output,
                  Looper outputLooper,
                  @ExtensionRendererMode int extensionRendererMode,
                  ArrayList<Renderer> out) {
                super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out);
                ((TextRenderer) Iterables.getLast(out))
                    .experimentalSetLegacyDecodingEnabled(!parseSubtitlesDuringExtraction);
              }
            })
        .setMediaSourceFactory(
            new DefaultMediaSourceFactory(context)
                .experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction))
        .build();
  }

  private static void playWhenLoadingIsDone(Player player) {
    AtomicBoolean loadingStarted = new AtomicBoolean(false);
    player.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_IS_LOADING_CHANGED)
                && loadingStarted.getAndSet(player.isLoading())
                && !player.isLoading()) {
              player.play();
            }
          }
        });
  }

  private static class TextCapturingPlaybackListener implements Player.Listener {

    private final ConditionVariable playbackEnded;
    private final List<List<Cue>> cues;
    private final AtomicReference<PlaybackException> playerError;

    private TextCapturingPlaybackListener() {
      playbackEnded = new ConditionVariable();
      cues = Collections.synchronizedList(new ArrayList<>());
      playerError = new AtomicReference<>();
    }

    @Override
    public void onCues(CueGroup cueGroup) {
      this.cues.add(cueGroup.cues);
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      if (playbackState == Player.STATE_ENDED) {
        playbackEnded.open();
      }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      playerError.set(error);
      playbackEnded.open();
    }

    public void block() throws InterruptedException, PlaybackException {
      playbackEnded.block();
      PlaybackException playerError = this.playerError.get();
      if (playerError != null) {
        throw playerError;
      }
    }
  }
}
