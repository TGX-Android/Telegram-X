package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/15/18
 * Author: default
 */
public interface NotificationSettingsListener {
  default void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) { }
  default void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) { }
  default void onNotificationChannelChanged (TdApi.NotificationSettingsScope scope) { }
  default void onNotificationChannelChanged (long chatId) { }
  default void onNotificationGlobalSettingsChanged () { }
}
