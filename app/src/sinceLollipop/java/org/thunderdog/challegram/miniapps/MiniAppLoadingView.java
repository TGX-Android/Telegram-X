/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/** The official client's default four-tile placeholder and bottom loading line. */
final class MiniAppLoadingView extends View {
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint shimmer = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path tiles = new Path();
  private final Matrix shaderMatrix = new Matrix();
  private int iconColor;
  private float gradientWidth, translation;
  private float progress, targetProgress, velocity;
  private long lastFrame, completedAt;

  MiniAppLoadingView (Context context) {
    super(context);
    line.setStyle(Paint.Style.STROKE);
    line.setStrokeWidth(Screen.dp(2));
    line.setStrokeCap(Paint.Cap.ROUND);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void reset () {
    progress = targetProgress = velocity = 0f;
    completedAt = lastFrame = 0;
    invalidate();
  }

  void setProgress (int percent) {
    targetProgress = Math.max(targetProgress, Math.min(1f, percent / 100f));
    if (targetProgress == 1f && completedAt == 0) completedAt = SystemClock.uptimeMillis();
    invalidate();
  }

  void refreshColors (int background) {
    // BotWebViewSheet derives the placeholder from its actual background, including the initial theme.
    float[] hsv = new float[3];
    Color.colorToHSV(background, hsv);
    float brightness = (.2126f * Color.red(background) + .7152f * Color.green(background) + .0722f * Color.blue(background)) / 255f;
    if (brightness > .7f) hsv[2] = Math.max(0f, hsv[2] - .15f);
    else { hsv[1] = Math.min(1f, hsv[1] + .025f); hsv[2] = Math.min(1f, hsv[2] + .15f); }
    iconColor = Color.HSVToColor(hsv);
    fill.setColor(iconColor);
    fill.setAlpha(127);
    line.setColor(Theme.getColor(ColorId.fillingPositive));
    updateShader();
    invalidate();
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // BotWebViewContainer's 512-unit path is displayed inside a centered 100dp view.
    float scale = Screen.dp(100) / 512f, c = 256f, size = 133.69f, halfGap = 31.29f / 2;
    float left = (w - Screen.dp(100)) / 2f, top = (h - Screen.dp(100)) / 2f;
    tiles.reset();
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
      float tileX = x == 0 ? c - size - halfGap : c + halfGap;
      float tileY = y == 0 ? c - size - halfGap : c + halfGap;
      tiles.addRoundRect(new RectF(left + tileX * scale, top + tileY * scale,
        left + (tileX + size) * scale, top + (tileY + size) * scale), 18 * scale, 18 * scale, Path.Direction.CW);
    }
    updateShader();
  }

  private void updateShader () {
    gradientWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels * 2f);
    float half = Math.min(.49f, Screen.dp(180) / gradientWidth / 2f);
    shimmer.setShader(new LinearGradient(0, 0, gradientWidth, 0,
      new int[] {0, 0, (iconColor & 0xffffff) | 0x7f000000, 0, 0},
      new float[] {0, .5f - half, .5f, .5f + half, 1}, Shader.TileMode.REPEAT));
  }

  @Override
  protected void onAttachedToWindow () { super.onAttachedToWindow(); lastFrame = 0; invalidate(); }

  @Override
  protected void onDraw (Canvas canvas) {
    long now = SystemClock.uptimeMillis();
    float dt = lastFrame == 0 ? 0 : Math.min(64, now - lastFrame) / 1000f;
    lastFrame = now;
    translation += dt * gradientWidth / 1.8f;
    if (translation > gradientWidth / 2) translation -= gradientWidth;
    shaderMatrix.setTranslate(translation, 0);
    shimmer.getShader().setLocalMatrix(shaderMatrix);
    canvas.drawPath(tiles, fill);
    canvas.drawPath(tiles, shimmer);

    // Critically damped spring, stiffness 400, matching WebProgressView without another animator dependency.
    float offset = progress - targetProgress, coefficient = velocity + 20 * offset;
    float decay = (float) Math.exp(-20 * dt);
    progress = targetProgress + (offset + coefficient * dt) * decay;
    velocity = (velocity - 20 * coefficient * dt) * decay;
    float alpha = completedAt == 0 ? 1f : Math.max(0f, 1f - (now - completedAt) / 200f);
    line.setAlpha(Math.round(255 * alpha));
    if (progress > 0 && alpha > 0) canvas.drawLine(0, getHeight() - Screen.dp(1), getWidth() * progress, getHeight() - Screen.dp(1), line);
    if (isShown()) postInvalidateOnAnimation();
  }
}
