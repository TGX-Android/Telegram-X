package org.thunderdog.challegram.telegram;

import org.junit.Test;

import static org.junit.Assert.*;

/** Synthetic chat IDs only; exercises the index without a database or Android. */
public class ForumTabsStateMruTest {
  @Test public void missingOrEmptyIndexStartsWithTouchedChat () {
    assertArrayEquals(new long[] {-73}, ForumTabsStateMru.touch(null, -73));
    assertArrayEquals(new long[] {-73}, ForumTabsStateMru.touch(new long[0], -73));
  }

  @Test public void touchingExistingChatPromotesItWithoutDuplicates () {
    long[] previous = {-17, -73, -91};
    assertArrayEquals(new long[] {-73, -17, -91}, ForumTabsStateMru.touch(previous, -73));
    assertArrayEquals(new long[] {-17, -73, -91}, previous);
    assertArrayEquals(previous, ForumTabsStateMru.touch(previous, -17));
    assertNotSame(previous, ForumTabsStateMru.touch(previous, -17));
  }

  @Test public void inserting65thChatEvictsLeastRecentlyUsed () {
    long[] index = null;
    for (int id = 1; id <= 64; id++) {
      index = ForumTabsStateMru.touch(index, -1000L - id);
    }
    assertEquals(64, index.length);
    assertEquals(-1064, index[0]);
    assertEquals(-1001, index[63]);
    index = ForumTabsStateMru.touch(index, -1065);
    assertEquals(64, index.length);
    assertEquals(-1065, index[0]);
    assertEquals(-1002, index[63]);
    assertFalse(ForumTabsStateMru.contains(index, -1001));
  }

  @Test public void readPromotionProtectsOldestChatFromNextEviction () {
    long[] index = null;
    for (int id = 1; id <= 64; id++) {
      index = ForumTabsStateMru.touch(index, -1000L - id);
    }
    index = ForumTabsStateMru.touch(index, -1001);
    assertEquals(64, index.length);
    index = ForumTabsStateMru.touch(index, -1065);
    assertTrue(ForumTabsStateMru.contains(index, -1001));
    assertFalse(ForumTabsStateMru.contains(index, -1002));
    assertEquals(-1065, index[0]);
    assertEquals(-1001, index[1]);
  }

  @Test public void corruptIndexIsDeduplicatedAndZerosAreDiscarded () {
    long[] previous = {0, -73, -17, -73, 0, -91, -17};
    assertArrayEquals(new long[] {-17, -73, -91}, ForumTabsStateMru.touch(previous, -17));
    assertArrayEquals(new long[] {0, -73, -17, -73, 0, -91, -17}, previous);
  }

  @Test public void oversizedIndexIsCappedInOriginalRecencyOrder () {
    long[] previous = new long[100];
    for (int i = 0; i < previous.length; i++) {
      previous[i] = -1000L - i;
    }
    long[] index = ForumTabsStateMru.touch(previous, -1099);
    assertEquals(64, index.length);
    assertEquals(-1099, index[0]);
    for (int i = 1; i < index.length; i++) {
      assertEquals(previous[i - 1], index[i]);
    }
    assertFalse(ForumTabsStateMru.contains(index, -1063));
  }

  @Test public void longChatIdentitiesAreNeverNarrowedOrSignRemapped () {
    long[] previous = {Long.MIN_VALUE, Long.MAX_VALUE, 73, -73};
    assertArrayEquals(new long[] {-73, Long.MIN_VALUE, Long.MAX_VALUE, 73}, ForumTabsStateMru.touch(previous, -73));
  }

  @Test public void independentIndexesDoNotShareMutableState () {
    long[] accountA = ForumTabsStateMru.touch(null, -73);
    long[] accountB = ForumTabsStateMru.touch(null, -73);
    long[] updatedA = ForumTabsStateMru.touch(accountA, -17);
    updatedA[1] = -91;
    assertArrayEquals(new long[] {-73}, accountA);
    assertArrayEquals(new long[] {-73}, accountB);
  }

  @Test(expected = IllegalArgumentException.class)
  public void zeroChatIdIsRejected () {
    ForumTabsStateMru.touch(null, 0);
  }
}
