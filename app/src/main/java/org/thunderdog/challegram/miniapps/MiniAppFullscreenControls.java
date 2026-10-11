/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

/** Floating controls use the geometry of Telegram Android's BotFullscreenButtons. */
public final class MiniAppFullscreenControls extends FrameLayout {
  public static final int CONTENT_INSET_DP = 46;
  private final LinearLayout left, right;
  private final Symbol close;
  private final TextView label;

  public MiniAppFullscreenControls (Context context, Runnable onBack, Runnable onMinimize, Runnable onMenu) {
    super(context);
    setClipChildren(false);
    left = capsule(context);
    close = new Symbol(context, 0);
    close.setFocusable(false);
    close.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    left.addView(close, new LinearLayout.LayoutParams(Screen.dp(30), -1));
    label = new TextView(context);
    label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
    label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    label.setTextColor(0xffffffff);
    label.setGravity(Gravity.CENTER_VERTICAL);
    label.setSingleLine(true);
    label.setPadding(0, 0, Screen.dp(12), 0);
    left.addView(label, new LinearLayout.LayoutParams(-2, -1));
    left.setOnClickListener(v -> onBack.run());
    left.setFocusable(true);
    FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-2, Screen.dp(30), Gravity.LEFT | Gravity.TOP);
    l.leftMargin = l.topMargin = Screen.dp(8);
    addView(left, l);
    right = capsule(context);
    Symbol collapse = new Symbol(context, 2);
    collapse.setContentDescription(Lang.getString(R.string.MiniAppMinimize));
    collapse.setOnClickListener(v -> onMinimize.run());
    right.addView(collapse, new LinearLayout.LayoutParams(Screen.dp(35.83f), -1));
    Symbol menu = new Symbol(context, 3);
    menu.setContentDescription(Lang.getString(R.string.MiniAppMore));
    menu.setOnClickListener(v -> onMenu.run());
    right.addView(menu, new LinearLayout.LayoutParams(Screen.dp(35.83f), -1));
    FrameLayout.LayoutParams r = new FrameLayout.LayoutParams(-2, Screen.dp(30), Gravity.RIGHT | Gravity.TOP);
    r.rightMargin = r.topMargin = Screen.dp(8);
    addView(right, r);
    setBack(false);
  }

  private static LinearLayout capsule (Context context) {
    LinearLayout view = new LinearLayout(context);
    view.setGravity(Gravity.CENTER_VERTICAL);
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(0x59000000);
    shape.setCornerRadius(Screen.dp(15));
    view.setBackground(shape);
    return view;
  }

  public void setBack (boolean back) {
    close.kind = back ? 1 : 0;
    close.invalidate();
    label.setText(Lang.getString(back ? R.string.MiniAppBack : R.string.MiniAppFullscreenClose));
    left.setContentDescription(label.getText());
  }

  private static final class Symbol extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int kind;
    Symbol (Context context, int kind) { super(context); this.kind = kind; setFocusable(true); }
    @Override protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      canvas.save();
      canvas.translate(getWidth() / 2f, getHeight() / 2f);
      canvas.scale(isPressed() ? .9f : 1f, isPressed() ? .9f : 1f);
      paint.setColor(0xffffffff);
      paint.setStrokeWidth(Screen.dp(2));
      paint.setStrokeCap(Paint.Cap.ROUND);
      if (kind == 0) {
        float r = Screen.dp(4.66f);
        canvas.drawLine(-r, -r, r, r, paint); canvas.drawLine(-r, r, r, -r, paint);
      } else if (kind == 1) {
        float r = Screen.dp(5.5f);
        canvas.drawLine(-r, 0, 0, r, paint); canvas.drawLine(-r, 0, 0, -r, paint);
        canvas.drawLine(-r, 0, r, 0, paint);
      } else if (kind == 2) {
        canvas.drawLine(-Screen.dp(4), -Screen.dp(3), Screen.dp(2), Screen.dp(3), paint);
        canvas.drawLine(Screen.dp(2), Screen.dp(3), Screen.dp(8), -Screen.dp(3), paint);
      } else {
        for (int y = -1; y <= 1; y++) canvas.drawCircle(Screen.dp(1), Screen.dp(y * 5), Screen.dp(1.66f), paint);
      }
      canvas.restore();
    }
  }
}
