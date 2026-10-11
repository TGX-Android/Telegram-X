/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.webkit.WebView;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.webkit.ScriptHandler;
import androidx.webkit.ProfileStore;
import androidx.webkit.Profile;
import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Loaded only by API 21+ variants. Feature support belongs to the installed WebView provider. */
public final class MiniAppPlatform {
  private ScriptHandler script;
  private boolean installed;

  public static boolean isSupported () {
    try {
      return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
    } catch (RuntimeException e) {
      return false;
    }
  }

  public static String profileName (long accountUserId, long botUserId) {
    if (accountUserId <= 0 || botUserId <= 0) throw new IllegalArgumentException("Missing Mini App owner");
    return "tg-miniapp-" + accountUserId + "-" + botUserId;
  }

  /** Set this before accessing settings, cookies, or loading any document. */
  public static boolean isolate (WebView webView, long accountUserId, long botUserId) {
    return isolate(webView, accountUserId, botUserId, false);
  }
  public static boolean isolate (WebView webView, long accountUserId, long botUserId, boolean testServer) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return false;
    SharedPreferences state = profileState(webView.getContext());
    pruneProfiles(state);
    WebViewCompat.setProfile(webView, (testServer ? "test-" : "") + profileName(accountUserId, botUserId) + "-" + state.getLong("epoch_" + testServer + "_" + accountUserId, 0));
    return true;
  }

  private static SharedPreferences profileState (Context context) {
    return context.getApplicationContext().getSharedPreferences("miniapp_profiles", Context.MODE_PRIVATE);
  }

  private static void pruneProfiles (SharedPreferences state) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return;
    Set<String> pending = new HashSet<>(state.getStringSet("retired", Collections.emptySet()));
    for (String name : new HashSet<>(pending)) {
      try { ProfileStore.getInstance().deleteProfile(name); pending.remove(name); }
      catch (IllegalStateException inUse) { /* Chromium can retain a profile until process exit. */ }
    }
    state.edit().putStringSet("retired", pending).apply();
  }

  public static void clearAccount (Context context, long accountUserId, boolean testServer) {
    if (accountUserId > 0) MiniAppStorage.clearAccount(context, accountUserId, testServer);
    if (accountUserId <= 0 || !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return;
    SharedPreferences state = profileState(context);
    Set<String> pending = new HashSet<>(state.getStringSet("retired", Collections.emptySet()));
    ProfileStore store = ProfileStore.getInstance();
    String prefix = (testServer ? "test-" : "") + "tg-miniapp-" + accountUserId + "-";
    for (String name : store.getAllProfileNames()) {
      if (!name.startsWith(prefix)) continue;
      pending.add(name);
      Profile profile = store.getProfile(name);
      if (profile != null) {
        profile.getCookieManager().removeAllCookies(null);
        profile.getWebStorage().deleteAllData();
        profile.getGeolocationPermissions().clearAll();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
          WebStorageCompat.deleteBrowsingData(profile.getWebStorage(), () -> { });
        }
      }
    }
    // Never reuse a retired profile after logout, even while Chromium still owns it.
    String epoch = "epoch_" + testServer + "_" + accountUserId;
    state.edit().putLong(epoch, state.getLong(epoch, 0) + 1)
      .putStringSet("retired", pending).apply();
    pruneProfiles(state);
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

  public void whenVisualStateReady (WebView webView, long requestId, Runnable ready) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
      WebViewCompat.postVisualStateCallback(webView, requestId, new WebViewCompat.VisualStateCallback() {
        @Override public void onComplete (long id) { ready.run(); }
      });
    } else {
      webView.postOnAnimation(ready);
    }
  }

  public void destroy (WebView webView) {
    if (script != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) script.remove();
    script = null;
    if (installed && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(webView, MiniAppBridgeTransport.OBJECT_NAME);
    installed = false;
  }
}
