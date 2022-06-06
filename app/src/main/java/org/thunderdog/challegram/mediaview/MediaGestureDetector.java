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
 * File created on 01/09/2015 at 03:40
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

public class MediaGestureDetector implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
  private GestureDetector detector;
  private MediaView boundView;

  public MediaGestureDetector (Context context) {
    this.detector = new GestureDetector(context, this);
    this.detector.setOnDoubleTapListener(this);
  }

  public void setBoundView (MediaView boundView) {
    this.boundView = boundView;
  }

  // OnGestureListener

  public boolean onTouchEvent (MotionEvent e) {
    return detector.onTouchEvent(e);
  }

  @Override
  public boolean onFling (MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    if (boundView == null || boundView.inSingleMode()) {
      return false;
    }

    float check = Screen.dp(250, 1f);
    float velocity = Math.abs(check) / (float) Screen.dp(500f);

    if ((Lang.rtl() ? velocityX <= -check : velocityX >= check) && boundView.hasPrevious()) {
      boundView.dropPreview(MediaView.DIRECTION_BACKWARD, velocity);
      return true;
    }

    if ((Lang.rtl() ? velocityX >= check : velocityX <= -check) && boundView.hasNext()) {
      boundView.dropPreview(MediaView.DIRECTION_FORWARD, velocity);
      return true;
    }

    return false;
  }

  @Override
  public boolean onSingleTapConfirmed (MotionEvent e) {
    return false;
  }

  @Override
  public boolean onDoubleTap (MotionEvent e) {
    return false;
  }

  // Unused OnGestureListener

  @Override
  public boolean onDown (MotionEvent e) {
    return false;
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

  // Unused OnDoubleTapListener

  @Override
  public boolean onDoubleTapEvent (MotionEvent e) {
    return false;
  }
}
