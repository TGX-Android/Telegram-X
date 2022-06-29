package org.thunderdog.challegram.widget;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class ThreeStateCheckBoxView extends View implements FactorAnimator.Target {
  public static enum State {
    TRUE, FALSE, INTERIM
  }

  private final RectF rect;
  private final Paint outerPaint;

  private State currentState = State.FALSE;
  private float checkedValue = 0f;

  private final FactorAnimator isChecked = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);
  private final BoolAnimator isHidden = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);
  private final BoolAnimator isDisabled = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l);


  public ThreeStateCheckBoxView (Context context) {
    super(context);

    outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    outerPaint.setColor(Theme.radioOutlineColor());
    outerPaint.setStyle(Paint.Style.STROKE);

    rect = new RectF();
  }

  public void setState (ThreeStateCheckBoxView.State newState) {
    currentState = newState;
    int value;
    switch (newState) {
      case FALSE: {
        value = 0;
        break;
      }
      case INTERIM: {
        value = 1;
        break;
      }
      case TRUE: {
        value = 2;
        break;
      }
      default: {
        throw new IllegalArgumentException();
      }
    }
    isChecked.animateTo(value);
  }

  public void setHidden (boolean hidden, final boolean animated) {
    isHidden.setValue(hidden, animated);
  }

  public void toggle () {
    if (currentState == State.FALSE) {
      setState(State.INTERIM);
    } else if (currentState == State.INTERIM) {
      setState(State.TRUE);
    } else {
      setState(State.FALSE);
    }
  }

  public void setDisabled (boolean disabled, boolean animated) {
    isDisabled.setValue(disabled, animated);
  }

  // Internal

  private static final float FACTOR_DIFF = .65f;
  private static final float SCALE_DIFF = .15f;

  @Override
  protected void onDraw (Canvas c) {
    final float showFactor = 1f - isHidden.getFloatValue();

    if (showFactor == 0f) {
      return;
    }

    final float factor = checkedValue;
    final int alpha = (int) (255f * showFactor);

    final int x1 = Screen.dp(7f);
    final int y1 = Screen.dp(12f);
    final int lineSize = Screen.dp(1.5f);

    float rectFactor = Math.min(factor / FACTOR_DIFF, 1f);
    float checkFactor = factor <= FACTOR_DIFF ? 0f : (factor - FACTOR_DIFF) / (1f - FACTOR_DIFF);
    float scaleFactor = 1f - (rectFactor == 1f ? 1f - checkFactor : rectFactor) * SCALE_DIFF;

    final int radius = Screen.dp(2f);
    final int offset = (int) ((float) radius * .5f);

    outerPaint.setStrokeWidth(radius);

    int size = Math.min(getMeasuredWidth(), getMeasuredHeight());

    rect.left = offset;
    rect.top = offset;
    rect.right = size - offset * 2;
    rect.bottom = size - offset * 2;

    float cx = (rect.left + rect.right) * .5f;
    float cy = (rect.top + rect.bottom) * .5f;

    final int restoreToCount = Views.save(c);
    c.scale(scaleFactor, scaleFactor, cx, cy);

    int color = ColorUtils.fromToArgb(Theme.radioOutlineColor(), Theme.radioFillingColor(), rectFactor * (1f - isDisabled.getFloatValue()));
    outerPaint.setColor(color);
    outerPaint.setAlpha(alpha);
    c.drawRoundRect(rect, radius, radius, outerPaint);

    if (rectFactor != 0f) {
      int w = (int) ((rect.right - rect.left - offset * 2) * .5f * rectFactor);
      int h = (int) ((rect.bottom - rect.top - offset * 2) * .5f * rectFactor);

      int left = (int) (rect.left + offset + w);
      int right = (int) (rect.right - offset - w);

      final int alphaColor = ColorUtils.alphaColor(showFactor, color);

      c.drawRect(rect.left + offset, rect.top + offset, left, rect.bottom - offset, Paints.fillingPaint(alphaColor));
      c.drawRect(right, rect.top + offset, rect.right - offset, rect.bottom - offset, Paints.fillingPaint(alphaColor));
      c.drawRect(left, rect.top + offset, right, rect.top + offset + h, Paints.fillingPaint(alphaColor));
      c.drawRect(left, rect.bottom - offset - h, right, rect.bottom - offset, Paints.fillingPaint(alphaColor));

      if (checkFactor > 1f) {
        float fac = checkFactor - 1;
        c.translate(-Screen.dp(.5f), 0);
        c.rotate(-45f, cx, cy);

        int w2 = (int) ((float) Screen.dp(7f) * fac / 2);
        int h1 = (int) ((float) Screen.dp(4f) * fac / 2);

        final int checkColor = ColorUtils.alphaColor(showFactor, Theme.radioCheckColor());
        c.drawRect(x1, y1 - h1, x1 + lineSize, y1, Paints.fillingPaint(checkColor));
        c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(checkColor));
      } else if (checkFactor > 0f && currentState == State.INTERIM) {
        int y2 = size / 2;
        int x2 = Screen.dp(3f);
        int w2 = (int) (size * checkFactor) / 2;

        final int checkColor = ColorUtils.alphaColor(showFactor, Theme.radioCheckColor());
        c.drawRect(cx - w2 + x2, y2 - lineSize, cx + w2 - x2, y2 + lineSize, Paints.fillingPaint(checkColor));
      }
    }

    Views.restore(c, restoreToCount);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    this.checkedValue = factor;
    invalidate();
  }

  public static ThreeStateCheckBoxView simpleCheckBox (Context context) {
    return simpleCheckBox(context, Lang.rtl());
  }

  public static ThreeStateCheckBoxView simpleCheckBox (Context context, boolean rtl) {
    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(Screen.dp(18f), Screen.dp(18f));
    params.gravity = Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT);
    params.leftMargin = params.rightMargin = Screen.dp(19f);

    ThreeStateCheckBoxView checkBox;
    checkBox = new ThreeStateCheckBoxView(context);
    checkBox.setLayoutParams(params);

    return checkBox;
  }
}
