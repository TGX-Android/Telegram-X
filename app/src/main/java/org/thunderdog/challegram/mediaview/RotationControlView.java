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
 * File created on 11/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.FloatRange;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.navigation.TriangleView;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class RotationControlView extends View implements FactorAnimator.Target {
  public interface Callback {
    boolean allowPreciseRotation (RotationControlView view);
    void onPreciseActiveFactorChanged (float activeFactor);
    void onPreciseActiveStateChanged (boolean isActive);
    void onPreciseRotationChanged (float newValue);
  }

  private TriangleView triangleView;
  private Paint numberPaint;
  private Paint numberSmallPaint;

  private Callback callback;

  private float minusWidth, degreeWidth;
  private float width0, width15, width30, width45, width60;

  private static final String STR__15 = "-15\u00B0";
  private static final String STR__30 = "-30\u00B0";
  private static final String STR__45 = "-45\u00B0";
  private static final String STR__60 = "-60\u00B0";

  private static final String STR_0 = "0°";
  private static final String STR_15 = "15\u00B0";
  private static final String STR_30 = "30\u00B0";
  private static final String STR_45 = "45\u00B0";
  private static final String STR_60 = "60\u00B0";

  private static String getText (int degrees) {
    switch (degrees) {
      case -60: return STR__60;
      case -45: return STR__45;
      case -30: return STR__30;
      case -15: return STR__15;
      case 0: return STR_0;
      case 15: return STR_15;
      case 30: return STR_30;
      case 45: return STR_45;
      case 60: return STR_60;
    }
    return "";
  }

  private float getTextWidth (int degrees) {
    switch (degrees) {
      case -60: return width60 + minusWidth;
      case -45: return width45 + minusWidth;
      case -30: return width30 + minusWidth;
      case -15: return width15 + minusWidth;
      case 0: return width0;
      case 15: return width15;
      case 30: return width30;
      case 45: return width45;
      case 60: return width60;
    }
    return 0;
  }

  private final int radius, spacing;

  public RotationControlView (Context context) {
    super(context);
    setPadding(Screen.dp(20f), 0, Screen.dp(20f), 0);

    numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    numberPaint.setTextSize(Screen.dp(14f));
    numberPaint.setColor(0xffffffff);
    numberPaint.setTypeface(Fonts.getRobotoRegular());

    numberSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    numberSmallPaint.setTextSize(Screen.dp(12f));
    numberSmallPaint.setColor(0xffffffff);
    numberSmallPaint.setTypeface(Fonts.getRobotoRegular());

    degreeWidth = U.measureText("°", numberSmallPaint);
    width0 = U.measureText("0", numberSmallPaint);
    width15 = U.measureText("15", numberSmallPaint);
    width30 = U.measureText("30", numberSmallPaint);
    width45 = U.measureText("45", numberSmallPaint);
    width60 = U.measureText("60", numberSmallPaint);
    minusWidth = U.measureText("-", numberSmallPaint);

    triangleView = new TriangleView();
    triangleView.setColor(0xffffffff);

    this.radius = Screen.dp(1.5f);
    this.spacing = Screen.dp(6.5f);

    setValueInt(0);
  }

  @FloatRange(from = -45f, to = 45f)
  private float value;

  public void reset (float degrees, boolean callListeners) {
    if (callListeners) {
      updateValue(degrees);
    } else {
      setValue(degrees);
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  private int valueInt;
  private String valueStr;
  private float valueWidth;

  private void setValueInt (int value) {
    if (this.valueInt != value || valueStr == null) {
      this.valueInt = value;
      this.valueStr = Integer.toString(value) + '°';
      this.valueWidth = U.measureText(valueStr, 0, valueStr.length() - 1, numberPaint);
    }
  }

  private void updateValue (float value) {
    value = Math.max(-45f, Math.min(45f, value));
    if (this.value != value) {
      setValue(value);
      if (callback != null) {
        callback.onPreciseRotationChanged(value);
      }
    }
  }

  private void setValue (float value) {
    if (this.value !=  value) {
      this.value = value;
      setValueInt(Math.round(value));
      invalidate();
    }
  }

  private float startX;
  private float startValue;
  private boolean isUp;

  private FactorAnimator activeAnimator;
  private float activeFactor;
  private static final int ACTIVE_ANIMATOR = 0;

  private void animateFactor (float toFactor) {
    if (activeAnimator == null) {
      activeAnimator = new FactorAnimator(ACTIVE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 140, activeFactor);
    }
    activeAnimator.animateTo(toFactor);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ACTIVE_ANIMATOR: {
        if (this.activeFactor != factor) {
          this.activeFactor = factor;
          invalidate();
          if (callback != null) {
            callback.onPreciseActiveFactorChanged(factor);
          }
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private void setUp (boolean isUp) {
    isUp = isUp && (callback == null || callback.allowPreciseRotation(this));
    if (this.isUp != isUp) {
      this.isUp = isUp;
      animateFactor(isUp ? 1f : 0f);
      if (callback != null) {
        callback.onPreciseActiveStateChanged(isUp);
      }
    }
  }

  private static final float MAX_WIDTH = 192f; // 168f
  private static final float MOVE_FACTOR = .1f;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    int currentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        startX = e.getX();

        int cx = getPaddingLeft() + (currentWidth) / 2;
        currentWidth = Math.min(currentWidth, Screen.dp(MAX_WIDTH));

        setUp(startX >= cx - currentWidth / 2 && startX <= cx + currentWidth / 2);
        if (isUp) {
          startValue = value;
          return true;
        }

        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isUp) {
          float diffX = (e.getX() - startX) * MOVE_FACTOR;
          float value = startValue + -45f * diffX / ((float) currentWidth / 2.5f);
          updateValue(value);
        }

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isUp) {
          setUp(false);
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (isUp) {
          setUp(false);
          return true;
        }
        break;
      }
    }
    return isUp;
  }

  private static final int DISABLED_ALPHA = 0x33;
  private static final int DEFAULT_ALPHA = 0x80;

  @Override
  protected void onDraw (Canvas c) {
    int currentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    int cx = getPaddingLeft() + (currentWidth) / 2;
    currentWidth = Math.min(currentWidth, Screen.dp(MAX_WIDTH));
    int cy = getPaddingTop() + (getMeasuredHeight() - getPaddingBottom() - getPaddingTop()) / 2;


    int sideWidth = currentWidth / 2 - radius * 2;
    int chunkWidth = sideWidth / 2;
    int currentMaxDiff = chunkWidth * 3;

    c.save();
    c.translate(cx - triangleView.getWidth() / 2, cy - Screen.dp(23f));
    triangleView.draw(c);
    c.restore();

    c.drawText(valueStr, cx - valueWidth / 2, cy + Screen.dp(5f), numberPaint);

    int startX = cx - sideWidth;
    int endX = cx + sideWidth;

    float factor = value / -45f;
    float addCircleX = factor * (float) currentMaxDiff;
    int x = startX/* - spacing*/ + Math.round(addCircleX) % spacing;
    int padding = Screen.dp(12f);
    int bound = (int) ((float) spacing * 2.5f);


    // circles

    // int allowedAlpha = 0x77;

    float realX = (int) (cx - chunkWidth * 4 + (float) (chunkWidth * 3) * factor);

    do {
      float alpha;
      if (x < startX) {
        alpha = 0f; // 1f - ((float) (startX - x) / (float) spacing);
      } else if (x > endX) {
        alpha = 0f; // 1f - ((float) (x - endX) / (float) spacing);
      } else if (x >= cx - padding && x <= cx + padding) {
        alpha = 0f;
      } else if (x >= cx - bound - padding && x <= cx - padding) {
        int distance = cx - padding - x;
        alpha = (float) distance / (float) bound;
      } else if (x >= cx + padding && x <= cx + padding + bound) {
        int distance = x - (cx + padding);
        alpha = (float) distance / (float) bound;
      } else {
        alpha = 1f;
      }

      if (alpha > 0f) {
        float allowedAlpha;
        if (x < realX + chunkWidth - radius || x > realX + chunkWidth * 7) {
          allowedAlpha = DISABLED_ALPHA;
        } else {
          allowedAlpha = DEFAULT_ALPHA + (int) ((float) (0xff - DEFAULT_ALPHA) * activeFactor);
        }
        c.drawCircle(x, cy, radius, Paints.fillingPaint(ColorUtils.color((int) (allowedAlpha * alpha), 0xffffff)));
      }

      x += spacing;
    } while (x < endX + spacing);

    // text

    x = (int) (cx - chunkWidth * 4 + (float) (chunkWidth * 3) * factor);
    int degrees = -60;
    do {
      String text = getText(degrees);
      float width = getTextWidth(degrees) + degreeWidth;
      float halfWidth = width / 2;

      float textX = x - halfWidth;
      float alpha;
      if (textX - halfWidth < startX) {
        alpha = 1f - (startX - (textX - halfWidth)) / width;
      } else if (textX + width + halfWidth > endX) {
        alpha = 1f - ((textX + width + halfWidth) - endX) / width;
      } else if (x >= cx - padding && x <= cx + padding) {
        alpha = 0f;
      } else if (x >= cx - bound - padding && x <= cx - padding) {
        int distance = cx - padding - x;
        alpha = (float) distance / (float) bound;
      } else if (x >= cx + padding && x <= cx + padding + bound) {
        int distance = x - (cx + padding);
        alpha = (float) distance / (float) bound;
      } else {
        alpha = 1f;
      }

      if (alpha > 0f) {
        float allowedAlpha = degrees == 60 || degrees == -60 ? DISABLED_ALPHA : DEFAULT_ALPHA + (int) ((float) (0xff - DEFAULT_ALPHA) * activeFactor);
        int color = ColorUtils.color((int) (allowedAlpha * alpha), 0xffffff);
        numberSmallPaint.setColor(color);
        c.drawText(text, textX, cy - Screen.dp(12f), numberSmallPaint);
      }
      degrees += 15;
      x += chunkWidth;
    } while (degrees <= 60);

  }
}
