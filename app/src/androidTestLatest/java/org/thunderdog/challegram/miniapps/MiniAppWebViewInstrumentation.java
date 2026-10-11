/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Device contract checks: real Chromium frames and storage, no account/network/bot actions. */
public final class MiniAppWebViewInstrumentation extends Instrumentation {
  private static final String ORIGIN = "https://miniapp.test";
  private final List<WebView> views = new ArrayList<>();
  private final List<MiniAppPlatform> bridges = new ArrayList<>();
  private final List<String> profiles = new ArrayList<>();
  private int passed;
  private final String runId = Long.toHexString(System.nanoTime());
  private boolean cleanupOnly;
  private boolean hostChecks;
  private boolean rendererChecks;
  private boolean stage45Checks, serverChecks;

  @Override public void onCreate (Bundle arguments) {
    super.onCreate(arguments);
    cleanupOnly = arguments != null && "true".equals(arguments.getString("cleanup"));
    hostChecks = arguments != null && "true".equals(arguments.getString("host"));
    rendererChecks = arguments != null && "true".equals(arguments.getString("renderer"));
    stage45Checks = arguments != null && "true".equals(arguments.getString("stage45"));
    serverChecks = arguments != null && "true".equals(arguments.getString("server"));
    start();
  }

  @Override public void onStart () {
    Bundle result = new Bundle();
    int resultCode = Activity.RESULT_OK;
    try {
      check(MiniAppPlatform.isSupported(), "provider supports isolated secure bridge");
      runOnMainSync(() -> {
        for (String name : ProfileStore.getInstance().getAllProfileNames()) {
          if (name.startsWith("miniapp-contract-") || name.startsWith("tg-miniapp-" + (Long.MAX_VALUE - 110) + "-") ||
            name.startsWith("tg-miniapp-" + (Long.MAX_VALUE - 111) + "-") || name.contains("-" + (Long.MAX_VALUE - 104) + "-")) ProfileStore.getInstance().deleteProfile(name);
        }
      });
      if (stage45Checks || serverChecks) {
        new MiniAppStage45Checks(this).run(serverChecks);
      } else if (rendererChecks) {
        // A separate process avoids terminating the renderer of unrelated WebView fixtures.
        new MiniAppHostDeviceChecks(this).run(true);
      } else if (!cleanupOnly) {
        checkFrames(true);
        checkFrames(false);
        checkStorage();
        checkNativeStorage();
        checkLogoutIsolation();
        if (hostChecks) new MiniAppHostDeviceChecks(this).run();
      }
      result.putString("stream", "Mini Apps WebView: " + passed + " checks passed\n");
      result.putInt("passed", passed);
    } catch (Throwable error) {
      result.putString("stream", "Mini Apps WebView FAILED: " + error + "\n");
      result.putInt("passed", passed);
      resultCode = Activity.RESULT_CANCELED;
    } finally {
      runOnMainSync(() -> {
        for (int i = 0; i < views.size(); i++) {
          bridges.get(i).destroy(views.get(i));
          views.get(i).destroy();
        }
        // Profiles loaded by Chromium may only be deleted in a fresh process.
        // Run with -e cleanup true afterwards to remove these synthetic fixtures.
      });
    }
    finish(resultCode, result);
  }

  private void checkFrames (boolean requireSameOrigin) throws Exception {
    CountDownLatch messages = new CountDownLatch(4);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    MiniAppDocument document = new MiniAppDocument(ORIGIN, requireSameOrigin);
    List<MiniAppDocument.Request> accepted = new ArrayList<>();
    WebView page = create("frames-" + requireSameOrigin, (data, origin) -> {
      try {
        MiniAppDocument.Request request = document.receive(origin, ORIGIN, data.length());
        if (data.contains("opaque")) {
          check(request == null, "opaque sandboxed iframe rejected");
        } else if (data.contains("foreign")) {
          check((request != null) == !requireSameOrigin, "cross-origin policy " + requireSameOrigin);
        } else {
          check(request != null, "same-origin main/iframe accepted");
        }
        if (request != null) accepted.add(request);
      } catch (Throwable error) { failure.set(error); }
      messages.countDown();
    });
    runOnMainSync(() -> page.loadUrl(ORIGIN + "/frames"));
    await(messages, "messages from four real frames");
    if (failure.get() != null) throw new AssertionError(failure.get());
    runOnMainSync(() -> {
      for (MiniAppDocument.Request request : accepted) check(document.isCurrent(request, ORIGIN), "current reply accepted");
      document.navigate();
      for (MiniAppDocument.Request request : accepted) check(!document.isCurrent(request, ORIGIN), "stale reply rejected after navigation");
      check(document.receive("null", ORIGIN, 10) == null, "opaque origin rejected");
      check(document.receive(ORIGIN, ORIGIN, MiniAppDocument.MAX_MESSAGE_LENGTH + 1) == null, "oversized payload rejected");
      document.close();
      check(document.receive(ORIGIN, ORIGIN, 10) == null, "closed document rejected");
    });
  }

