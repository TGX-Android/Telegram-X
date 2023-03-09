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
 * File created on 23/04/2015 at 17:11
 */
package org.thunderdog.challegram.tool;

import android.os.Build;

import me.vkryl.core.MathUtils;

public class Anim {
  public static final boolean ANIMATORS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;

  /*public static final Interpolator QUINTIC_INTERPOLATOR = new Interpolator() {
    @Override
    public float getInterpolation (float t) {
      t -= 1.0f;
      return t * t * t * t * t + 1.0f;
    }
  };*/

  public static float anticipateRange (float factor) {
    return MathUtils.clamp(factor, -.2f, 1f);
  }
}
