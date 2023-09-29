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

import org.drinkless.tdlib.TdApi;

public interface NotificationSettingsListener {
  default void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) { }
  default void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) { }
  default void onNotificationChannelChanged (TdApi.NotificationSettingsScope scope) { }
  default void onNotificationChannelChanged (long chatId) { }
  default void onNotificationGlobalSettingsChanged ()                                    { }
  default void onArchiveChatListSettingsChanged (TdApi.ArchiveChatListSettings settings) { }
}
