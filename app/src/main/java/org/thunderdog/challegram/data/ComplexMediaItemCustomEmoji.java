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
 * File created on 05/09/2022, 22:23.
 */

package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.td.Td;

public class ComplexMediaItemCustomEmoji implements ComplexMediaItem {
  public final TdApi.Sticker sticker;
  public final int size;
  public final Path outline;
  public final ImageFile miniThumbnail, thumbnail;
  public final ImageFile imageFile;
  public final GifFile gifFile;

  public ComplexMediaItemCustomEmoji (Tdlib tdlib, TdApi.Sticker sticker, int size) {
    this.sticker = sticker;
    this.size = size;

    this.outline = Td.buildOutline(sticker, size);

    this.miniThumbnail = null; // keeping in case it will appear in sticker object

    thumbnail = TD.toImageFile(tdlib, sticker.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(size);
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
      thumbnail.setNoBlur();
    }

    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR: {
        this.gifFile = new GifFile(tdlib, sticker);
        this.gifFile.setScaleType(GifFile.FIT_CENTER);
        this.gifFile.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
        this.gifFile.setRequestedSize(size);

        this.imageFile = null;
        break;
      }
      case TdApi.StickerFormatWebp.CONSTRUCTOR: {
        this.imageFile = new ImageFile(tdlib, sticker.sticker);
        this.imageFile.setSize(size);
        this.imageFile.setScaleType(ImageFile.FIT_CENTER);

        this.gifFile = null;
        break;
      }
      default:
        throw new UnsupportedOperationException(sticker.format.toString());
    }
  }

  @Override
  public boolean requiresTopLayer () {
    return sticker.format.getConstructor() == TdApi.StickerFormatTgs.CONSTRUCTOR;
  }

  public boolean isAnimated () {
    return gifFile != null;
  }

  public boolean isStatic () {
    return imageFile != null;
  }

  @Override
  public void requestComplexMedia (ComplexReceiver receiver, long displayMediaKey) {
    DoubleImageReceiver preview = receiver.getPreviewReceiver(displayMediaKey);
    preview.requestFile(miniThumbnail, thumbnail);
    if (imageFile != null) {
      receiver.getImageReceiver(displayMediaKey).requestFile(imageFile);
    } else if (gifFile != null) {
      receiver.getGifReceiver(displayMediaKey).requestFile(gifFile);
    }
  }

  @Override
  public String getComplexMediaKey () {
    return "emoji_" + Td.customEmojiId(sticker) + "_" + size;
  }

  @Override
  public void draw (Canvas c, Rect rect, ComplexReceiver mediaReceiver, long displayMediaKey, boolean translate) {
    if (translate && (rect.left == 0 && rect.top == 0)) {
      translate = false;
    }

    Receiver receiver;
    if (imageFile != null) {
      receiver = mediaReceiver.getImageReceiver(displayMediaKey);
    } else if (gifFile != null) {
      receiver = mediaReceiver.getGifReceiver(displayMediaKey);
    } else {
      throw new UnsupportedOperationException();
    }
    int restoreToCount;
    if (translate) {
      restoreToCount = Views.save(c);
      c.translate(rect.left, rect.top);
    } else {
      restoreToCount = -1;
    }
    if (translate) {
      receiver.setBounds(0, 0, rect.right - rect.left, rect.bottom - rect.top);
    } else {
      receiver.setBounds(rect.left, rect.top, rect.right, rect.bottom);
    }
    if (receiver.needPlaceholder()) {
      DoubleImageReceiver preview = mediaReceiver.getPreviewReceiver(displayMediaKey);
      if (translate) {
        preview.setBounds(0, 0, rect.right - rect.left, rect.bottom - rect.top);
      } else {
        preview.setBounds(rect.left, rect.top, rect.right, rect.bottom);
      }
      if (preview.needPlaceholder()) {
        preview.drawPlaceholderContour(c, outline);
      }
    }
    receiver.draw(c);
    if (translate) {
      Views.restore(c, restoreToCount);
    }
  }
}
