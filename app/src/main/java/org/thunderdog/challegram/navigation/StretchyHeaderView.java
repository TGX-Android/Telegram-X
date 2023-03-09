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
 * File created on 03/02/2016 at 10:30
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
