package org.thunderdog.challegram.telegram;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
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

/**
 * Date: 2/15/18
 * Author: default
 */

public class TdlibListeners {
  private final Tdlib tdlib;

  final ReferenceList<MessageListener> messageListeners;
  final ReferenceList<MessageEditListener> messageEditListeners;
  final ReferenceList<ChatListener> chatListeners;
  final ReferenceMap<String, ChatListListener> chatListListeners;
  final ReferenceList<NotificationSettingsListener> settingsListeners;
  final ReferenceList<StickersListener> stickersListeners;
  final ReferenceList<AnimationsListener> animationsListeners;
  final ReferenceList<ConnectionListener> connectionListeners;
  final ReferenceList<DayChangeListener> dayListeners;
  final ReferenceList<AuthorizationListener> authorizationListeners;
  final ReferenceList<CleanupStartupDelegate> componentDelegates;
  final ReferenceList<TdlibOptionListener> optionListeners;
  final ReferenceList<CounterChangeListener> totalCountersListeners;
  final ReferenceList<ChatsNearbyListener> chatsNearbyListeners;
  final ReferenceList<PrivacySettingsListener> privacySettingsListeners;
  final ReferenceList<CallsListener> callsListeners;
  final ReferenceList<SessionListener> sessionListeners;

  final ReferenceList<AnimatedEmojiListener> animatedEmojiListeners;

  final ReferenceLongMap<MessageListener> messageChatListeners;
  final ReferenceLongMap<MessageEditListener> messageEditChatListeners;
  final ReferenceLongMap<ChatListener> specificChatListeners;
  final ReferenceLongMap<NotificationSettingsListener> chatSettingsListeners;
  final ReferenceIntMap<FileUpdateListener> fileUpdateListeners;
  final ReferenceLongMap<PollListener> pollListeners;

  final Map<String, List<TdApi.Message>> pendingMessages = new HashMap<>();

  public TdlibListeners (Tdlib tdlib) {
    this.tdlib = tdlib;

    this.messageListeners = new ReferenceList<>();
    this.messageEditListeners = new ReferenceList<>();
    this.chatListeners = new ReferenceList<>();
    this.chatListListeners = new ReferenceMap<>(true);
    this.settingsListeners = new ReferenceList<>(true);
    this.stickersListeners = new ReferenceList<>(true);
    this.animationsListeners = new ReferenceList<>();
    this.connectionListeners = new ReferenceList<>(true);
    this.dayListeners = new ReferenceList<>();
    this.authorizationListeners = new ReferenceList<>(true);
    this.componentDelegates = new ReferenceList<>(true);
    this.optionListeners = new ReferenceList<>(true);
    this.totalCountersListeners = new ReferenceList<>(true);
    this.chatsNearbyListeners = new ReferenceList<>(true);
    this.privacySettingsListeners = new ReferenceList<>();
    this.callsListeners = new ReferenceList<>(true);
    this.sessionListeners = new ReferenceList<>(true);

    this.animatedEmojiListeners = new ReferenceList<>(true);

    this.messageChatListeners = new ReferenceLongMap<>();
    this.messageEditChatListeners = new ReferenceLongMap<>();
    this.specificChatListeners = new ReferenceLongMap<>();
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
      if (any instanceof DayChangeListener) {
        dayListeners.add((DayChangeListener) any);
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
      if (any instanceof CallsListener) {
        callsListeners.add((CallsListener) any);
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
      if (any instanceof DayChangeListener) {
        dayListeners.remove((DayChangeListener) any);
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
      if (any instanceof CallsListener) {
        callsListeners.remove((CallsListener) any);
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
  public void addCallsListener (CallsListener listener) {
    callsListeners.add(listener);
  }

  @AnyThread
  public void removeCallsListener (CallsListener listener) {
    callsListeners.remove(listener);
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
  public void subscribeToChatListUpdates (@NonNull TdApi.ChatList chatList, ChatListListener listener) {
    chatListListeners.add(TD.makeChatListKey(chatList), listener);
  }

  @AnyThread
  public void unsubscribeFromChatListUpdates (@NonNull TdApi.ChatList chatList, ChatListListener listener) {
    chatListListeners.remove(TD.makeChatListKey(chatList), listener);
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
        list.next().onMessageSendFailed(update.message, update.oldMessageId, update.errorCode, update.errorMessage);
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

  // updateChatFilters

  void updateChatFilters (TdApi.UpdateChatFilters update) {
    // TODO?
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

  private static void updateCall (TdApi.Call call, @Nullable Iterator<CallsListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onCallUpdated(call);
      }
    }
  }

  void updateCall (TdApi.UpdateCall update) {
    updateCall(update.call, callsListeners.iterator());
  }

  // updateNewCallSignalingData

  private static void updateNewCallSignalingData (int callId, byte[] data, @Nullable Iterator<CallsListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onNewCallSignallingDataArrived(callId, data);
      }
    }
  }

  void updateNewCallSignalingData (TdApi.UpdateNewCallSignalingData update) {
    updateNewCallSignalingData(update.callId, update.data, callsListeners.iterator());
  }

  // updateGroupCallParticipant

  private static void updateGroupCallParticipant (int groupCallId, TdApi.GroupCallParticipant groupCallParticipant, @Nullable Iterator<CallsListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onGroupCallParticipantUpdated(groupCallId, groupCallParticipant);
      }
    }
  }

  void updateGroupCallParticipant (TdApi.UpdateGroupCallParticipant update) {
    updateGroupCallParticipant(update.groupCallId, update.participant, callsListeners.iterator());
  }

  // updateGroupCall

  private static void updateGroupCall (TdApi.GroupCall groupCall, @Nullable Iterator<CallsListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onGroupCallUpdated(groupCall);
      }
    }
  }

