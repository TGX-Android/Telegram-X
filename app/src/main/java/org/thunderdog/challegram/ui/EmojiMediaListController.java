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
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.emoji.GifView;
import org.thunderdog.challegram.component.emoji.MediaGifsAdapter;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGGif;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.AnimationsListener;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StickerSetsDataProvider;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.NewFlowLayoutManager;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutRecyclerController;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutSectionPager;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutTrendingController;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;

public class EmojiMediaListController extends ViewController<EmojiLayout> implements
  StickersListener,
  AnimationsListener,
  MediaGifsAdapter.Callback,
  ClickHelper.Delegate,
  ForceTouchView.ActionListener {

  private static final int SECTION_STICKERS = 0;
  private static final int SECTION_GIFS = 1;
  private static final int SECTION_TRENDING = 2;

  private EmojiLayoutSectionPager contentView;
  private final EmojiLayoutRecyclerController stickersController;
  private final EmojiLayoutTrendingController trendingSetsController;

  public EmojiMediaListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    stickersController = new EmojiLayoutRecyclerController(context, tdlib, EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID);
    trendingSetsController = new EmojiLayoutTrendingController(context, tdlib, EmojiLayout.STICKERS_TRENDING_CONTROLLER_ID);
    trendingSetsController.setCallbacks(stickerSetsDataProvider(), new TdApi.StickerTypeRegular());
    trendingSetsController.stickerSets = new ArrayList<>();
  }

  @Override
  public int getId () {
    return R.id.controller_emojiMedia;
  }

  private MediaStickersAdapter stickersAdapter;
  private MediaStickersAdapter trendingAdapter;
  private MediaGifsAdapter gifsAdapter;
  private RecyclerView gifsView;

  @Override
  protected View onCreateView (Context context) {
    stickersAdapter = new MediaStickersAdapter(this, stickersController, false, this) {
      @Override
      protected void onToggleCollapseRecentStickers (TextView collapseView, TGStickerSetInfo recentSet) {
        boolean needExpand = recentSet.isCollapsed();
        int endIndex = recentSet.getEndIndex();
        int existingIndex = findRecentStickerSet();
        int visibleItemCount = recentSet.getSize();
        if (needExpand) {
          TdApi.Sticker[] allStickers = recentSet.getAllStickers();
          recentSet.setSize(allStickers.length);
          shiftStickerSets(existingIndex, recentSet.getStartIndex());

          ArrayList<MediaStickersAdapter.StickerItem> stickers = new ArrayList<>();
          for (int i = visibleItemCount; i < allStickers.length; i++) {
            TdApi.Sticker rawSticker = allStickers[i];
            TGStickerObj sticker = valueOf(rawSticker, recentSet.isFavorite());
            stickers.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
          }
          stickersAdapter.addRange(endIndex, stickers);
        } else {
          recentSet.setSize(Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT);
          stickersAdapter.removeRange(recentSet.getEndIndex(), endIndex - recentSet.getEndIndex());
          shiftStickerSets(existingIndex, recentSet.getStartIndex());
        }
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_EXPAND_RECENT_STICKERS, needExpand);
      }
    };
    stickersController.setArguments(getArguments());
    stickersController.setAdapter(stickersAdapter);
    stickersController.setSpanCount(spanCount);

    trendingAdapter = new MediaStickersAdapter(this, trendingSetsController, true, this);
    trendingAdapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PROGRESS));
    trendingSetsController.setArguments(getArguments());
    trendingSetsController.setAdapter(trendingAdapter);
    trendingSetsController.setSpanCount(5);

    gifsAdapter = new MediaGifsAdapter(context, this);
    gifsAdapter.setCallback(this);

    contentView = new EmojiLayoutSectionPager(context) {

      @Override
      protected View getSectionView (int section) {
        if (section == SECTION_STICKERS) {
          initStickers();
          return stickersController.recyclerView;
        } else if (section == SECTION_TRENDING) {
          initHots();
          return trendingSetsController.recyclerView;
        } else if (section == SECTION_GIFS) {
          initGIFs();
          return gifsView;
        }
        return null;
      }

      @Override
      protected void onSectionChangeStart (int prevSection, int nextSection, int stickerSetSection) {
        if (getArguments() != null) {
          if (getArguments().getCurrentItem() == 1) {
            if (prevSection == SECTION_GIFS && (nextSection == SECTION_STICKERS || nextSection == SECTION_TRENDING)) {
              getArguments().setCircleVisible(false, false);
            } else if ((prevSection == SECTION_STICKERS || prevSection == SECTION_TRENDING) && nextSection == SECTION_GIFS) {
              getArguments().setCircleVisible(true, true);
            }
          }
          getArguments().setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID,
            nextSection == SECTION_STICKERS ? (stickerSetSection != -1 ? stickerSetSection : stickersController.getStickerSetSection()) :
              nextSection == SECTION_TRENDING ? 2 : 1
            , nextSection == SECTION_STICKERS,true);
        }
      }

      @Override
      protected void onSectionChangeEnd (int prevSection, int currentSection) {
        if (getArguments() != null) {
          if (prevSection == SECTION_GIFS && (currentSection == SECTION_STICKERS || currentSection == SECTION_TRENDING)) {
            getArguments().setPreferredSection(EmojiMediaType.STICKER);
          } else if (currentSection == SECTION_GIFS && (prevSection == SECTION_STICKERS || prevSection == SECTION_TRENDING)) {
            getArguments().setPreferredSection(EmojiMediaType.GIF);
          }
        }

        if (prevSection == SECTION_TRENDING && currentSection != SECTION_TRENDING) {
          trendingSetsController.applyScheduledFeaturedSets();
        }

        if (getArguments() != null) {
          EmojiLayout.Listener listener = getArguments().getListener();
          if (listener != null) {
            int prevSectionX = prevSection == SECTION_GIFS ? EmojiMediaType.GIF : EmojiMediaType.STICKER;
            int newSection = currentSection == SECTION_GIFS ? EmojiMediaType.GIF : EmojiMediaType.STICKER;
            listener.onSectionSwitched(getArguments(), newSection, prevSectionX);
          }
          getArguments().resetScrollState(false);
        }
      }
    };
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    checkSpanCount();

    int currentSection = Settings.instance().getEmojiMediaSection();
    switch (currentSection) {
      case EmojiMediaType.STICKER: {
        this.contentView.init(SECTION_STICKERS);
        break;
      }
      case EmojiMediaType.GIF: {
        if (getArguments() != null) {
          getArguments().setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, 1, false, false);
          getArguments().setMediaSection(true);
        }
        this.contentView.init(SECTION_GIFS);
        break;
      }
    }

    loadGIFs(); // to show or hide GIF section
    loadStickers(); // to show sections
    trendingSetsController.loadTrending(0, 20, 0); // to show blue badge?

    return contentView;
  }

  public int getCurrentScrollY () {
    switch (contentView.getCurrentSection()) {
      case SECTION_GIFS: {
        return getGIFsScrollY();
      }
      case SECTION_STICKERS: {
        return stickersController.getStickersScrollY(showRecentTitle);
      }
      case SECTION_TRENDING: {
        return trendingSetsController.getStickersScrollY(showRecentTitle);
      }
    }
    return -1;
  }

  // Stickers

  public void removeRecentStickers () {
    setRecentStickers(null, null);
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (stickersController.recyclerView != null)
      stickersController.recyclerView.requestLayout();
    if (trendingAdapter != null)
      trendingAdapter.notifyDataSetChanged();
  }

  private void initStickers () {
    if (stickersController.recyclerView == null) {
      stickersController.getValue();
      stickersController.recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180));
      stickersController.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
          if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_STICKERS && getArguments() != null && getArguments().getCurrentItem() == 1) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (!stickersController.isNeedIgnoreScroll()) {
            if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_STICKERS && getArguments() != null && getArguments().isWatchingMovements() && getArguments().getCurrentItem() == 1) {
              getArguments().moveHeader(stickersController.getStickersScrollY(showRecentTitle));
              getArguments().setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, stickersController.getStickerSetSection(), true, true);
              getArguments().onSectionInteractedScroll(EmojiMediaType.STICKER, dy != 0);
            }
          }
        }
      });
    }
  }

  // GIFs

  private int getGIFsScrollY () {
    if (gifsView != null) {
      NewFlowLayoutManager manager = (NewFlowLayoutManager) gifsView.getLayoutManager();
      int i = manager.findFirstVisibleItemPosition();
      if (i == 0) {
        View view = manager.findViewByPosition(0);
        return view != null ? -manager.getDecoratedTop(view) : 0;
      } else {
        return EmojiLayout.getHeaderSize();
      }
    }
    return 0;
  }

  private void initGIFs () {
    if (gifsView == null) {
      final NewFlowLayoutManager manager = new NewFlowLayoutManager(context(), 100) {
        private final Size size = new Size();

        @Override
        protected Size getSizeForItem (int i) {
          TGGif gif = gifsAdapter.getGif(i);
          size.width = gif.width();
          size.height = gif.height();
          if (size.width == 0) {
            size.width = 100;
          }
          if (size.height == 0) {
            size.height = 100;
          }
          return size;
        }
      };
      manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
        @Override
        public int getSpanSize(int position) {
          return manager.getSpanSizeForItem(position);
        }
      });

      gifsView = new RecyclerView(context()); // (RecyclerView) Views.inflate(R.layout.recycler, contentView);
      gifsView.setItemAnimator(null);
      gifsView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      gifsView.setHasFixedSize(true);
      gifsView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
      gifsView.setAdapter(gifsAdapter);
      gifsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
          if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_GIFS && getArguments() != null && getArguments().getCurrentItem() == 1) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_GIFS && getArguments() != null && getArguments().getCurrentItem() == 1) {
            getArguments().moveHeaderFull(getGIFsScrollY());
            getArguments().onSectionInteractedScroll(EmojiMediaType.GIF, dy != 0);
          }
        }
      });
      gifsView.addItemDecoration(new RecyclerView.ItemDecoration() {
        @Override
        public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
          int position = parent.getChildAdapterPosition(view);
          outRect.top = manager.isFirstRow(position) ? Screen.dp(4f) + EmojiLayout.getHeaderSize() : 0;
          outRect.right = manager.isLastInRow(position) ? 0 : Screen.dp(3f);
          outRect.bottom = Screen.dp(3f);
        }
      });
      gifsView.setLayoutManager(manager);
    }
  }

  public float getDesiredHeaderHideFactor () {
    return contentView.getCurrentSection() == SECTION_GIFS ? Math.min(1f, Math.max(0f, (float) getGIFsScrollY() / (float) EmojiLayout.getHeaderSize())) : 0f;
  }

  @Override
  public void onGifPressed (View view, TdApi.Animation animation) {
    sendGif(view, animation);
  }

  @Override
  public void onGifLongPressed (View view, TdApi.Animation animation) {
    removeGif(animation);
  }

  private void removeGif (final TdApi.Animation animation) {
    ViewController<?> c = context().navigation().getCurrentStackItem();
    if (c != null) {
      c.showOptions(Lang.getString(R.string.RemoveGifConfirm), new int[]{R.id.btn_deleteGif, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_deleteGif) {
          gifsAdapter.removeSavedGif(animation.animation.id);
          if (gifsAdapter.getItemCount() == 0) {
            showStickers();
          }
          tdlib.client().send(new TdApi.RemoveSavedAnimation(new TdApi.InputFileId(animation.animation.id)), tdlib.okHandler());
        }
        return true;
      });
    }
  }

  private void sendGif (View view, TdApi.Animation animation) {
    if (getArguments() != null) {
      getArguments().sendGif(view, animation);
    }
  }

  // GIFs force touch

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return true;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    sendGif(view, ((GifView) view).getGif().getAnimation());
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
      removeGif(animation);
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
      gifsView.requestDisallowInterceptTouchEvent(true);
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
    gifsView.requestDisallowInterceptTouchEvent(false);
    context().closeForceTouch();
  }

  @Override
  public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) { }

  @Override
  public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
    if (actionId == R.id.btn_deleteGif) {
      removeGif((TdApi.Animation) arg);
    } else if (actionId == R.id.btn_send) {
      sendGif(context.getSourceView(), (TdApi.Animation) arg);
    }
  }

  // Trending stickers

  public static int parseTrending (Tdlib tdlib, ArrayList<TGStickerSetInfo> parsedStickerSets, ArrayList<MediaStickersAdapter.StickerItem> items, int offset, TdApi.StickerSetInfo[] stickerSets, TGStickerObj.DataProvider dataProvider, @Nullable TGStickerSetInfo.ViewCallback viewCallback, boolean needSeparators, boolean isEmojiStatuses, String highlight) {
    int unreadItemCount = 0;
    parsedStickerSets.ensureCapacity(stickerSets.length);
    items.ensureCapacity(items.size() + stickerSets.length * 2 + 1);
    int startIndex = offset + items.size();
    boolean first = true;
    for (TdApi.StickerSetInfo stickerSetInfo : stickerSets) {
      if (!stickerSetInfo.isViewed) {
        unreadItemCount++;
      }
      TGStickerSetInfo stickerSet = new TGStickerSetInfo(tdlib, stickerSetInfo);
      stickerSet.setViewCallback(viewCallback);
      if (isEmojiStatuses) {
        stickerSet.setIsTrendingEmoji();
      } else {
        stickerSet.setIsTrending();
      }
      if (needSeparators) {
        if (first) {
          first = false;
        } else {
          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_SEPARATOR));
          startIndex++;
        }
      }
      parsedStickerSets.add(stickerSet);
      stickerSet.setStartIndex(startIndex);
      if (isEmojiStatuses) {
        items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, stickerSet).setHighlightValue(highlight));
      } else {
        items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER_TRENDING, stickerSet).setHighlightValue(highlight));
      }
      int itemCount = isEmojiStatuses ? stickerSetInfo.size : (stickerSetInfo.stickerType.getConstructor() == TdApi.StickerTypeCustomEmoji.CONSTRUCTOR ? 16 : 5);
      for (int i = 0; i < itemCount; i++) {
        TGStickerObj stickerObj = new TGStickerObj(tdlib, i < stickerSetInfo.covers.length ? stickerSetInfo.covers[i] : null, null, stickerSetInfo.stickerType);
        stickerObj.setStickerSetId(stickerSetInfo.id, null);
        stickerObj.setDataProvider(dataProvider);
        stickerObj.setIsTrending();
        items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, stickerObj));
      }
      startIndex += itemCount + 1;
    }
    return unreadItemCount;
  }

  private void initHots () {
    if (trendingSetsController.recyclerView == null) {
      trendingSetsController.getValue();
      trendingSetsController.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
          if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_TRENDING && getArguments() != null && getArguments().getCurrentItem() == 1) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (contentView.isSectionStable() && contentView.getCurrentSection() == SECTION_TRENDING && getArguments() != null && getArguments().getCurrentItem() == 1) {
            trendingSetsController.onScrolledImpl(dy, showRecentTitle);
          }
        }
      });
    }
  }

  // Controller lifecycle

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromGlobalUpdates(this);
    stickersController.destroy();
    trendingSetsController.destroy();
  }

  // When rotation, etc

  private int spanCount;

  public void checkSpanCount () {
    int spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight());
    if (this.spanCount != spanCount) {
      this.spanCount = spanCount;
      stickersController.setSpanCount(spanCount);
      if (gifsView != null) {
        gifsView.invalidateItemDecorations();
      }
    }
  }

  private static int calculateSpanCount (int width, int height) {
    int minSide = Math.min(width, height);
    int minWidth = minSide / 5;
    return minWidth == 0 ? 5 : width / minWidth;
  }

  public static int getEstimateColumnResolution () {
    int spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight());
    return Screen.currentWidth() / spanCount;
  }

  @EmojiMediaType
  public int getMediaSection () {
    int currentSection = this.contentView.getNextSection() != -1 ? this.contentView.getNextSection() : this.contentView.getCurrentSection();
    if (currentSection == SECTION_GIFS) {
      return EmojiMediaType.GIF;
    } else {
      return EmojiMediaType.STICKER;
    }
  }

  // Stickers logic

  /*@Override
  public void onStickersUpdated () {
    TGDataManager.runOnUiThread(new Runnable() {
      @Override
      public void run () {
        if (!isDestroyed() && stickersView != null) {
          loadStickers();
        }
      }
    });
  }*/

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

  private boolean loadingRecentStickers, loadingFavoriteStickers;

  private void shiftStickerSets (int startPosition, int startIndex) {
    for (int i = startPosition; i < stickersController.stickerSets.size(); i++) {
      TGStickerSetInfo stickerSet = stickersController.stickerSets.get(i);
      stickerSet.setStartIndex(startIndex);
      startIndex = stickerSet.getEndIndex();
    }
  }

  private static final int MAX_HEADER_COUNT = 2;

  private int findRecentStickerSet () {
    if (stickersController.stickerSets != null && !stickersController.stickerSets.isEmpty()) {
      int i = 0;
      for (TGStickerSetInfo set : stickersController.stickerSets) {
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

  private int findFavoriteStickerSet () {
    if (stickersController.stickerSets != null && !stickersController.stickerSets.isEmpty()) {
      int i = 0;
      for (TGStickerSetInfo set : stickersController.stickerSets) {
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

  private int getRecentStartIndex () {
    int i = findFavoriteStickerSet();
    if (i != -1) {
      return stickersController.stickerSets.get(i).getEndIndex();
    }
    return 1;
  }

  private boolean needExpandRecentStickers () {
    return Settings.instance().getNewSetting(Settings.SETTING_FLAG_EXPAND_RECENT_STICKERS);
  }

  private boolean smartUpdateStickerPack (
    int stickerSetIndex, TGStickerSetInfo stickerSet, TdApi.Sticker[] newStickers,
    @NonNull ArrayList<MediaStickersAdapter.StickerItem> newItems, int visibleItemCount
  ) {
    if (stickersController == null || stickersAdapter == null) {
      return false;
    }
    LinearLayoutManager manager = (LinearLayoutManager) stickersController.getManager();
    if (manager == null) {
      return false;
    }
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    View firstView = manager.findViewByPosition(firstVisiblePosition);
    int firstViewOffset = firstView != null ? firstView.getTop() : 0;

    int oldSize = stickerSet.getSize();
    int newSize = newItems.size();
    int headerItemCount = stickerSet.isFavorite() ? 0 : 1; /*title*/
    int positionStartIndex = stickerSet.getStartIndex() + headerItemCount;

    ArrayList<MediaStickersAdapter.StickerItem> oldItems = new ArrayList<>();
    for (int index = positionStartIndex; index < stickerSet.getEndIndex(); index++) {
      oldItems.add(stickersAdapter.getItem(index));
    }

    DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
      @Override
      public int getOldListSize () {
        return oldSize;
      }

      @Override
      public int getNewListSize () {
        return newSize;
      }

      @Override
      public boolean areItemsTheSame (int oldItemPosition, int newItemPosition) {
        TGStickerObj oldSticker = oldItems.get(oldItemPosition).sticker;
        TGStickerObj newSticker = newItems.get(newItemPosition).sticker;
        return oldSticker.equals(newSticker);
      }

      @Override
      public boolean areContentsTheSame (int oldItemPosition, int newItemPosition) {
        return areItemsTheSame(oldItemPosition, newItemPosition);
      }
    });
    if (newStickers != null) {
      stickerSet.setStickers(newStickers, visibleItemCount);
    } else {
      stickerSet.setSize(visibleItemCount);
    }
    stickersAdapter.removeRange(positionStartIndex, oldItems.size(), false);
    stickersAdapter.insertRange(positionStartIndex, newItems, false);
    shiftStickerSets(stickerSetIndex, stickerSet.getStartIndex());
    diffResult.dispatchUpdatesTo(new ListUpdateCallback() {
      @Override
      public void onInserted (int position, int count) {
        stickersAdapter.notifyItemRangeInserted(positionStartIndex + position, count);
      }

      @Override
      public void onRemoved (int position, int count) {
        stickersAdapter.notifyItemRangeRemoved(positionStartIndex + position, count);
      }

      @Override
      public void onMoved (int fromPosition, int toPosition) {
        stickersAdapter.notifyItemMoved(positionStartIndex + fromPosition, positionStartIndex + toPosition);
      }

      @Override
      public void onChanged (int position, int count, @Nullable Object payload) {
        stickersAdapter.notifyItemRangeChanged(positionStartIndex + position, count, payload);
      }
    });
    manager.scrollToPositionWithOffset(firstVisiblePosition, firstViewOffset);
    return true;
  }

  private void setRecentStickers (@Nullable TdApi.Sticker[] recentStickers, @Nullable ArrayList<MediaStickersAdapter.StickerItem> items) {
    boolean haveRecentStickers = recentStickers != null && recentStickers.length > 0;
    if (getArguments() != null) {
      getArguments().setShowRecents(haveRecentStickers);
    }

    TGStickerSetInfo recentSet;
    int existingIndex = findRecentStickerSet();

    int totalRecentCount = items != null ? items.size() : 0;
    int visibleRecentCount = recentStickers != null ? Math.min(
      totalRecentCount,
      !needExpandRecentStickers() ?
        Math.min(totalRecentCount, Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT) :
        totalRecentCount
    ) : 0;

    boolean haveFavorites = findFavoriteStickerSet() != -1;

    allowCollapseRecent = totalRecentCount > Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT;
    showRecentTitle = haveRecentStickers && (
      Config.FORCE_SHOW_RECENTS_STICKERS_TITLE ||
        allowCollapseRecent ||
        haveFavorites
    );

    if (visibleRecentCount > 0) {
      for (int i = items.size() - 1; i >= visibleRecentCount; i--) {
        items.remove(i);
      }
    }

    if (existingIndex != -1) {
      if (haveRecentStickers) {
        recentSet = stickersController.stickerSets.get(existingIndex);
        TdApi.Sticker[] prevRecentStickers = recentSet.getAllStickers();
        if (prevRecentStickers != null && prevRecentStickers.length > 0 && items != null && !items.isEmpty()) {
          if (smartUpdateStickerPack(existingIndex, recentSet, recentStickers, items, visibleRecentCount)) {
            setShowRecentTitle(showRecentTitle, allowCollapseRecent);
            if (showRecentTitle && allowCollapseRecent && stickersController != null && recentSet.isCollapsed()) {
              // direct update of show X more
              View view = stickersController.getManager().findViewByPosition(recentSet.getStartIndex());
              if (view instanceof ViewGroup) {
                stickersAdapter.updateCollapseView((ViewGroup) view, recentSet);
              }
            }
            return;
          }
        }
      }

      // Too many changes for simple animation update
      recentSet = stickersController.stickerSets.remove(existingIndex);
      shiftStickerSets(existingIndex, recentSet.getStartIndex());
      stickersAdapter.removeRange(recentSet.getStartIndex(), recentSet.getItemCount());
    } else if (visibleRecentCount > 0) {
      recentSet = new TGStickerSetInfo(tdlib, recentStickers, false, visibleRecentCount);
    } else {
      recentSet = null;
    }

    if (visibleRecentCount > 0) {
      items.add(0, new MediaStickersAdapter.StickerItem(getRecentTitleViewType(), recentSet));

      int startIndex = getRecentStartIndex();
      recentSet.setStickers(recentStickers, visibleRecentCount);
      recentSet.setStartIndex(startIndex);
      int stickerSetIndex = haveFavorites ? 1 : 0;
      stickersController.stickerSets.add(stickerSetIndex, recentSet);
      shiftStickerSets(stickerSetIndex + 1, recentSet.getEndIndex());
      stickersAdapter.insertRange(startIndex, items);
    }
  }

  private int getRecentTitleViewType () {
    return showRecentTitle ? (allowCollapseRecent ? MediaStickersAdapter.StickerHolder.TYPE_HEADER_COLLAPSABLE : MediaStickersAdapter.StickerHolder.TYPE_HEADER) : MediaStickersAdapter.StickerHolder.TYPE_EMPTY;
  }

  private boolean showRecentTitle, allowCollapseRecent;

  private void setShowRecentTitle (boolean show, boolean allowToggleCollapse) {
    this.showRecentTitle = show;
    boolean prevAllowCollapseRecent = this.allowCollapseRecent;
    this.allowCollapseRecent = allowToggleCollapse;
    int i = findRecentStickerSet();
    if (i != -1) {
      int index = stickersController.stickerSets.get(i).getStartIndex();
      if (stickersAdapter.getItem(index).setViewType(getRecentTitleViewType()) || prevAllowCollapseRecent != allowToggleCollapse) {
        stickersAdapter.notifyItemChanged(index);
      }
    }
  }

  private void setFavoriteStickers (@Nullable TdApi.Sticker[] favoriteStickers, @Nullable ArrayList<MediaStickersAdapter.StickerItem> items) {
    boolean haveFavoriteStickers = favoriteStickers != null && favoriteStickers.length > 0;
    if (getArguments() != null) {
      getArguments().setShowFavorite(haveFavoriteStickers);
    }

    int recentStickerSetIndex = findRecentStickerSet();
    if (recentStickerSetIndex != -1) {
      TGStickerSetInfo recentSet = stickersController.stickerSets.get(recentStickerSetIndex);
      setShowRecentTitle(
        Config.FORCE_SHOW_RECENTS_STICKERS_TITLE ||
        recentSet.getFullSize() > Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT ||
        (favoriteStickers != null && favoriteStickers.length > 0),
        recentSet.getFullSize() > Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT
      );
    }

    TGStickerSetInfo favoriteSet;
    int existingIndex = findFavoriteStickerSet();

    if (existingIndex != -1) {
      favoriteSet = stickersController.stickerSets.get(existingIndex);
      if (haveFavoriteStickers && items != null && !items.isEmpty()) {
        if (smartUpdateStickerPack(existingIndex, favoriteSet, null, items, items.size())) {
          return;
        }
      }

      favoriteSet = stickersController.stickerSets.remove(existingIndex);
      shiftStickerSets(existingIndex, favoriteSet.getStartIndex());
      stickersAdapter.removeRange(favoriteSet.getStartIndex(), favoriteSet.getItemCount());
    } else if (favoriteStickers != null && favoriteStickers.length > 0) {
      favoriteSet = new TGStickerSetInfo(tdlib, favoriteStickers, true, 0);
    } else {
      favoriteSet = null;
    }

    if (favoriteSet != null && favoriteStickers != null && favoriteStickers.length > 0 && items != null) {
      int startIndex = 1; // getRecentStartIndex();
      favoriteSet.setSize(favoriteStickers.length);
      favoriteSet.setStartIndex(startIndex);
      int stickerSetIndex = 0; // findFavoriteStickerSet() != -1 ? 1 : 0;
      stickersController.stickerSets.add(stickerSetIndex, favoriteSet);
      shiftStickerSets(stickerSetIndex + 1, favoriteSet.getEndIndex());
      stickersAdapter.insertRange(startIndex, items);
    }
  }

  private TGStickerObj valueOf (TdApi.Sticker rawSticker, boolean isFavorite) {
    TGStickerObj sticker = new TGStickerObj(tdlib, rawSticker, null, rawSticker.fullType);
    if (isFavorite) {
      sticker.setIsFavorite();
    } else {
      sticker.setIsRecent();
    }
    return sticker;
  }

  private void processStickersImpl (final TdApi.Object object, final boolean areFavorite) {
    final ArrayList<MediaStickersAdapter.StickerItem> stickers;
    final TdApi.Sticker[] rawStickers;
    if (object.getConstructor() == TdApi.Stickers.CONSTRUCTOR) {
      rawStickers = ((TdApi.Stickers) object).stickers;
      stickers = new ArrayList<>(rawStickers.length);
      for (TdApi.Sticker rawSticker : rawStickers) {
        TGStickerObj sticker = valueOf(rawSticker, areFavorite);
        stickers.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
      }
    } else {
      stickers = null;
      rawStickers = null;
    }
    runOnUiThreadOptional(() -> {
      if (areFavorite) {
        if (loadingFavoriteStickers) {
          loadingFavoriteStickers = false;
          if (stickers != null) {
            setFavoriteStickers(rawStickers, stickers);
          }
        }
      } else {
        if (loadingRecentStickers) {
          loadingRecentStickers = false;
          if (stickers != null) {
            setRecentStickers(rawStickers, stickers);
          }
        }
      }
    });
  }

  private void reloadRecentStickers () {
    if (!loadingStickers && !loadingRecentStickers) {
      loadingRecentStickers = true;
      tdlib.client().send(new TdApi.GetRecentStickers(false), object -> processStickersImpl(object, false));
    }
  }

  private void reloadFavoriteStickers () {
    if (!loadingStickers && !loadingFavoriteStickers) {
      loadingFavoriteStickers = true;
      tdlib.client().send(new TdApi.GetFavoriteStickers(), object -> processStickersImpl(object, true));
    }
  }

  private void applyRecentStickers (int[] stickerIds) {
    reloadRecentStickers();
  }

  private void applyFavoriteStickers (int[] stickerIds) {
    reloadFavoriteStickers();
  }

  private boolean hasNoStickerSets () {
    for (TGStickerSetInfo info : stickersController.stickerSets) {
      if (!info.isFavorite() && !info.isRecent()) {
        return false;
      }
    }
    return true;
  }

  private int getSystemSetsCount () {
    int i = 0;
    for (TGStickerSetInfo info : stickersController.stickerSets) {
      if (info.isSystem()) {
        i++;
      }
    }
    return i;
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
    LongSparseArray<TGStickerSetInfo> removedStickerSets = new LongSparseArray<>(stickersController.stickerSets.size());
    // int currentSetIndex = 0;
    for (TGStickerSetInfo stickerSet : stickersController.stickerSets) {
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
      stickersController.removeStickerSet(stickerSet);
    }

    // Then, move items
    if (positions != null && !stickersController.stickerSets.isEmpty() ) {
      for (int j = 0; j < positions.size(); j++) {
        long setId = positions.keyAt(j);
        int newPosition = positions.valueAt(j);
        int currentPosition = stickersController.indexOfStickerSetById(setId);
        if (currentPosition == -1) {
          throw new RuntimeException();
        }
        if (currentPosition != newPosition) {
          int systemSetsCount = getSystemSetsCount();
          stickersController.moveStickerSet(currentPosition + systemSetsCount, newPosition + systemSetsCount);
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

            runOnUiThreadOptional(() -> stickersController.addStickerSet(stickerSet, items, insertIndex + getSystemSetsCount()));
          }

          if (++index[0] < addedCount) {
            tdlib.client().send(new TdApi.GetStickerSet(setIds[index[0]]), this);
          } else {
            runOnUiThreadOptional(() -> {
              setApplyingChanges(false);
            });
          }
        }
      });
    }
  }

  @Override
  public void onInstalledStickerSetsUpdated (final long[] stickerSetIds, TdApi.StickerType stickerType) {
    if (stickerType.getConstructor() == TdApi.StickerTypeRegular.CONSTRUCTOR) {
      runOnUiThreadOptional(() -> {
        if (!loadingStickers) {
          changeStickers(stickerSetIds);
        }
      });
    }
  }

  public void applyScheduledChanges () {
    trendingSetsController.applyScheduledFeaturedSets();
  }

  @Override
  public void onRecentStickersUpdated (final int[] stickerIds, boolean isAttached) {
    if (!isAttached) {
      runOnUiThreadOptional(() -> {
        if (!loadingStickers) {
          applyRecentStickers(stickerIds);
        }
      });
    }
  }

  private int[] scheduledFavoriteStickerIds;
  private boolean ignoreNextFavoritesUpdate;

  @Override
  public void onFavoriteStickersUpdated (final int[] stickerIds) {
    runOnUiThreadOptional(() -> {
      if (!loadingStickers) {
        if (ignoreNextFavoritesUpdate) {
          ignoreNextFavoritesUpdate = false;
          scheduledFavoriteStickerIds = stickerIds;
        } else {
          applyFavoriteStickers(stickerIds);
        }
      }
    });
  }

  @Override
  public void onTrendingStickersUpdated (final TdApi.StickerType stickerType, final TdApi.TrendingStickerSets stickerSets, int unreadCount) {
    if (stickerType.getConstructor() != TdApi.StickerTypeRegular.CONSTRUCTOR)
      return;
    runOnUiThreadOptional(() -> {
      if (getArguments() != null) {
        getArguments().setHasNewHots(EmojiLayout.STICKERS_TRENDING_CONTROLLER_ID, TD.getStickerSetsUnreadCount(stickerSets.sets) > 0);
      }
      trendingSetsController.scheduleFeaturedSets(stickerSets, contentView.getCurrentSection() == SECTION_TRENDING);
    });
  }

  private Client.ResultHandler stickerSetsHandler () {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.StickerSets.CONSTRUCTOR: {
          TdApi.StickerSetInfo[] rawStickerSets = ((TdApi.StickerSets) object).sets;

          final TdApi.Sticker[] favoriteStickers = pendingFavoriteStickers;
          final TdApi.Sticker[] recentStickers = pendingRecentStickers;
          final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>(rawStickerSets.length);
          final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();
          final boolean showRecentTitle, allowCollapseRecents;

          pendingFavoriteStickers = null;
          pendingRecentStickers = null;

          if (rawStickerSets.length == 0 && recentStickers.length == 0 && favoriteStickers.length == 0) {
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_NO_STICKERSETS));
            showRecentTitle = allowCollapseRecents = false;
          } else {
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));
            int startIndex = 1;

            if (favoriteStickers.length > 0) {
              TGStickerSetInfo info = new TGStickerSetInfo(tdlib, favoriteStickers, true, 0);
              info.setStartIndex(startIndex);
              stickerSets.add(info);
              for (TdApi.Sticker favoriteSticker : favoriteStickers) {
                TGStickerObj sticker = new TGStickerObj(tdlib, favoriteSticker, null, favoriteSticker.fullType);
                sticker.setIsFavorite();
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
              }
              startIndex += favoriteStickers.length;
            }

            final int totalRecentCount = recentStickers.length;

            showRecentTitle = totalRecentCount > 0 && (
              Config.FORCE_SHOW_RECENTS_STICKERS_TITLE ||
              totalRecentCount > Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT ||
              favoriteStickers.length > 0
            );
            allowCollapseRecents = totalRecentCount > Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT;
            int visibleRecentCount = allowCollapseRecents && !needExpandRecentStickers() ?
              Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT :
              totalRecentCount;

            if (totalRecentCount > 0) {
              TGStickerSetInfo info = new TGStickerSetInfo(tdlib, recentStickers, false, visibleRecentCount);
              info.setStartIndex(startIndex);
              stickerSets.add(info);
              items.add(new MediaStickersAdapter.StickerItem(showRecentTitle ?
                (allowCollapseRecents ? MediaStickersAdapter.StickerHolder.TYPE_HEADER_COLLAPSABLE : MediaStickersAdapter.StickerHolder.TYPE_HEADER) :
                MediaStickersAdapter.StickerHolder.TYPE_EMPTY, info
              ));
              int remainingCount = visibleRecentCount;
              for (TdApi.Sticker recentSticker : recentStickers) {
                TGStickerObj sticker = new TGStickerObj(tdlib, recentSticker, null, recentSticker.fullType);
                sticker.setIsRecent();
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
                if (--remainingCount == 0) {
                  break;
                }
              }
              startIndex += visibleRecentCount + 1;
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
                sticker.setStickerSetId(rawInfo.id, null);
                sticker.setDataProvider(stickerSetsDataProvider());
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
              }
              startIndex += rawInfo.size + 1;
            }
          }

          runOnUiThreadOptional(() -> {
            if (getArguments() != null) {
              getArguments().setStickerSets(stickerSets, favoriteStickers.length > 0, recentStickers.length > 0);
            }
            setShowRecentTitle(showRecentTitle, allowCollapseRecents);
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

  private boolean loadingStickers;

  private void loadStickers () {
    if (!loadingStickers) {
      loadingStickers = true;
      loadingRecentStickers = false;
      loadingFavoriteStickers = false;
      tdlib.client().send(new TdApi.GetFavoriteStickers(), serviceStickersHandler(true));
    }
  }

  private Client.ResultHandler serviceStickersHandler (boolean areFavorite) {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.Stickers.CONSTRUCTOR: {
          TdApi.Sticker[] stickers = ((TdApi.Stickers) object).stickers;
          if (areFavorite) {
            pendingFavoriteStickers = stickers;
            tdlib.client().send(new TdApi.GetRecentStickers(false), serviceStickersHandler(false));
          } else {
            pendingRecentStickers = stickers;
            tdlib.client().send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeRegular()), stickerSetsHandler());
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

  private void scrollToSystemStickers (boolean animated) {
    int i = findFavoriteStickerSet();
    if (i == -1)
      i = findRecentStickerSet();
    if (i != -1)
      stickersController.scrollToStickerSet(i == 0 ? 0 : stickersController.stickerSets.get(i).getStartIndex(), showRecentTitle, animated);
  }

  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.stickersController.setStickers(stickerSets, items);
    this.loadingStickers = false;
    if (stickerSetsDataProvider != null) {
      this.stickerSetsDataProvider.clear();
    }
    tdlib.listeners().subscribeToStickerUpdates(this);
  }

  private TdApi.Sticker[] pendingFavoriteStickers, pendingRecentStickers;

  // GIFs logic

  @Override
  public void onSavedAnimationsUpdated (int[] animationIds) {
    runOnUiThreadOptional(() -> {
      if (gifsView != null) {
        loadGIFs();
      }
    });
  }

  private boolean loadingGIFs;
  private ArrayList<TGGif> gifs;

  private void loadGIFs () {
    if (!loadingGIFs) {
      loadingGIFs = true;
      tdlib.client().send(new TdApi.GetSavedAnimations(), result -> {
        switch (result.getConstructor()) {
          case TdApi.Animations.CONSTRUCTOR: {
            TdApi.Animation[] animations = ((TdApi.Animations) result).animations;
            final ArrayList<TGGif> gifs = new ArrayList<>(animations.length);
            for (TdApi.Animation animation : animations) {
              gifs.add(new TGGif(tdlib, animation));
            }
            runOnUiThreadOptional(() -> {
              setGIFs(gifs);
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

  private void setGIFs (ArrayList<TGGif> gifs) {
    this.loadingGIFs = false;
    this.gifs = gifs;
    gifsAdapter.setGIFs(gifs);
    if (gifs.isEmpty() && contentView.getCurrentSection() == SECTION_GIFS) {
      showStickers();
    }
    tdlib.listeners().subscribeForAnimationsUpdates(this);
  }

  // When user clicked

  public void showHot () {
    if (contentView.canChangeSection()) {
      initHots();
      if (contentView.getCurrentSection() == SECTION_TRENDING && contentView.canChangeSection()) {
        trendingSetsController.recyclerView.smoothScrollBy(0, -trendingSetsController.getStickersScrollY(showRecentTitle));
      } else {
        contentView.changeSection(SECTION_TRENDING, contentView.getCurrentSection() != SECTION_GIFS, -1);
      }
    }
  }

  public boolean showGIFs () {
    if (contentView.canChangeSection() && gifs != null && !gifs.isEmpty()) {
      initGIFs();
      return contentView.changeSection(SECTION_GIFS, true, -1);
    }
    return false;
  }

  public boolean needSearchButton () {
    switch (contentView.getCurrentSection()) {
      case SECTION_GIFS:
        return getGIFsScrollY() == 0;
      /*case SECTION_STICKERS:
        return getArguments() != null && getArguments().getHeaderHideFactor() == 0f;*/
    }
    return false;
  }

  public void showStickers () {
    if (contentView.canChangeSection()) {
      initStickers();
      contentView.changeSection(SECTION_STICKERS, false, -1);
    }
  }

  public void showSystemStickers () {
    if (contentView.canChangeSection()) {
      initStickers();
      scrollToSystemStickers(contentView.getCurrentSection() == SECTION_STICKERS && contentView.isAnimationNotActive());
      contentView.changeSection(SECTION_STICKERS, false, 0);
    }
  }

  public boolean showStickerSet (TGStickerSetInfo stickerSet) {
    if (contentView.canChangeSection()) {
      int i = stickersController.indexOfStickerSet(stickerSet);
      if (i != -1) {
        initStickers();
        stickersController.scrollToStickerSet(i, showRecentTitle, contentView.getCurrentSection() == SECTION_STICKERS && contentView.isAnimationNotActive());
        return contentView.changeSection(SECTION_STICKERS, false, stickersController.indexOfStickerSetByAdapterPosition(i));
      }
    }
    return false;
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
        return stickersController.isIgnoreRequests(stickerSetId);
      }

      @Override
      protected int getLoadingFlags (long stickerSetId, TGStickerObj stickerObj) {
        return stickerObj.isTrending() ? FLAG_TRENDING: FLAG_REGULAR;
      }

      @Override
      protected void applyStickerSet (TdApi.StickerSet stickerSet, int flags) {
        if ((flags & FLAG_REGULAR) != 0) {
          stickersController.applyStickerSet(stickerSet, this);
        }
        if ((flags & FLAG_TRENDING) != 0) {
          trendingSetsController.applyStickerSet(stickerSet, this);
        }
      }
    };
  }
}
