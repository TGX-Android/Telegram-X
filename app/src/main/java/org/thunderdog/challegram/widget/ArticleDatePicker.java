/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.function.IntConsumer;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/** Day, month, hour and minute sheet used by the selected-text date action. */
public final class ArticleDatePicker {
  private ArticleDatePicker () { }

  public static Dialog show (Context context, long initial, IntConsumer result) {
    Dialog dialog = new Dialog(context, Theme.dialogTheme()); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Theme.backgroundColor());
    TextView title = new TextView(context); title.setText(Lang.getString(R.string.ArticleDateTitle)); title.setTextSize(20); title.setTextColor(Theme.textAccentColor());
    title.setPadding(Screen.dp(22), Screen.dp(16), Screen.dp(22), Screen.dp(12)); root.addView(title);
    Calendar date = Calendar.getInstance(); date.setTimeInMillis(Math.max(0, Math.min(Integer.MAX_VALUE * 1000L, initial)));
    NumberPicker day = picker(context, 1, 31), month = picker(context, 0, (2038 - 1970) * 12 - 1), hour = picker(context, 0, 23), minute = picker(context, 0, 59);
    String[] months = new String[month.getMaxValue() + 1];
    SimpleDateFormat monthFormat = new SimpleDateFormat("LLLL yyyy", Lang.locale()); Calendar cursor = Calendar.getInstance(); cursor.clear(); cursor.set(1970, Calendar.JANUARY, 1);
    for (int i = 0; i < months.length; i++) { months[i] = monthFormat.format(cursor.getTime()); cursor.add(Calendar.MONTH, 1); }
    month.setDisplayedValues(months);
    month.setValue((date.get(Calendar.YEAR) - 1970) * 12 + date.get(Calendar.MONTH)); day.setMaxValue(date.getActualMaximum(Calendar.DAY_OF_MONTH)); day.setValue(date.get(Calendar.DAY_OF_MONTH));
    hour.setValue(date.get(Calendar.HOUR_OF_DAY)); minute.setValue(date.get(Calendar.MINUTE));
    hour.setFormatter(value -> String.format(Lang.locale(), "%02d", value)); minute.setFormatter(value -> String.format(Lang.locale(), "%02d", value));
    LinearLayout pickers = new LinearLayout(context);
    pickers.addView(day, new LinearLayout.LayoutParams(0, -1, 1)); pickers.addView(month, new LinearLayout.LayoutParams(0, -1, 2));
    pickers.addView(hour, new LinearLayout.LayoutParams(0, -1, 1)); pickers.addView(minute, new LinearLayout.LayoutParams(0, -1, 1));
    root.addView(pickers, new LinearLayout.LayoutParams(-1, Screen.dp(210)));
    TextView done = new TextView(context); done.setTextSize(16); done.setGravity(Gravity.CENTER); done.setTextColor(Color.WHITE);
    done.setBackground(ArticleEditorPopup.background(Theme.textLinkColor(), 24));
    LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, Screen.dp(48)); doneParams.setMargins(Screen.dp(16), Screen.dp(12), Screen.dp(16), Screen.dp(16)); root.addView(done, doneParams);
    SimpleDateFormat display = new SimpleDateFormat("d MMM, HH:mm", Lang.locale());
    Runnable changed = () -> {
      date.clear(); date.set(1970 + month.getValue() / 12, month.getValue() % 12, 1, hour.getValue(), minute.getValue());
      int selectedDay = Math.min(day.getValue(), date.getActualMaximum(Calendar.DAY_OF_MONTH));
      day.setMaxValue(date.getActualMaximum(Calendar.DAY_OF_MONTH)); day.setValue(selectedDay); date.set(Calendar.DAY_OF_MONTH, selectedDay);
      long seconds = date.getTimeInMillis() / 1000L; done.setEnabled(seconds >= 0 && seconds <= Integer.MAX_VALUE);
      done.setText(display.format(date.getTime()));
    };
    for (NumberPicker picker : new NumberPicker[] {day, month, hour, minute}) picker.setOnValueChangedListener((view, old, value) -> changed.run());
    changed.run();
    done.setOnClickListener(view -> { result.accept((int) (date.getTimeInMillis() / 1000L)); dialog.dismiss(); });
    dialog.setContentView(root);
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.getDecorView().setPadding(0, 0, 0, 0);
      window.setGravity(Gravity.BOTTOM); window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(.2f);
      if (Build.VERSION.SDK_INT >= 21) window.setNavigationBarColor(Theme.backgroundColor());
    }
    dialog.show(); if (window != null) window.setLayout(-1, -2); return dialog;
  }

  private static NumberPicker picker (Context context, int min, int max) {
    NumberPicker picker = new NumberPicker(context); picker.setMinValue(min); picker.setMaxValue(max); picker.setWrapSelectorWheel(false);
    picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    if (Build.VERSION.SDK_INT >= 29) picker.setTextColor(Theme.textAccentColor());
    for (int i = 0; i < picker.getChildCount(); i++) { View child = picker.getChildAt(i); if (child instanceof TextView) ((TextView) child).setTextColor(Theme.textAccentColor()); }
    return picker;
  }
}
