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
import tgx.td.Td;

public class TdlibListeners {
  private final Tdlib tdlib;

  final ReferenceList<MessageListener> messageListeners;
  final ReferenceList<MessageEditListener> messageEditListeners;
  final ReferenceList<ChatListener> chatListeners;
  final ReferenceList<ChatFoldersListener> chatFoldersListeners;
  final ReferenceIntMap<ChatFolderListener> chatFolderListeners;
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
    this.chatFolderListeners = new ReferenceIntMap<>(true);
    this.settingsListeners = new ReferenceList<>(true);
    this.stickersListeners = new ReferenceList<>(true);
    this.animationsListeners = new ReferenceList<>();
    this.connectionListeners = new ReferenceList<>(true);
    this.authorizationListeners = new ReferenceList<>(true);
    this.componentDelegates = new ReferenceList<>(true);
    this.optionListeners = new ReferenceList<>(true);
    this.totalCountersListeners = new ReferenceList<>(true);
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
  public void subscribeForGlobalUpdates (Object globalListener) {
    synchronized (this) {
      if (globalListener instanceof MessageListener) {
        messageListeners.add((MessageListener) globalListener);
      }
      if (globalListener instanceof MessageEditListener) {
        messageEditListeners.add((MessageEditListener) globalListener);
      }
      if (globalListener instanceof ChatListener) {
        chatListeners.add((ChatListener) globalListener);
      }
      if (globalListener instanceof NotificationSettingsListener) {
        settingsListeners.add((NotificationSettingsListener) globalListener);
      }
      if (globalListener instanceof StickersListener) {
        stickersListeners.add((StickersListener) globalListener);
      }
      if (globalListener instanceof AnimationsListener) {
        animationsListeners.add((AnimationsListener) globalListener);
      }
      if (globalListener instanceof ConnectionListener) {
        connectionListeners.add((ConnectionListener) globalListener);
      }
      if (globalListener instanceof TdlibOptionListener) {
        optionListeners.add((TdlibOptionListener) globalListener);
      }
      if (globalListener instanceof CounterChangeListener) {
        totalCountersListeners.add((CounterChangeListener) globalListener);
      }
      if (globalListener instanceof AnimatedEmojiListener) {
        animatedEmojiListeners.add((AnimatedEmojiListener) globalListener);
      }
      if (globalListener instanceof PrivacySettingsListener) {
        privacySettingsListeners.add((PrivacySettingsListener) globalListener);
      }
      if (globalListener instanceof PrivateCallListener) {
        privateCallListeners.add((PrivateCallListener) globalListener);
      }
      if (globalListener instanceof GroupCallListener) {
        groupCallListeners.add((GroupCallListener) globalListener);
      }
      if (globalListener instanceof SessionListener) {
        sessionListeners.add((SessionListener) globalListener);
      }
    }
  }

