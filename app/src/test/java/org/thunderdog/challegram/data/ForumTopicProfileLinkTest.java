package org.thunderdog.challegram.data;

import org.junit.Test;

import static org.junit.Assert.*;

/** Synthetic URLs/identities only; no Android views, TDLib client, or server access. */
public class ForumTopicProfileLinkTest {
  @Test public void successfulEagerLookupMakesUrlAvailableBeforeAnyCopy () {
    ForumTopicProfileLink link = new ForumTopicProfileLink();
    ForumTopicProfileLink.Request request = link.begin(-100, 7);
    assertEquals(ForumTopicProfileLink.State.LOADING, link.state());
    assertNull(link.begin(-100, 7));
    assertTrue(link.complete(request, "https://example.invalid/topic/7", true));
    assertEquals(ForumTopicProfileLink.State.READY, link.state());
    assertEquals("https://example.invalid/topic/7", link.url());
    assertEquals("example.invalid/topic/7", link.displayUrl());
    assertTrue(link.isPublic());
    assertNull(link.begin(-100, 7));
  }

  @Test public void privateLinkRemainsCopyableWithoutBecomingAnInvite () {
    ForumTopicProfileLink link = new ForumTopicProfileLink();
    assertTrue(link.complete(link.begin(-100, 8), "https://example.invalid/c/100/8", false));
    assertEquals(ForumTopicProfileLink.State.READY, link.state());
    assertFalse(link.isPublic());
    assertEquals("https://example.invalid/c/100/8", link.url());
  }

  @Test public void failedOrEmptyResultIsUnavailableAndExplicitlyRetryable () {
    for (String empty : new String[] {null, "", "  "}) {
      ForumTopicProfileLink link = new ForumTopicProfileLink();
      ForumTopicProfileLink.Request failed = link.begin(-100, 7);
      assertTrue(link.complete(failed, empty, true));
      assertEquals(ForumTopicProfileLink.State.UNAVAILABLE, link.state());
      assertNull(link.url()); assertFalse(link.isPublic());
      ForumTopicProfileLink.Request retry = link.begin(-100, 7);
      assertNotNull(retry); assertNotSame(failed, retry);
      assertFalse(link.complete(failed, "https://example.invalid/stale", true));
      assertTrue(link.complete(retry, "https://example.invalid/retry", false));
    }
  }

  @Test public void timeoutRejectsLateSuccessAndCannotEraseCompletedLink () {
    ForumTopicProfileLink link = new ForumTopicProfileLink();
    ForumTopicProfileLink.Request expired = link.begin(-100, 7);
    assertTrue(link.complete(expired, null, false));
    assertFalse(link.complete(expired, "https://example.invalid/late", true));
    ForumTopicProfileLink.Request retry = link.begin(-100, 7);
    assertTrue(link.complete(retry, "https://example.invalid/current", true));
    assertFalse(link.complete(retry, null, false));
    assertEquals("https://example.invalid/current", link.url());
  }

  @Test public void topicAndChatRetargetsRejectOldReplies () {
    ForumTopicProfileLink link = new ForumTopicProfileLink();
    ForumTopicProfileLink.Request oldTopic = link.begin(-100, 7);
    ForumTopicProfileLink.Request newTopic = link.begin(-100, 8);
    assertFalse(link.complete(oldTopic, "https://example.invalid/old-topic", true));
    ForumTopicProfileLink.Request newChat = link.begin(-200, 8);
    assertFalse(link.complete(newTopic, "https://example.invalid/old-chat", true));
    assertTrue(link.matches(-200, 8)); assertFalse(link.matches(-100, 8));
    assertTrue(link.complete(newChat, "https://example.invalid/current", false));
  }

  @Test public void cleanupClearsCachedUrlAndInvalidatesInFlightRequests () {
    ForumTopicProfileLink link = new ForumTopicProfileLink();
    ForumTopicProfileLink.Request pending = link.begin(-100, 7);
    link.invalidate();
    assertFalse(link.complete(pending, "https://example.invalid/stale", true));
    assertFalse(link.matches(-100, 7));
    assertTrue(link.complete(link.begin(-100, 7), "https://example.invalid/current", true));
    link.invalidate();
    assertEquals(ForumTopicProfileLink.State.EMPTY, link.state());
    assertNull(link.url()); assertNull(link.displayUrl()); assertFalse(link.isPublic());
  }

  @Test(expected = IllegalArgumentException.class) public void topicZeroIsNeverAValidTarget () {
    new ForumTopicProfileLink().begin(-100, 0);
  }
}
