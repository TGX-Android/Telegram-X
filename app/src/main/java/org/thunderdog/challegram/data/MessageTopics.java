package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import tgx.td.Td;

/** Topic identity for navigation. A null topic means the whole chat, not a wildcard. */
public final class MessageTopics {
  private MessageTopics () { }

  @Nullable
  public static TdApi.MessageTopic effectiveTopic (@Nullable ThreadInfo threadInfo, @Nullable TdApi.MessageTopic topicId) {
    return threadInfo != null ? threadInfo.getMessageTopicId() : topicId;
  }

  public static boolean sameChat (long chatId, @Nullable TdApi.MessageTopic topicId, long otherChatId, @Nullable TdApi.MessageTopic otherTopicId) {
    return chatId == otherChatId && Td.equalsTo(topicId, otherTopicId);
  }

  public static boolean sameChat (long chatId, @Nullable TdApi.MessageTopic topicId, boolean scheduled, long otherChatId, @Nullable TdApi.MessageTopic otherTopicId, boolean otherScheduled) {
    return scheduled == otherScheduled && sameChat(chatId, topicId, otherChatId, otherTopicId);
  }
}
