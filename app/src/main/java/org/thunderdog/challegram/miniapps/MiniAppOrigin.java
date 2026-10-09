/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** A web origin, never a prefix match against an authorization-bearing URL. */
public final class MiniAppOrigin {
  private MiniAppOrigin () { }

  public static String of (String url) {
    if (url == null) return null;
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (scheme == null || host == null || uri.getRawUserInfo() != null) return null;
      scheme = scheme.toLowerCase(Locale.US);
      if (!"https".equals(scheme) && !"http".equals(scheme)) return null;
      int port = uri.getPort();
      if (port < -1 || port > 65535) return null;
      if (port == -1) port = "https".equals(scheme) ? 443 : 80;
      return scheme + "://" + host.toLowerCase(Locale.US) + ":" + port;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  public static boolean matches (String first, String second) {
    String origin = of(first);
    return origin != null && origin.equals(of(second));
  }
}
