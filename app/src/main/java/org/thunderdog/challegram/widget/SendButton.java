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
 * File created on 11/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;

public class SendButton extends View implements FactorAnimator.Target, TooltipOverlayView.LocationProvider {

  private static Paint strokePaint;
  private final Drawable sendIcon;

  public SendButton (Context context, int sendIconRes) {
    super(context);
    sendIcon = Drawables.get(getResources(), sendIconRes);
    if (strokePaint == null) {
      strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      strokePaint.setStyle(Paint.Style.STROKE);
      strokePaint.setColor(Theme.iconColor());
      ThemeManager.addThemeListener(strokePaint, ColorId.icon);
      strokePaint.setStrokeWidth(Screen.dp(2f));
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    outRect.top += Screen.dp(8f);
    outRect.bottom -= Screen.dp(8f);
    /*if (targetView == this) {
      outRect.left -= Screen.dp(4f);
      outRect.right -= Screen.dp(4f);
    }*/
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return Views.onTouchEvent(this, event) && super.onTouchEvent(event);
  }

  private boolean inlineProgress;

  public boolean inInlineMode () {
    return inInlineModeAnimator.getValue();
  }

  private void setInInlineMode (boolean inInlineMode) {
    inInlineModeAnimator.setValue(inInlineMode, true);
  }

  private void setInInlineProgress (boolean inlineProgress) {
    if (this.inlineProgress != inlineProgress) {
      this.inlineProgress = inlineProgress;
      animateInlineProgress();
    }
  }

  public void setInInlineMode (boolean inInlineMode, boolean inInlineProgress) {
    setInInlineMode(inInlineMode);
    setInInlineProgress(inInlineProgress);
  }

  @Override
  protected void onDraw (Canvas c) {
    final int cx = getMeasuredWidth() / 2;
    final int cy = getMeasuredHeight() / 2;

    final float inlineFactor = inInlineModeAnimator.getFloatValue();
    final float sendScale = 1f - inlineFactor;
    final float activeFactor = isActiveAnimator.getFloatValue();
    final float editFactor = inEditModeAnimator.getFloatValue();

    if (sendScale > 0f) {
      if (editFactor != 1f) {
        final Paint paint = Paints.getSendButtonPaint();
        final int sourceAlpha = Color.alpha(Theme.chatSendButtonColor());
        final boolean saved = editFactor != 0f || sendScale != 1f;
        if (saved) {
          c.save();
          final float scale = Config.DEFAULT_ICON_SWITCH_SCALE + (1f - Config.DEFAULT_ICON_SWITCH_SCALE) * (1f - editFactor) * sendScale;
          c.scale(scale, scale, cx, cy);
          paint.setAlpha((int) ((float) sourceAlpha * (1f - editFactor) * sendScale));
        }
        boolean rtl = Lang.rtl();
        if (rtl) {
          if (!saved)
            c.save();
          c.scale(-1f, 1f, cx, cy);
        }
        Drawables.draw(c, sendIcon, cx - sendIcon.getMinimumWidth() / 2, cy - sendIcon.getMinimumHeight() / 2, paint);
        if (saved) {
          paint.setAlpha(sourceAlpha);
          c.restore();
        } else if (rtl) {
          c.restore();
        }
      }
      final float editScaleFactor = editFactor * sendScale;
      if (editScaleFactor != 0f) {
        final int color = ColorUtils.color((int) (255f * editScaleFactor), ColorUtils.fromToArgb(Theme.iconLightColor(), Theme.chatSendButtonColor(), activeFactor));

        c.save();

        if (editScaleFactor != 1f) {
          final float scale = Config.DEFAULT_ICON_SWITCH_SCALE + (1f - Config.DEFAULT_ICON_SWITCH_SCALE) * editScaleFactor;
          c.scale(scale, scale, cx, cy);
        }

        final int lineSize = Screen.dp(2f);
        final int line1Width = Screen.dp(17f);
        final int line2Height = Screen.dp(6.5f);

        c.translate(Screen.dp(2f), 0);
        c.rotate(-45f, cx, cy);

        final int left = cx - line1Width / 2;
        final int top1 = cy - lineSize / 2;
        final int top2 = cy - line2Height;
        final int right1 = cx + line1Width / 2;
        final int right2 = cx - line1Width / 2 + lineSize;
        final int bottom1 = cy + lineSize / 2;

        if (activeFactor == 1f || activeFactor == 0f) {
          c.drawRect(left, top1, right1, bottom1, Paints.fillingPaint(color));
          c.drawRect(left, top2, right2, cy, Paints.fillingPaint(color));
        } else {
          final int inactiveColor = ColorUtils.color((int) (255f * editScaleFactor), Theme.iconLightColor());
          final int activeColor = ColorUtils.color((int) (255f * editScaleFactor), Theme.chatSendButtonColor());

          int diff1x = right1 - left;
          int diff2y = cy - top2;
          int totalWidth = (int) ((float) (diff1x + diff2y) * activeFactor);

          diff2y = diff2y - Math.max(0, diff2y - totalWidth);
          diff1x = diff1x - Math.max(0, diff1x - totalWidth);

          int right1x = left + diff1x;
          int bottom2y = top2 + diff2y;

          if (right1x != right1) {
            c.drawRect(right1x, top1, right1, bottom1, Paints.fillingPaint(inactiveColor));
          }

          if (bottom2y != cy) {
            c.drawRect(left, top2, right2, cy, Paints.fillingPaint(inactiveColor));
          }

          if (diff1x > 0) {
            c.drawRect(left, top1, right1x, bottom1, Paints.fillingPaint(activeColor));
          }
          if (diff2y > 0) {
            c.drawRect(left, top2, right2, bottom2y, Paints.fillingPaint(activeColor));
          }
        }

        c.restore();
      }
    }

    if (inlineFactor > 0f) {
      int color = ColorUtils.color((int) (255f * inlineFactor), Theme.iconColor());
      final boolean saved = inlineProgressFactor != 1f;
      if (saved) {
        c.save();
        final float scale = Config.DEFAULT_ICON_SWITCH_SCALE + (1f - Config.DEFAULT_ICON_SWITCH_SCALE) * inlineFactor;
        c.scale(scale, scale, cx, cy);

        strokePaint.setColor(color);
        int radius = Screen.dp(8f);

        final float startFactor = inlineProgressFactor < .5f ? 0f : (inlineProgressFactor - .5f) / .5f;
        final float sweepFactor = inlineProgressFactor < .5f ? inlineProgressFactor / .5f : 1f - startFactor;

        RectF rectF = Paints.getRectF();
        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawArc(rectF, -45f + 360f * startFactor, 360f * sweepFactor, false, strokePaint);
      }

      if (inlineProgressFactor != .5f) {
        c.save();

        int width = Screen.dp(2f);
        int totalSize = Screen.dp(18f);
        int halfSize = totalSize / 2;
        int top = cy - halfSize;
        int bottom = cy + halfSize;
        int left = cx - width / 2;
        int right = cx + width / 2;
        int halfSizeBig = halfSize + width / 2;
        int halfSizeSmall = halfSize - width / 2;

        float progressFactor = inlineProgressFactor <= .5f ? inlineProgressFactor / .5f : (inlineProgressFactor - .5f) / .5f;

        RectF rectF = Paints.getRectF();

        if (inlineProgressFactor <= .5f) {
          // p1: second top (top -> center)
          // p2: first bottom (bottom -> center)
          // p3: second bottom (bottom -> center)
          // p4: first bottom (center -> top)

          float p1 = progressFactor < .25f ? progressFactor / .25f : 1f;
          float p2 = progressFactor <= .25f ? 0f : progressFactor < .5f ? (progressFactor - .25f) / .25f : 1f;
          float p3 = progressFactor <= .5f ? 0f : progressFactor < .75f ? (progressFactor - .5f) / .25f : 1f;
          float p4 = progressFactor <= .75f ? 0f : progressFactor < 1f ? (progressFactor - .75f) / .25f : 1f;

          // line that goes from top-right to bottom-left edge
          c.rotate(45f, cx, cy);

          int b1 = bottom - (int) ((float) halfSizeSmall * p2) - (int) ((float) halfSizeBig * p4);
          rectF.set(left, top, right, b1);
          c.drawRoundRect(rectF, width / 2, width / 2, Paints.fillingPaint(color));
          // c.drawRect(left, top, right, b1, Paints.fillingPaint(color));

          // line that goes from bottom-right to top-left edge
          c.rotate(90f, cx, cy);

          int t2 = top + (int) ((float) halfSizeSmall * p1);
          int b2 = bottom - (int) ((float) halfSizeBig * p3);
          rectF.set(left, t2, right, b2);
          c.drawRoundRect(rectF, width / 2, width / 2, Paints.fillingPaint(color));
          // c.drawRect(left, t2, right, b2, Paints.fillingPaint(color));
        } else {
          // .5f -> 1f
          // p1: first bottom (top -> center)
          // p2: second top (center -> top)
          // p3: first bottom (center -> bottom)
          // p4: second bottom (center -> bottom)

          float p1 = progressFactor < .25f ? progressFactor / .25f : 1f;
          float p2 = progressFactor <= .25f ? 0f : progressFactor < .5f ? (progressFactor - .25f) / .25f : 1f;
          float p3 = progressFactor <= .5f ? 0f : progressFactor < .75f ? (progressFactor - .5f) / .25f : 1f;
          float p4 = progressFactor <= .75f ? 0f : progressFactor < 1f ? (progressFactor - .75f) / .25f : 1f;

          // line that goes from top-right to bottom-left edge
          c.rotate(45f, cx, cy);

          int b1 = bottom - (int) ((float) halfSizeBig * (1f - p1)) - (int) ((float) halfSizeSmall * (1f - p3));
          rectF.set(left, top, right, b1);
          c.drawRoundRect(rectF, width / 2, width / 2, Paints.fillingPaint(color));

          // line that goes from bottom-right to top-left edge
          c.rotate(90f, cx, cy);

          int t2 = top + (int) ((float) halfSizeBig * (1f - p2));
          int b2 = bottom - (int) ((float) halfSizeSmall * (1f - p4));
          rectF.set(left, t2, right, b2);
          c.drawRoundRect(rectF, width / 2, width / 2, Paints.fillingPaint(color));
        }

        c.restore();
      }

      if (saved) {
        c.restore();
      }
    }
  }

  public void forceState (boolean inEditMode, boolean isActive) {
    inEditModeAnimator.setValue(inEditMode, false);
    isActiveAnimator.setValue(isActive, false);
  }

  // Active

  public void setIsActive (boolean isActive) {
    isActiveAnimator.setValue(isActive, inEditModeAnimator.getFloatValue() > 0f);
  }

  // Edit

  public void setInEditMode (boolean inEditMode) {
    inEditModeAnimator.setValue(inEditMode, true);
  }

  public boolean inSimpleSendMode () {
    return !inEditModeAnimator.getValue() && !inInlineModeAnimator.getValue();
  }

  // Animators

  private static final int EDIT_ANIMATOR = 0;
  private static final int ACTIVE_ANIMATOR = 1;
  private static final int INLINE_ANIMATOR = 2;
  private static final int INLINE_PROGRESS_ANIMATOR = 3;

  private final BoolAnimator inEditModeAnimator = new BoolAnimator(EDIT_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator isActiveAnimator = new BoolAnimator(ACTIVE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220l, true);
  private final BoolAnimator inInlineModeAnimator = new BoolAnimator(INLINE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private float inlineProgressFactor;
  private FactorAnimator inlineProgressAnimator;

  private void animateInlineProgress () {
    if (!inlineProgress) {
      return;
    }

    if (inlineProgressAnimator == null) {
      inlineProgressAnimator = new FactorAnimator(INLINE_PROGRESS_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, 890);
    } else if (inlineProgressAnimator.isAnimating()) {
      return;
    } else {
      inlineProgressFactor = 0f;
      inlineProgressAnimator.forceFactor(0f);
    }

    inlineProgressAnimator.animateTo(1f);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case EDIT_ANIMATOR:
      case ACTIVE_ANIMATOR:
      case INLINE_ANIMATOR: {
        break;
      }
      case INLINE_PROGRESS_ANIMATOR: {
        this.inlineProgressFactor = factor;
        break;
      }
    }
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case INLINE_PROGRESS_ANIMATOR: {
        animateInlineProgress();
        break;
      }
    }
  }
}
