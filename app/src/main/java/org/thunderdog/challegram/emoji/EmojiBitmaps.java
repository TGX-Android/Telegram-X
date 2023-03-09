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
import android.os.Build;

import androidx.annotation.NonNull;

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

class EmojiBitmaps {
  public final String identifier;
  public final Bitmap[][] bitmaps = new Bitmap[5][EmojiCode.SPLIT_COUNT];
  private final boolean[][] loadingEmoji = new boolean[5][EmojiCode.SPLIT_COUNT];
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

  private static Bitmap loadAsset (String filePath, boolean isAsset) {
    final int sampleSize = calculateSampleSize();
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

  public Bitmap getBitmap (int page1, int page2) {
    return isReady(page1, page2) ? bitmaps[page1][page2] : null;
  }

  private boolean isReady (int page1, int page2) {
    if (recycled)
      return false;
    Bitmap bitmap = bitmaps[page1][page2];
    if (!U.isValidBitmap(bitmap)) {
      if (!loadingEmoji[page1][page2]) {
        loadingEmoji[page1][page2] = true;
        Media.instance().post(() -> loadEmoji(page1, page2));
      }
      return false;
    }
    return true;
  }

  public void recycle () {
    if (!recycled) {
      recycled = true;
      for (Bitmap[] bitmaps : this.bitmaps) {
        int index = 0;
        for (Bitmap bitmap : bitmaps) {
          U.recycle(bitmap);
          bitmaps[index] = null;
          index++;
        }
      }
    }
  }

  private void loadEmoji (int page1, int page2) {
    String fileSuffix = String.format(Locale.US, "%d_%d.png", page1, page2);

    Bitmap result = null;
    if (!BuildConfig.EMOJI_BUILTIN_ID.equals(identifier)) {
      File file = new File(new File(Emoji.getEmojiPackDirectory(), identifier), fileSuffix);
      result = loadAsset(file.getPath(), false);
    }
    if (result == null) {
      result = loadAsset(String.format(Locale.US, "emoji/v%d_%s", (12 + BuildConfig.EMOJI_VERSION), fileSuffix), true);
    }
    Bitmap resultFinal = result;
    UI.post(() -> {
      if (recycled) {
        if (resultFinal != null)
          resultFinal.recycle();
      } else {
        bitmaps[page1][page2] = resultFinal;
      }
      TGLegacyManager.instance().notifyEmojiChanged(false);
    });
  }
}
