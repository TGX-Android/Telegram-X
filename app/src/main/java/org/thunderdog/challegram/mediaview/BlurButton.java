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
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.util.ColorChanger;

public class BlurButton extends NoScrollTextView implements FactorAnimator.Target {
  private static final ColorChanger changer = new ColorChanger(0x77ffffff, 0xffffffff);

  public BlurButton (Context context) {
    super(context);
    Views.setClickable(this);
    setGravity(Gravity.CENTER);
    setSingleLine(true);
    setTypeface(Fonts.getRobotoMedium());
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    setTextColor(changer.getColor(0f));
  }

  private boolean isChecked;

  public void setIsChecked (boolean isChecked, boolean animated) {
    if (this.isChecked != isChecked) {
      this.isChecked = isChecked;
      if (animated) {
        animateFactor(isChecked ? 1f : 0f);
      } else {
        forceFactor(isChecked ? 1f : 0f);
      }
    }
  }

  private float factor;
  private FactorAnimator animator;

  private void animateFactor (float factor) {
    if (animator == null) {
      if (this.factor == factor) {
        return;
      }
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
    }
    animator.animateTo(factor);
  }

  private void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
  }

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      setTextColor(changer.getColor(factor));
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setFactor(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

  }
}
