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
 *
 * File created on 15/02/2018
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

public interface MessageListener {
  default void onNewMessage (TdApi.Message message) { }
  default void onMessageSendAcknowledged (long chatId, long messageId) { }
  default void onMessageSendSucceeded (TdApi.Message message, long oldMessageId) { }
  default void onMessageSendFailed (TdApi.Message message, long oldMessageId, TdApi.Error error) { }
  default void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) { }
  default void onMessageEdited (long chatId, long messageId, int editDate, @Nullable TdApi.ReplyMarkup replyMarkup) { }
  default void onMessagePinned (long chatId, long messageId, boolean isPinned) { }
  default void onMessageOpened (long chatId, long messageId) { }
  default void onAnimatedEmojiMessageClicked (long chatId, long messageId, TdApi.Sticker sticker) { }
  default void onMessageMentionRead (long chatId, long messageId) { }
  default void onMessageInteractionInfoChanged (long chatId, long messageId, @Nullable TdApi.MessageInteractionInfo interactionInfo) { }
  default void onMessageUnreadReactionsChanged (long chatId, long messageId, @Nullable TdApi.UnreadReaction[] unreadReactions, int unreadReactionCount) { }
  default void onMessagesDeleted (long chatId, long[] messageIds) { }
  default void onMessageLiveLocationViewed (long chatId, long messageId) { }
}
