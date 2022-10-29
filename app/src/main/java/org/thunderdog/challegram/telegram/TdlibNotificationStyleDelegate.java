/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 04/01/2019
 */
package org.thunderdog.challegram.telegram;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;

public interface TdlibNotificationStyleDelegate {
  void displayNotificationGroup (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group, @Nullable TdlibNotificationSettings settings);
  void rebuildNotificationsSilently (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, TdApi.NotificationSettingsScope scope, long specificChatId, int specificGroupId);
  void hideNotificationGroup (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group);
  void hideAllNotifications (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount);
  void cancelPendingMediaPreviewDownloads (@NonNull Context context, @NonNull TdlibNotificationHelper helper);
}
