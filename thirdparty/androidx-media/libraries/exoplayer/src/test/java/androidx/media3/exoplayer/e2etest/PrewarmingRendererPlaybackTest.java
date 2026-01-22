/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.image.ImageDecoder;
import androidx.media3.exoplayer.image.ImageRenderer;
import androidx.media3.exoplayer.metadata.MetadataOutput;
import androidx.media3.exoplayer.text.TextOutput;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** End-to-end playback tests using secondary renderers. */
@RunWith(AndroidJUnit4.class)
public class PrewarmingRendererPlaybackTest {

  private static final String TEST_AUDIO_MP4_URI = "asset:///media/mp4/sample_ac3.mp4";
  private static final String TEST_MP4_URI = "asset:///media/mp4/sample.mp4";
  private static final String TEST_IMAGE_URI = "asset:///media/jpeg/tokyo.jpg";

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void playback_withTwoMediaItemsAndSecondaryVideoRenderer_dumpsCorrectOutput()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersWithSecondaryVideoRendererFactory capturingRenderersFactory =
        new CapturingRenderersWithSecondaryVideoRendererFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.addMediaItems(
        ImmutableList.of(
            new MediaItem.Builder().setUri(TEST_MP4_URI).build(),
            new MediaItem.Builder().setUri(TEST_MP4_URI).build()));

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        /* dumpFile= */ "playbackdumps/prewarmingRenderer/"
            + "twoItemsPlaylist-withSecondaryVideoRenderer"
            + ".dump");
  }

  @Test
  public void playback_withThreeItemsAndSecondaryVideoRenderer_dumpsCorrectOutput()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersWithSecondaryVideoRendererFactory capturingRenderersFactory =
        new CapturingRenderersWithSecondaryVideoRendererFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.addMediaItems(
        ImmutableList.of(
            new MediaItem.Builder().setUri(TEST_MP4_URI).build(),
            new MediaItem.Builder().setUri(TEST_MP4_URI).build(),
            new MediaItem.Builder().setUri(TEST_MP4_URI).build()));
    // Disable audio renderer for simpler dump file.
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build());
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/prewarmingRenderer/"
            + "threeItemPlaylist-withSecondaryVideoRenderer"
            + ".dump");
  }

  @Test
  public void playback_withStopDuringPlaybackWithSecondaryVideoRenderer_dumpsCorrectOutput()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersWithSecondaryVideoRendererFactory capturingRenderersFactory =
        new CapturingRenderersWithSecondaryVideoRendererFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    // Create media item containing a single sample.
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(25)
                    .build())
            .build();
    player.addMediaItems(ImmutableList.of(mediaItem, mediaItem));
    // Disable audio renderer for simpler dump file.
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build());
    player.prepare();
    player.play();

    advance(player).untilStartOfMediaItem(1);

    // Stop and reset player to simulate stop, reset, and transition back to using primary.
    player.stop();
    player.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 0);
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/prewarmingRenderer/"
            + "twoItemPlaylist-clippedWithStopDuringPlaybackWithSecondaryVideoRenderer"
            + ".dump");
  }

  @Test
  public void playback_withMultipleMediaItemsWithClippingConfigurations_dumpsCorrectOutput()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersWithSecondaryVideoRendererFactory capturingRenderersFactory =
        new CapturingRenderersWithSecondaryVideoRendererFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.addMediaItem(
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(250)
                    .build())
            .build());
    player.addMediaItem(
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(250)
                    .setEndPositionMs(600)
                    .build())
            .build());
    player.addMediaItem(
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(600)
                    .setEndPositionMs(C.TIME_END_OF_SOURCE)
                    .build())
            .build());

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/prewarmingRenderer/"
            + "threeItemPlaylist-withClippingConfigurations"
            + ".dump");
  }

  @Test
  public void playback_withPrewarmingNonTransitioningRenderer_dumpsCorrectOutput()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersWithSecondaryVideoRendererFactory capturingRenderersFactory =
        new CapturingRenderersWithSecondaryVideoRendererFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.addMediaItem(new MediaItem.Builder().setUri(TEST_AUDIO_MP4_URI).build());
    player.addMediaItem(
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(300)
                    .setEndPositionMs(600)
                    .build())
            .build());

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/prewarmingRenderer/"
            + "twoItemPlaylist-withPrewarmingNonTransitioningRenderer"
            + ".dump");
  }

  @Test
  public void playback_withImageVideoPlaylistAndSecondaryVideoRendererOnly_dumpsCorrectOutput()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersWithSecondaryVideoAndImageRenderersFactory capturingRenderersFactory =
        new CapturingRenderersWithSecondaryVideoAndImageRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    player.addMediaItems(
        ImmutableList.of(
            new MediaItem.Builder().setUri(TEST_IMAGE_URI).setImageDurationMs(1000).build(),
            new MediaItem.Builder().setUri(TEST_MP4_URI).build(),
            new MediaItem.Builder().setUri(TEST_IMAGE_URI).setImageDurationMs(1000).build()));

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        /* dumpFile= */ "playbackdumps/prewarmingRenderer/"
            + "imageVideoPlaylist-withSecondaryRenderers"
            + ".dump");
  }

  /** This class extends {@link CapturingRenderersFactory} to provide a secondary video renderer. */
  private static final class CapturingRenderersWithSecondaryVideoRendererFactory
      extends CapturingRenderersFactory {

    /**
     * Creates an instance.
     *
     * @param context The {@link Context}.
     */
    public CapturingRenderersWithSecondaryVideoRendererFactory(Context context) {
      super(context);
    }

    @Override
    public Renderer createSecondaryRenderer(
        Renderer renderer,
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      if (renderer.getClass().getSuperclass() == MediaCodecVideoRenderer.class) {
        return createMediaCodecVideoRenderer(eventHandler, videoRendererEventListener);
      }
      return null;
    }
  }

  /** This class extends {@link CapturingRenderersFactory} to provide a secondary video renderer. */
  private static final class CapturingRenderersWithSecondaryVideoAndImageRenderersFactory
      extends CapturingRenderersFactory {

    /**
     * Creates an instance.
     *
     * @param context The {@link Context}.
     */
    public CapturingRenderersWithSecondaryVideoAndImageRenderersFactory(Context context) {
      super(context);
    }

    @Override
    public Renderer createSecondaryRenderer(
        Renderer renderer,
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      if (renderer.getClass().getSuperclass() == MediaCodecVideoRenderer.class) {
        return createMediaCodecVideoRenderer(eventHandler, videoRendererEventListener);
      } else if (renderer.getClass() == ImageRenderer.class) {
        return new ImageRenderer(ImageDecoder.Factory.DEFAULT, /* imageOutput= */ null);
      }
      return null;
    }
  }
}
