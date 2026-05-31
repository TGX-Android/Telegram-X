package org.thunderdog.challegram.v;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.ScrollView;

import org.thunderdog.challegram.tool.Views;

public class MaxHeightScrollView extends ScrollView {
  public MaxHeightScrollView (Context context) {
    super(context);
  }

  private int maxHeight;

  public void setMaxHeight (int maxHeight) {
    if (this.maxHeight != maxHeight) {
      this.maxHeight = maxHeight;
      requestLayout();
    }
  }

  private boolean isScrollable;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (maxHeight != 0 && getMeasuredHeight() > maxHeight) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));
      isScrollable = true;
    } else {
      isScrollable = false;
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent ev) {
    boolean res = super.onTouchEvent(ev) && (ev.getAction() != MotionEvent.ACTION_DOWN || (isScrollable && Views.isValid(this)));
    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (isScrollable && Views.isValid(this)) {
          ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(false);
        }
        break;
    }
    return res;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return (super.onInterceptTouchEvent(ev) && (ev.getAction() != MotionEvent.ACTION_DOWN || Views.isValid(this))) || !Views.isValid(this);
  }
}
