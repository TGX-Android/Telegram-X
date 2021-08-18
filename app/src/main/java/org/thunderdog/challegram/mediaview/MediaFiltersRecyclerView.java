package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.view.MotionEvent;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Date: 10/12/2016
 * Author: default
 */

public class MediaFiltersRecyclerView extends RecyclerView {
  public MediaFiltersRecyclerView (Context context) {
    super(context);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    super.onTouchEvent(e);
    return true;
  }
}
