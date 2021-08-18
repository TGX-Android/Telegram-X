/**
 * File created on 23/04/15 at 17:11
 * Copyright Vyacheslav Krylov, 2014
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
