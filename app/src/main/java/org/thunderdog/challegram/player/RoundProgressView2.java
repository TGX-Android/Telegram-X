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
 * File created on 16/06/2024
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.RateLimiter;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

class RoundProgressView2 extends View {
  private final BoolAnimator barOpacity = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, true);
  private final BoolAnimator barOpacityReverse = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, false);

  private final Paint strokePaint;

  private boolean isPaused;

  private RoundVideoController controller;
  private ViewParent seekCaught;

  public RoundProgressView2(Context context) {
    super(context);

    strokePaint = new Paint(Paints.videoStrokePaint());
  }

  public void setIsPaused (boolean isPaused) {
    this.isPaused = isPaused;
  }

  public void setController (RoundVideoController controller) {
    this.controller = controller;
  }

  private float removeDegrees;
  private float totalDistance;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    final double removeDistance = Paints.videoStrokePaint().getStrokeWidth();
    final double totalDistance = (int) (2.0 * Math.PI * (double) (getMeasuredWidth() / 2));
    this.totalDistance = (float) (totalDistance - removeDistance);
    this.removeDegrees = (float) (removeDistance / totalDistance) * 360f;
  }

  private float visualProgress;

  public void setVisualProgress (float progress) {
    if (this.visualProgress != progress) {
      boolean hasBigChange = Math.abs(visualProgress - progress) > 0.5f;
      this.visualProgress = progress;
      if (hasBigChange) {
        if (progress > 0.5f) {
          if (!barOpacity.isAnimating()) {
            barOpacity.setValue(false, false);
            barOpacity.setValue(true, true);
          }
        } else {
          if (!barOpacityReverse.isAnimating()) {
            barOpacityReverse.setValue(true, false);
            barOpacityReverse.setValue(false, true);
          }
        }
      }

      if ((int) (totalDistance * lastDrawProgress) != (int) (totalDistance * progress)) {
        invalidate();
      }
    }
  }






  private float lastDrawProgress;
  private float lastDrawPointerX;
  private float lastDrawPointerY;

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();

    final float progress = !captured && controller != null ? controller.getVisualProgress() : this.visualProgress;
    final float isPaused = 1f - (controller != null ? controller.getVisualIsPlaying() : 0f);

    if (progress != 0f || captured) {
      final int padding = MathUtils.fromTo(Screen.dp(1.5f), Screen.dp(16), isPaused);
      final float strokeWidth = MathUtils.fromTo(Screen.dp(3), Screen.dp(4), isPaused);
      final int defaultColor = Paints.videoStrokePaint().getColor();
      final int color = ColorUtils.fromToArgb(defaultColor, defaultColor | 0xFF000000, isPaused);
      final int strokeColor = ColorUtils.alphaColor(barOpacity.getFloatValue(), color);

      strokePaint.setStrokeWidth(strokeWidth);

      final float angle = (360f - removeDegrees) * progress;

      RectF rectF = Paints.getRectF();
      rectF.set(padding, padding, viewWidth - padding, viewHeight - padding);

      final float revFactor = barOpacityReverse.getFloatValue();
      if (revFactor > 0f) {
        final int revColor = ColorUtils.alphaColor(revFactor, defaultColor);
        strokePaint.setColor(revColor);
        c.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2f, strokePaint);
        // c.drawArc(rectF, angle, -90, false, strokePaint);
      }

      strokePaint.setColor(strokeColor);
      c.drawArc(rectF, -90, angle, false, strokePaint);

      if (isPaused > 0f) {
        final double angleRad = Math.toRadians(angle - 90);
        float x = lastDrawPointerX = (float) Math.cos(angleRad) * rectF.width() / 2f + rectF.centerX();
        float y = lastDrawPointerY = (float) Math.sin(angleRad) * rectF.width() / 2f + rectF.centerY();
        c.drawCircle(x, y, Screen.dp(6f * isPaused), Paints.fillingPaint(color));
      }
    }
    this.lastDrawProgress = progress;
  }



  /* Touch */

  private final RateLimiter updateSeekRunnable = new RateLimiter(this::updateSeek, 150L, null);
  private boolean captured;

  private void updateSeek () {
    if (controller != null) {
      controller.seekTo(MathUtils.clamp(visualProgress), false);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    final int action = event.getAction();
    final float x = event.getX();
    final float y = event.getY();

    if (!isPaused) {
      captured = false;
      return false;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (MathUtils.distance(x, y, lastDrawPointerX, lastDrawPointerY) < Screen.dp(24)) {
          captured = true;

          final View rootView = controller != null ? controller.getRootView() : null;
          this.seekCaught = rootView != null ? rootView.getParent() : null;
          if (seekCaught != null) {
            seekCaught.requestDisallowInterceptTouchEvent(true);
          }

          return true;
        }
        break;
      case MotionEvent.ACTION_MOVE:
        if (captured) {
          final double rad = Math.atan2(y - getMeasuredHeight() / 2f, x - getMeasuredWidth() / 2f);
          final float angle = ((float) Math.toDegrees(rad) + 360 + 90) % 360;

          setVisualProgress(MathUtils.clamp(angle / (360f - removeDegrees)));
          updateSeekRunnable.run();
          return true;
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (captured) {
          updateSeekRunnable.cancelIfScheduled();
          if (controller != null) {
            controller.seekTo(MathUtils.clamp(visualProgress), true);
          }
          if (seekCaught != null) {
            seekCaught.requestDisallowInterceptTouchEvent(false);
          }
          captured = false;
          return true;
        }
    }

    return false;
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    updateSeekRunnable.cancelIfScheduled();
  }
}
