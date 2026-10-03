package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

public class ForumTopicUpdatesTest {
  private static class Observer implements ChatListener {
    TdApi.UpdateForumTopic update;
    TdApi.ForumTopicInfo info;
    int count;

    @Override
    public void onForumTopicUpdated (TdApi.UpdateForumTopic update) {
      this.update = update;
      count++;
    }

    @Override
    public void onForumTopicInfoChanged (TdApi.ForumTopicInfo info) {
      this.info = info;
    }
  }

  @Test
  public void completeUpdateReachesTopicAndChatSubscribers () {
    TdlibListeners listeners = new TdlibListeners(null);
    Observer topic = new Observer();
    Observer chat = new Observer();
    Observer global = new Observer();
    listeners.subscribeToForumTopicUpdates(100, 17, topic);
    listeners.subscribeToChatUpdates(100, chat);
    listeners.subscribeForGlobalUpdates(global);
    TdApi.UpdateForumTopic update = new TdApi.UpdateForumTopic(100, 17, true, 200, 201, 3, 4, 5, new TdApi.ChatNotificationSettings(), new TdApi.DraftMessage());
    listeners.updateForumTopic(update);
    assertSame(update, topic.update);
    assertSame(update, chat.update);
    assertSame(update, global.update);
    assertEquals(3, topic.update.unreadMentionCount);
    assertEquals(4, topic.update.unreadReactionCount);
    assertEquals(5, topic.update.unreadPollVoteCount);
    assertSame(update.draftMessage, topic.update.draftMessage);
    assertSame(update.notificationSettings, topic.update.notificationSettings);
    update = new TdApi.UpdateForumTopic(100, 17, false, 202, 203, 0, 0, 0, new TdApi.ChatNotificationSettings(), null);
    listeners.updateForumTopic(update);
    assertSame(update, topic.update);
    assertNull(topic.update.draftMessage);
    assertEquals(2, topic.count);
  }

  @Test
  public void topicAndChatIdsRouteUpdatesExactly () {
    TdlibListeners listeners = new TdlibListeners(null);
    Observer first = new Observer();
    Observer second = new Observer();
    Observer otherChat = new Observer();
    listeners.subscribeToForumTopicUpdates(100, 17, first);
    listeners.subscribeToForumTopicUpdates(100, 18, second);
    listeners.subscribeToForumTopicUpdates(101, 17, otherChat);
    TdApi.UpdateForumTopic update = new TdApi.UpdateForumTopic();
    update.chatId = 100;
    update.forumTopicId = 17;
    listeners.updateForumTopic(update);
    assertSame(update, first.update);
    assertNull(second.update);
    assertNull(otherChat.update);
    listeners.unsubscribeFromForumTopicUpdates(100, 17, first);
    listeners.updateForumTopic(update);
    assertEquals(1, first.count);
  }

  @Test
  public void infoAndFullUpdatesUseTheSameSubscriptionKey () {
    TdlibListeners listeners = new TdlibListeners(null);
    Observer observer = new Observer();
    listeners.subscribeToForumTopicUpdates(100, 17, observer);
    TdApi.ForumTopicInfo info = new TdApi.ForumTopicInfo();
    info.chatId = 100;
    info.forumTopicId = 17;
    listeners.updateForumTopicInfo(new TdApi.UpdateForumTopicInfo(info));
    assertSame(info, observer.info);
    listeners.unsubscribeFromForumTopicUpdates(100, 17, observer);
    observer.info = null;
    listeners.updateForumTopicInfo(new TdApi.UpdateForumTopicInfo(info));
    assertNull(observer.info);
  }
}
