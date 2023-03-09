/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 15/12/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;

import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class ScalableTextView extends NoScrollTextView implements FactorAnimator.Target {
  public ScalableTextView (Context context) {
    super(context);
  }

  // Change

  private FactorAnimator changeAnimator;

  private CharSequence futureReplacement;

  public void replaceText (CharSequence text) {
    if (changeAnimator == null) {
      changeAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else {
      if (factor > .5f && factor != 1f) {
        if (futureReplacement != null && futureReplacement.equals(text)) {
          return;
        }
        this.factor = 1f - factor;
        changeAnimator.forceFactor(factor);
      } else {
        changeAnimator.forceFactor(0f);
      }
    }
    futureReplacement = text;
    changeAnimator.animateTo(1f);
  }

  private float factor;

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (factor >= .5f && futureReplacement != null) {
        Views.setMediumText(this, futureReplacement);
        futureReplacement = null;
      }
      float alpha = (factor <= .5f ? 1f - factor / .5f : (factor - .5f) / .5f);
      float scale = .6f + .4f * alpha;
      setScaleX(scale);
      setScaleY(scale);
      setAlpha(alpha);
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
