/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 05/05/2015 at 10:45
 */
package org.thunderdog.challegram;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.EnvironmentCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.UnrecognizedInputFormatException;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.loader.ImageStrictCache;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibDataSource;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.TextController;
import org.thunderdog.challegram.util.AppBuildInfo;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;

import me.vkryl.android.SdkVersion;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.util.LocalVar;
import me.vkryl.td.Td;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

@SuppressWarnings ("JniMissingFunction")
public class U {

  public static boolean blurBitmap (Bitmap bitmap, int radius, int unpin) {
    return U.isValidBitmap(bitmap) && N.blurBitmap(bitmap, radius, unpin, 0) == 0;
  }

  private static Boolean isAppSideLoaded;

  public static boolean isAppSideLoaded () {
    return (isAppSideLoaded != null ? isAppSideLoaded : (isAppSideLoaded = isAppSideLoadedImpl()));
  }

  public static int getHeading (Location location) {
    if (location.hasBearing()) {
      int heading = MathUtils.modulo(Math.round(location.getBearing()), 360);
      return heading != 0 ? heading : 360;
    }
    return 0;
  }

  public static final String VENDOR_GOOGLE_PLAY = "com.android.vending";

  @Nullable
  public static String getInstallerPackageName () {
    try {
      String packageName = UI.getAppContext().getPackageName();
      String installerPackageName = UI.getAppContext().getPackageManager().getInstallerPackageName(packageName);
      if (StringUtils.isEmpty(installerPackageName)) {
        return null;
      }
      return installerPackageName;
    } catch (Throwable t) {
      Log.v("Unable to determine installer package", t);
      return null;
    }
  }

  private static boolean isAppSideLoadedImpl () {
    String installerId = getInstallerPackageName();
    return StringUtils.isEmpty(installerId) || !VENDOR_GOOGLE_PLAY.equals(installerId);
  }

  public static String gzipFileToString (String path) {
    try (BufferedSource buffer = Okio.buffer(Okio.source(new GZIPInputStream(new FileInputStream(new File(path)))))) {
      return buffer.readString(StringUtils.UTF_8);
    } catch (Throwable t) {
      Log.w(Log.TAG_GIF_LOADER, "Cannot decode GZip, path: %s", t, path);
      return null;
    }
  }

  public static long getLongOrInt (Cursor c, int columnIndex) {
    try {
      return c.getLong(columnIndex);
    } catch (Throwable ignored) {
      return c.getInt(columnIndex);
    }
  }

