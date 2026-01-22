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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.GlUtil.getDefaultEglDisplay;
import static androidx.media3.effect.DebugTraceUtil.COMPONENT_VFP;
import static androidx.media3.effect.DebugTraceUtil.EVENT_RECEIVE_END_OF_ALL_INPUT;
import static androidx.media3.effect.DebugTraceUtil.EVENT_REGISTER_NEW_INPUT_STREAM;
import static androidx.media3.effect.DebugTraceUtil.EVENT_SIGNAL_ENDED;
import static com.google.common.collect.Iterables.getFirst;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link VideoFrameProcessor} implementation that applies {@link GlEffect} instances using OpenGL
 * on a background thread.
 *
 * <p>When using surface input ({@link #INPUT_TYPE_SURFACE} or {@link
 * #INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION}) the surface's format must be supported for
 * sampling as an external texture in OpenGL. When a {@link android.media.MediaCodec} decoder is
 * writing to the input surface, the default SDR color format is supported. When an {@link
 * android.media.ImageWriter} is writing to the input surface, {@link
 * android.graphics.PixelFormat#RGBA_8888} is supported for SDR data. Support for other formats may
 * be device-dependent.
 */
@UnstableApi
public final class DefaultVideoFrameProcessor implements VideoFrameProcessor {

  static {
    MediaLibraryInfo.registerModule("media3.effect");
  }

  /**
   * Releases the output information stored for textures before and at {@code presentationTimeUs}.
   */
  public interface ReleaseOutputTextureCallback {
    void release(long presentationTimeUs);
  }

  // LINT.IfChange(working_color_space)
  /**
   * Specifies the color space that frames passed to intermediate {@link GlShaderProgram}s will be
   * represented in.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({WORKING_COLOR_SPACE_DEFAULT, WORKING_COLOR_SPACE_ORIGINAL, WORKING_COLOR_SPACE_LINEAR})
  public @interface WorkingColorSpace {}

  /**
   * Use BT709 color primaries with the standard SDR transfer function (SMPTE 170m) as the working
   * color space.
   *
   * <p>Any SDR content in a different color space will be transferred to this one.
   */
  public static final int WORKING_COLOR_SPACE_DEFAULT = 0;

  /**
   * Use the original color space of the input as the working color space when the input is SDR.
   *
   * <p>Tonemapped HDR content will be represented with BT709 color primaries and the standard SDR
   * transfer function (SMPTE 170m).
   *
   * <p>No color transfers will be applied when the input is SDR.
   */
  public static final int WORKING_COLOR_SPACE_ORIGINAL = 1;

  /**
   * The working color space will have the same primaries as the input and a linear transfer
   * function.
   *
   * <p>This option is not recommended for SDR content since it may lead to color banding since
   * 8-bit colors are used in SDR processing. It may also cause effects that modify a frame's output
   * colors (for example {@linkplain OverlayEffect overlays}) to have incorrect output colors.
   */
  public static final int WORKING_COLOR_SPACE_LINEAR = 2;

  // LINT.ThenChange(
  // ../../../../../../../effect/src/main/assets/shaders/fragment_shader_transformation_sdr_external_es2.glsl:working_color_space,
  // ../../../../../../../effect/src/main/assets/shaders/fragment_shader_transformation_sdr_internal_es2.glsl:working_color_space,
  // )

  /** A factory for {@link DefaultVideoFrameProcessor} instances. */
  public static final class Factory implements VideoFrameProcessor.Factory {
    private static final String THREAD_NAME = "Effect:DefaultVideoFrameProcessor:GlThread";

    /** A builder for {@link DefaultVideoFrameProcessor.Factory} instances. */
    public static final class Builder {
      private @WorkingColorSpace int sdrWorkingColorSpace;
      @Nullable private ExecutorService executorService;
      private @MonotonicNonNull GlObjectsProvider glObjectsProvider;
      private GlTextureProducer.@MonotonicNonNull Listener textureOutputListener;
      private int textureOutputCapacity;
      private boolean requireRegisteringAllInputFrames;
      private boolean experimentalAdjustSurfaceTextureTransformationMatrix;
      private boolean experimentalRepeatInputBitmapWithoutResampling;

      /** Creates an instance. */
      public Builder() {
        sdrWorkingColorSpace = WORKING_COLOR_SPACE_DEFAULT;
        requireRegisteringAllInputFrames = true;
        experimentalAdjustSurfaceTextureTransformationMatrix = true;
        experimentalRepeatInputBitmapWithoutResampling = true;
      }

      private Builder(Factory factory) {
        sdrWorkingColorSpace = factory.sdrWorkingColorSpace;
        executorService = factory.executorService;
        glObjectsProvider = factory.glObjectsProvider;
        textureOutputListener = factory.textureOutputListener;
        textureOutputCapacity = factory.textureOutputCapacity;
        requireRegisteringAllInputFrames = !factory.repeatLastRegisteredFrame;
        experimentalAdjustSurfaceTextureTransformationMatrix =
            factory.experimentalAdjustSurfaceTextureTransformationMatrix;
        experimentalRepeatInputBitmapWithoutResampling =
            factory.experimentalRepeatInputBitmapWithoutResampling;
      }

      /**
       * Sets the {@link WorkingColorSpace} in which frames passed to intermediate effects will be
       * represented.
       *
       * <p>The default value is {@link #WORKING_COLOR_SPACE_LINEAR}.
       *
       * <p>This setter doesn't affect the working color space for HDR output, since the working
       * color space must have a linear transfer function for HDR output.
       */
      @CanIgnoreReturnValue
      public Builder setSdrWorkingColorSpace(@WorkingColorSpace int sdrWorkingColorSpace) {
        this.sdrWorkingColorSpace = sdrWorkingColorSpace;
        return this;
      }

      /**
       * Sets whether {@link VideoFrameProcessor#registerInputFrame() registering} every input frame
       * is required.
       *
       * <p>The default value is {@code true}, meaning that all frames input to the {@link
       * VideoFrameProcessor}'s input {@link #getInputSurface Surface} must be {@linkplain
       * #registerInputFrame() registered} before they are rendered. In this mode the input format
       * change between input streams is handled frame-exactly. If {@code false}, {@link
       * #registerInputFrame} can be called only once for each {@linkplain #registerInputStream
       * registered input stream} before rendering the first frame to the input {@link
       * #getInputSurface() Surface}. The same registered {@link Format} is repeated for the
       * subsequent frames. To ensure the format change between input streams is applied on the
       * right frame, the caller needs to {@linkplain #registerInputStream register} the new input
       * stream strictly after rendering all frames from the previous input stream. This mode should
       * be used in streams where users don't have direct control over rendering frames, like in a
       * camera feed.
       *
       * <p>Regardless of the value set, {@link #registerInputStream} must be called for each input
       * stream to specify the format for upcoming frames before calling {@link
       * #registerInputFrame()}.
       *
       * @param requireRegisteringAllInputFrames Whether registering every input frame is required.
       * @deprecated For automatic frame registration ({@code
       *     setRequireRegisteringAllInputFrames(false)}), use {@link
       *     VideoFrameProcessor#INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION} instead. This call
       *     can be removed otherwise.
       */
      @Deprecated
      @CanIgnoreReturnValue
      public Builder setRequireRegisteringAllInputFrames(boolean requireRegisteringAllInputFrames) {
        this.requireRegisteringAllInputFrames = requireRegisteringAllInputFrames;
        return this;
      }

      /**
       * Sets the {@link GlObjectsProvider}.
       *
       * <p>The default value is a {@link DefaultGlObjectsProvider}.
       *
       * <p>If both the {@link GlObjectsProvider} and the {@link ExecutorService} are set, it's the
       * caller's responsibility to release the {@link GlObjectsProvider} on the {@link
       * ExecutorService}'s thread.
       */
      @CanIgnoreReturnValue
      public Builder setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
        this.glObjectsProvider = glObjectsProvider;
        return this;
      }

      /**
       * Sets the {@link Util#newSingleThreadScheduledExecutor} to execute GL commands from.
       *
       * <p>If set to a non-null value, the {@link ExecutorService} must be {@linkplain
       * ExecutorService#shutdown shut down} by the caller after all {@linkplain VideoFrameProcessor
       * VideoFrameProcessors} using it have been {@linkplain #release released}.
       *
       * <p>The default value is a new {@link Util#newSingleThreadScheduledExecutor}, owned and
       * {@link ExecutorService#shutdown} by the created {@link DefaultVideoFrameProcessor}. Setting
       * a {@code null} {@link ExecutorService} is equivalent to using the default value.
       *
       * @param executorService The {@link ExecutorService}.
       */
      @CanIgnoreReturnValue
      public Builder setExecutorService(@Nullable ExecutorService executorService) {
        this.executorService = executorService;
        return this;
      }

      /**
       * Sets texture output settings.
       *
       * <p>If set, the {@link VideoFrameProcessor} will output to OpenGL textures, accessible via
       * {@link GlTextureProducer.Listener#onTextureRendered}. Textures will stop being outputted
       * when the number of output textures available reaches the {@code textureOutputCapacity}. To
       * regain capacity, output textures must be released using {@link
       * ReleaseOutputTextureCallback}.
       *
       * <p>If set, {@linkplain #setOutputSurfaceInfo} and {@link #renderOutputFrame} will be
       * no-ops, and {@code renderFramesAutomatically} will behave as if it is set to {@code true}.
       *
       * <p>If not set, there will be no texture output.
       *
       * @param textureOutputListener The {@link GlTextureProducer.Listener}.
       * @param textureOutputCapacity The amount of output textures that may be allocated at a time
       *     before texture output blocks. Must be greater than or equal to 1.
       */
      @CanIgnoreReturnValue
      public Builder setTextureOutput(
          GlTextureProducer.Listener textureOutputListener,
          @IntRange(from = 1) int textureOutputCapacity) {
        this.textureOutputListener = textureOutputListener;
        checkArgument(textureOutputCapacity >= 1);
        this.textureOutputCapacity = textureOutputCapacity;
        return this;
      }

      /**
       * Sets whether the {@link SurfaceTexture#getTransformMatrix(float[])} is adjusted to remove
       * the scale that cuts off a 1- or 2-texel border around the edge of a crop.
       *
       * <p>When set, programs sampling GL_TEXTURE_EXTERNAL_OES from {@link SurfaceTexture} must not
       * attempt to access data in any cropped region, including via GL_LINEAR resampling filter.
       *
       * <p>Defaults to {@code true}.
       *
       * @deprecated This experimental method will be removed in a future release.
       */
      @CanIgnoreReturnValue
      @Deprecated
      public Builder setExperimentalAdjustSurfaceTextureTransformationMatrix(
          boolean experimentalAdjustSurfaceTextureTransformationMatrix) {
        this.experimentalAdjustSurfaceTextureTransformationMatrix =
            experimentalAdjustSurfaceTextureTransformationMatrix;
        return this;
      }

      /**
       * Sets whether {@link BitmapTextureManager} will sample from the input bitmap only once for a
       * sequence of output frames.
       *
       * <p>Defaults to {@code true}. That is, each output frame will sample from the full
       * resolution input bitmap.
       *
       * @deprecated This experimental method will be removed in a future release.
       */
      @CanIgnoreReturnValue
      @Deprecated
      public Builder setExperimentalRepeatInputBitmapWithoutResampling(
          boolean experimentalRepeatInputBitmapWithoutResampling) {
        this.experimentalRepeatInputBitmapWithoutResampling =
            experimentalRepeatInputBitmapWithoutResampling;
        return this;
      }

      /** Builds an {@link DefaultVideoFrameProcessor.Factory} instance. */
      public DefaultVideoFrameProcessor.Factory build() {
        return new DefaultVideoFrameProcessor.Factory(
            sdrWorkingColorSpace,
            /* repeatLastRegisteredFrame= */ !requireRegisteringAllInputFrames,
            glObjectsProvider,
            executorService,
            textureOutputListener,
            textureOutputCapacity,
            experimentalAdjustSurfaceTextureTransformationMatrix,
            experimentalRepeatInputBitmapWithoutResampling);
      }
    }

    private final @WorkingColorSpace int sdrWorkingColorSpace;
    private final boolean repeatLastRegisteredFrame;
    @Nullable private final GlObjectsProvider glObjectsProvider;
    @Nullable private final ExecutorService executorService;
    @Nullable private final GlTextureProducer.Listener textureOutputListener;
    private final int textureOutputCapacity;
    private final boolean experimentalAdjustSurfaceTextureTransformationMatrix;
    private final boolean experimentalRepeatInputBitmapWithoutResampling;

    private Factory(
        @WorkingColorSpace int sdrWorkingColorSpace,
        boolean repeatLastRegisteredFrame,
        @Nullable GlObjectsProvider glObjectsProvider,
        @Nullable ExecutorService executorService,
        @Nullable GlTextureProducer.Listener textureOutputListener,
        int textureOutputCapacity,
        boolean experimentalAdjustSurfaceTextureTransformationMatrix,
        boolean experimentalRepeatInputBitmapWithoutResampling) {
      this.sdrWorkingColorSpace = sdrWorkingColorSpace;
      this.repeatLastRegisteredFrame = repeatLastRegisteredFrame;
      this.glObjectsProvider = glObjectsProvider;
      this.executorService = executorService;
      this.textureOutputListener = textureOutputListener;
      this.textureOutputCapacity = textureOutputCapacity;
      this.experimentalAdjustSurfaceTextureTransformationMatrix =
          experimentalAdjustSurfaceTextureTransformationMatrix;
      this.experimentalRepeatInputBitmapWithoutResampling =
          experimentalRepeatInputBitmapWithoutResampling;
    }

    public Builder buildUpon() {
      return new Builder(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Using HDR {@code outputColorInfo} requires OpenGL ES 3.0.
     *
     * <p>If outputting HDR content to a display, {@code EGL_GL_COLORSPACE_BT2020_PQ_EXT} or {@code
     * EGL_GL_COLORSPACE_BT2020_HLG_EXT} is required.
     *
     * <p>{@code outputColorInfo}'s {@link ColorInfo#colorRange} values are currently ignored, in
     * favor of {@link C#COLOR_RANGE_FULL}.
     *
     * <p>If {@code outputColorInfo} {@linkplain ColorInfo#isTransferHdr is HDR}, the context will
     * be configured with {@link GlUtil#EGL_CONFIG_ATTRIBUTES_RGBA_1010102}. Otherwise, the context
     * will be configured with {@link GlUtil#EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
     *
     * <p>If invoking the {@code listener} on {@link DefaultVideoFrameProcessor}'s internal thread
     * is desired, pass a {@link MoreExecutors#directExecutor() direct listenerExecutor}.
     *
     * <p>If {@linkplain Factory.Builder#setTextureOutput texture output} is set, {@linkplain
     * #setOutputSurfaceInfo} and {@link #renderOutputFrame} will be no-ops, and {@code
     * renderFramesAutomatically} will behave as if it is set to {@code true}.
     */
    @Override
    public DefaultVideoFrameProcessor create(
        Context context,
        DebugViewProvider debugViewProvider,
        ColorInfo outputColorInfo,
        boolean renderFramesAutomatically,
        Executor listenerExecutor,
        VideoFrameProcessor.Listener listener)
        throws VideoFrameProcessingException {
      // TODO(b/261188041) Add tests to verify the Listener is invoked on the given Executor.

      ExecutorService instanceExecutorService =
          executorService == null ? Util.newSingleThreadExecutor(THREAD_NAME) : executorService;
      boolean shouldShutdownExecutorService = executorService == null;
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor =
          new VideoFrameProcessingTaskExecutor(
              instanceExecutorService, shouldShutdownExecutorService, listener::onError);

      boolean shouldReleaseGlObjectsProvider = glObjectsProvider == null || executorService == null;
      GlObjectsProvider instanceGlObjectsProvider =
          glObjectsProvider == null ? new DefaultGlObjectsProvider() : glObjectsProvider;

      Future<DefaultVideoFrameProcessor> defaultVideoFrameProcessorFuture =
          instanceExecutorService.submit(
              () ->
                  createOpenGlObjectsAndFrameProcessor(
                      context,
                      debugViewProvider,
                      outputColorInfo,
                      sdrWorkingColorSpace,
                      renderFramesAutomatically,
                      videoFrameProcessingTaskExecutor,
                      listenerExecutor,
                      listener,
                      instanceGlObjectsProvider,
                      shouldReleaseGlObjectsProvider,
                      textureOutputListener,
                      textureOutputCapacity,
                      repeatLastRegisteredFrame,
                      experimentalAdjustSurfaceTextureTransformationMatrix,
                      experimentalRepeatInputBitmapWithoutResampling));

      try {
        return defaultVideoFrameProcessorFuture.get();
      } catch (ExecutionException e) {
        throw new VideoFrameProcessingException(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new VideoFrameProcessingException(e);
      }
    }
  }

  private static final String TAG = "DefaultFrameProcessor";

  private final Context context;
  private final GlObjectsProvider glObjectsProvider;
  private final boolean shouldReleaseGlObjectsProvider;
  private final EGLDisplay eglDisplay;
  private final InputSwitcher inputSwitcher;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final VideoFrameProcessor.Listener listener;
  private final Executor listenerExecutor;
  private final boolean renderFramesAutomatically;
  private final FinalShaderProgramWrapper finalShaderProgramWrapper;

  // Shader programs that apply Effects.
  private final List<GlShaderProgram> intermediateGlShaderPrograms;
  private final ConditionVariable inputStreamRegisteredCondition;

  private @MonotonicNonNull InputStreamInfo currentInputStreamInfo;

  /**
   * The input stream that is {@linkplain #registerInputStream registered}, but the pipeline has not
   * adapted to processing it.
   */
  @GuardedBy("lock")
  @Nullable
  private InputStreamInfo pendingInputStreamInfo;

  @GuardedBy("lock")
  private boolean registeredFirstInputStream;

  @GuardedBy("lock")
  @Nullable
  private Runnable onInputSurfaceReadyListener;

  private final List<Effect> activeEffects;
  private final Object lock;
  private final ColorInfo outputColorInfo;
  private final DebugViewProvider debugViewProvider;

  private volatile @MonotonicNonNull FrameInfo nextInputFrameInfo;
  private volatile boolean inputStreamEnded;

  private DefaultVideoFrameProcessor(
      Context context,
      GlObjectsProvider glObjectsProvider,
      boolean shouldReleaseGlObjectsProvider,
      EGLDisplay eglDisplay,
      InputSwitcher inputSwitcher,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Listener listener,
      Executor listenerExecutor,
      FinalShaderProgramWrapper finalShaderProgramWrapper,
      boolean renderFramesAutomatically,
      ColorInfo outputColorInfo,
      DebugViewProvider debugViewProvider) {
    this.context = context;
    this.glObjectsProvider = glObjectsProvider;
    this.shouldReleaseGlObjectsProvider = shouldReleaseGlObjectsProvider;
    this.eglDisplay = eglDisplay;
    this.inputSwitcher = inputSwitcher;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.listener = listener;
    this.listenerExecutor = listenerExecutor;
    this.renderFramesAutomatically = renderFramesAutomatically;
    this.activeEffects = new ArrayList<>();
    this.lock = new Object();
    this.outputColorInfo = outputColorInfo;
    this.debugViewProvider = debugViewProvider;
    this.finalShaderProgramWrapper = finalShaderProgramWrapper;
    this.intermediateGlShaderPrograms = new ArrayList<>();
    this.inputStreamRegisteredCondition = new ConditionVariable();
    inputStreamRegisteredCondition.open();
    this.finalShaderProgramWrapper.setOnInputStreamProcessedListener(
        () -> {
          if (inputStreamEnded) {
            listenerExecutor.execute(listener::onEnded);
            DebugTraceUtil.logEvent(COMPONENT_VFP, EVENT_SIGNAL_ENDED, C.TIME_END_OF_SOURCE);
          } else {
            submitPendingInputStream();
          }
        });
  }

  /** Returns the task executor that runs video frame processing tasks. */
  @VisibleForTesting
  public VideoFrameProcessingTaskExecutor getTaskExecutor() {
    return videoFrameProcessingTaskExecutor;
  }

  /**
   * Sets the default size for input buffers, for the case where the producer providing input does
   * not override the buffer size.
   *
   * <p>When input comes from a media codec it's not necessary to call this method because the codec
   * (producer) sets the buffer size automatically. For the case where input comes from CameraX,
   * call this method after instantiation to ensure that buffers are handled at full resolution. See
   * {@link SurfaceTexture#setDefaultBufferSize(int, int)} for more information.
   *
   * <p>This method must only be called when the {@link VideoFrameProcessor} is {@linkplain
   * VideoFrameProcessor.Factory#create created} with {@link #INPUT_TYPE_SURFACE}.
   *
   * @param width The default width for input buffers, in pixels.
   * @param height The default height for input buffers, in pixels.
   * @deprecated Set the input type to {@link
   *     VideoFrameProcessor#INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION} instead, which sets
   *     the default buffer size automatically based on the registered frame info.
   */
  @Deprecated
  public void setInputDefaultBufferSize(int width, int height) {
    inputSwitcher.setInputDefaultBufferSize(width, height);
  }

  @Override
  public boolean queueInputBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator) {
    checkState(!inputStreamEnded);
    if (!inputStreamRegisteredCondition.isOpen()) {
      return false;
    }
    if (ColorInfo.isTransferHdr(outputColorInfo)) {
      checkArgument(
          Util.SDK_INT >= 34 && inputBitmap.hasGainmap(),
          "VideoFrameProcessor configured for HDR output, but either received SDR input, or is on"
              + " an API level that doesn't support gainmaps. SDR to HDR tonemapping is not"
              + " supported.");
    }
    FrameInfo frameInfo = checkNotNull(this.nextInputFrameInfo);
    inputSwitcher
        .activeTextureManager()
        .queueInputBitmap(inputBitmap, frameInfo, timestampIterator);
    return true;
  }

  @Override
  public boolean queueInputTexture(int textureId, long presentationTimeUs) {
    checkState(!inputStreamEnded);
    if (!inputStreamRegisteredCondition.isOpen()) {
      return false;
    }

    inputSwitcher.activeTextureManager().queueInputTexture(textureId, presentationTimeUs);
    return true;
  }

  @Override
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    inputSwitcher.setOnInputFrameProcessedListener(listener);
  }

  @Override
  public void setOnInputSurfaceReadyListener(Runnable listener) {
    synchronized (lock) {
      if (inputStreamRegisteredCondition.isOpen()) {
        listener.run();
      } else {
        onInputSurfaceReadyListener = listener;
      }
    }
  }

  @Override
  public Surface getInputSurface() {
    return inputSwitcher.getInputSurface();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Using HDR {@link Format#colorInfo} requires OpenGL ES 3.0 and the {@code EXT_YUV_target}
   * OpenGL extension.
   *
   * <p>{@link Effect}s are applied on {@link C#COLOR_RANGE_FULL} colors with {@code null} {@link
   * ColorInfo#hdrStaticInfo}.
   *
   * <p>If either {@link Format#colorInfo} or {@code outputColorInfo} {@linkplain
   * ColorInfo#isTransferHdr} are HDR}, textures will use {@link GLES30#GL_RGBA16F} and {@link
   * GLES30#GL_HALF_FLOAT}. Otherwise, textures will use {@link GLES20#GL_RGBA} and {@link
   * GLES20#GL_UNSIGNED_BYTE}.
   *
   * <p>If {@linkplain Format#colorInfo input color} {@linkplain ColorInfo#isTransferHdr is HDR},
   * but {@code outputColorInfo} is SDR, then HDR to SDR tone-mapping is applied, and {@code
   * outputColorInfo}'s {@link ColorInfo#colorTransfer} must be {@link C#COLOR_TRANSFER_GAMMA_2_2}
   * or {@link C#COLOR_TRANSFER_SDR}. In this case, the actual output transfer function will be in
   * {@link C#COLOR_TRANSFER_GAMMA_2_2}, for consistency with other tone-mapping and color behavior
   * in the Android ecosystem (for example, MediaFormat's COLOR_TRANSFER_SDR_VIDEO is defined as
   * SMPTE 170M, but most OEMs process it as Gamma 2.2).
   */
  @Override
  public void registerInputStream(
      @InputType int inputType, Format format, List<Effect> effects, long offsetToAddUs) {
    // This method is only called after all samples in the current input stream are registered or
    // queued.
    DebugTraceUtil.logEvent(
        COMPONENT_VFP,
        EVENT_REGISTER_NEW_INPUT_STREAM,
        /* presentationTimeUs= */ offsetToAddUs,
        /* extraFormat= */ "InputType %s - %dx%d",
        /* extraArgs...= */ getInputTypeString(inputType),
        format.width,
        format.height);
    Format nextFormat = adjustForPixelWidthHeightRatio(format);
    nextInputFrameInfo = new FrameInfo(nextFormat, offsetToAddUs);

    try {
      // Blocks until the previous input stream registration completes.
      // TODO: b/296897956 - Handle multiple thread unblocking at the same time.
      inputStreamRegisteredCondition.block();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      listenerExecutor.execute(() -> listener.onError(VideoFrameProcessingException.from(e)));
    }

    synchronized (lock) {
      // An input stream is pending until its effects are configured.
      InputStreamInfo pendingInputStreamInfo =
          new InputStreamInfo(inputType, format, effects, offsetToAddUs);
      if (!registeredFirstInputStream) {
        registeredFirstInputStream = true;
        inputStreamRegisteredCondition.close();
        videoFrameProcessingTaskExecutor.submit(
            () -> configure(pendingInputStreamInfo, /* forceReconfigure= */ true));
      } else {
        // Rejects further inputs after signaling EOS and before the next input stream is fully
        // configured.
        this.pendingInputStreamInfo = pendingInputStreamInfo;
        inputStreamRegisteredCondition.close();
        inputSwitcher.signalEndOfCurrentInputStream();
      }
    }
  }

  @Override
  public boolean registerInputFrame() {
    checkState(!inputStreamEnded);
    checkStateNotNull(
        nextInputFrameInfo, "registerInputStream must be called before registering input frames");
    if (!inputStreamRegisteredCondition.isOpen()) {
      return false;
    }
    inputSwitcher.activeTextureManager().registerInputFrame(nextInputFrameInfo);
    return true;
  }

  @Override
  public int getPendingInputFrameCount() {
    if (inputSwitcher.hasActiveInput()) {
      return inputSwitcher.activeTextureManager().getPendingFrameCount();
    }
    // Return zero when InputSwitcher is not set up, i.e. before VideoFrameProcessor finishes its
    // first configuration.
    return 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>If {@linkplain Factory.Builder#setTextureOutput texture output} is set, calling this method
   * will be a no-op.
   */
  @Override
  public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
    finalShaderProgramWrapper.setOutputSurfaceInfo(outputSurfaceInfo);
  }

  /**
   * {@inheritDoc}
   *
   * <p>If {@linkplain Factory.Builder#setTextureOutput texture output} is set, calling this method
   * will be a no-op.
   */
  @Override
  public void renderOutputFrame(long renderTimeNs) {
    checkState(
        !renderFramesAutomatically,
        "Calling this method is not allowed when renderFramesAutomatically is enabled");
    videoFrameProcessingTaskExecutor.submitWithHighPriority(
        () -> finalShaderProgramWrapper.renderOutputFrame(glObjectsProvider, renderTimeNs));
  }

  @Override
  public void signalEndOfInput() {
    DebugTraceUtil.logEvent(COMPONENT_VFP, EVENT_RECEIVE_END_OF_ALL_INPUT, C.TIME_END_OF_SOURCE);
    checkState(!inputStreamEnded);
    inputStreamEnded = true;
    inputSwitcher.signalEndOfCurrentInputStream();
  }

  /**
   * {@inheritDoc}
   *
   * <p>The downstream frame consumer must be flushed before this instance is flushed, and stop
   * accepting input until this DefaultVideoFrameProcessor instance finishes flushing.
   *
   * <p>After this method is called, any object consuming {@linkplain
   * Factory.Builder#setTextureOutput texture output} must not access any output textures that were
   * {@link GlTextureProducer.Listener#onTextureRendered rendered} before calling this method.
   */
  @Override
  public void flush() {
    if (!inputSwitcher.hasActiveInput()) {
      return;
    }
    inputStreamEnded = false;
    try {
      TextureManager textureManager = inputSwitcher.activeTextureManager();
      textureManager.dropIncomingRegisteredFrames();
      // Flush pending tasks to prevent any operation to be executed on the frames being processed
      // before the flush operation.
      videoFrameProcessingTaskExecutor.flush();
      textureManager.releaseAllRegisteredFrames();
      CountDownLatch latch = new CountDownLatch(1);
      textureManager.setOnFlushCompleteListener(latch::countDown);
      // Flush from the end of the GlShaderProgram pipeline up to the start.
      videoFrameProcessingTaskExecutor.submit(finalShaderProgramWrapper::flush);
      latch.await();
      textureManager.setOnFlushCompleteListener(null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // Make sure any pending input stream is not swallowed.
    submitPendingInputStream();
  }

  @Override
  public void release() {
    try {
      videoFrameProcessingTaskExecutor.release(/* releaseTask= */ this::releaseGlObjects);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /**
   * Expands the frame based on the {@link Format#pixelWidthHeightRatio} and returns a new {@link
   * Format} instance with scaled dimensions and {@link Format#pixelWidthHeightRatio} of {@code 1}.
   */
  private Format adjustForPixelWidthHeightRatio(Format format) {
    if (format.pixelWidthHeightRatio > 1f) {
      return format
          .buildUpon()
          .setWidth((int) (format.width * format.pixelWidthHeightRatio))
          .setPixelWidthHeightRatio(1)
          .build();
    } else if (format.pixelWidthHeightRatio < 1f) {
      return format
          .buildUpon()
          .setHeight((int) (format.height / format.pixelWidthHeightRatio))
          .setPixelWidthHeightRatio(1)
          .build();
    } else {
      return format;
    }
  }

  private void submitPendingInputStream() {
    synchronized (lock) {
      if (pendingInputStreamInfo != null) {
        InputStreamInfo pendingInputStreamInfo = this.pendingInputStreamInfo;
        videoFrameProcessingTaskExecutor.submit(
            () -> configure(pendingInputStreamInfo, /* forceReconfigure= */ false));
        this.pendingInputStreamInfo = null;
      }
    }
  }

  // Methods that must be called on the GL thread.

  /**
   * Creates the OpenGL context, surfaces, textures, and frame buffers, initializes {@link
   * GlShaderProgram} instances corresponding to the {@link GlEffect} instances, and returns a new
   * {@code DefaultVideoFrameProcessor}.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * <p>This method must be called on the {@link Factory.Builder#setExecutorService}, as later
   * OpenGL commands will be called on that thread.
   */
  private static DefaultVideoFrameProcessor createOpenGlObjectsAndFrameProcessor(
      Context context,
      DebugViewProvider debugViewProvider,
      ColorInfo outputColorInfo,
      @WorkingColorSpace int sdrWorkingColorSpace,
      boolean renderFramesAutomatically,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Executor videoFrameProcessorListenerExecutor,
      Listener listener,
      GlObjectsProvider glObjectsProvider,
      boolean shouldReleaseGlObjectsProvider,
      @Nullable GlTextureProducer.Listener textureOutputListener,
      int textureOutputCapacity,
      boolean repeatLastRegisteredFrame,
      boolean experimentalAdjustSurfaceTextureTransformationMatrix,
      boolean experimentalRepeatInputBitmapWithoutResampling)
      throws GlUtil.GlException, VideoFrameProcessingException {
    EGLDisplay eglDisplay = getDefaultEglDisplay();
    int[] configAttributes =
        ColorInfo.isTransferHdr(outputColorInfo)
            ? GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_1010102
            : GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888;
    Pair<EGLContext, EGLSurface> eglContextAndPlaceholderSurface =
        createFocusedEglContextWithFallback(glObjectsProvider, eglDisplay, configAttributes);

    ColorInfo linearColorInfo =
        outputColorInfo
            .buildUpon()
            .setColorTransfer(C.COLOR_TRANSFER_LINEAR)
            .setHdrStaticInfo(null)
            .build();
    ColorInfo intermediateColorInfo =
        ColorInfo.isTransferHdr(outputColorInfo)
            ? linearColorInfo
            : sdrWorkingColorSpace == WORKING_COLOR_SPACE_LINEAR
                ? linearColorInfo
                : outputColorInfo;
    InputSwitcher inputSwitcher =
        new InputSwitcher(
            context,
            /* outputColorInfo= */ intermediateColorInfo,
            glObjectsProvider,
            videoFrameProcessingTaskExecutor,
            /* errorListenerExecutor= */ videoFrameProcessorListenerExecutor,
            /* samplingShaderProgramErrorListener= */ listener::onError,
            sdrWorkingColorSpace,
            repeatLastRegisteredFrame,
            experimentalAdjustSurfaceTextureTransformationMatrix,
            experimentalRepeatInputBitmapWithoutResampling);

    FinalShaderProgramWrapper finalShaderProgramWrapper =
        new FinalShaderProgramWrapper(
            context,
            eglDisplay,
            eglContextAndPlaceholderSurface.first,
            eglContextAndPlaceholderSurface.second,
            outputColorInfo,
            videoFrameProcessingTaskExecutor,
            videoFrameProcessorListenerExecutor,
            listener,
            textureOutputListener,
            textureOutputCapacity,
            sdrWorkingColorSpace,
            renderFramesAutomatically);

    return new DefaultVideoFrameProcessor(
        context,
        glObjectsProvider,
        shouldReleaseGlObjectsProvider,
        eglDisplay,
        inputSwitcher,
        videoFrameProcessingTaskExecutor,
        listener,
        videoFrameProcessorListenerExecutor,
        finalShaderProgramWrapper,
        renderFramesAutomatically,
        outputColorInfo,
        debugViewProvider);
  }

  /**
   * Combines consecutive {@link GlMatrixTransformation GlMatrixTransformations} and {@link
   * RgbMatrix RgbMatrices} instances into a single {@link DefaultShaderProgram} and converts all
   * other {@link GlEffect} instances to separate {@link GlShaderProgram} instances.
   *
   * <p>All {@link Effect} instances must be {@link GlEffect} instances.
   *
   * @param context The {@link Context}.
   * @param effects The list of {@link GlEffect effects}.
   * @param outputColorInfo The {@link ColorInfo} on {@code DefaultVideoFrameProcessor} output.
   * @param finalShaderProgramWrapper The {@link FinalShaderProgramWrapper} to apply the {@link
   *     GlMatrixTransformation GlMatrixTransformations} and {@link RgbMatrix RgbMatrices} after all
   *     other {@link GlEffect GlEffects}.
   * @return A non-empty list of {@link GlShaderProgram} instances to apply in the given order.
   */
  private static ImmutableList<GlShaderProgram> createGlShaderPrograms(
      Context context,
      List<Effect> effects,
      ColorInfo outputColorInfo,
      FinalShaderProgramWrapper finalShaderProgramWrapper)
      throws VideoFrameProcessingException {
    ImmutableList.Builder<GlShaderProgram> shaderProgramListBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<GlMatrixTransformation> matrixTransformationListBuilder =
        new ImmutableList.Builder<>();
    ImmutableList.Builder<RgbMatrix> rgbMatrixListBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < effects.size(); i++) {
      Effect effect = effects.get(i);
      checkArgument(
          effect instanceof GlEffect, "DefaultVideoFrameProcessor only supports GlEffects");
      GlEffect glEffect = (GlEffect) effect;
      // The following logic may change the order of the RgbMatrix and GlMatrixTransformation
      // effects. This does not influence the output since RgbMatrix only changes the individual
      // pixels and does not take any location in account, which the GlMatrixTransformation
      // may change.
      if (glEffect instanceof GlMatrixTransformation) {
        matrixTransformationListBuilder.add((GlMatrixTransformation) glEffect);
        continue;
      }
      if (glEffect instanceof RgbMatrix) {
        rgbMatrixListBuilder.add((RgbMatrix) glEffect);
        continue;
      }
      boolean isOutputTransferHdr = ColorInfo.isTransferHdr(outputColorInfo);
      ImmutableList<GlMatrixTransformation> matrixTransformations =
          matrixTransformationListBuilder.build();
      ImmutableList<RgbMatrix> rgbMatrices = rgbMatrixListBuilder.build();
      if (!matrixTransformations.isEmpty() || !rgbMatrices.isEmpty()) {
        DefaultShaderProgram defaultShaderProgram =
            DefaultShaderProgram.create(
                context, matrixTransformations, rgbMatrices, isOutputTransferHdr);
        shaderProgramListBuilder.add(defaultShaderProgram);
        matrixTransformationListBuilder = new ImmutableList.Builder<>();
        rgbMatrixListBuilder = new ImmutableList.Builder<>();
      }
      shaderProgramListBuilder.add(glEffect.toGlShaderProgram(context, isOutputTransferHdr));
    }

    finalShaderProgramWrapper.setMatrixTransformations(
        matrixTransformationListBuilder.build(), rgbMatrixListBuilder.build());
    return shaderProgramListBuilder.build();
  }

  /**
   * Chains the given {@link GlShaderProgram} instances using {@link
   * ChainingGlShaderProgramListener} instances.
   */
  private static void chainShaderProgramsWithListeners(
      GlObjectsProvider glObjectsProvider,
      List<GlShaderProgram> shaderPrograms,
      FinalShaderProgramWrapper finalShaderProgramWrapper,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      VideoFrameProcessor.Listener videoFrameProcessorListener,
      Executor videoFrameProcessorListenerExecutor) {
    ArrayList<GlShaderProgram> shaderProgramsToChain = new ArrayList<>(shaderPrograms);
    shaderProgramsToChain.add(finalShaderProgramWrapper);
    for (int i = 0; i < shaderProgramsToChain.size() - 1; i++) {
      GlShaderProgram producingGlShaderProgram = shaderProgramsToChain.get(i);
      GlShaderProgram consumingGlShaderProgram = shaderProgramsToChain.get(i + 1);
      ChainingGlShaderProgramListener chainingGlShaderProgramListener =
          new ChainingGlShaderProgramListener(
              glObjectsProvider,
              producingGlShaderProgram,
              consumingGlShaderProgram,
              videoFrameProcessingTaskExecutor);
      producingGlShaderProgram.setOutputListener(chainingGlShaderProgramListener);
      producingGlShaderProgram.setErrorListener(
          videoFrameProcessorListenerExecutor, videoFrameProcessorListener::onError);
      consumingGlShaderProgram.setInputListener(chainingGlShaderProgramListener);
    }
  }

  private static String getInputTypeString(@InputType int inputType) {
    switch (inputType) {
      case INPUT_TYPE_SURFACE:
        return "Surface";
      case INPUT_TYPE_BITMAP:
        return "Bitmap";
      case INPUT_TYPE_TEXTURE_ID:
        return "Texture ID";
      case INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION:
        return "Surface with automatic frame registration";
      default:
        throw new IllegalArgumentException(String.valueOf(inputType));
    }
  }

  /**
   * Configures for a new input stream.
   *
   * <p>The effect pipeline will only re-configure if the {@link InputStreamInfo#effects new
   * effects} don't match the {@link #activeEffects}, or when {@code forceReconfigure} is set to
   * {@code true}.
   */
  private void configure(InputStreamInfo inputStreamInfo, boolean forceReconfigure)
      throws VideoFrameProcessingException {
    checkColors(
        /* inputColorInfo= */ checkNotNull(inputStreamInfo.format.colorInfo), outputColorInfo);

    if (forceReconfigure || !activeEffects.equals(inputStreamInfo.effects)) {
      if (!intermediateGlShaderPrograms.isEmpty()) {
        for (int i = 0; i < intermediateGlShaderPrograms.size(); i++) {
          intermediateGlShaderPrograms.get(i).release();
        }
        intermediateGlShaderPrograms.clear();
      }

      ImmutableList.Builder<Effect> effectsListBuilder =
          new ImmutableList.Builder<Effect>().addAll(inputStreamInfo.effects);
      if (debugViewProvider != DebugViewProvider.NONE) {
        effectsListBuilder.add(new DebugViewEffect(debugViewProvider, outputColorInfo));
      }
      // The GlShaderPrograms that should be inserted in between InputSwitcher and
      // FinalShaderProgramWrapper.
      intermediateGlShaderPrograms.addAll(
          createGlShaderPrograms(
              context, effectsListBuilder.build(), outputColorInfo, finalShaderProgramWrapper));
      inputSwitcher.setDownstreamShaderProgram(
          getFirst(intermediateGlShaderPrograms, /* defaultValue= */ finalShaderProgramWrapper));
      chainShaderProgramsWithListeners(
          glObjectsProvider,
          intermediateGlShaderPrograms,
          finalShaderProgramWrapper,
          videoFrameProcessingTaskExecutor,
          listener,
          listenerExecutor);

      activeEffects.clear();
      activeEffects.addAll(inputStreamInfo.effects);
    }

    inputSwitcher.switchToInput(
        inputStreamInfo.inputType,
        new FrameInfo(inputStreamInfo.format, inputStreamInfo.offsetToAddUs));
    inputStreamRegisteredCondition.open();
    synchronized (lock) {
      if (onInputSurfaceReadyListener != null) {
        onInputSurfaceReadyListener.run();
        onInputSurfaceReadyListener = null;
      }
    }

    listenerExecutor.execute(
        () ->
            listener.onInputStreamRegistered(
                inputStreamInfo.inputType, inputStreamInfo.format, inputStreamInfo.effects));
    if (currentInputStreamInfo == null
        || inputStreamInfo.format.frameRate != currentInputStreamInfo.format.frameRate) {
      listenerExecutor.execute(
          () -> listener.onOutputFrameRateChanged(inputStreamInfo.format.frameRate));
    }
    this.currentInputStreamInfo = inputStreamInfo;
  }

  /** Checks that color configuration is valid for {@link DefaultVideoFrameProcessor}. */
  private static void checkColors(ColorInfo inputColorInfo, ColorInfo outputColorInfo)
      throws VideoFrameProcessingException {
    if (ColorInfo.isTransferHdr(inputColorInfo)) {
      checkArgument(inputColorInfo.colorSpace == C.COLOR_SPACE_BT2020);
    }
    if ((ColorInfo.isTransferHdr(inputColorInfo) || ColorInfo.isTransferHdr(outputColorInfo))) {
      long glVersion;
      try {
        glVersion = GlUtil.getContextMajorVersion();
      } catch (GlUtil.GlException e) {
        throw VideoFrameProcessingException.from(e);
      }
      if (glVersion != 3) {
        throw new VideoFrameProcessingException(
            "OpenGL ES 3.0 context support is required for HDR input or output.");
      }
    }

    checkArgument(inputColorInfo.isDataSpaceValid());
    checkArgument(inputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);
    checkArgument(outputColorInfo.isDataSpaceValid());
    checkArgument(outputColorInfo.colorTransfer != C.COLOR_TRANSFER_LINEAR);

    if (ColorInfo.isTransferHdr(inputColorInfo) != ColorInfo.isTransferHdr(outputColorInfo)) {
      checkArgument(
          isSupportedToneMapping(inputColorInfo, outputColorInfo)
              || isUltraHdr(inputColorInfo, outputColorInfo));
    }
  }

  private static boolean isSupportedToneMapping(
      ColorInfo inputColorInfo, ColorInfo outputColorInfo) {
    // OpenGL tone mapping is only implemented for BT2020 to BT709 and HDR to SDR.
    return inputColorInfo.colorSpace == C.COLOR_SPACE_BT2020
        && outputColorInfo.colorSpace != C.COLOR_SPACE_BT2020
        && ColorInfo.isTransferHdr(inputColorInfo)
        && (outputColorInfo.colorTransfer == C.COLOR_TRANSFER_GAMMA_2_2
            || outputColorInfo.colorTransfer == C.COLOR_TRANSFER_SDR);
  }

  private static boolean isUltraHdr(ColorInfo inputColorInfo, ColorInfo outputColorInfo) {
    // UltraHDR is is only implemented from SRGB_BT709_FULL to BT2020 HDR.
    return inputColorInfo.equals(ColorInfo.SRGB_BT709_FULL)
        && outputColorInfo.colorSpace == C.COLOR_SPACE_BT2020
        && ColorInfo.isTransferHdr(outputColorInfo);
  }

  /**
   * Releases the {@link GlShaderProgram} instances and destroys the OpenGL context.
   *
   * <p>This method must be called on the {@link Factory.Builder#setExecutorService}.
   */
  private void releaseGlObjects() {
    try {
      try {
        inputSwitcher.release();
        for (int i = 0; i < intermediateGlShaderPrograms.size(); i++) {
          intermediateGlShaderPrograms.get(i).release();
        }
        finalShaderProgramWrapper.release();
      } catch (Exception e) {
        Log.e(TAG, "Error releasing shader program", e);
      }
    } finally {
      if (shouldReleaseGlObjectsProvider) {
        try {
          glObjectsProvider.release(eglDisplay);
        } catch (GlUtil.GlException e) {
          Log.e(TAG, "Error releasing GL objects", e);
        }
      }
    }
  }

  /**
   * Creates an OpenGL ES 3.0 context if possible, and an OpenGL ES 2.0 context otherwise.
   *
   * <p>See {@link #createFocusedEglContext}.
   */
  private static Pair<EGLContext, EGLSurface> createFocusedEglContextWithFallback(
      GlObjectsProvider glObjectsProvider, EGLDisplay eglDisplay, int[] configAttributes)
      throws GlUtil.GlException {
    try {
      return createFocusedEglContext(
          glObjectsProvider, eglDisplay, /* openGlVersion= */ 3, configAttributes);
    } catch (GlUtil.GlException e) {
      return createFocusedEglContext(
          glObjectsProvider, eglDisplay, /* openGlVersion= */ 2, configAttributes);
    }
  }

  /**
   * Creates an {@link EGLContext} and focus it using a {@linkplain
   * GlObjectsProvider#createFocusedPlaceholderEglSurface placeholder EGL Surface}.
   *
   * @return The {@link EGLContext} and a placeholder {@link EGLSurface} as a {@link Pair}.
   */
  private static Pair<EGLContext, EGLSurface> createFocusedEglContext(
      GlObjectsProvider glObjectsProvider,
      EGLDisplay eglDisplay,
      int openGlVersion,
      int[] configAttributes)
      throws GlUtil.GlException {
    EGLContext eglContext =
        glObjectsProvider.createEglContext(eglDisplay, openGlVersion, configAttributes);
    // Some OpenGL ES 3.0 contexts returned from createEglContext may throw EGL_BAD_MATCH when being
    // used to createFocusedPlaceHolderEglSurface, despite GL documentation suggesting the contexts,
    // if successfully created, are valid. Check early whether the context is really valid.
    EGLSurface eglSurface =
        glObjectsProvider.createFocusedPlaceholderEglSurface(eglContext, eglDisplay);
    return Pair.create(eglContext, eglSurface);
  }

  private static final class InputStreamInfo {
    public final @InputType int inputType;
    public final Format format;
    public final List<Effect> effects;
    public final long offsetToAddUs;

    public InputStreamInfo(
        @InputType int inputType, Format format, List<Effect> effects, long offsetToAddUs) {
      this.inputType = inputType;
      this.format = format;
      this.effects = effects;
      this.offsetToAddUs = offsetToAddUs;
    }
  }
}
