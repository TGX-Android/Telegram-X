package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;

import me.vkryl.core.StringUtils;

/**
 * Date: 7/16/17
 * Author: default
 */

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
