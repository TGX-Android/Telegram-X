package org.thunderdog.challegram.component.attach;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;

import me.vkryl.core.ColorUtils;

/**
 * Date: 21/10/2016
 * Author: default
 */

public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
  private int spanCount;
  private int spacing;
  private boolean includeEdge;
  private boolean needVertical;
  private boolean enableRtl;

  public GridSpacingItemDecoration (int spanCount, int spacing, boolean includeEdge, boolean needVertical, boolean enableRtl) {
    this.spanCount = spanCount;
    this.spacing = spacing;
    this.includeEdge = includeEdge;
    this.needVertical = needVertical;
    this.enableRtl = enableRtl;
  }

  public void setSpanCount (int spanCount) {
    if (this.spanCount != spanCount) {
      this.spanCount = spanCount;
    }
  }

  private boolean needDraw;
  private int viewType, colorId;

  public void setNeedDraw (boolean needDraw, int viewType) {
    this.needDraw = needDraw;
    this.viewType = viewType;
  }

  public void setDrawColorId (@ThemeColorId int colorId) {
    this.colorId = colorId;
  }

  @Override
  public void onDraw (Canvas c, RecyclerView parent, RecyclerView.State state) {
    if (needDraw) {
      LinearLayoutManager manager = (LinearLayoutManager) parent.getLayoutManager();
      final int firstVisiblePosition = manager.findFirstVisibleItemPosition();
      final int lastVisiblePosition = manager.findLastVisibleItemPosition();

      if (firstVisiblePosition == -1 || lastVisiblePosition == -1) {
        return;
      }

      final int color = Theme.getColor(colorId);
      float maxAlpha = 0f;
      final int parentWidth = parent.getMeasuredWidth();
      boolean foundTop = false;
      int currentTop = 0;
      View lastView = null;
      for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
        View view = manager.findViewByPosition(i);
        lastView = view;
        RecyclerView.ViewHolder holder = parent.getChildViewHolder(view);
        if (holder == null) {
          continue;
        }
        if (holder.getItemViewType() == viewType) {
          if (!foundTop) {
            currentTop = view.getTop() - manager.getTopDecorationHeight(view);
            foundTop = true;
          }
          maxAlpha = Math.max(view.getAlpha(), maxAlpha);
        } else if (foundTop) {
          int viewTop = view.getTop();
          if (currentTop != viewTop && maxAlpha > 0f) {
            int drawColor = ColorUtils.color((int) ((float) Color.alpha(color) * maxAlpha), color);
            c.drawRect(0, currentTop, parentWidth, view.getTop(), Paints.fillingPaint(drawColor));
          }
          currentTop = 0;
          foundTop = false;
          maxAlpha = 0f;
        }
      }
      int viewBottom = lastView != null ? lastView.getTop() + lastView.getMeasuredHeight() + manager.getBottomDecorationHeight(lastView) : 0;
      if (foundTop && currentTop != viewBottom && maxAlpha != 0f) {
        int drawColor = ColorUtils.color((int) ((float) Color.alpha(color) * maxAlpha), color);
        c.drawRect(0, currentTop, parentWidth, viewBottom, Paints.fillingPaint(drawColor));
      }
    }
  }

  private GridLayoutManager.SpanSizeLookup lookup;

  public void setSpanSizeLookup (GridLayoutManager.SpanSizeLookup lookup) {
    this.lookup = lookup;
  }

  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
    final int adapterPosition = parent.getChildAdapterPosition(view); // item position
    int position = adapterPosition;
    int itemCount = parent.getAdapter().getItemCount();
    int column;

    if (lookup != null) {
      int spanSize = lookup.getSpanSize(position);

      if (spanSize != 1) {
        outRect.setEmpty();
        return;
      }

      column = lookup.getSpanIndex(position, spanCount);

      int i = position - 1;
      while (i >= 0 && lookup.getSpanSize(i++) != 1) {
        position--;
        if (position < 0 || i == itemCount) {
          outRect.setEmpty();
          return;
        }
      }
    } else {
      column = position % spanCount; // item column
    }

    if (enableRtl && Lang.rtl()) {
      column = spanCount - column - 1;
    }

    if (includeEdge) {
      outRect.left = spacing - column * spacing / spanCount; // spacing - column * ((1f / spanCount) * spacing)
      outRect.right = (column + 1) * spacing / spanCount; // (column + 1) * ((1f / spanCount) * spacing)
      if (needVertical) {
        if (position < spanCount) { // top edge
          outRect.top = spacing;
        }
        outRect.bottom = spacing; // item bottom
      } else if (lookup != null) {
        if (true) {
          outRect.top = 0;
        } else {
          if (adapterPosition > spanCount) {
            outRect.top = 0;
          } else {
            int topEndIndex = 0;
            while (topEndIndex < spanCount && lookup.getSpanSize(topEndIndex) == 1) {
              topEndIndex++;
            }
            outRect.top = adapterPosition < topEndIndex ? spacing : 0;
          }
        }
        int adapterItemCount = parent.getAdapter().getItemCount();
        if (adapterPosition < adapterItemCount - spanCount) {
          outRect.bottom = 0;
        } else {
          int bottomEndIndex = adapterItemCount - 1;
          while (bottomEndIndex > adapterItemCount - spanCount && lookup.getSpanSize(bottomEndIndex) == 0) {
            bottomEndIndex--;
          }
          outRect.bottom = adapterPosition >= bottomEndIndex ? spacing : 0;
        }
      }
    } else {
      outRect.left = column * spacing / spanCount; // column * ((1f / spanCount) * spacing)
      outRect.right = spacing - (column + 1) * spacing / spanCount; // spacing - (column + 1) * ((1f /    spanCount) * spacing)
      if (needVertical) {
        if (position >= spanCount || lookup != null) {
          outRect.top = spacing; // item top
        }
        if (lookup != null) {
          if (lookup.getSpanSize(Math.min(itemCount - 1, adapterPosition + (spanCount - column))) != 1) {
            outRect.bottom = spacing;
          } else {
            outRect.bottom = 0;
          }
        }
      } else {
        outRect.top = outRect.bottom = 0;
      }
    }
  }
}
