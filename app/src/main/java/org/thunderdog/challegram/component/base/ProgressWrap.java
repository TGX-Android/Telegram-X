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
 * File created on 15/05/2015 at 19:25
 */
package org.thunderdog.challegram.component.base;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SpinnerView;

import me.vkryl.android.widget.FrameLayoutFix;

public class ProgressWrap extends FrameLayoutFix {
  private TextView textView;
  private ProgressBar progressBar;

  public void addThemeListeners (ThemeListenerList themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(this);
      themeProvider.addThemeTextAccentColorListener(textView);
    }
  }

  public ProgressWrap (Context context) {
    super(context);

    int width = Math.min(Screen.smallestSide() - Screen.dp(56f), Screen.dp(300f));
    int height = Screen.dp(94f);

    ViewSupport.setThemedBackground(this, R.id.theme_color_filling);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(36f), Screen.dp(36f), Gravity.LEFT | Gravity.CENTER_VERTICAL);
    params.setMargins(Screen.dp(12f), 0, 0, 0);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progressBar = new ProgressBar(getContext());
      progressBar.setIndeterminate(true);
      progressBar.setLayoutParams(params);
      addView(progressBar);
    } else {
      SpinnerView spinnerView = new SpinnerView(getContext());
      spinnerView.setImageResource(R.drawable.spinner_48_inner);
      spinnerView.setLayoutParams(params);

      addView(spinnerView);
    }

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
    params.setMargins(Screen.dp(60f), Screen.dp(1f), 0, 0);

    textView = new NoScrollTextView(context);
    textView.setTextColor(Theme.textAccentColor());
    textView.setGravity(Gravity.LEFT);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setMaxWidth(width - Screen.dp(64f));
    textView.setMaxLines(2);
    textView.setLayoutParams(params);

    addView(textView);
    setLayoutParams(FrameLayoutFix.newParams(width, height, Gravity.CENTER));
  }

  public void setMessage (String message) {
    textView.setText(message);
  }

  public @Nullable ProgressBar getProgress () {
    return progressBar;
  }
}
