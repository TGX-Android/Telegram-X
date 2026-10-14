package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Mention-read scope for a notification group, independent of its open/reply topic. */
public final class ForumNotificationReadScope {
  private ForumNotificationReadScope () { }

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
   * Read mentions, not ordinary message history. Unknown/incomplete scopes (including legacy intents
   * and push-only groups) retain the pre-forum, chat-wide mention read. That deliberately also clears
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
