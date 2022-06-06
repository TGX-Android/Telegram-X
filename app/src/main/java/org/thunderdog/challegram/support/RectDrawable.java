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
 * File created on 26/01/2017
 */
package org.thunderdog.challegram.support;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

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
