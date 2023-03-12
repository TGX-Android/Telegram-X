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
 * File created on 19/07/2017
 */
package org.thunderdog.challegram.widget;

import android.view.View;

import org.thunderdog.challegram.loader.Receiver;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class SimplestCheckBoxHelper implements FactorAnimator.Target {
  private final View target;
  private final Receiver receiver;

  private float factor;
  private FactorAnimator animator;
  private boolean isChecked;

  public interface Listener {
    void onCheckFactorChanged (float factor);
  }

  public SimplestCheckBoxHelper (View target, Receiver receiver) {
    this.target = target;
    this.receiver = receiver;
  }

  public boolean isChecked () {
    return isChecked;
  }

  public float getCheckFactor () {
    return factor;
  }

  public void setIsChecked (boolean isChecked, boolean isAnimated) {
    if (this.isChecked != isChecked) {
      this.isChecked = isChecked;
      final float toFactor = isChecked ? 1f : 0f;
      if (isAnimated) {
        if (animator == null) {
          animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, factor);
        }
        animator.animateTo(toFactor);
      } else {
        if (animator != null) {
          animator.forceFactor(toFactor);
        }
        setCheckFactor(toFactor);
      }
    }
  }

  private void setCheckFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      target.invalidate();
      if (target instanceof Listener) {
        ((Listener) target).onCheckFactorChanged(factor);
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setCheckFactor(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }
}
