package org.thunderdog.challegram.telegram;

public interface MessageEditListener {
  default void onMessagePendingContentChanged (long chatId, long messageId) { }
}
