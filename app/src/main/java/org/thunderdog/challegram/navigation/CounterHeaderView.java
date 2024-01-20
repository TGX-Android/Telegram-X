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
 * File created on 26/08/2015 at 15:17
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.text.Text;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.StringUtils;

public class CounterHeaderView extends View {
  private static final long SWITCH_DURATION = 180;

  protected int fullWidth;
  private int spaceSize;

  private int textLeft, textTop;

  private ArrayList<Integer> counters;
  private final DigitView[] numbers = new DigitView[5];
  private int drawingSize;
  private boolean drawingSizeChanged;

  // private Paint paint;
  private int diff;

  private final float[] widths = new float[10];
  private float maxWidth;

  public CounterHeaderView (Context context) {
    super(context);
    this.counters = new ArrayList<>();
  }

  public void initDefault (@ColorId int colorId) {
    initDefault(19f, colorId);
  }

  private @ColorId int colorId;
  private float textSizeDp;

  public void setTextColorId (@ColorId int colorId) {
    if (this.colorId != colorId) {
      this.colorId = colorId;
      invalidate();
    }
  }

  public void initDefault (float textSizeDp, @ColorId int colorId) {
    init(textSizeDp, colorId, Screen.dp(20f), 0, Screen.dp(20f) + Screen.dp(15f));
  }

  public void setTextTop (int top) {
    this.textTop = top;
  }

  public void init (float textSizeDp, @ColorId int colorId, int diff, int textLeft, int textTop) {
    this.textSizeDp = textSizeDp;
    this.colorId = colorId;
    this.diff = diff;
    this.textLeft = textLeft;
    this.textTop = textTop;
    initImpl();
  }

  private void initImpl () {
    updateWidths();
    for (int i = 0; i < numbers.length; i++)
      numbers[i] = new DigitView(widths, diff);
    fullWidth = (int) Math.ceil(maxWidth * (float) numbers.length);
    spaceSize = (int) U.measureText(" ", Paints.getMediumTextPaint(textSizeDp, false));
  }

  private void updateWidths () {
    float max = 0f;
    for (int i = 0; i < 10; i++) {
      widths[i] = U.measureText(valueOf(i), getPaint(false, false));
      max = Math.max(max, widths[i]);
    }
    maxWidth = max;
  }

  private static String valueOf (int i) {
    return String.valueOf(i); // TODO arabic counter?
  }

  private int getTextWidth () {
    return Math.max(suffix != null ? suffix.width : 0, futureSuffix != null ? futureSuffix.width : 0);
  }

  public int getMaxDigitWidth () {
    return (int) maxWidth;
  }

  private int textWidth;

  private void checkTextWidth () {
    int textWidth = getTextWidth();
    if (this.textWidth != textWidth) {
      this.textWidth = textWidth;
      if (isWrapContent()) {
        requestLayout();
      }
    }
  }

