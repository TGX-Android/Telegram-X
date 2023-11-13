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
 * File created on 06/01/2023
 */
package org.thunderdog.challegram.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

public class AdapterSubListUpdateCallback implements ListUpdateCallback {
  private final RecyclerView.Adapter<?> adapter;
  private final int fromIndex;

  public AdapterSubListUpdateCallback (@NonNull RecyclerView.Adapter<?> adapter, int fromIndex) {
    this.adapter = adapter;
    this.fromIndex = fromIndex;
  }

  @Override
  public void onInserted (int position, int count) {
    adapter.notifyItemRangeInserted(position + fromIndex, count);
  }

  @Override
  public void onRemoved (int position, int count) {
    adapter.notifyItemRangeRemoved(position + fromIndex, count);
  }

  @Override
  public void onMoved (int fromPosition, int toPosition) {
    adapter.notifyItemMoved(fromPosition + fromIndex, toPosition + fromIndex);
  }

  @Override
  public void onChanged (int position, int count, @Nullable Object payload) {
    adapter.notifyItemRangeChanged(position + fromIndex, count, payload);
  }
}