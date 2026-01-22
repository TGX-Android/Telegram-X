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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.round;

import android.content.Context;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;

/**
 * A {@link GlEffect} that applies a Lanczos-windowed sinc function when resampling an image. See
 * Filters for Common Resampling Tasks, Ken Turkowski.
 *
 * <p>The filter rescales images in both dimensions with the same scaling factor.
 */
@UnstableApi
public final class LanczosResample implements GlEffect {
  // Default value for the radius, or alpha parameter used by Lanczos filter. A value of 3 is
  // used by ffmpeg (https://ffmpeg.org/ffmpeg-scaler.html), libplacebo, or Apple's vImage library.
  private static final float DEFAULT_RADIUS = 3f;
  // For scaling factors near 1, this algorithm won't bring a big improvement over bilinear or
  // nearest sampling using the GPU texture units
  private static final float NO_OP_THRESHOLD = 0.01f;

  private final float radius;
  private final int width;
  private final int height;

  /**
   * Creates an instance.
   *
   * @param width The width inside which the output contents will fit.
   * @param height The height inside which the output contents will fit.
   */
  public static LanczosResample scaleToFit(
      @IntRange(from = 1) int width, @IntRange(from = 1) int height) {
    checkArgument(width > 0);
    checkArgument(height > 0);
    return new LanczosResample(DEFAULT_RADIUS, width, height);
  }

  private LanczosResample(float radius, int width, int height) {
    this.radius = radius;
    this.width = width;
    this.height = height;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new SeparableConvolutionShaderProgram(
        context, useHdr, new LanczosResampleScaledFunctionProvider(radius, width, height));
  }

  /**
   * {@inheritDoc}
   *
   * <p>For scaling factors near 1, this effect applies no change because the Lanczos algorithm
   * won't bring a big improvement over bilinear or nearest sampling using the GPU texture units.
   */
  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return abs(scalingFactorToFit(inputWidth, inputHeight, width, height) - 1f) < NO_OP_THRESHOLD;
  }

  /**
   * Returns the scaling factor required to fit an input image into a target rectangle.
   *
   * @param inputWidth The width of the input image.
   * @param inputHeight The height of the input image.
   * @param targetWidth The width of the target rectangle.
   * @param targetHeight The height of the target rectangle.
   */
  private static float scalingFactorToFit(
      int inputWidth, int inputHeight, int targetWidth, int targetHeight) {
    checkArgument(inputWidth > 0);
    checkArgument(inputHeight > 0);
    // Scale to fit, preserving aspect ratio.
    if (inputHeight * targetWidth <= targetHeight * inputWidth) {
      return (float) targetWidth / inputWidth;
    } else {
      return (float) targetHeight / inputHeight;
    }
  }

  private static class LanczosResampleScaledFunctionProvider
      implements ConvolutionFunction1D.Provider {
    // Note: We deliberately don't use Float.MIN_VALUE because it's positive & very close to zero.
    private static final float SCALE_UNSET = -Float.MAX_VALUE;
    private final float radius;
    private final int width;
    private final int height;

    private float scale;

    private LanczosResampleScaledFunctionProvider(
        @FloatRange(from = 0, fromInclusive = false) float radius,
        @IntRange(from = 1) int width,
        @IntRange(from = 1) int height) {
      checkArgument(radius > 0);
      checkArgument(width > 0);
      checkArgument(height > 0);
      this.radius = radius;
      this.width = width;
      this.height = height;
      scale = SCALE_UNSET;
    }

    @Override
    public ConvolutionFunction1D getConvolution(long presentationTimeUs) {
      // When scaling down (scale < 1), scale the kernel function to ensure we sample all input
      // pixels inside the kernel radius.
      // When scaling up (scale > 1), leave the kernel function unchanged.
      return new ScaledLanczosFunction(radius, min(scale, 1f));
    }

    @Override
    public Size configure(Size inputSize) {
      scale = scalingFactorToFit(inputSize.getWidth(), inputSize.getHeight(), width, height);
      return new Size(round(inputSize.getWidth() * scale), round(inputSize.getHeight() * scale));
    }
  }
}
