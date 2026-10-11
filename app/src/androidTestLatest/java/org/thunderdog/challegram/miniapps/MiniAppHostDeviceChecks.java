/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the production controller. Fixtures and reflection exist only in the test APK. */
final class MiniAppHostDeviceChecks {
  private final MiniAppWebViewInstrumentation test;
  private MiniAppController host;
  private WebView web;
  private BaseActivity activity;
  MiniAppHostDeviceChecks (MiniAppWebViewInstrumentation test) { this.test = test; }

  void run () throws Exception {
    Intent intent = new Intent(test.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    test.startActivitySync(intent);
    for (int i = 0; i < 100; i++) {
      main(() -> activity = UI.getUiContext());
      if (activity != null && activity.currentTdlib().myUserId() != 0 && activity.getRootView().getHeight() > 0) break;
      Thread.sleep(100);
    }
    test.check(activity != null && activity.currentTdlib().myUserId() != 0, "host has an initialized account");
    final int previousOrientation = activity.getRequestedOrientation();
    try {
      main(() -> {
        Tdlib tdlib = activity.currentTdlib();
        MiniAppLaunchRequest request = new MiniAppLaunchRequest(MiniAppLaunchRequest.Source.MAIN_APP, Long.MAX_VALUE - 104, 0,
          "https://miniapp.test/host", "", "", "", null, null, new TdApi.WebAppOpenModeCompact(), false);
        host = new MiniAppController(activity, tdlib, request, null);
        host.getValue();
        web = field(host, "webView");
        set(host, "document", new MiniAppDocument("https://miniapp.test/host", true));
        WebViewClient original = web.getWebViewClient();
        web.setWebViewClient(new WebViewClient() {
          @Override public WebResourceResponse shouldInterceptRequest (WebView view, WebResourceRequest request) {
            String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'>" +
              "<style>body{background:#17212b;color:white;font:16px sans-serif;margin:0;padding:70px 20px}input{font-size:20px}</style>" +
              "<h1>Mini App device fixture</h1><input id='entry' placeholder='Keyboard check'><p>Native shell / viewport / storage</p>" +
              "<script>window.events=[];window.Telegram={WebView:{receiveEvent:function(n,d){events.push({event:n,data:d})}}};" +
              "function send(n,d){TelegramWebviewProxy.postEvent(n,JSON.stringify(d||{}))}send('web_app_ready');</script>";
            return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
          }
          @Override public void onPageStarted (WebView view, String url, Bitmap icon) { original.onPageStarted(view, url, icon); }
          @Override public void onPageFinished (WebView view, String url) { original.onPageFinished(view, url); }
        });
        invoke(host, "showSheet");
        web.loadUrl("https://miniapp.test/host");
      });
      waitPage();
      Thread.sleep(700);
      View root = field(host, "root");
      int compactHeight = root.getHeight();
      test.check(compactHeight < activity.getRootView().getHeight() * .85f, "explicit compact mode keeps compact height");
      send("web_app_expand", "{}"); Thread.sleep(400);
      test.check(root.getHeight() > compactHeight, "expand animates to available height");
      main(() -> {
        int[] position = new int[2]; web.getLocationOnScreen(position);
        int status = Math.max(Screen.getStatusBarHeight(), activity.getRootView().getSystemInsetsWithoutIme().top);
        test.check(position[1] >= status + Screen.dp(56), "normal viewport begins below status bar and header");
      });
      send("web_app_setup_main_button", "{\"is_visible\":true,\"is_active\":true,\"text\":\"Continue\"}");
      send("web_app_setup_secondary_button", "{\"is_visible\":true,\"is_active\":true,\"text\":\"Previous\",\"position\":\"top\"}");
      Thread.sleep(300);
      View buttons = field(host, "buttons");
      test.check(buttons.getHeight() >= Screen.dp(98), "vertical native buttons reserve viewport height");
      capture("host-expanded-buttons");
      send("web_app_request_fullscreen", "{}"); Thread.sleep(500);
      View header = field(host, "header"), controls = field(host, "fullscreenControls");
      test.check(header.getVisibility() == View.GONE && controls.getVisibility() == View.VISIBLE, "fullscreen replaces header with floating capsules");
      main(() -> {
        int[] point = new int[2]; buttons.getLocationOnScreen(point);
        int nav = activity.getRootView().getSystemInsetsWithoutIme().bottom;
        test.check(point[1] + buttons.getHeight() <= activity.getRootView().getHeight() - nav, "native fullscreen buttons stay above navigation area");
      });
      test.check(test.evaluate(web, "JSON.stringify(events.filter(e=>e.event==='content_safe_area_changed').slice(-1)[0].data)").contains("46"), "fullscreen content inset follows 46dp capsules");
      send("web_app_setup_back_button", "{\"is_visible\":true}");
      capture("host-fullscreen-portrait");
      int beforeKeyboard = web.getHeight();
      main(() -> { web.requestFocus(); ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(web, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); });
      test.evaluate(web, "document.getElementById('entry').focus()");
      Thread.sleep(800);
      test.check(web.getHeight() < beforeKeyboard - Screen.dp(100), "visible IME reduces the actual web viewport");
      main(() -> {
        int[] point = new int[2]; buttons.getLocationOnScreen(point);
        org.thunderdog.challegram.widget.PopupLayout sheet = field(host, "popup");
        test.check(point[1] + buttons.getHeight() <= sheet.getHeight() - sheet.getSystemInsets().bottom, "native buttons stay above the IME");
      });
      capture("host-keyboard");
      send("web_app_hide_keyboard", "{}"); Thread.sleep(400);
      main(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); Thread.sleep(1200);
      test.check(root.getWidth() > root.getHeight(), "fullscreen host follows landscape rotation");
      capture("host-fullscreen-landscape");
      main(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)); Thread.sleep(1000);
      send("web_app_device_storage_save_key", "{\"req_id\":\"save\",\"key\":\"fixture\",\"value\":\"ok\"}");
      waitEvent("device_storage_key_saved");
      send("web_app_device_storage_get_key", "{\"req_id\":\"get\",\"key\":\"fixture\"}");
      waitEvent("device_storage_key_received");
      test.check(test.evaluate(web, "JSON.stringify(events.filter(e=>e.event==='device_storage_key_received').slice(-1)[0].data)").contains("ok"), "Device Storage replies pass through the real bridge");
      send("web_app_device_storage_clear", "{\"req_id\":\"clear\"}"); waitEvent("device_storage_cleared");
      send("web_app_exit_fullscreen", "{}"); Thread.sleep(300);
      main(host::minimize); Thread.sleep(500);
      test.check(host.isMinimized(), "minimize retains the session");
      main(host::restore); Thread.sleep(700);
      test.check(host.isShowing() && web == field(host, "webView"), "restore retains the same renderer");
      test.check("\"ok\"".equals(test.evaluate(web, "'ok'")), "restored renderer responds");
      capture("host-restored");
    } finally {
      main(() -> { if (host != null) host.close(); activity.setRequestedOrientation(previousOrientation); });
    }
  }

