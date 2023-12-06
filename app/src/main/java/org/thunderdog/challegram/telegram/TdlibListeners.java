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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceIntMap;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceLongMap;
import me.vkryl.core.reference.ReferenceMap;
import me.vkryl.td.Td;

public class TdlibListeners {
  private final Tdlib tdlib;

  final ReferenceList<MessageListener> messageListeners;
  final ReferenceList<MessageEditListener> messageEditListeners;
  final ReferenceList<ChatListener> chatListeners;
  final ReferenceList<ChatFoldersListener> chatFoldersListeners;
  final ReferenceMap<String, ChatListListener> chatListListeners;
  final ReferenceList<StoryListener> storyListeners;
  final ReferenceList<NotificationSettingsListener> settingsListeners;
  final ReferenceList<StickersListener> stickersListeners;
  final ReferenceList<AnimationsListener> animationsListeners;
  final ReferenceList<ConnectionListener> connectionListeners;
  final ReferenceList<AuthorizationListener> authorizationListeners;
  final ReferenceList<CleanupStartupDelegate> componentDelegates;
  final ReferenceList<TdlibOptionListener> optionListeners;
  final ReferenceList<CounterChangeListener> totalCountersListeners;
  final ReferenceList<ChatsNearbyListener> chatsNearbyListeners;
  final ReferenceList<PrivacySettingsListener> privacySettingsListeners;
  final ReferenceList<PrivateCallListener> privateCallListeners;
  final ReferenceIntMap<PrivateCallListener> specificPrivateCallListeners;
  final ReferenceList<GroupCallListener> groupCallListeners;
  final ReferenceIntMap<GroupCallListener> specificGroupCallListeners;
  final ReferenceList<SessionListener> sessionListeners;
  final ReferenceList<DownloadsListUpdateListener> downloadsListListener;

  final ReferenceList<AnimatedEmojiListener> animatedEmojiListeners;

  final ReferenceLongMap<MessageListener> messageChatListeners;
  final ReferenceLongMap<MessageEditListener> messageEditChatListeners;
  final ReferenceLongMap<ChatListener> specificChatListeners;
  final ReferenceMap<String, StoryListener> specificStoryListeners;
  final ReferenceMap<String, ForumTopicInfoListener> specificForumTopicListeners;
  final ReferenceLongMap<NotificationSettingsListener> chatSettingsListeners;
  final ReferenceIntMap<FileUpdateListener> fileUpdateListeners;
  final ReferenceLongMap<PollListener> pollListeners;

  final ReferenceMap<String, ReactionLoadListener> reactionLoadListeners;

  final Map<String, List<TdApi.Message>> pendingMessages = new HashMap<>();

  public TdlibListeners (Tdlib tdlib) {
    this.tdlib = tdlib;

    this.messageListeners = new ReferenceList<>();
    this.messageEditListeners = new ReferenceList<>();
    this.chatListeners = new ReferenceList<>();
    this.storyListeners = new ReferenceList<>();
    this.chatListListeners = new ReferenceMap<>(true);
    this.chatFoldersListeners = new ReferenceList<>(true);
    this.settingsListeners = new ReferenceList<>(true);
    this.stickersListeners = new ReferenceList<>(true);
    this.animationsListeners = new ReferenceList<>();
    this.connectionListeners = new ReferenceList<>(true);
    this.authorizationListeners = new ReferenceList<>(true);
    this.componentDelegates = new ReferenceList<>(true);
    this.optionListeners = new ReferenceList<>(true);
    this.totalCountersListeners = new ReferenceList<>(true);
    this.chatsNearbyListeners = new ReferenceList<>(true);
    this.privacySettingsListeners = new ReferenceList<>();
    this.privateCallListeners = new ReferenceList<>(true);
    this.specificPrivateCallListeners = new ReferenceIntMap<>(true);
    this.groupCallListeners = new ReferenceList<>(true);
    this.specificGroupCallListeners = new ReferenceIntMap<>(true);
    this.sessionListeners = new ReferenceList<>(true);
    this.downloadsListListener = new ReferenceList<>(true);

    this.animatedEmojiListeners = new ReferenceList<>(true);

    this.reactionLoadListeners = new ReferenceMap<>(true);

    this.messageChatListeners = new ReferenceLongMap<>();
    this.messageEditChatListeners = new ReferenceLongMap<>();
    this.specificChatListeners = new ReferenceLongMap<>();
    this.specificStoryListeners = new ReferenceMap<>();
    this.specificForumTopicListeners = new ReferenceMap<>(true);
    this.chatSettingsListeners = new ReferenceLongMap<>(true);
    this.fileUpdateListeners = new ReferenceIntMap<>();
    this.pollListeners = new ReferenceLongMap<>();
  }

  public void subscribeToUpdates (TdApi.Message message) {
    String key = message.chatId + "_" + message.id;
    List<TdApi.Message> messages = pendingMessages.get(key);
    if (messages == null) {
      messages = new ArrayList<>();
      pendingMessages.put(key, messages);
    }
    messages.add(message);
  }

  public void unsubscribeFromUpdates (TdApi.Message message) {
    String key = message.chatId + "_" + message.id;
    List<TdApi.Message> messages = pendingMessages.get(key);
    if (messages != null && messages.remove(message) && messages.isEmpty()) {
      pendingMessages.remove(key);
    }
  }

  @AnyThread
  public void subscribeForAnyUpdates (Object any) {
    synchronized (this) {
      if (any instanceof MessageListener) {
        messageListeners.add((MessageListener) any);
      }
      if (any instanceof MessageEditListener) {
        messageEditListeners.add((MessageEditListener) any);
      }
      if (any instanceof ChatListener) {
        chatListeners.add((ChatListener) any);
      }
      if (any instanceof NotificationSettingsListener) {
        settingsListeners.add((NotificationSettingsListener) any);
      }
      if (any instanceof StickersListener) {
        stickersListeners.add((StickersListener) any);
      }
      if (any instanceof AnimationsListener) {
        animationsListeners.add((AnimationsListener) any);
      }
      if (any instanceof ConnectionListener) {
        connectionListeners.add((ConnectionListener) any);
      }
      if (any instanceof TdlibOptionListener) {
        optionListeners.add((TdlibOptionListener) any);
      }
      if (any instanceof CounterChangeListener) {
        totalCountersListeners.add((CounterChangeListener) any);
      }
      if (any instanceof ChatsNearbyListener) {
        chatsNearbyListeners.add((ChatsNearbyListener) any);
      }
      if (any instanceof AnimatedEmojiListener) {
        animatedEmojiListeners.add((AnimatedEmojiListener) any);
      }
      if (any instanceof PrivacySettingsListener) {
        privacySettingsListeners.add((PrivacySettingsListener) any);
      }
      if (any instanceof PrivateCallListener) {
        privateCallListeners.add((PrivateCallListener) any);
      }
      if (any instanceof GroupCallListener) {
        groupCallListeners.add((GroupCallListener) any);
      }
      if (any instanceof SessionListener) {
        sessionListeners.add((SessionListener) any);
      }
    }
  }

