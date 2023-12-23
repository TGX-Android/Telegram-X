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
 * File created on 31/05/2023
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.emoji.GifView;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerPreviewView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.AnimationsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.util.StickerSetsDataProvider;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;

public class EmojiStatusListController extends ViewController<EmojiLayout> implements
  StickerSmallView.StickerMovementCallback, StickerPreviewView.MenuStickerPreviewCallback,
  AnimationsListener,
  ClickHelper.Delegate,
  ForceTouchView.ActionListener {

  public EmojiStatusListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public void initWithFakeViews (CustomRecyclerView stickersView) {
    this.stickersViewToSet = stickersView;
  }

  @Override
  public int getId () {
    return R.id.controller_emojiCustom;
  }

  private FrameLayoutFix contentView;
  private MediaStickersAdapter stickersAdapter;
  private CustomRecyclerView stickersView;
  private CustomRecyclerView stickersViewToSet;

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    stickersAdapter = new MediaStickersAdapter(this, this, false, this);
    stickersAdapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PROGRESS));
    stickersAdapter.setMenuStickerPreviewCallback(this);

    checkSpanCount();

    initStickers();
    if (stickersViewToSet == null) {
      this.contentView.addView(stickersView);
    }

    loadStickers(); // to show sections
    return contentView;
  }

  // Stickers

  private int getStickerSetSection () {
    if (stickersView == null || spanCount == 0) {
      return -1;
    }
    int i = Views.findFirstCompletelyVisibleItemPositionWithOffset((LinearLayoutManager) stickersView.getLayoutManager(), EmojiLayout.getHeaderSize() / 2);
    if (i != -1) {
      return indexOfStickerSetByAdapterPosition(i);
    }
    return 0;
  }

  private int getStickersScrollY () {
    if (stickersView == null || spanCount == 0) {
      return 0;
    }
    int i = ((LinearLayoutManager) stickersView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i != -1) {
      View v = stickersView.getLayoutManager().findViewByPosition(i);
      int additional = v != null ? -v.getTop() : 0;
      int stickerSet = indexOfStickerSetByAdapterPosition(i);
      return additional + stickersAdapter.measureScrollTop(i, spanCount, stickerSet, stickerSets, null, false);
    }
    return 0;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (stickersView != null)
      stickersView.requestLayout();
  }

  private void initStickers () {
    if (stickersView == null) {
      GridLayoutManager manager = new RtlGridLayoutManager(context(), spanCount).setAlignOnly(true);
      manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
        @Override
        public int getSpanSize (int position) {
          int type = stickersAdapter.getItemViewType(position);
          return (type == MediaStickersAdapter.StickerHolder.TYPE_STICKER ||
            type == MediaStickersAdapter.StickerHolder.TYPE_EMOJI_STATUS_DEFAULT
          ) ? 1 : spanCount;
        }
      });

      stickersView = stickersViewToSet != null ? stickersViewToSet :
        (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, contentView);

      stickersView.setHasFixedSize(true);
      stickersView.setLayoutManager(manager);
      stickersView.setAdapter(stickersAdapter);
      // stickersView.setItemAnimator(null);
      stickersView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180));
      stickersView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
      stickersView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
          if (getArguments() != null && getArguments().getCurrentItem() == 0) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          onStickersScroll(dy);
        }
      });
    }
  }

  private void onStickersScroll (int movedDy) {
    if (ignoreStickersScroll == 0) {
      if (getArguments() != null && getArguments().isWatchingMovements() && getArguments().getCurrentItem() == 0) {
        int y = getStickersScrollY();
        getArguments().moveHeader(y);
        getArguments().setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, getStickerSetSection(), true, true);
      }
    }
  }


  // GIFs force touch

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return true;
  }

  @Override
  public void onClickAt (View view, float x, float y) {

  }

  @Override
  public boolean ignoreHapticFeedbackSettings (float x, float y) {
    return Config.FORCE_TOUCH_ENABLED && getArguments() != null;
  }

  @Override
  public boolean forceEnableVibration () {
    return Settings.instance().useCustomVibrations();
  }

  @Override
  public boolean needLongPress (float x, float y) {
    return true;
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    final TdApi.Animation animation = ((GifView) view).getGif().getAnimation();

    if (!Config.FORCE_TOUCH_ENABLED || getArguments() == null) {
      return true;
    }

    MediaCellView cellView = new MediaCellView(context());
    if (TD.isFileLoaded(animation.animation)) {
      cellView.setEnableEarlyLoad();
    }
    ForceTouchView.ForceTouchContext context = new ForceTouchView.ForceTouchContext(tdlib, view, cellView, null);

    cellView.setBoundForceTouchContext(context);

    MediaItem mediaItem = MediaItem.valueOf(context(), tdlib, animation, null);
    mediaItem.download(true);
    cellView.setMedia(mediaItem);

    IntList ids = new IntList(2);
    IntList icons = new IntList(2);
    StringList strings = new StringList(2);

    ids.append(R.id.btn_deleteGif);
    icons.append(R.drawable.baseline_delete_24);
    strings.append(R.string.Delete);

    ids.append(R.id.btn_send);
    icons.append(R.drawable.deproko_baseline_send_24);
    strings.append(R.string.Send);

    context.setButtons(this, animation, ids.get(), icons.get(), strings.get());

    if (context().openForceTouch(context)) {
      return true;
    } else {
      cellView.performDestroy();
    }

    return false;
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    context().processForceTouchMoveEvent(x, y, startX, startY);
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    context().closeForceTouch();
  }

  @Override
  public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) { }

  @Override
  public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) { }


  // When rotation, etc

  private int spanCount;

  public void checkSpanCount () {
    int spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight());
    if (this.spanCount != spanCount) {
      this.spanCount = spanCount;
      if (stickersView != null) {
        ((GridLayoutManager) stickersView.getLayoutManager()).setSpanCount(spanCount);
      }
    }
  }

  private static int calculateSpanCount (int width, int height) {
    int minSide = Math.min(width, height);
    int minWidth = minSide / 8;
    return minWidth == 0 ? 8 : width / minWidth;
  }

  // Sticker movements

  private @Nullable TGStickerSetInfo lastStickerSetInfo;
  private int lastStickerSetIndex;

  private void setLastStickerSetInfo (TGStickerSetInfo info, int index) {
    lastStickerSetInfo = info;
    lastStickerSetIndex = index;
  }

  private int indexOfStickerSetByAdapterPosition (int position) {
    if (position == 0) {
      return 0;
    }
    if (stickerSets != null) {
      if (lastStickerSetInfo != null) {
        if (position >= lastStickerSetInfo.getStartIndex() && position < lastStickerSetInfo.getEndIndex()) {
          return lastStickerSetIndex;
        } else if (position >= lastStickerSetInfo.getEndIndex()) {
          for (int i = lastStickerSetIndex + 1; i < stickerSets.size(); i++) {
            TGStickerSetInfo oldStickerSet = stickerSets.get(i);
            if (position >= oldStickerSet.getStartIndex() && position < oldStickerSet.getEndIndex()) {
              setLastStickerSetInfo(oldStickerSet, i);
              return lastStickerSetIndex;
            }
          }
        } else if (position < lastStickerSetInfo.getStartIndex()) {
          for (int i = lastStickerSetIndex - 1; i >= 0; i--) {
            TGStickerSetInfo oldStickerSet = stickerSets.get(i);
            if (position >= oldStickerSet.getStartIndex() && position < oldStickerSet.getEndIndex()) {
              setLastStickerSetInfo(oldStickerSet, i);
              return lastStickerSetIndex;
            }
          }
        }
      }
      int i = 0;
      for (TGStickerSetInfo oldStickerSet : stickerSets) {
        if (position >= oldStickerSet.getStartIndex() && position < oldStickerSet.getEndIndex()) {
          setLastStickerSetInfo(oldStickerSet, i);
          return lastStickerSetIndex;
        }
        i++;
      }
    }
    return -1;
  }

  private int indexOfStickerSet (TGStickerSetInfo stickerSet) {
    if (stickerSets != null) {
      for (TGStickerSetInfo oldStickerSet : stickerSets) {
        if (stickerSet.getId() == oldStickerSet.getId()) {
          return stickerSet.getStartIndex();
        }
      }
    }
    return -1;
  }

  private boolean isContainStickerSet (TdApi.StickerSetInfo stickerSet) {
    if (stickerSets != null) {
      for (TGStickerSetInfo oldStickerSet : stickerSets) {
        if (stickerSet.id == oldStickerSet.getId()) {
          return true;
        }
      }
    }
    return false;
  }

  private int indexOfSticker (TGStickerObj sticker) {
    if (stickerSets != null) {
      for (TGStickerSetInfo stickerSet : stickerSets) {
        boolean isFavorite = stickerSet.isFavorite();
        boolean isRecent = stickerSet.isRecent();
        boolean stickerFavorite = sticker.isFavorite();
        boolean stickerRecent = sticker.isRecent();
        if ((isFavorite && stickerFavorite) || (isRecent && stickerRecent) || (isFavorite == stickerFavorite && isRecent == stickerRecent && stickerSet.getId() == sticker.getStickerSetId())) {
          return stickersAdapter.indexOfSticker(sticker, stickerSet.getStartIndex());
        }
      }
    }
    return -1;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    int i = indexOfSticker(sticker);
    if (i != -1) {
      stickersAdapter.setStickerPressed(i, isPressed, stickersView != null ? stickersView.getLayoutManager() : null);
    }
  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    EmojiLayout parent = getArguments();
    return parent != null && recyclerY > parent.getHeaderBottom();
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return false;
  }

  @Override
  public int getViewportHeight () {
    return -1;
  }

  private boolean lockClickListenersByKeyboard;
  private final Runnable unlockClickListenersByKeyboardRunnable = () -> lockClickListenersByKeyboard = false;

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    if (lockClickListenersByKeyboard || context.isKeyboardVisible()) {
      context.hideSoftwareKeyboard();
    } else if (getArguments() != null) {
      return getArguments().setEmojiStatus(clickView, sticker, 0);
    }
    return false;
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    if (visible) {
      UI.cancel(unlockClickListenersByKeyboardRunnable);
      lockClickListenersByKeyboard = true;
    } else {
      UI.post(unlockClickListenersByKeyboardRunnable, 100);
    }
    return super.onKeyboardStateChanged(visible);
  }

  @Override
  public long getStickerOutputChatId () {
    return getArguments() != null ? getArguments().findOutputChatId() : 0;
  }

  @Override
  public int getStickersListTop () {
    return Views.getLocationInWindow(stickersView)[1];
  }

  // Stickers logic

  private boolean applyingChanges;
  private ArrayList<long[]> pendingChanges;

  private void setApplyingChanges (boolean applyingChanges) {
    if (this.applyingChanges != applyingChanges) {
      this.applyingChanges = applyingChanges;
      if (!applyingChanges && pendingChanges != null && !pendingChanges.isEmpty()) {
        do {
          long[] stickerSetIds = pendingChanges.remove(0);
          changeStickers(stickerSetIds);
        } while (!pendingChanges.isEmpty() && !this.applyingChanges);
      }
    }
  }

  private void reloadStickers () {
    if (pendingChanges != null) {
      pendingChanges.clear();
    }
    loadStickers(); // synonym
  }

  private static final int MAX_HEADER_COUNT = 2;

  private int findRecentStickerSet () {
    if (stickerSets != null && !stickerSets.isEmpty()) {
      int i = 0;
      for (TGStickerSetInfo set : stickerSets) {
        if (set.isRecent()) {
          return i;
        }
        i++;
        if (i > MAX_HEADER_COUNT) {
          break;
        }
      }
    }
    return -1;
  }

  private int findTrendingStickerSet () {
    if (stickerSets != null && !stickerSets.isEmpty()) {
      int i = 0;
      for (TGStickerSetInfo set : stickerSets) {
        if (set.isDefaultEmoji()) {
          return i;
        }
        i++;
        if (i > MAX_HEADER_COUNT) {
          break;
        }
      }
    }
    return -1;
  }

  private int findFavoriteStickerSet () {
    if (stickerSets != null && !stickerSets.isEmpty()) {
      int i = 0;
      for (TGStickerSetInfo set : stickerSets) {
        if (set.isFavorite()) {
          return i;
        }
        i++;
        if (i > MAX_HEADER_COUNT) {
          break;
        }
      }
    }
    return -1;
  }

  private boolean hasNoStickerSets () {
    for (TGStickerSetInfo info : stickerSets) {
      if (!info.isFavorite() && !info.isRecent()) {
        return false;
      }
    }
    return true;
  }

  private int getSystemSetsCount () {
    int i = 0;
    for (TGStickerSetInfo info : stickerSets) {
      if (info.isSystem()) {
        i++;
      }
    }
    return i;
  }

  private void changeStickers (long[] stickerSetIds) {
    if (applyingChanges) {
      if (pendingChanges == null) {
        pendingChanges = new ArrayList<>();
      }
      pendingChanges.add(stickerSetIds);
      return;
    }

    if (hasNoStickerSets()) {
      reloadStickers();
      return;
    }

    // setId -> position in the current list
    // LongSparseArray<Integer> currentStickerSets = new LongSparseArray<>(this.stickerSets.size());
    LongSparseArray<TGStickerSetInfo> removedStickerSets = new LongSparseArray<>(this.stickerSets.size());
    // int currentSetIndex = 0;
    for (TGStickerSetInfo stickerSet : this.stickerSets) {
      if (!stickerSet.isSystem()) {
        removedStickerSets.put(stickerSet.getId(), stickerSet);
        // currentStickerSets.put(stickerSet.getId(), currentSetIndex);
        // currentSetIndex++;
      }
    }

    // setId -> position in the future list
    LongSparseArray<Integer> positions = null;

    // items that are not represented in the list (yet)
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
      totalIndex++;
    }

    // First, remove items
    final int removedCount = removedStickerSets.size();
    for (int i = 0; i < removedCount; i++) {
      TGStickerSetInfo stickerSet = removedStickerSets.valueAt(i);
      removeStickerSet(stickerSet);
    }

    // Then, move items
    if (positions != null && !stickerSets.isEmpty() ) {
      for (int j = 0; j < positions.size(); j++) {
        long setId = positions.keyAt(j);
        int newPosition = positions.valueAt(j);
        int currentPosition = indexOfStickerSetById(setId);
        if (currentPosition == -1) {
          throw new RuntimeException();
        }
        if (currentPosition != newPosition) {
          int systemSetsCount = getSystemSetsCount();
          moveStickerSet(currentPosition + systemSetsCount, newPosition + systemSetsCount);
        }
      }
    }

    if (reloadAfterLocalChanges) {
      reloadStickers();
      return;
    }

    // Then, add items
    if (futureItems != null) {
      setApplyingChanges(true);
      final long[] setIds = futureItems.get();
      final int addedCount = futureItems.size();
      final int[] index = new int[2];
      tdlib.client().send(new TdApi.GetStickerSet(setIds[index[0]]), new Client.ResultHandler() {
        @Override
        public void onResult (TdApi.Object object) {
          if (object.getConstructor() == TdApi.StickerSet.CONSTRUCTOR) {

            TdApi.StickerSet rawStickerSet = (TdApi.StickerSet) object;
            final TGStickerSetInfo stickerSet = new TGStickerSetInfo(tdlib, rawStickerSet);
            final TdApi.Sticker[] stickers = ((TdApi.StickerSet) object).stickers;

            final int insertIndex = index[1]++;

            final ArrayList<MediaStickersAdapter.StickerItem> items;
            items = new ArrayList<>(stickers.length + 1);
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, stickerSet));

            int i = 0;
            for (TdApi.Sticker sticker : stickers) {
              TGStickerObj parsed = new TGStickerObj(tdlib, sticker, sticker.fullType, rawStickerSet.emojis[i].emojis);
              parsed.setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI_PREVIEW);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, parsed));
              i++;
            }

            runOnUiThreadOptional(() -> addStickerSet(stickerSet, items, insertIndex + getSystemSetsCount()));
          }

          if (++index[0] < addedCount) {
            tdlib.client().send(new TdApi.GetStickerSet(setIds[index[0]]), this);
          } else {
            runOnUiThreadOptional(() -> setApplyingChanges(false));
          }
        }
      });
    }
  }

  private int indexOfStickerSetById (long setId) {
    int index = 0;
    for (TGStickerSetInfo setInfo : stickerSets) {
      if (!setInfo.isSystem()) {
        if (setInfo.getId() == setId) {
          return index;
        }
        index++;
      }
    }
    return -1;
  }

  private int ignoreStickersScroll;

  private void moveStickerSet (int oldPosition, int newPosition) {
    beforeStickerChanges();

    if (getArguments() != null) {
      getArguments().onMoveStickerSection(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, oldPosition, newPosition);
    }

    TGStickerSetInfo stickerSet = stickerSets.remove(oldPosition);

    final int startIndex = stickerSet.getStartIndex();
    final int itemCount = stickerSet.getSize() + 1;

    int startPosition;
    if (oldPosition < newPosition) {
      startPosition = startIndex;
    } else {
      startPosition = stickerSets.get(newPosition).getStartIndex();
    }

    stickerSets.add(newPosition, stickerSet);

    for (int i = Math.min(oldPosition, newPosition); i < stickerSets.size(); i++) {
      TGStickerSetInfo nextSet = stickerSets.get(i);
      nextSet.setStartIndex(startPosition);
      startPosition += nextSet.getSize() + 1;
    }

    stickersAdapter.moveRange(startIndex, itemCount, stickerSet.getStartIndex());
    resetScrollCache();
  }

  private void beforeStickerChanges () {
    ignoreStickersScroll++;
  }

  private void resetScrollCache () {
    // lastStickerSetInfo = null; // FIXME removing current sticker set does not update selection
    // ignoreStickersScroll--;

    if (getArguments() != null) {
      getArguments().resetScrollState(false); // FIXME upd: ... fixme what?
    }
    UI.post(() -> {
      if (getArguments() != null) {
        getArguments().setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, getStickerSetSection(), true, true);
        getArguments().resetScrollState(false);
      }
      ignoreStickersScroll--;
    }, 400);
  }

  private void addStickerSet (TGStickerSetInfo stickerSet, ArrayList<MediaStickersAdapter.StickerItem> items, int index) {
    if (index < 0 || index >= stickerSets.size()) {
      return;
    }

    beforeStickerChanges();

    if (getArguments() != null) {
      getArguments().onAddStickerSection(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, index, stickerSet);
    }

    int startIndex = stickerSets.get(index).getStartIndex();
    stickerSets.add(index, stickerSet);
    for (int i = index; i < stickerSets.size(); i++) {
      TGStickerSetInfo nextStickerSet = stickerSets.get(i);
      nextStickerSet.setStartIndex(startIndex);
      startIndex += nextStickerSet.getSize() + 1;
    }

    stickersAdapter.addRange(stickerSet.getStartIndex(), items);
    resetScrollCache();
  }

  public void removeRecentStickers () {
    TGStickerSetInfo stickerSet = stickerSets.get(0);
    if (stickerSet != null && stickerSet.isRecent()) {
      removeStickerSet(stickerSet);
    }
  }

  public int removeStickerSet (TGStickerSetInfo stickerSet) {
    int i = stickerSets.indexOf(stickerSet);
    if (i != -1) {
      beforeStickerChanges();
      stickerSets.remove(i);
      if (getArguments() != null) {
        getArguments().onRemoveStickerSection(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, i);
      }
      int startIndex = stickerSet.getStartIndex();
      stickersAdapter.removeRange(startIndex, stickerSet.getSize() + 1);
      for (int j = i; j < stickerSets.size(); j++) {
        TGStickerSetInfo nextStickerSet = stickerSets.get(j);
        nextStickerSet.setStartIndex(startIndex);
        startIndex += nextStickerSet.getSize() + 1;
      }
      resetScrollCache();
    }
    return i;
  }

  private boolean ignoreRequests;
  private long ignoreException;

  private void setIgnoreRequests (boolean ignoreRequests, long exceptSetId) {
    if (this.ignoreRequests != ignoreRequests) {
      this.ignoreRequests = ignoreRequests;
      this.ignoreException = exceptSetId;
      if (!ignoreRequests && stickersView != null) {
        final int firstVisiblePosition = ((LinearLayoutManager) stickersView.getLayoutManager()).findFirstVisibleItemPosition();
        final int lastVisiblePosition = ((LinearLayoutManager) stickersView.getLayoutManager()).findLastVisibleItemPosition();

        for (int i = lastVisiblePosition; i >= firstVisiblePosition; i--) {
          MediaStickersAdapter.StickerItem item = stickersAdapter.getItem(i);
          if (item != null && item.viewType == MediaStickersAdapter.StickerHolder.TYPE_STICKER && item.sticker != null) {
            item.sticker.requestRequiredInformation();
          }
        }
      }
    }
  }

  private Client.ResultHandler stickerSetsHandler (boolean needAddDefaultPremiumStar) {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.StickerSets.CONSTRUCTOR: {
          TdApi.StickerSetInfo[] rawStickerSets = ((TdApi.StickerSets) object).sets;

          final TdApi.Sticker[] recentStickers = pendingRecentStickers;
          final TdApi.Sticker[] trendingStickers = pendingTrendingStickers;
          final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>(rawStickerSets.length);
          final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();
          pendingRecentStickers = null;
          pendingTrendingStickers = null;

          if (rawStickerSets.length == 0 && (recentStickers == null || recentStickers.length == 0) && (trendingStickers == null || trendingStickers.length == 0)) {
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_NO_EMOJISETS));
          } else {
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));
            int startIndex = 1;

            final int totalRecentCount = recentStickers != null ? recentStickers.length : 0;
            final int totalTrendingCount = trendingStickers != null ? trendingStickers.length : 0;
            if (totalRecentCount > 0) {
              TGStickerSetInfo info = new TGStickerSetInfo(tdlib, recentStickers, false, totalRecentCount);
              info.setStartIndex(startIndex);
              stickerSets.add(info);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_EMPTY, info));
              int remainingCount = totalRecentCount;
              for (TdApi.Sticker recentSticker : recentStickers) {
                TGStickerObj sticker = new TGStickerObj(tdlib, recentSticker, null, recentSticker.fullType);
                sticker.setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI_PREVIEW);
                sticker.setIsRecent();
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
                if (--remainingCount == 0) {
                  break;
                }
              }
              startIndex += totalRecentCount + 1;
            }

            if (totalTrendingCount > 0) {
              TGStickerSetInfo info = new TGStickerSetInfo(tdlib, trendingStickers, false, totalTrendingCount);
              info.setStartIndex(startIndex);
              info.setIsDefaultEmoji();
              stickerSets.add(info);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, info));
              if (needAddDefaultPremiumStar) {
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_EMOJI_STATUS_DEFAULT, info));
              }
              int remainingCount = totalTrendingCount;
              for (TdApi.Sticker trendingSticker : trendingStickers) {
                TGStickerObj sticker = new TGStickerObj(tdlib, trendingSticker, null, trendingSticker.fullType);
                sticker.setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI_PREVIEW);
                sticker.setIsRecent();
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
                if (--remainingCount == 0) {
                  break;
                }
              }
              startIndex += totalTrendingCount + (needAddDefaultPremiumStar ? 2 : 1);
            }

            for (TdApi.StickerSetInfo rawInfo : rawStickerSets) {
              TGStickerSetInfo info = new TGStickerSetInfo(tdlib, rawInfo);
              if (info.getSize() == 0) {
                continue;
              }
              stickerSets.add(info);
              info.setStartIndex(startIndex);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, info));
              for (int i = 0; i < rawInfo.size; i++) {
                TGStickerObj sticker = new TGStickerObj(tdlib, i < rawInfo.covers.length ? rawInfo.covers[i] : null, null, rawInfo.stickerType);
                sticker.setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI_PREVIEW);
                sticker.setStickerSetId(rawInfo.id, null);
                sticker.setDataProvider(stickerSetsDataProvider());
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
              }
              startIndex += rawInfo.size + 1;
            }
          }

          runOnUiThreadOptional(() -> {
            if (getArguments() != null) {
              getArguments().setStickerSets(stickerSets, false, recentStickers != null && recentStickers.length > 0, trendingStickers != null && trendingStickers.length > 0, !needAddDefaultPremiumStar);
            }
            setStickers(stickerSets, items);
            stickersLoaded = true;
            loadNextTrending();
            if (onStickersLoadListener != null) {
              onStickersLoadListener.run();
              onStickersLoadListener = null;
            }
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

  /* Loading functions */

  private boolean loadingStickers;
  private boolean stickersLoaded;
  private Runnable onStickersLoadListener;

  public void setOnStickersLoadListener (Runnable onStickersLoadListener) {
    this.onStickersLoadListener = onStickersLoadListener;
  }

  private void loadStickers () {
    if (!loadingStickers) {
      loadingStickers = true;
      stickersLoaded = false;

      if (StringUtils.isEmpty(currentTextSearchRequest) && StringUtils.isEmpty(currentEmojiSearchRequest)) {
        LongList recentEmojiList = new LongList(200);
        LongList trendingEmojiList = new LongList(200);
        getCustomEmojiStatusList(new TdApi.GetThemedEmojiStatuses(), trendingEmojiList, () ->
          getCustomEmojiStatusList(new TdApi.GetRecentEmojiStatuses(), recentEmojiList, () ->
            getCustomEmojiStatusList(new TdApi.GetDefaultEmojiStatuses(), trendingEmojiList, () ->
              getCustomEmojiStickers(trendingEmojiList.get(), trendingStickersHandler(() ->
                getCustomEmojiStickers(recentEmojiList.get(), serviceStickersHandler(null))))))
        );
      } else {
        if (StringUtils.isEmpty(currentEmojiSearchRequest)) {
          tdlib.client().send(new TdApi.SearchEmojis(currentTextSearchRequest, false, new String[] {"en"}), obj -> {
            switch (obj.getConstructor()) {
              case TdApi.Emojis.CONSTRUCTOR: {
                TdApi.Emojis emojis = (TdApi.Emojis) obj;
                if (emojis.emojis.length > 0) {
                  StringBuilder b = new StringBuilder();
                  for (String emoji : emojis.emojis) {
                    if (b.length() > 0) {
                      b.append(" ");
                    }
                    b.append(emoji);
                  }

                  tdlib.client().send(new TdApi.SearchStickers(new TdApi.StickerTypeCustomEmoji(), b.toString(), 200), serviceStickersHandler(currentTextSearchRequest));
                } else {
                  tdlib.client().send(new TdApi.SearchInstalledStickerSets(new TdApi.StickerTypeCustomEmoji(), currentTextSearchRequest, 200), stickerSetsHandler(false));
                }
                break;
              }
              case TdApi.Error.CONSTRUCTOR: {
                UI.showError(obj);
                break;
              }
            }
          });
        } else {
          tdlib.client().send(new TdApi.SearchStickers(new TdApi.StickerTypeCustomEmoji(), currentEmojiSearchRequest, 200), serviceStickersHandler(currentTextSearchRequest));
        }
      }
    }
  }

  private void getCustomEmojiStatusList (TdApi.Function<TdApi.EmojiStatuses> req, LongList longList, Runnable onReceive) {
    tdlib.client().send(req, object2 -> {
      switch (object2.getConstructor()) {
        case TdApi.EmojiStatuses.CONSTRUCTOR: {
          long[] customEmojiIds = ((TdApi.EmojiStatuses) object2).customEmojiIds;
          for (long customEmojiId : customEmojiIds) {
            if (longList.size() >= 200) break;
            longList.append(customEmojiId);
          }
          onReceive.run();
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object2);
          break;
        }
      }
    });
  }

  private void getCustomEmojiStickers (long[] emojiIds, Client.ResultHandler resultHandler) {
    tdlib.client().send(new TdApi.GetCustomEmojiStickers(emojiIds), resultHandler);
  }

  private Client.ResultHandler trendingStickersHandler (Runnable after) {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Stickers.CONSTRUCTOR: {
          pendingTrendingStickers = ((TdApi.Stickers) object).stickers;
          after.run();
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
      }
    };
  }

  private Client.ResultHandler serviceStickersHandler (final String setsQuery) {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Stickers.CONSTRUCTOR: {
          pendingRecentStickers = ((TdApi.Stickers) object).stickers;
          if (StringUtils.isEmpty(setsQuery)) {
            tdlib.client().send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeCustomEmoji()), stickerSetsHandler(true));
          } else {
            tdlib.client().send(new TdApi.SearchInstalledStickerSets(new TdApi.StickerTypeCustomEmoji(), setsQuery, 200), stickerSetsHandler(false));
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
      }
    };
  }

  public void scrollToSystemStickers (boolean animated) {
    int i = findFavoriteStickerSet();
    if (i == -1)
      i = findRecentStickerSet();
    if (i != -1)
      scrollToStickerSet(i == 0 ? 0 : stickerSets.get(i).getStartIndex(), animated);
  }

  public void scrollToTrendingStickers (boolean animated) {
    int i = findTrendingStickerSet();
    if (i != -1)
      scrollToStickerSet(i == 0 ? 0 : stickerSets.get(i).getStartIndex(), animated);
  }

  private FactorAnimator lastScrollAnimator;
  private static final int SCROLLBY_SECTION_LIMIT = 8;

  private void scrollToStickerSet (int stickerSetIndex, boolean animated) {
    final int futureSection = indexOfStickerSetByAdapterPosition(stickerSetIndex);
    if (futureSection == -1) {
      return;
    }

    stickersView.stopScroll();

    final int currentSection = getStickerSetSection();

    if (!animated || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || getArguments() == null || Math.abs(futureSection - currentSection) > SCROLLBY_SECTION_LIMIT) {
      if (getArguments() != null) {
        getArguments().setIgnoreMovement(true);
      }
      ((LinearLayoutManager) stickersView.getLayoutManager()).scrollToPositionWithOffset(stickerSetIndex, stickerSetIndex == 0 ? 0 : EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding());
      if (getArguments() != null) {
        getArguments().setIgnoreMovement(false);
      }
    } else {
      final int scrollTop;

      if (stickerSetIndex == 0) {
        scrollTop = 0;
      } else {
        scrollTop = Math.max(0, stickersAdapter.measureScrollTop(stickerSetIndex, spanCount, futureSection, stickerSets, null, false) - EmojiLayout.getHeaderSize() - EmojiLayout.getHeaderPadding());
      }

      final int currentScrollTop = getStickersScrollY();
      final int scrollDiff = scrollTop - currentScrollTop;
      final int[] totalScrolled = new int[1];

      if (lastScrollAnimator != null) {
        lastScrollAnimator.cancel();
      }
      stickersView.setScrollDisabled(true);
      setIgnoreRequests(true, stickerSets.get(futureSection).getId());
      if (getArguments() != null) {
        getArguments().setIgnoreMovement(true);
        getArguments().setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, futureSection, true, true);
      }

      lastScrollAnimator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          int diff = (int) ((float) scrollDiff * factor);
          stickersView.scrollBy(0, diff - totalScrolled[0]);
          totalScrolled[0] = diff;
        }

        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
          stickersView.setScrollDisabled(false);
          setIgnoreRequests(false, 0);
          if (getArguments() != null) {
            getArguments().setIgnoreMovement(false);
          }
        }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, Math.min(450, Math.max(250, Math.abs(currentSection - futureSection) * 150)));
      lastScrollAnimator.animateTo(1f);

      // stickersView.smoothScrollBy(0, scrollTop - currentScrollTop);
    }
  }

  private void applyStickerSet (TdApi.StickerSet stickerSet) {
    final int actualSize = stickerSet.stickers.length;
    if (stickerSets == null || stickerSets.isEmpty()) {
      return;
    }
    int i = 0;
    for (TGStickerSetInfo oldStickerSet : stickerSets) {
      if (oldStickerSet.isSystem()) {
        i++;
        continue;
      }
      if (oldStickerSet.getId() == stickerSet.id) {
        oldStickerSet.setStickerSet(stickerSet);
        final int oldSize = oldStickerSet.getSize();
        // If something has suddenly changed with this sticker set
        if (oldSize != actualSize) {
          if (actualSize == 0) {
            if (getArguments() != null) {
              getArguments().setIgnoreMovement(true);
            }
            stickerSets.remove(i);
            if (stickerSets.isEmpty()) {
              stickersAdapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_NO_EMOJISETS));
            } else {
              // Shifting next sticker sets bounds
              int startIndex;
              if (i != 0) {
                TGStickerSetInfo prevStickerSet = stickerSets.get(i - 1);
                startIndex = prevStickerSet.getStartIndex() + prevStickerSet.getSize() + 1;
              } else {
                startIndex = 1;
              }
              for (int j = i; j < stickerSets.size(); j++) {
                TGStickerSetInfo nextStickerSet = stickerSets.get(j);
                nextStickerSet.setStartIndex(startIndex);
                startIndex += nextStickerSet.getSize() + 1;
              }
              stickersAdapter.removeRange(oldStickerSet.getStartIndex(), oldStickerSet.getSize() + 1);
            }

            if (getArguments() != null) {
              getArguments().setIgnoreMovement(false);
            }

            return;
          } else {
            oldStickerSet.setSize(actualSize);

            // Shifting next sticker sets bounds
            int startIndex = oldStickerSet.getStartIndex() + actualSize + 1;
            for (int j = i + 1; j < stickerSets.size(); j++) {
              TGStickerSetInfo nextStickerSet = stickerSets.get(j);
              nextStickerSet.setStartIndex(startIndex);
              startIndex += nextStickerSet.getSize() + 1;
            }

            if (actualSize < oldSize) {
              stickersAdapter.removeRange(oldStickerSet.getStartIndex() + 1 + actualSize, oldSize - actualSize);
            } else {
              ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>(actualSize - oldSize);
              for (int j = oldSize; j < actualSize; j++) {
                TdApi.Sticker sticker = stickerSet.stickers[j];
                TGStickerObj obj = new TGStickerObj(tdlib, sticker, sticker.fullType, stickerSet.emojis[j].emojis);
                obj.setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI_PREVIEW);
                obj.setStickerSetId(stickerSet.id, stickerSet.emojis[j].emojis);
                obj.setDataProvider(stickerSetsDataProvider());
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, obj));
              }
              stickersAdapter.insertRange(oldStickerSet.getStartIndex() + 1 + oldSize, items);
            }
          }

          if (getArguments() != null) {
            getArguments().setIgnoreMovement(false);
          }
        }

        for (int stickerIndex = oldStickerSet.getCoverCount(), j = oldStickerSet.getStartIndex() + 1 + oldStickerSet.getCoverCount(); stickerIndex < stickerSet.stickers.length; stickerIndex++, j++) {
          MediaStickersAdapter.StickerItem item = stickersAdapter.getItem(j);
          TdApi.Sticker sticker = stickerSet.stickers[stickerIndex];
          item.sticker.set(tdlib, sticker, sticker.fullType, stickerSet.emojis[stickerIndex].emojis);

          View view = stickersView != null ? stickersView.getLayoutManager().findViewByPosition(j) : null;
          if (view instanceof StickerSmallView) {
            ((StickerSmallView) view).refreshSticker();
          } else {
            stickersAdapter.notifyItemChanged(j);
          }
        }

        break;
      }
      i++;
    }
  }

  private ArrayList<TGStickerSetInfo> stickerSets;

  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.stickerSets = stickerSets;
    this.loadingStickers = false;
    this.lastStickerSetInfo = null;
    if (this.stickerSetsDataProvider != null) {
      this.stickerSetsDataProvider.clear();
    }
    stickersAdapter.setItems(items);
  }

  private TdApi.Sticker[] pendingRecentStickers;
  private TdApi.Sticker[] pendingTrendingStickers;

  @Override
  public void onSavedAnimationsUpdated (int[] animationIds) {

  }

  public void showStickerSet (TGStickerSetInfo stickerSet) {
    int i = indexOfStickerSet(stickerSet);
    if (i != -1) {
      initStickers();
      scrollToStickerSet(i, true);
    }
  }

  private String currentTextSearchRequest;
  private String currentEmojiSearchRequest;

  public void search (String query, String emojiQuery) {
    if (StringUtils.equalsOrBothEmpty(query, currentTextSearchRequest) &&
      StringUtils.equalsOrBothEmpty(emojiQuery, currentEmojiSearchRequest)) return;

    setStickers(new ArrayList<>(), new ArrayList<>());
    stickersAdapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PROGRESS));
    if (getArguments() != null) {
      getArguments().setStickerSets(stickerSets, false, false, false, !StringUtils.isEmpty(query) || !StringUtils.isEmpty(emojiQuery));
    }
    currentTextSearchRequest = query;
    currentEmojiSearchRequest = emojiQuery;
    pendingRecentStickers = new TdApi.Sticker[0];
    pendingTrendingStickers = new TdApi.Sticker[0];
    reloadStickers();
  }


  /* Trending Sets */

  private final ArrayList<TGStickerSetInfo> trendingSets = new ArrayList<>();
  private boolean trendingLoading;
  private boolean canLoadMoreTrending = true;
  private int trendingOffset = 0;
  private int trendingSkip = 0;
  private @Nullable TGStickerSetInfo lastTrendingStickerSet;

  private void loadNextTrending () {
    if (!trendingLoading && stickersLoaded) {
      trendingLoading = true;
      tdlib.client().send(new TdApi.GetTrendingStickerSets(new TdApi.StickerTypeCustomEmoji(), trendingOffset, 25), object -> {
        final ArrayList<TGStickerSetInfo> parsedStickerSets = new ArrayList<>();
        final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();
        final int offset = trendingOffset;

        if (object.getConstructor() == TdApi.TrendingStickerSets.CONSTRUCTOR) {
          TdApi.TrendingStickerSets trendingStickerSets = (TdApi.TrendingStickerSets) object;
          trendingOffset += trendingStickerSets.sets.length;

          ArrayList<TdApi.StickerSetInfo> filtered = new ArrayList<>();
          for (TdApi.StickerSetInfo set : trendingStickerSets.sets) {
            if (!isContainStickerSet(set)) {
              filtered.add(set);
            } else {
              trendingSkip += 1;
            }
          }

          EmojiMediaListController.parseTrending(tdlib, parsedStickerSets, items,  stickersAdapter.getItemCount(), filtered.toArray(new TdApi.StickerSetInfo[0]), stickerSetsDataProvider(), null, false, true, null);
        }

        runOnUiThreadOptional(() -> {
          addTrendingStickers(parsedStickerSets, items, offset);
        });
      });
    }
  }

  private void addTrendingStickers (ArrayList<TGStickerSetInfo> trendingSets, ArrayList<MediaStickersAdapter.StickerItem> items, int offset) {
    if (offset != 0 && (!trendingLoading /*|| (offset != this.trendingSets.size() + trendingSkip)*/))
      return;

    if (trendingSets != null) {
      if (offset == 0) {
        this.lastTrendingStickerSet = null;
        this.trendingSets.clear();
      }
      this.trendingSets.addAll(trendingSets);
      for (TGStickerSetInfo info : trendingSets) {
        getArguments().onAddStickerSection(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, stickerSets.size(), info);
        stickerSets.add(info);
      }
    }
    this.canLoadMoreTrending = trendingSets != null && !trendingSets.isEmpty();

    stickersAdapter.addItems(items);

    this.trendingLoading = false;

    loadNextTrending();
  }


  /* Data provider */

  private StickerSetsDataProvider stickerSetsDataProvider;

  private StickerSetsDataProvider stickerSetsDataProvider() {
    if (stickerSetsDataProvider != null) {
      return stickerSetsDataProvider;
    }

    return stickerSetsDataProvider = new StickerSetsDataProvider(tdlib) {
      @Override
      protected boolean needIgnoreRequests (long stickerSetId, TGStickerObj stickerObj) {
        return ignoreRequests && stickerSetId != ignoreException;
      }

      @Override
      protected int getLoadingFlags (long stickerSetId, TGStickerObj stickerObj) {
        return FLAG_REGULAR;
      }

      @Override
      protected void applyStickerSet (TdApi.StickerSet stickerSet, int flags) {
        if (BitwiseUtils.hasFlag(flags, FLAG_REGULAR)) {
          EmojiStatusListController.this.applyStickerSet(stickerSet);
        }
      }
    };
  }



  /* Preview Sticker Menu */

  @Override
  public void buildMenuStickerPreview (ArrayList<StickerPreviewView.MenuItem> menuItems, @NonNull TGStickerObj sticker, @NonNull StickerSmallView stickerSmallView) {
    menuItems.add(new StickerPreviewView.MenuItem(StickerPreviewView.MenuItem.MENU_ITEM_TEXT,
      Lang.getString(R.string.SetEmojiAsStatus).toUpperCase(), R.id.btn_setEmojiStatus, ColorId.textNeutral));

    menuItems.add(new StickerPreviewView.MenuItem(StickerPreviewView.MenuItem.MENU_ITEM_TEXT,
      Lang.getString(R.string.SetEmojiAsStatusTimed).toUpperCase(), R.id.btn_setEmojiStatusTimed, ColorId.textNeutral));
  }

  @Override
  public void onMenuStickerPreviewClick (View v, ViewController<?> context, @NonNull TGStickerObj sticker, @NonNull StickerSmallView stickerSmallView) {
    final long emojiId = sticker.getCustomEmojiId();
    final int viewId = v.getId();
    if (viewId == R.id.btn_setEmojiStatus) {
      tdlib.client().send(new TdApi.SetEmojiStatus(new TdApi.EmojiStatus(emojiId, 0)), tdlib.okHandler());
      stickerSmallView.onSetEmojiStatus(v, sticker, emojiId, 0);
      stickerSmallView.closePreviewIfNeeded();
    } else if (viewId == R.id.btn_setEmojiStatusTimed) {
      if (context != null) {
        context.showOptions(null, new int[] {
          R.id.btn_setEmojiStatusTimed1Hour,
          R.id.btn_setEmojiStatusTimed2Hours,
          R.id.btn_setEmojiStatusTimed8Hours,
          R.id.btn_setEmojiStatusTimed2Days,
          R.id.btn_setEmojiStatusTimedCustom,
        }, new String[] {
          Lang.getString(R.string.SetEmojiAsStatusTimed1Hour),
          Lang.getString(R.string.SetEmojiAsStatusTimed2Hours),
          Lang.getString(R.string.SetEmojiAsStatusTimed8Hours),
          Lang.getString(R.string.SetEmojiAsStatusTimed2Days),
          Lang.getString(R.string.SetEmojiAsStatusTimedCustom)
        }, new int[] {
          ViewController.OPTION_COLOR_NORMAL,
          ViewController.OPTION_COLOR_NORMAL,
          ViewController.OPTION_COLOR_NORMAL,
          ViewController.OPTION_COLOR_NORMAL,
          ViewController.OPTION_COLOR_NORMAL,
        }, new int[] {
          R.drawable.baseline_access_time_24,
          R.drawable.baseline_access_time_24,
          R.drawable.baseline_access_time_24,
          R.drawable.baseline_access_time_24,
          R.drawable.baseline_date_range_24
        }, (optionItemView, id) -> {
          if (id == R.id.btn_setEmojiStatusTimedCustom) {
            int titleRes, todayRes, tomorrowRes, futureRes;
            titleRes = R.string.SetEmojiAsStatus;
            todayRes = R.string.SetTodayAt;
            tomorrowRes = R.string.SetTomorrowAt;
            futureRes = R.string.SetDateAt;

            context.showDateTimePicker(tdlib, Lang.getString(titleRes), todayRes, tomorrowRes, futureRes, millis -> {
              long expirationDate = millis / 1000L;
              stickerSmallView.onSetEmojiStatus(v, sticker, emojiId, expirationDate);
              tdlib.client().send(new TdApi.SetEmojiStatus(new TdApi.EmojiStatus(emojiId, (int) expirationDate)), tdlib.okHandler());
              stickerSmallView.closePreviewIfNeeded();
            }, null);
            return true;
          }

          final int duration;
          if (id == R.id.btn_setEmojiStatusTimed1Hour) {
            duration = 60 * 60;
          } else if (id == R.id.btn_setEmojiStatusTimed2Hours) {
            duration = 2 * 60 * 60;
          } else if (id == R.id.btn_setEmojiStatusTimed8Hours) {
            duration = 8 * 60 * 60;
          } else if (id == R.id.btn_setEmojiStatusTimed2Days) {
            duration = 2 * 24 * 60 * 60;
          } else {
            duration = 0;
          }
          long expirationDate = System.currentTimeMillis() / 1000L + duration;
          stickerSmallView.onSetEmojiStatus(v, sticker, emojiId, expirationDate);
          tdlib.client().send(new TdApi.SetEmojiStatus(new TdApi.EmojiStatus(emojiId, (int) expirationDate)), tdlib.okHandler());
          stickerSmallView.closePreviewIfNeeded();
          return true;
        });
      }
    }
  }
}
