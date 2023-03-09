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
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.util.DrawableProvider;

public class SparseDrawableView extends View implements DrawableProvider {
  public SparseDrawableView (Context context) {
    super(context);
  }

  public SparseDrawableView (Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public SparseDrawableView (Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  // Sparse drawable function
  private SparseArrayCompat<Drawable> sparseDrawables;
  @Override
  public final SparseArrayCompat<Drawable> getSparseDrawableHolder () { return (sparseDrawables != null ? sparseDrawables : (sparseDrawables = new SparseArrayCompat<>())); }
  @Override
  public final Resources getSparseDrawableResources () { return getResources(); }
}
