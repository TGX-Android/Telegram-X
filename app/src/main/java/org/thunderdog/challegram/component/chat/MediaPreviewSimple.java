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
package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.FileComponent;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageFileRemote;
import org.thunderdog.challegram.loader.ImageVideoThumbFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;

import java.io.File;

import me.vkryl.core.ColorUtils;
import me.vkryl.td.Td;

public class MediaPreviewSimple extends MediaPreview {
  private TdApi.Sticker sticker;
  private Path outline;
  private int outlineWidth, outlineHeight;
  private boolean hasSpoiler;

  private ImageFile previewImage;
  private GifFile previewGif;

  private ImageFile targetImage;
  private GifFile targetGif;

  private boolean drawColoredFileBackground;

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.ProfilePhoto profilePhoto) {
    super(size, cornerRadius);
    if (profilePhoto.minithumbnail != null) {
      this.previewImage = new ImageFileLocal(profilePhoto.minithumbnail);
      this.previewImage.setSize(size);
      this.previewImage.setScaleType(ImageFile.CENTER_CROP);
      this.previewImage.setDecodeSquare(true);
    }
    if (profilePhoto.small != null) {
      this.targetImage = new ImageFile(tdlib, profilePhoto.small, null);
      this.targetImage.setSize(size);
      this.targetImage.setScaleType(ImageFile.CENTER_CROP);
      this.targetImage.setDecodeSquare(true);
      this.targetImage.setNoBlur();
    }
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.ChatPhoto chatPhoto) {
    super(size, cornerRadius);
    if (chatPhoto.minithumbnail != null) {
      this.previewImage = new ImageFileLocal(chatPhoto.minithumbnail);
      this.previewImage.setSize(size);
      this.previewImage.setScaleType(ImageFile.CENTER_CROP);
      this.previewImage.setDecodeSquare(true);
    }
    if (chatPhoto.sizes.length > 0) {
      this.targetImage = new ImageFile(tdlib, chatPhoto.sizes[0].photo);
      this.targetImage.setSize(size);
      this.targetImage.setScaleType(ImageFile.CENTER_CROP);
      this.targetImage.setDecodeSquare(true);
    }
    // TODO handle animation?
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.ChatPhotoInfo chatPhotoInfo) {
    super(size, cornerRadius);
    if (chatPhotoInfo.minithumbnail != null) {
      this.previewImage = new ImageFileLocal(chatPhotoInfo.minithumbnail);
      this.previewImage.setSize(size);
      this.previewImage.setScaleType(ImageFile.CENTER_CROP);
      this.previewImage.setDecodeSquare(true);
    }
    if (chatPhotoInfo.small != null) {
      this.targetImage = new ImageFile(tdlib, chatPhotoInfo.small, null);
      this.targetImage.setSize(size);
      this.targetImage.setScaleType(ImageFile.CENTER_CROP);
      this.targetImage.setDecodeSquare(true);
      this.targetImage.setNoBlur();
    }
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Venue venue, TdApi.Thumbnail thumbnail) {
    this(tdlib, size, cornerRadius, venue.location, thumbnail);
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Location location, TdApi.Thumbnail thumbnail) {
    super(size, cornerRadius);
    if (thumbnail != null) {
      this.previewImage = TD.toImageFile(tdlib, thumbnail);
      if (this.previewImage != null) {
        this.previewImage.setSize(size);
        this.previewImage.setScaleType(ImageFile.CENTER_CROP);
        this.previewImage.setDecodeSquare(true);
        this.previewImage.setNoBlur();
      }
      this.previewGif = TD.toGifFile(tdlib, thumbnail);
      if (this.previewGif != null) {
        this.previewGif.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
        this.previewGif.setRequestedSize(size);
        this.previewGif.setScaleType(GifFile.CENTER_CROP);
        this.previewGif.setPlayOnce();
      }
    }

    switch (Settings.instance().getMapProviderType(true)) {
      case Settings.MAP_PROVIDER_GOOGLE: {
        this.targetImage = new ImageFileRemote(tdlib, U.getMapPreview(location.latitude, location.longitude, 16, false, size, size), new TdApi.FileTypeThumbnail());
        break;
      }
      case Settings.MAP_PROVIDER_TELEGRAM:
      default: {
        int scale = Screen.density() >= 2.0f ? 2 : 1;
        this.targetImage = new ImageFileRemote(tdlib, new TdApi.GetMapThumbnailFile(location, 16, size / scale, size / scale, scale, 0), "telegram_map_" + location.latitude + "," + location.longitude + "_" + size);
        break;
      }
    }
    this.targetImage.setSize(size);
    this.targetImage.setScaleType(ImageFile.CENTER_CROP);
    this.targetImage.setDecodeSquare(true);
    this.targetImage.setNoBlur();

    // TODO
    /*if (receiver.needPlaceholder()) {
      if (previewReceiver.needPlaceholder()) {
        previewReceiver.drawPlaceholder(c);
        if (needPinIcon) {
          Drawable drawable = view.getSparseDrawable(R.drawable.baseline_location_on_24, 0);
          Drawables.draw(c, drawable, receiver.centerX() - drawable.getMinimumWidth() / 2, receiver.centerY() - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(0xffffffff));
        }
      }
      previewReceiver.draw(c);
    }
    receiver.draw(c);*/
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Thumbnail thumbnail, TdApi.Minithumbnail minithumbnail) {
    this(tdlib, size, cornerRadius, thumbnail, minithumbnail, false);
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Thumbnail thumbnail, TdApi.Minithumbnail minithumbnail, boolean hasSpoiler) {
    super(size, cornerRadius);
    this.hasSpoiler = hasSpoiler;
    if (minithumbnail != null) {
      this.previewImage = new ImageFileLocal(minithumbnail);
      this.previewImage.setSize(size);
      this.previewImage.setScaleType(ImageFile.CENTER_CROP);
      this.previewImage.setDecodeSquare(true);
      if (hasSpoiler) {
        this.previewImage.setIsPrivate();
      }
    }
    if (thumbnail != null) {
      this.targetImage = TD.toImageFile(tdlib, thumbnail);
      if (this.targetImage != null) {
        this.targetImage.setSize(size);
        this.targetImage.setScaleType(ImageFile.CENTER_CROP);
        this.targetImage.setDecodeSquare(true);
        this.targetImage.setNoBlur();
        if (hasSpoiler) {
          this.targetImage.setIsPrivate();
        }
      }
      if (!hasSpoiler) {
        this.targetGif = TD.toGifFile(tdlib, thumbnail);
        if (this.targetGif != null) {
          this.targetGif.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
          this.targetGif.setRequestedSize(size);
          this.targetGif.setScaleType(GifFile.CENTER_CROP);
          this.targetGif.setPlayOnce();
        }
      }
    }
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, File file, String mimeType) {
    super(size, cornerRadius);
    if (TGMimeType.isImageMimeType(mimeType)) {
      this.targetImage = FileComponent.createFullPreview(new ImageFileLocal(file.getPath()), mimeType);
      this.targetImage.setProbablyRotated();
      this.targetImage.setDecodeSquare(true);
    } else if (TGMimeType.isVideoMimeType(mimeType)) {
      this.targetImage = FileComponent.createFullPreview(new ImageVideoThumbFile(tdlib, TD.newFile(file)), mimeType);
      this.targetImage.setDecodeSquare(true);
    } else {
      throw new UnsupportedOperationException();
    }
  }

  public MediaPreviewSimple (Tdlib tdlib, int size, int cornerRadius, TdApi.Sticker sticker) {
    super(size, cornerRadius);
    this.sticker = sticker;
    this.outline = Td.buildOutline(sticker, outlineWidth = size, outlineHeight = size);
    if (sticker.thumbnail != null) {
      this.previewImage = TD.toImageFile(tdlib, sticker.thumbnail);
      if (this.previewImage != null) {
        this.previewImage.setSize(size);
        this.previewImage.setScaleType(ImageFile.FIT_CENTER);
      }
      this.previewGif = TD.toGifFile(tdlib, sticker.thumbnail);
      if (this.previewGif != null) {
        this.previewGif.setRequestedSize(size);
        this.previewGif.setScaleType(GifFile.FIT_CENTER);
      }
    }
    if (Td.isAnimated(sticker.format)) {
      this.targetGif = new GifFile(tdlib, sticker);
      this.targetGif.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
      this.targetGif.setRequestedSize(size);
      this.targetGif.setScaleType(GifFile.FIT_CENTER);
      this.targetGif.setPlayOnce();
    } else {
      this.targetImage = new ImageFile(tdlib, sticker.sticker);
      this.targetImage.setWebp();
      this.targetImage.setNoBlur();
      this.targetImage.setSize(size);
      this.targetImage.setScaleType(GifFile.FIT_CENTER);
    }
  }

  public MediaPreviewSimple (int size, int cornerRadius, ImageFile remoteFile) {
    super(size, cornerRadius);
    this.drawColoredFileBackground = true;
    this.targetImage = remoteFile;
    this.targetImage.setSize(size);
    this.targetImage.setScaleType(ImageFile.CENTER_CROP);
    this.targetImage.setDecodeSquare(true);
    this.targetImage.setNoBlur();
  }

  @Override
  public void requestFiles (ComplexReceiver receiver, boolean invalidate) {
    GifReceiver gifPreview = receiver.getGifReceiver(0);
    gifPreview.requestFile(previewGif);

    GifReceiver gifReceiver = receiver.getGifReceiver(1);
    gifReceiver.requestFile(targetGif);

    DoubleImageReceiver imageReceiver = receiver.getPreviewReceiver(0);
    imageReceiver.requestFile(previewImage, targetImage);
  }

  private Receiver getTargetReceiver (ComplexReceiver receiver) {
    return targetGif != null ? receiver.getGifReceiver(1) : receiver.getPreviewReceiver(0).getReceiver();
  }

  private Receiver getPreviewReceiver (ComplexReceiver receiver) {
    return previewGif != null ? receiver.getGifReceiver(0) : receiver.getPreviewReceiver(0).getPreview();
  }

  @Override
  public boolean needPlaceholder (ComplexReceiver receiver) {
    Receiver target = getTargetReceiver(receiver);
    if (target.needPlaceholder()) {
      Receiver preview = getPreviewReceiver(receiver);
      return preview.needPlaceholder();
    }
    return false;
  }

  @Override
  public <T extends View & DrawableProvider> void draw (T view, Canvas c, ComplexReceiver receiver, float x, float y, float width, float height, int cornerRadius, float alpha) {
    Receiver preview = getPreviewReceiver(receiver);
    Receiver target = getTargetReceiver(receiver);

    preview.setRadius(cornerRadius);
    target.setRadius(cornerRadius);

    preview.setBounds((int) x, (int) y, (int) (x + width), (int) (y + height));
    target.setBounds((int) x, (int) y, (int) (x + width), (int) (y + height));

    if (alpha != 1f) {
      preview.setPaintAlpha(alpha);
      target.setPaintAlpha(alpha);
    }

    if (outline != null && (outlineWidth != preview.getWidth() || outlineHeight != preview.getHeight())) {
      outline = Td.buildOutline(sticker, outlineWidth = preview.getWidth(), outlineHeight = preview.getHeight());
    }

    if (target.needPlaceholder()) {
      if (preview.needPlaceholder()) {
        if (outline != null) {
          preview.drawPlaceholderContour(c, outline, alpha);
        } else {
          preview.drawPlaceholder(c);
        }
      }
      preview.draw(c);
    }

    if (drawColoredFileBackground) {
      target.drawPlaceholderRounded(c, cornerRadius, Theme.getColor(ColorId.file));
    }

    target.draw(c);

    if (drawColoredFileBackground) {
      target.drawPlaceholderRounded(c, cornerRadius, ColorUtils.alphaColor(target.getAlpha() * alpha, 0x44000000));
    }

    if (Config.DEBUG_STICKER_OUTLINES) {
      preview.drawPlaceholderContour(c, outline);
    }

    if (hasSpoiler) {
      DrawAlgorithms.drawRoundRect(c, cornerRadius, target.getLeft(), target.getTop(), target.getRight(), target.getBottom(), Paints.fillingPaint(Theme.getColor(ColorId.spoilerMediaOverlay)));
      DrawAlgorithms.drawParticles(c, cornerRadius, target.getLeft(), target.getTop(), target.getRight(), target.getBottom(), 1f);
    }

    if (alpha != 1f) {
      preview.restorePaintAlpha();
      target.restorePaintAlpha();
    }
  }
}
