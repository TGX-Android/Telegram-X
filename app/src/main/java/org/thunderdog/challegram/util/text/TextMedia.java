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
 */
package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
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

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TextMedia implements Destroyable, TdlibEmojiManager.Watcher {
  private final Text source;

  final List<TextPart> attachedToParts = new ArrayList<>();
  private int displayMediaKeyOffset = -1;

  private final Tdlib tdlib;
  public final String keyId;
  public final int id;
  private final int width, height;
  private boolean isDestroyed;

  private final long customEmojiId;
  private TdlibEmojiManager.Entry customEmoji;
  private boolean customEmojiRequested;

  private Path outline;
  private ImageFile miniThumbnail, thumbnail;
  private ImageFile imageFile;
  private GifFile gifFile;

  public TextMedia (Text source, Tdlib tdlib, String keyId, int id, int size, long customEmojiId) {
    if (tdlib == null)
      throw new IllegalArgumentException();
    this.source = source;
    this.tdlib = tdlib;
    this.keyId = keyId;
    this.id = id;
    this.width = size;
    this.height = size;
    this.customEmojiId = customEmojiId;
    this.customEmoji = tdlib.emoji().findOrPostponeRequest(customEmojiId, this);
    if (customEmoji != null && !customEmoji.isNotFound()) {
      buildCustomEmoji(customEmoji);
    }
  }

  public TextMedia (Text source, Tdlib tdlib, String keyId, int id, TdApi.RichTextIcon icon) {
    if (tdlib == null)
      throw new IllegalArgumentException();
    this.source = source;
    this.tdlib = tdlib;
    this.keyId = keyId;
    this.id = id;
    this.width = Screen.dp(icon.width);
    this.height = Screen.dp(icon.height);
    this.customEmojiId = 0;

    if (icon.document.minithumbnail != null) {
      miniThumbnail = new ImageFileLocal(icon.document.minithumbnail);
      miniThumbnail.setScaleType(ImageFile.FIT_CENTER);
      miniThumbnail.setNoBlur();
    }

    thumbnail = TD.toImageFile(tdlib, icon.document.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(Screen.dp(Math.max(icon.width, icon.height)));
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
      thumbnail.setNoBlur();
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

  public static String keyForIcon (Tdlib tdlib, TdApi.RichTextIcon icon)  {
    return "icon_" + tdlib.id() + "_" + icon.width + "x" + icon.height + "_" + icon.document.document.remote.uniqueId;
  }

  public static String keyForEmoji (long customEmojiId, int size) {
    return "emoji_" + customEmojiId + "_" + size;
  }

  private boolean isEmojiStatus;

  public void setIsEmojiStatus (@Nullable String sharedUsageId) {
    isEmojiStatus = true;
    if (gifFile != null) {
      gifFile.setRepeatCount(2);
      gifFile.setPlayOnceId(sharedUsageId);
    }
  }

  private void buildCustomEmoji (@NonNull TdlibEmojiManager.Entry customEmoji) {
    TdApi.Sticker sticker = customEmoji.value;
    if (sticker == null)
      return;

    this.outline = Td.buildOutline(sticker, width, height);

    thumbnail = TD.toImageFile(tdlib, sticker.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(Math.max(width, height));
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
      thumbnail.setNoBlur();
    }

    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR: {
        this.gifFile = new GifFile(tdlib, sticker);
        this.gifFile.setScaleType(GifFile.FIT_CENTER);
        this.gifFile.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
        this.gifFile.setRequestedSize(Math.max(width, height));
        if (isEmojiStatus) {
          this.gifFile.setRepeatCount(2);
        }
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

  public void rebuild () {
    if (customEmoji != null && !customEmoji.isNotFound()) {
      buildCustomEmoji(customEmoji);
    }
    tdlib.ui().post(() -> {
      if (!isDestroyed) {
        source.notifyMediaChanged(this);
      }
    });
  }

  @TdlibThread
  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    this.customEmoji = entry;
    if (!entry.isNotFound()) {
      buildCustomEmoji(entry);
    }
    tdlib.ui().post(() -> {
      if (!isDestroyed) {
        source.notifyMediaChanged(this);
      }
    });
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
    return customEmoji != null && customEmoji.value != null && Td.isAnimated(customEmoji.value.format);
  }

  public static float getScale (TdApi.Sticker sticker, int size) {
    // animated custom emoji must be:
    // 100x100 in 120x120 for webm
    // 427x427 in 512x512 for lottie
    // upd: turns out all of the webm emoji fit entire space
    if (Td.isAnimated(sticker.format) &&
        sticker.format.getConstructor() != TdApi.StickerFormatWebm.CONSTRUCTOR) {
      return 120.0f / 100.0f - (size != 0 ? Screen.dp(1f) * 2 / (float) size : 0);
    }
    return 1f;
  }

  public boolean needsRepainting () {
    return isCustomEmoji() && customEmoji != null && TD.needRepainting(customEmoji.value);
  }

  public boolean isCustomEmoji () {
    return customEmojiId != 0;
  }

  public boolean isNotFoundCustomEmoji () {
    return customEmoji != null && customEmoji.isNotFound();
  }

  public void performDestroy () {
    isDestroyed = true;
    if (customEmojiId != 0 && customEmoji == null) {
      tdlib.emoji().forgetWatcher(customEmojiId, this);
    }
  }

  void setDisplayMediaKeyOffset (int keyOffset) {
    this.displayMediaKeyOffset = keyOffset;
  }

  int getDisplayMediaKey () {
    if (displayMediaKeyOffset != -1) {
      return displayMediaKeyOffset + id;
    }
    return -1;
  }

  public void requestFiles (ComplexReceiver receiver) {
    int displayMediaKey = getDisplayMediaKey();
    if (displayMediaKey == -1)
      throw new IllegalStateException();
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

    final boolean needRepainting = needsRepainting();
    if (needRepainting) {
      c.saveLayerAlpha(left - width / 4f, top - height / 4f, right + width / 4f, bottom + height / 4f, 255, Canvas.ALL_SAVE_FLAG);
    }

    //noinspection ConstantConditions
    float scale = customEmoji != null && !customEmoji.isNotFound() ? getScale(customEmoji.value, (right - left)) : 1f;
    boolean needScaleUp = scale != 1f;
    int restoreToCount;
    if (needScaleUp) {
      restoreToCount = Views.save(c);
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
    if (needScaleUp) {
      Views.restore(c, restoreToCount);
    }
    if (needRepainting) {
      c.drawRect(left - width / 4f, top - height / 4f, right + width / 4f, bottom + height / 4f, Paints.getSrcInPaint(source.getEmojiStatusColor()));
      c.restore();
    }
  }
}
