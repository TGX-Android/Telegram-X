package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.concurrent.atomic.AtomicLong;

/** Pure routing rules. A null topic denotes the whole chat, never General. */
public final class ForumNavigation {
  private ForumNavigation () { }

  // TDLib ForumTopicId::general(), an int32 forum identity, NOT a message/thread id.
  public static final int GENERAL_TOPIC_ID = 1;

  public static boolean openTabs (boolean isForum, boolean hasForumTabs, @Nullable TdApi.MessageTopic topic,
                                 boolean hasThread, boolean scheduled, boolean hasFilter, boolean hasPayload) {
    return isForum && hasForumTabs && (topic == null || topic instanceof TdApi.MessageTopicForum) &&
      !hasThread && !scheduled && !hasFilter && !hasPayload;
  }

  /** A saved common-stream anchor is not a user's request to change the selected tab. */
  public static boolean resolveAnchorTopic (boolean explicitAnchor, boolean hasForumTabs) {
    return explicitAnchor || !hasForumTabs;
  }

  /** Latest user navigation wins; delayed metadata must not reopen an older target. */
  public static final class RequestGate {
    private final AtomicLong sequence = new AtomicLong();
    public long begin () { return sequence.incrementAndGet(); }
    public boolean isCurrent (long ticket) { return ticket != 0 && ticket == sequence.get(); }
  }

  public static boolean openTopicList (boolean isForum, boolean viewAsTopics, @Nullable TdApi.MessageTopic topic,
                                       boolean hasThread, boolean hasMessage, boolean scheduled, boolean hasFilter,
                                       boolean hasPayload, boolean forceMessages) {
    return isForum && viewAsTopics && topic == null && !hasThread && !hasMessage && !scheduled &&
      !hasFilter && !hasPayload && !forceMessages;
  }

  public static @Nullable TdApi.MessageTopic linkTopic (TdApi.MessageLinkInfo link) {
    return link.topicId != null ? link.topicId : link.message != null ? link.message.topicId : null;
  }

  /** Do not infer a topic from a message in another chat (e.g. a linked discussion). */
  public static @Nullable TdApi.MessageTopic forumTopic (long chatId, @Nullable TdApi.Message message) {
    return message != null && message.chatId == chatId && ForumHistory.isForum(message.topicId) ? message.topicId : null;
  }

  public static @Nullable TdApi.MessageTopic notificationTopic (@Nullable TdApi.NotificationType type) {
    return type instanceof TdApi.NotificationTypeNewMessage ? ((TdApi.NotificationTypeNewMessage) type).message.topicId : null;
  }

  /** An explicit topic remains usable even if the notification's message was deleted. */
  public static @Nullable TdApi.MessageTopic replyTopic (long chatId, @Nullable TdApi.MessageTopic explicitTopic,
                                                        @Nullable TdApi.Message message) {
    return ForumHistory.isForum(explicitTopic) ? explicitTopic : forumTopic(chatId, message);
  }
}
