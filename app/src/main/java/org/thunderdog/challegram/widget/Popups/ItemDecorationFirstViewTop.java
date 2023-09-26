package org.thunderdog.challegram.widget.Popups;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    checkCanDisableDecorationOffset(true);
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
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        checkCanDisableDecorationOffset(false);
      }

      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          checkCanDisableDecorationOffset(true);
        }
      }
    };
    this.callback = callback;
  }

  private void checkCanDisableDecorationOffset (boolean canScroll) {
    if (isScheduledDecorationOffsetDisable && !isDecorationOffsetDisabled) {
      View v = linearLayoutManager.findViewByPosition(0);
      if (v == null || v.getTop() <= 0) {
        setDecorationOffsetDisabled(true);
      } else if (canScroll) {
        recyclerView.smoothScrollBy(0, v.getTop());
      }
    }
  }

  private void setDecorationOffsetDisabled (boolean disabled) {
    if (isDecorationOffsetDisabled == disabled) return;
    isDecorationOffsetDisabled = disabled;
    isScheduledDecorationOffsetDisable &= disabled;

    recyclerView.invalidateItemDecorations();
    if (linearLayoutManager.findFirstVisibleItemPosition() == 0) {
      int offset = lastTopDecorationOffset * (disabled ? -1 : 1);
      if (offset != 0) {
        ScrollJumpCompensator.compensate(recyclerView, offset);
      }
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
