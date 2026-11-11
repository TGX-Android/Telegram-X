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
 * File created on 10/11/2026
 */
package org.thunderdog.challegram.telegram;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import me.vkryl.core.StringUtils;

// Copies of Telegram Call for the pickers of the system, which list only the media store: in
// Ringtones, and in Notifications for the pages of channels of One UI. Made once, never again
public final class TelegramCallRingtone {
  private static final String KEY_INSTALLED = "telegram_call_installed";
  private static final String KEY_RINGTONE = "telegram_call_ringtone";
  private static final String KEY_NOTIFICATION = "telegram_call_notification";
  private static final String FILE_NAME = "Telegram Call.ogg";
  private static final String TITLE = "Telegram Call";
  private static final String MIME_TYPE = "audio/ogg";

  private static boolean isAttempted;

  private TelegramCallRingtone () { }

  public static synchronized void install () {
    // Once a run of the app at most, so that a failure costs no work at each picker
    if (isAttempted || Settings.instance().getBoolean(KEY_INSTALLED, false)) {
      return;
    }
    Context context = AppContext.get();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      isAttempted = true;
      boolean isRingtoneDone = copyToMediaStore(context, Environment.DIRECTORY_RINGTONES, true);
      boolean isNotificationDone =
        copyToMediaStore(context, Environment.DIRECTORY_NOTIFICATIONS, false);
      if (isRingtoneDone && isNotificationDone) {
        Settings.instance().putBoolean(KEY_INSTALLED, true);
      }
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
          PackageManager.PERMISSION_GRANTED) {
        return;
      }
      isAttempted = true;
      copyToFile(context, Environment.DIRECTORY_RINGTONES, KEY_RINGTONE);
      copyToFile(context, Environment.DIRECTORY_NOTIFICATIONS, KEY_NOTIFICATION);
    }
  }

  public static @Nullable Uri getListedCopy () {
    // Pickers list only ringtones
    String ringtone = Settings.instance().getString(KEY_RINGTONE, null);
    if (StringUtils.isEmpty(ringtone)) {
      return null;
    }
    Uri uri = Uri.parse(ringtone);
    return hasCopy(uri, MediaStore.Audio.Media.IS_RINGTONE + " = 1 AND ") ? uri : null;
  }

  private static boolean hasCopy (Uri uri, String condition) {
    String selection = condition + MediaStore.MediaColumns.DISPLAY_NAME + " = ?";
    try (Cursor cursor = AppContext.get().getContentResolver().query(uri,
      new String[] {MediaStore.MediaColumns._ID}, selection, new String[] {FILE_NAME}, null)) {
      return cursor != null && cursor.moveToFirst();
    } catch (Throwable t) {
      Log.e("Unable to find the copy of Telegram Call", t);
      return false;
    }
  }

  public static boolean isTelegramCall (@Nullable Uri uri) {
    if (uri == null) {
      return false;
    }
    if (uri.equals(TdlibNotificationManager.getTelegramCallRingtone())) {
      return true;
    }
    if (!isExternalMedia(uri)) {
      return false;
    }
    // Pickers add the title to the URIs of their lists, by which a copy of an earlier install,
    // out of this one's sight, is Telegram Call too, and an id given to another file isn't
    String title = uri.getQueryParameter(MediaStore.MediaColumns.TITLE);
    return title != null ? TITLE.equals(title) :
      isCopy(uri, KEY_RINGTONE) || isCopy(uri, KEY_NOTIFICATION);
  }

  private static boolean isExternalMedia (Uri uri) {
    // Ids of the internal volume, of the ringtones of the system, are counted apart
    return MediaStore.AUTHORITY.equals(uri.getAuthority()) && !uri.getPathSegments().isEmpty() &&
      uri.getPathSegments().get(0).startsWith("external");
  }

  private static boolean isCopy (Uri uri, String key) {
    String copy = Settings.instance().getString(key, null);
    String id = StringUtils.isEmpty(copy) ? null : Uri.parse(copy).getLastPathSegment();
    return id != null && id.equals(uri.getLastPathSegment());
  }

  private static void onCopyMade (String key, Uri uri) {
    Settings.instance().putString(key, uri.toString());
    if (!StringUtils.isEmpty(Settings.instance().getString(KEY_RINGTONE, null)) &&
      !StringUtils.isEmpty(Settings.instance().getString(KEY_NOTIFICATION, null))) {
      Settings.instance().putBoolean(KEY_INSTALLED, true);
    }
  }

  private static void copyToFile (Context context, String directory, String key) {
    try {
      File dir = Environment.getExternalStoragePublicDirectory(directory);
      File file = new File(dir, FILE_NAME);
      if (!file.exists()) {
        if (!dir.exists() && !dir.mkdirs()) {
          return;
        }
        try (InputStream in = context.getResources().openRawResource(R.raw.telegram_call);
             OutputStream out = new FileOutputStream(file)) {
          copy(in, out);
        }
      }
      MediaScannerConnection.scanFile(context, new String[] {file.getPath()},
        new String[] {MIME_TYPE}, (path, uri) -> {
          if (uri != null) {
            onCopyMade(key, uri);
          }
        });
    } catch (Throwable t) {
      Log.e("Unable to copy Telegram Call into %s", t, directory);
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private static boolean copyToMediaStore (Context context, String directory,
    boolean isRingtone) {
    String key = isRingtone ? KEY_RINGTONE : KEY_NOTIFICATION;
    if (!StringUtils.isEmpty(Settings.instance().getString(key, null))) {
      return true;
    }
    ContentResolver resolver = context.getContentResolver();
    Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    String relativePath = directory + "/";
    String selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " +
      MediaStore.MediaColumns.RELATIVE_PATH + " = ?";
    try (Cursor cursor = resolver.query(collection, new String[] {MediaStore.MediaColumns._ID},
      selection, new String[] {FILE_NAME, relativePath}, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        onCopyMade(key, ContentUris.withAppendedId(collection, cursor.getLong(0)));
        return true;
      }
    } catch (Throwable t) {
      Log.e("Unable to look for Telegram Call in %s", t, directory);
      return false;
    }
    Uri uri = null;
    try {
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
      values.put(MediaStore.MediaColumns.TITLE, TITLE);
      values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
      values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
      values.put(isRingtone ? MediaStore.Audio.Media.IS_RINGTONE :
        MediaStore.Audio.Media.IS_NOTIFICATION, 1);
      values.put(MediaStore.MediaColumns.IS_PENDING, 1);
      uri = resolver.insert(collection, values);
      if (uri == null) {
        return false;
      }
      try (InputStream in = context.getResources().openRawResource(R.raw.telegram_call);
           OutputStream out = resolver.openOutputStream(uri)) {
        if (out == null) {
          throw new IllegalStateException("No stream of " + uri);
        }
        copy(in, out);
      }
      values.clear();
      values.put(MediaStore.MediaColumns.IS_PENDING, 0);
      resolver.update(uri, values, null, null);
      String displayName = getDisplayName(resolver, uri);
      if (displayName != null && !FILE_NAME.equals(displayName)) {
        // Renamed, as one of an earlier install of the app is there out of its sight: none twice
        resolver.delete(uri, null, null);
        return true;
      }
      onCopyMade(key, uri);
      return true;
    } catch (Throwable t) {
      Log.e("Unable to copy Telegram Call into %s", t, directory);
      if (uri != null) {
        try {
          resolver.delete(uri, null, null);
        } catch (Throwable ignored) { }
      }
      return false;
    }
  }

  private static @Nullable String getDisplayName (ContentResolver resolver, Uri uri) {
    try (Cursor cursor = resolver.query(uri,
      new String[] {MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
      return cursor != null && cursor.moveToFirst() ? cursor.getString(0) : null;
    }
  }

  private static void copy (InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
  }
}
