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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.data;

import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSetWrap;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.EmojiLayout;

import java.util.ArrayList;

import me.vkryl.core.BitwiseUtils;
import tgx.td.Td;

public class TGStickerSetInfo {
  private static final int FLAG_RECENT = 1;
  private static final int FLAG_TRENDING = 1 << 2;
  private static final int FLAG_FAVORITE = 1 << 3;
  private static final int FLAG_TRENDING_EMOJI = 1 << 4;
  private static final int FLAG_DEFAULT_EMOJI = 1 << 5;
  private static final int FLAG_FAKE_CLASSIC_EMOJI = 1 << 6;
  private static final int FLAG_COLLAPSABLE_EMOJI = 1 << 7;

  private final Tdlib tdlib;
  private final @Nullable TdApi.StickerSetInfo info;
  private final ImageFile previewImage;
  private final TdApi.ClosedVectorPath[] previewOutline;
  private final int previewWidth, previewHeight;
  private final GifFile previewAnimation;
  private int flags;
  private int size;
  private int startIndex;
  private @Nullable TdApi.StickerSet stickerSet;
  private @StringRes int titleRes;
  private boolean needThemedColorFilter;
  private int fakeClassicEmojiSectionId;

  private @Nullable ArrayList<TGStickerSetInfo> boundList;
  private TdApi.Sticker[] allStickers;

  public TGStickerSetInfo (Tdlib tdlib, TdApi.Sticker[] stickers, boolean areFavorite, int trimToSize) {
    this.tdlib = tdlib;
    this.allStickers = stickers;
    if (trimToSize > 0 && stickers.length > trimToSize) {
      this.size = trimToSize;
    } else {
      this.size = stickers.length;
    }
    this.info = null;
    this.previewImage = null;
    this.previewAnimation = null;
    this.previewOutline = null;
    this.previewWidth = this.previewHeight = 0;
    if (areFavorite) {
      setIsFavorite();
    } else {
      setIsRecent();
    }
  }

  public TGStickerSetInfo (Tdlib tdlib, @NonNull TdApi.StickerSetInfo info) {
    this(tdlib, info, -1);
  }

  public TGStickerSetInfo (Tdlib tdlib, @NonNull TdApi.StickerSetInfo info, int trimToSize) {
    this.tdlib = tdlib;
    this.info = info;
    if (info.thumbnail != null) {
      this.previewOutline = info.thumbnailOutline;
      this.previewWidth = info.thumbnail.width;
      this.previewHeight = info.thumbnail.height;
      final int gifType;
      switch (info.thumbnail.format.getConstructor()) {
        case TdApi.ThumbnailFormatTgs.CONSTRUCTOR:
          gifType = GifFile.TYPE_TG_LOTTIE;
          break;
        case TdApi.ThumbnailFormatMpeg4.CONSTRUCTOR:
          gifType = GifFile.TYPE_MPEG4;
          break;
        case TdApi.ThumbnailFormatWebm.CONSTRUCTOR:
          gifType = GifFile.TYPE_WEBM;
          break;
        case TdApi.ThumbnailFormatGif.CONSTRUCTOR:
          gifType = GifFile.TYPE_GIF;
          break;
        case TdApi.ThumbnailFormatJpeg.CONSTRUCTOR:
        case TdApi.ThumbnailFormatPng.CONSTRUCTOR:
        case TdApi.ThumbnailFormatWebp.CONSTRUCTOR:
        default:
          gifType = -1;
          break;
      }
      if (gifType != -1) {
        this.previewAnimation = new GifFile(tdlib, info.thumbnail.file, gifType);
        this.previewImage = null;
      } else {
        this.previewImage = TD.toImageFile(tdlib, info.thumbnail);
        this.previewAnimation = null;
      }
    } else if (info.covers != null && info.covers.length > 0) {
      this.previewOutline = info.covers[0].outline;
      this.previewWidth = info.covers[0].width;
      this.previewHeight = info.covers[0].height;
      this.needThemedColorFilter = TD.needThemedColorFilter(info.covers[0]);
      if (Td.isAnimated(info.covers[0].format)) {
        this.previewImage = null;
        this.previewAnimation = new GifFile(tdlib, info.covers[0].sticker, info.covers[0].format);
        this.previewAnimation.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
      } else if (info.covers[0].thumbnail != null) {
        this.previewImage = TD.toImageFile(tdlib, info.covers[0].thumbnail);
        this.previewAnimation = null;
      } else {
        this.previewImage = null;
        this.previewAnimation = null;
      }
    } else {
      this.previewOutline = null;
      this.previewImage = null;
      this.previewAnimation = null;
      this.previewWidth = this.previewHeight = 0;
    }
    if (this.previewImage != null) {
      this.previewImage.setSize(EmojiLayout.getHeaderSize());
      this.previewImage.setScaleType(ImageFile.FIT_CENTER);
      this.previewImage.setWebp();
    }
    if (this.previewAnimation != null) {
      this.previewAnimation.setOptimizationMode(GifFile.OptimizationMode.STICKER_PREVIEW);
      this.previewAnimation.setScaleType(ImageFile.FIT_CENTER);
    }

    if (trimToSize > 0 && info.size > trimToSize) {
      this.size = trimToSize;
      this.flags |= FLAG_COLLAPSABLE_EMOJI;
    }
  }

