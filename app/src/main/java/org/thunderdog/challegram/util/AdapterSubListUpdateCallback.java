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