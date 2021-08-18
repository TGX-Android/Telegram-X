package org.thunderdog.challegram.mediaview;

import me.vkryl.core.MathUtils;

/**
 * Date: 10/5/17
 * Author: default
 */

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
