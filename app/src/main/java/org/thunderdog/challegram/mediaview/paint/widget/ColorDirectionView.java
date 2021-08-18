package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.MathUtils;

/**
 * Date: 11/5/17
 * Author: default
 */

public class ColorDirectionView extends View {
  private float factor;
  private float h, s, v;

  public ColorDirectionView (Context context) {
    super(context);
  }

  public void setHsv (float h, float[] hsv) {
    if (this.h != h || this.s != hsv[1] || this.v != hsv[2]) {
      this.h = h;
      this.s = hsv[1];
      this.v = hsv[2];
      if (factor > 0f) {
        invalidate();
      }
    }
  }

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  private int pickerWidth;
  private int pickerLeft;

  public void setPickerLeft (int left) {
    if (this.pickerLeft != left) {
      this.pickerLeft = left;
      if (factor > 0f) {
        invalidate();
      }
    }
  }

  public void setPickerWidth (int width) {
    if (this.pickerWidth != width) {
      this.pickerWidth = width;
      if (factor > 0f) {
        invalidate();
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (factor == 0f) {
      return;
    }

    final float viewWidth = getMeasuredWidth();
    final float viewHeight = getMeasuredHeight();

    final float areaHeight = viewHeight - getPaddingTop();

    final float fromX = pickerLeft + pickerWidth * h;
    final float fromY = viewHeight;

    final float toX = viewWidth * s;
    final float toY = viewHeight - Screen.dp(18f) - (areaHeight- Screen.dp(18f)) * v;

    final float smallRadius = Screen.dp(1f);
    final float bigRadius = Screen.dp(3f);

    final int color = 0xe0ffffff;

    final float spacing = Screen.dp(6f);

    float distance = MathUtils.distance(fromX, fromY, toX, toY);

    int circleCount = (int) Math.floor(distance / spacing);

    float diffX = (toX - fromX) / circleCount;
    float diffY = (toY - fromY) / circleCount;

    float half = Screen.dp(48f) / 2;
    float toX2 = Math.max(half + Screen.dp(8f), Math.min(viewWidth - half - Screen.dp(8f), toX));
    float toY2 = toY - Screen.dp(ColorPreviewView.MARGIN_DISTANCE) + half;

    float distance2 = MathUtils.distance(toX, toY, toX2, toY2);
    int circleCount2 = (int) Math.floor(distance2 / spacing);

    float factorPerCircle = 1f / (circleCount + circleCount2 + 1f);
    float remainingFactor = factor;

    float remainingDistance = Math.abs(distance - distance / circleCount);
    float cx, cy;

    cx = fromX + diffX;
    cy = fromY + diffY;
    for (int i = 1; i < circleCount; i++) {
      float circleFactor = remainingFactor > factorPerCircle ? 1f : remainingFactor / factorPerCircle;

      c.drawCircle(cx, cy, smallRadius * circleFactor, Paints.fillingPaint(color));

      cx += diffX;
      cy += diffY;

      remainingDistance -= Math.abs(distance / circleCount);
      remainingFactor -= factorPerCircle;

      if (remainingFactor <= 0) {
        return;
      }

      if (remainingDistance < bigRadius * 2) {
        break;
      }
    }

    float pointFactor = remainingFactor > factorPerCircle ? 1f : remainingFactor / factorPerCircle;
    c.drawCircle(toX, toY, bigRadius * pointFactor, Paints.fillingPaint(color));
    remainingFactor -= factorPerCircle;

    remainingDistance = Math.abs(distance2 - distance2 / circleCount2);
    diffX = (toX2 - toX) / circleCount2;
    diffY = (toY2 - toY) / circleCount2;

    cx = toX + diffX;
    cy = toY + diffY - bigRadius;

    for (int i = 0; i < circleCount2; i++) {
      float circleFactor = remainingFactor > factorPerCircle ? 1f : remainingFactor / factorPerCircle;

      c.drawCircle(cx, cy, smallRadius * circleFactor, Paints.fillingPaint(color));

      cx += diffX;
      cy += diffY;

      remainingDistance -= Math.abs(distance2 / circleCount2);
      remainingFactor -= factorPerCircle;

      if (remainingFactor <= 0f) {
        return;
      }
      if (remainingDistance < smallRadius * 2) {
        break;
      }
    }
  }
}
