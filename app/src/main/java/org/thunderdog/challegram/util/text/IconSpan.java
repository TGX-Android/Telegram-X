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
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.text.style.DynamicDrawableSpan;
import android.view.Gravity;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.PorterDuffPaint;

import me.vkryl.core.MathUtils;

public class IconSpan extends DynamicDrawableSpan implements TextReplacementSpan {

  private static final int MAX_LEVEL = 10000;

  private final ScaleDrawable drawable;
  private final @PorterDuffColorId int iconColorId;
  private int overrideColor;
  private boolean useOverrideColor;

  public IconSpan (@DrawableRes int iconRes, @PorterDuffColorId int iconColorId) {
    super(DynamicDrawableSpan.ALIGN_BOTTOM);
    this.drawable = new ScaleDrawable(Drawables.get(iconRes), Gravity.CENTER, 1.0f, 1.0f);
    this.drawable.setLevel(MAX_LEVEL);
    this.drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    this.iconColorId = iconColorId;
  }

  @Override
  public Drawable getDrawable () {
    return drawable;
  }

  public void setAlpha (@FloatRange(from = 0.0, to = 1.0) float alpha) {
    Drawables.setAlpha(drawable, MathUtils.clamp((int) (0xFF * alpha), 0x00, 0xFF));
  }

  public void setScale (@FloatRange(from = 0.0, to = 1.0) float scale) {
    drawable.setLevel(MathUtils.clamp((int) (scale * MAX_LEVEL), 0, MAX_LEVEL));
  }

  public void setOverrideColor (@ColorInt int color) {
    useOverrideColor = true;
    overrideColor = color;
  }

  public void resetOverrideColor () {
    useOverrideColor = false;
    overrideColor = Color.MAGENTA;
  }

  @Override
  public void draw (@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
    if (useOverrideColor) {
      drawable.setColorFilter(overrideColor, PorterDuff.Mode.SRC_IN);
    } else if (iconColorId != ColorId.NONE) {
      drawable.setColorFilter(PorterDuffPaint.get(iconColorId).getColorFilter());
    } else {
      drawable.setColorFilter(paint.getColor(), PorterDuff.Mode.SRC_IN);
    }
    super.draw(canvas, text, start, end, x, top, y, bottom, paint);
  }

  @Override
  public int getSize (@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
    if (fm != null) {
      paint.getFontMetricsInt(fm);
    }
    return getRawSize(paint);
  }

  @Override
  public int getRawSize (Paint paint) {
    return drawable.getIntrinsicWidth();
  }
}
