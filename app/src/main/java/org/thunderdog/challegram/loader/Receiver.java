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
 * File created on 24/12/2016
 */
package org.thunderdog.challegram.loader;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.core.ColorUtils;
import tgx.td.data.StickerOutline;

public interface Receiver extends TooltipOverlayView.LocationProvider {
  View getTargetView ();
  int getTargetWidth ();
  int getTargetHeight ();

  <T extends Receiver> T setUpdateListener (ReceiverUpdateListener listener);

  int getLeft ();
  int getTop ();
  int getRight ();
  int getBottom ();

  void setRadius (float radius);

  void setTag (Object tag);
  Object getTag ();

  default int getWidth () {
    return getRight() - getLeft();
  }
  default int getHeight () {
    return getBottom() - getTop();
  }
  default int centerX () {
    return (int) ((float) (getLeft() + getRight()) * .5f);
  }
  default int centerY () {
    return (int) ((float) (getTop() + getBottom()) * .5f);
  }

  void attach ();
  void detach ();
  void clear ();
  void destroy ();

  boolean isEmpty ();

  default void toRect (Rect rect) {
    rect.set(getLeft(), getTop(), getRight(), getBottom());
  }

  default void setPorterDuffColorFilter (int colorOrColorId, float alpha, boolean colorIsId) {
    throw new UnsupportedOperationException();
  }
  default void setThemedPorterDuffColorId (@PorterDuffColorId int colorId) {
    setPorterDuffColorFilter(colorId, 1f, true);
  }
  default void setPorterDuffColorFilter (@ColorInt int color) {
    setPorterDuffColorFilter(color, 1f, false);
  }
  default void disablePorterDuffColorFilter () {
    setPorterDuffColorFilter(ColorId.NONE, 0f, true);
  }

  default void drawPlaceholderOutline (Canvas c, StickerOutline outline) {
    if (outline != null && outline.hasPath()) {
      drawPlaceholderContour(c, outline.getPath());
    }
  }

  default void drawPlaceholderContour (Canvas c, Path path) {
    drawPlaceholderContour(c, path, 1f);
  }

  default void drawPlaceholderContour (Canvas c, Path path, float alpha) {
    if (path != null) {
      int size = Math.min(getWidth(), getHeight());
      int left = centerX() - size / 2;
      int top = centerY() - size / 2;
      final boolean translate = left != 0 || top != 0;
      final int restoreToCount;
      if (translate) {
        restoreToCount = Views.save(c);
        c.translate(left, top);
      } else {
        restoreToCount = -1;
      }
      if (Config.DEBUG_STICKER_OUTLINES) {
        c.drawPath(path, Paints.fillingPaint(ColorUtils.alphaColor(alpha, 0x99ff0000)));
      } else {
        c.drawPath(path, alpha != 1f ? Paints.fillingPaint(ColorUtils.alphaColor(alpha, Theme.placeholderColor())) : Paints.getPlaceholderPaint());
      }
      if (translate) {
        Views.restore(c, restoreToCount);
      }
    }
  }
  default void drawPlaceholder (Canvas c) {
    drawPlaceholderRounded(c, 0);
  }
  default void drawPlaceholderRounded (Canvas c, float radius) {
    drawPlaceholderRounded(c, radius, Theme.placeholderColor());
  }
  default void drawPlaceholderRounded (Canvas c, float radius, int color) {
    drawPlaceholderRounded(c, radius, 0, Paints.fillingPaint(color));
  }
  default void drawPlaceholderRounded (Canvas c, float radius, int color, float addSize) {
    drawPlaceholderRounded(c, radius, addSize, Paints.fillingPaint(color));
  }
  default void drawPlaceholderRounded (Canvas c, float radius, float addSize, Paint paint) {
    if (radius > 0) {
      RectF rect = Paints.getRectF();
      rect.set(getLeft() - addSize, getTop() - addSize, getRight() + addSize, getBottom() + addSize);
      if (rect.width() == radius * 2 && rect.height() == radius * 2) {
        c.drawCircle(rect.centerX(), rect.centerY(), radius + addSize, paint);
      } else {
        c.drawRoundRect(rect, radius + addSize, radius + addSize, paint);
      }
    } else {
      c.drawRect(getLeft() - addSize, getTop() - addSize, getRight() + addSize, getBottom() + addSize, paint);
    }
  }

  boolean needPlaceholder ();
  void setAlpha (float alpha);
  float getAlpha ();
  void setAnimationDisabled (boolean disabled);
  boolean setBounds (int left, int top, int right, int bottom);
  default boolean setBoundsScaled (int left, int top, int right, int bottom, float scale) {
    if (scale == 1f) {
      return setBounds(left, top, right, bottom);
    } else {
      int width = right - left;
      int height = bottom - top;
      int centerX = left + width / 2;
      int centerY = top + height / 2;
      return setBounds(
        centerX - width / 2,
        centerY - height / 2,
        centerX + width / 2 + width % 2,
        centerY + height / 2 + height % 2
      );
    }
  }
  void forceBoundsLayout ();

  boolean isInsideContent (float x, float y, int emptyWidth, int emptyHeight);

  default boolean isInsideReceiver (float x, float y) {
    return x >= getLeft() && x <= getRight() && y >= getTop() && y <= getBottom();
  }

  void draw (Canvas c);

  default void drawScaled (Canvas c, float scale) {
    // Note: make sure placeholder is scaled as well when using this method
    if (scale == 1f) {
      draw(c);
    } else {
      int saveCount = Views.save(c);
      c.scale(scale, scale, centerX(), centerY());
      draw(c);
      Views.restore(c, saveCount);
    }
  }

  void invalidate ();

  @Override
  default void getTargetBounds (View targetView, Rect outRect) {
    outRect.set(getLeft(), getTop(), getRight(), getBottom());
  }

  float getPaintAlpha ();
  void setPaintAlpha (float alpha);
  void restorePaintAlpha ();
}