  private void checkStorage () throws Exception {
    WebView a = create("account-a-bot-a", (data, origin) -> { });
    WebView same = create("account-a-bot-a", (data, origin) -> { });
    WebView accountB = create("account-b-bot-a", (data, origin) -> { });
    WebView botB = create("account-a-bot-b", (data, origin) -> { });
    for (WebView view : new WebView[] { a, same, accountB, botB }) load(view, ORIGIN + "/storage");
    evaluate(a, "localStorage.setItem('scope','owner-a');document.cookie='scope=owner-a;path=/;Secure;SameSite=Lax';'ok'");
    check(evaluate(same, "localStorage.getItem('scope')").equals("\"owner-a\""), "same owner storage persists across views");
    check(evaluate(same, "document.cookie").contains("scope=owner-a"), "same owner cookies persist");
    for (WebView view : new WebView[] { accountB, botB }) {
      check(evaluate(view, "localStorage.getItem('scope')").equals("null"), "other owner cannot read localStorage");
      check(!evaluate(view, "document.cookie").contains("scope=owner-a"), "other owner cannot read cookies");
    }
    check(!MiniAppPlatform.profileName(1, 23).equals(MiniAppPlatform.profileName(12, 3)), "profile identifiers cannot collide");
  }

  private WebView create (String name, MiniAppBridgeTransport receiver) {
    AtomicReference<WebView> created = new AtomicReference<>();
    runOnMainSync(() -> {
      WebView view = new WebView(getTargetContext());
      String profile = "miniapp-contract-" + runId + "-" + name;
      WebViewCompat.setProfile(view, profile);
      if (!profiles.contains(profile)) profiles.add(profile);
      view.getSettings().setJavaScriptEnabled(true);
      view.getSettings().setDomStorageEnabled(true);
      MiniAppPlatform bridge = new MiniAppPlatform();
      check(bridge.install(view, receiver), "bridge installed");
      view.setWebViewClient(client(null));
      views.add(view);
      bridges.add(bridge);
      created.set(view);
    });
    return created.get();
  }

