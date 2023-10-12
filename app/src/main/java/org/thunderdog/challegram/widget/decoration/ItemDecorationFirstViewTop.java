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
 * File created on 27/09/2023
 */
package org.thunderdog.challegram.widget.decoration;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.ScrollJumpCompensator;

public class ItemDecorationFirstViewTop extends RecyclerView.ItemDecoration {
  private final RecyclerView recyclerView;
  private final LinearLayoutManager linearLayoutManager;
  private final RecyclerView.OnScrollListener scrollListener;
  private final Callback callback;

  private boolean isScheduledDecorationOffsetDisable;
  private boolean isDecorationOffsetDisabled;
  private int lastTopDecorationOffset;

  public interface Callback {
    int getTopDecorationOffset ();
  }

  public static ItemDecorationFirstViewTop attach (RecyclerView recyclerView, Callback callback) {
    ItemDecorationFirstViewTop decoration = new ItemDecorationFirstViewTop(recyclerView, (LinearLayoutManager) recyclerView.getLayoutManager(), callback);
    recyclerView.addItemDecoration(decoration);
    recyclerView.addOnScrollListener(decoration.scrollListener);
    return decoration;
  }

  public void scheduleDisableDecorationOffset () {
    this.isScheduledDecorationOffsetDisable = true;
    checkCanDisableDecorationOffset();
  }

  public void enableDecorationOffset () {
    setDecorationOffsetDisabled(false);
  }

  /* * */

  private ItemDecorationFirstViewTop (RecyclerView recyclerView, LinearLayoutManager linearLayoutManager, Callback callback) {
    this.recyclerView = recyclerView;
    this.linearLayoutManager = linearLayoutManager;
    this.scrollListener = new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          UI.post(() -> checkCanDisableDecorationOffset());
        }
      }
    };
    this.callback = callback;
  }

  private void checkCanDisableDecorationOffset () {
    if (isScheduledDecorationOffsetDisable && !isDecorationOffsetDisabled) {
      View v = linearLayoutManager.findViewByPosition(0);
      if (v == null || v.getTop() <= 0) {
        setDecorationOffsetDisabled(true);
      } else {
        recyclerView.smoothScrollBy(0, v.getTop());
      }
    }
  }

  private void setDecorationOffsetDisabled (boolean disabled) {
    if (isDecorationOffsetDisabled == disabled) return;
    isDecorationOffsetDisabled = disabled;
    isScheduledDecorationOffsetDisable &= disabled;

    final int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
    final int offset = lastTopDecorationOffset * (disabled ? -1 : 1);

    // Changing the height of the first view can be animated, this leads to unwanted behavior.
    // Temporarily disable animation.

    final RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
    recyclerView.setItemAnimator(null);

    recyclerView.invalidateItemDecorations();
    if (firstVisibleItemPosition == 0 && offset != 0) {
      ScrollJumpCompensator.compensate(recyclerView, offset);
    }

    if (itemAnimator != null) {
      UI.post(() -> {
        if (recyclerView.getItemAnimator() == null) {
          recyclerView.setItemAnimator(itemAnimator);
        }
      });
    }
  }

  @Override
  public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    final int position = parent.getChildAdapterPosition(view);
    final boolean isUnknown = position == RecyclerView.NO_POSITION;
    int top = 0;
    if (position == 0 || isUnknown) {
      top = lastTopDecorationOffset = callback.getTopDecorationOffset();
    }

    outRect.set(0, isDecorationOffsetDisabled ? 0 : top, 0, 0);
  }
}
