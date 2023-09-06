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
 * File created on 17/03/2016 at 01:05
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.sticker.StickerSetWrap;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoubleTextView;
import org.thunderdog.challegram.widget.NonMaterialButton;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.td.Td;

public class StickersController extends RecyclerViewController<StickersController.Args> implements View.OnClickListener, StickersListener {
  public static final int MODE_STICKERS = 0;
  public static final int MODE_STICKERS_ARCHIVED = 1;
  public static final int MODE_MASKS = 2;

  public static class Args {
    public final int mode;
    public final boolean isEmoji;
    public final boolean doNotLoad;

    public @Nullable ArrayList<TGStickerSetInfo> stickerSets;

    public Args (int mode, boolean isEmoji, boolean doNotLoad) {
      this.mode = mode;
      this.isEmoji = isEmoji;
      this.doNotLoad = doNotLoad;
    }

    public Args setStickerSets (@Nullable ArrayList<TGStickerSetInfo> stickerSets) {
      this.stickerSets = stickerSets;
      return this;
    }
  }

  public StickersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    switch (mode) {
      case MODE_STICKERS: return R.id.controller_stickers;
      case MODE_STICKERS_ARCHIVED: return R.id.controller_stickersArchived;
      case MODE_MASKS: return R.id.controller_masks;
    }
    return R.id.controller_stickers;
  }

  private int mode;
  private boolean isEmoji;
  private boolean doNotLoad;
  private @Nullable ArrayList<TGStickerSetInfo> stickerSetsToSets;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.isEmoji = args.isEmoji;
    this.doNotLoad = args.doNotLoad;
    this.stickerSetsToSets = args.stickerSets;
  }

  private AdapterForAdapter adapterForAdapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapterForAdapter = new AdapterForAdapter(this, mode, isEmoji);
    adapterForAdapter.setStickerSets(stickerSetsToSets);
    adapterForAdapter.search(searchRequest);

    if (mode == MODE_STICKERS || mode == MODE_MASKS) {
      if (mode == MODE_STICKERS) {
        recyclerView.setItemAnimator(null);
      }
      ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
        @Override
        public int getMovementFlags (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
          int viewType = viewHolder.getItemViewType();
          if (viewType == ListItem.TYPE_STICKER_SET && !adapterForAdapter.inSearchMode()) {
            int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            return makeMovementFlags(dragFlags, 0);
          }
          return 0;
        }

        @Override
        public boolean isLongPressDragEnabled () {
          return true;
        }

        private int dragFrom = -1;
        private int dragTo = -1;

        @Override
        public void onMoved (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, int fromPos, @NonNull RecyclerView.ViewHolder target, int toPos, int x, int y) {
          super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
          viewHolder.itemView.invalidate();
          target.itemView.invalidate();
        }

        @Override
        public boolean onMove (@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
          int fromPosition = viewHolder.getAdapterPosition();
          int toPosition = target.getAdapterPosition();

          int fromIndex = adapterForAdapter.getIndexFromPosition(fromPosition);
          int toIndex = adapterForAdapter.getIndexFromPosition(toPosition);

          if (stickerSetsToSets != null && fromIndex != -1 && toIndex != -1) {
            adapterForAdapter.moveStickerSet(fromIndex, toIndex);

            if (dragFrom == -1) {
              dragFrom = fromPosition;
            }
            dragTo = toPosition;

            return true;
          }

          return false;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
          super.clearView(recyclerView, viewHolder);

          if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
            adapterForAdapter.saveStickersOrder();
          }

          dragFrom = dragTo = -1;
        }

        @Override
        public void onSwiped (@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

        }
      });
      helper.attachToRecyclerView(recyclerView);
    }

    if (mode == MODE_STICKERS_ARCHIVED) {
      recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (!isLoading && ((adapterForAdapter.stickerSets != null && !adapterForAdapter.stickerSets.isEmpty()))) {
            int position = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
            if (position != RecyclerView.NO_POSITION) {
              position += 10;
              if (position >= adapterForAdapter.adapter.getItemCount() - 1) {
                loadData(true);
              }
            }
          }
        }
      });
    }

    if (stickerSetsToSets != null) {
      adapterForAdapter.buildItems();
    } else if (!doNotLoad) {
      loadData(false);
    }

    if (mode == MODE_STICKERS || mode == MODE_MASKS || mode == MODE_STICKERS_ARCHIVED) {
      tdlib.listeners().subscribeToStickerUpdates(this);
    }

    recyclerView.setAdapter(adapterForAdapter.adapter);
  }

  private TdApi.StickerType getStickerType () {
    return getStickerType(mode, isEmoji);
  }

  private static TdApi.StickerType getStickerType (int mode, boolean isEmoji) {
    if (isEmoji) {
      return new TdApi.StickerTypeCustomEmoji();
    }
    switch (mode) {
      case MODE_STICKERS:
      case MODE_STICKERS_ARCHIVED:
        return new TdApi.StickerTypeRegular();
      case MODE_MASKS:
        return new TdApi.StickerTypeMask();
    }
    throw new IllegalStateException("mode == " + mode);
  }

  private boolean parentFocusApplied;

  public void onParentFocus () {
    if (!parentFocusApplied) {
      parentFocusApplied = true;
      getRecyclerView().setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180L));
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
  }

  @Override
  public void onInstalledStickerSetsUpdated (final long[] stickerSetIds, TdApi.StickerType stickerType) {
    if (Td.equalsTo(getStickerType(), stickerType)) {
      runOnUiThreadOptional(() -> {
        if (!isLoading) {
          adapterForAdapter.changeStickerSets(stickerSetIds);
        }
      });
    }
  }

  private static boolean isMasks (TdApi.StickerType type) {
    return type.getConstructor() == TdApi.StickerTypeMask.CONSTRUCTOR;
  }

  @Override
  public void onStickerSetArchived (final TdApi.StickerSetInfo stickerSet) {
    if ((mode == MODE_MASKS && isMasks(stickerSet.stickerType)) || (mode == MODE_STICKERS_ARCHIVED && !isMasks(stickerSet.stickerType))) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && !isLoading) {
          TdApi.StickerSetInfo newRawInfo = Td.copyOf(stickerSet);
          if (mode == MODE_MASKS) {
            newRawInfo.isInstalled = true;
            newRawInfo.isArchived = true;
            TGStickerSetInfo info = new TGStickerSetInfo(tdlib, newRawInfo);
            adapterForAdapter.addStickerSet(info, adapterForAdapter.getInstalledSetsInDatasetCount());
          } else {
            newRawInfo.isArchived = true;
            TGStickerSetInfo info = new TGStickerSetInfo(tdlib, newRawInfo);
            adapterForAdapter.addStickerSet(info, 0);
          }
        }
      });
    }
  }

  @Override
  public void onStickerSetRemoved (TdApi.StickerSetInfo stickerSet) {
    if ((mode == MODE_MASKS && isMasks(stickerSet.stickerType)) || (mode == MODE_STICKERS_ARCHIVED && !isMasks(stickerSet.stickerType))) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && !isLoading) {
          if (mode == MODE_MASKS) {
            // TODO?
          } else {
            adapterForAdapter.removeStickerSet(stickerSet.id);
          }
        }
      });
    }
  }

  @Override
  public void onStickerSetInstalled (TdApi.StickerSetInfo stickerSet) {
    if ((mode == MODE_MASKS && isMasks(stickerSet.stickerType)) || (mode == MODE_STICKERS_ARCHIVED && !isMasks(stickerSet.stickerType))) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          if (mode == MODE_MASKS) {
            // TODO?
          } else {
            adapterForAdapter.removeStickerSet(stickerSet.id);
          }
        }
      });
    }
  }

  private boolean isLoading, isLoadingMore, endReached;

  private void loadData (boolean isMore) {
    if (isLoading || isMore && endReached) return;

    isLoading = true;
    isLoadingMore = isMore;
    TdApi.Function<?> function = null;
    if ((mode == MODE_STICKERS || mode == MODE_MASKS) && !isMore) {
      function = new TdApi.GetInstalledStickerSets(getStickerType());
    } else if (mode == MODE_STICKERS_ARCHIVED) {
      long offsetStickerSetId;
      int limit;
      if (isMore) {
        if (adapterForAdapter.stickerSets == null || adapterForAdapter.stickerSets.isEmpty()) {
          return;
        }
        offsetStickerSetId = adapterForAdapter.stickerSets.get(adapterForAdapter.stickerSets.size() - 1).getId();
        limit = 100;
      } else {
        offsetStickerSetId = 0;
        limit = Screen.calculateLoadingItems(Screen.dp(72f), 20);
      }
      function = new TdApi.GetArchivedStickerSets(getStickerType(), offsetStickerSetId, limit);
    }
    if (function != null) {
      tdlib.client().send(function, this::onStickerSetsResult);
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_stickerSetInfo) {
      ListItem item = (ListItem) v.getTag();
      TGStickerSetInfo info = adapterForAdapter.findStickerSetById(item.getLongId());
      if (info != null) {
        if (mode == MODE_STICKERS_ARCHIVED && adapterForAdapter.getState(info) == AdapterForAdapter.STATE_DONE) {
          return;
        }
        StickerSetWrap.showStickerSet(this, info.getInfo()).setIsOneShot();
      }
    } else if (viewId == R.id.btn_double) {
      ListItem item = (ListItem) ((ViewGroup) v.getParent()).getTag();
      final TGStickerSetInfo info = adapterForAdapter.findStickerSetById(item.getLongId());
      if (info != null) {
        int state = adapterForAdapter.getState(info);
        if (state == AdapterForAdapter.STATE_NONE) {
          adapterForAdapter.setState(info.getId(), AdapterForAdapter.STATE_IN_PROGRESS);
          tdlib.client().send(new TdApi.ChangeStickerSet(info.getId(), true, false), object -> tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              boolean isOk = object.getConstructor() == TdApi.Ok.CONSTRUCTOR;
              if (isOk) {
                info.setIsInstalled();
              }
              adapterForAdapter.setState(info.getId(), isOk ? AdapterForAdapter.STATE_DONE : AdapterForAdapter.STATE_NONE);
              if (isOk) {
                if (mode == MODE_STICKERS_ARCHIVED) {
                  UI.post(() -> adapterForAdapter.removeStickerSet(info.getId()), 1500);
                } else if (adapterForAdapter.currentStates != null) {
                  adapterForAdapter.currentStates.delete(info.getId());
                }
              }
            }
          }));
        }
      }
    }
  }

  @UiThread
  public void setStickerSets (ArrayList<TGStickerSetInfo> stickerSets) {
    this.adapterForAdapter.setStickerSets(stickerSets);
  }

  private void onStickerSetsResult (final TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.StickerSets.CONSTRUCTOR: {
        TdApi.StickerSetInfo[] sets = ((TdApi.StickerSets) object).sets;

        final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>(sets.length);

        switch (mode) {
          case MODE_STICKERS_ARCHIVED: {
            for (TdApi.StickerSetInfo info : sets) {
              if (info.isArchived) {
                TGStickerSetInfo stickerSet = new TGStickerSetInfo(tdlib, info);
                stickerSet.setBoundList(stickerSets);
                stickerSets.add(stickerSet);
              }
            }
            break;
          }
          case MODE_STICKERS: {
            for (TdApi.StickerSetInfo info : sets) {
              if (!info.isArchived) {
                TGStickerSetInfo stickerSet = new TGStickerSetInfo(tdlib, info);
                stickerSet.setBoundList(stickerSets);
                stickerSets.add(stickerSet);
              }
            }
            break;
          }
          case MODE_MASKS: {
            for (TdApi.StickerSetInfo info : sets) {
              TGStickerSetInfo stickerSet = new TGStickerSetInfo(tdlib, info);
              stickerSet.setBoundList(stickerSets);
              stickerSets.add(stickerSet);
            }
            break;
          }
        }

        stickerSets.trimToSize();

        if (mode == MODE_MASKS) {
          tdlib.client().send(new TdApi.GetArchivedStickerSets(getStickerType(), 0, 100), object1 -> {
            if (object1.getConstructor() == TdApi.StickerSets.CONSTRUCTOR && ((TdApi.StickerSets) object1).sets.length > 0) {
              TdApi.StickerSetInfo[] stickerSets1 = ((TdApi.StickerSets) object1).sets;
              for (TdApi.StickerSetInfo stickerSet : stickerSets1) {
                TGStickerSetInfo info = new TGStickerSetInfo(tdlib, stickerSet);
                info.setBoundList(stickerSets);
                stickerSets.add(info);
              }
            }

            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                isLoading = false;
                if (isLoadingMore) {
                  adapterForAdapter.addMoreStickerSets(stickerSets);
                } else {
                  setStickerSets(stickerSets);
                }
              }
            });
          });
        } else {
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              isLoading = false;
              if (isLoadingMore) {
                adapterForAdapter.addMoreStickerSets(stickerSets);
              } else {
                setStickerSets(stickerSets);
              }
            }
          });
        }

        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            isLoading = false;
            UI.showError(object);
          }
        });
        break;
      }
    }
  }





  /* Search */

  private String searchRequest;

  public void search (String request) {
    this.searchRequest = request;
    if (getWrapUnchecked() != null) {
      this.adapterForAdapter.search(request);
      this.adapterForAdapter.buildItems();
    }
  }

  public class AdapterForAdapter {
    private final Tdlib tdlib;
    private final SettingsAdapter adapter;
    private final boolean isEmoji;
    private final int mode;
    private final ViewController<?> context;

    private @Nullable ArrayList<TGStickerSetInfo> stickerSets = null;
    private @NonNull ArrayList<TGStickerSetInfo> foundStickerSets = new ArrayList<>();
    private @Nullable String searchRequest;
    private int itemsStartIndex = -1;
    private int itemsBreakIndex = -1;
    private int itemsBreakSize = -1;

    public AdapterForAdapter (ViewController<?> context, int mode, boolean isEmoji) {
      this.context = context;
      this.adapter = new SettingsAdapter(context) {
        @Override
        protected void setStickerSet (ListItem item, int position, DoubleTextView group, boolean isArchived, boolean isUpdate) {
          bind(item, position, group, isArchived, isUpdate);
        }
      };
      this.isEmoji = isEmoji;
      this.tdlib = context.tdlib();
      this.mode = mode;
    }

    public void buildItems () {
      ArrayList<TGStickerSetInfo> stickerSets = getActualStickerSetsList();
      ArrayList<ListItem> items = new ArrayList<>(Math.max(1, stickerSets != null ? stickerSets.size() * 2 - 1 : 0));
      itemsStartIndex = -1;
      itemsBreakIndex = -1;
      itemsBreakSize = -1;

      if (this.stickerSets == null) {
        items.add(new ListItem(ListItem.TYPE_PROGRESS));
        adapter.setItems(items, false);
        return;
      }

      if (stickerSets == null || stickerSets.isEmpty()) {
        if (mode == MODE_STICKERS_ARCHIVED) {
          items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, Lang.getString(!isEmoji ? R.string.ArchivedStickersInfo : R.string.ArchivedEmojiInfo, Strings.buildCounter(tdlib.getInstalledStickerSetLimit())), false));
        } else {
          items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, mode == MODE_STICKERS ? R.string.NoStickerSets : R.string.NoMasks));
        }
        adapter.setItems(items, false);
        return;
      }

      if (mode == MODE_STICKERS_ARCHIVED) {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(!isEmoji ? R.string.ArchivedStickersInfo : R.string.ArchivedEmojiInfo, Strings.buildCounter(tdlib.getInstalledStickerSetLimit())), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      } else if (mode == MODE_MASKS) {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MasksHint));
      }
      itemsStartIndex = items.size();

      if (mode == MODE_MASKS) {
        for (int a = 0; a < stickerSets.size(); a++) {
          TGStickerSetInfo info = stickerSets.get(a);
          if (!info.isArchived() && a == 0) {
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            itemsStartIndex += 1;
          }
          if (info.isArchived() && itemsBreakIndex == -1) {
            itemsBreakIndex = items.size();
            if (itemsBreakIndex != itemsStartIndex) {
              items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            }
            items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Archived));
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            itemsBreakSize = items.size() - itemsBreakIndex;
          }
          items.add(new ListItem(itemsBreakIndex == -1 ? ListItem.TYPE_STICKER_SET : ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()).setHighlightValue(searchRequest));
        }
      } else if (mode == MODE_STICKERS_ARCHIVED) {
        for (TGStickerSetInfo info : stickerSets) {
          items.add(new ListItem(ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()).setHighlightValue(searchRequest));
        }
      } else {
        for (TGStickerSetInfo info : stickerSets) {
          items.add(new ListItem(ListItem.TYPE_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()).setHighlightValue(searchRequest));
        }
      }
      if (!stickerSets.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
      if (mode != MODE_MASKS) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION_CENTERED, R.id.view_emojiPacksCount, 0, Lang.pluralBold(isEmoji ? R.string.xEmojiPacks : R.string.xStickerPacks, stickerSets.size()), false));
      }
      adapter.setItems(items, false);
    }

    public void search (@Nullable String searchRequest) {
      this.searchRequest = searchRequest;
      this.foundStickerSets = filter(stickerSets, searchRequest);
    }

    public void setStickerSets (@Nullable ArrayList<TGStickerSetInfo> items) {
      stickerSets = items;
      foundStickerSets = filter(items, searchRequest);
      buildItems();
    }

    public void bind (ListItem item, int position, DoubleTextView group, boolean isArchived, boolean isUpdate) {
      TGStickerSetInfo stickerSet = /*findStickerSetById(item.getLongId()); //*/ getStickerSetByPosition(position);
      if (stickerSet == null || stickerSet.getId() != item.getLongId()) {
        return;
      }
      group.setStickerSet(stickerSet, item.getHighlightValue());
      if (isArchived) {
        NonMaterialButton button = group.getButton();
        if (button != null) {
          int state = getState(stickerSet);
          button.setInProgress(state == STATE_IN_PROGRESS, isUpdate);
          button.setIsDone(state == STATE_DONE, isUpdate);
        }
      }
    }


    public void addMoreStickerSets (ArrayList<TGStickerSetInfo> stickerSetsToAdd) {
      if (mode != MODE_STICKERS_ARCHIVED) return; // todo: for masks
      if (stickerSets == null || stickerSets.isEmpty() || stickerSetsToAdd.isEmpty()) {
        return;
      }

      final ArrayList<TGStickerSetInfo> currentStickerSets = getActualStickerSetsList();
      final boolean inSearchMode = inSearchMode();
      final int oldSize = currentStickerSets != null ? currentStickerSets.size() : 0;

      stickerSets.addAll(stickerSetsToAdd);

      ArrayList<TGStickerSetInfo> realStickersToAdd = stickerSetsToAdd;
      if (inSearchMode) {
        foundStickerSets.addAll(realStickersToAdd = filter(stickerSetsToAdd, searchRequest));
      }
      if (oldSize == 0) {
        buildItems();
        return;
      }
      List<ListItem> items = adapter.getItems();
      int startIndex = itemsStartIndex + oldSize;
      int i = startIndex;
      for (TGStickerSetInfo info : realStickersToAdd) {
        info.setBoundList(this.stickerSets);
        items.add(i, new ListItem(ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setHighlightValue(searchRequest).setLongId(info.getId()));
        i++;
      }
      adapter.notifyItemRangeInserted(startIndex, realStickersToAdd.size());
      updateEmojiPacksCount();
    }

    public void removeStickerSet (long id) {
      final ArrayList<TGStickerSetInfo> currentStickerSets = getActualStickerSetsList();
      final boolean inSearchMode = inSearchMode();
      final int totalIndex = indexOfStickerSetById(stickerSets, id);
      final int foundIndex = indexOfStickerSetById(foundStickerSets, id);
      final int realIndex = inSearchMode ? foundIndex : totalIndex;

      if (currentStates != null) {
        currentStates.delete(id);
      }

      if (stickerSets != null && totalIndex != -1) {
        stickerSets.remove(totalIndex);
      }
      if (inSearchMode && foundIndex != -1) {
        foundStickerSets.remove(foundIndex);
      }
      if (itemsStartIndex == -1 || currentStickerSets == null || currentStickerSets.isEmpty()) {
        buildItems();
        return;
      }
      if (realIndex == -1) {
        return;
      }

      boolean needUpdateBreakPosition = false;
      int position = itemsStartIndex + realIndex;
      if (itemsBreakIndex != -1 && position > itemsBreakIndex) {
        position += itemsBreakSize;
      } else {
        needUpdateBreakPosition = itemsBreakIndex != -1;
      }
      if (needUpdateBreakPosition) {
        itemsBreakIndex -= 1;
      }
      adapter.removeItem(position);
      updateEmojiPacksCount();
    }

    public void addStickerSet (TGStickerSetInfo info, int index) {
      final ArrayList<TGStickerSetInfo> currentStickerSets = getActualStickerSetsList();
      final boolean inSearchMode = inSearchMode();
      final int oldSize = currentStickerSets != null ? currentStickerSets.size() : 0;
      final int totalIndex = indexOfStickerSetById(stickerSets, info.getId());
      if (totalIndex != -1 || stickerSets == null) {
        if (totalIndex != -1 && mode == MODE_MASKS) {
          removeStickerSet(info.getId());   // todo: call move
        } else {
          return;
        }
      }
      info.setBoundList(stickerSets);

      int firstVisiblePosition = ((LinearLayoutManager) getRecyclerView().getLayoutManager()).findFirstVisibleItemPosition();
      View topView = getRecyclerView().getLayoutManager().findViewByPosition(firstVisiblePosition);
      int offset = topView != null ? topView.getTop() : 0;

      final int realIndex;
      stickerSets.add(index, info);
      if (inSearchMode && filterImpl(info.getTitle(), searchRequest)) {
        int indexToInsert = indexOfFoundStickerSet(index);
        foundStickerSets.add(indexToInsert, info);
        realIndex = indexToInsert;
      } else {
        realIndex = inSearchMode ? -1 : index;
      }

      if (realIndex == -1) {
        return;
      }
      if (oldSize == 0 || mode == MODE_MASKS) {
        buildItems();
        return;
      }

      boolean needUpdateBreakPosition = false;
      int position = itemsStartIndex + realIndex;
      if (itemsBreakIndex != -1 && position > itemsBreakIndex) {
        position += itemsBreakSize;
      } else {
        needUpdateBreakPosition = itemsBreakIndex != -1;
      }
      if (needUpdateBreakPosition) {
        itemsBreakIndex += 1;
      }
      adapter.addItem(position, new ListItem(info.isArchived() ? ListItem.TYPE_ARCHIVED_STICKER_SET : ListItem.TYPE_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setHighlightValue(searchRequest).setLongId(info.getId()));
      updateEmojiPacksCount();

      ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(firstVisiblePosition, offset);
    }

    public void moveStickerSet (int oldPosition, int newPosition) {
      final boolean inSearchMode = inSearchMode();
      if (oldPosition == newPosition || stickerSets == null) {
        return;
      }
      ArrayUtils.move(stickerSets, oldPosition, newPosition);

      int realOldIndex = oldPosition;
      int realNewIndex = newPosition;
      boolean needUpdateAdapter = true;

      if (inSearchMode) {
        if (filterImpl(stickerSets.get(newPosition).getTitle(), searchRequest) || filterImpl(stickerSets.get(oldPosition).getTitle(), searchRequest)) {
          int oldFoundPosition = Math.min(indexOfFoundStickerSet(oldPosition), foundStickerSets.size() - 1);
          int newFoundPosition = Math.min(indexOfFoundStickerSet(newPosition), foundStickerSets.size() - 1);
          if (oldFoundPosition != newFoundPosition) {
            realOldIndex = oldFoundPosition;
            realNewIndex = newFoundPosition;
            ArrayUtils.move(foundStickerSets, oldFoundPosition, newFoundPosition);
          } else {
            needUpdateAdapter = false;
          }
        } else {
          needUpdateAdapter = false;
        }
      }

      if (!needUpdateAdapter) {
        return;
      }

      int positionOld = itemsStartIndex + realOldIndex;
      int positionNew = itemsStartIndex + realNewIndex;
      if (itemsBreakIndex != -1 && positionOld > itemsBreakIndex) {
        positionOld += itemsBreakSize;
      }
      if (itemsBreakIndex != -1 && positionNew > itemsBreakIndex) {
        positionNew += itemsBreakSize;
      }

      int firstVisiblePosition = ((LinearLayoutManager) getRecyclerView().getLayoutManager()).findFirstVisibleItemPosition();
      View topView = getRecyclerView().getLayoutManager().findViewByPosition(firstVisiblePosition);
      int offset = topView != null ? topView.getTop() : 0;
      adapter.moveItem(positionOld, positionNew, true);
      ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(firstVisiblePosition, offset);
    }

    private void updateEmojiPacksCount () {
      if (mode == MODE_MASKS) return;
      ArrayList<TGStickerSetInfo> stickerSets = getActualStickerSetsList();

      int i = adapter.indexOfViewById(R.id.view_emojiPacksCount);
      if (i != -1) {
        ListItem item = adapter.getItems().get(i);
        boolean changed = item.setStringIfChanged(Lang.pluralBold(R.string.xEmojiPacks, stickerSets != null ? stickerSets.size() : 0));
        if (changed) {
          adapter.notifyItemChanged(i);
        }
      }
    }

    private int indexOfFoundStickerSet (int index) {
      if (stickerSets == null) return -1;
      int indexToInsert = 0;
      for (int a = 0; a < index; a++) {
        TGStickerSetInfo info2 = stickerSets.get(a);
        if (filterImpl(info2.getTitle(), searchRequest)) {
          indexToInsert += 1;
        }
      }
      return indexToInsert;
    }

    public int getIndexFromPosition (int position) {
      ArrayList<TGStickerSetInfo> stickerSets = getActualStickerSetsList();

      if (stickerSets == null || itemsStartIndex == -1) return -1;
      position -= itemsStartIndex;

      if (itemsBreakIndex != -1 && position >= itemsBreakIndex) {
        position -= itemsBreakSize;
      }

      if (position >= 0 && position < stickerSets.size()) {
        return position;
      }
      return -1;
    }

    @Nullable
    private TGStickerSetInfo getStickerSetByPosition (int position) {
      ArrayList<TGStickerSetInfo> stickerSets = getActualStickerSetsList();
      int index = getIndexFromPosition(position);

      return index != -1 && stickerSets != null ? stickerSets.get(index) : null;
    }

    private boolean inSearchMode () {
      return !StringUtils.isEmpty(searchRequest);
    }

    @Nullable
    private ArrayList<TGStickerSetInfo> getActualStickerSetsList () {
      return inSearchMode() ? foundStickerSets : stickerSets;
    }

    @Nullable
    private TGStickerSetInfo findStickerSetById (long stickerSetId) {
      ArrayList<TGStickerSetInfo> stickerSets = getActualStickerSetsList();
      if (stickerSets != null && !stickerSets.isEmpty()) {
        for (TGStickerSetInfo info : stickerSets) {
          if (info.getId() == stickerSetId) {
            return info;
          }
        }
      }
      return null;
    }

    private int getInstalledSetsInDatasetCount () {
      if (stickerSets == null) {
        return 0;
      }
      for (int a = 0; a < stickerSets.size(); a++) {
        if (!stickerSets.get(a).isInstalled()) {
          return a;
        }
      }
      return stickerSets.size();
    }

    private int indexOfStickerSetById (@Nullable ArrayList<TGStickerSetInfo> stickerSets, long id) {
      if (stickerSets == null) {
        return -1;
      }
      int i = 0;
      for (TGStickerSetInfo info : stickerSets) {
        if (info.getId() == id) {
          return i;
        }
        i++;
      }
      return -1;
    }

    @NonNull
    private ArrayList<TGStickerSetInfo> filter (@Nullable  ArrayList<TGStickerSetInfo> stickerSets, @Nullable String request) {
      if (StringUtils.isEmpty(request) || stickerSets == null) {
        return new ArrayList<>();
      }
      ArrayList<TGStickerSetInfo> results = new ArrayList<>(stickerSets.size());
      for (TGStickerSetInfo info : stickerSets) {
        if (filterImpl(info.getTitle(), request)) {
          results.add(info);
        }
      }

      return results;
    }

    private boolean filterImpl (String title, String request) {
      return Highlight.isExactMatch(Highlight.valueOf(title, request));
    }

    /* States */

    private static final int STATE_NONE = 0;
    private static final int STATE_IN_PROGRESS = 1;
    private static final int STATE_DONE = 2;

    private LongSparseIntArray currentStates;

    private int getState (TGStickerSetInfo stickerSet) {
      return currentStates != null ? currentStates.get(stickerSet.getId(), STATE_NONE) : STATE_NONE;
    }

    private void setState (long setId, int state) {
      if (currentStates == null) {
        currentStates = new LongSparseIntArray();
      }
      currentStates.put(setId, state);
      adapter.updateStickerSetById(setId);
    }

    /* ? */

    private void changeStickerSets (long[] stickerSetIds) {
      if (mode == MODE_STICKERS_ARCHIVED) {
        for (long stickerSetId : stickerSetIds) {
          removeStickerSet(stickerSetId);
        }
        return;
      }

      if (applyingChanges) {
        if (pendingChanges == null) {
          pendingChanges = new ArrayList<>();
        }
        pendingChanges.add(stickerSetIds);
        return;
      }

      if ((stickerSets == null || stickerSets.isEmpty()) && mode != MODE_MASKS) {
        loadData(false);
        return;
      }

      final LongSparseArray<TGStickerSetInfo> removedStickerSets = new LongSparseArray<>();
      for (TGStickerSetInfo stickerSet : stickerSets) {
        if (mode != MODE_MASKS || !stickerSet.isArchived()) {
          removedStickerSets.put(stickerSet.getId(), stickerSet);
        }
      }

      LongSparseArray<Integer> positions = null;
      LongSparseArray<Integer> allItems = new LongSparseArray<>(stickerSetIds.length);
      LongList futureItems = null;

      int setIndex = 0;
      int totalIndex = 0;
      int lastAddedIndex = -1;
      boolean reloadAfterLocalChanges = false;
      for (long setId : stickerSetIds) {
        TGStickerSetInfo currentSet = removedStickerSets.get(setId);

        if (currentSet == null) {
          if (!reloadAfterLocalChanges) {
            if (totalIndex != ++lastAddedIndex) {
              reloadAfterLocalChanges = true;
            } else {
              if (futureItems == null) {
                futureItems = new LongList(5);
              }
              futureItems.append(setId);
            }
          }
        } else {
          removedStickerSets.remove(setId);

          if (positions == null) {
            positions = new LongSparseArray<>(5);
          }

          positions.put(setId, setIndex);
          setIndex++;
        }

        allItems.put(setId, totalIndex);
        totalIndex++;
      }

      // First, remove items
      final int removedCount = removedStickerSets.size();
      for (int i = 0; i < removedCount; i++) {
        TGStickerSetInfo stickerSet = removedStickerSets.valueAt(i);
        removeStickerSet(stickerSet.getId());
      }

      // Then, move items
      if (positions != null && !stickerSets.isEmpty()) {
        for (int j = 0; j < positions.size(); j++) {
          long setId = positions.keyAt(j);
          int newPosition = positions.valueAt(j);
          int currentPosition = indexOfStickerSetById(stickerSets, setId);
          if (currentPosition == -1) {
            throw new RuntimeException();
          }
          if (currentPosition != newPosition) {
            moveStickerSet(currentPosition, newPosition);
          }
        }
      }

      if (reloadAfterLocalChanges) {
        loadData(false);
        return;
      }

      if (futureItems != null) {
        setApplyingChanges(true);
        final long[] setIds = futureItems.get();
        final int addedCount = futureItems.size();
        final int[] index = new int[2];
        tdlib.client().send(new TdApi.GetStickerSet(setIds[index[0]]), new Client.ResultHandler() {
          @Override
          public void onResult (TdApi.Object object) {
            if (object.getConstructor() == TdApi.StickerSet.CONSTRUCTOR) {
              final TGStickerSetInfo stickerSet = new TGStickerSetInfo(tdlib, (TdApi.StickerSet) object);
              final int insertIndex = index[1]++;

              tdlib.ui().post(() -> {
                if (!context.isDestroyed()) {
                  addStickerSet(stickerSet, insertIndex);
                }
              });
            }

            if (++index[0] < addedCount) {
              tdlib.client().send(new TdApi.GetStickerSet(setIds[index[0]]), this);
            } else {
              tdlib.ui().post(() -> {
                if (!context.isDestroyed()) {
                  setApplyingChanges(false);
                }
              });
            }
          }
        });
      }
    }

    private boolean applyingChanges;
    private ArrayList<long[]> pendingChanges;

    private void setApplyingChanges (boolean applyingChanges) {
      if (this.applyingChanges != applyingChanges) {
        this.applyingChanges = applyingChanges;
        if (!applyingChanges && pendingChanges != null && !pendingChanges.isEmpty()) {
          do {
            long[] stickerSetIds = pendingChanges.remove(0);
            changeStickerSets(stickerSetIds);
          } while (!pendingChanges.isEmpty() && !this.applyingChanges);
        }
      }
    }

    private void saveStickersOrder () {
      if (stickerSets == null || stickerSets.isEmpty()) {
        return;
      }

      long[] stickerSetIds = new long[stickerSets.size()];
      int i = 0;
      for (TGStickerSetInfo info : stickerSets) {
        stickerSetIds[i++] = info.getId();
      }
      tdlib.client().send(new TdApi.ReorderInstalledStickerSets(getStickerType(mode, isEmoji), stickerSetIds), tdlib.okHandler());
    }
  }
}
