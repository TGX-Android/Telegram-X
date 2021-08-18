package org.thunderdog.challegram.v;

import android.content.Context;
import android.graphics.PointF;
import android.util.DisplayMetrics;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.component.chat.MessagesManager;

/**
 * Date: 9/1/17
 * Author: default
 */

public class MessagesLayoutManager extends LinearLayoutManager {
  private static final float MILLISECONDS_PER_INCH = 10f;

  private final Context context;
  private MessagesManager manager;

  public MessagesLayoutManager (Context context, int orientation, boolean reverseLayout) {
    super(context, orientation, reverseLayout);
    this.context = context;
  }

  public void setManager (MessagesManager manager) {
    this.manager = manager;
  }

  @Override
  public void smoothScrollToPosition (RecyclerView recyclerView, RecyclerView.State state, int position) {
    final long distance = manager.calculateScrollingDistance();
    if (distance <= recyclerView.getMeasuredHeight()) {
      super.smoothScrollToPosition(recyclerView, state, position);
      return;
    }
    final float ms = distance == 0 ? 1f : (float) (220.0 / distance);

    final LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
      @Override
      public PointF computeScrollVectorForPosition(int targetPosition) {
        return MessagesLayoutManager.this.computeScrollVectorForPosition(targetPosition);
      }

      @Override
      protected float calculateSpeedPerPixel (DisplayMetrics displayMetrics) {
        return ms;
      }
    };
    linearSmoothScroller.setTargetPosition(position);
    startSmoothScroll(linearSmoothScroller);
  }
}
