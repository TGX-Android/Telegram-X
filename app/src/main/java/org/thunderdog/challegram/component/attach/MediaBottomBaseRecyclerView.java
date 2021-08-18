package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.MotionEvent;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Date: 23/10/2016
 * Author: default
 */

public class MediaBottomBaseRecyclerView extends RecyclerView {
  public MediaBottomBaseRecyclerView (Context context) {
    super(context);
  }

  private boolean isDown;

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        isDown = true;
        break;
      }
      case MotionEvent.ACTION_UP: {
        isDown = false;
        break;
      }
    }
    return super.onInterceptTouchEvent(e);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        isDown = true;
        break;
      }
      case MotionEvent.ACTION_UP: {
        isDown = false;
        break;
      }
    }
    return e.getAction() != MotionEvent.ACTION_CANCEL && super.onTouchEvent(e);
  }

  public void processEvent (MotionEvent e) {
    if (isDown) {
      dispatchTouchEvent(e);
    }
  }
}
