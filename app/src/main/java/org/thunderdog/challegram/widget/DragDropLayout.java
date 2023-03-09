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
 * File created on 12/03/2018
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.component.preview.FlingDetector;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class DragDropLayout extends FrameLayoutFix implements FlingDetector.Callback {
  public DragDropLayout (@NonNull Context context) {
    super(context);
  }

  private static boolean isInside (float x, float y, float left, float top, int width, int height) {
    return x >= left && y >= top && x < left + width && y < top + height;
  }

  private View findChildUnder (float x, float y) {
    if (disallowIntercept)
      return null;
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      if (view != null && isInside(x, y, view.getLeft() + view.getTranslationX(), view.getTop() + view.getTranslationY(), view.getMeasuredWidth(), view.getMeasuredHeight())) {
        if (view.getAlpha() != 1f || view.getVisibility() != View.VISIBLE || view.getScaleX() != 1f || view.getScaleY() != 1f) {
          return null;
        }
        return view;
      }
    }
    return null;
  }

  private View dragView;
  private float startX, startY, dragX, dragY;
  private boolean dragging;

  private boolean disallowIntercept;

  @Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
    this.disallowIntercept = disallowIntercept;
  }

  private FlingDetector detector = new FlingDetector(getContext(), this);

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    float x = ev.getX();
    float y = ev.getY();
    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        dropDrag(0f, 0f);
        dragView = findChildUnder(x, y);
        if (dragView == null)
          return true;
        detector.onTouchEvent(ev);
        startX = x;
        startY = y;
        dragX = dragView.getTranslationX();
        dragY = dragView.getTranslationY();
        FactorAnimator animator = (FactorAnimator) dragView.getTag();
        if (animator != null && animator.isAnimating()) {
          animator.cancel();
          dragging = true;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (dragView != null && Math.max(Math.abs(x - startX), Math.abs(y - startY)) >= Screen.getTouchSlop()) {
          startX = x;
          startY = y;
          dragging = true;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        detector.onTouchEvent(ev);
        break;
      }
    }
    return super.onInterceptTouchEvent(ev);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (dragView != null)
      detector.onTouchEvent(e);
    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        if (dragging) {
          dragView.setTranslationX(dragX + e.getX() - startX);
          dragView.setTranslationY(dragY + e.getY() - startY);
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        dropDrag(0f, 0f);
        break;
      }
    }
    return super.onTouchEvent(e);
  }

  @Override
  public boolean onFling (float velocityX, float velocityY) {
    if (dragging) {
      dropDrag(velocityX, velocityY);
      return true;
    }
    return false;
  }

  private void dropDrag (float velocityX, float velocityY) {
    if (!dragging)
      return;

    View view = dragView;
    dragging = false;

    float centerX = getMeasuredWidth() / 2;
    float centerY = getMeasuredHeight() / 2;

    int halfWidth = view.getMeasuredWidth() / 2;
    int halfHeight = view.getMeasuredHeight() / 2;

    float cx = view.getX() + halfWidth;
    float cy = view.getY() + halfHeight;

    float currentX = cx > centerX ? 1f : 0f;
    float currentY = (cy > centerY - halfHeight / 2 && cy < centerY + halfHeight / 2) ? .5f : cy > centerY ? 1f : 0f;
    float toX, toY;

    float touchSlop = Screen.getTouchSlopBig();

    if ((velocityX != 0 || velocityY != 0) && Math.max(Math.abs(velocityX), Math.abs(velocityY)) > touchSlop) {
      double degrees = Math.toDegrees(Math.atan2(velocityY, velocityX));
      double absDegrees = Math.abs(degrees);

      toX = Math.abs(velocityX) < touchSlop ? currentX : absDegrees > 115 ? 0f : absDegrees < 65f ? 1f : currentX;
      toY = Math.abs(velocityY) < touchSlop ? currentY : (absDegrees >= 45 && absDegrees <= 135) ? (degrees > 0 ? (currentY >= .5f ? 1f : .5f) : (currentY >= .5f ? .5f : 0f)) : currentY;
    } else {
      toX = currentX;
      toY = currentY;
    }


    int gravity = ((LayoutParams) view.getLayoutParams()).gravity;
    int toGravity = (toX == 1f ? Gravity.RIGHT : toX == .5f ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT) | (toY == 1f ? Gravity.BOTTOM : toY == .5f ? Gravity.CENTER_VERTICAL : Gravity.TOP);

    final float animatedX, animatedY;
    if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
      animatedX = toX == 1f ? 0f : -1f;
    } else {
      animatedX = toX;
    }
    switch ((gravity & Gravity.VERTICAL_GRAVITY_MASK)) {
      case Gravity.BOTTOM:
        animatedY = toY - 1f;
        break;
      case Gravity.CENTER_VERTICAL:
        animatedY = toY - .5f;
        break;
      default:
        animatedY = toY;
        break;
    }

    float fromX = view.getTranslationX();
    float fromY = view.getTranslationY();

    FactorAnimator animator = (FactorAnimator) view.getTag();
    if (animator != null) {
      animator.cancel();
    }
    animator = new FactorAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        int availableX = getMeasuredWidth() - view.getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int availableY = getMeasuredHeight() - view.getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        view.setTranslationX(fromX + ((animatedX * availableX) - fromX) * factor);
        view.setTranslationY(fromY + ((animatedY * availableY) - fromY) * factor);
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        if (Views.setGravity(view, toGravity)) {
          view.setTranslationX(0f);
          view.setTranslationY(0f);
          Views.updateLayoutParams(view);
          Settings.instance().setMinimizedThemeLocation(toGravity); // FIXME move to different place
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 230l);
    animator.animateTo(1f);
  }

  public void addViewAnimated (View view) {
    if (view.getTag() instanceof FactorAnimator)
      ((FactorAnimator) view.getTag()).cancel();
    if (view.getParent() == null) {
      addView(view);
      view.setScaleX(.7f);
      view.setScaleY(.7f);
      view.setAlpha(0f);
    }
    float fromScaleX = view.getScaleX();
    float fromScaleY = view.getScaleY();
    float fromAlpha = view.getAlpha();
    FactorAnimator animator = new FactorAnimator(0, (id, factor, fraction, callee) -> {
      view.setScaleX(fromScaleX + (1f - fromScaleX) * factor);
      view.setScaleY(fromScaleY + (1f - fromScaleY) * factor);
      view.setAlpha(fromAlpha + (1f - fromAlpha) * factor);
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 140l);
    view.setTag(animator);
    animator.animateTo(1f);
  }

  public void removeViewAnimated (View view, boolean removeFromParent) {
    if (view.getTag() instanceof FactorAnimator) {
      ((FactorAnimator) view.getTag()).cancel();
    }
    float fromAlpha = view.getAlpha();
    float fromScaleX = view.getScaleX();
    float fromScaleY = view.getScaleY();
    FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        view.setAlpha(fromAlpha * (1f - factor));
        view.setScaleX(fromScaleX + (.7f - fromScaleX) * factor);
        view.setScaleY(fromScaleY + (.7f - fromScaleY) * factor);
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        removeView(view);
        if (removeFromParent && getChildCount() == 0 && getParent() != null) {
          ((ViewGroup) getParent()).removeView(DragDropLayout.this);
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 140l);
    view.setTag(animator);
    animator.animateTo(1f);
  }
}
