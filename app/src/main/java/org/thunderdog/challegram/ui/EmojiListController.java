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
 * File created on 25/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.EmojiView;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGDefaultEmoji;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StickerSetsDataProvider;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutRecyclerController;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutSectionPager;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutTrendingController;
import org.thunderdog.challegram.widget.emoji.section.EmojiSection;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;

public class EmojiListController extends ViewController<EmojiLayout> implements StickersListener {

  private static final int SECTION_STICKERS = 0;
  private static final int SECTION_TRENDING = 2;

  private EmojiLayoutSectionPager contentView;
  private final EmojiLayoutRecyclerController emojiController;
  private final EmojiLayoutTrendingController trendingSetsController;
  private final boolean onlyClassicEmoji;

  public EmojiListController (Context context, Tdlib tdlib, boolean onlyClassicEmoji) {
    super(context, tdlib);
    this.onlyClassicEmoji = onlyClassicEmoji;
    emojiController = new EmojiLayoutRecyclerController(context, tdlib, EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID);
    emojiController.setItemWidth(8, 45);
    emojiController.setOnlyClassicEmoji(onlyClassicEmoji);
    emojiController.setStickerObjModifier(this::modifyStickerObj);

    trendingSetsController = new EmojiLayoutTrendingController(context, tdlib, EmojiLayout.EMOJI_TRENDING_CONTROLLER_ID);
    trendingSetsController.setCallbacks(stickerSetsDataProvider(), new TdApi.StickerTypeCustomEmoji());
    trendingSetsController.stickerSets = new ArrayList<>();
  }

  public TGStickerObj modifyStickerObj (TGStickerObj sticker) {
    sticker.setPreviewOptimizationMode(GifFile.OptimizationMode.EMOJI_PREVIEW);
    return sticker;
  }

  @Override
  public int getId () {
    return R.id.controller_emoji;
  }

  private MediaStickersAdapter adapter;
  private MediaStickersAdapter trendingAdapter;
  private EmojiToneHelper toneHelper;