  protected boolean alignRight () {
    if (isWrapContent()) {
      ViewGroup.LayoutParams params = getLayoutParams();
      return params instanceof FrameLayout.LayoutParams && (((FrameLayout.LayoutParams) params).gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;
    }
    return false;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (isWrapContent()) {
      setMeasuredDimension(MeasureSpec.makeMeasureSpec(textLeft + fullWidth + getTextWidth(), MeasureSpec.EXACTLY), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  private boolean isWrapContent () {
    return getLayoutParams() == null || getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT;
  }

  private static class Suffix {
    public final String text;
    public final int width;
    public final int direction;
    public final boolean fakeBold;

    public Suffix (CounterHeaderView context, String text) {
      this.text = text;
      this.fakeBold = Text.needFakeBold(text);
      this.direction = Strings.getTextDirection(text);
      this.width = (int) U.measureText(text, context.getPaint(fakeBold, false));
    }

    public int length () {
      return text.length();
    }
  }

  private Suffix suffix, futureSuffix;
  private int suffixAnimationType;
  private int crossfadePrefixSize;
  private float crossfadePrefixWidth;

  private static final int SUFFIX_ANIMATE_CROSSFADE = 3;
  private static final int SUFFIX_ANIMATE_IN = 1;
  private static final int SUFFIX_ANIMATE_OUT = 2;

  private TextPaint getPaint (boolean fakeBold, boolean willDraw) {
    if (willDraw) {
      return Paints.getMediumTextPaint(textSizeDp, Theme.getColor(colorId), fakeBold);
    } else {
      return Paints.getMediumTextPaint(textSizeDp, fakeBold);
    }
  }

  public void setSuffix (@NonNull String suffix, boolean animated) {
    suffix = suffix.trim();
    if (this.suffix == null || !animated) {
      this.suffix = new Suffix(this, suffix);
      this.futureSuffix = null;
      checkTextWidth();
      invalidate();
      return;
    }
    if (!StringUtils.equalsOrBothEmpty(this.suffix.text, suffix)) {
      this.futureSuffix = new Suffix(this, suffix);
      if (suffix.startsWith(this.suffix.text)) {
        suffixAnimationType = SUFFIX_ANIMATE_IN;
      } else if (this.suffix.text.startsWith(suffix)) {
        suffixAnimationType = SUFFIX_ANIMATE_OUT;
      } else {
        crossfadePrefixSize = 0;
        suffixAnimationType = SUFFIX_ANIMATE_CROSSFADE;
        int maxLen = Math.min(this.suffix.length(), this.futureSuffix.length());
        for (int i = 0; i < maxLen; i++) {
          if (this.suffix.text.charAt(i) == this.futureSuffix.text.charAt(i)) {
            crossfadePrefixSize++;
          } else {
            break;
          }
        }
        if (crossfadePrefixSize > 0) {
          crossfadePrefixWidth = U.measureText(this.suffix.text, 0, crossfadePrefixSize, getPaint(this.suffix.fakeBold, false));
        } else {
          crossfadePrefixWidth = 0f;
        }
      }
      checkTextWidth();
      invalidate();
    } else if (this.futureSuffix != null) {
      this.futureSuffix = null;
      checkTextWidth();
      invalidate();
    }
  }

  private void applyFutureSuffix () {
    if (futureSuffix != null) {
      this.suffix = futureSuffix;
      this.futureSuffix = null;
      checkTextWidth();
      invalidate();
    }
  }

  private int counter;

  public void initCounter (int counter, boolean animated) {
    if (this.counter != counter && counter >= 0 && counter < 99999) {
      if (this.counter != 0) {
        for (int i = 0; i < drawingSize; i++) {
          numbers[i].reset();
        }
      }
      this.counter = counter;
      if (animated) {
        animateCounter(true);
      } else {
        buildCounter();
        applyCounter();
      }
    }
  }

  public float getMultipleFactor () {
    if (isAnimating) {
      if (animDirection) {
        return (prevCounter == 1 && counter == 2 ? factor : counter >= 2 ? 1f : 0f);
      } else {
        return (prevCounter == 2 && counter == 1 ? 1f - factor : counter >= 2 ? 1f : 0f);
      }
    } else {
      return counter >= 2 ? 1f : 0f;
    }
  }

  private int prevCounter;

  public boolean setCounter (int counter) {
    if (this.counter != counter && counter >= 0 && counter < 99999) {
      boolean forward = this.counter < counter;
      this.prevCounter = this.counter;
      this.counter = counter;
      animateCounter(forward);
      return true;
    }
    return false;
  }

  public int getCounter () {
    return counter;
  }

  private void setDrawingSize (int size) {
    if (this.drawingSize != size) {
      this.drawingSize = size;
      /*if (isWrapContent()) {
        requestLayout();
      }*/
    }
  }

  private void buildCounter () {
    int prevSize = counters.size();
    counters.clear();
    int c = counter;
    do {
      counters.add(0, c % 10);
    } while ((c /= 10) != 0);
    int size = counters.size();
    setDrawingSize(Math.max(prevSize, size));
    drawingSizeChanged = prevSize != size;
    for (int i = 0; i < drawingSize; i++) {
      int x = i >= size ? -1 : counters.get(i);
      numbers[i].setDigit(x == 0 && size == 1 ? -1 : x);
    }
  }

  private void applyCounter () {
    for (int i = 0; i < drawingSize; i++) {
      numbers[i].applyDigit();
    }
    setDrawingSize(counters.size());
    drawingSizeChanged = false;
    invalidate();
  }

  private boolean isAnimating, animDirection;
  private ValueAnimator animator;

  private void animateCounter (boolean forward) {
    this.animDirection = forward;
    if (isAnimating && animator != null) {
      isAnimating = false;
      animator.cancel();
    }
    buildCounter();
    isAnimating = true;
    animDirection = forward;
    final float startFactor = getFactor();
    final float diffFactor = 1f - startFactor;
    animator = AnimatorUtils.simpleValueAnimator();
    animator.addUpdateListener(animation -> setFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    animator.setDuration(SWITCH_DURATION);
    animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        if (isAnimating) {
          applyFutureSuffix();
          factor = 0f;
          isAnimating = false;
        }
        applyCounter();
      }
    });
    animator.start();
  }

  private float factor;

  public interface FactorChangeListener {
    void onMultipleFactorChanged (CounterHeaderView v);
  }

  private FactorChangeListener listener;

  public void setFactorChangeListener (FactorChangeListener listener) {
    this.listener = listener;
  }

  public void setFactor (float factor) {
    if (this.factor != factor && isAnimating) {
      this.factor = factor;
      if (listener != null && (this.counter == 2 || this.prevCounter == 2)) {
        listener.onMultipleFactorChanged(this);
      }
      invalidate();
    }
  }

  public float getFactor () {
    return factor;
  }

  private float calculateCounterWidth () {
    float cx = 0;
    if (isAnimating) {
      for (int i = 0; i < drawingSize; i++) {
        DigitView v = numbers[i];
        cx += v.getWidth();
      }
      if (drawingSizeChanged) {
        cx -= Math.round((animDirection ? 1f - factor : factor) * numbers[drawingSize].getWidth());
      }
    } else {
      for (int i = 0; i < drawingSize; i++) {
        DigitView v = numbers[i];
        cx += v.getWidth();
      }
    }
    return cx;
  }

  private float calculateSuffixWidth () {
    float cx = 0f;
    if (suffix != null) {
      cx += spaceSize;
      if (futureSuffix == null) {
        cx += suffix.width;
      } else {
        cx += suffix.width + (float) (futureSuffix.width - suffix.width) * factor;
      }
    }
    return cx;
  }

  private float calculateDrawingSize () {
    float cx = textLeft + getPaddingLeft();
    cx += calculateCounterWidth();
    cx += calculateSuffixWidth();
    return cx;
  }

  private float drawCounter (Canvas c, float cx) {
    if (isAnimating) {
      for (int i = 0; i < drawingSize; i++) {
        DigitView v = numbers[i];
        v.draw(this, c, Theme.getColor(colorId), cx, textTop, factor, animDirection);
        cx += v.getWidth();
      }
      if (drawingSizeChanged) {
        cx -= Math.round((animDirection ? 1f - factor : factor) * numbers[drawingSize].getWidth());
      }
    } else {
      for (int i = 0; i < drawingSize; i++) {
        DigitView v = numbers[i];
        v.draw(this, c, Theme.getColor(colorId), cx, textTop, 0f, false);
        cx += v.getWidth();
      }
    }
    return cx;
  }

  private float drawSuffix (Canvas c, float cx, boolean rtl) {
    if (suffix == null)
      return cx;
    if (!rtl)
      cx += spaceSize;
    TextPaint suffixPaint = getPaint(suffix.fakeBold, true);
    if (futureSuffix == null) {
      c.drawText(suffix.text, cx, textTop, suffixPaint);
      cx += suffix.width;
    } else {
      TextPaint futureSuffixPaint = getPaint(futureSuffix.fakeBold, true);
      if (rtl) {
        cx += suffix.width + (float) (futureSuffix.width - suffix.width) * factor;
        switch (suffixAnimationType) {
          case SUFFIX_ANIMATE_IN: {
            c.drawText(suffix.text, cx - suffix.width, textTop, suffixPaint);
            futureSuffixPaint.setAlpha((int) (255f * factor));
            c.drawText(futureSuffix.text, suffix.length(), futureSuffix.length(), cx - futureSuffix.width, textTop, futureSuffixPaint);
            break;
          }
          case SUFFIX_ANIMATE_OUT: {
            c.drawText(futureSuffix.text, cx - futureSuffix.width, textTop, futureSuffixPaint);
            suffixPaint.setAlpha((int) (255f * (1f - factor)));
            c.drawText(suffix.text, futureSuffix.length(), suffix.length(), cx - suffix.width, textTop, suffixPaint);
            break;
          }

          case SUFFIX_ANIMATE_CROSSFADE: {
            if (crossfadePrefixSize > 0) {
              suffixPaint.setAlpha(255);
              c.drawText(suffix.text, 0, crossfadePrefixSize, cx - crossfadePrefixWidth, textTop, suffixPaint);
            }
            suffixPaint.setAlpha((int) (255f * (1f - factor)));
            c.drawText(suffix.text, crossfadePrefixSize, suffix.text.length(), cx - suffix.width, textTop, suffixPaint);
            futureSuffixPaint.setAlpha((int) (255f * factor));
            c.drawText(futureSuffix.text, crossfadePrefixSize, futureSuffix.text.length(), cx - futureSuffix.width, textTop, futureSuffixPaint);
            break;
          }
        }
      } else {
        switch (suffixAnimationType) {
          case SUFFIX_ANIMATE_IN: {
            c.drawText(suffix.text, cx, textTop, suffixPaint);
            futureSuffixPaint.setAlpha((int) (255f * factor));
            c.drawText(futureSuffix.text, suffix.length(), futureSuffix.length(), cx + suffix.width, textTop, futureSuffixPaint);
            break;
          }
          case SUFFIX_ANIMATE_OUT: {
            c.drawText(futureSuffix.text, cx, textTop, futureSuffixPaint);
            suffixPaint.setAlpha((int) (255f * (1f - factor)));
            c.drawText(suffix.text, futureSuffix.length(), suffix.length(), cx + futureSuffix.width, textTop, suffixPaint);
            break;
          }
          case SUFFIX_ANIMATE_CROSSFADE: {
            if (crossfadePrefixSize > 0) {
              suffixPaint.setAlpha(255);
              c.drawText(suffix.text, 0, crossfadePrefixSize, cx, textTop, suffixPaint);
            }
            suffixPaint.setAlpha((int) (255f * (1f - factor)));
            c.drawText(suffix.text, crossfadePrefixSize, suffix.text.length(), cx + crossfadePrefixWidth, textTop, suffixPaint);
            futureSuffixPaint.setAlpha((int) (255f * factor));
            c.drawText(futureSuffix.text, crossfadePrefixSize, futureSuffix.text.length(), cx + crossfadePrefixWidth, textTop, futureSuffixPaint);
            break;
          }
        }
        cx += suffix.width + (float) (futureSuffix.width - suffix.width) * factor;
      }
    }
    if (rtl)
      cx += spaceSize;
    return cx;
  }

  @Override
  protected void onDraw (Canvas c) {
    float translateX = 0;
    if (alignRight()) {
      translateX = getMeasuredWidth() - calculateDrawingSize() - getPaddingRight();
      if (translateX != 0) {
        c.save();
        c.translate(translateX, 0);
      }
    }
    boolean needSpecial = needSpecial();
    if (needSpecial) {
      int width = 0;
      if (isAnimating) {
        for (int i = 0; i < drawingSize; i++) {
          DigitView v = numbers[i];
          width += v.getWidth();
        }
        if (drawingSizeChanged) {
          width -= Math.round((animDirection ? 1f - factor : factor) * numbers[drawingSize].getWidth());
        }
      } else {
        for (int i = 0; i < drawingSize; i++) {
          DigitView v = numbers[i];
          width += v.getWidth();
        }
      }
      drawSpecial(c, width, (int) (width - numbers[0].getWidth()));
    }
    float cx = textLeft + getPaddingLeft();
    if (suffix == null) {
      drawCounter(c, cx);
    } else if (suffix.direction == Strings.DIRECTION_RTL) {
      cx = drawSuffix(c, cx, true);
      drawCounter(c, cx);
    } else {
      cx = drawCounter(c, cx);
      drawSuffix(c, cx, false);
    }

    if (needSpecial) {
      restoreSpecial(c);
    }
    if (translateX != 0f) {
      c.restore();
    }
  }

  protected boolean needSpecial () {
    return false;
  }

  protected void drawSpecial (Canvas c, int additionalWidth, int add) {

  }

  protected void restoreSpecial (Canvas c) {

  }

  private static class DigitView {
    private float[] widths;
    private int diff;

    private int digit;
    private int toDigit;

    private String digitStr;
    private String toDigitStr;

    public DigitView (float[] widths, int diff) {
      this.widths = widths;
      this.diff = diff;
      this.digit = -1;
    }

    public void reset () {
      digit = -1;
      digitStr = null;
      toDigit = -1;
      toDigitStr = null;
    }

    public float getWidth () {
      float width = digit == -1 ? 0f : widths[digit];
      if (toDigit != -1) {
        width = Math.max(widths[toDigit], width);
      }
      return width;
    }

    public void applyDigit () {
      digit = toDigit;
      digitStr = toDigitStr;
      toDigit = -1;
      toDigitStr = null;
    }

    public void setDigit (int digit) {
      toDigit = digit;
      toDigitStr = digit == -1 ? null : valueOf(digit);
    }

    public void draw (CounterHeaderView v, Canvas c, int color, float left, float top, float factor, boolean forward) {
      TextPaint paint = v.getPaint(false, false);
      paint.setColor(color);
      if (factor == 0f || digit == toDigit) {
        if (digitStr != null) {
          c.drawText(digitStr, left, top, paint);
        }
        return;
      }
      if (factor == 1f) {
        if (toDigitStr != null) {
          c.drawText(toDigitStr, left, top, paint);
        }
        return;
      }

      float offset = forward ? top + diff * factor : top - diff * factor;

      if (digitStr != null) {
        paint.setAlpha((int) (255f * (1f - factor)));
        c.drawText(digitStr, left, offset, paint);
      }

      if (toDigitStr != null) {
        paint.setAlpha((int) (255f * factor));
        if (forward) {
          c.drawText(toDigitStr, left, offset - diff, paint);
        } else {
          c.drawText(toDigitStr, left, offset + diff, paint);
        }
      }

      /*if (digitStr != null || toDigitStr != null) {
        paint.setAlpha(255);
      }*/
    }
  }
}
