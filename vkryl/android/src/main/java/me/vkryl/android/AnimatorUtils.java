package me.vkryl.android;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import me.vkryl.android.animator.Animated;

public final class AnimatorUtils {
  public static final AnticipateOvershootInterpolator ANTICIPATE_OVERSHOOT_INTERPOLATOR = new AnticipateOvershootInterpolator();
  public static final DecelerateInterpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();
  public static final AccelerateInterpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();
  public static final DecelerateInterpolator NAVIGATION_INTERPOLATOR = new DecelerateInterpolator(1.78f);
  // public static final DecelerateInterpolator SLOW_DECELERATE_INTERPOLATOR = new DecelerateInterpolator(.72f);
  public static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
  public static final OvershootInterpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(3.2f);
  public static final AccelerateDecelerateInterpolator ACCELERATE_DECELERATE_INTERPOLATOR = new AccelerateDecelerateInterpolator();

  public static ValueAnimator simpleValueAnimator () {
    return ValueAnimator.ofFloat(0f, 1f);
  }

  public static float getFraction (ValueAnimator animator) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
      return animator.getAnimatedFraction();
    } else {
      return (Float) animator.getAnimatedValue();
    }
  }

  public static void startAnimator (final View view, final Animator animator) {
    startAnimator(view, animator, false);
  }

  public static void startAnimator (final View view, final Animator animator, boolean forceOnLayout) {
    if (view == null) {
      throw new IllegalArgumentException("view must be not null");
    }
    if (animator == null) {
      throw new IllegalArgumentException("animator must be not null");
    }
    if (view.getMeasuredWidth() != 0 && view.getMeasuredHeight() != 0 && !forceOnLayout) {
      animator.start();
    } else if (view instanceof Animated) {
      ((Animated) view).runOnceViewBecomesReady(view, animator::start);
    } else {
      view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange (View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
          view.removeOnLayoutChangeListener(this);
          animator.start();
        }
      });
    }
  }
}
