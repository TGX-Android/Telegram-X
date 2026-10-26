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
 */
package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import tgx.td.Td;

/** Topic identity for navigation. A null topic means the whole chat, not a wildcard. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class MessageTopics {
  private MessageTopics () {
  }

  @Nullable
  public static TdApi.MessageTopic effectiveTopic (
    @Nullable ThreadInfo threadInfo,
    @Nullable TdApi.MessageTopic topicId
  ) {
    return threadInfo != null ? threadInfo.getMessageTopicId() : topicId;
  }

  public static boolean sameChat (
    long chatId,
    @Nullable TdApi.MessageTopic topicId,
    long otherChatId,
    @Nullable TdApi.MessageTopic otherTopicId
  ) {
    return chatId == otherChatId && Td.equalsTo(topicId, otherTopicId);
  }

  public static boolean sameChat (
    long chatId,
    @Nullable TdApi.MessageTopic topicId,
    boolean scheduled,
    long otherChatId,
    @Nullable TdApi.MessageTopic otherTopicId,
    boolean otherScheduled
  ) {
    return scheduled == otherScheduled && sameChat(chatId, topicId, otherChatId, otherTopicId);
  }
}