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
 * File created on 27/02/2024
 */
package org.thunderdog.challegram.mediaview.crop;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;

public class CroppedLayout extends ViewGroup {
  private int sourceWidth, sourceHeight, unappliedRotationDegrees;
  private CropState cropState;

  public CroppedLayout (Context context) {
    super(context);
    setClipChildren(true);
  }

  public void setSourceDimensions (int width, int height, int unappliedRotationDegrees) {
    if (this.sourceWidth != width || this.sourceHeight != height || this.unappliedRotationDegrees != unappliedRotationDegrees) {
      this.sourceWidth = width;
      this.sourceHeight = height;
      this.unappliedRotationDegrees = unappliedRotationDegrees;
      requestLayout();
      invalidate();
    }
  }

  public void setCropState (CropState cropState) {
    if (cropState != null && cropState.isEmpty()) {
      cropState = null;
    }
    if ((this.cropState == null && cropState != null) || (this.cropState != null && !this.cropState.equals(cropState))) {
      this.cropState = cropState != null ? new CropState(cropState) : null;
      requestLayout();
      invalidate();
    }
  }

  private int renderWidth, renderHeight;

  public void onPreMeasure (int canvasWidth, int canvasHeight,
                            int viewportWidth, int viewportHeight,
                            int renderWidth, int renderHeight) {
    this.renderWidth = renderWidth;
    this.renderHeight = renderHeight;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int parentWidth = getMeasuredWidth();
    final int parentHeight = getMeasuredHeight();
    final int viewCount = getChildCount();
    for (int i = 0; i < viewCount; i++) {
      View view = getChildAt(i);
      ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
      if (layoutParams != null && (layoutParams.width == LayoutParams.WRAP_CONTENT && layoutParams.height == LayoutParams.WRAP_CONTENT)) {
        Log.v("renderView: %dx%d, parent: %dx%d", renderWidth, renderHeight, parentWidth, parentHeight);
        view.measure(
          MeasureSpec.makeMeasureSpec(renderWidth, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(renderHeight, MeasureSpec.EXACTLY)
        );
      } else {
        measureChild(view, widthMeasureSpec, heightMeasureSpec);
      }
    }
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    final int childCount = getChildCount();
    final int parentWidth = getMeasuredWidth();
    final int parentHeight = getMeasuredHeight();
    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      int width = view.getMeasuredWidth();
      int height = view.getMeasuredHeight();
      int pivotX, pivotY;
      int croppedWidth, croppedHeight;
      int rotateBy = (cropState != null ? cropState.getRotateBy() : 0) + unappliedRotationDegrees;
      boolean isRotated = U.isRotated(rotateBy);
      if (cropState != null && !cropState.isRegionEmpty()) {
        double left, top, right, bottom;
        switch (cropState.getRotateBy()) {
          case 0: {
            left = cropState.getLeft();
            top = cropState.getTop();
            right = cropState.getRight();
            bottom = cropState.getBottom();
            break;
          }
          case 90: {
            left = cropState.getTop();
            top = 1.0 - cropState.getRight();
            right = cropState.getBottom();
            bottom = 1.0 - cropState.getLeft();
            break;
          }
          case 180: {
            left = 1.0 - cropState.getRight();
            top = 1.0 - cropState.getBottom();
            right = 1.0 - cropState.getLeft();
            bottom = 1.0 - cropState.getTop();
            break;
          }
          case 270: {
            left = 1.0 - cropState.getBottom();
            top = cropState.getLeft();
            right = 1.0 - cropState.getTop();
            bottom = cropState.getRight();
            break;
          }
          default:
            throw new IllegalStateException();
        }

        pivotX = (int) (width * (left + (right - left) / 2.0));
        pivotY = (int) (height * (top + (bottom - top) / 2.0));

        croppedWidth = (int) ((isRotated ? height : width) * cropState.getRegionWidth());
        croppedHeight = (int) ((isRotated ? width : height) * cropState.getRegionHeight());
      } else {
        if (isRotated) {
          croppedWidth = height;
          croppedHeight = width;
        } else {
          croppedWidth = width;
          croppedHeight = height;
        }
        pivotX = width / 2;
        pivotY = height / 2;
      }
      int left = parentWidth / 2 - pivotX;
      int top = parentHeight / 2 - pivotY;
      view.layout(left, top, left + width, top + height);
      float scale = Math.max(
        (float) parentWidth / (float) croppedWidth,
        (float) parentHeight / (float) croppedHeight
      );
      view.setPivotX(pivotX);
      view.setPivotY(pivotY);
      view.setScaleX(scale);
      view.setScaleY(scale);
      view.setRotation(rotateBy);
    }
  }
}
