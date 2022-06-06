/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/10/2018
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.GridLayoutManager;

import org.thunderdog.challegram.core.Lang;

public class RtlGridLayoutManager extends GridLayoutManager {
  private boolean alignOnly;

  public RtlGridLayoutManager (Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public RtlGridLayoutManager (Context context, int spanCount) {
    super(context, spanCount);
  }

  public RtlGridLayoutManager (Context context, int spanCount, int orientation, boolean reverseLayout) {
    super(context, spanCount, orientation, reverseLayout);
  }

  public RtlGridLayoutManager setAlignOnly (boolean alignOnly) {
    this.alignOnly = alignOnly;
    return this;
  }

  @Override
  protected final boolean isLayoutRTL () {
    return !alignOnly && Lang.rtl();
  }
}
