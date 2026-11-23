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
 * File created on 01/02/2018
 */
package org.thunderdog.challegram.telegram;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageCache;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.unsorted.DeviceTokenRetrieverInstance;
import org.thunderdog.challegram.util.DeviceTokenType;
import org.thunderdog.challegram.util.text.Letters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.StringUtils;
import tgx.bridge.DeviceTokenRetriever;
import tgx.bridge.TokenRetrieverListener;
import tgx.td.ChatId;
import tgx.td.Td;

public class TdlibNotificationUtils {
  private static TextPaint lettersPaint;
  private static TextPaint lettersPaintFake;
  private static Paint fillingPaint;
  private static Paint bitmapPaint;

  public static Bitmap buildLargeIcon (Tdlib tdlib, TdApi.Chat chat, boolean allowDownload) {
    if (tdlib.isSelfChat(chat)) {
      return buildSelfIcon(tdlib, chat.id);
    } else {
      return buildLargeIcon(tdlib, chat.id, chat.photo != null ? chat.photo.small : null,
        tdlib.chatAccentColor(chat), tdlib.chatLetters(chat), true, allowDownload);
    }
  }

  private static final float MAX_DENSITY = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? 1.5f : 2.5f;

  public static int iconSize () {
    return Screen.dp(52f, MAX_DENSITY);
  }

  public static String getPersonUri (Tdlib tdlib, TdApi.User user) {
    return !StringUtils.isEmpty(user.phoneNumber) ?
      "tel:+" + user.phoneNumber :
      getLocalPersonUri(tdlib, user);
  }

  public static String getLocalPersonUri (Tdlib tdlib, TdApi.User user) {
    return "tg://user?local_id=" + tdlib.settings().getLocalChatId(ChatId.fromUserId(user.id)) +
      "&account_id=" + tdlib.id();
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  public static void addCallPerson (
    Notification.Builder builder,
    Tdlib tdlib,
    TdApi.User user,
    boolean isLocked
  ) {
    // Do not disturb lets starred contacts and repeat callers ring
    String uri = isLocked ? getLocalPersonUri(tdlib, user) : getPersonUri(tdlib, user);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      android.app.Person.Builder person = new android.app.Person.Builder().setUri(uri);
      if (!isLocked) {
        person.setName(TD.getUserName(user));
      }
      builder.addPerson(person.build());
    } else {
      builder.addPerson(uri);
    }
  }

  @RequiresApi(Build.VERSION_CODES.S)
  public static android.app.Person newCallPerson (
    Tdlib tdlib,
    @Nullable TdApi.User user,
    boolean isLocked,
    @Nullable Bitmap icon
  ) {
    // Notification.CallStyle refuses a person without a name
    String name = user != null && !isLocked ? TD.getUserName(user) : null;
    android.app.Person.Builder person = new android.app.Person.Builder()
      .setName(StringUtils.isEmpty(name) ? Lang.getString(R.string.VoipBranding) : name)
      .setImportant(true);
    if (user != null) {
      person.setUri(isLocked ? getLocalPersonUri(tdlib, user) : getPersonUri(tdlib, user));
    }
    if (icon != null) {
      person.setIcon(android.graphics.drawable.Icon.createWithBitmap(icon));
    }
    return person.build();
  }

  public static Bitmap buildSelfIcon (Tdlib tdlib, long chatId) {
    final int size = iconSize();
    final int color = Theme.getColor(ColorId.avatarSavedMessages, tdlib.settings().globalTheme());
    final String key = "self_" + size + "_" + color;
    Bitmap bitmap = cachedIcon(tdlib, chatId, key);
    if (bitmap != null) {
      return bitmap;
    }
    synchronized (TdlibNotificationUtils.class) {
      if (fillingPaint == null) {
        fillingPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        fillingPaint.setStyle(Paint.Style.FILL);

        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        bitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        lettersPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        lettersPaint.setTypeface(Fonts.getRobotoMedium());
        lettersPaint.setColor(0xffffffff);

        lettersPaintFake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        lettersPaintFake.setTypeface(Fonts.getRobotoRegular());
        lettersPaintFake.setColor(0xffffffff);
      }

      lettersPaint.setTextSize(Screen.dp(20f, MAX_DENSITY));
      lettersPaintFake.setTextSize(Screen.dp(20f, MAX_DENSITY));

      try {
        Bitmap createdBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(createdBitmap);

        bitmapPaint.setColor(color);
        if (Device.ROUND_NOTIFICAITON_IMAGE) {
          fillingPaint.setColor(color);
          c.drawCircle(size / 2f, size / 2f, size / 2f, fillingPaint);
        } else {
          c.drawColor(color);
        }

        Drawable d = Drawables.get(UI.getResources(), R.drawable.baseline_bookmark_24);
        float scale = (float) size / (float) Screen.dp(44f);
        if (scale != 1f) {
          c.save();
          c.scale(scale, scale, size / 2f, size / 2f);
        }
        Drawables.draw(c, d, size / 2f - d.getMinimumWidth() / 2f, size / 2f - d.getMinimumHeight() / 2f, PorterDuffPaint.get(ColorId.avatar_content));
        if (scale != 1f) {
          c.restore();
        }

        U.recycle(c);
        bitmap = cacheIcon(tdlib, chatId, key, createdBitmap);
      } catch (Throwable t) {
        Log.e(Log.TAG_FCM, "Cannot build large icon", t);
      }
    }

    return bitmap;
  }

