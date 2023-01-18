package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface ForumTopicInfoListener {
  default void onForumTopicInfoChanged (long chatId, TdApi.ForumTopicInfo info) { }
}
