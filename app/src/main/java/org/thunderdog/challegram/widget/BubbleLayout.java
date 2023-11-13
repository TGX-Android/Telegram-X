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
 * File created on 06/03/2018
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.AnimatedLinearLayout;
import me.vkryl.core.MathUtils;

public class BubbleLayout extends AnimatedLinearLayout implements FactorAnimator.Target {
  private final Drawable backgroundDrawable, cornerDrawable;

  private float maxAllowedVisibility = 1f;

  private final @Nullable ViewController<?> themeProvider;
  private boolean top;

  public BubbleLayout (@NonNull Context context, @Nullable ViewController<?> themeProvider, boolean top) {
    super(context);

    setOrientation(LinearLayout.VERTICAL);

    this.themeProvider = themeProvider;
    this.top = top;

    this.backgroundDrawable = Theme.filteredDrawable(R.drawable.stickers_back_all, ColorId.overlayFilling, themeProvider);
    this.cornerDrawable = Theme.filteredDrawable(R.drawable.stickers_back_arrow, ColorId.overlayFilling, themeProvider);

    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(this);
    }
    setDefaultPadding();
    ViewUtils.setBackground(this, new Drawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        int viewWidth = getMeasuredWidth();
        int viewHeight = getMeasuredHeight();
        int cornerWidth = Screen.dp(18f);
        int cornerHeight = Screen.dp(8f);
        if (BubbleLayout.this.top) {
          backgroundDrawable.setBounds(0, cornerHeight - Screen.dp(2f), viewWidth, viewHeight);
          backgroundDrawable.draw(c);

          int cornerX = viewWidth / 2 - cornerWidth / 2;
          cornerDrawable.setBounds(cornerX, 0, cornerX + cornerWidth, cornerHeight);
          c.save();
          c.rotate(180f, viewWidth / 2f, cornerHeight / 2f);
          cornerDrawable.draw(c);
          c.restore();
        } else {
          int cornerY = viewHeight - cornerHeight - Screen.dp(1f);
          backgroundDrawable.setBounds(0, 0, viewWidth, cornerY + Screen.dp(.5f));
          backgroundDrawable.draw(c);

          cornerY -= Screen.dp(4f);
          int cornerX = cornerCenterX - cornerWidth / 2;
          cornerDrawable.setBounds(cornerX, cornerY, cornerX + cornerWidth, cornerY + cornerHeight);
          cornerDrawable.draw(c);
        }
      }

      @Override
      public void setAlpha (int alpha) { }

      @Override
      public void setColorFilter (@Nullable ColorFilter colorFilter) { }

      @Override
      public int getOpacity () {
        return PixelFormat.UNKNOWN;
      }
    });

    /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect(view.getPaddingLeft(), view.getPaddingTop(), view.getMeasuredWidth() - view.getPaddingRight(), view.getMeasuredHeight() - view.getPaddingBottom(), Screen.dp(4f));
        }
      });
    }*/
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return super.onInterceptTouchEvent(ev) || getAlpha() == 0f;
  }

  public void removeThemeListeners () {
    if (themeProvider != null) {
      themeProvider.removeThemeListenerByTarget(backgroundDrawable);
      themeProvider.removeThemeListenerByTarget(cornerDrawable);
      themeProvider.removeThemeListenerByTarget(this);
    }
  }

  private static final int ANIMATOR_VISIBILITY = 0;
  private BoolAnimator visibilityAnimator;

  public boolean isBubbleVisible () {
    return visibilityAnimator != null && visibilityAnimator.getValue();
  }

  public void setMaxAllowedVisibility (float factor) {
    if (this.maxAllowedVisibility != factor) {
      this.maxAllowedVisibility = factor;
      updateStyles();
    }
  }

  public static final long REVEAL_DURATION = 210l;
  public static final long DISMISS_DURATION = 100;

  public void setBubbleVisible (boolean isVisible, @Nullable View view) {
    boolean wasVisible = isBubbleVisible();
    if (wasVisible != isVisible) {
      if (visibilityAnimator == null) {
        visibilityAnimator = new BoolAnimator(ANIMATOR_VISIBILITY, this, AnimatorUtils.OVERSHOOT_INTERPOLATOR, REVEAL_DURATION);
      } else {
        if (isVisible && visibilityAnimator.getFloatValue() == 0f) {
          visibilityAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          visibilityAnimator.setDuration(REVEAL_DURATION);
        } else {
          visibilityAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          visibilityAnimator.setDuration(DISMISS_DURATION);
        }
      }
      visibilityAnimator.setValue(isVisible, maxAllowedVisibility > 0f, view);
    }
  }

  private float visibilityFactor;
  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBILITY: {
        if (visibilityFactor != factor) {
          visibilityFactor = factor;
          updateStyles();
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private int cornerCenterX;
  private boolean cornerCenterChanged;

  public void setCornerCenterX (int cx) {
    if (!this.cornerCenterChanged || this.cornerCenterX != cx) {
      this.cornerCenterChanged = true;
      this.cornerCenterX = cx;
      setPivotX(cx);
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (!cornerCenterChanged) {
      setPivotX(getMeasuredWidth() / 2f);
    }
    if (top) {
      setPivotY(Screen.dp(8f) / 2f + Screen.dp(1f));
    } else {
      setPivotY(getMeasuredHeight() - Screen.dp(8f) / 2f - Screen.dp(1f));
    }
  }

  private void updateStyles () {
    float factor = maxAllowedVisibility * visibilityFactor;
    final float scale = .8f + .2f * factor;
    setScaleX(scale);
    setScaleY(scale);
    setAlpha(MathUtils.clamp(factor));
  }

  public boolean setTop (boolean top) {
    if (this.top != top) {
      this.top = top;
      requestLayout();
      return true;
    }
    return false;
  }

  public void setDefaultPadding () {
    int paddingTop = Screen.dp(2);
    int paddingBottom = Screen.dp(4f) + Screen.dp(8f) + Screen.dp(1f);
    if (top) {
      setPadding(Screen.dp(1f), paddingBottom - Screen.dp(4f) - Screen.dp(2f), Screen.dp(1), paddingTop + Screen.dp(2f));
    } else {
      setPadding(Screen.dp(1f), paddingTop, Screen.dp(1), paddingBottom);
    }
  }
}
