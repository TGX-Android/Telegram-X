/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 14/02/2016 at 22:17
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class CheckBoxView extends View {
  private final BoolAnimator isChecked = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);
  private final BoolAnimator isHidden = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);
  private final BoolAnimator isDisabled = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);
  private final BoolAnimator isIntermediate = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);

  private final Path path;
  private final RectF rect;
  private final Paint outerPaint;

  private final PointF pointA, pointB, pointC;

  public CheckBoxView (Context context) {
    super(context);

    outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    outerPaint.setColor(Theme.radioOutlineColor());
    outerPaint.setStyle(Paint.Style.STROKE);

    path = new Path();
    rect = new RectF();

    pointA = new PointF();
    pointB = new PointF();
    pointC = new PointF();
  }

  public void setChecked (boolean checked, final boolean animated) {
    isChecked.setValue(checked, animated);
  }

  public void setIntermediate (boolean intermediate, final boolean animated) {
    isIntermediate.setValue(intermediate, animated);
  }

  public void setHidden (boolean hidden, final boolean animated) {
    isHidden.setValue(hidden, animated);
  }

  public boolean toggle () {
    return isChecked.toggleValue(true);
  }

  public void setDisabled (boolean disabled, boolean animated) {
    isDisabled.setValue(disabled, animated);
  }

  // Internal

  private static final float FACTOR_DIFF = .65f;
  private static final float SCALE_DIFF = .15f;

  @Override
  protected void onDraw (Canvas c) {
    final float showFactor = 1f - isHidden.getFloatValue();

    if (showFactor == 0f) {
      return;
    }

    final float factor = isChecked.getFloatValue();
    final int alpha = (int) (255f * showFactor);

    final int lineSize = Screen.dp(1.5f);

    float rectFactor = Math.min(factor / FACTOR_DIFF, 1f);
    float checkFactor = factor <= FACTOR_DIFF ? 0f : (factor - FACTOR_DIFF) / (1f - FACTOR_DIFF);
    float scaleFactor = 1f - (rectFactor == 1f ? 1f - checkFactor : rectFactor) * SCALE_DIFF;

    final int radius = Screen.dp(2f);
    final int offset = (int) ((float) radius * .5f);

    outerPaint.setStrokeWidth(radius);

    int size = Math.min(getMeasuredWidth(), getMeasuredHeight());

    rect.left = offset;
    rect.top = offset;
    rect.right = size - offset;
    rect.bottom = size - offset;

    float cx = rect.centerX();
    float cy = rect.centerY();

    final int restoreToCount = Views.save(c);
    c.scale(scaleFactor, scaleFactor, cx, cy);

    int color = ColorUtils.fromToArgb(Theme.radioOutlineColor(), Theme.radioFillingColor(), rectFactor * (1f - isDisabled.getFloatValue()));
    outerPaint.setColor(color);
    outerPaint.setAlpha(alpha);
    c.drawRoundRect(rect, radius, radius, outerPaint);

    if (rectFactor != 0f) {
      int w = (int) ((rect.width() - offset * 2) * .5f * rectFactor);
      int h = (int) ((rect.height() - offset * 2) * .5f * rectFactor);

      int left = (int) (rect.left + offset + w);
      int right = (int) (rect.right - offset - w);

      final int alphaColor = ColorUtils.alphaColor(showFactor, color);

      c.drawRect(rect.left + offset, rect.top + offset, left, rect.bottom - offset, Paints.fillingPaint(alphaColor));
      c.drawRect(right, rect.top + offset, rect.right - offset, rect.bottom - offset, Paints.fillingPaint(alphaColor));
      c.drawRect(left, rect.top + offset, right, rect.top + offset + h, Paints.fillingPaint(alphaColor));
      c.drawRect(left, rect.bottom - offset - h, right, rect.bottom - offset, Paints.fillingPaint(alphaColor));

      if (checkFactor != 0f) {
        final int checkColor = ColorUtils.alphaColor(showFactor, Theme.radioCheckColor());
        final float intermediateFactor = isIntermediate.getFloatValue();

        final Paint strokePaint = Paints.strokeBigPaint(checkColor);
        final float strokeWidth = strokePaint.getStrokeWidth();
        strokePaint.setStrokeWidth(lineSize);

        pointA.x = 3f / 16f * size;
        pointA.y = (17f - intermediateFactor) / 32f * size;

        pointB.x = 13f / 16f * size;
        pointB.y = (9f + 7f * intermediateFactor) / 32f * size;

        pointC.x = (6f + 2f * intermediateFactor) / 16f * size;
        pointC.y = (23f - 7f * intermediateFactor) / 32f * size;

        pointA.x = pointC.x + (pointA.x - pointC.x) * checkFactor;
        pointA.y = pointC.y + (pointA.y - pointC.y) * checkFactor;

        pointB.x = pointC.x + (pointB.x - pointC.x) * checkFactor;
        pointB.y = pointC.y + (pointB.y - pointC.y) * checkFactor;

        path.rewind();
        path.moveTo(pointA.x, pointA.y);
        path.lineTo(pointC.x, pointC.y);
        path.lineTo(pointB.x, pointB.y);
        c.drawPath(path, strokePaint);

        strokePaint.setStrokeWidth(strokeWidth);
      }
    }

    Views.restore(c, restoreToCount);
  }

  public static CheckBoxView simpleCheckBox (Context context) {
    return simpleCheckBox(context, Lang.rtl());
  }

  public static CheckBoxView simpleCheckBox (Context context, boolean rtl) {
    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(Screen.dp(18f), Screen.dp(18f));
    params.gravity = Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT);
    params.leftMargin = params.rightMargin = Screen.dp(19f);

    CheckBoxView checkBox;
    checkBox = new CheckBoxView(context);
    checkBox.setLayoutParams(params);

    return checkBox;
  }
}
