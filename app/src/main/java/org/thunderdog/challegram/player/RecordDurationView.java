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
 * File created on 03/03/2016 at 20:44
 */
package org.thunderdog.challegram.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;

public class RecordDurationView extends View {
  private int alpha = 255;
  private float circleAlpha = 1f;
  private ValueAnimator animator;

  private long elapsedDeltaTime, animationStart;

  public interface TimerCallback {
    void onTimerTick ();
  }

  private TimerCallback timerCallback;

  public RecordDurationView (Context context) {
    super(context);
    if (circleRadius == 0) {
      initSizes();
    }
    setAlpha(0);
    buildCells();
    setLayoutParams(new ViewGroup.LayoutParams(width(), Screen.dp(49f)));
  }

  public void setTimerCallback (TimerCallback timerCallback) {
    this.timerCallback = timerCallback;
  }

  // Public interrupts

  public void start (long startTimeMs) {
    if (animator == null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        final android.animation.TimeAnimator animator;
        animator = new android.animation.TimeAnimator();
        animator.setTimeListener((animation, totalTime, deltaTime) -> {
          elapsedDeltaTime += deltaTime;
          if (elapsedDeltaTime >= 15) {
            elapsedDeltaTime = 0;
            if (processMillis(totalTime)) {
              invalidate();
              if (timerCallback != null) {
                timerCallback.onTimerTick();
              }
            }
          }
        });
        this.animator = animator;
      } else {
        animator = AnimatorUtils.simpleValueAnimator();
        animator.setDuration(1000l);
        animator.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
          long totalTime = SystemClock.uptimeMillis() - animationStart;
          if (elapsedDeltaTime == 0l || totalTime - elapsedDeltaTime >= 15l) {
            elapsedDeltaTime = totalTime;
            if (processMillis(totalTime)) {
              invalidate();
              if (timerCallback != null) {
                timerCallback.onTimerTick();
              }
            }
          }
        });
      }
    }
    elapsedDeltaTime = 0l;
    this.animationStart = startTimeMs;
    animator.start();
  }

  public void stop () {
    if (animator != null) {
      animator.cancel();
      elapsedDeltaTime = 0l;
    }
  }

  // Initial values

  private Cell[] cells;
  private float[] widths;

  private void buildCells () {
    TextPaint textPaint = Paints.getRegularTextPaint(15f);
    if (widths == null) {
      widths = new float[10];
      for (int i = 0; i < widths.length; i++) {
        widths[i] = U.measureText(String.valueOf(i), textPaint);
      }
    }
    cells = new Cell[7];
    cells[0] = new Cell(0);
    cells[0].noZero = true;
    cells[1] = new Cell(0);
    cells[2] = new Cell(":");
    cells[3] = new Cell(0);
    cells[4] = new Cell(0);
    cells[5] = new Cell(",");
    cells[6] = new Cell(0);
  }

  public void reset () {
    circleAlpha = 1f;
    cells[0].reset(0);
    cells[1].reset(0);
    cells[3].reset(0);
    cells[4].reset(0);
    cells[6].reset(0);
    invalidate();
  }

  @Override
  protected void onDraw (Canvas c) {
    draw(c, getMeasuredHeight() / 2);
  }

  public void draw (Canvas c, float centerY) {
    c.drawCircle(circleCenterX, centerY, circleRadius, Paints.fillingPaint(ColorUtils.alphaColor((float) alpha / 255f * circleAlpha, 0xffff3b36)));

    float cx = textLeft, cy = centerY + textOffset;
    for (Cell cell : cells) {
      cell.draw(c, cx, cy, alpha);
      cx += cell.width;
    }
  }

  private static final float SWITCH_FACTOR = .125f;

  private boolean processMillis (long timeTotal) {
    int updated = 0;
    int millis = (int) (timeTotal % 1000l) / 100;
    if (cells[6].set(millis)) {
      updated = 1;
    }

    int secondTotal = (int) (timeTotal % 10000);
    float rawSecondFactor = (float) (secondTotal % 1000) / 1000f;
    float secondFactor = rawSecondFactor >= SWITCH_FACTOR ? 1f : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(rawSecondFactor / SWITCH_FACTOR);
    int second = secondTotal / 1000;
    if (cells[4].set(second, second == 9 ? 0 : second + 1, secondFactor)) {
      updated = 1;
    }

    int time = (int) (timeTotal / 1000l);

    int ten = (time % 60) / 10;
    int nextTen = ((time + 1) % 60) / 10;
    if (cells[3].set(ten, nextTen, secondFactor)) {
      updated = 1;
    }

    int min = (time / 60) % 10;
    int nextMin = ((time + 1) / 60) % 10;
    if (cells[1].set(min, nextMin, secondFactor)) {
      updated = 1;
    }

    int tenMin = (time / 60) / 10;
    int nextTenMin = ((time + 1) / 60) / 10;
    if (cells[0].set(tenMin, nextTenMin, secondFactor)) {
      updated = 1;
    }

    //float alpha = rawSecondFactor <= .25f ? (1f - Anim.DECELERATE_INTERPOLATOR.getInterpolation(rawSecondFactor / .25f)) : rawSecondFactor > .5f ? 1f : Anim.DECELERATE_INTERPOLATOR.getInterpolation((rawSecondFactor - .25f) / .25f);

    //float alpha = rawSecondFactor <= .5f ? 1f : rawSecondFactor <= .75f ? 1f - Anim.DECELERATE_INTERPOLATOR.getInterpolation((rawSecondFactor - .5f) / .25f) : Anim.DECELERATE_INTERPOLATOR.getInterpolation((rawSecondFactor - .75f) / .25f);

    float alpha = rawSecondFactor <= .5f ? 1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(rawSecondFactor / .5f) : AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((rawSecondFactor - .5f) / .5f);

    if (this.circleAlpha != alpha && (int) (255f * circleAlpha) != (int) (255f * alpha)) {
      this.circleAlpha = alpha;
      updated = 1;
    }

    return updated == 1;
  }

  private class Cell {
    int next = -1, number = -1;
    String nextStr, str;
    boolean noZero;
    float width, factor;

    public Cell (int number) {
      reset(number);
    }

    public Cell (String str) {
      this.number = -1;
      this.str = str;
      this.width = U.measureText(str, Paints.getRegularTextPaint(15f));
    }

    public void reset (int number) {
      this.number = number;
      this.str = String.valueOf(number);
      this.width = widths[number];
      this.factor = 0f;
      this.next = -1;
      this.nextStr = null;
    }

    public boolean set (int number) {
      if (this.number != number) {
        this.number = number;
        this.str = String.valueOf(number);
        this.width = widths[number];
        return true;
      }
      return false;
    }

    public boolean set (int number, int next, float factor) {
      int updated = 0;
      if (this.number != number) {
        this.number = number;
        this.str = String.valueOf(number);
        updated = 1;
      }
      if (this.next != next) {
        this.next = next;
        this.nextStr = String.valueOf(next);
        updated = 1;
      }
      if (this.factor != factor) {
        this.factor = factor;
        updated = 1;
      }
      return updated == 1;
    }

    public void draw (Canvas c, float x, float y, int alpha) {
      TextPaint textPaint = Paints.getRegularTextPaint(15f, ColorUtils.color(alpha, Theme.textAccentColor()));
      if (factor == 0f || number == next) {
        if (number != 0 || !noZero) {
          c.drawText(str, x, y, textPaint);
        }
      } else {
        float add = textShift * factor;
        if (factor != 1f) {
          if (number != 0 || !noZero) {
            textPaint.setAlpha((int) (alpha * (1f - factor)));
            c.drawText(str, x, y + add, textPaint);
          }
        }
        if (nextStr != null) {
          textPaint.setAlpha((int) (alpha * factor));
          c.drawText(nextStr, x, y - textShift + add, textPaint);
        }
      }
    }
  }

  private static int circleRadius, circleCenterX, textLeft, textOffset;
  private static float textShift;

  private static void initSizes () {
    circleRadius = Screen.dp(5f) - 1;
    circleCenterX = Screen.dp(66f);
    textLeft = Screen.dp(5f);
    textOffset = Screen.dp(5f);
    textShift = Screen.dp(17f);
  }

  public static void resetSizes () {
    if (circleRadius != 0) {
      initSizes();
    }
  }

  public static int width () {
    if (circleRadius == 0) {
      initSizes();
    }
    return circleCenterX + circleRadius + circleRadius;
  }
}