  public static Bitmap buildLargeIcon (Tdlib tdlib, long chatId, TdApi.File rawFile,
    TdlibAccentColor accentColor, Letters letters, boolean allowSyncDownload,
    boolean allowDownload) {
    boolean fileLoaded = false;
    if (rawFile != null) {
      tdlib.files().syncFile(rawFile, null, 500);
      fileLoaded = TD.isFileLoadedAndExists(rawFile);
      if (!fileLoaded && allowSyncDownload && allowDownload) {
        tdlib.files().downloadFileSync(rawFile, TdlibFilesManager.PRIORITY_NOTIFICATION_AVATAR, 1000, null, null, null);
        fileLoaded = TD.isFileLoadedAndExists(rawFile);
      }
      if (!fileLoaded && allowDownload) {
        if (!Config.DEBUG_DISABLE_DOWNLOAD) {
          tdlib.client().send(new TdApi.DownloadFile(rawFile.id, TdlibFilesManager.PRIORITY_NOTIFICATION_MEDIA, 0, 0, false), tdlib.silentHandler());
        }
      }
    }

    final int size = iconSize();
    final @ThemeId int themeId = tdlib.settings().globalTheme();
    final int color = Theme.toColorInt(accentColor.getPrimaryComplexColor(), themeId);
    final int lettersColor = Theme.toColorInt(accentColor.getPrimaryContentComplexColor(), themeId);
    final String key = fileLoaded ?
      "avatar_" + rawFile.id + "_" + size + "_" + color :
      "letters_" + letters.text + "_" + letters.needFakeBold + "_" + size + "_" + color + "_" +
        lettersColor;
    Bitmap bitmap = cachedIcon(tdlib, chatId, key);
    if (bitmap != null) {
      return bitmap;
    }

    Bitmap avatarBitmap = null;
    if (fileLoaded) {
      ImageFile file = new ImageFile(tdlib, rawFile);
      file.setSize(size);
      avatarBitmap = ImageCache.instance().getBitmap(file);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && avatarBitmap != null) {
        if (avatarBitmap.getConfig() == Bitmap.Config.HARDWARE) {
          avatarBitmap = null;
        }
      }
      if (avatarBitmap == null) {
        try {
          BitmapFactory.Options opts = ImageReader.getImageSize(rawFile.local.path);
          opts.inSampleSize = ImageReader.calculateInSampleSize(opts, size, size);
          opts.inJustDecodeBounds = false;
          avatarBitmap = ImageReader.decodeFile(rawFile.local.path, opts);
        } catch (Throwable t) {
          Log.e(Log.TAG_FCM, "Cannot get local file for large icon, building placeholder...", t);
        }
      }
    }

