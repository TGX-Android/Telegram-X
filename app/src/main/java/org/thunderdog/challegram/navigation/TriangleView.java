/**
 * File created on 09/08/15 at 16:11
 * Copyright Vyacheslav Krylov, 2014
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
