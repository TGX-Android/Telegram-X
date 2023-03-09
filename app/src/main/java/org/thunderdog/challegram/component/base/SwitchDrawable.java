/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 31/08/2015 at 13:54
 */
package org.thunderdog.challegram.component.base;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;

public class SwitchDrawable extends Drawable {
  private static final int TRANSITION_DURATION = 180;

  private float factor;
  private boolean isAnimating;
  private ValueAnimator animator;

  private View boundView;
  private Bitmap bottomBitmap;
  private Bitmap topBitmap;

  public SwitchDrawable (int bottomResource, int topResource) {
    Resources res = UI.getResources();

    this.bottomBitmap = BitmapFactory.decodeResource(res, bottomResource);
    this.topBitmap = BitmapFactory.decodeResource(res, topResource);

    if (bottomBitmap.getWidth() != topBitmap.getWidth() || bottomBitmap.getHeight() != topBitmap.getHeight()) {
      Log.w("SwitchDrawable: bitmap sizes are not equal: %dx%d vs %dx%d", bottomBitmap.getWidth(), bottomBitmap.getHeight(), topBitmap.getWidth(), topBitmap.getHeight());
    }
  }

  public void setBoundView (View boundView) {
    this.boundView = boundView;
  }

  public void invalidate () {
    if (boundView != null) {
      boundView.invalidate();
    }
  }

  public void forceSetFactor (float factor) {
    this.factor = factor;
    invalidate();
  }

  public void animateFactor (float toFactor) {
    if (isAnimating && animator != null) {
      isAnimating = false;
      animator.cancel();
    }

    if (factor == toFactor) {
      isAnimating = false;
      forceSetFactor(toFactor);
      return;
    }

    isAnimating = true;
    final float startFactor = getFactor();
    final float diffFactor = toFactor - startFactor;
    animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> setFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.setDuration(TRANSITION_DURATION);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        isAnimating = false;
      }
    });
    animator.start();
  }

  public void setFactor (float factor) {
    if (isAnimating && this.factor != factor) {
      this.factor = factor;
      invalidate();
    }
  }

  public float getFactor () {
    return factor;
  }

  private float bitmapLeft, bitmapTop;

  @Override
  public void setBounds (int left, int top, int right, int bottom) {
    super.setBounds(left, top, right, bottom);
    bitmapLeft = left + (float) (right - left) * .5f - (float) bottomBitmap.getWidth() * .5f;
    bitmapTop = top + (float) (bottom - top) * .5f - (float) bottomBitmap.getHeight() * .5f;
  }

  @Override
  public void draw (Canvas c) {
    Paint paint = Paints.getBitmapPaint();
    int restoreAlpha = paint.getAlpha();
    if (factor != 1f) {
      paint.setAlpha((int) ((1f - factor) * 255f));
      c.drawBitmap(bottomBitmap, bitmapLeft, bitmapTop, paint);
      paint.setAlpha(restoreAlpha);
    }

    if (factor != 0f) {
      paint.setAlpha((int) (factor * 255f));
      c.drawBitmap(topBitmap, bitmapLeft, bitmapTop, paint);
      paint.setAlpha(restoreAlpha);
    }
  }

  @Override
  public void setAlpha (int alpha) {

  }

  @Override
  public void setColorFilter (ColorFilter colorFilter) {

  }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }
}
