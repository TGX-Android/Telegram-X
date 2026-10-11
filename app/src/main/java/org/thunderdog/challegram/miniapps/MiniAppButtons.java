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
public final class MiniAppButtons extends LinearLayout implements me.vkryl.core.lambda.Destroyable {
  private final Action main, secondary;
  private final org.thunderdog.challegram.telegram.Tdlib tdlib;
  private String position = "left";
  private int buttonColor, textColor;

  public MiniAppButtons (Context context, org.thunderdog.challegram.telegram.Tdlib tdlib, Runnable onMain, Runnable onSecondary) {
    super(context);
    this.tdlib = tdlib;
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
    String text = data.optString("text", "");
    long emojiId = data.optLong("icon_custom_emoji_id", 0);
    if (emojiId != 0) {
      android.text.SpannableString value = new android.text.SpannableString("* " + text);
      value.setSpan(new org.thunderdog.challegram.emoji.CustomEmojiSpanImpl(null, action.label, tdlib, emojiId), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      action.label.setText(value);
    } else action.label.setText(text);
    action.label.setContentDescription(text);
    action.shine = data.optBoolean("has_shine_effect") && action.visible;
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
    main.shine = secondary.shine = false;
    arrange();
  }

  @Override public void performDestroy () { main.label.performDestroy(); secondary.label.performDestroy(); }

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
    final org.thunderdog.challegram.widget.CustomEmojiTextView label;
    final ProgressBar progress;
    boolean visible, active = true;
    boolean shine;
    long shineStart;
    private final android.graphics.Paint shinePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint shineFrame = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Matrix shineMatrix = new android.graphics.Matrix();
    private final android.graphics.RectF shineBounds = new android.graphics.RectF();
    Integer customColor, customTextColor;

    Action (Context context, Runnable callback) {
      super(context);
      setWillNotDraw(false);
      label = new org.thunderdog.challegram.widget.CustomEmojiTextView(context, tdlib);
      label.setGravity(Gravity.CENTER);
      label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
      label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      label.setSingleLine();
      label.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
      int color = label.getCurrentTextColor() & 0xffffff;
      shinePaint.setShader(new android.graphics.LinearGradient(0, 0, Screen.dp(160), 0, new int[]{0, color | 0x40000000, 0}, null, android.graphics.Shader.TileMode.CLAMP));
      shineFrame.setShader(new android.graphics.LinearGradient(0, 0, Screen.dp(160), 0, new int[]{0, color | 0xcc000000, 0}, null, android.graphics.Shader.TileMode.CLAMP));
      shineFrame.setStyle(android.graphics.Paint.Style.STROKE);
      shineFrame.setStrokeWidth(Screen.dp(2));
      invalidate();
    }

    @Override protected void onAttachedToWindow () {
      super.onAttachedToWindow(); label.provideComplexReceiverForSpan(null).attach(); shineStart = android.os.SystemClock.uptimeMillis();
    }
    @Override protected void onDetachedFromWindow () {
      label.provideComplexReceiverForSpan(null).detach(); super.onDetachedFromWindow();
    }
    @Override protected void onDraw (android.graphics.Canvas canvas) {
      super.onDraw(canvas);
      if (!shine || !isShown()) return;
      float progress = (android.os.SystemClock.uptimeMillis() - shineStart) % 2400 / 1200f;
      shineMatrix.setTranslate((getWidth() + Screen.dp(320)) * progress - Screen.dp(160), 0);
      shinePaint.getShader().setLocalMatrix(shineMatrix); shineFrame.getShader().setLocalMatrix(shineMatrix);
      shineBounds.set(0, 0, getWidth(), getHeight());
      canvas.drawRoundRect(shineBounds, Screen.dp(9), Screen.dp(9), shinePaint);
      shineBounds.inset(Screen.dp(1), Screen.dp(1)); canvas.drawRoundRect(shineBounds, Screen.dp(8), Screen.dp(8), shineFrame);
      postInvalidateOnAnimation();
    }
  }
}
