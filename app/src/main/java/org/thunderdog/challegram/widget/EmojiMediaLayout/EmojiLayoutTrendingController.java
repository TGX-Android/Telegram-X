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
 * File created on 26/08/2023
 */
package org.thunderdog.challegram.widget.EmojiMediaLayout;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.collection.LongSparseArray;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.EmojiMediaListController;

import java.util.ArrayList;

import me.vkryl.core.lambda.CancellableRunnable;

public class EmojiLayoutTrendingController extends EmojiLayoutRecyclerController implements TGStickerSetInfo.ViewCallback {

  public EmojiLayoutTrendingController (Context context, Tdlib tdlib, @IdRes int controllerId) {
    super(context, tdlib, controllerId);
  }

  public void setCallbacks (TGStickerObj.DataProvider dataProvider, TdApi.StickerType stickerType) {
    this.dataProvider = dataProvider;
    this.stickerType = stickerType;
  }

  private TGStickerObj.DataProvider dataProvider;
  private TdApi.StickerType stickerType;
  private TdApi.TrendingStickerSets scheduledFeaturedSets;
  public boolean trendingLoading, canLoadMoreTrending;


  public void loadTrending (int offset, int limit, int cellCount) {
    if (!trendingLoading) {
      trendingLoading = true;
      tdlib.client().send(new TdApi.GetTrendingStickerSets(stickerType, offset, limit), object -> {
        final ArrayList<TGStickerSetInfo> parsedStickerSets = new ArrayList<>();
        final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();
        final int unreadItemCount;

        if (object.getConstructor() == TdApi.TrendingStickerSets.CONSTRUCTOR) {
          TdApi.TrendingStickerSets trendingStickerSets = (TdApi.TrendingStickerSets) object;
          if (offset == 0)
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));
          unreadItemCount = EmojiMediaListController.parseTrending(tdlib, parsedStickerSets, items,  cellCount, trendingStickerSets.sets, dataProvider, this, false, false, null);
        } else {
          if (offset == 0)
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_COME_AGAIN_LATER));
          unreadItemCount = 0;
        }

