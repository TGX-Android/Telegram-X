package org.thunderdog.challegram.theme;

import android.graphics.Color;

import androidx.annotation.Nullable;

import java.util.Arrays;

import me.vkryl.core.ColorUtils;

/**
 * Date: 25/11/2018
 * Author: default
 */
public class StackColor {
  public boolean canModify;

  public int value;
  public final float[] hsv = new float[3];

  public StackColor (int color) {
    this(color, false, null);
  }

  public StackColor (int color, @Nullable StackColor parent) {
    this(color, true, parent);
  }

  public StackColor (int color, boolean canModify, @Nullable StackColor parent) {
    this.canModify = canModify;
    if (canModify && parent != null) {
      this.value = parent.value;
      this.hsv[0] = parent.hsv[0];
      this.hsv[1] = parent.hsv[1];
      this.hsv[2] = parent.hsv[2];
      setValue(color);
    } else {
      this.value = color;
      Color.colorToHSV(color, hsv);
    }
  }

  public void setValue (int value) {
    if (ColorUtils.color(255, this.value) == ColorUtils.color(255, value)) {
      // Only alpha has changed
      this.value = value;
    } else if (this.value != value) {
      this.value = value;

      // Trying to use same hue
      float prevHue = hsv[0];
      Color.colorToHSV(value, hsv);
      if (hsv[0] != prevHue) {
        float tempHue = hsv[0];
        hsv[0] = prevHue;
        if (Color.HSVToColor(Color.alpha(value), hsv) != value) {
          hsv[0] = tempHue;
        }
      }
    }
  }

  public boolean setHsv (int prop, float hsv) {
    if (this.hsv[prop] != hsv) {
      this.hsv[prop] = hsv;
      this.value = Color.HSVToColor(Color.alpha(value), this.hsv);
      return true;
    }
    return false;
  }

  @Override
  public boolean equals (@Nullable Object obj) {
    if (obj instanceof StackColor) {
      StackColor c = (StackColor) obj;
      return value == c.value && Arrays.equals(hsv, c.hsv);
    }
    return false;
  }
}
