/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer.mh.performance;

import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888Image;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888ImageBuffer;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.media3.transformer.mh.performance.PlaybackTestUtil.createTimestampOverlay;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.TimestampWrapper;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.transformer.SurfaceTestActivity;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Pixel tests for {@link ExoPlayer#setVideoEffects}. */
// These tests are in the performance package even though they are not performance tests so that
// they are not run on all devices. This is because they use ImageReader, which has a tendency to
// drop frames.
@RunWith(AndroidJUnit4.class)
public class EffectPlaybackPixelTest {

  private static final String TEST_DIRECTORY = "test-generated-goldens/ExoPlayerPlaybackTest";
  private static final long TEST_TIMEOUT_MS = 10_000;

  /** Playback test for {@link Effect}-enabled playback. */
  @Rule public final TestName testName = new TestName();

  // Force the test to run in foreground to make it faster and avoid ImageReader frame drops.
  @Rule
  public ActivityScenarioRule<SurfaceTestActivity> rule =
      new ActivityScenarioRule<>(SurfaceTestActivity.class);

  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private @MonotonicNonNull ExoPlayer player;
  private @MonotonicNonNull ImageReader outputImageReader;
  private String testId;

  @Before
  public void setUp() {
    testId = testName.getMethodName();
    // Setting maxImages=10 ensures image reader gets all rendered frames from
    // VideoFrameProcessor. Using maxImages=10 runs successfully on a Pixel3.
    outputImageReader =
        ImageReader.newInstance(
            MP4_ASSET.videoFormat.width,
            MP4_ASSET.videoFormat.height,
            PixelFormat.RGBA_8888,
            // Use a larger count to avoid ImageReader dropping frames
            /* maxImages= */ 10);
  }

  @After
  public void tearDown() {
    instrumentation.runOnMainSync(() -> release(player, outputImageReader));
  }

  @Test
  public void exoplayerEffectsPreviewTest_playWhenReadySetToFalse_ensuresFirstFrameRendered()
      throws Exception {
    AtomicReference<Bitmap> renderedFirstFrameBitmap = new AtomicReference<>();
    ConditionVariable hasRenderedFirstFrameCondition = new ConditionVariable();

    instrumentation.runOnMainSync(
        () -> {
          player = new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();
          checkStateNotNull(outputImageReader);
          outputImageReader.setOnImageAvailableListener(
              imageReader -> {
                try (Image image = imageReader.acquireLatestImage()) {
                  renderedFirstFrameBitmap.set(createArgb8888BitmapFromRgba8888Image(image));
                }
                hasRenderedFirstFrameCondition.open();
              },
              Util.createHandlerForCurrentOrMainLooper());

          setOutputSurfaceAndSizeOnPlayer(
              player,
              checkNotNull(findVideoRenderer(player)),
              outputImageReader.getSurface(),
              new Size(MP4_ASSET.videoFormat.width, MP4_ASSET.videoFormat.height));

          player.setPlayWhenReady(false);
          player.setVideoEffects(ImmutableList.of(createTimestampOverlay()));

          // Adding an EventLogger to use its log output in case the test fails.
          player.addAnalyticsListener(new EventLogger());
          player.setMediaItem(MediaItem.fromUri(MP4_ASSET.uri));
          player.prepare();
        });

    if (!hasRenderedFirstFrameCondition.block(TEST_TIMEOUT_MS)) {
      throw new TimeoutException(
          Util.formatInvariant("First frame not rendered in %d ms.", TEST_TIMEOUT_MS));
    }

    assertThat(renderedFirstFrameBitmap.get()).isNotNull();
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            /* expected= */ readBitmap(TEST_DIRECTORY + "/first_frame.png"),
            /* actual= */ renderedFirstFrameBitmap.get(),
            testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    // TODO: b/315800590 - Verify onFirstFrameRendered is invoked only once.
  }

  @Test
  public void exoplayerEffectsPreviewTest_ensuresAllFramesRendered() throws Exception {
    // Internal reference: b/264252759.
    assumeTrue(
        "This test should run on real devices because OpenGL to ImageReader rendering is"
            + "not always reliable on emulators.",
        !Util.isRunningOnEmulator());

    ArrayList<BitmapPixelTestUtil.ImageBuffer> readImageBuffers = new ArrayList<>();
    AtomicInteger renderedFramesCount = new AtomicInteger();
    ConditionVariable playerEnded = new ConditionVariable();
    ConditionVariable readAllOutputFrames = new ConditionVariable();

    instrumentation.runOnMainSync(
        () -> {
          Context context = ApplicationProvider.getApplicationContext();
          Renderer videoRenderer =
              new NoFrameDroppedVideoRenderer(context, MediaCodecSelector.DEFAULT);
          player =
              new ExoPlayer.Builder(context)
                  .setRenderersFactory(
                      new DefaultRenderersFactory(context) {
                        @Override
                        protected void buildVideoRenderers(
                            Context context,
                            @ExtensionRendererMode int extensionRendererMode,
                            MediaCodecSelector mediaCodecSelector,
                            boolean enableDecoderFallback,
                            Handler eventHandler,
                            VideoRendererEventListener eventListener,
                            long allowedVideoJoiningTimeMs,
                            ArrayList<Renderer> out) {
                          out.add(videoRenderer);
                        }
                      })
                  .build();

          checkStateNotNull(outputImageReader);
          outputImageReader.setOnImageAvailableListener(
              imageReader -> {
                try (Image image = imageReader.acquireNextImage()) {
                  readImageBuffers.add(BitmapPixelTestUtil.copyByteBufferFromRbga8888Image(image));
                }
                if (renderedFramesCount.incrementAndGet() == MP4_ASSET.videoFrameCount) {
                  readAllOutputFrames.open();
                }
              },
              Util.createHandlerForCurrentOrMainLooper());

          setOutputSurfaceAndSizeOnPlayer(
              player,
              videoRenderer,
              outputImageReader.getSurface(),
              new Size(MP4_ASSET.videoFormat.width, MP4_ASSET.videoFormat.height));
          player.setPlayWhenReady(true);
          player.setVideoEffects(ImmutableList.of(createTimestampOverlay()));

          // Adding an EventLogger to use its log output in case the test fails.
          player.addAnalyticsListener(new EventLogger());
          player.addListener(
              new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(@Player.State int playbackState) {
                  if (playbackState == STATE_ENDED) {
                    playerEnded.open();
                  }
                }
              });
          player.setMediaItem(MediaItem.fromUri(MP4_ASSET.uri));
          player.prepare();
        });

    if (!playerEnded.block(TEST_TIMEOUT_MS)) {
      throw new TimeoutException(
          Util.formatInvariant("Playback not ended in %d ms.", TEST_TIMEOUT_MS));
    }

    if (!readAllOutputFrames.block(TEST_TIMEOUT_MS)) {
      throw new TimeoutException(
          Util.formatInvariant(
              "Haven't received all frames in %d ms after playback ends.", TEST_TIMEOUT_MS));
    }

    ArrayList<Float> averagePixelDifferences =
        new ArrayList<>(/* initialCapacity= */ readImageBuffers.size());
    for (int i = 0; i < readImageBuffers.size(); i++) {
      Bitmap actualBitmap = createArgb8888BitmapFromRgba8888ImageBuffer(readImageBuffers.get(i));
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(
              /* expected= */ readBitmap(
                  Util.formatInvariant("%s/%s/frame_%d.png", TEST_DIRECTORY, testId, i)),
              /* actual= */ actualBitmap,
              /* testId= */ Util.formatInvariant("%s_frame_%d", testId, i));
      averagePixelDifferences.add(averagePixelAbsoluteDifference);
    }

    for (int i = 0; i < averagePixelDifferences.size(); i++) {
      float averagePixelDifference = averagePixelDifferences.get(i);
      assertWithMessage(
              Util.formatInvariant(
                  "Frame %d with average pixel difference %f. ", i, averagePixelDifference))
          .that(averagePixelDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    }
  }

  @Test
  public void exoplayerEffectsPreview_withTimestampWrapper_ensuresAllFramesRendered()
      throws Exception {
    // Internal reference: b/264252759.
    assumeTrue(
        "This test should run on real devices because OpenGL to ImageReader rendering is"
            + "not always reliable on emulators.",
        !Util.isRunningOnEmulator());

    ArrayList<BitmapPixelTestUtil.ImageBuffer> readImageBuffers = new ArrayList<>();
    AtomicInteger renderedFramesCount = new AtomicInteger();
    ConditionVariable playerEnded = new ConditionVariable();
    ConditionVariable readAllOutputFrames = new ConditionVariable();
    // Setting maxImages=10 ensures image reader gets all rendered frames from
    // VideoFrameProcessor. Using maxImages=10 runs successfully on a Pixel3.
    outputImageReader =
        ImageReader.newInstance(
            MP4_ASSET.videoFormat.width,
            MP4_ASSET.videoFormat.height,
            PixelFormat.RGBA_8888,
            /* maxImages= */ 10);

    instrumentation.runOnMainSync(
        () -> {
          Context context = ApplicationProvider.getApplicationContext();
          Renderer videoRenderer =
              new NoFrameDroppedVideoRenderer(context, MediaCodecSelector.DEFAULT);
          player =
              new ExoPlayer.Builder(context)
                  .setRenderersFactory(
                      new DefaultRenderersFactory(context) {
                        @Override
                        protected void buildVideoRenderers(
                            Context context,
                            @ExtensionRendererMode int extensionRendererMode,
                            MediaCodecSelector mediaCodecSelector,
                            boolean enableDecoderFallback,
                            Handler eventHandler,
                            VideoRendererEventListener eventListener,
                            long allowedVideoJoiningTimeMs,
                            ArrayList<Renderer> out) {
                          out.add(videoRenderer);
                        }
                      })
                  .build();

          checkStateNotNull(outputImageReader);
          outputImageReader.setOnImageAvailableListener(
              imageReader -> {
                try (Image image = imageReader.acquireNextImage()) {
                  readImageBuffers.add(BitmapPixelTestUtil.copyByteBufferFromRbga8888Image(image));
                }
                if (renderedFramesCount.incrementAndGet() == MP4_ASSET.videoFrameCount) {
                  readAllOutputFrames.open();
                }
              },
              Util.createHandlerForCurrentOrMainLooper());

          setOutputSurfaceAndSizeOnPlayer(
              player,
              videoRenderer,
              outputImageReader.getSurface(),
              new Size(MP4_ASSET.videoFormat.width, MP4_ASSET.videoFormat.height));
          player.setPlayWhenReady(true);
          player.setVideoEffects(
              ImmutableList.of(
                  new TimestampWrapper(
                      new Brightness(0.5f), /* startTimeUs= */ 166833, /* endTimeUs= */ 510000)));

          // Adding an EventLogger to use its log output in case the test fails.
          player.addAnalyticsListener(new EventLogger());
          player.addListener(
              new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(@Player.State int playbackState) {
                  if (playbackState == STATE_ENDED) {
                    playerEnded.open();
                  }
                }
              });
          player.setMediaItem(MediaItem.fromUri(MP4_ASSET.uri));
          player.prepare();
        });

    if (!playerEnded.block(TEST_TIMEOUT_MS)) {
      throw new TimeoutException(
          Util.formatInvariant("Playback not ended in %d ms.", TEST_TIMEOUT_MS));
    }

    if (!readAllOutputFrames.block(TEST_TIMEOUT_MS)) {
      throw new TimeoutException(
          Util.formatInvariant(
              "Haven't received all frames in %d ms after playback ends.", TEST_TIMEOUT_MS));
    }

    ArrayList<Float> averagePixelDifferences =
        new ArrayList<>(/* initialCapacity= */ readImageBuffers.size());
    for (int i = 0; i < readImageBuffers.size(); i++) {
      Bitmap actualBitmap = createArgb8888BitmapFromRgba8888ImageBuffer(readImageBuffers.get(i));
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(
              /* expected= */ readBitmap(
                  Util.formatInvariant("%s/%s/frame_%d.png", TEST_DIRECTORY, testId, i)),
              /* actual= */ actualBitmap,
              /* testId= */ Util.formatInvariant("%s_frame_%d", testId, i));
      averagePixelDifferences.add(averagePixelAbsoluteDifference);
    }

    for (int i = 0; i < averagePixelDifferences.size(); i++) {
      float averagePixelDifference = averagePixelDifferences.get(i);
      assertWithMessage(
              Util.formatInvariant(
                  "Frame %d with average pixel difference %f. ", i, averagePixelDifference))
          .that(averagePixelDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    }
  }

  @Nullable
  private static MediaCodecVideoRenderer findVideoRenderer(ExoPlayer player) {
    for (int i = 0; i < player.getRendererCount(); i++) {
      if (player.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
        Renderer renderer = player.getRenderer(i);
        if (renderer instanceof MediaCodecVideoRenderer) {
          return (MediaCodecVideoRenderer) renderer;
        }
      }
    }
    return null;
  }

  private static void setOutputSurfaceAndSizeOnPlayer(
      ExoPlayer player, Renderer videoRenderer, Surface outputSurface, Size outputSize) {
    // We need to access renderer directly because ExoPlayer.setVideoEffects() doesn't support
    // output to a Surface. When using ImageReader, we need to manually set output resolution on
    // the renderer directly.
    player
        .createMessage(videoRenderer)
        .setType(Renderer.MSG_SET_VIDEO_OUTPUT)
        .setPayload(outputSurface)
        .send();
    player
        .createMessage(videoRenderer)
        .setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION)
        .setPayload(outputSize)
        .send();
  }

  private static void release(@Nullable Player player, @Nullable ImageReader imageReader) {
    if (player != null) {
      player.release();
    }
    if (imageReader != null) {
      imageReader.close();
    }
  }

  private static class NoFrameDroppedVideoRenderer extends MediaCodecVideoRenderer {

    public NoFrameDroppedVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
      super(new Builder(context).setMediaCodecSelector(mediaCodecSelector));
    }

    @Override
    protected boolean shouldDropOutputBuffer(
        long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
      return false;
    }

    @Override
    protected boolean shouldDropBuffersToKeyframe(
        long earlyUs, long elapsedRealtimeUs, boolean isLastBuffer) {
      return false;
    }
  }
}
