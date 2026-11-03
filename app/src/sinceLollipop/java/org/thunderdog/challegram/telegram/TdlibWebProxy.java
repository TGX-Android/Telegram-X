/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/10/2026
 */
package org.thunderdog.challegram.telegram;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.SparseArray;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.UiThread;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class TdlibWebProxy implements UI.StateListener {
  private static final int MAX_CARRIERS = 8;
  private static final int MAX_TEMPORARY_CARRIERS = 4;
  private static final int MAX_MESSAGE_BYTES = 1024 * 1024 + 8;
  private static final int MAX_QUEUED_BYTES = 4 * 1024 * 1024;
  private static final int MAX_ENCODED_LENGTH = ((MAX_MESSAGE_BYTES + 2) / 3) * 4 + 1;
  private static final String NATIVE_BRIDGE = "TgxWebProxyNative";
  private static final String PROFILE_PREFIX = "tgx_web_proxy_";
  private static boolean cleanedProfiles;
  // Profile deletion can fail until WebView releases it; retry on the main thread.
  private static final List<String> pendingProfileDeletions = new ArrayList<>();

  private final Client client;
  private final Runnable onResume;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final AtomicInteger queuedUpdateBytes = new AtomicInteger();
  private final SparseArray<Carrier> carriers = new SparseArray<>();
  private volatile boolean destroyed;
  private String bridgeScript;
  private boolean waitingForResume;

  TdlibWebProxy (Client client, Runnable onResume) {
    this.client = client;
    this.onResume = onResume;
    handler.post(() -> {
      if (!destroyed) {
        UI.addStateListener(this);
      }
    });
  }

  private void trace (int carrierId, String event, long value) {
    if (BuildConfig.DEBUG) {
      Log.i(
        "WebProxy client=%x carrier=%d t=%d event=%s value=%d",
        System.identityHashCode(client),
        carrierId,
        SystemClock.elapsedRealtime(),
        event,
        value
      );
    }
  }

  @Override
  public void onUiStateChanged (int newState) {
    if (destroyed) {
      return;
    }
    trace(0, "ui-state", newState);
    if (newState != UI.State.RESUMED) {
      waitingForResume = true;
      return;
    }
    if (waitingForResume) {
      waitingForResume = false;
      // Background WebViews can retain a carrier whose transport has stopped responding.
      while (carriers.size() > 0) {
        carriers.valueAt(0).fail(new TdApi.WebProxyCarrierErrorTerminated());
      }
      trace(0, "network-recheck", 0);
      onResume.run();
    }
  }

  void open (TdApi.WebProxyCarrier info) {
    long requestedAt = SystemClock.elapsedRealtime();
    trace(info.id, "open-request", info.isTemporary ? 1 : 0);
    handler.post(() -> {
      trace(info.id, "open-dispatch-ms", SystemClock.elapsedRealtime() - requestedAt);
      if (destroyed) {
        return;
      }
      if (carriers.get(info.id) != null) {
        return;
      }
      if (!UI.isResumed()) {
        waitingForResume = true;
        report(info.id, new TdApi.WebProxyCarrierErrorPaused());
        return;
      }
      int temporaryCount = 0;
      for (int i = 0; i < carriers.size(); i++) {
        if (carriers.valueAt(i).info.isTemporary) {
          temporaryCount++;
        }
      }
      if (info.isTemporary && temporaryCount >= MAX_TEMPORARY_CARRIERS) {
        report(info.id, new TdApi.WebProxyCarrierErrorRefused());
        return;
      }
      if (carriers.size() >= MAX_CARRIERS) {
        report(info.id, info.isTemporary ? new TdApi.WebProxyCarrierErrorRefused() :
          new TdApi.WebProxyCarrierErrorPaused());
        return;
      }
      Carrier carrier = new Carrier(info);
      carriers.put(info.id, carrier);
      try {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
          carrier.fail(new TdApi.WebProxyCarrierErrorUnsupported());
          return;
        }
        if (bridgeScript == null) {
          try (InputStream input = AppContext.get().getAssets().open("web_proxy.js")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int size;
            while ((size = input.read(buffer)) != -1) {
              output.write(buffer, 0, size);
            }
            bridgeScript = output.toString("UTF-8");
          }
        }
        carrier.start();
      } catch (Throwable ignored) {
        // Provider exceptions can include the capability URL. Never log them.
        carrier.fail(new TdApi.WebProxyCarrierErrorLoadFailed("Relay initialization failed"));
      }
    });
  }

  void data (int carrierId, byte[] data) {
    if (destroyed) {
      return;
    }
    if (queuedUpdateBytes.addAndGet(data.length) > 4 * MAX_QUEUED_BYTES) {
      queuedUpdateBytes.addAndGet(-data.length);
      handler.post(() -> {
        Carrier carrier = carriers.get(carrierId);
        if (carrier != null) {
          carrier.fail(new TdApi.WebProxyCarrierErrorTerminated());
        }
      });
      return;
    }
    handler.post(() -> {
      queuedUpdateBytes.addAndGet(-data.length);
      Carrier carrier = carriers.get(carrierId);
      if (!destroyed && carrier != null) {
        carrier.send(data);
      }
    });
  }

  void close (int carrierId) {
    handler.post(() -> {
      Carrier carrier = carriers.get(carrierId);
      if (carrier != null) {
        carrier.close(true);
      }
    });
  }

  void destroy () {
    destroyed = true;
    handler.post(() -> {
      UI.removeStateListener(this);
      while (carriers.size() > 0) {
        carriers.valueAt(0).close(true);
      }
    });
  }

  private void report (int id, TdApi.WebProxyCarrierError error) {
    trace(id, error.getClass().getSimpleName(), 0);
    if (!destroyed) {
      client.send(new TdApi.ReportWebProxyCarrierError(id, error), ignored -> { });
    }
  }

  private static void deleteProfile (String name) {
    try {
      ProfileStore.getInstance().deleteProfile(name);
    } catch (Throwable ignored) {
      if (!pendingProfileDeletions.contains(name)) {
        pendingProfileDeletions.add(name);
      }
    }
  }

  private static void retryProfileDeletions () {
    if (pendingProfileDeletions.isEmpty()) {
      return;
    }
    List<String> existingNames = ProfileStore.getInstance().getAllProfileNames();
    for (int i = pendingProfileDeletions.size() - 1; i >= 0; i--) {
      String name = pendingProfileDeletions.get(i);
      if (existingNames.contains(name)) {
        try {
          ProfileStore.getInstance().deleteProfile(name);
          pendingProfileDeletions.remove(i);
        } catch (Throwable ignored) {
        }
      } else {
        pendingProfileDeletions.remove(i);
      }
    }
  }

  @UiThread
  private final class Carrier {
    final TdApi.WebProxyCarrier info;
    final Uri origin;
    final ArrayDeque<byte[]> pending = new ArrayDeque<>();
    WebView view;
    JavaScriptReplyProxy reply;
    String profileName;
    int pendingBytes;
    final long createdAt = SystemClock.elapsedRealtime();
    long sentMessages, receivedMessages;
    boolean closed, hasPageMessage, ready, pageStarted;
    final Runnable loadTimeout = () ->
      fail(new TdApi.WebProxyCarrierErrorLoadFailed("Relay load timed out"));

    Carrier (TdApi.WebProxyCarrier info) {
      this.info = info;
      this.origin = Uri.parse(info.origin);
    }

    boolean isOrigin (Uri uri) {
      return "https".equals(uri.getScheme()) && uri.getUserInfo() == null &&
        origin.getHost() != null && origin.getHost().equalsIgnoreCase(uri.getHost()) &&
        (uri.getPort() == -1 || uri.getPort() == 443);
    }

    boolean allowed (Uri uri) {
      if (!isOrigin(uri)) {
        return false;
      }
      String rawPath = uri.getEncodedPath();
      if (rawPath == null) {
        return false;
      }
      String lowerPath = rawPath.toLowerCase(Locale.US);
      if (lowerPath.contains("%2e") || lowerPath.contains("%2f") ||
          lowerPath.contains("%5c") || rawPath.indexOf('\\') >= 0) {
        return false;
      }
      try {
        String path = new URI(null, null, uri.getPath(), null).normalize().getPath();
        return path.startsWith(info.pathPrefix);
      } catch (Exception ignored) {
        return false;
      }
    }

    @SuppressLint("SetJavaScriptEnabled")
    void start () {
      retryProfileDeletions();
      if (!"https".equals(origin.getScheme()) || !allowed(Uri.parse(info.url)) ||
          !info.pathPrefix.startsWith("/") || !info.pathPrefix.endsWith("/")) {
        fail(new TdApi.WebProxyCarrierErrorLoadFailed("Invalid relay origin or path"));
        return;
      }
      trace(info.id, "webview-create-ms", SystemClock.elapsedRealtime() - createdAt);
      view = new WebView(AppContext.get());
      trace(info.id, "webview-created-ms", SystemClock.elapsedRealtime() - createdAt);
      // Separate from Mini Apps, login pages and other carriers. Delete after destroy.
      if (!cleanedProfiles) {
        cleanedProfiles = true;
        for (String name : ProfileStore.getInstance().getAllProfileNames()) {
          if (name.startsWith(PROFILE_PREFIX)) {
            deleteProfile(name);
          }
        }
      }
      profileName = PROFILE_PREFIX + UUID.randomUUID();
      WebViewCompat.setProfile(view, profileName);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        TdlibWebProxyServiceWorker.install(WebViewCompat.getProfile(view));
      }
      CookieManager cookies = WebViewCompat.getProfile(view).getCookieManager();
      cookies.setAcceptCookie(false);
      cookies.setAcceptThirdPartyCookies(view, false);
      WebSettings settings = view.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setAllowFileAccess(false);
      settings.setAllowContentAccess(false);
      settings.setAllowFileAccessFromFileURLs(false);
      settings.setAllowUniversalAccessFromFileURLs(false);
      settings.setDomStorageEnabled(false);
      settings.setDatabaseEnabled(false);
      settings.setGeolocationEnabled(false);
      settings.setSaveFormData(false);
      settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
      settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
      settings.setJavaScriptCanOpenWindowsAutomatically(false);
      settings.setSupportMultipleWindows(true);
      settings.setMediaPlaybackRequiresUserGesture(true);
      view.setDownloadListener((url, agent, disposition, mime, length) -> { });
      view.setWebChromeClient(new WebChromeClient() {
        @Override
        public boolean onConsoleMessage (ConsoleMessage message) {
          return true;
        }
        @Override
        public void onPermissionRequest (PermissionRequest request) {
          request.deny();
        }
        @Override
        public void onGeolocationPermissionsShowPrompt (
          String origin,
          GeolocationPermissions.Callback callback
        ) {
          callback.invoke(origin, false, false);
        }
        @Override
        public boolean onJsAlert (WebView view, String url, String message, JsResult result) {
          result.cancel();
          return true;
        }
        @Override
        public boolean onJsConfirm (WebView view, String url, String message, JsResult result) {
          result.cancel();
          return true;
        }
        @Override
        public boolean onJsPrompt (
          WebView view,
          String url,
          String message,
          String value,
          JsPromptResult result
        ) {
          result.cancel();
          return true;
        }
        @Override
        public boolean onJsBeforeUnload (
          WebView view,
          String url,
          String message,
          JsResult result
        ) {
          result.cancel();
          return true;
        }
        @Override
        public boolean onShowFileChooser (
          WebView view,
          ValueCallback<Uri[]> callback,
          FileChooserParams params
        ) {
          callback.onReceiveValue(null);
          return true;
        }
      });
      TdlibWebProxyClient.Callback clientCallback = new TdlibWebProxyClient.Callback() {
        @Override
        public boolean isAllowed (Uri uri) {
          return allowed(uri);
        }
        @Override
        public boolean isExpectedPage (String url) {
          Uri expected = Uri.parse(info.url).buildUpon().fragment(null).build();
          Uri actual = Uri.parse(url).buildUpon().fragment(null).build();
          trace(info.id, "page-start-ms", SystemClock.elapsedRealtime() - createdAt);
          boolean result = !pageStarted && expected.equals(actual);
          pageStarted = true;
          return result;
        }
        @Override
        public void onLoadFailed (String reason) {
          trace(info.id, reason, SystemClock.elapsedRealtime() - createdAt);
          fail(new TdApi.WebProxyCarrierErrorLoadFailed(reason));
        }
        @Override
        public void onTerminated () {
          fail(new TdApi.WebProxyCarrierErrorTerminated());
        }
      };
      view.setWebViewClient(
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
          new TdlibWebProxyOreoClient(clientCallback) : new TdlibWebProxyClient(clientCallback)
      );
      WebViewCompat.addWebMessageListener(
        view,
        NATIVE_BRIDGE,
        Collections.singleton(info.origin),
        (source, message, sourceOrigin, mainFrame, proxy) -> {
          if (closed || destroyed || source != view || !mainFrame || !isOrigin(sourceOrigin)) {
            return;
          }
          try {
            String value = message.getType() == WebMessageCompat.TYPE_STRING ?
              message.getData() : null;
            if (value == null || value.isEmpty() || value.length() > MAX_ENCODED_LENGTH) {
              fail(new TdApi.WebProxyCarrierErrorTerminated());
              return;
            }
            reply = proxy;
            if ("r".equals(value)) {
              handler.removeCallbacks(loadTimeout);
              trace(info.id, "bridge-ready-ms", SystemClock.elapsedRealtime() - createdAt);
              ready = true;
              while (!pending.isEmpty() && !closed) {
                sendNow(pending.removeFirst());
              }
              pendingBytes = 0;
              return;
            }
            TdApi.WebProxyCarrierMessage payload;
            if (value.charAt(0) == 'b') {
              byte[] bytes = Base64.decode(value.substring(1), Base64.NO_WRAP);
              if (bytes.length > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException();
              }
              payload = new TdApi.WebProxyCarrierMessageBinary(bytes);
            } else if (value.charAt(0) == 't') {
              payload = new TdApi.WebProxyCarrierMessageText(value.substring(1));
            } else {
              fail(new TdApi.WebProxyCarrierErrorTerminated());
              return;
            }
            if (receivedMessages++ == 0) {
              trace(info.id, "first-page-message-ms", SystemClock.elapsedRealtime() - createdAt);
            }
            hasPageMessage = true;
            handler.removeCallbacks(loadTimeout);
            client.send(new TdApi.ProcessWebProxyCarrierMessage(info.id, payload), ignored -> { });
          } catch (Throwable ignored) {
            fail(new TdApi.WebProxyCarrierErrorTerminated());
          }
        }
      );
      String script = "(" + bridgeScript + ")(" + JSONObject.quote(info.origin) + "," +
        JSONObject.quote(info.pathPrefix) + "," + JSONObject.quote(info.bridgeObjectName) + "," +
        JSONObject.quote(NATIVE_BRIDGE) + ");";
      WebViewCompat.addDocumentStartJavaScript(view, script, Collections.singleton(info.origin));
      handler.postDelayed(loadTimeout, 30000);
      trace(info.id, "load-url-ms", SystemClock.elapsedRealtime() - createdAt);
      view.loadUrl(info.url);
    }

    void send (byte[] data) {
      if (closed) {
        return;
      }
      if (data.length > MAX_MESSAGE_BYTES) {
        fail(new TdApi.WebProxyCarrierErrorTerminated());
      } else if (ready) {
        sendNow(data);
      } else if (pending.size() >= 64 || pendingBytes + data.length > MAX_QUEUED_BYTES) {
        fail(new TdApi.WebProxyCarrierErrorTerminated());
      } else {
        pending.addLast(data);
        pendingBytes += data.length;
      }
    }

    void sendNow (byte[] data) {
      try {
        if (sentMessages++ == 0) {
          trace(info.id, "first-tdlib-data-ms", SystemClock.elapsedRealtime() - createdAt);
        }
        reply.postMessage("b" + Base64.encodeToString(data, Base64.NO_WRAP));
      } catch (Throwable ignored) {
        fail(new TdApi.WebProxyCarrierErrorTerminated());
      }
    }

    void fail (TdApi.WebProxyCarrierError error) {
      if (closed) {
        return;
      }
      report(info.id, error);
      close(false);
    }

    void close (boolean graceful) {
      if (closed) {
        return;
      }
      trace(info.id, "close-age-ms", SystemClock.elapsedRealtime() - createdAt);
      trace(info.id, "messages-to-page", sentMessages);
      trace(info.id, "messages-from-page", receivedMessages);
      trace(info.id, "pending-bytes", pendingBytes);
      closed = true;
      carriers.remove(info.id);
      handler.removeCallbacks(loadTimeout);
      pending.clear();
      pendingBytes = 0;
      if (graceful && hasPageMessage && reply != null) {
        try {
          reply.postMessage("t{\"t\":\"close\"}");
          handler.postDelayed(this::dispose, 200);
          return;
        } catch (Throwable ignored) { }
      }
      dispose();
    }

    void dispose () {
      if (view != null) {
        try {
          view.stopLoading();
          WebViewCompat.removeWebMessageListener(view, NATIVE_BRIDGE);
          view.destroy();
        } catch (Throwable ignored) { }
        view = null;
      }
      reply = null;
      if (profileName != null) {
        deleteProfile(profileName);
        profileName = null;
      }
    }
  }
}
