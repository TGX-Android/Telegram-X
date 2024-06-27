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
 * File created on 12/06/2024
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.content.Context;
import android.widget.LinearLayout;

import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;

public abstract class MenuMoreWrapAbstract extends LinearLayout {
  public MenuMoreWrapAbstract (Context context) {
    super(context);
  }

  public abstract int getItemsWidth ();

  public abstract int getItemsHeight ();

  public int getAnchorMode () {
    return MenuMoreWrap.ANCHOR_MODE_RIGHT;
  }

  public boolean shouldPivotBottom () {
    return false;
  }

  public float getRevealRadius () {
    return (float) Math.hypot(getItemsWidth(), getItemsHeight());
  }

  public void scaleIn (Animator.AnimatorListener listener) {
    Views.animate(this, 1f, 1f, 1f, 135L, 10L, AnimatorUtils.DECELERATE_INTERPOLATOR, listener);
  }

  public void scaleOut (Animator.AnimatorListener listener) {
    Views.animate(this, MenuMoreWrap.START_SCALE, MenuMoreWrap.START_SCALE, 0f, 120L, 0L, AnimatorUtils.ACCELERATE_INTERPOLATOR, listener);
  }
}
