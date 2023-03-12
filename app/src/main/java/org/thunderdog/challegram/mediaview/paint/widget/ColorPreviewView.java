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
 * File created on 11/05/2017
 */
package org.thunderdog.challegram.mediaview.paint.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class ColorPreviewView extends View implements FactorAnimator.Target {
  private static final float MIN_RADIUS = 4f;
  private static final float MAX_RADIUS = 20f;

  private float radiusFactor;

  private int color;
  private int strokeColor;

  private ColorToneView tone;
  private ColorDirectionView direction;

  public ColorPreviewView (Context context) {
    super(context);
    setBackgroundResource(R.drawable.knob_shadow);
  }

  public void setTone (ColorToneView tone) {
    this.tone = tone;
  }

  public void setDirection (ColorDirectionView direction) {
    this.direction = direction;
  }

  public void reset (boolean inEditMode) {
    if (inEditMode) {
      setScaleFactor(0f);
      setRadiusFactor(.25f);
      setColor(0xffff3300, -1f);
    } else {
      setScaleFactor(0f);
      setRadiusFactor(1f);
      setColor(0xffff3300, -1f);
    }
  }

  private float scaleFactor = 1f;

  public void setScaleFactor (float factor) {
    if (this.scaleFactor != factor) {
      this.scaleFactor = factor;
      float scale;
      if (factor == 1f) {
        scale = 1f;
      } else {
        float startScale = (float) Screen.dp(24f) / (float) Screen.dp(44f);
        scale = startScale + (1f - startScale) * factor;
      }
      setScaleX(scale);
      setScaleY(scale);
    }
  }

  private float forceRadiusFactor;
  private FactorAnimator forceAnimator;
  private float futureForceFactor;

  private static final int ANIMATOR_FORCE = 0;

  public void setForceRadiusFactor (float forceRadiusFactor, boolean animated) {
    if (animated) {
      if (forceAnimator == null) {
        if (this.forceRadiusFactor == forceRadiusFactor) {
          return;
        }
        forceAnimator = new FactorAnimator(ANIMATOR_FORCE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l, this.forceRadiusFactor);
        forceAnimator.animateTo(forceRadiusFactor);
      } else if (forceAnimator.isAnimating()) {
        if (futureForceFactor == forceRadiusFactor) {
          return;
        }
      }
      forceAnimator.animateTo(forceRadiusFactor);
    } else {
      if (forceAnimator != null) {
        forceAnimator.forceFactor(forceRadiusFactor);
      }
      setForceRadiusFactor(forceRadiusFactor);
    }
    this.futureForceFactor = forceRadiusFactor;
  }

  private void setForceRadiusFactor (float forceRadiusFactor) {
    if (this.forceRadiusFactor != forceRadiusFactor) {
      this.forceRadiusFactor = forceRadiusFactor;
      invalidate();
    }
  }

  public void setRadiusFactor (float factor) {
    if (this.radiusFactor != factor) {
      this.radiusFactor = factor;
      notifyBrushChangeListeners();
      invalidate();
    }
    setForceRadiusFactor(0f, true);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FORCE: {
        setForceRadiusFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }

  private int toneWidth, toneHeight;

  public void updateToneSizes (int toneWidth, int toneHeight) {
    if (this.toneWidth != toneWidth) {
      this.toneWidth = toneWidth;
      updateTranslationX();
    }
    if (this.toneHeight != toneHeight) {
      this.toneHeight = toneHeight;
      updateTranslationY();
    }
  }

  private float toneFactor;

  public void setInToneFactor (float factor) {
    if (this.toneFactor != factor) {
      this.toneFactor = factor;
      updateTranslationX();
      updateTranslationY();
    }
  }

  private int targetWidth;

  public void setTargetWidth (int width) {
    if (this.targetWidth != width) {
      this.targetWidth = width;
      updateTranslationX();
    }
  }

  private void updateTranslationX () {
    float baseX = (float) targetWidth * hue;
    if (toneFactor != 0f) {
      int half = getMeasuredWidth() / 2;
      float x = hsv[1];
      float toneX = Math.max(half + Screen.dp(8f), Math.min(toneWidth - half - Screen.dp(8f), toneWidth * x)) - getLeft() - half;
      baseX = baseX + (toneX - baseX) * toneFactor;
    }
    setTranslationX(baseX);
    if (positionChangeListener != null) {
      positionChangeListener.onPositionChange();
    }
  }

  private float baseY;

  public void setBaseY (float y) {
    if (this.baseY != y) {
      this.baseY = y;
      updateTranslationY();
    }
  }

  public static final float MARGIN_DISTANCE = 64f;

  private void updateTranslationY () {
    float baseY = this.baseY;
    if (toneFactor != 0f) {
      int half = getMeasuredHeight() / 2;
      float y = hsv[2];
      float toneY = -toneHeight * y + toneHeight - (getTop() - tone.getTop()) - half - Screen.dp(MARGIN_DISTANCE);
      baseY = baseY + (toneY - baseY) * toneFactor;
    }
    setTranslationY(baseY);
    if (positionChangeListener != null) {
      positionChangeListener.onPositionChange();
    }
  }

  public interface PositionChangeListener {
    void onPositionChange ();
  }

  private PositionChangeListener positionChangeListener;

  public void setPositionChangeListener (PositionChangeListener listener) {
    this.positionChangeListener = listener;
  }

  private float hue;

  public float getHue () {
    return hue;
  }

  public float[] getHsv () {
    return hsv;
  }

  public void getHsv (float[] hsv) {
    hsv[0] = this.hue;
    hsv[1] = this.hsv[1];
    hsv[2] = this.hsv[2];
  }

  private void setStrokeColor (int color) {
    int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
    int average = (r + g + b) / 3;
    int min = Math.min(r, Math.min(g, b));
    int max = Math.max(r, Math.max(g, b));
    float limit = 0xe0;
    if (average >= limit && ((float) (max - min) / 255f < 0.1f)) {
      float scale = Math.max(limit / (float) r, Math.max(limit / (float) g, limit / (float) b));
      r *= scale;
      g *= scale;
      b *= scale;
      strokeColor = Color.argb(0xff, r, g, b);
    } else {
      strokeColor = 0;
    }
    /*if (hsv[0] < 0.001 && hsv[1] < 0.001 && hsv[2] > 0.92f) {
      int c = (int) ((1.0f - (hsv[2] - 0.92f) / 0.08f * 0.22f) * 255);
      strokeColor = Color.argb(0xff, c, c, c);
    } else {
      strokeColor = 0;
    }*/
  }

  public float getBrushRadius (float scale) {
    return (MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * radiusFactor) * scale;
  }

  public int getBrushColor () {
    return color;
  }

  public interface BrushChangeListener {
    void onBrushChanged (ColorPreviewView v);
  }

  private @Nullable BrushChangeListener brushChangeListener;

  public void setBrushChangeListener (@Nullable BrushChangeListener brushChangeListener) {
    this.brushChangeListener = brushChangeListener;
  }

  private void setColorImpl (int color) {
    if (this.color != color) {
      this.color = color;
      notifyBrushChangeListeners();
    }
  }

  private void notifyBrushChangeListeners () {
    if (brushChangeListener != null) {
      brushChangeListener.onBrushChanged(this);
    }
  }

  public void setColor (float hue, float saturation, float value) {
    if (this.hue != hue || this.hsv[1] != saturation || this.hsv[2] != value) {
      this.hue = hue;
      this.hsv[0] = hue == 1f ? 0f : hue * 360f;
      this.hsv[1] = saturation;
      this.hsv[2] = value;
      setColorImpl(Color.HSVToColor(hsv));
      setStrokeColor(color);
      updateTranslationX();
      updateTranslationY();
      if (direction != null) {
        direction.setHsv(hue, hsv);
      }
      invalidate();
      if (listener != null) {
        listener.onColorChanged(this, color);
      }
    }
  }

  public void setHue (float hue) {
    setColor(hue, hsv[1], hsv[2]);
  }

  public interface ColorChangeListener {
    void onColorChanged (ColorPreviewView v, int newColor);
  }

  private ColorChangeListener listener;

  public void setColorChangeListener (ColorChangeListener listener) {
    this.listener = listener;
  }

  public void setColor (int color, float hue) {
    boolean changed = false;
    if (this.color != color) {
      setColorImpl(color);
      Color.colorToHSV(color, hsv);
      setStrokeColor(color);
      if (hue == -1f) {
        hue = hsv[0] / 360f;
      }
      if (this.hue != hue) {
        this.hue = hue;
        updateTranslationX();
      }
      if (toneFactor > 0f) {
        updateTranslationY();
      }
      if (direction != null) {
        direction.setHsv(hue, hsv);
      }
      invalidate();
      changed = true;
    } else if (this.hue != hue && hue >= 0f && hue <= 1f) {
      this.hue = hue;
      updateTranslationX();
      changed = true;
    }
    if (changed && listener != null) {
      listener.onColorChanged(this, color);
    }
  }

  public int getRadius () {
    float radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * radiusFactor;
    radius = radius + (MAX_RADIUS - radius) * forceRadiusFactor;
    return Screen.dp(radius);
  }

  private float[] hsv = new float[3];

  @Override
  protected void onDraw (Canvas c) {
    int radius = getRadius();

    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();

    int cx = viewWidth / 2;
    int cy = viewHeight / 2;

    c.drawCircle(cx, cy, viewWidth / 2 - Screen.dp(2f), Paints.fillingPaint(0xffffffff));
    c.drawCircle(cx, cy, radius, Paints.fillingPaint(color));
    if (strokeColor != 0 && strokeColor != color) {
      c.drawCircle(cx, cy, radius - Screen.dp(.5f), Paints.strokeSeparatorPaint(strokeColor));
    }
  }
}
