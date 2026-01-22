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

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;
import androidx.annotation.CallSuper;
import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link GlShaderProgram} for performing separable convolutions.
 *
 * <p>A single {@link ConvolutionFunction1D} is applied horizontally on a first pass and vertically
 * on a second pass.
 */
@UnstableApi
public class SeparableConvolutionShaderProgram implements GlShaderProgram {
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH =
      "shaders/fragment_shader_separable_convolution_es2.glsl";

  // TODO (b/282767994): Fix TAP hanging issue and update samples per texel.
  private static final int RASTER_SAMPLES_PER_TEXEL = 5;
  // Apply some padding in the function LUT to avoid any issues from GL sampling off the texture.
  private static final int FUNCTION_LUT_PADDING = RASTER_SAMPLES_PER_TEXEL;

  private final GlProgram glProgram;
  private final boolean useHdr;
  private final ConvolutionFunction1D.Provider convolutionFunction1DProvider;

  private GlShaderProgram.InputListener inputListener;
  private GlShaderProgram.OutputListener outputListener;
  private GlShaderProgram.ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private boolean outputTextureInUse;
  private GlTextureInfo outputTexture;
  private GlTextureInfo intermediateTexture;
  private GlTextureInfo functionLutTexture; // Values for the function LUT as a texture.
  private float functionLutTexelStep;
  private float functionLutCenterX;
  private float functionLutDomainStart;
  private float functionLutWidth;
  private Size outputSize;
  private Size lastInputSize;
  private Size intermediateSize;
  private @MonotonicNonNull ConvolutionFunction1D lastConvolutionFunction;

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param convolution The {@link SeparableConvolution} to apply in each direction.
   * @param scaleWidth The scaling factor used to determine the width of the output relative to the
   *     input.
   * @param scaleHeight The scaling factor used to determine the height of the output relative to
   *     the input.
   * @throws VideoFrameProcessingException If a problem occurs while reading shader files.
   */
  public SeparableConvolutionShaderProgram(
      Context context,
      boolean useHdr,
      SeparableConvolution convolution,
      float scaleWidth,
      float scaleHeight)
      throws VideoFrameProcessingException {
    this(context, useHdr, new SeparableConvolutionWrapper(convolution, scaleWidth, scaleHeight));
  }

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param convolutionFunction1DProvider The {@link ConvolutionFunction1D.Provider} which will
   *     provide the 1D convolution function to apply in each direction.
   * @throws VideoFrameProcessingException If a problem occurs while reading shader files.
   */
  public SeparableConvolutionShaderProgram(
      Context context, boolean useHdr, ConvolutionFunction1D.Provider convolutionFunction1DProvider)
      throws VideoFrameProcessingException {
    this.useHdr = useHdr;
    this.convolutionFunction1DProvider = convolutionFunction1DProvider;
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
    functionLutTexture = GlTextureInfo.UNSET;
    intermediateTexture = GlTextureInfo.UNSET;
    outputTexture = GlTextureInfo.UNSET;
    lastInputSize = Size.ZERO;
    intermediateSize = Size.ZERO;
    outputSize = Size.ZERO;
    lastConvolutionFunction = null;

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  @Override
  public final void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (!outputTextureInUse) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public final void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public final void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    this.errorListenerExecutor = errorListenerExecutor;
    this.errorListener = errorListener;
  }

