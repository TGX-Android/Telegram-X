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
 * File created on 05/01/2019
 */
package org.thunderdog.challegram.telegram;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
// region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
import org.thunderdog.challegram.data.ForumNotificationReadScope;
// endregion
import org.thunderdog.challegram.tool.UI;

// region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
import java.util.List;
// endregion
import java.util.concurrent.TimeUnit;
// region @MadeByLLM("GPT-5 (Codex; exact runtime version unavailable)", id = "forum-topics", ref = "PR #1095")
import java.util.concurrent.atomic.AtomicInteger;
// endregion

import tgx.td.ChatId;
import tgx.td.Td;

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
    this.topicId = null;
    this.maxNotificationId = 0;
    this.notificationGroupId = 0;
    this.needReply = false;
    this.areMentions = false;
    this.messageIds = null;
    this.userIds = null;
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
    this.readForumTopicIds = null;
    // endregion
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
    TdApi.MessageTopic topicId = Td.restoreMessageTopic(bundle, "topic_id");
    int maxNotificationId = bundle.getInt("max_notification_id");
    int notificationGroupId = bundle.getInt("notification_group_id");
    boolean needReply = bundle.getBoolean("need_reply");
    boolean mentions = bundle.getBoolean("mentions");
    long[] messageIds = getLongArray(bundle, "message_ids");
    long[] userIds = getLongOrIntArray(bundle, "user_ids");
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
    int[] readForumTopicIds = getIntArray(bundle, "read_forum_topic_ids");
    // endregion
    if (accountId == TdlibAccount.NO_ID || category == -1 || chatId == 0 || maxNotificationId == 0 || notificationGroupId == 0) {
      Log.w("Incomplete notification extras: %s", bundle);
      return null;
    }
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
    return new TdlibNotificationExtras(accountId, category, chatId, topicId, maxNotificationId, notificationGroupId, needReply, mentions, messageIds, userIds, readForumTopicIds);
    // endregion
  }

  public final int accountId;
  public final int category;
  public final long chatId;
  public final TdApi.MessageTopic topicId;
  public final int maxNotificationId;
  public final int notificationGroupId;
  public final boolean needReply;
  public final boolean forceExternalReply = false; // Keep this flag in case it will be needed in future
  public final boolean areMentions;
  public final long[] messageIds;
  public final long[] userIds;
  // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
  // Null on legacy/unknown scopes; never substitute the navigation/reply topicId.
  private final int[] readForumTopicIds;
  // endregion

  // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
  private TdlibNotificationExtras (int accountId, int category, long chatId, TdApi.MessageTopic topicId, int maxNotificationId, int notificationGroupId, boolean needReply, boolean areMentions, long[] messageIds, long[] userIds, int[] readForumTopicIds) {
  // endregion
    this.accountId = accountId;
    this.category = category;
    this.chatId = chatId;
    this.topicId = topicId;
    this.maxNotificationId = maxNotificationId;
    this.notificationGroupId = notificationGroupId;
    this.needReply = needReply;
    this.areMentions = areMentions;
    this.messageIds = messageIds != null && messageIds.length > 0 ? messageIds : null;
    this.userIds = userIds != null && userIds.length > 0 ? userIds : null;
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
    this.readForumTopicIds = readForumTopicIds;
    // endregion
  }

  public static void put (Intent intent, Tdlib tdlib, TdlibNotificationGroup group, boolean needReply, long[] messageIds, long[] userIds) {
    final TdlibNotification lastNotification = group.lastNotification();
    intent.putExtra("account_id", tdlib.id());
    intent.putExtra("category", group.getCategory());
    intent.putExtra("chat_id", group.getChatId());
    Td.put(intent, "topic_id", group.getMessageTopicId());
    intent.putExtra("max_notification_id", lastNotification.getId());
    intent.putExtra("notification_group_id", group.getId());
    intent.putExtra("need_reply", needReply);
    intent.putExtra("mentions", group.isMention());
    intent.putExtra("message_ids", messageIds);
    intent.putExtra("user_ids", userIds);
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
    intent.putExtra("read_forum_topic_ids", group.isMention() ? group.getMentionReadForumTopicIds() : null);
    // endregion
  }

  public void setMuteFor (Tdlib tdlib, int muteForSeconds) {
    boolean needToast = tdlib.notifications().isUnknownGroup(notificationGroupId);
    String text = null;
    if (areMentions) {
      if (userIds != null) {
        if (needToast) {
          if (userIds.length == 1) {
            @StringRes int stringRes = muteForSeconds == 0 ? R.string.NotificationUnmutedPerson : R.string.NotificationMutedPerson;
            text = Lang.getString(stringRes, tdlib.cache().userName(userIds[0]));
          } else {
            @StringRes int stringRes = muteForSeconds == 0 ? R.string.NotificationUnmutedPeople : R.string.NotificationMutedPersons;
            text = Lang.plural(stringRes, userIds.length);
          }
        }
        for (long userId : userIds) {
          tdlib.setMuteForSync(userId, muteForSeconds);
        }
      }
    } else {
      tdlib.setMuteForSync(chatId, muteForSeconds);
      @StringRes int stringRes;
      if (muteForSeconds == 0) {
        stringRes = ChatId.isUserChat(chatId) ? R.string.NotificationUnmutedPerson : R.string.NotificationUnmutedChat;
      } else {
        stringRes = ChatId.isUserChat(chatId) ? R.string.NotificationMutedPerson : R.string.NotificationMutedChat;
      }
      text = needToast ? Lang.getString(stringRes, tdlib.chatTitle(chatId)) : null;
    }
    hide(tdlib);
    if (needToast) {
      UI.showToast(text, Toast.LENGTH_SHORT);
    }
  }

  public void unmute (Tdlib tdlib) {
    setMuteFor(tdlib, 0);
  }

  public void mute (Tdlib tdlib) {
    int muteFor = (int) TimeUnit.HOURS.toSeconds(1);
    setMuteFor(tdlib, muteFor);
  }

  // region @MadeByLLM("GPT-5 (Codex; exact runtime version unavailable)", id = "forum-topics", ref = "PR #1095")
  public void read (Tdlib tdlib, @Nullable Runnable after) {
  // endregion
    boolean needToast = tdlib.notifications().isUnknownGroup(notificationGroupId);
    if (areMentions) {
      // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
      List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(chatId, readForumTopicIds);
      ForumNotificationReadScope.Completion completion = new ForumNotificationReadScope.Completion(requests.size());
      // region @MadeByLLM("GPT-5 (Codex; exact runtime version unavailable)", id = "forum-topics", ref = "PR #1095")
      AtomicInteger remaining = new AtomicInteger(requests.size());
      // endregion
      for (TdApi.Function<TdApi.Ok> request : requests) {
        tdlib.client().send(request, result -> {
          boolean success = result.getConstructor() == TdApi.Ok.CONSTRUCTOR;
          if (completion.onResult(success)) {
            hide(tdlib);
            if (needToast) {
              UI.showToast(R.string.NotificationReadMentions, Toast.LENGTH_SHORT);
            }
          } else if (!success) {
            UI.showError(result);
          }
          // region @MadeByLLM("GPT-5 (Codex; exact runtime version unavailable)", id = "forum-topics", ref = "PR #1095")
          if (remaining.decrementAndGet() == 0 && after != null) {
            after.run();
          }
          // endregion
        });
      }
      return;
      // endregion
    }
    // region @MadeByLLM("GPT-5 (Codex; exact runtime version unavailable)", id = "forum-topics", ref = "PR #1095")
    tdlib.readNotificationMessages(chatId, messageIds, (ok, error) -> {
      if (error != null) {
        UI.showError(error);
      } else {
        hide(tdlib);
        if (needToast) {
          UI.showToast(R.string.NotificationRead, Toast.LENGTH_SHORT);
        }
      }
      if (after != null) {
        after.run();
      }
    });
    // endregion
  }

  public void hide (Tdlib tdlib) {
    tdlib.notifications().removeNotificationGroup(this);
    // Log.v(Log.TAG_ACCOUNTS, "Removing notification group %d %d", notificationGroupId, maxNotificationId);
    // tdlib.client().send(new TdApi.RemoveNotificationGroup(notificationGroupId, maxNotificationId), tdlib.silentHandler());
  }
}
