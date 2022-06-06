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

import android.graphics.Rect;

public class EmojiInfo {
  public final Rect rect;
  public final int page1, page2;

  public EmojiInfo (Rect rect, int page1, int page2) {
    this.rect = rect;
    this.page1 = page1;
    this.page2 = page2;
  }
}
