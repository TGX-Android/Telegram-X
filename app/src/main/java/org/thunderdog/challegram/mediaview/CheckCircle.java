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
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class CheckCircle extends View implements FactorAnimator.Target {
  private static Paint strokePaint;
  private @ColorId int colorId;

  public CheckCircle (Context context) {
    super(context);
    if (strokePaint == null) {
      strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      strokePaint.setStrokeWidth(Screen.dp(2f));
      strokePaint.setStyle(Paint.Style.STROKE);
    }
    Views.setClickable(this);
  }

  public void setColorId (int color) {
    this.colorId = color;
  }

  private boolean isChecked;

  public void setChecked (boolean isChecked, boolean animated) {
    if (this.isChecked != isChecked) {
      this.isChecked = isChecked;
      if (animated) {
        animateFactor(isChecked ? 1f : 0f);
      } else {
        forceFactor(isChecked ? 1f : 0f);
      }
    }
  }

  private float factor;
  private FactorAnimator animator;

  private void animateFactor (float toFactor) {
    if (animator == null) {
      if (factor == toFactor) {
        return;
      }
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180, factor);
    }
    animator.animateTo(toFactor);
  }

  private void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
  }

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setFactor(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private static final float SWITCH_FACTOR = .5f;

  @Override
  protected void onDraw (Canvas c) {
    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;

    int fullRadius = Screen.dp(10f);
    int innerRadius = Screen.dp(5f);
    int eraseRadius = Screen.dp(8f);

    final int color = Theme.getColor(colorId);
    strokePaint.setColor(color);

    c.drawCircle(cx, cy, fullRadius - strokePaint.getStrokeWidth() / 2, strokePaint);

    final float factor = 1f - this.factor;

    float factor1 = factor <= SWITCH_FACTOR ? factor / SWITCH_FACTOR : 1f;
    float factor2 = factor > SWITCH_FACTOR ? (factor - SWITCH_FACTOR) / (1f - SWITCH_FACTOR) : 0f;

    c.drawCircle(cx, cy, innerRadius + (fullRadius - innerRadius) * factor1, Paints.fillingPaint(color));

    if (factor2 > 0f) {
      c.drawCircle(cx, cy, (int) ((float) eraseRadius * factor2), Paints.fillingPaint(0xff000000));
    }
  }
}
