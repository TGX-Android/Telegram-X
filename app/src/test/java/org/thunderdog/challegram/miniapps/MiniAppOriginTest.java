package org.thunderdog.challegram.miniapps;

import org.junit.Test;

import static org.junit.Assert.*;

public class MiniAppOriginTest {
  @Test public void canonicalizesSchemeHostAndDefaultPorts () {
    assertTrue(MiniAppOrigin.matches("HTTPS://App.Example/path#auth", "https://app.example:443/other"));
    assertTrue(MiniAppOrigin.matches("http://app.example", "http://APP.EXAMPLE:80"));
    assertTrue(MiniAppOrigin.matches("https://[2001:db8::1]/", "https://[2001:db8::1]:443/"));
  }

  @Test public void rejectsPrefixAndUserInfoConfusion () {
    assertFalse(MiniAppOrigin.matches("https://app.example", "https://app.example.attacker.test"));
    assertFalse(MiniAppOrigin.matches("https://app.example", "https://app.example@attacker.test"));
    assertFalse(MiniAppOrigin.matches("https://app.example", "https://attacker.test@app.example"));
    assertFalse(MiniAppOrigin.matches("https://app.example", "https://app.example:444"));
    assertFalse(MiniAppOrigin.matches("https://app.example", "http://app.example"));
  }

  @Test public void failsClosedOnOpaqueAndMalformedOrigins () {
    for (String url : new String[] { null, "null", "", "data:text/html,test", "file:///tmp/app.html", "about:blank", "javascript:alert(1)", "https://", "https://app.example:99999", "https://app.example\\@attacker.test" }) {
      assertNull(url, MiniAppOrigin.of(url));
      assertFalse(MiniAppOrigin.matches(url, url));
    }
  }
}
