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
 * File created on 28/02/2016 at 18:36
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

public class ShadowView extends View {
  private Paint shadowPaint;

  public ShadowView (Context context) {
    super(context);
  }

  public void setVerticalShadow (int fromColor, int toColor, int height) {
    LinearGradient shader;
    shader = new LinearGradient(0, 0, 0, height, fromColor, toColor, Shader.TileMode.CLAMP);
    if (shadowPaint == null) {
      shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint.setShader(shader);
  }

  public void setVerticalShadow (int[] colors, float[] positions, int height) {
    LinearGradient shader;
    shader = new LinearGradient(0, 0, 0, height, colors, positions, Shader.TileMode.CLAMP);
    if (shadowPaint == null) {
      shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint.setShader(shader);
  }

  public void setHorizontalShadow (int[] colors, float[] positions, int width) {
    LinearGradient shader;
    shader = new LinearGradient(0, 0, width, 0, colors, positions, Shader.TileMode.CLAMP);
    if (shadowPaint == null) {
      shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint.setShader(shader);
  }

  public void setSimpleRightShadow (boolean withDefaultSize) {
    final int[] colors = new int[] {0x2c000000, 0x25000000, 0x1d000000, 0x16000000, 0x10000000, 0x0a000000, 0x06000000, 0x03000000, 0x01000000};
    isTransparent = true;
    setHorizontalShadow(colors, null, Screen.dp(3f));
    setPadding(0, 0, 0, 0);
    if (withDefaultSize) {
      setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(7f), ViewGroup.LayoutParams.MATCH_PARENT));
    }
  }

  public void setSimpleLeftShadow (boolean withDefaultSize) {
    final int[] colors = new int[] {0x01000000, 0x03000000, 0x06000000, 0x0a000000, 0x10000000, 0x16000000, 0x1d000000, 0x25000000, 0x2c000000};
    isTransparent = true;
    setHorizontalShadow(colors, null, Screen.dp(3f));
    setPadding(Screen.dp(7f) - Screen.dp(3f), 0, 0, 0);
    if (withDefaultSize) {
      setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(7f), ViewGroup.LayoutParams.MATCH_PARENT));
    }
  }

  public static int[] bottomShadowColors () {
    return new int[] {0x18000000, 0x15000000, 0x10000000, 0x0d000000, 0x08000000, 0x05000000, 0x03000000, 0x01000000, 0x00000000}; // {0x15000000, 0x10000000, 0x0e000000, 0x08000000, 0x06000000, 0x03000000, 0x02000000, 0x01000000, 0x00000000};
  }

  public static int[] topShadowColors () {
    return new int[] {0x00000000, 0x03000000, 0x07000000, 0x0a000000}; // {0x00000000, 0x01000000, 0x03000000, 0x06000000};
  }

  public void setSimpleBottomTransparentShadow (boolean withDefaultSize) {
    final int[] colors = bottomShadowColors();
    isTransparent = true;
    setVerticalShadow(colors, null, simpleBottomShadowHeight());
    if (withDefaultSize) {
      setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(7f)));
    }
  }

  public void setSimpleTopShadow (boolean withDefaultSize, ViewController<?> themeProvider) {
    setSimpleTopShadow(withDefaultSize);
    ViewSupport.setThemedBackground(this, R.id.theme_color_background, themeProvider);
  }

  private boolean isTopShadow;
  private boolean isTransparent;

  public void setSimpleTopShadow (boolean withDefaultSize) {
    final int height = simpleTopShadowHeight();
    final int[] colors = topShadowColors();
    isTopShadow = true;
    isTransparent = true;
    setVerticalShadow(colors, null, height);
    if (withDefaultSize) {
      int viewHeight = Screen.dp(6f);
      setPadding(0, viewHeight - height, 0, 0);
      setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));
    }
  }

  private boolean alignBottom;

  public void setAlignBottom () {
    alignBottom = true;
  }

  public static int simpleTopShadowHeight () {
    return Math.max(1, Screen.dp(1f));
  }

  public static int simpleBottomShadowHeight () {
    return Screen.dp(3f);
  }

  public int getShadowTop () {
    return alignBottom ? getMeasuredHeight() - simpleTopShadowHeight() : getPaddingTop();
  }

  private void drawSimpleShadow (Canvas c, float factor) {
    if (shadowPaint != null) {
      final int paddingTop = getShadowTop(); // 7 - 2 = 5
      final int paddingLeft = getPaddingLeft();
      final boolean saved = paddingTop != 0 || paddingLeft != 0;
      if (saved) {
        c.save();
        c.translate(paddingLeft, paddingTop); // 5
      }
      shadowPaint.setAlpha((int) (255f * factor));
      c.drawRect(0, 0, getMeasuredWidth() - paddingLeft, getMeasuredHeight() - paddingTop, shadowPaint); // 7 - 5 = 2
      if (saved) {
        c.restore();
      }
    }
  }

  private void drawSeparator (Canvas c, float factor) {
    final int height = separatorHeight();
    final int paddingTop = isTopShadow ? getMeasuredHeight() - height : getPaddingTop();
    c.drawRect(0, paddingTop, getMeasuredWidth() - getPaddingLeft(), paddingTop + height, Paints.fillingPaint(ColorUtils.alphaColor(factor, Theme.separatorColor())));
  }

  @Override
  protected void onDraw (Canvas c) {
    float separatorFactor = Theme.getSeparatorReplacement();
    if (separatorFactor == 0f) {
      drawSimpleShadow(c, (isTransparent ? Theme.getShadowDepth() : 1f));
    } else if (separatorFactor == 1f) {
      drawSeparator(c, 1f);
    } else {
      drawSimpleShadow(c, (isTransparent ? Theme.getShadowDepth() : 1f) * (1f - separatorFactor));
      drawSeparator(c, separatorFactor);
    }
  }

  // Dark mode invert

  public static @ColorInt int darkColor (int color) {
    final int tr, sr, xr;
    tr = 0x30;
    sr = 0xf4;
    xr = Color.red(color);
    int x = tr - (int) ((sr - xr) * .25f);
    return Color.argb(Color.alpha(color), x, x, x);
  }

  // simple top shadow

  private static int separatorHeight () {
    return Math.max(1, Screen.dp(.5f));
  }

  private static void drawSeparator (Canvas c, int startX, int endX, int y, float alpha) {
    c.drawRect(startX, y, endX, y + separatorHeight(), Paints.fillingPaint(ColorUtils.alphaColor(alpha, Theme.separatorColor())));
  }

  private static Paint simpleTopShadowPaint;

  private static void drawTopShadowImpl (Canvas c, int startX, int endX, int y, float alpha) {
    if (simpleTopShadowPaint == null) {
      simpleTopShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int[] topShadowColors = ShadowView.topShadowColors();
      simpleTopShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleTopShadowHeight(), topShadowColors, null, Shader.TileMode.CLAMP));
    }
    simpleTopShadowPaint.setAlpha((int) (255f * Theme.getShadowDepth() * alpha));
    boolean translate = startX != 0 || y != 0;
    if (translate) {
      c.save();
      c.translate(startX, y);
    }
    c.drawRect(0, 0, endX - startX, simpleTopShadowHeight(), simpleTopShadowPaint);
    if (translate) {
      c.restore();
    }
  }

  public static void drawTopShadow (Canvas c, int startX, int endX, int y, float alpha) {
    if (alpha > 0f) {
      float separatorFactor = Theme.getSeparatorReplacement();
      if (separatorFactor == 0f) {
        drawTopShadowImpl(c, startX, endX, y - simpleTopShadowHeight(), alpha);
      } else if (separatorFactor == 1f) {
        drawSeparator(c, startX, endX, y - separatorHeight(), alpha);
      } else {
        drawTopShadowImpl(c, startX, endX, y, alpha * (1f - separatorFactor));
        drawSeparator(c, startX, endX, y - separatorHeight(), alpha * separatorFactor);
      }
    }
  }

  private static Paint simpleBottomShadowPaint;

  private static void drawBottomShadowImpl (Canvas c, int startX, int endX, int y, float alpha) {
    if (simpleBottomShadowPaint == null) {
      simpleBottomShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int[] bottomShadowColors = ShadowView.bottomShadowColors();
      simpleBottomShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleBottomShadowHeight(), bottomShadowColors, null, Shader.TileMode.CLAMP));
    }
    alpha *= Theme.getShadowDepth();
    simpleBottomShadowPaint.setAlpha((int) (255f * Theme.getShadowDepth() * alpha));
    boolean translate = startX != 0 || y != 0;
    if (translate) {
      c.save();
      c.translate(startX, y);
    }
    c.drawRect(0, 0, endX - startX, simpleBottomShadowHeight(), simpleBottomShadowPaint);
    if (translate) {
      c.restore();
    }
  }

  public static void drawBottomShadow (Canvas c, int startX, int endX, int y, float alpha) {
    if (alpha > 0f) {
      float separatorFactor = Theme.getSeparatorReplacement();
      if (separatorFactor == 0f) {
        drawBottomShadowImpl(c, startX, endX, y, alpha);
      } else if (separatorFactor == 1f) {
        drawSeparator(c, startX, endX, y, alpha);
      } else {
        drawBottomShadowImpl(c, startX, endX, y, alpha * (1f - separatorFactor));
        drawSeparator(c, startX, endX, y, alpha * separatorFactor);
      }
    }
  }

  // simple drop shadow

  private static Paint dropShadowPaint;
  private static void initDropShadow () {
    if (dropShadowPaint == null) {
      dropShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      int[] colors = ShadowView.bottomShadowColors();
      int i = 0;
      for (int c : colors) {
        colors[i] = Color.argb(Color.alpha(c) * 2, Color.red(c), Color.green(c), Color.blue(c));
        i++;
      }
      dropShadowPaint.setShader(new LinearGradient(0, 0, 0, ShadowView.simpleBottomShadowHeight(), colors, null, Shader.TileMode.CLAMP));
      dropShadowPaint.setAlpha((int) (255f * .5f));
    }
  }

  public static void drawDropShadow (Canvas c, int startX, int endX, int startY, float alpha) {
    if (alpha > 0f) {
      if (dropShadowPaint == null) {
        initDropShadow();
      }
      final float factor = 1f - Theme.getSeparatorReplacement();
      if (factor != 0f) {
        c.save();
        c.translate(startX, startY);
        float maxAlpha = Theme.getShadowDepth();
        dropShadowPaint.setAlpha((int) (255f * maxAlpha * alpha));
        c.drawRect(0, 0, endX - startX, ShadowView.simpleBottomShadowHeight(), dropShadowPaint);
        c.restore();
      }
      if (factor != 1f) {
        c.drawRect(startX, startY, endX, startY + Math.max(1, Screen.dp(.5f, 3f)), Paints.fillingPaint(ColorUtils.alphaColor(alpha * (1f - factor), Theme.separatorColor())));
      }
    }
  }
}
