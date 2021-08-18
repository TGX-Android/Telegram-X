package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.Gravity;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

/**
 * Date: 8/25/17
 * Author: default
 */

public class DoneButton extends CircleButton {
  private final FactorAnimator.Target target;

  private static final int ANIMATOR_VISIBILITY = 0;

  private float maximumAlpha = 1f;

  public DoneButton (Context context) {
    super(context);

    init(R.drawable.baseline_check_24, 56f, 4f, R.id.theme_color_circleButtonRegular, R.id.theme_color_circleButtonRegularIcon);

    int padding = Screen.dp(4f);
    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;
    setLayoutParams(params);

    setAlpha(0f);
    setScaleX(.6f);
    setScaleY(.6f);

    target = new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        switch (id) {
          case ANIMATOR_VISIBILITY:
            setVisibilityFactor(factor);
            break;
        }
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

      }
    };
  }

  private boolean isVisible;
  private float visibilityFactor;
  private FactorAnimator visibilityAnimator;

  public boolean getIsVisible () {
    return isVisible;
  }

  public void setIsVisible (boolean isVisible, boolean animated) {
    if (this.isVisible != isVisible) {
      this.isVisible = isVisible;

      final float toFactor = isVisible ? 1f : 0f;

      if (animated && maximumAlpha > 0f) {
        if (visibilityAnimator == null) {
          visibilityAnimator = new FactorAnimator(ANIMATOR_VISIBILITY, target, AnimatorUtils.OVERSHOOT_INTERPOLATOR, 210l, visibilityFactor);
        }
        if (toFactor == 1f && visibilityFactor == 0f) {
          visibilityAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          visibilityAnimator.setDuration(210l);
        } else {
          visibilityAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          visibilityAnimator.setDuration(100l);
        }
        visibilityAnimator.animateTo(toFactor);
      } else {
        if (visibilityAnimator != null) {
          visibilityAnimator.forceFactor(toFactor);
        }
        setVisibilityFactor(toFactor);
      }
    }
  }

  protected void setVisibilityFactor (float factor) {
    if (this.visibilityFactor != factor) {
      this.visibilityFactor = factor;
      float scale = .6f + .4f * factor;
      setScaleX(scale);
      setScaleY(scale);
      updateAlpha();
    }
  }

  public void setMaximumAlpha (float alpha) {
    if (this.maximumAlpha != alpha) {
      this.maximumAlpha = alpha;
      updateAlpha();
    }
  }

  private void updateAlpha () {
    setAlpha(MathUtils.clamp(visibilityFactor) * MathUtils.clamp(maximumAlpha));
  }
}
