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
 * File created on 11/05/2017
 */
package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class ColorToneView extends View implements ClickHelper.Delegate {
  private float hue = -1f;

  @Nullable
  private ColorPreviewView preview;

  public ColorToneView (Context context) {
    super(context);
  }

  public void setPreview (ColorPreviewView preview) {
    this.preview = preview;
  }

  private int prevWidth, prevHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    if (prevWidth != width || prevHeight != height) {
      prevWidth = width;
      prevHeight = height;
      // backgroundPaint.setShader(new LinearGradient(width / 2, 0, width / 2, height, 0xffffffff, 0xff000000, Shader.TileMode.CLAMP));
      setShader();

      if (preview != null)
        preview.updateToneSizes(width, height);
    }
  }

  public void setColor (int color, float[] hsv) {
    color = ColorUtils.color(255, color);
    float hue = hsv[0] / 360f;
    if (this.hue != hue) {
      this.circleColor = color;
      this.hue = hue;
      setShader();
      this.saturation = hsv[1];
      this.value = hsv[2];
      updateCircleColor(false);
      invalidate();
    } else if (this.circleColor != color || this.saturation != hsv[1] || this.value != hsv[2]) {
      this.circleColor = color;
      this.saturation = hsv[1];
      this.value = hsv[2];
      updateCircleColor(false);
      invalidate();
    }
  }

  private void updateCircleColor (boolean updateColor) {
    hsv[0] = hue * 360f;
    hsv[1] = saturation;
    hsv[2] = value;

    if (updateColor) {
      this.circleColor = Color.HSVToColor(hsv);
    }

    /*int r = Color.red(circleColor), g = Color.green(circleColor), b = Color.blue(circleColor);
    int average = (r + g + b) / 3;
    int min = Math.min(r, Math.min(g, b));
    int max = Math.max(r, Math.max(g, b));
    float limit = 0xe0;
    if (average >= limit && ((float) (max - min) / 255f < 0.1f)) {
      float scale = Math.max(limit / (float) r, Math.max(limit / (float) g, limit / (float) b));
      r *= scale;
      g *= scale;
      b *= scale;
      circleOutline = Color.argb(0xff, r, g, b);
    } else {
      circleOutline = 0xffffffff;
    }*/
    this.circleOutline = Color.red(circleColor) > 0x9a && Color.green(circleColor) > 0x9a && Color.blue(circleColor) > 0x9a ? 0xff000000 : 0xffffffff;
  }

  public void setHue (float hue) {
    if (this.hue != hue) {
      this.hue = hue;
      setShader();
      invalidate();
    }
  }

  private final float[] hsv = new float[3];
  private float saturation = -1f, value = -1f;
  private int circleOutline, circleColor;

  private void setShader () {
    int viewWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    int viewHeight = getMeasuredHeight() - getPaddingLeft() - getPaddingRight();

    if (hue == -1 || viewWidth <= 0 || viewHeight <= 0) {
      return;
    }

    int size = Math.min(viewWidth, viewHeight);
    float scale = Math.max(30f / (float) size, Math.min(.05f, 100f / (float) size));
    size *= scale;

    if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() != size || bitmap.getHeight() != size) {
      if (bitmap != null && !bitmap.isRecycled()) {
        bitmap.recycle();
      }
      bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    }

    hsv[0] = hue * 360f;
    hsv[1] = hsv[2] = 1f;
    int color = Color.HSVToColor(hsv);

    // long ms = SystemClock.uptimeMillis();
    for (int i = 0; i < size; i++) {
      float x = (float) i / (float) size;
      int horizontalColor = ColorUtils.fromToArgb(0xffffffff, color, x);
      for (int j = 0; j < size; j++) {
        float y = (float) j / (float) size;
        int resultColor = ColorUtils.fromToArgb(horizontalColor, 0xff000000, y);
        bitmap.setPixel(i, j, resultColor);
      }
    }
    if (paint != null) {
      paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    }
    // Log.i("duration: %dms", SystemClock.uptimeMillis() - ms);
  }

  private Paint paint;
  private Bitmap bitmap;

  @Override
  protected void onDraw (Canvas c) {
    if (hue == -1 || bitmap == null || bitmap.isRecycled()) {
      return;
    }

    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();
    int startX = getPaddingLeft();
    int startY = getPaddingTop();
    int width = viewWidth - startX - getPaddingRight();
    int height = viewHeight - startY - getPaddingBottom();

    int rounding = Screen.dp(3f);

    RectF rectF = Paints.getRectF();
    rectF.set(startX, startY, startX + width, startY + height);
    if (width == viewWidth && height == viewHeight) {
      c.drawBitmap(bitmap, null, rectF, Paints.getBitmapPaint());
    } else {
      c.save();
      c.translate(startX, startY);
      rectF.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
      float cx = (float) width / rectF.right;
      float cy = (float) height / rectF.bottom;
      c.scale(cx, cy, 0, 0);
      if (paint == null) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
      }
      c.drawRoundRect(rectF, (float) rounding / cx, (float) rounding / cy, paint);
      c.restore();
    }

    if (this.saturation != -1f && this.value != -1f) {
      boolean clipped = width != viewWidth || height != viewHeight;
      if (clipped) {
        c.save();
        c.clipRect(startX, startY, startX + width, startY + height);
      }
      int radius = Screen.dp(8f);
      float cx = calculateCenterX();
      float cy = calculateCenterY();
      c.drawCircle(cx, cy, radius, Paints.fillingPaint(circleColor));
      c.drawCircle(cx, cy, radius, Paints.strokeBigPaint(circleOutline));
      if (clipped) {
        c.restore();
        rectF.set(startX, startY, startX + width, startY + height);
        c.drawRoundRect(rectF, rounding, rounding, Paints.strokeSeparatorPaint(Theme.separatorColor()));
      }
    }
  }

  // Touch listener

  private float calculateCenterX () {
    int startX = getPaddingLeft();
    int width = getMeasuredWidth() - startX - getPaddingRight();
    return startX + width * saturation;
  }

  private float calculateCenterY () {
    int startY = getPaddingTop();
    int height = getMeasuredHeight() - startY - getPaddingBottom();
    return startY + height * (1f - value);
  }

  public interface ChangeListener {
    void onValuesChanged (ColorToneView view, float saturation, float value, boolean isFinished);
    default void onValuesChangeStarted (ColorToneView view, boolean isChanging) { }
  }

  private @Nullable ChangeListener listener;
  private ClickHelper clickHelper;

  public void setListener (ChangeListener listener) {
    if (this.listener != listener) {
      this.listener = listener;
      if (clickHelper == null)
        this.clickHelper = new ClickHelper(this);
    }
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return x >= getPaddingLeft() && x < getMeasuredWidth() - getPaddingRight() && y >= getPaddingTop() && y < getMeasuredHeight() - getPaddingBottom();
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (!isDragging) {
      dropTouch();
      int startX = getPaddingLeft();
      int startY = getPaddingTop();
      int width = getMeasuredWidth() - startX - getPaddingRight();
      int height = getMeasuredHeight() - startY - getPaddingBottom();
      x -= startX;
      y -= startY;

      float newSaturation = x / (float) width;
      float newValue = 1f - y / (float) height;

      if (this.saturation != newSaturation || this.value != newValue) {
        this.saturation = newSaturation;
        this.value = newValue;
        listener.onValuesChanged(this, newSaturation, newValue, true);
        updateCircleColor(true);
        invalidate();
      }
    }
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return needClickAt(this, x, y);
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (!isDragging) {
      UI.forceVibrate(this, true);
      this.isSlowDragging = true;
      this.listenDrag = true;
      if (parent == null && (parent = getParent()) != null)
        parent.requestDisallowInterceptTouchEvent(true);
      if (listener != null) {
        listener.onValuesChangeStarted(this, true);
      }
      return true;
    }
    return false;
  }

  private float startX, startY;
  private float startSaturation, startValue;
  private ViewParent parent;

  private boolean isDragging, isSlowDragging;
  private boolean listenDrag, slowDragHorizontal;

  private void dropTouch () {
    isDragging = false;
    listenDrag = false;
    if (parent != null) {
      parent.requestDisallowInterceptTouchEvent(false);
      parent = null;
    }
    if (hadTemporaryUpdates) {
      if (listener != null) {
        listener.onValuesChanged(this, saturation, value, true);
      }
      hadTemporaryUpdates = false;
    }
    if (listener != null) {
      listener.onValuesChangeStarted(this, false);
    }
  }

  private void startDrag (float x, float y, boolean horizontal) {
    if (parent == null && (parent = getParent()) != null) {
      parent.requestDisallowInterceptTouchEvent(true);
    }
    startX = x;
    startY = y;
    listenDrag = false;
    isDragging = true;
    startSaturation = saturation;
    startValue = value;
    slowDragHorizontal = horizontal;
    if (listener != null) {
      listener.onValuesChangeStarted(this, true);
    }
  }

  private boolean isInsideCircle (float x, float y) {
    float cx = calculateCenterX();
    float cy = calculateCenterY();
    float radius = Screen.dp(32f);
    return x >= cx - radius && x < cx + radius && y >= cy - radius && y < cy + radius;
  }

  private boolean hadTemporaryUpdates;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (listener == null)
      return super.onTouchEvent(e);

    boolean res = clickHelper.onTouchEvent(this, e);
    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (!needClickAt(this, x, y)) {
          dropTouch();
          return false;
        }

        startX = x;
        startY = y;

        isDragging = false;
        isSlowDragging = false;

        if (listenDrag = isInsideCircle(x, y)) {
          if ((parent = getParent()) != null)
            parent.requestDisallowInterceptTouchEvent(true);
          return true;
        }

        return res;
      }
      case MotionEvent.ACTION_MOVE: {
        if (listenDrag) {
          float diffX = Math.abs(x - startX);
          float diffY = Math.abs(y - startY);
          if (Math.max(diffX, diffY) > Screen.getTouchSlop()) {
            startDrag(x, y, diffX > diffY);
            return true;
          }
          return false;
        } else if (isDragging) {
          float width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
          float height = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

          float addX = (x - startX) / width;
          float addY = (y - startY) / height;
          if (isSlowDragging) {
            if (slowDragHorizontal) {
              addX *= .075f;
              addY = 0f;
            } else {
              addY *= .075f;
              addX = 0f;
            }
          }

          float newSaturation = MathUtils.clamp(startSaturation + addX);
          float newValue = MathUtils.clamp(startValue - addY);

          if (this.saturation != newSaturation || this.value != newValue) {
            this.saturation = newSaturation;
            this.value = newValue;
            this.hadTemporaryUpdates = true;
            listener.onValuesChanged(this, newSaturation, newValue, false);
            updateCircleColor(true);
            invalidate();
          }
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP: {
        dropTouch();
        break;
      }
    }

    return res;
  }
}
