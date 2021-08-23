/**
 * File created on 01/09/15 at 14:03
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.CustomRecyclerView;

public class ComplexRecyclerView extends CustomRecyclerView implements Runnable {
  private ComplexHeaderView headerView;
  private ViewController<?> target;
  private FloatingButton floatingButton;

  private float scrollFactor;
  private boolean factorLocked;
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
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
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

  public void setHeaderView (ComplexHeaderView headerView, ViewController<?> target) {
    this.headerView = headerView;
    this.target = target;
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
    float t = view == null ? target.getMaximumHeaderHeight() : -view.getTop();
    float factor = 1f - t / (float) Size.getHeaderSizeDifference(true);
    if (factor >= 1f) {
      scrollFactor = 1f;
    } else if (factor <= 0f) {
      scrollFactor = 0f;
    } else {
      scrollFactor = factor;
    }
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