  @AnyThread
  public void unsubscribeFromAnyUpdates (Object any) {
    synchronized (this) {
      if (any instanceof MessageListener) {
        messageListeners.remove((MessageListener) any);
      }
      if (any instanceof MessageEditListener) {
        messageEditListeners.remove((MessageEditListener) any);
      }
      if (any instanceof ChatListener) {
        chatListeners.remove((ChatListener) any);
      }
      if (any instanceof NotificationSettingsListener) {
        settingsListeners.remove((NotificationSettingsListener) any);
      }
      if (any instanceof StickersListener) {
        stickersListeners.remove((StickersListener) any);
      }
      if (any instanceof StickersListener) {
        animationsListeners.remove((AnimationsListener) any);
      }
      if (any instanceof ConnectionListener) {
        connectionListeners.remove((ConnectionListener) any);
      }
      if (any instanceof TdlibOptionListener) {
        optionListeners.remove((TdlibOptionListener) any);
      }
      if (any instanceof CounterChangeListener) {
        totalCountersListeners.remove((CounterChangeListener) any);
      }
      if (any instanceof ChatsNearbyListener) {
        chatsNearbyListeners.remove((ChatsNearbyListener) any);
      }
      if (any instanceof AnimatedEmojiListener) {
        animatedEmojiListeners.remove((AnimatedEmojiListener) any);
      }
      if (any instanceof PrivacySettingsListener) {
        privacySettingsListeners.remove((PrivacySettingsListener) any);
      }
      if (any instanceof PrivateCallListener) {
        privateCallListeners.remove((PrivateCallListener) any);
      }
      if (any instanceof GroupCallListener) {
        groupCallListeners.remove((GroupCallListener) any);
      }
      if (any instanceof SessionListener) {
        sessionListeners.remove((SessionListener) any);
      }
    }
  }

  @AnyThread
  public void addAuthorizationChangeListener (AuthorizationListener listener) {
    authorizationListeners.add(listener);
  }

  @AnyThread
  public void removeAuthorizationChangeListener (AuthorizationListener listener) {
    authorizationListeners.remove(listener);
  }

