package org.thunderdog.challegram.miniapps;

import org.junit.Test;

import static org.junit.Assert.*;

public class MiniAppDocumentTest {
  private static final String APP = "https://app.example/miniapp#signed-data";
  private static final String OTHER = "https://other.example/";

  @Test public void validatesFrameAndTopLevelIndependently () {
    MiniAppDocument doc = new MiniAppDocument(APP, true);
    assertNotNull(doc.receive("https://app.example:443", APP, 32));
    assertNull(doc.receive(OTHER, APP, 32));
    assertNull(doc.receive(APP, OTHER, 32));
    assertNull(doc.receive(null, APP, 32));
  }

  @Test public void permitsSameOriginIframesAndExplicitUnrestrictedNavigation () {
    MiniAppDocument restricted = new MiniAppDocument(APP, true);
    assertNotNull(restricted.receive("https://app.example/frame", APP, 1));
    MiniAppDocument unrestricted = new MiniAppDocument(APP, false);
    assertNotNull(unrestricted.receive(OTHER, OTHER, 1));
    assertNull(unrestricted.receive("null", OTHER, 1));
  }

  @Test public void lateCallbacksCannotCrossReloadNavigationOrOwner () {
    MiniAppDocument doc = new MiniAppDocument(APP, false);
    MiniAppDocument.Request first = doc.receive(APP, APP, 1);
    assertTrue(doc.isCurrent(first, APP));
    assertFalse(new MiniAppDocument(APP, false).isCurrent(first, APP));
    doc.navigate();
    assertFalse(doc.isCurrent(first, APP));
    assertFalse(doc.isCurrent(first, OTHER));
    MiniAppDocument.Request second = doc.receive(OTHER, OTHER, 1);
    assertTrue(doc.isCurrent(second, OTHER));
    doc.close();
    assertFalse(doc.isCurrent(second, OTHER));
    assertNull(doc.receive(APP, APP, 1));
  }

  @Test public void callbackRevalidatesTopLevelOrigin () {
    MiniAppDocument doc = new MiniAppDocument(APP, true);
    MiniAppDocument.Request request = doc.receive(APP, APP, 1);
    assertFalse(doc.isCurrent(request, OTHER));
    assertFalse(doc.canSend(OTHER));
  }

  @Test public void limitsUntrustedPayloads () {
    MiniAppDocument doc = new MiniAppDocument(APP, true);
    assertNotNull(doc.receive(APP, APP, MiniAppDocument.MAX_MESSAGE_LENGTH));
    assertNull(doc.receive(APP, APP, MiniAppDocument.MAX_MESSAGE_LENGTH + 1));
    assertNull(doc.receive(APP, APP, -1));
  }
}
