package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

public class MiniAppLaunchRequestTest {
  private MiniAppLaunchRequest request (MiniAppLaunchRequest.Source source) {
    return new MiniAppLaunchRequest(source, 123, 456, "https://bot.example", "Buy", "shop", "start",
      new TdApi.MessageTopicForum(789), new TdApi.InputMessageReplyToMessage(), new TdApi.WebAppOpenModeFullSize(), false);
  }

  @Test public void contextualLaunchRetainsThreadReplyAndMode () {
    MiniAppLaunchRequest request = request(MiniAppLaunchRequest.Source.INLINE_KEYBOARD);
    TdApi.OpenWebApp function = (TdApi.OpenWebApp) request.function(null, false);
    assertEquals(456, function.chatId);
    assertEquals(123, function.botUserId);
    assertSame(request.topicId, function.topicId);
    assertSame(request.replyTo, function.replyTo);
    assertSame(request.mode, function.parameters.mode);
  }

  @Test public void simpleSourcesDoNotCreateContextualLease () {
    for (MiniAppLaunchRequest.Source source : new MiniAppLaunchRequest.Source[] {
      MiniAppLaunchRequest.Source.REPLY_KEYBOARD, MiniAppLaunchRequest.Source.INLINE_RESULTS, MiniAppLaunchRequest.Source.SIDE_MENU }) {
      assertTrue(request(source).function(null, false) instanceof TdApi.GetWebAppUrl);
    }
  }

  @Test public void directLinkPropagatesExplicitWriteConsent () {
    MiniAppLaunchRequest request = request(MiniAppLaunchRequest.Source.DIRECT_LINK);
    TdApi.GetWebAppLinkUrl denied = (TdApi.GetWebAppLinkUrl) request.function(null, false);
    TdApi.GetWebAppLinkUrl allowed = (TdApi.GetWebAppLinkUrl) request.function(null, true);
    assertFalse(denied.allowWriteAccess);
    assertTrue(allowed.allowWriteAccess);
    assertEquals("shop", allowed.webAppShortName);
    assertEquals("start", allowed.startParameter);
    assertTrue(request(MiniAppLaunchRequest.Source.MAIN_APP).function(null, false) instanceof TdApi.GetMainWebApp);
  }
}
