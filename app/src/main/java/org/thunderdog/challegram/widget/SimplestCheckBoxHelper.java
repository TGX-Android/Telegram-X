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

import androidx.annotation.Nullable;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;

public class SimplestCheckBoxHelper implements FactorAnimator.Target {
  private final ViewProvider viewProvider;
  private final @Nullable Listener listener;

  private float factor;
  private FactorAnimator animator;
  private boolean isChecked;

  public interface Listener {
    void onCheckFactorChanged (float factor);
  }

  public SimplestCheckBoxHelper (View target) {
    this(new SingleViewProvider(target), target instanceof Listener ? (Listener) target : null);
  }

  public SimplestCheckBoxHelper (ViewProvider viewProvider) {
    this(viewProvider, null);
  }

  public SimplestCheckBoxHelper (ViewProvider viewProvider, @Nullable Listener listener) {
    this.viewProvider = viewProvider;
    this.listener = listener;
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
      viewProvider.invalidate();
      if (listener != null) {
        listener.onCheckFactorChanged(factor);
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setCheckFactor(factor);
  }
}
