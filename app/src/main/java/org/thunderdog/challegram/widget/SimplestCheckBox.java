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
 * File created on 08/02/2017
 */
package org.thunderdog.challegram.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import androidx.annotation.FloatRange;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.HashMap;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

/**
 * Circular check box that doesn't require separate view to draw
 */
public class SimplestCheckBox {
  private static TextPaint counterPaint;

  private final Bitmap bitmap;
  private final Canvas c;

  private float lastDrawnFactor, lastDrawnSquareFactor;
  private int lastFillingColor, lastCheckColor;
  private String lastCounter;
  private float lastCounterWidth;

  public static int size () {
    return Screen.dp(20f) + Screen.dp(2f) * 2;
  }

  private SimplestCheckBox (float initialFactor, String counter, float counterWidth, int fillingColor, int contentColor, boolean isNegative, float squareFactor) {
    int size = size();
    this.bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    this.c = new Canvas(bitmap);
    drawInBitmap(initialFactor, true, counter, counterWidth, fillingColor, contentColor, isNegative, squareFactor);
  }

  public static SimplestCheckBox newInstance (float initialFactor, String counter) {
    return newInstance(initialFactor, counter, ColorId.checkActive, ColorId.checkContent, false, 0f);
  }

  public static SimplestCheckBox newInstance (float initialFactor, String counter, int fillingColor, int contentColor, boolean isNegative, float squareFactor) {
    return new SimplestCheckBox(initialFactor, counter, getCounterWidth(counter), fillingColor, contentColor, isNegative, squareFactor);
  }

  public void destroy () {
    bitmap.recycle();
  }

  public static void reset () {
    if (counterPaint != null) {
      counterPaint.setTextSize(Screen.dp(12f));
    }
    if (frames != null) {
      for (int i = 0; i < frames.length; i++) {
        if (frames[i] != null) {
          frames[i].destroy();
          frames[i] = null;
        }
      }
    }
  }

  private static void prepareCounterPaint () {
    if (counterPaint == null) {
      counterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      counterPaint.setTypeface(Fonts.getRobotoBold());
      counterPaint.setTextSize(Screen.dp(12f));
    }
  }