  void updateGroupCall (TdApi.UpdateGroupCall update) {
    updateGroupCall(update.groupCall, callsListeners.iterator());
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

  private static void updateChatMessageTtlSetting (long chatId, int messageTtlSetting, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatMessageTtlSettingChanged(chatId, messageTtlSetting);
      }
    }
  }

  void updateChatMessageTtlSetting (TdApi.UpdateChatMessageTtl update) {
    updateChatMessageTtlSetting(update.chatId, update.messageTtl, chatListeners.iterator());
    updateChatMessageTtlSetting(update.chatId, update.messageTtl, specificChatListeners.iterator(update.chatId));
  }

  // updateChatVoiceChat

  private static void updateChatVideoChat (long chatId, TdApi.VideoChat voiceChat, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatVideoChatChanged(chatId, voiceChat);
      }
    }
  }

  void updateChatVideoChat (TdApi.UpdateChatVideoChat update) {
    updateChatVideoChat(update.chatId, update.videoChat, chatListeners.iterator());
    updateChatVideoChat(update.chatId, update.videoChat, specificChatListeners.iterator(update.chatId));
  }

  // updateChatPendingJoinRequests

  private static void updateChatPendingJoinRequests (long chatId, TdApi.ChatJoinRequestsInfo pendingJoinRequests, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatPendingJoinRequestsChanged(chatId, pendingJoinRequests);
      }
    }
  }

  void updateChatPendingJoinRequests (TdApi.UpdateChatPendingJoinRequests update) {
    updateChatPendingJoinRequests(update.chatId, update.pendingJoinRequests, chatListeners.iterator());
    updateChatPendingJoinRequests(update.chatId, update.pendingJoinRequests, specificChatListeners.iterator(update.chatId));
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

  private static void updateChatIsMarkedAsUnread (long chatId, boolean isMarkedAsUnread, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatMarkedAsUnread(chatId, isMarkedAsUnread);
      }
    }
  }

  void updateChatIsMarkedAsUnread (TdApi.UpdateChatIsMarkedAsUnread update) {
    updateChatIsMarkedAsUnread(update.chatId, update.isMarkedAsUnread, chatListeners.iterator());
    updateChatIsMarkedAsUnread(update.chatId, update.isMarkedAsUnread, specificChatListeners.iterator(update.chatId));
  }

  // updateChatIsBlocked

  private static void updateChatIsBlocked (long chatId, boolean isBlocked, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatBlocked(chatId, isBlocked);
      }
    }
  }

  void updateChatIsBlocked (TdApi.UpdateChatIsBlocked update) {
    updateChatIsBlocked(update.chatId, update.isBlocked, chatListeners.iterator());
    updateChatIsBlocked(update.chatId, update.isBlocked, specificChatListeners.iterator(update.chatId));
  }

  // updateChatClientDataChanged

  private static void updateChatClientDataChanged (long chatId, String clientData, @Nullable Iterator<ChatListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatClientDataChanged(chatId, clientData);
      }
    }
  }

  void updateChatClientDataChanged (long chatId, String newClientData) {
    updateChatClientDataChanged(chatId, newClientData, chatListeners.iterator());
    updateChatClientDataChanged(chatId, newClientData, specificChatListeners.iterator(chatId));
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
  public void notifyChatCountersChanged (TdApi.ChatList chatList, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) {
    for (CounterChangeListener listener : totalCountersListeners) {
      listener.onChatCounterChanged(chatList, availabilityChanged, totalCount, unreadCount, unreadUnmutedCount);
    }
  }

  @AnyThread
  public void notifyMessageCountersChanged (TdApi.ChatList chatList, int unreadCount, int unreadUnmutedCount) {
    for (CounterChangeListener listener : totalCountersListeners) {
      listener.onMessageCounterChanged(chatList, unreadCount, unreadUnmutedCount);
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

  // updateInstalledStickerSets

  void updateInstalledStickerSets (TdApi.UpdateInstalledStickerSets update) {
    for (StickersListener listener : stickersListeners) {
      listener.onInstalledStickerSetsUpdated(update.stickerSetIds, update.isMasks);
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
      listener.onTrendingStickersUpdated(update.stickerSets, unreadCount);
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

  void updateSuggestedActions (TdApi.UpdateSuggestedActions update) {
    for (TdlibOptionListener listener : optionListeners) {
      listener.onSuggestedActionsChanged(update.addedActions, update.removedActions);
    }
  }
}
