package org.thunderdog.challegram.telegram;

import androidx.collection.LongSparseArray;

import org.drinkless.td.libcore.telegram.TdApi;

import me.vkryl.core.collection.LongSparseIntArray;

/**
 * Date: 28/01/2017
 * Author: default
 */

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