  private void drawInBitmap (@FloatRange(from = 0.0, to = 1.0) final float factor, boolean force, String counter, float counterWidth, int fillingColor, int contentColor, boolean isNegative, float squareFactor) {
    if (!force && this.lastDrawnFactor == factor && this.lastFillingColor == fillingColor && this.lastCheckColor == contentColor && this.lastDrawnSquareFactor == squareFactor && StringUtils.equalsOrBothEmpty(this.lastCounter, counter)) {
      return;
    }

    this.lastDrawnFactor = factor;
    this.lastFillingColor = fillingColor;
    this.lastCheckColor = contentColor;
    this.lastCounter = counter;
    this.lastCounterWidth = counterWidth;
    this.lastDrawnSquareFactor = squareFactor;

    bitmap.eraseColor(0);

    final int centerX = bitmap.getWidth() / 2;
    final int centerY = bitmap.getHeight() / 2;

    int radius = Screen.dp(10f);
    radius -= Screen.dp(1f) * squareFactor;
    final int eraseRadius = (int) ((float) radius * (1f - factor));

    if (eraseRadius < radius) {
      final float squareRadius = squareFactor > 0f ? MathUtils.fromTo(radius, Screen.dp(3f), squareFactor) : radius;
      RectF rectF = Paints.getRectF();
      if (squareFactor > 0f) {
        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        c.drawRoundRect(rectF, squareRadius, squareRadius, Paints.fillingPaint(fillingColor));
      } else {
        c.drawCircle(centerX, centerY, radius, Paints.fillingPaint(fillingColor));
      }

      if (StringUtils.isEmpty(counter)) {
        if (isNegative) {
          DrawAlgorithms.drawAnimatedCross(c, centerX, centerY, factor, contentColor, Screen.dp(8f));
        } else {
          final float fx = factor <= .2f ? 0f : (factor - .2f) / .8f;

          if (fx > 0f) {
            final float t1;
            final float f1, f2;

            t1 = .3f;
            f1 = fx <= t1 ? fx / t1 : 1f;
            f2 = fx <= t1 ? 0f : (fx - t1) / (1f - t1);

            // check
            c.save();
            c.translate(-Screen.dp(.35f), centerY);
            c.rotate(-45f);

            final int w2max = Screen.dp(10f);
            final int h1max = Screen.dp(5f);

            final int w2 = (int) ((float) w2max * f2);
            final int h1 = (int) ((float) h1max * f1);

            final int x1, y1;

            x1 = Screen.dp(4f);
            y1 = Screen.dp(11f);

            int lineSize = Screen.dp(2f);
            c.drawRect(x1, y1 - h1max, x1 + lineSize, y1 - h1max + h1, Paints.fillingPaint(contentColor));
            c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(contentColor));

            c.restore();
          }
        }
      } else {
        if (factor < 1f) {
          c.save();
          float scale = .6f + .4f * factor;
          c.scale(scale, scale, centerX, centerY);
          counterPaint.setColor(ColorUtils.alphaColor(factor, contentColor));
        } else {
          counterPaint.setColor(contentColor);
        }
        c.drawText(counter, centerX - counterWidth / 2, centerY + Screen.dp(4.5f), counterPaint);
        if (factor < 1f) {
          c.restore();
        }
      }

      // erase
      if (eraseRadius != 0) {
        if (squareFactor > 0f) {
          c.save();
          c.scale((1f - factor), (1f - factor), centerX, centerY);
          c.drawRoundRect(rectF, squareRadius, squareRadius, Paints.getErasePaint());
          c.restore();
        } else {
          c.drawCircle(centerX, centerY, eraseRadius, Paints.getErasePaint());
        }
      }
    }
  }

  private static final long ANIMATION_DURATION = 165l;
  private static final int FRAMES_COUNT = Math.round(60f * ((float) ANIMATION_DURATION / 1000f)) * 2;
  private static SimplestCheckBox[] frames;
  private static HashMap<String, Float> counterWidths;

  private static int frameIndex (float factor) {
    return Math.round(MathUtils.clamp(factor) * (float) (FRAMES_COUNT - 1));
  }

  public static void draw (Canvas c, Receiver receiver, float factor) {
    final double radians = Math.toRadians(45f);

    final int x = receiver.centerX() + (int) ((float) receiver.getWidth() / 2 * Math.sin(radians));
    final int y = receiver.centerY() + (int) ((float) receiver.getHeight() / 2 * Math.cos(radians));

    draw(c, x, y, factor, null, null);
  }

  private static float getCounterWidth (String counter) {
    if (StringUtils.isEmpty(counter)) {
      return 0f;
    }
    float counterWidth;
    Float counterWidthObj = null;
    if (counterWidths == null) {
      counterWidths = new HashMap<>();
    } else {
      counterWidthObj = counterWidths.get(counter);
    }
    if (counterWidthObj == null) {
      prepareCounterPaint();
      counterWidth = U.measureText(counter, counterPaint);
      counterWidths.put(counter, counterWidth);
    } else {
      counterWidth = counterWidthObj;
    }
    return counterWidth;
  }

  public static void draw (Canvas c, int centerX, int centerY, float factor, String counter) {
    draw(c, centerX, centerY, factor, counter, null);
  }

  public static void draw (Canvas c, int centerX, int centerY, float factor, String counter, SimplestCheckBox frame) {
    draw(c, centerX, centerY, factor, counter, frame, Theme.checkFillingColor(), Theme.checkCheckColor(), false, 0f);
  }

  public static void draw (Canvas c, int centerX, int centerY, float factor, String counter, SimplestCheckBox frame, int fillingColor, int checkColor, boolean isNegative, float squareFactor) {
    boolean draw = true;
    if (frame == null) {
      final int index = frameIndex(factor);
      if (index == 0) {
        return;
      }
      if (frames == null) {
        frames = new SimplestCheckBox[FRAMES_COUNT];
      }
      if (frames[index] == null) {
        frames[index] = frame = new SimplestCheckBox((float) index / (float) (FRAMES_COUNT - 1), counter, getCounterWidth(counter), fillingColor, checkColor, isNegative, squareFactor);
        draw = false;
      } else {
        frame = frames[index];
      }
    }
    if (draw) {
      float counterWidth;
      if (counter != null && StringUtils.equalsOrBothEmpty(counter, frame.lastCounter)) {
        counterWidth = frame.lastCounterWidth;
      } else {
        counterWidth = getCounterWidth(counter);
      }
      frame.drawInBitmap(factor, false, counter, counterWidth, fillingColor, checkColor, isNegative, squareFactor);
    }
    c.drawBitmap(frame.bitmap, centerX - frame.bitmap.getWidth() / 2f, centerY - frame.bitmap.getHeight() / 2f, Paints.getBitmapPaint());
  }
}
