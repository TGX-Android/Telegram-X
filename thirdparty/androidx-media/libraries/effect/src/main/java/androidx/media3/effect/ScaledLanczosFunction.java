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

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.sin;

import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * Implementation of a scaled Lanczos window function.
 *
 * <p>The function input is multiplied by {@code scale} before applying the textbook Lanczos window
 * function.
 */
/* package */ final class ScaledLanczosFunction implements ConvolutionFunction1D {
  private final float radius;
  private final float scale;

  /**
   * Creates an instance.
   *
   * @param radius The radius parameter of the Lanczos window function.
   * @param scale The scaling factor applied to inputs.
   */
  public ScaledLanczosFunction(float radius, float scale) {
    this.radius = radius;
    this.scale = scale;
  }

  @Override
  public float domainStart() {
    return -radius / scale;
  }

  @Override
  public float domainEnd() {
    return radius / scale;
  }

  @Override
  public float value(float samplePosition) {
    float x = samplePosition * scale;
    if (abs(x) < 1e-5) {
      return 1.0f;
    }
    if (abs(x) > radius) {
      return 0.0f;
    }
    return (float) (radius * sin(PI * x) * sin(PI * x / radius) / (PI * PI * x * x));
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ScaledLanczosFunction)) {
      return false;
    }
    ScaledLanczosFunction that = (ScaledLanczosFunction) o;
    return Float.compare(that.radius, radius) == 0 && Float.compare(that.scale, scale) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(radius, scale);
  }
}
