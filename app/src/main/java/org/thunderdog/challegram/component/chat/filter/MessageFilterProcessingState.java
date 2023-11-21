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
 * File created on 16/11/2023
 */
package org.thunderdog.challegram.component.chat.filter;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class MessageFilterProcessingState implements Destroyable, MessagesFilterProvider.MessageSenderUpdatesListener,
  MessagesFilterProvider.UsernameResolverUpdatesListener, MessagesFilterProvider.ChatCustomFilterSettingsUpdatesListener {
  private final Tdlib tdlib;
  private final TGMessage message;

  private final long chatId;
  private final long senderId;

  private final Set<Long> resolvedMentions = new HashSet<>();
  private boolean senderIsBlocked;


  public MessageFilterProcessingState (TGMessage tgMessage, TdApi.Message message) {
    this.tdlib = tgMessage.tdlib();
    this.message = tgMessage;

    this.chatId = message.chatId;
    this.senderId = Td.getSenderId(message.senderId);
    this.senderIsBlocked = tdlib.chatFullyBlocked(senderId);

    this.rawContents.put(message.id, message.content);
    checkContent();

    subscribeToSenderId(senderId);
    tdlib.messagesFilterProvider().subscribeToChatCustomFilterSettingsUpdates(chatId, this);
  }

  @Override
  public void onChatFilterSettingsUpdate (long chatId) {
    if (this.chatId == chatId) {
      checkCurrentFilterReason();
    }
  }

  @Override
  public void onMessageSenderBlockedUpdate (long chatId, boolean isBlocked) {
    boolean needCheckReason = false;

    if (this.senderId == chatId) {
      this.senderIsBlocked = isBlocked;
      needCheckReason = true;
    }
    if (this.replySenderId == chatId) {
      this.replySenderIsBlocked = isBlocked;
      needCheckReason = true;
    }
    if (messageContent != null && messageContent.mentionsId.contains(chatId)) {
      needCheckReason = true;
    }
    if (resolvedMentions.contains(chatId)) {
      needCheckReason = true;
    }
    if (needCheckReason) {
      checkCurrentFilterReason();
    }
  }

  @Override
  public void onUsernameResolverUpdate (String username, long chatId) {
    if (messageContent != null && messageContent.mentionsUsername.contains(username)) {
      if (resolvedMentions.add(chatId)) {
        subscribeToSenderId(chatId);
        checkCurrentFilterReason();
      }
    }
  }

  private void checkCurrentFilterReason () {
    if (Settings.instance().getMessagesFilterSetting(Settings.MESSAGES_FILTER_HIDE_BLOCKED_SENDERS)) {
      if (senderIsBlocked) {
        setReason(FilterReason.BLOCKED_SENDER);
        return;
      }
    }

    if (Settings.instance().getMessagesFilterSetting(Settings.MESSAGES_FILTER_HIDE_BLOCKED_SENDERS_MENTIONS)) {
      if (replySenderIsBlocked) {
        setReason(FilterReason.BLOCKED_SENDER_MENTION);
        return;
      }

      if (messageContent != null) {
        for (Long chatId : messageContent.mentionsId) {
          if (tdlib.chatFullyBlocked(chatId)) {
            setReason(FilterReason.BLOCKED_SENDER_MENTION);
            return;
          }
        }
      }

      for (Long chatId : resolvedMentions) {
        if (tdlib.chatFullyBlocked(chatId)) {
          setReason(FilterReason.BLOCKED_SENDER_MENTION);
          return;
        }
      }
    }

    if (messageContent != null) {
      if (Settings.instance().isChatFilterEnabled(chatId, Settings.FILTER_TYPE_LINKS_EXTERNAL)) {
        if (!messageContent.externalLinks.isEmpty()) {
          setReason(FilterReason.CONTAINS_EXTERNAL_LINK);
          return;
        }
      }
      if (Settings.instance().isChatFilterEnabled(chatId, Settings.FILTER_TYPE_LINKS_INTERNAL)) {
        if (!messageContent.internalLinks.isEmpty()) {
          setReason(FilterReason.CONTAINS_INTERNAL_LINK);
          return;
        }
        if (!messageContent.mentionsUsername.isEmpty()) {   // todo: ignore self-mention
          setReason(FilterReason.CONTAINS_INTERNAL_LINK);
          return;
        }
      }
    }

    setReason(FilterReason.NONE);
  }



  private long replySenderId;
  private boolean replySenderIsBlocked;

  public void setReplySenderId (@Nullable TdApi.MessageSender sender) {
    long senderId = Td.getSenderId(sender);
    if (replySenderId == senderId) return;

    if (replySenderId != 0) {
      unsubscribeFromSenderId(replySenderId);
    }

    subscribeToSenderId(senderId);
    this.replySenderId = senderId;
    this.replySenderIsBlocked = tdlib.chatFullyBlocked(senderId);
    checkCurrentFilterReason();
  }










  /* Combine and update content logic */

  private Content messageContent;

  final HashMap<Long, TdApi.MessageContent> rawContents = new HashMap<>();
  private CancellableRunnable checkContentRunnable;

  @UiThread
  public void updateMessageContent (long messageId, TdApi.MessageContent content) {
    rawContents.put(messageId, content);
    checkContent();
  }

  @AnyThread
  public void combineWith (TdApi.Message message) {
    if (!UI.inUiThread()) {
      UI.post(() -> combineWith(message));
      return;
    }


    rawContents.put(message.id, message.content);
    if (checkContentRunnable != null) {
      checkContentRunnable.cancel();
    }

    UI.post(checkContentRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        checkContentRunnable = null;
        checkContent();
      }
    });
  }

  private void checkContent () {
    if (messageContent != null) {
      unsubscribeFromContent(messageContent);
    }

    this.messageContent = new Content();
    for (Map.Entry<Long, TdApi.MessageContent> entry: rawContents.entrySet()) {
      this.messageContent.add(entry.getValue());
    }

    subscribeToContent(messageContent);
    checkCurrentFilterReason();
  }



  /* Subscriptions controls */

  private void subscribeToContent (Content content) {
    for (Long chatId : content.mentionsId) {
      subscribeToSenderId(chatId);
    }

    resolvedMentions.clear();
    for (String username : content.mentionsUsername) {
      subscribeToUsernameResolver(username);
      long chatId = tdlib.messagesFilterProvider().resolveUsername(username);
      if (chatId != 0) {
        if (resolvedMentions.add(chatId)) {
          subscribeToSenderId(chatId);
        }
      }
    }
  }

  private void unsubscribeFromContent (Content content) {
    for (Long chatId : content.mentionsId) {
      unsubscribeFromSenderId(chatId);
    }
    for (String username : content.mentionsUsername) {
      unsubscribeFromUsernameResolver(username);
    }
    for (Long chatId : resolvedMentions) {
      unsubscribeFromSenderId(chatId);
    }
    resolvedMentions.clear();
  }


  private final LongSparseIntArray senderSubscriptions = new LongSparseIntArray();

  private void subscribeToSenderId (long senderId) {
    int count = senderSubscriptions.get(senderId, -1);
    if (count == -1) {
      tdlib.messagesFilterProvider().subscribeToMessageSenderUpdates(senderId, this);
      senderSubscriptions.put(senderId, 1);
    } else {
      senderSubscriptions.put(senderId, count + 1);
    }
  }

  private void unsubscribeFromSenderId (long senderId) {
    int count = senderSubscriptions.get(senderId, -1);
    if (count <= 1) {
      tdlib.messagesFilterProvider().unsubscribeFromMessageSenderUpdates(senderId, this);
      senderSubscriptions.delete(senderId);
    } else {
      senderSubscriptions.put(senderId, count - 1);
    }
  }

  private void unsubscribeFromAllSenderIds () {
    int s = senderSubscriptions.size();
    for (int a = 0; a < s; a++) {
      tdlib.messagesFilterProvider().unsubscribeFromMessageSenderUpdates(senderSubscriptions.keyAt(a), this);
    }
    senderSubscriptions.clear();
  }


  private final HashMap<String, Integer> usernameResolverSubscriptions = new HashMap<>();

  private void subscribeToUsernameResolver (String username) {
    Integer count = usernameResolverSubscriptions.get(username);
    if (count == null) {
      tdlib.messagesFilterProvider().subscribeToUsernameResolverUpdates(username, this);
      usernameResolverSubscriptions.put(username, 1);
    } else {
      usernameResolverSubscriptions.put(username, count + 1);
    }
  }

  private void unsubscribeFromUsernameResolver (String username) {
    Integer count = usernameResolverSubscriptions.get(username);
    if (count == null) return;

    if (count <= 1) {
      tdlib.messagesFilterProvider().unsubscribeFromUsernameResolverUpdates(username, this);
      usernameResolverSubscriptions.remove(username);
    } else {
      usernameResolverSubscriptions.put(username, count - 1);
    }
  }

  private void unsubscribeFromAllFromUsernameResolvers () {
    for (Map.Entry<String, Integer> entry : usernameResolverSubscriptions.entrySet()) {
      tdlib.messagesFilterProvider().unsubscribeFromUsernameResolverUpdates(entry.getKey(), this);
    }
    usernameResolverSubscriptions.clear();
  }



  /* State */

  private @FilterState int state = FilterState.LAST_STATE_VISIBLE;
  private @FilterReason int reason = FilterReason.PENDING;

  private void setReason (@FilterReason int reason) {
    if (this.reason == reason) return;
    this.reason = reason;

    if (reason == FilterReason.NONE) {
      setState(FilterState.VISIBLE);
    } else if (reason == FilterReason.PENDING) {
      if (state == FilterState.VISIBLE) {
        setState(FilterState.LAST_STATE_VISIBLE);
      } else if (state == FilterState.HIDDEN) {
        setState(FilterState.LAST_STATE_HIDDEN);
      }
    } else {
      setState(FilterState.HIDDEN);
    }
  }

  private void setState (@FilterState int state) {
    final boolean oldNeedHideMessage = needHideMessage();

    if (this.state == state) return;
    this.state = state;

    final boolean newNeedHideMessage = needHideMessage();
    if (newNeedHideMessage != oldNeedHideMessage) {
      message.setIsHiddenByMessagesFilter(newNeedHideMessage, true);
    }
  }

  @FilterReason
  public int getCurrentFilterReason () {
    return reason;
  }

  public boolean needHideMessage () {
    return state == FilterState.LAST_STATE_HIDDEN || state == FilterState.HIDDEN;
  }



  /* * */

  @Override
  public void performDestroy () {
    if (messageContent != null) {
      unsubscribeFromContent(messageContent);
    }
    unsubscribeFromAllSenderIds();
    unsubscribeFromAllFromUsernameResolvers();
    tdlib.messagesFilterProvider().unsubscribeFromChatCustomFilterSettingsUpdates(chatId, this);
  }
}
