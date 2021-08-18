/**
 * File created on 03/02/16 at 10:30
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import androidx.annotation.FloatRange;

public interface StretchyHeaderView {
  void setScaleFactor (@FloatRange(from = 0.0, to = 1.0) float scaleFactor, float fromFactor, @FloatRange(from = 0.0, to = 1.0) float toFactor, boolean byScroll);

  /*
  Simple translation example:

  private float scaleFactor;

  @Override
  public float getScaleFactor () {
    return scaleFactor;
  }

  @Override
  public void setScaleFactor (float scaleFactor) {
    if (this.scaleFactor != scaleFactor) {
      this.scaleFactor = scaleFactor;
      setTranslationY(-Size.HEADER_SIZE_DIFFERENCE * (1f - scaleFactor));
    }
  }

  * */
}
