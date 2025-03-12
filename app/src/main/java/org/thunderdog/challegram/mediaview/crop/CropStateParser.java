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
 * File created on 25/02/2024
 */
package org.thunderdog.challegram.mediaview.crop;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;

import me.vkryl.core.StringUtils;

public class CropStateParser {
  private CropStateParser () { }

  @Nullable
  public static CropState parse (String in) {
    if (StringUtils.isEmpty(in)) {
      return null;
    }
    try {
      String[] data = in.split(":");
      if (data.length < 6 || data.length > 7) {
        throw new IllegalArgumentException("data.length < 6 || data.length > 7 (" + data.length + ", " + in + ")");
      }
      double left = Double.parseDouble(data[0]);
      double top = Double.parseDouble(data[1]);
      double right = Double.parseDouble(data[2]);
      double bottom = Double.parseDouble(data[3]);
      int rotateBy = Integer.parseInt(data[4]);
      float degreesAroundCenter = Float.parseFloat(data[5]);
      int flags = data.length > 6 ? Integer.parseInt(data[6]) : 0;
      return new CropState(left, top, right, bottom, rotateBy, degreesAroundCenter, flags);
    } catch (Throwable t) {
      Log.e(t);
    }
    return null;
  }

  @NonNull
  public static String toParsableString (@NonNull CropState cropState) {
    return String.valueOf(cropState.getLeft()) +
      ':' +
      cropState.getTop() +
      ':' +
      cropState.getRight() +
      ':' +
      cropState.getBottom() +
      ':' +
      cropState.getRotateBy() +
      ':' +
      cropState.getDegreesAroundCenter() +
      (cropState.getFlags() != 0 ? ":" + cropState.getFlags() : "");
  }
}
