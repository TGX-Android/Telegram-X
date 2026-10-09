package org.thunderdog.challegram.component.chat;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import org.thunderdog.challegram.data.ForumHistory;
import org.thunderdog.challegram.data.MessageTopics;

import static org.junit.Assert.*;

public class InputSendContextTest {
  private final Object controller = new Object(), account = new Object(), arguments = new Object();
  private static final long CHAT = -100;

  private InputSendContext capture (TdApi.MessageTopic visible, boolean scheduled, TdApi.MessageTopic outgoing) {
    return new InputSendContext(controller, account, arguments, CHAT, visible, scheduled, outgoing);
  }

  private boolean matches (InputSendContext pending, TdApi.MessageTopic visible, boolean scheduled) {
    return pending.matches(controller, account, arguments, false, CHAT, visible, scheduled);
  }

  @Test
  public void allCanSendToGeneralWithoutBecomingGeneral () {
    TdApi.MessageTopic general = new TdApi.MessageTopicForum(1);
    InputSendContext pending = capture(null, false, general);
    assertTrue(matches(pending, null, false));
    assertSame(general, pending.outgoingTopic);
    // This was the old IME guard: a destination compared against the visible All identity.
    assertFalse(MessageTopics.sameChat(CHAT, general, CHAT, null));
    assertFalse(matches(pending, general, false));
  }

  @Test
  public void allReplyKeepsTheRepliedTopicForImmediateAndScheduledSends () {
    TdApi.MessageTopic replyTopic = new TdApi.MessageTopicForum(37);
    for (boolean scheduled : new boolean[] {false, true}) {
      InputSendContext pending = capture(null, scheduled, ForumHistory.outgoingTopic(null, replyTopic));
      assertTrue(matches(pending, null, scheduled));
      assertSame(replyTopic, pending.outgoingTopic);
      assertFalse(matches(pending, replyTopic, scheduled));
    }
  }

  @Test
  public void explicitTopicAndOrdinaryWholeChatStillMatch () {
    TdApi.MessageTopic visible = new TdApi.MessageTopicForum(37);
    InputSendContext topic = capture(visible, false, ForumHistory.outgoingTopic(visible, new TdApi.MessageTopicForum(49)));
    assertTrue(matches(topic, new TdApi.MessageTopicForum(37), false));
    assertSame(visible, topic.outgoingTopic);
    InputSendContext wholeChat = capture(null, false, null);
    assertTrue(matches(wholeChat, null, false));
    assertNull(wholeChat.outgoingTopic);
  }

  @Test
  public void identicalIdsDoNotAllowAnotherControllerAccountOrViewArguments () {
    InputSendContext pending = capture(null, false, new TdApi.MessageTopicForum(1));
    assertFalse(pending.matches(new Object(), account, arguments, false, CHAT, null, false));
    assertFalse(pending.matches(controller, new Object(), arguments, false, CHAT, null, false));
    assertFalse(pending.matches(controller, account, new Object(), false, CHAT, null, false));
    assertFalse(pending.matches(null, account, arguments, false, CHAT, null, false));
    assertFalse(pending.matches(controller, null, arguments, false, CHAT, null, false));
    assertFalse(pending.matches(controller, account, arguments, true, CHAT, null, false));
  }

  @Test
  public void chatTopicConstructorAndScheduledModeArePartOfTheViewIdentity () {
    InputSendContext pending = capture(new TdApi.MessageTopicForum(37), false, new TdApi.MessageTopicForum(37));
    assertFalse(pending.matches(controller, account, arguments, false, CHAT - 1, new TdApi.MessageTopicForum(37), false));
    assertFalse(matches(pending, null, false));
    assertFalse(matches(pending, new TdApi.MessageTopicForum(49), false));
    assertFalse(matches(pending, new TdApi.MessageTopicThread(37), false));
    assertFalse(matches(pending, new TdApi.MessageTopicSavedMessages(37), false));
    assertFalse(matches(pending, new TdApi.MessageTopicDirectMessages(37), false));
    assertFalse(matches(pending, new TdApi.MessageTopicForum(37), true));
  }

  @Test
  public void scheduleConfirmationMustRecheckAfterTheMenuWasOpened () {
    InputSendContext pending = capture(null, true, new TdApi.MessageTopicForum(1));
    assertTrue(matches(pending, null, true)); // Media preparation completed; picker may open.
    assertFalse(matches(pending, null, false)); // Same controller now shows ordinary history.
    assertFalse(matches(pending, new TdApi.MessageTopicForum(1), true)); // All -> General.
    assertFalse(pending.matches(controller, account, arguments, true, CHAT, null, true));
    assertFalse(pending.matches(new Object(), account, arguments, false, CHAT, null, true));
    assertFalse(pending.matches(controller, new Object(), arguments, false, CHAT, null, true));
    assertTrue(matches(pending, null, true)); // An unchanged view can still confirm.
    assertEquals(1, ((TdApi.MessageTopicForum) pending.outgoingTopic).forumTopicId);
  }

  @Test
  public void capturedTopicValueDoesNotFollowMutableTdlibObjects () {
    TdApi.MessageTopicForum visible = new TdApi.MessageTopicForum(37);
    InputSendContext pending = capture(visible, false, new TdApi.MessageTopicForum(37));
    visible.forumTopicId = 49;
    assertFalse(matches(pending, visible, false));
    assertTrue(matches(pending, new TdApi.MessageTopicForum(37), false));
  }

  @Test
  public void longTopicIdsAreNotTruncated () {
    long topicId = (1L << 32) + 37;
    InputSendContext pending = capture(new TdApi.MessageTopicThread(topicId), false, null);
    assertTrue(matches(pending, new TdApi.MessageTopicThread(topicId), false));
    assertFalse(matches(pending, new TdApi.MessageTopicThread(37), false));
  }
}
