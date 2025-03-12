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
 * File created on 17/06/2015 at 17:24
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

public class SeparatorView extends View {
  private final Paint paint;

  private float left;
  private float right;

  private float top;

  private int height;
  private int forcedFillingColor;
  private int forcedColor, colorId = ColorId.separator;
  private boolean noAlign;

  public SeparatorView (Context context) {
    super(context);

    paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setColor(Theme.getColor(colorId));
    paint.setStyle(Paint.Style.FILL);

    height = Math.max(Screen.dp(.5f), 1);
  }

  public void setColorId (@ColorId int colorId) {
    if (this.colorId != colorId) {
      this.colorId = colorId;
      this.paint.setColor(Theme.getColor(colorId));
    }
  }

  public void forceColor (int color) {
    paint.setColor(forcedColor = color);
  }

  public void setOffsets (float left, float right) {
    this.left = left;
    this.right = right;
  }

  private boolean useFilling;

  public void setUseFilling () {
    this.useFilling = true;
  }

  public void forceFillingColor (int filling) {
    this.useFilling = true;
    this.forcedFillingColor = filling;
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    super.onTouchEvent(event);
    return true;
  }

  public void setNoAlign () {
    this.noAlign = true;
  }

  public void setSeparatorHeight (int height) {
    this.height = height;
  }

  private boolean alignBottom;

  public void setAlignBottom () {
    alignBottom = true;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    top = alignBottom ? getMeasuredHeight() - height : noAlign ? 0f : (float) getMeasuredHeight() / 2f;
  }

  @Override
  protected void onDraw (Canvas c) {
    int width = getMeasuredWidth();
    if (useFilling) {
      c.drawColor(forcedFillingColor != 0 ? forcedFillingColor : Theme.fillingColor());
    }
    if (forcedColor == 0) {
      paint.setColor(Theme.getColor(colorId));
    } else {
      paint.setColor(forcedColor);
    }
    if (left != 0 || right != 0) {
      if (Lang.rtl()) {
        c.drawRect(right, top, width - left, top + height, paint);
      } else {
        c.drawRect(left, top, width - right, top + height, paint);
      }
    } else {
      c.drawRect(0, top, width, top + height, paint);
    }
  }

  public static SeparatorView simpleSeparator (Context context, ViewGroup.LayoutParams params, boolean needFilling) {
    SeparatorView view;
    view = new SeparatorView(context);
    view.setSeparatorHeight(Math.max(1, Screen.dp(.5f)));
    if (needFilling) {
      view.setNoAlign();
      view.setUseFilling();
    }
    // view.setOffsets(Screen.dp(72f), 0f);
    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
    params.height = Screen.dp(1f);
    view.setLayoutParams(params);
    return view;
  }
}
