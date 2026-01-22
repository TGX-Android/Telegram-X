/*
 * Copyright 2022 The Android Open Source Project
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

import static androidx.media3.common.VideoFrameProcessor.RENDER_OUTPUT_FRAME_IMMEDIATELY;
import static androidx.media3.common.VideoFrameProcessor.RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.effect.DebugTraceUtil.COMPONENT_VFP;
import static androidx.media3.effect.DebugTraceUtil.EVENT_RENDERED_TO_OUTPUT_SURFACE;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.LongArrayQueue;
import androidx.media3.common.util.Size;
import androidx.media3.effect.DefaultVideoFrameProcessor.WorkingColorSpace;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Wrapper around a {@link DefaultShaderProgram} that renders to either the provided output surface
 * or texture.
 *
 * <p>Also renders to a debug surface, if provided.
 *
 * <p>The wrapped {@link DefaultShaderProgram} applies the {@link GlMatrixTransformation} and {@link
 * RgbMatrix} instances passed to the constructor, followed by any transformations needed to convert
 * the frames to the dimensions specified by the provided {@link SurfaceInfo}.
 *
 * <p>This wrapper is used for the final {@link DefaultShaderProgram} instance in the chain of
 * {@link DefaultShaderProgram} instances used by {@link VideoFrameProcessor}.
 */
