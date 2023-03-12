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
 * File created on 07/08/2015 at 17:08
 */
package org.thunderdog.challegram.component.base;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class TogglerView extends View implements FactorAnimator.Target, TooltipOverlayView.LocationProvider {
  private Paint circlePaint;

  private boolean isEnabled;
  private BoolAnimator isDisabled;
  private float factor;

  private FactorAnimator enableAnimator;

  private boolean useNegativeState;

  public TogglerView (Context context) {
    super(context);
  }

  public void init (boolean isEnabled) {
    setRadioEnabled(isEnabled, false);
    initDrawingItems();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRoundRect((int) (origX - origRadius), (int) (origY - origRadius), (int) (origX + origRadius), (int) (origY + origRadius), origRadius);
        }
      });
      setElevation(Math.max(1, Screen.dp(.5f)));
      setTranslationZ(Math.max(1, Screen.dp(.5f)));
      ViewUtils.setBackground(this, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          final int fromBackgroundColor;
          final int toBackgroundColor;
          if (useNegativeState) {
            fromBackgroundColor = Theme.getColor(R.id.theme_color_togglerNegativeBackground);
            toBackgroundColor = Theme.getColor(R.id.theme_color_togglerPositiveBackground);
          } else {
            fromBackgroundColor = Theme.togglerInactiveFillingColor();
            toBackgroundColor = Theme.togglerActiveFillingColor();
          }
          final boolean isDisabled = !useNegativeState && TogglerView.this.isDisabled();
          c.drawRoundRect(fillingRect, fillRadius, fillRadius, Paints.fillingPaint(ColorUtils.fromToArgb(fromBackgroundColor, toBackgroundColor, isDisabled ? 0f : factor)));
        }

        @Override
        public void setAlpha (@IntRange(from = 0, to = 255) int i) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
    } else {
      setLayerType(LAYER_TYPE_SOFTWARE, circlePaint);
    }
    buildLayout();
  }

  public TogglerView setUseNegativeState (boolean useNegativeState) {
    if (this.useNegativeState != useNegativeState) {
      this.useNegativeState = useNegativeState;
      invalidate();
    }
    return this;
  }

  private void initDrawingItems () {
    circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    circlePaint.setStyle(Paint.Style.FILL);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      float radius = Math.max(1, Screen.dpf(.5f));
      circlePaint.setShadowLayer(radius, 0f, radius, 0x7a000000);
    }
  }

  private boolean isDisabled () {
    return this.isDisabled != null && this.isDisabled.getValue();
  }

  private static final int ANIMATOR_DISABLE = 1;

  public void setDisabled (boolean isDisabled, boolean animated) {
    if (this.isDisabled != null || isDisabled) {
      if (this.isDisabled == null) {
        this.isDisabled = new BoolAnimator(ANIMATOR_DISABLE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 168l);
      }
      this.isDisabled.setValue(isDisabled, animated);
    }
  }

  private boolean showLock;
  private Drawable lockDrawable;

  public void setShowLock (boolean showLock) {
    if (this.showLock != showLock) {
      this.showLock = showLock;
      if (lockDrawable == null) {
        lockDrawable = Drawables.get(getResources(), R.drawable.baseline_lock_14);
      }
      invalidate();
    }
  }

  private static final int ANIMATOR_SWITCH = 0;

  public void setRadioEnabled (boolean isEnabled, boolean animated) {
    if (this.isEnabled != isEnabled) {
      this.isEnabled = isEnabled;

      if (animated) {
        if (this.enableAnimator == null) {
          this.enableAnimator = new FactorAnimator(ANIMATOR_SWITCH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
        }
        enableAnimator.animateTo(isEnabled ? 1f : 0f);
      } else {
        if (enableAnimator != null) {
          enableAnimator.forceFactor(isEnabled ? 1f : 0f);
        }
        setFactor(isEnabled ? 1f : 0f);
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_SWITCH:
        setFactor(factor);
        break;
      case ANIMATOR_DISABLE:
        invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public boolean toggle (boolean animated) {
    setRadioEnabled(!isEnabled, animated);
    return isEnabled;
  }

  public float getFactor () {
    return factor;
  }

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      updateOrigX();
      invalidate();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        invalidateOutline();
      }
    }
  }

  /*public boolean isAnimating () {
    return isAnimating;
  }*/

  public boolean isEnabled () {
    return isEnabled;
  }

  private RectF fillingRect;
  private float fillRadius;
  private float minX, maxX, diffX, origX, origY, origRadius;

  private int currentWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    if (currentWidth != width) {
      currentWidth = width;
      buildLayout();
    }
  }

  public void checkRtl (boolean allowLayout) {
    if (Views.setGravity(this, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL) && allowLayout) {
      Views.updateLayoutParams(this);
      if (currentWidth > 0) {
        buildLayout();
        invalidate();
      }
    }
  }

  private void buildLayout () {
    if (fillingRect == null) {
      fillingRect = new RectF();
    }

    int start = Screen.dp(58f);
    int left = Screen.dp(4f);
    int right = left + Screen.dp(34f);

    fillingRect.left = currentWidth - start + left;
    fillingRect.right = currentWidth - start + right;

    int top = Screen.dp(20f);
    int bottom = top + Screen.dp(14f);

    fillingRect.top = top;
    fillingRect.bottom = bottom;

    origRadius = Screen.dpf(10f);
    fillRadius = Screen.dp(7f);
    float offset = Screen.dp(3f);

    minX = fillingRect.left - offset + origRadius;
    maxX = fillingRect.right + offset - origRadius;
    diffX = maxX - minX;
    origY = fillingRect.top - offset + origRadius;
    updateOrigX();
  }

  private void updateOrigX () {
    if (Lang.rtl()) {
      origX = factor == 0f ? maxX : factor == 1f ? minX : maxX - factor * diffX;
    } else {
      origX = factor == 0f ? minX : factor == 1f ? maxX : minX + factor * diffX;
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    final int bound = Screen.dp(2f);
    outRect.set((int) (origX - origRadius - bound), (int) (origY - origRadius - bound), (int) (origX + origRadius + bound), (int) (origY + origRadius + bound));
  }

  @Override
  public void onDraw (Canvas c) {
    final int fromBackgroundColor, fromColor;
    final int toBackgroundColor, toColor;
    int checkColor;
    if (useNegativeState) {
      fromBackgroundColor = Theme.getColor(R.id.theme_color_togglerNegativeBackground); // ThemeRed.getColor(R.id.theme_color_togglerFillingActive);
      fromColor = Theme.getColor(R.id.theme_color_togglerNegative); // ThemeRed.getColor(R.id.theme_color_togglerActive);
      toBackgroundColor = Theme.getColor(R.id.theme_color_togglerPositiveBackground); // ThemeBase.getColor(R.id.theme_color_togglerFillingActive);
      toColor = Theme.getColor(R.id.theme_color_togglerPositive); // ThemeBase.getColor(R.id.theme_color_togglerActive);
      checkColor = ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_togglerNegativeContent), Theme.getColor(R.id.theme_color_togglerPositiveContent), factor);
    } else {
      fromBackgroundColor = Theme.togglerInactiveFillingColor();
      fromColor = Theme.togglerInactiveColor();
      toBackgroundColor = Theme.togglerActiveFillingColor();
      toColor = Theme.togglerActiveColor();
      checkColor = 0;
    }

    final float disableFactor = !useNegativeState && this.isDisabled != null ? this.isDisabled.getFloatValue() : 0f;

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      c.drawRoundRect(fillingRect, fillRadius, fillRadius, Paints.fillingPaint(ColorUtils.fromToArgb(fromBackgroundColor, toBackgroundColor, factor * (1f - disableFactor))));
    }
    circlePaint.setColor(ColorUtils.fromToArgb(fromColor, toColor, factor * (1f - disableFactor)));
    c.drawCircle(origX, origY, origRadius, circlePaint);

    if (showLock) {
      Drawables.draw(c, lockDrawable, origX - lockDrawable.getMinimumWidth() / 2, origY - lockDrawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(checkColor));
    } else if (useNegativeState) {
      Paint strokePaint = Paints.getProgressPaint(checkColor, Screen.dp(2f));

      int lineWidth = (int) (origRadius * .75f);
      int linePart = lineWidth / 2;

      /*c.save();
      c.rotate(-90f * (1f - factor), origX, origY);*/

      int offsetGlobal = (int) (Screen.dp(.5f) * factor);
      int offsetGlobalY = (int) (Screen.dp(.5f) * factor);
      int offsetX1 = (int) (Screen.dp(1.5f) * factor);
      c.drawLine(origX - linePart + offsetX1, origY + linePart + offsetGlobalY, origX + linePart + offsetX1, origY - linePart + offsetGlobalY, strokePaint);
      int offsetX = (int) (Screen.dp(-3.5f) * factor);
      int offsetY = (int) (Screen.dp(3f) * factor);
      int removeLeft = (int) (Screen.dp(.5f) * factor);
      c.drawLine(origX - linePart + offsetX + offsetGlobal + removeLeft, origY - linePart + offsetY + offsetGlobalY + removeLeft, origX + linePart * (1f - factor) + offsetX + offsetGlobal, origY + linePart * (1f - factor) + offsetY + offsetGlobalY, strokePaint);

      // c.restore();
    }
  }
}
