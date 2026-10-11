/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import java.nio.charset.Charset;
import java.net.URI;
import java.util.Locale;

public final class MiniAppActionPolicy {
  private MiniAppActionPolicy () { }
  public static boolean fileName (String name) {
    return name != null && !name.isEmpty() && !name.equals(".") && !name.equals("..") && name.getBytes(Charset.forName("UTF-8")).length <= 255 &&
      name.indexOf('/') < 0 && name.indexOf('\\') < 0 && !name.matches("(?s).*[\\x00-\\x1f\\x7f].*");
  }
  public static boolean externalUrl (String url, String protocols) {
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      if (scheme == null || uri.getUserInfo() != null) return false;
      scheme = scheme.toLowerCase(Locale.US);
      if (scheme.equals("javascript") || scheme.equals("data") || scheme.equals("file") || scheme.equals("content") || scheme.equals("intent")) return false;
      boolean allowed = false;
      for (String protocol : protocols.split(" ")) if (scheme.equals(protocol)) allowed = true;
      return allowed && (!scheme.equals("http") && !scheme.equals("https") || MiniAppOrigin.of(url) != null);
    } catch (Exception ignored) { return false; }
  }
  public static String browserPackage (String browser) {
    switch (browser) {
      case "google-chrome": case "chrome": return "com.android.chrome";
      case "mozilla-firefox": case "firefox": return "org.mozilla.firefox";
      case "microsoft-edge": case "edge": return "com.microsoft.emmx";
      case "opera": return "com.opera.browser";
      case "opera-mini": return "com.opera.mini.native";
      case "brave": case "brave-browser": return "com.brave.browser";
      case "duckduckgo": case "duckduckgo-browser": return "com.duckduckgo.mobile.android";
      case "samsung": case "samsung-browser": return "com.sec.android.app.sbrowser";
      case "vivaldi": case "vivaldi-browser": return "com.vivaldi.browser";
      case "kiwi": case "kiwi-browser": return "com.kiwibrowser.browser";
      case "uc": case "uc-browser": return "com.UCMobile.intl";
      case "tor": case "tor-browser": return "org.torproject.torbrowser";
      default: return null;
    }
  }
}