  private WebViewClient client (CountDownLatch loaded) {
    return new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest (WebView view, WebResourceRequest request) {
        String path = request.getUrl().getPath();
        String host = request.getUrl().getHost();
        String html = "<!doctype html><meta charset='utf-8'>";
        if ("/frames".equals(path)) {
          html += "<script>TelegramWebviewProxy.postEvent('main','{}')</script>" +
            "<iframe src='https://miniapp.test/frame'></iframe><iframe src='https://foreign.test/frame'></iframe>" +
            "<iframe sandbox='allow-scripts' src='https://miniapp.test/opaque'></iframe>";
        } else if ("/frame".equals(path)) {
          html += "<script>TelegramWebviewProxy.postEvent('" + ("foreign.test".equals(host) ? "foreign" : "same") + "','{}')</script>";
        } else if ("/opaque".equals(path)) {
          html += "<script>TelegramWebviewProxy.postEvent('opaque','{}')</script>";
        }
        return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
      }
      @Override public void onPageFinished (WebView view, String url) { if (loaded != null) loaded.countDown(); }
    };
  }

  private void load (WebView view, String url) throws Exception {
    CountDownLatch loaded = new CountDownLatch(1);
    runOnMainSync(() -> { view.setWebViewClient(client(loaded)); view.loadUrl(url); });
    await(loaded, "fixture load");
  }

  String evaluate (WebView view, String script) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<String> value = new AtomicReference<>();
    runOnMainSync(() -> view.evaluateJavascript(script, result -> { value.set(result); done.countDown(); }));
    await(done, "JavaScript result");
    return value.get();
  }

  void check (boolean valid, String message) {
    if (!valid) throw new AssertionError(message);
    passed++;
    Bundle progress = new Bundle(); progress.putString("stream", "PASS " + message + "\n"); sendStatus(0, progress);
  }

  private void checkNativeStorage () {
    long a = Long.MAX_VALUE - 101, b = Long.MAX_VALUE - 102, bot = Long.MAX_VALUE - 103;
    for (boolean secure : new boolean[] {false, true}) {
      MiniAppStorage one = new MiniAppStorage(getTargetContext(), a, bot, secure);
      MiniAppStorage other = new MiniAppStorage(getTargetContext(), b, bot, secure);
      MiniAppStorage otherBot = new MiniAppStorage(getTargetContext(), a, bot - 1, secure);
      MiniAppStorage testServer = new MiniAppStorage(getTargetContext(), a, bot, secure, true);
      try {
        one.clear(); other.clear(); otherBot.clear(); testServer.clear();
        one.set("probe", "fixture-secret-value");
        check("fixture-secret-value".equals(new MiniAppStorage(getTargetContext(), a, bot, secure).get("probe")), "native storage persists " + secure);
        check(other.get("probe") == null && otherBot.get("probe") == null && testServer.get("probe") == null, "native storage owner/bot/server isolation " + secure);
        one.set("probe", null); check(one.get("probe") == null, "null removes storage key " + secure);
        one.set("probe", "fixture-secret-value");
        if (secure) {
          java.util.Set<Long> active = new java.util.HashSet<>(); active.add(a);
          check(other.restorable("probe", active).isEmpty(), "active account secure storage cannot be restored");
          active.clear();
          check(other.restorable("probe", active).contains(a), "signed-out data is eligible for consent");
          check("fixture-secret-value".equals(other.restore(a, "probe", active)), "confirmed restore transfers encrypted data");
          check(one.get("probe") == null, "restore revokes prior owner");
          other.clear();
          for (int i = 0; i < 10; i++) other.set("key" + i, "value");
          boolean rejected = false;
          try { other.set("eleventh", "value"); } catch (IllegalStateException e) { rejected = "QUOTA_EXCEEDED".equals(e.getMessage()); }
          check(rejected && other.get("eleventh") == null, "secure ten-key quota is atomic");
        }
      } finally { one.clear(); other.clear(); otherBot.clear(); testServer.clear(); }
    }
  }

  private void checkLogoutIsolation () throws Exception {
    long owner = Long.MAX_VALUE - 110, other = Long.MAX_VALUE - 111;
    WebView a = isolated(owner), b = isolated(other);
    load(a, ORIGIN + "/storage"); load(b, ORIGIN + "/storage");
    evaluate(a, "localStorage.setItem('logout','retired');document.cookie='logout=retired;path=/;Secure';'ok'");
    evaluate(b, "localStorage.setItem('logout','other');'ok'");
    runOnMainSync(() -> MiniAppPlatform.clearAccount(getTargetContext(), owner, false));
    WebView next = isolated(owner); load(next, ORIGIN + "/storage");
    check("null".equals(evaluate(next, "localStorage.getItem('logout')")), "logout rotates the production profile before reuse");
    check(!evaluate(next, "document.cookie").contains("retired"), "logged-out cookies never leak into a fresh profile");
    check("\"other\"".equals(evaluate(b, "localStorage.getItem('logout')")), "logout preserves other accounts");
  }

  private WebView isolated (long owner) {
    AtomicReference<WebView> result = new AtomicReference<>();
    runOnMainSync(() -> {
      WebView view = new WebView(getTargetContext());
      check(MiniAppPlatform.isolate(view, owner, 1), "production profile selected before settings");
      view.getSettings().setJavaScriptEnabled(true); view.getSettings().setDomStorageEnabled(true);
      MiniAppPlatform bridge = new MiniAppPlatform();
      bridge.install(view, (data, origin) -> { });
      views.add(view); bridges.add(bridge); result.set(view);
    });
    return result.get();
  }

  private static void await (CountDownLatch latch, String operation) throws InterruptedException {
    if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Timeout: " + operation);
  }
}
