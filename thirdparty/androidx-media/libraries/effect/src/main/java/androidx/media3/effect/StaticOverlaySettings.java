package androidx.media3.effect;

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
import static androidx.media3.common.util.Assertions.checkArgument;

import android.util.Pair;
import androidx.annotation.FloatRange;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.util.UnstableApi;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** A statically valued {@link OverlaySettings}. */
@UnstableApi
public final class StaticOverlaySettings implements OverlaySettings {

  /** A builder for {@link StaticOverlaySettings} instances. */
  public static final class Builder {
    private float alphaScale;
    private Pair<Float, Float> backgroundFrameAnchor;
    private Pair<Float, Float> overlayFrameAnchor;
    private Pair<Float, Float> scale;
    private float rotationDegrees;
    private float hdrLuminanceMultiplier;

    /** Creates a new {@link Builder}. */
    public Builder() {
      alphaScale = DEFAULT_ALPHA_SCALE;
      backgroundFrameAnchor = DEFAULT_BACKGROUND_FRAME_ANCHOR;
      overlayFrameAnchor = DEFAULT_OVERLAY_FRAME_ANCHOR;
      scale = DEFAULT_SCALE;
      rotationDegrees = DEFAULT_ROTATION_DEGREES;
      hdrLuminanceMultiplier = DEFAULT_HDR_LUMINANCE_MULTIPLIER;
    }

    /**
     * Sets the alpha scale value of the overlay, altering its translucency.
     *
     * @see OverlaySettings#getAlphaScale()
     */
    @CanIgnoreReturnValue
    public Builder setAlphaScale(@FloatRange(from = 0) float alphaScale) {
      checkArgument(0 <= alphaScale, "alphaScale needs to be greater than or equal to zero.");
      this.alphaScale = alphaScale;
      return this;
    }

    /**
     * Sets the coordinates for the anchor point of the overlay within the background frame.
     *
     * @see OverlaySettings#getBackgroundFrameAnchor()
     */
    @CanIgnoreReturnValue
    public Builder setBackgroundFrameAnchor(
        @FloatRange(from = -1, to = 1) float x, @FloatRange(from = -1, to = 1) float y) {
      checkArgument(-1 <= x && x <= 1);
      checkArgument(-1 <= y && y <= 1);
      this.backgroundFrameAnchor = Pair.create(x, y);
      return this;
    }

    /**
     * Sets the coordinates for the anchor point within the overlay.
     *
     * @see OverlaySettings#getOverlayFrameAnchor()
     */
    @CanIgnoreReturnValue
    public Builder setOverlayFrameAnchor(
        @FloatRange(from = -1, to = 1) float x, @FloatRange(from = -1, to = 1) float y) {
      checkArgument(-1 <= x && x <= 1);
      checkArgument(-1 <= y && y <= 1);
      this.overlayFrameAnchor = Pair.create(x, y);
      return this;
    }

    /**
     * Sets the scaling of the overlay.
     *
     * @see OverlaySettings#getScale()
     */
    @CanIgnoreReturnValue
    public Builder setScale(float x, float y) {
      this.scale = Pair.create(x, y);
      return this;
    }

    /**
     * Sets the rotation of the overlay, counter-clockwise.
     *
     * @see OverlaySettings#getRotationDegrees()
     */
    @CanIgnoreReturnValue
    public Builder setRotationDegrees(float rotationDegree) {
      this.rotationDegrees = rotationDegree;
      return this;
    }

    /**
     * Set the luminance multiplier of an SDR overlay when overlaid on a HDR frame.
     *
     * @see OverlaySettings#getHdrLuminanceMultiplier()
     */
    @CanIgnoreReturnValue
    public Builder setHdrLuminanceMultiplier(float hdrLuminanceMultiplier) {
      this.hdrLuminanceMultiplier = hdrLuminanceMultiplier;
      return this;
    }

    /** Creates an instance of {@link StaticOverlaySettings}, using defaults if values are unset. */
    public StaticOverlaySettings build() {
      return new StaticOverlaySettings(
          alphaScale,
          backgroundFrameAnchor,
          overlayFrameAnchor,
          scale,
          rotationDegrees,
          hdrLuminanceMultiplier);
    }
  }

  private final float alphaScale;
  private final Pair<Float, Float> backgroundFrameAnchor;
  private final Pair<Float, Float> overlayFrameAnchor;
  private final Pair<Float, Float> scale;
  private final float rotationDegrees;
  private final float hdrLuminanceMultiplier;

  private StaticOverlaySettings(
      float alphaScale,
      Pair<Float, Float> backgroundFrameAnchor,
      Pair<Float, Float> overlayFrameAnchor,
      Pair<Float, Float> scale,
      float rotationDegrees,
      float hdrLuminanceMultiplier) {
    this.alphaScale = alphaScale;
    this.backgroundFrameAnchor = backgroundFrameAnchor;
    this.overlayFrameAnchor = overlayFrameAnchor;
    this.scale = scale;
    this.rotationDegrees = rotationDegrees;
    this.hdrLuminanceMultiplier = hdrLuminanceMultiplier;
  }

  @Override
  public float getAlphaScale() {
    return alphaScale;
  }

  @Override
  public Pair<Float, Float> getBackgroundFrameAnchor() {
    return backgroundFrameAnchor;
  }

  @Override
  public Pair<Float, Float> getOverlayFrameAnchor() {
    return overlayFrameAnchor;
  }

  @Override
  public Pair<Float, Float> getScale() {
    return scale;
  }

  @Override
  public float getRotationDegrees() {
    return rotationDegrees;
  }

  @Override
  public float getHdrLuminanceMultiplier() {
    return hdrLuminanceMultiplier;
  }
}
