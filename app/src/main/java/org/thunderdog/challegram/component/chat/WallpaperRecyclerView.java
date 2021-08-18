package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Date: 11/7/18
 * Author: default
 */
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
