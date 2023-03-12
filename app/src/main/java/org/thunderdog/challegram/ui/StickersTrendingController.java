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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.CancellableRunnable;

public class StickersTrendingController extends ViewController<Void> implements StickerSmallView.StickerMovementCallback, Client.ResultHandler, TGStickerObj.DataProvider, StickersListener, TGStickerSetInfo.ViewCallback {
  public StickersTrendingController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_stickersTrending;
  }

  private RecyclerView recyclerView;
  private MediaStickersAdapter adapter;

  @Override
  protected View onCreateView (Context context) {
    adapter = new MediaStickersAdapter(this, this, true, this);
    adapter.setIsBig();

    GridLayoutManager manager = new GridLayoutManager(context, 5);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return adapter.getItemViewType(position) == MediaStickersAdapter.StickerHolder.TYPE_STICKER ? 1 : 5;
      }
    });

    recyclerView = (RecyclerView) Views.inflate(context(), R.layout.recycler, null);
    Views.setScrollBarPosition(recyclerView);
    recyclerView.setItemAnimator(null);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);
    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_filling, this);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    adapter.setManager(recyclerView.getLayoutManager());
    adapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PROGRESS));
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (!loadingTrending && canLoadMoreTrending) {
          int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
          if (lastVisiblePosition != -1) {
            int index = stickerSets.indexOf(adapter.getItem(lastVisiblePosition).stickerSet);
            if (index != -1 && index + 5 >= stickerSets.size()) {
              loadTrending(stickerSets.size(), 25, adapter.getItemCount());
            }
          }
        }
      }
    });

    tdlib.listeners().subscribeToStickerUpdates(this);

    loadTrending(0, 20, 0);

    return recyclerView;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
    Views.setScrollBarPosition(recyclerView);
  }

  public boolean isTrendingLoaded () {
    return !loadingTrending;
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
  }

  @Override
  public void onRecentStickersUpdated (int[] stickerIds, boolean isAttached) { }

  @Override
  public void onInstalledStickerSetsUpdated (long[] stickerSetIds, TdApi.StickerType stickerType) {
    if (stickerType.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR)
      return;
    final LongSparseArray<TGStickerSetInfo> sets = new LongSparseArray<>(stickerSetIds.length);
    for (long setId : stickerSetIds) {
      sets.put(setId, null);
    }
    runOnUiThreadOptional(() -> {
      for (TGStickerSetInfo stickerSet : stickerSets) {
        int i = sets.indexOfKey(stickerSet.getId());
        if (i >= 0) {
          stickerSet.setIsInstalled();
        } else {
          stickerSet.setIsNotInstalled();
        }
        adapter.updateDone(stickerSet);
      }
    });
  }

  @Override
  public void onStickerSetArchived (TdApi.StickerSetInfo stickerSet) {
    if (stickerSet.stickerType.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR)
      return;
    final long stickerSetId = stickerSet.id;
    runOnUiThreadOptional(() -> {
      for (TGStickerSetInfo stickerSet1 : stickerSets) {
        if (stickerSetId == stickerSet1.getId()) {
          stickerSet1.setIsArchived();
          adapter.updateDone(stickerSet1);
          break;
        }
      }
    });
  }

  @Override
  public void onStickerSetRemoved (TdApi.StickerSetInfo removedStickerSet) {
    if (removedStickerSet.stickerType.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR)
      return;
    final long removedStickerSetId = removedStickerSet.id;
    runOnUiThreadOptional(() -> {
      for (TGStickerSetInfo stickerSet : stickerSets) {
        if (removedStickerSetId == stickerSet.getId()) {
          stickerSet.setIsNotInstalled();
          stickerSet.setIsNotArchived();
          adapter.updateDone(stickerSet);
          break;
        }
      }
    });
  }

  @Override
  public void onStickerSetInstalled (TdApi.StickerSetInfo installedStickerSet) {
    if (installedStickerSet.stickerType.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR)
      return;
    final long installedStickerSetId = installedStickerSet.id;
    runOnUiThreadOptional(() -> {
      for (TGStickerSetInfo stickerSet : stickerSets) {
        if (installedStickerSetId == stickerSet.getId()) {
          stickerSet.setIsInstalled();
          adapter.updateDone(stickerSet);
          break;
        }
      }
    });
  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return true;
  }

  @Override
  public void onTrendingStickersUpdated (final TdApi.StickerType stickerType, final TdApi.TrendingStickerSets stickerSets, int unreadCount) {
    if (stickerType.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR || stickerSets.sets.length == 0)
      return;
    runOnUiThreadOptional(() -> {
      if (!loadingTrending) {
        loadTrending(0, 20, 0);
      }
    });
  }

  @Override
  public void onFavoriteStickersUpdated (int[] stickerIds) { }

  private boolean loadingTrending, canLoadMoreTrending;

  private void loadTrending (int offset, int limit, int cellCount) {
    if (!loadingTrending) {
      loadingTrending = true;
      tdlib.client().send(new TdApi.GetTrendingStickerSets(new TdApi.StickerTypeRegular(), offset, limit), result -> {
        switch (result.getConstructor()) {
          case TdApi.TrendingStickerSets.CONSTRUCTOR: {
            final TdApi.TrendingStickerSets trendingStickerSets = (TdApi.TrendingStickerSets) result;
            final ArrayList<MediaStickersAdapter.StickerItem> stickerItems = new ArrayList<>();
            final ArrayList<TGStickerSetInfo> stickerSets;

            TdApi.StickerSetInfo[] sets = trendingStickerSets.sets;
            if (sets.length > 0) {
              stickerSets = new ArrayList<>(sets.length);
              EmojiMediaListController.parseTrending(tdlib, stickerSets, stickerItems, cellCount, sets, this, this, true);
            } else {
              stickerSets = null;
              if (offset == 0)
                stickerItems.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_COME_AGAIN_LATER));
            }

            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                addStickerSets(stickerSets, stickerItems, offset, cellCount);
                getParentOrSelf().executeScheduledAnimation();
              }
            });
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(result);
            break;
          }
        }
      });
    }
  }

  private final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>();

  private void addStickerSets (@Nullable ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> stickerItems, int offset, int cellCount) {
    if (offset != 0 && (!loadingTrending || offset != this.stickerSets.size()))
      return;

    if (stickerSets != null) {
      if (offset == 0)
        this.stickerSets.clear();
      this.stickerSets.addAll(stickerSets);
    }
    this.canLoadMoreTrending = stickerSets != null && !stickerSets.isEmpty();

    if (offset == 0) {
      adapter.setItems(stickerItems);
    } else {
      adapter.addItems(stickerItems);
    }
    this.loadingTrending = false;
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
    UI.post(viewSets, 750l);
  }

  private void applyStickerSet (TdApi.StickerSet stickerSet) {
    loadingStickerSets.remove(stickerSet.id);

    if (stickerSets == null) {
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

          View view = recyclerView != null ? recyclerView.getLayoutManager().findViewByPosition(j) : null;
          if (view != null && view instanceof StickerSmallView) {
            ((StickerSmallView) view).refreshSticker();
          } else {
            adapter.notifyItemChanged(j);
          }
        }
        break;
      }
    }
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.StickerSet.CONSTRUCTOR: {
        final TdApi.StickerSet stickerSet = (TdApi.StickerSet) object;
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            applyStickerSet(stickerSet);
          }
        });

        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        break;
      }
    }
  }

  @Override
  public void viewStickerSet (TGStickerSetInfo stickerSetInfo) {
    viewStickerSetInternal(stickerSetInfo.getId());
  }

  private LongSparseArray<Boolean> loadingStickerSets;

  @Override
  public void requestStickerData (TGStickerObj sticker, long stickerSetId) {
    if (loadingStickerSets == null) {
      loadingStickerSets = new LongSparseArray<>();
    } else if (loadingStickerSets.get(stickerSetId, Boolean.FALSE)) {
      return;
    }

    loadingStickerSets.put(stickerSetId, true);
    tdlib.client().send(new TdApi.GetStickerSet(stickerSetId), this);
  }

  private int indexOfTrendingStickerSetById (long setId) {
    if (stickerSets != null) {
      int i = 0;
      for (TGStickerSetInfo info : stickerSets) {
        if (info.getId() == setId) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  @Override
  public int getViewportHeight () {
    return -1;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    int i = indexOfTrendingStickerSetById(sticker.getStickerSetId());
    if (i != -1 && stickerSets != null) {
      if (isMenuClick) {
        ShareController c = new ShareController(context, tdlib);
        // TODO pass sendOptions
        c.setArguments(new ShareController.Args(sticker.getSticker()));
        c.show();
      } else {
        stickerSets.get(i).show(this);
      }
      return true;
    }
    return false;
  }

  @Override
  public long getStickerOutputChatId () {
    return 0;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    adapter.setStickerPressed(sticker, isPressed, recyclerView.getLayoutManager());
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return true;
  }

  @Override
  public int getStickersListTop () {
    return Views.getLocationInWindow(recyclerView)[1];
  }
}
