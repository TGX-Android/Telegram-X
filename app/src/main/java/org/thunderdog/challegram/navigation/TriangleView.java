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
 * File created on 09/08/2015 at 16:11
 */
package org.thunderdog.challegram.navigation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

public class TriangleView {
  private Paint paint;
  private Path path;

  private float width, height;

  public TriangleView () {
    this(0f, 0f);
  }

  public TriangleView (float x, float y) {
    paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setColor(Theme.headerBackColor());
    paint.setStrokeWidth(1);
    paint.setStyle(Paint.Style.FILL_AND_STROKE);

    width = Screen.dpf(10f);
    height = Screen.dpf(5f);

    path = new Path();
    path.setFillType(Path.FillType.EVEN_ODD);
    path.moveTo(x, y);
    path.lineTo(x + width, y);
    path.lineTo(x + width * .5f, y + height);
    path.close();
  }

  public float getCenterX () {
    return width * .5f;
  }

  public float getWidth () {
    return width;
  }

  public float getHeight () {
    return height;
  }

  public void setColor (int color) {
    paint.setColor(color);
  }

  public void draw (Canvas c) {
    c.drawPath(path, paint);
  }
}
