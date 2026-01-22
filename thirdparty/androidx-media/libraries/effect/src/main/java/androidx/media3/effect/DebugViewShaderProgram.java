/*
 * Copyright 2025 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.GlUtil.getDefaultEglDisplay;
import static androidx.media3.effect.DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_DEFAULT;
import static androidx.media3.effect.DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_LINEAR;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * {@link GlShaderProgram} that renders to a {@link SurfaceView} provided by {@link
 * DebugViewProvider}.
 */
@UnstableApi
public final class DebugViewShaderProgram implements GlShaderProgram {
  private static final String TAG = "DebugViewShaderProgram";

  private final Context context;
  private final DebugViewProvider debugViewProvider;
  @Nullable private SurfaceView debugSurfaceView;
  @Nullable private DefaultShaderProgram defaultShaderProgram;
  @Nullable private SurfaceViewWrapper debugSurfaceViewWrapper;

  private final ColorInfo outputColorInfo;
  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private int outputWidth = C.LENGTH_UNSET;
  private int outputHeight = C.LENGTH_UNSET;

  public DebugViewShaderProgram(
      Context context, DebugViewProvider debugViewProvider, ColorInfo outputColorInfo) {
    this.context = context;
    this.debugViewProvider = debugViewProvider;
    this.outputColorInfo = outputColorInfo;
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener =
        (frameProcessingException) ->
            Log.e(TAG, "Exception caught by errorListener.", frameProcessingException);
    errorListenerExecutor = directExecutor();
  }

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor executor, ErrorListener errorListener) {
    this.errorListener = errorListener;
    this.errorListenerExecutor = executor;
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    try {
      ensureConfigured(inputTexture.width, inputTexture.height);
      DefaultShaderProgram defaultShaderProgram = checkNotNull(this.defaultShaderProgram);
      checkNotNull(this.debugSurfaceViewWrapper)
          .maybeRenderToSurfaceView(
              () -> defaultShaderProgram.drawFrame(inputTexture.texId, presentationTimeUs),
              glObjectsProvider);
      outputListener.onOutputFrameAvailable(inputTexture, presentationTimeUs);
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      errorListenerExecutor.execute(
          () -> errorListener.onError(VideoFrameProcessingException.from(e, presentationTimeUs)));
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    inputListener.onInputFrameProcessed(outputTexture);
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  public void flush() {
    if (defaultShaderProgram != null) {
      defaultShaderProgram.flush();
    }
    inputListener.onFlush();
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    if (defaultShaderProgram != null) {
      defaultShaderProgram.release();
    }
    try {
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private void ensureConfigured(int inputWidth, int inputHeight)
      throws VideoFrameProcessingException, GlUtil.GlException {
    if (eglDisplay == null) {
      eglDisplay = getDefaultEglDisplay();
    }
    EGLContext eglContext = GlUtil.getCurrentContext();
    if (outputWidth == C.LENGTH_UNSET || outputHeight == C.LENGTH_UNSET) {
      outputWidth = inputWidth;
      outputHeight = inputHeight;
    }
    @Nullable
    SurfaceView debugSurfaceView =
        debugViewProvider.getDebugPreviewSurfaceView(outputWidth, outputHeight);
    if (debugSurfaceView != null && !Objects.equals(this.debugSurfaceView, debugSurfaceView)) {
      debugSurfaceViewWrapper =
          new SurfaceViewWrapper(
              eglDisplay, eglContext, debugSurfaceView, outputColorInfo.colorTransfer);
    }
    this.debugSurfaceView = debugSurfaceView;
    if (defaultShaderProgram == null) {
      ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
          new ImmutableList.Builder<>();
      matrixTransformationListBuilder.add(
          Presentation.createForWidthAndHeight(
              outputWidth, outputHeight, Presentation.LAYOUT_SCALE_TO_FIT));

      defaultShaderProgram =
          DefaultShaderProgram.createApplyingOetf(
              context,
              /* matrixTransformations= */ matrixTransformationListBuilder.build(),
              /* rgbMatrices= */ ImmutableList.of(),
              outputColorInfo,
              outputColorInfo.colorTransfer == C.COLOR_TRANSFER_LINEAR
                  ? WORKING_COLOR_SPACE_LINEAR
                  : WORKING_COLOR_SPACE_DEFAULT);
    }
  }

  /**
   * Wrapper around a {@link SurfaceView} that keeps track of whether the output surface is valid,
   * and makes rendering a no-op if not.
   */
  private static final class SurfaceViewWrapper implements SurfaceHolder.Callback {
    public final @C.ColorTransfer int outputColorTransfer;
    private final EGLDisplay eglDisplay;
    private final EGLContext eglContext;

    @GuardedBy("this")
    @Nullable
    private Surface surface;

    @GuardedBy("this")
    @Nullable
    private EGLSurface eglSurface;

    private int width;
    private int height;

    public SurfaceViewWrapper(
        EGLDisplay eglDisplay,
        EGLContext eglContext,
        SurfaceView surfaceView,
        @C.ColorTransfer int outputColorTransfer) {
      this.eglDisplay = eglDisplay;
      this.eglContext = eglContext;
      // PQ SurfaceView output is supported from API 33, but HLG output is supported from API 34.
      // Therefore, convert HLG to PQ below API 34, so that HLG input can be displayed properly on
      // API 33.
      this.outputColorTransfer =
          outputColorTransfer == C.COLOR_TRANSFER_HLG && Util.SDK_INT < 34
              ? C.COLOR_TRANSFER_ST2084
              : outputColorTransfer;
      surfaceView.getHolder().addCallback(this);
      surface = surfaceView.getHolder().getSurface();
      width = surfaceView.getWidth();
      height = surfaceView.getHeight();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public synchronized void surfaceChanged(
        SurfaceHolder holder, int format, int width, int height) {
      this.width = width;
      this.height = height;
      Surface newSurface = holder.getSurface();
      if (!newSurface.equals(surface)) {
        surface = newSurface;
        eglSurface = null;
      }
    }

    @Override
    public synchronized void surfaceDestroyed(SurfaceHolder holder) {
      surface = null;
      eglSurface = null;
      width = C.LENGTH_UNSET;
      height = C.LENGTH_UNSET;
    }

    /**
     * Focuses the wrapped surface view's surface as an {@link EGLSurface}, renders using {@code
     * renderingTask} and swaps buffers, if the view's holder has a valid surface. Does nothing
     * otherwise.
     *
     * <p>Must be called on the GL thread.
     */
    public synchronized void maybeRenderToSurfaceView(
        VideoFrameProcessingTaskExecutor.Task renderingTask, GlObjectsProvider glObjectsProvider)
        throws GlUtil.GlException, VideoFrameProcessingException {
      if (surface == null) {
        return;
      }

      if (eglSurface == null) {
        eglSurface =
            glObjectsProvider.createEglSurface(
                eglDisplay, surface, outputColorTransfer, /* isEncoderInputSurface= */ false);
      }
      EGLSurface eglSurface = this.eglSurface;
      GlUtil.focusEglSurface(eglDisplay, eglContext, eglSurface, width, height);
      renderingTask.run();
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);
      // Prevents white flashing on the debug SurfaceView when frames are rendered too fast.
      // TODO: b/393316699 - Investigate removing this to speed up transcoding.
      GLES20.glFinish();
    }
  }
}
