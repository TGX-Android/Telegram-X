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
 * File created on 09/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;

public class MediaViewThumbLocation {
  public int left, top, right, bottom;
  public int clipLeft, clipTop, clipRight, clipBottom;
  public float topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius;

  private static final int FLAG_NO_BOUNCE = 0x01;
  private static final int FLAG_NO_PLACEHOLDER = 0x02;

  private int flags;

  public MediaViewThumbLocation () { }

  public MediaViewThumbLocation (int left, int top, int right, int bottom) {
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  public void setNoBounce () {
    this.flags |= FLAG_NO_BOUNCE;
  }

  public boolean noBounce () {
    return (flags & FLAG_NO_BOUNCE) != 0;
  }

  public void set (int left, int top, int right, int bottom) {
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  public void setClip (int left, int top, int right, int bottom) {
    this.clipLeft = left;
    this.clipTop = top;
    this.clipRight = right;
    this.clipBottom = bottom;
  }

  public int centerX () {
    return (left + right) / 2;
  }

  public int centerY () {
    return (top + bottom) / 2;
  }

  public int width () {
    return (right - clipRight) - (left + clipLeft);
  }

  public int height () {
    return (bottom - clipBottom) - (top + clipTop);
  }

  public int clippedLeft () {
    return left + clipLeft;
  }

  public int clippedRight () {
    return right - clipRight;
  }

  public int clippedTop () {
    return top + clipTop;
  }

  public int clippedBottom () {
    return bottom - clipBottom;
  }

  private @ColorId
  int colorId = ColorId.filling;

  public void setColorId (int colorId) {
    this.colorId = colorId;
  }

  public void setNoPlaceholder () {
    flags |= FLAG_NO_PLACEHOLDER;
  }

  public boolean noPlaceholder () {
    return (flags & FLAG_NO_PLACEHOLDER) != 0;
  }

  private Path path;

  public void setRoundings (float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {
    if (this.topLeftRadius != topLeftRadius || this.topRightRadius != topRightRadius || this.bottomRightRadius != bottomRightRadius || this.bottomLeftRadius != bottomLeftRadius) {
      this.topLeftRadius = topLeftRadius;
      this.topRightRadius = topRightRadius;
      this.bottomRightRadius = bottomRightRadius;
      this.bottomLeftRadius = bottomLeftRadius;
    }
  }

  public void setTopLeftRadius (int topLeftRadius) {
    this.topLeftRadius = topLeftRadius;
  }

  public void setTopRightRadius (int topRightRadius) {
    this.topRightRadius = topRightRadius;
  }

  public void setBottomRightRadius (int bottomRightRadius) {
    this.bottomRightRadius = bottomRightRadius;
  }

  public void setBottomLeftRadius (int bottomLeftRadius) {
    this.bottomLeftRadius = bottomLeftRadius;
  }

  public void setRoundings (int radius) {
    setRoundings(radius, radius, radius, radius);
  }

  private int lastLeft, lastTop, lastRight, lastBottom;
  private float lastTopLeft, lastTopRight, lastBottomRight, lastBottomLeft;

  private void preparePath (int left, int top, int right, int bottom) {
    if (path == null ||
        lastLeft != left || lastTop != top || lastRight != right || lastBottom != bottom ||
        lastTopLeft != topLeftRadius || lastTopRight != topRightRadius || lastBottomRight != bottomRightRadius || lastBottomLeft != bottomLeftRadius) {
      lastLeft = left;
      lastTop = top;
      lastRight = right;
      lastBottom = bottom;

      lastTopLeft = topLeftRadius;
      lastTopRight = topRightRadius;
      lastBottomRight = bottomRightRadius;
      lastBottomLeft = bottomLeftRadius;


      if (path == null) {
        path = new Path();
      } else {
        path.reset();
      }
      RectF rectF = Paints.getRectF();
      rectF.set(left, top, right, bottom);
      DrawAlgorithms.buildPath(path, rectF, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
    }
  }

  public float getRadius () {
    return bottomLeftRadius == bottomRightRadius && bottomLeftRadius == topLeftRadius && bottomLeftRadius == topRightRadius ? bottomLeftRadius : 0;
  }

  public void drawPlaceholder (Canvas c) {
    if (!noPlaceholder()) {
      int left = this.left + clipLeft;
      int top = this.top + clipTop;
      int right = this.right - clipRight;
      int bottom = this.bottom - clipBottom;
      Paint paint = Paints.fillingPaint(Theme.getColor(colorId));
      if (topLeftRadius == topRightRadius && topLeftRadius == bottomRightRadius && topLeftRadius == bottomLeftRadius) {
        if (topLeftRadius == 0) {
          c.drawRect(left, top, right, bottom, paint);
        } else {
          RectF rectF = Paints.getRectF();
          rectF.set(left, top, right, bottom);
          c.drawRoundRect(rectF, topLeftRadius, topLeftRadius, paint);
        }
      } else {
        preparePath(left, top, right, bottom);
        c.drawPath(path, paint);
      }
    }
  }
}
