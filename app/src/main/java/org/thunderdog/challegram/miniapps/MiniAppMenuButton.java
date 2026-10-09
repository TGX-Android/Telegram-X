/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

/** BotCommandsMenuView's 32dp pill and 40dp collapsed state, fitted to the X composer. */
public final class MiniAppMenuButton extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final Runnable onWidthChanged;
  private String text = "";
  private boolean expanded = true;
  private float expansion = 1f;
  private ValueAnimator animator;

  public MiniAppMenuButton (Context context, Runnable onWidthChanged) {
    super(context);
    this.onWidthChanged = onWidthChanged;
    textPaint.setTextSize(Screen.dp(15));
    textPaint.setTypeface(Fonts.getRobotoBold());
    setClickable(true);
    setFocusable(true);
    setVisibility(GONE);
  }

  public void setMenu (String text, boolean expanded, boolean animated) {
    this.text = text != null ? text : "";
    setContentDescription(this.text);
    if (this.expanded != expanded) {
      this.expanded = expanded;
      if (animator != null) animator.cancel();
      if (animated) {
        animator = ValueAnimator.ofFloat(expansion, expanded ? 1 : 0);
        animator.setDuration(150);
        animator.addUpdateListener(a -> { expansion = (float) a.getAnimatedValue(); updateWidth(); });
        animator.start();
      } else expansion = expanded ? 1 : 0;
    }
    updateWidth();
  }

  private CharSequence label () {
    return TextUtils.ellipsize(text, textPaint, getResources().getDisplayMetrics().widthPixels * .4f, TextUtils.TruncateAt.END);
  }

  public int buttonWidth () {
    return Screen.dp(40) + Math.round((textPaint.measureText(label().toString()) + Screen.dp(4)) * expansion);
  }

  private void updateWidth () {
    if (getLayoutParams() != null && getLayoutParams().width != buttonWidth()) {
      getLayoutParams().width = buttonWidth();
      requestLayout();
    }
    invalidate();
    onWidthChanged.run();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    float top = (getHeight() - Screen.dp(32)) / 2f;
    paint.setColor(Theme.getColor(ColorId.fillingPositive));
    paint.setStyle(Paint.Style.FILL);
    rect.set(0, top, getWidth(), top + Screen.dp(32));
    canvas.drawRoundRect(rect, Screen.dp(16), Screen.dp(16), paint);
    paint.setColor(Theme.getColor(ColorId.fillingPositiveContent));
    paint.setStrokeWidth(Screen.dp(1.8f));
    paint.setStyle(Paint.Style.STROKE);
    rect.set(Screen.dp(10), top + Screen.dp(9), Screen.dp(25), top + Screen.dp(23));
    canvas.drawRoundRect(rect, Screen.dp(2), Screen.dp(2), paint);
    canvas.drawLine(rect.left, top + Screen.dp(12), rect.right, top + Screen.dp(12), paint);
    textPaint.setColor(paint.getColor());
    textPaint.setAlpha(Math.round(255 * expansion));
    canvas.save();
    canvas.clipRect(Screen.dp(32), 0, getWidth() - Screen.dp(6), getHeight());
    canvas.drawText(label().toString(), Screen.dp(34), getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
    canvas.restore();
  }

  @Override
  protected void onDetachedFromWindow () {
    if (animator != null) animator.cancel();
    super.onDetachedFromWindow();
  }
}
