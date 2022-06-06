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
 * File created on 04/05/2019
 */
package org.thunderdog.challegram.emoji;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Screen;

public class EmojiDrawable extends Drawable {
  private final EmojiInfo info;

  public EmojiDrawable (EmojiInfo info) {
    this.info = info;
  }

  @Override
  public void setAlpha (int alpha) { }

  @Override
  public void setColorFilter (ColorFilter colorFilter) { }

  @Override
  public int getOpacity () {
    return PixelFormat.TRANSPARENT;
  }

  @Override
  public void draw (@NonNull Canvas c) {
    Emoji.instance().draw(c, info, getBounds());
  }

  @Override
  public int getIntrinsicWidth () {
    return Screen.dp(24f);
  }

  @Override
  public int getIntrinsicHeight () {
    return Screen.dp(24f);
  }
}
