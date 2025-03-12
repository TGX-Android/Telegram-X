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
 * File created on 04/05/2019
 */
package org.thunderdog.challegram.emoji;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.tool.EmojiCode;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.InputStream;
import java.util.Locale;

import me.vkryl.core.lambda.Destroyable;

class EmojiBitmaps {
  public static class Entry implements Destroyable {
    public Bitmap bitmap;
    public int inSampleSize;

    private boolean isLoading;

    public Entry () { }

    public void setBitmap (Bitmap bitmap, int inSampleSize) {
      this.bitmap = bitmap;
      this.inSampleSize = inSampleSize;
    }

    public boolean isLoaded () {
      return U.isValidBitmap(bitmap);
    }

    public boolean markAsLoading () {
      synchronized (this) {
        if (!isLoading) {
          isLoading = true;
          return true;
        }
      }
      return false;
    }

    @Override
    public void performDestroy () {
      U.recycle(bitmap);
      setBitmap(null, 0);
      isLoading = false;
    }

    public boolean draw (@NonNull Canvas c, EmojiInfo info, Rect outRect, Paint paint) {
      if (isLoaded()) {
        c.drawBitmap(bitmap, info.getRect(inSampleSize), outRect, paint);
        return true;
      }
      return false;
    }
  }

  public final String identifier;
  private final Entry[][] bitmaps = new Entry[5][EmojiCode.SPLIT_COUNT];
  private boolean recycled;

  public final float scaleDp;

  public EmojiBitmaps (String identifier) {
    this.identifier = identifier;
    switch (identifier) {
      case "twitter": scaleDp = 2f; break;
      case "openmoji": scaleDp = -2f; break;
      default: scaleDp = 0f; break;
    }
  }

  private static Bitmap loadAsset (String filePath, boolean isAsset, int sampleSize) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Config.MODERN_IMAGE_DECODER_ENABLED) {
      try {
        android.graphics.ImageDecoder.Source source;
        if (isAsset) {
          source = android.graphics.ImageDecoder.createSource(UI.getAppContext().getAssets(), filePath);
        } else {
          source = android.graphics.ImageDecoder.createSource(new File(filePath));
        }
        return android.graphics.ImageDecoder.decodeBitmap(source, new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
          @Override
          public void onHeaderDecoded (@NonNull android.graphics.ImageDecoder decoder, @NonNull android.graphics.ImageDecoder.ImageInfo info, @NonNull android.graphics.ImageDecoder.Source source) {
            if (sampleSize != 1)
              decoder.setTargetSampleSize(sampleSize);
            if (Config.FORCE_SOFTWARE_IMAGE_DECODER) {
              decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
            }
          }
        });
      } catch (Throwable t) {
        Log.e("Cannot load emoji bitmap (Pie)", t);
      }
    } else {
      try (InputStream is = isAsset ? UI.getAppContext().getAssets().open(filePath) : U.openInputStream(filePath)) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sampleSize;
        return BitmapFactory.decodeStream(is, null, opts);
      } catch (Throwable t) {
        Log.e("Cannot load emoji bitmap", t);
      }
    }
    return null;
  }

  static int calculateSampleSize () {
    return Screen.density() <= 1.0f ? 2 : 1;
  }

  @Nullable
  public Entry getBitmap (int section, int page) {
    return isReady(section, page) ? bitmaps[section][page] : null;
  }

  private boolean isReady (int page1, int page2) {
    if (recycled)
      return false;
    Entry bitmap = bitmaps[page1][page2];
    if (bitmap == null) {
      bitmap = new Entry();
      bitmaps[page1][page2] = bitmap;
    }
    if (!bitmap.isLoaded()) {
      if (bitmap.markAsLoading()) {
        Media.instance().post(() -> loadEmoji(page1, page2));
      }
      return false;
    }
    return true;
  }

  public void recycle () {
    if (!recycled) {
      recycled = true;
      for (Entry[] bitmaps : this.bitmaps) {
        for (int i = 0; i < bitmaps.length; i++) {
          Entry bitmap = bitmaps[i];
          if (bitmap != null) {
            bitmap.performDestroy();
            bitmaps[i] = null;
          }
        }
      }
    }
  }

  private void loadEmoji (int section, int page) {
    String fileSuffix = String.format(Locale.US, "%d_%d.png", section, page);

    int sampleSize = calculateSampleSize();
    Bitmap result = null;
    int attemptNo = 0;
    do {
      if (!BuildConfig.EMOJI_BUILTIN_ID.equals(identifier)) {
        File file = new File(new File(Emoji.getEmojiPackDirectory(), identifier), fileSuffix);
        result = loadAsset(file.getPath(), false, sampleSize);
      }
      if (result == null) {
        result = loadAsset(String.format(Locale.US, "emoji/v%d_%s", (12 + BuildConfig.EMOJI_VERSION), fileSuffix), true, sampleSize);
      }
      if (U.isValidBitmap(result)) {
        break;
      }
      attemptNo++;
      sampleSize++;
    } while (attemptNo < 3);
    final Bitmap resultFinal = result;
    final int sampleSizeFinal = sampleSize;
    UI.post(() -> {
      if (recycled) {
        if (resultFinal != null)
          resultFinal.recycle();
      } else {
        bitmaps[section][page].setBitmap(resultFinal, sampleSizeFinal);
      }
      TGLegacyManager.instance().notifyEmojiChanged(false);
    });
  }
}
