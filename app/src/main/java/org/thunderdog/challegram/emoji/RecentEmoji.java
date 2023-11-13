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

import org.thunderdog.challegram.tool.Strings;

public class RecentEmoji {
  public final String emoji;
  public final RecentInfo info;
  public final boolean isCustomEmoji;
  public final long customEmojiId;

  public RecentEmoji (String emoji, RecentInfo info) {
    this.emoji = emoji;
    this.info = info;
    this.isCustomEmoji = emoji.startsWith(Emoji.CUSTOM_EMOJI_CACHE) || emoji.startsWith(Emoji.CUSTOM_EMOJI_CACHE_OLD);
    long customEmojiId = 0;
    if (isCustomEmoji) {
      try {
        customEmojiId = Long.parseLong(Strings.getNumber(emoji));
      } catch (Throwable ignored) {}
    }
    this.customEmojiId = customEmojiId;
  }

  public boolean isCustomEmoji () {
    return isCustomEmoji;
  }
}
