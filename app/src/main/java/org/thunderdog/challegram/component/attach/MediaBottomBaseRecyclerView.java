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
 * File created on 23/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.MotionEvent;

import androidx.recyclerview.widget.RecyclerView;

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
