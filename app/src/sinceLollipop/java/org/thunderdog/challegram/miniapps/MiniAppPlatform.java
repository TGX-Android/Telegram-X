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
  private final Set<androidx.webkit.JavaScriptReplyProxy> mediaFrames = new HashSet<>();
  private boolean mediaPaused;
  private static final String MEDIA_BRIDGE = "TelegramWebviewMediaLifecycle";
  // Each frame tracks only locally captured streams. Pausing one host must not use
  // WebView.pauseTimers(), which would also freeze other Mini Apps and browsers.
  private static final String MEDIA_SCRIPT = "(function(){const b=window." + MEDIA_BRIDGE + ";if(!b||b.installed)return;b.installed=true;"
    + "const tracks=new Set();let paused=false;const stop=()=>{tracks.forEach(t=>t.stop());tracks.clear()};"
    + "const track=s=>{if(paused){s.getTracks().forEach(t=>t.stop());throw new DOMException('Mini App is paused','AbortError')}s.getTracks().forEach(t=>tracks.add(t));b.postMessage('ready');return s};"
    + "b.onmessage=e=>{if(e.data==='resume'){paused=false}else{if(e.data==='pause')paused=true;stop()}};"
    + "const m=navigator.mediaDevices;if(m&&m.getUserMedia){const get=m.getUserMedia.bind(m);m.getUserMedia=(...a)=>get(...a).then(track)};"
    + "['getUserMedia','webkitGetUserMedia'].forEach(k=>{if(typeof navigator[k]==='function'){const get=navigator[k].bind(navigator);navigator[k]=(c,ok,fail)=>get(c,s=>{try{ok(track(s))}catch(e){if(fail)fail(e)}},fail)}});"
    + "if(window.MediaStreamTrack){const clone=MediaStreamTrack.prototype.clone;MediaStreamTrack.prototype.clone=function(){const t=clone.call(this);if(tracks.has(this))tracks.add(t);return t}};"
    + "if(window.MediaStream){const clone=MediaStream.prototype.clone;MediaStream.prototype.clone=function(){const s=clone.call(this);return this.getTracks().some(t=>tracks.has(t))?track(s):s}};"
    + "addEventListener('pagehide',stop);addEventListener('DOMContentLoaded',()=>b.postMessage('ready'));b.postMessage('ready')})();";

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
    if (accountUserId > 0) {
      MiniAppStorage.clearAccount(context, accountUserId, testServer);
      String owner = (testServer ? "test-" : "production-") + accountUserId + "-";
      java.io.File directory = new java.io.File(context.getApplicationInfo().dataDir, "shared_prefs");
      java.io.File[] preferences = directory.listFiles();
      if (preferences != null) for (java.io.File file : preferences) {
        String name = file.getName();
        if ((name.startsWith("miniapp-biometry-" + owner) || name.startsWith("miniapp-location-" + owner)) && name.endsWith(".xml"))
          context.getSharedPreferences(name.substring(0, name.length() - 4), Context.MODE_PRIVATE).edit().clear().apply();
      }
      MiniAppStorage.IO.execute(() -> {
        try {
          java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
          java.util.Enumeration<String> aliases = keys.aliases();
          while (aliases.hasMoreElements()) { String name = aliases.nextElement(); if (name.startsWith("miniapp-biometry-" + owner)) keys.deleteEntry(name); }
        } catch (Exception ignored) { }
      });
    }
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
    WebViewCompat.addWebMessageListener(webView, MEDIA_BRIDGE, Collections.singleton("*"), (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
      if (message.getType() == WebMessageCompat.TYPE_STRING && "ready".equals(message.getData())) {
        if (mediaFrames.size() < 256) mediaFrames.add(replyProxy);
        if (mediaPaused) replyProxy.postMessage("pause");
      }
    });
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
      script = WebViewCompat.addDocumentStartJavaScript(webView, MiniAppBridgeTransport.SCRIPT + MEDIA_SCRIPT, Collections.singleton("*"));
    }
    return true;
  }

  public void onPageStarted (WebView webView) {
    mediaFrames.clear();
    if (installed && script == null) webView.evaluateJavascript(MiniAppBridgeTransport.SCRIPT + MEDIA_SCRIPT, null);
  }

  public void setMediaPaused (boolean paused) { mediaPaused = paused; mediaCommand(paused ? "pause" : "resume"); }
  public void stopMedia () { mediaCommand("stop"); }
  private void mediaCommand (String command) {
    for (androidx.webkit.JavaScriptReplyProxy frame : new HashSet<>(mediaFrames)) {
      try { frame.postMessage(command); } catch (RuntimeException stale) { mediaFrames.remove(frame); }
    }
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
    stopMedia(); mediaFrames.clear();
    if (script != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) script.remove();
    script = null;
    if (installed && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
      WebViewCompat.removeWebMessageListener(webView, MiniAppBridgeTransport.OBJECT_NAME);
      WebViewCompat.removeWebMessageListener(webView, MEDIA_BRIDGE);
    }
    installed = false;
  }
}
