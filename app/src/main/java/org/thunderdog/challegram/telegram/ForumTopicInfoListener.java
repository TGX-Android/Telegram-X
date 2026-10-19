package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

public interface ForumTopicInfoListener {
  default void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) { }
  // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
  /** The complete update, including counters and nullable draft; do not mutate it. */
  default void onForumTopicUpdated (TdApi.UpdateForumTopic update) { }
  // endregion
}
