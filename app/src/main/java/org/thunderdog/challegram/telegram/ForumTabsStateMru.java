package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import java.util.Arrays;

/** Pure bounded index for forum-tabs state; most recently used chat comes first. */
final class ForumTabsStateMru {
  static final int MAX_CHATS = 64;

  private ForumTabsStateMru () { }

  /** Never mutates the input; repairs duplicates, zero IDs and oversized indexes. */
  static long[] touch (@Nullable long[] chatIds, long chatId) {
    if (chatId == 0) {
      throw new IllegalArgumentException("Expected a nonzero chat ID");
    }
    long[] result = new long[MAX_CHATS];
    result[0] = chatId;
    int size = 1;
    if (chatIds != null) {
      for (long previousChatId : chatIds) {
        if (previousChatId != 0 && !contains(result, previousChatId)) {
          result[size++] = previousChatId;
          if (size == MAX_CHATS) {
            break;
          }
        }
      }
    }
    return Arrays.copyOf(result, size);
  }

  static boolean contains (long[] chatIds, long chatId) {
    for (long candidate : chatIds) {
      if (candidate == chatId) {
        return true;
      }
    }
    return false;
  }
}
