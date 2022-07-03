package org.thunderdog.challegram.component.payments;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class PaymentTipPartView extends View implements FactorAnimator.Target {
  private final Path rectPath;
  private final RectF rect;

  public static int defaultHeight () {
    return Screen.dp(32f);
  }

  public PaymentTipPartView (Context context) {
    super(context);

    rectPath = new Path();
    rect = new RectF();
  }

  // Text and layout

  private Letters text;
  private int textWidth;

  public void setText (String txt) {
    this.text = new Letters(txt.toUpperCase());
    this.textWidth = (int) U.measureText(text.text, Paints.getBoldPaint15(text.needFakeBold));
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.makeMeasureSpec(textWidth + Screen.dp(15f) * 2 + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    updatePath();
  }

  // Touch events

  private boolean isToggled;

  public void setToggled (boolean isToggled) {
    if (this.isToggled != isToggled) {
      this.isToggled = isToggled;
      animateToggleAnimator(isToggled ? 1f : 0f);
    }
  }

  // Animations

  private static final int TOGGLE_ANIMATOR = 0;
  private FactorAnimator toggleAnimator;

  private void animateToggleAnimator (float factor) {
    if (toggleAnimator == null) {
      toggleAnimator = new FactorAnimator(TOGGLE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, toggleFactor);
    }
    toggleAnimator.animateTo(factor);
  }

  private float toggleFactor;

  private void setToggleFactor (float factor) {
    if (this.toggleFactor != factor) {
      this.toggleFactor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case TOGGLE_ANIMATOR: {
        setToggleFactor(factor);
        break;
      }
    }
  }

  private int getStrokeWidth () {
    return Screen.dp(1.5f);
  }

  private void updatePath () {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    int padding = getStrokeWidth() / 2;
    rect.set(padding, padding, width - padding, height - padding);

    rectPath.reset();
    rectPath.addRoundRect(rect, Screen.dp(3f), Screen.dp(3f), Path.Direction.CCW);
  }

  @Override
  public void setEnabled (boolean enabled) {
    if (isEnabled() != enabled) {
      super.setEnabled(enabled);
      setInnerAlpha(enabled ? 1f : .3f);
    }
  }

  // Drawing

  private int backgroundColorId;

  public void setBackgroundColorId (int backgroundColorId) {
    if (this.backgroundColorId != backgroundColorId) {
      this.backgroundColorId = backgroundColorId;
      invalidate();
    }
  }

  private float innerAlpha = 1f;

  private void setInnerAlpha (float alpha) {
    if (this.innerAlpha != alpha) {
      this.innerAlpha = alpha;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    final int outlineColor = Theme.inlineOutlineColor(false);

    RectF rectF = Paints.getRectF();
    int radius = Screen.dp(3f);

    int padding = getStrokeWidth() / 2;
    rectF.set(padding, padding, width - padding, height - padding);

    if (backgroundColorId != 0) {
      c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(Theme.getColor(backgroundColorId)));
    }
    c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(ColorUtils.alphaColor(innerAlpha, Theme.inlineOutlineColor(false)), getStrokeWidth()));

    final float pressedFactor = 1f - this.toggleFactor;
    final float reverseFactor = this.toggleFactor;

    final float colorFactor = reverseFactor;
    final int selectionColor = ColorUtils.alphaColor(innerAlpha, ColorUtils.fromToArgb(ColorUtils.color(0x00, outlineColor), outlineColor, reverseFactor));
    final int activeColor = Theme.inlineTextActiveColor();

    final int textColor = ColorUtils.fromToArgb(Theme.inlineTextColor(false), activeColor, colorFactor);

    c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(selectionColor));

    int color = ColorUtils.color((int) (255f * innerAlpha), textColor);
    int cx = width / 2;
    if (this.text != null) {
      cx -= textWidth / 2;
      c.drawText(text.text, cx, height / 2f + Screen.dp(5f), Paints.getBoldPaint15(text.needFakeBold, color));
    }
  }
}
