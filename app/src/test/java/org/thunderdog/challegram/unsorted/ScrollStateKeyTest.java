package org.thunderdog.challegram.unsorted;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ScrollStateKeyTest {
  private static final String[] FIELDS = {"_message", "_chat", "_aliases", "_stack", "_offset", "_read", "_top"};
  private static final TdApi.MessageTopic[] TOPICS = {
    null, new TdApi.MessageTopicThread(17), new TdApi.MessageTopicForum(17),
    new TdApi.MessageTopicDirectMessages(17), new TdApi.MessageTopicSavedMessages(17),
    new TdApi.MessageTopicSavedMessages(-17), new TdApi.MessageTopicForum(18)
  };

  @Test
  public void existingKeyFormatIsPreserved () {
    assertEquals("scroll_chat100_message", ScrollStateKey.key("_message", 0, 100, null));
    assertEquals("2_scroll_chat-100_offset_forum17", ScrollStateKey.key("_offset", 2, -100, new TdApi.MessageTopicForum(17)));
    assertEquals("2_scroll_chat100_top_thread17", ScrollStateKey.key("_top", 2, 100, new TdApi.MessageTopicThread(17)));
  }

  @Test
  public void accountsChatsTopicsAndFieldsHaveDistinctKeys () {
    Set<String> keys = new HashSet<>();
    for (int accountId : new int[] {0, 2}) {
      for (long chatId : new long[] {100, 1000, -100}) {
        for (TdApi.MessageTopic topic : TOPICS) {
          for (String field : FIELDS) {
            assertTrue(keys.add(ScrollStateKey.key(field, accountId, chatId, topic)));
          }
        }
      }
    }
    assertEquals(294, keys.size());
  }

  @Test
  public void readBackOnlyAcceptsTheExactTopic () {
    for (int i = 0; i < TOPICS.length; i++) {
      for (String field : FIELDS) {
        String key = ScrollStateKey.key(field, 2, 100, TOPICS[i]);
        for (int j = 0; j < TOPICS.length; j++) {
          assertEquals(i == j ? field : null, ScrollStateKey.field(key, 2, 100, TOPICS[j]));
        }
      }
    }
  }

  @Test
  public void signedTopicKeysNeverLeakIntoTheWholeChat () {
    String key = ScrollStateKey.key("_message", 0, 100, new TdApi.MessageTopicSavedMessages(Long.MIN_VALUE));
    assertNull(ScrollStateKey.field(key, 0, 100, null));
    assertEquals("_message", ScrollStateKey.field(key, 0, 100, new TdApi.MessageTopicSavedMessages(Long.MIN_VALUE)));
  }

  @Test
  public void chatAndAccountPrefixCollisionsAreRejected () {
    String key = ScrollStateKey.key("_message", 2, 1000, new TdApi.MessageTopicForum(17));
    assertNull(ScrollStateKey.field(key, 2, 100, new TdApi.MessageTopicForum(17)));
    assertNull(ScrollStateKey.field(key, 0, 1000, new TdApi.MessageTopicForum(17)));
    assertNull(ScrollStateKey.field(key, 3, 1000, new TdApi.MessageTopicForum(17)));
    assertNull(ScrollStateKey.field(key, 2, 100));
  }

  @Test
  public void acknowledgementRemappingRecognizesEveryTopicType () {
    for (TdApi.MessageTopic topic : TOPICS) {
      for (String field : FIELDS) {
        assertEquals(field, ScrollStateKey.field(ScrollStateKey.key(field, 0, 100, topic), 0, 100));
      }
    }
  }

  @Test
  public void unrelatedOrMalformedKeysAreIgnored () {
    for (String suffix : new String[] {"_other", "_message_forum", "_message_forum17_extra", "_message_forum17forum18", "_message_scheduled"}) {
      assertNull(ScrollStateKey.field("scroll_chat100" + suffix, 0, 100));
    }
  }
}
