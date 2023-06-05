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
 * File created on 15/08/2017
 */
package org.thunderdog.challegram.widget;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Paints;

import java.util.ArrayList;

public class FillingDecoration extends RecyclerView.ItemDecoration {
  private final ArrayList<int[]> ranges = new ArrayList<>();

  private @ColorId int fillingColorId = ColorId.filling;

  public FillingDecoration (RecyclerView view, @Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
  }

  public FillingDecoration addRange (int fromIndex, int toIndex) {
    ranges.add(new int[] {fromIndex, toIndex});
    return this;
  }

  public int[] removeLastRange () {
    return ranges.remove(ranges.size() - 1);
  }

  public FillingDecoration clearRanges () {
    ranges.clear();
    return this;
  }

  public int[] rangeAt (int index) {
    return ranges.get(index);
  }

  public int[] lastRange () {
    return ranges.get(ranges.size() - 1);
  }

  private int bottomId = View.NO_ID;
  private int maxIndex = -1;

  public FillingDecoration addBottom (int bottomId, int maxIndex) {
    this.bottomId = bottomId;
    this.maxIndex = maxIndex;
    return this;
  }

  @Override
  public final void onDraw (Canvas c, RecyclerView parent, @NonNull RecyclerView.State state) {
    LinearLayoutManager manager = (LinearLayoutManager) parent.getLayoutManager();
    int first = manager.findFirstVisibleItemPosition();
    int last = manager.findLastVisibleItemPosition();

    if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
      return;
    }

    int fillingTop = -1, fillingBottom = -1;

    int openFillingColor = 0;

    boolean searchById = bottomId != View.NO_ID && maxIndex != -1;
    boolean bottomFound = false;
    View view = null;
    for (int position = 0; position <= last; position++) {
      view = manager.findViewByPosition(position);

      int fillingColor = view != null ? getFillingColor(position, view) : 0;
      if (fillingColor == 0 && view == null && (searchById && position < maxIndex && !bottomFound)) {
        fillingColor = Theme.getColor(this.fillingColorId);
      }

      if (searchById && fillingColor != 0 && view != null && view.getId() == bottomId) {
        fillingColor = 0;
        bottomFound = true;
      }

      if (fillingColor != openFillingColor) {
        if (openFillingColor != 0) {
          drawFilling(c, parent, view, fillingTop, fillingBottom, openFillingColor);
        }
        if (fillingColor != 0) {
          fillingTop = view != null ? (int) (manager.getDecoratedTop(view) + view.getTranslationY()) : fillingBottom;
        }
        openFillingColor = fillingColor;
      }
      if (fillingColor != 0) {
        fillingBottom = view != null ? (int) (manager.getDecoratedBottom(view) + view.getTranslationY()) : parent.getMeasuredHeight();
      }
    }

    if (openFillingColor != 0) {
      drawFilling(c, parent, view, fillingTop, fillingBottom, openFillingColor);
    }
    if (needSeparateDecorations()) {
      for (int i = 0; i < parent.getChildCount(); i++) {
        view = parent.getChildAt(i);
        int adapterPosition = view != null ? parent.getChildAdapterPosition(view) : RecyclerView.NO_POSITION;
        if (view == null || adapterPosition != RecyclerView.NO_POSITION)
          continue;
        int fillingColor = getFillingColor(adapterPosition, view);
        if (fillingColor != 0) {
          drawFilling(c, parent, view, manager.getDecoratedTop(view) + (int) view.getTranslationY(), manager.getDecoratedBottom(view) + (int) view.getTranslationY(), fillingColor);
        }
        drawDecorationForView(c, parent, state, view);
      }
      for (int i = first; i <= last; i++) {
        view = manager.findViewByPosition(i);
        if (view != null) {
          drawDecorationForView(c, parent, state, view);
        }
      }
    }
  }

  private void drawFilling (Canvas c, RecyclerView parent, View view, int fillingTop, int fillingBottom, int fillingColor) {
    if (view instanceof ShadowView) {
      fillingBottom += ((ShadowView) view).getShadowTop();
    }
    c.drawRect(0, Math.max(0, fillingTop), parent.getMeasuredWidth(), Math.min(parent.getMeasuredHeight(), fillingBottom), Paints.fillingPaint(fillingColor));
  }

  protected boolean needSeparateDecorations () {
    return false;
  }

  protected void drawDecorationForView (Canvas c, RecyclerView parent, RecyclerView.State state, @NonNull View view) {
    // Override
  }

  protected int getFillingColor (int i, @NonNull View view) {
    for (int[] range : ranges) {
      if (i >= range[0] && i < range[1]) {
        return Theme.getColor(fillingColorId);
      }
    }
    return 0;
  }
}
