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
 * File created on 01/09/2015 at 14:03
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.CustomRecyclerView;

import me.vkryl.core.MathUtils;

public class ComplexRecyclerView extends CustomRecyclerView implements Runnable {
  private StretchyHeaderView headerView;
  private ViewController<?> target;
  private FloatingButton floatingButton;

  private float scrollFactor;
  private boolean factorLocked;
  private int totalY;

  public ComplexRecyclerView (Context context, ViewController<?> target) {
    super(context);
    this.target = target;

    this.scrollFactor = getMaxFactor();
    this.factorLocked = true;
    setHasFixedSize(true);
    setVerticalScrollBarEnabled(false);

    addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        totalY += dy;
        if (headerView != null && !factorLocked) {
          updateScrollFactor(true);
        }
      }
    });
  }

  public void setFloatingButton (FloatingButton floatingButton) {
    this.floatingButton = floatingButton;
  }

  public void setFactorLocked (boolean locked) {
    this.factorLocked = locked;
  }

  public void setHeaderView (StretchyHeaderView headerView, ViewController<?> target) {
    this.headerView = headerView;
    this.target = target;
  }

  private float getMaxFactor () {
    int maxHeight = Size.getHeaderBigPortraitSize(true);
    int targetMaxHeight = target.getMaximumHeaderHeight();
    return 1f - (float) (maxHeight - targetMaxHeight) / (float) Size.getHeaderSizeDifference(true);
  }

  public float getScrollFactor () {
    if (getChildCount() == 0) {
      return getMaxFactor();
    }
    if (headerView == null || factorLocked) {
      return scrollFactor;
    }
    updateScrollFactor(false);
    return scrollFactor;
  }

  private static final int FLAG_REBUILD = 0x01;
  private static final int FLAG_FORCE = 0x02;

  private int flags;

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (changed && !factorLocked && (flags & FLAG_REBUILD) != 0) {
      flags &= ~FLAG_REBUILD;
      updateScrollFactor(true);
    }
  }

  public void rebuildTop () {
    flags |= FLAG_REBUILD;
  }

  public void postUpdate () {
    post(this);
  }

  public void forceUpdate () {
    flags |= FLAG_FORCE;
    updateScrollFactor(false);
    flags &= ~FLAG_FORCE;
  }

  @Override
  public void run () {
    updateScrollFactor(true);
  }

  private void updateScrollFactor (boolean updateFilling) {
    if (target.inCustomMode() && (flags & FLAG_FORCE) == 0) {
      return;
    }
    View view = getLayoutManager().findViewByPosition(0);
    int diff = Size.getHeaderBigPortraitSize(true) - target.getMaximumHeaderHeight();
    float t = view == null ? Size.getHeaderSizeDifference(true) : -view.getTop() + diff;
    float factor = 1f - t / (float) Size.getHeaderSizeDifference(true);
    scrollFactor = MathUtils.clamp(factor);
    if ((flags & FLAG_FORCE) == 0) {
      headerView.setScaleFactor(scrollFactor, scrollFactor, scrollFactor, true);
      if (floatingButton != null && target.getFloatingButtonId() != 0) {
        floatingButton.setHeightFactor(scrollFactor, 0f, true);
      }
      if (updateFilling && target.headerView != null) {
        if (scrollFactor == 1f) {
          target.headerView.setBackgroundHeight(Size.getHeaderBigPortraitSize(true));
        } else if (scrollFactor == 0f) {
          target.headerView.setBackgroundHeight(Size.getHeaderPortraitSize());
        } else {
          target.headerView.setBackgroundHeight(Size.getHeaderPortraitSize() + (int) (Size.getHeaderSizeDifference(true) * scrollFactor));
        }
      }
    }
  }
}
