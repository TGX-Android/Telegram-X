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
 * File created on 02/09/2023
 */
package org.thunderdog.challegram.util;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.collection.LongSparseIntArray;

public abstract class StickerSetsDataProvider implements TGStickerObj.DataProvider {
  public static final int FLAG_TRENDING = 0x01;
  public static final int FLAG_REGULAR = 0x02;

  private final LongSparseIntArray loadingStickerSets = new LongSparseIntArray();
  private final Tdlib tdlib;

  public StickerSetsDataProvider (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  @Override
  public void requestStickerData (TGStickerObj sticker, long stickerSetId) {
    if (needIgnoreRequests(stickerSetId, sticker)) {
      return;
    }

    final int currentFlags = loadingStickerSets.get(stickerSetId, 0);
    final int loadingFlags = getLoadingFlags(stickerSetId, sticker);
    final boolean needRequestData = ((currentFlags ^ loadingFlags) & loadingFlags) != 0;

    loadingStickerSets.put(stickerSetId, currentFlags | loadingFlags);

    if (needRequestData) {
      tdlib.client().send(new TdApi.GetStickerSet(stickerSetId), singleStickerSetHandler());
    }
  }

  public void clear () {
    loadingStickerSets.clear();
  }

  private Client.ResultHandler singleStickerSetHandler () {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.StickerSet.CONSTRUCTOR: {
          final TdApi.StickerSet stickerSet = (TdApi.StickerSet) object;
          UI.post(() -> {
            final int flags = loadingStickerSets.get(stickerSet.id);
            loadingStickerSets.delete(stickerSet.id);
            applyStickerSet(stickerSet, flags);
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
      }
    };
  }

  protected abstract boolean needIgnoreRequests (long stickerSetId, TGStickerObj stickerObj);
  protected abstract int getLoadingFlags (long stickerSetId, TGStickerObj stickerObj);
  protected abstract void applyStickerSet (TdApi.StickerSet stickerSet, int flags);
}
