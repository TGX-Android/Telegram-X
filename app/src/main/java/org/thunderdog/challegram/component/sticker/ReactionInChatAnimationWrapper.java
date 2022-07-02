/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 22/11/2016
 */
package org.thunderdog.challegram.component.sticker;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class ReactionInChatAnimationWrapper extends FrameLayoutFix implements Destroyable {

  public static final float PADDING = 8f;

  private final int[] locationOnScreen = new int[2];

  public int yOffset = 0;

  private int targetX = -1;
  private int targetY = -1;

  private SpringAnimation translateAnimationX = null;
  private SpringAnimation translateAnimationY = null;
  private SpringAnimation scaleAnimationX = null;
  private SpringAnimation scaleAnimationY = null;

  private ReactionInChatView reactionInChatView;

  public ReactionInChatAnimationWrapper (Context context) {
    super(context);
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    this.getLocationOnScreen(locationOnScreen);
    yOffset = locationOnScreen[1];
  }

  @Override
  public void performDestroy () {
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View view = getChildAt(i);
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      } else if (view instanceof ViewGroup) {
        Views.destroy((ViewGroup) view);
      }
      removeViewAt(i);
    }
  }

  public void reattach (ReactionInChatView reactionInChatView) {
    this.reactionInChatView = reactionInChatView;

    reactionInChatView.detach();

    int[] location = new int[2];
    reactionInChatView.getLocationOnScreen(location);
    int x = location[0];
    int y = location[1];

    ((ViewGroup) reactionInChatView.getParent()).removeView(reactionInChatView);
    this.addView(reactionInChatView);

    reactionInChatView.setPadding(0, 0, 0, 0);
    LayoutParams layoutParams = (LayoutParams) reactionInChatView.getLayoutParams();
    layoutParams.leftMargin = x;
    layoutParams.topMargin = y - yOffset;
    reactionInChatView.setLayoutParams(layoutParams);

    reactionInChatView.attach();
  }

  public void setTargetXY (View view, int targetX, int targetY) {
    if (view == null) return;

    int[] position = new int[2];
    view.getLocationOnScreen(position);

    int fromLeftMargin = ((MarginLayoutParams) getLayoutParams()).leftMargin;
    int fromTopMargin = ((MarginLayoutParams) getLayoutParams()).topMargin;

    this.targetX = position[0] + targetX - Screen.dp(10f) - fromLeftMargin;
    this.targetY = position[1] + targetY - yOffset - Screen.dp(10f) - fromTopMargin;
  }

  public void playAnimation () {
    if (targetX == -1 || targetY == -1 || reactionInChatView == null) return;

    translateAnimationX = new SpringAnimation(reactionInChatView, floatPropertyAnimX, targetX);
    translateAnimationX.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    translateAnimationX.getSpring().setStiffness(75f);
    translateAnimationX.addEndListener((animation, canceled, value, velocity) -> {
      startScaleAnimation();
    });
    translateAnimationY = new SpringAnimation(reactionInChatView, floatPropertyAnimY, targetY);
    translateAnimationY.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    translateAnimationY.getSpring().setStiffness(SpringForce.STIFFNESS_VERY_LOW);
    translateAnimationY.addEndListener((animation, canceled, value, velocity) -> {
      startScaleAnimation();
    });

    scaleAnimationX = new SpringAnimation(reactionInChatView, DynamicAnimation.SCALE_X, .5f);
    scaleAnimationX.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    scaleAnimationX.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
    scaleAnimationX.addEndListener((animation, canceled, value, velocity) -> {
      removeReaction();
    });

    scaleAnimationY = new SpringAnimation(reactionInChatView, DynamicAnimation.SCALE_Y, .5f);
    scaleAnimationY.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    scaleAnimationY.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
    scaleAnimationY.addEndListener((animation, canceled, value, velocity) -> {
      removeReaction();
    });

    translateAnimationX.start();
    translateAnimationY.start();
  }

  private void startScaleAnimation () {
    if (translateAnimationX != null
            && translateAnimationY != null
            && !translateAnimationX.isRunning()
            && !translateAnimationY.isRunning()) {
      if (scaleAnimationX != null && scaleAnimationY != null) {
        scaleAnimationX.start();
        scaleAnimationY.start();
      }
    }
  }

  private void removeReaction () {
    if (scaleAnimationX != null
            && scaleAnimationY != null
            && !scaleAnimationX.isRunning()
            && !scaleAnimationY.isRunning()) {
      if (reactionInChatView != null) {
        removeView(reactionInChatView);
        reactionInChatView = null;
      }
    }
  }


  private final FloatPropertyCompat<ReactionInChatView> floatPropertyAnimX = new FloatPropertyCompat<>("X") {
    @Override
    public float getValue (ReactionInChatView view) {
      return ((MarginLayoutParams) view.getLayoutParams()).leftMargin;
    }

    @Override
    public void setValue (ReactionInChatView view, float value) {
      MarginLayoutParams layoutParams = (MarginLayoutParams) view.getLayoutParams();
      layoutParams.leftMargin = (int) value;
      view.setLayoutParams(layoutParams);
    }
  };

  private final FloatPropertyCompat<ReactionInChatView> floatPropertyAnimY = new FloatPropertyCompat<>("Y") {
    @Override
    public float getValue (ReactionInChatView view) {
      return ((MarginLayoutParams) view.getLayoutParams()).topMargin;
    }

    @Override
    public void setValue (ReactionInChatView view, float value) {
      MarginLayoutParams layoutParams = (MarginLayoutParams) view.getLayoutParams();
      layoutParams.topMargin = (int) value;
      view.setLayoutParams(layoutParams);
    }
  };
}
