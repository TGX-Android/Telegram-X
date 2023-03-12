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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.tool.DrawAlgorithms;

import me.vkryl.core.MathUtils;

public class CropTargetView extends View {
  private Bitmap bitmap;
  private int rotation;
  private PaintState paintState;

  public CropTargetView (Context context) {
    super(context);
  }

  public void resetState (Bitmap bitmap, int rotation, float degrees, PaintState paintState) {
    if (this.bitmap != bitmap || this.rotation != rotation || this.degrees != degrees || (this.paintState == null && paintState != null) || (this.paintState != null && paintState == null) || (this.paintState != null && !this.paintState.compare(paintState))) {
      boolean needLayout = bitmap != null && (this.bitmap == null || this.bitmap.isRecycled() || U.getWidth(this.bitmap, this.rotation) != U.getWidth(bitmap, rotation) || U.getHeight(this.bitmap, this.rotation) != U.getHeight(bitmap, rotation));
      this.bitmap = bitmap;
      this.rotation = rotation;
      this.degrees = degrees;
      this.paintState = paintState;
      if (needLayout) {
        requestLayout();
      }
      checkDegreesRotationScale();
      invalidate();
    }
  }

  public void rotateTargetBy (int degrees) {
    resetState(bitmap, MathUtils.modulo(rotation + degrees, 360), this.degrees, this.paintState);
  }

  private float degrees;

  public void setDegreesAroundCenter (float degrees) {
    if (this.degrees != degrees) {
      this.degrees = degrees;
      checkDegreesRotationScale();
    }
  }

  private float rotationScale;

  private void checkDegreesRotationScale () {
    float w = getMeasuredWidth();
    float h = getMeasuredHeight();

    if (w == 0 || h == 0) {
      return;
    }

    double rad = Math.toRadians(degrees);
    float sin = (float) Math.abs(Math.sin(rad));
    float cos = (float) Math.abs(Math.cos(rad));

    // W = w·|cos φ| + h·|sin φ|
    // H = w·|sin φ| + h·|cos φ|

    float W = w * cos + h * sin;
    float H = w * sin + h * cos;

    rotationScale = Math.max(W / w, H / h);

    updateStyles(false);
  }

  private float baseScaleX = 1f, baseScaleY = 1f;
  private float baseRotation;

  public void setBaseScale (float baseScaleX, float baseScaleY) {
    if (this.baseScaleX != baseScaleX || this.baseScaleY != baseScaleY) {
      this.baseScaleX = baseScaleX;
      this.baseScaleY = baseScaleY;
      updateStyles(false);
    }
  }

  public void setBaseRotation (float baseRotation) {
    if (this.baseRotation != baseRotation) {
      this.baseRotation = baseRotation;
      updateStyles(false);
    }
  }

  private boolean rotateInternally;

  public void setRotateInternally (boolean rotateInternally) {
    if (this.rotateInternally != rotateInternally) {
      this.rotateInternally = rotateInternally;
      updateStyles(degrees != 0f);
    }
  }

  private void updateStyles (boolean forceInvalidate) {
    float rotationScale, degrees;

    if (rotateInternally) {
      rotationScale = 1f;
      degrees = 0;
    } else {
      rotationScale = this.rotationScale;
      degrees = this.degrees;
    }

    setScaleX(baseScaleX * rotationScale);
    setScaleY(baseScaleY * rotationScale);

    setRotation(baseRotation + degrees);

    if (forceInvalidate || rotateInternally) {
      invalidate();
    }
  }

  public int getTargetWidth () {
    return U.getWidth(bitmap, rotation);
  }

  public int getTargetHeight () {
    return U.getHeight(bitmap, rotation);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (bitmap != null) {
      int availWidth = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
      int availHeight = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
      int w = getTargetWidth(), h = getTargetHeight();
      float scale = Math.min((float) availWidth / (float) w, (float) availHeight / (float) h);
      w *= scale;
      h *= scale;
      setMeasuredDimension(w, h);
      setTranslationY(availHeight / 2 - h / 2);
      checkDegreesRotationScale();
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    // c.drawColor(0xffff0000);

    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;

    final boolean saved = rotateInternally && (degrees != 0f || rotationScale != 1f);
    if (saved) {
      c.save();
      c.rotate(degrees, cx, cy);
      c.scale(rotationScale, rotationScale, cx, cy);
    }

    DrawAlgorithms.drawScaledBitmap(getMeasuredWidth(), getMeasuredHeight(), c, bitmap, rotation, paintState);

    if (saved) {
      c.restore();
    }
  }
}
