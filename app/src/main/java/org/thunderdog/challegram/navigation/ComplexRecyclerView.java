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
  private boolean needHeaderExpand = true;
  private int totalY;

  public ComplexRecyclerView (Context context, ViewController<?> target) {
    super(context);
    this.target = target;

    this.scrollFactor = 1f;
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
    setHeaderView(headerView, target, /* needExpand */ true);
  }

  public void setHeaderView (StretchyHeaderView headerView, ViewController<?> target, boolean needExpand) {
    this.headerView = headerView;
    this.target = target;
    this.needHeaderExpand = needExpand;
  }

  public float getScrollFactor () {
    if (getChildCount() == 0) {
      return 1f;
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
    int minHeight = Size.getHeaderPortraitSize();
    int maxHeight = target.getMaximumHeaderHeight();
    int difference = maxHeight - minHeight;

    float t = view == null ? difference  : Math.min(difference, -view.getTop());
    float headerFactor = t / (float) difference;

    float baseScale = (float) maxHeight / (float) Size.getHeaderBigPortraitSize(true);
    float factor = baseScale * (1f - headerFactor);
    this.scrollFactor = MathUtils.clamp(factor);

    int backgroundHeight = headerFactor >= 1f ? minHeight : headerFactor <= 0f ? maxHeight : maxHeight - (int) ((float) difference * headerFactor);
    if ((flags & FLAG_FORCE) == 0) {
      headerView.setScaleFactor(scrollFactor, scrollFactor, scrollFactor, true);
      if (floatingButton != null && target.getFloatingButtonId() != 0) {
        floatingButton.setHeightFactor(scrollFactor, 0f, true);
      }
      if (updateFilling && target.headerView != null) {
        target.headerView.setBackgroundHeight(backgroundHeight);
      }
    }
  }
}
