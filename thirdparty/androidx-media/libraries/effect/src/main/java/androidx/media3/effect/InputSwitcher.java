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
 *
 */

package androidx.media3.effect;

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_SURFACE;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION;
import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.contains;

import android.content.Context;
import android.util.SparseArray;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.DefaultVideoFrameProcessor.WorkingColorSpace;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A switcher to switch between {@linkplain TextureManager texture managers} of different
 * {@linkplain VideoFrameProcessor.InputType input types}.
 */
/* package */ final class InputSwitcher {
  private final Context context;
  private final ColorInfo outputColorInfo;
  private final GlObjectsProvider glObjectsProvider;
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final GlShaderProgram.ErrorListener samplingShaderProgramErrorListener;
  private final Executor errorListenerExecutor;
  private final SparseArray<Input> inputs;
  private final @WorkingColorSpace int sdrWorkingColorSpace;
  private final boolean experimentalAdjustSurfaceTextureTransformationMatrix;

  private @MonotonicNonNull GlShaderProgram downstreamShaderProgram;
  private @MonotonicNonNull TextureManager activeTextureManager;

  public InputSwitcher(
      Context context,
      ColorInfo outputColorInfo,
      GlObjectsProvider glObjectsProvider,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor,
      Executor errorListenerExecutor,
      GlShaderProgram.ErrorListener samplingShaderProgramErrorListener,
      @WorkingColorSpace int sdrWorkingColorSpace,
      boolean repeatLastRegisteredFrame,
      boolean experimentalAdjustSurfaceTextureTransformationMatrix,
      boolean experimentalRepeatInputBitmapWithoutResampling)
      throws VideoFrameProcessingException {
    this.context = context;
    this.outputColorInfo = outputColorInfo;
    this.glObjectsProvider = glObjectsProvider;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    this.errorListenerExecutor = errorListenerExecutor;
    this.samplingShaderProgramErrorListener = samplingShaderProgramErrorListener;
    this.inputs = new SparseArray<>();
    this.sdrWorkingColorSpace = sdrWorkingColorSpace;
    this.experimentalAdjustSurfaceTextureTransformationMatrix =
        experimentalAdjustSurfaceTextureTransformationMatrix;

    // TODO(b/274109008): Investigate lazily instantiating the texture managers.
    Input surfaceInput =
        new Input(
            new ExternalTextureManager(
                glObjectsProvider,
                videoFrameProcessingTaskExecutor,
                repeatLastRegisteredFrame,
                experimentalAdjustSurfaceTextureTransformationMatrix));
    inputs.put(INPUT_TYPE_SURFACE, surfaceInput);
    inputs.put(INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION, surfaceInput);
    inputs.put(
        INPUT_TYPE_BITMAP,
        new Input(
            new BitmapTextureManager(
                glObjectsProvider,
                videoFrameProcessingTaskExecutor,
                /* signalRepeatingSequence= */ experimentalRepeatInputBitmapWithoutResampling)));
    inputs.put(
        INPUT_TYPE_TEXTURE_ID,
        new Input(new TexIdTextureManager(glObjectsProvider, videoFrameProcessingTaskExecutor)));
  }

  private DefaultShaderProgram createSamplingShaderProgram(
      ColorInfo inputColorInfo, @VideoFrameProcessor.InputType int inputType)
      throws VideoFrameProcessingException {
    // TODO(b/274109008): Refactor DefaultShaderProgram to create a class just for sampling.
    DefaultShaderProgram samplingShaderProgram;
    switch (inputType) {
      case INPUT_TYPE_SURFACE:
      case INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION:
        samplingShaderProgram =
            DefaultShaderProgram.createWithExternalSampler(
                context,
                inputColorInfo,
                outputColorInfo,
                sdrWorkingColorSpace,
                experimentalAdjustSurfaceTextureTransformationMatrix);
        break;
      case INPUT_TYPE_BITMAP:
      case INPUT_TYPE_TEXTURE_ID:
        samplingShaderProgram =
            DefaultShaderProgram.createWithInternalSampler(
                context, inputColorInfo, outputColorInfo, sdrWorkingColorSpace, inputType);
        break;
      default:
        throw new VideoFrameProcessingException("Unsupported input type " + inputType);
    }
    samplingShaderProgram.setErrorListener(
        errorListenerExecutor, samplingShaderProgramErrorListener);
    return samplingShaderProgram;
  }

  /** Sets the {@link GlShaderProgram} that {@code InputSwitcher} outputs to. */
  public void setDownstreamShaderProgram(GlShaderProgram downstreamShaderProgram) {
    this.downstreamShaderProgram = downstreamShaderProgram;
  }

  /**
   * Switches to a new source of input.
   *
   * <p>The first time this is called for each {@link VideoFrameProcessor.InputType}, a sampling
   * {@link GlShaderProgram} is created for the {@code newInputType}.
   *
   * @param newInputType The new {@link VideoFrameProcessor.InputType} to switch to.
   * @param newInputFrameInfo The {@link FrameInfo} associated with the new input.
   */
  public void switchToInput(
      @VideoFrameProcessor.InputType int newInputType, FrameInfo newInputFrameInfo)
      throws VideoFrameProcessingException {
    checkStateNotNull(downstreamShaderProgram);
    checkState(contains(inputs, newInputType), "Input type not registered: " + newInputType);

    for (int i = 0; i < inputs.size(); i++) {
      inputs.get(inputs.keyAt(i)).setActive(false);
    }

    // Activate the relevant input for the new input type.
    Input input = inputs.get(newInputType);
    ColorInfo newInputColorInfo = checkNotNull(newInputFrameInfo.format.colorInfo);
    if (input.getInputColorInfo() == null || !newInputColorInfo.equals(input.getInputColorInfo())) {
      input.setSamplingGlShaderProgram(
          createSamplingShaderProgram(newInputColorInfo, newInputType));
      input.setInputColorInfo(newInputColorInfo);
    }
    input.setChainingListener(
        new GatedChainingListenerWrapper(
            glObjectsProvider,
            checkNotNull(input.getSamplingGlShaderProgram()),
            this.downstreamShaderProgram,
            videoFrameProcessingTaskExecutor));
    input.setActive(true);
    downstreamShaderProgram.setInputListener(checkNotNull(input.gatedChainingListenerWrapper));
    activeTextureManager = input.textureManager;
    boolean automaticRegistration = newInputType == INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION;
    checkNotNull(activeTextureManager).setInputFrameInfo(newInputFrameInfo, automaticRegistration);
  }

  /** Returns whether the {@code InputSwitcher} is connected to an active input. */
  public boolean hasActiveInput() {
    return activeTextureManager != null;
  }

  /**
   * Returns the {@link TextureManager} that is currently being used.
   *
   * @throws IllegalStateException If the {@code InputSwitcher} is not connected to an {@linkplain
   *     #hasActiveInput() input}.
   */
  public TextureManager activeTextureManager() {
    return checkStateNotNull(activeTextureManager);
  }

  /**
   * Invokes {@link TextureManager#signalEndOfCurrentInputStream} on the active {@link
   * TextureManager}.
   */
  public void signalEndOfCurrentInputStream() {
    checkNotNull(activeTextureManager).signalEndOfCurrentInputStream();
  }

  /**
   * Returns the input {@link Surface}.
   *
   * @return The input {@link Surface}, regardless of whether the current input is {@linkplain
   *     #switchToInput set} to {@link VideoFrameProcessor#INPUT_TYPE_SURFACE}.
   */
  public Surface getInputSurface() {
    checkState(contains(inputs, INPUT_TYPE_SURFACE));
    return inputs.get(INPUT_TYPE_SURFACE).textureManager.getInputSurface();
  }

  /** See {@link DefaultVideoFrameProcessor#setInputDefaultBufferSize}. */
  public void setInputDefaultBufferSize(int width, int height) {
    checkState(contains(inputs, INPUT_TYPE_SURFACE));
    inputs.get(INPUT_TYPE_SURFACE).textureManager.setDefaultBufferSize(width, height);
  }

  /** Sets the {@link OnInputFrameProcessedListener}. */
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    checkState(contains(inputs, INPUT_TYPE_TEXTURE_ID));
    inputs.get(INPUT_TYPE_TEXTURE_ID).textureManager.setOnInputFrameProcessedListener(listener);
  }

  /** Releases the resources. */
  public void release() throws VideoFrameProcessingException {
    for (int i = 0; i < inputs.size(); i++) {
      inputs.get(inputs.keyAt(i)).release();
    }
  }

  /**
   * Wraps a {@link TextureManager} and an appropriate {@linkplain GlShaderProgram sampling shader
   * program}.
   *
   * <p>The output is always an internal GL texture.
   */
  private static final class Input {
    public final TextureManager textureManager;

    private @MonotonicNonNull ExternalShaderProgram samplingGlShaderProgram;
    private @MonotonicNonNull ColorInfo inputColorInfo;
    private @MonotonicNonNull GatedChainingListenerWrapper gatedChainingListenerWrapper;
    private boolean released;

    public Input(TextureManager textureManager) {
      this.textureManager = textureManager;
    }

    public void setSamplingGlShaderProgram(ExternalShaderProgram samplingGlShaderProgram)
        throws VideoFrameProcessingException {
      if (this.samplingGlShaderProgram != null) {
        this.samplingGlShaderProgram.release();
      }
      this.samplingGlShaderProgram = samplingGlShaderProgram;
      textureManager.setSamplingGlShaderProgram(samplingGlShaderProgram);
      samplingGlShaderProgram.setInputListener(textureManager);
    }

    public void setInputColorInfo(ColorInfo inputColorInfo) {
      this.inputColorInfo = inputColorInfo;
    }

    public void setChainingListener(GatedChainingListenerWrapper gatedChainingListenerWrapper) {
      this.gatedChainingListenerWrapper = gatedChainingListenerWrapper;
      checkNotNull(samplingGlShaderProgram).setOutputListener(gatedChainingListenerWrapper);
    }

    @Nullable
    public ExternalShaderProgram getSamplingGlShaderProgram() {
      return samplingGlShaderProgram;
    }

    @Nullable
    public ColorInfo getInputColorInfo() {
      return inputColorInfo;
    }

    public void setActive(boolean active) {
      if (gatedChainingListenerWrapper == null) {
        return;
      }
      gatedChainingListenerWrapper.setActive(active);
    }

    public void release() throws VideoFrameProcessingException {
      if (!released) {
        released = true;
        textureManager.release();
        if (samplingGlShaderProgram != null) {
          samplingGlShaderProgram.release();
        }
      }
    }
  }

  /**
   * Wraps a {@link ChainingGlShaderProgramListener}, with the ability to turn off the event
   * listening.
   */
  private static final class GatedChainingListenerWrapper
      implements GlShaderProgram.OutputListener, GlShaderProgram.InputListener {

    private final ChainingGlShaderProgramListener chainingGlShaderProgramListener;

    private boolean isActive;

    public GatedChainingListenerWrapper(
        GlObjectsProvider glObjectsProvider,
        GlShaderProgram producingGlShaderProgram,
        GlShaderProgram consumingGlShaderProgram,
        VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
      this.chainingGlShaderProgramListener =
          new ChainingGlShaderProgramListener(
              glObjectsProvider,
              producingGlShaderProgram,
              consumingGlShaderProgram,
              videoFrameProcessingTaskExecutor);
    }

    @Override
    public void onReadyToAcceptInputFrame() {
      if (isActive) {
        chainingGlShaderProgramListener.onReadyToAcceptInputFrame();
      }
    }

    @Override
    public void onInputFrameProcessed(GlTextureInfo inputTexture) {
      if (isActive) {
        chainingGlShaderProgramListener.onInputFrameProcessed(inputTexture);
      }
    }

    @Override
    public synchronized void onFlush() {
      if (isActive) {
        chainingGlShaderProgramListener.onFlush();
      }
    }

    @Override
    public synchronized void onOutputFrameAvailable(
        GlTextureInfo outputTexture, long presentationTimeUs) {
      if (isActive) {
        chainingGlShaderProgramListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
      }
    }

    @Override
    public synchronized void onCurrentOutputStreamEnded() {
      if (isActive) {
        chainingGlShaderProgramListener.onCurrentOutputStreamEnded();
      }
    }

    public void setActive(boolean isActive) {
      this.isActive = isActive;
    }
  }
}
