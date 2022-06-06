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
 * File created on 22/11/2016
 */
package org.thunderdog.challegram.component.sticker;

import android.graphics.Path;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.td.Td;

public class TGStickerObj {
  private Tdlib tdlib;
  private @Nullable TdApi.Sticker sticker;
  private TdApi.StickerType stickerType;
  private ImageFile preview;
  private ImageFile fullImage;
  private GifFile previewAnimation, fullAnimation;
  private String foundByEmoji;

  private int flags;

  private static final int FLAG_RECENT = 1 << 1;
  private static final int FLAG_TRENDING = 1 << 2;
  private static final int FLAG_FAVORITE = 1 << 3;
  private static final int FLAG_NO_VIEW_PACK = 1 << 4;

  public TGStickerObj (Tdlib tdlib, @Nullable TdApi.Sticker sticker, @Nullable String foundByEmoji, TdApi.StickerType stickerType) {
    set(tdlib, sticker, stickerType, null);
    this.foundByEmoji = foundByEmoji;
    if (preview != null) {
      preview.setNeedCancellation(true);
    }
  }

  public TGStickerObj (Tdlib tdlib, @Nullable TdApi.Sticker sticker, TdApi.StickerType stickerType, String[] emojis) {
    set(tdlib, sticker, stickerType, emojis);
  }

  public boolean set (Tdlib tdlib, @Nullable TdApi.Sticker sticker, TdApi.StickerType stickerType, String[] emojis) {
    if (this.sticker == null && sticker == null) {
      return false;
    }
    setEmojiImpl(emojis);
    if (this.sticker == null || sticker == null || this.tdlib != tdlib || this.sticker.sticker.id != sticker.sticker.id || !Td.equalsTo(this.sticker.type, sticker.type)) {
      this.tdlib = tdlib;
      this.sticker = sticker;
      this.fullImage = null;
      this.previewAnimation = null;
      this.fullAnimation = null;
      this.stickerType = stickerType;
      if (sticker != null && (sticker.thumbnail != null || !Td.isAnimated(sticker.type))) {
        this.preview = TD.toImageFile(tdlib, sticker.thumbnail);
        if (this.preview != null) {
          this.preview.setSize(Screen.dp(82f));
          this.preview.setWebp();
          this.preview.setScaleType(ImageFile.FIT_CENTER);
        }
      } else {
        this.preview = null;
      }
      return true;
    }
    return false;
  }

  public long getStickerSetId () {
    return stickerSetId != 0 ? stickerSetId : sticker != null ? sticker.setId : 0;
  }

  public boolean needViewPackButton () {
    // (isFavorite() || isRecent())
    return getStickerSetId() != 0 && (flags & FLAG_NO_VIEW_PACK) == 0;
  }

  public void setNoViewPack () {
    flags |= FLAG_NO_VIEW_PACK;
  }

  @Override
  public boolean equals (Object obj) {
    if (!(obj instanceof TGStickerObj)) {
      return false;
    }

    TGStickerObj b = (TGStickerObj) obj;
    return (b.sticker == null && sticker == null && b.flags == flags) ||
           (b.sticker != null && sticker != null && b.flags == flags &&
             b.sticker.setId == sticker.setId &&
             b.sticker.sticker.id == sticker.sticker.id &&
             Td.equalsTo(b.sticker.type, sticker.type)
           );
  }

  public String getAllEmoji () {
    return emojis != null && emojis.length > 0 ? TextUtils.join(" ", emojis) : sticker != null ? sticker.emoji : "";
  }

  public @Nullable TdApi.Sticker getSticker () {
    return sticker;
  }

  public @Nullable String getFoundByEmoji () {
    return foundByEmoji;
  }

  public Path getContour (int targetSize) {
    return sticker != null ? Td.buildOutline(sticker.outline, targetSize == -1 ? 1f : Math.min((float) targetSize / (float) sticker.width, (float) targetSize / (float) sticker.height)) : null;
  }

  public ImageFile getImage () {
    return preview;
  }

  public boolean isFullLoaded () {
    return sticker != null && TD.isFileLoaded(sticker.sticker);
  }

  public boolean isAnimated () {
    return sticker != null && Td.isAnimated(sticker.type);
  }

  public ImageFile getFullImage () {
    if (fullImage == null && sticker != null && !Td.isAnimated(sticker.type) && tdlib != null) {
      this.fullImage = new ImageFile(tdlib, sticker.sticker);
      this.fullImage.setScaleType(ImageFile.FIT_CENTER);
      this.fullImage.setSize(Screen.dp(190f));
      this.fullImage.setWebp();
    }
    return fullImage;
  }

  public GifFile getPreviewAnimation () {
    if (previewAnimation == null && sticker != null && Td.isAnimated(sticker.type) && tdlib != null) {
      this.previewAnimation = new GifFile(tdlib, sticker);
      this.previewAnimation.setPlayOnce();
      this.previewAnimation.setScaleType(ImageFile.FIT_CENTER);
      this.previewAnimation.setOptimize(true);
    }
    return previewAnimation;
  }

  public GifFile getFullAnimation () {
    if (fullAnimation == null && sticker != null && Td.isAnimated(sticker.type) && tdlib != null) {
      this.fullAnimation = new GifFile(tdlib, sticker);
      this.fullAnimation.setScaleType(ImageFile.FIT_CENTER);
      this.fullAnimation.setUnique(true);
    }
    return fullAnimation;
  }

  public void setIsRecent () {
    flags |= FLAG_RECENT;
  }

  public void setIsFavorite () {
    flags |= FLAG_FAVORITE;
  }

  public boolean isFavorite () {
    return (flags & FLAG_FAVORITE) != 0;
  }

  public void setIsTrending () {
    flags |= FLAG_TRENDING;
  }

  public boolean isTrending () {
    return (flags & FLAG_TRENDING) != 0;
  }

  public boolean isRecent () {
    return (flags & FLAG_RECENT) != 0;
  }

  public boolean isMasks () {
    return stickerType.getConstructor() == TdApi.StickerTypeMask.CONSTRUCTOR;
  }

  public int getId () {
    return sticker != null ? sticker.sticker.id : 0;
  }

  public int getWidth () {
    return sticker != null ? sticker.width : 0;
  }

  public int getHeight () {
    return sticker != null ? sticker.height : 0;
  }

  // If sticker set is not loaded yet

  public boolean isEmpty () {
    return sticker == null;
  }

  private long stickerSetId;
  private @Nullable DataProvider provider;
  private String[] emojis;

  private void setEmojiImpl (String[] emojis) {
    if (emojis != null && emojis.length > 5) {
      this.emojis = new String[5];
      System.arraycopy(emojis, 0, this.emojis, 0, 5);
    } else {
      this.emojis = emojis;
    }
  }

  public void setStickerSetId (long stickerSetId, String[] emojis) {
    this.stickerSetId = stickerSetId;
    setEmojiImpl(emojis);
  }

  public interface DataProvider {
    void requestStickerData (TGStickerObj sticker, long stickerSetId);
  }

  public void setDataProvider (DataProvider provider) {
    this.provider = provider;
  }

  public void requestRequiredInformation () {
    if (provider != null && isEmpty()) {
      provider.requestStickerData(this, stickerSetId);
    }
  }
}
