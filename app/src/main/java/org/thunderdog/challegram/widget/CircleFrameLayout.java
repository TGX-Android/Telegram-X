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
 * File created on 28/08/2017
 */
package org.thunderdog.challegram.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.config.Config;

import me.vkryl.android.widget.FrameLayoutFix;

public class CircleFrameLayout extends FrameLayoutFix {
  private final Path path;
  private final Paint aspectPaint;

  private boolean transparentOutline = true;

  public CircleFrameLayout (@NonNull Context context) {
    super(context);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DEBUG_CLIPPING) {
      aspectPaint = null;

      path = null;
      setOutlineProvider(new android.view.ViewOutlineProvider() {
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void getOutline (View view, android.graphics.Outline outline) {
          final int viewWidth = view.getMeasuredWidth();
          outline.setRoundRect(0, 0, viewWidth, view.getMeasuredHeight(), viewWidth / 2);
          outline.setAlpha(transparentOutline ? 0f : 1f);
        }
      });
      setClipToOutline(true);
    } else {
      aspectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      aspectPaint.setColor(0xff000000);
      aspectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
      setLayerType(LAYER_TYPE_HARDWARE, null);

      path = new Path();
    }
  }

  public void setTransparentOutline (boolean transparentOutline) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DEBUG_CLIPPING) {
      if (this.transparentOutline != transparentOutline) {
        this.transparentOutline = transparentOutline;
        invalidateOutline();
      }
    }
  }

  private int lastWidth, lastHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int newWidth = getMeasuredWidth();
    int newHeight = getMeasuredHeight();
    if (lastWidth != newWidth || lastHeight != newHeight) {
      lastWidth = newWidth;
      lastHeight = newHeight;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DEBUG_CLIPPING) {
        invalidateOutline();
      } else {
        path.reset();
        path.addCircle(lastWidth / 2, lastHeight / 2, lastWidth / 2, Path.Direction.CW);
        path.toggleInverseFillType();
      }
    }
  }

  @Override
  protected void dispatchDraw(Canvas c) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.DEBUG_CLIPPING) {
      super.dispatchDraw(c);
    } else {
      super.dispatchDraw(c);
      c.drawPath(path, aspectPaint);
    }
  }
}