  @AnyThread
  public void unsubscribeFromGlobalUpdates (Object globalListener) {
    synchronized (this) {
      if (globalListener instanceof MessageListener) {
        messageListeners.remove((MessageListener) globalListener);
      }
      if (globalListener instanceof MessageEditListener) {
        messageEditListeners.remove((MessageEditListener) globalListener);
      }
      if (globalListener instanceof ChatListener) {
        chatListeners.remove((ChatListener) globalListener);
      }
      if (globalListener instanceof NotificationSettingsListener) {
        settingsListeners.remove((NotificationSettingsListener) globalListener);
      }
      if (globalListener instanceof StickersListener) {
        stickersListeners.remove((StickersListener) globalListener);
      }
      if (globalListener instanceof StickersListener) {
        animationsListeners.remove((AnimationsListener) globalListener);
      }
      if (globalListener instanceof ConnectionListener) {
        connectionListeners.remove((ConnectionListener) globalListener);
      }
      if (globalListener instanceof TdlibOptionListener) {
        optionListeners.remove((TdlibOptionListener) globalListener);
      }
      if (globalListener instanceof CounterChangeListener) {
        totalCountersListeners.remove((CounterChangeListener) globalListener);
      }
      if (globalListener instanceof AnimatedEmojiListener) {
        animatedEmojiListeners.remove((AnimatedEmojiListener) globalListener);
      }
      if (globalListener instanceof PrivacySettingsListener) {
        privacySettingsListeners.remove((PrivacySettingsListener) globalListener);
      }
      if (globalListener instanceof PrivateCallListener) {
        privateCallListeners.remove((PrivateCallListener) globalListener);
      }
      if (globalListener instanceof GroupCallListener) {
        groupCallListeners.remove((GroupCallListener) globalListener);
      }
      if (globalListener instanceof SessionListener) {
        sessionListeners.remove((SessionListener) globalListener);
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
  public void addChatFolderListener (int chatFolderId, ChatFolderListener listener) {
    if (chatFolderId != 0) {
      chatFolderListeners.add(chatFolderId, listener);
    }

  }

  @AnyThread
  public void removeChatFolderListener (int chatFolderId, ChatFolderListener listener) {
    if (chatFolderId != 0) {
      chatFolderListeners.remove(chatFolderId, listener);
    }
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

  @SafeVarargs
  private static <T> void runUpdate (RunnableData<T> act, @Nullable Iterator<? extends T>... lists) {
    if (lists != null) {
      for (Iterator<? extends T> list : lists) {
        runUpdate(list, act);
      }
    }
  }

  private static <T> void runUpdate (@Nullable Iterable<? extends T> iterable, RunnableData<T> act) {
    if (iterable != null) {
      runUpdate(iterable.iterator(), act);
    }
  }

  private static <T> void runUpdate (@Nullable Iterator<? extends T> list, RunnableData<T> act) {
    if (list != null) {
      while (list.hasNext()) {
        T next = list.next();
        act.runWithData(next);
      }
    }
  }

  private void runChatUpdate (long chatId, RunnableData<ChatListener> act) {
    runUpdate(act,
      chatListeners.iterator(),
      specificChatListeners.iterator(chatId)
    );
  }

  private void runForumUpdate (long chatId, int forumTopicId, RunnableData<ForumTopicInfoListener> act) {
    runUpdate(act,
      specificForumTopicListeners.iterator(uniqueForumTopicKey(chatId, forumTopicId)),
      chatListeners.iterator(),
      specificChatListeners.iterator(chatId)
    );
  }

  private void runChatListUpdate (TdlibChatList chatList, RunnableData<ChatListListener> act) {
    runUpdate(act,
      chatListListeners.iterator(TD.makeChatListKey(chatList.chatList()))
    );
  }

  private void runMessageUpdate (long chatId, RunnableData<MessageListener> act) {
    runUpdate(act,
      messageListeners.iterator(),
      messageChatListeners.iterator(chatId)
    );
  }

  private void runPrivateCallUpdate (int privateCallId, RunnableData<PrivateCallListener> act) {
    runUpdate(act,
      privateCallListeners.iterator(),
      specificPrivateCallListeners.iterator(privateCallId)
    );
  }

  private void runGroupCallUpdate (int groupCallId, RunnableData<GroupCallListener> act) {
    runUpdate(act,
      groupCallListeners.iterator(),
      specificGroupCallListeners.iterator(groupCallId)
    );
  }

  // updateNewMessage

  void updateNewMessage (TdApi.UpdateNewMessage update) {
    runMessageUpdate(update.message.chatId, listener ->
      listener.onNewMessage(update.message)
    );
  }

  // updateMessageSendSucceeded

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
    runMessageUpdate(update.message.chatId, listener ->
      listener.onMessageSendSucceeded(update.message, update.oldMessageId)
    );
  }

  // updateMessageSendFailed

  void updateMessageSendFailed (TdApi.UpdateMessageSendFailed update) {
    replaceMessage(update.oldMessageId, update.message);
    runMessageUpdate(update.message.chatId, listener ->
      listener.onMessageSendFailed(update.message, update.oldMessageId, update.error)
    );
  }

  // updateMessageSendAcknowledged

  void updateMessageSendAcknowledged (TdApi.UpdateMessageSendAcknowledged update) {
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageSendAcknowledged(update.chatId, update.messageId)
    );
  }

  // updateMessageContent

  void updateMessageContent (TdApi.UpdateMessageContent update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.content = update.newContent;
      }
    }
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageContentChanged(update.chatId, update.messageId, update.newContent)
    );
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
    runUpdate(list, listener ->
      listener.onUpdatePoll(updatedPoll)
    );
  }

  // updateMessageEdited

  void updateMessageEdited (TdApi.UpdateMessageEdited update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.editDate = update.editDate;
        message.replyMarkup = update.replyMarkup;
      }
    }
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageEdited(update.chatId, update.messageId, update.editDate, update.replyMarkup)
    );
  }

  // updateMessageContentOpened

  void updateMessageContentOpened (TdApi.UpdateMessageContentOpened update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        TD.setMessageOpened(message);
      }
    }
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageOpened(update.chatId, update.messageId)
    );
  }

  // updateAnimatedEmojiMessageClicked

  void updateAnimatedEmojiMessageClicked (TdApi.UpdateAnimatedEmojiMessageClicked update) {
    runMessageUpdate(update.messageId, listener ->
      listener.onAnimatedEmojiMessageClicked(update.chatId, update.messageId, update.sticker)
    );
  }

  // updateMessageIsPinned

  void updateMessageIsPinned (TdApi.UpdateMessageIsPinned update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.isPinned = update.isPinned;
      }
    }
    runMessageUpdate(update.chatId, listener ->
      listener.onMessagePinned(update.chatId, update.messageId, update.isPinned)
    );
  }

  // updateMessagePendingContentUpdated

  void updateMessagePendingContentChanged (long chatId, long messageId) {
    runUpdate(listener ->
      listener.onMessagePendingContentChanged(chatId, messageId),
      messageEditListeners.iterator(),
      messageEditChatListeners.iterator(chatId)
    );
  }

  // updateMessageLiveLocationViewed

  void updateMessageLiveLocationViewed (TdApi.UpdateMessageLiveLocationViewed update) {
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageLiveLocationViewed(update.chatId, update.messageId)
    );
  }

  // updateMessageMentionRead

  void updateMessageMentionRead (TdApi.UpdateMessageMentionRead update, boolean counterChanged, boolean availabilityChanged) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.containsUnreadMention = false;
      }
    }
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageMentionRead(update.chatId, update.messageId)
    );
    if (counterChanged) {
      runChatUpdate(update.chatId, listener ->
        listener.onChatUnreadMentionCount(update.chatId, update.unreadMentionCount, availabilityChanged)
      );
    }
  }

  // updateOption + getStickerSet

  public void notifyAnimatedEmojiListeners (int type) {
    runUpdate(animatedEmojiListeners, listener ->
      listener.onAnimatedEmojiChanged(type)
    );
  }

  // getEmojiReaction + getCustomReaction

  public void notifyReactionLoaded (String reactionKey) {
    ReferenceList<ReactionLoadListener> list = reactionLoadListeners.removeAll(reactionKey);
    runUpdate(list, listener ->
      listener.onReactionLoaded(reactionKey)
    );
  }

  // notifyChatFolder*

  public void notifyChatFolderNewChatsChanged (int chatFolderId) {
    runUpdate(chatFolderListeners.iterator(chatFolderId), (listener) -> {
      listener.onChatFolderNewChatsChanged(chatFolderId);
    });
  }

  public void notifyChatFolderInviteLinkDeleted (int chatFolderId, String inviteLink) {
    runUpdate(chatFolderListeners.iterator(chatFolderId), (listener) -> {
      listener.onChatFolderInviteLinkDeleted(chatFolderId, inviteLink);
    });
  }

  public void notifyChatFolderInviteLinkChanged (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {
    runUpdate(chatFolderListeners.iterator(chatFolderId), (listener) -> {
      listener.onChatFolderInviteLinkChanged(chatFolderId, inviteLink);
    });
  }

  public void notifyChatFolderInviteLinkCreated (int chatFolderId, TdApi.ChatFolderInviteLink inviteLink) {
    runUpdate(chatFolderListeners.iterator(chatFolderId), (listener) -> {
      listener.onChatFolderInviteLinkCreated(chatFolderId, inviteLink);
    });
  }

  // updateMessageInteractionInfo

  void updateMessageInteractionInfo (TdApi.UpdateMessageInteractionInfo update) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.interactionInfo = update.interactionInfo;
      }
    }
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageInteractionInfoChanged(update.chatId, update.messageId, update.interactionInfo)
    );
  }

  // updateMessageUnreadReactions

  void updateMessageUnreadReactions (TdApi.UpdateMessageUnreadReactions update, boolean counterChanged, boolean availabilityChanged, TdApi.Chat chat, @Nullable TdlibChatList[] chatLists) {
    List<TdApi.Message> messages = pendingMessages.get(update.chatId + "_" + update.messageId);
    if (messages != null) {
      for (TdApi.Message message : messages) {
        message.unreadReactions = update.unreadReactions;
      }
    }
    runMessageUpdate(update.chatId, listener ->
      listener.onMessageUnreadReactionsChanged(update.chatId, update.messageId, update.unreadReactions, update.unreadReactionCount)
    );
    if (counterChanged) {
      runChatUpdate(update.chatId, listener ->
        listener.onChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged)
      );
      if (chatLists != null) {
        for (TdlibChatList chatList : chatLists) {
          runChatListUpdate(chatList, listener ->
            listener.onChatListItemChanged(chatList, chat, availabilityChanged ? ChatListListener.ItemChangeType.UNREAD_AVAILABILITY_CHANGED : ChatListListener.ItemChangeType.READ_INBOX)
          );
        }
      }
    }
  }

  // updateDeleteMessages

  void updateMessagesDeleted (TdApi.UpdateDeleteMessages update) {
    runMessageUpdate(update.chatId, listener ->
      listener.onMessagesDeleted(update.chatId, update.messageIds)
    );
  }

  // updateChatUnreadMentionCount

  void updateChatUnreadMentionCount (TdApi.UpdateChatUnreadMentionCount update, boolean availabilityChanged) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatUnreadMentionCount(update.chatId, update.unreadMentionCount, availabilityChanged)
    );
  }

  // updateChatUnreadReactionCount

  void updateChatUnreadReactionCount (TdApi.UpdateChatUnreadReactionCount update, boolean availabilityChanged, TdApi.Chat chat, @Nullable TdlibChatList[] chatLists) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatUnreadReactionCount(update.chatId, update.unreadReactionCount, availabilityChanged)
    );
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        runChatListUpdate(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, availabilityChanged ? ChatListListener.ItemChangeType.UNREAD_AVAILABILITY_CHANGED : ChatListListener.ItemChangeType.READ_INBOX)
        );
      }
    }
  }

  // updateChatLastMessage

  void updateChatLastMessage (TdApi.UpdateChatLastMessage update, @Nullable List<Tdlib.ChatListChange> listChanges) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatTopMessageChanged(update.chatId, update.lastMessage);
      if (listChanges != null) {
        for (Tdlib.ChatListChange listChange : listChanges) {
          Tdlib.ChatChange positionChange = listChange.change;
          listener.onChatPositionChanged(update.chatId, positionChange.position, positionChange.orderChanged(), positionChange.sourceChanged(), positionChange.pinStateChanged());
        }
      }
    });
    if (listChanges != null) {
      for (Tdlib.ChatListChange listChange : listChanges) {
        listChange.list.onUpdateChatPosition(listChange.chat, listChange.change);
        runChatListUpdate(listChange.list, listener ->
          listener.onChatListItemChanged(listChange.list, listChange.chat, ChatListListener.ItemChangeType.LAST_MESSAGE)
        );
      }
    }
  }

  // updateChatOrder

  void updateChatPosition (TdApi.UpdateChatPosition update, Tdlib.ChatListChange listChange) {
    boolean orderChanged = listChange.change.orderChanged();
    boolean sourceChanged = listChange.change.sourceChanged();
    boolean pinStateChanged = listChange.change.pinStateChanged();
    runChatUpdate(update.chatId, listener ->
      listener.onChatPositionChanged(update.chatId, update.position, orderChanged, sourceChanged, pinStateChanged)
    );
    listChange.list.onUpdateChatPosition(listChange.chat, listChange.change);
  }

  // updateChatAddedToList, updateChatRemovedFromList

  void updateChatAddedToList (TdApi.UpdateChatAddedToList update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatAddedToList(update.chatId, update.chatList)
    );
  }

  void updateChatRemovedFromList (TdApi.UpdateChatRemovedFromList update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatRemovedFromList(update.chatId, update.chatList)
    );
  }

  // updateChatPermissions

  void updateChatPermissions (TdApi.UpdateChatPermissions update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatPermissionsChanged(update.chatId, update.permissions)
    );
  }

  // updateChatTitle

  void updateChatTitle (TdApi.UpdateChatTitle update, TdApi.Chat chat, TdlibChatList[] chatLists) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatTitleChanged(update.chatId, update.title)
    );
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        runChatListUpdate(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, ChatListListener.ItemChangeType.TITLE)
        );
      }
    }
  }

  // updateChatTheme

  void updateChatTheme (TdApi.UpdateChatTheme update, TdApi.Chat chat, @Nullable TdlibChatList[] chatLists) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatThemeChanged(update.chatId, update.theme)
    );
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        runChatListUpdate(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, ChatListListener.ItemChangeType.THEME)
        );
      }
    }
  }

  // updateChatPhoto

  void updateChatPhoto (TdApi.UpdateChatPhoto update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatPhotoChanged(update.chatId, update.photo)
    );
  }

  // updateChatActionBar

  void updateChatActionBar (TdApi.UpdateChatActionBar update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatActionBarChanged(update.chatId, update.actionBar)
    );
  }

  // updateChatBusinessBotManagerBar

  void updateChatBusinessBotManageBar (TdApi.UpdateChatBusinessBotManageBar update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatBusinessBotManageBarChanged(update.chatId, update.businessBotManageBar)
    );
  }

  // updateChatHasScheduledMessages

  void updateChatHasScheduledMessages (TdApi.UpdateChatHasScheduledMessages update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatHasScheduledMessagesChanged(update.chatId, update.hasScheduledMessages)
    );
  }

  // updateChatHasProtectedContent

  void updateChatHasProtectedContent (TdApi.UpdateChatHasProtectedContent update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatHasProtectedContentChanged(update.chatId, update.hasProtectedContent)
    );
  }

  // updateChatReadInbox

  void updateChatReadInbox (TdApi.UpdateChatReadInbox update, boolean availabilityChanged, TdApi.Chat chat, TdlibChatList[] chatLists) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatReadInbox(update.chatId, update.lastReadInboxMessageId, update.unreadCount, availabilityChanged)
    );
    if (chatLists != null) {
      for (TdlibChatList chatList : chatLists) {
        runChatListUpdate(chatList, listener ->
          listener.onChatListItemChanged(chatList, chat, availabilityChanged ? ChatListListener.ItemChangeType.UNREAD_AVAILABILITY_CHANGED : ChatListListener.ItemChangeType.READ_INBOX)
        );
      }
    }
  }

  // updateChatReadOutbox

  void updateChatReadOutbox (TdApi.UpdateChatReadOutbox update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatReadOutbox(update.chatId, update.lastReadOutboxMessageId)
    );
  }

  // updateChatReplyMarkup

  void updateChatReplyMarkup (TdApi.UpdateChatReplyMarkup update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatReplyMarkupChanged(update.chatId, update.replyMarkupMessageId)
    );
  }

  // updateChatDraftMessage

  void updateChatDraftMessage (TdApi.UpdateChatDraftMessage update, @Nullable List<Tdlib.ChatListChange> listChanges) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatDraftMessageChanged(update.chatId, update.draftMessage);
      if (listChanges != null) {
        for (Tdlib.ChatListChange listChange : listChanges) {
          Tdlib.ChatChange positionChange = listChange.change;
          listener.onChatPositionChanged(update.chatId, positionChange.position,
            positionChange.orderChanged(),
            positionChange.sourceChanged(),
            positionChange.pinStateChanged()
          );
        }
      }
    });
    if (listChanges != null) {
      for (Tdlib.ChatListChange listChange : listChanges) {
        listChange.list.onUpdateChatPosition(listChange.chat, listChange.change);
        runChatListUpdate(listChange.list, listener ->
          listener.onChatListItemChanged(listChange.list, listChange.chat, ChatListListener.ItemChangeType.DRAFT)
        );
      }
    }
  }

  // updateChatFolders

  void updateChatFolders (TdApi.UpdateChatFolders update) {
    runUpdate(chatFoldersListeners, listener ->
      listener.onChatFoldersChanged(update.chatFolders, update.mainChatListPosition)
    );
  }

  // updateChatAvailableReactions

  void updateChatAvailableReactions (TdApi.UpdateChatAvailableReactions update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatAvailableReactionsUpdated(update.chatId, update.availableReactions)
    );
  }

  // updateCall

  void updateCall (TdApi.UpdateCall update) {
    runPrivateCallUpdate(update.call.id, listener ->
      listener.onCallUpdated(update.call)
    );
  }

  // updateNewCallSignalingData

  void updateNewCallSignalingData (TdApi.UpdateNewCallSignalingData update) {
    runPrivateCallUpdate(update.callId, listener ->
      listener.onNewCallSignalingDataArrived(update.callId, update.data)
    );
  }

  // updateGroupCall

  void updateGroupCall (TdApi.UpdateGroupCall update) {
    runGroupCallUpdate(update.groupCall.id, listener ->
      listener.onGroupCallUpdated(update.groupCall)
    );
  }

  // updateGroupCallParticipant

  void updateGroupCallParticipant (TdApi.UpdateGroupCallParticipant update) {
    runGroupCallUpdate(update.groupCallId, listener ->
      listener.onGroupCallParticipantUpdated(update.groupCallId, update.participant)
    );
  }

  // updateGroupCallParticipants

  void updateGroupCallParticipants (TdApi.UpdateGroupCallParticipants update) {
    runGroupCallUpdate(update.groupCallId, listener ->
      listener.onGroupCallParticipantsChanged(update.groupCallId, update.participantUserIds)
    );
  }

  // updateNewGroupCallMessage

  void updateNewGroupCallMessage (TdApi.UpdateNewGroupCallMessage update) {
    runGroupCallUpdate(update.groupCallId, listener ->
      listener.onNewGroupCallMessage(update.groupCallId, update.message)
    );
  }

  // updateNewGroupCallPaidReaction

  void updateNewGroupCallPaidReaction (TdApi.UpdateNewGroupCallPaidReaction update) {
    runGroupCallUpdate(update.groupCallId, listener ->
      listener.onNewGroupCallPaidReaction(update.groupCallId, update.senderId, update.starCount)
    );
  }

  // updateGroupCallMessageLevels

  void updateGroupCallMessageLevels (TdApi.UpdateGroupCallMessageLevels update) {
    runUpdate(optionListeners, listener ->
      listener.onGroupCallMessageLevelsUpdated(update.levels)
    );
  }

  // updateGroupCallMessageSendFailed

  void updateGroupCallMessageSendFailed (TdApi.UpdateGroupCallMessageSendFailed update) {
    runGroupCallUpdate(update.groupCallId, listener ->
      listener.onGroupCallMessageSendFailed(update.groupCallId, update.messageId, update.error)
    );
  }

  // updateGroupCallMessagesDeleted

  void updateGroupCallMessagesDeleted (TdApi.UpdateGroupCallMessagesDeleted update) {
    runGroupCallUpdate(update.groupCallId, listener ->
      listener.onGroupCallMessagesDeleted(update.groupCallId, update.messageIds)
    );
  }

  // updateGroupCallVerificationState

  void updateGroupCallVerificationState (TdApi.UpdateGroupCallVerificationState update) {
    runGroupCallUpdate(update.groupCallId, listener ->
      listener.onGroupCallVerificationStateChanged(update.groupCallId, update.generation, update.emojis)
    );
  }

  // updateChatOnlineMemberCount

  void updateChatOnlineMemberCount (TdApi.UpdateChatOnlineMemberCount update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatOnlineMemberCountChanged(update.chatId, update.onlineMemberCount)
    );
  }

  // updateMessageTtlSetting

  void updateChatMessageAutoDeleteTime (TdApi.UpdateChatMessageAutoDeleteTime update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatMessageTtlSettingChanged(update.chatId, update.messageAutoDeleteTime)
    );
  }

  // updateChatActiveStories

  void updateChatActiveStories (TdApi.UpdateChatActiveStories update) {
    runChatUpdate(update.activeStories.chatId, listener ->
      listener.onChatActiveStoriesChanged(update.activeStories)
    );
  }

  // updateStory

  private static String uniqueStoryKey (TdApi.Story story) {
    return uniqueStoryKey(story.posterChatId, story.id);
  }

  private static String uniqueStoryKey (long posterChatId, int storyId) {
    return posterChatId + "_" + storyId;
  }

  void updateStory (TdApi.UpdateStory update) {
    runUpdate(listener -> listener.onStoryUpdated(update.story),
      storyListeners.iterator(),
      specificStoryListeners.iterator(uniqueStoryKey(update.story))
    );
  }

  // updateStoryDeleted

  void updateStoryDeleted (TdApi.UpdateStoryDeleted update) {
    runUpdate(listener -> listener.onStoryDeleted(update.storyPosterChatId, update.storyId),
      storyListeners.iterator(),
      specificStoryListeners.iterator(uniqueStoryKey(update.storyPosterChatId, update.storyId))
    );
  }

  // updateStorySendSucceeded

  void updateStoryPostSucceeded (TdApi.UpdateStoryPostSucceeded update) {
    runUpdate(listener -> listener.onStorySendSucceeded(update.story, update.oldStoryId),
      storyListeners.iterator(),
      specificStoryListeners.iterator(uniqueStoryKey(update.story.posterChatId, update.oldStoryId))
    );
  }

  // updateStorySendFailed

  void updateStoryPostFailed (TdApi.UpdateStoryPostFailed update) {
    runUpdate(listener -> listener.onStorySendFailed(update.story, update.error, update.errorType),
      storyListeners.iterator(),
      specificStoryListeners.iterator(uniqueStoryKey(update.story))
    );
  }

  // updateStoryStealthMode

  void updateStoryStealthMode (TdApi.UpdateStoryStealthMode update) {
    runUpdate(listener -> listener.onStoryStealthModeUpdated(update.activeUntilDate, update.cooldownUntilDate),
      storyListeners.iterator(),
      specificStoryListeners.combinedIterator()
    );
  }

  // updateChatVoiceChat

  void updateChatVideoChat (TdApi.UpdateChatVideoChat update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatVideoChatChanged(update.chatId, update.videoChat);
    });
  }

  // updateForumTopicInfo

  private static String uniqueForumTopicKey (long chatId, int forumTopicId) {
    return chatId + "_" + forumTopicId;
  }

  void updateForumTopicInfo (TdApi.UpdateForumTopicInfo update) {
    runForumUpdate(update.info.chatId, update.info.forumTopicId, listener ->
      listener.onForumTopicInfoChanged(update.info)
    );
  }

  // updateForumTopic

  void updateForumTopic (TdApi.UpdateForumTopic update) {
    runForumUpdate(update.chatId, update.forumTopicId, listener ->
      listener.onForumTopicUpdated(update.chatId, update.forumTopicId, update.isPinned, update.lastReadInboxMessageId, update.lastReadOutboxMessageId, update.notificationSettings)
    );
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

  // updateChatAccentColors

  void updateChatAccentColors (TdApi.UpdateChatAccentColors update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatAccentColorsChanged(update.chatId,
        update.accentColorId,
        update.backgroundCustomEmojiId,
        update.profileAccentColorId,
        update.profileBackgroundCustomEmojiId
      )
    );
  }

  // updateChatEmojiStatus

  void updateChatEmojiStatus (TdApi.UpdateChatEmojiStatus update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatEmojiStatusChanged(update.chatId,
        update.emojiStatus
      )
    );
  }

  // updateChatIsTranslatable

  void updateChatIsTranslatable (TdApi.UpdateChatIsTranslatable update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatIsTranslatableChanged(update.chatId, update.isTranslatable);
    });
  }

  // updateChatIsBlocked

  void updateChatBlockList (TdApi.UpdateChatBlockList update) {
    runChatUpdate(update.chatId, listener -> {
      listener.onChatBlockListChanged(update.chatId, update.blockList);
    });
  }

  // updateChatClientDataChanged

  void updateChatClientDataChanged (long chatId, String newClientData) {
    runChatUpdate(chatId, listener ->
      listener.onChatClientDataChanged(chatId, newClientData)
    );
  }

  // updateNotificationSettings

  @TdlibThread
  void updateNotificationSettings (TdApi.UpdateChatNotificationSettings update) {
    runUpdate(listener -> listener.onNotificationSettingsChanged(update.chatId, update.notificationSettings),
      settingsListeners.iterator(),
      chatSettingsListeners.iterator(update.chatId)
    );
  }

  @TdlibThread
  void updateNotificationSettings (TdApi.UpdateScopeNotificationSettings update) {
    runUpdate(settingsListeners.iterator(), listener ->
      listener.onNotificationSettingsChanged(update.scope, update.notificationSettings)
    );
  }

  @TdlibThread
  void updateReactionNotificationSettings (TdApi.UpdateReactionNotificationSettings update) {
    runUpdate(settingsListeners.iterator(), listener ->
      listener.onReactionNotificationSettingsChanged(update.notificationSettings)
    );
  }

  @AnyThread
  void updateNotificationChannel (TdApi.NotificationSettingsScope scope) {
    runUpdate(settingsListeners.iterator(), listener ->
      listener.onNotificationChannelChanged(scope)
    );
  }

  @AnyThread
  void updateNotificationChannel (long chatId) {
    runUpdate(listener -> listener.onNotificationChannelChanged(chatId),
      settingsListeners.iterator(),
      chatSettingsListeners.iterator(chatId)
    );
  }

  @AnyThread
  public void updateNotificationGlobalSettings () {
    runUpdate(settingsListeners, NotificationSettingsListener::onNotificationGlobalSettingsChanged);
  }

  @AnyThread
  public void notifyArchiveChatListSettingsChanged (TdApi.ArchiveChatListSettings archiveChatListSettings) {
    runUpdate(settingsListeners, listener ->
      listener.onArchiveChatListSettingsChanged(archiveChatListSettings)
    );
  }

  @AnyThread
  public void notifyChatCountersChanged (TdApi.ChatList chatList, TdlibCounter counter, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
    runUpdate(totalCountersListeners, listener ->
      listener.onChatCounterChanged(chatList, counter, availabilityChanged, totalCount, unreadCount, unreadUnmutedCount)
    );
  }

  @AnyThread
  public void notifyMessageCountersChanged (TdApi.ChatList chatList, TdlibCounter counter, int unreadCount, int unreadUnmutedCount) {
    runUpdate(totalCountersListeners, listener ->
      listener.onMessageCounterChanged(chatList, counter, unreadCount, unreadUnmutedCount)
    );
  }

  // chat lists

  @TdlibThread
  void updateChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    runChatListUpdate(chatList, listener -> {
      listener.onChatAdded(chatList, chat, atIndex, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_ADDED);
    });
  }

  @TdlibThread
  void updateChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    runChatListUpdate(chatList, listener -> {
      listener.onChatRemoved(chatList, chat, fromIndex, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_REMOVED);
    });
  }

  @TdlibThread
  void updateChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    runChatListUpdate(chatList, listener -> {
      listener.onChatMoved(chatList, chat, fromIndex, toIndex, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_MOVED);
    });
  }

  @TdlibThread
  void updateChatChanged (TdlibChatList chatList, TdApi.Chat chat, int index, Tdlib.ChatChange changeInfo) {
    runChatListUpdate(chatList, listener -> {
      listener.onChatChanged(chatList, chat, index, changeInfo);
      listener.onChatListChanged(chatList, ChatListListener.ChangeFlags.ITEM_METADATA_CHANGED);
    });
  }

  @TdlibThread
  void updateChatListStateChanged (TdlibChatList chatList, @TdlibChatList.State int newState, @TdlibChatList.State int oldState) {
    runChatListUpdate(chatList, listener ->
      listener.onChatListStateChanged(chatList, newState, oldState)
    );
  }

  // updatePrivacySettingRules

  @AnyThread
  public void updatePrivacySettingRules (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    runUpdate(privacySettingsListeners, listener ->
      listener.onPrivacySettingRulesChanged(setting, rules)
    );
  }

  @AnyThread
  public void updateReadDatePrivacySettings (TdApi.ReadDatePrivacySettings settings) {
    runUpdate(privacySettingsListeners, listener ->
      listener.onReadDatePrivacySettingsChanged(settings)
    );
  }

  @AnyThread
  public void updateNewChatPrivacySettings (TdApi.NewChatPrivacySettings settings) {
    runUpdate(privacySettingsListeners, listener ->
      listener.onNewChatPrivacySettingsChanged(settings)
    );
  }

  // updateFile

  void updateFile (TdApi.UpdateFile update) {
    runUpdate(fileUpdateListeners.iterator(update.file.id), listener ->
      listener.onUpdateFile(update)
    );
  }

  public void addFileListener (int fileId, FileUpdateListener listener) {
    fileUpdateListeners.add(fileId, listener);
  }

  public void removeFileListener (int fileId, FileUpdateListener listener) {
    fileUpdateListeners.remove(fileId, listener);
  }

  // updateConnectionState

  void updateConnectionState (@ConnectionState int newState, int oldState) {
    runUpdate(connectionListeners, listener ->
      listener.onConnectionStateChanged(newState, oldState)
    );
  }

  void updateConnectionType (@NonNull TdApi.NetworkType networkType) {
    runUpdate(connectionListeners, listener ->
      listener.onConnectionTypeChanged(networkType)
    );
  }

  void updateConnectionDisplayStatusChanged () {
    runUpdate(connectionListeners, ConnectionListener::onConnectionDisplayStatusChanged);
  }

  // updateInstalledStickerSets

  void updateInstalledStickerSets (TdApi.UpdateInstalledStickerSets update) {
    runUpdate(stickersListeners, listener ->
      listener.onInstalledStickerSetsUpdated(update.stickerSetIds, update.stickerType)
    );
  }

  // CUSTOM updateStickerSetArchived

  public final void notifyStickerSetArchived (TdApi.StickerSetInfo info) {
    runUpdate(stickersListeners, listener ->
      listener.onStickerSetArchived(info)
    );
  }

  public final void notifyStickerSetRemoved (TdApi.StickerSetInfo info) {
    runUpdate(stickersListeners, listener ->
      listener.onStickerSetRemoved(info)
    );
  }

  public final void notifyStickerSetInstalled (TdApi.StickerSetInfo info) {
    runUpdate(stickersListeners, listener ->
      listener.onStickerSetInstalled(info)
    );
  }

  // updateFavoriteStickers

  void updateFavoriteStickers (TdApi.UpdateFavoriteStickers update) {
    runUpdate(stickersListeners, listener ->
      listener.onFavoriteStickersUpdated(update.stickerIds)
    );
  }

  // updateStickerSet

  void updateStickerSet (TdApi.StickerSet stickerSet) {
    runUpdate(stickersListeners, listener ->
      listener.onStickerSetUpdated(stickerSet)
    );
  }

  // updateRecentStickers

  void updateRecentStickers (TdApi.UpdateRecentStickers update) {
    runUpdate(stickersListeners, listener ->
      listener.onRecentStickersUpdated(update.stickerIds, update.isAttached)
    );
  }

  // updateTrendingStickerSets

  void updateTrendingStickerSets (TdApi.UpdateTrendingStickerSets update, int unreadCount) {
    runUpdate(stickersListeners, listener ->
      listener.onTrendingStickersUpdated(update.stickerType, update.stickerSets, unreadCount)
    );
  }

  // updateTrustedMiniAppBots

  void updateTrustedMiniAppBots (TdApi.UpdateTrustedMiniAppBots update) {
    runUpdate(optionListeners, listener ->
      listener.onTrustedMiniAppBotsUpdated(update.botUserIds)
    );
  }

  // updateSavedAnimations

  void updateSavedAnimations (TdApi.UpdateSavedAnimations update) {
    runUpdate(animationsListeners, listener ->
      listener.onSavedAnimationsUpdated(update.animationIds)
    );
  }

  // updateChatDefaultDisableNotifications

  void updateChatDefaultDisableNotifications (TdApi.UpdateChatDefaultDisableNotification update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatDefaultDisableNotifications(update.chatId, update.defaultDisableNotification)
    );
  }

  // updateChatDefaultMessageSenderId

  void updateChatDefaultMessageSenderId (TdApi.UpdateChatMessageSender update) {
    runChatUpdate(update.chatId, listener ->
      listener.onChatDefaultMessageSenderIdChanged(update.chatId, update.messageSenderId)
    );
  }

  // updateOption

  void updateTopChatsDisabled (boolean areDisabled) {
    runUpdate(optionListeners, listener ->
      listener.onTopChatsDisabled(areDisabled)
    );
  }

  void updatedSentScheduledMessageNotificationsDisabled (boolean areDisabled) {
    runUpdate(optionListeners, listener ->
      listener.onSentScheduledMessageNotificationsDisabled(areDisabled)
    );
  }

  void updateArchiveAndMuteChatsFromUnknownUsersEnabled (boolean enabled) { // FIXME?
    for (TdlibOptionListener listener : optionListeners) {
      listener.onArchiveAndMuteChatsFromUnknownUsersEnabled(enabled);
    }
  }

  void updateSuggestedLanguageChanged (String languagePackId, TdApi.LanguagePackInfo languagePackInfo) {
    runUpdate(optionListeners, listener ->
      listener.onSuggestedLanguagePackChanged(languagePackId, languagePackInfo)
    );
  }

  void updateContactRegisteredNotificationsDisabled (boolean areDisabled) {
    runUpdate(optionListeners, listener ->
      listener.onContactRegisteredNotificationsDisabled(areDisabled)
    );
  }

  void updateAccentColors (TdApi.UpdateAccentColors update) {
    runUpdate(optionListeners, listener ->
      listener.onAccentColorsChanged(update.colors, update.availableAccentColorIds)
    );
  }

  void updateProfileAccentColors (TdApi.UpdateProfileAccentColors update, boolean listChanged) {
    runUpdate(optionListeners, listener ->
      listener.onProfileAccentColorsChanged(listChanged)
    );
  }

  void updateSuggestedActions (TdApi.UpdateSuggestedActions update) {
    runUpdate(optionListeners, listener ->
      listener.onSuggestedActionsChanged(update.addedActions, update.removedActions)
    );
  }

  void updateChatRevenueAmount (TdApi.UpdateChatRevenueAmount update) {
    runUpdate(optionListeners, listener ->
      listener.onChatRevenueUpdated(update.chatId, update.revenueAmount)
    );
  }

  void updateStarRevenueStatus (TdApi.UpdateStarRevenueStatus update) {
    runUpdate(optionListeners, listener ->
      listener.onStarRevenueStatusUpdated(update.ownerId, update.status)
    );
  }

  void updateTonRevenueStatus (TdApi.UpdateTonRevenueStatus update) {
    runUpdate(optionListeners, listener ->
      listener.onTonRevenueStatusUpdated(update.status)
    );
  }

  void updateSpeedLimitNotification (TdApi.UpdateSpeedLimitNotification update) {
    runUpdate(optionListeners, listener ->
      listener.onSpeedLimitNotification(update.isUpload)
    );
  }

  void updateContactCloseBirthdayUsers (TdApi.UpdateContactCloseBirthdays update) {
    runUpdate(optionListeners, listener ->
      listener.onContactCloseBirthdayUsersChanged(update.closeBirthdayUsers)
    );
  }
}
