package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;

import tgx.td.Td;

/** Pure history routing rules shared by the loader, albums and live updates. */
public final class ForumHistory {
  private ForumHistory () { }

  public static boolean isForum (@Nullable TdApi.MessageTopic topicId) {
    return topicId instanceof TdApi.MessageTopicForum;
  }

  public static TdApi.Function<TdApi.Messages> request (long chatId, @Nullable TdApi.MessageTopic topicId, long fromMessageId, int offset, int limit, boolean onlyLocal) {
    if (isForum(topicId)) {
      // Unlike GetChatHistory, the forum API has no local-only mode.
      return new TdApi.GetForumTopicHistory(chatId, ((TdApi.MessageTopicForum) topicId).forumTopicId, fromMessageId, offset, limit);
    }
    return new TdApi.GetChatHistory(chatId, fromMessageId, offset, limit, onlyLocal);
  }

  public static boolean matches (TdApi.Message message, long chatId, @Nullable TdApi.MessageTopic topicId, boolean scheduled) {
    return message != null && message.chatId == chatId &&
      (message.schedulingState != null) == scheduled && Td.matchesTopic(message.topicId, topicId);
  }

  public static TdApi.Message[] filter (TdApi.Message[] messages, long chatId, @Nullable TdApi.MessageTopic topicId, boolean scheduled) {
    if (!isForum(topicId)) return messages;
    ArrayList<TdApi.Message> result = new ArrayList<>(messages.length);
    for (TdApi.Message message : messages) {
      if (matches(message, chatId, topicId, scheduled)) result.add(message);
    }
    return result.toArray(new TdApi.Message[0]);
  }

  public static boolean sameAlbum (TdApi.Message anchor, TdApi.Message message) {
    return anchor.mediaAlbumId != 0 && anchor.mediaAlbumId == message.mediaAlbumId &&
      matches(message, anchor.chatId, anchor.topicId, anchor.schedulingState != null);
  }

  @Nullable
  public static TdApi.MessageTopic outgoingTopic (@Nullable TdApi.MessageTopic current, @Nullable TdApi.MessageTopic replyTopic) {
    // An external reply must never move a send out of the currently open forum topic.
    return isForum(current) || replyTopic == null ? current : replyTopic;
  }
}
