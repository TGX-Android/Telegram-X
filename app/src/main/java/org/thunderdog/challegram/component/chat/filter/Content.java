package org.thunderdog.challegram.component.chat.filter;

import org.drinkless.tdlib.TdApi;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

class Content {
  public final Set<String> mentionsUsername = new HashSet<>();
  public final Set<Long> mentionsId = new HashSet<>();
  public final Set<String> links = new HashSet<>();

  // private final Set<String> internalLinks = new HashSet<>();
  // private final Set<String> externalLinks = new HashSet<>();

  public Content () {}

  public void add (TdApi.MessageContent content) {
    final TdApi.FormattedText textOrCaption = Td.textOrCaption(content);
    if (textOrCaption == null || textOrCaption.text == null) return;

    if (textOrCaption.entities != null) {
      for (TdApi.TextEntity entity : textOrCaption.entities) {
        switch (entity.type.getConstructor()) {
          case TdApi.TextEntityTypeMention.CONSTRUCTOR: {
            mentionsUsername.add(Td.substring(textOrCaption.text, entity));
            break;
          }
          case TdApi.TextEntityTypeMentionName.CONSTRUCTOR: {
            mentionsId.add(ChatId.fromUserId(((TdApi.TextEntityTypeMentionName) entity.type).userId));
            break;
          }
          case TdApi.TextEntityTypeUrl.CONSTRUCTOR: {
            addLink(Td.substring(textOrCaption.text, entity));
            break;
          }
          case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: {
            addLink(((TdApi.TextEntityTypeTextUrl) entity.type).url);
            break;
          }
          default: {
            break;
          }
        }
      }
    }
  }

  private void addLink (String link) {
    links.add(link);
  }
}
