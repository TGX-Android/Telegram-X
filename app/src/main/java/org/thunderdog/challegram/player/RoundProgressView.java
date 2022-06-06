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
 * File created on 16/10/2017
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

class RoundProgressView extends View implements FactorAnimator.Target {
  private RoundVideoController controller;

  public RoundProgressView (Context context) {
    super(context);
  }

  private ProgressComponent progress;

  public void addProgressIfNeeded () {
    if (progress == null) {
      progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(6f));
      progress.setUseLargerPaint(Screen.dp(2f));
      progress.setIsPrecise();
      progress.forceColor(0xffffffff);
      progress.setAlpha(0f);
      progress.attachToView(this);
      progress.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setProgressAlpha(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  private boolean progressVisible;
  private FactorAnimator progressVisibilityAnimator;
  private float progressVisibleFactor;

  public void setLoadProgress (float progress) {
    if (this.progress != null) {
      this.progress.setProgress(progress, true);
    }
  }

  public void setProgressVisible (boolean isVisible) {
    if (this.progressVisible != isVisible) {
      this.progressVisible = isVisible;

      addProgressIfNeeded();

      if (isVisible) {
        progress.setProgress(0f, true);
      }

      final float toFactor = isVisible ? 1f : 0f;
      final boolean animated = getAlpha() != 0f && getParent() != null && ((View) getParent()).getAlpha() != 0f;

      if (animated) {
        if (progressVisibilityAnimator == null) {
          if (progressVisibleFactor == toFactor) {
            return;
          }
          progressVisibilityAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.progressVisibleFactor);
        }
        progressVisibilityAnimator.animateTo(toFactor);
      } else {
        if (progressVisibilityAnimator != null) {
          progressVisibilityAnimator.forceFactor(toFactor);
        }
        setProgressAlpha(toFactor);
      }
    }
  }

  private void setProgressAlpha (float alpha) {
    if (this.progressVisibleFactor != alpha) {
      this.progressVisibleFactor = alpha;
      progress.setAlpha(alpha);
      invalidate();
    }
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

    if (progress != null) {
      progress.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }
  }

  private float visualProgress;

  public void setVisualProgress (float progress) {
    if (this.visualProgress != progress) {
      this.visualProgress = progress;
      if ((int) (totalDistance * lastDrawProgress) != (int) (totalDistance * progress)) {
        invalidate();
      }
    }
  }

  private float lastDrawProgress;

  @Override
  protected void onDraw (Canvas c) {
    final float progress = controller != null ? controller.getVisualProgress() : this.visualProgress;
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    if (progress != 0f) {
      RectF rectF = Paints.getRectF();
      int padding = Screen.dp(1.5f);

      rectF.set(padding, padding, viewWidth - padding, viewHeight - padding);
      c.drawArc(rectF, -90, (360f - removeDegrees) * progress, false, Paints.videoStrokePaint());
    }
    if (this.progress != null) {
      c.drawCircle(viewWidth / 2, viewHeight / 2, Screen.dp(12f), Paints.fillingPaint(ColorUtils.alphaColor(this.progress.getAlpha(), 0x44000000)));
      this.progress.draw(c);
    }
    this.lastDrawProgress = progress;
  }
}
