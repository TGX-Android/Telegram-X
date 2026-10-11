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

  void run () throws Exception { run(false); }

  void run (boolean rendererLoss) throws Exception {
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
      createFixture();
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
      for (String position : new String[] {"left", "right", "top", "bottom"}) {
        send("web_app_setup_secondary_button", "{\"is_visible\":true,\"is_active\":true,\"text\":\"Previous\",\"position\":\"" + position + "\"}");
        Thread.sleep(120);
        View primary = field(buttons, "main"), secondary = field(buttons, "secondary");
        boolean vertical = "top".equals(position) || "bottom".equals(position);
        boolean before = "top".equals(position) || "left".equals(position);
        boolean actualBefore = vertical ? secondary.getTop() < primary.getTop() : secondary.getLeft() < primary.getLeft();
        test.check(actualBefore == before && (vertical ? primary.getWidth() == secondary.getWidth() : primary.getWidth() > 0 && secondary.getWidth() > 0),
          "secondary button position " + position);
      }
      send("web_app_setup_main_button", "{\"is_visible\":true,\"is_active\":false,\"text\":\"Disabled\"}");
      Thread.sleep(100);
      main(() -> ((View) field(buttons, "main")).performClick());
      test.check("false".equals(test.evaluate(web, "events.some(e=>e.event==='main_button_pressed')")), "inactive native button cannot invoke the app");
      send("web_app_setup_main_button", "{\"is_visible\":true,\"is_active\":true,\"text\":\"Continue\",\"has_shine_effect\":true,\"is_progress_visible\":true}");
      Thread.sleep(100);
      main(() -> ((View) field(buttons, "main")).performClick()); waitEvent("main_button_pressed");
      test.check(((View) field(field(buttons, "main"), "progress")).getVisibility() == View.VISIBLE, "native progress indicator is shown");
      capture("host-shine-progress");
      send("web_app_setup_main_button", "{\"is_visible\":true,\"is_active\":true,\"text\":\"Continue\"}");
      send("web_app_setup_secondary_button", "{\"is_visible\":true,\"is_active\":true,\"text\":\"Previous\",\"position\":\"top\"}");
      Thread.sleep(120);
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
      Thread.sleep(100);
      main(() -> {
        long time = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, Screen.dp(20), Screen.dp(3), 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(time, time + 50, android.view.MotionEvent.ACTION_UP, Screen.dp(20), Screen.dp(3), 0);
        controls.dispatchTouchEvent(down); controls.dispatchTouchEvent(up); down.recycle(); up.recycle();
      });
      waitEvent("back_button_pressed");
      test.check(true, "fullscreen capsule hit target extends beyond visible 30dp pill");
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
      main(() -> {
        int[] point = new int[2]; buttons.getLocationOnScreen(point);
        androidx.core.graphics.Insets insets = androidx.core.view.ViewCompat.getRootWindowInsets(root).getInsetsIgnoringVisibility(
          androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
        test.check(point[0] >= insets.left && point[0] + buttons.getWidth() <= activity.getRootView().getWidth() - insets.right,
          "landscape native buttons avoid both side cutouts");
      });
      capture("host-fullscreen-landscape");
      main(() -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)); Thread.sleep(1000);
      send("web_app_device_storage_save_key", "{\"req_id\":\"save\",\"key\":\"fixture\",\"value\":\"ok\"}");
      waitEvent("device_storage_key_saved");
      send("web_app_device_storage_get_key", "{\"req_id\":\"get\",\"key\":\"fixture\"}");
      waitEvent("device_storage_key_received");
      test.check(test.evaluate(web, "JSON.stringify(events.filter(e=>e.event==='device_storage_key_received').slice(-1)[0].data)").contains("ok"), "Device Storage replies pass through the real bridge");
      send("web_app_device_storage_clear", "{\"req_id\":\"clear\"}"); waitEvent("device_storage_cleared");
      checkSecureRestoreConsent();
      checkLaunchConsent();
      for (String sensor : new String[] {"accelerometer", "gyroscope", "device_orientation"}) {
        send("web_app_start_" + sensor, "{\"refresh_rate\":20}");
        waitEvent(sensor + "_started"); waitEvent(sensor + "_changed");
        test.check(true, sensor + " emits real hardware data through bridge");
      }
      send("web_app_start_device_orientation", "{\"refresh_rate\":20,\"need_absolute\":true}");
      Thread.sleep(400);
      test.check(test.evaluate(web, "events.filter(e=>e.event==='device_orientation_changed').slice(-1)[0].data.absolute").equals("true"),
        "absolute orientation combines real accelerometer and magnetometer");
      main(host::onActivityPause); Thread.sleep(100);
      String paused = sensorCount(); Thread.sleep(250);
      test.check(paused.equals(sensorCount()), "activity pause unregisters sensors and queued samples");
      main(host::onActivityResume); Thread.sleep(300);
      test.check(!paused.equals(sensorCount()), "activity resume restores sensor subscriptions");
      send("web_app_exit_fullscreen", "{}"); Thread.sleep(300);
      main(host::minimize); Thread.sleep(500);
      test.check(host.isMinimized(), "minimize retains the session");
      String minimized = sensorCount(); Thread.sleep(250);
      test.check(minimized.equals(sensorCount()), "minimized Mini App receives no motion samples");
      main(host::restore); Thread.sleep(700);
      test.check(host.isShowing() && web == field(host, "webView"), "restore retains the same renderer");
      test.check("\"ok\"".equals(test.evaluate(web, "'ok'")), "restored renderer responds");
      test.check(!minimized.equals(sensorCount()), "restoring Mini App resumes sensors without a new start");
      for (String sensor : new String[] {"accelerometer", "gyroscope", "device_orientation"}) {
        send("web_app_stop_" + sensor, "{}"); waitEvent(sensor + "_stopped");
      }
      String stopped = sensorCount(); Thread.sleep(250);
      test.check(stopped.equals(sensorCount()), "stop cancels all sensor listeners and delayed samples");
      capture("host-restored");
      checkThemeChange();
      if (rendererLoss) {
        main(() -> test.check(web.getWebViewRenderProcess() != null && web.getWebViewRenderProcess().terminate(), "fixture renderer termination requested"));
        for (int i = 0; i < 40 && !host.isDestroyed(); i++) Thread.sleep(100);
        test.check(host.isDestroyed(), "renderer loss closes the broken controller without crashing the app");
        createFixture(); waitPage(); Thread.sleep(400);
        test.check(!host.isDestroyed() && "\"alive\"".equals(test.evaluate(web, "'alive'")), "Mini App can reopen after renderer loss");
        capture("host-renderer-recovered");
      }
      checkMinimizedClose();
    } finally {
      main(() -> { if (host != null) host.close(); activity.setRequestedOrientation(previousOrientation); });
      test.check(lightStatusBar() == org.thunderdog.challegram.theme.Theme.needLightStatusBar(), "closing a Mini App restores native status icon contrast");
    }
  }

  private void createFixture () {
    main(() -> {
      Tdlib tdlib = activity.currentTdlib();
      MiniAppLaunchRequest request = new MiniAppLaunchRequest(MiniAppLaunchRequest.Source.MAIN_APP, Long.MAX_VALUE - 104, 0,
        "https://miniapp.test/host", "", "", "", null, null, new TdApi.WebAppOpenModeCompact(), false);
      host = new MiniAppController(activity, tdlib, request, null);
      host.getValue(); web = field(host, "webView");
      set(host, "document", new MiniAppDocument("https://miniapp.test/host", true));
      WebViewClient original = web.getWebViewClient();
      web.setWebViewClient(new WebViewClient() {
        @Override public WebResourceResponse shouldInterceptRequest (WebView view, WebResourceRequest request) {
          String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'>" +
            "<style>body{background:#17212b;color:white;font:16px sans-serif;margin:0;padding:70px 20px}input{font-size:20px}</style>" +
            "<h1>Mini App device fixture</h1><input id='entry' placeholder='Keyboard check'><p>Native shell / viewport / storage</p>" +
            "<script>window.events=[];window.Telegram={WebView:{receiveEvent:function(n,d){events.push({event:n,data:d});" +
            "if(n==='theme_changed'){document.body.style.background=d.theme_params.bg_color;document.body.style.color=d.theme_params.text_color}}}};" +
            "function send(n,d){TelegramWebviewProxy.postEvent(n,JSON.stringify(d||{}))}send('web_app_ready');</script>";
          return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        }
        @Override public void onPageStarted (WebView view, String url, Bitmap icon) { original.onPageStarted(view, url, icon); }
        @Override public void onPageFinished (WebView view, String url) { original.onPageFinished(view, url); }
        @Override public boolean onRenderProcessGone (WebView view, android.webkit.RenderProcessGoneDetail detail) { return original.onRenderProcessGone(view, detail); }
      });
      invoke(host, "showSheet"); web.loadUrl("https://miniapp.test/host");
    });
  }

  private void checkMinimizedClose () throws Exception {
    main(host::minimize); Thread.sleep(400);
    main(() -> host.onMessage("{\"eventType\":\"web_app_close\"}", "https://foreign.test"));
    test.check(!host.isDestroyed(), "a foreign origin cannot close a minimized Mini App");
    boolean backVisible = field(host, "backVisible");
    send("web_app_setup_back_button", "{\"is_visible\":" + !backVisible + "}"); Thread.sleep(150);
    test.check(backVisible == (boolean) field(host, "backVisible"), "minimized Mini App still rejects other bridge actions");
    test.evaluate(web, "setTimeout(()=>send('web_app_close'),150); 'close scheduled'");
    for (int i = 0; i < 40 && !host.isDestroyed(); i++) Thread.sleep(100);
    test.check(host.isDestroyed(), "delayed close through the real bridge destroys a minimized Mini App");
  }

  private void checkThemeChange () throws Exception {
    org.thunderdog.challegram.theme.ThemeManager manager = org.thunderdog.challegram.theme.ThemeManager.instance();
    org.thunderdog.challegram.theme.ThemeDelegate original = manager.currentTheme(false);
    org.thunderdog.challegram.unsorted.Settings settings = org.thunderdog.challegram.unsorted.Settings.instance();
    int autoMode = settings.getNightMode();
    Integer oldHeader = field(host, "headerColor"), oldBottom = field(host, "bottomColor");
    try {
      main(() -> manager.changeGlobalTheme(activity.currentTdlib(), manager.getTheme(activity.currentTdlib().settings().globalDaylightTheme()), true, null));
      Thread.sleep(450);
      test.check(!manager.currentTheme(false).isDark(), "native shell changes to saved daylight theme");
      String color = test.evaluate(web, "events.filter(e=>e.event==='theme_changed').slice(-1)[0].data.theme_params.bg_color");
      test.check(color.equals("\"" + MiniAppTheme.hex(org.thunderdog.challegram.theme.Theme.fillingColor()) + "\""), "new theme reaches existing page through bridge");
      test.check(lightStatusBar(), "light Mini App header uses dark status icons");
      capture("host-light-theme");
      send("web_app_set_header_color", "{\"color\":\"#101010\"}");
      send("web_app_set_bottom_bar_color", "{\"color\":\"#101010\"}"); Thread.sleep(200);
      test.check(!lightStatusBar() && !lightNavigationBar(), "dark app overrides use light system icons in a daylight chat theme");
      capture("host-custom-dark-bars");
      send("web_app_set_header_color", "{\"color\":\"#ffffff\"}");
      send("web_app_set_bottom_bar_color", "{\"color\":\"#ffffff\"}"); Thread.sleep(200);
      test.check(lightStatusBar() && lightNavigationBar(), "light app overrides use dark system icons");
      capture("host-custom-light-bars");
      main(host::minimize); Thread.sleep(500);
      test.check(lightStatusBar() == org.thunderdog.challegram.theme.Theme.needLightStatusBar(), "minimizing restores the underlying chat status icons");
      main(host::restore); Thread.sleep(700);
      test.check(lightStatusBar() && lightNavigationBar(), "restoring reapplies Mini App system icon contrast");
    } finally {
      main(() -> {
        set(host, "headerColor", oldHeader); set(host, "bottomColor", oldBottom);
        manager.changeGlobalTheme(activity.currentTdlib(), original, true, null);
        invoke(host, "applyTheme");
      });
      Thread.sleep(450);
      test.check(manager.currentThemeId() == original.getId() && settings.getNightMode() == autoMode, "original theme and automatic night mode are preserved");
    }
  }

  private boolean lightStatusBar () {
    return (activity.getWindow().getInsetsController().getSystemBarsAppearance() & android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS) != 0;
  }

  private boolean lightNavigationBar () {
    return (activity.getWindow().getInsetsController().getSystemBarsAppearance() & android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) != 0;
  }

  private String sensorCount () throws Exception {
    return test.evaluate(web, "events.filter(e=>['accelerometer_changed','gyroscope_changed','device_orientation_changed'].includes(e.event)).length");
  }

  private void checkLaunchConsent () throws Exception {
    final android.app.AlertDialog[] consent = new android.app.AlertDialog[1];
    final java.util.concurrent.atomic.AtomicInteger accepted = new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger cancelled = new java.util.concurrent.atomic.AtomicInteger();
    main(() -> consent[0] = MiniAppLaunchDialog.show(host, Long.MAX_VALUE - 104, false,
      org.thunderdog.challegram.R.string.BotStart, write -> accepted.incrementAndGet(), cancelled::incrementAndGet));
    Thread.sleep(200);
    main(() -> consent[0].getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick());
    Thread.sleep(200);
    test.check(accepted.get() == 0 && cancelled.get() == 1, "launch consent cancel does not authorize a launch");
  }

  private void checkSecureRestoreConsent () throws Exception {
    long bot = Long.MAX_VALUE - 104, previous = Long.MAX_VALUE - 105;
    boolean server = activity.currentTdlib().account().isDebug();
    MiniAppStorage old = new MiniAppStorage(activity, previous, bot, true, server);
    MiniAppStorage target = new MiniAppStorage(activity, Long.MAX_VALUE - 106, bot, true, server);
    try {
      old.clear(); target.clear(); old.setOwnerName("Synthetic signed-out account"); old.set("fixture", "synthetic-restore-value");
      restoreFixture(target, previous, "restore_cancel");
      android.app.AlertDialog dialog = waitDialog();
      test.check(target.get("fixture") == null, "secure restore waits for native user consent");
      main(() -> dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick());
      waitEvent("secure_storage_failed");
      test.check(target.get("fixture") == null && old.get("fixture") != null, "cancelling restore preserves both storage owners");
      restoreFixture(target, previous, "restore_accept");
      android.app.AlertDialog confirmed = waitDialog();
      main(() -> confirmed.getListView().performItemClick(confirmed.getListView().getChildAt(0), 0, 0));
      waitEvent("secure_storage_key_restored");
      test.check("synthetic-restore-value".equals(target.get("fixture")) && old.get("fixture") == null,
        "native restore confirmation transfers only selected synthetic storage");
    } finally { old.clear(); target.clear(); }
  }

  private android.app.AlertDialog waitDialog () throws Exception {
    for (int i = 0; i < 40; i++) {
      android.app.AlertDialog dialog = field(host, "activeDialog");
      if (dialog != null && dialog.isShowing()) return dialog;
      Thread.sleep(100);
    }
    throw new AssertionError("Native consent did not open");
  }

  private void restoreFixture (MiniAppStorage store, long previous, String id) {
    main(() -> {
      try {
        // Account-wide restore must NEVER reassign the real signed-in user's identity.
        Method method = MiniAppController.class.getDeclaredMethod("restoreStorage", MiniAppDocument.Request.class, String.class, String.class, MiniAppStorage.class, java.util.List.class, String[].class);
        method.setAccessible(true);
        method.invoke(host, host.webRequest(web.getUrl()), id, "fixture", store, java.util.Collections.singletonList(previous), new String[] {"Synthetic signed-out account"});
      } catch (Exception error) { throw new AssertionError(error); }
    });
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
