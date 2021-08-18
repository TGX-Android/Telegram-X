package me.vkryl.android;

/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.LruCache;
import android.view.Gravity;

/**
 * Utility methods for creating prettier gradient scrims.
 */
public final class ScrimUtil {
  private static final LruCache<Integer, Drawable> cubicGradientScrimCache = new LruCache<>(10);

  private ScrimUtil() {
  }

  /**
   * Creates an approximated cubic gradient using a multi-stop linear gradient. See
   * <a href="https://plus.google.com/+RomanNurik/posts/2QvHVFWrHZf">this post</a> for more
   * details.
   */
  public static Drawable makeCubicGradientScrimDrawable (int baseColor, int numStops, int gravity, boolean useCache) {
    numStops = Math.max(numStops, 2);

    // Generate a cache key by hashing together the inputs, based on the method described in the Effective Java book
    int cacheKeyHash = baseColor;
    cacheKeyHash = 31 * cacheKeyHash + numStops;
    cacheKeyHash = 31 * cacheKeyHash + gravity;

    Drawable cachedGradient = useCache ? cubicGradientScrimCache.get(cacheKeyHash) : null;
    if (cachedGradient != null) {
      return cachedGradient;
    }

    PaintDrawable paintDrawable = new PaintDrawable();
    paintDrawable.setShape(new RectShape());

    final int[] stopColors = new int[numStops];

    int red = Color.red(baseColor);
    int green = Color.green(baseColor);
    int blue = Color.blue(baseColor);
    int alpha = Color.alpha(baseColor);

    for (int i = 0; i < numStops; i++) {
      float x = i * 1f / (numStops - 1);
      float opacity = MathUtil.constrain(0, 1, (float) Math.pow(x, 3));
      stopColors[i] = Color.argb((int) (alpha * opacity), red, green, blue);
    }

    final float x0, x1, y0, y1;
    switch (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
      case Gravity.LEFT:  x0 = 1; x1 = 0; break;
      case Gravity.RIGHT: x0 = 0; x1 = 1; break;
      default:            x0 = 0; x1 = 0; break;
    }
    switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
      case Gravity.TOP:    y0 = 1; y1 = 0; break;
      case Gravity.BOTTOM: y0 = 0; y1 = 1; break;
      default:             y0 = 0; y1 = 0; break;
    }

    paintDrawable.setShaderFactory(new ShapeDrawable.ShaderFactory() {
      @Override
      public Shader resize(int width, int height) {
        LinearGradient linearGradient = new LinearGradient(
          width * x0,
          height * y0,
          width * x1,
          height * y1,
          stopColors, null,
          Shader.TileMode.CLAMP);
        return linearGradient;
      }
    });

    if (useCache) {
      cubicGradientScrimCache.put(cacheKeyHash, paintDrawable);
    }
    return paintDrawable;
  }

  private static class MathUtil {
    public static float constrain(float min, float max, float v) {
      return Math.max(min, Math.min(max, v));
    }

    public static float interpolate(float x1, float x2, float f) {
      return x1 + (x2 - x1) * f;
    }

    public static float uninterpolate(float x1, float x2, float v) {
      if (x2 - x1 == 0) {
        throw new IllegalArgumentException("Can't reverse interpolate with domain size of 0");
      }
      return (v - x1) / (x2 - x1);
    }

    public static int floorEven(int num) {
      return num & ~0x01;
    }

    public static int roundMult4(int num) {
      return (num + 2) & ~0x03;
    }

    // divide two integers but round up
    // see http://stackoverflow.com/a/7446742/102703
    public static int intDivideRoundUp(int num, int divisor) {
      int sign = (num > 0 ? 1 : -1) * (divisor > 0 ? 1 : -1);
      return sign * (Math.abs(num) + Math.abs(divisor) - 1) / Math.abs(divisor);
    }

    public static float maxDistanceToCorner(int x, int y, int left, int top, int right, int bottom) {
      float maxDistance = 0;
      maxDistance = Math.max(maxDistance, (float) Math.hypot(x - left, y - top));
      maxDistance = Math.max(maxDistance, (float) Math.hypot(x - right, y - top));
      maxDistance = Math.max(maxDistance, (float) Math.hypot(x - left, y - bottom));
      maxDistance = Math.max(maxDistance, (float) Math.hypot(x - right, y - bottom));
      return maxDistance;
    }

    private MathUtil() {
    }
  }
}