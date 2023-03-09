/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/08/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.MotionEvent;

import org.thunderdog.challegram.component.preview.FlingDetector;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

public class BaseRootLayout extends RootFrameLayout implements FlingDetector.Callback {
  private final FlingDetector detector;

  public BaseRootLayout (Context context) {
    super(context);
    detector = new FlingDetector(context, this);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    lastTouchX = ev.getX();
    lastTouchY = ev.getY();
    if (mode == MODE_LISTENING) {
      if (ev.getPointerCount() > 1) {
        mode = MODE_NONE;
      } else {
        switch (ev.getAction()) {
          case MotionEvent.ACTION_MOVE: {
            float diffY = touchStartY - lastTouchY;
            float absDiffY = Math.abs(diffY);
            float threshold = Screen.getTouchSlopBig() * 2;
            if ((diffY > 0 == dragDirectionDownUp) && absDiffY >= threshold && Math.abs(lastTouchX - touchStartX) < absDiffY) {
              if (UI.getContext(getContext()).startCameraDrag(pendingCameraOptions, dragDirectionDownUp)) {
                mode = MODE_DRAGGING;
                touchStartX = lastTouchX;
                touchStartY = lastTouchY;
                detector.onTouchEvent(MotionEvent.obtain(ev.getDownTime(), ev.getEventTime(), MotionEvent.ACTION_DOWN, ev.getX(), ev.getY(), ev.getMetaState()));
                return true;
              }
              mode = MODE_NONE;
            }
            break;
          }
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            mode = MODE_NONE;
            break;
        }
      }
    }
    return mode == MODE_DRAGGING || super.onInterceptTouchEvent(ev);
  }

  @Override
  public boolean onFling (float velocityX, float velocityY) {
    if (mode == MODE_DRAGGING) {
      if (Math.abs(velocityY) >= Screen.dp(10f)) {
        UI.getContext(getContext()).dropCameraDrag(velocityY < 0, true);
        mode = MODE_NONE;
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (mode != MODE_DRAGGING) {
      return super.onTouchEvent(e);
    }
    detector.onTouchEvent(e);
    if (mode != MODE_DRAGGING) {
      return true;
    }
    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        float touchY = e.getY();
        float factor = Math.max(0f, Math.min(1f, (dragDirectionDownUp ? touchStartY - touchY : touchY - touchStartY) / (float) getMeasuredHeight()));
        UI.getContext(getContext()).setCameraDragFactor(dragDirectionDownUp ? factor : 1f - factor);
        break;
      }
      case MotionEvent.ACTION_UP: {
        UI.getContext(getContext()).dropCameraDrag();
        mode = MODE_NONE;
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        UI.getContext(getContext()).dropCameraDrag(false, false);
        mode = MODE_NONE;
        break;
      }
    }
    return true;
  }

  private static final int MODE_NONE = 0;
  private static final int MODE_LISTENING = 1;
  private static final int MODE_DRAGGING = 2;

  private int mode;
  private ViewController.CameraOpenOptions pendingCameraOptions;
  private float lastTouchX, lastTouchY;
  private float touchStartX, touchStartY;
  private boolean dragDirectionDownUp; // true if finger movement is bottom -> top

  public void prepareVerticalDrag (ViewController.CameraOpenOptions options, boolean isOpen) {
    if (mode == MODE_NONE) {
      this.mode = MODE_LISTENING;
      if (isOpen) {
        this.pendingCameraOptions = options;
      }
      this.dragDirectionDownUp = isOpen;
      this.touchStartX = lastTouchX;
      this.touchStartY = lastTouchY;
    }
  }
}
