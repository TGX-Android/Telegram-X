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
 * File created on 22/09/2017
 */
package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class CameraBlurView extends View implements FactorAnimator.Target {
  public CameraBlurView (Context context) {
    super(context);
    setLayoutParams(FrameLayoutFix.newParams(Screen.dp(100f), Screen.dp(100f)));
  }

  private float factor;

  public void setExpandFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  private FactorAnimator successAnimator;

  public void performSuccessHint () {
    if (successAnimator == null) {
      successAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 400l);
    } else {
      successAnimator.forceFactor(0f);
    }
    successAnimator.animateTo(1f);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case 0:
        invalidate();
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case 0:
        successAnimator.forceFactor(0f);
        break;
    }
  }

  private static final int COLOR = 0x70eaeaea;

  @Override
  protected void onDraw (Canvas c) {
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();

    int cx = viewWidth / 2;
    int cy = viewHeight / 2;

    int radius = Screen.dp(40f + 10f * factor);
    c.drawCircle(cx, cy, radius, Paints.fillingPaint(COLOR));

    if (successAnimator != null) {
      float factor = successAnimator.getFactor();
      if (factor != 0f && factor != 1f) {
        float expandFactor = factor < .5f ? factor / .5f : 1f;
        float alpha = factor >= .4f ? 1f - ((factor - .4f) / .6f) : 1f;
        if (alpha != 0f) {
          c.drawCircle(cx, cy, radius * expandFactor, Paints.fillingPaint(ColorUtils.alphaColor(alpha, COLOR)));
        }
      }
    }
  }
}