        UI.post(() -> addTrendingStickers(parsedStickerSets, items, unreadItemCount > 0, offset));
      });
    }
  }

  private void addTrendingStickers (ArrayList<TGStickerSetInfo> trendingSets, ArrayList<MediaStickersAdapter.StickerItem> items, boolean hasUnread, int offset) {
    if (offset != 0 && (!trendingLoading || offset != stickerSets.size()))
      return;

    if (trendingSets != null) {
      if (offset == 0) {
        lastStickerSetInfo = null;
        stickerSets.clear();
      }
      stickerSets.addAll(trendingSets);
    }
    this.canLoadMoreTrending = trendingSets != null && !trendingSets.isEmpty();
    if (emojiLayout != null && (hasUnread || offset == 0)) {
      emojiLayout.setHasNewHots(getId(), hasUnread);
    }
    if (offset == 0) {
      if (recyclerView != null) {
        recyclerView.stopScroll();
        manager.scrollToPositionWithOffset(0, 0);
      }
      adapter.setItems(items);
    } else {
      adapter.addItems(items);
    }
    this.trendingLoading = false;
  }

  public void updateTrendingSets (long[] stickerSetIds) {
    if (stickerSets == null) return;

    LongSparseArray<TGStickerSetInfo> installedStickerSets = new LongSparseArray<>(stickerSetIds.length);
    for (long stickerSetId : stickerSetIds) {
      installedStickerSets.put(stickerSetId, null);
    }
    for (TGStickerSetInfo stickerSet : stickerSets) {
      int i = installedStickerSets.indexOfKey(stickerSet.getId());
      if (i >= 0) {
        stickerSet.setIsInstalled();
        adapter.updateDone(stickerSet);
      } else {
        stickerSet.setIsNotInstalled();
        adapter.updateDone(stickerSet);
      }
    }
  }

  private void applyScheduledFeaturedSets (TdApi.TrendingStickerSets sets) {
    if (sets != null && !isDestroyed() && !trendingLoading) {
      if (stickerSets != null && stickerSets.size() == sets.sets.length && !stickerSets.isEmpty()) {
        boolean equal = true;
        int i = 0;
        for (TGStickerSetInfo stickerSetInfo : stickerSets) {
          if (stickerSetInfo.getId() != sets.sets[i].id) {
            equal = false;
            break;
          }
          boolean visuallyChanged = stickerSetInfo.isViewed() != sets.sets[i].isViewed;
          stickerSetInfo.updateState(sets.sets[i]);
          if (visuallyChanged) {
            adapter.updateState(stickerSetInfo);
          }
          i++;
        }
        if (equal) {
          return;
        }
      }

      final ArrayList<MediaStickersAdapter.StickerItem> stickerItems = new ArrayList<>(sets.sets.length * 2 + 1);
      final ArrayList<TGStickerSetInfo> stickerSetInfos = new ArrayList<>(sets.sets.length);
      stickerItems.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));
      final int unreadItemCount = EmojiMediaListController.parseTrending(tdlib, stickerSetInfos, stickerItems, 0, sets.sets, dataProvider, this, false, false, null);
      addTrendingStickers(stickerSetInfos, stickerItems, unreadItemCount > 0, 0);
    }
  }

  @Override
  public void applyStickerSet (TdApi.StickerSet stickerSet, TGStickerObj.DataProvider dataProvider) {
    if (stickerSets == null || stickerSets.isEmpty()) {
      return;
    }
    for (TGStickerSetInfo oldStickerSet : stickerSets) {
      if (oldStickerSet.getId() == stickerSet.id) {
        oldStickerSet.setStickerSet(stickerSet);
        for (int stickerIndex = oldStickerSet.getCoverCount(), j = oldStickerSet.getStartIndex() + 1 + oldStickerSet.getCoverCount(); stickerIndex < Math.min(stickerSet.stickers.length - oldStickerSet.getCoverCount(), oldStickerSet.getCoverCount() + 4); stickerIndex++, j++) {
          MediaStickersAdapter.StickerItem item = adapter.getItem(j);
          if (item.sticker != null) {
            TdApi.Sticker sticker = stickerSet.stickers[stickerIndex];
            item.sticker.set(tdlib, sticker, sticker.fullType, stickerSet.emojis[stickerIndex].emojis);
          }

          View view = recyclerView != null ? manager.findViewByPosition(j) : null;
          if (view instanceof StickerSmallView && view.getTag() == item) {
            ((StickerSmallView) view).refreshSticker();
          } else {
            adapter.notifyItemChanged(j);
          }
        }
        break;
      }
    }
  }

  public void scheduleFeaturedSets (TdApi.TrendingStickerSets stickerSets, boolean isSectionVisible) {
    if (isSectionVisible) {
      scheduledFeaturedSets = stickerSets;
    } else {
      scheduledFeaturedSets = null;
      applyScheduledFeaturedSets(stickerSets);
    }
  }

  public void applyScheduledFeaturedSets () {
    if (scheduledFeaturedSets != null) {
      applyScheduledFeaturedSets(scheduledFeaturedSets);
      scheduledFeaturedSets = null;
    }
  }

  public void onScrolledImpl (int dy, boolean showRecentTitle) {
    if (emojiLayout != null) {
      emojiLayout.moveHeader(getStickersScrollY(showRecentTitle));
      emojiLayout.onSectionInteractedScroll(mediaType, dy != 0);
    }
    if (!trendingLoading && canLoadMoreTrending) {
      int lastVisiblePosition = manager.findLastVisibleItemPosition();
      if (lastVisiblePosition != -1) {
        int index = stickerSets.indexOf(adapter.getItem(lastVisiblePosition).stickerSet);
        if (index != -1 && index + 5 >= stickerSets.size()) {
          loadTrending(stickerSets.size(), 25, adapter.getItemCount());
        }
      }
    }
  }



  /* View sets */

  @Override
  public void viewStickerSet (TGStickerSetInfo stickerSetInfo) {
    viewStickerSetInternal(stickerSetInfo.getId());
  }

  private LongSparseArray<Boolean> pendingViewStickerSets;
  private CancellableRunnable viewSets;

  private void viewStickerSetInternal (long stickerSetId) {
    if (pendingViewStickerSets == null) {
      pendingViewStickerSets = new LongSparseArray<>();
    } else if (pendingViewStickerSets.indexOfKey(stickerSetId) >= 0) {
      return;
    }
    pendingViewStickerSets.put(stickerSetId, true);
    if (viewSets != null) {
      viewSets.cancel();
    }
    viewSets = new CancellableRunnable() {
      @Override
      public void act () {
        if (pendingViewStickerSets != null && pendingViewStickerSets.size() > 0) {
          final int size = pendingViewStickerSets.size();
          long[] setIds = new long[size];
          for (int i = 0; i < size; i++) {
            setIds[i] = pendingViewStickerSets.keyAt(i);
          }
          pendingViewStickerSets.clear();
          tdlib.client().send(new TdApi.ViewTrendingStickerSets(setIds), tdlib.okHandler());
        }
      }
    };
    UI.post(viewSets, 750L);
  }
}
