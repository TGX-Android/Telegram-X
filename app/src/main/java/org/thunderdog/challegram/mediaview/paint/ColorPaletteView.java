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
 * File created on 15/11/2018
 */
package org.thunderdog.challegram.mediaview.paint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.FloatListener;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class ColorPaletteView extends View implements ClickHelper.Delegate {
  private final Paint colorPaint;
  private final Paint transparentGradientPaint;

  private int color = 0xff000000;
  private float value = -1f;

  @Nullable
  private FloatListener listener;

  private final ClickHelper helper;

  public ColorPaletteView (Context context, boolean inTransparentMode) {
    super(context);
    this.colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    if (inTransparentMode) {
      this.transparentGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      this.hsv = null;
    } else {
      this.transparentGradientPaint = null;
      this.hsv = new float[3];
      this.hsv[0] = -1f;
      this.hsv[1] = this.hsv[2] = 1f;
    }
    this.helper = new ClickHelper(this);
  }

  private boolean hadTemporaryChanges;

  private void dispatchValue (float value, boolean isFinished, boolean force) {
    value = MathUtils.clamp(value);
    if (this.value != value) {
      this.value = value;
      if (hsv != null) {
        this.hsv[0] = value * 360f;
        this.color = Color.HSVToColor(hsv);
      }
      hadTemporaryChanges = !isFinished;
      if (listener != null) {
        listener.onValueChange(this, value, isFinished);
      }
      invalidate();
    } else if (force || (isFinished && hadTemporaryChanges)) {
      hadTemporaryChanges = false;
      if (listener != null)
        listener.onValueChange(this, value, true);
    }
  }

  public void setValueListener (FloatListener listener) {
    this.listener = listener;
  }

  public void setTransparentColor (int color) {
    if (transparentGradientPaint == null)
      throw new IllegalStateException();
    float alpha = (float) Color.alpha(color) / 255f;
    color = ColorUtils.color(255, color);
    if (this.color != color) {
      this.color = color;
      this.value = alpha;
      updateShader();
      invalidate();
    } else if (this.value != alpha) {
      this.value = alpha;
      invalidate();
    }
  }

  private final float[] hsv;

  public void setHue (float hue) {
    if (hsv == null)
      throw new IllegalStateException();
    if (this.hsv[0] != hue) {
      this.value = hue / 360f;
      this.hsv[0] = hue;
      this.color = Color.HSVToColor(hsv);
      invalidate();
    }
  }

  private int lastWidth, lastHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int viewWidth = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
    int viewHeight = Math.max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
    if (this.lastWidth != viewWidth || this.lastHeight != viewHeight) {
      this.lastWidth = viewWidth;
      this.lastHeight = viewHeight;
      updateShader();
    }
  }

  private void updateShader () {
    int viewWidth = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
    int viewHeight = Math.max(0, getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
    if (viewWidth == 0 || viewHeight == 0)
      return;
    if (transparentGradientPaint != null) {
      if (transparentGradientPaint.getShader() == null) {
        int size = viewHeight / 4;
        Bitmap bitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.drawRect(size, 0, size * 2, size, Paints.fillingPaint(0xffcccccc));
        c.drawRect(0, size, size, size * 2, Paints.fillingPaint(0xffcccccc));
        transparentGradientPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
      }
      colorPaint.setShader(new LinearGradient(0, viewHeight / 2, viewWidth, viewHeight / 2, 0, color, Shader.TileMode.CLAMP));
    } else {
      colorPaint.setShader(new LinearGradient(viewHeight / 2, 0, viewWidth, viewHeight / 2, DrawAlgorithms.COLOR_PICKER_COLORS_NICE, null, Shader.TileMode.MIRROR));
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    int paddingLeft = getPaddingLeft();
    int paddingTop = getPaddingTop();
    int width = getMeasuredWidth() - paddingLeft - getPaddingRight();
    int height = getMeasuredHeight() - paddingTop - getPaddingBottom();
    if (paddingLeft != 0 || paddingTop != 0) {
      c.save();
      c.translate(paddingLeft, paddingTop);
    }
    RectF rectF = Paints.getRectF();
    rectF.set(0, 0, width, height);
    int rounding = Screen.dp(3f);
    if (transparentGradientPaint != null) {
      c.drawRoundRect(rectF, rounding, rounding, transparentGradientPaint);
    }
    c.drawRoundRect(rectF, rounding, rounding, colorPaint);
    if (transparentGradientPaint != null) {
      c.drawRoundRect(rectF, rounding, rounding, Paints.strokeSeparatorPaint(Theme.separatorColor()));
    }
    if (value != -1f) {
      int innerMargin = Screen.dp(2f);
      int controlWidth = Screen.dp(7f);
      float cx = innerMargin + (width - innerMargin * 2 - controlWidth) * value;
      rectF.set(cx, innerMargin, cx + controlWidth, innerMargin + (height - innerMargin - innerMargin));
      int innerRounding = Screen.dp(2.5f);
      if (transparentGradientPaint != null) {
        c.drawRoundRect(rectF, innerRounding, innerRounding, Paints.strokeSeparatorPaint(0xffcccccc));
      }
      c.drawRoundRect(rectF, innerRounding, innerRounding, Paints.fillingPaint(0xffffffff));
      int padding = Screen.dp(1.5f);
      rectF.left += padding;
      rectF.top += padding;
      rectF.right -= padding;
      rectF.bottom -= padding;
      c.drawRoundRect(rectF, innerRounding, innerRounding, Paints.fillingPaint(color));
    }

    if (paddingLeft != 0 || paddingTop != 0) {
      c.restore();
    }
  }

  // Touch handling

  private float convertToFactor (float x) {
    int paddingLeft = getPaddingLeft();
    int width = getMeasuredWidth();
    width -= paddingLeft + getPaddingRight();
    return x / width;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return true;
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return true;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (!isDragging) {
      dispatchValue(convertToFactor(x), true, true);
    } else {
      dropTouch();
    }
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (startDrag(x, true)) {
      inSlowDrag = true;
      return true;
    }/* else if (isDragging) {
      startValue = value;
      startX = x;
      inSlowDrag = true;
    }*/
    return false;
  }

  private ViewParent requestedParent;

  private float startX, startValue, downY;
  private boolean catchDrag, isDragging, inSlowDrag;

  private void dropTouch () {
    if (isDragging)
      dispatchValue(value, true, false);
    if (requestedParent != null) {
      requestedParent.requestDisallowInterceptTouchEvent(false);
      requestedParent = null;
    }
    catchDrag = inSlowDrag = isDragging = false;
    if (listener != null) {
      listener.onValuesChangeStarted(this, false);
    }
  }

  private boolean startDrag (float x, boolean byLongPress) {
    if (!isDragging) {
      catchDrag = false;
      isDragging = true;
      startX = x;
      startValue = value;
      if (byLongPress)
        UI.forceVibrate(this, true);
      if ((requestedParent = getParent()) != null) {
        requestedParent.requestDisallowInterceptTouchEvent(true);
      }
      if (listener != null) {
        listener.onValuesChangeStarted(this, true);
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res = helper.onTouchEvent(this, e);
    float x = e.getX();
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        startX = x;
        downY = e.getY();
        if (catchDrag = listener != null) {
          int cx = getPaddingLeft() + (int) ((getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) * value);
          int bound = Screen.dp(24f);
          if (x >= cx - bound && x < cx + bound) {
            if ((requestedParent = getParent()) != null) {
              requestedParent.requestDisallowInterceptTouchEvent(true);
            }
          }
        }
        return catchDrag || res;
      }
      case MotionEvent.ACTION_MOVE: {
        if (catchDrag) {
          float y = e.getY();
          float xDiff = Math.abs(x - startX);
          float yDiff = Math.abs(y - downY);
          float touchSlop = Screen.getTouchSlop();
          if (yDiff > xDiff && yDiff >= touchSlop) {
            catchDrag = false;
            return false;
          } else if (xDiff >= touchSlop) {
            startDrag(x, false);
            return true;
          } else if (xDiff > touchSlop * .5f && xDiff > yDiff) {
            if ((requestedParent = getParent()) != null) {
              requestedParent.requestDisallowInterceptTouchEvent(true);
            }
            return true;
          }
          return false;
        } else if (isDragging) {
          float diff = (x - startX) / (float) (getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
          if (inSlowDrag)
            diff *= .075f;

          dispatchValue(MathUtils.clamp(startValue + diff), false, false);
          return true;
        }
        return false;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP: {
        dropTouch();
        break;
      }
    }
    return super.onTouchEvent(e);
  }
}
