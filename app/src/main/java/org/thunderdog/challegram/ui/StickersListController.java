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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerPreviewView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.RtlGridLayoutManager;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class StickersListController extends ViewController<StickersListController.StickerSetProvider> implements
  Menu, StickerSmallView.StickerMovementCallback, Client.ResultHandler,
  MoreDelegate, StickersListener, StickerPreviewView.MenuStickerPreviewCallback {
  public StickersListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public interface StickerSetProvider {
    boolean canArchiveStickerSet (long setId);
    boolean canRemoveStickerSet (long setId);
    boolean canInstallStickerSet (long setId);
    boolean canViewPack ();
    void archiveStickerSets (long[] setIds);
    void installStickerSets (long[] setIds);
    void removeStickerSets (long[] setIds);
    boolean onStickerClick (View view, TGStickerObj obj, boolean isMenuClick, TdApi.MessageSendOptions sendOptions);
    long getStickerOutputChatId ();
  }

  @Override
  public int getId () {
    return R.id.controller_stickerSet;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.headerLightIcon;
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (getArguments() == null) return;

    if (id == R.id.menu_btn_more) {
      IntList ids = new IntList(4);
      StringList strings = new StringList(4);
      IntList icons = new IntList(4);

      if (stickerSetInfoToLoad != null) {
        ids.append(R.id.btn_share);
        strings.append(R.string.Share);
        icons.append(R.drawable.baseline_forward_24);
      }

      ids.append(R.id.btn_copyLink);
      strings.append(R.string.CopyLink);
      icons.append(R.drawable.baseline_link_24);

      if (stickerSetInfoToLoad != null) {
        if (getArguments().canArchiveStickerSet(stickerSetInfoToLoad != null ? stickerSetInfoToLoad.id : -1)) {
          ids.append(R.id.btn_archive);
          strings.append(R.string.StickersHide);
          icons.append(R.drawable.baseline_archive_24);
        }
        if (getArguments().canRemoveStickerSet(stickerSetInfoToLoad != null ? stickerSetInfoToLoad.id : -1)) {
          ids.append(R.id.btn_delete);
          strings.append(R.string.DeleteArchivedPack);
          icons.append(R.drawable.baseline_delete_24);
        }
        showMore(ids.get(), strings.get(), icons.get(), 0);
      } else if (!stickerSections.isEmpty() ) {
        int setsToInstall = 0;
        int setsToArchive = 0;

        for (StickerSection section : stickerSections) {
          if (section.info == null) continue;
          if (getArguments().canArchiveStickerSet(section.info.getId())) {
            setsToArchive += 1;
          }
          if (getArguments().canInstallStickerSet(section.info.getId())) {
            setsToInstall += 1;
          }
        }
        if (setsToInstall > 0) {
          ids.append(R.id.btn_installStickerSet);
          strings.append(Lang.plural(R.string.xStickersInstall, setsToInstall));
          icons.append(R.drawable.deproko_baseline_stickers_24);
        }
        if (setsToArchive > 0) {
          ids.append(R.id.btn_archive);
          strings.append(Lang.plural(R.string.xStickersHide, setsToArchive));
          icons.append(R.drawable.baseline_archive_24);
        }
        showMore(ids.get(), strings.get(), icons.get(), 0);
      }
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    if (id == R.id.btn_share) {
      tdlib.ui().shareStickerSetUrl(this, stickerSetInfoToLoad);
    } else if (id == R.id.btn_copyLink) {
      if (stickerSetInfoToLoad != null) {
        UI.copyText(tdlib.tMeStickerSetUrl(stickerSetInfoToLoad), R.string.CopiedLink);
      } else {
        StringBuilder b = new StringBuilder();
        for (StickerSection section : stickerSections) {
          TdApi.StickerSetInfo stickerSetInfo = section.info != null ? section.info.getInfo() : null;
          if (stickerSetInfo == null) {
            continue;
          }
          if (b.length() != 0) {
            b.append('\n');
          }
          b.append(tdlib.tMeStickerSetUrl(stickerSetInfo));
        }
        UI.copyText(b.toString(), R.string.CopiedLink);
      }
    } else if (id == R.id.btn_archive) {
      if (getArguments() != null) {
        if (stickerSetInfoToLoad != null) {
          getArguments().archiveStickerSets(new long[] {stickerSetInfoToLoad.id});
        } else {
          LongList stickerSetsToArchive = new LongList(stickerSections.size());
          for (StickerSection section : stickerSections) {
            if (section.info == null) continue;
            long setId = section.info.getId();
            if (getArguments().canArchiveStickerSet(setId)) {
              stickerSetsToArchive.append(setId);
            }
          }
          getArguments().archiveStickerSets(stickerSetsToArchive.get());
        }
      }
    } else if (id == R.id.btn_delete) {
      if (getArguments() != null) {
        long stickerSetId = stickerSetInfoToLoad != null ? stickerSetInfoToLoad.id : -1;
        getArguments().removeStickerSets(new long[] {stickerSetId});
      }
    } else if (id == R.id.btn_installStickerSet) {
      if (getArguments() != null) {
        if (stickerSetInfoToLoad != null) {
          getArguments().installStickerSets(new long[] {stickerSetInfoToLoad.id});
        } else {
          LongList stickerSetsToInstall = new LongList(stickerSections.size());
          for (StickerSection section : stickerSections) {
            if (section.info == null) continue;
            final long setId = section.info.getId();
            if (getArguments().canInstallStickerSet(setId)) {
              stickerSetsToInstall.append(setId);
            }
          }
          getArguments().installStickerSets(stickerSetsToInstall.get());
        }
      }
    }
  }

  @Override
  public CharSequence getName () {
    if (stickerSetInfoToLoad != null) {
      TdApi.StickerSetInfo info = stickerSetInfoToLoad;
      TdApi.TextEntity[] entities = Td.findEntities(info.title);
      return TD.formatString(this, info.title, entities, null, null);
    }
    if (stickerSections.size() > 1) {
      return Lang.plural(R.string.xEmojiPacks, stickerSections.size());
    }
    if (stickerSetIdsToLoad != null && stickerSetIdsToLoad.length > 1) {
      return Lang.plural(R.string.xEmojiPacks, stickerSetIdsToLoad.length);
    }
    return null;
  }

  private MediaStickersAdapter.OffsetProvider offsetProvider;



  public void setOffsetProvider (MediaStickersAdapter.OffsetProvider provider) {
    this.offsetProvider = provider;
  }

  public void setStickerSetInfo (TdApi.StickerSetInfo info) {
    this.stickerSetInfoToLoad = info;
    this.isEmojiPack = info.stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR;
  }

  public void setIsEmojiPack (boolean isEmojiPack) {
    this.isEmojiPack = isEmojiPack;
  }

  public void setStickerSets (long[] stickerSetIds) {
    this.stickerSetIdsToLoad = stickerSetIds;
  }

  public void setStickers (TdApi.Sticker[] stickers, TdApi.StickerType stickerType, TdApi.Emojis[] emojis) {
    boolean canViewPack = getArguments() == null || getArguments().canViewPack();
    this.stickerSections.clear();
    this.stickerSections.add(new StickerSection(tdlib, stickers, stickerType, emojis, canViewPack));
  }

  private RecyclerView recyclerView;
  private MediaStickersAdapter adapter;
  private int spanCount;
  private int lastSpanCountWidth, lastSpanCountHeight;
  private GridLayoutManager manager;

  private void setSpanCount (int width, int height) {
    if (width == 0 || height == 0) {
      return;
    }
    if (lastSpanCountWidth != width || lastSpanCountHeight != height) {
      lastSpanCountWidth = width;
      lastSpanCountHeight = height;
      int newSpanCount = calculateSpanCount(width, height, isEmojiPack);
      if (newSpanCount != spanCount) {
        this.spanCount = newSpanCount;
        manager.setSpanCount(newSpanCount);
      }
    }
  }

  private int offsetScroll;
  private boolean isSeparate;

  @Override
  public void attachHeaderViewWithoutNavigation (HeaderView headerView) {
    super.attachHeaderViewWithoutNavigation(headerView);
    this.isSeparate = true;
  }

  @Override
  public boolean needsTempUpdates () {
    return isSeparate || super.needsTempUpdates();
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (isSeparate && headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  protected View onCreateView (Context context) {
    spanCount = calculateSpanCount(lastSpanCountWidth = Screen.currentWidth(), lastSpanCountHeight = Screen.currentHeight(), isEmojiPack);

    FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setSpanCount(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
      }
    };
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = Size.getHeaderPortraitSize();
    params.bottomMargin = Screen.dp(56f);

    recyclerView = new RecyclerView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
          int topEdge = offsetProvider.provideOffset() - offsetScroll;
          float y = e.getY() + Size.getHeaderPortraitSize();
          return y >= topEdge && super.onTouchEvent(e);
        } else {
          return super.onTouchEvent(e);
        }
      }
    };
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(manager = new RtlGridLayoutManager(context, spanCount).setAlignOnly(true));
    recyclerView.setAdapter(adapter = new MediaStickersAdapter(this, this, false, this, offsetProvider, true, null) {
      @Override
      protected void onToggleCollapseRecentStickers (TextView collapseView, TGStickerSetInfo recentSet) {
        int existingIndex = getStickerSetSectionIndexById(recentSet.getId());
        if (existingIndex == -1) return;

        StickerSection section = stickerSections.get(existingIndex);
        boolean needExpand = recentSet.isCollapsed();
        int endIndex = recentSet.getEndIndex();
        int visibleItemCount = recentSet.getItemCount();

        if (needExpand) {
          recentSet.setSize(recentSet.getFullSize());

          ArrayList<MediaStickersAdapter.StickerItem> stickers = section.toItems(true);
          for (int a = 0; a < visibleItemCount - 1; a++) {
            stickers.remove(0);
          }
          if (existingIndex != stickerSections.size() - 1) {
            stickers.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR));
          }

          shiftStickerSets(existingIndex, stickers.size() - 1);
          adapter.removeRange(endIndex - 1, 1);
          adapter.addRange(endIndex - 1, stickers);
        } else {
         // recentSet.setSize(spanCount * 2);
         // adapter.removeRange(recentSet.getEndIndex(), endIndex - recentSet.getEndIndex());
         // shiftStickerSets(existingIndex, recentSet.getEndIndex() - endIndex);
        }
      }

      @Override
      public void updateCollapseView (TextView collapseView, TGStickerSetInfo stickerSet, @StringRes int showMoreRes) {
        if (stickerSet != null && stickerSet.isCollapsed()) {       // ignore updates for expanded sets
          super.updateCollapseView(collapseView, stickerSet, showMoreRes);
        }
      }
    });
    recyclerView.setLayoutParams(params);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          offsetProvider.onScrollFinished();
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        int i = manager.findFirstVisibleItemPosition();
        View view = manager.findViewByPosition(i);
        if (view != null) {
          View stickerView = i == 1 ? view : manager.findViewByPosition(1);
          float shadowFactor = stickerView == null ? 1f : stickerView.getTop() >= 0 ? 0f : Math.max(0f, Math.min(1f, (float) -stickerView.getTop() / (float) Screen.dp(StickerSmallView.PADDING)));

          manager.findViewByPosition(1);

          offsetScroll = i > 0 ? offsetProvider.provideOffset() : -view.getTop();
          offsetProvider.onContentScroll(shadowFactor);
        }
      }
    });
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        int type = adapter.getItemViewType(position);
        return position == 0
          || type == MediaStickersAdapter.StickerHolder.TYPE_HEADER
          || type == MediaStickersAdapter.StickerHolder.TYPE_HEADER_COLLAPSABLE
          || type == MediaStickersAdapter.StickerHolder.TYPE_PROGRESS_OFFSETABLE
          || type == MediaStickersAdapter.StickerHolder.TYPE_HEADER_TRENDING
          || type == MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR_COLLAPSABLE
          || type == MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR ? spanCount : 1;
      }
    });
    adapter.setMenuStickerPreviewCallback(this);
    adapter.setRepaintingColorId(ColorId.text);
    adapter.setManager(manager);
    adapter.setIsBig();

    contentView.addView(recyclerView);

    buildCells(false);
    if (stickerSections.isEmpty()) {
      if (stickerSetInfoToLoad != null) {
        tdlib.client().send(new TdApi.GetStickerSet(stickerSetInfoToLoad.id), this);
      } else if (stickerSetIdsToLoad != null) {
        startLoadStickerSets();
      }
    }

    tdlib.listeners().subscribeToStickerUpdates(this);

    return contentView;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (recyclerView != null)
      recyclerView.requestLayout();
  }

  public int getOffsetScroll () {
    return offsetScroll;
  }

  private CancellableRunnable itemAnimatorRunnable;

  public void setItemAnimator () {
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180L));
  }

  private boolean isEmojiPack;

  private void shiftStickerSets (int startPosition, int offset) {
    for (int i = startPosition + 1; i < stickerSections.size(); i++) {
      TGStickerSetInfo stickerSet = stickerSections.get(i).info;
      stickerSet.setStartIndex(stickerSet.getStartIndex() + offset);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
    tdlib.listeners().unsubscribeFromStickerUpdates(this);
  }

  @Override
  public void onResult (final TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.StickerSet.CONSTRUCTOR: {
        TdApi.StickerSet stickerSet = (TdApi.StickerSet) object;
        setStickers(stickerSet.stickers, stickerSet.stickerType, stickerSet.emojis);

        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            buildCells(false);
          }
        });

        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            UI.showToast(TD.toErrorString(object), Toast.LENGTH_SHORT);
          }
        });
        break;
      }
    }
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    int i = indexOfSticker(sticker);
    if (i != -1) {
      final View childView = manager.findViewByPosition(i);
      if (childView != null && childView instanceof StickerSmallView) {
        ((StickerSmallView) childView).setStickerPressed(isPressed);
      } else {
        adapter.notifyItemChanged(i);
      }
    }
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return true;
  }

  @Override
  public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {

  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return true;
  }

  @Override
  public int getViewportHeight () {
    return -1;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    return getArguments() != null && getArgumentsStrict().onStickerClick(clickView, sticker, isMenuClick, sendOptions);
  }

  @Override
  public long getStickerOutputChatId () {
    return getArgumentsStrict().getStickerOutputChatId();
  }

  @Override
  public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {

  }

  @Override
  public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj sticker) {

  }

  @Override
  public int getStickersListTop () {
    return Size.getHeaderPortraitSize();
  }

  public void scrollBy (int y) {
    recyclerView.smoothScrollBy(0, y);
  }

  private static int calculateSpanCount (int width, int height, boolean isEmoji) {
    int minSide = Math.min(width, height);
    int minWidth = isEmoji ? Screen.dp(42) : (minSide / 4);
    return minWidth != 0 ? width / minWidth : 4;
  }

  public static int getEstimateColumnResolution () {
    int spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight(), false);
    return Screen.currentWidth() / spanCount;
  }

  public int indexOfSticker (TGStickerObj obj) {
    int i = 0;
    /*for (TGStickerObj sticker : adapter.stickers) {
      if (sticker.equals(obj)) {
        return i + 1;
      }
      i++;
    }*/
    return -1;
  }



  /* * */

  private boolean isInLoadSetsProgress;
  private int loadSetsKey = -1;
  private int loadSetsCounter;

  private @Nullable TdApi.StickerSetInfo stickerSetInfoToLoad;
  private @Nullable long[] stickerSetIdsToLoad;

  private RunnableData<ArrayList<TdApi.StickerSet>> loadStickerSetsListener;

  public void setLoadStickerSetsListener (RunnableData<ArrayList<TdApi.StickerSet>> loadStickerSetsListener) {
    this.loadStickerSetsListener = loadStickerSetsListener;
  }

  private void startLoadStickerSets () {
    if (isInLoadSetsProgress || stickerSetIdsToLoad == null) return;
    isInLoadSetsProgress = true;
    loadSetsCounter = stickerSetIdsToLoad.length;
    loadSetsKey += 1;

    final int currentKey = loadSetsKey;
    final long[] currentStickerSetIds = stickerSetIdsToLoad;
    final TdApi.StickerSet[] loadSetsResult = new TdApi.StickerSet[stickerSetIdsToLoad.length];
    for (long id : stickerSetIdsToLoad) {
      tdlib.send(new TdApi.GetStickerSet(id), (stickerSet, error) -> UI.post(() -> {
        if (currentKey != loadSetsKey || isDestroyed()) return;
        if (stickerSet != null) {
          int index = ArrayUtils.indexOf(currentStickerSetIds, stickerSet.id);
          if (index > -1 && index < loadSetsResult.length) {
            loadSetsResult[index] = stickerSet;
          }
        }
        loadSetsCounter -= 1;
        if (loadSetsCounter == 0) {
          stickerSections.clear();

          isInLoadSetsProgress = false;
          ArrayList<TdApi.StickerSet> sets = new ArrayList<>(loadSetsResult.length);
          for (TdApi.StickerSet set : loadSetsResult) {
            if (set != null) {
              sets.add(set);
              stickerSections.add(new StickerSection(tdlib, set, Math.max(16, spanCount * 2)));
            }
          }

          if (loadStickerSetsListener != null) {
            loadStickerSetsListener.runWithData(sets);
          }

          buildCells(true);
          if (headerView != null) {
            headerView.setTitle(this);
          }
        }
      }));
    }
  }



  /* Listeners */

  @Override // todo:: update sticker pack
  public void onStickerSetUpdated (TdApi.StickerSet stickerSet) { }

  @Override
  public void onInstalledStickerSetsUpdated (long[] stickerSetIds, TdApi.StickerType stickerType) {
    final LongSparseArray<TGStickerSetInfo> sets = new LongSparseArray<>(stickerSetIds.length);
    for (long setId : stickerSetIds) {
      sets.put(setId, null);
    }
    runOnUiThreadOptional(() -> {
      for (StickerSection stickerSection : stickerSections) {
        if (stickerSection.info == null) continue;

        int i = sets.indexOfKey(stickerSection.info.getId());
        if (i >= 0) {
          stickerSection.info.setIsInstalled();
        } else {
          stickerSection.info.setIsNotInstalled();
        }
        adapter.updateDone(stickerSection.info);
      }
    });
  }

  @Override
  public void onStickerSetArchived (TdApi.StickerSetInfo stickerSet) {
    final long stickerSetId = stickerSet.id;
    runOnUiThreadOptional(() -> {
      TGStickerSetInfo info = getStickerSetInfoById(stickerSetId);
      if (info != null) {
        info.setIsArchived();
        adapter.updateDone(info);
      }
    });
  }

  @Override
  public void onStickerSetRemoved (TdApi.StickerSetInfo stickerSet) {
    final long stickerSetId = stickerSet.id;
    runOnUiThreadOptional(() -> {
      TGStickerSetInfo info = getStickerSetInfoById(stickerSetId);
      if (info != null) {
        info.setIsNotInstalled();
        info.setIsNotArchived();
        adapter.updateDone(info);
      }
    });
  }

  @Override
  public void onStickerSetInstalled (TdApi.StickerSetInfo stickerSet) {
    final long stickerSetId = stickerSet.id;
    runOnUiThreadOptional(() -> {
      TGStickerSetInfo info = getStickerSetInfoById(stickerSetId);
      if (info != null) {
        info.setIsInstalled();
        adapter.updateDone(info);
      }
    });
  }


  /* Data */

  private final ArrayList<StickerSection> stickerSections = new ArrayList<>();

  private @Nullable TGStickerSetInfo getStickerSetInfoById (long id) {
    for (StickerSection stickerSection : stickerSections) {
      if (stickerSection.info != null && id == stickerSection.info.getId()) {
        return stickerSection.info;
      }
    }
    return null;
  }

  private void buildCells (boolean needInfo) {
    if (itemAnimatorRunnable != null) {
      itemAnimatorRunnable.cancel();
      itemAnimatorRunnable = null;
    }

    ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();
    items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PADDING_OFFSETABLE));
    if (stickerSections.isEmpty()) {
      items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PROGRESS_OFFSETABLE));
    } else {
      for (int a = 0; a < stickerSections.size(); a++) {
        StickerSection section = stickerSections.get(a);
        if (section.info != null && needInfo) {
          section.info.setStartIndex(items.size());
        }
        items.addAll(section.toItems(needInfo));
        if (a != stickerSections.size() - 1 && !(section.info != null && section.info.isCollapsableEmojiSet() && section.info.isCollapsed())) {
          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR));
        }
      }
    }

    adapter.setItems(items);
  }

  private int getStickerSetSectionIndexById (long id) {
    int i = 0;
    for (StickerSection section : stickerSections) {
      if (section.info != null && section.info.getId() == id) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private static class StickerSection {
    public @Nullable TGStickerSetInfo info;
    public ArrayList<TGStickerObj> stickers;

    public StickerSection (Tdlib tdlib, TdApi.StickerSet stickerSet, int trimToSize) {
      this.info = new TGStickerSetInfo(tdlib, Td.toStickerSetInfo(stickerSet), trimToSize);
      this.stickers = new ArrayList<>(stickerSet.stickers.length);

      for (TdApi.Sticker sticker : stickerSet.stickers) {
        stickers.add(new TGStickerObj(tdlib, sticker, "", sticker.fullType));
      }
    }

    public StickerSection (Tdlib tdlib, TdApi.Sticker[] stickers, TdApi.StickerType stickerType, TdApi.Emojis[] emojis, boolean canViewPack) {
      this.stickers = new ArrayList<>(stickers.length);
      int i = 0;
      for (TdApi.Sticker sticker : stickers) {
        TGStickerObj obj = new TGStickerObj(tdlib, sticker, stickerType, emojis[i].emojis);
        if (!canViewPack) {
          obj.setNoViewPack();
        }
        this.stickers.add(obj);
        i++;
      }
    }

    public ArrayList<MediaStickersAdapter.StickerItem> toItems (boolean needInfo) {
      ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>((info != null && needInfo ? 1 : 0) + stickers.size());
      if (info != null && needInfo) {
        items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER_TRENDING, info));
      }

      if (info != null && info.isCollapsed()) {
        int i = 0;
        for (TGStickerObj stickerObj : stickers) {
          if (i++ == info.getSize()) {
            break;
          }
          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, stickerObj));
        }
        if (info.isCollapsableEmojiSet() && info.isCollapsed()) {
          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR_COLLAPSABLE, info));
        }
      } else {
        for (TGStickerObj stickerObj : stickers) {
          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, stickerObj));
        }
      }

      return items;
    }
  }

  @Override
  public void buildMenuStickerPreview (ArrayList<StickerPreviewView.MenuItem> menuItems, @NonNull TGStickerObj sticker, @NonNull StickerSmallView stickerSmallView) {
    final NavigationController navigation = context.navigation();
    final ViewController<?> c = navigation != null ? navigation.getCurrentStackItem() : null;
    final boolean canWriteMessages = c instanceof MessagesController && ((MessagesController) c).canWriteMessages();

    final boolean needViewPackButton = sticker.needViewPackButton();
    final boolean isEmoji = sticker.isCustomEmoji();

    final @StringRes int sendText = isEmoji ? (canWriteMessages ? R.string.PasteCustomEmoji : R.string.ShareCustomEmoji) : R.string.SendSticker;

    menuItems.add(new StickerPreviewView.MenuItem(StickerPreviewView.MenuItem.MENU_ITEM_TEXT,
      Lang.getString(sendText).toUpperCase(), R.id.btn_send, ColorId.textNeutral));

    if (needViewPackButton) {
      menuItems.add(new StickerPreviewView.MenuItem(StickerPreviewView.MenuItem.MENU_ITEM_TEXT,
        Lang.getString(R.string.ViewPackPreview).toUpperCase(), R.id.btn_view, ColorId.textNeutral));
    }
  }

  @Override
  public void onMenuStickerPreviewClick (View v, ViewController<?> context, @NonNull TGStickerObj sticker, @NonNull StickerSmallView stickerSmallView) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_send) {
      if (stickerSmallView.onSendSticker(v, sticker, Td.newSendOptions())) {
        stickerSmallView.closePreviewIfNeeded();
      }
    } else if (viewId == R.id.btn_view) {
      if (context != null) {
        tdlib.ui().showStickerSet(context, sticker.getStickerSetId(), null);
        stickerSmallView.closePreviewIfNeeded();
      }
    }
  }
}
