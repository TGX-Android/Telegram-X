package me.vkryl.android.widget;

import android.content.Context;
import android.view.View;

import me.vkryl.android.animator.Animated;

/**
 * Date: 01/12/2016
 * Author: default
 */

public class AnimatedFrameLayout extends FrameLayoutFix implements Animated {
  public AnimatedFrameLayout (Context context) {
    super(context);
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    this.pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }
}
