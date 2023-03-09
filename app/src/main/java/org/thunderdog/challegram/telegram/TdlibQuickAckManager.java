/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 28/01/2017
 */
package org.thunderdog.challegram.telegram;

import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.TdApi;

import me.vkryl.core.collection.LongSparseIntArray;

public class TdlibQuickAckManager {
  private final Tdlib tdlib;
  private final LongSparseArray<LongSparseIntArray> sentMessageIds;

  /*package*/ TdlibQuickAckManager (Tdlib tdlib) {
    this.tdlib = tdlib;
    this.sentMessageIds = new LongSparseArray<>();
  }

  public boolean isMessageAcknowledged (long chatId, long messageId) {
    return getMessageAcknowledgeCount(chatId, messageId) > 0;
  }

  public int getMessageAcknowledgeCount (long chatId, long messageId) {
    synchronized (this) {
      LongSparseIntArray messageIds = sentMessageIds.get(chatId);
      return messageIds != null ? messageIds.get(messageId, 0) : 0;
    }
  }

  public void onMessageSendAcknowledged (TdApi.UpdateMessageSendAcknowledged update) {
    boolean notify;
    synchronized (this) {
      LongSparseIntArray messageIds = sentMessageIds.get(update.chatId);
      if (messageIds != null) {
        final int acknowledgeCount = messageIds.get(update.messageId);
        messageIds.put(update.messageId, acknowledgeCount + 1);
        notify = acknowledgeCount == 0;
      } else {
        messageIds = new LongSparseIntArray(4);
        messageIds.put(update.messageId, 1);
        sentMessageIds.put(update.chatId, messageIds);
        notify = true;
      }
    }
    if (notify) {
      tdlib.onMessageSendAcknowledged(update);
    }
  }

  public void onMessageSendSucceeded (final long chatId, final long messageId) {
    removeSentMessage(chatId, messageId);
  }

  public void onMessageSendFailed (final long chatId, final long messageId) {
    removeSentMessage(chatId, messageId);
  }

  public void removeSentMessage (final long chatId, final long messageId) {
    tdlib.ui().postDelayed(() -> {
      synchronized (TdlibQuickAckManager.this) {
        LongSparseIntArray messageIds = sentMessageIds.get(chatId);
        if (messageIds != null) {
          messageIds.delete(messageId);
          if (messageIds.size() == 0) {
            sentMessageIds.remove(chatId);
          }
        }
      }
    }, 200);
  }
}
