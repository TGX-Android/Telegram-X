package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

public class MiniAppLaunchRequestTest {
  @Test public void guardApprovalIsBoundToTheExactJoinQuery () {
    MiniAppLaunchRequest request = MiniAppLaunchRequest.guard(123, 999);
    TdApi.GetGuardBotWebAppUrl function = (TdApi.GetGuardBotWebAppUrl) request.function(null, false);
    assertEquals(999, function.queryId);
    assertFalse(request.sameLaunch(MiniAppLaunchRequest.guard(123, 998)));
    assertFalse(request.sameLaunch(MiniAppLaunchRequest.guard(124, 999)));
  }

  @Test public void onlyIdenticalLaunchesCanReuseAMinimizedTab () {
    MiniAppLaunchRequest catalog = new MiniAppLaunchRequest(MiniAppLaunchRequest.Source.INLINE_KEYBOARD, 123, 456, "https://bot.example/app", "", "", "", null, null, null, false);
    MiniAppLaunchRequest notifications = new MiniAppLaunchRequest(catalog.source, 123, 456, "https://bot.example/app/notifications", "", "", "", null, null, null, false);
    assertTrue(catalog.sameLaunch(catalog));
    assertFalse(catalog.sameLaunch(notifications));
    assertFalse(catalog.sameLaunch(new MiniAppLaunchRequest(catalog.source, 123, 456, catalog.url, "", "", "different-start", null, null, null, false)));
    assertFalse(catalog.sameLaunch(new MiniAppLaunchRequest(catalog.source, 123, 999, catalog.url, "", "", "", null, null, null, false)));
  }

  @Test public void defaultModeDoesNotForceAnInitialCompactSheet () {
    for (MiniAppLaunchRequest.Source source : MiniAppLaunchRequest.Source.values()) {
      MiniAppLaunchRequest request = new MiniAppLaunchRequest(source, 123, 456, "https://bot.example/app/notifications", "", "", "", null, null, null, false);
      assertTrue(request.mode instanceof TdApi.WebAppOpenModeFullSize);
    }
  }

  @Test public void urlModesAreExplicitAndDoNotChangeButtonDestination () {
    String url = "https://bot.example/app/notifications?mode=compact&filter=dance";
    MiniAppLaunchRequest request = new MiniAppLaunchRequest(MiniAppLaunchRequest.Source.INLINE_KEYBOARD, 123, 456, url, "", "", "", null, null, null, false);
    assertTrue(request.mode instanceof TdApi.WebAppOpenModeCompact);
    assertEquals(url, ((TdApi.OpenWebApp) request.function(null, false)).url);
    assertTrue(new MiniAppLaunchRequest(request.source, 123, 456, "https://bot.example?mode=fullscreen", "", "", "", null, null, null, false).mode instanceof TdApi.WebAppOpenModeFullScreen);
    assertTrue(new MiniAppLaunchRequest(request.source, 123, 456, url, "", "", "", null, null, new TdApi.WebAppOpenModeFullSize(), false).mode instanceof TdApi.WebAppOpenModeFullSize);
  }

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