  @AnyThread
  public void subscribeToSessionUpdates (SessionListener listener) {
    sessionListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromSessionUpdates (SessionListener listener) {
    sessionListeners.remove(listener);
  }

  void notifyAllSessionsTerminated (TdApi.Session currentSession) {
    for (SessionListener listener : sessionListeners) {
      listener.onAllOtherSessionsTerminated(tdlib, currentSession);
    }
  }

  void notifySessionCreatedViaQrCode (TdApi.Session newSession) {
    for (SessionListener listener : sessionListeners) {
      listener.onSessionCreatedViaQrCode(tdlib, newSession);
    }
  }

  void notifySessionTerminated (TdApi.Session session) {
    for (SessionListener listener : sessionListeners) {
      listener.onSessionTerminated(tdlib, session);
    }
  }

  void notifyInactiveSessionTtlChanged (int ttlDays) {
    for (SessionListener listener : sessionListeners) {
      listener.onInactiveSessionTtlChanged(tdlib, ttlDays);
    }
  }

  void notifySessionListPossiblyChanged (boolean isWeakGuess) {
    for (SessionListener listener : sessionListeners) {
      listener.onSessionListChanged(tdlib, isWeakGuess);
    }
  }

  @AnyThread
  public void addCleanupListener (CleanupStartupDelegate listener) {
    componentDelegates.add(listener);
    if (tdlib.isAuthorized() && tdlib.isCurrent()) {
      if (tdlib.isStartupPerformed()) {
        listener.onPerformStartup(false);
      }
    } else if (tdlib.isUnauthorized()) {
      listener.onPerformUserCleanup();
    }
  }

  @AnyThread
  public void removeCleanupListener (CleanupStartupDelegate listener) {
    componentDelegates.remove(listener);
  }

  @AnyThread
  public void addCallsListener (PrivateCallListener listener) {
    privateCallListeners.add(listener);
  }

  @AnyThread
  public void removeCallsListener (PrivateCallListener listener) {
    privateCallListeners.remove(listener);
  }

  @AnyThread
  public void subscribeToCallUpdates (int callId, PrivateCallListener listener) {
    specificPrivateCallListeners.add(callId, listener);
  }

  @AnyThread
  public void unsubscribeFromCallUpdates (int callId, PrivateCallListener listener) {
    specificPrivateCallListeners.remove(callId, listener);
  }

  public void performStartup (boolean isAfterRestart) {
    for (CleanupStartupDelegate delegate : componentDelegates) {
      delegate.onPerformStartup(isAfterRestart);
    }
  }

  public void performCleanup () {
    for (CleanupStartupDelegate delegate : componentDelegates) {
      delegate.onPerformUserCleanup();
    }
  }

  public void performRestart () {
    for (CleanupStartupDelegate delegate : componentDelegates) {
      delegate.onPerformRestart();
    }
  }

  @AnyThread
  public void subscribeToMessageUpdates (long chatId, MessageListener listener) {
    messageChatListeners.add(chatId, listener);
  }

  @AnyThread
  public void unsubscribeFromMessageUpdates (long chatId, MessageListener listener) {
    messageChatListeners.remove(chatId, listener);
  }

  @AnyThread
  public void subscribeToMessageEditUpdates (long chatId, MessageEditListener listener) {
    messageEditChatListeners.add(chatId, listener);
  }

  @AnyThread
  public void unsubscribeFromMessageEditUpdates (long chatId, MessageEditListener listener) {
    messageEditChatListeners.remove(chatId, listener);
  }

  @AnyThread
  public void subscribeToAnimatedEmojiUpdates (AnimatedEmojiListener listener) {
    animatedEmojiListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromAnimatedEmojiUpdates (AnimatedEmojiListener listener) {
    animatedEmojiListeners.remove(listener);
  }

  @AnyThread
  public void addOptionsListener (TdlibOptionListener listener) {
    optionListeners.add(listener);
  }

  @AnyThread
  public void removeOptionListener (TdlibOptionListener listener) {
    optionListeners.remove(listener);
  }

  @AnyThread
  public void addTotalChatCounterListener (CounterChangeListener listener) {
    totalCountersListeners.add(listener);
  }

  @AnyThread
  public void removeTotalChatCounterListener (CounterChangeListener listener) {
    totalCountersListeners.remove(listener);
  }

  @AnyThread
  public void subscribeToChatUpdates (long chatId, ChatListener listener) {
    specificChatListeners.add(chatId, listener);
  }

  @AnyThread
  public void unsubscribeFromChatUpdates (long chatId, ChatListener listener) {
    specificChatListeners.remove(chatId, listener);
  }

  @AnyThread
  public void subscribeToForumTopicUpdates (long chatId, long messageThreadId, ForumTopicInfoListener listener) {
    specificForumTopicListeners.add(chatId + "_" + messageThreadId, listener);
  }

  @AnyThread
  public void unsubscribeFromForumTopicUpdates (long chatId, long messageThreadId, ForumTopicInfoListener listener) {
    specificForumTopicListeners.remove(chatId + "_" + messageThreadId, listener);
  }

  @AnyThread
  public void subscribeToChatListUpdates (@NonNull TdApi.ChatList chatList, ChatListListener listener) {
    chatListListeners.add(TD.makeChatListKey(chatList), listener);
  }

  @AnyThread
  public void unsubscribeFromChatListUpdates (@NonNull TdApi.ChatList chatList, ChatListListener listener) {
    chatListListeners.remove(TD.makeChatListKey(chatList), listener);
  }

  @AnyThread
  public void subscribeToChatFoldersUpdates (ChatFoldersListener listener) {
    chatFoldersListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromChatFoldersUpdates (ChatFoldersListener listener) {
    chatFoldersListeners.remove(listener);
  }

  @AnyThread
  public void addReactionLoadListener (String reactionKey, ReactionLoadListener listener) {
    reactionLoadListeners.add(reactionKey, listener);
  }

  @AnyThread
  public void removeReactionLoadListener (String reactionKey, ReactionLoadListener listener) {
    reactionLoadListeners.remove(reactionKey, listener);
  }

  @AnyThread
  public void subscribeToSettingsUpdates (NotificationSettingsListener listener) {
    settingsListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromSettingsUpdates (NotificationSettingsListener listener) {
    settingsListeners.remove(listener);
  }

  @AnyThread
  public void subscribeToPrivacyUpdates (PrivacySettingsListener listener) {
    privacySettingsListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromPrivacyUpdates (PrivacySettingsListener listener) {
    privacySettingsListeners.remove(listener);
  }

  @AnyThread
  public void subscribeToSettingsUpdates (long chatId, NotificationSettingsListener listener) {
    chatSettingsListeners.add(chatId, listener);
  }

  @AnyThread
  public void unsubscribeFromSettingsUpdates (long chatId, NotificationSettingsListener listener) {
    chatSettingsListeners.remove(chatId, listener);
  }

  @AnyThread
  public void subscribeToConnectivityUpdates (ConnectionListener listener) {
    connectionListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromConnectivityUpdates (ConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  @AnyThread
  public void subscribeToStickerUpdates (StickersListener listener) {
    stickersListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromStickerUpdates (StickersListener listener) {
    stickersListeners.remove(listener);
  }
  @AnyThread
  public void subscribeForAnimationsUpdates (AnimationsListener listener) {
    animationsListeners.add(listener);
  }

  @AnyThread
  public void unsubscribeFromAnimationsUpdates (AnimationsListener listener) {
    animationsListeners.remove(listener);
  }

  @AnyThread
  public void subscribeToDownloadsListUpdates (DownloadsListUpdateListener listener) {
    downloadsListListener.add(listener);
  }

  @AnyThread
  public void unsubscribeFromDownloadsListUpdates (DownloadsListUpdateListener listener) {
    downloadsListListener.remove(listener);
  }

  public void updateFileAddedToDownloads (TdApi.UpdateFileAddedToDownloads update) {
    for (DownloadsListUpdateListener listener : downloadsListListener) {
      listener.updateFileAddedToDownloads(update.fileDownload, update.counts);
    }
  }

  public void updateFileDownload (TdApi.UpdateFileDownload update) {
    for (DownloadsListUpdateListener listener : downloadsListListener) {
      listener.updateFileDownload(update.fileId, update.completeDate, update.isPaused, update.counts);
    }
  }

  public void updateFileDownloads (TdApi.UpdateFileDownloads update) {
    for (DownloadsListUpdateListener listener : downloadsListListener) {
      listener.updateFileDownloads(update.totalSize, update.totalCount, update.downloadedSize);
    }
  }

  public void updateFileRemovedFromDownloads (TdApi.UpdateFileRemovedFromDownloads update) {
    for (DownloadsListUpdateListener listener : downloadsListListener) {
      listener.updateFileRemovedFromDownloads(update.fileId, update.counts);
    }
  }

  // updateAuthorizationState

  void updateAuthorizationState (TdApi.AuthorizationState authorizationState) {
    for (AuthorizationListener listener : authorizationListeners) {
      listener.onAuthorizationStateChanged(authorizationState);
    }
  }

  void updateAuthorizationCodeReceived (String code) {
    for (AuthorizationListener listener : authorizationListeners) {
      listener.onAuthorizationCodeReceived(code);
    }
  }

  // Generic updates template

  private static <T> void runUpdate (@Nullable Iterator<T> list, RunnableData<T> act) {
    if (list != null) {
      while (list.hasNext()) {
        T next = list.next();
        act.runWithData(next);
      }
    }
  }

  private void runChatUpdate (long chatId, RunnableData<ChatListener> act) {
    runUpdate(chatListeners.iterator(), act);
    runUpdate(specificChatListeners.iterator(chatId), act);
  }

  // updateNewMessage

  private static void updateNewMessage (TdApi.UpdateNewMessage update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onNewMessage(update.message);
      }
    }
  }

  void updateNewMessage (TdApi.UpdateNewMessage update) {
    updateNewMessage(update, messageListeners.iterator());
    updateNewMessage(update, messageChatListeners.iterator(update.message.chatId));
  }

  // updateMessageSendSucceeded

  private static void updateMessageSendSucceeded (TdApi.UpdateMessageSendSucceeded update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageSendSucceeded(update.message, update.oldMessageId);
      }
    }
  }

  private void replaceMessage (long oldMessageId, TdApi.Message message) {
    List<TdApi.Message> messagesToUpdate = pendingMessages.remove(message.chatId + "_" + oldMessageId);
    if (messagesToUpdate != null && !messagesToUpdate.isEmpty()) {
      pendingMessages.put(message.chatId + "_" + message.id, messagesToUpdate);
      for (TdApi.Message dst : messagesToUpdate) {
        Td.copyTo(message, dst);
      }
    }
  }

  void updateMessageSendSucceeded (TdApi.UpdateMessageSendSucceeded update) {
    replaceMessage(update.oldMessageId, update.message);
    updateMessageSendSucceeded(update, messageListeners.iterator());
    updateMessageSendSucceeded(update, messageChatListeners.iterator(update.message.chatId));
  }

  // updateMessageSendFailed

  private static void updateMessageSendFailed (TdApi.UpdateMessageSendFailed update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageSendFailed(update.message, update.oldMessageId, update.error);
      }
    }
  }

  void updateMessageSendFailed (TdApi.UpdateMessageSendFailed update) {
    replaceMessage(update.oldMessageId, update.message);
    updateMessageSendFailed(update, messageListeners.iterator());
    updateMessageSendFailed(update, messageChatListeners.iterator(update.message.chatId));
  }

  // updateMessageSendAcknowledged

  private static void updateMessageSendAcknowledged (TdApi.UpdateMessageSendAcknowledged update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageSendAcknowledged(update.chatId, update.messageId);
      }
    }
  }

  void updateMessageSendAcknowledged (TdApi.UpdateMessageSendAcknowledged update) {
    updateMessageSendAcknowledged(update, messageListeners.iterator());
    updateMessageSendAcknowledged(update, messageChatListeners.iterator(update.chatId));
  }

  // updateMessageContent

