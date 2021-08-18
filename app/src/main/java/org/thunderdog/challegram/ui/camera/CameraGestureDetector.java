package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * Date: 9/22/17
 * Author: default
 */

public class CameraGestureDetector implements GestureDetector.OnGestureListener {
  private final Context context;
  private final GestureDetector detector;

  public CameraGestureDetector (Context context) {
    this.context = context;
    detector = new GestureDetector(context, this);
  }

  @Override
  public boolean onDown (MotionEvent e) {
    if (e.getPointerCount() == 1) {

    }
    return true;
  }

  @Override
  public void onShowPress (MotionEvent e) {

  }

  @Override
  public boolean onSingleTapUp (MotionEvent e) {
    return true;
  }

  @Override
  public boolean onScroll (MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    return false;
  }

  @Override
  public void onLongPress (MotionEvent e) {

  }

  @Override
  public boolean onFling (MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    return false;
  }
}