    synchronized (TdlibNotificationUtils.class) {
      if (fillingPaint == null) {
        fillingPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        fillingPaint.setStyle(Paint.Style.FILL);

        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        bitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        lettersPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        lettersPaint.setTypeface(Fonts.getRobotoMedium());
        lettersPaint.setColor(0xffffffff);

        lettersPaintFake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        lettersPaintFake.setTypeface(Fonts.getRobotoRegular());
        lettersPaintFake.setColor(0xffffffff);
      }

      lettersPaint.setTextSize(Screen.dp(20f, MAX_DENSITY));
      lettersPaintFake.setTextSize(Screen.dp(20f, MAX_DENSITY));

      try {
        Bitmap createdBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(createdBitmap);

        bitmapPaint.setColor(color);
        if (Device.ROUND_NOTIFICAITON_IMAGE) {
          fillingPaint.setColor(color);
          c.drawCircle(size / 2f, size / 2f, size / 2f, fillingPaint);
        } else {
          c.drawColor(color);
        }

        if (avatarBitmap == null) {
          final Paint paint = letters.needFakeBold ? lettersPaintFake : lettersPaint;
          paint.setColor(lettersColor);
          c.drawText(letters.text, size / 2f - U.measureText(letters.text, letters.needFakeBold ? lettersPaintFake : lettersPaint) / 2, size / 2f + Screen.dp(8f, MAX_DENSITY), paint);
        } else {
          float scale = (float) size / (float) avatarBitmap.getWidth();
          c.save();
          c.scale(scale, scale, size / 2f, size / 2f);
          c.drawBitmap(avatarBitmap, size / 2f - avatarBitmap.getWidth() / 2f, size / 2f - avatarBitmap.getHeight() / 2f, bitmapPaint);
          c.restore();
        }
        U.recycle(c);
        // An avatar that wasn't decoded isn't kept: it may decode next time
        bitmap = fileLoaded && avatarBitmap == null ? createdBitmap :
          cacheIcon(tdlib, chatId, key, createdBitmap);
      } catch (Throwable t) {
        Log.e(Log.TAG_FCM, "Cannot build large icon", t);
      }
    }

