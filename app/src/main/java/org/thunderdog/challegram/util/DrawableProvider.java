package org.thunderdog.challegram.util;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;

/**
 * Date: 6/4/18
 * Author: default
 */
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