  @Override
  public final void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    Assertions.checkState(
        !outputTextureInUse,
        "The shader program does not currently accept input frames. Release prior output frames"
            + " first.");
    try {
      ensureTexturesAreConfigured(
          glObjectsProvider, new Size(inputTexture.width, inputTexture.height), presentationTimeUs);
      outputTextureInUse = true;
      renderHorizontal(inputTexture);
      renderVertical();

      onBlurRendered(inputTexture);

      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* i1= */ 0, /* i2= */ 4);
      GlUtil.checkGlError();
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (GlUtil.GlException e) {
      errorListenerExecutor.execute(
          () -> errorListener.onError(VideoFrameProcessingException.from(e, presentationTimeUs)));
    }
  }

  @Override
  public final void releaseOutputFrame(GlTextureInfo outputTexture) {
    outputTextureInUse = false;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public final void signalEndOfCurrentInputStream() {
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  public final void flush() {
    outputTextureInUse = false;
    inputListener.onFlush();
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  @CallSuper
  public void release() throws VideoFrameProcessingException {
    try {
      outputTexture.release();
      intermediateTexture.release();
      functionLutTexture.release();
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  /**
   * Called when the blur has been rendered onto the frame.
   *
   * <p>The default implementation is a no-op.
   *
   * @param inputTexture The input texture.
   * @throws GlUtil.GlException If an error occurs.
   */
  protected void onBlurRendered(GlTextureInfo inputTexture) throws GlUtil.GlException {
    // Do nothing.
  }

  private void renderOnePass(int inputTexId, boolean isHorizontal) throws GlUtil.GlException {
    int size = isHorizontal ? lastInputSize.getWidth() : intermediateSize.getHeight();
    glProgram.use();
    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
    glProgram.setIntUniform("uIsHorizontal", isHorizontal ? 1 : 0);
    glProgram.setFloatUniform("uSourceTexelSize", 1.0f / size);
    glProgram.setFloatUniform("uSourceFullSize", (float) size);
    glProgram.setFloatUniform("uConvStartTexels", functionLutDomainStart);
    glProgram.setFloatUniform("uConvWidthTexels", functionLutWidth);
    glProgram.setFloatUniform("uFunctionLookupStepSize", functionLutTexelStep);
    glProgram.setFloatsUniform("uFunctionLookupCenter", new float[] {functionLutCenterX, 0.5f});
    glProgram.setSamplerTexIdUniform(
        "uFunctionLookupSampler", functionLutTexture.texId, /* texUnitIndex= */ 1);
    glProgram.bindAttributesAndUniforms();

    // The four-vertex triangle strip forms a quad.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    GlUtil.checkGlError();
  }

  private void renderHorizontal(GlTextureInfo inputTexture) throws GlUtil.GlException {
    // Render horizontal reads from the input texture and renders to the intermediate texture.
    GlUtil.focusFramebufferUsingCurrentContext(
        intermediateTexture.fboId, intermediateTexture.width, intermediateTexture.height);
    GlUtil.clearFocusedBuffers();
    renderOnePass(inputTexture.texId, /* isHorizontal= */ true);
  }

  private void renderVertical() throws GlUtil.GlException {
    // Render vertical reads from the intermediate and renders to the output texture.
    GlUtil.focusFramebufferUsingCurrentContext(
        outputTexture.fboId, outputTexture.width, outputTexture.height);
    GlUtil.clearFocusedBuffers();
    renderOnePass(intermediateTexture.texId, /* isHorizontal= */ false);
  }

  private void ensureTexturesAreConfigured(
      GlObjectsProvider glObjectsProvider, Size inputSize, long presentationTimeUs)
      throws GlUtil.GlException {
    outputSize = convolutionFunction1DProvider.configure(inputSize);
    ConvolutionFunction1D currentConvolutionFunction =
        convolutionFunction1DProvider.getConvolution(presentationTimeUs);
    if (!currentConvolutionFunction.equals(lastConvolutionFunction)) {
      updateFunctionTexture(currentConvolutionFunction);
      lastConvolutionFunction = currentConvolutionFunction;
    }

    // Only update intermediate and output textures if the size changes.
    if (inputSize.equals(lastInputSize)) {
      return;
    }

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);

    // If there is a size change with the filtering (for example, a scaling operation), the first
    // pass is applied horizontally.  As a result, width of the intermediate texture will match the
    // output size, while the height will be unchanged from the input
    intermediateSize = new Size(outputSize.getWidth(), inputSize.getHeight());
    intermediateTexture =
        configurePixelTexture(glObjectsProvider, intermediateTexture, intermediateSize);
    outputTexture = configurePixelTexture(glObjectsProvider, outputTexture, outputSize);

    this.lastInputSize = inputSize;
  }

  /**
   * Creates a function lookup table for the convolution, and stores it in a 16b floating point
   * texture for GPU access.
   */
  private void updateFunctionTexture(ConvolutionFunction1D convolutionFunction)
      throws GlUtil.GlException {

    int lutRasterSize =
        (int)
            Math.ceil(
                convolutionFunction.width() * RASTER_SAMPLES_PER_TEXEL + 2 * FUNCTION_LUT_PADDING);

    // The function LUT is mapped to [0, 1] texture coords. We need to calculate what change
    // in texture coordinated corresponds exactly with a size of 1 texel (or pixel) in the function.
    // This is basically 1 / function_width, but due to the ceil() call above, it needs to be
    // calculated based on the actual raster size.
    this.functionLutTexelStep = 1.0f / ((float) lutRasterSize / RASTER_SAMPLES_PER_TEXEL);

    FloatBuffer functionValues = FloatBuffer.allocate(lutRasterSize);
    float rasterSampleStep = 1.0f / RASTER_SAMPLES_PER_TEXEL;
    float functionDomainStart = convolutionFunction.domainStart();
    int index = 0;

    for (int i = 0; i < lutRasterSize; i++) {
      float sampleValue = 0.0f;
      int unpaddedI = i - FUNCTION_LUT_PADDING;
      float samplePosition = functionDomainStart + unpaddedI * rasterSampleStep;

      if (unpaddedI >= 0 && i <= lutRasterSize - FUNCTION_LUT_PADDING) {
        sampleValue = convolutionFunction.value(samplePosition);
      }
      functionValues.put(index++, sampleValue);
    }

    // Calculate the center of the function in the raster.  The formula below is a slight
    // adjustment on (value - min) / (max - min), where value = 0 at center and
    // rasterSampleStep * lutRasterSize is equal to (max - min) over the range of the raster
    // samples, which may be slightly different than the difference between the function's max
    // and min domain values.
    // To find the value associated at position 0 in the texture, is the value corresponding with
    // the leading edge position of the first sample.  This needs to account for the padding and
    // the 1/2 texel offsets used in texture lookups (index 0 is centered at 0.5 / numTexels).
    float minValueWithPadding =
        functionDomainStart - rasterSampleStep * (FUNCTION_LUT_PADDING + 0.5f);
    this.functionLutCenterX = -minValueWithPadding / (rasterSampleStep * lutRasterSize);
    this.functionLutDomainStart = convolutionFunction.domainStart();
    this.functionLutWidth = convolutionFunction.width();

    // Create new GL texture if needed.
    if (functionLutTexture == GlTextureInfo.UNSET || functionLutTexture.width != lutRasterSize) {
      functionLutTexture.release();

      int functionLutTextureId = GlUtil.generateTexture();
      // We do not render into lookup table. Do not generate framebuffer or renderbuffer.
      functionLutTexture =
          new GlTextureInfo(
              functionLutTextureId,
              /* fboId= */ C.INDEX_UNSET,
              /* rboId= */ C.INDEX_UNSET,
              /* width= */ lutRasterSize,
              /* height= */ 1);
    }
    GlUtil.bindTexture(GLES20.GL_TEXTURE_2D, functionLutTexture.texId, GLES20.GL_LINEAR);
    GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D,
        /* level= */ 0,
        /* internalformat= */ GLES30.GL_R16F,
        /* width= */ lutRasterSize,
        /* height= */ 1,
        /* border= */ 0,
        /* format= */ GLES30.GL_RED,
        /* type= */ GLES30.GL_FLOAT,
        /* buffer= */ functionValues);
    GlUtil.checkGlError();
  }

  private GlTextureInfo configurePixelTexture(
      GlObjectsProvider glObjectsProvider, GlTextureInfo existingTexture, Size size)
      throws GlUtil.GlException {
    if (size.getWidth() == existingTexture.width && size.getHeight() == existingTexture.height) {
      return existingTexture;
    }

    existingTexture.release();
    int texId = GlUtil.createTexture(size.getWidth(), size.getHeight(), useHdr);

    return glObjectsProvider.createBuffersForTexture(texId, size.getWidth(), size.getHeight());
  }

  private static final class SeparableConvolutionWrapper implements ConvolutionFunction1D.Provider {
    private final SeparableConvolution separableConvolution;
    private final float scaleWidth;
    private final float scaleHeight;

    public SeparableConvolutionWrapper(
        SeparableConvolution separableConvolution, float scaleWidth, float scaleHeight) {
      this.separableConvolution = separableConvolution;
      this.scaleWidth = scaleWidth;
      this.scaleHeight = scaleHeight;
    }

    @Override
    public ConvolutionFunction1D getConvolution(long presentationTimeUs) {
      return separableConvolution.getConvolution(presentationTimeUs);
    }

    @Override
    public Size configure(Size inputSize) {
      return new Size(
          (int) (inputSize.getWidth() * scaleWidth), (int) (inputSize.getHeight() * scaleHeight));
    }
  }
}
