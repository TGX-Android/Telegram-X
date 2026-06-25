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
import android.graphics.Color;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;

import java.util.ArrayList;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.collection.IntSet;

public class FillingDecoration extends RecyclerView.ItemDecoration {
  private final ArrayList<int[]> ranges = new ArrayList<>();
  private final IntSet ids = new IntSet();

  private @ColorId int fillingColorId = ColorId.filling;

  public FillingDecoration (RecyclerView view, @Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
  }

  public FillingDecoration addId (@IdRes int id) {
    ids.add(id);
    return this;
  }

  public FillingDecoration removeId (@IdRes int id) {
    ids.remove(id);
    return this;
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

  public int[] firstRange () {
    return ranges.get(0);
  }

  public int[] lastRange () {
    return ranges.get(ranges.size() - 1);
  }

  public void offsetLastRange (int by) {
    int[] range = lastRange();
    range[0] += by;
    range[1] += by;
  }

  public int rangesCount () {
    return ranges.size();
  }

  @Override
  public final void onDraw (Canvas c, RecyclerView parent, @NonNull RecyclerView.State state) {
    LinearLayoutManager manager = (LinearLayoutManager) parent.getLayoutManager();
    int first = manager.findFirstVisibleItemPosition();
    int last = manager.findLastVisibleItemPosition();

    if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
      return;
    }

    int fillingCount = 0;
    View view;
    boolean extrasEnabled = needSeparateDecorations();
    for (int i = 0; i < parent.getChildCount(); i++) {
      view = parent.getChildAt(i);
      int adapterPosition = view != null ? parent.getChildAdapterPosition(view) : RecyclerView.NO_POSITION;
      if (view == null)
        continue;
      int fillingColor = getFillingColor(parent, adapterPosition, view);
      if (fillingColor != 0) {
        drawFilling(c, parent, view, manager.getDecoratedTop(view) + view.getTranslationY(), manager.getDecoratedBottom(view) + view.getTranslationY(), fillingColor);
        if (adapterPosition != RecyclerView.NO_POSITION) {
          fillingCount++;
        }
      }
      if (extrasEnabled) {
        drawDecorationForView(c, parent, state, view);
      }
    }

    if (fillingCount > 1) {
      // Fill gaps within filling range that do not have underlying view
      int openColor = 0;
      float lastBottom = -1;
      for (int i = first; i <= last; i++) {
        view = manager.findViewByPosition(i);
        int fillingColor = view != null ? getFillingColor(parent, i, view) : 0;
        float bottom = view != null ? view.getBottom() + view.getTranslationY() : -1;
        float top = view != null ? view.getTop() + view.getTranslationY() : -1;
        if (fillingColor != openColor) {
          openColor = fillingColor;
        } else if (view != null && Color.alpha(openColor) != 0 && top != lastBottom) {
          drawFilling(c, parent, view, lastBottom, top, openColor);
        }
        lastBottom = bottom;
      }
    }
  }

  private void drawFilling (Canvas c, RecyclerView parent, View view, float fillingTop, float fillingBottom, int fillingColor) {
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

  protected int getFillingColor (RecyclerView parent, int position, View view) {
    float alpha = view.getAlpha();
    @IdRes int id = view.getId();
    if (id != View.NO_ID && ids.has(id)) {
      return ColorUtils.alphaColor(alpha, Theme.getColor(fillingColorId));
    }
    for (int[] range : ranges) {
      if (position >= range[0] && position < range[1]) {
        float maxAlpha = alpha;
        for (int i = range[0]; i < range[1] && maxAlpha < 1.0f; i++) {
          if (i != position) {
            View childView = parent.getLayoutManager().findViewByPosition(i);
            if (childView != null) {
              maxAlpha = Math.max(maxAlpha, childView.getAlpha());
            }
          }
        }
        return ColorUtils.alphaColor(maxAlpha, Theme.getColor(fillingColorId));
      }
    }
    return 0;
  }
}
