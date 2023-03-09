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
 * File created on 29/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;

public class NonMaterialButton extends View implements FactorAnimator.Target, ClickHelper.Delegate {
  private final Path rectPath;
  private final RectF rect;

  private final ClickHelper helper;

  private boolean forceActive;

  public interface PressureListener {
    void onPressStateChanged (NonMaterialButton btn, boolean isPressed);
  }

  private PressureListener pressureListener;

  public void setPressureListener (PressureListener listener) {
    this.pressureListener = listener;
  }

  public static int defaultHeight () {
    return Screen.dp(32f);
  }

  public NonMaterialButton (Context context) {
    super(context);

    rectPath = new Path();
    rect = new RectF();
    helper = new ClickHelper(this);
  }

  public void setForceActive (boolean isActive) {
    if (this.forceActive != isActive) {
      this.forceActive = isActive;
      invalidate();
    }
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return onClickListener != null && !isDone && isEnabled() && getVisibility() == View.VISIBLE;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    onClick();
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return onLongClickListener != null;
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    return onLongClick();
  }

  // Text and layout

  private Letters text;
  private int textWidth;

  private Drawable icon;
  private int iconRes;

  public void setText (@StringRes int res) {
    this.text = res != 0 ? new Letters(Lang.getString(res).toUpperCase()) : null;
    this.textWidth = text != null ? (int) U.measureText(text.text, Paints.getBoldPaint15(text.needFakeBold)) : 0;

    this.icon = null;
    this.iconRes = 0;
  }

  public void setIcon (@DrawableRes int res) {
    this.icon = res != 0 ? (iconRes == res ? icon : Drawables.get(getResources(), res)) : null;
    this.iconRes = res;

    this.text = null;
    this.textWidth = 0;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int measuredWidth;
    if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
      measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
    } else {
      measuredWidth = textWidth + Screen.dp(15f) * 2 + getPaddingLeft() + getPaddingRight();
    }
    int measuredHeight = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(measuredWidth, measuredHeight);

