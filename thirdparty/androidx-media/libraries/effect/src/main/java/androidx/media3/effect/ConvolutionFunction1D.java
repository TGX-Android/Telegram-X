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

import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;

/**
 * An interface for 1 dimensional convolution functions.
 *
 * <p>The domain defines the region over which the function operates, in pixels.
 */
@UnstableApi
public interface ConvolutionFunction1D {

  /** A configurable provider for {@link ConvolutionFunction1D} instances. */
  interface Provider {

    /**
     * Configures the provider to return {@linkplain ConvolutionFunction1D 1D convolution functions}
     * based on the input frame dimensions.
     *
     * <p>This method must be called before {@link #getConvolution(long)}.
     *
     * @param inputSize The input frame size on which to apply a convolution.
     * @return The output frame size after applying the convolution.
     */
    Size configure(Size inputSize);

    /**
     * Returns a {@linkplain ConvolutionFunction1D 1D convolution function}.
     *
     * @param presentationTimeUs The presentation timestamp of the input frame, in microseconds.
     */
    ConvolutionFunction1D getConvolution(long presentationTimeUs);
  }

  /** Returns the start of the domain. */
  float domainStart();

  /** Returns the end of the domain. */
  float domainEnd();

  /** Returns the width of the domain. */
  default float width() {
    return domainEnd() - domainStart();
  }

  /** Returns the value of the function at the {@code samplePosition}. */
  float value(float samplePosition);
}
