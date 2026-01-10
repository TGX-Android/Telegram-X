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
 * File created for forum topics icon display
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.tool.Screen;

/**
 * DrawModifier that renders a topic icon on the left side of a SettingView.
 * Supports both colored circles (for topics without custom emoji) and
 * custom emoji icons (stickers) for topics with iconCustomEmojiId.
 */
public class TopicIconModifier implements DrawModifier, TdlibEmojiManager.Watcher {

  private static final float ICON_SIZE_DP = 20f;
  private static final float LEFT_OFFSET_DP = 18f; // Left padding area, before text

  private final Tdlib tdlib;
  private final int iconColor;
  private final long customEmojiId;

  private final Paint circlePaint;

  // Custom emoji support
  private @Nullable TdlibEmojiManager.Entry customEmoji;
  private @Nullable ImageFile imageFile;
  private @Nullable GifFile gifFile;
  private @Nullable ImageFile thumbnail;
  private @Nullable ComplexReceiver iconReceiver;
  private @Nullable View attachedView;

  public TopicIconModifier (Tdlib tdlib, TdApi.ForumTopicIcon icon) {
    this.tdlib = tdlib;
    this.iconColor = icon != null ? icon.color : 0x6FB9F0; // Default blue
    this.customEmojiId = icon != null ? icon.customEmojiId : 0;

    this.circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    this.circlePaint.setStyle(Paint.Style.FILL);
    this.circlePaint.setColor(iconColor | 0xFF000000); // Ensure full alpha

    if (customEmojiId != 0) {
      loadCustomEmoji();
    }
  }

  private void loadCustomEmoji () {
    this.customEmoji = tdlib.emoji().findOrPostponeRequest(customEmojiId, this);
    if (customEmoji != null && !customEmoji.isNotFound()) {
      buildCustomEmojiFiles(customEmoji);
    } else {
      tdlib.emoji().performPostponedRequests();
    }
  }

  private void buildCustomEmojiFiles (TdlibEmojiManager.Entry entry) {
    TdApi.Sticker sticker = entry.value;
    if (sticker == null) return;

    int size = Screen.dp(ICON_SIZE_DP);

    // Thumbnail
    thumbnail = TD.toImageFile(tdlib, sticker.thumbnail);
    if (thumbnail != null) {
      thumbnail.setSize(size);
      thumbnail.setScaleType(ImageFile.FIT_CENTER);
      thumbnail.setNoBlur();
    }

    // Main image/animation based on format
    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR: {
        this.gifFile = new GifFile(tdlib, sticker);
        this.gifFile.setScaleType(GifFile.FIT_CENTER);
        this.gifFile.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
        this.gifFile.setRequestedSize(size);
        break;
      }
      case TdApi.StickerFormatWebp.CONSTRUCTOR: {
        this.imageFile = new ImageFile(tdlib, sticker.sticker);
        this.imageFile.setSize(size);
        this.imageFile.setScaleType(ImageFile.FIT_CENTER);
        this.imageFile.setNoBlur();
        break;
      }
    }
  }

  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    this.customEmoji = entry;
    if (!entry.isNotFound()) {
      buildCustomEmojiFiles(entry);
    }
    // Request files and invalidate view on UI thread
    if (tdlib != null) {
      tdlib.ui().post(() -> {
        requestIconFiles();
        if (attachedView != null) {
          attachedView.invalidate();
        }
      });
    }
  }

  private void requestIconFiles () {
    if (iconReceiver == null) return;

    DoubleImageReceiver preview = iconReceiver.getPreviewReceiver(0);
    preview.requestFile(null, thumbnail);
    if (imageFile != null) {
      iconReceiver.getImageReceiver(0).requestFile(imageFile);
    } else if (gifFile != null) {
      iconReceiver.getGifReceiver(0).requestFile(gifFile);
    }
  }

  /**
   * Attach the modifier to a view to enable custom emoji rendering.
   * Must be called when the view is bound.
   */
  public void attachToView (View view) {
    this.attachedView = view;
    if (customEmojiId != 0 && iconReceiver == null) {
      this.iconReceiver = new ComplexReceiver(view);
      requestIconFiles();
    }
  }

  /**
   * Detach from the view and clean up receivers.
   */
  public void detach () {
    this.attachedView = null;
    if (iconReceiver != null) {
      iconReceiver.clear();
      iconReceiver = null;
    }
  }

  @Override
  public void beforeDraw (View view, Canvas c) {
    // Attach view if not already attached
    if (attachedView != view) {
      attachToView(view);
    }

    int iconSize = Screen.dp(ICON_SIZE_DP);
    int leftOffset = Screen.dp(LEFT_OFFSET_DP);
    int centerY = view.getHeight() / 2;

    // Try to draw custom emoji first
    if (customEmojiId != 0 && iconReceiver != null) {
      int left = leftOffset;
      int top = centerY - iconSize / 2;
      int right = left + iconSize;
      int bottom = top + iconSize;

      // Draw from image receiver (static or animated)
      ImageReceiver imageRcv = iconReceiver.getImageReceiver(0);
      GifReceiver gifRcv = iconReceiver.getGifReceiver(0);

      if (gifFile != null && gifRcv != null) {
        gifRcv.setBounds(left, top, right, bottom);
        if (gifRcv.needPlaceholder()) {
          // Draw thumbnail as placeholder
          DoubleImageReceiver preview = iconReceiver.getPreviewReceiver(0);
          if (preview != null) {
            preview.setBounds(left, top, right, bottom);
            preview.draw(c);
          }
        }
        gifRcv.draw(c);
        return;
      } else if (imageFile != null && imageRcv != null) {
        imageRcv.setBounds(left, top, right, bottom);
        if (!imageRcv.needPlaceholder()) {
          imageRcv.draw(c);
          return;
        }
        // Draw thumbnail as placeholder
        DoubleImageReceiver preview = iconReceiver.getPreviewReceiver(0);
        if (preview != null) {
          preview.setBounds(left, top, right, bottom);
          preview.draw(c);
        }
        if (!imageRcv.needPlaceholder()) {
          imageRcv.draw(c);
          return;
        }
      }
    }

    // Fallback: draw colored circle
    float cx = leftOffset + iconSize / 2f;
    float cy = centerY;
    float radius = iconSize / 2f;
    c.drawCircle(cx, cy, radius, circlePaint);
  }

  @Override
  public int getWidth () {
    // Reserve space for the icon (not needed for left-side icons actually,
    // but keep minimal to avoid affecting text layout)
    return 0;
  }
}
