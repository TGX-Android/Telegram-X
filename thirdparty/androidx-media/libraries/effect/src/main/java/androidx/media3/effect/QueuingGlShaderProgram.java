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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlRect;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link GlShaderProgram} that enables {@linkplain ConcurrentEffect
 * asynchronous} processing of video frames outside the current OpenGL context without processor
 * stalls.
 *
 * <h3>Data Dependencies and Processor Stalls</h3>
 *
 * Sharing image data between GPU and a {@link ConcurrentEffect} running on another processor
 * creates a data dependency. The GPU must finish processing the frame before the data can be
 * {@linkplain ConcurrentEffect#queueInputFrame submitted} to the other processor. And the other
 * processor must finish processing the image data before any modifications can be {@linkplain
 * ConcurrentEffect#finishProcessingAndBlend drawn} back to the main video stream.
 *
 * <p>If we force a synchronization and data transfer (e.g. via {@link
 * android.opengl.GLES20#glReadPixels}) too early a processor would stall without any work
 * available.
 *
 * <p>To keep multiple processors busy, {@code QueuingGlShaderProgram} maintains a queue of frames
 * that are being processed by the provided {@link ConcurrentEffect}. The queue pipelines the
 * processing stages and allows one frame to be processed on the GPU, while at the same time another
 * frame is processed by the {@link ConcurrentEffect}. The size of the queue is configurable on
 * construction, and should be large enough to compensate for the time required to execute the
 * {@linkplain ConcurrentEffect asynchronous effect}, and any data transfer that is required between
 * the processors.
 *
 * <p>The output frame {@link GlTextureInfo} produced by this class contains a copy of the
 * {@linkplain #queueInputFrame input frame}, unless the frame contents were modified by the {@link
 * ConcurrentEffect}.
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 *
 * @param <T> An intermediate type used by {@link ConcurrentEffect} implementations.
 */
@UnstableApi
/* package */ final class QueuingGlShaderProgram<T> implements GlShaderProgram {

  private static final long PROCESSING_TIMEOUT_MS = 500_000L;
  private static final String TAG = "QueuingGlShaderProgram";

  /** A concurrent effect that is applied by the {@link QueuingGlShaderProgram}. */
  public interface ConcurrentEffect<T> {
    /**
     * Submits a frame to be processed by the concurrent effect.
     *
     * <p>The {@linkplain GlTextureInfo textureInfo} will hold the image data corresponding to the
     * frame at {@code presentationTimeUs}. The image data will not be modified until the returned
     * {@link Future} {@linkplain Future#isDone() completes} or {@linkplain Future#isCancelled() is
     * cancelled}.
     *
     * <p>The {@linkplain GlTextureInfo textureInfo} will have a valid {@linkplain
     * GlTextureInfo#fboId framebuffer object}.
     *
     * <p>This method will be called on the thread that owns the OpenGL context.
     *
     * @param textureInfo The texture info of the current frame.
     * @param presentationTimeUs The presentation timestamp of the input frame, in microseconds.
     * @return A {@link Future} representing pending completion of the task.
     */
    Future<T> queueInputFrame(
        GlObjectsProvider glObjectsProvider, GlTextureInfo textureInfo, long presentationTimeUs);

    /**
     * Finishes processing the frame at {@code presentationTimeUs}. This method optionally allows
     * the instance to draw an overlay or blend with the {@linkplain GlTextureInfo output frame}.
     *
     * <p>The {@linkplain GlTextureInfo outputFrame} contains the image data corresponding to the
     * frame at {@code presentationTimeUs} when this method is invoked.
     *
     * <p>This method will be called on the thread that owns the OpenGL context.
     *
     * @param outputFrame The texture info of the frame.
     * @param presentationTimeUs The presentation timestamp of the frame, in microseconds.
     * @param result The result of the asynchronous computation in {@link #queueInputFrame}.
     */
    void finishProcessingAndBlend(GlTextureInfo outputFrame, long presentationTimeUs, T result)
        throws VideoFrameProcessingException;

    /**
     * Notifies the {@code ConcurrentEffect} that no further input frames belonging to the current
     * input stream will be queued.
     *
     * <p>Can block until the {@code ConcurrentEffect} finishes processing pending frames.
     *
     * @throws VideoFrameProcessingException If an error occurs while processing pending frames.
     */
    void signalEndOfCurrentInputStream() throws VideoFrameProcessingException;

    /**
     * Flushes the {@code ConcurrentEffect}.
     *
     * <p>The {@code ConcurrentEffect} should reclaim the ownership of any allocated resources.
     *
     * @throws VideoFrameProcessingException If an error occurs while reclaiming resources.
     */
    void flush() throws VideoFrameProcessingException;

    /**
     * Releases all resources.
     *
     * @throws VideoFrameProcessingException If an error occurs while releasing resources.
     */
    void release() throws VideoFrameProcessingException;
  }

  private final ConcurrentEffect<T> concurrentEffect;
  private final TexturePool outputTexturePool;
  private final Queue<TimedTextureInfo<T>> frameQueue;
  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private int inputWidth;
  private int inputHeight;

  /**
   * Creates a {@code QueuingGlShaderProgram} instance.
   *
   * @param useHighPrecisionColorComponents If {@code false}, uses colors with 8-bit unsigned bytes.
   *     If {@code true}, use 16-bit (half-precision) floating-point.
   * @param queueSize The number of frames to buffer before producing output, and also the capacity
   *     of the texture pool.
   * @param concurrentEffect The asynchronous effect to apply to each frame.
   */
  public QueuingGlShaderProgram(
      boolean useHighPrecisionColorComponents,
      @IntRange(from = 1) int queueSize,
      ConcurrentEffect<T> concurrentEffect) {
    checkArgument(queueSize > 0);
    this.concurrentEffect = concurrentEffect;
    frameQueue = new ArrayDeque<>(queueSize);
    outputTexturePool = new TexturePool(useHighPrecisionColorComponents, queueSize);
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener =
        (frameProcessingException) ->
            Log.e(
                TAG,
                "Exception caught by default QueuingGlShaderProgram errorListener.",
                frameProcessingException);
    errorListenerExecutor = MoreExecutors.directExecutor();
    inputWidth = C.LENGTH_UNSET;
    inputHeight = C.LENGTH_UNSET;
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    for (int i = 0; i < outputTexturePool.freeTextureCount(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    this.errorListenerExecutor = errorListenerExecutor;
    this.errorListener = errorListener;
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    try {
      if (inputWidth != inputTexture.width
          || inputHeight != inputTexture.height
          || !outputTexturePool.isConfigured()) {
        // Output all pending frames before processing a format change.
        while (outputOneFrame()) {}
        inputWidth = inputTexture.width;
        inputHeight = inputTexture.height;
        outputTexturePool.ensureConfigured(glObjectsProvider, inputWidth, inputHeight);
      }

      // Focus on the next free buffer.
      GlTextureInfo outputTexture = outputTexturePool.useTexture();

      // Copy frame from inputTexture fbo to outputTexture fbo.
      checkState(inputTexture.fboId != C.INDEX_UNSET);
      GlUtil.blitFrameBuffer(
          inputTexture.fboId,
          new GlRect(inputWidth, inputHeight),
          outputTexture.fboId,
          new GlRect(inputWidth, inputHeight));

      Future<T> task =
          concurrentEffect.queueInputFrame(glObjectsProvider, outputTexture, presentationTimeUs);
      frameQueue.add(new TimedTextureInfo<T>(outputTexture, presentationTimeUs, task));

      inputListener.onInputFrameProcessed(inputTexture);

      if (frameQueue.size() == outputTexturePool.capacity()) {
        checkState(outputOneFrame());
      }
    } catch (GlUtil.GlException e) {
      onError(e);
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    if (!outputTexturePool.isUsingTexture(outputTexture)) {
      // This allows us to ignore outputTexture instances not associated with this
      // GlShaderProgram instance. This may happen if a GlShaderProgram is introduced into
      // the GlShaderProgram chain after frames already exist in the pipeline.
      // TODO - b/320481157: Consider removing this if condition and disallowing disconnecting a
      //  GlShaderProgram while it still has in-use frames.
      return;
    }
    outputTexturePool.freeTexture(outputTexture);
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    try {
      concurrentEffect.signalEndOfCurrentInputStream();
    } catch (VideoFrameProcessingException e) {
      onError(e);
    }
    while (outputOneFrame()) {}
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  @CallSuper
  public void flush() {
    try {
      concurrentEffect.flush();
    } catch (VideoFrameProcessingException e) {
      onError(e);
    }
    cancelProcessingOfPendingFrames();
    outputTexturePool.freeAllTextures();
    inputListener.onFlush();
    for (int i = 0; i < outputTexturePool.capacity(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  @CallSuper
  public void release() throws VideoFrameProcessingException {
    try {
      cancelProcessingOfPendingFrames();
      concurrentEffect.release();
      outputTexturePool.deleteAllTextures();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  /**
   * Outputs one frame from {@link #frameQueue}.
   *
   * <p>Returns {@code false} if no more frames are available for output.
   */
  private boolean outputOneFrame() {
    TimedTextureInfo<T> timedTextureInfo = frameQueue.poll();
    if (timedTextureInfo == null) {
      return false;
    }
    try {
      T result =
          Futures.getChecked(
              timedTextureInfo.task,
              VideoFrameProcessingException.class,
              PROCESSING_TIMEOUT_MS,
              TimeUnit.MILLISECONDS);
      GlUtil.focusFramebufferUsingCurrentContext(
          timedTextureInfo.textureInfo.fboId,
          timedTextureInfo.textureInfo.width,
          timedTextureInfo.textureInfo.height);
      concurrentEffect.finishProcessingAndBlend(
          timedTextureInfo.textureInfo, timedTextureInfo.presentationTimeUs, result);
      outputListener.onOutputFrameAvailable(
          timedTextureInfo.textureInfo, timedTextureInfo.presentationTimeUs);
      return true;
    } catch (GlUtil.GlException | VideoFrameProcessingException e) {
      onError(e);
      return false;
    }
  }

  private void cancelProcessingOfPendingFrames() {
    TimedTextureInfo<T> timedTextureInfo;
    while ((timedTextureInfo = frameQueue.poll()) != null) {
      timedTextureInfo.task.cancel(/* mayInterruptIfRunning= */ false);
    }
  }

  private void onError(Exception e) {
    errorListenerExecutor.execute(
        () -> errorListener.onError(VideoFrameProcessingException.from(e)));
  }

  private static class TimedTextureInfo<T> {
    final GlTextureInfo textureInfo;
    final long presentationTimeUs;
    final Future<T> task;

    TimedTextureInfo(GlTextureInfo textureInfo, long presentationTimeUs, Future<T> task) {
      this.textureInfo = textureInfo;
      this.presentationTimeUs = presentationTimeUs;
      this.task = task;
    }
  }
}
