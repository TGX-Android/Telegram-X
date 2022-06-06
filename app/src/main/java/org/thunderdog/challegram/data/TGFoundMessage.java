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
 * File created on 16/07/2017
 */
package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;

import me.vkryl.core.StringUtils;

public class TGFoundMessage {
  private final TGFoundChat chat;
  private final TdApi.Message message;
  private final CharSequence text;

  public TGFoundMessage (Tdlib tdlib, TdApi.ChatList chatList, TdApi.Chat chat, TdApi.Message message, String query) {
    this.chat = new TGFoundChat(tdlib, chatList, chat, null);
    this.message = message;

    if (StringUtils.isEmpty(query)) {
      this.text = TD.buildShortPreview(tdlib, message, true);
      return;
    }

    CharSequence out;
    String copyText = TD.getTextFromMessage(message);
    if (!StringUtils.isEmpty(copyText)) {
      out = Strings.highlightWords(Strings.replaceNewLines(copyText), query, 0, InlineResultEmojiSuggestion.SPECIAL_SPLITTERS);
    } else {
      out = TD.buildShortPreview(tdlib, message, true);
    }

    text = Emoji.instance().replaceEmoji(out);
  }

  public long getId () {
    return message.id;
  }

  public TGFoundChat getChat () {
    return chat;
  }

  public CharSequence getText () {
    return text;
  }

  public ImageFile getAvatar () {
    return chat.getAvatar();
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholderMetadata () {
    return chat.getAvatarPlaceholderMetadata();
  }

  public TdApi.Message getMessage () {
    return message;
  }
}
