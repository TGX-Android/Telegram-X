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
 * File created on 16/05/2015 at 16:02
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.Animated;
import me.vkryl.android.widget.FrameLayoutFix;

public class MenuMoreWrap extends MenuWrap {

  public MenuMoreWrap (Context context) {
    super(context);
  }

  public void updateItem (int index, int id, CharSequence title, int icon, OnClickListener listener, @Nullable ThemeListenerList themeProvider) {
    TextView menuItem = (TextView) getChildAt(index);
    menuItem.setId(id);
    menuItem.setOnClickListener(listener);
    menuItem.setText(title);
    Drawable drawable = icon != 0 ? Drawables.get(getResources(), icon) : null;
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setVisibility(View.VISIBLE);
    if (drawable != null) {
      drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_icon)));
      if (themeProvider != null) {
        themeProvider.addThemeFilterListener(drawable, R.id.theme_color_icon);
      }
      if (Drawables.needMirror(icon)) {
        // TODO
      }
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
      }
    } else {
      menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
    }

    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
  }

  public TextView addItem (int id, CharSequence title, int iconRes, Drawable icon, OnClickListener listener) {
    return addItem(id, title, iconRes, icon, true, listener);
  }

  public TextView addItem (int id, CharSequence title, int iconRes, Drawable icon, boolean useColorFilter, OnClickListener listener) {
    TextView menuItem = new NoScrollTextView(getContext());
    menuItem.setId(id);
    menuItem.setTypeface(Fonts.getRobotoRegular());
    menuItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    if (forcedTheme != null) {
      menuItem.setTextColor(forcedTheme.getColor(R.id.theme_color_text));
    } else {
      menuItem.setTextColor(Theme.textAccentColor());
      if (themeListeners != null) {
        themeListeners.addThemeTextAccentColorListener(menuItem);
      }
    }
    menuItem.setText(title);
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setSingleLine(true);
    menuItem.setEllipsize(TextUtils.TruncateAt.END);
    menuItem.setOnClickListener(listener);
    menuItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f)));
    menuItem.setPadding(Screen.dp(17f), 0, Screen.dp(17f), 0);
    menuItem.setCompoundDrawablePadding(Screen.dp(18f));
    icon = iconRes != 0 ? Drawables.get(getResources(), iconRes) : icon;
    if (icon != null) {
      if (useColorFilter) {
        if (forcedTheme != null) {
          icon.setColorFilter(Paints.getColorFilter(forcedTheme.getColor(R.id.theme_color_icon)));
        } else {
          icon.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_icon)));
          if (themeListeners != null) {
            themeListeners.addThemeFilterListener(icon, R.id.theme_color_icon);
          }
        }
      }
      if (Drawables.needMirror(iconRes)) {
        // TODO
      }
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
      }
    }
    Views.setClickable(menuItem);
    RippleSupport.setTransparentSelector(menuItem);
    addView(menuItem);
    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
    return menuItem;
  }
<<<<<<< HEAD

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(getItemsWidth(), MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  public int getItemsWidth () {
    int padding = Screen.dp(8f);
    int childCount = getChildCount();
    int maxWidth = 0;
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE && v.getTag() instanceof Integer) {
        maxWidth = Math.max(maxWidth, (Integer) v.getTag());
      }
    }
    return Math.max(getMinimumWidth(), maxWidth + padding + padding);
  }

  private boolean shouldPivotBottom;

  public void setShouldPivotBottom (boolean shouldPivotBottom) {
    this.shouldPivotBottom = shouldPivotBottom;
  }

  public boolean shouldPivotBottom () {
    return shouldPivotBottom;
  }

  public int getItemsHeight () {
    int itemHeight = Screen.dp(48f);
    int padding = Screen.dp(8f);
    int total = 0;
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE) {
        total += itemHeight;
      }
    }
    return total + padding + padding;
  }

  public float getRevealRadius () {
    return (float) Math.hypot(getItemsWidth(), getItemsHeight());
  }

  public void scaleIn (Animator.AnimatorListener listener) {
    Views.animate(this, 1f, 1f, 1f, 135l, 10l, AnimatorUtils.DECELERATE_INTERPOLATOR, listener);
  }

  public void scaleOut (Animator.AnimatorListener listener) {
    Views.animate(this, START_SCALE, START_SCALE, 0f, 120l, 0l, AnimatorUtils.ACCELERATE_INTERPOLATOR, listener);
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