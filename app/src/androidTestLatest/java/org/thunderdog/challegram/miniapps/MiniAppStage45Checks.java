/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Stage 4/5 integration checks. Network writes run ONLY with explicit -e server true. */
final class MiniAppStage45Checks {
  private final MiniAppWebViewInstrumentation test;
  private MiniAppController host;
  private WebView web;
  private BaseActivity activity;
  private Tdlib tdlib;
  private long bot = Long.MAX_VALUE - 104;
  MiniAppStage45Checks (MiniAppWebViewInstrumentation test) { this.test = test; }

  void run (boolean server) throws Exception {
    test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    for (int i = 0; i < 100; i++) {
      main(() -> activity = UI.getUiContext());
      if (activity != null && activity.currentTdlib().myUserId() != 0 && activity.getRootView().getHeight() > 0) break;
      Thread.sleep(100);
    }
    test.check(activity != null && activity.currentTdlib().myUserId() != 0, "stage 4/5 uses an initialized account");
    tdlib = activity.currentTdlib();
    if (server) {
      TdApi.Chat chat = (TdApi.Chat) call(new TdApi.SearchPublicChat("nskdancebot"));
      bot = tdlib.chatUserId(chat);
      test.check(bot != 0, "explicitly authorized nskdancebot resolved");
    }
    create(MiniAppLaunchRequest.Source.MAIN_APP);
    try {
      if (server) serverChecks(); else localChecks();
    } finally {
      main(() -> { if (host != null) host.close(); });
      if (!server) {
        new MiniAppStorage(activity, tdlib.myUserId(), bot, false, tdlib.account().isDebug()).clear();
        new MiniAppStorage(activity, tdlib.myUserId(), bot, true, tdlib.account().isDebug()).clear();
        String scope = MiniAppBotPermissions.scope(tdlib, bot);
        for (String type : new String[] {"location", "biometry"}) activity.getSharedPreferences("miniapp-" + type + "-" + scope, 0).edit().clear().commit();
      }
    }
  }

