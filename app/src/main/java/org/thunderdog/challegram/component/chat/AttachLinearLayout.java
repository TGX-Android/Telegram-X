/**
 * File created on 14/03/16 at 00:29
 * Copyright Vyacheslav Krylov, 2014
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
