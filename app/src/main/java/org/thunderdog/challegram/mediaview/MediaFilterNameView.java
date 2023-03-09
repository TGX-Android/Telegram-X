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
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class MediaFilterNameView extends FrameLayoutFix implements FactorAnimator.Target {
  private final TextView name, value;

  private static class AutoFitTextView extends NoScrollTextView {
    public AutoFitTextView (Context context) {
      super(context);
    }

    private float scaleX = 1f, scaleY = 1f, baseScale = 1f;

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      checkScale();
    }

    @Override
    protected void onTextChanged (CharSequence text, int start, int lengthBefore, int lengthAfter) {
      super.onTextChanged(text, start, lengthBefore, lengthAfter);
      checkScale();
    }

    private void checkScale () {
      Layout layout = getLayout();
      float lineWidth = layout != null && layout.getLineCount() > 0 ? layout.getLineWidth(0) : 0;
      float viewWidth = getMeasuredWidth();
      if (viewWidth > 0 && lineWidth > viewWidth) {
        baseScale = Math.min(1f, viewWidth / lineWidth);
        setMeasuredDimension((int) lineWidth, getMeasuredHeight());
      } else {
        baseScale = 1f;
      }
      super.setScaleX(this.scaleX * baseScale);
      super.setScaleY(this.scaleY * baseScale);
      setPivotX(getMeasuredWidth());
      setPivotY(getMeasuredHeight() / 2f);
    }

    @Override
    public void setScaleX (float scaleX) {
      super.setScaleX((this.scaleX = scaleX) * baseScale);
    }

    @Override
    public void setScaleY (float scaleY) {
      super.setScaleY((this.scaleY = scaleY) * baseScale);
    }
  }

  public MediaFilterNameView (Context context) {
    super(context);

    this.name = new AutoFitTextView(context);
    this.name.setTextColor(0xffffffff);
    this.name.setTypeface(Fonts.getRobotoRegular());
    this.name.setGravity(Gravity.RIGHT);
    this.name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    this.name.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
    this.name.setSingleLine(true);
    addView(name);

    this.value = new NoScrollTextView(context);
    this.value.setTextColor(0xff64CEFD);
    this.value.setTypeface(Fonts.getRobotoRegular());
    this.value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    this.value.setAlpha(0f);
    this.value.setGravity(Gravity.CENTER);
    this.value.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
    this.value.setSingleLine(true);
    this.value.setText("0");
    addView(value);
  }

  private @ThemeColorId int nameColorId, valueColorId;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      if (nameColorId != 0) {
        themeProvider.addThemeTextColorListener(name, nameColorId);
      }
      if (valueColorId != 0) {
        themeProvider.addThemeTextColorListener(value, valueColorId);
      }
    }
  }

  public void setColors (@ThemeColorId int nameColorId, @ThemeColorId int valueColorId) {
    name.setTextColor(Theme.getColor(this.nameColorId = nameColorId));
    value.setTextColor(Theme.getColor(this.valueColorId = valueColorId));
  }

  public void setSizes (float size) {
    name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
  }

  private boolean isDragging, isAlwaysDragging;
  private final BoolAnimator animator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public void setAlwaysDragging (boolean isAlwaysDragging) {
    if (this.isAlwaysDragging != isAlwaysDragging) {
      this.isAlwaysDragging = isAlwaysDragging;
      animator.setValue(isAlwaysDragging || isDragging, false);
    }
  }

  public void setIsDragging (boolean isDragging, boolean animated) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      if (animated) {
        animator.setDuration(name.getText().length() == 0 ? 120l : 180l);
      }
      animator.setValue(isDragging || isAlwaysDragging, animated);
    }
  }

  public void setName (CharSequence name) {
    this.name.setText(name);
  }

  public void setValue (String value) {
    this.value.setText(value);
  }

  public void setValueMaxWidth (float maxWidth) {
    this.value.setMinimumWidth(Math.round(maxWidth));
  }

  private static final float MIN_SCALE = .8f;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (name.getText().length() == 0) {
      setStyle(value, factor);
    } else {
      float factor1 = factor <= .5f ? 1f - (factor / .5f) : 0f;
      float factor2 = factor > .5f ? (factor - .5f) / .5f : 0f;
      setStyle(name, factor1);
      setStyle(value, factor2);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private void setStyle (View view, float factor) {
    view.setAlpha(factor);
    final float scale = MIN_SCALE + (1f - MIN_SCALE) * factor;
    view.setScaleX(scale);
    view.setScaleY(scale);
  }
}
