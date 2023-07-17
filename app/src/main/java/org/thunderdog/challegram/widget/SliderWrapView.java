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
 * File created on 18/12/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.mediaview.MediaFilterNameView;
import org.thunderdog.challegram.mediaview.SliderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public class SliderWrapView extends LinearLayout implements SliderView.Listener, Destroyable, AttachDelegate, BaseActivity.LuxChangeListener {
  public interface Callback {
    void onSliderValueChanged (SliderWrapView v, int value);
  }

  public interface RealTimeChangeListener {
    void onNewValue (SliderWrapView wrapView, float value, float valueMax, int valueIndex, boolean isFinished);
  }

  private static class BrightnessIcon extends ImageView implements Destroyable {
    private float value;

    private static final float RADIUS = 4f;

    public BrightnessIcon (Context context) {
      super(context);
      setScaleType(ScaleType.CENTER);
      setImageResource(R.drawable.baseline_brightness_5_24);
      setColorFilter(Theme.iconColor());
    }

    private Bitmap cutOffBitmap;
    private Canvas cutOffCanvas;
    private int lastCutOffColor;

    private void prepareCutOff () {
      if (cutOffBitmap == null) {
        int size = Screen.dp(RADIUS) * 2 + Screen.dp(2f);
        cutOffBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        cutOffCanvas = new Canvas(cutOffBitmap);
      }
    }

    private void drawCutOff () {
      if (cutOffBitmap == null) {
        prepareCutOff();
      }
      cutOffBitmap.eraseColor(0);
      int width = cutOffBitmap.getWidth();
      cutOffCanvas.drawCircle(width / 2, width / 2, Screen.dp(RADIUS), Paints.fillingPaint(lastCutOffColor = Theme.iconColor()));
      cutOffCanvas.drawCircle(width / 2 + (int) (Screen.dp(RADIUS) * 2f * value), width / 2, Screen.dp(RADIUS), Paints.getErasePaint());
    }

    @Override
    public void performDestroy () {
      if (cutOffBitmap != null) {
        cutOffBitmap.recycle();
        cutOffBitmap = null;
      }
      cutOffCanvas = null;
    }

    private static final DecelerateInterpolator interpolator = new DecelerateInterpolator(2f);

    public void setValue (float value) {
      value = value > 0f && value < 1f ? interpolator.getInterpolation(value) : value;
      if (this.value != value) {
        float oldValue = this.value;
        this.value = value;
        float radius = Screen.dp(RADIUS);
        int prevDiameter = (int) (radius * 2 * oldValue);
        int newDiameter = (int) (radius * 2 * value);
        if (prevDiameter != newDiameter) {
          if (newDiameter > 0 && newDiameter < (int) (radius * 2)) {
            drawCutOff();
          }
          invalidate();
        }
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      super.onDraw(c);
      final int cx = getMeasuredWidth() / 2;
      final int cy = getMeasuredHeight() / 2;
      final float radius = Screen.dp(RADIUS);
      final int maxDiameter = (int) (radius * 2);
      final int diameter = (int) (maxDiameter * value);
      if (diameter == maxDiameter) {
        c.drawCircle(cx, cy, radius, Paints.fillingPaint(Theme.iconColor()));
      } else if (diameter > 0) {
        if (cutOffBitmap == null || Theme.iconColor() != lastCutOffColor) {
          drawCutOff();
        }
        int size = cutOffBitmap.getWidth();
        c.drawBitmap(cutOffBitmap, cx - size / 2, cy - size / 2, Paints.getBitmapPaint());
      }
    }
  }

  @Nullable
  private MediaFilterNameView nameView;

  @Nullable
  private BrightnessIcon currentBrightness, fullBrightness;

  private SliderView sliderView;

  public SliderWrapView (Context context) {
    super(context);

    sliderView = new SliderView(context);
    sliderView.setAnchorMode(SliderView.ANCHOR_MODE_START);
    sliderView.setSlideEnabled(true, false);
    sliderView.setListener(this);
    sliderView.setColorId(ColorId.sliderActive, false);
    sliderView.setForceBackgroundColorId(ColorId.sliderInactive);

    setOrientation(HORIZONTAL);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f)));
  }

  public void setColors (@ColorId int sliderColorId, @ColorId int inactiveSliderColorId, @ColorId int textColorId, @ColorId int valueColorId) {
    sliderView.setColorId(sliderColorId, false);
    sliderView.setForceBackgroundColorId(inactiveSliderColorId);
    if (nameView != null) {
      nameView.setColors(textColorId, valueColorId);
    }
  }

  public void initWithName () {
    nameView = new MediaFilterNameView(getContext());
    nameView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    nameView.setPadding(Screen.dp(16f), 0, 0, 0);
    nameView.setColors(ColorId.text, ColorId.textNeutral);
    addView(nameView);

    sliderView.setPadding(Screen.dp(16f), Screen.dp(1f), Screen.dp(16f), 0);
    sliderView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(sliderView);
  }

  public void initWithBrightnessIcons () {
    currentBrightness = new BrightnessIcon(getContext());
    currentBrightness.setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT));
    addView(currentBrightness);

    sliderView.setPadding(Screen.dp(16f), 0, Screen.dp(16), 0);
    sliderView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    addView(sliderView);

    fullBrightness = new BrightnessIcon(getContext());
    fullBrightness.setValue(1f);
    fullBrightness.setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT));
    addView(fullBrightness);
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      if (nameView != null) {
        nameView.addThemeListeners(themeProvider);
      }
      if (currentBrightness != null) {
        themeProvider.addThemeFilterListener(currentBrightness, ColorId.icon);
      }
      if (fullBrightness != null) {
        themeProvider.addThemeFilterListener(fullBrightness, ColorId.icon);
      }
      themeProvider.addThemeInvalidateListener(sliderView);
    }
  }

  private float currentValue;

  @Nullable
  private String[] values;
  private int valueIndex;

  private float valueMax;

  public void setValue (float value, float maxValue) {
    this.valueMax = maxValue;
    this.currentValue = MathUtils.clamp(value / maxValue);
    if (currentBrightness != null) {
      currentBrightness.setValue(currentValue);
    }
    this.sliderView.setValue(currentValue);
  }

  public void setValues (CharSequence name, @NonNull String[] values, int valueIndex) {
    this.values = values;
    if (nameView != null) {
      this.nameView.setName(name);
      this.nameView.setValue(values[valueIndex]);
      float maxWidth = 0f;
      for (String value : values) {
        maxWidth = Math.max(maxWidth, U.measureText(value, Paints.getRegularTextPaint(13f)));
      }
      this.nameView.setValueMaxWidth(maxWidth);
    }
    this.currentValue = valueForIndex(valueIndex);
    if (currentBrightness != null) {
      this.currentBrightness.setValue(currentValue);
    }
    this.valueIndex = valueIndex;
    this.sliderView.setValue(currentValue);
    this.sliderView.setValueCount(values.length);
  }

  private Callback callback;

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  private float valueForIndex (int index) {
    return values != null ? (float) index * (1f / (float) (values.length - 1)) : 0;
  }

  private int indexForValue (float value) {
    return values != null ? Math.round(value * (float) (values.length - 1)) : 0;
  }

  public void setShowOnlyValue (boolean showOnlyValue) {
    if (nameView != null) {
      nameView.setAlwaysDragging(showOnlyValue);
    }
  }

  private void setCurrentValue (float value, boolean isFinished) {
    if (this.currentValue != value || isFinished) {
      this.currentValue = value;
      if (currentBrightness != null) {
        currentBrightness.setValue(value);
      }
      if (changeListener != null) {
        changeListener.onNewValue(this, currentValue, values != null ? 1f : valueMax, valueIndex, isFinished);
      }
    }
  }

  @Override
  public void onSetStateChanged (SliderView view, boolean isSetting) {
    if (nameView != null) {
      nameView.setIsDragging(isSetting, true);
    }
    if (!isSetting) {
      float value = currentValue;
      if (values != null) {
        value = valueForIndex(valueIndex);
        sliderView.animateValue(value);
      }
      setCurrentValue(value, true);
    }
  }

  @Override
  public void onValueChanged (SliderView view, float factor) {
    int newIndex = indexForValue(factor);
    if (valueIndex != newIndex) {
      valueIndex = newIndex;
      if (nameView != null) {
        nameView.setValue(values != null ? values[newIndex] : Integer.toString(newIndex));
      }
      if (callback != null) {
        callback.onSliderValueChanged(this, valueIndex);
      }
    }
    setCurrentValue(factor, false);
  }

  @Override
  public void onLuxChanged (float newLux) {
    if (currentBrightness != null) {
      sliderView.setSmallValue(valueMax != 0 ? MathUtils.clamp(newLux / valueMax) : 0, true);
    }
  }

  @Override
  public void attach () {
    if (currentBrightness != null) {
      BaseActivity activity = UI.getContext(getContext());
      sliderView.setSmallValue(valueMax != 0 ? MathUtils.clamp(activity.getLastLuxValue() / valueMax) : 0, false);
      activity.addLuxListener(this);
    }
  }

  @Override
  public void detach () {
    if (currentBrightness != null) {
      UI.getContext(getContext()).removeLuxListener(this);
    }
  }

  @Override
  public void performDestroy () {
    if (currentBrightness != null) {
      currentBrightness.performDestroy();
      UI.getContext(getContext()).removeLuxListener(this);
    }
  }

  private @Nullable RealTimeChangeListener changeListener;

  public void setRealTimeChangeListener (@Nullable RealTimeChangeListener listener) {
    this.changeListener = listener;
  }

  @Override
  public boolean allowSliderChanges (SliderView view) {
    return values != null || valueMax > 0f;
  }
}
