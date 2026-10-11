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
import tgx.td.Td;
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

  void run (boolean server, String capability) throws Exception {
    test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    for (int i = 0; i < 100; i++) {
      main(() -> activity = UI.getUiContext());
      if (activity != null && activity.currentTdlib().myUserId() != 0 && activity.getRootView().getHeight() > 0) break;
      Thread.sleep(100);
    }
    test.check(activity != null && activity.currentTdlib().myUserId() != 0, "stage 4/5 uses an initialized account");
    tdlib = activity.currentTdlib();
    boolean realBot = server || "actions".equals(capability) || "emoji".equals(capability) || capability.startsWith("requested_chat") || capability.startsWith("prepared") || "managed_bot".equals(capability);
    if (realBot) {
      TdApi.Chat chat = (TdApi.Chat) call(new TdApi.SearchPublicChat("nskdancebot"));
      bot = tdlib.chatUserId(chat);
      test.check(bot != 0, "explicitly authorized nskdancebot resolved");
    }
    create(MiniAppLaunchRequest.Source.MAIN_APP);
    try {
      if (server) serverChecks();
      else if ("biometry".equals(capability)) biometricChecks();
      else if ("biometry_cancel".equals(capability)) biometricCancelChecks();
      else if ("hardware".equals(capability)) grantedHardwareChecks();
      else if ("optical_qr".equals(capability)) opticalQrChecks();
      else if ("capture".equals(capability)) fileSuccessChecks(true);
      else if ("file_selection".equals(capability)) fileSuccessChecks(false);
      else if ("requested_chat".equals(capability)) requestedChatChecks();
      else if ("requested_chat_server".equals(capability)) requestedChatServerChecks();
      else if ("managed_bot".equals(capability)) managedBotChecks();
      else if ("prepared".equals(capability)) preparedMessageChecks();
      else if ("prepared_server".equals(capability)) preparedMessageServerChecks();
      else if ("actions".equals(capability)) actionChecks();
      else if ("emoji".equals(capability)) authorizedEmojiChecks();
      else localChecks();
    } finally {
      main(() -> { if (host != null) host.close(); });
      if (!realBot) {
        new MiniAppStorage(activity, tdlib.myUserId(), bot, false, tdlib.account().isDebug()).clear();
        new MiniAppStorage(activity, tdlib.myUserId(), bot, true, tdlib.account().isDebug()).clear();
        String scope = MiniAppBotPermissions.scope(tdlib, bot);
        for (String type : new String[] {"location", "biometry"}) activity.getSharedPreferences("miniapp-" + type + "-" + scope, 0).edit().clear().commit();
        java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore"); keys.load(null); keys.deleteEntry("miniapp-biometry-" + scope);
      }
    }
  }

  private void biometricChecks () throws Exception {
    send("web_app_biometry_get_info", "{}");
    test.check(result("biometry_info_received").getBoolean("available"), "strong enrolled biometrics available on this phone");
    clear(); send("web_app_biometry_request_access", "{\"reason\":\"Mini Apps test: grant access\"}"); click(dialog(), true);
    test.check(biometricResult("biometry_info_received").getBoolean("access_granted"), "real biometric authentication grants the synthetic bot access");
    clear(); send("web_app_biometry_update_token", "{\"token\":\"synthetic-biometric-token\",\"reason\":\"Mini Apps test: protect test token\"}");
    test.check("updated".equals(biometricResult("biometry_token_updated").getString("status")), "Android Keystore encrypts token with authenticated cipher");
    clear(); send("web_app_biometry_request_auth", "{\"reason\":\"Mini Apps test: read test token\"}");
    JSONObject auth = biometricResult("biometry_auth_requested");
    test.check("authorized".equals(auth.getString("status")) && "synthetic-biometric-token".equals(auth.getString("token")), "authenticated cipher decrypts exact token through real bridge");
    clear(); send("web_app_biometry_update_token", "{\"token\":\"\",\"reason\":\"Mini Apps test: remove test token\"}");
    test.check("removed".equals(biometricResult("biometry_token_updated").getString("status")), "biometric token removal acknowledged after authentication");
    Thread.sleep(1000); biometricCancelChecks();
  }
  private void biometricCancelChecks () throws Exception {
    main(() -> { invoke(host, "biometry"); MiniAppBiometry bio = field(host, "biometry"); bio.setGranted(true); });
    clear(); send("web_app_biometry_request_auth", "{\"reason\":\"Mini Apps cancellation test\"}");
    for (int i = 0; i < 100 && field(field(host, "biometry"), "pending") == null; i++) Thread.sleep(100);
    test.check(field(field(host, "biometry"), "pending") != null, "actual biometric prompt is pending before lifecycle cancellation");
    main(host::minimize); Thread.sleep(400); main(host::restore); Thread.sleep(400);
    test.check("failed".equals(result("biometry_auth_requested").getString("status")), "minimization cancels authentication without a token");
  }
  private JSONObject biometricResult (String event) throws Exception {
    System.out.println("WAIT fingerprint on phone for " + event);
    for (int i = 0; i < 1800; i++) {
      if (has(event)) return result(event);
      Thread.sleep(100);
    }
    throw new AssertionError("Fingerprint not provided within three minutes");
  }

  private void grantedHardwareChecks () throws Exception {
    clear(); send("web_app_request_location", "{}"); click(dialog(), true);
    for (int i = 0; i < 200 && !has("location_requested"); i++) { allowAndroidPermission(); Thread.sleep(100); }
    JSONObject location = result("location_requested");
    test.check(location.getBoolean("available") && location.has("horizontal_accuracy"), "real LocationManager returns coordinates locally, never logged or transmitted");
    test.check(field(host.location(), "listener") == null && field(host.location(), "pending") == null, "location callback releases all listeners");
    main(() -> MiniAppBotPermissions.toggle(activity, tdlib, bot, false, () -> { }));
    clear(); send("web_app_request_location", "{}");
    test.check(!result("location_requested").getBoolean("available"), "revoking permission in bot profile blocks subsequent coordinates");
    android.location.Location noAccuracy = new android.location.Location("test");
    JSONObject empty = MiniAppLocation.locationObject(noAccuracy);
    test.check(empty.isNull("altitude") && empty.isNull("speed_accuracy"), "missing location accuracy fields remain null");
    test.evaluate(web, "window.captureResult='waiting';navigator.mediaDevices.getUserMedia({audio:true,video:true}).then(s=>{window.captureStream=s;captureResult='live'},e=>captureResult=e.name)");
    click(dialog(), true);
    for (int i = 0; i < 150; i++) { allowAndroidPermission(); if (!"\"waiting\"".equals(test.evaluate(web, "captureResult"))) break; Thread.sleep(100); }
    test.check("\"live\"".equals(test.evaluate(web, "captureResult")), "real WebRTC camera and microphone open only after native and Android consent");
    test.check("true".equals(test.evaluate(web, "captureStream.getTracks().length===2&&captureStream.getTracks().every(t=>t.readyState==='live')")), "both requested tracks are live");
    test.check("true".equals(test.evaluate(web, "!!TelegramWebviewMediaLifecycle.installed&&navigator.mediaDevices.getUserMedia.toString().includes('track')")), "document-start media lifecycle hook owns getUserMedia");
    test.check(!((java.util.Set<?>) field(field(host, "platform"), "mediaFrames")).isEmpty(), "capturing frame registered after navigation callbacks");
    test.evaluate(web, "window.captureClone=captureStream.getVideoTracks()[0].clone();window.streamClone=captureStream.clone()");
    main(host::minimize); Thread.sleep(500); main(host::restore); Thread.sleep(300);
    test.check("true".equals(test.evaluate(web, "captureStream.getTracks().every(t=>t.readyState==='ended')&&captureClone.readyState==='ended'&&streamClone.getTracks().every(t=>t.readyState==='ended')")), "minimization stops active camera/microphone, cloned tracks and cloned streams");
    main(host::onActivityPause);
    test.evaluate(web, "window.backgroundCapture='waiting';navigator.mediaDevices.getUserMedia({audio:true,video:true}).then(s=>{s.getTracks().forEach(t=>t.stop());backgroundCapture='unexpected'},e=>backgroundCapture=e.name)");
    for (int i = 0; i < 100; i++) {
      AlertDialog consent = field(host, "activeDialog"); if (consent != null) click(consent, true);
      allowAndroidPermission();
      if (!"\"waiting\"".equals(test.evaluate(web, "backgroundCapture"))) break;
      Thread.sleep(100);
    }
    test.check("\"AbortError\"".equals(test.evaluate(web, "backgroundCapture")), "a stream resolving after activity pause is stopped before delivery to JavaScript");
    main(host::onActivityResume);
    android.net.Uri first = android.net.Uri.parse("content://picker.test/one"), second = android.net.Uri.parse("content://picker.test/two");
    Intent files = new Intent(); android.content.ClipData clip = android.content.ClipData.newRawUri("one", first); clip.addItem(new android.content.ClipData.Item(second)); files.setClipData(clip);
    test.check(MiniAppMedia.pickerUris(files, true).length == 2 && MiniAppMedia.pickerUris(files, false) == null, "picker result preserves multiple URIs and rejects multiple files in single mode");
  }

  /** Run only after the human authorizes a temporary visible status and permission change. */
  private void authorizedEmojiChecks () throws Exception {
    TdApi.User me = (TdApi.User) call(new TdApi.GetMe());
    test.check(me.isPremium, "account supports successful emoji status changes");
    TdApi.EmojiStatus original = me.emojiStatus;
    TdApi.UserFullInfo full = (TdApi.UserFullInfo) call(new TdApi.GetUserFullInfo(bot));
    boolean originalPermission = full.botInfo.canManageEmojiStatus;
    long emoji = ((TdApi.EmojiStatusCustomEmojis) call(new TdApi.GetDefaultEmojiStatuses())).customEmojiIds[0];
    try {
      clear(); main(host::recordGesture); send("web_app_set_emoji_status", "{\"custom_emoji_id\":\"" + emoji + "\",\"duration\":60}");
      AlertDialog status = dialog(); capture("stage45-authorized-status-consent"); click(status, true);
      result("emoji_status_set");
      TdApi.User changed = (TdApi.User) call(new TdApi.GetMe());
      test.check(changed.emojiStatus != null && changed.emojiStatus.type instanceof TdApi.EmojiStatusTypeCustomEmoji && ((TdApi.EmojiStatusTypeCustomEmoji) changed.emojiStatus.type).customEmojiId == emoji,
        "temporary emoji status is confirmed by Telegram and read back");
      clear(); main(host::recordGesture); send("web_app_request_emoji_status_access", "{}");
      for (int i = 0; i < 100 && !has("emoji_status_access_requested"); i++) {
        AlertDialog access = field(host, "activeDialog"); if (access != null) { capture("stage45-authorized-emoji-permission"); click(access, true); break; } Thread.sleep(100);
      }
      test.check("allowed".equals(result("emoji_status_access_requested").getString("status")), "emoji management grant receives successful server result");
      test.check(((TdApi.UserFullInfo) call(new TdApi.GetUserFullInfo(bot))).botInfo.canManageEmojiStatus, "nskdancebot emoji permission read back as enabled");
    } finally {
      try { call(new TdApi.SetEmojiStatus(original)); }
      finally { call(new TdApi.ToggleBotCanManageEmojiStatus(bot, originalPermission)); }
      TdApi.User restored = (TdApi.User) call(new TdApi.GetMe());
      test.check(java.util.Objects.equals(String.valueOf(restored.emojiStatus), String.valueOf(original)), "original public emoji status restored and read back");
      test.check(((TdApi.UserFullInfo) call(new TdApi.GetUserFullInfo(bot))).botInfo.canManageEmojiStatus == originalPermission, "original bot emoji permission restored and read back");
    }
  }

  private void actionChecks () throws Exception {
    TdApi.EmojiStatusCustomEmojis statuses = (TdApi.EmojiStatusCustomEmojis) call(new TdApi.GetDefaultEmojiStatuses());
    test.check(statuses.customEmojiIds.length > 0, "Telegram supplied a real emoji for native preview");
    clear(); main(host::recordGesture); send("web_app_set_emoji_status", "{\"custom_emoji_id\":\"" + statuses.customEmojiIds[0] + "\",\"duration\":90060}");
    AlertDialog status = dialog(); Thread.sleep(1000); capture("stage45-real-emoji-preview"); click(status, false);
    test.check("USER_DECLINED".equals(result("emoji_status_failed").getString("error")), "emoji status preview cancellation leaves public status unchanged");
    clear(); main(host::recordGesture); send("web_app_request_emoji_status_access", "{}");
    for (int i = 0; i < 100 && !has("emoji_status_access_requested"); i++) {
      AlertDialog access = field(host, "activeDialog"); if (access != null) { Thread.sleep(700); capture("stage45-real-emoji-access"); click(access, false); break; } Thread.sleep(100);
    }
    String access = result("emoji_status_access_requested").getString("status");
    test.check("cancelled".equals(access) || "allowed".equals(access), "emoji permission reports native refusal or existing server permission");
    String filename = "mini-apps-contract-" + Long.toHexString(System.nanoTime()) + ".svg";
    clear(); main(host::recordGesture); send("web_app_request_file_download", new JSONObject().put("file_name", filename).put("url", "https://nskdance.danceworldtv.ru/favicon.svg").toString());
    AlertDialog download = dialog(); Thread.sleep(1000); capture("stage45-real-download"); click(download, true);
    test.check("downloading".equals(result("file_download_requested").getString("status")), "nskdancebot download passes CheckWebAppFileDownload and real enqueue");
    MiniAppActions actions = field(host, "actions"); android.content.SharedPreferences prefs = field(actions, "downloads");
    long id = 0; for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) if (filename.equals(entry.getValue())) id = Long.parseLong(entry.getKey());
    android.app.DownloadManager manager = field(actions, "downloadManager");
    try {
      int state = 0;
      for (int i = 0; i < 300; i++) {
        try (android.database.Cursor cursor = manager.query(new android.app.DownloadManager.Query().setFilterById(id))) {
          if (cursor != null && cursor.moveToFirst()) state = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
        }
        if (state == android.app.DownloadManager.STATUS_SUCCESSFUL || state == android.app.DownloadManager.STATUS_FAILED) break;
        Thread.sleep(100);
      }
      test.check(state == android.app.DownloadManager.STATUS_SUCCESSFUL && manager.getUriForDownloadedFile(id) != null, "DownloadManager saved the actual file and exposes a content URI");
      main(actions::showDownloads); Thread.sleep(500); capture("stage45-downloads-list");
      try (android.os.ParcelFileDescriptor ignored = test.getUiAutomation().executeShellCommand("input keyevent 4")) { Thread.sleep(300); }
    } finally { if (id != 0) { manager.remove(id); prefs.edit().remove(Long.toString(id)).commit(); } }
    clear(); main(host::recordGesture); send("web_app_add_to_home_screen", "{}"); Thread.sleep(1200); capture("stage45-shortcut-preview");
    System.out.println("WAIT launcher pin confirmation on phone");
    for (int i = 0; i < 600 && !has("home_screen_added"); i++) Thread.sleep(100);
    test.check(has("home_screen_added"), "launcher confirms actual bot shortcut pin");
    clear(); send("web_app_check_home_screen", "{}");
    test.check("added".equals(result("home_screen_checked").getString("status")), "subsequent shortcut query reads launcher pinned state");
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
    for (String id : new String[] {"permission_allow_one_time_button", "permission_allow_foreground_only_button", "permission_allow_button"}) {
      for (String namespace : new String[] {"com.android.permissioncontroller", root.getPackageName().toString()}) {
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(namespace + ":id/" + id);
        if (!nodes.isEmpty()) { nodes.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); return; }
      }
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
    // DocumentsUI remembers its last folder. Back may navigate parents before cancelling the picker.
    for (int attempt = 0; attempt < 6 && replies.get() == 0; attempt++) {
      try (android.os.ParcelFileDescriptor ignored = test.getUiAutomation().executeShellCommand("input keyevent 4")) { Thread.sleep(500); }
    }
    for (int i = 0; i < 30 && replies.get() == 0; i++) Thread.sleep(100);
    test.check(replies.get() == 1, "real Android multiple-file picker cancellation completes once");
  }

  private void opticalQrChecks () throws Exception {
    send("web_app_open_scan_qr_popup", "{\"text\":\"Scan the Mini Apps test QR on the computer screen\"}");
    System.out.println("WAIT optical QR: point the rear camera at the test QR on the computer screen");
    for (int i = 0; i < 1800 && !has("qr_text_received"); i++) { allowAndroidPermission(); Thread.sleep(100); }
    test.check("Mini Apps optical QR acceptance".equals(result("qr_text_received").getString("data")), "actual camera optically decodes the test QR from a separate screen");
    Object scanner = field(host, "qrScanner");
    send("web_app_close_scan_qr_popup", "{}"); result("scan_qr_popup_closed");
    test.check(field(scanner, "camera") == null, "successful optical scan releases the camera when the page closes the scanner");
    test.check("\"https://miniapp.test/stage45\"".equals(test.evaluate(web, "location.href")), "decoded QR text is returned to JavaScript without navigation");
  }

  private void requestedChatServerChecks () throws Exception {
    send("web_app_request_chat", "{\"req_id\":\"miniapps-expired-fixture\"}");
    JSONObject failed = result("requested_chat_failed");
    test.check("miniapps-expired-fixture".equals(failed.getString("req_id")), "real Telegram rejects an unknown prepared button and preserves req_id");
    test.check("1".equals(test.evaluate(web, "events.filter(e=>e.event==='requested_chat_failed').length")), "real server error completes once");
    test.check(!has("requested_chat_sent"), "real server rejection never reports success");
  }

  /** Native UI and protocol tests, with a controlled transport that cannot share real peers. */
  private void requestedChatChecks () throws Exception {
    TdApi.Chat self = (TdApi.Chat) call(new TdApi.CreatePrivateChat(tdlib.myUserId(), false));
    AtomicReference<org.drinkless.tdlib.Client.ResultHandler> pending = new AtomicReference<>(), fetch = new AtomicReference<>();
    AtomicReference<TdApi.ShareUsersWithBot> share = new AtomicReference<>();
    java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger(), checked = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicInteger created = new java.util.concurrent.atomic.AtomicInteger(), usernameUpdates = new java.util.concurrent.atomic.AtomicInteger(), chatsShared = new java.util.concurrent.atomic.AtomicInteger();
    AtomicReference<TdApi.CreateNewSupergroupChat> creation = new AtomicReference<>();
    main(() -> set(host, "requestedChat", new MiniAppRequestedChat(host, (function, callback) -> {
      if (function instanceof TdApi.GetPreparedKeyboardButton) {
        String id = ((TdApi.GetPreparedKeyboardButton) function).preparedButtonId;
        if ("late".equals(id)) { fetch.set(callback); return; }
        if ("expired".equals(id)) { callback.onResult(new TdApi.Error(400, "BUTTON_ID_INVALID")); return; }
        TdApi.KeyboardButton button = new TdApi.KeyboardButton(); button.text = "Test";
        if ("group".equals(id) || "group_public".equals(id)) {
          TdApi.KeyboardButtonTypeRequestChat type = new TdApi.KeyboardButtonTypeRequestChat(); type.id = 88;
          if ("group_public".equals(id)) { type.restrictChatHasUsername = type.chatHasUsername = type.restrictChatIsForum = type.chatIsForum = true; }
          button.type = type;
        }
        else if ("managed".equals(id)) button.type = new TdApi.KeyboardButtonTypeRequestManagedBot(77, "Test Bot", "test_bot");
        else button.type = new TdApi.KeyboardButtonTypeRequestUsers(77, true, false, false, false, 2, true, true, false);
        callback.onResult(button);
      } else if (function instanceof TdApi.GetChats || function instanceof TdApi.SearchChats) callback.onResult(new TdApi.Chats(1, new long[] {self.id}));
      else if (function instanceof TdApi.GetOption) callback.onResult(new TdApi.OptionValueBoolean(false));
      else if (function instanceof TdApi.SearchContacts) callback.onResult(new TdApi.Users(0, new long[0]));
      else if (function instanceof TdApi.LoadChats) callback.onResult(new TdApi.Ok());
      else if (function instanceof TdApi.ShareUsersWithBot) {
        TdApi.ShareUsersWithBot request = (TdApi.ShareUsersWithBot) function;
        if (request.onlyCheck) { checked.incrementAndGet(); callback.onResult(new TdApi.Ok()); }
        else { sent.incrementAndGet(); share.set(request); pending.set(callback); }
      } else if (function instanceof TdApi.CheckChatUsername) callback.onResult(new TdApi.CheckChatUsernameResultOk());
      else if (function instanceof TdApi.CreateNewSupergroupChat) {
        created.incrementAndGet(); creation.set((TdApi.CreateNewSupergroupChat) function);
        TdApi.Chat chat = new TdApi.Chat(); chat.id = -1000000777L; chat.type = new TdApi.ChatTypeSupergroup(777, false); chat.title = "Synthetic requested group"; callback.onResult(chat);
      } else if (function instanceof TdApi.SetSupergroupUsername) {
        callback.onResult(usernameUpdates.incrementAndGet() == 1 ? new TdApi.Error(400, "USERNAME_OCCUPIED") : new TdApi.Ok());
      } else if (function instanceof TdApi.ShareChatWithBot) {
        if (((TdApi.ShareChatWithBot) function).onlyCheck) callback.onResult(new TdApi.Ok());
        else { chatsShared.incrementAndGet(); pending.set(callback); }
      } else throw new AssertionError("Unexpected fixture request: " + function.getClass().getSimpleName());
    })));
    send("web_app_request_chat", "{\"req_id\":\"users\"}"); Object operation = requestedOperation();
    android.widget.LinearLayout rows = field(operation, "rows");
    for (int i = 0; i < 100 && rows.getChildCount() == 0; i++) Thread.sleep(100);
    test.check(rows.getChildCount() == 1, "requested users picker applies the bot and premium filters to cached chats");
    capture("stage6-requested-users");
    main(() -> rows.getChildAt(0).performClick()); main(() -> ((android.view.View) field(operation, "submit")).performClick()); Thread.sleep(300);
    test.check(checked.get() == 1 && sent.get() == 0, "selection is validated by onlyCheck before native confirmation");
    capture("stage6-requested-users-confirm");
    main(() -> ((android.view.View) field(operation, "submit")).performClick());
    main(() -> ((android.view.View) field(operation, "submit")).performClick());
    test.check(sent.get() == 1 && !has("requested_chat_sent"), "double tap does not resend and queued request is not reported as success");
    TdApi.ShareUsersWithBot outgoing = share.get();
    test.check(outgoing.buttonId == 77 && outgoing.sharedUserIds.length == 1 && outgoing.sharedUserIds[0] == tdlib.myUserId() && outgoing.source instanceof TdApi.KeyboardButtonSourceWebApp && ((TdApi.KeyboardButtonSourceWebApp) outgoing.source).preparedButtonId.equals("users"), "prepared source, button id and exact selected users reach the transport");
    main(host::minimize); Thread.sleep(300);
    test.check(field(field(host, "requestedChat"), "operation") != null, "minimizing an already submitted share does not invent a cancellation");
    pending.get().onResult(new TdApi.Ok()); Thread.sleep(200); main(host::restore); Thread.sleep(300);
    test.check("users".equals(result("requested_chat_sent").getString("req_id")), "success emitted after acknowledged sharing with original req_id");
    test.check(!has("requested_chat_failed"), "acknowledgement while minimized is delivered as success after restore");
    pending.get().onResult(new TdApi.Ok()); Thread.sleep(100);
    test.check("1".equals(test.evaluate(web, "events.filter(e=>e.event==='requested_chat_sent').length")), "late duplicate transport completion cannot emit twice");
    clear(); send("web_app_request_chat", "{\"req_id\":\"cancel\"}"); Object cancelled = requestedOperation();
    main(() -> ((android.app.Dialog) field(cancelled, "dialog")).cancel());
    test.check("cancel".equals(result("requested_chat_failed").getString("req_id")), "picker Back or outside cancellation keeps original req_id");
    clear(); send("web_app_request_chat", "{\"req_id\":\"late\"}");
    for (int i = 0; i < 100 && fetch.get() == null; i++) Thread.sleep(50);
    main(host::minimize); Thread.sleep(350); main(host::restore); Thread.sleep(350);
    test.check("late".equals(result("requested_chat_failed").getString("req_id")), "minimization cancels pending request and returns failure on restore");
    fetch.get().onResult(new TdApi.KeyboardButton("Test", 0, null, new TdApi.KeyboardButtonTypeRequestUsers())); Thread.sleep(150);
    test.check(field(field(host, "requestedChat"), "operation") == null, "late prepared button cannot reopen a dismissed selection sheet");
    clear(); send("web_app_request_chat", "{\"req_id\":\"group\"}"); Object group = requestedOperation();
    test.check(((android.widget.LinearLayout) field(group, "rows")).getChildCount() == 0, "requested group picker excludes private chats");
    main(() -> invoke(group, "createChat")); capture("stage6-create-requested-group");
    main(() -> ((android.app.Dialog) field(group, "dialog")).cancel());
    test.check("group".equals(result("requested_chat_failed").getString("req_id")), "new group form cancellation does not create a chat");
    test.check(created.get() == 0, "opening and cancelling creation never dispatches CreateNewSupergroupChat");
    clear(); send("web_app_request_chat", "{\"req_id\":\"group_public\"}"); Object publicGroup = requestedOperation();
    main(() -> {
      invoke(publicGroup, "createChat");
      android.widget.LinearLayout content = field(publicGroup, "content"); int input = 0;
      for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i) instanceof android.widget.EditText) {
        ((android.widget.EditText) content.getChildAt(i)).setText(input++ == 0 ? "Synthetic requested group" : "miniapps_synthetic_group");
      }
      ((android.view.View) field(publicGroup, "submit")).performClick();
    });
    for (int i = 0; i < 100 && usernameUpdates.get() == 0; i++) Thread.sleep(50);
    Thread.sleep(200);
    test.check(created.get() == 1 && chatsShared.get() == 0 && creation.get().isForum && !creation.get().isChannel, "create-and-share carries requested forum type and does not share before username setup");
    main(() -> ((android.view.View) field(publicGroup, "submit")).performClick());
    for (int i = 0; i < 100 && chatsShared.get() == 0; i++) Thread.sleep(50);
    test.check(created.get() == 1 && usernameUpdates.get() == 2 && chatsShared.get() == 1, "retry after username failure reuses the created chat without duplicate creation");
    test.check(!has("requested_chat_sent"), "new chat is not reported as shared before server acknowledgement");
    pending.get().onResult(new TdApi.Ok());
    test.check("group_public".equals(result("requested_chat_sent").getString("req_id")), "controlled new-group share completes with original req_id");
    clear(); send("web_app_request_chat", "{\"req_id\":\"send_error\"}"); Object rejected = requestedOperation();
    main(() -> ((android.widget.LinearLayout) field(rejected, "rows")).getChildAt(0).performClick());
    main(() -> ((android.view.View) field(rejected, "submit")).performClick()); Thread.sleep(200);
    main(() -> ((android.view.View) field(rejected, "submit")).performClick());
    pending.get().onResult(new TdApi.Error(400, "BUTTON_ID_INVALID"));
    test.check("send_error".equals(result("requested_chat_failed").getString("req_id")) && !has("requested_chat_sent"), "server rejection after confirmation returns failure rather than optimistic success");
    clear(); send("web_app_request_chat", "{\"req_id\":\"managed\"}");
    test.check("managed".equals(result("requested_chat_failed").getString("req_id")), "managed bot dependency fails explicitly before creating any real bot");
    test.check(sent.get() == 2 && chatsShared.get() == 1, "all cancellation paths performed zero extra sends");
  }
  private Object requestedOperation () throws Exception {
    for (int i = 0; i < 100; i++) { Object request = field(host, "requestedChat"), operation = request != null ? field(request, "operation") : null; if (operation != null && field(operation, "dialog") != null && field(operation, "rows") != null) { Thread.sleep(200); return operation; } Thread.sleep(100); }
    throw new AssertionError("Requested chat picker missing");
  }

  /** All create/share requests are intercepted; only native capability reads use the live client. */
  private void managedBotChecks () throws Exception {
    TdApi.Object nativeManaged = call(new TdApi.GetOption("can_share_managed_bot"));
    test.check(nativeManaged instanceof TdApi.OptionValueBoolean && ((TdApi.OptionValueBoolean) nativeManaged).value, "installed native TDLib supports managed bot sharing");
    TdApi.Object nativePreview = call(new TdApi.GetOption("can_get_inline_query_result_message"));
    test.check(nativePreview instanceof TdApi.OptionValueBoolean && ((TdApi.OptionValueBoolean) nativePreview).value, "installed native TDLib exposes actual inline message previews");
    // The independent client branch still builds with the upstream bundle. This opt-in suite
    // requires the separately built TDLib extension and verifies its generated JNI bindings.
    TdApi.Function<?> previewRequest = (TdApi.Function<?>) Class.forName("org.drinkless.tdlib.TdApi$GetInlineQueryResultMessage").getConstructor(long.class, String.class).newInstance(-1L, "missing-synthetic-result");
    TdApi.Object missingPreview = callRaw(previewRequest);
    test.check(missingPreview instanceof TdApi.Error && ((TdApi.Error) missingPreview).code == 400, "new generated Java request reaches native API and rejects an uncached preview");
    java.util.concurrent.atomic.AtomicBoolean supported = new java.util.concurrent.atomic.AtomicBoolean(true), managerEnabled = new java.util.concurrent.atomic.AtomicBoolean(true);
    java.util.concurrent.atomic.AtomicInteger creates = new java.util.concurrent.atomic.AtomicInteger(), shares = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicLong opened = new java.util.concurrent.atomic.AtomicLong();
    AtomicReference<TdApi.CreateBot> creation = new AtomicReference<>(); AtomicReference<TdApi.ShareUsersWithBot> sharing = new AtomicReference<>();
    AtomicReference<org.drinkless.tdlib.Client.ResultHandler> createReply = new AtomicReference<>(), shareReply = new AtomicReference<>();
    main(() -> set(host, "requestedChat", new MiniAppRequestedChat(host, (function, callback) -> {
      if (function instanceof TdApi.GetPreparedKeyboardButton) callback.onResult(new TdApi.KeyboardButton("Test", 0, null, new TdApi.KeyboardButtonTypeRequestManagedBot(79, "Synthetic Bot", "synthetic_BOT")));
      else if (function instanceof TdApi.GetOption) callback.onResult(new TdApi.OptionValueBoolean(supported.get()));
      else if (function instanceof TdApi.GetUser) {
        TdApi.User manager = new TdApi.User(); manager.id = bot; manager.firstName = "NskDanceBot"; manager.lastName = "";
        TdApi.UserTypeBot type = new TdApi.UserTypeBot(); type.canManageBots = managerEnabled.get(); manager.type = type; callback.onResult(manager);
      } else if (function instanceof TdApi.CheckBotUsername) callback.onResult(new TdApi.CheckChatUsernameResultOk());
      else if (function instanceof TdApi.CreateBot) { creates.incrementAndGet(); creation.set((TdApi.CreateBot) function); createReply.set(callback); }
      else if (function instanceof TdApi.ShareUsersWithBot) { shares.incrementAndGet(); sharing.set((TdApi.ShareUsersWithBot) function); shareReply.set(callback); }
      else throw new AssertionError("Unexpected managed-bot fixture call: " + function.getClass().getSimpleName());
    }, opened::set)));
    clear(); send("web_app_request_chat", "{\"req_id\":\"managed-cancel\"}"); Object cancelled = managedOperation();
    java.util.ArrayList<android.widget.EditText> inputs = new java.util.ArrayList<>(); main(() -> collectInputs((android.view.View) field(cancelled, "content"), inputs));
    test.check(inputs.size() == 2 && "Synthetic Bot".contentEquals(inputs.get(0).getText()) && "synthetic_".contentEquals(inputs.get(1).getText()), "official creation sheet keeps suggested name and separates the fixed bot suffix");
    main(() -> {
      android.widget.LinearLayout content = field(cancelled, "content");
      CharSequence subtitle = ((android.widget.TextView) content.getChildAt(2)).getText();
      test.check(subtitle instanceof android.text.Spanned && !subtitle.toString().contains("**") && android.graphics.Typeface.DEFAULT.equals(inputs.get(1).getTypeface()), "managed sheet uses formatted bot attribution and the regular username typeface");
    });
    capture("stage6-managed-bot-create");
    int orientation = activity.getRequestedOrientation();
    try {
      main(() -> { inputs.get(1).requestFocus(); ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(inputs.get(1), android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); });
      Thread.sleep(800);
      main(() -> {
        android.graphics.Rect visible = new android.graphics.Rect(), fieldBounds = new android.graphics.Rect();
        ((android.app.Dialog) field(cancelled, "dialog")).getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        inputs.get(1).getGlobalVisibleRect(fieldBounds);
        test.check(fieldBounds.height() > 0 && fieldBounds.bottom <= visible.bottom, "managed username field remains visible above the real IME");
      });
      capture("stage6-managed-bot-keyboard");
      main(() -> ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(inputs.get(1).getWindowToken(), 0));
      main(() -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); Thread.sleep(1200);
      main(() -> {
        android.view.View decor = ((android.app.Dialog) field(cancelled, "dialog")).getWindow().getDecorView();
        test.check(decor.getWidth() > decor.getHeight() && decor.getHeight() <= activity.getRootView().getHeight(), "managed creation sheet fits landscape height and remains scrollable");
        ((android.widget.ScrollView) ((android.view.View) field(cancelled, "content")).getParent()).fullScroll(android.view.View.FOCUS_DOWN);
      });
      Thread.sleep(300);
      main(() -> {
        android.graphics.Rect visible = new android.graphics.Rect(), bounds = new android.graphics.Rect();
        ((android.app.Dialog) field(cancelled, "dialog")).getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        android.view.View submit = field(cancelled, "submit"); submit.getGlobalVisibleRect(bounds);
        test.check(bounds.height() == submit.getHeight() && bounds.bottom <= visible.bottom, "landscape scrolling exposes the entire create button above navigation insets");
      });
      capture("stage6-managed-bot-landscape");
    } finally {
      main(() -> activity.setRequestedOrientation(orientation)); Thread.sleep(900);
    }
    main(() -> ((android.app.Dialog) field(cancelled, "dialog")).cancel());
    test.check("managed-cancel".equals(result("requested_chat_failed").getString("req_id")) && creates.get() == 0 && shares.get() == 0, "cancelling creation does not create or share a bot");
    clear(); send("web_app_request_chat", "{\"req_id\":\"managed-success\"}"); Object operation = managedOperation();
    for (int i = 0; i < 100 && field(operation, "checkedBotUsername") == null; i++) Thread.sleep(50);
    main(() -> { ((android.view.View) field(operation, "submit")).performClick(); ((android.view.View) field(operation, "submit")).performClick(); });
    test.check(creates.get() == 1 && shares.get() == 0, "double tap creates only once and cannot share before the create response");
    test.check(creation.get().managerBotUserId == bot && !creation.get().viaLink && "synthetic_bot".equals(creation.get().username) && "Synthetic Bot".equals(creation.get().name), "creation carries the actual manager, name, normalized suffix and Mini App source");
    createReply.get().onResult(new TdApi.Error(400, "USERNAME_OCCUPIED")); Thread.sleep(200);
    test.check(!has("requested_chat_sent") && !has("requested_chat_failed"), "recoverable creation error keeps the form available without a success event");
    main(() -> ((android.view.View) field(operation, "submit")).performClick());
    test.check(creates.get() == 2, "an explicit retry is allowed after a definitive create failure");
    TdApi.User created = new TdApi.User(); created.id = Long.MAX_VALUE - 120; created.firstName = "Synthetic Bot"; created.lastName = ""; created.type = new TdApi.UserTypeBot();
    createReply.get().onResult(created); Thread.sleep(200);
    test.check(shares.get() == 1 && !has("requested_chat_sent"), "created bot is shared only once and waits for server acknowledgement");
    TdApi.ShareUsersWithBot share = sharing.get();
    test.check(share.buttonId == 79 && !share.onlyCheck && share.sharedUserIds.length == 1 && share.sharedUserIds[0] == created.id && share.source instanceof TdApi.KeyboardButtonSourceWebApp && "managed-success".equals(((TdApi.KeyboardButtonSourceWebApp) share.source).preparedButtonId), "managed bot share preserves prepared button and exact newly created user");
    shareReply.get().onResult(new TdApi.Ok());
    test.check("managed-success".equals(result("requested_chat_sent").getString("req_id")), "managed flow completes after acknowledged share");
    Thread.sleep(150); test.check(opened.get() == created.id, "successful foreground share opens the created bot through the navigation delegate");
    shareReply.get().onResult(new TdApi.Ok()); Thread.sleep(100);
    test.check("1".equals(test.evaluate(web, "events.filter(e=>e.event==='requested_chat_sent').length")), "duplicate acknowledgement cannot complete twice");
    clear(); supported.set(false); send("web_app_request_chat", "{\"req_id\":\"managed-old-native\"}");
    test.check("managed-old-native".equals(result("requested_chat_failed").getString("req_id")) && creates.get() == 2, "older native library is rejected before creating an orphaned bot");
    clear(); supported.set(true); managerEnabled.set(false); send("web_app_request_chat", "{\"req_id\":\"managed-unsupported\"}");
    test.check("managed-unsupported".equals(result("requested_chat_failed").getString("req_id")) && creates.get() == 2, "bot without Bot Management mode is rejected before creation");
    clear(); managerEnabled.set(true); opened.set(0); send("web_app_request_chat", "{\"req_id\":\"managed-background\"}"); Object background = managedOperation();
    for (int i = 0; i < 100 && field(background, "checkedBotUsername") == null; i++) Thread.sleep(50);
    main(() -> ((android.view.View) field(background, "submit")).performClick());
    main(host::minimize); Thread.sleep(350); createReply.get().onResult(created); Thread.sleep(200);
    test.check(creates.get() == 3 && shares.get() == 2, "minimization preserves confirmed creation and shares the returned bot once");
    shareReply.get().onResult(new TdApi.Ok()); Thread.sleep(200);
    test.check(opened.get() == 0, "background acknowledgement does not navigate over the user's current screen");
    main(host::restore); Thread.sleep(350);
    test.check("managed-background".equals(result("requested_chat_sent").getString("req_id")) && !has("requested_chat_failed"), "confirmed managed share is delivered once after restore without a false cancellation");
    TdApi.User actualManager = (TdApi.User) call(new TdApi.GetUser(bot));
    android.os.Bundle info = new android.os.Bundle(); info.putString("stream", "INFO nskdancebot Bot Management mode: " + (actualManager.type instanceof TdApi.UserTypeBot && ((TdApi.UserTypeBot) actualManager.type).canManageBots) + "\n"); test.sendStatus(0, info);
  }
  private Object managedOperation () throws Exception {
    for (int i = 0; i < 100; i++) { Object service = field(host, "requestedChat"), operation = service != null ? field(service, "operation") : null; if (operation != null && field(operation, "dialog") != null) { Thread.sleep(250); return operation; } Thread.sleep(100); }
    throw new AssertionError("Managed bot form missing");
  }
  private static void collectInputs (android.view.View view, java.util.List<android.widget.EditText> output) {
    if (view instanceof android.widget.EditText) output.add((android.widget.EditText) view);
    if (view instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) collectInputs(((android.view.ViewGroup) view).getChildAt(i), output);
  }

  private void fileSuccessChecks (boolean camera) throws Exception {
    AtomicReference<android.net.Uri[]> selected = new AtomicReference<>();
    java.util.concurrent.atomic.AtomicInteger replies = new java.util.concurrent.atomic.AtomicInteger();
    main(() -> web.getWebChromeClient().onShowFileChooser(web, files -> { selected.set(files); replies.incrementAndGet(); }, new android.webkit.WebChromeClient.FileChooserParams() {
      @Override public int getMode () { return camera ? MODE_OPEN : MODE_OPEN_MULTIPLE; }
      @Override public String[] getAcceptTypes () { return new String[] {camera ? "image/jpeg" : "text/plain"}; }
      @Override public boolean isCaptureEnabled () { return camera; }
      @Override public CharSequence getTitle () { return null; }
      @Override public String getFilenameHint () { return null; }
      @Override public Intent createIntent () { return new Intent(Intent.ACTION_GET_CONTENT); }
    }));
    System.out.println(camera ? "WAIT external camera: capture test target and accept photo" : "WAIT file picker: select the two miniapps-stage45 synthetic text files");
    for (int i = 0; i < 1800 && replies.get() == 0; i++) { allowAndroidPermission(); Thread.sleep(100); }
    test.check(replies.get() == 1, "actual external activity delivers its result exactly once");
    android.net.Uri[] files = selected.get();
    test.check(files != null && files.length == (camera ? 1 : 2), "successful external activity preserves the requested file count");
    for (android.net.Uri file : files) {
      test.check("content".equals(file.getScheme()), "file is delivered through a content URI");
      try (java.io.InputStream input = activity.getContentResolver().openInputStream(file)) {
        if (camera) {
          android.graphics.BitmapFactory.Options dimensions = new android.graphics.BitmapFactory.Options(); dimensions.inJustDecodeBounds = true;
          android.graphics.BitmapFactory.decodeStream(input, null, dimensions);
          test.check(dimensions.outWidth > 0 && dimensions.outHeight > 0, "external camera wrote a decodable photo to the private capture URI");
        } else {
          byte[] buffer = new byte[64]; int count = input.read(buffer);
          test.check(count > 0 && new String(buffer, 0, count, StandardCharsets.UTF_8).startsWith("Mini Apps synthetic picker file"), "picker returned the synthetic fixture content without uploading it");
        }
      }
    }
    if (camera) {
      main(host::close);
      boolean removed = false;
      try (java.io.InputStream ignored = activity.getContentResolver().openInputStream(files[0])) { } catch (java.io.FileNotFoundException expected) { removed = true; }
      test.check(removed, "closing the Mini App deletes the temporary camera capture");
    }
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
    TdApi.Object result = callRaw(function);
    if (result instanceof TdApi.Error) throw new AssertionError("TDLib error " + ((TdApi.Error) result).code);
    return result;
  }
  private TdApi.Object callRaw (TdApi.Function<?> function) throws Exception {
    CountDownLatch latch = new CountDownLatch(1); AtomicReference<TdApi.Object> result = new AtomicReference<>();
    tdlib.client().send(function, value -> { result.set(value); latch.countDown(); });
    if (!latch.await(30, TimeUnit.SECONDS)) throw new AssertionError("TDLib timeout");
    return result.get();
  }

  private void preparedMessageServerChecks () throws Exception {
    TdApi.Object missing = callRaw(new TdApi.GetPreparedInlineMessage(bot, "miniapps-missing-prepared-fixture"));
    test.check(missing instanceof TdApi.Error, "nskdancebot rejects an unknown prepared message without sending anything");
    main(() -> set(host, "lastInteraction", android.os.SystemClock.uptimeMillis()));
    send("web_app_send_prepared_message", "{\"id\":\"miniapps-missing-prepared-fixture\"}");
    test.check("MESSAGE_EXPIRED".equals(result("prepared_message_failed").getString("error")), "real Telegram error returns the official MESSAGE_EXPIRED event");
    test.check(!has("prepared_message_sent"), "server rejection cannot report message sent");
    TdApi.Function<?> request = MiniAppInlinePreview.request(-1, "missing-synthetic-result");
    test.check(request != null && callRaw(request) instanceof TdApi.Error, "rebuilt JNI accepts the preview API and rejects an absent cache entry");
  }

  /** Only the transport is synthetic: preview, picker, message updates and WebView callbacks run on the phone. */
  private void preparedMessageChecks () throws Exception {
    TdApi.Chat self = (TdApi.Chat) call(new TdApi.CreatePrivateChat(tdlib.myUserId(), false));
    AtomicReference<org.drinkless.tdlib.Client.ResultHandler> fetch = new AtomicReference<>(), pending = new AtomicReference<>();
    AtomicReference<TdApi.SendInlineQueryResultMessage> outgoing = new AtomicReference<>();
    AtomicReference<TdApi.SendMessage> outgoingComment = new AtomicReference<>();
    java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicInteger linkRequests = new java.util.concurrent.atomic.AtomicInteger();
    AtomicReference<MiniAppInlinePreview> preview = new AtomicReference<>();
    TdApi.FormattedText text = new TdApi.FormattedText("Mini Apps message preview\nBold text and /command", new TdApi.TextEntity[] {
      new TdApi.TextEntity(26, 9, new TdApi.TextEntityTypeBold()), new TdApi.TextEntity(40, 8, new TdApi.TextEntityTypeBotCommand())
    });
    TdApi.ReplyMarkup markup = new TdApi.ReplyMarkupInlineKeyboard(new TdApi.InlineKeyboardButton[][] {{new TdApi.InlineKeyboardButton("Open test link", 0, null, new TdApi.InlineKeyboardButtonTypeUrl("https://example.org/"))}}, false);
    preview.set(new MiniAppInlinePreview(new TdApi.MessageText(text, null, null), markup));
    main(() -> set(host, "preparedMessage", new MiniAppPreparedMessage(host, (function, callback) -> {
      if (function instanceof TdApi.GetPreparedInlineMessage) {
        String id = ((TdApi.GetPreparedInlineMessage) function).preparedMessageId;
        if ("late".equals(id)) { fetch.set(callback); return; }
        if ("expired".equals(id)) { callback.onResult(new TdApi.Error(400, "MESSAGE_ID_INVALID")); return; }
        TdApi.InlineQueryResultArticle article = new TdApi.InlineQueryResultArticle(); article.id = "cached-result"; article.title = "Discovery title, never the message preview";
        callback.onResult(new TdApi.PreparedInlineMessage(99112233L, article, new TdApi.TargetChatTypes(true, false, false, false)));
      } else if (function instanceof TdApi.CreatePrivateChat) callback.onResult(self);
      else if (function instanceof TdApi.GetChats || function instanceof TdApi.SearchChats) callback.onResult(new TdApi.Chats(1, new long[] {self.id}));
      else if (function instanceof TdApi.LoadChats) callback.onResult(new TdApi.Error(404, "Not Found"));
      else if (function instanceof TdApi.SendInlineQueryResultMessage) { sends.incrementAndGet(); outgoing.set((TdApi.SendInlineQueryResultMessage) function); pending.set(callback); }
      else if (function instanceof TdApi.SendMessage) {
        outgoingComment.set((TdApi.SendMessage) function);
        TdApi.Message delivered = new TdApi.Message(); delivered.chatId = self.id; delivered.id = 1002; callback.onResult(delivered);
      } else if (function instanceof TdApi.GetLinkPreview) {
        linkRequests.incrementAndGet();
        callback.onResult(new TdApi.LinkPreview("https://example.org/", "example.org", "Example", "Synthetic link card", new TdApi.FormattedText("Link preview content", null), "", new TdApi.LinkPreviewTypeArticle(null), false, false, false, true, false, 0));
      }
      else throw new AssertionError("Unexpected prepared-message fixture request: " + function.getClass().getSimpleName());
    }, (query, id, callback) -> callback.loaded(preview.get(), null))));

    preparedSend("preview"); Object first = preparedOperation(true);
    test.check(field(first, "previewView") != null, "native sheet renders the actual cached message and inline markup");
    capture("stage6-prepared-text");
    MiniAppMessagePreview messagePreview = field(first, "previewView");
    org.thunderdog.challegram.component.chat.MessageView messageView = field(messagePreview, "messageView");
    test.check(messageView.getMessage().getMessage().content == preview.get().content, "preview uses MessageContent rather than the inline discovery title");
    test.check(messageView.getMessage().getMessage().replyMarkup == markup, "preview preserves real inline keyboard markup");
    test.check(messageView.getMessage().hasHeader(), "standalone preview displays its via-bot attribution header");
    main(() -> ((android.app.Dialog) field(first, "dialog")).cancel());
    test.check("USER_DECLINED".equals(result("prepared_message_failed").getString("error")), "dismissing the preview reports cancellation once");
    test.check(sends.get() == 0, "preview cancellation never submits a message");

    clear(); preparedSend("share"); Object share = preparedOperation(true);
    main(() -> ((android.view.View) field(share, "submit")).performClick()); Thread.sleep(500);
    android.widget.LinearLayout rows = field(share, "rows");
    for (int i = 0; i < 100 && rows.getChildCount() == 0; i++) Thread.sleep(50);
    test.check(host.isMinimized() && rows.getChildCount() == 1, "target picker minimizes Mini App and honors user-only chat types");
    main(() -> rows.getChildAt(0).performClick()); capture("stage6-prepared-targets");
    main(() -> { ((android.view.View) field(share, "submit")).performClick(); ((android.view.View) field(share, "submit")).performClick(); });
    test.check(sends.get() == 1 && !has("prepared_message_sent"), "double click submits once and does not report a queued request as sent");
    test.check(outgoing.get().queryId == 99112233L && "cached-result".equals(outgoing.get().resultId) && outgoing.get().chatId == self.id && outgoing.get().topicId == null && outgoing.get().replyTo == null && !outgoing.get().hideViaBot, "outgoing inline query, result, recipient and attribution are preserved");
    TdApi.Message queued = new TdApi.Message(); queued.id = -765; queued.chatId = self.id; queued.sendingState = new TdApi.MessageSendingStatePending(0);
    pending.get().onResult(queued); Thread.sleep(150);
    test.check(!has("prepared_message_sent"), "MessageSendingStatePending is not a delivery acknowledgement");
    java.util.List<MiniAppMessageSender> senders = field(share, "senders");
    TdApi.Message delivered = new TdApi.Message(); delivered.id = 1001; delivered.chatId = self.id;
    senders.get(0).onMessageSendSucceeded(delivered, -765); Thread.sleep(600);
    test.check(has("prepared_message_sent") && !host.isMinimized(), "server success restores Mini App and delivers prepared_message_sent");
    pending.get().onResult(delivered); Thread.sleep(100);
    test.check("1".equals(test.evaluate(web, "events.filter(e=>e.event==='prepared_message_sent').length")), "duplicate transport response cannot emit a second completion");

    clear(); preparedSend("silent-comment"); Object silent = preparedOperation(true);
    main(() -> ((android.view.View) field(silent, "submit")).performClick()); Thread.sleep(400);
    android.widget.LinearLayout silentRows = field(silent, "rows");
    for (int i = 0; i < 100 && silentRows.getChildCount() == 0; i++) Thread.sleep(50);
    main(() -> {
      silentRows.getChildAt(0).performClick();
      ((android.widget.EditText) field(silent, "comment")).setText("Synthetic comment");
      ((android.view.View) field(silent, "submit")).performLongClick();
    });
    Thread.sleep(400);
    org.thunderdog.challegram.widget.PopupLayout options = field(silent, "optionsPopup");
    test.check(options != null && options.getBoundView().findViewById(org.thunderdog.challegram.R.id.btn_sendScheduledCustom) != null, "long press exposes native silent and schedule actions");
    capture("stage6-prepared-send-options");
    main(() -> options.getBoundView().findViewById(org.thunderdog.challegram.R.id.btn_sendNoSound).performClick());
    Thread.sleep(300);
    test.check(outgoing.get().options.disableNotification && outgoingComment.get() == null, "silent option reaches the transport while comment waits for acknowledgement");
    pending.get().onResult(delivered); Thread.sleep(500);
    test.check(outgoingComment.get() != null && outgoingComment.get().chatId == self.id && outgoingComment.get().options.disableNotification &&
      "Synthetic comment".equals(((TdApi.InputMessageText) outgoingComment.get().inputMessageContent).text.text), "acknowledged share sends the comment with the same recipient and options");
    test.check(has("prepared_message_sent") && !host.isMinimized(), "comment completion restores Mini App once");

    clear(); preparedSend("error"); Object rejected = preparedOperation(true);
    main(() -> ((android.view.View) field(rejected, "submit")).performClick()); Thread.sleep(350);
    android.widget.LinearLayout rejectedRows = field(rejected, "rows");
    for (int i = 0; i < 100 && rejectedRows.getChildCount() == 0; i++) Thread.sleep(50);
    main(() -> { rejectedRows.getChildAt(0).performClick(); ((android.view.View) field(rejected, "submit")).performClick(); });
    pending.get().onResult(new TdApi.Error(400, "Synthetic send rejection")); Thread.sleep(200); main(host::restore); Thread.sleep(400);
    test.check("MESSAGE_SEND_FAILED".equals(result("prepared_message_failed").getString("error")) && !has("prepared_message_sent"), "failed sending reports MESSAGE_SEND_FAILED without false success");

    clear(); preparedSend("late"); main(host::minimize); Thread.sleep(300); main(host::restore); Thread.sleep(400);
    fetch.get().onResult(new TdApi.Error(400, "Late result")); Thread.sleep(100);
    test.check("USER_DECLINED".equals(result("prepared_message_failed").getString("error")), "closing during fetch cancels the operation");
    test.check("1".equals(test.evaluate(web, "events.filter(e=>e.event==='prepared_message_failed').length")), "late fetch callback is ignored after cancellation");
    clear(); preparedSend("expired");
    test.check("MESSAGE_EXPIRED".equals(result("prepared_message_failed").getString("error")), "expired prepared messages never open a preview");

    TdApi.MessageText linkedText = new TdApi.MessageText(new TdApi.FormattedText("Synthetic link message", null), null, new TdApi.LinkPreviewOptions(false, "https://example.org/", false, false, true));
    preview.set(new MiniAppInlinePreview(linkedText, markup));
    clear(); preparedSend("link"); Object link = preparedOperation(true); capture("stage6-prepared-link");
    test.check(linkRequests.get() == 1 && linkedText.linkPreview != null && "Synthetic link card".equals(linkedText.linkPreview.title), "uncached inline webpage content is resolved before displaying the preview");
    main(() -> ((android.app.Dialog) field(link, "dialog")).cancel());

    // Exercise rich content through the same ordinary renderer, without reading or sending personal media.
    preview.set(new MiniAppInlinePreview(new TdApi.MessageContact(new TdApi.Contact("+10000000000", "Synthetic", "Contact", "", 0)), markup));
    clear(); preparedSend("contact"); Object contact = preparedOperation(true); capture("stage6-prepared-contact");
    test.check(field(contact, "previewView") != null, "contact preview uses a native contact bubble");
    main(() -> ((android.app.Dialog) field(contact, "dialog")).cancel());
    File image = new File(test.getTargetContext().getCacheDir(), "miniapp-prepared-fixture.png");
    Bitmap bitmap = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.rgb(54, 133, 213));
    try (FileOutputStream output = new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); } finally { bitmap.recycle(); }
    try {
      TdApi.File file = new TdApi.File(2147483600, image.length(), image.length(), new TdApi.LocalFile(image.getPath(), false, false, false, true, 0, image.length(), image.length()), new TdApi.RemoteFile("", "", false, false, 0));
      TdApi.Photo photo = new TdApi.Photo(false, null, new TdApi.PhotoSize[] {new TdApi.PhotoSize("x", file, 360, 240, null)});
      preview.set(new MiniAppInlinePreview(new TdApi.MessagePhoto(photo, null, new TdApi.FormattedText("Synthetic photo caption", null), false, false, false), markup));
      clear(); preparedSend("photo"); Object photoOperation = preparedOperation(true);
      MiniAppMessagePreview photoPreview = field(photoOperation, "previewView");
      // Check rendered pixels, not merely that a view exists: receivers must load the actual photo.
      boolean photoVisible = false;
      for (int i = 0; i < 100 && !photoVisible; i++) {
        AtomicReference<Boolean> visiblePhoto = new AtomicReference<>(false);
        main(() -> {
          Bitmap rendered = Bitmap.createBitmap(photoPreview.getWidth(), photoPreview.getHeight(), Bitmap.Config.ARGB_8888);
          try {
            photoPreview.draw(new android.graphics.Canvas(rendered));
            int bluePixels = 0;
            for (int y = 0; y < rendered.getHeight(); y += 8) for (int x = 0; x < rendered.getWidth(); x += 8) {
              if ((rendered.getPixel(x, y) & 0xffffff) == 0x3685d5) bluePixels++;
            }
            visiblePhoto.set(bluePixels > 100);
          } finally { rendered.recycle(); }
        });
        photoVisible = visiblePhoto.get(); if (!photoVisible) Thread.sleep(100);
      }
      test.check(photoVisible, "native preview renders the actual synthetic photo pixels and caption");
      capture("stage6-prepared-photo");
      int orientation = activity.getRequestedOrientation();
      try {
        main(() -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)); Thread.sleep(900);
        capture("stage6-prepared-landscape");
        main(() -> {
          android.graphics.Rect visible = new android.graphics.Rect(), button = new android.graphics.Rect();
          ((android.app.Dialog) field(photoOperation, "dialog")).getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
          ((android.view.View) field(photoOperation, "submit")).getGlobalVisibleRect(button);
          test.check(!button.isEmpty() && button.bottom <= visible.bottom, "preview share button remains visible in landscape");
        });
      } finally { main(() -> activity.setRequestedOrientation(orientation)); Thread.sleep(700); }
      main(() -> ((android.app.Dialog) field(photoOperation, "dialog")).cancel());
    } finally { image.delete(); }
    preparedSenderRaceCheck(self.id);
  }

  private void preparedSenderRaceCheck (long chatId) throws Exception {
    AtomicReference<org.drinkless.tdlib.Client.ResultHandler> response = new AtomicReference<>();
    java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
    MiniAppMessageSender sender = new MiniAppMessageSender(tdlib, chatId, new TdApi.SendInlineQueryResultMessage(chatId, null, null, Td.newSendOptions(), 1, "synthetic", false), (request, result) -> response.set(result), error -> callbacks.incrementAndGet());
    sender.send(); TdApi.Message delivered = new TdApi.Message(); delivered.chatId = chatId; delivered.id = 19;
    sender.onMessageSendSucceeded(delivered, -19);
    TdApi.Message queued = new TdApi.Message(); queued.chatId = chatId; queued.id = -19; queued.sendingState = new TdApi.MessageSendingStatePending();
    response.get().onResult(queued); Thread.sleep(100);
    test.check(callbacks.get() == 1, "delivery update arriving before send response is retained"); sender.cancel();
  }

  private void preparedSend (String id) throws Exception {
    main(() -> set(host, "lastInteraction", android.os.SystemClock.uptimeMillis()));
    send("web_app_send_prepared_message", "{\"id\":" + JSONObject.quote(id) + "}");
  }
  private Object preparedOperation (boolean withPreview) throws Exception {
    for (int i = 0; i < 100; i++) {
      Object service = field(host, "preparedMessage"), operation = service != null ? field(service, "operation") : null;
      if (operation != null && field(operation, withPreview ? "previewView" : "dialog") != null) { Thread.sleep(300); return operation; }
      Thread.sleep(100);
    }
    throw new AssertionError("Prepared message UI missing");
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
    test.waitForIdleSync(); Thread.sleep(350);
    Bitmap image = test.getUiAutomation().takeScreenshot(); File dir = new File(test.getTargetContext().getExternalFilesDir(null), "miniapp-contracts"); dir.mkdirs();
    try (FileOutputStream output = new FileOutputStream(new File(dir, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, output); } finally { image.recycle(); }
  }
  private void main (Runnable task) { AtomicReference<Throwable> error = new AtomicReference<>(); test.runOnMainSync(() -> { try { task.run(); } catch (Throwable e) { error.set(e); } }); if (error.get() != null) throw new AssertionError(error.get()); }
  @SuppressWarnings("unchecked") private static <T> T field (Object obj, String name) { try { Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(obj); } catch (Exception e) { throw new AssertionError(e); } }
  private static void set (Object obj, String name, Object value) { try { Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); f.set(obj, value); } catch (Exception e) { throw new AssertionError(e); } }
  private static void invoke (Object obj, String name) { try { Method m = obj.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(obj); } catch (Exception e) { throw new AssertionError(e); } }
}
