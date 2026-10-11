/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.thunderdog.challegram.tool.Screen;

/** Official bottom-button geometry: 44dp height, 9dp corners, 8dp sides and 10dp gap. */
public final class MiniAppButtons extends LinearLayout {
  private final Action main, secondary;
  private String position = "left";
  private int buttonColor, textColor;

  public MiniAppButtons (Context context, Runnable onMain, Runnable onSecondary) {
    super(context);
    main = new Action(context, onMain);
    secondary = new Action(context, onSecondary);
    refreshTheme();
    arrange();
  }

  public void refreshTheme () {
    buttonColor = MiniAppTheme.parameters().buttonColor;
    textColor = MiniAppTheme.parameters().buttonTextColor;
    main.applyColors();
    secondary.applyColors();
  }

  public void configure (boolean isSecondary, JSONObject data) {
    Action action = isSecondary ? secondary : main;
    action.visible = data.optBoolean("is_visible", false);
    action.active = data.optBoolean("is_active", true);
    action.label.setText(data.optString("text", ""));
    action.customColor = parseColor(data.optString("color", ""));
    action.customTextColor = parseColor(data.optString("text_color", ""));
    action.progress.setVisibility(data.optBoolean("is_progress_visible", false) ? VISIBLE : GONE);
    action.setEnabled(action.active);
    action.setAlpha(action.active ? 1f : .5f);
    action.applyColors();
    if (isSecondary) {
      String value = data.optString("position", "left");
      if ("left".equals(value) || "right".equals(value) || "top".equals(value) || "bottom".equals(value)) position = value;
    }
    arrange();
  }

  public void reset () {
    main.visible = secondary.visible = false;
    arrange();
  }

  private void arrange () {
    removeAllViews();
    boolean vertical = "top".equals(position) || "bottom".equals(position);
    setOrientation(vertical ? VERTICAL : HORIZONTAL);
    boolean both = main.visible && secondary.visible;
    boolean secondaryFirst = "left".equals(position) || "top".equals(position);
    if (both) {
      addAction(secondaryFirst ? secondary : main, vertical, false);
      addAction(secondaryFirst ? main : secondary, vertical, true);
    } else if (main.visible || secondary.visible) {
      addAction(main.visible ? main : secondary, true, false);
    }
    setPadding(Screen.dp(8), Screen.dp(7), Screen.dp(8), Screen.dp(7));
    setVisibility(main.visible || secondary.visible ? VISIBLE : GONE);
  }

  private void addAction (Action action, boolean vertical, boolean gap) {
    LayoutParams params = new LayoutParams(vertical ? LayoutParams.MATCH_PARENT : 0, Screen.dp(44), vertical ? 0 : 1);
    if (gap) {
      if (vertical) params.topMargin = Screen.dp(10); else params.leftMargin = Screen.dp(10);
    }
    addView(action, params);
  }

  public static Integer parseColor (String value) {
    if (value == null || !value.matches("#[a-fA-F0-9]{6}")) return null;
    return Color.parseColor(value);
  }

  private final class Action extends FrameLayout {
    final TextView label;
    final ProgressBar progress;
    boolean visible, active = true;
    Integer customColor, customTextColor;

    Action (Context context, Runnable callback) {
      super(context);
      label = new TextView(context);
      label.setGravity(Gravity.CENTER);
      label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
      label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      label.setSingleLine();
      label.setPadding(Screen.dp(12), 0, Screen.dp(12), 0);
      addView(label, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
      FrameLayout.LayoutParams spinner = new FrameLayout.LayoutParams(Screen.dp(20), Screen.dp(20), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
      spinner.rightMargin = Screen.dp(12);
      addView(progress, spinner);
      progress.setVisibility(View.GONE);
      setOnClickListener(v -> { if (active) callback.run(); });
    }

    void applyColors () {
      GradientDrawable background = new GradientDrawable();
      background.setCornerRadius(Screen.dp(9));
      background.setColor(customColor != null ? customColor : buttonColor);
      setBackground(background);
      label.setTextColor(customTextColor != null ? customTextColor : textColor);
    }
  }
}
