/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.media3.transformer;

import static androidx.media3.common.VideoFrameProcessor.RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.SingleInputVideoGraph;
import java.util.List;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link TransformerVideoGraph Transformer}-specific implementation of {@link
 * SingleInputVideoGraph}.
 */
/* package */ final class TransformerSingleInputVideoGraph extends SingleInputVideoGraph
    implements TransformerVideoGraph {

  /** A factory for creating {@link TransformerSingleInputVideoGraph} instances. */
  public static final class Factory implements TransformerVideoGraph.Factory {

    private final VideoFrameProcessor.Factory videoFrameProcessorFactory;

    public Factory(VideoFrameProcessor.Factory videoFrameProcessorFactory) {
      this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    }

    @Override
    public TransformerSingleInputVideoGraph create(
        Context context,
        ColorInfo outputColorInfo,
        DebugViewProvider debugViewProvider,
        Listener listener,
        Executor listenerExecutor,
        VideoCompositorSettings videoCompositorSettings,
        List<Effect> compositionEffects,
        long initialTimestampOffsetUs,
        boolean renderFramesAutomatically) {
      return new TransformerSingleInputVideoGraph(
          context,
          videoFrameProcessorFactory,
          outputColorInfo,
          listener,
          debugViewProvider,
          listenerExecutor,
          videoCompositorSettings,
          renderFramesAutomatically,
          compositionEffects,
          initialTimestampOffsetUs);
    }
  }

  private final List<Effect> compositionEffects;
  private @MonotonicNonNull VideoFrameProcessingWrapper videoFrameProcessingWrapper;

  private TransformerSingleInputVideoGraph(
      Context context,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      ColorInfo outputColorInfo,
      Listener listener,
      DebugViewProvider debugViewProvider,
      Executor listenerExecutor,
      VideoCompositorSettings videoCompositorSettings,
      boolean renderFramesAutomatically,
      List<Effect> compositionEffects,
      long initialTimestampOffsetUs) {
    super(
        context,
        videoFrameProcessorFactory,
        outputColorInfo,
        listener,
        debugViewProvider,
        listenerExecutor,
        videoCompositorSettings,
        renderFramesAutomatically,
        initialTimestampOffsetUs);
    this.compositionEffects = compositionEffects;
  }

  @Override
  public GraphInput createInput(int inputIndex) throws VideoFrameProcessingException {
    checkState(videoFrameProcessingWrapper == null);
    registerInput(inputIndex);
    videoFrameProcessingWrapper =
        new VideoFrameProcessingWrapper(
            getProcessor(inputIndex), compositionEffects, getInitialTimestampOffsetUs());
    return videoFrameProcessingWrapper;
  }

  @Override
  public void renderOutputFrameWithMediaPresentationTime() {
    getProcessor(getInputIndex()).renderOutputFrame(RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME);
  }
}