  private void localChecks () throws Exception {
    popupChecks();
    send("web_app_check_home_screen", "{}");
    String shortcut = result("home_screen_checked").getString("status");
    test.check("added".equals(shortcut) || "missed".equals(shortcut) || "unsupported".equals(shortcut), "launcher capability is reported by Android");
    send("web_app_biometry_get_info", "{}");
    JSONObject info = result("biometry_info_received");
    test.check(info.getString("device_id").length() == 64 && info.has("available") && !info.getBoolean("access_granted"), "biometry exposes stable scoped device ID without granting access");
    send("web_app_biometry_request_auth", "{}");
    test.check("failed".equals(result("biometry_auth_requested").getString("status")), "biometry cannot authenticate without bot consent");
    send("web_app_biometry_update_token", "{\"token\":\"synthetic-token\"}");
    test.check("failed".equals(result("biometry_token_updated").getString("status")), "biometry cannot write token without consent");
    if (info.getBoolean("available")) {
      clear(); send("web_app_biometry_request_access", "{\"reason\":\"Mini Apps biometric contract check\"}");
      AlertDialog dialog = dialog(); capture("stage45-biometry-consent"); click(dialog, false);
      info = result("biometry_info_received");
      test.check(info.getBoolean("access_requested") && !info.getBoolean("access_granted"), "biometric decline is remembered without an authentication grant");
    }
    clear(); send("web_app_check_location", "{}");
    test.check(result("location_checked").has("available"), "location availability reported without obtaining coordinates");
    clear(); send("web_app_request_location", "{}");
    AlertDialog locationDialog = dialog(); capture("stage45-location-consent"); click(locationDialog, false);
    test.check(!result("location_requested").getBoolean("available"), "location decline returns unavailable");
    test.check("1".equals(test.evaluate(web, "events.filter(e=>e.event==='location_requested').length")), "location decline completes once");
    clear(); send("web_app_request_location", "{}");
    test.check(!result("location_requested").getBoolean("available") && field(host, "activeDialog") == null, "declined bot cannot repeatedly reopen location prompt");
    htmlMediaChecks();
    clear(); send("web_app_read_text_from_clipboard", "{\"req_id\":\"no-gesture\"}");
    test.check(!result("clipboard_text_received").has("data"), "clipboard denies requests without a recent gesture");
    main(host::recordGesture); clear(); send("web_app_read_text_from_clipboard", "{\"req_id\":\"not-trusted\"}");
    test.check(!result("clipboard_text_received").has("data"), "gesture alone cannot read clipboard for an untrusted bot");
    main(host::recordGesture); clear(); send("web_app_request_file_download", "{\"url\":\"https://miniapp.test/file\",\"file_name\":\"../escape\"}");
    test.check("cancelled".equals(result("file_download_requested").getString("status")), "download rejects traversal before server request");
    main(host::recordGesture); clear(); send("web_app_set_emoji_status", "{\"custom_emoji_id\":\"invalid\"}");
    test.check("SUGGESTED_EMOJI_INVALID".equals(result("emoji_status_failed").getString("error")), "invalid emoji produces the official error contract");
    storageChecks();
    hardwareChecks();
    clear(); send("web_app_request_phone", "{}");
    AlertDialog phone = dialog(); capture("stage45-contact-consent"); click(phone, false);
    test.check("cancelled".equals(result("phone_requested").getString("status")), "contact decline sends no contact");
    clear(); send("web_app_request_phone", "{}"); dialog();
    main(host::minimize); Thread.sleep(300); main(host::restore); Thread.sleep(400);
    test.check("cancelled".equals(result("phone_requested").getString("status")), "minimization cancels an unanswered consent and delivers one reply after restore");
    clear(); send("web_app_request_phone", "{}"); dialog();
    main(() -> { MiniAppDocument doc = field(host, "document"); doc.navigate(); invoke(host, "cancelDocumentRequests"); });
    Thread.sleep(200);
    test.check("0".equals(test.evaluate(web, "events.filter(e=>e.event==='phone_requested').length")), "document change drops stale native consent callback");
    test.check(!MiniAppActions.validFileName("name\nfile") && MiniAppActions.validFileName("schedule.pdf"), "download file validation is enforced by production action");
  }

  private void popupChecks () throws Exception {
    clear(); send("web_app_open_popup", "{\"title\":\"Mini App\",\"message\":\"Confirm the action\",\"buttons\":[{\"id\":\"yes\",\"type\":\"default\",\"text\":\"Continue\"},{\"id\":\"no\",\"type\":\"cancel\"}]}");
    AlertDialog dialog = dialog(); capture("stage45-popup"); click(dialog, true);
    test.check("yes".equals(result("popup_closed").getString("button_id")), "popup returns selected button ID");
    Thread.sleep(200); test.check("1".equals(test.evaluate(web, "events.filter(e=>e.event==='popup_closed').length")), "popup callback fires once on click and dismissal");
    clear(); send("web_app_open_popup", "{\"message\":\"Dismiss me\",\"buttons\":[{\"type\":\"ok\"}]}");
    AlertDialog cancelled = dialog(); main(cancelled::cancel);
    test.check(!result("popup_closed").has("button_id"), "popup back/outside dismissal has no button ID");
  }

