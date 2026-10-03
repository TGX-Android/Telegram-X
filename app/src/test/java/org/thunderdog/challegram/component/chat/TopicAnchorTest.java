package org.thunderdog.challegram.component.chat;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

public class TopicAnchorTest {
  @Test
  public void topicDoesNotInheritWholeChatUnreadAnchor () {
    TdApi.Chat chat = new TdApi.Chat();
    chat.id = 100;
    chat.unreadCount = 5;
    chat.lastReadInboxMessageId = 200;
    chat.lastMessage = new TdApi.Message();
    chat.lastMessage.id = 201;
    assertTrue(MessagesManager.canGoUnread(chat, null, null));
    for (TdApi.MessageTopic topic : new TdApi.MessageTopic[] {
      new TdApi.MessageTopicForum(17), new TdApi.MessageTopicSavedMessages(17),
      new TdApi.MessageTopicDirectMessages(17)
    }) {
      assertFalse(MessagesManager.canGoUnread(chat, null, topic));
      assertNull(MessagesManager.getAnchorMessageId(0, chat, null, topic, MessagesManager.HIGHLIGHT_MODE_UNREAD));
      assertNull(MessagesManager.getAnchorMessageId(0, chat, null, topic, MessagesManager.HIGHLIGHT_MODE_UNREAD_NEXT));
    }
  }
}
