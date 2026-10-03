package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

/** Account/chat-scoped forum identities, never adapter positions or generic message threads. */
public final class ForumTopicSelection {
  public static final int MAX_SELECTED = 100;
  public enum Action {
    PIN, UNPIN, MUTE, UNMUTE, CLOSE, OPEN, DELETE, CLEAR_GENERAL, READ_MENTIONS, READ_REACTIONS, READ_POLL_VOTES;
    public boolean isDestructive () { return this == DELETE || this == CLEAR_GENERAL; }
  }

  public final int accountId;
  public final long chatId;
  private final LinkedHashMap<Integer, TdApi.ForumTopic> selected = new LinkedHashMap<>();

  public ForumTopicSelection (int accountId, long chatId) { this.accountId = accountId; this.chatId = chatId; }
  public int size () { return selected.size(); }
  public boolean isEmpty () { return selected.isEmpty(); }
  public boolean contains (int id) { return selected.containsKey(id); }
  public void clear () { selected.clear(); }
  public void remove (int id) { selected.remove(id); }
  public List<TdApi.ForumTopic> topics () { return new ArrayList<>(selected.values()); }
  public int[] ids () {
    int[] result = new int[size()]; int i = 0;
    for (int id : selected.keySet()) result[i++] = id;
    return result;
  }
  public boolean add (TdApi.ForumTopic topic) {
    if (topic == null || topic.info == null || topic.info.chatId != chatId || topic.info.forumTopicId <= 0 ||
        !contains(topic.info.forumTopicId) && size() >= MAX_SELECTED) return false;
    selected.put(topic.info.forumTopicId, topic);
    return true;
  }
  public void update (Collection<TdApi.ForumTopic> topics) {
    for (TdApi.ForumTopic topic : topics) if (contains(topic.info.forumTopicId)) add(topic);
    // A missing row on a partial/refresh page is NOT evidence of deletion.
  }

  /** Reserve X/count and one overflow target; each direct action has a 48dp touch area. */
  public static int primaryActionSlots (float paneWidthDp, float fontScale) {
    float reserved = 112 + 32 * Math.max(1f, fontScale);
    return Math.max(0, Math.min(3, (int) ((paneWidthDp - reserved) / 48) - 1));
  }

  public static boolean allowed (Action action, TdApi.ForumTopic t, TdApi.ChatMemberStatus status) {
    if (t == null || t.info == null || !ForumTopicPolicy.isMember(status)) return false;
    switch (action) {
      case PIN: case UNPIN: return ForumTopicPolicy.canManage(status);
      case CLOSE: case OPEN: return ForumTopicPolicy.canEdit(status, t.info);
      case DELETE: return !t.info.isGeneral && ForumTopicPolicy.canOfferDelete(status, t.info);
      case CLEAR_GENERAL: return t.info.isGeneral && ForumTopicPolicy.canDeleteAny(status);
      case MUTE: case UNMUTE: return t.notificationSettings != null;
      case READ_MENTIONS: case READ_REACTIONS: case READ_POLL_VOTES: return true;
      default: return false;
    }
  }

  public static boolean applicable (Action action, TdApi.ForumTopic t, boolean groupMuted) {
    switch (action) {
      case PIN: return !t.isPinned;
      case UNPIN: return t.isPinned;
      case CLOSE: return !t.info.isClosed;
      case OPEN: return t.info.isClosed;
      case MUTE: return !effectiveMute(t, groupMuted);
      case UNMUTE: return effectiveMute(t, groupMuted);
      case READ_MENTIONS: return t.unreadMentionCount > 0;
      case READ_REACTIONS: return t.unreadReactionCount > 0;
      case READ_POLL_VOTES: return t.unreadPollVoteCount > 0;
      default: return true;
    }
  }

  public static boolean effectiveMute (TdApi.ForumTopic topic, boolean groupMuted) {
    return topic.notificationSettings == null || topic.notificationSettings.useDefaultMuteFor ? groupMuted : topic.notificationSettings.muteFor > 0;
  }

  /** Unknown/incomplete pins suppress Pin, never estimate capacity from a search or partial list. */
  public static EnumSet<Action> intersection (List<TdApi.ForumTopic> topics, TdApi.ChatMemberStatus status,
                                            boolean groupMuted, boolean completePins, int pinnedCount, int pinLimit) {
    EnumSet<Action> result = EnumSet.noneOf(Action.class);
    if (topics.isEmpty() || topics.size() > MAX_SELECTED) return result;
    long chatId = topics.get(0).info.chatId;
    for (Action action : Action.values()) {
      boolean common = action != Action.CLEAR_GENERAL || topics.size() == 1;
      if (action == Action.PIN && (!completePins || pinLimit < 0 || (long) pinnedCount + topics.size() > pinLimit)) common = false;
      for (TdApi.ForumTopic topic : topics) {
        if (topic.info.chatId != chatId || !allowed(action, topic, status) || !applicable(action, topic, groupMuted)) { common = false; break; }
      }
      if (common) result.add(action);
    }
    return result;
  }
}
