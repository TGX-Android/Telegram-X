/**
 * File created on 03/05/15 at 21:42
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.tool;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import androidx.core.app.ActivityCompat;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.receiver.LiveLocationReceiver;
import org.thunderdog.challegram.receiver.TGShareBroadcastReceiver;
import org.thunderdog.challegram.theme.Theme;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;

public class Intents {
  public static final int ACTIVITY_RESULT_GOOGLE_PLAY_UPDATE = 10001;
  public static final int ACTIVITY_RESULT_IMAGE_CAPTURE = 100;
  public static final int ACTIVITY_RESULT_GALLERY = 101;
  public static final int ACTIVITY_RESULT_AUDIO = 102;
  public static final int ACTIVITY_RESULT_RESOLUTION = 103;
  public static final int ACTIVITY_RESULT_GALLERY_FILE = 104;
  public static final int ACTIVITY_RESULT_FILES = 105;
  public static final int ACTIVITY_RESULT_RESOLUTION_INLINE = 106;
  public static final int ACTIVITY_RESULT_RINGTONE = 107;
  public static final int ACTIVITY_RESULT_RINGTONE_NOTIFICATION = 108;
  public static final int ACTIVITY_RESULT_VIDEO_CAPTURE = 109;
  public static final int ACTIVITY_RESULT_MANAGE_STORAGE = 110;

  private static final String PACKAGE_NAME = BuildConfig.APPLICATION_ID; // UI.getAppContext().getPackageName();

  // "chat_id" -> long
  public static final String ACTION_OPEN_CHAT = PACKAGE_NAME + ".OPEN_CHAT";

  public static final String ACTION_OPEN_CALL = PACKAGE_NAME + ".OPEN_CALL";

  public static final String ACTION_END_CALL = PACKAGE_NAME + ".END_CALL";
  public static final String ACTION_DECLINE_CALL = PACKAGE_NAME + ".DECLINE_CALL";
  public static final String ACTION_ANSWER_CALL = PACKAGE_NAME + ".ANSWER_CALL";

  public static final String ACTION_OPEN_LOGS = PACKAGE_NAME + ".OPEN_LOGS";
  public static final String ACTION_OPEN_PLAYER = PACKAGE_NAME + ".OPEN_PLAYER";

  public static final String ACTION_LOCATION_VIEW = PACKAGE_NAME + ".ACTION_VIEW_LOCATION";
  public static final String ACTION_LOCATION_RESOLVE = PACKAGE_NAME + ".ACTION_RESOLVE_LOCATION";
  public static final String ACTION_LOCATION_STOP = PACKAGE_NAME + ".ACTION_STOP_LOCATION";
  public static final String CHANNEL_ID_LOCATION = "location";

  public static final String ACTION_OPEN_MAIN = PACKAGE_NAME + ".OPEN_MAIN";

  public static final String ACTION_MESSAGE_REPLY = PACKAGE_NAME + ".ACTION_MESSAGE_REPLY";
  public static final String ACTION_MESSAGE_MUTE = PACKAGE_NAME + ".ACTION_MESSAGE_MUTE";
  public static final String ACTION_MESSAGE_READ = PACKAGE_NAME + ".ACTION_MESSAGE_READ";
  /// public static final String ACTION_MESSAGE_HIDE = PACKAGE_NAME + ".ACTION_MESSAGE_HIDE";
  public static final String ACTION_MESSAGE_HEARD = PACKAGE_NAME + ".ACTION_MESSAGE_HEARD"; // chat_id, last_message_id

  // public static final String ACTION_NOTIFICATION_REMOVE = PACKAGE_NAME + ".ACTION_NOTIFICATION_REMOVE"; // account_id, chat_id, last_message_id

  public static final String ACTION_PLAYBACK_SKIP_PREVIOUS = PACKAGE_NAME + ".ACTION_PLAY_SKIP_PREVIOUS";
  public static final String ACTION_PLAYBACK_SKIP_NEXT = PACKAGE_NAME + ".ACTION_PLAY_SKIP_NEXT";
  public static final String ACTION_PLAYBACK_STOP = PACKAGE_NAME + ".ACTION_PLAY_STOP";
  public static final String ACTION_PLAYBACK_PLAY = PACKAGE_NAME + ".ACTION_PLAY_PLAY";
  public static final String ACTION_PLAYBACK_PAUSE = PACKAGE_NAME + ".ACTION_PLAY_PAUSE";
  public static final String CHANNEL_ID_PLAYBACK = "playback";

  @TargetApi(Build.VERSION_CODES.O)
  public static String newSimpleChannel (String channelId, @StringRes int channelName) {
    NotificationManager m = (NotificationManager) UI.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (m != null) {
      android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, Lang.getString(channelName), NotificationManager.IMPORTANCE_LOW);
      channel.enableVibration(false);
      channel.enableLights(false);
      channel.setSound(null, null);
      m.createNotificationChannel(channel);
    }
    return channelId;
  }

  public static String getCleanAction (String action) {
    if (!StringUtils.isEmpty(action)) {
      int i = action.indexOf("?random_id=");
      if (i != -1) {
        action = action.substring(0, i);
      }
    }
    return action;
  }

  public static String randomAction (String action) {
    return action + "?random_id=" + Math.random();
  }

  public static boolean openGooglePlay (String packageName) {
    return
      openUri("market://details?id=" + packageName) ||
      openUri("https://play.google.com/store/apps/details?id=" + packageName);
  }

  public static boolean openSelfGooglePlay () {
    return Intents.openGooglePlay(BuildConfig.APPLICATION_ID);
  }

  public static boolean openUri (String uri) {
    if (uri != null) {
      try {
        Intent intent;

        intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final BaseActivity context = UI.getUiContext();
        if (context != null) {
          context.startActivity(intent);
          return true;
        }
      } catch (Throwable t) {
        Log.w("Cannot open uri: %s", t, uri);
      }
    }
    return false;
  }

  private static boolean openExcludeCurrentImpl (Intent intent, @Nullable Bundle options) {
    Uri uri = intent.getData();
    if (uri == null || !("http".equals(intent.getScheme()) || "https".equals(intent.getScheme()))) {
      return false;
    }
    try {
      intent.setData(Uri.parse("https://www.example.com/"));

      PackageManager packageManager = UI.getAppContext().getPackageManager();
      List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0); // PackageManager.MATCH_ALL
      List<Intent> targetIntents = new ArrayList<>();
      String[] blackList = {BuildConfig.APPLICATION_ID, "org.telegram.messenger"};
      for (ResolveInfo currentInfo : activities) {
        String packageName = currentInfo.activityInfo.packageName;
        if (ArrayUtils.indexOf(blackList, packageName) == -1) {
          Intent targetIntent = new Intent(android.content.Intent.ACTION_VIEW);
          targetIntent.setPackage(packageName);
          targetIntent.setData(uri);
          targetIntents.add(targetIntent);
        }
      }
      final BaseActivity context = UI.getUiContext();
      if (context != null) {
        if (targetIntents.size() == 1) {
          ActivityCompat.startActivity(context, targetIntents.get(0), options);
          return true;
        } else if (targetIntents.size() > 0) {
          Intent chooserIntent = Intent.createChooser(targetIntents.remove(0), Lang.getString(R.string.OpenInExternalApp));
          if (!targetIntents.isEmpty())
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toArray(new Parcelable[0]));
          ActivityCompat.startActivity(context, chooserIntent, options);
          return true;
        }
      }
    } catch (Throwable t) {
      Log.e("Unable to find proper activity", t);
    }
    return false;
  }

  public static boolean openUriInBrowser (Uri uri) {
    if (openInAppBrowser(UI.getUiContext(), uri, false)) {
      return true;
    }
    Intent intent = new Intent();
    intent.setAction(android.content.Intent.ACTION_VIEW);
    intent.setData(uri);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return openExcludeCurrentImpl(intent, null);
  }

  public static void sendEmail (String emailAddress, String subject, String text) {
    sendEmail(emailAddress, subject, text, null);
  }

  public static void sendEmail (String emailAddress, String subject, String text, @Nullable String fallbackText) {
    try {
      Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
        "mailto", emailAddress, null));
      intent.putExtra(Intent.EXTRA_SUBJECT, subject);
      intent.putExtra(Intent.EXTRA_TEXT, text);
      intent.putExtra(Intent.EXTRA_EMAIL, new String[] {emailAddress});
      UI.startActivity(Intent.createChooser(intent, Lang.getString(R.string.SendMessageToX, emailAddress)));
    } catch (Throwable t) {
      if (fallbackText != null) {
        UI.showToast(fallbackText, Toast.LENGTH_LONG);
      } else {
        UI.showToast(R.string.NoEmailApp, Toast.LENGTH_SHORT);
        UI.showToast(Lang.getString(R.string.SendMessageToX, emailAddress), Toast.LENGTH_LONG);
      }
    }
  }

  public static void sendEmail (String email) {
    try {
      Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
        "mailto", email, null));
      intent.putExtra(Intent.EXTRA_SUBJECT, "");
      intent.putExtra(Intent.EXTRA_TEXT, "");
      intent.putExtra(Intent.EXTRA_EMAIL, new String[] {email});
      UI.startActivity(Intent.createChooser(intent, Lang.getString(R.string.SendMessageToX, email)));
    } catch (Throwable t) {
      UI.showToast("No Email app found", Toast.LENGTH_SHORT);
    }
  }

  public static void openLink (String url) {
    if (!StringUtils.isEmpty(url)) {
      Uri uri = Strings.wrapHttps(url);
      if (uri != null && !openLink(uri)) {
        String scheme = uri.getScheme();
        if (Strings.isValidLink(scheme) && scheme.contains("/")) {
          openLink("http://" + uri);
        }
      }
    }
  }

  private static boolean openLink (Uri uri) {
    if (uri != null) {
      try {
        BaseActivity context = UI.getUiContext();
        if (UI.getUiState() == UI.STATE_RESUMED && openInAppBrowser(context, uri, false)) {
          return true;
        }

        Intent intent;

        intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        UI.startActivity(intent);
        return true;
      } catch (Throwable throwable) {
        Log.w("Cannot open link: %s", throwable, uri);
      }
    }
    return false;
  }

  private static @Nullable ArrayList<Uri> openedFiles;

  private static void revokeFileReadPermission (Uri uri) {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
      try {
        UI.getAppContext().revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Throwable e) {
        Log.e("Cannot revokeUriPermission", e);
      }
    }
  }

  public static void revokeFileReadPermissions () {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
      synchronized (Intents.class) {
        if (openedFiles != null) {
          while (!openedFiles.isEmpty()) {
            int index = openedFiles.size() - 1;
            Uri uri = openedFiles.get(index);
            revokeFileReadPermission(uri);
            openedFiles.remove(index);
          }
        }
      }
    }
  }

  public static boolean openFile (File file, @Nullable String mimeType) {
    return openFile(file, mimeType, false);
  }

  private static boolean openFile (final File file, @Nullable String mimeTypeRaw, final boolean isRetry) {
    if (!isRetry) {
      String newMimeType = U.resolveMimeType(file.getPath());
      if (!StringUtils.isEmpty(newMimeType)) {
        mimeTypeRaw = newMimeType;
      }
    }
    final String mimeType = mimeTypeRaw;
    if (U.requestPermissionsIfNeeded(() -> openFile(file, mimeType, isRetry), Manifest.permission.READ_EXTERNAL_STORAGE))
      return false;

    Uri uri = U.makeUriForFile(file, mimeType, isRetry);

    if (uri == null) {
      return false;
    }
    try {
      Intent intent;

      boolean isApk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && StringUtils.equalsOrBothEmpty(mimeType, "application/vnd.android.package-archive");
      if (isApk) {
        intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
      } else {
        intent = new Intent(Intent.ACTION_VIEW);
      }
      intent.setDataAndType(uri, mimeType);

      if (isApk || "content".equals(uri.getScheme())) {
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      }

      PackageManager pm = UI.getAppContext().getPackageManager();

      // Workaround for Android bug.
      // grantUriPermission also needed for KITKAT,
      // see https://code.google.com/p/android/issues/detail?id=76683
      if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
        List<ResolveInfo> resInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
          String packageName = resolveInfo.activityInfo.packageName;
          UI.getAppContext().grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        synchronized (Intents.class) {
          if (openedFiles == null) {
            openedFiles = new ArrayList<>();
          }
          if (!openedFiles.contains(uri)) {
            openedFiles.add(uri);
          }
        }
      }

      if (intent.resolveActivity(pm) != null) {
        UI.startActivity(intent);
        return true;
      }
    } catch (Throwable t) {
      Log.e("Cannot open Intent", t);
      UI.showToast(t.toString(), Toast.LENGTH_LONG);
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
      synchronized (Intents.class) {
        revokeFileReadPermission(uri);
        if (openedFiles != null) {
          openedFiles.remove(uri);
        }
      }
    }

    Log.e("ACTION_VIEW failed. Mime: %s, Uri:\n%s", mimeType, uri.toString());

    if (!isRetry && !StringUtils.isEmpty(mimeType)) {
      if (!mimeType.endsWith("/*")) {
        int i = mimeType.lastIndexOf('/');
        if (i != -1) {
          return openFile(file, mimeType.substring(0, i + 1) + "*", true);
        }
      }
      return openFile(file, null, true);
    }

    return !isRetry && openFile(file, mimeType, true);
  }

  private static File lastOutputMedia;

  public static File takeLastOutputMedia () {
    File file = lastOutputMedia;
    if (file != null && !file.exists()) {
      file = null;
    }
    lastOutputMedia = null;
    return file;
  }

  public static void openCamera (Context context, boolean isPrivate, boolean isVideo) {
    if (isVideo) {
      if (U.requestPermissionsIfNeeded(() -> openCamera(context, isPrivate, isVideo), Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        return;
    } else {
      if (U.requestPermissionsIfNeeded(() -> openCamera(context, isPrivate, isVideo), Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        return;
    }
    try {
      Intent intent = new Intent(isVideo ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
      File image = U.generateMediaPath(isPrivate, isVideo);
      if (image != null) {
        Uri uri = U.contentUriFromFile(image);
        if (uri == null) {
          return;
        }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
      }
      lastOutputMedia = image;
      UI.getContext(context).startActivityForResult(intent, isVideo ? ACTIVITY_RESULT_VIDEO_CAPTURE : ACTIVITY_RESULT_IMAGE_CAPTURE);
    } catch (Throwable t) {
      Log.w("Cannot open camera intent", t);
    }
  }

  public static void openIntent (String action) {
    try {
      Intent intent = new Intent(action);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      UI.startActivity(intent);
    } catch (Throwable t) {
      Log.w("Cannot open settings intent", t);
    }
  }

  public static void openWirelessSettings () {
    openIntent(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
  }

  public static void openAirplaneSettings () {
    openIntent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS);
  }

  public static void openPermissionSettings () {
    try {
      final Intent intent;

      intent = new Intent();
      intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setData(Uri.parse("package:" + UI.getAppContext().getPackageName()));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
      UI.startActivity(intent);
    } catch (Throwable t) {
      Log.w("Cannot open settings intent", t);
    }
  }

  public static void openGallery (boolean sendAsFile) {
    if (U.requestPermissionsIfNeeded(() -> openGallery(sendAsFile), Manifest.permission.READ_EXTERNAL_STORAGE))
      return;

    try {
      Intent intent;

      intent = new Intent(Intent.ACTION_PICK);
      intent.setType("image/*");

      UI.startActivityForResult(intent, sendAsFile ? ACTIVITY_RESULT_GALLERY_FILE : ACTIVITY_RESULT_GALLERY);
    } catch (Throwable t) {
      Log.w("Cannot open gallery intent", t);
    }
  }

  public static void shareText (String text) {
    try {
      Intent intent;

      intent = new Intent(Intent.ACTION_SEND);
      intent.setType("text/plain");
      intent.putExtra(Intent.EXTRA_TEXT, text);

      UI.startActivity(Intent.createChooser(intent, Lang.getString(R.string.ShareTitleText)));
    } catch (Throwable t) {
      Log.w("Cannot share text", t);
    }
  }

  public static void openAudio () {
    if (U.requestPermissionsIfNeeded(Intents::openAudio, Manifest.permission.READ_EXTERNAL_STORAGE))
      return;

    try {
      Intent intent;

      intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("audio/*");

      UI.startActivityForResult(intent, ACTIVITY_RESULT_AUDIO);
    } catch (Throwable t) {
      Log.w("Cannot open audio intent", t);
    }
  }

  public static void openNumber (String number) {
    try {
      Intent intent;

      intent = new Intent(Intent.ACTION_DIAL);
      intent.setData(Uri.parse("tel:" + number));

      UI.startActivity(intent);
    } catch (Throwable t) {
      Log.w("Cannot open dial intent", t);
    }
  }

  private static String getDestination (double latitude, double longitude, String title, String address, boolean forceCoordinates) {
    StringBuilder b = new StringBuilder();
    if (!StringUtils.isEmpty(title)) {
      b.append(title);
      b.append(", ");
    }
    if (!StringUtils.isEmpty(address)) {
      b.append(address);
      if (forceCoordinates) {
        b.append(", ").append(latitude).append(",").append(longitude);
      }
    } else {
      b.append(latitude);
      b.append(",");
      b.append(longitude);
    }
    return b.toString();
  }

  public static void openDirections (double latitude, double longitude, String title, String address) {
    try {
      Intent intent;

      String destination = URLEncoder.encode(getDestination(latitude, longitude, title, address, false), "UTF-8");

      intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + destination));

      UI.startActivity(intent);
    } catch (Throwable t) {
      Log.w("Cannot launch directions intent", t);
      openMap(latitude, longitude, title, address);
    }
  }

  public static boolean openMap (double latitude, double longitude, String title, String address) {
    try {
      Intent intent;

      String destination = URLEncoder.encode(getDestination(latitude, longitude, title, address, true), "UTF-8");

      intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse("https://www.google.com/maps/search/?api=1&query=" + destination));

      UI.startActivity(intent);
      return true;
    } catch (Throwable t) {
      Log.w("Cannot launch map intent", t);
      return openMap(latitude, longitude);
    }
  }

  public static boolean openMap (double latitude, double longitude) {
    try {
      Intent intent;

      intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f", latitude, longitude, latitude, longitude)));

      UI.startActivity(intent);
      return true;
    } catch (Throwable t) {
      Log.w("Cannot open map", t);
      return false;
    }
  }

  public static void sendSms (String phoneNumber, String text) {
    try {
      try {
        Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phoneNumber));
        smsIntent.putExtra("sms_body", text);
        UI.startActivity(smsIntent);
      } catch (Throwable ignored) {
        Intent smsIntent = new Intent(android.content.Intent.ACTION_VIEW);
        smsIntent.setType("vnd.android-dir/mms-sms");
        smsIntent.putExtra("address", phoneNumber);
        smsIntent.putExtra("sms_body", text);
        UI.startActivity(smsIntent);
      }
    } catch (Throwable t) {
      Log.w("Cannot send SMS", t);
    }
  }

  public static Intent secureIntent (Intent intent, boolean allowStopped) {
    intent.setPackage(BuildConfig.APPLICATION_ID);
    if (allowStopped) {
      intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
    }
    // intent.setClass(UI.getAppContext(), MainActivity.class);
    return intent;
  }

  public static Intent valueOfCall () {
    Intent intent = new Intent(UI.getContext(), MainActivity.class);
    secureIntent(intent, false);
    intent.setAction(Intents.ACTION_OPEN_CALL);
    return intent;
  }

  public static Intent valueOfPlayer (int accountId) {
    Intent intent = new Intent(UI.getContext(), MainActivity.class);
    secureIntent(intent, false);
    intent.setAction(Intents.ACTION_OPEN_PLAYER + "." + accountId);
    intent.putExtra("account_id", accountId);
    return intent;
  }

  public static Intent valueOfLocation (boolean resolveError) {
    Intent intent = new Intent(UI.getContext(), MainActivity.class);
    secureIntent(intent, false);
    intent.setAction(resolveError ? ACTION_LOCATION_RESOLVE : ACTION_LOCATION_VIEW);
    return intent;
  }

  public static Intent valueOfLocationReceiver (String action) {
    Intent intent = new Intent(UI.getContext(), LiveLocationReceiver.class);
    secureIntent(intent, false);
    intent.setAction(action);
    return intent;
  }

  public static Intent valueOfLocalChatId (int accountId, long localChatId, long specificMessageId) {
    Intent intent = new Intent(UI.getContext(), MainActivity.class);
    secureIntent(intent, true);
    intent.setAction(Intents.ACTION_OPEN_CHAT + "." + accountId + "." + localChatId + "." + Math.random());
    intent.putExtra("account_id", accountId);
    intent.putExtra("local_id", localChatId);
    intent.putExtra("message_id", specificMessageId);
    // intent.setFlags(32768);
    return intent;
  }

  public static Intent valueOfMain (int accountId) {
    Intent intent = new Intent(UI.getContext(), MainActivity.class);
    secureIntent(intent, true);
    intent.setAction(Intents.ACTION_OPEN_MAIN + "." + accountId);
    intent.putExtra("account_id", accountId);
    // intent.setFlags(32768);
    return intent;
  }

  public static boolean openInAppBrowser (Activity context, Uri uri, boolean ignoreSetting) {
    if (context == null || uri == null) {
      return false;
    }
    try {
      String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
      if (Config.IN_APP_BROWSER_AVAILABLE && (ignoreSetting || org.thunderdog.challegram.unsorted.Settings.instance().useInAppBrowser()) && !scheme.equals("tel")) {
        Intent share = new Intent(UI.getContext(), TGShareBroadcastReceiver.class);
        share.setAction(Intent.ACTION_SEND);

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setToolbarColor(Theme.getColor(R.id.theme_color_headerBackground));
        builder.setSecondaryToolbarColor(Theme.getColor(R.id.theme_color_headerText));
        builder.setShowTitle(true);
        builder.setActionButton(Drawables.getBitmap(R.drawable.baseline_share_24), Lang.getString(R.string.Share), PendingIntent.getBroadcast(UI.getContext(), 0, share, 0), true);
        CustomTabsIntent intent = builder.build();
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (TD.isKnownHost(uri)) {
          List<ResolveInfo> packages = getCustomTabsPackages(context);
          if (!packages.isEmpty()) {
            intent.intent.setPackage(packages.get(0).activityInfo.packageName);
          } else {
            return openExcludeCurrentImpl(intent.intent, intent.startAnimationBundle);
          }
        }
        intent.launchUrl(context, uri);
        return true;
      }
    } catch (Throwable t) {
      Log.e("Cant launch CustomTabs client", t);
    }
    return false;
  }

  /**
   * Returns a list of packages that support Custom Tabs.
   */
  @NonNull
  private static List<ResolveInfo> getCustomTabsPackages(Context context) {
    PackageManager pm = context.getPackageManager();
    // Get default VIEW intent handler.
    Intent activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));

    // Get all apps that can handle VIEW intents.
    List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
    List<ResolveInfo> packagesSupportingCustomTabs = new ArrayList<>();
    for (ResolveInfo info : resolvedActivityList) {
      Intent serviceIntent = new Intent();
      serviceIntent.setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
      serviceIntent.setPackage(info.activityInfo.packageName);
      // Check if this package also resolves the Custom Tabs service.
      if (pm.resolveService(serviceIntent, 0) != null) {
        packagesSupportingCustomTabs.add(info);
      }
    }
    return packagesSupportingCustomTabs;
  }
}
