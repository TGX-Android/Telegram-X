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
 * File created on 10/05/2017
 */
package org.thunderdog.challegram.mediaview;

import me.vkryl.core.MathUtils;

public class PostTransformation {
  public PostTransformation () { }

  // Common

  public boolean isEmpty () {
    return rotationAroundCenter == 0;
  }

  // Rotation Around Center. Video only

  private float rotationAroundCenter;

  public float rotateAroundCenter () {
    return (rotationAroundCenter = MathUtils.modulo(rotationAroundCenter - 90, 360));
  }

  public float getRotationAroundCenter () {
    return rotationAroundCenter;
  }
}
