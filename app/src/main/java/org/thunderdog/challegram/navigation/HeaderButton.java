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
 * File created on 25/04/2015 at 07:26
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.ColorUtils;

public class HeaderButton extends View {
  public interface OnTouchDownListener {
    void onTouchDown (HeaderButton v, MotionEvent e);
  }

  private Drawable drawable;
  private @DrawableRes int drawableRes;

  protected @ThemeColorId int themeColorId, toThemeColorId;
  protected float colorFactor = -1f;

  private OnTouchDownListener touchDownListener;

  public HeaderButton (Context context) {
    super(context);
    Views.setClickable(this);
  }

  public void setTouchDownListener (OnTouchDownListener listener) {
    this.touchDownListener = listener;
  }

  public HeaderButton setThemeColorId (@ThemeColorId int colorId) {
    this.themeColorId = colorId;
    this.colorFactor = -1f;
    invalidate();
    return this;
  }

  public HeaderButton setThemeColorFactor (float factor) {
    if (this.colorFactor != factor && this.colorFactor != -1f) {
      this.colorFactor = factor;
      invalidate();
    }
    return this;
  }

  public HeaderButton setThemeColorId (@ThemeColorId int fromColorId, @ThemeColorId int toColorId, float factor) {
    this.themeColorId = fromColorId;
    this.toThemeColorId = toColorId;
    this.colorFactor = factor;
    invalidate();
    return this;
  }

  private static boolean checkValidness (View view) {
    return view == null || (view.getVisibility() == View.VISIBLE && view.getAlpha() > 0f);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if ((e.getAction() != MotionEvent.ACTION_DOWN || checkValidness(this))) {
      boolean res = super.onTouchEvent(e);
      if (touchDownListener != null && e.getAction() == MotionEvent.ACTION_DOWN) {
        touchDownListener.onTouchDown(this, e);
      }
      return res;
    }
    return false;
  }

  private int currentResource;

  public void setImageResource (int resource) {
    if (currentResource != resource) {
      currentResource = resource;
      boolean invalidate = drawable != null;
      drawable = resource != 0 ? Drawables.get(getResources(), resource) : null;
      drawableRes = resource;
      if (invalidate) {
        invalidate();
      }
    }
  }

  public Drawable getDrawable () {
    return drawable;
  }

  private int lastBgResource;

  public void setButtonBackground (int resource) {
    if (lastBgResource != resource) {
      lastBgResource = resource;
      setBackgroundResource(resource);
    }
  }

  protected final int getColor (int defaultColor) {
    if (themeColorId == 0) {
      return defaultColor;
    } else if (colorFactor != -1f) {
      return ColorUtils.fromToArgb(Theme.getColor(themeColorId), Theme.getColor(toThemeColorId), colorFactor);
    } else {
      return Theme.getColor(themeColorId);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (progressFactor > 0f) {
      if (progress != null) {
        progress.forceColor(ColorUtils.alphaColor(progressFactor, getColor(Theme.progressColor())));
        progress.draw(c);
      }
    }
    if (drawable != null) {
      Paint paint;
      if (themeColorId == 0) {
        paint = Paints.getBitmapPaint();
      } else {
        paint = Paints.getPorterDuffPaint(getColor(0));
      }
      final boolean needMirror = Drawables.needMirror(drawableRes);
      if (needMirror) {
        c.save();
        c.scale(-1f, 1f, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
      }
      float scale = .8f + .2f * (1f - progressFactor);
      if (scale != 1f) {
        c.save();
        c.scale(scale, scale, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
      }
      Drawables.draw(c, drawable, getMeasuredWidth() / 2 - drawable.getMinimumWidth() / 2, getMeasuredHeight() / 2 - drawable.getMinimumHeight() / 2, paint);
      if (scale != 1f) {
        c.restore();
      }
      if (needMirror) {
        c.restore();
      }
    }
  }

  // Progress

  private BoolAnimator progressAnimator;
  private ProgressComponent progress;
  private float progressFactor;

  public void setShowProgress (boolean showProgress, float startProgress) {
    if (progressAnimator == null) {
      if (!showProgress)
        return;
      progressAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
        if (this.progressFactor != factor) {
          this.progressFactor = factor;
          invalidate();
        }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    if (progress == null) {
      progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(15));
      if (startProgress >= 0f) {
        progress.setProgress(startProgress, false);
        progress.setIsPrecise();
      }
      progress.forceColor(ColorUtils.alphaColor(progressFactor, getColor(Theme.progressColor())));
      progress.setUseLargerPaint(Screen.dp(2.5f));
      progress.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
      progress.setUseStupidInvalidate();
      progress.setViewProvider(new SingleViewProvider(this));
    }
    progressAnimator.setValue(showProgress, true);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (progress != null) {
      progress.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }
  }

  public void setCurrentProgress (float progress) {
    if (this.progress != null) {
      this.progress.setProgress(progress, progressFactor > 0f);
    }
  }
}
