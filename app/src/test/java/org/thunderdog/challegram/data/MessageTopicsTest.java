package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import tgx.td.Td;

import static org.junit.Assert.*;

public class MessageTopicsTest {
  private static TdApi.MessageTopic[] topics (int id) {
    return new TdApi.MessageTopic[] {
      new TdApi.MessageTopicThread(id), new TdApi.MessageTopicForum(id),
      new TdApi.MessageTopicDirectMessages(id), new TdApi.MessageTopicSavedMessages(id)
    };
  }

  @Test
  public void nullIsOnlyEqualToNull () {
    assertTrue(Td.equalsTo((TdApi.MessageTopic) null, null));
    for (TdApi.MessageTopic topic : topics(17)) {
      assertFalse(Td.equalsTo(topic, null));
      assertFalse(Td.equalsTo(null, topic));
    }
  }

  @Test
  public void constructorsArePartOfIdentity () {
    TdApi.MessageTopic[] left = topics(17);
    TdApi.MessageTopic[] right = topics(17);
    for (int i = 0; i < left.length; i++) {
      for (int j = 0; j < right.length; j++) {
        assertEquals(i == j, Td.equalsTo(left[i], right[j]));
      }
    }
  }

  @Test
  public void differentIdsNeverMatch () {
    for (TdApi.MessageTopic left : topics(17)) {
      for (TdApi.MessageTopic right : topics(18)) {
        assertFalse(Td.equalsTo(left, right));
      }
    }
  }

  @Test
  public void longIdsAreNotTruncatedToForumIds () {
    assertFalse(Td.equalsTo(new TdApi.MessageTopicThread(17), new TdApi.MessageTopicThread((1L << 32) + 17)));
    assertFalse(Td.equalsTo(new TdApi.MessageTopicSavedMessages(-17), new TdApi.MessageTopicSavedMessages(17)));
    assertFalse(Td.equalsTo(new TdApi.MessageTopicDirectMessages(Long.MAX_VALUE), new TdApi.MessageTopicForum(Integer.MAX_VALUE)));
  }

  @Test
  public void wholeChatMembershipIsDirectionalAndNotNavigationEquality () {
    for (TdApi.MessageTopic topic : topics(17)) {
      assertTrue(Td.matchesTopic(topic, null));
      assertFalse(Td.matchesTopic(null, topic));
      assertFalse(MessageTopics.sameChat(100, topic, 100, null));
      assertFalse(MessageTopics.sameChat(100, null, 100, topic));
    }
    assertTrue(Td.matchesTopic(null, null));
    assertFalse(Td.matchesTopic(new TdApi.MessageTopicForum(17), new TdApi.MessageTopicThread(17)));
  }

  @Test
  public void navigationBetweenTopicsRequiresAnotherContext () {
    assertFalse(MessageTopics.sameChat(100, new TdApi.MessageTopicForum(17), false, 100, new TdApi.MessageTopicForum(18), false));
    assertTrue(MessageTopics.sameChat(100, new TdApi.MessageTopicForum(17), false, 100, new TdApi.MessageTopicForum(17), false));
    assertFalse(MessageTopics.sameChat(100, new TdApi.MessageTopicForum(17), false, 101, new TdApi.MessageTopicForum(17), false));
    assertFalse(MessageTopics.sameChat(100, new TdApi.MessageTopicForum(17), false, 100, new TdApi.MessageTopicForum(17), true));
    assertTrue(MessageTopics.sameChat(100, null, true, 100, null, true));
  }

  @Test
  public void effectiveTopicKeepsExplicitTopicWithoutThreadInfo () {
    assertNull(MessageTopics.effectiveTopic(null, null));
    for (TdApi.MessageTopic topic : topics(17)) {
      assertSame(topic, MessageTopics.effectiveTopic(null, topic));
    }
  }

  @Test
  public void genericThreadTakesPrecedenceConsistently () {
    ThreadInfo thread = ThreadInfo.INVALID;
    assertSame(thread.getMessageTopicId(), MessageTopics.effectiveTopic(thread, null));
    assertSame(thread.getMessageTopicId(), MessageTopics.effectiveTopic(thread, new TdApi.MessageTopicForum(17)));
  }

  @Test
  public void generatedCacheKeysKeepAllConstructorsSeparate () {
    Set<String> keys = new HashSet<>();
    keys.add(Td.cacheKey(null));
    for (TdApi.MessageTopic topic : topics(17)) {
      assertTrue(keys.add(Td.cacheKey(topic)));
    }
    assertEquals(5, keys.size());
  }
}
