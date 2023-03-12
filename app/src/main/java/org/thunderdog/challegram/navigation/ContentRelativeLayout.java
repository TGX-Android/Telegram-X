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
 * File created on 16/05/2015 at 00:04
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.RelativeLayout;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;

public class ContentRelativeLayout extends RelativeLayout {
  private int lastWidth;
  private int lastHeight;
  private float targetDiff;
  private float currentTarget, factor;

  public ContentRelativeLayout (Context context) {
    super(context);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (ContentFrameLayout.CUT_ENABLED) {
      lastWidth = getMeasuredWidth();
      lastHeight = getMeasuredHeight();
      currentTarget = -((float) Screen.currentWidth() / Size.NAVIGATION_PREVIEW_TRANSLATE_FACTOR);
      targetDiff = lastWidth + currentTarget;
    }
  }

  @Override
  public void setTranslationX (float x) {
    super.setTranslationX(x);
    if (ContentFrameLayout.CUT_ENABLED) {
      factor = x / currentTarget;
      if (x <= 0f) {
        invalidate();
      }
    }
  }

  @Override
  protected boolean drawChild (Canvas c, View v, long drawingTime) {
    if (ContentFrameLayout.CUT_ENABLED) {
      float x = getTranslationX();
      if (x < 0f) {
        c.save();
        c.clipRect(0, 0, lastWidth - factor * targetDiff, lastHeight);
        boolean result = super.drawChild(c, v, drawingTime);
        c.restore();
        return result;
      } else {
        return super.drawChild(c, v, drawingTime);
      }
    } else {
      return super.drawChild(c, v, drawingTime);
    }
  }
}
