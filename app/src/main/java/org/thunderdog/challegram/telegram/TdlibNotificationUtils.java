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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
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
import org.thunderdog.challegram.unsorted.DeviceTokenRetrieverInstance;
import org.thunderdog.challegram.util.DeviceTokenType;
import org.thunderdog.challegram.util.text.Letters;

import tgx.bridge.DeviceTokenRetriever;
import tgx.bridge.TokenRetrieverListener;
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
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
    return newIntent(accountId, forLocalChatId, specificMessageId, null);
  }

  static PendingIntent newIntent (
    int accountId,
    long forLocalChatId,
    long specificMessageId,
    @Nullable TdApi.MessageTopic topic
  ) {
    return PendingIntent.getActivity(
      UI.getContext(),
      0,
      newCoreIntent(accountId, forLocalChatId, specificMessageId, topic),
      PendingIntent.FLAG_ONE_SHOT | Intents.mutabilityFlags(true)
    );
    // endregion
  }

  static Intent newCoreIntent (int accountId, long forLocalChatId, long specificMessageId) {
    // region @MadeByLLM("gpt-6-astra", id = "forum-topics", ref = "PR #1095")
    return newCoreIntent(accountId, forLocalChatId, specificMessageId, null);
  }

  static Intent newCoreIntent (
    int accountId,
    long forLocalChatId,
    long specificMessageId,
    @Nullable TdApi.MessageTopic topic
  ) {
    Intent intent = forLocalChatId != 0 ? Intents.valueOfLocalChatId(
      accountId,
      forLocalChatId,
      specificMessageId
    ) : Intents.valueOfMain(accountId);
    Td.put(intent, "topic_id", topic);
    return intent;
    // endregion
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
