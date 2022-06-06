/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/03/2016 at 00:20
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
