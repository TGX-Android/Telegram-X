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
 * File created on 18/10/2017
 */
package org.thunderdog.challegram.mediaview.crop;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;

public class CropState {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    Flags.MIRROR_HORIZONTALLY,
    Flags.MIRROR_VERTICALLY
  }, flag = true)
  public @interface Flags {
    int
      MIRROR_HORIZONTALLY = 1,
      MIRROR_VERTICALLY = 1 << 1;
  }

  private double left = 0.0, top = 0.0, right = 1.0, bottom = 1.0;
  private int rotateBy = 0;
  private float degreesAroundCenter = 0;
  private @Flags int flags;

  public CropState () { }

  public CropState (double left, double top, double right, double bottom, int rotateBy, float degreesAroundCenter, int flags) {
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
    this.rotateBy = rotateBy;
    this.degreesAroundCenter = degreesAroundCenter;
    this.flags = flags;
  }

  public CropState (CropState copy) {
    set(copy);
  }

  public void set (CropState copy) {
    if (copy != null) {
      this.left = copy.left;
      this.top = copy.top;
      this.right = copy.right;
      this.bottom = copy.bottom;
      this.rotateBy = copy.rotateBy;
      this.degreesAroundCenter = copy.degreesAroundCenter;
      this.flags = copy.flags;
    } else {
      this.left = 0.0;
      this.top = 0.0;
      this.right = 1.0;
      this.bottom = 1.0;
      this.rotateBy = 0;
      this.degreesAroundCenter = 0.0f;
      this.flags = 0;
    }
  }

  @Override
  @NonNull
  public String toString () {
    return CropStateParser.toParsableString(this);
  }

  public boolean isEmpty () {
    return isRegionEmpty() && rotateBy == 0 && degreesAroundCenter == 0 && flags == 0;
  }

  public boolean isRegionEmpty () {
    return left == 0.0 && right == 1.0 && top == 0.0 && bottom == 1.0;
  }

  public double getRegionWidth () {
    return (right - left);
  }

  public double getRegionCenterX () {
    return left + (right - left) / 2.0;
  }

  public double getRegionHeight () {
    return (bottom - top);
  }

  public double getRegionCenterY () {
    return top + (bottom - top) / 2.0;
  }

  public boolean hasRotations () {
    return rotateBy != 0 || degreesAroundCenter != 0;
  }

  public boolean hasFlag (int flag) {
    return BitwiseUtils.hasAllFlags(flags, flag);
  }

  public boolean needMirror () {
    return BitwiseUtils.hasFlag(flags, Flags.MIRROR_HORIZONTALLY | Flags.MIRROR_VERTICALLY);
  }

  public boolean needMirrorHorizontally () {
    return hasFlag(Flags.MIRROR_HORIZONTALLY);
  }

  public boolean needMirrorVertically () {
    return hasFlag(Flags.MIRROR_VERTICALLY);
  }

  public int getFlags () {
    return flags;
  }

  public boolean compare (CropState state) {
    if (state == null) {
      return isEmpty();
    }
    return
      this.left == state.left &&
      this.top == state.top &&
      this.right == state.right &&
      this.bottom == state.bottom &&
      this.rotateBy == state.rotateBy &&
      this.degreesAroundCenter == state.degreesAroundCenter &&
      this.flags == state.flags;
  }

  @Override
  public boolean equals (Object obj) {
    return this == obj || (obj instanceof CropState && compare((CropState) obj));
  }

  protected void onCropChanged (boolean rectChanged) { }

  public int rotateBy (int rotateDelta) {
    int rotateBy = MathUtils.modulo(this.rotateBy + rotateDelta, 360);
    if (this.rotateBy != rotateBy) {
      this.rotateBy = rotateBy;
      onCropChanged((right - left) != (bottom - top));
    }
    return rotateBy;
  }

  public void setDegreesAroundCenter (float degreesAroundCenter) {
    if (this.degreesAroundCenter != degreesAroundCenter) {
      this.degreesAroundCenter = degreesAroundCenter;
      onCropChanged(false);
    }
  }

  public void setFlags (int flags) {
    if (this.flags != flags) {
      this.flags = flags;
      onCropChanged(false);
    }
  }

  public double getLeft () {
    return left;
  }

  public double getTop () {
    return top;
  }

  public double getRight () {
    return right;
  }

  public double getBottom () {
    return bottom;
  }

  public int getRotateBy () {
    return rotateBy;
  }

  public float getDegreesAroundCenter () {
    return degreesAroundCenter;
  }

  public void setRect (double left, double top, double right, double bottom) {
    if (this.left != left || this.top != top || this.right != right || this.bottom != bottom) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
      onCropChanged(true);
    }
  }
}
