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
 * File created on 17/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.TextChangeDelegate;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class ProgressComponentView extends View implements FactorAnimator.Target, TextChangeDelegate, AttachDelegate, Destroyable {
  // private static final ColorChanger changer = new ColorChanger(0x001EAAF1, );

  private @Nullable ProgressComponent progress;

  public ProgressComponentView (Context context) {
    super(context);
  }

  public ProgressComponent getProgress () {
    return progress;
  }

  public void setUseStupidInvalidate () {
    if (progress != null) {
      progress.setUseStupidInvalidate();
    }
  }

  private static int color (float factor) {
    return ColorUtils.fromToArgb(ColorUtils.alphaColor(0f, Theme.progressColor()), Theme.progressColor(), factor);
  }

  @Override
  public void setTextColor (@ColorInt int color) {
    if (progress != null) {
      progress.forceColor(color);
    }
  }

  public void initCustom (float radius, float forceFactor, float minSize) {
    progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(radius));
    progress.setAlpha(forceFactor);
    forceFactor(forceFactor);

    setMinimumWidth(Screen.dp(minSize));
    setMinimumHeight(Screen.dp(minSize));
  }

  public void initLarge (float forceFactor) {
    progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(18f));
    progress.setUseLargerPaint(Screen.dp(4f));
    progress.setSlowerDurations();
    progress.setAlpha(forceFactor);

    forceFactor(forceFactor);
    updateProgressView();

    setMinimumWidth(Screen.dp(44f));
    setMinimumHeight(Screen.dp(44f));
  }

  public void initBig (float forceFactor) {
    progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(8));
    progress.setUseLargerPaint(Screen.dp(2.5f));
    progress.setSlowerDurations();
    progress.setAlpha(forceFactor);

    forceFactor(forceFactor);

    setMinimumWidth(Screen.dp(20f));
    setMinimumHeight(Screen.dp(20f));
  }

  public void initSmall (float forceFactor) {
    progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(3.5f));
    progress.setAlpha(forceFactor);

    forceFactor(forceFactor);

    setMinimumWidth(Screen.dp(8f));
    setMinimumHeight(Screen.dp(8f));
  }

  public void initStatus (float forceFactor) {
    progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(5f));
    progress.setAlpha(forceFactor);
    progress.setUseLargerPaint();
    progress.setSlowerDurations();

    forceFactor(forceFactor);

    setMinimumWidth(Screen.dp(16f));
    setMinimumHeight(Screen.dp(16f));
  }

  public void initMedium (float forceFactor) {
    progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(6f));
    progress.setAlpha(forceFactor);
    progress.setUseLargerPaint();
    progress.setSlowerDurations();

    forceFactor(forceFactor);

    setMinimumWidth(Screen.dp(16f));
    setMinimumHeight(Screen.dp(16f));
  }

  public void setProgressColor (@ColorInt int color) {
    if (progress != null) {
      progress.forceColor(color);
    }
  }

  @Override
  public void setVisibility (int visibility) {
    super.setVisibility(visibility);
    updateProgressView();
  }

  @Override
  public void setAlpha (float alpha) {
    super.setAlpha(alpha);
    updateProgressView();
  }

  private void updateProgressView () {
    if (progress != null) {
      if (getVisibility() == View.VISIBLE && getAlpha() > 0f) {
        progress.attachToView(this);
      } else {
        progress.detachFromView(this);
      }
    }
  }

  @Override
  public void attach () {
    if (progress != null) {
      updateProgressView();
    }
  }

  @Override
  public void detach () {
    if (progress != null) {
      progress.detachFromView(this);
    }
  }

  @Override
  public void performDestroy () {
    detach();
  }

  private float currentFactor;
  private FactorAnimator animator;

  public void animateFactor (float toFactor) {
    if (animator == null) {
      this.animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, currentFactor);
    }
    animator.animateTo(toFactor);
  }

  public float cancelPendingAnimation () {
    if (animator != null) {
      animator.cancel();
    }
    return currentFactor;
  }

  public void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setFactor(factor);
    invalidate();
  }

  private @Nullable View inverseView;

  public void setInverseView (@Nullable View view) {
    this.inverseView = view;
  }

  private void setFactor (float factor) {
    if (this.currentFactor != factor) {
      currentFactor = factor;
      if (progress != null) {
        progress.setAlpha(factor);
      }
      if (inverseView != null) {
        inverseView.setAlpha(1f - factor);
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (progress != null) {
      progress.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (progress != null) {
      progress.draw(c);
    }
  }
}
