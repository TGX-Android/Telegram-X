/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 15/02/2018
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface StickersListener {
  default void onInstalledStickerSetsUpdated (long[] stickerSetIds, TdApi.StickerType stickerType) { }
  default void onRecentStickersUpdated (int[] stickerIds, boolean isAttached) { }
  default void onFavoriteStickersUpdated (int[] stickerIds) { }
  default void onTrendingStickersUpdated (TdApi.StickerType stickerType, TdApi.TrendingStickerSets stickerSets, int unreadCount) { }
  default void onStickerSetUpdated (TdApi.StickerSet stickerSet) { }
  default void onStickerSetArchived (TdApi.StickerSetInfo stickerSet) { }
  default void onStickerSetRemoved (TdApi.StickerSetInfo stickerSet) { }
  default void onStickerSetInstalled (TdApi.StickerSetInfo stickerSet) { }
}
