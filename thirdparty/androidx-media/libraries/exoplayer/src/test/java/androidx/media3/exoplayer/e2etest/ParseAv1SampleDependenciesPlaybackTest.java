/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.ClippingConfiguration;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** End-to-end playback tests using AV1 sample skipping. */
@RunWith(AndroidJUnit4.class)
public class ParseAv1SampleDependenciesPlaybackTest {

  private static final String TEST_MP4_URI = "asset:///media/mp4/sample_with_av1c.mp4";

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void playback_withClippedMediaItem_skipNonReferenceInputSamples() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory renderersFactory = new CapturingRenderersFactory(applicationContext);
    renderersFactory.experimentalSetParseAv1SampleDependencies(true);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);
    player.setMediaItem(
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new ClippingConfiguration.Builder().setStartPositionMs(200).build())
            .build());

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        /* dumpFile= */ "playbackdumps/av1SampleDependencies/clippedMediaItem.dump");
  }

  // TODO: b/390604981 - Run the test on older SDK levels to ensure it uses a MediaCodec shadow
  // with more than one buffer slot.
  @Config(minSdk = 30)
  @Test
  public void playback_withLateThresholdToDropDecoderInput_skipNonReferenceInputSamples()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactoryWithLateThresholdToDropDecoderInputUs renderersFactory =
        new CapturingRenderersFactoryWithLateThresholdToDropDecoderInputUs(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.addAnalyticsListener(
        new AnalyticsListener() {
          @Override
          public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
            // Input buffers near the reset position should not be dropped.
            assertThat(eventTime.currentPlaybackPositionMs).isAtLeast(200);
          }
        });
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    player.setMediaItem(MediaItem.fromUri(TEST_MP4_URI));

    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();
    DecoderCounters decoderCounters = player.getVideoDecoderCounters();

    // Do not assert on a full playback dump as it depends on the number of MediaCodec buffer
    // slots, which may change in b/390604981.
    // Half of the input buffers are non-reference OBU_FRAME which will be dropped.
    // The other half are non-reference OBU_FRAME_HEADER - only the first one may be dropped.
    // Which input buffer is dropped first depends on the number of MediaCodec buffer slots.
    // This means the asserts cannot be isEqualTo.
    assertThat(decoderCounters.maxConsecutiveDroppedBufferCount).isAtMost(2);
    assertThat(decoderCounters.droppedInputBufferCount).isAtLeast(4);
  }

  private static final class CapturingRenderersFactoryWithLateThresholdToDropDecoderInputUs
      extends CapturingRenderersFactory {

    private final Context context;

    /**
     * Creates an instance.
     *
     * @param context The {@link Context}.
     */
    public CapturingRenderersFactoryWithLateThresholdToDropDecoderInputUs(Context context) {
      super(context);
      this.context = context;
    }

    @Override
    public Renderer[] createRenderers(
        Handler eventHandler,
        VideoRendererEventListener videoRendererEventListener,
        AudioRendererEventListener audioRendererEventListener,
        TextOutput textRendererOutput,
        MetadataOutput metadataRendererOutput) {
      return new Renderer[] {
        new CapturingMediaCodecVideoRenderer(
            context,
            getMediaCodecAdapterFactory(),
            MediaCodecSelector.DEFAULT,
            DefaultRenderersFactory.DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS,
            /* enableDecoderFallback= */ false,
            eventHandler,
            videoRendererEventListener,
            /* parseAv1SampleDependencies= */ true,
            /* lateThresholdToDropDecoderInputUs= */ -100_000_000L)
      };
    }

    /**
     * A {@link MediaCodecVideoRenderer} that will not skip or drop buffers due to slow processing.
     */
    private static class CapturingMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
      private CapturingMediaCodecVideoRenderer(
          Context context,
          MediaCodecAdapter.Factory codecAdapterFactory,
          MediaCodecSelector mediaCodecSelector,
          long allowedJoiningTimeMs,
          boolean enableDecoderFallback,
          @Nullable Handler eventHandler,
          @Nullable VideoRendererEventListener eventListener,
          boolean parseAv1SampleDependencies,
          long lateThresholdToDropDecoderInputUs) {
        super(
            new Builder(context)
                .setCodecAdapterFactory(codecAdapterFactory)
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(1)
                .experimentalSetParseAv1SampleDependencies(parseAv1SampleDependencies)
                .experimentalSetLateThresholdToDropDecoderInputUs(
                    lateThresholdToDropDecoderInputUs));
      }

      @Override
      protected boolean shouldDropOutputBuffer(
          long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
        // Do not drop output buffers due to slow processing.
        return false;
      }

      @Override
      protected boolean shouldDropBuffersToKeyframe(
          long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
        // Do not drop output buffers due to slow processing.
        return false;
      }

      @Override
      protected boolean shouldSkipBuffersWithIdenticalReleaseTime() {
        // Do not skip buffers with identical vsync times as we can't control this from tests.
        return false;
      }

      @Override
      protected boolean shouldForceRenderOutputBuffer(long earlyUs, long elapsedSinceLastRenderUs) {
        // An auto-advancing FakeClock can make a lot of progress before
        // AsynchronousMediaCodecAdapter produces an output buffer - causing all output buffers to
        // be force rendered.
        // Force rendering output buffers prevents evaluation of lateThresholdToDropDecoderInputUs.
        // Do not allow force rendering of output buffers when testing
        // lateThresholdToDropDecoderInputUs.
        return false;
      }
    }
  }
}
