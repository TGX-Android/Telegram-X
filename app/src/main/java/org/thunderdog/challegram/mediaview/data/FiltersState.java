/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview.data;

import android.util.SparseIntArray;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;

public class FiltersState {
  public static final int BLUR_TYPE_NONE = 0;
  public static final int BLUR_TYPE_RADIAL = 1;
  public static final int BLUR_TYPE_LINEAR = 2;

  // Keys

  public static final int KEY_ENHANCE = 0;
  public static final int KEY_EXPOSURE = 1;
  public static final int KEY_CONTRAST = 2;
  public static final int KEY_WARMTH = 3;
  public static final int KEY_SATURATION = 4;

  public static final int KEY_FADE = 5;
  public static final int KEY_VIGNETTE = 6;
  public static final int KEY_GRAIN = 7;
  public static final int KEY_SHARPEN = 8;

  public static final int KEY_SHADOWS = 10;
  public static final int KEY_SHADOWS_COLOR_ID = 11;
  public static final int KEY_HIGHLIGHTS = 12;
  public static final int KEY_HIGHLIGHTS_COLOR_ID = 13;

  public static final int KEY_BLUR_TYPE = 14;
  // public static final int KEY_BLUR_STRENGTH = 15;

  // Blur types

  // Tint stuff

  public static final int[] SHADOWS_TINT_COLOR_IDS = {
    0,
    R.id.theme_color_photoShadowTint1,
    R.id.theme_color_photoShadowTint2,
    R.id.theme_color_photoShadowTint3,
    R.id.theme_color_photoShadowTint4,
    R.id.theme_color_photoShadowTint5,
    R.id.theme_color_photoShadowTint6,
    R.id.theme_color_photoShadowTint7
  };

  public static final int[] HIGHLIGHTS_TINT_COLOR_IDS = {
    0,
    R.id.theme_color_photoHighlightTint1,
    R.id.theme_color_photoHighlightTint2,
    R.id.theme_color_photoHighlightTint3,
    R.id.theme_color_photoHighlightTint4,
    R.id.theme_color_photoHighlightTint5,
    R.id.theme_color_photoHighlightTint6,
    R.id.theme_color_photoHighlightTint7,
  };

  // Implementation

  private final SparseIntArray data;
  private long sessionId;
  private boolean isPrivate;

  public FiltersState () {
    this.data = new SparseIntArray(15);
    for (int i = 0; i < 15; i++) {
      data.put(i, 0);
    }
  }

  public FiltersState (FiltersState source) {
    this.data = new SparseIntArray(source.data.size());
    reset(source);
  }

  @Override
  @NonNull
  public String toString () {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < data.size(); i++) {
      if (i != 0) {
        b.append("|");
      }
      b.append(data.valueAt(i));
    }
    return b.toString();
  }

  public long getSessionId () {
    return sessionId;
  }

  public boolean isPrivateSession () {
    return isPrivate;
  }

  public void setSessionId (boolean isPrivate, long sessionId) {
    this.isPrivate = isPrivate;
    this.sessionId = sessionId;
  }

  public void reset (FiltersState source) {
    this.isPrivate = source.isPrivate;
    this.sessionId = source.sessionId;
    final int size = source.data.size();
    for (int i = 0; i < size; i++) {
      this.data.append(source.data.keyAt(i), source.data.valueAt(i));
    }
  }

  public boolean compare (FiltersState to) {
    if (to.blurExcludeSize != blurExcludeSize || to.blurExcludeBlurSize != blurExcludeBlurSize || to.blurExcludeX != blurExcludeX || to.blurExcludeY != blurExcludeY || to.blurAngle != blurAngle) {
      return false;
    }
    if (to.data.size() != data.size()) {
      return false;
    }
    final int size = to.data.size();
    for (int i = 0; i < size; i++) {
      int key = to.data.keyAt(i);
      int value = to.data.valueAt(i);
      if (value != data.get(key)) {
        return false;
      }
    }
    return true;
  }

  public boolean isEmpty () {
    int size = data.size();
    for (int i = 0; i < size; i++) {
      if (data.valueAt(i) != 0) {
        return false;
      }
    }
    return true;
  }

  public boolean setValue (int key, int value) {
    int currentValue = getValue(key);
    if (currentValue != value) {
      data.put(key, value);
      return true;
    }
    return false;
  }

  public boolean setFactor (int key, float factor) {
    return setValue(key, Math.round(factor * 100f));
  }

  public int getValue (int key) {
    return data.get(key);
  }

  public float getFactor (int key) {
    return (float) data.get(key) / 100f;
  }

  // Blur state

  private float blurExcludeSize, blurExcludeBlurSize, blurExcludeX, blurExcludeY, blurAngle;

  public float getBlurAngle () {
    return blurAngle;
  }

  public float getBlurExcludeY () {
    return blurExcludeY;
  }

  public float getBlurExcludeX () {
    return blurExcludeX;
  }

  public float getBlurExcludeBlurSize () {
    return blurExcludeBlurSize;
  }

  public float getBlurExcludeSize () {
    return blurExcludeSize;
  }

  // Utils

  public static boolean canBeNegative (int key) {
    switch (key) {
      case KEY_EXPOSURE:
      case KEY_CONTRAST:
      case KEY_WARMTH:
      case KEY_SATURATION:

      case KEY_SHADOWS:
      case KEY_HIGHLIGHTS: {
        return true;
      }
    }
    return false;
  }
}
