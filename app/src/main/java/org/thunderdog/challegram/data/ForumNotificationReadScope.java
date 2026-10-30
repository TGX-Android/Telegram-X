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

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Notification read scopes, independent of the group's open/reply topic. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class ForumNotificationReadScope {
  private ForumNotificationReadScope () {
  }

  /**
   * Returns every represented forum topic, or null when a topic-only read cannot cover the group.
   * TDLib's totalCount may exceed the cached notifications. Push notifications have usable message
   * IDs, but no topic; neither case is evidence that the latest message's topic covers the group.
   */
  public static int[] topicIds (int totalCount, List<TdApi.NotificationType> notifications) {
    if (notifications == null || notifications.isEmpty() || totalCount > notifications.size()) {
      return null;
    }
    Set<Integer> topicIds = new LinkedHashSet<>();
    for (TdApi.NotificationType notification : notifications) {
      if (!(notification instanceof TdApi.NotificationTypeNewMessage)) {
        return null;
      }
      TdApi.Message message = ((TdApi.NotificationTypeNewMessage) notification).message;
      if (message == null || !(message.topicId instanceof TdApi.MessageTopicForum)) {
        return null;
      }
      int topicId = ((TdApi.MessageTopicForum) message.topicId).forumTopicId;
      if (topicId <= 0) {
        return null;
      }
      topicIds.add(topicId);
    }
    int[] result = new int[topicIds.size()];
    int index = 0;
    for (int topicId : topicIds) {
      result[index++] = topicId;
    }
    return result;
  }

  /**
   * Read mentions, not ordinary message history. Unknown/incomplete scopes (including legacy
   * intents
   * and push-only groups) retain the pre-forum, chat-wide mention read. That deliberately also
   * clears
   * unrepresented mentions, instead of dismissing notifications whose topic was never handled.
   */
  public static List<TdApi.Function<TdApi.Ok>> requests (long chatId, int[] forumTopicIds) {
    Set<Integer> topicIds = new LinkedHashSet<>();
    if (forumTopicIds != null) {
      for (int topicId : forumTopicIds) {
        if (topicId <= 0) {
          return Collections.singletonList(new TdApi.ReadAllChatMentions(chatId));
        }
        topicIds.add(topicId);
      }
    }
    if (topicIds.isEmpty()) {
      return Collections.singletonList(new TdApi.ReadAllChatMentions(chatId));
    }
    List<TdApi.Function<TdApi.Ok>> requests = new ArrayList<>(topicIds.size());
    for (int topicId : topicIds) {
      requests.add(new TdApi.ReadAllForumTopicMentions(chatId, topicId));
    }
    return requests;
  }

  /**
   * Resolve ordinary notifications before reading them: MessageSourceNotification reads the chat,
   * not its forum topics. Each topic needs a separate ViewMessages request, including General.
   * Missing or mismatched messages must not fall back to reading an unrelated topic or whole chat.
   */
  @me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
  public static List<TdApi.ViewMessages> messageRequests (
    long chatId,
    long[] messageIds,
    TdApi.Message[] messages
  ) {
    if (messageIds == null ||
      messageIds.length == 0 ||
      messages == null ||
      messages.length != messageIds.length) {
      return null;
    }
    Map<Integer, Set<Long>> groups = new LinkedHashMap<>();
    for (int i = 0; i < messageIds.length; i++) {
      TdApi.Message message = messages[i];
      if (message == null || message.id != messageIds[i] || message.chatId != chatId) {
        return null;
      }
      int topicId = 0;
      if (message.topicId instanceof TdApi.MessageTopicForum) {
        topicId = ((TdApi.MessageTopicForum) message.topicId).forumTopicId;
        if (topicId <= 0) {
          return null;
        }
      }
      Set<Long> group = groups.get(topicId);
      if (group == null) {
        group = new LinkedHashSet<>();
        groups.put(topicId, group);
      }
      group.add(message.id);
    }
    List<TdApi.ViewMessages> requests = new ArrayList<>(groups.size());
    for (Map.Entry<Integer, Set<Long>> entry : groups.entrySet()) {
      long[] ids = new long[entry.getValue().size()];
      int index = 0;
      for (long messageId : entry.getValue()) {
        ids[index++] = messageId;
      }
      TdApi.MessageSource source = entry.getKey() != 0 ?
        new TdApi.MessageSourceForumTopicHistory() : new TdApi.MessageSourceNotification();
      requests.add(new TdApi.ViewMessages(chatId, ids, source, true));
    }
    return requests;
  }

  /** Never dismiss the whole group after only some of its read requests succeeded. */
  public static final class Completion {
    private int remaining;
    private boolean failed;

    public Completion (int requestCount) {
      if (requestCount <= 0) {
        throw new IllegalArgumentException("Empty notification read scope");
      }
      remaining = requestCount;
    }

    public synchronized boolean onResult (boolean success) {
      if (remaining == 0) {
        return false;
      }
      failed |= !success;
      return --remaining == 0 && !failed;
    }
  }
}