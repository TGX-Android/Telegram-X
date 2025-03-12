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
 * File created on 25/02/2024
 */
package org.thunderdog.challegram.mediaview.crop;

import android.annotation.TargetApi;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.effect.Crop;
import androidx.media3.effect.ScaleAndRotateTransformation;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class CropEffectFactory {
  private CropEffectFactory () { }

  public static boolean needCropRegionEffect (@Nullable CropState cropState) {
    return cropState != null && !cropState.isRegionEmpty();
  }

  public static Crop createCropRegionEffect (@NonNull CropState cropState) {
    return createCropRegionEffect(cropState, false);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  public static Crop createCropRegionEffect (@NonNull CropState cropState, boolean includeRotation) {
    if (cropState.isRegionEmpty())
      throw new IllegalArgumentException();
    // CropState zero: left = 0.0, right = 1.0, top = 0.0, bottom = 1.0
    double left = cropState.getLeft();
    double right = cropState.getRight();
    double top = cropState.getTop();
    double bottom = cropState.getBottom();
    if (includeRotation && cropState.getRotateBy() != 0f) {
      double prevLeft = left;
      double prevTop = top;
      double prevRight = right;
      double prevBottom = bottom;
      int rotateBy = cropState.getRotateBy();
      switch (rotateBy) {
        case 90:
          left = prevTop;
          bottom = 1.0 - prevLeft;
          right = prevBottom;
          top = 1.0 - prevRight;
          break;
        case 180:
          bottom = 1.0 - prevTop;
          top = 1.0 - prevBottom;
          break;
        case 270:
          left = 1.0 - prevBottom;
          bottom = prevRight;
          top = prevLeft;
          right = 1.0 - prevTop;
          break;
      }
    }
    // NDC zero: left = -1.0, right = 1.0, top = 1.0, bottom = -1.0
    float ndcLeft = (float) (-1.0 + (left * 2.0));
    float ndcRight = (float) (-1.0 + (right * 2.0));
    float ndcTop = (float) -(-1.0 + (top * 2.0));
    float ndcBottom = (float) -(-1.0 + (bottom * 2.0));
    return new Crop(ndcLeft, ndcRight, ndcBottom, ndcTop);
  }

  private static int getRotationDegrees (@Nullable CropState cropState, int exifOrientation) {
    int rotationDegrees = (exifOrientation + (cropState != null ? cropState.getRotateBy() : 0)) % 360;
    if (rotationDegrees < 0)
      rotationDegrees += 360;
    return rotationDegrees;
  }

  public static boolean needScaleAndRotateEffect (@Nullable CropState cropState, int exifOrientation) {
    return
      getRotationDegrees(cropState, exifOrientation) != 0 ||
      (cropState != null && cropState.needMirror());
  }

  public static ScaleAndRotateTransformation createScaleAndRotateEffect (@Nullable CropState cropState, int exifOrientation) {
    boolean hasMirrorEffects = cropState != null && cropState.needMirror();
    int rotationDegrees = getRotationDegrees(cropState, exifOrientation);
    if (rotationDegrees == 0 && !hasMirrorEffects)
      throw new IllegalArgumentException();
    ScaleAndRotateTransformation.Builder scaleBuilder = new ScaleAndRotateTransformation.Builder();
    if (rotationDegrees != 0) {
      scaleBuilder.setRotationDegrees(360 - rotationDegrees);
    }
    if (hasMirrorEffects) {
      scaleBuilder.setScale(
        cropState.needMirrorHorizontally() ? -1.0f : 1.0f,
        cropState.needMirrorVertically() ? -1.0f : 1.0f
      );
    }
    return scaleBuilder.build();
  }
}
