/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;

/** Loaded only by API 21+ variants. Feature support belongs to the installed WebView provider. */
public final class MiniAppPlatform {
  private ScriptHandler script;
  private boolean installed;

  public static boolean isSupported () {
    try {
      return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
    } catch (RuntimeException e) {
      return false;
    }
  }

  public boolean install (WebView webView, MiniAppBridgeTransport receiver) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false;
    WebViewCompat.addWebMessageListener(webView, MiniAppBridgeTransport.OBJECT_NAME, Collections.singleton("*"),
      (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
        if (message.getType() == WebMessageCompat.TYPE_STRING) {
          String data = message.getData();
          if (data != null && data.length() <= MiniAppDocument.MAX_MESSAGE_LENGTH) receiver.onMessage(data, sourceOrigin.toString());
        }
      });
    installed = true;
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
      script = WebViewCompat.addDocumentStartJavaScript(webView, MiniAppBridgeTransport.SCRIPT, Collections.singleton("*"));
    }
    return true;
  }

  public void onPageStarted (WebView webView) {
    if (installed && script == null) webView.evaluateJavascript(MiniAppBridgeTransport.SCRIPT, null);
  }

  public void destroy (WebView webView) {
    if (script != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) script.remove();
    script = null;
    if (installed && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(webView, MiniAppBridgeTransport.OBJECT_NAME);
    installed = false;
  }
}
