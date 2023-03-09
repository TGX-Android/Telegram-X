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
 * File created on 07/11/2018
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class WallpaperRecyclerView extends RecyclerView {
  public WallpaperRecyclerView (@NonNull Context context) {
    super(context);
    addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        switch (newState) {
          case RecyclerView.SCROLL_STATE_DRAGGING:
          case RecyclerView.SCROLL_STATE_IDLE:
            smoothRequested = false;
            break;
        }
      }
    });
  }

  private boolean smoothRequested;

  @Override
  public void smoothScrollBy (int dx, int dy) {
    smoothRequested = dx != 0;
    super.smoothScrollBy(dx, dy);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    if (smoothRequested && e.getAction() == MotionEvent.ACTION_DOWN) {
      stopScroll(); // Hack
    }
    return super.onInterceptTouchEvent(e);
  }

  int lastWidth;
  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    if (lastWidth != getMeasuredWidth()) {
      lastWidth = getMeasuredWidth();
      ((WallpaperAdapter) getAdapter()).centerWallpapers(false);
    }
  }
}