  public TGStickerSetInfo (Tdlib tdlib, TdApi.StickerSet info) {
    this(tdlib, Td.toStickerSetInfo(info));
  }

  public static TGStickerSetInfo fromEmojiSection (Tdlib tdlib, int sectionId, int titleRes, int size) {
    return new TGStickerSetInfo(tdlib, sectionId, titleRes, size);
  }

  private TGStickerSetInfo (Tdlib tdlib, int sectionId, int titleRes, int size) {
    this.tdlib = tdlib;
    this.info = null;
    this.previewAnimation = null;
    this.previewImage = null;
    this.previewOutline = null;
    this.previewWidth = 0;
    this.previewHeight = 0;
    this.flags = FLAG_FAKE_CLASSIC_EMOJI;
    this.titleRes = titleRes;
    this.size = size;
    this.fakeClassicEmojiSectionId = sectionId;
  }

  public int getFakeClassicEmojiSectionId () {
    return fakeClassicEmojiSectionId;
  }

  public boolean needThemedColorFilter () {
    return needThemedColorFilter;
  }

  public void setBoundList (@Nullable ArrayList<TGStickerSetInfo> list) {
    this.boundList = list;
  }

  public boolean needSeparatorOnTop () {
    return boundList != null && !boundList.isEmpty() && boundList.get(0).getId() != getId();
  }

  public interface ViewCallback {
    void viewStickerSet (TGStickerSetInfo stickerSet);
  }

  private @Nullable ViewCallback callback;

  public void setViewCallback (@Nullable ViewCallback callback) {
    this.callback = callback;
  }

  public void view () {
    if (callback != null) {
      callback.viewStickerSet(this);
    }
  }

  public void setStickerSet (@Nullable TdApi.StickerSet stickerSet) {
    this.stickerSet = stickerSet;
  }

  public void updateState (TdApi.StickerSetInfo stickerSetInfo) {
    info.isViewed = stickerSetInfo.isViewed;
    info.isArchived = stickerSetInfo.isArchived;
    info.isInstalled = stickerSetInfo.isInstalled;
    info.covers = stickerSetInfo.covers;
    // info.size = stickerSetInfo.size;
  }

  public @Nullable TdApi.StickerSet getStickerSet () {
    return stickerSet;
  }

  public void show (ViewController<?> context) {
    StickerSetWrap wrap;
    if (stickerSet != null) {
      stickerSet.isInstalled = info.isInstalled;
      stickerSet.isArchived = info.isArchived;
      stickerSet.isViewed = info.isViewed;
      stickerSet.stickerType = info.stickerType;
      wrap = StickerSetWrap.showStickerSet(context, stickerSet);
    } else if (info != null && info.id != 0) {
      wrap = StickerSetWrap.showStickerSet(context, info);
    } else {
      wrap = null;
    }
    if (wrap != null && isTrending()) {
      wrap.setIsOneShot();
    }
  }

  public void setIsDefaultEmoji () {
    flags |= FLAG_DEFAULT_EMOJI;
  }

  public boolean isDefaultEmoji () {
    return (flags & FLAG_DEFAULT_EMOJI) != 0;
  }

  public boolean isFakeClassicEmoji () {
    return (flags & FLAG_FAKE_CLASSIC_EMOJI) != 0;
  }

  public void setIsTrendingEmoji () {
    flags |= FLAG_TRENDING_EMOJI;
  }

  public void unsetIsTrendingEmoji () {
    flags = BitwiseUtils.setFlag(flags, FLAG_TRENDING_EMOJI, false);
  }

  public boolean isTrendingEmoji () {
    return (flags & FLAG_TRENDING_EMOJI) != 0;
  }

  public void setIsTrending () {
    flags |= FLAG_TRENDING;
  }

