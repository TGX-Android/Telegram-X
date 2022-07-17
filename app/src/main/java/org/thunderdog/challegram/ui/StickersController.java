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
 * File created on 17/03/2016 at 01:05
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.sticker.StickerSetWrap;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoubleTextView;
import org.thunderdog.challegram.widget.NonMaterialButton;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSparseIntArray;

public class StickersController extends RecyclerViewController<StickersController.Args> implements Client.ResultHandler, View.OnClickListener, StickersListener {
  public static final int MODE_STICKERS = 0;
  public static final int MODE_STICKERS_ARCHIVED = 1;
  public static final int MODE_MASKS = 2;

  public static class Args {
    public final int mode;
    public final boolean doNotLoad;

    public @Nullable ArrayList<TGStickerSetInfo> stickerSets;

    public Args (int mode, boolean doNotLoad) {
      this.mode = mode;
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
  private boolean doNotLoad;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.doNotLoad = args.doNotLoad;
    this.stickerSets = args.stickerSets;
  }

  private SettingsAdapter adapter;

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

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setStickerSet (ListItem item, int position, DoubleTextView group, boolean isArchived, boolean isUpdate) {
        TGStickerSetInfo stickerSet;
        if (isArchived && archivedSets != null) {
          stickerSet = archivedSets.get(position - getArchivedStartIndex());
        } else if (stickerSets != null) {
          stickerSet = stickerSets.get(position - getStartIndex());
        } else {
          return;
        }
        group.setStickerSet(stickerSet);
        if (isArchived) {
          NonMaterialButton button = group.getButton();
          if (button != null) {
            int state = getState(stickerSet);
            button.setInProgress(state == STATE_IN_PROGRESS, isUpdate);
            button.setIsDone(state == STATE_DONE, isUpdate);
          }
        }
      }
    };

    if (mode == MODE_STICKERS || mode == MODE_MASKS) {
      if (mode == MODE_STICKERS) {
        recyclerView.setItemAnimator(null);
      }
      ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
        @Override
        public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
          if (stickerSets == null) {
            return 0;
          }
          int position = viewHolder.getAdapterPosition();
          if (position != -1 && position >= getStartIndex() && stickerSets != null && position < getStartIndex() + stickerSets.size()) {
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
        public void onMoved (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
          super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
          viewHolder.itemView.invalidate();
          target.itemView.invalidate();
        }

        @Override
        public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
          int fromPosition = viewHolder.getAdapterPosition();
          int toPosition = target.getAdapterPosition();

          if (stickerSets != null && fromPosition >= getStartIndex() && fromPosition < getStartIndex() + stickerSets.size() && toPosition >= getStartIndex() && toPosition < getStartIndex() + stickerSets.size()) {
            moveStickerSet(fromPosition - getStartIndex(), toPosition - getStartIndex());

            if (dragFrom == -1) {
              dragFrom = fromPosition;
            }
            dragTo = toPosition;

            return true;
          }

          return false;
        }

        private void reallyMoved (int from, int to) {
          saveStickersOrder();
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
          super.clearView(recyclerView, viewHolder);

          if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
            reallyMoved(dragFrom, dragTo);
          }

          dragFrom = dragTo = -1;
        }

