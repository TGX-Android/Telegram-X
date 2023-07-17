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
 * File created on 17/03/2016 at 22:19
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class DoubleHeaderView extends FrameLayoutFix implements RtlCheckListener, FactorAnimator.Target, TextChangeDelegate, Destroyable {
  private final EmojiTextView titleView, subtitleView;

  private @Nullable Drawable titleIcon;

  public DoubleHeaderView (Context context) {
    super(context);

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    params.topMargin = Screen.dp(5f);

    titleView = new EmojiTextView(context) {
      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        if (titleIcon != null && titleIcon.getMinimumWidth() > 0 && titleIcon.getMinimumHeight() > 0) {
          Layout layout = getLayout();
          float left = getPaddingLeft() + (layout != null ? U.getWidth(layout) + Screen.dp(1f) : 0);
          if (left + titleIcon.getMinimumWidth() <= getWidth() - getPaddingRight()) {
            float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f - titleIcon.getMinimumHeight() / 2f;
            Drawables.draw(canvas, titleIcon, left, top, Paints.getIconGrayPorterDuffPaint());
          }
        }
      }
    };
    titleView.setScrollDisabled(true);
    titleView.setTextColor(Theme.headerTextColor());
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setSingleLine(true);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    titleView.setGravity(Lang.gravity());
    titleView.setLayoutParams(params);
    addView(titleView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    params.topMargin = Screen.dp(28f);

    subtitleView = new EmojiTextView(context);
    subtitleView.setScrollDisabled(true);
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setSingleLine(true);
    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
    subtitleView.setGravity(Lang.gravity());
    subtitleView.setLayoutParams(params);
    addView(subtitleView);
  }

  @Override
  public void performDestroy () {
    titleView.performDestroy();
    subtitleView.performDestroy();
  }

  @Override
  public void checkRtl () {
    if (Views.setGravity(titleView, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))) {
      titleView.setGravity(Lang.gravity());
      Views.updateLayoutParams(titleView);
    }
    if (Views.setGravity(subtitleView, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))) {
      subtitleView.setGravity(Lang.gravity());
      Views.updateLayoutParams(subtitleView);
    }
  }

  public void setThemedTextColor (ViewController<?> themeProvider) {
    setThemedTextColor(themeProvider.getHeaderTextColorId(), themeProvider);
  }

  public void setThemedTextColor (int colorId, ViewController<?> themeProvider) {
    setTextColor(Theme.getColor(colorId));
    themeProvider.addThemeTextColorListener(this, colorId);
  }

  private boolean customColors;

  @Override
  public void setTextColor (@ColorInt int color) {
    if (!customColors) {
      titleView.setTextColor(color);
      subtitleView.setTextColor(Theme.subtitleColor(color));
    }
  }

  public void setTextColors (int titleColor, int subtitleColor) {
    if (!customColors) {
      titleView.setTextColor(titleColor);
      subtitleView.setTextColor(subtitleColor);
    }
  }

  public void setThemedTextColor (@ColorId int titleColorId, @ColorId int subtitleColorId, @Nullable ViewController<?> themeProvider) {
    titleView.setTextColor(Theme.getColor(titleColorId));
    subtitleView.setTextColor(Theme.getColor(subtitleColorId));
    customColors = true;
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(titleView, titleColorId);
      themeProvider.addThemeTextColorListener(subtitleView, subtitleColorId);
    }
  }

  @Override
  public void setPadding (int left, int top, int right, int bottom) {
    super.setPadding(left, top, right, bottom);
  }

  public void initWithMargin (int marginRight, boolean transparentHeaderColor) {
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getSize(false), Lang.gravity(), Lang.rtl() ? marginRight : Screen.dp(68f), 0, Lang.rtl() ? Screen.dp(68f) : marginRight, 0));
    if (transparentHeaderColor) {
      subtitleView.setTextColor(Theme.subtitleColor(titleView.getCurrentTextColor()));
    }
  }

  public void checkRtlMargin (int marginRight) {
    if (Views.setGravity(this, Lang.gravity())) {
      Views.setMargins(((FrameLayout.LayoutParams) getLayoutParams()), Lang.rtl() ? marginRight : Screen.dp(68f), 0, Lang.rtl() ? Screen.dp(68f) : marginRight, 0);
      Views.updateLayoutParams(this);
    }
  }

  public void setTitle (@StringRes int titleResId) {
    Views.setMediumText(titleView, Lang.getString(titleResId));
  }

  public void setTitle (CharSequence title) {
    Views.setMediumText(titleView, title);
  }

  public void setTitleIcon (@DrawableRes int iconRes) {
    Drawable icon = iconRes != 0 ? Drawables.get(iconRes) : null;
    if (titleIcon != icon) {
      titleIcon = icon;
      titleView.invalidate();
    }
  }

  public void setSubtitle (@StringRes int subtitleRes) {
    subtitleView.setText(Lang.getString(subtitleRes));
  }

  @Override
  public void setLayoutParams (ViewGroup.LayoutParams params) {
    super.setLayoutParams(params);
  }

  public void setSubtitle (CharSequence subtitle) {
    subtitleView.setText(subtitle);
  }



  // Progress stuff

  private static final int PROGRESS_ANIMATOR = 0;
  private FactorAnimator progressAnimator;

  private static final int PROGRESS_FADE_ANIMATOR = 1;
  private boolean progressFadingOut;
  private float progressFadeFactor;

  public void animateProgress (float progress) {
    if (progress < progressFactor) {
      return;
    }
    if (progressAnimator == null) {
      progressAnimator = new FactorAnimator(PROGRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 320, 0f);
    }
    progressAnimator.animateTo(progress);
  }

  private float progressFactor;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case PROGRESS_ANIMATOR: {
        if (this.progressFactor != factor) {
          progressFactor = factor;
          setWillNotDraw(factor == 0f || progressFadeFactor == 1f);
          invalidate();

          if (factor == 1f && !progressFadingOut) {
            progressFadingOut = true;
            FactorAnimator animator = new FactorAnimator(PROGRESS_FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 280l);
            animator.animateTo(1f);
          }
        }
        break;
      }
      case PROGRESS_FADE_ANIMATOR: {
        if (this.progressFadeFactor != factor) {
          progressFadeFactor = factor;
          setWillNotDraw(progressFactor == 0f || progressFadeFactor == 1f);
          invalidate();
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  protected void onDraw (Canvas c) {
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();
    int height = Screen.dp(2f);
    int width = (int) ((float) viewWidth * progressFactor);
    int color = ColorUtils.color((int) (255f * (1f - progressFadeFactor)), Theme.headerTextColor());

    if (width < viewWidth) {
      int reverseColor = ColorUtils.color((int) ((float) 0x10 * (1f - progressFadeFactor)), 0);
      c.drawRect(width, viewHeight - height, viewWidth, viewHeight, Paints.fillingPaint(reverseColor));
    }

    c.drawRect(0, viewHeight - height, width, viewHeight, Paints.fillingPaint(color));

  }
}
