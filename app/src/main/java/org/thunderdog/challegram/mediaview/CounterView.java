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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.CounterHeaderView;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

public class CounterView extends CounterHeaderView {
  public CounterView (Context context) {
    super(context);
    init(14f, R.id.theme_color_white, Screen.dp(16f), 0, Screen.dp(9f) + Screen.dp(10f));
    if (Config.HARDWARE_CLIP_PATH_FIX) {
      Views.setLayerType(this, LAYER_TYPE_HARDWARE);
    }
  }

  @Override
  protected boolean needSpecial () {
    return true;
  }

  private Path path = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? new Path() : null;
  private float lastLeft, lastRight;

  @Override
  protected boolean alignRight () {
    return false;
  }

  @Override
  protected void drawSpecial (Canvas c, int width, int add) {
    c.save();

    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();
    int radius = viewHeight / 2;

    RectF rectF = Paints.getRectF();

    float strokeWidth = Screen.dp(2f);
    float left = viewWidth - radius - radius - add / 2 + strokeWidth / 2;
    float right = viewWidth - strokeWidth / 2;
    rectF.set(left, strokeWidth / 2, right, viewHeight - strokeWidth / 2);
    c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(0xa0000000));
    c.drawRoundRect(rectF, radius, radius, Paints.getProgressPaint(0xffffffff, strokeWidth));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null) {
      if (lastLeft != left || lastRight != right) {
        lastLeft = left;
        lastRight = right;
        path.reset();
        path.addRoundRect(rectF, radius, radius, Path.Direction.CCW);
      }
      try {
        c.clipPath(path);
      } catch (Throwable ignored) { }
    }

    int textLeft = (int) (left + right) / 2 - width / 2;
    c.translate(textLeft, 0);
  }

  @Override
  protected void restoreSpecial (Canvas c) {
    c.restore();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int size = getLayoutParams().height;
    setMeasuredDimension(size + fullWidth, heightMeasureSpec);
    setPivotX(getMeasuredWidth() - size / 2);
    setPivotY(getMeasuredHeight() / 2);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return Views.isValid(this) && super.onTouchEvent(event);
  }
}
