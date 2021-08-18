package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.thunderdog.challegram.Log;

/**
 * Date: 27/02/2017
 * Author: default
 */

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