  public boolean isTrending () {
    return (flags & FLAG_TRENDING) != 0;
  }

  public boolean isCollapsableEmojiSet () {
    return BitwiseUtils.hasFlag(flags, FLAG_COLLAPSABLE_EMOJI);
  }

  @Override
  public boolean equals (Object obj) {
    if (obj == null || !(obj instanceof TGStickerSetInfo)) {
      return false;
    }
    TGStickerSetInfo b = (TGStickerSetInfo) obj;
    return b.flags == flags && ((b.info == null && info == null) || (b.info != null && info != null && b.info.id == info.id));
  }

  public void setStartIndex (int startIndex) {
    this.startIndex = startIndex;
  }

  public int getStartIndex () {
    return startIndex;
  }

  public int getCoverCount () {
    return info != null ? info.covers.length : 0;
  }

  public int getEndIndex () {
    return startIndex + getItemCount();
  }

  public int getItemCount () {
    if (isTrending()) {
      return isEmoji() ? 16 : 5;
    }
    if (isCollapsableEmojiSet()) {
      return size + 1 + (isCollapsed() ? 1 : 0);
    }
    if (info != null) {
      return info.size + 1;
    }
    if (isFavorite() || isFakeClassicEmoji()) {
      return size;
    }
    return size + 1;
  }

  public void setStickers (TdApi.Sticker[] stickers, int visibleSize) {
    this.allStickers = stickers;
    setSize(visibleSize);
  }

  public TdApi.Sticker[] getAllStickers () {
    return allStickers;
  }

  public void setSize (int size) {
    if (info != null && !isCollapsableEmojiSet()) {
      info.size = size;
    } else {
      this.size = size;
    }
  }

  public void setIsInstalled () {
    if (info != null) {
      info.isInstalled = true;
      info.isArchived = false;
    }
  }

  public void setIsNotInstalled () {
    if (info != null) {
      info.isInstalled = false;
    }
  }

  public void setIsNotArchived () {
    if (info != null) {
      info.isArchived = false;
    }
  }

  public void setIsArchived () {
    if (info != null) {
      info.isInstalled = false;
      info.isArchived = true;
    }
  }

  public boolean isViewed () {
    return info != null && info.isViewed;
  }

  public boolean isRecent () {
    return (flags & FLAG_RECENT) != 0;
  }

  public boolean isSystem () {
    return (flags & (FLAG_RECENT | FLAG_FAVORITE)) != 0;
  }

  public boolean isFavorite () {
    return (flags & FLAG_FAVORITE) != 0;
  }

  public void setIsFavorite () {
    flags |= FLAG_FAVORITE;
  }

  public void setIsRecent () {
    flags |= FLAG_RECENT;
  }

  public boolean isInstalled () {
    return info != null && info.isInstalled && !info.isArchived;
  }

  public boolean isArchived () {
    return info != null && info.isArchived;
  }

  public boolean isMasks () {
    return info != null && info.stickerType.getConstructor() == TdApi.StickerTypeMask.CONSTRUCTOR;
  }

  public boolean isEmoji () {
    return info != null && info.stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR;
  }

  public long getId () {
    return info != null ? info.id : 0;
  }

  public TdApi.StickerSetInfo getInfo () {
    return info;
  }

  public ImageFile getPreviewImage () {
    return previewImage;
  }

  public Path getPreviewContour (int targetSize) {
    return previewWidth != 0 && previewHeight != 0 ? Td.buildOutline(previewOutline, previewWidth, previewHeight, targetSize, targetSize) : null;
  }

  public GifFile getPreviewAnimation () {
    return previewAnimation;
  }

  public boolean isPreviewAnimated () {
    return previewAnimation != null;
  }

  public int getSize () {
    if (isCollapsableEmojiSet()) {
      return size;
    }
    return info != null ? info.size : size;
  }

  public int getFullSize () {
    if (isCollapsableEmojiSet()) {
      return info != null ? info.size : getSize();
    }
    return allStickers != null ? allStickers.length : getSize();
  }

  public boolean isCollapsed () {
    return getFullSize() > getSize();
  }

  public int getTitleRes () {
    return titleRes;
  }

  public String getName () {
    return info != null ? info.name : null;
  }

  public String getTitle () {
    if (isFakeClassicEmoji()) {
      return titleRes != -1 ? Lang.getString(titleRes) : null;
    }
    return isDefaultEmoji() ? Lang.getString(R.string.TrendingStatuses) : isFavorite() ? "" : isRecent() ? Lang.getString(R.string.RecentStickers) : info != null ? info.title : null;
  }
}
