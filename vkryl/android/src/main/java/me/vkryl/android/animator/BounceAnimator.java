package me.vkryl.android.animator;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.util.ViewProvider;

/**
 * Date: 10/04/2019
 * Author: default
 */
public final class BounceAnimator {
  private final BoolAnimator animator;

  public BounceAnimator (ViewProvider provider) {
    this.animator = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        provider.invalidate();
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        provider.invalidate();
      }
    }, AnimatorUtils.OVERSHOOT_INTERPOLATOR, 210l);
  }

  public BounceAnimator (FactorAnimator.Target target) {
    this.animator = new BoolAnimator(0, target, AnimatorUtils.OVERSHOOT_INTERPOLATOR, 210l);
  }

  public void setValue (boolean value, boolean animated) {
    if (animated) {
      if (value && animator.getFloatValue() == 0f) {
        animator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
        animator.setDuration(210l);
      } else {
        animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        animator.setDuration(100l);
      }
    }
    animator.setValue(value, animated);
  }

  public float getFloatValue () {
    return animator.getFloatValue();
  }

  public boolean getValue () {
    return animator.getValue();
  }
}
