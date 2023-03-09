/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 22/01/2017
 */
package org.thunderdog.challegram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.ColorUtils;

public class FillingDrawable extends Drawable {
  @ThemeColorId
  private int colorId;

  @Nullable
  private ThemeDelegate forcedTheme;

  private float cornerRadius;

  public FillingDrawable (int colorId) {
    this.colorId = colorId;
  }

  public final void setForcedTheme (ThemeDelegate forcedTheme) {
    if (this.forcedTheme != forcedTheme) {
      this.forcedTheme = forcedTheme;
      invalidateSelf();
    }
  }

  public void setCornerRadius (float radius) {
    if (this.cornerRadius != radius) {
      this.cornerRadius = radius;
      invalidateSelf();
    }
  }

  @ThemeColorId
  public final int getColorId () {
    return colorId;
  }

  public final void setColorId (int colorId) {
    if (this.colorId != colorId) {
      this.colorId = colorId;
      invalidateSelf();
    }
  }

  public void setAlphaFactor (float alpha) {
    if (this.alpha != alpha) {
      this.alpha = alpha;
      invalidateSelf();
    }
  }

  protected int getFillingColor () {
    return ColorUtils.alphaColor(alpha, forcedTheme != null ? forcedTheme.getColor(colorId) : Theme.getColor(colorId));
  }

  @Override
  public final void draw (@NonNull Canvas c) {
    if (colorId != 0) {
      if (cornerRadius != 0) {
        RectF rectF = Paints.getRectF();
        rectF.set(getBounds());
        float radius = Screen.dp(cornerRadius);
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(getFillingColor()));
      } else {
        c.drawRect(getBounds(), Paints.fillingPaint(getFillingColor()));
      }
    }
  }

  private float alpha = 1f;

  @Override
  public final void setAlpha (int alpha) { }

  @Override
  public final void setColorFilter (ColorFilter colorFilter) { }

  @Override
  public final int getOpacity () {
    return PixelFormat.UNKNOWN;
  }

  public static void changeColor (View view, @ThemeColorId int newColorId) {
    if (view != null) {
      Drawable drawable = view.getBackground();
      if (drawable instanceof FillingDrawable) {
        FillingDrawable fillingDrawable = (FillingDrawable) drawable;
        if (fillingDrawable.colorId != newColorId) {
          fillingDrawable.colorId = newColorId;
          view.invalidate();
        }
      }
    }
  }
}
