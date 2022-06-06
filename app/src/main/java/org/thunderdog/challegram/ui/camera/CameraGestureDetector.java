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
 * File created on 22/09/2017
 */
package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

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
