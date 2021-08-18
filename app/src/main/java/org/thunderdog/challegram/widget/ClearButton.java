/**
 * File created on 15/03/16 at 21:15
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;

public class ClearButton extends HeaderButton {
  private static final int ANIMATION_MODE_SCALE = 0;
  private static final int ANIMATION_MODE_CROSS = 1;
  private static final int ANIMATION_MODE_ROTATE = 2;

  private static final int ANIMATION_MODE = ANIMATION_MODE_ROTATE;

  private final int totalWidth = Screen.dp(49f);
  private final int cx = (int) ((float) totalWidth * .5f);
  private final int lineHeight = Screen.dp(12f);
  private final int lineRadius = (int) ((float) lineHeight * .5f);

  private final Paint paint;

  public ClearButton (Context context) {
    super(context);

    paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(0xffffffff);
    paint.setStrokeWidth(Screen.dp(2f));

    setLayoutParams(new LinearLayout.LayoutParams(totalWidth, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private int colorId;

  public void setColorId (@ThemeColorId int color) {
    this.colorId = color;
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return factor == 1f && super.onTouchEvent(event);
  }

  private int totalHeight;
  private int cy;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    totalHeight = getMeasuredHeight() - Screen.dp(1f);
    cy = Screen.dp(1f) + totalHeight / 2;
  }

  private boolean isVisible;

  @SuppressWarnings ("ConstantConditions")
  public void setVisible (boolean visible, final boolean animated) {
    if (isVisible == visible) {
      return;
    }

    isVisible = visible;
    if (!animated) {
      setFactor(visible ? 1f : 0f);
      return;
    }

    ValueAnimator obj;
    final float fromFactor = getFactor();
    obj = AnimatorUtils.simpleValueAnimator();
    if (visible) {
      final float diffFactor = 1f - fromFactor;
      obj.addUpdateListener(animation -> setFactor(fromFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    } else {
      obj.addUpdateListener(animation -> setFactor(fromFactor - fromFactor * AnimatorUtils.getFraction(animation)));
    }
    switch (ANIMATION_MODE) {
      case ANIMATION_MODE_SCALE: {
        if (isVisible) {
          obj.setDuration(100l);
          obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        } else {
          obj.setDuration(300l);
          obj.setInterpolator(AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR);
        }
        break;
      }
      case ANIMATION_MODE_CROSS: {
        obj.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
        obj.setDuration(200l);
        break;
      }
      case ANIMATION_MODE_ROTATE: {
        obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        obj.setDuration(162l);
        break;
      }
    }
    if (lastAnimator != null) {
      lastAnimator.cancel();
    }
    obj.start();
    lastAnimator = obj;
  }

  private ValueAnimator lastAnimator;

  private float factor;

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (factor >= 0f) {
        invalidate();
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  public void setInProgress (boolean inProgress) {
    // TODO
  }

  @SuppressWarnings ("ConstantConditions")
  @Override
  protected void onDraw (Canvas c) {
    if (colorId != 0) {
      paint.setColor(Theme.getColor(colorId));
    }
    if (factor > 0f && totalHeight > 0) {
      switch (ANIMATION_MODE) {
        case ANIMATION_MODE_SCALE: {
          paint.setAlpha((int) (255f * Math.min(1f, factor)));
          int d = (int) ((float) lineHeight * .5f * factor);
          c.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
          c.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
          break;
        }
        case ANIMATION_MODE_CROSS: {
          DrawAlgorithms.drawAnimatedCross(c, cx, cy, factor, 0xffffffff, lineHeight);
          break;
        }
        case ANIMATION_MODE_ROTATE: {
          c.save();
          c.rotate((Lang.rtl() ? -90 : 90f) * (1f - factor), cx, cy);
          int d = (int) ((float) lineHeight * .5f * factor);
          c.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
          c.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
          c.restore();
          break;
        }
      }
    }
  }
}
