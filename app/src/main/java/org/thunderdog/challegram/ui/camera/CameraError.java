package org.thunderdog.challegram.ui.camera;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Date: 9/21/17
 * Author: default
 */

public class CameraError {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({NOT_ENOUGH_SPACE})
  public @interface Code {}

  /**
   * Not enough storage space. Offer user to free some space.
   * */
  public static final int NOT_ENOUGH_SPACE = -1;
}
