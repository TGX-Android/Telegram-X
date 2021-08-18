/**
 * File created on 06/03/16 at 00:20
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.preview;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class FlingDetector implements GestureDetector.OnGestureListener {
  public interface Callback {
    boolean onFling (float velocityX, float velocityY);
  }

  private GestureDetector detector;
  private Callback callback;

  public FlingDetector (Context context, Callback callback) {
    this.callback = callback;
    detector = new GestureDetector(context, this);
  }

  public boolean onTouchEvent (MotionEvent e) {
    return detector.onTouchEvent(e);
  }

  @Override
  public boolean onDown (MotionEvent e) {
    return true;
  }

  @Override
  public void onShowPress (MotionEvent e) {

  }

  @Override
  public boolean onSingleTapUp (MotionEvent e) {
    return false;
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
    return callback.onFling(velocityX, velocityY);
  }
}
