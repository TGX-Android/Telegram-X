/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.webkit.WebView;

/** Old providers cannot identify the calling frame. Never fall back to addJavascriptInterface. */
public final class MiniAppPlatform {
  public static boolean isSupported () { return false; }
  public static void clearAccount (long accountUserId) { }
  public boolean install (WebView webView, MiniAppBridgeTransport receiver) { return false; }
  public void onPageStarted (WebView webView) { }
  public void destroy (WebView webView) { }
}
