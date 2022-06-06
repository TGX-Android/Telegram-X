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
 * File created on 26/04/2015 at 11:27
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.Anim;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;

public class SpinnerView extends ImageView implements Runnable {
  public static final long CYCLE_DURATION = 1000000;

  public boolean started;

  //private Runnable starter;
  private RotateAnimation animation;

  public SpinnerView (Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_INSIDE);
  }

  public void start () {
    if (!started && getVisibility() != View.GONE) {
      started = true;

      if (Anim.ANIMATORS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        startAnimator();
      } else {
        startSimpleAnimation();
      }
    }
  }

  private void startAnimator () {
    try {
      Views.rotateBy(this, -360000f, CYCLE_DURATION, AnimatorUtils.LINEAR_INTERPOLATOR, null);
    } catch (Throwable throwable) {
      Log.e("Cannot animate SpinnerView, applying simple Animation", throwable);
      startSimpleAnimation();
    }
  }

  private void startSimpleAnimation () {
    try {
      animation = new RotateAnimation(0, -360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      animation.setDuration(1000);
      animation.setRepeatCount(-1);
      animation.setInterpolator(AnimatorUtils.LINEAR_INTERPOLATOR);
      animation.setFillAfter(true);

      startAnimation(animation);
    } catch (Throwable t) {
      Log.e("Cannot start simple animation on SpinnerView", t);
    }
  }

  public void stop () {
    if (started) {
      started = false;
      if (Anim.ANIMATORS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        stopAnimator();
      } else {
        stopSimpleAnimation();
      }
    }
  }

  private void stopAnimator () {
    try {
      Views.clearAnimations(this);
    } catch (Throwable throwable) {
      Log.e("Cannot cancel pending animator on SpinnerView", throwable);
    }
  }

  private void stopSimpleAnimation () {
    if (animation != null) {
      try {
        clearAnimation();
        animation.cancel();
        animation = null;
      } catch (Throwable throwable) {
        Log.e("Cannot cancel simple animation in SpinnerView", throwable);
      }
    }
  }

  @Override
  public void onAttachedToWindow () {
    super.onAttachedToWindow();
    postDelayed(this, 200L);
  }

  @Override
  public void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    stop();
  }

  @Override
  public void setVisibility (int visibility) {
    super.setVisibility(visibility);
    if (visibility == View.GONE)
      stop();
    else if (visibility == View.VISIBLE)
      start();
  }

  @Override
  public void run () {
    start();
  }
}