        @Override
        public void onSwiped (RecyclerView.ViewHolder viewHolder, int direction) {

        }
      });
      helper.attachToRecyclerView(recyclerView);
    }

    if (mode == MODE_STICKERS_ARCHIVED) {
      recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          if (!isLoading && ((stickerSets != null && !stickerSets.isEmpty()) || (archivedSets != null && !archivedSets.isEmpty()))) {
            int position = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
            if (position != RecyclerView.NO_POSITION) {
              position += 10;
              if (position >= adapter.getItemCount() - 1) {
                loadData(true);
              }
            }
          }
        }
      });
    }

    if (stickerSets != null) {
      buildCells();
    } else if (!doNotLoad) {
      loadData(false);
    }

    if (mode == MODE_STICKERS || mode == MODE_MASKS || mode == MODE_STICKERS_ARCHIVED) {
      tdlib.listeners().subscribeToStickerUpdates(this);
    }

    recyclerView.setAdapter(adapter);
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
    tdlib.client().send(new TdApi.ReorderInstalledStickerSets(mode == MODE_MASKS, stickerSetIds), tdlib.okHandler());
  }

  private boolean parentFocusApplied;

  public void onParentFocus () {
    if (!parentFocusApplied) {
      parentFocusApplied = true;
      getRecyclerView().setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
  }

  @Override
  public void onInstalledStickerSetsUpdated (final long[] stickerSetIds, boolean isMasks) {
    if ((mode == MODE_MASKS && isMasks) || (mode == MODE_STICKERS && !isMasks)/* || (mode == MODE_STICKERS_ARCHIVED && !isMasks)*/) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && !isLoading && stickerSets != null) {
          changeStickerSets(stickerSetIds);
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
        if (!isDestroyed() && !isLoading && stickerSets != null) {
          if (mode == MODE_MASKS) {
            addArchivedMasks(stickerSet);
          } else {
            addArchivedSet(stickerSet);
          }
        }
      });
    }
  }

  @Override
  public void onStickerSetRemoved (TdApi.StickerSetInfo stickerSet) {
    if ((mode == MODE_MASKS && isMasks(stickerSet.stickerType)) || (mode == MODE_STICKERS_ARCHIVED && !isMasks(stickerSet.stickerType))) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && !isLoading && stickerSets != null) {
          if (mode == MODE_MASKS) {
            // TODO?
          } else {
            removeStickerSet(stickerSet.id);
          }
        }
      });
    }
  }

  @Override
  public void onStickerSetInstalled (TdApi.StickerSetInfo stickerSet) {
    if ((mode == MODE_MASKS && isMasks(stickerSet.stickerType)) || (mode == MODE_STICKERS_ARCHIVED && !isMasks(stickerSet.stickerType))) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && !isLoading && stickerSets != null) {
          if (mode == MODE_MASKS) {
            // TODO?
          } else {
            removeStickerSet(stickerSet.id);
          }
        }
      });
    }
  }

  private boolean isLoading, isLoadingMore, endReached;

  private void loadData (boolean isMore) {
    if (!isLoading) {
      if (isMore && endReached) {
        return;
      }
      isLoading = true;
      isLoadingMore = isMore;
      switch (mode) {
        case MODE_STICKERS: {
          if (!isMore) {
            tdlib.client().send(new TdApi.GetInstalledStickerSets(false), this);
          }
          break;
        }
        case MODE_STICKERS_ARCHIVED: {
          long offsetStickerSetId;
          int limit;
          if (isMore) {
            if (stickerSets == null || stickerSets.isEmpty()) {
              return;
            }
            offsetStickerSetId = stickerSets.get(stickerSets.size() - 1).getId();
            limit = 100;
          } else {
            offsetStickerSetId = 0;
            limit = Screen.calculateLoadingItems(Screen.dp(72f), 20);
          }
          tdlib.client().send(new TdApi.GetArchivedStickerSets(false, offsetStickerSetId, limit), this);
          break;
        }
        case MODE_MASKS: {
          if (!isMore) {
            tdlib.client().send(new TdApi.GetInstalledStickerSets(true), this);
          }
          break;
        }
      }
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_stickerSetInfo: {
        ListItem item = (ListItem) v.getTag();
        TGStickerSetInfo info = findStickerSetById(item.getLongId());
        if (info != null) {
          if (mode == MODE_STICKERS_ARCHIVED && currentStates != null && currentStates.get(info.getId(), STATE_NONE) == STATE_DONE) {
            return;
          }
          StickerSetWrap.showStickerSet(this, info.getInfo()).setIsOneShot();
        }
        break;
      }
      case R.id.btn_double: {
        ListItem item = (ListItem) ((ViewGroup) v.getParent()).getTag();
        final TGStickerSetInfo info = findStickerSetById(item.getLongId());
        if (info != null) {
          int state = getState(info);
          if (state == STATE_NONE) {
            setState(info.getId(), STATE_IN_PROGRESS);
            tdlib.client().send(new TdApi.ChangeStickerSet(info.getId(), true, false), object -> tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                boolean isOk = object.getConstructor() == TdApi.Ok.CONSTRUCTOR;
                if (isOk) {
                  info.setIsInstalled();
                }
                setState(info.getId(), isOk ? STATE_DONE : STATE_NONE);
                if (isOk) {
                  if (mode == MODE_STICKERS_ARCHIVED) {
                    UI.post(() -> removeArchivedStickerSet(info), 1500);
                  } else if (currentStates != null) {
                    currentStates.delete(info.getId());
                  }
                }
              }
            }));
          }
        }
        break;
      }
    }
  }

  private @Nullable TGStickerSetInfo findStickerSetById (long stickerSetId) {
    if (stickerSets != null && !stickerSets.isEmpty()) {
      for (TGStickerSetInfo info : stickerSets) {
        if (info.getId() == stickerSetId) {
          return info;
        }
      }
    }
    if (archivedSets != null && !archivedSets.isEmpty()) {
      for (TGStickerSetInfo info : archivedSets) {
        if (info.getId() == stickerSetId) {
          return info;
        }
      }
    }
    return null;
  }

  private @Nullable ArrayList<TGStickerSetInfo> stickerSets;
  private @Nullable ArrayList<TGStickerSetInfo> archivedSets;

  @UiThread
  public void addMoreStickerSets (ArrayList<TGStickerSetInfo> stickerSets, @Nullable ArrayList<TGStickerSetInfo> archivedSets) {
    switch (mode) {
      case MODE_STICKERS_ARCHIVED: {
        if (this.stickerSets == null || this.stickerSets.isEmpty() || stickerSets.isEmpty()) {
          return;
        }
        this.stickerSets.addAll(stickerSets);
        List<ListItem> items = adapter.getItems();
        int startIndex = items.size() - 1;
        ListItem shadow = items.remove(startIndex);
        for (TGStickerSetInfo info : stickerSets) {
          info.setBoundList(this.stickerSets);
          items.add(new ListItem(ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()));
        }
        items.add(shadow);
        adapter.notifyItemRangeInserted(startIndex, stickerSets.size());
        break;
      }
    }
  }

  @UiThread
  public void setStickerSets (ArrayList<TGStickerSetInfo> stickerSets, @Nullable ArrayList<TGStickerSetInfo> archivedSets) {
    this.stickerSets = stickerSets;
    this.archivedSets = archivedSets;
    buildCells();
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>(Math.max(0, stickerSets != null ? stickerSets.size() * 2 - 1 : 0));

    if (!stickerSets.isEmpty() || (archivedSets != null && !archivedSets.isEmpty())) {
      if (mode == MODE_STICKERS_ARCHIVED) {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.ArchivedStickersInfo, Strings.buildCounter(tdlib.getInstalledStickerSetLimit())), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      } else if (mode == MODE_MASKS) {
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MasksHint));

        if (!stickerSets.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        }
      }

      if (mode == MODE_STICKERS_ARCHIVED) {
        for (TGStickerSetInfo info : stickerSets) {
          items.add(new ListItem(ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()));
        }
      } else {
        for (TGStickerSetInfo info : stickerSets) {
          items.add(new ListItem(ListItem.TYPE_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()));
        }
      }
      if (!stickerSets.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      if (mode == MODE_MASKS && archivedSets != null && !archivedSets.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Archived));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        for (TGStickerSetInfo info : archivedSets) {
          items.add(new ListItem(ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }

      if (mode == MODE_STICKERS) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.ArchivedStickersInfo, Strings.buildCounter(tdlib.getInstalledStickerSetLimit())), false));
      }
    } else if (mode == MODE_STICKERS_ARCHIVED) {
      items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, Lang.getString(R.string.ArchivedStickersInfo, Strings.buildCounter(tdlib.getInstalledStickerSetLimit())), false));
    } else {
      items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, mode == MODE_STICKERS ? R.string.NoStickerSets : R.string.NoMasks));
    }

    adapter.setItems(items, false);
  }

  private void removeArchivedStickerSet (TGStickerSetInfo info) {
    if (stickerSets == null) {
      return;
    }
    if (currentStates != null) {
      currentStates.delete(info.getId());
    }
    int i = indexOfStickerSet(info.getId());
    if (i != -1) {
      stickerSets.remove(i);
      if (stickerSets.size() == 0) {
        buildCells();
      } else {
        adapter.getItems().remove(3 + i);
        adapter.notifyItemRemoved(3 + i);
      }
    }
  }

  private void addArchivedSet (TdApi.StickerSetInfo rawInfo) {
    if (stickerSets == null) {
      return;
    }
    TdApi.StickerSetInfo newRawInfo = new TdApi.StickerSetInfo(rawInfo.id, rawInfo.title, rawInfo.title, rawInfo.thumbnail, rawInfo.thumbnailOutline, rawInfo.isInstalled, true, rawInfo.isOfficial, rawInfo.stickerType, rawInfo.isViewed, rawInfo.size, rawInfo.covers);
    TGStickerSetInfo info = new TGStickerSetInfo(tdlib, newRawInfo);
    info.setBoundList(stickerSets);
    stickerSets.add(0, info);

    if (stickerSets.size() == 1) {
      buildCells();
    } else {
      adapter.getItems().add(3, new ListItem(ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId()));
      adapter.notifyItemInserted(3);
    }
  }

  private void addArchivedMasks (TdApi.StickerSetInfo rawInfo) {
    if (archivedSets == null) {
      archivedSets = new ArrayList<>();
    } else {
      for (TGStickerSetInfo prevInfo : archivedSets) {
        if (prevInfo.getId() == rawInfo.id) {
          return;
        }
      }
    }

    TdApi.StickerSetInfo newRawInfo = new TdApi.StickerSetInfo(rawInfo.id, rawInfo.title, rawInfo.title, rawInfo.thumbnail, rawInfo.thumbnailOutline, true, true, rawInfo.isOfficial, rawInfo.stickerType, rawInfo.isViewed, rawInfo.size, rawInfo.covers);
    TGStickerSetInfo info = new TGStickerSetInfo(tdlib, newRawInfo);
    info.setBoundList(archivedSets);

    int startIndex = getArchivedStartIndex();

    ListItem item = new ListItem(ListItem.TYPE_ARCHIVED_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(info.getId());
    archivedSets.add(0, info);

    if (archivedSets.size() == 1) {
      int index = adapter.getItems().size();
      adapter.getItems().add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Archived));
      adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      adapter.getItems().add(item);
      adapter.getItems().add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.notifyItemRangeInserted(index, 4);
    } else {
      adapter.getItems().add(startIndex, item);
      adapter.notifyItemInserted(startIndex);
    }
  }

  @Override
  public void onResult (final TdApi.Object object) {
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
          tdlib.client().send(new TdApi.GetArchivedStickerSets(true, 0, 100), object1 -> {
            final ArrayList<TGStickerSetInfo> archivedSets;

            if (object1.getConstructor() == TdApi.StickerSets.CONSTRUCTOR && ((TdApi.StickerSets) object1).sets.length > 0) {
              TdApi.StickerSetInfo[] stickerSets1 = ((TdApi.StickerSets) object1).sets;
              archivedSets = new ArrayList<>(stickerSets1.length);
              for (TdApi.StickerSetInfo stickerSet : stickerSets1) {
                TGStickerSetInfo info = new TGStickerSetInfo(tdlib, stickerSet);
                info.setBoundList(archivedSets);
                archivedSets.add(info);
              }
            } else {
              archivedSets = null;
            }

            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                isLoading = false;
                if (isLoadingMore) {
                  addMoreStickerSets(stickerSets, archivedSets);
                } else {
                  setStickerSets(stickerSets, archivedSets);
                }
              }
            });
          });
        } else {
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              isLoading = false;
              if (isLoadingMore) {
                addMoreStickerSets(stickerSets, null);
              } else {
                setStickerSets(stickerSets, null);
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

  private int indexOfStickerSet (long setId) {
    if (stickerSets == null) {
      return -1;
    }
    int i = 0;
    for (TGStickerSetInfo info : stickerSets) {
      if (info.getId() == setId) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private int getStartIndex () {
    return stickerSets == null || mode == MODE_STICKERS ? 0 : stickerSets.isEmpty() ? 1 : 3;
  }

  private int getArchivedStartIndex () {
    return getStartIndex() + (stickerSets != null ? stickerSets.size() + 3 : 1);
  }

  private void moveStickerSet (int oldPosition, int newPosition) {
    if (oldPosition == newPosition || stickerSets == null) {
      return;
    }

    ArrayUtils.move(stickerSets, oldPosition, newPosition);

    oldPosition += getStartIndex();
    newPosition += getStartIndex();

    int firstVisiblePosition = ((LinearLayoutManager) getRecyclerView().getLayoutManager()).findFirstVisibleItemPosition();
    View topView = getRecyclerView().getLayoutManager().findViewByPosition(firstVisiblePosition);
    int offset = topView != null ? topView.getTop() : 0;

    adapter.moveItem(oldPosition, newPosition, true);

    ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(firstVisiblePosition, offset);
  }

  private void addStickerSet (TGStickerSetInfo stickerSet, int index) {
    if (stickerSets == null) {
      return;
    }
    stickerSet.setBoundList(stickerSets);
    stickerSets.add(index, stickerSet);

    int firstVisiblePosition = ((LinearLayoutManager) getRecyclerView().getLayoutManager()).findFirstVisibleItemPosition();
    View topView = getRecyclerView().getLayoutManager().findViewByPosition(firstVisiblePosition);
    int offset = topView != null ? topView.getTop() : 0;

    ListItem item = new ListItem(ListItem.TYPE_STICKER_SET, R.id.btn_stickerSetInfo, 0, 0).setLongId(stickerSet.getId());

    if (stickerSets.size() == 1 && mode != MODE_STICKERS) {
      index += 2;
      adapter.getItems().add(index, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.getItems().add(index, item);
      adapter.getItems().add(index, new ListItem(ListItem.TYPE_SHADOW_TOP));
      adapter.notifyItemRangeInserted(index, 3);
    } else {
      index += getStartIndex();
      adapter.getItems().add(index, item);
      adapter.notifyItemInserted(index);
    }

    ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(firstVisiblePosition, offset);
  }

  private void removeStickerSet (long setId) {
    int i = indexOfStickerSet(setId);
    if (i != -1) {
      removeStickerSetByPosition(i);
    }
  }

  private void removeStickerSetByPosition (int i) {
    if (stickerSets == null) {
      return;
    }

    stickerSets.remove(i);

    if (stickerSets.isEmpty()) {
      if (mode == MODE_MASKS) {
        i += 2;
        adapter.removeRange(i, 3);
      } else {
        buildCells();
      }
    } else {
      i += getStartIndex();
      adapter.getItems().remove(i);
      adapter.notifyItemRemoved(i);
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

  private void removeArchivedSet (int index) {
    if (archivedSets == null || archivedSets.isEmpty()) {
      return;
    }

    TGStickerSetInfo info = archivedSets.remove(index);
    if (currentStates != null) {
      currentStates.delete(info.getId());
    }
    if (archivedSets.isEmpty()) {
      adapter.removeRange(getArchivedStartIndex() - 2, 4);
    } else {
      adapter.removeRange(getArchivedStartIndex() + index, 1);
    }
  }

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
      removedStickerSets.put(stickerSet.getId(), stickerSet);
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

    if (archivedSets != null && !archivedSets.isEmpty()) {
      final int size = archivedSets.size();
      for (int i = size - 1; i >= 0; i--) {
        if (allItems.get(archivedSets.get(i).getId(), -1) != -1) {
          removeArchivedSet(i);
        }
      }
    }

    // First, remove items
    final int removedCount = removedStickerSets.size();
    for (int i = 0; i < removedCount; i++) {
      TGStickerSetInfo stickerSet = removedStickerSets.valueAt(i);
      removeStickerSet(stickerSet.getId());
    }

    // Then, move items
    if (positions != null && !stickerSets.isEmpty() ) {
      for (int j = 0; j < positions.size(); j++) {
        long setId = positions.keyAt(j);
        int newPosition = positions.valueAt(j);
        int currentPosition = indexOfStickerSet(setId);
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
              if (!isDestroyed()) {
                addStickerSet(stickerSet, insertIndex);
              }
            });
          }

          if (++index[0] < addedCount) {
            tdlib.client().send(new TdApi.GetStickerSet(setIds[index[0]]), this);
          } else {
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                setApplyingChanges(false);
              }
            });
          }
        }
      });
    }
  }
}
