package org.thunderdog.challegram.util;

import androidx.recyclerview.widget.DiffUtil;

import org.thunderdog.challegram.ui.ListItem;

import java.util.List;

public abstract class ListItemDiffUtilCallback extends DiffUtil.Callback {
  private final List<ListItem> oldList;
  private final List<ListItem> newList;

  public ListItemDiffUtilCallback (List<ListItem> oldList, List<ListItem> newList) {
    this.oldList = oldList;
    this.newList = newList;
  }

  @Override
  public final int getOldListSize () {
    return oldList.size();
  }

  @Override
  public final int getNewListSize () {
    return newList.size();
  }

  @Override
  public final boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
    ListItem oldItem = oldList.get(oldItemPosition);
    ListItem newItem = newList.get(newItemPosition);
    return areItemsTheSame(oldItem, newItem);
  }

  @Override
  public final boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
    ListItem oldItem = oldList.get(oldItemPosition);
    ListItem newItem = newList.get(newItemPosition);
    return areContentsTheSame(oldItem, newItem);
  }

  public abstract boolean areItemsTheSame (ListItem oldItem, ListItem newItem);
  public abstract boolean areContentsTheSame (ListItem oldItem, ListItem newItem);
}
