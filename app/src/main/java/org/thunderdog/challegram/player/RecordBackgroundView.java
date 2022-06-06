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
 * File created on 10/12/2017
 */
package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Views;

public class RecordBackgroundView extends View {
  private int pivotX = -1, pivotY = -1;

  public RecordBackgroundView (Context context) {
    super(context);
  }

  private float factor = -1f;

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      // invalidate();
      setAlpha(factor);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return Views.isValid(this) && (onClickListener == null || super.onTouchEvent(event));
  }

  private OnClickListener onClickListener;

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    super.setOnClickListener(l);
    this.onClickListener = l;
  }

  public void setPivot (int x, int y) {
    if (this.pivotX != x || this.pivotY != y) {
      this.pivotX = x;
      this.pivotY = y;

      if (factor != 0f && factor != 1f) {
        // invalidate();
      }
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    /*if (factor > 0f) {
      int color = Utils.alphaColor(factor, Theme.getColor(R.id.theme_color_overlay));
      int halfColor = Utils.alphaColor(.5f, color);
      c.drawColor(halfColor);
      if (factor == 1f) {
        c.drawColor(halfColor);
      } else {
        int viewWidth = getMeasuredWidth();
        int viewHeight = getMeasuredHeight();

        float radius = (float) Math.sqrt(viewWidth * viewWidth + viewHeight * viewHeight) * .5f; // * Math.max((float) pivotX / (float) viewWidth, (float) pivotY / (float) viewHeight);

        int toX = viewWidth / 2;
        int toY = viewHeight / 2;

        float x = (pivotX + (toX - pivotX) * factor);
        float y = (pivotY + (toY - pivotY) * factor);

        c.drawCircle(x, y, radius * factor, Paints.fillingPaint(halfColor));

        // c.drawCircle(pivotX, pivotY, radius * factor, Paints.fillingPaint(halfColor));
      }
    }*/
  }
}
