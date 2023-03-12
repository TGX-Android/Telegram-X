/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/02/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.thunderdog.challegram.Log;

public class ViewPager extends androidx.viewpager.widget.ViewPager {
  public ViewPager (Context context) {
    super(context);
  }

  public ViewPager (Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  private boolean oneShot;
  private java.lang.reflect.Field mFirstLayoutField;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (oneShot) {
      try {
        if (mFirstLayoutField == null) {
          mFirstLayoutField = androidx.viewpager.widget.ViewPager.class.getDeclaredField("mFirstLayout");
          mFirstLayoutField.setAccessible(true);
        }
        mFirstLayoutField.set(this, false);
      } catch (Throwable t) {
        Log.w(t);
      }
    } else {
      oneShot = true;
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    try {
      return pagingEnabled && super.onInterceptTouchEvent(ev);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean pagingEnabled = true;

  public void setPagingEnabled (boolean isEnabled) {
    this.pagingEnabled = isEnabled;
  }

  public boolean isPagingEnabled () {
    return pagingEnabled;
  }

  @Override
  public boolean onTouchEvent (MotionEvent ev) {
    return pagingEnabled && super.onTouchEvent(ev);
  }

}