  private static void updateMessageContent (TdApi.UpdateMessageContent update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageContentChanged(update.chatId, update.messageId, update.newContent);
      }
    }
  }

  void updateMessageContent (TdApi.UpdateMessageContent update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.content = update.newContent;
      }
    }
    updateMessageContent(update, messageListeners.iterator());
    updateMessageContent(update, messageChatListeners.iterator(update.chatId));
  }

  // updatePoll (fake via updateMessageContent)

  public void addPollListener (long id, PollListener listener) {
    pollListeners.add(id, listener);
  }

  public void removePollListener (long id, PollListener listener) {
    pollListeners.remove(id, listener);
  }

  void updatePoll (TdApi.Poll updatedPoll) {
    Iterator<PollListener> list = pollListeners.iterator(updatedPoll.id);
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUpdatePoll(updatedPoll);
      }
    }
  }

  // updateMessageEdited

  private static void updateMessageEdited (TdApi.UpdateMessageEdited update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageEdited(update.chatId, update.messageId, update.editDate, update.replyMarkup);
      }
    }
  }

  void updateMessageEdited (TdApi.UpdateMessageEdited update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.editDate = update.editDate;
        message.replyMarkup = update.replyMarkup;
      }
    }
    updateMessageEdited(update, messageListeners.iterator());
    updateMessageEdited(update, messageChatListeners.iterator(update.chatId));
  }

  // updateMessageContentOpened

  private static void updateMessageContentOpened (TdApi.UpdateMessageContentOpened update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageOpened(update.chatId, update.messageId);
      }
    }
  }

  void updateMessageContentOpened (TdApi.UpdateMessageContentOpened update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        TD.setMessageOpened(message);
      }
    }
    updateMessageContentOpened(update, messageListeners.iterator());
    updateMessageContentOpened(update, messageChatListeners.iterator(update.chatId));
  }

  // updateAnimatedEmojiMessageClicked

  private static void updateAnimatedEmojiMessageClicked (TdApi.UpdateAnimatedEmojiMessageClicked update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onAnimatedEmojiMessageClicked(update.chatId, update.messageId, update.sticker);
      }
    }
  }

  void updateAnimatedEmojiMessageClicked (TdApi.UpdateAnimatedEmojiMessageClicked update) {
    updateAnimatedEmojiMessageClicked(update, messageListeners.iterator());
    updateAnimatedEmojiMessageClicked(update, messageChatListeners.iterator(update.chatId));
  }

  // updateMessageIsPinned

  private static void updateMessageIsPinned (TdApi.UpdateMessageIsPinned update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessagePinned(update.chatId, update.messageId, update.isPinned);
      }
    }
  }

  void updateMessageIsPinned (TdApi.UpdateMessageIsPinned update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.isPinned = update.isPinned;
      }
    }
    updateMessageIsPinned(update, messageListeners.iterator());
    updateMessageIsPinned(update, messageChatListeners.iterator(update.chatId));
  }

  // updateMessagePendingContentUpdated

  private static void updateMessagePendingContentChanged (long chatId, long messageId, @Nullable Iterator<MessageEditListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessagePendingContentChanged(chatId, messageId);
      }
    }
  }

  void updateMessagePendingContentChanged (long chatId, long messageId) {
    updateMessagePendingContentChanged(chatId, messageId, messageEditListeners.iterator());
    updateMessagePendingContentChanged(chatId, messageId, messageEditChatListeners.iterator(chatId));
  }

  // updateMessageLiveLocationViewed

  private static void updateMessageLiveLocationViewed (TdApi.UpdateMessageLiveLocationViewed update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageLiveLocationViewed(update.chatId, update.messageId);
      }
    }
  }

  void updateMessageLiveLocationViewed (TdApi.UpdateMessageLiveLocationViewed update) {
    updateMessageLiveLocationViewed(update, messageListeners.iterator());
    updateMessageLiveLocationViewed(update, messageChatListeners.iterator(update.chatId));
  }

  // updateMessageMentionRead

  private static void updateMessageMentionRead (TdApi.UpdateMessageMentionRead update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageMentionRead(update.chatId, update.messageId);
      }
    }
  }

  void updateMessageMentionRead (TdApi.UpdateMessageMentionRead update, boolean counterChanged, boolean availabilityChanged) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.containsUnreadMention = false;
      }
    }
    updateMessageMentionRead(update, messageListeners.iterator());
    updateMessageMentionRead(update, messageChatListeners.iterator(update.chatId));
    if (counterChanged) {
      updateChatUnreadMentionCount(update.chatId, update.unreadMentionCount, availabilityChanged, chatListeners.iterator());
      updateChatUnreadMentionCount(update.chatId, update.unreadMentionCount, availabilityChanged, specificChatListeners.iterator(update.chatId));
    }
  }

  // updateOption + getStickerSet

  public void notifyAnimatedEmojiListeners (int type) {
    for (AnimatedEmojiListener listener : animatedEmojiListeners) {
      listener.onAnimatedEmojiChanged(type);
    }
  }

  // getEmojiReaction + getCustomReaction

  public void notifyReactionLoaded (String reactionKey) {
    ReferenceList<ReactionLoadListener> list = reactionLoadListeners.removeAll(reactionKey);
    if (list != null) {
      for (ReactionLoadListener loadListener : list) {
        loadListener.onReactionLoaded(reactionKey);
      }
    }
  }

  // updateMessageInteractionInfo

  private static void updateMessageInteractionInfo (TdApi.UpdateMessageInteractionInfo update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageInteractionInfoChanged(update.chatId, update.messageId, update.interactionInfo);
      }
    }
  }

  void updateMessageInteractionInfo (TdApi.UpdateMessageInteractionInfo update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.interactionInfo = update.interactionInfo;
      }
    }
    updateMessageInteractionInfo(update, messageListeners.iterator());
    updateMessageInteractionInfo(update, messageChatListeners.iterator(update.chatId));
  }

  // updateMessageUnreadReactions

  private static void updateMessageUnreadReactions (TdApi.UpdateMessageUnreadReactions update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessageUnreadReactionsChanged(update.chatId, update.messageId, update.unreadReactions, update.unreadReactionCount);
      }
    }
  }

  void updateMessageUnreadReactions (TdApi.UpdateMessageUnreadReactions update, boolean counterChanged, boolean availabilityChanged, TdApi.Chat chat, TdlibChatList[] chatLists) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.unreadReactions = update.unreadReactions;
      }
    }
    updateMessageUnreadReactions(update, messageListeners.iterator());
    updateMessageUnreadReactions(update, messageChatListeners.iterator(update.chatId));
    if (counterChanged) {
      updateChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged, chatListeners.iterator());
      updateChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged, specificChatListeners.iterator(update.chatId));
    }
    if (counterChanged) {
      updateChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged, chatListeners.iterator());
      updateChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged, specificChatListeners.iterator(update.chatId));
      if (chatLists != null) {
        for (TdlibChatList chatList : chatLists) {
          iterateChatListListeners(chatList, listener ->
            listener.onChatListItemChanged(chatList, chat, availabilityChanged ? ChatListListener.ItemChangeType.UNREAD_AVAILABILITY_CHANGED : ChatListListener.ItemChangeType.READ_INBOX)
          );
        }
      }
    }
  }

  // updateDeleteMessages

  private static void updateMessagesDeleted (TdApi.UpdateDeleteMessages update, @Nullable Iterator<MessageListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onMessagesDeleted(update.chatId, update.messageIds);
      }
    }
  }

  void updateMessagesDeleted (TdApi.UpdateDeleteMessages update) {
    updateMessagesDeleted(update, messageListeners.iterator());
    updateMessagesDeleted(update, messageChatListeners.iterator(update.chatId));
  }

  // updateChatUnreadMentionCount

  private static void updateChatUnreadMentionCount (long chatId, int unreadMentionCount, boolean availabilityChanged, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatUnreadMentionCount(chatId, unreadMentionCount, availabilityChanged);
      }
    }
  }

  void updateChatUnreadMentionCount (TdApi.UpdateChatUnreadMentionCount update, boolean availabilityChanged) {
    updateChatUnreadMentionCount(update.chatId, update.unreadMentionCount, availabilityChanged, chatListeners.iterator());
    updateChatUnreadMentionCount(update.chatId, update.unreadMentionCount, availabilityChanged, specificChatListeners.iterator(update.chatId));
  }

  // updateChatUnreadReactionCount

  private static void updateChatUnreadReactionCount (long chatId, int unreadReactionCount, boolean availabilityChanged, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatUnreadReactionCount(chatId, unreadReactionCount, availabilityChanged);
      }
    }
  }

  void updateChatUnreadReactionCount (TdApi.UpdateChatUnreadReactionCount update, boolean availabilityChanged, TdApi.Chat chat, TdlibChatList[] chatLists) {
    updateChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged, chatListeners.iterator());
    updateChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged, specificChatListeners.iterator(update.chatId));
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        iterateChatListListeners(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, availabilityChanged ? ChatListListener.ItemChangeType.UNREAD_AVAILABILITY_CHANGED : ChatListListener.ItemChangeType.READ_INBOX)
        );
      }
    }
  }

  // updateChatLastMessage

  private static void updateChatLastMessage (long chatId, TdApi.Message lastMessage, @Nullable List<Tdlib.ChatListChange> listChanges, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        ChatListener listener = list.next();
        listener.onChatTopMessageChanged(chatId, lastMessage);
        if (listChanges != null) {
          for (Tdlib.ChatListChange listChange : listChanges) {
            Tdlib.ChatChange positionChange = listChange.change;
            listener.onChatPositionChanged(chatId, positionChange.position, positionChange.orderChanged(), positionChange.sourceChanged(), positionChange.pinStateChanged());
          }
        }
      }
    }
  }

  void updateChatLastMessage (TdApi.UpdateChatLastMessage update, @Nullable List<Tdlib.ChatListChange> listChanges) {
    updateChatLastMessage(update.chatId, update.lastMessage, listChanges, chatListeners.iterator());
    updateChatLastMessage(update.chatId, update.lastMessage, listChanges, specificChatListeners.iterator(update.chatId));
    if (listChanges != null) {
      for (Tdlib.ChatListChange listChange : listChanges) {
        listChange.list.onUpdateChatPosition(listChange.chat, listChange.change);
        iterateChatListListeners(listChange.list, listener ->
          listener.onChatListItemChanged(listChange.list, listChange.chat, ChatListListener.ItemChangeType.LAST_MESSAGE)
        );
      }
    }
  }

  // updateChatOrder

  private static void updateChatPosition (long chatId, TdApi.ChatPosition position, boolean orderChanged, boolean sourceChanged, boolean pinStateChanged, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatPositionChanged(chatId, position, orderChanged, sourceChanged, pinStateChanged);
      }
    }
  }

  void updateChatPosition (TdApi.UpdateChatPosition update, Tdlib.ChatListChange listChange) {
    boolean orderChanged = listChange.change.orderChanged();
    boolean sourceChanged = listChange.change.sourceChanged();
    boolean pinStateChanged = listChange.change.pinStateChanged();
    updateChatPosition(update.chatId, update.position, orderChanged, sourceChanged, pinStateChanged, chatListeners.iterator());
    updateChatPosition(update.chatId, update.position, orderChanged, sourceChanged, pinStateChanged, specificChatListeners.iterator(update.chatId));
    listChange.list.onUpdateChatPosition(listChange.chat, listChange.change);
  }

  // updateChatPermissions

  private static void updateChatPermissions (long chatId, TdApi.ChatPermissions permissions, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatPermissionsChanged(chatId, permissions);
      }
    }
  }

  void updateChatPermissions (TdApi.UpdateChatPermissions update) {
    updateChatPermissions(update.chatId, update.permissions, chatListeners.iterator());
    updateChatPermissions(update.chatId, update.permissions, specificChatListeners.iterator(update.chatId));
  }

  // updateChatTitle

  private static void updateChatTitle (TdApi.UpdateChatTitle update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatTitleChanged(update.chatId, update.title);
      }
    }
  }

  void updateChatTitle (TdApi.UpdateChatTitle update, TdApi.Chat chat, TdlibChatList[] chatLists) {
    updateChatTitle(update, chatListeners.iterator());
    updateChatTitle(update, specificChatListeners.iterator(update.chatId));
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        iterateChatListListeners(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, ChatListListener.ItemChangeType.TITLE)
        );
      }
    }
  }

  // updateChatTheme

  private static void updateChatTheme (TdApi.UpdateChatTheme update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatThemeChanged(update.chatId, update.themeName);
      }
    }
  }

  void updateChatTheme (TdApi.UpdateChatTheme update, TdApi.Chat chat, TdlibChatList[] chatLists) {
    updateChatTheme(update, chatListeners.iterator());
    updateChatTheme(update, specificChatListeners.iterator(update.chatId));
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        iterateChatListListeners(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, ChatListListener.ItemChangeType.THEME)
        );
      }
    }
  }

  // updateChatPhoto

  private static void updateChatPhoto (TdApi.UpdateChatPhoto update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatPhotoChanged(update.chatId, update.photo);
      }
    }
  }

  void updateChatPhoto (TdApi.UpdateChatPhoto update) {
    updateChatPhoto(update, chatListeners.iterator());
    updateChatPhoto(update, specificChatListeners.iterator(update.chatId));
  }

  // updateChatActionBar

  private static void updateChatActionBar (TdApi.UpdateChatActionBar update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatActionBarChanged(update.chatId, update.actionBar);
      }
    }
  }

  void updateChatActionBar (TdApi.UpdateChatActionBar update) {
    updateChatActionBar(update, chatListeners.iterator());
    updateChatActionBar(update, specificChatListeners.iterator(update.chatId));
  }

  // updateChatHasScheduledMessages

  private static void updateChatHasScheduledMessages (TdApi.UpdateChatHasScheduledMessages update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatHasScheduledMessagesChanged(update.chatId, update.hasScheduledMessages);
      }
    }
  }

  void updateChatHasScheduledMessages (TdApi.UpdateChatHasScheduledMessages update) {
    updateChatHasScheduledMessages(update, chatListeners.iterator());
    updateChatHasScheduledMessages(update, specificChatListeners.iterator(update.chatId));
  }

  // updateChatHasProtectedContent

  private static void updateChatHasProtectedContent (TdApi.UpdateChatHasProtectedContent update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatHasProtectedContentChanged(update.chatId, update.hasProtectedContent);
      }
    }
  }

  void updateChatHasProtectedContent (TdApi.UpdateChatHasProtectedContent update) {
    updateChatHasProtectedContent(update, chatListeners.iterator());
    updateChatHasProtectedContent(update, specificChatListeners.iterator(update.chatId));
  }

  // updateChatReadInbox

  private static void updateChatReadInbox (TdApi.UpdateChatReadInbox update, boolean availabilityChanged, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatReadInbox(update.chatId, update.lastReadInboxMessageId, update.unreadCount, availabilityChanged);
      }
    }
  }

  void updateChatReadInbox (TdApi.UpdateChatReadInbox update, boolean availabilityChanged, TdApi.Chat chat, TdlibChatList[] chatLists) {
    updateChatReadInbox(update, availabilityChanged, chatListeners.iterator());
    updateChatReadInbox(update, availabilityChanged, specificChatListeners.iterator(update.chatId));
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        iterateChatListListeners(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, availabilityChanged ? ChatListListener.ItemChangeType.UNREAD_AVAILABILITY_CHANGED : ChatListListener.ItemChangeType.READ_INBOX)
        );
      }
    }
  }

  // updateChatReadOutbox

  private static void updateChatReadOutbox (TdApi.UpdateChatReadOutbox update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatReadOutbox(update.chatId, update.lastReadOutboxMessageId);
      }
    }
  }

  void updateChatReadOutbox (TdApi.UpdateChatReadOutbox update) {
    updateChatReadOutbox(update, chatListeners.iterator());
    updateChatReadOutbox(update, specificChatListeners.iterator(update.chatId));
  }

  // updateChatReplyMarkup

  private static void updateChatReplyMarkup (TdApi.UpdateChatReplyMarkup update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatReplyMarkupChanged(update.chatId, update.replyMarkupMessageId);
      }
    }
  }

  void updateChatReplyMarkup (TdApi.UpdateChatReplyMarkup update) {
    updateChatReplyMarkup(update, chatListeners.iterator());
    updateChatReplyMarkup(update, specificChatListeners.iterator(update.chatId));
  }

  // updateChatDraftMessage

  private static void updateChatDraftMessage (long chatId, @Nullable TdApi.DraftMessage draftMessage, @Nullable List<Tdlib.ChatListChange> listChanges, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        ChatListener listener = list.next();
        listener.onChatDraftMessageChanged(chatId, draftMessage);
        if (listChanges != null) {
          for (Tdlib.ChatListChange listChange : listChanges) {
            Tdlib.ChatChange positionChange = listChange.change;
            listener.onChatPositionChanged(chatId, positionChange.position,
              positionChange.orderChanged(),
              positionChange.sourceChanged(),
              positionChange.pinStateChanged()
            );
          }
        }
      }
    }
  }

  void updateChatDraftMessage (TdApi.UpdateChatDraftMessage update, List<Tdlib.ChatListChange> listChanges) {
    updateChatDraftMessage(update.chatId, update.draftMessage, listChanges, chatListeners.iterator());
    updateChatDraftMessage(update.chatId, update.draftMessage, listChanges, specificChatListeners.iterator(update.chatId));
    if (listChanges != null) {
      for (Tdlib.ChatListChange listChange : listChanges) {
        listChange.list.onUpdateChatPosition(listChange.chat, listChange.change);
        iterateChatListListeners(listChange.list, listener ->
          listener.onChatListItemChanged(listChange.list, listChange.chat, ChatListListener.ItemChangeType.DRAFT)
        );
      }
    }
  }

  // updateChatFolders

  void updateChatFolders (TdApi.UpdateChatFolders update) {
    for (ChatFoldersListener listener : chatFoldersListeners) {
      listener.onChatFoldersChanged(update.chatFolders, update.mainChatListPosition);
    }
  }

  // updateChatAvailableReactions

  private static void updateChatAvailableReactions (TdApi.UpdateChatAvailableReactions update, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatAvailableReactionsUpdated(update.chatId, update.availableReactions);
      }
    }
  }

  void updateChatAvailableReactions (TdApi.UpdateChatAvailableReactions update) {
    updateChatAvailableReactions(update, chatListeners.iterator());
    updateChatAvailableReactions(update, specificChatListeners.iterator(update.chatId));
  }

  // updateCall

  private static void updateCall (TdApi.Call call, @Nullable Iterator<PrivateCallListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onCallUpdated(call);
      }
    }
  }

  void updateCall (TdApi.UpdateCall update) {
    updateCall(update.call, privateCallListeners.iterator());
    updateCall(update.call, specificPrivateCallListeners.iterator(update.call.id));
  }

  // updateNewCallSignalingData

  private static void updateNewCallSignalingData (int callId, byte[] data, @Nullable Iterator<PrivateCallListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onNewCallSignalingDataArrived(callId, data);
      }
    }
  }

  void updateNewCallSignalingData (TdApi.UpdateNewCallSignalingData update) {
    updateNewCallSignalingData(update.callId, update.data, privateCallListeners.iterator());
    updateNewCallSignalingData(update.callId, update.data, specificPrivateCallListeners.iterator(update.callId));
  }

  // updateGroupCallParticipant

  private static void updateGroupCallParticipant (int groupCallId, TdApi.GroupCallParticipant groupCallParticipant, @Nullable Iterator<GroupCallListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onGroupCallParticipantUpdated(groupCallId, groupCallParticipant);
      }
    }
  }

  void updateGroupCallParticipant (TdApi.UpdateGroupCallParticipant update) {
    updateGroupCallParticipant(update.groupCallId, update.participant, groupCallListeners.iterator());
    updateGroupCallParticipant(update.groupCallId, update.participant, specificGroupCallListeners.iterator(update.groupCallId));
  }

  // updateGroupCall

  private static void updateGroupCall (TdApi.GroupCall groupCall, @Nullable Iterator<GroupCallListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onGroupCallUpdated(groupCall);
      }
    }
  }

  void updateGroupCall (TdApi.UpdateGroupCall update) {
    updateGroupCall(update.groupCall, groupCallListeners.iterator());
    updateGroupCall(update.groupCall, specificGroupCallListeners.iterator(update.groupCall.id));
  }

  // updateChatOnlineMemberCount

  private static void updateChatOnlineMemberCount (long chatId, int onlineMemberCount, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatOnlineMemberCountChanged(chatId, onlineMemberCount);
      }
    }
  }

  void updateChatOnlineMemberCount (TdApi.UpdateChatOnlineMemberCount update) {
    updateChatOnlineMemberCount(update.chatId, update.onlineMemberCount, chatListeners.iterator());
    updateChatOnlineMemberCount(update.chatId, update.onlineMemberCount, specificChatListeners.iterator(update.chatId));
  }

  // updateMessageTtlSetting

  private static void updateChatMessageAutoDeleteTime (long chatId, int messageTtlSetting, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatMessageTtlSettingChanged(chatId, messageTtlSetting);
      }
    }
  }

  void updateChatMessageAutoDeleteTime (TdApi.UpdateChatMessageAutoDeleteTime update) {
    updateChatMessageAutoDeleteTime(update.chatId, update.messageAutoDeleteTime, chatListeners.iterator());
    updateChatMessageAutoDeleteTime(update.chatId, update.messageAutoDeleteTime, specificChatListeners.iterator(update.chatId));
  }

  // updateChatActiveStories

  private static void updateChatActiveStories (TdApi.ChatActiveStories activeStories, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatActiveStoriesChanged(activeStories);
      }
    }
  }

  void updateChatActiveStories (TdApi.UpdateChatActiveStories update) {
    updateChatActiveStories(update.activeStories, chatListeners.iterator());
    updateChatActiveStories(update.activeStories, specificChatListeners.iterator(update.activeStories.chatId));
  }

  // updateStory

  private static String uniqueStoryKey (TdApi.Story story) {
    return uniqueStoryKey(story.senderChatId, story.id);
  }

  private static String uniqueStoryKey (long storySenderChatId, int storyId) {
    return storySenderChatId + "_" + storyId;
  }

  private static void updateStory (TdApi.Story story, @Nullable Iterator<StoryListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onStoryUpdated(story);
      }
    }
  }

  void updateStory (TdApi.UpdateStory update) {
    updateStory(update.story, storyListeners.iterator());
    updateStory(update.story, specificStoryListeners.iterator(uniqueStoryKey(update.story)));
  }

  // updateStoryDeleted

  private static void updateStoryDeleted (long storySenderChatId, int storyId, @Nullable Iterator<StoryListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onStoryDeleted(storySenderChatId, storyId);
      }
    }
  }

  void updateStoryDeleted (TdApi.UpdateStoryDeleted update) {
    updateStoryDeleted(update.storySenderChatId, update.storyId, storyListeners.iterator());
    updateStoryDeleted(update.storySenderChatId, update.storyId, specificStoryListeners.iterator(uniqueStoryKey(update.storySenderChatId, update.storyId)));
  }

  // updateStorySendSucceeded

  private static void updateStorySendSucceeded (TdApi.Story story, int oldStoryId, @Nullable Iterator<StoryListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onStorySendSucceeded(story, oldStoryId);
      }
    }
  }

  void updateStorySendSucceeded (TdApi.UpdateStorySendSucceeded update) {
    updateStorySendSucceeded(update.story, update.oldStoryId, storyListeners.iterator());
    updateStorySendSucceeded(update.story, update.oldStoryId, specificStoryListeners.iterator(uniqueStoryKey(update.story.senderChatId, update.oldStoryId)));
  }

  // updateStorySendFailed

  private static void updateStorySendFailed (TdApi.Story story, TdApi.Error error, @Nullable TdApi.CanSendStoryResult errorType, @Nullable Iterator<StoryListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onStorySendFailed(story, error, errorType);
      }
    }
  }

  void updateStorySendFailed (TdApi.UpdateStorySendFailed update) {
    updateStorySendFailed(update.story, update.error, update.errorType, storyListeners.iterator());
    updateStorySendFailed(update.story, update.error, update.errorType, specificStoryListeners.iterator(uniqueStoryKey(update.story)));
  }

  // updateStoryStealthMode

  private static void updateStoryStealthMode (int activeUntilDate, int cooldownUntilDate, @Nullable Iterator<StoryListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onStoryStealthModeUpdated(activeUntilDate, cooldownUntilDate);
      }
    }
  }

  void updateStoryStealthMode (TdApi.UpdateStoryStealthMode update) {
    updateStoryStealthMode(update.activeUntilDate, update.cooldownUntilDate, storyListeners.iterator());
    updateStoryStealthMode(update.activeUntilDate, update.cooldownUntilDate, specificStoryListeners.combinedIterator());
  }

  // updateChatVoiceChat

  void updateChatVideoChat (TdApi.UpdateChatVideoChat update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatVideoChatChanged(update.chatId, update.videoChat);
    });
  }

  // updateForumTopicInfo

  void updateForumTopicInfo (TdApi.UpdateForumTopicInfo update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onForumTopicInfoChanged(update.chatId, update.info);
    });
    runUpdate(specificForumTopicListeners.iterator(update.chatId + "_" + update.info.messageThreadId), listener -> {
      listener.onForumTopicInfoChanged(update.chatId, update.info);
    });
  }

  // updateChatViewAsTopics

  void updateChatViewAsTopics (TdApi.UpdateChatViewAsTopics update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatViewAsTopics(update.chatId, update.viewAsTopics);
    });
  }

  // updateChatPendingJoinRequests

  void updateChatPendingJoinRequests (TdApi.UpdateChatPendingJoinRequests update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatPendingJoinRequestsChanged(update.chatId, update.pendingJoinRequests);
    });
  }

  // updateUsersNearby

  private static void updateUsersNearby (TdApi.ChatNearby[] usersNearby, @Nullable Iterator<ChatsNearbyListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUsersNearbyUpdated(usersNearby);
      }
    }
  }

  void updateUsersNearby (TdApi.UpdateUsersNearby update) {
    updateUsersNearby(update.usersNearby, chatsNearbyListeners.iterator());
  }

  // updateChatIsMarkedAsUnread

  void updateChatIsMarkedAsUnread (TdApi.UpdateChatIsMarkedAsUnread update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatMarkedAsUnread(update.chatId, update.isMarkedAsUnread);
    });
  }

  // updateChatBackground

  void updateChatBackground (TdApi.UpdateChatBackground update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatBackgroundChanged(update.chatId, update.background)
    );
  }

  // updateChatAccentColor

  void updateChatAccentColor (TdApi.UpdateChatAccentColor update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatAccentColorChanged(update.chatId, update.accentColorId)
    );
  }

  // updateChatBackgroundCustomEmoji

  void updateChatBackgroundCustomEmoji (TdApi.UpdateChatBackgroundCustomEmoji update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatBackgroundCustomEmojiChanged(update.chatId, update.backgroundCustomEmojiId)
    );
  }

  // updateChatIsTranslatable

  void updateChatIsTranslatable (TdApi.UpdateChatIsTranslatable update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatIsTranslatableChanged(update.chatId, update.isTranslatable);
    });
  }

  // updateChatIsBlocked

  private static void updateChatBlockList (long chatId, @Nullable TdApi.BlockList blockList, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatBlockListChanged(chatId, blockList);
      }
    }
  }

  void updateChatBlockList (TdApi.UpdateChatBlockList update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatBlockListChanged(update.chatId, update.blockList);
    });
  }

  // updateChatClientDataChanged

  void updateChatClientDataChanged (long chatId, String newClientData) {
    runChatUpdate(chatId, listener -> {
      listener.onChatClientDataChanged(chatId, newClientData);
    });
  }

  // updateNotificationSettings

  private static void notifySettingsChanged (@Nullable Iterator<NotificationSettingsListener> list, TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onNotificationSettingsChanged(scope, settings);
      }
    }
  }

  private static void notifySettingsChanged (@Nullable Iterator<NotificationSettingsListener> list, long chatId, TdApi.ChatNotificationSettings settings) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onNotificationSettingsChanged(chatId, settings);
      }
    }
  }

  private static void notifyChannelChanged (@Nullable Iterator<NotificationSettingsListener> list, TdApi.NotificationSettingsScope scope) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onNotificationChannelChanged(scope);
      }
    }
  }

  private static void notifyChannelChanged (@Nullable Iterator<NotificationSettingsListener> list, long chatId) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onNotificationChannelChanged(chatId);
      }
    }
  }

  @TdlibThread
  void updateNotificationSettings (TdApi.UpdateChatNotificationSettings update) {
    notifySettingsChanged(settingsListeners.iterator(), update.chatId, update.notificationSettings);
    notifySettingsChanged(chatSettingsListeners.iterator(update.chatId), update.chatId, update.notificationSettings);
  }

  @TdlibThread
  void updateNotificationSettings (TdApi.UpdateScopeNotificationSettings update) {
    notifySettingsChanged(settingsListeners.iterator(), update.scope, update.notificationSettings);
  }

  @AnyThread
  void updateNotificationChannel (TdApi.NotificationSettingsScope scope) {
    notifyChannelChanged(settingsListeners.iterator(), scope);
  }

  @AnyThread
  void updateNotificationChannel (long chatId) {
    notifyChannelChanged(settingsListeners.iterator(), chatId);
    notifyChannelChanged(chatSettingsListeners.iterator(chatId), chatId);
  }

  @AnyThread
  public void updateNotificationGlobalSettings () {
    for (NotificationSettingsListener listener : settingsListeners) {
      listener.onNotificationGlobalSettingsChanged();
    }
  }

  @AnyThread
  public void notifyArchiveChatListSettingsChanged (TdApi.ArchiveChatListSettings archiveChatListSettings) {
    for (NotificationSettingsListener listener : settingsListeners) {
      listener.onArchiveChatListSettingsChanged(archiveChatListSettings);
    }
  }

  @AnyThread
  public void notifyChatCountersChanged (TdApi.ChatList chatList, TdlibCounter counter, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
    for (CounterChangeListener listener : totalCountersListeners) {
      listener.onChatCounterChanged(chatList, counter, availabilityChanged, totalCount, unreadCount, unreadUnmutedCount);
    }
  }

  @AnyThread
  public void notifyMessageCountersChanged (TdApi.ChatList chatList, TdlibCounter counter, int unreadCount, int unreadUnmutedCount) {
    for (CounterChangeListener listener : totalCountersListeners) {
      listener.onMessageCounterChanged(chatList, counter, unreadCount, unreadUnmutedCount);
    }
  }

  // chat lists

  private void iterateChatListListeners (TdlibChatList chatList, RunnableData<ChatListListener> callback) {
    Iterator<ChatListListener> list = chatListListeners.iterator(TD.makeChatListKey(chatList.chatList()));
    if (list != null) {
      while (list.hasNext()) {
        callback.runWithData(list.next());
      }
    }
  }

  @TdlibThread
  void updateChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    iterateChatListListeners(chatList, listener -> {
      listener.onChatAdded(chatList, chat, atIndex, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_ADDED);
    });
  }

  @TdlibThread
  void updateChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    iterateChatListListeners(chatList, listener -> {
      listener.onChatRemoved(chatList, chat, fromIndex, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_REMOVED);
    });
  }

  @TdlibThread
  void updateChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    iterateChatListListeners(chatList, listener -> {
      listener.onChatMoved(chatList, chat, fromIndex, toIndex, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_MOVED);
    });
  }

  @TdlibThread
  void updateChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) {
    iterateChatListListeners(chatList, listener -> {
      listener.onChatChanged(chatList, chat, index, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_METADATA_CHANGED);
    });
  }

  @TdlibThread
  void updateChatListStateChanged (TdlibChatList chatList, @TdlibChatList.State int newState, @TdlibChatList.State int oldState) {
    iterateChatListListeners(chatList, listener ->
      listener.onChatListStateChanged(chatList, newState, oldState)
    );
  }

  // updatePrivacySettingRules

  @AnyThread
  public void updatePrivacySettingRules (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    for (PrivacySettingsListener listener : privacySettingsListeners) {
      listener.onPrivacySettingRulesChanged(setting, rules);
    }
  }

  // updateFile

  void updateFile (TdApi.UpdateFile update) {
    Iterator<FileUpdateListener> list = fileUpdateListeners.iterator(update.file.id);
    if (list != null) {
      while (list.hasNext()) {
        list.next().onUpdateFile(update);
      }
    }
  }

  public void addFileListener (int fileId, FileUpdateListener listener) {
    fileUpdateListeners.add(fileId, listener);
  }

  public void removeFileListener (int fileId, FileUpdateListener listener) {
    fileUpdateListeners.remove(fileId, listener);
  }

  // updateConnectionState

  void updateConnectionState (@ConnectionState int newState, int oldState) {
    for (ConnectionListener listener : connectionListeners) {
      listener.onConnectionStateChanged(newState, oldState);
    }
  }

  void updateConnectionType (@NonNull TdApi.NetworkType networkType) {
    for (ConnectionListener listener : connectionListeners) {
      listener.onConnectionTypeChanged(networkType);
    }
  }

  void updateConnectionDisplayStatusChanged () {
    for (ConnectionListener listener : connectionListeners) {
      listener.onConnectionDisplayStatusChanged();
    }
  }

  // updateInstalledStickerSets

  void updateInstalledStickerSets (TdApi.UpdateInstalledStickerSets update) {
    for (StickersListener listener : stickersListeners) {
      listener.onInstalledStickerSetsUpdated(update.stickerSetIds, update.stickerType);
    }
  }

  // CUSTOM updateStickerSetArchived

  public final void notifyStickerSetArchived (TdApi.StickerSetInfo info) {
    for (StickersListener listener : stickersListeners) {
      listener.onStickerSetArchived(info);
    }
  }

  public final void notifyStickerSetRemoved (TdApi.StickerSetInfo info) {
    for (StickersListener listener : stickersListeners) {
      listener.onStickerSetRemoved(info);
    }
  }

  public final void notifyStickerSetInstalled (TdApi.StickerSetInfo info) {
    for (StickersListener listener : stickersListeners) {
      listener.onStickerSetInstalled(info);
    }
  }

  // updateFavoriteStickers

  void updateFavoriteStickers (TdApi.UpdateFavoriteStickers update) {
    for (StickersListener listener : stickersListeners) {
      listener.onFavoriteStickersUpdated(update.stickerIds);
    }
  }

  // updateStickerSet

  void updateStickerSet (TdApi.StickerSet stickerSet) {
    for (StickersListener listener : stickersListeners) {
      listener.onStickerSetUpdated(stickerSet);
    }
  }

  // updateRecentStickers

  void updateRecentStickers (TdApi.UpdateRecentStickers update) {
    for (StickersListener listener : stickersListeners) {
      listener.onRecentStickersUpdated(update.stickerIds, update.isAttached);
    }
  }

  // updateTrendingStickerSets

  void updateTrendingStickerSets (TdApi.UpdateTrendingStickerSets update, int unreadCount) {
    for (StickersListener listener : stickersListeners) {
      listener.onTrendingStickersUpdated(update.stickerType, update.stickerSets, unreadCount);
    }
  }

  // updateSavedAnimations

  void updateSavedAnimations (TdApi.UpdateSavedAnimations update) {
    for (AnimationsListener listener : animationsListeners) {
      listener.onSavedAnimationsUpdated(update.animationIds);
    }
  }

  // updateChatDefaultDisableNotifications

  void updateChatDefaultDisableNotifications (TdApi.UpdateChatDefaultDisableNotification update) {
    for (ChatListener listener : chatListeners) {
      listener.onChatDefaultDisableNotifications(update.chatId, update.defaultDisableNotification);
    }
    Iterator<ChatListener> list = specificChatListeners.iterator(update.chatId);
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatDefaultDisableNotifications(update.chatId, update.defaultDisableNotification);
      }
    }
  }

  // updateChatDefaultMessageSenderId

  void updateChatDefaultMessageSenderId (TdApi.UpdateChatMessageSender update) {
    for (ChatListener listener : chatListeners) {
      listener.onChatDefaultMessageSenderIdChanged(update.chatId, update.messageSenderId);
    }
    Iterator<ChatListener> list = specificChatListeners.iterator(update.chatId);
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatDefaultMessageSenderIdChanged(update.chatId, update.messageSenderId);
      }
    }
  }

  // updateOption

  void updateTopChatsDisabled (boolean areDisabled) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onTopChatsDisabled(areDisabled);
    }
  }

  void updatedSentScheduledMessageNotificationsDisabled (boolean areDisabled) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onSentScheduledMessageNotificationsDisabled(areDisabled);
    }
  }

  void updateArchiveAndMuteChatsFromUnknownUsersEnabled (boolean enabled) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onArchiveAndMuteChatsFromUnknownUsersEnabled(enabled);
    }
  }

  void updateSuggestedLanguageChanged (String languagePackId, TdApi.LanguagePackInfo languagePackInfo) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onSuggestedLanguagePackChanged(languagePackId, languagePackInfo);
    }
  }

  void updateContactRegisteredNotificationsDisabled (boolean areDisabled) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onContactRegisteredNotificationsDisabled(areDisabled);
    }
  }

  void updateAccentColors (TdApi.UpdateAccentColors update) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onAccentColorsChanged();
    }
  }

  void updateProfileAccentColors (TdApi.UpdateProfileAccentColors update, boolean listChanged) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onProfileAccentColorsChanged(listChanged);
    }
  }

  void updateSuggestedActions (TdApi.UpdateSuggestedActions update) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onSuggestedActionsChanged(update.addedActions, update.removedActions);
    }
  }
}
