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
 * File created on 07/11/2016
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.view.MotionEvent;

import me.vkryl.android.widget.FrameLayoutFix;

public class DrawerContentView extends FrameLayoutFix {
  // private Object lastInsets;

  public DrawerContentView (Context context) {
    super(context);

    /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      setFitsSystemWindows(true);
      setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {
        @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
        @Override
        public WindowInsets onApplyWindowInsets (View v, WindowInsets insets) {
          lastInsets = insets;
          setWillNotDraw(insets.getSystemWindowInsetTop() <= 0 && getBackground() == null);
          requestLayout();
          return insets.consumeSystemWindowInsets();
        }
      });
      setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }*/
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return super.onTouchEvent(event) || event.getAction() == MotionEvent.ACTION_DOWN;
  }

  /*@Override
  protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
    if (!allowDrawContent) {
      return false;
    }
    final int height = getHeight();
    final boolean drawingContent = child != drawerLayout;
    int lastVisibleChild = 0;
    int clipLeft = 0, clipRight = getWidth();

    final int restoreCount = canvas.save();
    if (drawingContent) {
      final int childCount = getChildCount();
      for (int i = 0; i < childCount; i++) {
        final View v = getChildAt(i);
        if (v.getVisibility() == VISIBLE && v != drawerLayout) {
          lastVisibleChild = i;
        }
        if (v == child || v.getVisibility() != VISIBLE || v != drawerLayout || v.getHeight() < height) {
          continue;
        }

        final int vright = v.getRight();
        if (vright > clipLeft) {
          clipLeft = vright;
        }
      }
      if (clipLeft != 0) {
        canvas.clipRect(clipLeft, 0, clipRight, getHeight());
      }
    }
    final boolean result = super.drawChild(canvas, child, drawingTime);
    canvas.restoreToCount(restoreCount);

    if (scrimOpacity > 0 && drawingContent) {
      if (indexOfChild(child) == lastVisibleChild) {
        scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * scrimOpacity) << 24);
        canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
      }
    } else if (shadowLeft != null) {
      final float alpha = Math.max(0, Math.min(drawerPosition / AndroidUtilities.dp(20), 1.0f));
      if (alpha != 0) {
        shadowLeft.setBounds((int) drawerPosition, child.getTop(), (int) drawerPosition + shadowLeft.getIntrinsicWidth(), child.getBottom());
        shadowLeft.setAlpha((int) (0xff * alpha));
        shadowLeft.draw(canvas);
      }
    }
    return result;
  }*/

  @Override
  public boolean hasOverlappingRendering() {
    return false;
  }
}