  public static boolean isRoaming() {
    try {
      ConnectivityManager cm = (ConnectivityManager) UI.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        android.net.Network network = cm.getActiveNetwork();
        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return !capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
      } else {
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isRoaming();
      }
    } catch (Throwable t) {
      Log.e("Unable to detect roaming", t);
    }
    return false;
  }

  public static boolean isImeDone (int actionId, KeyEvent event) {
    switch (actionId) {
      case EditorInfo
        .IME_NULL:
        return event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
      case EditorInfo.IME_ACTION_DONE:
        return true;
    }
    return false;
  }

  public static String getRingtoneName (@Nullable String ringtoneUri, @Nullable String fallbackName) {
    if (StringUtils.isEmpty(ringtoneUri))
      return ringtoneUri;
    return getRingtoneName(Uri.parse(ringtoneUri), fallbackName);
  }

  public static String getRingtoneName (@Nullable Uri ringtoneUri, @Nullable String fallbackName) {
    String[] projection = {MediaStore.MediaColumns.TITLE};
    try {
      try (Cursor cur = UI.getContext().getContentResolver().query(ringtoneUri, projection, null, null, null)) {
        if (cur != null && cur.moveToFirst()) {
          String title = cur.getString(0);
          Log.i("Success: %s -> %s", ringtoneUri, title);
          return title;
        }
      } catch (Throwable t) {
        Log.e("Couldn't get ringtone name for %s", t, ringtoneUri);
      }
    } catch (Throwable t) {
      Log.e("Error querying %s", t, ringtoneUri);
    }
    return fallbackName;
  }

  public static boolean isScreenshotFolder (String str) {
    if (StringUtils.isEmpty(str)) {
      return false;
    }
    str = str.toLowerCase();
    return str.contains("screencapture") || str.contains("screenshot") || str.contains("экран");
  }

  public static float maxWidth (Layout layout) {
    if (layout == null)
      return 0;
    int lineCount = layout.getLineCount();
    float max = 0f;
    for (int i = 0; i < lineCount; i++)
      max = Math.max(max, layout.getLineWidth(i));
    return max;
  }

  public static boolean isLocalhost (String server) {
    switch (server) {
      case "127.0.0.1":
      case "::1":
      case "localhost":
        return true;
    }
    return false;
  }

  public static int removeByPrefix (Map<String,?> map, String prefix) {
    List<String> itemsToRemove = null;
    for (String key : map.keySet()) {
      if (key.startsWith(prefix)) {
        if (itemsToRemove == null)
          itemsToRemove = new ArrayList<>();
        itemsToRemove.add(key);
      }
    }
    if (itemsToRemove != null) {
      for (String key : itemsToRemove) {
        map.remove(key);
      }
      return itemsToRemove.size();
    }
    return 0;
  }

  public static Location newFakeLocation () {
    Location location = new Location("network");
    location.setTime(System.currentTimeMillis());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
    }
    return location;
  }

  public static Location newFakeLocation (double latitude, double longitude) {
    Location location = newFakeLocation();
    location.setLatitude(latitude);
    location.setLongitude(longitude);
    return location;
  }

  public static float distanceBetween (double latitude1, double longitude1, double latitude2, double longitude2) {
    Location location1 = newFakeLocation(latitude1, longitude1);
    Location location2 = newFakeLocation(latitude2, longitude2);
    return Math.abs(location1.distanceTo(location2));
  }

  public static long timeSinceGenerationMs (Location location) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1000000l;
    } else {
      return System.currentTimeMillis() - location.getTime();
    }
  }

  private static File getRootOfInnerSdCardFolder (File file) {
    if (file == null) {
      return null;
    }
    final long totalSpace = file.getTotalSpace();
    while (true)  {
      final File parentFile = file.getParentFile();
      if (parentFile == null || parentFile.getTotalSpace() != totalSpace) {
        return file;
      }
      file = parentFile;
    }
  }

  public static @Nullable ArrayList<String> getExternalStorageDirectories (@Nullable String ignorePath, boolean cleanupResult) {
    ArrayList<String> results = null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { //Method 1 for KitKat & above
      File[] externalDirs = UI.getAppContext().getExternalFilesDirs(null);

      for (File file : externalDirs) {
        String path = file.getPath().split("/Android")[0];
        boolean addPath;
        String state = EnvironmentCompat.getStorageState(file);
        addPath = Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
        if (addPath && !StringUtils.equalsOrBothEmpty(ignorePath, path)) {
          if (results == null) {
            results = new ArrayList<>();
          }
          results.add(path);
        }
      }
    }

    if (results == null) { //Method 2 for all versions
      // better variation of: https://stackoverflow.com/a/40123073/5002496
      StringBuilder out = new StringBuilder();
      try {
        final Process process = new ProcessBuilder().command("mount | grep /dev/block/vold")
          .redirectErrorStream(true).start();
        process.waitFor();
        final InputStream is = process.getInputStream();
        final byte[] buffer = new byte[1024];
        while (is.read(buffer) != -1) {
          out.append(new String(buffer));
        }
        is.close();
      } catch (Throwable ignored) { }
      String output = out.toString();
      if (!output.trim().isEmpty()) {
        String[] devicePoints = output.split("\n");
        for (String voldPoint: devicePoints) {
          String path = voldPoint.split(" ")[2];
          if (!StringUtils.equalsOrBothEmpty(ignorePath, path)) {
            if (results == null) {
              results = new ArrayList<>();
            }
            results.add(path);
          }
        }
      }
    }

    if (cleanupResult && results != null) {
      //Below few lines is to remove paths which may not be external memory card, like OTG (feel free to comment them out)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        for (int i = 0; i < results.size(); i++) {
          if (!results.get(i).toLowerCase().matches(".*[0-9a-f]{4}[-][0-9a-f]{4}")) {
            // Log.d(LOG_TAG, results.get(i) + " might not be extSDcard");
            results.remove(i--);
          }
        }
      } else {
        for (int i = 0; i < results.size(); i++) {
          if (!results.get(i).toLowerCase().contains("ext") && !results.get(i).toLowerCase().contains("sdcard")) {
            // Log.d(LOG_TAG, results.get(i)+" might not be extSDcard");
            results.remove(i--);
          }
        }
      }
    }
    return results;
  }

  public static String getSecureFileName (String fileName) {
    if (StringUtils.isEmpty(fileName)) {
      return fileName;
    }
    int i = 0;
    int len = fileName.length();
    while (i < len) {
      int codePoint = fileName.codePointAt(i);
      int codePointSize = Character.charCount(codePoint);

      if (codePointSize == 1 && codePoint == '.') {
        i++;
        continue;
      }
      break;
    }
    if (len - i <= 0) {
      return "file";
    }
    StringBuilder b = new StringBuilder(len - i);
    b.append(fileName, i, len);
    i = 0;
    len = b.length();
    while (i < len) {
      int codePoint = b.codePointAt(i);
      int codePointSize = Character.charCount(codePoint);
      if (codePointSize == 1 && (codePoint == '/' || codePoint == '\\')) {
        b.setCharAt(i, '_');
      }
      i += codePointSize;
    }
    return fileName;
  }

  public static String toHexString (String str) {
    StringBuilder b = new StringBuilder();
    int len = str.length();
    for (int i = 0; i < len; i++) {
      char c = str.charAt(i);
      b.append("\\u").append(Integer.toString(c, 16).toUpperCase());
    }
    return b.toString();
  }

  public static void startForeground (Service service, int notificationId, Notification notification) {
    if (notification == null)
      throw new IllegalArgumentException();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (StringUtils.isEmpty(notification.getChannelId()))
        throw new IllegalArgumentException("id == " + notificationId);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      int knownType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE;
      switch (notificationId) {
        case TdlibNotificationManager.ID_MUSIC:
          knownType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
          break;
        case TdlibNotificationManager.ID_LOCATION:
          knownType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
          break;
        case TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION:
        case TdlibNotificationManager.ID_INCOMING_CALL_NOTIFICATION:
          knownType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;
          break;
        case TdlibNotificationManager.ID_PENDING_TASK:
          knownType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
          break;
      }
      if (knownType != android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) {
        service.startForeground(notificationId, notification, knownType);
        return;
      }
    }
    service.startForeground(notificationId, notification);
  }

  public static void stopForeground (Service service, boolean removeNotification, int... notificationIds) {
    service.stopForeground(removeNotification);
    if (removeNotification) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        NotificationManager manager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
          for (int notificationId : notificationIds) {
            manager.cancel(notificationId);
          }
        }
      }
    }
  }

  public static boolean isVertical (int width, int height, int rotation) {
    return isRotated(rotation) ? width > height : height > width;
  }

  public static boolean isInside (float x, float y, float cx, float cy, float radius) {
    return Math.pow(x - cx, 2) + Math.pow(y - cy, 2) < radius * radius;
  }

  public static boolean isInsideAreaOf (float targetX, float targetY, float x, float y, float radius) {
    return x >= targetX - radius && x <= targetX + radius && y >= targetY - radius && y <= targetY + radius;
  }

  public static int getWidth (Bitmap bitmap, int rotation) {
    return isRotated(rotation) ? bitmap.getHeight() : bitmap.getWidth();
  }

  public static float getWidth (Layout layout) {
    return layout != null && layout.getLineCount() > 0 ? layout.getLineWidth(0) : 0;
  }

  public static int getHeight (Bitmap bitmap, int rotation) {
    return isRotated(rotation) ? bitmap.getWidth() : bitmap.getHeight();
  }


  public static boolean isRotated (int degrees) {
    return MathUtils.modulo(degrees, 180) == 90;
  }

  public static boolean isRotated (float degrees) {
    return MathUtils.modulo(degrees, 180f) == 90f;
  }

  public static void setTextColor (Layout layout, int color) {
    if (layout != null) {
      layout.getPaint().setColor(color);
    }
  }

  public static Layout createLayout (CharSequence in, int width, TextPaint p) {
    return createLayout(in, width, p, Layout.Alignment.ALIGN_NORMAL);
  }

  public static Layout createLayout (CharSequence in, int width, TextPaint p, Layout.Alignment alignment) {
    BoringLayout.Metrics metrics = BoringLayout.isBoring(in, p);
    if (metrics != null && metrics.width <= width) {
      return new BoringLayout(in, p, width, alignment, 1.0f, 0f, metrics, false);
    } else {
      return new StaticLayout(in, 0, in.length(), p, width, alignment, 1.0f, 0, false);
    }
  }

  public static int calculateTextHeight (CharSequence input, int width, float textSize, @Nullable HashMap<CharSequence, int[]> out) {
    if (width > 0) {
      int[] calcs = out != null ? out.get(input) : null;
      if (calcs == null || calcs[0] != width) {
        boolean needPut = calcs == null;
        if (needPut) {
          calcs = new int[2];
          if (out != null) {
            out.put(input, calcs);
          }
        }
        calcs[0] = width;
        calcs[1] = calculateTextHeight(input, width, textSize);
      }
      return calcs[1];
    }
    return 0;
  }

  private static SoftReference<TextView> cachedTextView;

  public static int calculateTextHeight (CharSequence input, int width, float textSize) {
    TextView textView = cachedTextView != null ? cachedTextView.get() : null;
    if (textView == null) {
      textView = new NoScrollTextView(UI.getUiContext());
      textView.setTypeface(Fonts.getRobotoRegular());
      textView.setPadding(0, 0, 0, 0);
      //cachedTextView = new SoftReference<>(textView);
    }
    textView.setText(input);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
    int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST);
    int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
    textView.measure(widthMeasureSpec, heightMeasureSpec);
    //textView.layout(0, 0, textView.getMeasuredWidth(), textView.getMeasuredHeight());
    return textView.getMeasuredHeight();
  }

  public static void gc () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      try { System.gc(); } catch (Throwable ignored) { }
    }
  }

  public static String convertStreamToString (InputStream is) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString();
    }
  }

  public static String getStringFromFile (String filePath) throws IOException {
    File fl = new File(filePath);
    try (FileInputStream fin = new FileInputStream(fl)) {
      return convertStreamToString(fin);
    }
  }

  public static long getLastModifiedTime (String path) {
    if (StringUtils.isEmpty(path)) {
      return 0l;
    }
    if (path.startsWith("content://")) {
      if (Config.ALLOW_DATE_MODIFIED_RESOLVING) {
        try {
          Uri uri = Uri.parse(path);
          String[] columns = new String[]{
            MediaStore.MediaColumns.DATE_MODIFIED
          };
          try (Cursor c = UI.getContext().getContentResolver().query(uri, columns, null, null, null)) {
            long modified = 0;
            if (c != null && c.moveToFirst()) {
              try { modified = Math.max(0, c.getLong(0)); } catch (Throwable ignored) { }
            }
            if (modified == 0) {
              modified = System.currentTimeMillis();
            }
            return modified;
          }
        } catch (Throwable t) {
          Log.e("Cannot determine modified time v3", t);
        }
      }
      return System.currentTimeMillis();
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        URI uri = new File(path.startsWith("file://") ? path.substring("file://".length()) : path).toURI();
        return java.nio.file.Files.getLastModifiedTime(java.nio.file.Paths.get(uri)).to(TimeUnit.MILLISECONDS);
      } catch (Throwable t) {
        Log.e("Cannot determine last modified time v2", t);
      }
    }
    try {
      return new File(path).lastModified();
    } catch (Throwable t) {
      Log.e("Cannot determine last modified time", t);
    }
    return 0;
  }

  public static void recycle (@Nullable Canvas c) {
    if (c != null) {
      try {
        c.setBitmap(null);
      } catch (Throwable ignored) { }
    }
  }

  public static void recycle (@Nullable Bitmap bitmap) {
    if (bitmap != null) {
      try {
        if (!bitmap.isRecycled())
          bitmap.recycle();
      } catch (Throwable ignored) { }
    }
  }

  public static boolean isGooglePlayServicesInstalled (Context context) {
    PackageManager pm = context.getPackageManager();
    boolean app_installed;
    try {
      PackageInfo info = pm.getPackageInfo("com.android.vending", PackageManager.GET_ACTIVITIES);
      String label = (String) info.applicationInfo.loadLabel(pm);
      app_installed = !StringUtils.isEmpty(label) && label.startsWith("Google Play");
    } catch(PackageManager.NameNotFoundException e) {
      app_installed = false;
    }
    return app_installed;
  }

  public static ExoPlayer newExoPlayer (Context context, boolean preferExtensions) {
    // new AdaptiveVideoTrackSelection.Factory(new DefaultBandwidthMeter())
    // DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
    // DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    final int extensionMode = preferExtensions || org.thunderdog.challegram.unsorted.Settings.instance().getNewSetting(org.thunderdog.challegram.unsorted.Settings.SETTING_FLAG_FORCE_EXO_PLAYER_EXTENSIONS) ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
    final RenderersFactory renderersFactory = new DefaultRenderersFactory(context).setExtensionRendererMode(extensionMode);
    final MediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(context, new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true));
    return new ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
      .setTrackSelector(new DefaultTrackSelector(context))
      .setLoadControl(new DefaultLoadControl())
      .build();
  }

  public static boolean isUnsupportedFormat (PlaybackException e) {
    return e instanceof ExoPlaybackException && ((ExoPlaybackException) e).type == ExoPlaybackException.TYPE_SOURCE && e.getCause() instanceof UnrecognizedInputFormatException;
  }

  public static boolean isRenderError (PlaybackException e) {
    return e instanceof ExoPlaybackException && ((ExoPlaybackException) e).type == ExoPlaybackException.TYPE_RENDERER;
  }

  public static MediaSource newMediaSource (File file) {
    return new ProgressiveMediaSource.Factory(new FileDataSource.Factory()).createMediaSource(newMediaItem(Uri.fromFile(file)));
  }

  public static com.google.android.exoplayer2.MediaItem newMediaItem (Uri uri) {
    return new com.google.android.exoplayer2.MediaItem.Builder().setUri(uri).build();
  }

  public static MediaSource newMediaSource (int accountId, TdApi.Message message) {
    return newMediaSource(accountId, TD.getFile(message));
  }

  public static MediaSource newMediaSource (int accountId, @Nullable TdApi.File file) {
    if (file == null)
      throw new IllegalArgumentException();
    if (file.id == -1 && !StringUtils.isEmpty(file.local.path)) {
      return newMediaSource(new File(file.local.path));
    } else {
      return new ProgressiveMediaSource.Factory(new TdlibDataSource.Factory()).createMediaSource(newMediaItem(TdlibDataSource.UriFactory.create(accountId, file)));
    }
  }

  public static MediaSource newMediaSource (int accountId, int fileId) {
    return new ProgressiveMediaSource.Factory(new TdlibDataSource.Factory()).createMediaSource(newMediaItem(TdlibDataSource.UriFactory.create(accountId, fileId)));
  }

  public static boolean isGooglePlayServicesAvailable (Context context) {
    try {
      if (context != null && Config.GCM_ENABLED) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
      }
    } catch (Throwable ignored) { }
    return false;
  }

  @SuppressWarnings("deprecation")
  public static boolean isAirplaneModeOn () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return Settings.Global.getInt(UI.getContext().getContentResolver(),
        Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    } else {
      return Settings.System.getInt(UI.getContext().getContentResolver(),
        Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }
  }

  private static long lastTrace;

  public static void trace (String name) {
    if (name == null) {
      lastTrace = SystemClock.elapsedRealtime();
    } else {
      long ms = SystemClock.elapsedRealtime() - lastTrace;
      if (ms >= 100) {
        Log.e("%s took %dms", name, (int) ms);
      } else {
        Log.v("%s took %dms", name, (int) ms);
      }
      lastTrace = SystemClock.elapsedRealtime();
    }
  }

  public static long getTotalUsedSpace (List<File> files) {
    if (files == null || files.isEmpty()) {
      return 0;
    }
    long totalSpace = 0;
    for (File file : files) {
      totalSpace += file.length();
    }
    return totalSpace;
  }

  public static boolean isExternalMemoryAvailable () {
    String state = Environment.getExternalStorageState();
    return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
  }

  public static long getFreeMemorySize (StatFs statFs) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return statFs.getBlockSizeLong() * statFs.getAvailableBlocksLong();
    } else {
      //noinspection deprecation
      return (long) statFs.getBlockSize() * (long) statFs.getAvailableBlocks();
    }
  }

  public static String getMarketUrl () {
    String url = Lang.getStringSecure(R.string.MarketUrl);
    return Strings.isValidLink(url) ? url : BuildConfig.MARKET_URL;
  }

  /*public static boolean isAfter (int hour, int minute, int second, int afterHour, int afterMinute, int afterSecond) {
    return hour > afterHour || (hour == afterHour && (minute > afterMinute || (minute == afterMinute && second > afterSecond)));
  }*/

  public static String getOtherNotificationChannel () {
    return getNotificationChannel("other", R.string.NotificationChannelOther);
  }

  public static String getNotificationChannel (String id, int stringRes) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      android.app.NotificationChannel channel = new android.app.NotificationChannel(id, Lang.getString(stringRes), NotificationManager.IMPORTANCE_LOW);
      NotificationManager manager = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
      manager.createNotificationChannel(channel);
    }
    return id;
  }

  private static Integer maxTextureSize;

  public static synchronized int getMaxTextureSize () {
    Integer value = U.maxTextureSize;
    if (value != null)
      return value;
    int res = determineMaxTextureSize();
    U.maxTextureSize = res;
    return res;
  }

  private static int determineMaxTextureSize () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)
      return 0;

    int[] maxTextureSize = new int[1];
    GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);
    if (maxTextureSize[0] != 0) {
      return maxTextureSize[0];
    }

    EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    int[] vers = new int[2];
    EGL14.eglInitialize(dpy, vers, 0, vers, 1);


    int[] configAttr = {
      EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
      EGL14.EGL_LEVEL, 0,
      EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
      EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
      EGL14.EGL_NONE
    };
    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfig = new int[1];
    EGL14.eglChooseConfig(dpy, configAttr, 0,
      configs, 0, 1, numConfig, 0);
    if (numConfig[0] == 0) {
      // TROUBLE! No config found.
      return 0;
    }
    EGLConfig config = configs[0];

    int[] surfAttr = {
      EGL14.EGL_WIDTH, 64,
      EGL14.EGL_HEIGHT, 64,
      EGL14.EGL_NONE
    };
    EGLSurface surf = EGL14.eglCreatePbufferSurface(dpy, config, surfAttr, 0);

    int[] ctxAttrib = {
      EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
      EGL14.EGL_NONE
    };
    EGLContext ctx = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);

    EGL14.eglMakeCurrent(dpy, surf, surf, ctx);
    GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);

    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
      EGL14.EGL_NO_CONTEXT);
    EGL14.eglDestroySurface(dpy, surf);
    EGL14.eglDestroyContext(dpy, ctx);
    EGL14.eglTerminate(dpy);

    return maxTextureSize[0];
  }

  public static String timeToString (int mergedTime) {
    return timeToString(BitwiseUtils.splitIntToHour(mergedTime), BitwiseUtils.splitIntToMinute(mergedTime), BitwiseUtils.splitIntToSecond(mergedTime));
  }

  public static String timeToString (int hourOfDay, int minute, int second) {
    boolean needAmPm = UI.needAmPm();
    int totalLength = 5;
    if (needAmPm) {
      totalLength += 3;
    }
    if (second != 0) {
      totalLength += 3;
    }
    StringBuilder b = new StringBuilder(totalLength);
    int hours = needAmPm ? (hourOfDay == 0 ? 12 : hourOfDay > 12 ? hourOfDay - 12 : hourOfDay) : hourOfDay;
    if (hours < 10) {
      b.append('0');
    }
    b.append(hours);
    b.append(':');
    if (minute < 10) {
      b.append('0');
    }
    b.append(minute);
    if (second > 0) {
      b.append(':');
      if (second < 10) {
        b.append('0');
      }
      b.append(second);
    }
    if (needAmPm) {
      b.append(hourOfDay < 12 ? " AM" : " PM");
    }
    return b.toString();
  }

  public static long getTotalMemorySize (StatFs statFs) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return statFs.getBlockSizeLong() * statFs.getBlockCountLong();
    } else {
      //noinspection deprecation
      return (long) statFs.getBlockSize() * (long) statFs.getBlockCount();
    }
  }

  private static final String MAP_DARK_STYLE = "&style=element:geometry%7Ccolor:0x212121&style=element:labels.icon%7Cvisibility:off&style=element:labels.text.fill%7Ccolor:0x757575&style=element:labels.text.stroke%7Ccolor:0x212121&style=feature:administrative%7Celement:geometry%7Ccolor:0x757575&style=feature:administrative.country%7Celement:labels.text.fill%7Ccolor:0x9e9e9e&style=feature:administrative.land_parcel%7Cvisibility:off&style=feature:administrative.locality%7Celement:labels.text.fill%7Ccolor:0xbdbdbd&style=feature:poi%7Celement:labels.text.fill%7Ccolor:0x757575&style=feature:poi.park%7Celement:geometry%7Ccolor:0x181818&style=feature:poi.park%7Celement:labels.text.fill%7Ccolor:0x616161&style=feature:poi.park%7Celement:labels.text.stroke%7Ccolor:0x1b1b1b&style=feature:road%7Celement:geometry.fill%7Ccolor:0x2c2c2c&style=feature:road%7Celement:labels.text.fill%7Ccolor:0x8a8a8a&style=feature:road.arterial%7Celement:geometry%7Ccolor:0x373737&style=feature:road.highway%7Celement:geometry%7Ccolor:0x3c3c3c&style=feature:road.highway.controlled_access%7Celement:geometry%7Ccolor:0x4e4e4e&style=feature:road.local%7Celement:labels.text.fill%7Ccolor:0x616161&style=feature:transit%7Celement:labels.text.fill%7Ccolor:0x757575&style=feature:water%7Celement:geometry%7Ccolor:0x000000&style=feature:water%7Celement:labels.text.fill%7Ccolor:0x3d3d3d";

  @SuppressWarnings(value = "SpellCheckingInspection")
  public static String getMapPreview (Tdlib tdlib, double lat, double lon, int zoom, boolean dark, int viewWidth, int viewHeight, int[] resultSize) {
    int scale = Screen.density() >= 2.0f ? 2 : 1;

    int maxWidth = Math.min(viewWidth / scale, MAP_WIDTH);
    int maxHeight = Math.min(viewHeight / scale, MAP_HEIGHT);

    int width = MAP_WIDTH;
    int height = MAP_HEIGHT;

    float ratio = Math.min((float) maxWidth / (float) width, (float) maxHeight / (float) height);

    width = (int) ((float) width * ratio);
    height = (int) ((float) height * ratio);

    width -= width % 2;
    height -= height % 2;

    String result = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?format=jpg&center=%f,%f&zoom=%d&size=%dx%d&maptype=roadmap&scale=%d&sensor=false&language=%s%s", lat, lon, zoom, width, height, scale, tdlib.language(), dark ? MAP_DARK_STYLE : "");

    if (resultSize != null) {
      resultSize[0] = width * scale;
      resultSize[1] = height * scale;
    }

    return result;
  }

  public static long calculateDelayForDiameter (long diameter, long duration) {
    int distance = (int) ((double) (diameter) * Math.PI);
    return calculateDelayForDistance(distance, duration);
  }

  public static long calculateDelayForDistance (int distance, long duration) {
    long delay = duration <= 0 ? 10 : (long) Math.ceil((double) duration / (double) distance);
    if (delay <= 10) {
      delay = (long) Math.ceil((double) delay * Screen.density());
    }
    return Math.max(ValueAnimator.getFrameDelay(), delay);
  }

  public static String getMapPreview (double lat, double lon, int zoom, boolean dark, int viewWidth, int viewHeight) {
    int scale = Screen.density() >= 2.0f ? 2 : 1;

    viewWidth /= scale;
    viewHeight /= scale;

    if (viewWidth > MAP_WIDTH || viewHeight > MAP_HEIGHT) {
      float ratio = Math.min((float) MAP_WIDTH / (float) viewWidth, (float) MAP_HEIGHT / (float) viewHeight);
      viewWidth = (int) ((float) viewWidth * ratio);
      viewHeight = (int) ((float) viewHeight * ratio);
    }

    return String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?format=jpg&center=%f,%f&zoom=%d&size=%dx%d&maptype=roadmap&scale=%d&sensor=false%s", lat, lon, zoom, viewWidth, viewHeight, scale, dark ? MAP_DARK_STYLE : "");
  }

  public static boolean canStreamVideo (TdApi.InputFile inputFile) {
    // TODO: check if supportsStreaming boolean is set dynamically in any existing TG client
    // "send as originals" should be false here I suppose
    return true;
  }

  public static Location getLastKnownLocation (Context context, boolean allowNetwork) {
    try {
      LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
      if (manager != null) {
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
          return manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (allowNetwork && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
          return manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        return null;
      }
    } catch (SecurityException ignored) {

    } catch (Throwable t) {
      Log.w("Error getting last known location", t);
    }
    return null;
  }

  public static boolean deviceHasMicrophone (Context context) {
    PackageManager pm = context.getPackageManager();
    return pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
  }

  @SuppressWarnings("UnsupportedChromeOsCameraSystemFeature")
  public static boolean deviceHasAnyCamera (Context context) {
    PackageManager pm = context.getPackageManager();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    } else {
      return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }
  }

  public static final String PACKAGE_GOOGLE_MAPS = "com.google.android.apps.maps";
  public static final String PACKAGE_TOR = "org.torproject.android";

  public static final int MAP_WIDTH = 640;
  public static final int MAP_HEIGHT = 320;

  private static final char[] HEX_CHARACTERS = "0123456789ABCDEF".toCharArray();

  public static String bytesToHex (byte[] bytes) {
    if (bytes == null) {
      return "";
    }
    char[] hexChars = new char[bytes.length * 2];
    int v;
    for (int j = 0; j < bytes.length; j++) {
      v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_CHARACTERS[v >>> 4];
      hexChars[j * 2 + 1] = HEX_CHARACTERS[v & 0x0F];
    }
    return new String(hexChars);
  }

  public static String buildHex (byte[] keyHash) {
    StringBuilder b = new StringBuilder();
    if (keyHash.length > 16) {
      String hex = bytesToHex(keyHash);
      for (int a = 0; a < 32; a++) {
        if (a != 0) {
          if (a % 8 == 0) {
            b.append('\n');
          } else if (a % 4 == 0) {
            b.append(' ');
          }
        }
        b.append(hex.substring(a * 2, a * 2 + 2));
        b.append(' ');
      }
      b.append("\n");
      for (int a = 0; a < 5; a++) {
        int num = ((keyHash[16 + a * 4] & 0x7f) << 24) | ((keyHash[16 + a * 4 + 1] & 0xff) << 16) | ((keyHash[16 + a * 4 + 2] & 0xff) << 8) | (keyHash[16 + a * 4 + 3] & 0xff);
        /*if (a != 0) {
          emojis.append(" ");
        }
        emojis.append(EmojiData.emojiSecret[num % EmojiData.emojiSecret.length]);*/
      }
      // emojiText = emojis.toString();
    }
    return b.toString();
  }

  public static Uri contentUriFromFile (File file) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        return FileProvider.getUriForFile(UI.getAppContext(), Config.FILE_PROVIDER_AUTHORITY, file);
      } catch (Throwable t) {
        Log.e("Can't create content uri for path", t);
        // UI.showToast("Could not open path: " + file.getPath() + ", reason: " + t.getMessage(), Toast.LENGTH_LONG);
      }
      return null;
    }
    return Uri.fromFile(file);
  }

  // Returns either good file path, or content:// stuff
  public static String tryResolveFilePath (final Uri uri) {
    String result = null;
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(UI.getContext(), uri)) {
        if (isExternalStorageDocument(uri)) {
          final String docId = DocumentsContract.getDocumentId(uri);
          final String[] split = docId.split(":", 2);
          final String type = split[0];
          if ("primary".equalsIgnoreCase(type)) {
            result = Environment.getExternalStorageDirectory() + "/" + split[1];
          }
        } else if (isDownloadsDocument(uri)) {
          final String id = DocumentsContract.getDocumentId(uri);
          final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
          result = getDataColumn(UI.getContext(), contentUri, null, null);
        } else if (isMediaDocument(uri)) {
          final String docId = DocumentsContract.getDocumentId(uri);
          final String[] split = docId.split(":", 2);
          final String type = split[0];

          Uri contentUri = null;
          switch (type) {
            case "image":
              contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
              break;
            case "video":
              contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
              break;
            case "audio":
              contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
              break;
          }

          final String selection = "_id=?";
          final String[] selectionArgs = new String[] {
            split[1]
          };

          result = getDataColumn(UI.getContext(), contentUri, selection, selectionArgs);
        }
      } else if ("content".equalsIgnoreCase(uri.getScheme())) {
        result = getDataColumn(UI.getContext(), uri, null, null);
      } else if ("file".equalsIgnoreCase(uri.getScheme())) {
        result = uri.getPath();
      }
    } catch (Throwable t) {
      Log.w("Cannot get path of the file", t);
    }
    return StringUtils.isEmpty(result) ? uri.toString() : result;
  }

  public static void openFile (TdlibDelegate context, TdApi.Video video) {
    openFile(context, StringUtils.isEmpty(video.fileName) ? ("video/mp4".equals(video.mimeType) ? "video.mp4" : "video/quicktime".equals(video.mimeType) ? "video.mov" : "") : video.fileName, new File(video.video.local.path), video.mimeType, 0);
  }

  public static Uri getUri (Parcelable parcelable) {
    return parcelable == null ? null : parcelable instanceof Uri ? (Uri) parcelable : Uri.parse(parcelable.toString());
  }

  public static void openFile (TdlibDelegate context, String displayName, File file, String mimeType, int viewCount) {
    String extension = getExtension(file.getPath());
    if (StringUtils.isEmpty(mimeType)) {
      mimeType = U.resolveMimeType(file.getPath(), extension);
    }

    if ((!StringUtils.isEmpty(extension) && TGMimeType.isPlainTextExtension(extension)) || (!StringUtils.isEmpty(mimeType) && TGMimeType.isPlainTextMimeType(mimeType))) {
      TextController c;
      c = new TextController(context.context(), context.tdlib());
      c.setArguments(TextController.Arguments.fromFile(displayName, file.getPath(), mimeType).setViews(viewCount));
      UI.navigateTo(c);
      return;
    }

    if (Intents.openFile(file, mimeType)) {
      return;
    }

    String newMimeType = U.resolveMimeType(file.getPath());
    if (!StringUtils.isEmpty(newMimeType) && !StringUtils.equalsOrBothEmpty(mimeType, newMimeType)) {
      openFile(context, displayName, file, newMimeType, viewCount);
      return;
    }

    UI.showToast(R.string.NoAppToOpen, Toast.LENGTH_SHORT);
  }

  public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
    final String column = "_data";
    final String[] projection = {
      column
    };
    try (final Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        final int column_index = cursor.getColumnIndexOrThrow(column);
        return cursor.getString(column_index);
      }
    } catch (Throwable t) {
      Log.w("Cannot get data column", t);
    }
    return null;
  }

  @SuppressWarnings(value = "SpellCheckingInspection")
  public static boolean isExternalStorageDocument (Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
  }

  public static boolean isDownloadsDocument (Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
  }

  public static boolean isMediaDocument (Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
  }

  public static boolean isGoogleDriveDocument(Uri uri) {
    return "com.google.android.apps.docs.storage".equals(uri.getAuthority());
  }

  public static String getFileTimestamp () {
    return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
  }


  public static File generatePicturePath () {
    return generatePicturePath(false);
  }

  public static File generatePicturePath (boolean isPrivate) {
    return generateMediaPath(isPrivate, "jpg");
  }

  public static File newFile (File parent, String fileName) {
    String extension = getExtension(fileName);
    if (StringUtils.isEmpty(extension)) {
      return newFile(parent, fileName, null);
    } else {
      return newFile(parent, fileName.substring(0, fileName.length() - extension.length() - 1), extension);
    }
  }

  public static File newFile (File parent, String fileName, String extension) {
    File file;
    int attemptCount = 0;
    do {
      StringBuilder name = new StringBuilder(fileName);
      if (attemptCount != 0) {
        name.append(" (").append(attemptCount).append(")");
      }
      if (!StringUtils.isEmpty(extension)) {
        name.append(".").append(extension);
      }
      file = new File(parent, name.toString());
      attemptCount++;
    } while (file.exists());
    return file;
  }

  public static File generateMediaPath (boolean isPrivate, String extension) {
    return newFile(getAlbumDir(isPrivate), getFileTimestamp(), extension);
  }

  public static File generateMediaPath (boolean isPrivate, boolean isVideo) {
    return isVideo ? generateVideoPath(isPrivate) : generatePicturePath(isPrivate);
  }

  public static File generateVideoPath (boolean isPrivate) {
    return generateMediaPath(isPrivate, "mp4");
  }

  public static class MediaMetadata {
    public final int width, height, rotation;
    public final long durationMs, bitrate;
    public final boolean hasVideo, hasAudio;
    public final String title, performer;

    public MediaMetadata (int width, int height, int rotation, long durationMs, long bitrate, boolean hasVideo, boolean hasAudio, String title, String performer) {
      this.width = width;
      this.height = height;
      this.rotation = rotation;
      this.durationMs = durationMs;
      this.bitrate = bitrate;
      this.hasVideo = hasVideo;
      this.hasAudio = hasAudio;
      this.title = title;
      this.performer = performer;
    }

    public long getDuration (TimeUnit unit) {
      return unit.convert(durationMs, TimeUnit.MILLISECONDS);
    }
  }

  @Nullable
  public static MediaMetadata getMediaMetadata (String path) {
    MediaMetadataRetriever retriever;
    try {
      retriever = U.openRetriever(path);
    } catch (Throwable t) {
      return null;
    }
    if (retriever != null) {
      boolean hasVideo = "yes".equalsIgnoreCase(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
      boolean hasAudio = "yes".equalsIgnoreCase(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
      long duration = StringUtils.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
      long bitrate = StringUtils.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
      int videoWidth = hasVideo ? StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)) : 0;
      int videoHeight = hasVideo ? StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)) : 0;
      int videoRotation;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && hasVideo) {
        videoRotation = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
      } else {
        videoRotation = 0;
      }

      String title, performer;
      if (hasVideo) {
        title = performer = null;
      } else {
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        performer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        if (StringUtils.isEmpty(performer)) {
          performer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);
        }
        if (StringUtils.isEmpty(performer)) {
          performer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
        }
      }
      U.closeRetriever(retriever);
      return new MediaMetadata(videoWidth, videoHeight, videoRotation, duration, bitrate, hasVideo, hasAudio, title, performer);
    }
    // Log.w("Unable to fetch video sizes, trying to use ffmpeg for that");
    // TODO ffmpeg fallback
    return null;
  }

  public static void toGalleryFile (Uri outFile, boolean isVideo, RunnableData<ImageGalleryFile> callback) {
    if (outFile != null && "file".equals(outFile.getScheme())) {
      toGalleryFile(new File(outFile.getPath()), isVideo, callback);
    } else {
      callback.runWithData(null);
    }
  }

  public static void toGalleryFile (File outFile, boolean isVideo, RunnableData<ImageGalleryFile> callback) {
    Background.instance().post(() -> {
      if (!outFile.exists()) {
        UI.post(() -> callback.runWithData(null));
        return;
      }
      ImageGalleryFile file;
      if (isVideo) {
        MediaMetadata mediaMetadata = getMediaMetadata(outFile.getPath());
        if (mediaMetadata != null) {
          file = new ImageGalleryFile(-1, outFile.getPath(), outFile.lastModified(), mediaMetadata.width, mediaMetadata.height, -1, false);
          file.setFromCamera();
          file.setIsVideo(mediaMetadata.getDuration(TimeUnit.MILLISECONDS), "video/mov");
          file.setReady();
          file.setRotation(mediaMetadata.rotation);
        } else {
          file = null;
        }
      } else {
        int exifOrientation = U.getExifOrientation(outFile.getPath());
        int rotation = U.getRotationForExifOrientation(exifOrientation);
        BitmapFactory.Options options = ImageReader.getImageSize(outFile.getPath());
        int width, height;
        if (U.isExifRotated(exifOrientation) && rotation == 0) {
          width = options.outHeight;
          height = options.outWidth;
        } else {
          width = options.outWidth;
          height = options.outHeight;
        }
        file = new ImageGalleryFile(-1, outFile.getPath(), outFile.lastModified(), width, height, -1, false);
        file.setNoCache();
        file.setForceArgb8888();
        file.setFromCamera();
        file.setSize(MediaItem.maxDisplaySize());
        file.setRotation(rotation);
      }
      if (file != null) {
        ImageLoader.instance().loadFile(file, (success, result) -> {
          if (result != null) {
            ImageStrictCache.instance().put(file, result);
          }
          UI.post(() -> callback.runWithData(file));
        });
      } else {
        UI.post(() -> callback.runWithData(null));
      }
    });
  }

  public static String getFileName (String path) {
    int i = path.lastIndexOf('/');
    return i != -1 ? path.substring(i + 1) : path;
  }

  public static boolean compareExtension (@NonNull String extension, @Nullable String path) {
    return extension.equalsIgnoreCase(U.getExtension(path));
  }

  public static String getExtension (String path) {
    final String fileName = getFileName(path);
    int i = fileName.lastIndexOf('.');
    if (i != -1) {
      String ext = fileName.substring(i + 1);
      if (ext.toLowerCase().equals("crdownload")) {
        return getExtension(fileName.substring(0, i));
      }
      return ext;
    }
    return null;
  }

  public static File generateMediaPath (String fileName, int mediaType) {
    return generateMediaPath(false, fileName, mediaType);
  }

  public static File generateMediaPath (boolean isPrivate, String fileName, int mediaType) {
    if (mediaType == TYPE_PHOTO) {
      return generatePicturePath();
    }
    try {
      File storageDir = getAlbumDir(isPrivate);
      String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
      final String type = "VID";
      String extension = getExtension(fileName);
      if (extension == null) {
        extension = mediaType == TYPE_GIF ? "gif" : "mp4";
      }
      return new File(storageDir, type + "_" + timeStamp + "." + extension);
    } catch (Throwable t) {
      Log.w("Cannot generate picture path", t);
    }
    return null;
  }

  public static int rotateExifOrientation (int orientation, int degrees) {
    if (degrees % 90 != 0) {
      Log.w( "Can only rotate in right angles (eg. 0, 90, 180, 270). %d is unsupported.", degrees);
      return ExifInterface.ORIENTATION_UNDEFINED;
    }
    degrees %= 360;
    while (degrees < 0) {
      degrees += 90;
      switch (orientation) {
        case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
          orientation = ExifInterface.ORIENTATION_TRANSPOSE;
          break;
        case ExifInterface.ORIENTATION_ROTATE_180:
          orientation = ExifInterface.ORIENTATION_ROTATE_90;
          break;
        case ExifInterface.ORIENTATION_FLIP_VERTICAL:
          orientation = ExifInterface.ORIENTATION_TRANSVERSE;
          break;
        case ExifInterface.ORIENTATION_TRANSPOSE:
          orientation = ExifInterface.ORIENTATION_FLIP_VERTICAL;
          break;
        case ExifInterface.ORIENTATION_ROTATE_90:
          orientation = ExifInterface.ORIENTATION_NORMAL;
          break;
        case ExifInterface.ORIENTATION_TRANSVERSE:
          orientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
          break;
        case ExifInterface.ORIENTATION_ROTATE_270:
          orientation = ExifInterface.ORIENTATION_ROTATE_90;
          break;
        case ExifInterface.ORIENTATION_NORMAL:
          // Fall-through
        case ExifInterface.ORIENTATION_UNDEFINED:
          // Fall-through
        default:
          orientation = ExifInterface.ORIENTATION_ROTATE_270;
          break;
      }
    }
    while (degrees > 0) {
      degrees -= 90;

      switch (orientation) {
        case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
          orientation = ExifInterface.ORIENTATION_TRANSVERSE;
          break;
        case ExifInterface.ORIENTATION_ROTATE_180:
          orientation = ExifInterface.ORIENTATION_ROTATE_270;
          break;
        case ExifInterface.ORIENTATION_FLIP_VERTICAL:
          orientation = ExifInterface.ORIENTATION_TRANSPOSE;
          break;
        case ExifInterface.ORIENTATION_TRANSPOSE:
          orientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
          break;
        case ExifInterface.ORIENTATION_ROTATE_90:
          orientation = ExifInterface.ORIENTATION_ROTATE_180;
          break;
        case ExifInterface.ORIENTATION_TRANSVERSE:
          orientation = ExifInterface.ORIENTATION_FLIP_VERTICAL;
          break;
        case ExifInterface.ORIENTATION_ROTATE_270:
          orientation = ExifInterface.ORIENTATION_NORMAL;
          break;
        case ExifInterface.ORIENTATION_NORMAL:
          // Fall-through
        case ExifInterface.ORIENTATION_UNDEFINED:
          // Fall-through
        default:
          orientation = ExifInterface.ORIENTATION_ROTATE_90;
          break;
      }
    }
    return orientation;
  }

  public static int getExifOrientationForRotation (int rotation) {
    switch (MathUtils.modulo(rotation, 360)) {
      case 0:
        return ExifInterface.ORIENTATION_NORMAL;
      case 90:
        return ExifInterface.ORIENTATION_ROTATE_90;
      case 180:
        return ExifInterface.ORIENTATION_ROTATE_180;
      case 270:
        return ExifInterface.ORIENTATION_ROTATE_270;
    }
    return ExifInterface.ORIENTATION_UNDEFINED;
  }

  @Deprecated
  public static int getRotation (String path) {
    return getRotationForExifOrientation(getExifOrientation(path));
  }

  @Deprecated
  public static int getRotationForExifOrientation (int exifOrientation) {
    switch (exifOrientation) {
      case ExifInterface.ORIENTATION_ROTATE_90:
        // FIXME case ExifInterface.ORIENTATION_TRANSVERSE:
        return 90;
      case ExifInterface.ORIENTATION_ROTATE_180:
        return 180;
      case ExifInterface.ORIENTATION_ROTATE_270:
        // FIXME case ExifInterface.ORIENTATION_TRANSPOSE:
        return 270;
    }
    return 0;
  }

  public static boolean isExifRotated (int orientation) {
    switch (orientation) {
      case ExifInterface.ORIENTATION_ROTATE_90:
      case ExifInterface.ORIENTATION_ROTATE_270:
      case ExifInterface.ORIENTATION_TRANSVERSE:
      case ExifInterface.ORIENTATION_TRANSPOSE:
        return true;
    }
    return false;
  }

  public static boolean isExifRotated (String path) {
    return isExifRotated(getExifOrientation(path));
  }

  public static int getExifOrientation (String path) {
    if (path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".gif")) {
      return ExifInterface.ORIENTATION_UNDEFINED;
    }
    ExifInterface exif = null;
    if (path.startsWith("content://")) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try (InputStream is = U.openInputStream(path)) {
          exif = new ExifInterface(is);
        } catch (Throwable ignored) { }
      }
    } else {
      try {
        exif = new ExifInterface(path);
      } catch (Throwable ignored) { }
    }
    return exif != null ? exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) : ExifInterface.ORIENTATION_UNDEFINED;
  }

  private static void logRecursive (StringBuilder b, File file, int level) {
    final int spaceCount = level * 2;
    b.ensureCapacity(b.length() + spaceCount + file.getPath().length());
    for (int i = 0; i < spaceCount; i++) {
      b.append('=');
    }
    b.append('>');
    b.append(file.getName());
    if (file.isDirectory()) {
      b.append(":\n");
      for (File child : file.listFiles()) {
        logRecursive(b, child, level + 1);
      }
    } else {
      b.append('\n');
    }
  }

  public static void logRecursive (File file) {
    StringBuilder b = new StringBuilder();
    logRecursive(b, file, 0);
    Log.v(b.toString());
  }

  public static int flipExifHorizontally (int orientation) {
    switch (orientation) {
      case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
        orientation = ExifInterface.ORIENTATION_NORMAL;
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        orientation = ExifInterface.ORIENTATION_FLIP_VERTICAL;
        break;
      case ExifInterface.ORIENTATION_FLIP_VERTICAL:
        orientation = ExifInterface.ORIENTATION_ROTATE_180;
        break;
      case ExifInterface.ORIENTATION_TRANSPOSE:
        orientation = ExifInterface.ORIENTATION_ROTATE_90;
        break;
      case ExifInterface.ORIENTATION_ROTATE_90:
        orientation = ExifInterface.ORIENTATION_TRANSPOSE;
        break;
      case ExifInterface.ORIENTATION_TRANSVERSE:
        orientation = ExifInterface.ORIENTATION_ROTATE_270;
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        orientation = ExifInterface.ORIENTATION_TRANSVERSE;
        break;
      case ExifInterface.ORIENTATION_NORMAL:
        // Fall-through
      case ExifInterface.ORIENTATION_UNDEFINED:
        // Fall-through
      default:
        orientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL;
        break;
    }
    return orientation;
  }

  public static boolean requestPermissionsIfNeeded (Runnable onDone, String... permissions) {
    if (permissions == null || permissions.length == 0)
      return false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (U.needsPermissionRequest(permissions)) {
        BaseActivity activity = UI.getUiContext();
        if (activity != null) {
          activity.requestCustomPermissions(permissions, (code, granted) -> {
            if (granted) {
              onDone.run();
            }
          });
        }
        return true;
      }
    }
    return false;
  }

  public static boolean needsPermissionRequest (final String... permissions) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();
      if (context != null) {
        for (String permission : permissions) {
          if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static void run (Runnable runnable) {
    if (runnable != null) {
      runnable.run();
    }
  }

  public static boolean needsPermissionRequest (final String permission) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();
      return context != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED;
    }
    return false;
  }

  public static boolean shouldShowPermissionRationale (final String permission) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();
      return context != null && context.shouldShowRequestPermissionRationale(permission);
    }
    return false;
  }

  public static void requestPermissions (final String[] permissions, final RunnableBool after) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      BaseActivity context = UI.getUiContext();
      if (context == null) {
        return;
      }
      context.requestCustomPermissions(permissions, (code, granted) -> {
        if (after != null) {
          after.runWithBool(granted);
        }
      });
    }
  }

  public static final int TYPE_PHOTO = 0;
  public static final int TYPE_VIDEO = 1;
  public static final int TYPE_GIF = 2;
  public static final int TYPE_FILE = 3;

  public static void savePhotoToGallery (final Bitmap bitmap, boolean transparent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && needsPermissionRequest(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)) {
      requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, result -> {
        if (result) {
          savePhotoToGallery(bitmap, transparent);
        }
      });
      return;
    }
    Background.instance().post(() -> {
      File file = generateMediaPath(null, TYPE_PHOTO);
      boolean success = false;
      try (FileOutputStream stream = new FileOutputStream(file)) {
        success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
      } catch (Throwable t) {
        Log.e("Cannot open filtered target file stream");
      }
      U.recycle(bitmap);
      if (success) {
        try {
          addToGallery(file);
        } catch (Throwable t) {
          Log.e("Can't save file to gallery", t);
        }
      }
    });
  }

  public static void copyToGallery (final String fromPath, final int type) {
    copyToGallery(fromPath, type, true, null);
  }

  public static boolean copyToGalleryImpl (final String fromPath, int type, RunnableData<File> onSaved) {
    File file = generateMediaPath(fromPath, type);
    if (file != null) {
      try {
        if (FileUtils.copy(new File(fromPath), file)) {
          addToGallery(file);
          if (onSaved != null) {
            onSaved.runWithData(file);
          }
          return true;
        } else {
          Log.w("Cannot copy file to gallery");
        }
      } catch (Throwable t) {
        Log.w("Cannot save file to gallery", t);
      }
    }
    return false;
  }

  public static void copyToGallery (final String fromPath, final int type, boolean needAlert, RunnableData<File> onSaved) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && needsPermissionRequest(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, result -> {
        if (result) {
          copyToGallery(fromPath, type, needAlert, onSaved);
        }
      });
      return;
    }
    if (fromPath != null && !fromPath.isEmpty()) {
      Background.instance().post(() -> {
        if (copyToGalleryImpl(fromPath, type, onSaved)) {
          if (needAlert) {
            switch (type) {
              case TYPE_GIF:
                UI.showToast(R.string.GifHasBeenSavedToGallery, Toast.LENGTH_SHORT);
                break;
              case TYPE_VIDEO:
                UI.showToast(R.string.VideoHasBeenSavedToGallery, Toast.LENGTH_SHORT);
                break;
              case TYPE_PHOTO:
                UI.showToast(R.string.PhotoHasBeenSavedToGallery, Toast.LENGTH_SHORT);
                break;
            }
          }
        }
      });
    }
  }

  public static File getAppDir (boolean allowExternal) {
    File file = null;
    if (allowExternal) {
      file = UI.getContext().getExternalFilesDir(null);
    }
    return file != null ? file : UI.getContext().getFilesDir();
  }

  public static File getRingtonesDir () {
    File ringtonesDir = new File(getAppDir(false), "ringtones");
    if (!ringtonesDir.exists() && !ringtonesDir.mkdir())
      throw new IllegalStateException();
    return ringtonesDir;
  }

  public static boolean copyFile (Context context, Uri src, File dst) {
    switch (src.getScheme()) {
      case "file":
        return FileUtils.copy(new File(src.getPath()), dst);
      case "content": {
        long totalDone = 0;
        try (InputStream inputStream = context.getContentResolver().openInputStream(src)) {
          try (Source in = Okio.source(inputStream);
               Sink sink = Okio.sink(dst);
               BufferedSink out = Okio.buffer(sink)) {
            long done;
            while ((done = in.read(out.getBuffer(), 20480)) != -1) {
              totalDone += done;
            }
            out.flush();
          }
          Log.i("Copied %d bytes: %s to %s", totalDone, src, dst);
          return true;
        } catch (Throwable t) {
          Log.e("Unable to copy file", t);
          return false;
        }
      }
      default:
        throw new UnsupportedOperationException(src.getScheme());
    }
  }

  public static boolean isPrivateFile (File file) {
    return isPrivateFile(file.getPath());
  }

  public static boolean isPrivateFile (String path) {
    String privateDir = getAlbumDir(true).getPath();
    return path.startsWith(privateDir);
  }

  private static boolean moveFiles (File fromDirectory, File toDirectory) {
    File[] mediaFiles = fromDirectory.listFiles();
    if (mediaFiles == null || mediaFiles.length == 0) {
      if (!fromDirectory.delete()) {
        Log.w("Unable to delete media directory");
        return false;
      }
      return true;
    }
    if (!toDirectory.mkdirs() && !toDirectory.exists()) {
      Log.w("Failed to create output directory");
      return false;
    }
    if (!toDirectory.isDirectory()) {
      Log.w("Output directory is not a directory");
      return false;
    }
    int successCount = 0;
    for (File fromFile : mediaFiles) {
      File toFile = new File(toDirectory, fromFile.getName());
      if (moveFile(fromFile, toFile))
        successCount++;
    }
    return successCount == mediaFiles.length && fromDirectory.delete();
  }

  private static boolean moveFile (File fromFile, File toFile) {
    if (fromFile.renameTo(toFile)) {
      return true;
    }
    if (fromFile.isDirectory()) {
      return moveFiles(fromFile, toFile);
    }
    if (!FileUtils.copy(fromFile, toFile)) {
      Log.w("Cannot copy file");
      return false;
    }
    return fromFile.delete();
  }

  public static void moveUnsafePrivateMedia () {
    File fromDirectory = getUnsecurePrivateAlbumDir();
    if (!fromDirectory.exists())
      return;
    File toDirectory = getAlbumDir(true);
    if (toDirectory == null || toDirectory.equals(fromDirectory)) {
      return;
    }
    moveFiles(fromDirectory, toDirectory);
  }

  private static File getUnsecurePrivateAlbumDir () {
    return new File(getAppDir(true), "media");
  }

  public static File getAlbumDir (boolean isPrivate) {
    File storageDir = null;
    if (!isPrivate) {
      if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
        storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), BuildConfig.PROJECT_NAME);
      } else {
        Log.w("External storage is not mounted READ/WRITE.");
      }
    }
    if (isPrivate || storageDir == null) {
      storageDir = new File(getAppDir(!isPrivate), "media");
    }
    if (!storageDir.mkdirs() && !storageDir.exists()) {
      Log.w("Failed to create album directory");
      // return null;
    }
    return storageDir;
  }

  public static void deleteGalleyFile (File file) {
    if (file == null)
      return;
    if (isPrivateFile(file)) {
      FileUtils.deleteFile(file);
      return;
    }
    MediaScannerConnection.scanFile(UI.getAppContext(), new String[] {file.getPath()}, null, (path, uri) -> {
      if (uri != null) {
        UI.getAppContext().getContentResolver().delete(uri, null, null);
      }
      FileUtils.deleteFile(file);
    });
  }

  public static int getLineHeight (Paint.FontMetricsInt fm) {
    return (Math.abs(fm.descent - fm.ascent) + fm.leading);
  }

  public static void scanFile (File file) {
    if (file == null)
      return;
    /*String mimeType = TGMimeType.mimeTypeForExtension(U.getExtension(file.getPath()));
    MediaScannerConnection.scanFile(UI.getAppContext(), new String[] {file.getPath()}, !Strings.isEmpty(mimeType) ? new String[] {mimeType} : null, (path, uri) -> {

    });*/
    Uri uri = Uri.fromFile(file);
    if (uri != null) {
      Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
      mediaScanIntent.setData(uri);
      UI.getContext().sendBroadcast(mediaScanIntent);
    }
  }

  public static void addToGallery (File file) {
    if (file != null) {
      scanFile(file);
    }
  }

  public static void addToGallery (String fromPath) {
    if (fromPath != null) {
      addToGallery(new File(fromPath));
    }
  }

  public static int sum (IntList obj) {
    int sum = 0;
    final int size = obj.size();
    for (int i = 0; i < size; i++) {
      sum += obj.get(i);
    }
    return size;
  }

  public static int[] increase (int[] obj, int by) {
    int[] res = new int[obj.length + by];
    System.arraycopy(obj, 0, res, 0, Math.min(obj.length, res.length));
    return res;
  }

  public static String[] increase (String[] obj, int by) {
    String[] res = new String[obj.length + by];
    System.arraycopy(obj, 0, res, 0, Math.min(obj.length, res.length));
    return res;
  }

  private static @Nullable LocalVar<float[]> __smallTextWidths, __textWidths;

  private static float[] getLastWidths (int count) {
    return pickWidths(count, false);
  }

  private static float[] pickWidths (int count, boolean force) {
    if (count >= 1 && count <= 3) {
      LocalVar<float[]> smallTextWidths = __smallTextWidths;
      if (smallTextWidths == null) {
        synchronized (U.class) {
          if (__smallTextWidths == null) {
            __smallTextWidths = new LocalVar<>();
          }
          smallTextWidths = __smallTextWidths;
        }
      }
      float[] widths = smallTextWidths.get();
      if (widths == null && force) {
        smallTextWidths.set(widths = new float[3]);
      }
      return widths;
    } else {
      LocalVar<float[]> textWidths = __textWidths;
      if (textWidths == null) {
        synchronized (U.class) {
          if (__textWidths == null) {
            __textWidths = new LocalVar<>();
          }
          textWidths = __textWidths;
        }
      }
      float[] widths = textWidths.get();
      if ((widths == null || widths.length < count) && force) {
        textWidths.set(widths = new float[Math.max(count, 100)]);
      }
      return widths;
    }
  }

  public static float [] pickSmallWidths () {
    return pickWidths(3, true);
  }

  public static float measureEmojiText (@Nullable CharSequence in, @NonNull Paint p) {
    return StringUtils.isEmpty(in) ? 0 : measureEmojiText(in, 0, in.length(), p);
  }

  public static String hexWithZero (int color) {
    String part = Integer.toHexString(color).toUpperCase();
    if (part.length() == 1)
      return "0" + part;
    return part;
  }

  public static String formatFloat (float value, boolean needFull) {
    return value == (int) value ? String.valueOf((int) value) : needFull ? String.valueOf(value) : String.format(Locale.US, "%.2f", value);
  }

  private static class SpanComparator implements Comparator<Object> {
    private Spannable s;

    public SpanComparator () { }

    public void setSpan (Spannable s) {
      this.s = s;
    }

    @Override
    public int compare (Object o1, Object o2) {
      return Integer.compare(s.getSpanStart(o1), s.getSpanStart(o2));
    }
  }

  private static LocalVar<SpanComparator> spanComparator;

  private static SpanComparator getSpanComparator () {
    if (spanComparator == null) {
      synchronized (U.class) {
        if (spanComparator == null) {
          spanComparator = new LocalVar<>();
        }
      }
    }
    SpanComparator c = spanComparator.get();
    if (c == null) {
      c = new SpanComparator();
      spanComparator.set(c);
    }
    return c;
  }

  public static float measureEmojiText (@Nullable CharSequence in, final int start, final int end, @NonNull Paint p) {
    if (StringUtils.isEmpty(in)) {
      return 0;
    }
    if (!(in instanceof Spannable)) {
      return measureText(in, start, end, p);
    }

    final Spannable s = (Spannable) in;
    EmojiSpan[] spans = s.getSpans(start, end, EmojiSpan.class);
    if (spans == null || spans.length == 0) {
      return measureText(in, start, end, p);
    }

    if (spans.length > 1) {
      SpanComparator comparator = getSpanComparator();
      comparator.setSpan(s);
      Arrays.sort(spans, comparator);
    }

    float textWidth = 0;
    int startIndex = start;
    for (EmojiSpan span : spans) {
      int spanStart = s.getSpanStart(span);
      if (startIndex < spanStart) {
        textWidth += measureText(in, startIndex, spanStart, p);
      }
      textWidth += span.getRawSize(p);
      startIndex = s.getSpanEnd(span);
    }
    if (startIndex < end) {
      textWidth += measureText(in, startIndex, end, p);
    }
    return textWidth;
  }

  public static float measureText (@Nullable CharSequence in, @NonNull Paint p) {
    return StringUtils.isEmpty(in) ? 0 : measureText(in, 0, in.length(), p);
  }

  private static long step;
  public static void step (String msg) {
    if (BuildConfig.DEBUG) {
      if (msg != null && step != 0) {
        Log.i("MEASURE_STEP %s in %dms", msg, SystemClock.uptimeMillis() - step);
      }
      step = SystemClock.uptimeMillis();
    }
  }

  /*private static void measureMeasureText (CharSequence in, Paint p) {
    if (true) {
      try {
        float[] widths = new float[in.length()];
        int start = 0;
        int end = in.length();
        long elapsed = SystemClock.elapsedRealtime();
        float res1 = 0f;
        for (int i = 0; i < 1000; i++) {
          p.getTextWidths(in, start, end, widths);
          res1 = sum(widths);
        }
        long ms1 = SystemClock.elapsedRealtime() - elapsed;
        elapsed = SystemClock.elapsedRealtime();
        float res2 = 0f;
        for (int i = 0; i < 1000; i++) {
          res2 = p.getRunAdvance(in, start, end, start, end, false, end);
        }
        long ms2 = SystemClock.elapsedRealtime() - elapsed;

        //ms1 /= 1000;
        //ms2 /= 1000;

        if (ms2 < ms1) {
          Logger.e("getRunAdvance is faster for length=%d: %dms vs %dms, result: %f vs %f", end - start, ms1, ms2, res1, res2);
        } else {
          Logger.v("getTextWidths is faster for length=%d: %dms vs %dms, result: %f vs %f", end - start, ms1, ms2, res1, res2);
        }
      } catch (Throwable t) {
        Logger.e(t);
      }
    }
  }*/

  /*private static boolean getTextRunAdvancesStr_attempted, attempted_getTextRunAdvancesChars_attempted;
  private static Method getTextRunAdvancesStr, getTextRunAdvancesChars;*/

  public static float measureText (char[] in, int start, int end, @NonNull Paint p) {
    final int count = end - start;

    if (in == null || in.length == 0 || count <= 0) {
      return 0;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      /*
      getTextRunAdvances(char[] chars, int index, int count,
            int contextIndex, int contextCount, boolean isRtl, float[] advances,
            int advancesIndex) * */

      return p.getRunAdvance(in, start, end, 0, in.length, false, end);
    } else {
      float[] widths = pickWidths(count, true);
      p.getTextWidths(in, start, end, widths);
      return ArrayUtils.sum(widths, count);
    }
  }

 /* Turns out calling getTextRunAdvances 3x times slower, probably because of reflection.
    This is a FIXME for android

  @SuppressLint("PrivateApi")
  @TargetApi(Build.VERSION_CODES.M)
  private static float getRunAdvance (@NonNull CharSequence in, int start, int end, @NonNull Paint p) {
    if (Config.BETA) {
      *//* public float getTextRunAdvances(CharSequence text, int start, int end,
            int contextStart, int contextEnd, boolean isRtl, float[] advances,
            int advancesIndex)*//*
      if (!getTextRunAdvancesStr_attempted) {
        synchronized (Utils.class) {
          if (!getTextRunAdvancesStr_attempted) {
            try {
              getTextRunAdvancesStr = Paint.class.getDeclaredMethod(
                "getTextRunAdvances",
                CharSequence.class *//*text*//*,
                int.class *//*start*//*,
                int.class *//*end*//*,
                int.class *//*contextStart*//*,
                int.class *//*contextEnd*//*,
                boolean.class *//*isRtl*//*,
                float[].class *//*advances*//*,
                int.class *//*advancesIndex*//*);
            } catch (Throwable ignored) {
              if (Config.BETA) {
                Logger.e(ignored);
              }
            }
            getTextRunAdvancesStr_attempted = true;
          }
        }
      }


      float res1 = 0f;
      long startMs1 = SystemClock.elapsedRealtime();
      for (int i = 0; i < 10000; i++) {
        float[] widths = pickWidths(end - start, true);
        try {
          Object ret = getTextRunAdvancesStr.invoke(p, in, start, end, 0, in.length(), Boolean.FALSE, widths, 0);
          res1 = (Float) ret;
        } catch (Throwable ignored) {
          if (Config.BETA) {
            Logger.e(ignored);
          }
          getTextRunAdvancesStr = null;
        }
      }
      long ms1 = SystemClock.elapsedRealtime() - startMs1;

      float res2 = 0f;
      long startMs2 = SystemClock.elapsedRealtime();
      for (int i = 0; i < 10000; i++) {
        res2 = p.getRunAdvance(in, start, end, 0, in.length(), false, end);
      }
      long ms2 = SystemClock.elapsedRealtime() - startMs2;

      if (ms2 < ms1) {
        Logger.v("getRunAdvance is faster: %dms vs %dms, %f vs %f", ms1, ms2, res1, res2);
      } else {
        Logger.e("getTextRunAdvances is faster: %dms vs %dms %f vs %f", ms1, ms2, res1, res2);
      }

      float result = 0f;

      if (getTextRunAdvancesStr != null) {
        float[] widths = pickWidths(end - start, true);
        try {
          Object ret = getTextRunAdvancesStr.invoke(p, in, start, end, 0, in.length(), Boolean.FALSE, widths, 0);
          result = (Float) ret;
        } catch (Throwable ignored) {
          if (Config.BETA) {
            Logger.e(ignored);
          }
          getTextRunAdvancesStr = null;
        }
      }

      if (result != 0f) {
        return result;
      }
    }

    return p.getRunAdvance(in, start, end, 0, in.length(), false, end);
  }*/

  /*private static void measureMeasureText (CharSequence in, int start, int end, Paint p) {
    final int count = end - start;

    long start1 = SystemClock.elapsedRealtime();
    for (int i = 0; i < 1000; i++) {
      boolean isRtl = false;
      if (in instanceof String) {
        isRtl = Strings.getTextDirection(in.toString(), start, end) == Strings.DIRECTION_RTL;
      }
      float res1 = p.getRunAdvance(in, start, end, 0, in.length(), isRtl, end);
    }
    long ms1 = SystemClock.elapsedRealtime() - start1;

    long start2 = SystemClock.elapsedRealtime();
    for (int i = 0; i < 1000; i++) {
      float[] widths = pickWidths(count, true);
      p.getTextWidths(in, start, end, widths);
      float res2 = sum(widths, count);
    }
    long ms2 = SystemClock.elapsedRealtime() - start2;

    if (ms1 < ms2) {
      Logger.v("getRunAdvance is faster: count=%d %dms vs %dms", count, ms1, ms2);
    } else {
      Logger.e("getTextWidths is faster: count=%d %dms vs %dms %s", count, ms1, ms2, in.subSequence(start, end));
    }
  }*/

  public static float measureText (@Nullable CharSequence in, int start, int end, @NonNull Paint p) {
    final int count = end - start;

    if (in == null || in.length() == 0 || count <= 0) {
      return 0;
    }

    if (p == null)
      throw new IllegalArgumentException();

    if (Config.USE_TEXT_ADVANCE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Strings.getTextDirection(in, start, end) != Strings.DIRECTION_RTL) {
      return p.getRunAdvance(in, start, end, 0, in.length(), false, end);
    } else {
      float[] widths = pickWidths(count, true);
      p.getTextWidths(in, start, end, widths);
      return ArrayUtils.sum(widths, count);
    }
  }

  public static byte[] computeSHA1(byte[] convertme, int offset, int len) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
      md.update(convertme, offset, len);
      return md.digest();
    } catch (Throwable t) {
      Log.e("Cannot compute SHA-1", t);
    }
    return new byte[20];
  }

  public static byte[] computeSHA256(byte[] convertme, int offset, int len) {
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
      md.update(convertme, offset, len);
      return md.digest();
    } catch (Throwable t) {
      Log.e("Cannot compute SHA-256", t);
    }
    return null;
  }

  private static byte[] encrypt (byte[] data, String algorithm) {
    if (data == null)
      return null;
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance(algorithm);
      return md.digest(data);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static String base64 (byte[] data) {
    StringBuilder sb = new StringBuilder(2 * data.length);
    for (byte element : data) {
      sb.append(Integer.toHexString((element & 0xFF) | 0x100), 1, 3);
    }
    return sb.toString();
  }

  private static String encrypt (String str, String algorithm) {
    if (str == null)
      return null;
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance(algorithm);
      return base64(md.digest(str.getBytes()));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] sha1 (byte[] sha1) {
    return encrypt(sha1, "SHA-1");
  }

  public static String sha256 (String sha256) {
    return encrypt(sha256, "SHA-256");
  }

  public static String md5 (String md5) {
    return encrypt(md5, "MD5");
  }

  public static <T> void deleteItem (T[] src, T[] dst, int i) {
    if (dst.length > 0) {
      System.arraycopy(src, 0, dst, 0, i);
      System.arraycopy(src, i + 1, dst, i, src.length - i - 1);
    }
  }

  public static String getManufacturer () {
    return StringUtils.ucfirst(Build.MANUFACTURER, Lang.locale());
  }

  public static int reverseBinaryIndex (int i) {
    if (i >= 0)
      throw new IllegalArgumentException();
    return i * -1 - 1;
  }

  public static int getVideoRotation (String path) {
    int rotation = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      MediaMetadataRetriever retriever = null;
      try {
        retriever = U.openRetriever(path);
        String rotationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        rotation = StringUtils.parseInt(rotationString);
      } catch (Throwable ignored) { }
      U.closeRetriever(retriever);
    }
    return rotation;
  }

  public static String getUsefulMetadata (@Nullable Tdlib tdlib) {
    AppBuildInfo buildInfo = org.thunderdog.challegram.unsorted.Settings.instance().getCurrentBuildInformation();
    String metadata = Lang.getAppBuildAndVersion(tdlib) + " (" + BuildConfig.COMMIT + ")\n" +
      (!buildInfo.getPullRequests().isEmpty() ? "PRs: " + buildInfo.pullRequestsList() + "\n" : "") +
      "TDLib: " + Td.tdlibVersion() + " (tdlib/td@" + Td.tdlibCommitHash() + ")\n" +
      "Android: " + SdkVersion.getPrettyName() + " (" + Build.VERSION.SDK_INT + ")" + "\n" +
      "Device: " + Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL + " (" + Build.DISPLAY + ")\n" +
      "Screen: " + Screen.widestSide() + "x" + Screen.smallestSide() + " (density: " + Screen.density() + ", fps: " + Screen.refreshRate() + ")" + "\n" +
      "Build: `" + Build.FINGERPRINT + "`\n" +
      "Package: " + UI.getAppContext().getPackageName();
    String installerName = U.getInstallerPackageName();
    if (!StringUtils.isEmpty(installerName)) {
      metadata += "\nInstaller: " + (U.VENDOR_GOOGLE_PLAY.equals(installerName) ? "Google Play" : installerName);
    }
    String fingerprint = U.getApkFingerprint("SHA1");
    if (!StringUtils.isEmpty(fingerprint)) {
      metadata += "\nFingerprint: `" + fingerprint + "`";
    }
    return metadata;
  }

  public static String getApkFingerprint (String algorithm) {
    return getApkFingerprint(algorithm, true);
  }

  /**
   * @param algorithm string like: SHA1, SHA256, MD5.
   */
  @SuppressWarnings("PackageManagerGetSignatures")
  @Nullable
  public static String getApkFingerprint (String algorithm, boolean needSeparator) {
    try {
      final PackageInfo info = UI.getAppContext().getPackageManager()
        .getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNATURES);
      for (Signature signature : info.signatures) {
        final MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(signature.toByteArray());

        final byte[] digest = md.digest();
        final StringBuilder fingerprint = new StringBuilder(digest.length * 2 + (digest.length - 1));
        for (int i = 0; i < digest.length; i++) {
          if (i != 0 && needSeparator) {
            fingerprint.append(":");
          }
          int b = digest[i] & 0xff;
          String hex = Integer.toHexString(b);
          if (hex.length() == 1) {
            fingerprint.append("0");
          }
          fingerprint.append(hex);
        }
        return fingerprint.toString();
      }
    } catch (Throwable e) {
      Log.e("Unable to get app fingerprint");
    }
    return null;
  }

  public static String resolveMimeType (String path) {
    return resolveMimeType(path, getExtension(path));
  }

  public static String resolveMimeType (String path, String extension) {
    if (!StringUtils.isEmpty(extension)) {
      String mimeType = TGMimeType.mimeTypeForExtension(extension);
      if (mimeType != null) {
        return mimeType;
      }
    }
    try {
      return MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
    } catch (Throwable t) {
      Log.w("Cannot resolver mimeType, path: %s", t, path);
    }
    return null;
  }

  public static void replaceItems (final RecyclerView.Adapter<?> adapter, final int oldItemCount) {
    int newItemCount = adapter.getItemCount();
    if (oldItemCount == newItemCount) {
      if (oldItemCount != 0) {
        adapter.notifyItemRangeChanged(0, newItemCount);
      }
    } else if (oldItemCount == 0) {
      adapter.notifyItemRangeInserted(0, newItemCount);
    } else if (newItemCount == 0) {
      adapter.notifyItemRangeRemoved(0, oldItemCount);
    } else if (newItemCount > oldItemCount) {
      adapter.notifyItemRangeInserted(oldItemCount, newItemCount - oldItemCount);
      adapter.notifyItemRangeChanged(0, oldItemCount);
    } else {
      adapter.notifyItemRangeRemoved(newItemCount, oldItemCount - newItemCount);
      adapter.notifyItemRangeChanged(0, newItemCount);
    }
  }

  public static void notifyItemsReplaced (final RecyclerView.Adapter<?> adapter, final int oldItemCount) {
    notifyItemsReplaced(adapter, oldItemCount, 0);
  }

  public static void notifyItemsReplaced (final RecyclerView.Adapter<?> adapter, final int oldItemCount, final int headerItemCount) {
    int newItemCount = adapter.getItemCount();
    if (newItemCount < headerItemCount)
      throw new IllegalStateException();
    if (oldItemCount == newItemCount) {
      if (oldItemCount != 0) {
        adapter.notifyItemRangeChanged(headerItemCount, newItemCount - headerItemCount);
      }
    } else if (oldItemCount == 0) {
      adapter.notifyItemRangeInserted(0, newItemCount);
    } else if (newItemCount == 0) {
      adapter.notifyItemRangeRemoved(0, oldItemCount);
    } else {
      // note: do not call notifyItemRangeChanged here
      if (oldItemCount > headerItemCount) {
        adapter.notifyItemRangeRemoved(headerItemCount, oldItemCount - headerItemCount);
      }
      adapter.notifyItemRangeInserted(headerItemCount, newItemCount - headerItemCount);
    }
  }

  public static void awaitLatch (CountDownLatch latch) {
    if (latch != null) {
      try {
        latch.await();
      } catch (Throwable t) {
        Log.i(t);
        throw new RuntimeException(t);
      }
    }
  }

  public static boolean awaitLatch (CountDownLatch latch, long time, TimeUnit unit) {
    if (latch != null) {
      try {
        return latch.await(time, unit);
      } catch (Throwable t) {
        Log.i(t);
        throw new RuntimeException(t);
      }
    }
    return false;
  }

  public static final String NO_LANGUAGE = "zz";

  public static Locale getDisplayLocaleOfSubtypeLocale (@NonNull final String localeString) {
    if (NO_LANGUAGE.equals(localeString)) {
      return UI.getResources().getConfiguration().locale;
    }
    return constructLocaleFromString(localeString);
  }

  @Nullable
  public static Locale constructLocaleFromString (@Nullable final String localeStr) {
    if (localeStr == null) {
      // TODO: Should this be Locale.ROOT?
      return null;
    }
    final String[] localeParams = localeStr.split("_", 3);
    if (localeParams.length == 1) {
      return new Locale(localeParams[0]);
    } else if (localeParams.length == 2) {
      return new Locale(localeParams[0], localeParams[1]);
    } else if (localeParams.length == 3) {
      return new Locale(localeParams[0], localeParams[1], localeParams[2]);
    }
    // TODO: Should return Locale.ROOT instead of null?
    return null;
  }

  public static @Nullable Bitmap tryDecodeVideoThumb (String path, long timeUs, int dstWidth, int dstHeight, @Nullable int[] rotation) {
    Bitmap bitmap = null;
    MediaMetadataRetriever retriever = null;
    try {
      retriever = U.openRetriever(path);
      if (rotation != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
          rotation[0] = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        if (Math.min(dstWidth, dstHeight) > 0) {
          int videoWidth = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
          int videoHeight = StringUtils.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
          if (Math.max(dstWidth, dstHeight) < Math.max(videoWidth, videoHeight)) {
            bitmap = retriever.getScaledFrameAtTime(timeUs, timeUs == -1 ? MediaMetadataRetriever.OPTION_CLOSEST_SYNC : MediaMetadataRetriever.OPTION_NEXT_SYNC, dstWidth, dstHeight);
          }
        }
      }
      if (bitmap == null) {
        if (timeUs == -1) {
          bitmap = retriever.getFrameAtTime();
        } else {
          bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_NEXT_SYNC);
        }
      }
    } catch (IllegalArgumentException ignored) {
      // Assume this is a corrupt video file
    } catch (RuntimeException | FileNotFoundException ignored) {
      // Assume this is a corrupt video file.
    }
    U.closeRetriever(retriever);
    return bitmap;
  }

  public static @Nullable Bitmap tryDecodeRegion (String path, Rect rect, BitmapFactory.Options opts) {
    if (Device.HAS_BUGGY_REGION_DECODER) {
      // There's some sort of native crash otherwise on Asus Zenphone 3
      if (opts != null && Math.max(opts.outWidth, opts.outHeight) > 1024) {
        return null;
      }
    }
    BitmapRegionDecoder decoder = null;
    Bitmap bitmap = null;
    try {
      decoder = BitmapRegionDecoder.newInstance(path, false);
      bitmap = decoder.decodeRegion(rect, opts);
    } catch (Throwable t) {
      Log.w(Log.TAG_IMAGE_LOADER, "Cannot decode region", t);
    }
    if (decoder != null) { try { decoder.recycle(); } catch (Throwable ignored) { } }
    return bitmap;
  }

  public static boolean checkLocationPermission (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    return true;
  }

  // Array utils

  /*public static <T> T[] fit (T[] array, T[] newArray) {
    for (int i = newArray.length; i < array.length; i++) {
      if (array[i] instanceof DestroyDelegate) {
        ((DestroyDelegate) array[i]).onDataDestroy();
      }
    }
    System.arraycopy(array, 0, newArray, 0, newArray.length);
    return array;
  }

  public static TextWrapper[] resize (TextWrapper[] array, int newSize) {
    return array.length == newSize ? array : fit(array, new TextWrapper[newSize]);
  }

  public static ProgressComponent[] resize (ProgressComponent[] array, int newSize) {
    return array.length == newSize ? array : fit(array, new ProgressComponent[newSize]);
  }

  public static SimplestCheckBox[] resize (SimplestCheckBox[] array, int newSize) {
    return array.length == newSize ? array : fit(array, new SimplestCheckBox[newSize]);
  }

  public static TGMessagePoll.OptionEntry[] resize (TGMessagePoll.OptionEntry[] array, int newSize) {
    return array.length == newSize ? array : fit(array, new TGMessagePoll.OptionEntry[newSize]);
  }*/

  public static float[] reuseLocalFloats (LocalVar<float[]> threadLocal, int initialCapacity) {
    float[] x = threadLocal.get();
    if (x != null) {
      for (int i = 0; i < x.length; i++) {
        x[i] = 0f;
      }
    } else {
      x = new float[initialCapacity];
    }
    return x;
  }

  public static int[] reuseLocalInts (LocalVar<int[]> threadLocal, int initialCapacity) {
    int[] x = threadLocal.get();
    if (x != null) {
      for (int i = 0; i < x.length; i++) {
        x[i] = 0;
      }
    } else {
      x = new int[initialCapacity];
    }
    return x;
  }

  /*public static Uri makeUriForFile (File file, @Nullable String mimeType) {
    return makeUriForFile(file, mimeType, false);
  }*/

  public static void set (@Nullable boolean[] out, boolean value) {
    if (out != null && out.length > 0) {
      out[0] = value;
    }
  }

  public static void set (@Nullable RunnableBool out, boolean value) {
    if (out != null) {
      out.runWithBool(value);
    }
  }

  public static Uri makeUriForFile (File file, @Nullable String mimeType, boolean isRetry) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      if (isRetry || "application/vnd.android.package-archive".equals(mimeType)) {
        return Uri.fromFile(file);
      }
    }
    return contentUriFromFile(file);
  }

  public static boolean canOpenFile (File file, @Nullable String mimeType) {
    try {
      return makeUriForFile(file, mimeType, false) != null;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public static MediaMetadataRetriever openRetriever (String path) throws RuntimeException, FileNotFoundException {
    MediaMetadataRetriever retriever = null;
    try {
      retriever = new MediaMetadataRetriever();
      if (path.startsWith("content://")) {
        retriever.setDataSource(UI.getContext(), Uri.parse(path));
      } else {
        File file = new File(path);
        if (!file.exists()) {
          throw new FileNotFoundException();
        }
        retriever.setDataSource(path);
      }
    } catch (Throwable t) {
      U.closeRetriever(retriever);
      throw t;
    }
    return retriever;
  }

  public static File sharedPreferencesFile (String name) {
    try {
      File appDir;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appDir = UI.getAppContext().getDataDir();
      } else {
        appDir = UI.getAppContext().getFilesDir().getParentFile();
      }
      File prefsDir = new File(appDir, "shared_prefs");
      if (prefsDir.exists()) {
        File prefsFile = new File(prefsDir, name + ".xml");
        if (prefsFile.exists()) {
          return prefsFile;
        }
      }
    } catch (Throwable t) {
      Log.e("Cannot retrieve shared prefs file", t);
    }
    return null;
  }

  public static boolean deleteSharedPreferences (String name) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return UI.getAppContext().deleteSharedPreferences(name);
      } else {
        File file = sharedPreferencesFile(name);
        return file != null && file.delete();
      }
    } catch (Throwable t) {
      Log.e("Cannot delete shared preferences, name:%s", name);
      return false;
    }
  }

  public static void closeRetriever (MediaMetadataRetriever retriever) {
    if (retriever != null) {
      try {
        retriever.release();
      } catch (Throwable t) {
        Log.w(t);
      }
    }
  }

  public static void closeFile (RandomAccessFile file) {
    if (file != null) {
      try {
        file.close();
      } catch (Throwable t) {
        Log.w(t);
      }
    }
  }

  public static void closeCursor (Cursor c) {
    if (c != null) {
      try {
        c.close();
      } catch (Throwable t) {
        Log.w(t);
      }
    }
  }

  public static int readShort (InputStream is) throws IOException {
    return is.read() | (is.read() << 8);
  }

  public static InputStream openInputStream (String path) throws Throwable {
    InputStream is;
    if (path.startsWith("content://")) {
      is = UI.getContext().getContentResolver().openInputStream(Uri.parse(path));
      if (is == null) {
        throw new IllegalArgumentException("getContentResolver().openInputStream() failed for path: " + path);
      }
    } else {
      is = new FileInputStream(path);
    }
    return is;
  }

  private static void skipGIFBlock (InputStream in) throws IOException {
    int blockSize = in.read();
    long count = 0;
    int n = 0;
    while (n < blockSize) {
      count = in.skip(blockSize - n);
      if (count == -1) {
        break;
      }
      n += count;
    }
    if (n < blockSize)
      throw new IllegalArgumentException();
  }

  private static byte[] readGIFBlock (InputStream in, int sizeLimit) throws IOException {
    int blockSize = in.read();
    int n = 0;
    if (sizeLimit != -1 && sizeLimit < blockSize)
      throw new IllegalArgumentException("block size: " + blockSize + ", limit: " + sizeLimit);
    byte[] block = new byte[blockSize];
    if (blockSize > 0) {
      int count = 0;
      while (n < blockSize) {
        count = in.read(block, n, blockSize - n);
        if (count == -1) {
          break;
        }
        n += count;
      }
      if (n < blockSize) {
        throw new IllegalArgumentException();
      }
    }
    return block;
  }

  public static boolean isAnimatedGIF (InputStream in) throws IOException {
    /*if (true) {
      GifDecoder decoder = new GifDecoder(in, true);
      return decoder.getFrameCount() > 1;
    }*/
    // ID
    StringBuilder b = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      b.append((char) in.read());
    }
    String id = b.toString();
    if (!id.startsWith("GIF"))
      throw new IllegalArgumentException("Unknown file id: " + id);

    // Logical Screen Descriptor
    int width = U.readShort(in);
    int height = U.readShort(in);
    int packed = in.read();
    boolean gctFlag = (packed & 0x80) != 0; // 1 : global color table flag
    // 2-4 : color resolution
    // 5 : gct sort flag
    int gctSize = 2 << (packed & 7); // 6-8 : gct size
    int bgIndex = in.read(); // background color index
    int pixelAspect = in.read(); // pixel aspect ratio

    if (gctFlag) {
      int nbytes = 3 * gctSize;
      while(nbytes > 0) {
        long skip = in.skip(nbytes);
        if (skip <= 0)
          break;
        nbytes -= skip;
      }
      if (nbytes != 0)
        throw new IllegalArgumentException();
    }

    while (true) {
      int code = in.read();
      switch (code) {
        case 0x2c: // image separator
          return false;
        case 0x21:
          code = in.read();
          switch (code) {
            case 0xf9: // graphics control extension
              return false;

            case 0xff: { // application extension
              /*int blockSize = in.read();
              if (blockSize != 11)
                return true;
              try {
                b = new StringBuilder(blockSize);
                for (int i = 0; i < blockSize; i++) {
                  b.append((char) in.read());
                }
                if (b.toString().equals("NETSCAPE2.0")) {
                  byte[] block = readGIFBlock(in, -1);
                  if (block[0] == 1) {
                    // loop count sub-block
                    int b1 = ((int) block[1]) & 0xff;
                    int b2 = ((int) block[2]) & 0xff;
                    int loopCount = (b2 << 8) | b1;
                    return loopCount == 0 || loopCount != 1;
                  }
                }
              } catch (Throwable t) {
                Log.e(t);
              }*/
              return true;
            }

            case 0xfe: // comment extension
            case 0x01: // plain text extension
            default:
              skipGIFBlock(in);
          }
          continue;
        case 0x3b: // terminator
          return false;
        case 0x00: // bad byte, but keep going and see what happens break;
        default:
          return false;
      }
    }
  }

  public static String getEGLErrorString (int error) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
      return GLUtils.getEGLErrorString(error);
    } else {
      switch (error) {
        case EGL10.EGL_SUCCESS:
          return "EGL_SUCCESS";
        case EGL10.EGL_NOT_INITIALIZED:
          return "EGL_NOT_INITIALIZED";
        case EGL10.EGL_BAD_ACCESS:
          return "EGL_BAD_ACCESS";
        case EGL10.EGL_BAD_ALLOC:
          return "EGL_BAD_ALLOC";
        case EGL10.EGL_BAD_ATTRIBUTE:
          return "EGL_BAD_ATTRIBUTE";
        case EGL10.EGL_BAD_CONFIG:
          return "EGL_BAD_CONFIG";
        case EGL10.EGL_BAD_CONTEXT:
          return "EGL_BAD_CONTEXT";
        case EGL10.EGL_BAD_CURRENT_SURFACE:
          return "EGL_BAD_CURRENT_SURFACE";
        case EGL10.EGL_BAD_DISPLAY:
          return "EGL_BAD_DISPLAY";
        case EGL10.EGL_BAD_MATCH:
          return "EGL_BAD_MATCH";
        case EGL10.EGL_BAD_NATIVE_PIXMAP:
          return "EGL_BAD_NATIVE_PIXMAP";
        case EGL10.EGL_BAD_NATIVE_WINDOW:
          return "EGL_BAD_NATIVE_WINDOW";
        case EGL10.EGL_BAD_PARAMETER:
          return "EGL_BAD_PARAMETER";
        case EGL10.EGL_BAD_SURFACE:
          return "EGL_BAD_SURFACE";
        case EGL11.EGL_CONTEXT_LOST:
          return "EGL_CONTEXT_LOST";
        default:
          return "0x" + Integer.toHexString(error);
      }
    }
  }

  public static boolean isValidBitmap (Bitmap bitmap) {
    return bitmap != null && !bitmap.isRecycled();
  }

  public static int calculateRemainingSeconds (long millis) {
    return (int) (Math.round((double) millis / 1000d));
  }

  public static int calculateDoneSeconds (long millis) {
    return (int) (Math.floor((double) millis / 1000d));
  }

  public static class QueryMapContainer {
    private final Map<String, List<String>> queryPairs;

    public QueryMapContainer (Map<String, List<String>> queryPairs) {
      this.queryPairs = queryPairs;
    }

    public Map<String, List<String>> get () {
      return queryPairs;
    }

    public @Nullable String getFirst (String key) {
      List<String> items = queryPairs != null ? queryPairs.get(key) : null;
      return items != null && !items.isEmpty() ? items.get(0) : null;
    }

    public @Nullable String getLast (String key) {
      List<String> items = queryPairs != null ? queryPairs.get(key) : null;
      return items != null && !items.isEmpty() ? items.get(items.size() - 1) : null;
    }
  }

  public static QueryMapContainer splitQuery (String link) {
    try {
      java.net.URI uri = java.net.URI.create(link);
      final Map<String, List<String>> query_pairs = new LinkedHashMap<>();
      final String[] pairs = uri.getQuery().split("&");
      for (String pair : pairs) {
        final int idx = pair.indexOf("=");
        final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
        if (!query_pairs.containsKey(key)) {
          query_pairs.put(key, new LinkedList<>());
        }
        final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
        query_pairs.get(key).add(value);
      }
      return new QueryMapContainer(query_pairs);
    } catch (Throwable ignored) {
      return new QueryMapContainer(null);
    }
  }

  public static String normalizeFilePath (String pathString) {
    if (StringUtils.isEmpty(pathString)) {
      return pathString;
    }
    while (true) {
      String newPath = N.readlink(pathString);
      if (StringUtils.isEmpty(newPath) || newPath.equals(pathString)) {
        break;
      }
      pathString = newPath;
    }
    try {
      String path = new File(pathString).getCanonicalPath();
      if (!StringUtils.isEmpty(path)) {
        pathString = path;
      }
    } catch (Exception e) {
      pathString = pathString.replace("/./", "/");
    }
    return pathString;
  }

  public static boolean isInternalUri (Uri uri) {
    String pathString = normalizeFilePath(uri.getPath());
    File dir = UI.getAppContext().getFilesDir();
    return !StringUtils.isEmpty(pathString) && pathString.startsWith(dir.getPath());
  }

  public static byte[] decodeQuotedPrintable(final byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
      for (int i = 0; i < bytes.length; i++) {
        final int b = bytes[i];
        if (b == '=') {
          final int u = Character.digit((char) bytes[++i], 16);
          final int l = Character.digit((char) bytes[++i], 16);
          buffer.write((char) ((u << 4) + l));
        } else {
          buffer.write(b);
        }
      }
      return buffer.toByteArray();
    } catch (Exception t) {
      Log.w(t);
    }
    return null;
  }

  public static Bitmap.CompressFormat compressFormat (boolean transparent) {
    if (transparent) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        return Bitmap.CompressFormat.WEBP;
      } else {
        return Bitmap.CompressFormat.PNG;
      }
    }
    return Bitmap.CompressFormat.JPEG;
  }

  public static boolean compress (Bitmap bitmap, int quality, String outPath) {
    try (final FileOutputStream out = new FileOutputStream(outPath)) {
      return bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
    } catch (Throwable t) {
      Log.w("Failed to compress bitmap", t);
    }
    return false;
  }

  public static void putExifOrientation (File file, int orientation) {
    try {
      ExifInterface exif = new ExifInterface(file);
      exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
      exif.saveAttributes();
    } catch (Throwable t) {
      Log.e("Unable to update exif orientation for path: %s", t, file.getPath());
    }
  }

  public static boolean setExifOrientation (byte[] jpeg, int[] offsetOutput, int orientation) {
    int length = offsetOutput[1];
    if (length > 0) {
      int offset = offsetOutput[0];
      boolean isLittleEndian = offsetOutput[2] == 1;
      unpackInt(orientation, jpeg, offset, length, isLittleEndian);
      return true;
    }
    return false;
  }

  public static int getExifOrientation (byte[] jpeg, @Nullable int[] offsetOutput) {
    if (jpeg == null) {
      return 0;
    }

    int offset = 0;
    int length = 0;

    while (offset + 3 < jpeg.length && (jpeg[offset++] & 0xFF) == 0xFF) {
      int marker = jpeg[offset] & 0xFF;

      if (marker == 0xFF) {
        continue;
      }
      offset++;

      if (marker == 0xD8 || marker == 0x01) {
        continue;
      }
      if (marker == 0xD9 || marker == 0xDA) {
        break;
      }

      length = packInt(jpeg, offset, 2, false);
      if (length < 2 || offset + length > jpeg.length) {
        return 0;
      }

      // Break if the marker is EXIF in APP1.
      if (marker == 0xE1 && length >= 8 &&
        packInt(jpeg, offset + 2, 4, false) == 0x45786966 &&
        packInt(jpeg, offset + 6, 2, false) == 0) {
        offset += 8;
        length -= 8;
        break;
      }

      offset += length;
      length = 0;
    }

    if (length > 8) {
      int tag = packInt(jpeg, offset, 4, false);
      if (tag != 0x49492A00 && tag != 0x4D4D002A) {
        return ExifInterface.ORIENTATION_UNDEFINED;
      }
      boolean littleEndian = (tag == 0x49492A00);

      int count = packInt(jpeg, offset + 4, 4, littleEndian) + 2;
      if (count < 10 || count > length) {
        return ExifInterface.ORIENTATION_UNDEFINED;
      }
      offset += count;
      length -= count;

      count = packInt(jpeg, offset - 2, 2, littleEndian);
      while (count-- > 0 && length >= 12) {
        tag = packInt(jpeg, offset, 2, littleEndian);
        if (tag == 0x0112) {
          if (offsetOutput != null) {
            offsetOutput[0] = offset + 8;
            offsetOutput[1] = 2;
            offsetOutput[2] = littleEndian ? 1 : 0;
          }
          return packInt(jpeg, offset + 8, 2, littleEndian);
        }
        offset += 12;
        length -= 12;
      }
    }
    return ExifInterface.ORIENTATION_UNDEFINED;
  }

  public static Matrix exifMatrix (int width, int height, int orientation) {
    switch (orientation) {
      case ExifInterface.ORIENTATION_UNDEFINED:
      case ExifInterface.ORIENTATION_NORMAL:
        return null;
    }
    Matrix matrix = new Matrix();
    exifMatrix(width, height, orientation, matrix);
    return matrix;
  }

  public static void exifMatrix (int width, int height, int orientation, Matrix matrix) {
    matrix.reset();
    switch (orientation) {
      case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
        matrix.postScale(-1f, 1f, width / 2f, height / 2f);
        break;
      case ExifInterface.ORIENTATION_FLIP_VERTICAL:
        matrix.postScale(1f, -1f, width / 2f, height / 2f);
        break;
      case ExifInterface.ORIENTATION_ROTATE_90:
        matrix.postRotate(90);
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        matrix.postRotate(180);
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        matrix.postRotate(270);
        break;
      case ExifInterface.ORIENTATION_TRANSPOSE:
        matrix.postScale(-1f, 1f, width / 2f, height / 2f);
        matrix.postRotate(270);
        break;
      case ExifInterface.ORIENTATION_TRANSVERSE:
        matrix.postScale(-1f, 1f, width, height / 2f);
        matrix.postRotate(90);
        break;
    }
  }

  private static void unpackInt (int value, byte[] bytes, int offset, int length, boolean littleEndian) {
    int step = 1;
    if (!littleEndian) {
      step = -1;
      offset += length - 1;
    }
    int shift = 0;
    while (length-- > 0) {
      bytes[offset] = (byte) ((value >> shift) & 0xff);
      shift += 8;
      offset += step;
    }
  }

  private static int packInt (byte[] bytes, int offset, int length, boolean littleEndian) {
    int step = 1;
    if (littleEndian) {
      offset += length - 1;
      step = -1;
    }

    int value = 0;
    while (length-- > 0) {
      value = (value << 8) | (bytes[offset] & 0xFF);
      offset += step;
    }
    return value;
  }

  // https://android.googlesource.com/platform/frameworks/base/+/e098050/services/java/com/android/server/TwilightCalculator.java

  public static final int DAY = 0;
  public static final int NIGHT = 1;
  private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0f);
  // element for calculating solar transit.
  private static final float J0 = 0.0009f;
  // correction for civil twilight
  private static final float ALTIDUTE_CORRECTION_CIVIL_TWILIGHT = -0.104719755f;
  // coefficients for calculating Equation of Center.
  private static final float C1 = 0.0334196f;
  private static final float C2 = 0.000349066f;
  private static final float C3 = 0.000005236f;
  private static final float OBLIQUITY = 0.40927971f;
  // Java time on Jan 1, 2000 12:00 UTC.
  private static final long UTC_2000 = 946728000000L;

  /**
   * calculates the civil twilight bases on time and geo-coordinates.
   *
   * @param time time in milliseconds.
   * @param latitude latitude in degrees.
   * @param longitude latitude in degrees.
   */
  public static long[] calculateTwilight (long time, double latitude, double longitude) {
    final float daysSince2000 = (float) (time - UTC_2000) / DateUtils.DAY_IN_MILLIS;
    // mean anomaly
    final float meanAnomaly = 6.240059968f + daysSince2000 * 0.01720197f;
    // true anomaly
    final double trueAnomaly = meanAnomaly + C1 * Math.sin(meanAnomaly) + C2
      * Math.sin(2 * meanAnomaly) + C3 * Math.sin(3 * meanAnomaly);
    // ecliptic longitude
    final double solarLng = trueAnomaly + 1.796593063d + Math.PI;
    // solar transit in days since 2000
    final double arcLongitude = -longitude / 360;
    float n = Math.round(daysSince2000 - J0 - arcLongitude);
    double solarTransitJ2000 = n + J0 + arcLongitude + 0.0053d * Math.sin(meanAnomaly)
      + -0.0069d * Math.sin(2 * solarLng);
    // declination of sun
    double solarDec = Math.asin(Math.sin(solarLng) * Math.sin(OBLIQUITY));
    final double latRad = latitude * DEGREES_TO_RADIANS;
    double cosHourAngle = (Math.sin(ALTIDUTE_CORRECTION_CIVIL_TWILIGHT) - Math.sin(latRad)
      * Math.sin(solarDec)) / (Math.cos(latRad) * Math.cos(solarDec));
    // The day or night never ends for the given date and location, if this value is out of
    // range.
    if (cosHourAngle >= 1) {
      return new long[] {-1, -1, NIGHT};
    } else if (cosHourAngle <= -1) {
      return new long[] {-1, -1, DAY};
    }
    float hourAngle = (float) (Math.acos(cosHourAngle) / (2 * Math.PI));
    long sunset = Math.round((solarTransitJ2000 + hourAngle) * DateUtils.DAY_IN_MILLIS) + UTC_2000;
    long sunrise = Math.round((solarTransitJ2000 - hourAngle) * DateUtils.DAY_IN_MILLIS) + UTC_2000;
    int state;
    if (sunrise < time && sunset > time) {
      state = DAY;
    } else {
      state = NIGHT;
    }
    return new long[] {sunset, sunrise, state};
  }

  public static boolean isWiredHeadsetOn (AudioManager am) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      android.media.AudioDeviceInfo[] infos = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
      if (infos == null || infos.length == 0)
        return false;
      for (android.media.AudioDeviceInfo info : infos) {
        switch (info.getType()) {
          case android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
          case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
          case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
          case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
            return true;
        }
      }
      return false;
    } else {
      return am.isWiredHeadsetOn() || am.isBluetoothA2dpOn();
    }
  }

  // Emulator detection

  // wake lock

  /**
   * Run a runnable with a wake lock. Ensures that the lock is safely acquired and released.
   */
  public static void runWithLock (@NonNull Context context, int lockType, long timeout, @NonNull String tag, @NonNull Runnable task) {
    PowerManager.WakeLock wakeLock = null;
    try {
      wakeLock = acquireWakelock(context, lockType, timeout, tag);
      task.run();
      Log.d("Acquired wakelock, tag: %s", tag);
    } finally {
      if (wakeLock != null) {
        release(wakeLock, tag);
      }
    }
  }

  public static PowerManager.WakeLock acquireWakelock (@NonNull Context context, int lockType, long timeout, @NonNull String tag) {
    try {
      PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
      PowerManager.WakeLock wakeLock = powerManager.newWakeLock(lockType, tag);
      wakeLock.acquire(timeout);
      return wakeLock;
    } catch (Throwable t) {
      return null;
    }
  }

  public static void release (@NonNull PowerManager.WakeLock wakeLock, @NonNull String tag) {
    try {
      if (wakeLock.isHeld()) {
        wakeLock.release();
        Log.v("Released wakelock, tag: %s", tag);
      } else {
        Log.v("Wakelock isn't held, tag: %s", tag);
      }
    } catch (Throwable t) {
      Log.w("Failed to release wakelock, tag: %s", t, tag);
    }
  }

  public static boolean externalMemoryAvailable () {
    return android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);
  }

  public static long getAvailableInternalMemorySize () {
    File path = Environment.getDataDirectory();
    StatFs stat = new StatFs(path.getPath());
    long blockSize, availableBlocks;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      blockSize = stat.getBlockSizeLong();
      availableBlocks = stat.getAvailableBlocksLong();
    } else {
      blockSize = stat.getBlockSize();
      availableBlocks = stat.getAvailableBlocks();
    }
    return availableBlocks * blockSize;
  }

  public static long getTotalInternalMemorySize () {
    File path = Environment.getDataDirectory();
    StatFs stat = new StatFs(path.getPath());
    long blockSize, totalBlocks;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      blockSize = stat.getBlockSizeLong();
      totalBlocks = stat.getBlockCountLong();
    } else {
      blockSize = stat.getBlockSize();
      totalBlocks = stat.getBlockCount();
    }
    return totalBlocks * blockSize;
  }

  public static long getAvailableExternalMemorySize () {
    if (externalMemoryAvailable()) {
      File path = Environment.getExternalStorageDirectory();
      StatFs stat = new StatFs(path.getPath());
      long blockSize, availableBlocks;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        blockSize = stat.getBlockSizeLong();
        availableBlocks = stat.getAvailableBlocksLong();
      } else {
        blockSize = stat.getBlockSize();
        availableBlocks = stat.getAvailableBlocks();
      }
      return availableBlocks * blockSize;
    } else {
      return -1;
    }
  }

  public static long getTotalExternalMemorySize () {
    if (externalMemoryAvailable()) {
      File path = Environment.getExternalStorageDirectory();
      StatFs stat = new StatFs(path.getPath());
      long blockSize, totalBlocks;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        blockSize = stat.getBlockSizeLong();
        totalBlocks = stat.getBlockCountLong();
      } else {
        blockSize = stat.getBlockSize();
        totalBlocks = stat.getBlockCount();
      }
      return totalBlocks * blockSize;
    } else {
      return -1;
    }
  }

  // ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION opened, but the permission is still not granted. Ignore until the app restarts.

  public static boolean canManageStorage () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return Environment.isExternalStorageLegacy() || Environment.isExternalStorageManager();
    }
    return true; // Q allows for requestExternalStorage
  }

  @TargetApi(Build.VERSION_CODES.R)
  public static void requestManageStorage (Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
      intent.setData(Uri.parse("package:" + context.getPackageName()));
      ((Activity) context).startActivityForResult(intent, Intents.ACTIVITY_RESULT_MANAGE_STORAGE);
    }
  }

  public static boolean canReadFile (String url) {
    try {
      return new File(url).canRead();
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public static boolean canReadContentUri (Uri uri) {
    try (InputStream ignored = openInputStream(uri.toString())) {
      return true;
    } catch (Throwable ignored2) {
      return false;
    }
  }

  public static String getCpuArchitecture () {
    return System.getProperty("os.arch");
  }

  public static void copyText (CharSequence text) {
    //noinspection ObsoleteSdkInt
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      android.content.ClipboardManager clipboard = (android.content.ClipboardManager) UI.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard != null) {
        android.content.ClipData clip = null;
        if (text instanceof Spanned) {
          String htmlText = TD.toHtmlCopyText((Spanned) text);
          if (!StringUtils.isEmpty(htmlText)) {
            clip = android.content.ClipData.newHtmlText(BuildConfig.PROJECT_NAME, text, htmlText);
          }
        }
        if (clip == null) {
          clip = android.content.ClipData.newPlainText(BuildConfig.PROJECT_NAME, text);
        }
        clipboard.setPrimaryClip(clip);
      }
    } else {
      //noinspection deprecation
      android.text.ClipboardManager clipboard = (android.text.ClipboardManager) UI.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard != null) {
        //noinspection deprecation
        clipboard.setText(text);
      }
    }
  }

  public static CharSequence getPasteText (Context context) {
    //noinspection ObsoleteSdkInt
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      android.content.ClipboardManager clipboard = (android.content.ClipboardManager) UI.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard != null) {
        android.content.ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() != 1) {
          return null;
        }
        android.content.ClipData.Item clipItem = clipData.getItemAt(0);
        if (clipData.getDescription().hasMimeType("text/html")) {
          String htmlText = clipItem.getHtmlText();
          return TD.htmlToCharSequence(htmlText);
        } else if (clipData.getDescription().hasMimeType("text/plain")) {
          return clipItem.getText();
        }
        return null;
      }
    } else {
      //noinspection deprecation
      android.text.ClipboardManager clipboard = (android.text.ClipboardManager) UI.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard != null) {
        //noinspection deprecation
        return clipboard.getText();
      }
    }
    return null;
  }

  public static boolean setRect (RectF rectF, float left, float top, float right, float bottom) {
    if (rectF.left != left || rectF.top != top || rectF.right != right || rectF.bottom != bottom) {
      rectF.set(left, top, right, bottom);
      return true;
    }
    return false;
  }
}
