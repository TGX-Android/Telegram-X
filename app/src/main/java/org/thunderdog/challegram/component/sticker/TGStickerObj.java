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
 * File created on 22/11/2016
 */
package org.thunderdog.challegram.component.sticker;

import android.graphics.Path;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
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
  private GifFile previewAnimation, fullAnimation, premiumFullAnimation;
  private String foundByEmoji;
  private TdApi.ReactionType reactionType;
  private boolean needThemedColorFilter;
  private boolean isDefaultPremiumStar;

  private int flags;
  private float displayScale = 1f;

  private static final int FLAG_RECENT = 1 << 1;
  private static final int FLAG_TRENDING = 1 << 2;
  private static final int FLAG_FAVORITE = 1 << 3;
  private static final int FLAG_NO_VIEW_PACK = 1 << 4;

  public TGStickerObj (Tdlib tdlib, @Nullable TdApi.Sticker sticker, @Nullable String foundByEmoji, TdApi.StickerFullType stickerType) {
    this(tdlib, sticker, foundByEmoji, Td.toType(stickerType));
  }

  public TGStickerObj (Tdlib tdlib, @Nullable TdApi.Sticker sticker, @Nullable String foundByEmoji, TdApi.StickerType stickerType) {
    set(tdlib, sticker, stickerType, null);
    this.foundByEmoji = foundByEmoji;
    if (preview != null) {
      preview.setNeedCancellation(true);
    }
  }

  public static TGStickerObj makeDefaultPremiumStar (Tdlib tdlib) {
    TGStickerObj o = new TGStickerObj(tdlib, null, null, new TdApi.StickerTypeCustomEmoji());
    o.isDefaultPremiumStar = true;
    return o;
  }


  public TGStickerObj setDisplayScale (float scale) {
    this.displayScale = scale;
    return this;
  }

  public float getDisplayScale () {
    return displayScale;
  }

  public boolean isCustomReaction () {
    return reactionType != null && reactionType.getConstructor() == TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR;
  }

  public boolean isEmojiReaction () {
    return reactionType != null && reactionType.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR;
  }

  public boolean isCustomEmoji () {
    return stickerType != null && stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR;
  }

  public boolean needGenericAnimation () {
    return isCustomReaction();
  }

  public TGStickerObj (Tdlib tdlib, @Nullable TdApi.Sticker sticker, TdApi.StickerType stickerType, String[] emojis) {
    set(tdlib, sticker, stickerType, emojis);
  }

  public TGStickerObj (Tdlib tdlib, @Nullable TdApi.Sticker sticker, TdApi.StickerFullType stickerFullType, String[] emojis) {
    set(tdlib, sticker, stickerFullType, emojis);
  }

  public TGStickerObj setReactionType (TdApi.ReactionType reactionType) {
    this.reactionType = reactionType;
    return this;
  }

  public TdApi.ReactionType getReactionType () {
    if (reactionType != null) {
      return reactionType;
    } else if (isCustomEmoji()) {
      return new TdApi.ReactionTypeCustomEmoji(getCustomEmojiId());
    }
    return null;
  }

  public boolean set (Tdlib tdlib, @Nullable TdApi.Sticker sticker, TdApi.StickerFullType stickerType, String[] emojis) {
    return set(tdlib, sticker, Td.toType(stickerType), emojis);
  }

  public boolean set (Tdlib tdlib, @Nullable TdApi.Sticker sticker, TdApi.StickerType stickerType, String[] emojis) {
    if (this.sticker == null && sticker == null) {
      return false;
    }
    setEmojiImpl(emojis);
    if (this.sticker == null || sticker == null || this.tdlib != tdlib || !Td.equalsTo(this.sticker, sticker)) {
      this.tdlib = tdlib;
      this.sticker = sticker;
      this.needThemedColorFilter = TD.needThemedColorFilter(sticker);
      this.fullImage = null;
      this.previewAnimation = null;
      this.fullAnimation = null;
      this.premiumFullAnimation = null;
      this.stickerType = stickerType;
      if (sticker != null && (sticker.thumbnail != null || !Td.isAnimated(sticker.format))) {
        this.preview = TD.toImageFile(tdlib, sticker.thumbnail);
        if (this.preview != null) {
          this.preview.setSize(Screen.dp(isCustomEmoji() ? 40f : 82f));   // In some cases, emoji are drawn at more than 40 dp;
          this.preview.setWebp();                                         // perhaps, in order not to lose quality when scaling,
          this.preview.setScaleType(ImageFile.FIT_CENTER);                //  it is worth adding an arbitrary preview size
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

  private long tag;

  public void setTag (long tag) {
    this.tag = tag;
  }

  public long getTag () {
    return tag;
  }

  public boolean isPremium () {
    return Td.isPremium(sticker);
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
           (b.sticker != null && sticker != null && b.flags == flags && Td.equalsTo(b.sticker, sticker));
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
    return getContour(targetSize, targetSize);
  }

  public Path getContour (int targetWidth, int targetHeight) {
    return sticker != null ? Td.buildOutline(sticker, targetWidth, targetHeight) : null;
  }

  public ImageFile getImage () {
    return preview;
  }

  public boolean isFullLoaded () {
    return sticker != null && TD.isFileLoaded(sticker.sticker);
  }

  public boolean isAnimated () {
    return sticker != null && Td.isAnimated(sticker.format);
  }

  public ImageFile getFullImage () {
    if (fullImage == null && sticker != null && !Td.isAnimated(sticker.format) && tdlib != null) {
      this.fullImage = new ImageFile(tdlib, sticker.sticker);
      this.fullImage.setScaleType(ImageFile.FIT_CENTER);
      this.fullImage.setSize(Screen.dp(190f));
      this.fullImage.setWebp();
    }
    return fullImage;
  }

  @GifFile.OptimizationMode
  private int previewOptimizationMode = GifFile.OptimizationMode.STICKER_PREVIEW;

  public TGStickerObj setPreviewOptimizationMode (@GifFile.OptimizationMode int mode) {
    this.previewOptimizationMode = mode;
    return this;
  }

  public GifFile getPreviewAnimation () {
    if (previewAnimation == null && sticker != null && Td.isAnimated(sticker.format) && tdlib != null) {
      this.previewAnimation = new GifFile(tdlib, sticker);
      this.previewAnimation.setPlayOnce();
      this.previewAnimation.setScaleType(ImageFile.FIT_CENTER);
      this.previewAnimation.setOptimizationMode(previewOptimizationMode);
    }
    return previewAnimation;
  }

  public GifFile getFullAnimation () {
    if (fullAnimation == null && sticker != null && Td.isAnimated(sticker.format) && tdlib != null) {
      this.fullAnimation = new GifFile(tdlib, sticker);
      this.fullAnimation.setScaleType(ImageFile.FIT_CENTER);
      this.fullAnimation.setUnique(true);
    }
    return fullAnimation;
  }

  public GifFile getPremiumFullAnimation () {
    if (premiumFullAnimation == null && sticker != null && Td.isAnimated(sticker.format) && tdlib != null && Td.isPremium(sticker)) {
      this.premiumFullAnimation = new GifFile(tdlib, ((TdApi.StickerFullTypeRegular) sticker.fullType).premiumAnimation, sticker.format);
      this.premiumFullAnimation.setScaleType(ImageFile.FIT_CENTER);
      this.premiumFullAnimation.setUnique(true);
    }
    return premiumFullAnimation;
  }

  public boolean needThemedColorFilter () {
    return needThemedColorFilter || isDefaultPremiumStar();
  }

  public boolean isDefaultPremiumStar () {
    return isDefaultPremiumStar;
  }

  public long getCustomEmojiId () {
    return Td.customEmojiId(sticker);
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
    return !isDefaultPremiumStar && stickerType.getConstructor() == TdApi.StickerTypeMask.CONSTRUCTOR;
  }

  public int getId () {
    return sticker != null ? sticker.sticker.id : 0;
  }

  public int getWidth () {
    return isDefaultPremiumStar ? 512 : sticker != null ? sticker.width : 0;
  }

  public int getHeight () {
    return isDefaultPremiumStar ? 512 : sticker != null ? sticker.height : 0;
  }

  // If sticker set is not loaded yet

  public boolean isEmpty () {
    return sticker == null && !isDefaultPremiumStar;
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
