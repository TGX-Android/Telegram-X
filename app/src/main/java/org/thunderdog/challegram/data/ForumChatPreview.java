package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** A small, immutable projection of the account's forum store, not a second topic cache. */
public final class ForumChatPreview {
  public static final int LIMIT = 4;

  private ForumChatPreview () { }

  public static final class Item {
    public final int id;
    public final String name;
    public final int color;
    public final long customEmojiId;
    public final boolean general;

    private Item (int id, @Nullable TdApi.ForumTopicInfo info) {
      this.id = id;
      this.name = info != null ? info.name : "";
      this.color = info != null && info.icon != null ? info.icon.color : ForumTopicPolicy.ICON_COLORS[0];
      this.customEmojiId = info != null && info.icon != null ? info.icon.customEmojiId : 0;
      this.general = info != null && info.isGeneral;
    }

    @Override public boolean equals (Object obj) {
      if (!(obj instanceof Item)) return false;
      Item other = (Item) obj;
      return id == other.id && color == other.color && customEmojiId == other.customEmojiId &&
        general == other.general && Objects.equals(name, other.name);
    }

    @Override public int hashCode () { return Objects.hash(id, name, color, customEmojiId, general); }
  }

  public static int topicId (long chatId, @Nullable TdApi.Message message) {
    return message != null && message.chatId == chatId && message.topicId instanceof TdApi.MessageTopicForum ?
      ((TdApi.MessageTopicForum) message.topicId).forumTopicId : 0;
  }

  /** Order by actual last-message activity, never by pin rank or the topic creation date. */
  public static final Comparator<TdApi.ForumTopic> RECENT_FIRST =
    Comparator.comparingInt((TdApi.ForumTopic t) -> t.lastMessage != null ? t.lastMessage.date : 0).reversed()
      .thenComparing(Comparator.comparingLong((TdApi.ForumTopic t) -> t.lastMessage != null ? t.lastMessage.id : 0).reversed())
      .thenComparingInt(t -> t.info.forumTopicId);

  public static boolean hasTextDraft (@Nullable TdApi.DraftMessage draft) {
    return draft != null && draft.content instanceof TdApi.DraftMessageContentText &&
      ((TdApi.DraftMessageContentText) draft.content).text != null &&
      !((TdApi.DraftMessageContentText) draft.content).text.text.isEmpty();
  }

  /** Keep drafts in the bounded projection too, without changing the activity order of its message labels. */
  public static final Comparator<TdApi.ForumTopic> CACHE_FIRST =
    Comparator.comparingInt((TdApi.ForumTopic t) -> Math.max(t.lastMessage != null ? t.lastMessage.date : 0,
      hasTextDraft(t.draftMessage) ? t.draftMessage.date : 0)).reversed().thenComparing(RECENT_FIRST);

  public static @Nullable TdApi.ForumTopic latestDraft (long chatId, List<TdApi.ForumTopic> topics) {
    TdApi.ForumTopic newest = null;
    for (TdApi.ForumTopic topic : topics) {
      if (available(chatId, topic) && hasTextDraft(topic.draftMessage) &&
          (newest == null || topic.draftMessage.date > newest.draftMessage.date)) newest = topic;
    }
    return newest;
  }

  private static boolean available (long chatId, @Nullable TdApi.ForumTopic topic) {
    return topic != null && topic.info != null && topic.info.chatId == chatId && topic.info.forumTopicId > 0 &&
      !topic.info.isHidden;
  }

  public static boolean inaccessible (@Nullable TdApi.Error error) {
    if (error == null) return false;
    return error.code == 403 || error.code == 404 || "CHANNEL_PRIVATE".equals(error.message) ||
      "CHAT_ACCESS_DENIED".equals(error.message) || "CHAT_NOT_FOUND".equals(error.message) ||
      "TOPIC_DELETED".equals(error.message);
  }

  public static List<Item> select (long chatId, @Nullable TdApi.Message message, List<TdApi.ForumTopic> topics,
                                  @Nullable TdApi.ForumTopic leadingTopic, boolean leadingUnavailable) {
    return select(chatId, topicId(chatId, message), topics, leadingTopic, leadingUnavailable);
  }

  public static List<Item> select (long chatId, int firstId, List<TdApi.ForumTopic> topics,
                                  @Nullable TdApi.ForumTopic leadingTopic, boolean leadingUnavailable) {
    LinkedHashMap<Integer, TdApi.ForumTopic> candidates = new LinkedHashMap<>();
    for (TdApi.ForumTopic topic : topics) {
      if (available(chatId, topic)) candidates.put(topic.info.forumTopicId, topic);
    }
    if (available(chatId, leadingTopic) && leadingTopic.info.forumTopicId == firstId) candidates.put(firstId, leadingTopic);
    boolean hiddenLeading = leadingTopic != null && leadingTopic.info != null && leadingTopic.info.forumTopicId == firstId && leadingTopic.info.isHidden;
    for (TdApi.ForumTopic topic : topics) {
      if (topic.info != null && topic.info.chatId == chatId && topic.info.forumTopicId == firstId && topic.info.isHidden) hiddenLeading = true;
    }
    ArrayList<Item> result = new ArrayList<>(LIMIT);
    if (firstId > 0 && !leadingUnavailable && !hiddenLeading) {
      TdApi.ForumTopic first = candidates.remove(firstId);
      // Reserve the correct leading topic while its metadata loads; never label the message with another topic.
      result.add(new Item(firstId, first != null ? first.info : null));
    } else {
      candidates.remove(firstId);
    }
    ArrayList<TdApi.ForumTopic> recent = new ArrayList<>(candidates.values());
    recent.sort(RECENT_FIRST);
    for (TdApi.ForumTopic topic : recent) {
      if (result.size() == LIMIT) break;
      if (topic.lastMessage != null && topic.lastMessage.schedulingState == null &&
          topicId(chatId, topic.lastMessage) == topic.info.forumTopicId) result.add(new Item(topic.info.forumTopicId, topic.info));
    }
    return Collections.unmodifiableList(result);
  }
}
