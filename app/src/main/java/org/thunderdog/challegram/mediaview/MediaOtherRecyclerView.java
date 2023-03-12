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
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

public class MediaOtherRecyclerView extends RecyclerView {
  public MediaOtherRecyclerView (Context context) {
    super(context);
  }

  private Path path = null;//Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? new Path() : null;
  private float factor;

  public void setFactor (float factor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null) {
      if (this.factor != factor) {
        this.factor = factor;
        layoutPath();
        invalidate();
      }
    } else {
      setAlpha(factor);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (getAlpha() > 0f) {
      super.onTouchEvent(e);
      return true;
    }
    return false;
  }

  private void layoutPath () {
    if (path != null) {
      int viewWidth = getMeasuredWidth();
      int fromCx = viewWidth - Screen.dp(78f) + Screen.dp(15f);
      int fromCy = Screen.dp(26f) + HeaderView.getTopOffset() + Screen.dp(15f);
      int fromRadius = Screen.dp(15f);

      int cx = fromCx + (int) ((float) (viewWidth - fromCx) * factor);
      int cy = fromCy - (int) ((float) fromCy * factor);

      int width = fromRadius + (int) ((float) (viewWidth - fromRadius) * factor);
      int height = fromRadius + (int) ((float) (getMeasuredHeight() - fromRadius) * factor);
      float radius = fromRadius * (1f - factor);

      RectF rectF = Paints.getRectF();
      rectF.set(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2);
      path.reset();
      path.addRoundRect(rectF, radius, radius, Path.Direction.CCW);
    }
  }

  @Override
  protected void onMeasure (int widthSpec, int heightSpec) {
    super.onMeasure(widthSpec, heightSpec);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null) {
      layoutPath();
    }
  }

  @Override
  public void draw (Canvas c) {
    final boolean needClip = factor < 1f;
    final int saveCount = needClip ? ViewSupport.clipPath(c, path) : Integer.MIN_VALUE;

    super.draw(c);

    if (needClip) {
      ViewSupport.restoreClipPath(c, saveCount);
    }
  }
}
