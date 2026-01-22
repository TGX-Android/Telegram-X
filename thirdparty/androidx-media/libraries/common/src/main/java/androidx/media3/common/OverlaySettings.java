/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.media3.common;

import android.util.Pair;
import androidx.media3.common.util.UnstableApi;

/**
 * Provides information of how an input texture (for example, a {@code TextureOverlay} or in {@code
 * VideoCompositor}) is presented.
 */
@UnstableApi
public interface OverlaySettings {

  /** The default alpha scale value of the overlay. */
  float DEFAULT_ALPHA_SCALE = 1f;

  /** The default coordinates for the anchor point of the overlay within the background frame. */
  Pair<Float, Float> DEFAULT_BACKGROUND_FRAME_ANCHOR = Pair.create(0f, 0f);

  /** The default coordinates for the anchor point of the overlay frame. */
  Pair<Float, Float> DEFAULT_OVERLAY_FRAME_ANCHOR = Pair.create(0f, 0f);

  /** The default scaling of the overlay. */
  Pair<Float, Float> DEFAULT_SCALE = Pair.create(1f, 1f);

  /** The default rotation of the overlay, counter-clockwise. */
  float DEFAULT_ROTATION_DEGREES = 0f;

  /** The default luminance multiplier of an SDR overlay when overlaid on a HDR frame. */
  float DEFAULT_HDR_LUMINANCE_MULTIPLIER = 1f;

  /**
   * Returns the alpha scale value of the overlay, altering its translucency.
   *
   * <p>An {@code alphaScale} value of {@code 1} means no change is applied. A value below {@code 1}
   * increases translucency, and a value above {@code 1} reduces translucency.
   *
   * <p>The default value is {@link #DEFAULT_ALPHA_SCALE}.
   */
  default float getAlphaScale() {
    return DEFAULT_ALPHA_SCALE;
  }

  /**
   * Returns the coordinates for the anchor point of the overlay within the background frame.
   *
   * <p>The coordinates are specified in Normalised Device Coordinates (NDCs) relative to the
   * background frame. The ranges for x and y are from {@code -1} to {@code 1}. The default value is
   * {@code (0,0)}, the center of the background frame.
   *
   * <p>The overlay's {@linkplain #getOverlayFrameAnchor anchor point} will be positioned at the
   * anchor point returned from this method. For example, a value of {@code (1,1)} will move the
   * {@linkplain #getOverlayFrameAnchor overlay's anchor} to the top right corner. That is, if the
   * overlay's anchor is at {@code (1,1)} (the top right corner), the overlay's top right corner
   * will be aligned with that of the background frame; whereas if the overlay's anchor is at {@code
   * (0,0)} (the center), the overlay's center will be positioned at the top right corner of the
   * background frame.
   *
   * <p>The default value is {@link #DEFAULT_BACKGROUND_FRAME_ANCHOR}.
   */
  default Pair<Float, Float> getBackgroundFrameAnchor() {
    return DEFAULT_BACKGROUND_FRAME_ANCHOR;
  }

  /**
   * Returns the coordinates for the anchor point within the overlay.
   *
   * <p>The anchor point is the point inside the overlay that is placed on the {@linkplain
   * #getBackgroundFrameAnchor background frame anchor}
   *
   * <p>The coordinates are specified in Normalised Device Coordinates (NDCs) relative to the
   * overlay. The ranges for x and y are from {@code -1} to {@code 1}. The default value is {@code
   * (0,0)}, the center of the overlay.
   *
   * <p>See {@link #getBackgroundFrameAnchor} for examples of how to position an overlay.
   *
   * <p>The default value is {@link #DEFAULT_OVERLAY_FRAME_ANCHOR}.
   */
  default Pair<Float, Float> getOverlayFrameAnchor() {
    return DEFAULT_OVERLAY_FRAME_ANCHOR;
  }

  /**
   * Returns the scaling of the overlay.
   *
   * <p>The default value is {@link #DEFAULT_SCALE}.
   */
  default Pair<Float, Float> getScale() {
    return DEFAULT_SCALE;
  }

  /**
   * Returns the rotation of the overlay, counter-clockwise.
   *
   * <p>The overlay is rotated at the center of its frame.
   *
   * <p>The default value is {@link #DEFAULT_ROTATION_DEGREES}.
   */
  default float getRotationDegrees() {
    return DEFAULT_ROTATION_DEGREES;
  }

  /**
   * Returns the luminance multiplier of an SDR overlay when overlaid on a HDR frame.
   *
   * <p>Scales the luminance of the overlay to adjust the output brightness of the overlay on the
   * frame. The default value is 1, which scales the overlay colors into the standard HDR luminance
   * within the processing pipeline. Use 0.5 to scale the luminance of the overlay to SDR range, so
   * that no extra luminance is added.
   *
   * <p>Currently only supported on text overlays
   *
   * <p>The default value is {@link #DEFAULT_HDR_LUMINANCE_MULTIPLIER}.
   */
  default float getHdrLuminanceMultiplier() {
    return DEFAULT_HDR_LUMINANCE_MULTIPLIER;
  }
}
