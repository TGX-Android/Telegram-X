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
 * File created on 05/01/2019
 */
package org.thunderdog.challegram.telegram;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.UI;

import java.util.concurrent.TimeUnit;

import me.vkryl.td.ChatId;

public class TdlibNotificationExtras {
  private static long[] getLongOrIntArray (Bundle bundle, String key) {
    try {
      Object data = bundle.get(key);
      if (data instanceof long[]) {
        return (long[]) data;
      } else if (data instanceof int[]) {
        int[] array = (int[]) data;
        long[] result = new long[array.length];
        for (int i = 0; i < array.length; i++) {
          result[i] = array[i];
        }
        return result;
      }
    } catch (Throwable ignored) { }
    return null;
  }

  private static long[] getLongArray (Bundle bundle, String key) {
    try {
      return bundle.getLongArray(key);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static int[] getIntArray (Bundle bundle, String key) {
    try {
      return bundle.getIntArray(key);
    } catch (Throwable ignored) {
      return null;
    }
  }

  public TdlibNotificationExtras (int accountId, int category) {
    this.accountId = accountId;
    this.category = category;
    this.chatId = 0;
    this.messageThreadId = 0;
    this.maxNotificationId = 0;
    this.notificationGroupId = 0;
    this.needReply = false;
    this.areMentions = false;
    this.messageIds = null;
    this.userIds = null;
  }

  public static @Nullable TdlibNotificationExtras parseCategory (Bundle bundle) {
    if (bundle == null)
      return null;
    int accountId = bundle.getInt("account_id", TdlibAccount.NO_ID);
    int category = bundle.getInt("category", -1);
    if (accountId == TdlibAccount.NO_ID || category == -1) {
      Log.w("Incomplete notification extras: %s", bundle);
      return null;
    }
    return new TdlibNotificationExtras(accountId, category);
  }

  public static @Nullable TdlibNotificationExtras parse (Bundle bundle) {
    if (bundle == null)
      return null;
    int accountId = bundle.getInt("account_id", TdlibAccount.NO_ID);
    int category = bundle.getInt("category", -1);
    long chatId = bundle.getLong("chat_id");
    long messageThreadId = bundle.getLong("message_thread_id");
    int maxNotificationId = bundle.getInt("max_notification_id");
    int notificationGroupId = bundle.getInt("notification_group_id");
    boolean needReply = bundle.getBoolean("need_reply");
    boolean mentions = bundle.getBoolean("mentions");
    long[] messageIds = getLongArray(bundle, "message_ids");
    long[] userIds = getLongOrIntArray(bundle, "user_ids");
    if (accountId == TdlibAccount.NO_ID || category == -1 || chatId == 0 || maxNotificationId == 0 || notificationGroupId == 0) {
      Log.w("Incomplete notification extras: %s", bundle);
      return null;
    }
    return new TdlibNotificationExtras(accountId, category, chatId, messageThreadId, maxNotificationId, notificationGroupId, needReply, mentions, messageIds, userIds);
  }

  public final int accountId;
  public final int category;
  public final long chatId;
  public final long messageThreadId;
  public final int maxNotificationId;
  public final int notificationGroupId;
  public final boolean needReply;
  public final boolean areMentions;
  public final long[] messageIds;
  public final long[] userIds;

  private TdlibNotificationExtras (int accountId, int category, long chatId, long messageThreadId, int maxNotificationId, int notificationGroupId, boolean needReply, boolean areMentions, long[] messageIds, long[] userIds) {
    this.accountId = accountId;
    this.category = category;
    this.chatId = chatId;
    this.messageThreadId = messageThreadId;
    this.maxNotificationId = maxNotificationId;
    this.notificationGroupId = notificationGroupId;
    this.needReply = needReply;
    this.areMentions = areMentions;
    this.messageIds = messageIds != null && messageIds.length > 0 ? messageIds : null;
    this.userIds = userIds != null && userIds.length > 0 ? userIds : null;
  }

  public static void put (Intent intent, Tdlib tdlib, TdlibNotificationGroup group, boolean needReply, long[] messageIds, long[] userIds) {
    final TdlibNotification lastNotification = group.lastNotification();
    intent.putExtra("account_id", tdlib.id());
    intent.putExtra("category", group.getCategory());
    intent.putExtra("chat_id", group.getChatId());
    intent.putExtra("message_thread_id", group.getMessageThreadId());
    intent.putExtra("max_notification_id", lastNotification.getId());
    intent.putExtra("notification_group_id", group.getId());
    intent.putExtra("need_reply", needReply);
    intent.putExtra("mentions", group.isMention());
    intent.putExtra("message_ids", messageIds);
    intent.putExtra("user_ids", userIds);
  }

  public void mute (Tdlib tdlib) {
    boolean needToast = tdlib.notifications().isUnknownGroup(notificationGroupId);
    String text = null;
    int muteFor = (int) TimeUnit.HOURS.toSeconds(1);
    if (areMentions) {
      if (userIds != null) {
        if (needToast) {
          if (userIds.length == 1) {
            text = Lang.getString(R.string.NotificationMutedPerson, tdlib.cache().userName(userIds[0]));
          } else {
            text = Lang.plural(R.string.NotificationMutedPersons, userIds.length);
          }
        }
        for (long userId : userIds) {
          tdlib.setMuteForSync(userId, muteFor);
        }
      }
    } else {
      tdlib.setMuteForSync(chatId, muteFor);
      text = needToast ? Lang.getString(ChatId.isUserChat(chatId) ? R.string.NotificationMutedPerson : R.string.NotificationMutedChat, tdlib.chatTitle(chatId)) : null;
    }
    hide(tdlib);
    if (needToast) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  public void read (Tdlib tdlib) {
    boolean needToast = tdlib.notifications().isUnknownGroup(notificationGroupId);
    if (areMentions) {
      tdlib.client().send(new TdApi.ReadAllChatMentions(chatId), tdlib.silentHandler());
    } else {
      tdlib.readMessages(chatId, messageIds, new TdApi.MessageSourceNotification());
    }
    hide(tdlib);
    if (needToast) {
      UI.showToast(areMentions ? R.string.NotificationReadMentions : R.string.NotificationRead, Toast.LENGTH_SHORT);
    }
  }

  public void hide (Tdlib tdlib) {
    tdlib.notifications().removeNotificationGroup(this);
    // Log.v(Log.TAG_ACCOUNTS, "Removing notification group %d %d", notificationGroupId, maxNotificationId);
    // tdlib.client().send(new TdApi.RemoveNotificationGroup(notificationGroupId, maxNotificationId), tdlib.silentHandler());
  }
}
