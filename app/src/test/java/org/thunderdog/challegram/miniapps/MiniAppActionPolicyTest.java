/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.junit.Test;
import static org.junit.Assert.*;

public class MiniAppActionPolicyTest {
  @Test public void downloadDoesNotEscapeDownloadsDirectory () {
    for (String value : new String[] {"", ".", "..", "../token", "a/b", "a\\b", "a\nb", "\u0000file", "a\u007fb"}) assertFalse(value, MiniAppActionPolicy.fileName(value));
    assertTrue(MiniAppActionPolicy.fileName("Расписание на октябрь.pdf"));
  }
  @Test public void downloadLimitsEncodedName () {
    String ascii = new String(new char[255]).replace('\0', 'a');
    assertTrue(MiniAppActionPolicy.fileName(ascii)); assertFalse(MiniAppActionPolicy.fileName(ascii + "b"));
    assertFalse(MiniAppActionPolicy.fileName(new String(new char[128]).replace('\0', 'я')));
  }
  @Test public void activeSchemesCannotBecomeExternalLinks () {
    for (String scheme : new String[] {"file", "content", "intent", "javascript", "data"}) assertFalse(MiniAppActionPolicy.externalUrl(scheme + ":evil", "https " + scheme));
    assertFalse(MiniAppActionPolicy.externalUrl("https://bot@evil.test/path", "https"));
    assertFalse(MiniAppActionPolicy.externalUrl("https://good.test/\r\nevil", "https"));
  }
  @Test public void externalLinksUseServerAllowlist () {
    assertTrue(MiniAppActionPolicy.externalUrl("https://example.test/?a=b", "http https"));
    assertFalse(MiniAppActionPolicy.externalUrl("tg://resolve?domain=bot", "http https"));
    assertTrue(MiniAppActionPolicy.externalUrl("mailto:user@example.test", "https mailto"));
  }
  @Test public void browserNamesCannotInjectPackages () {
    assertEquals("com.android.chrome", MiniAppActionPolicy.browserPackage("google-chrome"));
    assertEquals("org.mozilla.firefox", MiniAppActionPolicy.browserPackage("firefox"));
    assertNull(MiniAppActionPolicy.browserPackage("com.attacker.app"));
  }
}