    return bitmap;
  }

  @Nullable
  private static Bitmap cachedIcon (Tdlib tdlib, long chatId, String key) {
    return Config.REUSE_NOTIFICATION_ICONS ?
      tdlib.context().notificationIcons().get(tdlib.id(), chatId, key) : null;
  }

  private static Bitmap cacheIcon (Tdlib tdlib, long chatId, String key, Bitmap createdBitmap) {
    if (!Config.REUSE_NOTIFICATION_ICONS) {
      return createdBitmap;
    }
    return tdlib.context().notificationIcons().put(tdlib.id(), chatId, key, createdBitmap);
  }

  static PendingIntent newIntent (int accountId, long forLocalChatId, long specificMessageId) {
    return PendingIntent.getActivity(UI.getContext(), 0, forLocalChatId != 0 ? Intents.valueOfLocalChatId(accountId, forLocalChatId, specificMessageId) : Intents.valueOfMain(accountId), PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(true));
  }

  static Intent newCoreIntent (int accountId, long forLocalChatId, long specificMessageId) {
    return forLocalChatId != 0 ? Intents.valueOfLocalChatId(accountId, forLocalChatId, specificMessageId) : Intents.valueOfMain(accountId);
  }

  private static final String KEY_PREFIX_SHORTCUT = "tgx_ns_";
  private static final String KEY_PREFIX_CUSTOM_SHORTCUT = "tgx_nsc_";

  private static String makeShortcutIdPrefix (int accountId, boolean isCustom) {
    return (isCustom ? KEY_PREFIX_CUSTOM_SHORTCUT : KEY_PREFIX_SHORTCUT) + accountId + "_";
  }

  private static String makeShortcutId (int accountId, long localChatId, boolean isCustom) {
    return makeShortcutIdPrefix(accountId, isCustom) + localChatId;
  }

  @Nullable
  static String findChatShortcutId (Tdlib tdlib, long chatId) {
    long localChatId = tdlib.settings().findLocalChatId(chatId);
    return localChatId != 0 ? makeShortcutId(tdlib.id(), localChatId, false) : null;
  }

  static String makeCustomShortcutId (Tdlib tdlib, long chatId) {
    return makeShortcutId(tdlib.id(), tdlib.settings().getLocalChatId(chatId), true);
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static ShortcutInfoCompat newChatShortcut (
    Context context,
    int accountId,
    String shortcutId,
    CharSequence title,
    long localChatId,
    long messageId,
    @Nullable Bitmap icon
  ) {
    IconCompat chatIcon = U.isValidBitmap(icon) ? IconCompat.createWithBitmap(icon) : null;
    Person person = new Person.Builder()
      .setName(title)
      .setIcon(chatIcon)
      .setKey(shortcutId)
      .build();
    return new ShortcutInfoCompat.Builder(context, shortcutId)
      .setPerson(person)
      .setIsConversation()
      .setLongLived(true)
      .setShortLabel(title)
      .setIntent(newCoreIntent(accountId, localChatId, messageId))
      .setIcon(chatIcon)
      .build();
  }

  private static final AtomicBoolean loggedShortcutRefusal = new AtomicBoolean();

  static boolean pushShortcut (Context context, ShortcutInfoCompat shortcut) {
    try {
      ShortcutManagerCompat.pushDynamicShortcut(context, shortcut);
      return true;
    } catch (Throwable t) {
      if (loggedShortcutRefusal.compareAndSet(false, true)) {
        Log.e("Unable to push the shortcut of a notification", t);
      }
      return false;
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private static List<android.content.pm.ShortcutInfo> findShortcuts (Context context, int flags) {
    android.content.pm.ShortcutManager manager =
      context.getSystemService(android.content.pm.ShortcutManager.class);
    try {
      return manager.getShortcuts(flags);
    } catch (IllegalStateException e) {
      return Collections.emptyList();
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private static List<android.content.pm.ShortcutInfo> findShortcuts (Context context) {
    return findShortcuts(context, android.content.pm.ShortcutManager.FLAG_MATCH_DYNAMIC |
      android.content.pm.ShortcutManager.FLAG_MATCH_CACHED);
  }

  @RequiresApi(Build.VERSION_CODES.R)
  @Nullable
  private static android.content.pm.ShortcutInfo findShortcut (Context context, String id) {
    for (android.content.pm.ShortcutInfo shortcut : findShortcuts(context)) {
      if (id.equals(shortcut.getId())) {
        return shortcut;
      }
    }
    return null;
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static boolean hasPlaceForShortcut (Context context, String shortcutId, int keptPlaces) {
    // Over the limit a push removes the oldest dynamic shortcut, an uncached one with its channels
    int dynamicCount = 0;
    for (android.content.pm.ShortcutInfo shortcut : findShortcuts(context)) {
      if (shortcut.isDynamic()) {
        if (shortcutId.equals(shortcut.getId())) {
          return true;
        }
        dynamicCount++;
      }
    }
    return dynamicCount + 1 + keptPlaces <=
      ShortcutManagerCompat.getMaxShortcutCountPerActivity(context);
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static boolean isCachedShortcut (Context context, String shortcutId) {
    android.content.pm.ShortcutInfo shortcut = findShortcut(context, shortcutId);
    return shortcut != null && shortcut.isCached();
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static boolean canPost (Context context, @Nullable String channelId, String shortcutId) {
    // The system caches the shortcut only of a post that it doesn't refuse
    if (channelId == null || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
      return false;
    }
    NotificationManager m =
      (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    android.app.NotificationChannel channel = m.getNotificationChannel(channelId, shortcutId);
    if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
      return false;
    }
    android.app.NotificationChannelGroup group =
      channel.getGroup() != null ? m.getNotificationChannelGroup(channel.getGroup()) : null;
    return group == null || !group.isBlocked();
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static void pushCustomShortcut (Tdlib tdlib, TdApi.Chat chat) {
    // The system's settings name a conversation by its shortcut, else only after a notification
    Context context = AppContext.get();
    String shortcutId = makeCustomShortcutId(tdlib, chat.id);
    if (findShortcut(context, shortcutId) != null ||
      !hasPlaceForShortcut(context, shortcutId, 1)) {
      return;
    }
    pushShortcut(context, newChatShortcut(
      context,
      tdlib.id(),
      shortcutId,
      tdlib.chatTitle(chat),
      tdlib.settings().getLocalChatId(chat.id),
      0,
      buildLargeIcon(tdlib, chat, false)
    ));
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static void removeCustomShortcut (Tdlib tdlib, long chatId) {
    // Launchers list it while it's dynamic
    long localChatId = tdlib.settings().findLocalChatId(chatId);
    if (localChatId != 0) {
      removeShortcuts(Collections.singletonList(makeShortcutId(tdlib.id(), localChatId, true)));
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static void removeAccountShortcuts (Tdlib tdlib, boolean isLogOut) {
    // Local ids of chats restart after a log out, so they would open other chats
    String prefix = makeShortcutIdPrefix(tdlib.id(), false);
    String customPrefix = makeShortcutIdPrefix(tdlib.id(), true);
    List<String> shortcutIds = new ArrayList<>();
    List<String> pinnedIds = new ArrayList<>();
    for (android.content.pm.ShortcutInfo shortcut : findShortcuts(AppContext.get(),
      android.content.pm.ShortcutManager.FLAG_MATCH_DYNAMIC |
        android.content.pm.ShortcutManager.FLAG_MATCH_CACHED |
        android.content.pm.ShortcutManager.FLAG_MATCH_PINNED)) {
      String shortcutId = shortcut.getId();
      if (!shortcutId.startsWith(prefix) && !shortcutId.startsWith(customPrefix)) {
        continue;
      }
      if (shortcut.isDynamic() || shortcut.isCached()) {
        shortcutIds.add(shortcutId);
      }
      if (shortcut.isPinned()) {
        pinnedIds.add(shortcutId);
      }
    }
    if (!shortcutIds.isEmpty()) {
      removeShortcuts(shortcutIds);
    }
    if (isLogOut && !pinnedIds.isEmpty()) {
      android.content.pm.ShortcutManager manager =
        AppContext.get().getSystemService(android.content.pm.ShortcutManager.class);
      try {
        manager.disableShortcuts(pinnedIds);
      } catch (IllegalStateException e) {
        Log.e("Unable to disable pinned shortcuts of conversations", e);
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private static void removeShortcuts (List<String> shortcutIds) {
    android.content.pm.ShortcutManager manager =
      AppContext.get().getSystemService(android.content.pm.ShortcutManager.class);
    try {
      manager.removeLongLivedShortcuts(shortcutIds);
    } catch (IllegalStateException e) {
      Log.e("Unable to remove shortcuts of conversations", e);
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  static String findShortcutId (int accountId, long localChatId, @Nullable String channelId) {
    if (TdlibNotificationChannelGroup.isCustomChannelId(channelId)) {
      String customShortcutId = makeShortcutId(accountId, localChatId, true);
      NotificationManager m =
        (NotificationManager) AppContext.get().getSystemService(Context.NOTIFICATION_SERVICE);
      android.app.NotificationChannel channel =
        m != null ? m.getNotificationChannel(channelId) : null;
      if (channel != null && customShortcutId.equals(channel.getConversationId())) {
        return customShortcutId;
      }
    }
    return makeShortcutId(accountId, localChatId, false);
  }

  public static long findShortcutChatId (Tdlib tdlib, @Nullable String shortcutId) {
    long chatId = findShortcutChatId(tdlib, shortcutId, false);
    return chatId != 0 ? chatId : findShortcutChatId(tdlib, shortcutId, true);
  }

  static long findShortcutChatId (Tdlib tdlib, @Nullable String shortcutId, boolean isCustom) {
    String prefix = makeShortcutIdPrefix(tdlib.id(), isCustom);
    if (shortcutId == null || !shortcutId.startsWith(prefix)) {
      return 0;
    }
    long localChatId = StringUtils.parseLong(shortcutId.substring(prefix.length()));
    return localChatId != 0 ? tdlib.settings().getRemoteChatId(localChatId) : 0;
  }

  public static class NotificationInitializationFailedError extends RuntimeException {
    public NotificationInitializationFailedError () {
      super("Notifications not initialized");
    }
  }

  @DeviceTokenType
  public static int getDeviceTokenType (TdApi.DeviceToken deviceToken) {
    return switch (deviceToken.getConstructor()) {
      case TdApi.DeviceTokenFirebaseCloudMessaging.CONSTRUCTOR ->
        DeviceTokenType.FIREBASE_CLOUD_MESSAGING;
      case TdApi.DeviceTokenHuaweiPush.CONSTRUCTOR ->
        DeviceTokenType.HUAWEI_PUSH_SERVICE;
      case TdApi.DeviceTokenSimplePush.CONSTRUCTOR ->
        DeviceTokenType.SIMPLE_PUSH_SERVICE;
      default -> {
        Td.assertDeviceToken_de4a4f61();
        throw Td.unsupported(deviceToken);
      }
    };
  }

  public static void getDeviceToken (Context context, int retryCount, TokenRetrieverListener listener) {
    if (retryCount > 0) {
      getDeviceTokenImpl(context, retryCount, new TokenRetrieverListener() {
        @Override
        public void onTokenRetrievalSuccess (@NonNull TdApi.DeviceToken token) {
          listener.onTokenRetrievalSuccess(token);
        }

        @Override
        public void onTokenRetrievalError (@NonNull String errorKey, @Nullable Throwable e) {
          UI.post(() ->
            getDeviceToken(context, retryCount - 1, listener),
            3500
          );
        }
      });
    } else {
      getDeviceTokenImpl(context, 0, listener);
    }
  }

  private static void getDeviceTokenImpl (Context context, int retryCount, TokenRetrieverListener listener) {
    if (DeviceTokenRetrieverInstance.initialize()) {
      DeviceTokenRetriever deviceTokenRetriever = DeviceTokenRetrieverInstance.get();
      TDLib.Tag.notifications("Retrieving device token via %s... retryCount: %d", deviceTokenRetriever.name, retryCount);
      deviceTokenRetriever.retrieveDeviceToken(context, listener);
    } else {
      TDLib.Tag.notifications("Token fetch failed because TokenRetriever was not initialized, retryCount: %d", retryCount);
      listener.onTokenRetrievalError("INITIALIZATION_ERROR", new NotificationInitializationFailedError());
    }
  }
}
