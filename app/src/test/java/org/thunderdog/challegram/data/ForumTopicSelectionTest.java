package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;
import static org.thunderdog.challegram.data.ForumTopicSelection.Action.*;

/** Synthetic identities only; no Android, JNI or server. */
public class ForumTopicSelectionTest {
  private TdApi.ForumTopic topic (int id) {
    TdApi.ForumTopic t = new TdApi.ForumTopic();
    t.info = new TdApi.ForumTopicInfo(); t.info.chatId = 100; t.info.forumTopicId = id; t.info.name = "Synthetic " + id;
    t.info.isGeneral = id == 1; t.notificationSettings = new TdApi.ChatNotificationSettings();
    return t;
  }
  private TdApi.ChatMemberStatusCreator owner () {
    TdApi.ChatMemberStatusCreator s = new TdApi.ChatMemberStatusCreator(); s.isMember = true; return s;
  }
  private EnumSet<ForumTopicSelection.Action> actions (List<TdApi.ForumTopic> topics) {
    return ForumTopicSelection.intersection(topics, owner(), false, true, 0, 5);
  }
  @Test public void homogeneousDirectionsOnly () {
    List<TdApi.ForumTopic> topics = Arrays.asList(topic(17), topic(18));
    assertTrue(actions(topics).containsAll(Arrays.asList(PIN, MUTE, CLOSE, DELETE)));
    topics.get(1).isPinned = true; topics.get(1).info.isClosed = true; topics.get(1).notificationSettings.muteFor = 100;
    EnumSet<ForumTopicSelection.Action> result = actions(topics);
    assertFalse(result.contains(PIN)); assertFalse(result.contains(UNPIN));
    assertFalse(result.contains(MUTE)); assertFalse(result.contains(UNMUTE));
    assertFalse(result.contains(CLOSE)); assertFalse(result.contains(OPEN));
    assertTrue(result.contains(DELETE));
  }
  @Test public void homogeneousInverseDirections () {
    TdApi.ForumTopic t = topic(17); t.isPinned = true; t.info.isClosed = true; t.notificationSettings.muteFor = 10;
    assertTrue(actions(Collections.singletonList(t)).containsAll(Arrays.asList(UNPIN, UNMUTE, OPEN)));
  }
  @Test public void effectiveMuteIncludesGroupInheritance () {
    TdApi.ForumTopic inherited = topic(17), explicit = topic(18);
    inherited.notificationSettings.useDefaultMuteFor = true; explicit.notificationSettings.muteFor = 50;
    List<TdApi.ForumTopic> topics = Arrays.asList(inherited, explicit);
    assertTrue(ForumTopicSelection.intersection(topics, owner(), true, true, 0, 5).contains(UNMUTE));
    assertFalse(ForumTopicSelection.intersection(topics, owner(), false, true, 0, 5).contains(UNMUTE));
    explicit.notificationSettings.muteFor = 0;
    assertFalse(ForumTopicSelection.intersection(topics, owner(), true, true, 0, 5).contains(MUTE));
  }
  @Test public void unknownSettingsNeverOfferMute () {
    TdApi.ForumTopic t = topic(17); t.notificationSettings = null;
    assertFalse(actions(Collections.singletonList(t)).contains(MUTE));
    assertFalse(actions(Collections.singletonList(t)).contains(UNMUTE));
  }
  @Test public void generalIsNeverPartOfBatchDelete () {
    assertFalse(actions(Arrays.asList(topic(1), topic(17))).contains(DELETE));
    assertFalse(actions(Arrays.asList(topic(1), topic(17))).contains(CLEAR_GENERAL));
    assertTrue(actions(Collections.singletonList(topic(1))).contains(CLEAR_GENERAL));
    assertFalse(actions(Collections.singletonList(topic(1))).contains(DELETE));
  }
  @Test public void capacityIsCheckedForWholeSelectionAndUnknownPrefixFailsClosed () {
    List<TdApi.ForumTopic> topics = Arrays.asList(topic(17), topic(18));
    assertFalse(ForumTopicSelection.intersection(topics, owner(), false, true, 4, 5).contains(PIN));
    assertTrue(ForumTopicSelection.intersection(topics, owner(), false, true, 3, 5).contains(PIN));
    assertFalse(ForumTopicSelection.intersection(topics, owner(), false, false, 0, 5).contains(PIN));
  }
  @Test public void intersectionIncludesIndividualCreatorRights () {
    TdApi.ForumTopic a = topic(17), b = topic(18); a.info.isOutgoing = true;
    assertFalse(ForumTopicSelection.intersection(Arrays.asList(a, b), new TdApi.ChatMemberStatusMember(), false, true, 0, 5).contains(CLOSE));
    b.info.isOutgoing = true;
    assertTrue(ForumTopicSelection.intersection(Arrays.asList(a, b), new TdApi.ChatMemberStatusMember(), false, true, 0, 5).contains(CLOSE));
    assertTrue(ForumTopicSelection.intersection(Arrays.asList(a, b), new TdApi.ChatMemberStatusLeft(), false, true, 0, 5).isEmpty());
  }
  @Test public void readCountersAreAlsoStrictIntersection () {
    TdApi.ForumTopic a = topic(17), b = topic(18); a.unreadMentionCount = 2;
    assertFalse(actions(Arrays.asList(a, b)).contains(READ_MENTIONS));
    b.unreadMentionCount = 1;
    assertTrue(actions(Arrays.asList(a, b)).contains(READ_MENTIONS));
  }
  @Test public void selectionIsIdentityBasedAndSurvivesRenameReorderAndPartialRefresh () {
    ForumTopicSelection s = new ForumTopicSelection(3, 100);
    assertTrue(s.add(topic(17))); assertTrue(s.add(topic(18)));
    TdApi.ForumTopic renamed = topic(17); renamed.info.name = "Renamed";
    s.update(Arrays.asList(topic(99), renamed));
    assertEquals(2, s.size()); assertArrayEquals(new int[] {17, 18}, s.ids());
    assertEquals("Renamed", s.topics().get(0).info.name);
    s.remove(18); assertEquals(1, s.size()); s.clear(); assertTrue(s.isEmpty());
  }
  @Test public void foreignChatAndOverLimitAreRejectedAndCopiesCannotMutateSelection () {
    ForumTopicSelection s = new ForumTopicSelection(3, 100);
    TdApi.ForumTopic foreign = topic(17); foreign.info.chatId = 200;
    assertFalse(s.add(foreign));
    for (int id = 2; id < ForumTopicSelection.MAX_SELECTED + 2; id++) assertTrue(s.add(topic(id)));
    assertFalse(s.add(topic(1000))); assertTrue(s.add(topic(2)));
    s.topics().clear(); s.ids()[0] = 1000;
    assertEquals(ForumTopicSelection.MAX_SELECTED, s.size()); assertTrue(s.contains(2));
  }
  @Test public void crossChatIntersectionIsEmpty () {
    TdApi.ForumTopic foreign = topic(18); foreign.info.chatId = 200;
    assertTrue(actions(Arrays.asList(topic(17), foreign)).isEmpty());
  }
  @Test public void selectionHeaderFitsRailNarrowWidthsAndLargeFonts () {
    assertEquals(1, ForumTopicSelection.primaryActionSlots(264, 1));
    assertEquals(2, ForumTopicSelection.primaryActionSlots(296, 1));
    assertEquals(3, ForumTopicSelection.primaryActionSlots(348, 1));
    assertEquals(0, ForumTopicSelection.primaryActionSlots(264, 2));
    for (float width : new float[] {264, 296, 348, 600}) for (float scale : new float[] {1, 1.3f, 2}) {
      assertTrue(48 * (ForumTopicSelection.primaryActionSlots(width, scale) + 1) + 112 + 32 * scale <= width);
    }
  }
}
