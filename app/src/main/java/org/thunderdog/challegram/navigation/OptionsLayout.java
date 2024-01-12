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
 * File created on 26/03/2016 at 23:51
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.Animated;
import me.vkryl.core.StringUtils;

public class OptionsLayout extends LinearLayout implements Animated {
  private final CustomTextView textView;
  private final CustomTextView headerView;
  private final ViewController<?> parent;
  private final @Nullable ThemeDelegate forcedTheme;

  public OptionsLayout (Context context, ViewController<?> parent, @Nullable ThemeDelegate forcedTheme) {
    super(context);
    this.parent = parent;
    this.forcedTheme = forcedTheme;

    setOrientation(LinearLayout.VERTICAL);

    headerView = new CustomTextView(context, parent.tdlib());
    headerView.setTextSize(18f);
    headerView.setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(6f));
    headerView.setTextColorId(ColorId.text);
    headerView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    headerView.setMaxLineCount(1);
    headerView.setVisibility(View.GONE);
    addView(headerView);

    textView = new CustomTextView(context, parent.tdlib());
    textView.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(6f));
    textView.setTextColorId(ColorId.textLight);
    textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(textView);

    ViewUtils.setBackground(this, new Drawable() {
      @Override
      public void draw (@NonNull Canvas c) {
        View view = getChildAt(0);
        int height = view != null ? view.getMeasuredHeight() : 0;
        if (height > 0)
          c.drawRect(0, height, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.filling) : Theme.getColor(ColorId.filling)));
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

    if (forcedTheme != null) {
      textView.setForcedTheme(forcedTheme);
      headerView.setForcedTheme(forcedTheme);
    } else {
      // parent.addThemeTextColorListener(textView, ColorId.textLight);
      parent.addThemeInvalidateListener(textView);
      parent.addThemeInvalidateListener(headerView);
      parent.addThemeInvalidateListener(this);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int currentHeight = getMeasuredHeight();
    int actualHeight = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view != null) {
        actualHeight += view.getMeasuredHeight();
      }
    }
    int diff = Math.min(0, currentHeight - actualHeight);
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view != null) {
        view.setTranslationY(diff);
      }
    }
  }

  public static @ColorId int getOptionColorId (int color) {
    switch (color) {
      case ViewController.OPTION_COLOR_NORMAL: {
        return ColorId.text;
      }
      case ViewController.OPTION_COLOR_RED: {
        return ColorId.textNegative;
      }
      case ViewController.OPTION_COLOR_BLUE: {
        return ColorId.textNeutral;
      }
      case ViewController.OPTION_COLOR_GREEN: {
        return ColorId.iconPositive;
      }
    }
    throw new IllegalArgumentException("color == " + color);
  }

  public static TextView genOptionView (Context context, int id, CharSequence string, int color, int icon, OnClickListener onClickListener, @Nullable ThemeListenerList themeProvider, @Nullable ThemeDelegate forcedTheme) {
    EmojiTextView text = new EmojiTextView(context);
    text.setScrollDisabled(true);

    text.setId(id);
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    final int colorId = getOptionColorId(color);
    if (forcedTheme != null) {
      text.setTextColor(forcedTheme.getColor(colorId));
    } else {
      text.setTextColor(Theme.getColor(colorId));
      if (themeProvider != null)
        themeProvider.addThemeColorListener(text, colorId);
    }
    text.setOnClickListener(onClickListener);
    text.setSingleLine(true);
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
    text.setPadding(Screen.dp(17f), Screen.dp(1f), Screen.dp(17f), 0);
    text.setCompoundDrawablePadding(Screen.dp(18f));
    if (icon != 0) {
      Drawable drawable = Drawables.get(context.getResources(), icon);
      if (drawable != null) {
        final int drawableColorId = color == ViewController.OPTION_COLOR_NORMAL ? ColorId.icon : colorId;
        drawable.setColorFilter(Paints.getColorFilter(forcedTheme != null ? forcedTheme.getColor(drawableColorId) : Theme.getColor(drawableColorId)));
        if (themeProvider != null) {
          themeProvider.addThemeFilterListener(drawable, drawableColorId);
        }
        if (Drawables.needMirror(icon)) {
          // TODO
        }
        if (Lang.rtl()) {
          text.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        } else {
          text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
      }
    }
    Views.setClickable(text);
    if (!StringUtils.isEmpty(string)) {
      text.setText(string);
    }

    return text;
  }

  public void setHeader (CharSequence text) {
    if (!StringUtils.isEmpty(text)) {
      headerView.setBoldText(text, null, false);
      headerView.setVisibility(View.VISIBLE);
    } else {
      headerView.setVisibility(View.GONE);
    }
  }

  public void setSubtitle (CharSequence name, @DrawableRes int icon, int color) {
    EmojiTextView text = new EmojiTextView(getContext());
    text.setScrollDisabled(true);
    text.setTypeface(Fonts.getRobotoRegular());
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    final int colorId = getOptionColorId(color);
    if (forcedTheme != null) {
      text.setTextColor(forcedTheme.getColor(colorId));
    } else {
      text.setTextColor(Theme.getColor(colorId));
      if (parent != null)
        parent.addThemeTextColorListener(text, colorId);
    }
    text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
    text.setPadding(Screen.dp(17f), Screen.dp(6f), Screen.dp(17f), 0);
    text.setCompoundDrawablePadding(Screen.dp(8f));
    if (icon != 0) {
      Drawable drawable = Drawables.get(getContext().getResources(), icon);
      if (drawable != null) {
        final int drawableColorId = color == ViewController.OPTION_COLOR_NORMAL ? ColorId.icon : colorId;
        drawable.setColorFilter(Paints.getColorFilter(forcedTheme != null ? forcedTheme.getColor(drawableColorId) : Theme.getColor(drawableColorId)));
        if (parent != null) {
          parent.addThemeFilterListener(drawable, drawableColorId);
        }
        if (Drawables.needMirror(icon)) {
          // TODO
        }
        if (Lang.rtl()) {
          text.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        } else {
          text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
      }
    }
    if (!StringUtils.isEmpty(name)) {
      text.setText(name);
    }

    text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    addView(text, 1);
  }

  public void setInfo (ViewController<?> context, Tdlib tdlib, CharSequence info, boolean isTitle) {
    if (!StringUtils.isEmpty(info)) {
      String str = info.toString();
      TextEntity[] parsed = TD.collectAllEntities(context, tdlib, info, false, null);
      setInfo(str, parsed, isTitle);
    } else {
      textView.setVisibility(View.GONE);
    }
  }

  public void setInfo (String text, TextEntity[] entities, boolean isTitle) {
    if (!StringUtils.isEmpty(text)) {
      textView.setVisibility(View.VISIBLE);
      if (isTitle) {
        textView.setBoldText(text, entities, false);
        textView.setTextSize(19f);
        textView.setTextColorId(ColorId.text);
      } else {
        textView.setText(text, entities, false);
        textView.setTextSize(15f);
        textView.setTextColorId(ColorId.textLight);
      }
    } else {
      textView.setVisibility(View.GONE);
    }
  }

  public int getTextHeight () {
    if (textView.getVisibility() == View.VISIBLE) {
      return textView.getCurrentHeight(UI.getContext(getContext()).getControllerWidth(textView));
    } else {
      return 0;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    super.onTouchEvent(event);
    return true;
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }
}