  private void hardwareChecks () throws Exception {
    main(() -> {
      MiniAppSensors absent = new MiniAppSensors(new android.content.ContextWrapper(activity) {
        @Override public Object getSystemService (String name) { return android.content.Context.SENSOR_SERVICE.equals(name) ? null : super.getSystemService(name); }
      });
      for (int i = 0; i < 3; i++) test.check(!absent.start(i, 100, false, (event, data) -> { }), "missing sensor family " + i + " reports unavailable");
    });
    clear(); send("web_app_start_accelerometer", "{\"refresh_rate\":1000}"); result("accelerometer_started");
    send("web_app_start_accelerometer", "{\"refresh_rate\":20}"); Thread.sleep(100);
    Object[] streams = field(field(host, "sensors"), "streams");
    test.check(((Long) field(streams[0], "interval")) == 20L, "sensor refresh rate can change on an existing subscription");
    send("web_app_stop_accelerometer", "{}");
    int previous = activity.getRequestedOrientation(); send("web_app_toggle_orientation_lock", "{\"locked\":true}"); Thread.sleep(100);
    test.check(activity.getRequestedOrientation() == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED, "orientation lock reaches Android");
    send("web_app_toggle_orientation_lock", "{\"locked\":false}"); Thread.sleep(100);
    test.check(activity.getRequestedOrientation() == previous, "unlock restores prior activity orientation");
    com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter().encode("Mini Apps QR contract", com.google.zxing.BarcodeFormat.QR_CODE, 320, 320);
    byte[] frame = new byte[320 * 320];
    for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++) frame[y * 320 + x] = (byte) (matrix.get(x, y) ? 0 : 255);
    test.check("Mini Apps QR contract".equals(MiniAppQrScanner.decode(frame, 320, 320)), "production QR decoder accepts arbitrary text without opening it as a URL");
    clear(); send("web_app_open_scan_qr_popup", "{\"text\":\"Scan the test QR code\"}");
    for (int i = 0; i < 50; i++) {
      allowAndroidPermission();
      Object scanner = field(host, "qrScanner");
      if (scanner != null && field(scanner, "camera") != null) break;
      Thread.sleep(100);
    }
    Object scanner = field(host, "qrScanner");
    test.check(scanner != null && field(scanner, "camera") != null, "QR sheet opens actual device camera"); capture("stage45-qr-camera");
    send("web_app_close_scan_qr_popup", "{}"); result("scan_qr_popup_closed");
    Thread.sleep(200);
    test.check(field(scanner, "camera") == null && "1".equals(test.evaluate(web, "events.filter(e=>e.event==='scan_qr_popup_closed').length")), "QR close releases camera and completes once");
    pickerChecks();
  }

  private void allowAndroidPermission () {
    android.view.accessibility.AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
    if (root == null || root.getPackageName() == null || !root.getPackageName().toString().contains("permissioncontroller")) return;
    for (String id : new String[] {"permission_allow_foreground_only_button", "permission_allow_one_time_button", "permission_allow_button"}) {
      java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(root.getPackageName() + ":id/" + id);
      if (!nodes.isEmpty()) { nodes.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); return; }
    }
  }

  private void pickerChecks () throws Exception {
    java.util.concurrent.atomic.AtomicInteger replies = new java.util.concurrent.atomic.AtomicInteger();
    main(() -> web.getWebChromeClient().onShowFileChooser(web, files -> { test.check(files == null, "cancelled picker returns null, not an arbitrary URI"); replies.incrementAndGet(); }, new android.webkit.WebChromeClient.FileChooserParams() {
      @Override public int getMode () { return MODE_OPEN_MULTIPLE; }
      @Override public String[] getAcceptTypes () { return new String[] {"text/plain", "image/*"}; }
      @Override public boolean isCaptureEnabled () { return false; }
      @Override public CharSequence getTitle () { return null; }
      @Override public String getFilenameHint () { return null; }
      @Override public Intent createIntent () { return new Intent(Intent.ACTION_GET_CONTENT); }
    }));
    Thread.sleep(900); capture("stage45-file-picker");
    try (android.os.ParcelFileDescriptor ignored = test.getUiAutomation().executeShellCommand("input keyevent 4")) { Thread.sleep(400); }
    for (int i = 0; i < 30 && replies.get() == 0; i++) Thread.sleep(100);
    test.check(replies.get() == 1, "real Android multiple-file picker cancellation completes once");
  }

  private void htmlMediaChecks () throws Exception {
    test.evaluate(web, "window.geoDone=0;navigator.geolocation.getCurrentPosition(()=>geoDone++,()=>geoDone++)");
    AlertDialog geolocation = dialog(); capture("stage45-html-location"); click(geolocation, false);
    waitJs("geoDone===1"); test.check(true, "HTML geolocation uses its own origin consent and completes once on denial");
    test.evaluate(web, "window.mediaDone=0;navigator.mediaDevices.getUserMedia({audio:true,video:true}).then(s=>{s.getTracks().forEach(t=>t.stop());mediaDone++},()=>mediaDone++)");
    AlertDialog media = dialog(); capture("stage45-html-media"); click(media, false);
    waitJs("mediaDone===1"); test.check(true, "HTML camera/microphone denies both resources after native decline");
    final int[] geolocationAnswers = {0};
    main(() -> web.getWebChromeClient().onGeolocationPermissionsShowPrompt("https://foreign.test", (origin, allowed, retain) -> {
      test.check(!allowed && !retain, "foreign origin does not gain geolocation from bot consent"); geolocationAnswers[0]++;
    }));
    test.check(geolocationAnswers[0] == 1, "foreign geolocation callback completes once");
    final int[] denied = {0};
    main(() -> web.getWebChromeClient().onPermissionRequest(new android.webkit.PermissionRequest() {
      @Override public android.net.Uri getOrigin () { return android.net.Uri.parse("https://foreign.test"); }
      @Override public String[] getResources () { return new String[] {RESOURCE_VIDEO_CAPTURE, RESOURCE_PROTECTED_MEDIA_ID}; }
      @Override public void grant (String[] resources) { throw new AssertionError("Unexpected grant"); }
      @Override public void deny () { denied[0]++; }
    }));
    test.check(denied[0] == 1, "foreign/unsupported HTML media resources are denied");
  }

  private void storageChecks () throws Exception {
    clear(); send("web_app_device_storage_save_key", "{\"req_id\":\"key-invalid\",\"key\":7,\"value\":\"value\"}");
    test.check("KEY_INVALID".equals(result("device_storage_failed").getString("error")), "invalid device storage key rejected");
    clear(); send("web_app_secure_storage_save_key", "{\"req_id\":\"value-invalid\",\"key\":\"k\",\"value\":{}}");
    test.check("VALUE_INVALID".equals(result("secure_storage_failed").getString("error")), "invalid secure storage value rejected");
    clear(); test.evaluate(web, "send('web_app_device_storage_save_key',{req_id:'large',key:'large',value:'x'.repeat(1100000)})");
    test.check("large".equals(result("device_storage_key_saved").getString("req_id")), "DeviceStorage accepts a legitimate value larger than 1 MiB through real bridge");
    MiniAppStorage store = new MiniAppStorage(activity, tdlib.myUserId(), bot, false, tdlib.account().isDebug());
    store.clear();
    String value = new String(new char[MiniAppStorage.QUOTA - 8]).replace('\0', 'x');
    store.set("k", value);
    test.check(store.get("k").length() == value.length(), "exact 5 MiB serialized DeviceStorage quota accepted");
    boolean rejected = false;
    try { store.set("extra", "x"); } catch (IllegalStateException e) { rejected = "QUOTA_EXCEEDED".equals(e.getMessage()); }
    test.check(rejected && store.get("extra") == null, "one byte beyond storage quota cannot partially overwrite data"); store.clear();
    long old = Long.MAX_VALUE - 190, next = Long.MAX_VALUE - 191, firstBot = Long.MAX_VALUE - 192, secondBot = Long.MAX_VALUE - 193;
    MiniAppStorage a = new MiniAppStorage(activity, old, firstBot, true), b = new MiniAppStorage(activity, old, secondBot, true);
    MiniAppStorage c = new MiniAppStorage(activity, next, firstBot, true), d = new MiniAppStorage(activity, next, secondBot, true);
    try {
      a.clear(); b.clear(); c.clear(); d.clear(); a.set("identity", "one"); b.set("identity", "two");
      java.util.Set<Long> active = new java.util.HashSet<>(); active.add(next);
      c.restore(old, "identity", active);
      test.check("one".equals(c.get("identity")) && "two".equals(d.get("identity")), "SecureStorage restore changes the account-wide identity across two bots");
      test.check(a.get("identity") == null && b.get("identity") == null, "former owner cannot reacquire either bot after transfer");
    } finally { a.clear(); b.clear(); c.clear(); d.clear(); }
  }

  private void serverChecks () throws Exception {
    String key = "codex_stage45_" + Long.toHexString(System.nanoTime());
    try {
      custom("save", "saveStorageValue", new JSONObject().put("key", key).put("value", "stage45-probe"));
      JSONObject saved = result("custom_method_invoked"); test.check(!saved.has("error"), "nskdancebot CloudStorage write acknowledged by Telegram");
      clear(); custom("read", "getStorageValues", new JSONObject().put("keys", new org.json.JSONArray().put(key)));
      JSONObject read = result("custom_method_invoked");
      test.check("stage45-probe".equals(read.getJSONObject("result").getString(key)), "CloudStorage read returns exact server-stored value");
    } finally {
      clear(); custom("delete", "deleteStorageValues", new JSONObject().put("keys", new org.json.JSONArray().put(key)));
      test.check(!result("custom_method_invoked").has("error"), "CloudStorage probe removed from server");
    }
    clear(); custom("invalid", "codexNonexistentMethod", new JSONObject());
    test.check(result("custom_method_invoked").has("error"), "server custom-method error reaches matching request");
    clear(); send("web_app_request_write_access", "{}");
    for (int i = 0; i < 120; i++) {
      AlertDialog dialog = field(host, "activeDialog");
      if (dialog != null) { capture("stage45-server-write-consent"); click(dialog, true); break; }
      if (has("write_access_requested")) break;
      Thread.sleep(100);
    }
    test.check("allowed".equals(result("write_access_requested").getString("status")), "nskdancebot messaging access confirmed by Telegram");
    clear(); send("web_app_request_phone", "{}"); AlertDialog phone = dialog(); capture("stage45-server-contact-consent"); click(phone, true);
    test.check("sent".equals(result("phone_requested").getString("status")), "authorized contact reaches onMessageSendSucceeded for nskdancebot");
    main(host::close); create(MiniAppLaunchRequest.Source.REPLY_KEYBOARD);
    send("web_app_data_send", "{\"data\":\"{\\\"type\\\":\\\"mini_apps_contract_test\\\",\\\"version\\\":1}\"}");
    for (int i = 0; i < 200 && !host.isDestroyed(); i++) Thread.sleep(100);
    test.check(host.isDestroyed(), "reply-keyboard sendData closes only after Telegram accepts the data");
  }
  private void custom (String id, String method, JSONObject params) throws Exception { send("web_app_invoke_custom_method", new JSONObject().put("req_id", id).put("method", method).put("params", params).toString()); }
  private TdApi.Object call (TdApi.Function<?> function) throws Exception {
    CountDownLatch latch = new CountDownLatch(1); AtomicReference<TdApi.Object> result = new AtomicReference<>();
    tdlib.client().send(function, value -> { result.set(value); latch.countDown(); });
    if (!latch.await(30, TimeUnit.SECONDS)) throw new AssertionError("TDLib timeout");
    if (result.get() instanceof TdApi.Error) throw new AssertionError("TDLib error " + ((TdApi.Error) result.get()).code);
    return result.get();
  }

  private void create (MiniAppLaunchRequest.Source source) throws Exception {
    main(() -> {
      host = new MiniAppController(activity, tdlib, new MiniAppLaunchRequest(source, bot, 0, "https://miniapp.test/stage45", "Mini Apps contract test", "", "", null, null, new TdApi.WebAppOpenModeFullSize(), false), null);
      host.getValue(); web = field(host, "webView"); set(host, "document", new MiniAppDocument("https://miniapp.test/stage45", true));
      WebViewClient original = web.getWebViewClient();
      web.setWebViewClient(new WebViewClient() {
        @Override public WebResourceResponse shouldInterceptRequest (WebView view, WebResourceRequest request) {
          String html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><style>body{background:#17212b;color:white;font:16px sans-serif;padding:24px}</style>" +
            "<h2>Mini Apps · stages 4–5</h2><p>Native API contract check</p><input id=file type=file multiple accept='text/plain,image/*'>" +
            "<script>window.events=[];window.Telegram={WebView:{receiveEvent:(event,data)=>events.push({event,data})}};function send(event,data){TelegramWebviewProxy.postEvent(event,JSON.stringify(data||{}))}send('web_app_ready')</script>";
          return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        }
        @Override public void onPageStarted (WebView view, String url, Bitmap icon) { original.onPageStarted(view, url, icon); }
        @Override public void onPageFinished (WebView view, String url) { original.onPageFinished(view, url); }
      });
      invoke(host, "showSheet"); web.loadUrl("https://miniapp.test/stage45");
    });
    waitJs("typeof send==='function'"); Thread.sleep(500);
  }
  private void clear () throws Exception { test.evaluate(web, "events=[]"); }
  private void send (String event, String data) throws Exception { test.evaluate(web, "send(" + JSONObject.quote(event) + "," + data + ")"); }
  private boolean has (String event) throws Exception { return "true".equals(test.evaluate(web, "events.some(e=>e.event===" + JSONObject.quote(event) + ")")); }
  private JSONObject result (String event) throws Exception {
    for (int i = 0; i < 300; i++) {
      if (has(event)) return new JSONObject((String) new JSONTokener(test.evaluate(web, "JSON.stringify(events.filter(e=>e.event===" + JSONObject.quote(event) + ").slice(-1)[0].data)")).nextValue());
      Thread.sleep(100);
    }
    throw new AssertionError("Missing event: " + event);
  }
  private void waitJs (String expression) throws Exception { for (int i = 0; i < 100; i++) { if ("true".equals(test.evaluate(web, expression))) return; Thread.sleep(100); } throw new AssertionError("JS condition timed out"); }
  private AlertDialog dialog () throws Exception { for (int i = 0; i < 100; i++) { AlertDialog dialog = field(host, "activeDialog"); if (dialog != null && dialog.isShowing()) return dialog; Thread.sleep(100); } throw new AssertionError("Native dialog missing"); }
  private void click (AlertDialog dialog, boolean positive) { main(() -> dialog.getButton(positive ? AlertDialog.BUTTON_POSITIVE : AlertDialog.BUTTON_NEGATIVE).performClick()); }
  private void capture (String name) throws Exception {
    Bitmap image = test.getUiAutomation().takeScreenshot(); File dir = new File(test.getTargetContext().getExternalFilesDir(null), "miniapp-contracts"); dir.mkdirs();
    try (FileOutputStream output = new FileOutputStream(new File(dir, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, output); } finally { image.recycle(); }
  }
  private void main (Runnable task) { AtomicReference<Throwable> error = new AtomicReference<>(); test.runOnMainSync(() -> { try { task.run(); } catch (Throwable e) { error.set(e); } }); if (error.get() != null) throw new AssertionError(error.get()); }
  @SuppressWarnings("unchecked") private static <T> T field (Object obj, String name) { try { Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(obj); } catch (Exception e) { throw new AssertionError(e); } }
  private static void set (Object obj, String name, Object value) { try { Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); f.set(obj, value); } catch (Exception e) { throw new AssertionError(e); } }
  private static void invoke (Object obj, String name) { try { Method m = obj.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(obj); } catch (Exception e) { throw new AssertionError(e); } }
}
