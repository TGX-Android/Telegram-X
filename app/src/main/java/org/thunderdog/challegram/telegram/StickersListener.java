package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/15/18
 * Author: default
 */
public interface StickersListener {
  default void onInstalledStickerSetsUpdated (long[] stickerSetIds, boolean isMasks) { }
  default void onRecentStickersUpdated (int[] stickerIds, boolean isAttached) { }
  default void onFavoriteStickersUpdated (int[] stickerIds) { }
  default void onTrendingStickersUpdated (TdApi.StickerSets stickerSets, int unreadCount) { }
  default void onStickerSetUpdated (TdApi.StickerSet stickerSet) { }
  default void onStickerSetArchived (TdApi.StickerSetInfo stickerSet) { }
  default void onStickerSetRemoved (TdApi.StickerSetInfo stickerSet) { }
  default void onStickerSetInstalled (TdApi.StickerSetInfo stickerSet) { }
}
