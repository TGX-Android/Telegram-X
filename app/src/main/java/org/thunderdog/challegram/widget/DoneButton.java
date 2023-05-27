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
 * File created on 25/08/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.Gravity;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class DoneButton extends CircleButton {
  private final FactorAnimator.Target target;

  private static final int ANIMATOR_VISIBILITY = 0;

  private float maximumAlpha = 1f;

  public DoneButton (Context context) {
    super(context);

    init(R.drawable.baseline_check_24, 56f, 4f, ColorId.circleButtonRegular, ColorId.circleButtonRegularIcon);

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
