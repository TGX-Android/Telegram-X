package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

public interface ForumTopicInfoListener {
  default void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) { }
  /**
   * Called when a forum topic's read/pin/notification/draft state changes.
   * Note: unreadCount is not provided by TDLib's UpdateForumTopic - it needs to be
   * fetched separately via GetForumTopic when lastReadInboxMessageId changes.
   */
  default void onForumTopicUpdated (long chatId, long messageThreadId, boolean isPinned, long lastReadInboxMessageId, long lastReadOutboxMessageId, int unreadMentionCount, int unreadReactionCount, TdApi.ChatNotificationSettings notificationSettings, @Nullable TdApi.DraftMessage draftMessage) { }
  /**
   * Called when the full forum topic data is refreshed (includes unreadCount).
   */
  default void onForumTopicFullyUpdated (long chatId, TdApi.ForumTopic topic) { }
}
