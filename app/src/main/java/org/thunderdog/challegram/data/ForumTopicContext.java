package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import tgx.td.MessageId;

/** One open topic. A new instance is also the epoch token for asynchronous UI work. */
public final class ForumTopicContext {
  public final long chatId;
  public final int topicId;
  private volatile TdApi.ForumTopic topic;
  private TdApi.Error error;
  private boolean hasLocalDraft;
  private TdApi.DraftMessage localDraft;

  public ForumTopicContext (long chatId, int topicId) {
    this.chatId = chatId;
    this.topicId = topicId;
  }

  public boolean update (@Nullable TdApi.ForumTopic value, @Nullable TdApi.Error error) {
    if (value != null && (value.info.chatId != chatId || value.info.forumTopicId != topicId)) return false;
    this.topic = value;
    this.error = error;
    return true;
  }

  @Nullable public TdApi.ForumTopic topic () { return topic; }
  @Nullable public TdApi.Error error () { return error; }
  public boolean isLoading () { return topic == null && error == null; }
  public boolean hasLocalDraft () { return hasLocalDraft; }
  public void setLocalDraft (@Nullable TdApi.DraftMessage draft) { hasLocalDraft = true; localDraft = draft; }
  public void acknowledgeDraft (@Nullable TdApi.DraftMessage draft) {
    if (hasLocalDraft && localDraft == draft) { hasLocalDraft = false; localDraft = null; }
  }
  @Nullable public TdApi.DraftMessage draft () { return hasLocalDraft ? localDraft : topic != null ? topic.draftMessage : null; }
  public long lastReadInbox () { TdApi.ForumTopic value = topic; return value != null ? value.lastReadInboxMessageId : 0; }
  public long lastReadOutbox () { TdApi.ForumTopic value = topic; return value != null ? value.lastReadOutboxMessageId : 0; }
  public long lastMessageId () { TdApi.ForumTopic value = topic; return value != null && value.lastMessage != null ? value.lastMessage.id : 0; }

  public static boolean canGoUnread (@Nullable TdApi.ForumTopic topic) {
    return topic != null && topic.unreadCount > 0 && topic.lastReadInboxMessageId != MessageId.MAX_VALID_ID;
  }

  public boolean canSend (@Nullable TdApi.ChatMemberStatus status, boolean supergroup) {
    if (topic == null || error != null) return false;
    if (!supergroup || !topic.info.isClosed || topic.info.isOutgoing) return true;
    return status instanceof TdApi.ChatMemberStatusCreator ||
      status instanceof TdApi.ChatMemberStatusAdministrator && ((TdApi.ChatMemberStatusAdministrator) status).rights.canManageTopics;
  }
}
