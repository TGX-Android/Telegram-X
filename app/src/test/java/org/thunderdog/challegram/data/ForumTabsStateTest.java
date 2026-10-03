package org.thunderdog.challegram.data;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.thunderdog.challegram.data.ForumTabsState.*;

/** Synthetic identities only; no Android, JNI, server or persisted user data. */
public class ForumTabsStateTest {
  private static void assertState (int placement, int nextHorizontal, Integer topicId, ForumTabsState actual) {
    assertEquals(placement, actual.placement);
    assertEquals(nextHorizontal, actual.nextHorizontal);
    assertEquals(topicId, actual.selectedTopicId);
  }

  private static void assertDefault (int[] encoded) {
    assertState(TOP, BOTTOM, null, decode(encoded));
  }

  @Test public void defaultIsTopWithUnsetSelection () {
    assertState(TOP, BOTTOM, null, defaultState());
    assertArrayEquals(new int[] {1, TOP, BOTTOM, -1}, defaultState().encode());
  }

  @Test public void cyclesThroughBothStartDirectionsRepeatedly () {
    ForumTabsState state = defaultState().withSelectedTopic(73);
    int[] placements = {TOP, START, BOTTOM, START};
    int[] next = {BOTTOM, BOTTOM, TOP, TOP};
    for (int i = 0; i < 20; i++) {
      assertState(placements[i % 4], next[i % 4], 73, state);
      state = state.cyclePlacement();
    }
    assertState(TOP, BOTTOM, 73, state);
  }

  @Test public void roundTripsEveryPlacementDirectionAndSelectionKind () {
    for (Integer topicId : new Integer[] {null, 0, 73, Integer.MAX_VALUE}) {
      ForumTabsState state = topicId == null ? defaultState() : defaultState().withSelectedTopic(topicId);
      for (int i = 0; i < 4; i++) {
        ForumTabsState restored = decode(state.encode());
        assertState(state.placement, state.nextHorizontal, topicId, restored);
        assertArrayEquals(state.encode(), restored.encode());
        assertArrayEquals(state.cyclePlacement().encode(), restored.cyclePlacement().encode());
        state = state.cyclePlacement();
      }
    }
  }

  @Test public void allIsDifferentFromUnsetAndClearingRetainsPlacementDirection () {
    ForumTabsState unset = defaultState().cyclePlacement().cyclePlacement().cyclePlacement();
    ForumTabsState all = unset.withSelectedTopic(0);
    assertFalse(Arrays.equals(unset.encode(), all.encode()));
    assertState(START, TOP, null, decode(unset.encode()));
    assertState(START, TOP, 0, decode(all.encode()));
    assertState(START, TOP, null, all.withoutSelection());
    assertState(START, TOP, 0, all);
    assertState(TOP, BOTTOM, null, all.withoutSelection().cyclePlacement());
  }

  @Test public void positiveForumIdsArePreservedWithoutRemappingGeneral () {
    for (int topicId : new int[] {17, 73, 1024, Integer.MAX_VALUE}) {
      assertEquals(Integer.valueOf(topicId), decode(defaultState().withSelectedTopic(topicId).encode()).selectedTopicId);
    }
  }

  @Test public void stateAndCodecArraysAreIndependent () {
    ForumTabsState original = defaultState().withSelectedTopic(73);
    ForumTabsState changed = original.cyclePlacement().withSelectedTopic(17);
    ForumTabsState cleared = changed.withoutSelection();
    int[] encoded = changed.encode();
    ForumTabsState restored = decode(encoded);
    Arrays.fill(encoded, Integer.MIN_VALUE);
    int[] otherEncoding = restored.encode();
    otherEncoding[3] = 0;
    assertState(TOP, BOTTOM, 73, original);
    assertState(START, BOTTOM, 17, changed);
    assertState(START, BOTTOM, null, cleared);
    assertState(START, BOTTOM, 17, restored);
    assertState(TOP, BOTTOM, null, defaultState());
  }

  @Test public void missingTruncatedAndTrailingDataFallBackToDefault () {
    assertDefault(null);
    int[] valid = defaultState().cyclePlacement().withSelectedTopic(73).encode();
    for (int length = 0; length < valid.length; length++) {
      assertDefault(Arrays.copyOf(valid, length));
    }
    assertDefault(Arrays.copyOf(valid, valid.length + 1));
  }

  @Test public void unknownVersionsFallBackToDefault () {
    for (int version : new int[] {Integer.MIN_VALUE, -1, 0, 2, Integer.MAX_VALUE}) {
      assertDefault(new int[] {version, START, TOP, 73});
    }
  }

  @Test public void invalidPlacementAndDirectionFallBackToDefault () {
    for (int invalid : new int[] {Integer.MIN_VALUE, -1, 3, Integer.MAX_VALUE}) {
      assertDefault(new int[] {1, invalid, TOP, 73});
      assertDefault(new int[] {1, START, invalid, 73});
    }
    assertDefault(new int[] {1, START, START, 73});
    assertDefault(new int[] {1, TOP, TOP, 73});
    assertDefault(new int[] {1, BOTTOM, BOTTOM, 73});
  }

  @Test public void corruptTopicIdsFallBackWithoutKeepingPartialState () {
    assertDefault(new int[] {1, START, TOP, -2});
    assertDefault(new int[] {1, BOTTOM, TOP, Integer.MIN_VALUE});
  }

  @Test(expected = IllegalArgumentException.class)
  public void unsetCannotBeSelectedAsATopic () {
    defaultState().withSelectedTopic(-1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void negativeTopicIdsAreRejected () {
    defaultState().withSelectedTopic(Integer.MIN_VALUE);
  }
}
