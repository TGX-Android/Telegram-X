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
 * File created on 04/06/2018
 */
package org.thunderdog.challegram.util;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;

public interface DrawableProvider {
  SparseArrayCompat<Drawable> getSparseDrawableHolder ();
  Resources getSparseDrawableResources ();

  default Drawable getSparseDrawable (@DrawableRes int res, @ThemeColorId int knownThemeColorId) {
    if (res == 0)
      return null;
    /*Drawable drawable = knownThemeColorId != 0 ? Icons.getSparseDrawable(res, knownThemeColorId) : null;
    if (drawable != null)
      return drawable;*/
    SparseArrayCompat<Drawable> array = getSparseDrawableHolder();
    Drawable drawable = array.get(res);
    if (drawable == null) {
      drawable = Drawables.get(getSparseDrawableResources(), res);
      array.put(res, drawable);
    }
    return drawable;
  }
}
