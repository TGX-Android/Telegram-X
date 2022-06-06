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
 * File created on 12/11/2018
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

public class LineDrawModifier implements DrawModifier {
  @ThemeColorId
  private final int colorId;

  private ThemeDelegate forcedTheme;

  public LineDrawModifier (@ThemeColorId int colorId, @Nullable ThemeDelegate forcedTheme) {
    this.colorId = colorId;
    this.forcedTheme = forcedTheme;
  }

  @Override
  public void beforeDraw (View view, Canvas c) {
    RectF rectF = Paints.getRectF();

    rectF.top = Screen.dp(12f);
    rectF.bottom = view.getMeasuredHeight() - rectF.top;
    int width = Screen.dp(3f);
    int margin = Screen.dp(14f);
    if (Lang.rtl()) {
      rectF.left = view.getMeasuredWidth() - margin - width;
    } else {
      rectF.left = margin;
    }
    rectF.right = rectF.left + width;

    c.drawRoundRect(rectF, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(forcedTheme != null ? forcedTheme.getColor(colorId) : Theme.getColor(colorId)));

    c.save();
    c.translate(Screen.dp(8f) * (Lang.rtl() ? -1 : 1), 0);
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    c.restore();
  }
}
