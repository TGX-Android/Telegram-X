package org.thunderdog.challegram.data;

import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSetWrap;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.EmojiLayout;

import java.util.ArrayList;

import me.vkryl.td.Td;

/**
 * Date: 21/11/2016
 * Author: default
 */

public class TGStickerSetInfo {
  private static final int FLAG_RECENT = 0x01;
  private static final int FLAG_TRENDING = 0x04;
  private static final int FLAG_FAVORITE = 0x08;

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

  private @Nullable ArrayList<TGStickerSetInfo> boundList;

  public TGStickerSetInfo (Tdlib tdlib, TdApi.Sticker[] recentStickers) {
    this.tdlib = tdlib;
    this.size = recentStickers.length;
    this.info = null;
    this.previewImage = null;
    this.previewAnimation = null;
    this.previewOutline = null;
    this.previewWidth = this.previewHeight = 0;
  }

  public TGStickerSetInfo (Tdlib tdlib, @NonNull TdApi.StickerSetInfo info) {
    this.tdlib = tdlib;
    this.info = info;
    if (info.thumbnail != null) {
      this.previewOutline = info.thumbnailOutline;
      this.previewWidth = info.thumbnail.width;
      this.previewHeight = info.thumbnail.height;
      if (info.isAnimated && info.thumbnail.format.getConstructor() == TdApi.ThumbnailFormatTgs.CONSTRUCTOR) {
        this.previewAnimation = new GifFile(tdlib, info.thumbnail.file, GifFile.TYPE_TG_LOTTIE);
        this.previewImage = null;
      } else {
        this.previewImage = TD.toImageFile(tdlib, info.thumbnail);
        this.previewAnimation = null;
      }
    } else if (info.covers != null && info.covers.length > 0) {
      this.previewOutline = info.covers[0].outline;
      this.previewWidth = info.covers[0].width;
      this.previewHeight = info.covers[0].height;
      if (info.covers[0].isAnimated) {
        this.previewImage = null;
        this.previewAnimation = new GifFile(tdlib, info.covers[0].sticker, GifFile.TYPE_TG_LOTTIE);
        this.previewAnimation.setOptimize(true);
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
      this.previewAnimation.setOptimize(true);
      this.previewAnimation.setScaleType(ImageFile.FIT_CENTER);
    }
  }

  public TGStickerSetInfo (Tdlib tdlib, TdApi.StickerSet info) {
    this(tdlib, new TdApi.StickerSetInfo(info.id, info.title, info.name, info.thumbnail, info.thumbnailOutline, info.isInstalled, info.isArchived, info.isOfficial, info.isAnimated, info.isMasks, info.isViewed, info.stickers.length, info.stickers));
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

  public void show (ViewController context) {
    StickerSetWrap wrap;
    if (stickerSet != null) {
      stickerSet.isInstalled = info.isInstalled;
      stickerSet.isArchived = info.isArchived;
      stickerSet.isViewed = info.isViewed;
      stickerSet.isMasks = info.isMasks;
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

  public void setIsTrending () {
    flags |= FLAG_TRENDING;
  }

  public boolean isTrending () {
    return (flags & FLAG_TRENDING) != 0;
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
      return 5;
    }
    if (info != null) {
      return info.size + 1;
    }
    if (isFavorite() || (Config.HEADLESS_RECENT_PACK && isRecent())) {
      return size;
    }
    return size + 1;
  }

  public void setSize (int size) {
    if (info != null) {
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
    return info != null && info.isMasks;
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
    return previewWidth != 0 && previewHeight != 0 ? Td.buildOutline(previewOutline, Math.min((float) targetSize / (float) previewWidth, (float) targetSize / (float) previewHeight)) : null;
  }

  public GifFile getPreviewAnimation () {
    return previewAnimation;
  }

  public boolean isAnimated () {
    return info != null && info.isAnimated;
  }

  public int getSize () {
    return info != null ? info.size : size;
  }

  public String getName () {
    return info != null ? info.name : null;
  }

  public String getTitle () {
    return isFavorite() ? "" : isRecent() ? Lang.getString(R.string.RecentStickers) : info != null ? info.title : null;
  }
}
