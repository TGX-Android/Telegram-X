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
 * File created on 04/05/2019
 */
package org.thunderdog.challegram.emoji;

import android.graphics.Rect;

import org.thunderdog.challegram.tool.EmojiCode;

public class EmojiInfo {
  public final int section, page;
  public final int position;
  private final Rect rect = new Rect();
  private int inSampleSize = -1;

  public EmojiInfo (int section, int page, int position) {
    this.section = section;
    this.page = page;
    this.position = position;
  }

  public Rect getRect (int inSampleSize) {
    if (this.inSampleSize != inSampleSize) {
      this.inSampleSize = inSampleSize;
      int emojiOriginalSize = (int) (30 * EmojiCode.SCALE) / inSampleSize;

      int row = position % EmojiCode.COLUMNS[section][page];
      int col = position / EmojiCode.COLUMNS[section][page];

      int margin = (int) (EmojiCode.MARGINS[section][page] * (EmojiCode.SCALE / inSampleSize));

      int marginLeft = margin * row;
      int marginTop = margin * col;

      int left = row * emojiOriginalSize + marginLeft;
      int top = col * emojiOriginalSize + marginTop;

      rect.set(
        left,
        top,
        left + emojiOriginalSize,
        top + emojiOriginalSize
      );
    }
    return rect;
  }
}
