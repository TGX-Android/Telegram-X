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
 * File created on 27/12/2016
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MediaRecyclerView extends RecyclerView {
  public MediaRecyclerView (Context context) {
    super(context);
    init();
  }

  public MediaRecyclerView (Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public MediaRecyclerView (Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init();
  }

  public interface MeasureCallback {
    void onRecyclerMeasure (MediaRecyclerView recyclerView, int width, int height);
  }

  private MeasureCallback measureCallback;

  public void setMeasureCallback (MeasureCallback callback) {
    this.measureCallback = callback;
  }

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    if (measureCallback != null && Math.max(getMeasuredWidth(), getMeasuredHeight()) > 0) {
      measureCallback.onRecyclerMeasure(this, getMeasuredWidth(), getMeasuredHeight());
    }
  }

  @Override
  public int getVerticalScrollbarPosition () {
    LinearLayoutManager manager = (LinearLayoutManager) getLayoutManager();
    if (manager.findFirstCompletelyVisibleItemPosition() == 0) {
      View view = manager.findViewByPosition(0);
      if (view != null) {
        return Math.max(super.getVerticalScrollbarPosition(), view.getTop());
      }
    }
    return super.getVerticalScrollbarPosition();
  }

  private void init () {
    setVerticalScrollBarEnabled(false);
    addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        LinearLayoutManager manager = (LinearLayoutManager) getLayoutManager();
        setScrollbarsVisible(manager.findFirstVisibleItemPosition() != 0);
      }
    });
  }

  private boolean scrollbarsVisible;

  private void setScrollbarsVisible (boolean visible) {
    if (this.scrollbarsVisible != visible) {
      this.scrollbarsVisible = visible;
      setVerticalScrollBarEnabled(visible);
      if (visible) {
        awakenScrollBars();
      }
    }
  }
}
