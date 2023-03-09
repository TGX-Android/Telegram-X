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
 * File created on 14/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

public class ToastView extends NoScrollTextView {
  public ToastView (Context context) {
    super(context);

    setTypeface(Fonts.getRobotoRegular());
    setTextColor(0xffffffff);
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    setPadding(Screen.dp(10f), Screen.dp(10f), Screen.dp(10f), Screen.dp(10f));
    setGravity(Gravity.CENTER);

    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  @Override
  protected void onDraw (Canvas c) {
    RectF rectF = Paints.getRectF();
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    int radius = Screen.dp(10f);
    rectF.set(0, 0, width, height);
    c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(0xa0000000));

    super.onDraw(c);
  }
}