    updatePath();
    setProgressBounds();
  }

  // Touch events

  private float startX, startY;
  private int anchorX, anchorY;
  private boolean isPressed;

  private @Nullable View.OnClickListener onClickListener;

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    this.onClickListener = l;
  }

  private void onClick () {
    ViewUtils.onClick(this);
    if (onClickListener != null && !inProgress) {
      onClickListener.onClick(this);
    }
  }

  private @Nullable View.OnLongClickListener onLongClickListener;

  @Override
  public void setOnLongClickListener (@Nullable OnLongClickListener l) {
    this.onLongClickListener = l;
  }

  private boolean onLongClick () {
    return onLongClickListener != null && onLongClickListener.onLongClick(this);
  }

  private void setIsPressed (boolean isPressed) {
    if (this.isPressed != isPressed) {
      this.isPressed = isPressed;
      if (pressureListener != null)
        pressureListener.onPressStateChanged(this, isPressed);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res = helper.onTouchEvent(this, e);
    float x = e.getX();
    float y = e.getY();
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        startX = x;
        startY = y;

        anchorX = (int) x;
        anchorY = (int) y;

        setIsPressed(needClickAt(this, x, y));
        if (isPressed) {
          if (pressedFactor != 0f)
            resetPressure();
          animatePressFactor(1f);
        } else {
          return res;
        }

        break;
      }
      case MotionEvent.ACTION_MOVE: {
        anchorX = (int) x;
        anchorY = (int) y;

        if (isPressed && Math.max(Math.abs(x - startX), Math.abs(y - startY)) > Screen.getTouchSlop()) {
          setIsPressed(false);
          animateFadeFactor(1f);
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP: {
        if (isPressed) {
          setIsPressed(false);
          animateFadeFactor(1f);
        }
        break;
      }
    }
    return res;
  }

  // Animations

  private static final int PRESS_ANIMATOR = 0;
  private FactorAnimator pressAnimator;

  private void animatePressFactor (float factor) {
    if (pressAnimator == null) {
      pressAnimator = new FactorAnimator(PRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, pressedFactor);
    }
    pressAnimator.animateTo(factor);
  }

  private float pressedFactor;

  private void setPressedFactor (float factor) {
    if (this.pressedFactor != factor) {
      this.pressedFactor = factor;
      invalidate();
    }
  }

  private static final int FADE_ANIMATOR = 1;
  private FactorAnimator fadeAnimator;
  private float fadeFactor;

  private void animateFadeFactor (float factor) {
    if (fadeAnimator == null) {
      fadeAnimator = new FactorAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, fadeFactor);
    }
    fadeAnimator.animateTo(factor);
  }

  private void setFadeFactor (float factor) {
    if (this.fadeFactor != factor) {
      this.fadeFactor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case PRESS_ANIMATOR: {
        setPressedFactor(factor);
        break;
      }
      case FADE_ANIMATOR: {
        setFadeFactor(factor);
        break;
      }
      case PROGRESS_ANIMATOR: {
        setProgressFactor(factor);
        break;
      }
      case DONE_ANIMATOR: {
        setDoneFactor(factor);
        break;
      }
    }
  }

  private void resetPressure () {
    fadeAnimator.forceFactor(0f);
    fadeFactor = 0f;
    if (pressAnimator != null) {
      pressAnimator.forceFactor(0f);
    }
    this.pressedFactor = 0f;
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case FADE_ANIMATOR: {
        resetPressure();
        break;
      }
    }
  }

  // progress

  private static final int PROGRESS_ANIMATOR = 2;
  private ProgressComponent progress;
  private FactorAnimator progressAnimator;
  private float progressFactor;

  private boolean inProgress;

  public void setInProgress (boolean inProgress, boolean animated) {
    if (inProgress && progress == null) {
      progress = new ProgressComponent(UI.getContext(getContext()), Screen.dp(3.5f));
      progress.attachToView(this);
      setProgressBounds();
    }
    if (this.inProgress != inProgress && animated) {
      this.inProgress = inProgress;
      if (progressAnimator == null) {
        progressAnimator = new FactorAnimator(PROGRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, progressFactor);
      }
      progressAnimator.animateTo(inProgress ? 1f : 0f);
    } else {
      this.inProgress = inProgress;
      if (progressAnimator != null) {
        progressAnimator.forceFactor(inProgress ? 1f : 0f);
      }
      setProgressFactor(inProgress ? 1f : 0f);
    }
  }

  private void setProgressFactor (float factor) {
    if (this.progressFactor != factor) {
      this.progressFactor = factor;
      invalidate();
    }
  }

  private int getRadius () {
    return doneFactor == 0f ? Screen.dp(3f) : Screen.dp(3f) + (int) ((float) (Screen.dp(14f) - Screen.dp(3f)) * doneFactor);
  }

  private int getStrokeWidth () {
    return Screen.dp(1.5f);
  }

  private void updatePath () {
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();

    int padding = (int) (getStrokeWidth() / 2);
    int left = padding; // + (int) ((float) (width - Screen.dp(14f) * 2) * doneFactor);
    rect.set(left, padding, width - padding, height - padding);

    rectPath.reset();
    rectPath.addRoundRect(rect, Screen.dp(3f), Screen.dp(3f), Path.Direction.CCW);
  }

  private void setProgressBounds () {
    if (progress != null) {
      int padding = (int) (getStrokeWidth() / 2);
      int width = getMeasuredWidth();
      progress.setBounds((width - Screen.dp(13f) - padding), padding, width - padding, padding + Screen.dp(13f));
    }
  }

  // Done

  private static final int DONE_ANIMATOR = 3;
  private FactorAnimator doneAnimator;
  private boolean isDone;

  public void setIsDone (boolean isDone, boolean animated) {
    if (this.isDone != isDone && animated) {
      this.isDone = isDone;
      if (doneAnimator == null) {
        doneAnimator = new FactorAnimator(DONE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, doneFactor);
      }
      doneAnimator.animateTo(isDone ? 1f : 0f);
    } else if (!animated) {
      this.isDone = isDone;
      if (doneAnimator != null) {
        doneAnimator.forceFactor(isDone ? 1f : 0f);
      }
      setDoneFactor(isDone ? 1f : 0f);
    }
  }

  private float doneFactor;

  private void setDoneFactor (float factor) {
    if (this.doneFactor != factor) {
      this.doneFactor = factor;
      invalidate();
    }
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

    final float buttonFactor = doneFactor >= .5f ? 0f : 1f - (doneFactor / .5f);
    final int outlineColor = Theme.inlineOutlineColor(false);

    if (buttonFactor > 0f) {
      RectF rectF = Paints.getRectF();
      int radius = Screen.dp(3f);

      int padding = (int) (getStrokeWidth() / 2);
      rectF.set(padding, padding, width - padding, height - padding);

      final boolean saved = buttonFactor != 1f;
      if (saved) {
        c.save();
        final float scale = .7f + .3f * buttonFactor;
        c.scale(scale, scale, rectF.centerX(), rectF.centerY());
      }

      if (backgroundColorId != 0) {
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(Theme.getColor(backgroundColorId)));
      }
      c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(ColorUtils.alphaColor(innerAlpha, Theme.inlineOutlineColor(false)), getStrokeWidth()));

      final float pressedFactor = this.forceActive ? 1f : this.pressedFactor;
      final float reverseFactor = pressedFactor * (1f - fadeFactor);
      final float colorFactor = reverseFactor * buttonFactor;
      final int selectionColor = ColorUtils.alphaColor(innerAlpha, ColorUtils.fromToArgb(ColorUtils.color(0x00, outlineColor), outlineColor, reverseFactor * buttonFactor));
      final int activeColor = Theme.inlineTextActiveColor();

      final int textColor = ColorUtils.fromToArgb(Theme.inlineTextColor(false), activeColor, colorFactor);

      if (pressedFactor > 0 && fadeFactor < 1f) {
        if (pressedFactor == 1f) {
          c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(selectionColor));
        } else {
          final int saveCount;
          if ((saveCount = ViewSupport.clipPath(c, rectPath)) != Integer.MIN_VALUE) {
          /* selection */
            int anchorX = Math.max(Math.min(this.anchorX, width), 0);
            int anchorY = Math.max(Math.min(this.anchorY, height), 0);
            float selectionRadius = (float) Math.sqrt(width * width + height * height) * .5f * pressedFactor;
            float centerX = width / 2;
            float centerY = height / 2;
            float diffX = centerX - anchorX;
            float diffY = centerY - anchorY;
            float selectionX = anchorX + diffX * pressedFactor;
            float selectionY = anchorY + diffY * pressedFactor;

            c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
          } else {
            c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(selectionColor));
          }
          ViewSupport.restoreClipPath(c, saveCount);
        }
      }

      if (progress != null) {
        final int iconColor = ColorUtils.fromToArgb(Theme.inlineIconColor(false), activeColor, colorFactor);
        progress.forceColor(ColorUtils.color((int) (255f * innerAlpha * progressFactor * buttonFactor), iconColor));
        progress.draw(c);
      }

      int color = ColorUtils.color((int) (255f * buttonFactor * innerAlpha), textColor);
      int cx = width / 2;
      if (this.text != null) {
        cx -= textWidth / 2;
        c.drawText(text.text, cx, height / 2 + Screen.dp(5f), Paints.getBoldPaint15(text.needFakeBold, color));
      } else if (this.icon != null) {
        cx -= this.icon.getMinimumWidth() / 2;
        Paint paint = Paints.getPorterDuffPaint(color); // TODO performance
        Drawables.draw(c, icon, cx, height / 2 - this.icon.getMinimumHeight() / 2, paint);
      }

      if (saved) {
        c.restore();
      }
    } else if (progress != null) {
      progress.setAlpha(0f);
    }

    final float checkFactor = doneFactor > .5f ? (doneFactor - .5f) / .5f : 0f;

    if (checkFactor > 0f) {
      c.save();
      int checkWidth = Screen.dp(24f);
      c.translate(Lang.rtl() ? Screen.dp(2.5f) : width - checkWidth - Screen.dp(2.5f), height / 2 + Screen.dp(1f));
      c.rotate(-45f);

      final float fx = isDone ? checkFactor : 1f;
      final float t1 = .3f;
      final float f1 = fx <= t1 ? fx / t1 : 1f;
      final float f2 = fx <= t1 ? 0f : (fx - t1) / (1f - t1);

      final int w2max = Screen.dp(14f);
      final int h1max = Screen.dp(7f);

      final int w2 = (int) ((float) w2max * f2);
      final int h1 = (int) ((float) h1max * f1);

      final int x1 = Screen.dp(4f);
      final int y1 = Screen.dp(11f);

      int lineSize = Screen.dp(2f);

      final int color = isDone ? outlineColor : ColorUtils.fromToArgb(ColorUtils.color(0x00, outlineColor), outlineColor, checkFactor);
      c.drawRect(x1, y1 - h1max, x1 + lineSize, y1 - h1max + h1, Paints.fillingPaint(color));
      c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(color));

      c.restore();
    }
  }
}
