package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * Date: 7/22/17
 * Author: default
 */

public class CustomImageView extends ImageView {
  public CustomImageView (Context context) {
    super(context);
  }

  public CustomImageView (Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public CustomImageView (Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return (e.getAction() != MotionEvent.ACTION_DOWN || (getVisibility() == View.VISIBLE && getAlpha() == 1f)) && super.onTouchEvent(e);
  }

  private boolean preventLayout;

  @Override
  public void setImageBitmap (Bitmap bm) {
    preventLayout = true;
    super.setImageBitmap(bm);
    preventLayout = false;
  }

  @Override
  public void requestLayout () {
    if (!preventLayout) {
      super.requestLayout();
    }
  }
}
