package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2019-12-13
 * Author: default
 */
public interface CounterChangeListener {
  default void onChatCounterChanged (@NonNull TdApi.ChatList chatList, boolean availabilityChanged, int totalCount, int unreadCount, int unreadUnmutedCount) { }
  default void onMessageCounterChanged (@NonNull TdApi.ChatList chatList, int unreadCount, int unreadUnmutedCount) { }
}
