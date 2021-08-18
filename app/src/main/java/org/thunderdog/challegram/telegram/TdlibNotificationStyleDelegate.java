package org.thunderdog.challegram.telegram;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 04/01/2019
 * Author: default
 */
public interface TdlibNotificationStyleDelegate {
  void displayNotificationGroup (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group, @Nullable TdlibNotificationSettings settings);
  void rebuildNotificationsSilently (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, TdApi.NotificationSettingsScope scope, long specificChatId, int specificGroupId);
  void hideNotificationGroup (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount, boolean allowPreview, @NonNull TdlibNotificationGroup group);
  void hideAllNotifications (@NonNull Context context, @NonNull TdlibNotificationHelper helper, int badgeCount);
}
