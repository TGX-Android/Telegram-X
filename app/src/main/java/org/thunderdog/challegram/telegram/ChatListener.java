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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

public interface ChatListener extends ForumTopicInfoListener {
  default void onChatTopMessageChanged (long chatId, @Nullable TdApi.Message topMessage) { }
  default void onChatPositionChanged (long chatId, TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged) { }
  default void onChatAddedToList (long chatId, TdApi.ChatList chatList) { }
  default void onChatRemovedFromList (long chatId, TdApi.ChatList chatList) { }
  default void onChatPermissionsChanged (long chatId, TdApi.ChatPermissions permissions) { }
  default void onChatTitleChanged (long chatId, String title) { }
  default void onChatThemeChanged (long chatId, String themeName) { }

  default void onChatBackgroundChanged (long chatId, @Nullable TdApi.ChatBackground background) { }
  default void onChatAccentColorsChanged (long chatId,
                                          int accentColorId, long backgroundCustomEmojiId,
                                          int profileAccentColorId, long profileBackgroundCustomEmojiId) { }
  default void onChatEmojiStatusChanged (long chatId, @Nullable TdApi.EmojiStatus emojiStatus) { }

  default void onChatBackgroundCustomEmojiChanged (long chatId, long customEmojiId) { }
  default void onChatActionBarChanged (long chatId, TdApi.ChatActionBar actionBar) { }
  default void onChatBusinessBotManageBarChanged (long chatId, @Nullable TdApi.BusinessBotManageBar botManageBar) { }
  default void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) { }
  default void onChatReadInbox (long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) { }
  default void onChatHasScheduledMessagesChanged (long chatId, boolean hasScheduledMessages) { }
  default void onChatHasProtectedContentChanged (long chatId, boolean hasProtectedContent) { }
  default void onChatReadOutbox (long chatId, long lastReadOutboxMessageId) { }
  default void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) { }
  default void onChatIsTranslatableChanged (long chatId, boolean isTranslatable) { }
  default void onChatBlockListChanged (long chatId, @Nullable TdApi.BlockList blockList) { }
  default void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) { }
  default void onChatMessageTtlSettingChanged (long chatId, int messageTtlSetting) { }
  default void onChatActiveStoriesChanged (@NonNull TdApi.ChatActiveStories activeStories) { }
  default void onChatVideoChatChanged (long chatId, TdApi.VideoChat videoChat) { }
  default void onChatViewAsTopics (long chatId, boolean viewAsTopics) { }
  default void onChatPendingJoinRequestsChanged (long chatId, TdApi.ChatJoinRequestsInfo pendingJoinRequests) { }
  default void onChatReplyMarkupChanged (long chatId, long replyMarkupMessageId) { }
  default void onChatDraftMessageChanged (long chatId, @Nullable TdApi.DraftMessage draftMessage) { }
  default void onChatUnreadMentionCount(long chatId, int unreadMentionCount, boolean availabilityChanged) { }
  default void onChatUnreadReactionCount (long chatId, int unreadReactionCount, boolean availabilityChanged) { }
  default void onChatDefaultDisableNotifications (long chatId, boolean defaultDisableNotifications) { }
  default void onChatDefaultMessageSenderIdChanged (long chatId, TdApi.MessageSender senderId) { }
  default void onChatClientDataChanged (long chatId, @Nullable String clientData)                             { }
  default void onChatAvailableReactionsUpdated (long chatId, TdApi.ChatAvailableReactions availableReactions) { }
}
