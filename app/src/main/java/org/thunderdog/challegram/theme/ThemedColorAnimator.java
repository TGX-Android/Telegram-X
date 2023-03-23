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
 * File created on 23/03/2023
 */
package org.thunderdog.challegram.theme;

import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import me.vkryl.android.animator.ColorAnimator;

public class ThemedColorAnimator extends ColorAnimator {
  private static FutureColor futureForColorId (@ThemeColorId int colorId, @Nullable ThemeDelegate forcedTheme) {
    if (colorId == ThemeColorId.NONE)
      throw new IllegalArgumentException();
    if (forcedTheme != null) {
      return () ->
        forcedTheme.getColor(colorId);
    } else {
      return () ->
        Theme.getColor(colorId);
    }
  }

  public ThemedColorAnimator (Target<FutureColor> target, Interpolator interpolator, long duration, @ThemeColorId int startColorId) {
    this(target, interpolator, duration, startColorId, null);
  }

  public ThemedColorAnimator (Target<FutureColor> target, Interpolator interpolator, long duration, @ThemeColorId int startColorId, @Nullable ThemeDelegate forcedTheme) {
    super(target, interpolator, duration, futureForColorId(startColorId, forcedTheme));
  }

  public void setValue (@ThemeColorId int colorId, boolean animated) {
    setValue(colorId, null, animated);
  }

  public void setValue (@ThemeColorId int colorId, @Nullable ThemeDelegate forcedTheme, boolean animated) {
    setValue(futureForColorId(colorId, forcedTheme), animated);
  }
}
