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
 * File created on 05/02/2016 at 19:13
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class RadioView extends View implements FactorAnimator.Target {
  private Paint radioPaint;

  private final BoolAnimator checkAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 192l, false);
  private final BoolAnimator activeAnimator = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, true);

  public RadioView (Context context) {
    super(context);
    radioPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    radioPaint.setStrokeWidth(Screen.dp(2f));
    radioPaint.setStyle(Paint.Style.STROKE);
  }

  /*private float selectFactor;

  private void setSelectFactor (float selectFactor) {
    if (this.selectFactor != selectFactor) {
      this.selectFactor = selectFactor;
      invalidate();
    }
  }*/

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    invalidate();
  }

  public boolean isChecked () {
    return checkAnimator.getValue();
  }

  public void setChecked (boolean checked, boolean isAnimated) {
    checkAnimator.setValue(checked, isAnimated);
  }

  public void setActive (boolean active, boolean animated) {
    activeAnimator.setValue(active, animated);
  }

  private int colorId;

  public void setColorId (int colorId) {
    if (this.colorId != colorId) {
      this.colorId = colorId;
      invalidate();
    }
  }

  public boolean toggleChecked () {
    setChecked(!checkAnimator.getValue(), true);
    return isChecked();
  }

  private boolean useColor;

  public void setApplyColor (boolean useColor) {
    if (this.useColor != useColor) {
      this.useColor = useColor;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    final int paddingLeft = getPaddingLeft();
    final int paddingTop = getPaddingTop();

    float cx = paddingLeft + (getMeasuredWidth() - paddingLeft - getPaddingRight()) / 2;
    float cy = paddingTop + (getMeasuredHeight() - paddingTop - getPaddingBottom()) / 2;

    final float radius = Screen.dp(9f);
    final float innerRadius = Screen.dp(5f);

    final int color = Theme.getColor(colorId != 0 ? colorId : ColorId.controlInactive);
    final int radioFillingColor = ColorUtils.fromToArgb(color, colorId != 0 && useColor ? color : Theme.radioFillingColor(), activeAnimator.getFloatValue());

    final float selectFactor = checkAnimator.getFloatValue();

    if (selectFactor == 0f || selectFactor == 1f) {
      radioPaint.setColor(ColorUtils.fromToArgb(color, radioFillingColor, selectFactor));
      c.drawCircle(cx, cy, radius, radioPaint);
    }

    final float factor = 1f - selectFactor;

    if (factor == 0f) {
      c.drawCircle(cx, cy, innerRadius, Paints.fillingPaint(radioFillingColor));
    } else if (factor != 1f) {
      final float addRadius = Screen.dp(4f);
      final float totalRadius = innerRadius + radius;

      final float currentRadius = factor * totalRadius;
      final float fillRadius = Math.max(0, currentRadius - addRadius);

      final float radioFactor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(1f - MathUtils.clamp(fillRadius / (totalRadius - addRadius)));
      final int radioColor = ColorUtils.fromToArgb(color, radioFillingColor, radioFactor);

      c.drawCircle(cx, cy, innerRadius + Math.min(addRadius, currentRadius), Paints.fillingPaint(radioColor));
      c.drawCircle(cx, cy, fillRadius, Paints.fillingPaint(Theme.fillingColor()));

      radioPaint.setColor(radioColor);
      c.drawCircle(cx, cy, radius, radioPaint);
    }
  }

  public static RadioView simpleRadioView (Context context) {
    return simpleRadioView(context, Lang.rtl());
  }

  public static RadioView simpleRadioView (Context context, boolean alignLeft) {
    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(Screen.dp(22f), Screen.dp(22f));
    params.gravity = Gravity.CENTER_VERTICAL | (alignLeft ? Gravity.LEFT : Gravity.RIGHT);
    params.rightMargin = params.leftMargin = Screen.dp(18f);

    RadioView radioView;
    radioView = new RadioView(context);
    radioView.setLayoutParams(params);

    return radioView;
  }
}
