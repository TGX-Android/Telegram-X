package org.thunderdog.challegram.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Date: 9/1/17
 * Author: default
 */

public class RectFrameLayout extends FrameLayoutFix {
  private int top, bottom;

  public RectFrameLayout (@NonNull Context context) {
    super(context);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DEBUG_CLIPPING) {
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          outline.setRect(0, top, view.getMeasuredWidth(), view.getMeasuredHeight() - bottom);
        }
      });
      setClipToOutline(true);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    checkOutline();
  }

  private void checkOutline () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DEBUG_CLIPPING) {
      invalidateOutline();
    }
  }

  public void setClip (int top, int bottom) {
    if (this.top != top || this.bottom != bottom) {
      this.top = top;
      this.bottom = bottom;
      checkOutline();
    }
  }

  @Override
  protected void dispatchDraw(Canvas c) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DEBUG_CLIPPING) {
      super.dispatchDraw(c);
    } else {
      final int saveCount = Views.save(c);
      c.clipRect(0, top, getMeasuredWidth(), getMeasuredHeight() - bottom);
      super.dispatchDraw(c);
      Views.restore(c, saveCount);
    }
  }
}
