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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

public class SettingStupidView extends RelativeLayout implements RtlCheckListener {
  private final TextView titleView;
  private final TextView subtitleView;

  private @ThemeColorId int titleColorId;
  private @ThemeColorId int subtitleColorId;

  public SettingStupidView (Context context) {
    super(context);


    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    setMinimumHeight(Screen.dp(76f));
    setPadding(Screen.dp(16f), Screen.dp(18f), Screen.dp(16f), Screen.dp(18f));

    RelativeLayout.LayoutParams params;

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(Lang.alignParent());

    titleView = new NoScrollTextView(context);
    titleView.setId(R.id.text_stupid);
    titleView.setTextColor(Theme.getColor(titleColorId = R.id.theme_color_text));
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleView.setTypeface(Fonts.getRobotoRegular());
    titleView.setLayoutParams(params);
    addView(titleView);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.BELOW, R.id.text_stupid);
    params.addRule(Lang.alignParent());
    params.topMargin = Screen.dp(2f);

    subtitleView = new NoScrollTextView(context);
    subtitleView.setTextColor(Theme.getColor(subtitleColorId = R.id.theme_color_textLight));
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setLayoutParams(params);
    addView(subtitleView);

    Views.setClickable(this);
    RippleSupport.setSimpleWhiteBackground(this);
  }

  @Override
  public void checkRtl () {
    if (Views.setAlignParent(titleView, Lang.rtl()))
      Views.updateLayoutParams(titleView);
    if (Views.setAlignParent(subtitleView, Lang.rtl()))
      Views.updateLayoutParams(subtitleView);
  }

  public void setTitle (@StringRes int titleResId) {
    titleView.setText(Lang.getString(titleResId));
  }

  public void setTitle (CharSequence title) {
    titleView.setText(title);
  }

  public void setSubtitle (@StringRes int subtitleResId) {
    subtitleView.setText(Lang.getString(subtitleResId));
  }

  public void setSubtitle (CharSequence subtitle) {
    subtitleView.setText(subtitle);
  }

  public void setIsRed () {
    titleView.setTextColor(Theme.getColor(titleColorId = R.id.theme_color_textNegative));
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(titleView, titleColorId);
      themeProvider.addThemeTextColorListener(subtitleView, subtitleColorId);
      themeProvider.addThemeInvalidateListener(this);
    }
  }
}
