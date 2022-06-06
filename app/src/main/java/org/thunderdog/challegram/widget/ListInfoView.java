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
 * File created on 27/04/2015 at 15:36
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

public class ListInfoView extends FrameLayoutFix {
  private TextView textView;

  private ProgressBar progress;
  private SpinnerView spinnerView;

  private boolean isEmptyView;

  public ListInfoView (Context context) {
    super(context);

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    textView = new NoScrollTextView(context);
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    textView.setTextColor(Theme.textDecent2Color());
    textView.setVisibility(View.GONE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progress = new ProgressBar(getContext());
      progress.setIndeterminate(true);
      addView(progress, new LayoutParams(Screen.dp(32f), Screen.dp(32f), Gravity.CENTER));
    } else {
      spinnerView = new SpinnerView(context);
      spinnerView.setImageResource(R.drawable.spinner_48_inner);
      addView(spinnerView, new LayoutParams(Screen.dp(32f), Screen.dp(32f), Gravity.CENTER));
    }

    addView(textView, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(textView, R.id.theme_color_background_textLight);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCurrentHeight(), MeasureSpec.EXACTLY));
  }

  private int getCurrentHeight () {
    return isEmptyView ? Math.max(getParent() == null ? 0 : ((ViewGroup) getParent()).getMeasuredHeight(), Screen.dp(42f)) : Screen.dp(42f);
  }

  public void showProgress () {
    isEmptyView = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progress.setVisibility(View.VISIBLE);
    } else {
      spinnerView.setVisibility(View.VISIBLE);
    }
    textView.setVisibility(View.GONE);
    int height = getMeasuredHeight();
    if (height != 0 && height != getCurrentHeight()) {
      requestLayout();
    }
  }

  public void showInfo (CharSequence message) {
    if (isEmptyView) {
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
      isEmptyView = false;
    }
    setText(message);
  }

  public void showEmpty (CharSequence empty) {
    if (!isEmptyView) {
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
      isEmptyView = true;
    }
    setText(empty);
  }

  private void setText (CharSequence text) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      progress.setVisibility(View.GONE);
    } else {
      spinnerView.setVisibility(View.GONE);
    }
    textView.setText(text);
    textView.setVisibility(View.VISIBLE);
    int height = getMeasuredHeight();
    if (height != 0 && height != getCurrentHeight()) {
      requestLayout();
    }
  }
}
