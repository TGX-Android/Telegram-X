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
 * File created on 28/10/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;

import me.vkryl.core.ColorUtils;

public class RippleRevealView extends View {
  private @ColorId int colorId = ColorId.filling;
  private float revealFactor;

  public RippleRevealView (Context context) {
    super(context);
  }

  public void setRevealFactor (float factor) {
    if (this.revealFactor != factor) {
      this.revealFactor = factor;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (revealFactor > 0f) {
      final int color = Theme.getColor(colorId);
      c.drawColor(ColorUtils.alphaColor(revealFactor, color));

      float width = getMeasuredWidth();
      float height = getMeasuredHeight();
      float radius = (float) Math.sqrt(width * width + height * height) * .5f;
      c.drawCircle(width / 2, height / 2, radius * revealFactor, Paints.fillingPaint(color));
    }
  }
}
