package org.thunderdog.challegram.support;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Date: 26/01/2017
 * Author: default
 */

public class RectDrawable extends SimpleShapeDrawable {
  private final float padding;

  public RectDrawable (int colorId, float size, float padding, boolean isPressed) {
    super(colorId, size, isPressed);
    this.padding = padding;
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Rect rect = getBounds();
    RectF rectF = Paints.getRectF();
    int padding = Screen.dp(this.padding);
    rectF.set(rect.left + padding, rect.top + padding, rect.right - padding, rect.bottom - padding);
    int radius = Screen.dp(size);
    final int color = getDrawColor();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(color));
    } else {
      c.drawRoundRect(rectF, radius, radius, Paints.shadowFillingPaint(color));
    }
  }
}
