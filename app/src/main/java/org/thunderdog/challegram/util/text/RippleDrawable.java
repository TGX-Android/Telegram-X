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
 * File created on 12/06/2024
 */

package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;

public class RippleDrawable extends Drawable {
  private final ArrayList<RipplePart> parts = new ArrayList<>();

  @Override
  public void draw (@NonNull Canvas c) {
    this.beforeDraw();

    final int buttonWidth = bounds.width();
    final int buttonHeight = bounds.height();
    final int left = bounds.left;
    final int top = bounds.top;

    final int rippleColor = ColorUtils.alphaColor(mainAlpha, Theme.getColor(mainColorId));

    final int partsCount = parts.size();
    int offset = 0;
    for (int a = 0; a < partsCount; a++) {
      RipplePart part = parts.get(a - offset);
      if (part.isJunk) {
        parts.remove(a - offset);
        offset++;
        continue;
      }

      part.draw(c, left, top, buttonWidth, buttonHeight, rippleColor);
    }
  }

  // View

  private @Nullable ViewProvider viewProvider;

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
    for (RipplePart part : parts) {
      part.setViewProvider(viewProvider);
    }
  }

  private void invalidate () {
    if (viewProvider != null) {
      viewProvider.invalidate(bounds);
    }
  }

  // Touch events

  private RipplePart caughtPart;



  public boolean onTouchEvent (View view, MotionEvent e, int x, int y) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        caughtPart = new RipplePart(this);
        caughtPart.anchorX = x;
        caughtPart.anchorY = y;
        caughtPart.animateSelectionFactor(1f);
        parts.add(caughtPart);
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (caughtPart != null) {
          caughtPart.anchorX = x;
          caughtPart.anchorY = y;
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (caughtPart != null) {
          caughtPart = null;
          cancelSelection();
        }
        return true;
      }
      case MotionEvent.ACTION_UP: {
        if (caughtPart != null) {
          caughtPart.anchorX = x;
          caughtPart.anchorY = y;
          caughtPart = null;
          ViewUtils.onClick(view);
          performClick(view);
          return true;
        }
        return false;
      }
    }

    return true;
  }

  public void performClick (View ignoredView) {
    cancelSelection();
  }

  public boolean performLongPress (View ignoredView) {
    if (caughtPart != null) {
      caughtPart = null;
      cancelSelection();
    }
    return false;
  }

  public void performCancelTouch () {
    if (caughtPart != null) {
      caughtPart = null;
      cancelSelection();
    }
  }

  private void cancelSelection () {
    for (RipplePart part : parts) {
      part.cancelSelection();
    }
  }





  /* * */

  private int mainColorId;
  private float mainAlpha;

  public void setMainColorId (@ColorId int mainColorId, float mainAlpha) {
    this.mainColorId = mainColorId;
    this.mainAlpha = mainAlpha;
  }

  public final Rect bounds = new Rect();
  private final RectF boundsF = new RectF();
  private final float[] radii = new float[8];
  private final Path path = new Path();
  private boolean pathInvalidated;

  public void setRadius(float leftTop, float rightTop, float rightBottom, float leftBottom) {
    radii[0] = radii[1] = leftTop;
    radii[2] = radii[3] = rightTop;
    radii[4] = radii[5] = rightBottom;
    radii[6] = radii[7] = leftBottom;
    pathInvalidated = true;
  }

  @Override
  public void setBounds (int left, int top, int right, int bottom) {
    super.setBounds(left, top, right, bottom);
    this.bounds.set(left, top, right, bottom);
    this.boundsF.set(left, top, right, bottom);
    pathInvalidated = true;
  }

  @Override
  public void setBounds (@NonNull Rect bounds) {
    super.setBounds(bounds);
    this.bounds.set(bounds);
    this.boundsF.set(bounds);
    pathInvalidated = true;
  }

  private void beforeDraw () {
    if (pathInvalidated) {
      path.reset();
      path.addRoundRect(boundsF, radii, Path.Direction.CCW);
      path.close();
      pathInvalidated = false;
    }
  }

  @Override
  public void setAlpha (int alpha) {

  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {

  }

  @Override
  @SuppressWarnings("deprecation")
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }


  /*  */

  private static class RipplePart implements FactorAnimator.Target {
    private static final long ANIMATION_DURATION = 220L;
    private static final int SELECTION_ANIMATOR = 0;
    private static final int FADE_ANIMATOR = 1;

    private final RippleDrawable parent;
    private int anchorX, anchorY;

    public RipplePart (RippleDrawable parent) {
      this.parent = parent;
    }

    private float selectionFactor;
    private FactorAnimator selectionAnimator;

    private void animateSelectionFactor (float toFactor) {
      if (selectionAnimator == null) {
        selectionAnimator = new FactorAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        selectionAnimator.setIsBlocked(parent.viewProvider == null || !parent.viewProvider.hasAnyTargetToInvalidate());
      }
      selectionAnimator.animateTo(toFactor);
    }

    private float fadeFactor;
    private FactorAnimator fadeAnimator;

    private void cancelSelection () {
      animateFadeFactor(1f);
    }

    private void animateFadeFactor (float toFactor) {
      if (fadeAnimator == null) {
        fadeAnimator = new FactorAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        fadeAnimator.setIsBlocked(parent.viewProvider == null || !parent.viewProvider.hasAnyTargetToInvalidate());
      }
      fadeAnimator.animateTo(toFactor);
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case SELECTION_ANIMATOR: {
          this.selectionFactor = factor;
          break;
        }
        case FADE_ANIMATOR: {
          this.fadeFactor = factor;
          break;
        }
      }
      parent.invalidate();
    }

    private boolean isJunk;

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == FADE_ANIMATOR) {
        if (finalFactor == 1f) {
          isJunk = true;
          parent.invalidate();
          // forceResetSelection();
        }
      }
    }

    private void forceResetSelection () {
      if (fadeAnimator != null) {
        fadeAnimator.forceFactor(this.fadeFactor = 0f);
      }
      if (selectionAnimator != null) {
        selectionAnimator.forceFactor(this.selectionFactor = 0f);
      }
    }

    public void setViewProvider (@Nullable ViewProvider viewProvider) {
      final boolean isBlocked = viewProvider == null;
      if (fadeAnimator != null) {
        fadeAnimator.setIsBlocked(isBlocked);
      }
      if (selectionAnimator != null) {
        selectionAnimator.setIsBlocked(isBlocked);
      }
    }

    public void draw (Canvas c, int x, int y, int width, int height, int color) {
      int selectionColor = ColorUtils.fromToArgb(ColorUtils.alphaColor(0x1a / 255f, color), color, selectionFactor);
      if (fadeFactor != 0f) {
        selectionColor = ColorUtils.color((int) ((float) Color.alpha(selectionColor) * (1f - fadeFactor)), selectionColor);
      }

      if (selectionFactor != 0f) {
        if (selectionFactor == 1f) {
          c.drawPath(parent.path, Paints.fillingPaint(selectionColor));
        } else {
          int anchorX = Math.max(Math.min(this.anchorX, width), 0);
          int anchorY = Math.max(Math.min(this.anchorY, height), 0);
          float selectionRadius = (float) Math.sqrt(width * width + height * height) * .5f * selectionFactor;
          float centerX = width / 2f;
          float centerY = height / 2f;
          float diffX = centerX - anchorX;
          float diffY = centerY - anchorY;
          float selectionX = x + anchorX + diffX * selectionFactor;
          float selectionY = y + anchorY + diffY * selectionFactor;

          final int saveCount;
          if ((saveCount = ViewSupport.clipPath(c, parent.path)) != Integer.MIN_VALUE) {
            c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
          } else {
            c.drawPath(parent.path, Paints.fillingPaint(selectionColor));
          }
          ViewSupport.restoreClipPath(c, saveCount);
        }
      }
    }
  }
}
