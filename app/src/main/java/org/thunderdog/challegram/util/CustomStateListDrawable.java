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
 * File created on 06/08/2017
 */
package org.thunderdog.challegram.util;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import java.util.ArrayList;

public class CustomStateListDrawable extends StateListDrawable {
  private final ArrayList<Drawable> drawableList = new ArrayList<>();

  @Override
  public void addState (int[] stateSet, Drawable drawable) {
    super.addState(stateSet, drawable);
    if (!drawableList.contains(drawable)) {
      drawableList.add(drawable);
    }
  }

  public ArrayList<Drawable> getDrawableList () {
    return drawableList;
  }
}
