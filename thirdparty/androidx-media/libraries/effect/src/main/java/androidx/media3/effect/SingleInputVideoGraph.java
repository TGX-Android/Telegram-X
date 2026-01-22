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

package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoCompositorSettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executor;

/** A {@link VideoGraph} that handles one input stream. */
@UnstableApi
public abstract class SingleInputVideoGraph implements VideoGraph {

  private final Context context;
  private final VideoFrameProcessor.Factory videoFrameProcessorFactory;
  private final ColorInfo outputColorInfo;
  private final Listener listener;
  private final DebugViewProvider debugViewProvider;
  private final Executor listenerExecutor;
  private final boolean renderFramesAutomatically;
  private final long initialTimestampOffsetUs;

  @Nullable private VideoFrameProcessor videoFrameProcessor;
  @Nullable private SurfaceInfo outputSurfaceInfo;
  private boolean released;
  private volatile boolean hasProducedFrameWithTimestampZero;
  private int inputIndex;

  /**
   * Creates an instance.
   *
   * <p>{@code videoCompositorSettings} must be {@link VideoCompositorSettings#DEFAULT}.
   */
  public SingleInputVideoGraph(
      Context context,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      ColorInfo outputColorInfo,
      Listener listener,
      DebugViewProvider debugViewProvider,
      Executor listenerExecutor,
      VideoCompositorSettings videoCompositorSettings,
      boolean renderFramesAutomatically,
      long initialTimestampOffsetUs) {
    checkState(
        VideoCompositorSettings.DEFAULT.equals(videoCompositorSettings),
        "SingleInputVideoGraph does not use VideoCompositor, and therefore cannot apply"
            + " VideoCompositorSettings");
    this.context = context;
    this.videoFrameProcessorFactory = videoFrameProcessorFactory;
    this.outputColorInfo = outputColorInfo;
    this.listener = listener;
    this.debugViewProvider = debugViewProvider;
    this.listenerExecutor = listenerExecutor;
    this.renderFramesAutomatically = renderFramesAutomatically;
    this.initialTimestampOffsetUs = initialTimestampOffsetUs;
    this.inputIndex = C.INDEX_UNSET;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method must be called at most once.
   */
  @Override
  public void initialize() {
    // Initialization is deferred to registerInput();
  }

  @Override
  public void registerInput(int inputIndex) throws VideoFrameProcessingException {
    checkStateNotNull(videoFrameProcessor == null && !released);
    checkState(this.inputIndex == C.INDEX_UNSET, "This VideoGraph supports only one input.");

    this.inputIndex = inputIndex;
    videoFrameProcessor =
        videoFrameProcessorFactory.create(
            context,
            debugViewProvider,
            outputColorInfo,
            renderFramesAutomatically,
            /* listenerExecutor= */ MoreExecutors.directExecutor(),
            new VideoFrameProcessor.Listener() {
              private long lastProcessedFramePresentationTimeUs;

              @Override
              public void onOutputSizeChanged(int width, int height) {
                listenerExecutor.execute(() -> listener.onOutputSizeChanged(width, height));
              }

              @Override
              public void onOutputFrameRateChanged(float frameRate) {
                listenerExecutor.execute(() -> listener.onOutputFrameRateChanged(frameRate));
              }

              @Override
              public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                // Frames are rendered automatically.
                if (presentationTimeUs == 0) {
                  hasProducedFrameWithTimestampZero = true;
                }
                lastProcessedFramePresentationTimeUs = presentationTimeUs;
                listenerExecutor.execute(
                    () -> listener.onOutputFrameAvailableForRendering(presentationTimeUs));
              }

              @Override
              public void onError(VideoFrameProcessingException exception) {
                listenerExecutor.execute(() -> listener.onError(exception));
              }

              @Override
              public void onEnded() {
                listenerExecutor.execute(
                    () -> listener.onEnded(lastProcessedFramePresentationTimeUs));
              }
            });
    if (outputSurfaceInfo != null) {
      videoFrameProcessor.setOutputSurfaceInfo(outputSurfaceInfo);
    }
  }

  @Override
  public VideoFrameProcessor getProcessor(int inputIndex) {
    checkArgument(this.inputIndex != C.INDEX_UNSET && this.inputIndex == inputIndex);
    return checkStateNotNull(videoFrameProcessor);
  }

  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    this.outputSurfaceInfo = outputSurfaceInfo;
    if (videoFrameProcessor != null) {
      videoFrameProcessor.setOutputSurfaceInfo(outputSurfaceInfo);
    }
  }

  @Override
  public boolean hasProducedFrameWithTimestampZero() {
    return hasProducedFrameWithTimestampZero;
  }

  @Override
  public void release() {
    if (released) {
      return;
    }

    if (videoFrameProcessor != null) {
      videoFrameProcessor.release();
      videoFrameProcessor = null;
    }
    released = true;
  }

  protected int getInputIndex() {
    return inputIndex;
  }

  protected long getInitialTimestampOffsetUs() {
    return initialTimestampOffsetUs;
  }
}
