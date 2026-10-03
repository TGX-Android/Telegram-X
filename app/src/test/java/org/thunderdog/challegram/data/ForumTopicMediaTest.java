package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/** Synthetic scopes only; no TDLib client, real chats, or history scans. */
public class ForumTopicMediaTest {
  private static TdApi.Message message (long chatId, TdApi.MessageTopic topic) {
    TdApi.Message value = new TdApi.Message(); value.chatId = chatId; value.topicId = topic; return value;
  }

  @Test public void allSevenFiltersHaveTheRequiredStableOrderIncludingPolls () {
    int[] constructors = {TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR, TdApi.SearchMessagesFilterDocument.CONSTRUCTOR,
      TdApi.SearchMessagesFilterUrl.CONSTRUCTOR, TdApi.SearchMessagesFilterAudio.CONSTRUCTOR, TdApi.SearchMessagesFilterPoll.CONSTRUCTOR,
      TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR, TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR};
    assertEquals(constructors.length, ForumTopicMedia.CATEGORY_COUNT);
    for (int i = 0; i < constructors.length; i++) assertEquals(constructors[i], ForumTopicMedia.filter(i).getConstructor());
  }

  @Test public void sameChatDifferentTopicsNeverMatch () {
    TdApi.MessageTopicForum topic = new TdApi.MessageTopicForum(101);
    assertTrue(ForumTopicMedia.matches(-1234, topic, message(-1234, new TdApi.MessageTopicForum(101))));
    assertFalse(ForumTopicMedia.matches(-1234, topic, message(-1234, new TdApi.MessageTopicForum(102))));
    assertFalse(ForumTopicMedia.matches(-1234, topic, message(-5678, new TdApi.MessageTopicForum(101))));
    assertFalse(ForumTopicMedia.matches(-1234, topic, message(-1234, null)));
    assertFalse(ForumTopicMedia.matches(-1234, topic, null));
  }

  @Test public void numericTopicIdDoesNotConfuseDifferentTopicKinds () {
    TdApi.MessageTopicForum topic = new TdApi.MessageTopicForum(101);
    assertFalse(ForumTopicMedia.matches(-1234, topic, message(-1234, new TdApi.MessageTopicThread(101))));
    assertFalse(ForumTopicMedia.matches(-1234, topic, message(-1234, new TdApi.MessageTopicSavedMessages(101))));
    assertFalse(ForumTopicMedia.matches(-1234, topic, message(-1234, new TdApi.MessageTopicDirectMessages(101))));
  }

  @Test public void ordinaryChatsAndExistingGenericScopesRetainTheirSemantics () {
    assertTrue(ForumTopicMedia.matches(-1234, null, message(-1234, null)));
    assertTrue(ForumTopicMedia.matches(-1234, null, message(-1234, new TdApi.MessageTopicForum(101))));
    for (TdApi.MessageTopic topic : new TdApi.MessageTopic[] {new TdApi.MessageTopicThread(101), new TdApi.MessageTopicSavedMessages(101), new TdApi.MessageTopicDirectMessages(101)}) {
      assertTrue(ForumTopicMedia.matches(-1234, topic, message(-1234, topic)));
      assertFalse(ForumTopicMedia.matches(-1234, topic, message(-1234, new TdApi.MessageTopicForum(101))));
    }
  }

  @Test public void unknownAndZeroApproximateCountsAreNotAnEmptyVerdict () {
    ForumTopicMedia.Category value = new ForumTopicMedia.Category();
    assertEquals(ForumTopicMedia.Availability.UNKNOWN, value.availability);
    int request = value.begin();
    assertTrue(value.count(request, -1, null));
    assertEquals(-1, value.approximateCount);
    assertEquals(ForumTopicMedia.Availability.LOADING, value.availability);
    assertTrue(value.count(request, 0, null));
    assertEquals(ForumTopicMedia.Availability.LOADING, value.availability);
    assertTrue(value.confirm(request, true, null));
    assertEquals(ForumTopicMedia.Availability.PRESENT, value.availability);
  }

  @Test public void serverEmptyOverridesAnApproximatePositiveCount () {
    ForumTopicMedia.Category value = new ForumTopicMedia.Category(); int request = value.begin();
    value.count(request, 50, null); value.confirm(request, false, null);
    assertEquals(ForumTopicMedia.Availability.EMPTY, value.availability);
  }

  @Test public void partialPageDoesNotBecomeEmptyAndUnknownResponsesAreVersioned () {
    ForumTopicMedia.Category value = new ForumTopicMedia.Category(); int request = value.begin();
    value.count(request, -1, null);
    assertTrue(value.unknown(request));
    assertEquals(ForumTopicMedia.Availability.UNKNOWN, value.availability);
    value.arrived();
    assertFalse(value.unknown(request));
    assertEquals(ForumTopicMedia.Availability.PRESENT, value.availability);
  }

  @Test public void errorAndOfflineAreNotEmptyOrUnknown () {
    ForumTopicMedia.Category value = new ForumTopicMedia.Category(); int request = value.begin();
    TdApi.Error failure = new TdApi.Error(500, "Synthetic connection error");
    value.count(request, 0, failure); value.confirm(request, false, failure);
    assertEquals(ForumTopicMedia.Availability.ERROR, value.availability);
    assertSame(failure, value.error); assertSame(failure, value.countError);
    assertEquals(-1, value.approximateCount);
  }

  @Test public void staleResponsesCannotUndoFirstMaterialOrNewerRefresh () {
    ForumTopicMedia.Category value = new ForumTopicMedia.Category(); int request = value.begin();
    value.arrived();
    assertFalse(value.confirm(request, false, null)); assertFalse(value.count(request, 0, null));
    assertEquals(ForumTopicMedia.Availability.PRESENT, value.availability);
    int next = value.begin(); int newer = value.begin();
    assertFalse(value.confirm(next, false, null)); assertTrue(value.confirm(newer, false, null));
    assertEquals(ForumTopicMedia.Availability.EMPTY, value.availability);
    int lifecycleRequest = value.begin(); value.invalidate();
    assertFalse(value.confirm(lifecycleRequest, true, null));
  }

  @Test public void transientFailureKeepsKnownMaterialsReachable () {
    ForumTopicMedia.Category value = new ForumTopicMedia.Category(); value.arrived(); int request = value.begin();
    value.confirm(request, false, new TdApi.Error(408, "Synthetic timeout"));
    assertEquals(ForumTopicMedia.Availability.PRESENT, value.availability); assertNotNull(value.error);
  }

  @Test public void tabsOnlyContainConfirmedNonemptyTypesAndSelectNearestRemaining () {
    ForumTopicMedia.Category[] categories = new ForumTopicMedia.Category[7];
    for (int i = 0; i < categories.length; i++) { categories[i] = new ForumTopicMedia.Category(); categories[i].arrived(); }
    assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6), ForumTopicMedia.visible(categories));
    for (int index : new int[] {0, 2, 4, 5}) categories[index].confirm(categories[index].begin(), false, null);
    assertEquals(Arrays.asList(1, 3, 6), ForumTopicMedia.visible(categories));
    assertEquals(6, ForumTopicMedia.nearest(ForumTopicMedia.visible(categories), 4));
    assertEquals(3, ForumTopicMedia.nearest(ForumTopicMedia.visible(categories), 3));
    assertEquals(6, ForumTopicMedia.nearest(ForumTopicMedia.visible(categories), 7));
    for (ForumTopicMedia.Category category : categories) category.confirm(category.begin(), false, null);
    assertEquals(-1, ForumTopicMedia.nearest(ForumTopicMedia.visible(categories), 6));
  }
}