  private void waitPage () throws Exception {
    for (int i = 0; i < 60; i++) {
      if ("true".equals(test.evaluate(web, "typeof send==='function'"))) return;
      Thread.sleep(100);
    }
    throw new AssertionError("Host fixture did not load");
  }
  private void send (String event, String data) throws Exception { test.evaluate(web, "send('" + event + "'," + data + ")"); }
  private void waitEvent (String event) throws Exception {
    for (int i = 0; i < 40; i++) {
      if ("true".equals(test.evaluate(web, "events.some(e=>e.event==='" + event + "')"))) return;
      Thread.sleep(100);
    }
    throw new AssertionError("Missing event " + event);
  }
  private void capture (String name) throws Exception {
    Bitmap image = test.getUiAutomation().takeScreenshot();
    File directory = new File(test.getTargetContext().getExternalFilesDir(null), "miniapp-contracts");
    if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create fixture directory");
    try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, stream); }
    finally { image.recycle(); }
  }
  private void main (Runnable task) {
    AtomicReference<Throwable> error = new AtomicReference<>();
    test.runOnMainSync(() -> { try { task.run(); } catch (Throwable e) { error.set(e); } });
    if (error.get() != null) throw new AssertionError(error.get());
  }
  @SuppressWarnings("unchecked") private static <T> T field (Object object, String name) {
    try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(object); }
    catch (Exception e) { throw new AssertionError(e); }
  }
  private static void set (Object object, String name, Object value) {
    try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
    catch (Exception e) { throw new AssertionError(e); }
  }
  private static void invoke (Object object, String name) {
    try { Method m = object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object); }
    catch (Exception e) { throw new AssertionError(e); }
  }
}
