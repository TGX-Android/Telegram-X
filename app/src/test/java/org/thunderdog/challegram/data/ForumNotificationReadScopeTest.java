package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ForumNotificationReadScopeTest {
  private static final long CHAT_ID = 42;

  private static TdApi.NotificationType message (TdApi.MessageTopic topicId) {
    TdApi.Message message = new TdApi.Message();
    message.topicId = topicId;
    return new TdApi.NotificationTypeNewMessage(message, true);
  }

  private static TdApi.NotificationType forumMessage (int topicId) {
    return message(new TdApi.MessageTopicForum(topicId));
  }

  private static int[] topics (TdApi.NotificationType... notifications) {
    return ForumNotificationReadScope.topicIds(notifications.length, Arrays.asList(notifications));
  }

  private static void assertChatWide (int[] topicIds) {
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(1, requests.size());
    assertTrue(requests.get(0) instanceof TdApi.ReadAllChatMentions);
    assertEquals(CHAT_ID, ((TdApi.ReadAllChatMentions) requests.get(0)).chatId);
  }

  @Test public void mixedTopicsReadEveryRepresentedTopicNotJustLatest () {
    int[] topicIds = topics(forumMessage(7), forumMessage(9), forumMessage(7));
    assertArrayEquals(new int[] {7, 9}, topicIds);
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(2, requests.size());
    for (int i = 0; i < requests.size(); i++) {
      assertTrue(requests.get(i) instanceof TdApi.ReadAllForumTopicMentions);
      TdApi.ReadAllForumTopicMentions request = (TdApi.ReadAllForumTopicMentions) requests.get(i);
      assertEquals(CHAT_ID, request.chatId);
      assertEquals(topicIds[i], request.forumTopicId);
    }
  }

  @Test public void generalIsAnExplicitTopicAndRepeatedTopicsNeedOnlyOneRequest () {
    int[] topicIds = topics(forumMessage(1), forumMessage(1));
    assertArrayEquals(new int[] {1}, topicIds);
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(1, requests.size());
    assertEquals(1, ((TdApi.ReadAllForumTopicMentions) requests.get(0)).forumTopicId);
  }

  @Test public void serializedScopeIsDeduplicatedWithoutChangingItsInput () {
    int[] topicIds = {9, 7, 9};
    List<TdApi.Function<TdApi.Ok>> requests = ForumNotificationReadScope.requests(CHAT_ID, topicIds);
    assertEquals(2, requests.size());
    assertEquals(9, ((TdApi.ReadAllForumTopicMentions) requests.get(0)).forumTopicId);
    assertEquals(7, ((TdApi.ReadAllForumTopicMentions) requests.get(1)).forumTopicId);
    assertArrayEquals(new int[] {9, 7, 9}, topicIds);
  }

  @Test public void nonForumMentionsRetainTheChatWideRead () {
    int[] topicIds = topics(message(null));
    assertNull(topicIds);
    assertChatWide(topicIds);
  }

  @Test public void pushWithoutTopicNeverBorrowsTheLatestMessagesTopic () {
    TdApi.NotificationTypeNewPushMessage push = new TdApi.NotificationTypeNewPushMessage();
    push.messageId = 100;
    assertNull(topics(push));
    assertNull(topics(push, forumMessage(9)));
    assertNull(topics(forumMessage(9), push));
    assertChatWide(topics(push, forumMessage(9)));
  }

  @Test public void missingMetadataAndUnknownTypesUseTheCompatibleFallback () {
    assertChatWide(topics(new TdApi.NotificationTypeNewMessage()));
    assertChatWide(topics(new TdApi.NotificationTypeNewSecretChat()));
    assertChatWide(topics(forumMessage(9), null));
    assertChatWide(topics(forumMessage(9), message(null)));
  }

  @Test public void incompleteCachedGroupCannotDismissUnrepresentedTopics () {
    int[] topicIds = ForumNotificationReadScope.topicIds(3, Arrays.asList(forumMessage(7), forumMessage(9)));
    assertNull(topicIds);
    assertChatWide(topicIds);
  }

  @Test public void legacyIntentsAndEmptySnapshotsDoNotDependOnAnInMemoryGroup () {
    assertChatWide(null);
    assertChatWide(new int[0]);
    assertNull(ForumNotificationReadScope.topicIds(0, Collections.emptyList()));
    assertNull(ForumNotificationReadScope.topicIds(1, null));
  }

  @Test public void invalidTopicIdentityFallsBackForTheWholeGroupNotOnlyTheValidSubset () {
    for (int invalid : new int[] {0, -1}) {
      assertNull(topics(forumMessage(9), forumMessage(invalid)));
      assertChatWide(new int[] {9, invalid});
    }
  }

  @Test public void groupDismissalWaitsForEverySuccessfulRead () {
    ForumNotificationReadScope.Completion completion = new ForumNotificationReadScope.Completion(3);
    assertFalse(completion.onResult(true));
    assertFalse(completion.onResult(true));
    assertTrue(completion.onResult(true));
    assertFalse(completion.onResult(true));
  }

  @Test public void anyFailedTopicPreventsGroupDismissalRegardlessOfCallbackOrder () {
    for (int failedIndex = 0; failedIndex < 3; failedIndex++) {
      ForumNotificationReadScope.Completion completion = new ForumNotificationReadScope.Completion(3);
      for (int index = 0; index < 3; index++) {
        assertFalse(completion.onResult(index != failedIndex));
      }
    }
  }

  @Test public void failedChatWideFallbackAlsoKeepsTheNotification () {
    ForumNotificationReadScope.Completion failure = new ForumNotificationReadScope.Completion(1);
    assertFalse(failure.onResult(false));
    ForumNotificationReadScope.Completion success = new ForumNotificationReadScope.Completion(1);
    assertTrue(success.onResult(true));
  }

  @Test(expected = IllegalArgumentException.class)
  public void emptyRequestSetCannotBeTreatedAsSuccessful () {
    new ForumNotificationReadScope.Completion(0);
  }
}
