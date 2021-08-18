package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import java.lang.reflect.Field;

/**
 * Date: 11/10/18
 * Author: default
 */
public class NoClipEditText extends EditText {
  private static Field mScrollYField;
  private int ignoreTopCount, ignoreBottomCount, scrollY;

  public NoClipEditText(Context context) {
    super(context);

    if (mScrollYField == null) {
      try {
        //noinspection JavaReflectionMemberAccess
        mScrollYField = View.class.getDeclaredField("mScrollY");
        mScrollYField.setAccessible(true);
      } catch (Throwable ignored) { }
    }
  }

  @Override
  public int getExtendedPaddingTop() {
    if (ignoreTopCount != 0) {
      ignoreTopCount--;
      return 0;
    }
    return super.getExtendedPaddingTop();
  }

  @Override
  public int getExtendedPaddingBottom() {
    if (ignoreBottomCount != 0) {
      ignoreBottomCount--;
      return scrollY != Integer.MAX_VALUE ? -scrollY : 0;
    }
    return super.getExtendedPaddingBottom();
  }

  @Override
  protected void onDraw(Canvas c) {
    if (mScrollYField == null) {
      super.onDraw(c);
      return;
    }
    int topPadding = getExtendedPaddingTop();
    scrollY = Integer.MAX_VALUE;
    try {
      scrollY = mScrollYField.getInt(this);
      if (scrollY != 0) {
        mScrollYField.set(this, 0);
      }
    } catch (Throwable ignored) { }
    ignoreTopCount = 1;
    ignoreBottomCount = 1;
    c.save();
    c.translate(0, topPadding);
    try {
      super.onDraw(c);
    } catch (Throwable ignored) {  }
    if (scrollY != Integer.MAX_VALUE && scrollY != 0) {
      try {
        mScrollYField.set(this, scrollY);
      } catch (Throwable ignored) {  }
    }
    c.restore();
  }
}
