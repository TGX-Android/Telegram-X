/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.drinkless.tdlib.TdApi;
import org.json.JSONObject;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import java.util.ArrayList;
import static org.thunderdog.challegram.miniapps.MiniAppTheme.object;

/** Server-authorized actions. Replies describe the real result, never enqueueing a TDLib request alone. */
final class MiniAppActions {
  private final MiniAppController host;
  private final SharedPreferences downloads;
  private final DownloadManager downloadManager;
  private BroadcastReceiver shortcutReceiver;
  private boolean checkingDownload, emojiPending;
  private int generation;
  MiniAppActions (MiniAppController host) {
    this.host = host;
    downloads = host.context().getSharedPreferences("miniapp-downloads-" + host.storageScope(), Context.MODE_PRIVATE);
    downloadManager = (DownloadManager) host.context().getSystemService(Context.DOWNLOAD_SERVICE);
  }

  static boolean validFileName (String name) {
    return MiniAppActionPolicy.fileName(name);
  }
  private void downloadReply (MiniAppDocument.Request origin, boolean started) { host.reply(origin, "file_download_requested", object("status", started ? "downloading" : "cancelled")); }
  void download (MiniAppDocument.Request origin, JSONObject data) {
    if (!host.recentGesture() || checkingDownload) return;
    String url = data.optString("url"), name = data.optString("file_name");
    if (MiniAppOrigin.of(url) == null || !validFileName(name) || downloadManager == null) { downloadReply(origin, false); return; }
    checkingDownload = true;
    final int captured = generation;
    host.tdlib().send(new TdApi.CheckWebAppFileDownload(host.botId(), name, url), (ok, error) -> host.tdlib().ui().post(() -> {
      if (captured != generation || !host.current(origin)) return;
      checkingDownload = false;
      if (error != null) { downloadReply(origin, false); return; }
      View file = fileRow(name, url);
      host.nativePrompt(origin, Lang.getString(R.string.MiniAppDownloadTitle), Lang.getString(R.string.MiniAppDownloadRequest, host.botName()), R.string.MiniAppDownload, file, () -> {
        Runnable enqueue = () -> {
          if (!host.current(origin) || captured != generation) return;
          try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url)).setTitle(host.botName()).setDescription(name)
              .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
              .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            long id = downloadManager.enqueue(request);
            downloads.edit().putString(Long.toString(id), name).apply();
            host.showDownloadBulletin(this, id, name);
            downloadReply(origin, true);
          } catch (RuntimeException failure) { downloadReply(origin, false); }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || !host.context().permissions().requestPermissions((me.vkryl.core.lambda.RunnableBool) allowed -> {
          if (allowed) enqueue.run(); else downloadReply(origin, false);
        }, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) enqueue.run();
      }, () -> downloadReply(origin, false));
    }));
  }
  private View fileRow (String name, String url) {
    LinearLayout row = new LinearLayout(host.context()); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Screen.dp(22), 0, Screen.dp(22), Screen.dp(12));
    ImageView icon = new ImageView(host.context()); icon.setImageResource(R.drawable.baseline_insert_drive_file_24); icon.setColorFilter(android.graphics.Color.WHITE); icon.setScaleType(ImageView.ScaleType.CENTER);
    android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable(); circle.setShape(android.graphics.drawable.GradientDrawable.OVAL); circle.setColor(Theme.getColor(ColorId.fillingPositive)); icon.setBackground(circle);
    row.addView(icon, new LinearLayout.LayoutParams(Screen.dp(44), Screen.dp(44)));
    LinearLayout labels = new LinearLayout(host.context()); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(Screen.dp(10), 0, 0, 0);
    TextView title = new TextView(host.context()); title.setText(name); title.setTextSize(15); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setTextColor(Theme.getColor(ColorId.textLink));
    labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
    TextView subtitle = new TextView(host.context()); subtitle.setText("…"); subtitle.setTextSize(12); subtitle.setTextColor(Theme.getColor(ColorId.textLight));
    labels.addView(subtitle, new LinearLayout.LayoutParams(-1, -2)); row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    final int captured = generation;
    new Thread(() -> {
      String detail = Lang.getString(R.string.File);
      java.net.HttpURLConnection connection = null;
      try {
        connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000); connection.setRequestMethod("HEAD");
        connection.connect();
        String mime = connection.getContentType();
        if (mime != null) mime = mime.split(";")[0].trim();
        String ext = mime != null ? android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) : null;
        String length = connection.getHeaderField("Content-Length");
        long size = length != null ? Long.parseLong(length) : -1;
        detail = (size > 0 ? "~" + org.thunderdog.challegram.tool.Strings.buildSize(size) : "") + (ext != null ? " " + ext.toUpperCase(java.util.Locale.US) : "");
        if (detail.trim().isEmpty()) detail = Lang.getString(R.string.File);
      } catch (Exception ignored) { } finally { if (connection != null) connection.disconnect(); }
      final String label = detail.trim();
      host.tdlib().ui().post(() -> { if (captured == generation && row.isAttachedToWindow()) subtitle.setText(label); });
    }, "MiniAppFileMetadata").start();
    return row;
  }
  void showDownloads () {
    host.showDownloadsMenu(this);
  }
  static final class Download {
    final long id, total, loaded;
    final String name;
    final int status;
    Download (long id, String name, int status, long total, long loaded) { this.id = id; this.name = name; this.status = status; this.total = total; this.loaded = loaded; }
    boolean running () { return status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED; }
    String description () {
      if (status == DownloadManager.STATUS_FAILED) return Lang.getString(R.string.MiniAppDownloadFailed);
      if (!running()) return org.thunderdog.challegram.tool.Strings.buildSize(Math.max(0, total));
      String result = org.thunderdog.challegram.tool.Strings.buildSize(Math.max(0, loaded));
      return total > 0 ? result + " / " + org.thunderdog.challegram.tool.Strings.buildSize(total) : result;
    }
  }
  Download download (long id, String name) {
    if (downloadManager == null) return null;
    try (Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id))) {
      if (cursor == null || !cursor.moveToFirst()) { downloads.edit().remove(Long.toString(id)).apply(); return null; }
      return new Download(id, name, cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)), cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)), cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
    } catch (RuntimeException ignored) { return null; }
  }
  ArrayList<Download> downloads () {
    ArrayList<Download> files = new ArrayList<>();
    for (java.util.Map.Entry<String, ?> entry : downloads.getAll().entrySet()) {
      try {
        Download file = download(Long.parseLong(entry.getKey()), String.valueOf(entry.getValue()));
        if (file != null) files.add(file);
      } catch (RuntimeException ignored) { }
    }
    java.util.Collections.sort(files, (a, b) -> Long.compare(b.id, a.id));
    return files;
  }
  void cancelDownload (long id) {
    if (downloadManager != null && downloads.contains(Long.toString(id))) { downloadManager.remove(id); downloads.edit().remove(Long.toString(id)).apply(); }
  }
  void openDownload (long id) {
    if (downloadManager == null || !downloads.contains(Long.toString(id))) return;
    Uri uri = downloadManager.getUriForDownloadedFile(id);
    if (uri == null) return;
    String type = downloadManager.getMimeTypeForDownloadedFile(id);
    try { host.context().startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type != null ? type : "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
    catch (android.content.ActivityNotFoundException ignored) { UI.showToast(R.string.NoAppToOpen, android.widget.Toast.LENGTH_SHORT); }
  }

  String shortcutStatus () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "unsupported";
    ShortcutManager manager = host.context().getSystemService(ShortcutManager.class);
    if (manager == null || !manager.isRequestPinShortcutSupported()) return "unsupported";
    for (ShortcutInfo info : manager.getPinnedShortcuts()) if (info.getId().equals(shortcutId())) return "added";
    return "missed";
  }
  private String shortcutId () { return "miniapp-" + host.storageScope(); }
  void addShortcut (MiniAppDocument.Request origin) {
    if (!host.recentGesture()) return;
    unregisterShortcut();
    if ("added".equals(shortcutStatus())) { host.reply(origin, "home_screen_added", object()); return; }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || "unsupported".equals(shortcutStatus())) {
      host.reply(origin, "home_screen_failed", object("error", "UNSUPPORTED")); return;
    }
    ShortcutManager manager = host.context().getSystemService(ShortcutManager.class);
    Intent intent = new Intent(host.context(), MainActivity.class).setAction(host.context().getPackageName() + ".MINI_APP_SHORTCUT")
      .putExtra("account_id", host.tdlib().id()).putExtra("miniapp_owner", host.tdlib().myUserId()).putExtra("miniapp_bot", host.botId()).putExtra("miniapp_test", host.tdlib().account().isDebug())
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    String action = host.context().getPackageName() + ".MINI_APP_PIN." + java.util.UUID.randomUUID();
    shortcutReceiver = new BroadcastReceiver() {
      @Override public void onReceive (Context context, Intent intent) {
        unregisterShortcut();
        // The launcher sends this PendingIntent only after accepting the pin.
        host.reply(origin, "home_screen_added", object());
      }
    };
    androidx.core.content.ContextCompat.registerReceiver(host.context(), shortcutReceiver, new IntentFilter(action), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    PendingIntent callback = PendingIntent.getBroadcast(host.context(), 0, new Intent(action).setPackage(host.context().getPackageName()), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    ShortcutInfo info = new ShortcutInfo.Builder(host.context(), shortcutId()).setShortLabel(host.botName())
      .setIcon(shortcutIcon()).setIntent(intent).build();
    try {
      if (!manager.requestPinShortcut(info, callback.getIntentSender())) { unregisterShortcut(); host.reply(origin, "home_screen_failed", object("error", "UNSUPPORTED")); }
    } catch (RuntimeException error) { unregisterShortcut(); host.reply(origin, "home_screen_failed", object("error", "UNSUPPORTED")); }
  }
  private void unregisterShortcut () { if (shortcutReceiver != null) { host.context().unregisterReceiver(shortcutReceiver); shortcutReceiver = null; } }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
  private Icon shortcutIcon () {
    int size = Screen.dp(58);
    android.graphics.Bitmap result = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(result);
    TdApi.User bot = host.tdlib().cache().user(host.botId());
    android.graphics.Bitmap photo = bot != null && bot.profilePhoto != null && bot.profilePhoto.small.local.isDownloadingCompleted ?
      android.graphics.BitmapFactory.decodeFile(bot.profilePhoto.small.local.path) : null;
    if (photo != null) {
      android.graphics.BitmapShader shader = new android.graphics.BitmapShader(photo, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
      android.graphics.Matrix matrix = new android.graphics.Matrix(); float scale = (float) size / Math.min(photo.getWidth(), photo.getHeight());
      matrix.setScale(scale, scale); matrix.postTranslate((size - photo.getWidth() * scale) / 2, (size - photo.getHeight() * scale) / 2); shader.setLocalMatrix(matrix);
      android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); paint.setShader(shader); canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint); photo.recycle();
    } else {
      org.thunderdog.challegram.widget.AvatarView avatar = new org.thunderdog.challegram.widget.AvatarView(host.context());
      avatar.setUser(host.tdlib(), host.botId(), false); avatar.layout(0, 0, size, size); avatar.draw(canvas); avatar.performDestroy();
    }
    return Icon.createWithBitmap(result);
  }

  void emojiStatus (MiniAppDocument.Request origin, JSONObject data) {
    if (!host.recentGesture() || emojiPending) return;
    long id;
    try { id = Long.parseLong(data.getString("custom_emoji_id")); } catch (Exception error) { emojiError(origin, "SUGGESTED_EMOJI_INVALID"); return; }
    long duration = data.optLong("duration", 0);
    if (id == 0) { emojiError(origin, "SUGGESTED_EMOJI_INVALID"); return; }
    if (duration < 0 || duration > Integer.MAX_VALUE - host.tdlib().currentTimeMillis() / 1000) { emojiError(origin, "DURATION_INVALID"); return; }
    emojiPending = true; final int captured = generation;
    host.tdlib().send(new TdApi.GetCustomEmojiStickers(new long[] {id}), (stickers, error) -> host.tdlib().ui().post(() -> {
      if (captured != generation || !host.current(origin)) return;
      emojiPending = false;
      if (error != null || stickers.stickers.length == 0) { emojiError(origin, "SUGGESTED_EMOJI_INVALID"); return; }
      MiniAppEmojiPreview preview = new MiniAppEmojiPreview(host, id);
      String message = Lang.getString(R.string.MiniAppEmojiRequest, host.botName());
      if (duration > 0) message += "\n" + Lang.getString(R.string.MiniAppEmojiDuration, emojiDuration((int) duration));
      host.nativePrompt(origin, "", message, R.string.MiniAppEmojiConfirm, preview, () -> {
        if (!host.tdlib().hasPremium()) { premium(); emojiError(origin, "USER_DECLINED"); return; }
        emojiPending = true;
        TdApi.EmojiStatus status = new TdApi.EmojiStatus(new TdApi.EmojiStatusTypeCustomEmoji(id), duration == 0 ? 0 : (int) (host.tdlib().currentTimeMillis() / 1000 + duration));
        host.tdlib().send(new TdApi.SetEmojiStatus(status), (ok, setError) -> host.tdlib().ui().post(() -> {
          emojiPending = false;
          if (setError == null) host.reply(origin, "emoji_status_set", object()); else emojiError(origin, "SERVER_ERROR");
        }));
      }, () -> emojiError(origin, "USER_DECLINED"));
    }));
  }
  private void emojiError (MiniAppDocument.Request origin, String error) { host.reply(origin, "emoji_status_failed", object("error", error)); }
  private String emojiDuration (int seconds) {
    ArrayList<String> parts = new ArrayList<>();
    int days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = Math.round(seconds % 3600 / 60f);
    if (days > 0) parts.add(Lang.plural(R.string.xDays, days));
    if (hours > 0) parts.add(Lang.plural(R.string.xHours, hours));
    if (minutes > 0) parts.add(Lang.plural(R.string.xMinutes, minutes));
    return android.text.TextUtils.join(" ", parts);
  }
  private void premium () { host.tdlib().ui().openUrl(host, "tg://premium_offer?ref=emoji_status", null); }
  void emojiAccess (MiniAppDocument.Request origin) {
    if (!host.recentGesture() || emojiPending) return;
    emojiPending = true; final int captured = generation;
    host.tdlib().send(new TdApi.GetUserFullInfo(host.botId()), (full, error) -> host.tdlib().ui().post(() -> {
      if (captured != generation || !host.current(origin)) return;
      emojiPending = false;
      if (error != null) { emojiAccessReply(origin, false); return; }
      if (full.botInfo != null && full.botInfo.canManageEmojiStatus) { emojiAccessReply(origin, true); return; }
      MiniAppBotPermissions.markEmojiRequested(host.context(), host.tdlib(), host.botId());
      host.nativePrompt(origin, "", Lang.getString(R.string.MiniAppEmojiPermission, host.botName()), R.string.Allow, new MiniAppEmojiPreview(host, 0), () -> {
        if (!host.tdlib().hasPremium()) { premium(); emojiAccessReply(origin, false); return; }
        emojiPending = true;
        host.tdlib().send(new TdApi.ToggleBotCanManageEmojiStatus(host.botId(), true), (ok, allowError) -> host.tdlib().ui().post(() -> {
          emojiPending = false; emojiAccessReply(origin, allowError == null);
        }));
      }, () -> emojiAccessReply(origin, false));
    }));
  }
  private void emojiAccessReply (MiniAppDocument.Request origin, boolean allowed) { host.reply(origin, "emoji_status_access_requested", object("status", allowed ? "allowed" : "cancelled")); }
  void cancel () { generation++; checkingDownload = emojiPending = false; unregisterShortcut(); }
}
