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
 * File created on 08/08/2015 at 13:36
 */
package org.thunderdog.challegram.component.passcode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;

public class PatternLine {
  private static Paint paint;

  private float fromX, fromY;
  private float toX, toY;

  private View parentView;

  public PatternLine (float fromX, float fromY, float toX, float toY) {
    this.fromX = fromX;
    this.fromY = fromY;
    this.toX = toX;
    this.toY = toY;
    if (paint == null) {
      paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      paint.setStyle(Paint.Style.FILL_AND_STROKE);
      paint.setStrokeWidth(Screen.dp(3f));
    }
  }

  public void setParentView (View view) {
    this.parentView = view;
  }

  public float getToX () {
    return toX;
  }

  public float getToY () {
    return toY;
  }

  public void setFromXY (float x, float y) {
    this.fromX = x;
    this.fromY = y;
  }

  public void setToXY (float x, float y) {
    this.toX = x;
    this.toY = y;
  }

  private float factor;
  private boolean isTo;
  private float origX, origY;
  private float diffX, diffY;
  private ValueAnimator animator;

  private void animateTo (final float x, final float y) {
    this.diffX = x - origX;
    this.diffY = y - origY;
    this.factor = 0f;
    animator = AnimatorUtils.simpleValueAnimator();
    // FIXME do with implements
    animator.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
    animator.setDuration(140l);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (isTo) {
          toX = x;
          toY = y;
        } else {
          fromX = x;
          fromY = y;
        }
      }
    });
    animator.start();
  }

  public void stopAnimation () {
    if (animator != null) {
      animator.cancel();
    }
  }

  public void setToXYAnimated (float x, float y) {
    if (toX != x || toY != y) {
      this.origX = toX;
      this.origY = toY;
      stopAnimation();
      this.isTo = true;
      animateTo(x, y);
    }
  }

  public void setFromXYAnimated (float x, float y) {
    if (fromX != x || fromY != y) {
      this.origX = fromX;
      this.origY = fromY;
      stopAnimation();
      this.isTo = false;
      animateTo(x, y);
    }
  }

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (isTo) {
        this.toX = origX + factor * diffX;
        this.toY = origY + factor * diffY;
      } else {
        this.fromX = origX + factor * diffX;
        this.fromY = origY + factor * diffY;
      }
      if (parentView != null) {
        parentView.invalidate();
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  public void draw (Canvas c) {
    int alpha = paint.getAlpha();
    int color = ColorUtils.alphaColor((float) alpha / 255f, Theme.getColor(ColorId.passcodeIcon));
    paint.setColor(color);
    c.drawLine(fromX, fromY, toX, toY, paint);
    paint.setAlpha(alpha);
  }

  public static void setAlpha (float alpha) {
    paint.setAlpha((int) (255 * alpha));
  }
}
