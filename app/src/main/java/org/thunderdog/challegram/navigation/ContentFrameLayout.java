/**
 * File created on 15/05/15 at 23:30
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;

import me.vkryl.android.widget.FrameLayoutFix;

public class ContentFrameLayout extends FrameLayoutFix {
  public static final boolean CUT_ENABLED = false;

  private int lastWidth;
  private int lastHeight;
  private float targetDiff;
  private float currentTarget, factor;

  private int clipLeft;

  public ContentFrameLayout (Context context) {
    super(context);
  }

  public void setClipLeft (int clipLeft) {
    if (this.clipLeft != clipLeft) {
      if (clipLeft < this.clipLeft) {
        this.clipLeft = clipLeft;
        invalidate();
      } else {
        this.clipLeft = clipLeft;
      }
    }
  }

  public int getClipLeft () {
    return clipLeft;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (CUT_ENABLED) {
      lastWidth = getMeasuredWidth();
      lastHeight = getMeasuredHeight();
      currentTarget = -((float) Screen.currentWidth() / Size.NAVIGATION_PREVIEW_TRANSLATE_FACTOR);
      targetDiff = lastWidth + currentTarget;
    }
  }

  @Override
  public void setTranslationX (float x) {
    super.setTranslationX(x);
    if (CUT_ENABLED) {
      factor = x / currentTarget;
      if (x <= 0f) {
        invalidate();
      }
    }
  }

  @Override
  protected boolean drawChild (Canvas c, View v, long drawingTime) {
    if (CUT_ENABLED) {
      if (clipLeft != 0) {
        c.save();
        c.clipRect(clipLeft, 0, lastWidth, lastHeight);
        boolean result = super.drawChild(c, v, drawingTime);
        c.restore();
        return result;
      }
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
