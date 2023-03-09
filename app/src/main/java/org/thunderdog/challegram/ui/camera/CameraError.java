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
 * File created on 21/09/2017
 */
package org.thunderdog.challegram.ui.camera;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class CameraError {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({NOT_ENOUGH_SPACE})
  public @interface Code {}

  /**
   * Not enough storage space. Offer user to free some space.
   * */
  public static final int NOT_ENOUGH_SPACE = -1;
}
