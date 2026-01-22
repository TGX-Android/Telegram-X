/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.video;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.PreviewingVideoGraph;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.media3.common.util.TimestampIterator;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/** Unit test for {@link PlaybackVideoGraphWrapper}. */
@RunWith(AndroidJUnit4.class)
public final class PlaybackVideoGraphWrapperTest {
  @Test
  public void builder_calledMultipleTimes_throws() {
    Context context = ApplicationProvider.getApplicationContext();
    PlaybackVideoGraphWrapper.Builder builder =
        new PlaybackVideoGraphWrapper.Builder(context, createVideoFrameReleaseControl());

    builder.build();

    assertThrows(IllegalStateException.class, builder::build);
  }

  @Test
  public void initializeSink_calledTwice_throws() throws VideoSink.VideoSinkException {
    PlaybackVideoGraphWrapper playbackVideoGraphWrapper =
        createPlaybackVideoGraphWrapper(new FakeVideoFrameProcessor());
    VideoSink sink = playbackVideoGraphWrapper.getSink(/* inputIndex= */ 0);
    sink.initialize(new Format.Builder().build());

    assertThrows(IllegalStateException.class, () -> sink.initialize(new Format.Builder().build()));
  }

  @Test
  public void onInputStreamChanged_setsVideoSinkVideoEffects() throws VideoSink.VideoSinkException {
    ImmutableList<Effect> firstEffects = ImmutableList.of(Mockito.mock(Effect.class));
    ImmutableList<Effect> secondEffects =
        ImmutableList.of(Mockito.mock(Effect.class), Mockito.mock(Effect.class));
    FakeVideoFrameProcessor videoFrameProcessor = new FakeVideoFrameProcessor();
    PlaybackVideoGraphWrapper playbackVideoGraphWrapper =
        createPlaybackVideoGraphWrapper(videoFrameProcessor);
    Format format = new Format.Builder().build();
    VideoSink sink = playbackVideoGraphWrapper.getSink(/* inputIndex= */ 0);

    sink.initialize(format);

    sink.onInputStreamChanged(VideoSink.INPUT_TYPE_SURFACE, format, firstEffects);
    assertThat(videoFrameProcessor.registeredEffects).isEqualTo(firstEffects);
    sink.onInputStreamChanged(VideoSink.INPUT_TYPE_SURFACE, format, secondEffects);
    assertThat(videoFrameProcessor.registeredEffects).isEqualTo(secondEffects);
    sink.onInputStreamChanged(VideoSink.INPUT_TYPE_SURFACE, format, ImmutableList.of());
    assertThat(videoFrameProcessor.registeredEffects).isEmpty();
  }

  private static PlaybackVideoGraphWrapper createPlaybackVideoGraphWrapper(
      VideoFrameProcessor videoFrameProcessor) {
    Context context = ApplicationProvider.getApplicationContext();
    return new PlaybackVideoGraphWrapper.Builder(context, createVideoFrameReleaseControl())
        .setPreviewingVideoGraphFactory(new TestPreviewingVideoGraphFactory(videoFrameProcessor))
        .build();
  }

  private static VideoFrameReleaseControl createVideoFrameReleaseControl() {
    Context context = ApplicationProvider.getApplicationContext();
    VideoFrameReleaseControl.FrameTimingEvaluator frameTimingEvaluator =
        new VideoFrameReleaseControl.FrameTimingEvaluator() {
          @Override
          public boolean shouldForceReleaseFrame(long earlyUs, long elapsedSinceLastReleaseUs) {
            return false;
          }

          @Override
          public boolean shouldDropFrame(
              long earlyUs, long elapsedRealtimeUs, boolean isLastFrame) {
            return false;
          }

          @Override
          public boolean shouldIgnoreFrame(
              long earlyUs,
              long positionUs,
              long elapsedRealtimeUs,
              boolean isLastFrame,
              boolean treatDroppedBuffersAsSkipped) {
            return false;
          }
        };
    return new VideoFrameReleaseControl(
        context, frameTimingEvaluator, /* allowedJoiningTimeMs= */ 0);
  }

  private static class FakeVideoFrameProcessor implements VideoFrameProcessor {

    List<Effect> registeredEffects = ImmutableList.of();

    @Override
    public boolean queueInputBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator) {
      return false;
    }

    @Override
    public boolean queueInputTexture(int textureId, long presentationTimeUs) {
      return false;
    }

    @Override
    public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {}

    @Override
    public void setOnInputSurfaceReadyListener(Runnable listener) {}

    @Override
    public Surface getInputSurface() {
      return null;
    }

    @Override
    public void registerInputStream(
        @InputType int inputType, Format format, List<Effect> effects, long offsetToAddUs) {
      registeredEffects = effects;
    }

    @Override
    public boolean registerInputFrame() {
      return true;
    }

    @Override
    public int getPendingInputFrameCount() {
      return 0;
    }

    @Override
    public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {}

    @Override
    public void renderOutputFrame(long renderTimeNs) {}

    @Override
    public void signalEndOfInput() {}

    @Override
    public void flush() {}

    @Override
    public void release() {}
  }

  private static class TestPreviewingVideoGraphFactory implements PreviewingVideoGraph.Factory {
    // Using a mock but we don't assert mock interactions. If needed to assert interactions, we
    // should a fake instead.
    private final PreviewingVideoGraph previewingVideoGraph =
        Mockito.mock(PreviewingVideoGraph.class);
    private final VideoFrameProcessor videoFrameProcessor;

    public TestPreviewingVideoGraphFactory(VideoFrameProcessor videoFrameProcessor) {
      this.videoFrameProcessor = videoFrameProcessor;
    }

    @Override
    public PreviewingVideoGraph create(
        Context context,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        VideoGraph.Listener listener,
        Executor listenerExecutor,
        VideoCompositorSettings videoCompositorSettings,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs) {
      when(previewingVideoGraph.getProcessor(anyInt())).thenReturn(videoFrameProcessor);
      return previewingVideoGraph;
    }

    @Override
    public boolean supportsMultipleInputs() {
      return false;
    }
  }
}
