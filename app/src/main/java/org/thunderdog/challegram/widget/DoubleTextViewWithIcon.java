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
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class DoubleTextViewWithIcon extends FrameLayoutFix {
  private final ImageView iconView;
  private final DoubleTextView textView;

  public DoubleTextViewWithIcon (Context context) {
    super(context);
    int iconWidth = Screen.dp(56);
    iconView = new ImageView(context);
    textView = new DoubleTextView(context);
    textView.ignoreStartOffset(true);
    textView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, Screen.dp(72), 0, iconWidth, 0, 0, 0));
    iconView.setLayoutParams(FrameLayoutFix.newParams(iconWidth, Screen.dp(72)));
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setColorFilter(Theme.getColor(ColorId.icon));
    addView(iconView);
    addView(textView);
    setBackgroundColor(Theme.fillingColor());
  }

  public ImageView icon () {
    return iconView;
  }

  public DoubleTextView text () {
    return textView;
  }

  public void attach () {
    textView.attach();
  }

  public void detach () {
    textView.detach();
  }

  public void setIconClickListener (OnClickListener listener) {
    Views.setClickable(iconView);
    ViewUtils.setBackground(iconView, Theme.transparentSelector());
    iconView.setOnClickListener(listener);
  }

  public void setTextClickListener (OnClickListener listener) {
    Views.setClickable(textView);
    ViewUtils.setBackground(textView, Theme.transparentSelector());
    textView.setOnClickListener(listener);
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      textView.addThemeListeners(themeProvider);
      themeProvider.addThemeBackgroundColorListener(this, ColorId.filling);
      themeProvider.addThemeFilterListener(iconView, ColorId.icon);
    }
  }

  public void checkRtl () {
    textView.checkRtl();
  }
}
