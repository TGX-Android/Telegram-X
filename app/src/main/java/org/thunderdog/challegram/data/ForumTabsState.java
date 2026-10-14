package org.thunderdog.challegram.data;

import androidx.annotation.Nullable;

/** Immutable, platform-independent UI state for one account's forum chat. */
public final class ForumTabsState {
  public static final int TOP = 0;
  public static final int START = 1;
  public static final int BOTTOM = 2;

  private static final int VERSION = 1;
  private static final int UNSET_TOPIC = -1;
  private static final ForumTabsState DEFAULT = new ForumTabsState(TOP, BOTTOM, null);

  public final int placement;
  /** The horizontal placement to use on the next transition out of START. */
  public final int nextHorizontal;
  /** Null is unset, zero is All, and a positive value is a TDLib forumTopicId. */
  public final @Nullable Integer selectedTopicId;

  private ForumTabsState (int placement, int nextHorizontal, @Nullable Integer selectedTopicId) {
    this.placement = placement;
    this.nextHorizontal = nextHorizontal;
    this.selectedTopicId = selectedTopicId;
  }

  public static ForumTabsState defaultState () {
    return DEFAULT;
  }

  /** TOP -> START -> BOTTOM -> START -> TOP, retaining the selected topic. */
  public ForumTabsState cyclePlacement () {
    if (placement == START) {
      return new ForumTabsState(nextHorizontal, nextHorizontal == TOP ? BOTTOM : TOP, selectedTopicId);
    }
    return new ForumTabsState(START, nextHorizontal, selectedTopicId);
  }

  /** Accepts All (zero) or an already resolved, positive TDLib forumTopicId. */
  public ForumTabsState withSelectedTopic (int topicId) {
    if (topicId < 0) {
      throw new IllegalArgumentException("Expected All or a positive forum topic ID");
    }
    return new ForumTabsState(placement, nextHorizontal, topicId);
  }

  public ForumTabsState withoutSelection () {
    return new ForumTabsState(placement, nextHorizontal, null);
  }

  /** Version 1: [version, placement, next horizontal placement, topic ID or -1]. */
  public int[] encode () {
    return new int[] {VERSION, placement, nextHorizontal, selectedTopicId != null ? selectedTopicId : UNSET_TOPIC};
  }

  /** Missing, unknown-version, truncated or inconsistent records fall back to the default. */
  public static ForumTabsState decode (@Nullable int[] encoded) {
    if (encoded == null || encoded.length != 4 || encoded[0] != VERSION) {
      return defaultState();
    }
    int placement = encoded[1];
    int nextHorizontal = encoded[2];
    int topicId = encoded[3];
    if ((placement != TOP && placement != START && placement != BOTTOM) ||
        (nextHorizontal != TOP && nextHorizontal != BOTTOM) ||
        (placement == TOP && nextHorizontal != BOTTOM) ||
        (placement == BOTTOM && nextHorizontal != TOP) || topicId < UNSET_TOPIC) {
      return defaultState();
    }
    return new ForumTabsState(placement, nextHorizontal, topicId == UNSET_TOPIC ? null : topicId);
  }
}
