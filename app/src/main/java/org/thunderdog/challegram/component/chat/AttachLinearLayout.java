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
 * File created on 14/03/2016 at 00:29
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.thunderdog.challegram.tool.Views;

public class AttachLinearLayout extends LinearLayout {
  public AttachLinearLayout (Context context) {
    super(context);
  }

  /*private int calculateWidth () {
    int width = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      if (v.getVisibility() == View.VISIBLE) {
        width += v.getLayoutParams().width;
      }
    }
    return width;
  }*/

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return (ev.getAction() == MotionEvent.ACTION_DOWN && !Views.isValid(this)) || super.onInterceptTouchEvent(ev);
  }

  public void updatePivot () {
    int totalWidth = 0;
    int width = 0;

    //int itemWidth = 0;
    int itemHeight = 0;

    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      ViewGroup.LayoutParams params = v.getLayoutParams();
      int w = params.width;
      if (itemHeight == 0) {
        //itemWidth = params.width;
        itemHeight = params.height;
      }
      totalWidth += w;
      if (v.getVisibility() == View.VISIBLE) {
        width += w;
      }
    }

    setPivotX(totalWidth - (int) ((float) width * .5f));
    setPivotY((int) ((float) itemHeight * .5f));
  }

  public int getVisibleChildrenWidth () {
    int totalWidth = 0;
    final int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View v = getChildAt(i);
      if (v.getVisibility() == View.VISIBLE) {
        int width = v.getLayoutParams().width;
        totalWidth += width;
      }
    }
    return totalWidth;
  }
}
