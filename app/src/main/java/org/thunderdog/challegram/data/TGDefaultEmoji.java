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
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.data;

import org.thunderdog.challegram.tool.EmojiData;

public class TGDefaultEmoji {
  public final int strRes;
  public final String emoji;
  public final int emojiColorState;
  public final boolean isRecent;

  public TGDefaultEmoji (String emoji) {
    this.emoji = emoji;
    this.emojiColorState = EmojiData.instance().getEmojiColorState(emoji);
    this.strRes = 0;
    this.isRecent = false;
  }

  public TGDefaultEmoji (String emoji, boolean isRecent) {
    this.emoji = emoji;
    this.emojiColorState = EmojiData.instance().getEmojiColorState(emoji);
    this.strRes = 0;
    this.isRecent = isRecent;
  }

  public boolean canBeColored () {
    return emojiColorState != EmojiData.STATE_NO_COLORS;
  }
}