  private boolean useDarkMode;

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (emojiController.recyclerView != null)
      emojiController.recyclerView.requestLayout();
    if (trendingAdapter != null)
      trendingAdapter.notifyDataSetChanged();
  }

  @Override
  protected View onCreateView (Context context) {
    toneHelper = new EmojiToneHelper(context, getArgumentsStrict().getToneDelegate(), tdlib, this);
    toneHelper.setOnCustomEmojiSelectedListener(this::onCustomEmojiSelected);
    adapter = new MediaStickersAdapter(this, emojiController, false, this, null, false,  toneHelper);
    adapter.setClassicEmojiClickListener(this::onClassicEmojiClick);
    adapter.setRepaintingColorId(ColorId.text);

    this.useDarkMode = getArgumentsStrict().useDarkMode();

    emojiController.setArguments(getArguments());
    emojiController.setAdapter(adapter);
    emojiController.getValue();
    emojiController.recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140L));
    emojiController.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
        if (getArguments() != null && getArguments().getCurrentItem() == 0) {
          getArguments().setIsScrolling(isScrolling);
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (emojiController.isNeedIgnoreScroll()) return;

        if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_STICKERS && getArguments() != null && getArguments().isWatchingMovements() && getArguments().getCurrentItem() == 0) {
          int y = emojiController.getStickersScrollY(false);
          getArguments().moveHeader(y);
          getArguments().setCurrentStickerSectionByPosition(EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID, emojiController.getStickerSetSection(EmojiLayout.getHeaderSize() / 2), true, true);
         //  getArguments().onSectionScroll(EmojiMediaType.STICKER, dy != 0);
        }

        /*if (getArguments() != null && getArguments().isWatchingMovements() && getArguments().getCurrentItem() == 0) {
          getArguments().onScroll(getCurrentScrollY());
          if (!emojiController.scrollAnimationIsActive()) {
            getArguments().setCurrentEmojiSection(emojiController.getStickerSetSection());
          }
        }*/
      }
    });

    trendingAdapter = new MediaStickersAdapter(this, trendingSetsController, false, this);
    trendingAdapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PROGRESS));
    trendingSetsController.setArguments(getArguments());
    trendingSetsController.setAdapter(trendingAdapter);
    trendingSetsController.setSpanCount(8);

    contentView = new EmojiLayoutSectionPager(context) {
      @Override
      protected View getSectionView (int section) {
        if (section == SECTION_STICKERS) {
          return emojiController.recyclerView;
        } else if (section == SECTION_TRENDING) {
          initTrending();
          return trendingSetsController.recyclerView;
          /*return trendingView;*/
        }
        return null;
      }

      @Override
      protected void onSectionChangeStart (int prevSection, int nextSection, int stickerSetSection) {
        if (getArguments() != null && stickerSetSection != -1) {
          getArguments().setCurrentStickerSectionByPosition(EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID, nextSection == SECTION_STICKERS ? stickerSetSection : 0, nextSection == SECTION_STICKERS, true);
        }
      }

      @Override
      protected void onSectionChangeEnd (int prevSection, int currentSection) {
        if (getArguments() != null) {
          getArguments().resetScrollState(false);
        }

        if (prevSection == SECTION_TRENDING && currentSection != SECTION_TRENDING) {
          trendingSetsController.applyScheduledFeaturedSets();
        }
      }
    };
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.init(SECTION_STICKERS);

    buildEmojis();
    loadStickers();
    trendingSetsController.loadTrending(0, 20, 0);

    return contentView;
  }

  public void resetRecentEmoji () {
    emojiController.onResetRecentEmoji();
  }

  @Override
  public void destroy () {
    super.destroy();
    if (!onlyClassicEmoji) {
      tdlib.listeners().unsubscribeFromStickerUpdates(this);
    }
    emojiController.destroy();
    trendingSetsController.destroy();
  }

  public int getCurrentScrollY () {
    switch (contentView.getCurrentSection()) {
      case SECTION_STICKERS: {
        return emojiController.getStickersScrollY(false);
      }
      case SECTION_TRENDING: {
        return trendingSetsController.getStickersScrollY(false);
      }
    }
    return -1;
  }

  public void invalidateItems () {
    final int first = emojiController.getManager().findFirstVisibleItemPosition();
    final int last = emojiController.getManager().findLastVisibleItemPosition();

    for (int i = first; i <= last; i++) {
      View view = emojiController.getManager().findViewByPosition(i);
      if (view != null) {
        view.invalidate();
      } else {
        adapter.notifyItemChanged(i);
      }
    }
  }

  private void onClassicEmojiClick (View v) {
    if (!(v instanceof EmojiView)) {
      return;
    }
    EmojiView emojiView = (EmojiView) v;
    String rawEmoji = emojiView.getRawEmoji();
    String emoji = emojiView.getEmojiColored();
    if (StringUtils.isEmpty(rawEmoji)) {
      return;
    }
    final int viewId = v.getId();
    if (viewId == R.id.emoji) {
      Emoji.instance().saveRecentEmoji(rawEmoji);
    } else if (viewId == R.id.emoji_recent) {
      // Nothing to do?
    }
    if (getArguments() != null) {
      getArguments().onEnterEmoji(emoji);
    }
  }

  private void onCustomEmojiSelected (TGStickerObj stickerObj) {
    if (getArguments() != null) {
      getArguments().onEnterCustomEmoji(stickerObj);
    }
  }

  public void checkSpanCount () {
    emojiController.checkSpanCount();
  }

  public void showEmojiSection (int section) {
    if (contentView.canChangeSection()) {
      TGStickerSetInfo info = emojiController.getStickerSetBySectionIndex(section);
      int position = section != 0 && info != null ? info.getStartIndex() : 0;

      emojiController.scrollToStickerSet(position, false, contentView.getCurrentSection() != SECTION_TRENDING);
      if (contentView.getCurrentSection() == SECTION_TRENDING) {
        showStickers();
      }
    }
  }

  private void buildEmojis () {
    ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>(1);
    ArrayList<TGStickerSetInfo> emojiPacks = new ArrayList<>(8);

    items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));

    ArrayList<MediaStickersAdapter.StickerItem> recentEmojiItems = emojiController.makeRecentEmojiItems();
    if (!recentEmojiItems.isEmpty()) {
      TGStickerSetInfo pack = TGStickerSetInfo.fromEmojiSection(tdlib, EmojiSection.SECTION_EMOJI_RECENT, -1, recentEmojiItems.size());
      pack.setStartIndex(items.size());
      pack.setIsRecent();
      emojiPacks.add(pack);
      items.addAll(recentEmojiItems);
    }

    for (int i = 0; i < EmojiData.dataColored.length; i++) {
      String[] emoji = EmojiData.dataColored[i];
      TGStickerSetInfo pack = null;
      switch (i) {
        case 0: pack = TGStickerSetInfo.fromEmojiSection(tdlib, EmojiSection.SECTION_EMOJI_SMILEYS, R.string.SmileysAndPeople, emoji.length); break;
        case 1: pack = TGStickerSetInfo.fromEmojiSection(tdlib, EmojiSection.SECTION_EMOJI_ANIMALS, R.string.AnimalsAndNature, emoji.length); break;
        case 2: pack = TGStickerSetInfo.fromEmojiSection(tdlib, EmojiSection.SECTION_EMOJI_FOOD, R.string.FoodDrink, emoji.length); break;
        case 3: pack = TGStickerSetInfo.fromEmojiSection(tdlib, EmojiSection.SECTION_EMOJI_TRAVEL, R.string.TravelAndPlaces, emoji.length); break;
        case 4: pack = TGStickerSetInfo.fromEmojiSection(tdlib, EmojiSection.SECTION_EMOJI_SYMBOLS, R.string.SymbolsAndObjects, emoji.length); break;
        case 5: pack = TGStickerSetInfo.fromEmojiSection(tdlib, EmojiSection.SECTION_EMOJI_FLAGS, R.string.Flags, emoji.length); break;
      }
      if (pack != null) {
        pack.setStartIndex(items.size());
        items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, pack));
        emojiPacks.add(pack);
      }
      items.ensureCapacity(items.size() + emoji.length + 1);
      for (String emojiCode : emoji) {
        items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_DEFAULT_EMOJI, new TGDefaultEmoji(emojiCode)));
      }
    }

    emojiController.setDefaultEmojiPacks(emojiPacks, items);
  }

  /* * */

  // private RecyclerView trendingView;

  public void showStickers () {
    if (contentView.canChangeSection()) {
      contentView.changeSection(SECTION_STICKERS, false, -1);
    }
  }

  public void showTrending () {
    if (contentView.canChangeSection()) {
      initTrending();
      if (contentView.getCurrentSection() == SECTION_TRENDING) {
        trendingSetsController.recyclerView.smoothScrollBy(0, -trendingSetsController.getStickersScrollY(false));
      } else {
        contentView.changeSection(SECTION_TRENDING, true, -1);
        if (getArguments() != null && getArguments().getCurrentItem() == 0) {
          getArguments().setCurrentStickerSectionByPosition(EmojiLayout.EMOJI_INSTALLED_CONTROLLER_ID, 0, false, true);
        }
      }
    }
  }

  private void initTrending () {
    if (trendingSetsController.recyclerView == null) {
      trendingSetsController.getValue();
      trendingSetsController.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
          if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_TRENDING && getArguments() != null && getArguments().getCurrentItem() == 0) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_TRENDING && getArguments() != null && getArguments().getCurrentItem() == 0) {
            trendingSetsController.onScrolledImpl(dy, false);
          }
        }
      });
    }
  }

  /* * */

  private boolean loadingStickers;

  private void loadStickers () {
    if (!loadingStickers && !onlyClassicEmoji) {
      loadingStickers = true;
      tdlib.client().send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeCustomEmoji()), stickerSetsHandler());
    }
  }

  private Client.ResultHandler stickerSetsHandler () {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.StickerSets.CONSTRUCTOR: {
          TdApi.StickerSetInfo[] rawStickerSets = ((TdApi.StickerSets) object).sets;

          final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>(rawStickerSets.length);
          final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();

          if (rawStickerSets.length > 0) {
            int startIndex = this.adapter.getItemCount();

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
                sticker.setStickerSetId(rawInfo.id, null);
                sticker.setDataProvider(stickerSetsDataProvider());
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
              }
              startIndex += rawInfo.size + 1;
            }
          }

          runOnUiThreadOptional(() -> {
            if (getArguments() != null) {
              getArguments().setEmojiPacks(stickerSets);
            }
            setStickers(stickerSets, items);
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

  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.emojiController.setStickers(stickerSets, items);
    this.loadingStickers = false;
    if (stickerSetsDataProvider != null) {
      stickerSetsDataProvider.clear();
    }
    if (!onlyClassicEmoji) {
      tdlib.listeners().subscribeToStickerUpdates(this);
    }
  }

  public void applyScheduledChanges () {
    trendingSetsController.applyScheduledFeaturedSets();
  }

  public boolean showStickerSet (TGStickerSetInfo stickerSet) {
    if (contentView.canChangeSection()) {
      int i = emojiController.indexOfStickerSet(stickerSet);
      if (i != -1) {
        emojiController.scrollToStickerSet(i, false, contentView.getCurrentSection() == SECTION_STICKERS && contentView.isAnimationNotActive());
        return contentView.changeSection(SECTION_STICKERS, false, emojiController.indexOfStickerSetByAdapterPosition(i));
      }
    }
    return false;
  }


  /* Sticker updates */

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

  private int getSystemSetsCount () {
    int i = 0;
    for (TGStickerSetInfo info : emojiController.stickerSets) {
      if (info.isSystem() || info.isFakeClassicEmoji()) {
        i++;
      }
    }
    return i;
  }

  private boolean hasNoStickerSets () {
    for (TGStickerSetInfo info : emojiController.stickerSets) {
      if (!info.isFavorite() && !info.isRecent() && !info.isFakeClassicEmoji()) {
        return false;
      }
    }
    return true;
  }

  private void changeStickers (long[] stickerSetIds) {
    trendingSetsController.updateTrendingSets(stickerSetIds);
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
    // LongSparseArray<Integer> currentStickerSets = new LongSparseArray<>(stickersController.stickerSets.size());
    LongSparseArray<TGStickerSetInfo> removedStickerSets = new LongSparseArray<>(emojiController.stickerSets.size());
    // int currentSetIndex = 0;
    for (TGStickerSetInfo stickerSet : emojiController.stickerSets) {
      if (!stickerSet.isSystem() && !stickerSet.isFakeClassicEmoji()) {
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
      emojiController.removeStickerSet(stickerSet);
    }

    // Then, move items
    if (positions != null && !emojiController.stickerSets.isEmpty() ) {
      for (int j = 0; j < positions.size(); j++) {
        long setId = positions.keyAt(j);
        int newPosition = positions.valueAt(j);
        int currentPosition = emojiController.indexOfStickerSetById(setId);
        if (currentPosition == -1) {
          throw new RuntimeException();
        }
        if (currentPosition != newPosition) {
          int systemSetsCount = getSystemSetsCount();
          emojiController.moveStickerSet(currentPosition + systemSetsCount, newPosition + systemSetsCount);
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
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, parsed));
              i++;
            }

            runOnUiThreadOptional(() -> emojiController.addStickerSet(stickerSet, items, insertIndex + getSystemSetsCount()));
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

  @Override
  public void onTrendingStickersUpdated (final TdApi.StickerType stickerType, final TdApi.TrendingStickerSets stickerSets, int unreadCount) {
    if (stickerType.getConstructor() != TdApi.StickerTypeCustomEmoji.CONSTRUCTOR)
      return;
    runOnUiThreadOptional(() -> {
      if (getArguments() != null) {
        getArguments().setHasNewHots(EmojiLayout.EMOJI_TRENDING_CONTROLLER_ID, TD.getStickerSetsUnreadCount(stickerSets.sets) > 0);
      }
      trendingSetsController.scheduleFeaturedSets(stickerSets, contentView.getCurrentSection() == SECTION_TRENDING);
    });
  }

  @Override
  public void onInstalledStickerSetsUpdated (final long[] stickerSetIds, TdApi.StickerType stickerType) {
    if (stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR) {
      runOnUiThreadOptional(() -> {
        if (!loadingStickers) {
          changeStickers(stickerSetIds);
        }
      });
    }
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
        return emojiController.isIgnoreRequests(stickerSetId);
      }

      @Override
      protected int getLoadingFlags (long stickerSetId, TGStickerObj stickerObj) {
        return stickerObj.isTrending() ? FLAG_TRENDING: FLAG_REGULAR;
      }

      @Override
      protected void applyStickerSet (TdApi.StickerSet stickerSet, int flags) {
        if ((flags & FLAG_REGULAR) != 0) {
          emojiController.applyStickerSet(stickerSet, this);
        }
        if ((flags & FLAG_TRENDING) != 0) {
          trendingSetsController.applyStickerSet(stickerSet, this);
        }
      }
    };
  }
}
