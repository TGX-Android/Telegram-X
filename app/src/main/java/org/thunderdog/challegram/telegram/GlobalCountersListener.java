package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 3/4/18
 * Author: default
 */

public interface GlobalCountersListener {
  @UiThread
  default void onUnreadCountersChanged (Tdlib tdlib, @NonNull TdApi.ChatList chatList, int count, boolean isMuted) { }
  @UiThread
  void onTotalUnreadCounterChanged (@NonNull TdApi.ChatList chatList, boolean isReset);
}
