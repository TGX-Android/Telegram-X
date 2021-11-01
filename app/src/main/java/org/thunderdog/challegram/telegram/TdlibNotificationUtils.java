package org.thunderdog.challegram.telegram;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageCache;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Letters;

/**
 * Date: 2/1/18
 * Author: default
 */

public class TdlibNotificationUtils {
  private static TextPaint lettersPaint;
  private static TextPaint lettersPaintFake;
  private static Paint fillingPaint;
  private static Paint bitmapPaint;

  public static Bitmap buildLargeIcon (Tdlib tdlib, TdApi.Chat chat, boolean allowDownload) {
    if (tdlib.isSelfChat(chat)) {
      return buildSelfIcon(tdlib);
    } else {
      return buildLargeIcon(tdlib, chat.photo != null ? chat.photo.small : null, tdlib.chatAvatarColorId(chat), tdlib.chatLetters(chat), true, allowDownload);
    }
  }

  private static final float MAX_DENSITY = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? 1.5f : 2.5f;

  public static int iconSize () {
    return Screen.dp(52f, MAX_DENSITY);
  }

  public static Bitmap buildSelfIcon (Tdlib tdlib) {
    Bitmap bitmap = null;
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
        int size = iconSize();
        Bitmap createdBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(createdBitmap);

        final int color = Theme.getColor(R.id.theme_color_avatarSavedMessages, tdlib.settings().globalTheme());

        bitmapPaint.setColor(color);
        if (Device.ROUND_NOTIFICAITON_IMAGE) {
          fillingPaint.setColor(color);
          c.drawCircle(size / 2, size / 2, size / 2, fillingPaint);
        } else {
          c.drawColor(color);
        }

        Drawable d = Drawables.get(UI.getResources(), R.drawable.baseline_bookmark_24);
        float scale = (float) size / (float) Screen.dp(44f);
        if (scale != 1f) {
          c.save();
          c.scale(scale, scale, size / 2, size / 2);
        }
        Drawables.draw(c, d, size / 2 - d.getMinimumWidth() / 2, size / 2 - d.getMinimumHeight() / 2, PorterDuffPaint.get(R.id.theme_color_avatar_content));
        if (scale != 1f) {
          c.restore();
        }

        bitmap = createdBitmap;
        U.recycle(c);
      } catch (Throwable t) {
        Log.e(Log.TAG_FCM, "Cannot build large icon", t);
      }
    }

    return bitmap;
  }

  public static Bitmap buildLargeIcon (Tdlib tdlib, TdApi.File rawFile, @ThemeColorId int colorId, Letters letters, boolean allowSyncDownload, boolean allowDownload) {
    Bitmap avatarBitmap = null;
    if (rawFile != null) {
      tdlib.files().syncFile(rawFile, null, 500);
      boolean fileLoaded = TD.isFileLoadedAndExists(rawFile);
      if (!fileLoaded && allowSyncDownload && allowDownload) {
        tdlib.files().downloadFileSync(rawFile, 1000, null, null);
        fileLoaded = TD.isFileLoadedAndExists(rawFile);
      }
      if (fileLoaded) {
        ImageFile file = new ImageFile(tdlib, rawFile);
        file.setSize(iconSize());
        avatarBitmap = ImageCache.instance().getBitmap(file);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && avatarBitmap != null) {
          if (avatarBitmap.getConfig() == Bitmap.Config.HARDWARE) {
            avatarBitmap = null;
          }
        }
        if (avatarBitmap == null) {
          try {
            avatarBitmap = ImageReader.decodeFile(rawFile.local.path, null);
          } catch (Throwable t) {
            Log.e(Log.TAG_FCM, "Cannot get local file for large icon, building placeholder...", t);
          }
        }
      } else if (allowDownload) {
        if (!Config.DEBUG_DISABLE_DOWNLOAD) {
          tdlib.client().send(new TdApi.DownloadFile(rawFile.id, 1, 0, 0, false), tdlib.silentHandler());
        }
      }
    }

    Bitmap bitmap = null;
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
        int size = iconSize();
        Bitmap createdBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(createdBitmap);

        final int color = Theme.getColor(colorId, tdlib.settings().globalTheme());

        bitmapPaint.setColor(color);
        if (Device.ROUND_NOTIFICAITON_IMAGE) {
          fillingPaint.setColor(color);
          c.drawCircle(size / 2, size / 2, size / 2, fillingPaint);
        } else {
          c.drawColor(color);
        }

        if (avatarBitmap == null) {
          c.drawText(letters.text, size / 2 - U.measureText(letters.text, letters.needFakeBold ? lettersPaintFake : lettersPaint) / 2, size / 2 + Screen.dp(8f, MAX_DENSITY), letters.needFakeBold ? lettersPaintFake : lettersPaint);
        } else {
          float scale = (float) size / (float) avatarBitmap.getWidth();
          c.save();
          c.scale(scale, scale, size / 2, size / 2);
          c.drawBitmap(avatarBitmap, size / 2 - avatarBitmap.getWidth() / 2, size / 2 - avatarBitmap.getHeight() / 2, bitmapPaint);
          c.restore();
        }
        bitmap = createdBitmap;
        U.recycle(c);
      } catch (Throwable t) {
        Log.e(Log.TAG_FCM, "Cannot build large icon", t);
      }
    }

    return bitmap;
  }

  static PendingIntent newIntent (int accountId, long forLocalChatId, long specificMessageId) {
    return PendingIntent.getActivity(UI.getContext(), 0, forLocalChatId != 0 ? Intents.valueOfLocalChatId(accountId, forLocalChatId, specificMessageId) : Intents.valueOfMain(accountId), PendingIntent.FLAG_ONE_SHOT);
  }

  static Intent newCoreIntent (int accountId, long forLocalChatId, long specificMessageId) {
    return forLocalChatId != 0 ? Intents.valueOfLocalChatId(accountId, forLocalChatId, specificMessageId) : Intents.valueOfMain(accountId);
  }
}
