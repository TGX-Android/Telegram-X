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
 * File created on 19/01/2024
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;

public class EndIconModifier implements DrawModifier, TooltipOverlayView.LocationProvider {

  private static final float HORIZONTAL_OFFSET_DP = 16f;

  private final Drawable drawable;
  private final @PorterDuffColorId int iconColorId;

  private boolean isVisible = true;

  public EndIconModifier (@DrawableRes int iconRes, @PorterDuffColorId int colorId) {
    drawable = Drawables.get(iconRes);
    iconColorId = colorId;
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    if (!isVisible) return;
    Drawables.draw(c, drawable, getX(view), getY(view), PorterDuffPaint.get(iconColorId));
  }

  @Override
  public int getWidth () {
    return isVisible ? Screen.dp(HORIZONTAL_OFFSET_DP) * 2 + drawable.getIntrinsicWidth() : 0;
  }

  public void setVisible (boolean isVisible) {
    this.isVisible = isVisible;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (isVisible) {
      int x = getX(targetView);
      int y = getY(targetView);
      outRect.set(x, y, x + drawable.getIntrinsicWidth(), y + drawable.getIntrinsicHeight());
      outRect.inset(0, -Screen.dp(10f));
    } else {
      targetView.getDrawingRect(outRect);
    }
  }

  private int getX (View view) {
    return view.getWidth() - drawable.getIntrinsicWidth() - Screen.dp(HORIZONTAL_OFFSET_DP);
  }

  private int getY (View view) {
    return Math.round((view.getHeight() - drawable.getIntrinsicHeight()) / 2f);
  }
}