/* package */ final class FinalShaderProgramWrapper implements GlShaderProgram, GlTextureProducer {

  interface OnInputStreamProcessedListener {
    void onInputStreamProcessed();
  }

  private static final String TAG = "FinalShaderWrapper";
  private static final int SURFACE_INPUT_CAPACITY = 1;

  // All fields but videoFrameProcessingTaskExecutor should be accessed only on the GL thread.

  private final Context context;
  private final List<GlMatrixTransformation> matrixTransformations;
  private final List<RgbMatrix> rgbMatrices;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final EGLSurface placeholderSurface;
  private final ColorInfo outputColorInfo;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final Executor videoFrameProcessorListenerExecutor;
  private final VideoFrameProcessor.Listener videoFrameProcessorListener;
  private final Queue<Pair<GlTextureInfo, Long>> availableFrames;
  private final TexturePool outputTexturePool;
  private final LongArrayQueue outputTextureTimestamps; // Synchronized with outputTexturePool.
  private final LongArrayQueue syncObjects;
  @Nullable private final GlTextureProducer.Listener textureOutputListener;
  private final @WorkingColorSpace int sdrWorkingColorSpace;
  private final boolean renderFramesAutomatically;

  private int inputWidth;
  private int inputHeight;
  @Nullable private DefaultShaderProgram defaultShaderProgram;
  // Whether the input stream has ended, but not all input has been released. This is relevant only
  // when renderFramesAutomatically is false. Ensures all frames are rendered before reporting
  // onInputStreamProcessed.
  // TODO: b/320481157 - Apply isInputStreamEnded to texture output as well.
  private boolean isInputStreamEndedWithPendingAvailableFrames;
  private InputListener inputListener;
  private @MonotonicNonNull Size outputSizeBeforeSurfaceTransformation;
  @Nullable private OnInputStreamProcessedListener onInputStreamProcessedListener;
  private boolean matrixTransformationsChanged;
  private boolean outputSurfaceInfoChanged;
  @Nullable private SurfaceInfo outputSurfaceInfo;

  /** Wraps the {@link Surface} in {@link #outputSurfaceInfo}. */
  @Nullable private EGLSurface outputEglSurface;

  public FinalShaderProgramWrapper(
      Context context,
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface placeholderSurface,
      ColorInfo outputColorInfo,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Executor videoFrameProcessorListenerExecutor,
      VideoFrameProcessor.Listener videoFrameProcessorListener,
      @Nullable Listener textureOutputListener,
      int textureOutputCapacity,
      @WorkingColorSpace int sdrWorkingColorSpace,
      boolean renderFramesAutomatically) {
    this.context = context;
    this.matrixTransformations = new ArrayList<>();
    this.rgbMatrices = new ArrayList<>();
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.placeholderSurface = placeholderSurface;
    this.outputColorInfo = outputColorInfo;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.videoFrameProcessorListenerExecutor = videoFrameProcessorListenerExecutor;
    this.videoFrameProcessorListener = videoFrameProcessorListener;
    this.textureOutputListener = textureOutputListener;
    this.sdrWorkingColorSpace = sdrWorkingColorSpace;
    this.renderFramesAutomatically = renderFramesAutomatically;

    inputListener = new InputListener() {};
    availableFrames = new ConcurrentLinkedQueue<>();

    boolean useHighPrecisionColorComponents = ColorInfo.isTransferHdr(outputColorInfo);
    outputTexturePool = new TexturePool(useHighPrecisionColorComponents, textureOutputCapacity);
    outputTextureTimestamps = new LongArrayQueue(textureOutputCapacity);
    syncObjects = new LongArrayQueue(textureOutputCapacity);
  }

  // GlTextureProducer interface. Can be called on any thread.

  @Override
  public void releaseOutputTexture(long presentationTimeUs) {
    videoFrameProcessingTaskExecutor.submit(() -> releaseOutputTextureInternal(presentationTimeUs));
  }

  private void releaseOutputTextureInternal(long presentationTimeUs) throws GlUtil.GlException {
    checkState(textureOutputListener != null);
    while (outputTexturePool.freeTextureCount() < outputTexturePool.capacity()
        && outputTextureTimestamps.element() <= presentationTimeUs) {
      outputTexturePool.freeTexture();
      outputTextureTimestamps.remove();
      GlUtil.deleteSyncObject(syncObjects.remove());
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  // GlShaderProgram interface. Must be called on the GL thread.

  @Override
  public void setInputListener(InputListener inputListener) {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    this.inputListener = inputListener;
    for (int i = 0; i < getInputCapacity(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    // The VideoFrameProcessor.Listener passed to the constructor is used for output-related events.
    throw new UnsupportedOperationException();
  }

  @Override
  public void setErrorListener(Executor executor, ErrorListener errorListener) {
    // The VideoFrameProcessor.Listener passed to the constructor is used for errors.
    throw new UnsupportedOperationException();
  }

  public void setOnInputStreamProcessedListener(
      @Nullable OnInputStreamProcessedListener onInputStreamProcessedListener) {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    this.onInputStreamProcessedListener = onInputStreamProcessedListener;
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    if (availableFrames.isEmpty()) {
      checkNotNull(onInputStreamProcessedListener).onInputStreamProcessed();
      isInputStreamEndedWithPendingAvailableFrames = false;
    } else {
      checkState(!renderFramesAutomatically);
      isInputStreamEndedWithPendingAvailableFrames = true;
    }
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    videoFrameProcessorListenerExecutor.execute(
        () -> videoFrameProcessorListener.onOutputFrameAvailableForRendering(presentationTimeUs));
    if (textureOutputListener == null) {
      if (renderFramesAutomatically) {
        renderFrame(
            glObjectsProvider,
            inputTexture,
            presentationTimeUs,
            /* renderTimeNs= */ presentationTimeUs * 1000);
      } else {
        availableFrames.add(Pair.create(inputTexture, presentationTimeUs));
      }
      inputListener.onReadyToAcceptInputFrame();
    } else {
      checkState(outputTexturePool.freeTextureCount() > 0);
      renderFrame(
          glObjectsProvider,
          inputTexture,
          presentationTimeUs,
          /* renderTimeNs= */ presentationTimeUs * 1000);
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    // FinalShaderProgramWrapper cannot release output textures using GlTextureInfo.
    throw new UnsupportedOperationException();
  }

  @Override
  public void flush() {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    // The downstream consumer must already have been flushed, so the textureOutputListener
    // implementation does not access its previously output textures, per its contract. However, the
    // downstream consumer may not have called releaseOutputTexture on all these textures. Release
    // all output textures that aren't already released.
    if (textureOutputListener != null) {
      outputTexturePool.freeAllTextures();
      outputTextureTimestamps.clear();
      syncObjects.clear();
    }

    // Drops all frames that aren't rendered yet.
    availableFrames.clear();
    isInputStreamEndedWithPendingAvailableFrames = false;
    if (defaultShaderProgram != null) {
      defaultShaderProgram.flush();
    }

    // Signal flush upstream.
    inputListener.onFlush();
    for (int i = 0; i < getInputCapacity(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    if (defaultShaderProgram != null) {
      defaultShaderProgram.release();
    }
    try {
      outputTexturePool.deleteAllTextures();
      GlUtil.destroyEglSurface(eglDisplay, outputEglSurface);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  /**
   * Sets the list of {@link GlMatrixTransformation GlMatrixTransformations} and list of {@link
   * RgbMatrix RgbMatrices} to apply to the next {@linkplain #queueInputFrame queued} frame.
   *
   * <p>The new transformations will be applied to the next {@linkplain #queueInputFrame queued}
   * frame.
   *
   * <p>Must be called on the GL thread.
   */
  public void setMatrixTransformations(
      List<GlMatrixTransformation> matrixTransformations, List<RgbMatrix> rgbMatrices) {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    this.matrixTransformations.clear();
    this.matrixTransformations.addAll(matrixTransformations);
    this.rgbMatrices.clear();
    this.rgbMatrices.addAll(rgbMatrices);
    matrixTransformationsChanged = true;
  }

  public void renderOutputFrame(GlObjectsProvider glObjectsProvider, long renderTimeNs) {
    videoFrameProcessingTaskExecutor.verifyVideoFrameProcessingThread();
    if (textureOutputListener != null) {
      return;
    }
    checkState(!renderFramesAutomatically);
    Pair<GlTextureInfo, Long> oldestAvailableFrame = availableFrames.remove();
    renderFrame(
        glObjectsProvider,
        /* inputTexture= */ oldestAvailableFrame.first,
        /* presentationTimeUs= */ oldestAvailableFrame.second,
        renderTimeNs);
    if (availableFrames.isEmpty() && isInputStreamEndedWithPendingAvailableFrames) {
      checkNotNull(onInputStreamProcessedListener).onInputStreamProcessed();
      isInputStreamEndedWithPendingAvailableFrames = false;
    }
  }

  /**
   * See {@link DefaultVideoFrameProcessor#setOutputSurfaceInfo}
   *
   * <p>Can be called on any thread.
   */
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    try {
      videoFrameProcessingTaskExecutor.invoke(
          () -> setOutputSurfaceInfoInternal(outputSurfaceInfo));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      videoFrameProcessorListenerExecutor.execute(
          () -> videoFrameProcessorListener.onError(VideoFrameProcessingException.from(e)));
    }
  }

  /** Must be called on the GL thread. */
  private void setOutputSurfaceInfoInternal(@Nullable SurfaceInfo outputSurfaceInfo) {
    if (textureOutputListener != null) {
      return;
    }
    if (Objects.equals(this.outputSurfaceInfo, outputSurfaceInfo)) {
      return;
    }

    if (this.outputSurfaceInfo != null
        && (outputSurfaceInfo == null
            || !this.outputSurfaceInfo.surface.equals(outputSurfaceInfo.surface))) {
      // Destroy outputEglSurface as soon as we lose reference to the corresponding Surface.
      // outputEglSurface is a graphics buffer producer for a BufferQueue, and
      // this.outputSurfaceInfo.surface is the associated consumer. The consumer owns the
      // BufferQueue https://source.android.com/docs/core/graphics/arch-bq-gralloc#BufferQueue.
      // If the BufferQueue is released while the producer is still alive, EGL gets stuck trying
      // to dequeue a new buffer from the released BufferQueue. This probably
      // happens when the previously queued back buffer is ready for display.
      destroyOutputEglSurface();
    }
    outputSurfaceInfoChanged =
        this.outputSurfaceInfo == null
            || outputSurfaceInfo == null
            || this.outputSurfaceInfo.width != outputSurfaceInfo.width
            || this.outputSurfaceInfo.height != outputSurfaceInfo.height
            || this.outputSurfaceInfo.orientationDegrees != outputSurfaceInfo.orientationDegrees;
    this.outputSurfaceInfo = outputSurfaceInfo;
  }

  private int getInputCapacity() {
    return textureOutputListener == null
        ? SURFACE_INPUT_CAPACITY
        : outputTexturePool.freeTextureCount();
  }

  private void destroyOutputEglSurface() {
    if (outputEglSurface == null) {
      return;
    }
    try {
      // outputEglSurface will be destroyed only if it's not current.
      // See EGL docs. Make the placeholder surface current before destroying.
      GlUtil.focusEglSurface(
          eglDisplay, eglContext, placeholderSurface, /* width= */ 1, /* height= */ 1);
      GlUtil.destroyEglSurface(eglDisplay, outputEglSurface);
    } catch (GlUtil.GlException e) {
      videoFrameProcessorListenerExecutor.execute(
          () -> videoFrameProcessorListener.onError(VideoFrameProcessingException.from(e)));
    } finally {
      this.outputEglSurface = null;
    }
  }

  private void renderFrame(
      GlObjectsProvider glObjectsProvider,
      GlTextureInfo inputTexture,
      long presentationTimeUs,
      long renderTimeNs) {
    try {
      if (renderTimeNs == VideoFrameProcessor.DROP_OUTPUT_FRAME
          || !ensureConfigured(glObjectsProvider, inputTexture.width, inputTexture.height)) {
        inputListener.onInputFrameProcessed(inputTexture);
        return; // Drop frames when requested, or there is no output surface and output texture.
      }
      if (outputSurfaceInfo != null) {
        renderFrameToOutputSurface(inputTexture, presentationTimeUs, renderTimeNs);
      } else if (textureOutputListener != null) {
        renderFrameToOutputTexture(inputTexture, presentationTimeUs);
      }
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      videoFrameProcessorListenerExecutor.execute(
          () ->
              videoFrameProcessorListener.onError(
                  VideoFrameProcessingException.from(e, presentationTimeUs)));
    }

    inputListener.onInputFrameProcessed(inputTexture);
  }

  private void renderFrameToOutputSurface(
      GlTextureInfo inputTexture, long presentationTimeUs, long renderTimeNs)
      throws VideoFrameProcessingException, GlUtil.GlException {
    EGLSurface outputEglSurface = checkNotNull(this.outputEglSurface);
    SurfaceInfo outputSurfaceInfo = checkNotNull(this.outputSurfaceInfo);
    DefaultShaderProgram defaultShaderProgram = checkNotNull(this.defaultShaderProgram);

    GlUtil.focusEglSurface(
        eglDisplay,
        eglContext,
        outputEglSurface,
        outputSurfaceInfo.width,
        outputSurfaceInfo.height);
    GlUtil.clearFocusedBuffers();
    defaultShaderProgram.drawFrame(inputTexture.texId, presentationTimeUs);

    long eglPresentationTimeNs;
    if (renderTimeNs == RENDER_OUTPUT_FRAME_IMMEDIATELY) {
      eglPresentationTimeNs = System.nanoTime();
    } else if (renderTimeNs == RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME) {
      checkState(presentationTimeUs != C.TIME_UNSET);
      eglPresentationTimeNs = presentationTimeUs * 1000;
    } else {
      eglPresentationTimeNs = renderTimeNs;
    }

    EGLExt.eglPresentationTimeANDROID(eglDisplay, outputEglSurface, eglPresentationTimeNs);
    EGL14.eglSwapBuffers(eglDisplay, outputEglSurface);
    DebugTraceUtil.logEvent(COMPONENT_VFP, EVENT_RENDERED_TO_OUTPUT_SURFACE, presentationTimeUs);
  }

  private void renderFrameToOutputTexture(GlTextureInfo inputTexture, long presentationTimeUs)
      throws GlUtil.GlException, VideoFrameProcessingException {
    GlTextureInfo outputTexture = outputTexturePool.useTexture();
    outputTextureTimestamps.add(presentationTimeUs);
    GlUtil.focusFramebufferUsingCurrentContext(
        outputTexture.fboId, outputTexture.width, outputTexture.height);
    GlUtil.clearFocusedBuffers();
    checkNotNull(defaultShaderProgram).drawFrame(inputTexture.texId, presentationTimeUs);
    long syncObject = GlUtil.createGlSyncFence();
    syncObjects.add(syncObject);
    checkNotNull(textureOutputListener)
        .onTextureRendered(
            /* textureProducer= */ this, outputTexture, presentationTimeUs, syncObject);
  }

  /**
   * Ensures the instance is configured.
   *
   * <p>Returns {@code false} if {@code outputSurfaceInfo} is unset.
   */
  private boolean ensureConfigured(
      GlObjectsProvider glObjectsProvider, int inputWidth, int inputHeight)
      throws VideoFrameProcessingException, GlUtil.GlException {
    // Clear extra or outdated resources.
    boolean inputSizeChanged =
        this.inputWidth != inputWidth
            || this.inputHeight != inputHeight
            || this.outputSizeBeforeSurfaceTransformation == null;
    if (inputSizeChanged) {
      this.inputWidth = inputWidth;
      this.inputHeight = inputHeight;
      Size outputSizeBeforeSurfaceTransformation =
          MatrixUtils.configureAndGetOutputSize(inputWidth, inputHeight, matrixTransformations);
      if (!Objects.equals(
          this.outputSizeBeforeSurfaceTransformation, outputSizeBeforeSurfaceTransformation)) {
        this.outputSizeBeforeSurfaceTransformation = outputSizeBeforeSurfaceTransformation;
        videoFrameProcessorListenerExecutor.execute(
            () ->
                videoFrameProcessorListener.onOutputSizeChanged(
                    outputSizeBeforeSurfaceTransformation.getWidth(),
                    outputSizeBeforeSurfaceTransformation.getHeight()));
      }
    }
    checkNotNull(outputSizeBeforeSurfaceTransformation);

    if (outputSurfaceInfo == null && textureOutputListener == null) {
      checkState(outputEglSurface == null);
      if (defaultShaderProgram != null) {
        defaultShaderProgram.release();
        defaultShaderProgram = null;
      }
      Log.w(TAG, "Output surface and size not set, dropping frame.");
      return false;
    }

    int outputWidth =
        outputSurfaceInfo == null
            ? outputSizeBeforeSurfaceTransformation.getWidth()
            : outputSurfaceInfo.width;
    int outputHeight =
        outputSurfaceInfo == null
            ? outputSizeBeforeSurfaceTransformation.getHeight()
            : outputSurfaceInfo.height;

    // Allocate or update resources.
    if (outputSurfaceInfo != null && outputEglSurface == null) {
      outputEglSurface =
          glObjectsProvider.createEglSurface(
              eglDisplay,
              outputSurfaceInfo.surface,
              outputColorInfo.colorTransfer,
              outputSurfaceInfo.isEncoderInputSurface);
    }
    if (textureOutputListener != null) {
      outputTexturePool.ensureConfigured(glObjectsProvider, outputWidth, outputHeight);
    }

    if (defaultShaderProgram != null
        && (outputSurfaceInfoChanged || inputSizeChanged || matrixTransformationsChanged)) {
      defaultShaderProgram.release();
      defaultShaderProgram = null;
      outputSurfaceInfoChanged = false;
      matrixTransformationsChanged = false;
    }

    if (defaultShaderProgram == null) {
      defaultShaderProgram =
          createDefaultShaderProgram(
              outputSurfaceInfo == null ? 0 : outputSurfaceInfo.orientationDegrees,
              outputWidth,
              outputHeight);
      outputSurfaceInfoChanged = false;
    }
    return true;
  }

  private DefaultShaderProgram createDefaultShaderProgram(
      int outputOrientationDegrees, int outputWidth, int outputHeight)
      throws VideoFrameProcessingException {
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<GlMatrixTransformation>().addAll(matrixTransformations);
    if (outputOrientationDegrees != 0) {
      matrixTransformationListBuilder.add(
          new ScaleAndRotateTransformation.Builder()
              .setRotationDegrees(outputOrientationDegrees)
              .build());
    }
    matrixTransformationListBuilder.add(
        Presentation.createForWidthAndHeight(
            outputWidth, outputHeight, Presentation.LAYOUT_SCALE_TO_FIT));

    DefaultShaderProgram defaultShaderProgram;
    ImmutableList<GlMatrixTransformation> expandedMatrixTransformations =
        matrixTransformationListBuilder.build();
    defaultShaderProgram =
        DefaultShaderProgram.createApplyingOetf(
            context,
            expandedMatrixTransformations,
            rgbMatrices,
            outputColorInfo,
            sdrWorkingColorSpace);

    Size outputSize = defaultShaderProgram.configure(inputWidth, inputHeight);
    if (outputSurfaceInfo != null) {
      SurfaceInfo outputSurfaceInfo = checkNotNull(this.outputSurfaceInfo);
      checkState(outputSize.getWidth() == outputSurfaceInfo.width);
      checkState(outputSize.getHeight() == outputSurfaceInfo.height);
    }
    return defaultShaderProgram;
  }
}
