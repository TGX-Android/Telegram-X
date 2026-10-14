package org.thunderdog.challegram.component.chat;

import androidx.recyclerview.widget.DiffUtil;

import org.drinkless.tdlib.TdApi;

import java.util.List;

/** Store rows are immutable snapshots. A stale/loading change must not rebind every topic. */
public final class ForumTopicListDiff extends DiffUtil.Callback {
  public static final Object SELECTION_PAYLOAD = new Object();
  // Rebind the existing holder: a content refresh must not cross-fade an identical row.
  public static final Object CONTENT_PAYLOAD = new Object();
  private final List<TdApi.ForumTopic> before, after;

  public ForumTopicListDiff (List<TdApi.ForumTopic> before, List<TdApi.ForumTopic> after) {
    this.before = before;
    this.after = after;
  }

  @Override public int getOldListSize () { return before.size() + 1; }
  @Override public int getNewListSize () { return after.size() + 1; }

  @Override public boolean areItemsTheSame (int oldPosition, int newPosition) {
    if (oldPosition == before.size() || newPosition == after.size()) {
      return oldPosition == before.size() && newPosition == after.size();
    }
    TdApi.ForumTopicInfo old = before.get(oldPosition).info, next = after.get(newPosition).info;
    return old.chatId == next.chatId && old.forumTopicId == next.forumTopicId;
  }

  @Override public boolean areContentsTheSame (int oldPosition, int newPosition) {
    // The footer reflects loading/error/staleness stored by the controller, not a topic row.
    return oldPosition < before.size() && newPosition < after.size() && before.get(oldPosition) == after.get(newPosition);
  }

  @Override public Object getChangePayload (int oldPosition, int newPosition) {
    return CONTENT_PAYLOAD;
  }
}
