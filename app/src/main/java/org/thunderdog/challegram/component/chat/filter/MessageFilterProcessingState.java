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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class MessageFilterProcessingState implements Destroyable, MessagesFilterProvider.SenderUpdateListener {
  private final Tdlib tdlib;
  private final TGMessage message;

  private final long senderId;

  public MessageFilterProcessingState (TGMessage tgMessage, TdApi.Message message) {
    this.tdlib = tgMessage.tdlib();
    this.message = tgMessage;

    this.senderId = Td.getSenderId(message.senderId);
    processMessage(message);
    checkCurrentFilterReason();
    tdlib.messagesFilterProvider().subscribeToMessageSenderUpdates(senderId, this);
  }

  public void onUpdateMessageContent (TdApi.MessageContent content) {
    processMessageContent(content);
    checkCurrentFilterReason();
  }

  @Override
  public void onMessageSenderBlockedUpdate (long chatId, boolean isBlocked) {
    if (this.senderId != chatId) return;

    this.senderIsBlocked = isBlocked;
    checkCurrentFilterReason();
  }

  private boolean senderIsBlocked;

  private void processMessage (TdApi.Message message) {
    this.senderIsBlocked = tdlib.chatFullyBlocked(senderId);
    processMessageContent(message.content);
  }

  private Set<String> mentions = new HashSet<>();

  private void processMessageContent (@Nullable TdApi.MessageContent content) {
    final TdApi.FormattedText textOrCaption = Td.textOrCaption(content);

    final Set<String> newMentions = new HashSet<>();

    if (textOrCaption != null && textOrCaption.text != null) {
      if (textOrCaption.entities != null) {
        for (TdApi.TextEntity entity : textOrCaption.entities) {
          if (entity.type.getConstructor() == TdApi.TextEntityTypeMention.CONSTRUCTOR) {
            newMentions.add(Td.substring(textOrCaption.text, entity));
          }
        }
      }
    }

    this.mentions = newMentions;    // SearchPublicChat
  }

  private void checkCurrentFilterReason () {
    if (senderIsBlocked) {
      setReason(FilterReason.BLOCKED_SENDER);
      return;
    }

    setReason(FilterReason.NONE);
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

  public boolean needHideMessage () {
    return state == FilterState.LAST_STATE_HIDDEN || state == FilterState.HIDDEN;
  }


  /* * */

  @Override
  public void performDestroy () {
    tdlib.messagesFilterProvider().unsubscribeFromChatUpdates(senderId, this);
  }
}
