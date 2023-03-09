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
 * File created on 23/04/2015 at 17:44
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class InterceptLayout extends FrameLayoutFix implements FactorAnimator.StartHelper {
  public InterceptLayout (Context context) {
    super(context);
    setOnTouchListener((BaseActivity) context);
  }

  public boolean isBlocked () {
    final BaseActivity context = UI.getUiContext();
    return context != null && context.getGestureController() != null && context.getGestureController().isDispatching();
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return super.onTouchEvent(event) || event.getAction() == MotionEvent.ACTION_DOWN;
  }

  @Override
  public void draw (Canvas c) {
    if (((BaseActivity) getContext()).isPasscodeShowing()) {
      c.drawColor(Theme.headerColor());
    } else {
      super.draw(c);
    }
  }

  private boolean disallowIntercept;

  /*@Override
  public void requestDisallowInterceptTouchEvent (boolean disallowIntercept) {
    this.disallowIntercept = disallowIntercept;
    super.requestDisallowInterceptTouchEvent(disallowIntercept);
  }*/

  public boolean onInterceptTouchEvent (MotionEvent event) {
    if (disallowIntercept) {
      return false;
    }

    final BaseActivity context = ((BaseActivity) getContext());

    if (isBlocked() || context.isAnimating(true)) {
      return true;
    }

    DrawerController drawer = context.getDrawer();
    if (drawer != null && drawer.isVisible() && event.getAction() == MotionEvent.ACTION_DOWN) {
      if (Lang.rtl()) {
        if (event.getX() < getMeasuredWidth() - drawer.getWidth() + drawer.getShadowWidth()) {
          context.processTouchEvent(event);
          return true;
        }
      } else {
        if (event.getX() >= drawer.getWidth()) {
          context.processTouchEvent(event);
          return true;
        }
      }
    }

    return context.processTouchEvent(event) && event.getAction() != MotionEvent.ACTION_DOWN;
  }

  private FactorAnimator scheduledAnimator;
  private float scheduledAnimationFactor;

  @Override
  public void startAnimatorOnLayout (FactorAnimator animator, float toFactor) {
    scheduledAnimator = animator;
    scheduledAnimationFactor = toFactor;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (scheduledAnimator != null) {
      scheduledAnimator.animateTo(scheduledAnimationFactor);
      scheduledAnimator = null;
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    UI.getContext(getContext()).applyContentTranslation(this);
  }
}
