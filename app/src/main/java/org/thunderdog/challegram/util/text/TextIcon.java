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
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Path;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TextIcon implements Destroyable, TdlibEmojiManager.Watcher {
  private final Tdlib tdlib;
  private final int width, height;

  private final long customEmojiId;
  private TdlibEmojiManager.Entry customEmoji;
  private boolean customEmojiRequested;

  private Path outline;
  private ImageFile miniThumbnail, thumbnail;
  private ImageFile imageFile;
  private GifFile gifFile;

  public TextIcon (Tdlib tdlib, int size, long customEmojiId) {
    this.tdlib = tdlib;
    this.width = size;
    this.height = size;
    this.customEmojiId = customEmojiId;
    this.customEmoji = tdlib.emoji().findOrPostponeRequest(customEmojiId, this);
    if (customEmoji != null && !customEmoji.isNotFound()) {
      buildCustomEmoji(customEmoji);
    }
  }

  public TextIcon (Tdlib tdlib, TdApi.RichTextIcon icon) {
    this.tdlib = tdlib;
    this.width = Screen.dp(icon.width);
    this.height = Screen.dp(icon.height);
    this.customEmojiId = 0;

    if (icon.document.minithumbnail != null) {
      miniThumbnail = new ImageFileLocal(icon.document.minithumbnail);
      miniThumbnail.setScaleType(ImageFile.FIT_CENTER);
    }

    thumbnail = TD.toImageFile(tdlib, icon.document.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(Screen.dp(Math.max(icon.width, icon.height)));
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
    }

    if ("video/mp4".equals(icon.document.mimeType)) {
      gifFile = new GifFile(tdlib, icon.document.document, GifFile.TYPE_MPEG4);
      gifFile.setScaleType(GifFile.FIT_CENTER);
    } else if ("image/gif".equals(icon.document.mimeType)) {
      gifFile = new GifFile(tdlib, icon.document.document, GifFile.TYPE_GIF);
      gifFile.setScaleType(GifFile.FIT_CENTER);
    } else if (TdConstants.ANIMATED_STICKER_MIME_TYPE.equals(icon.document.mimeType)) {
      gifFile = new GifFile(tdlib, icon.document.document, GifFile.TYPE_TG_LOTTIE);
      gifFile.setScaleType(GifFile.FIT_CENTER);
    } else {
      imageFile = new ImageFile(tdlib, icon.document.document);
      imageFile.setSize(Screen.dp(Math.max(icon.width, icon.height)));
    }
  }

  public String getKey () {
    if (isCustomEmoji()) {
      return "emoji_" + customEmojiId + "_" + height;
    }
    StringBuilder b = new StringBuilder();
    if (imageFile != null) {
      b.append("image_").append(imageFile);
    } else if (gifFile != null) {
      b.append("gif_").append(gifFile);
    } else {
      b.append("unknown_");
    }
    b.append(width).append("x").append(height);
    return b.toString();
  }

  private void buildCustomEmoji (@NonNull TdlibEmojiManager.Entry customEmoji) {
    TdApi.Sticker sticker = customEmoji.sticker;
    if (sticker == null)
      return;

    this.outline = Td.buildOutline(sticker, width, height);

    thumbnail = TD.toImageFile(tdlib, sticker.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(Math.max(width, height));
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
    }

    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR: {
        this.gifFile = new GifFile(tdlib, sticker);
        this.gifFile.setScaleType(GifFile.FIT_CENTER);
        this.gifFile.setOptimize(true);
        this.gifFile.setSize(Math.max(width, height));
        break;
      }
      case TdApi.StickerFormatWebp.CONSTRUCTOR: {
        this.imageFile = new ImageFile(tdlib, sticker.sticker);
        this.imageFile.setSize(Math.max(width, height));
        this.imageFile.setScaleType(ImageFile.FIT_CENTER);
        break;
      }
    }
  }

  @TdlibThread
  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, long customEmojiId, TdlibEmojiManager.Entry entry) {
    this.customEmoji = entry;
    if (!entry.isNotFound()) {
      buildCustomEmoji(entry);
    }
    // TODO ui -> invalidateSingleMedia
  }

  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return height;
  }

  public boolean isImage () {
    return imageFile != null;
  }

  public boolean isAnimated () {
    return gifFile != null;
  }

  public boolean isAnimatedCustomEmoji () {
    return customEmoji != null && customEmoji.sticker != null && Td.isAnimated(customEmoji.sticker.format);
  }

  public boolean isCustomEmoji () {
    return customEmojiId != 0;
  }

  public boolean isNotFoundCustomEmoji () {
    return customEmoji != null && customEmoji.isNotFound();
  }

  public void performDestroy () {
    if (customEmojiId != 0 && customEmoji == null) {
      tdlib.emoji().forgetWatcher(customEmojiId, this);
    }
  }

  public void requestFiles (ComplexReceiver receiver, int displayMediaKey) {
    if (isCustomEmoji() && customEmoji == null && !customEmojiRequested) {
      tdlib.emoji().performPostponedRequests();
      customEmojiRequested = true;
    }
    DoubleImageReceiver preview = receiver.getPreviewReceiver(displayMediaKey);
    preview.requestFile(miniThumbnail, thumbnail);
    if (imageFile != null) {
      receiver.getImageReceiver(displayMediaKey).requestFile(imageFile);
    } else if (gifFile != null) {
      receiver.getGifReceiver(displayMediaKey).requestFile(gifFile);
    }
  }

  public void draw (Canvas c, ComplexReceiver receiver, int left, int top, int right, int bottom, float alpha, int displayMediaKey) {
    if (isCustomEmoji() && customEmoji == null) {
      if (BuildConfig.DEBUG) {
        c.drawCircle(left + (right - left) / 2f, top + (bottom - top) / 2f, height / 2f, Paints.fillingPaint(ColorUtils.alphaColor(alpha, 0xffff0000)));
      }
      return;
    }
    boolean needRestore = isAnimatedCustomEmoji();
    int restoreToCount;
    if (needRestore) {
      restoreToCount = Views.save(c);
      // animated custom emoji must be:
      // 100x100 in 120x120 for webm
      // 427x427 in 512x512 for lottie
      float scale = 120.0f / 100.0f - (Screen.dp(1f) * 2 / (float) (right - left));
      c.scale(scale, scale, left + (right - left) / 2f, top + (bottom - top) / 2f);
    } else {
      restoreToCount = -1;
    }
    Receiver content;
    if (isImage()) {
      ImageReceiver image = receiver.getImageReceiver(displayMediaKey);
      image.setBounds(left, top, right, bottom);
      image.setPaintAlpha(image.getPaintAlpha() * alpha);
      content = image;
    } else if (isAnimated()) {
      GifReceiver gif = receiver.getGifReceiver(displayMediaKey);
      gif.setBounds(left, top, right, bottom);
      gif.setAlpha(alpha);
      content = gif;
    } else {
      content = null;
    }
    DoubleImageReceiver preview = content == null || content.needPlaceholder() ? receiver.getPreviewReceiver(displayMediaKey) : null;
    if (preview != null) {
      preview.setBounds(left, top, right, bottom);
      preview.setPaintAlpha(alpha);
      if (outline != null && preview.needPlaceholder()) {
        preview.drawPlaceholderContour(c, outline, alpha);
      }
      preview.draw(c);
      preview.restorePaintAlpha();
    }
    if (content != null) {
      if (outline != null && content.needPlaceholder()) {
        content.drawPlaceholderContour(c, outline, alpha);
      }
      content.draw(c);
      if (isImage()) {
        content.restorePaintAlpha();
      } else {
        // ((GifReceiver) content).setAlpha(1f);
      }
    }
    if (needRestore) {
      Views.restore(c, restoreToCount);
    }
  }
}
