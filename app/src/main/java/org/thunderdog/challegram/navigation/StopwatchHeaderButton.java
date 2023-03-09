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
 * File created on 10/11/2016
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class StopwatchHeaderButton extends HeaderButton implements FactorAnimator.Target {
  private @Nullable Letters value;
  private int valueWidth;

  // private final TextPaint paint;
  private final Paint circlePaint;

  public static final float WIDTH = 39f;

  public StopwatchHeaderButton (Context context) {
    super(context);

    this.circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    this.circlePaint.setStrokeWidth(Screen.dp(2f));
    this.circlePaint.setStyle(Paint.Style.STROKE);
    this.circlePaint.setColor(0xffffffff);

    setId(R.id.menu_btn_stopwatch);
    setButtonBackground(ThemeDeprecated.headerSelector());
    setPadding(0, Screen.dp(2f), 0, 0);
    setLayoutParams(new ViewGroup.LayoutParams(Screen.dp(WIDTH), ViewGroup.LayoutParams.MATCH_PARENT));

    setEnabled(false);
  }

  private TextPaint getPaint (boolean needFakeBold) {
    return Paints.getMediumTextPaint(16f, needFakeBold);
  }

  public void forceValue (String value, boolean isVisible) {
    if (replaceAnimator != null) {
      replaceAnimator.forceFactor(0f);
    }
    this.replaceFactor = 0f;
    this.nextValue = null;

    if (visibilityAnimator != null) {
      visibilityAnimator.forceFactor(isVisible ? 1f : 0f);
    }
    this.isVisible = isVisible;
    this.visibilityFactor = isVisible ? 1f : 0f;
    setEnabled(isVisible);

    if (stopwatchAnimator != null) {
      stopwatchAnimator.forceFactor(value != null ? 1f : 0f);
    }
    this.stopwatchFactor = value != null ? 1f : 0f;
    this.value = value != null ? new Letters(value) : null;
    this.valueWidth = value != null ? (int) U.measureText(value, getPaint(this.value.needFakeBold)) : 0;
  }

  public boolean getIsVisible () {
    return isVisible;
  }

  private boolean isVisible;

  public void setIsVisible (boolean isVisible) {
    if (this.isVisible != isVisible) {
      this.isVisible = isVisible;
      animateVisibilityFactor(isVisible ? 1f : 0f);
    }
  }

  public void setValue (@Nullable String value) {
    setValue(value, true);
  }

  public void setValue (@Nullable String value, boolean allowClockAnimation) {
    if (this.value == null && value == null) {
      return;
    }
    if (this.value == null || value == null) {
      if (value != null) {
        this.value = new Letters(value);
        this.valueWidth = (int) U.measureText(value, getPaint(this.value.needFakeBold));
        animateStopwatchFactor(1f, allowClockAnimation);
      } else {
        animateStopwatchFactor(0f, false);
      }
    } else if (!this.value.text.equals(value)) {
      Letters newValue = new Letters(value);
      animateReplace(newValue, (int) U.measureText(value, getPaint(newValue.needFakeBold)));
    }
  }

  // Visibility animator

  private FactorAnimator visibilityAnimator;
  private float visibilityFactor;
  private static final long VISIBILITY_DURATION = 400;

  private void animateVisibilityFactor (float toFactor) {
    if (visibilityAnimator == null) {
      visibilityAnimator = new FactorAnimator(VISIBILITY_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, VISIBILITY_DURATION, visibilityFactor);
    }
    visibilityAnimator.animateTo(toFactor);
  }

  // Replace animator

  private FactorAnimator replaceAnimator;
  private float replaceFactor;
  private Letters nextValue;
  private int nextValueWidth;

  private void animateReplace (Letters nextValue, int nextValueWidth) {
    if (replaceAnimator == null) {
      replaceAnimator = new FactorAnimator(REPLACE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, STOPWATCH_TRANSFORM_DURATION);
    } else {
      replaceAnimator.cancel();
    }
    if (replaceAnimator.getFactor() == 1f) {
      replaceAnimator.forceFactor(this.replaceFactor = 0f);
    }
    this.nextValue = nextValue;
    this.nextValueWidth = nextValueWidth;
    replaceAnimator.animateTo(1f);
  }

  // Stopwatch animator

  private FactorAnimator stopwatchAnimator;
  private float stopwatchFactor;
  private static final long STOPWATCH_TRANSFORM_DELAY = -170;
  private static final long STOPWATCH_DURATION = 1100L + STOPWATCH_TRANSFORM_DELAY;
  private static final long STOPWATCH_TICK_DURATION = 800l;
  private static final long STOPWATCH_PRESSURE_DURATION = 200l;
  private static final long STOPWATCH_TICK_START = STOPWATCH_PRESSURE_DURATION / 2;
  private static final long STOPWATCH_TRANSFORM_START = STOPWATCH_TICK_START + STOPWATCH_TICK_DURATION + STOPWATCH_TRANSFORM_DELAY;
  private static final long STOPWATCH_TRANSFORM_DURATION = 200l;

  private boolean useClockAnimation;

  private void animateStopwatchFactor (float toFactor, boolean allowClockAnimation) {
    if (stopwatchAnimator == null) {
      stopwatchAnimator = new FactorAnimator(STOPWATCH_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, STOPWATCH_DURATION, stopwatchFactor);
    }

    this.useClockAnimation = toFactor == 1f && allowClockAnimation;
    stopwatchAnimator.setDuration(useClockAnimation ? STOPWATCH_DURATION : STOPWATCH_TRANSFORM_DURATION);
    if (useClockAnimation) {
      stopwatchAnimator.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
    } else {
      stopwatchAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    }
    stopwatchAnimator.animateTo(toFactor);
  }

  // Animation

  private static final int STOPWATCH_ANIMATOR = 0;
  private static final int VISIBILITY_ANIMATOR = 1;
  private static final int REPLACE_ANIMATOR = 2;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case STOPWATCH_ANIMATOR: {
        this.stopwatchFactor = factor;
        break;
      }
      case VISIBILITY_ANIMATOR: {
        this.visibilityFactor = isVisible ? AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR.getInterpolation(factor) : 1f - AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR.getInterpolation(1f - factor);
        setEnabled(visibilityFactor == 1f);
        break;
      }
      case REPLACE_ANIMATOR: {
        this.replaceFactor = factor;
        if (this.nextValue != null && factor >= .5f) {
          this.value = nextValue;
          this.valueWidth = nextValueWidth;
          this.nextValue = null;
        }
        break;
      }
    }
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case STOPWATCH_ANIMATOR: {
        if (finalFactor == 0f) {
          value = null;
        }
        break;
      }
    }
  }

  private static final float VISIBILITY_MIN_FACTOR = .6f;
  private static final float TRANSFORM_MIN_FACTOR = .4f;

  @Override
  protected void onDraw (Canvas c) {
    if (visibilityFactor <= 0f) {
      return;
    }

    final int cx = getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2;
    final int cy = getPaddingTop() + (getMeasuredHeight() - getPaddingBottom() - getPaddingTop()) / 2;

    if (visibilityFactor != 1f) {
      final float scale = VISIBILITY_MIN_FACTOR + (1f - VISIBILITY_MIN_FACTOR) * visibilityFactor;
      c.save();
      c.scale(scale, scale, cx, cy);
    }

    if (stopwatchFactor == 1f) {
      final float transformFactor = replaceFactor < .5f ? 1f - (replaceFactor / .5f) : (replaceFactor - .5f) / .5f;

      if (transformFactor != 1f) {
        c.save();
        final float scale = TRANSFORM_MIN_FACTOR + (1f - TRANSFORM_MIN_FACTOR) * transformFactor;
        c.scale(scale, scale, cx, cy);
      }

      if (value != null) {
        final int textColor = ((int) (255f * transformFactor) << 24) | getColor(0xffffffff);
        TextPaint paint = getPaint(value.needFakeBold);
        paint.setColor(textColor);
        float textScale = Math.min(1f, (float) getMeasuredWidth() / (float) valueWidth);
        if (textScale != 1f) {
          c.save();
          c.scale(textScale, textScale, cx, cy);
        }
        c.drawText(value.text, cx - valueWidth / 2, cy + Screen.dp(5f), paint);
        if (textScale != 1f) {
          c.restore();
        }
      }

      if (transformFactor != 1f) {
        c.restore();
      }
    } else {
      // Stopwatch

      final float stopwatchPressureFactor;
      final float stopwatchTickFactor;
      final float transformFactor;

      if (useClockAnimation) {
        final float stopwatchAnimationTime = (float) STOPWATCH_DURATION * stopwatchFactor;
        stopwatchPressureFactor = stopwatchAnimationTime <= STOPWATCH_PRESSURE_DURATION ? AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(stopwatchAnimationTime / (float) STOPWATCH_PRESSURE_DURATION) : 1f;
        stopwatchTickFactor = stopwatchAnimationTime >= STOPWATCH_TICK_START + STOPWATCH_TICK_DURATION ? 1f : stopwatchAnimationTime > STOPWATCH_TICK_START ? AnimatorUtils.NAVIGATION_INTERPOLATOR.getInterpolation((stopwatchAnimationTime - STOPWATCH_TICK_START) / (float) STOPWATCH_TICK_DURATION) : 0f;
        transformFactor = stopwatchAnimationTime >= STOPWATCH_TRANSFORM_START + STOPWATCH_TRANSFORM_DURATION ? 1f : stopwatchAnimationTime > STOPWATCH_TRANSFORM_START ? AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation((stopwatchAnimationTime - STOPWATCH_TRANSFORM_START) / (float) (STOPWATCH_TRANSFORM_DURATION)) : 0f;
      } else {
        stopwatchTickFactor = 0f;
        stopwatchPressureFactor = 0f;
        transformFactor = stopwatchFactor;
      }

      int sourceColor = getColor(0xffffffff);
      final int color = (Math.max(0, Math.min((int) (255f * visibilityFactor * (1f - Math.min(transformFactor, .5f) / .5f)), 255)) << 24) | sourceColor;
      final Paint fillingPaint = Paints.fillingPaint(color);

      if (transformFactor != 0f) {
        c.save();
        float scale = TRANSFORM_MIN_FACTOR + (1f - TRANSFORM_MIN_FACTOR) * (1f - transformFactor);
        c.scale(scale, scale, cx, cy);
      }

      int width = Screen.dp(2f);
      int radius = Screen.dp(8f);

      int left = cx - width / 2;
      int right = cx + width / 2;

      int pressWidth = Screen.dp(6f);
      int pressOffset = (int) ((float) width * (stopwatchPressureFactor < .5f ? stopwatchPressureFactor / .5f : 1f - (stopwatchPressureFactor - .5f) / .5f));
      c.drawRect(cx - pressWidth / 2, cy - radius - width - width + pressOffset, cx + pressWidth / 2, cy - radius - width + pressOffset, fillingPaint);

      if (stopwatchTickFactor != 0f && stopwatchTickFactor != 1f) {
        c.save();
        c.rotate(360f * stopwatchTickFactor, cx, cy);
      }
      c.drawRect(left, cy - width / 2 - Screen.dp(4f), right, cy + width / 2, fillingPaint);
      if (stopwatchTickFactor != 0f && stopwatchTickFactor != 1f) {
        c.restore();
      }

      circlePaint.setColor(color);
      c.drawCircle(cx, cy, radius, circlePaint);

      c.save();
      c.rotate(45f, cx, cy);
      c.drawRect(left, cy - radius - width - width / 2, right, cy - radius, fillingPaint);
      c.restore();

      if (transformFactor != 0f) {
        c.restore();

        if (value != null) {
          final int textColor = ((int) (255f * (transformFactor >= .5f ? (transformFactor - .5f) / .5f : 0f)) << 24) | sourceColor;
          TextPaint paint = getPaint(value.needFakeBold);
          paint.setColor(textColor);

          c.save();
          float textScale = Math.min(1f, (float) getMeasuredWidth() / (float) valueWidth);
          float scale = (TRANSFORM_MIN_FACTOR + (1f - TRANSFORM_MIN_FACTOR) * transformFactor) * textScale;
          c.scale(scale, scale, cx, cy);
          c.drawText(value.text, cx - valueWidth / 2, cy + Screen.dp(5f), paint);
          c.restore();
        }
      }
    }

    if (visibilityFactor != 1f) {
      c.restore();
    }
  }
}
