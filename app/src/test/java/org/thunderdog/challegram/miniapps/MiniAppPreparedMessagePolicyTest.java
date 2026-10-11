package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class MiniAppPreparedMessagePolicyTest {
  @Test public void preparedIdentifierMustBeAString () {
    assertFalse(MiniAppPreparedMessagePolicy.validId(null));
    assertFalse(MiniAppPreparedMessagePolicy.validId(123));
    assertFalse(MiniAppPreparedMessagePolicy.validId(""));
    assertFalse(MiniAppPreparedMessagePolicy.validId(new String(new char[1025])));
    assertTrue(MiniAppPreparedMessagePolicy.validId("server-issued-id"));
  }
  @Test public void discoveryTitleIsNeverUsedAsMessageOrIdentifier () {
    TdApi.InlineQueryResultArticle article = new TdApi.InlineQueryResultArticle();
    article.id = "result-id"; article.title = "This is not the sent message";
    assertEquals("result-id", MiniAppPreparedMessagePolicy.resultId(article));
    assertNull(MiniAppPreparedMessagePolicy.resultId(null));
  }
  @Test public void launchReplyCannotLeakToAnotherRecipientOrTopic () {
    TdApi.InputMessageReplyTo reply = new TdApi.InputMessageReplyToMessage(123, null, 0, "");
    TdApi.MessageTopic topic = new TdApi.MessageTopicForum(88);
    assertSame(reply, MiniAppPreparedMessagePolicy.replyTo(-100, topic, reply, -100, new TdApi.MessageTopicForum(88)));
    assertNull(MiniAppPreparedMessagePolicy.replyTo(-100, topic, reply, -200, topic));
    assertNull(MiniAppPreparedMessagePolicy.replyTo(-100, topic, reply, -100, new TdApi.MessageTopicForum(99)));
    assertNull(MiniAppPreparedMessagePolicy.replyTo(-100, topic, reply, -100, null));
  }
  @Test public void plainChatReplySurvivesOnlyForItsOriginalChat () {
    TdApi.InputMessageReplyTo reply = new TdApi.InputMessageReplyToMessage(123, null, 0, "");
    assertSame(reply, MiniAppPreparedMessagePolicy.replyTo(7, null, reply, 7, null));
    assertNull(MiniAppPreparedMessagePolicy.replyTo(7, null, reply, 8, null));
  }
}
