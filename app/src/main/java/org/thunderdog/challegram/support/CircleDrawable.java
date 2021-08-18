package org.thunderdog.challegram.support;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Date: 26/01/2017
 * Author: default
 */

public class CircleDrawable extends SimpleShapeDrawable {
  public CircleDrawable (int colorId, float size, boolean isPressed) {
    super(colorId, size, isPressed);
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Rect rect = getBounds();
    int centerX = rect.centerX();
    int centerY = rect.centerY();
    int radius = Screen.dp(size) / 2;

    final int color = getDrawColor();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (color != 0) {
        c.drawCircle(centerX, centerY, radius, Paints.fillingPaint(color));
      }
    } else {
      c.drawCircle(centerX, centerY, radius, Paints.shadowFillingPaint(color));
    }
  }
}
