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
 * File created on 25/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.emoji.GifView;
import org.thunderdog.challegram.component.emoji.MediaGifsAdapter;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
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
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.NewFlowLayoutManager;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.core.lambda.CancellableRunnable;

public class EmojiMediaListController extends ViewController<EmojiLayout> implements
  Client.ResultHandler,
  StickerSmallView.StickerMovementCallback,
  StickersListener,
  AnimationsListener,
  TGStickerObj.DataProvider,
  FactorAnimator.Target,
  MediaGifsAdapter.Callback,
  TGStickerSetInfo.ViewCallback,
  ClickHelper.Delegate,
  ForceTouchView.ActionListener {
  public EmojiMediaListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_emojiMedia;
  }

  private static final int SECTION_STICKERS = 0;
  private static final int SECTION_GIFS = 1;
  private static final int SECTION_TRENDING = 2;

  private int currentSection;

  private FrameLayoutFix contentView;
  private MediaStickersAdapter stickersAdapter;
  private MediaStickersAdapter trendingAdapter;
  private MediaGifsAdapter gifsAdapter;
  private CustomRecyclerView stickersView;
  private RecyclerView gifsView;
  private RecyclerView hotView;

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updatePositions();
      }
    };
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    stickersAdapter = new MediaStickersAdapter(this, this, false, this);
    trendingAdapter = new MediaStickersAdapter(this, this, true, this);
    trendingAdapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_PROGRESS));
    gifsAdapter = new MediaGifsAdapter(context, this);
    gifsAdapter.setCallback(this);

    checkSpanCount();

    int currentSection = Settings.instance().getEmojiMediaSection();
    switch (currentSection) {
      case Settings.EMOJI_MEDIA_SECTION_STICKERS: {
        initStickers();
        this.currentSection = SECTION_STICKERS;
        this.currentSectionView = stickersView;
        this.contentView.addView(stickersView);
        break;
      }
      case Settings.EMOJI_MEDIA_SECTION_GIFS: {
        initGIFs();
        if (getArguments() != null) {
          getArguments().setCurrentStickerSectionByPosition(1, false, false);
          getArguments().setMediaSection(true);
        }
        this.currentSection = SECTION_GIFS;
        this.currentSectionView = gifsView;
        this.contentView.addView(gifsView);
        break;
      }
    }

    loadGIFs(); // to show or hide GIF section
    loadStickers(); // to show sections
    loadTrending(0, 20, 0); // to show blue badge?

    return contentView;
  }

  public int getCurrentScrollY () {
    switch (currentSection) {
      case SECTION_GIFS: {
        return getGIFsScrollY();
      }
      case SECTION_STICKERS: {
        return getStickersScrollY();
      }
      case SECTION_TRENDING: {
        return getTrendingScrollY();
      }
    }
    return -1;
  }

  // Stickers

  public void removeRecentStickers () {
    setRecentStickers(null, null);
  }

  private int getStickerSetSection () {
    if (stickersView == null || spanCount == 0) {
      return -1;
    }
    int i = ((LinearLayoutManager) stickersView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
    if (i == -1) {
      i = ((LinearLayoutManager) stickersView.getLayoutManager()).findFirstVisibleItemPosition();
    }
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
      return additional + stickersAdapter.measureScrollTop(i, spanCount, stickerSet, stickerSets);
    }
    return 0;
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (stickersView != null)
      stickersView.requestLayout();
    if (trendingAdapter != null)
      trendingAdapter.notifyDataSetChanged();
  }

  private void initStickers () {
    if (stickersView == null) {
      GridLayoutManager manager = new RtlGridLayoutManager(context(), spanCount).setAlignOnly(true);
      manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
        @Override
        public int getSpanSize (int position) {
          return stickersAdapter.getItemViewType(position) == MediaStickersAdapter.StickerHolder.TYPE_STICKER ? 1 : spanCount;
        }
      });

      stickersView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, contentView);
      stickersView.setHasFixedSize(true);
      stickersView.setLayoutManager(manager);
      stickersView.setAdapter(stickersAdapter);
      // stickersView.setItemAnimator(null);
      stickersView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180));
      stickersView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
      stickersView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
          if ((sectionAnimator == null || sectionAnimator.getFactor() == 0f) && currentSection == SECTION_STICKERS && getArguments() != null && getArguments().getCurrentItem() == 1) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          onStickersScroll(false);
        }
      });
    }
  }

  private void onStickersScroll (boolean force) {
    if (ignoreStickersScroll == 0) {
      if ((sectionAnimator == null || sectionAnimator.getFactor() == 0f) && currentSection == SECTION_STICKERS && getArguments() != null && getArguments().isWatchingMovements() && getArguments().getCurrentItem() == 1) {
        int y = getStickersScrollY();
        getArguments().onScroll(y);
        getArguments().setCurrentStickerSectionByPosition(getStickerSetSection(), true, true);
      }
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
        private Size size = new Size();

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
        public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
          if ((sectionAnimator == null || sectionAnimator.getFactor() == 0f) && currentSection == SECTION_GIFS && getArguments() != null && getArguments().getCurrentItem() == 1) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          if ((sectionAnimator == null || sectionAnimator.getFactor() == 0f) && currentSection == SECTION_GIFS && getArguments() != null && getArguments().getCurrentItem() == 1) {
            getArguments().moveHeaderFull(getGIFsScrollY());
          }
        }
      });
      gifsView.addItemDecoration(new RecyclerView.ItemDecoration() {
        @Override
        public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
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
    return currentSection == SECTION_GIFS ? Math.min(1f, Math.max(0f, (float) getGIFsScrollY() / (float) EmojiLayout.getHeaderSize())) : 0f;
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
        switch (id) {
          case R.id.btn_deleteGif: {
            gifsAdapter.removeSavedGif(animation.animation.id);
            if (gifsAdapter.getItemCount() == 0) {
              showStickers();
            }
            tdlib.client().send(new TdApi.RemoveSavedAnimation(new TdApi.InputFileId(animation.animation.id)), tdlib.okHandler());
            break;
          }
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
    switch (actionId) {
      case R.id.btn_deleteGif: {
        removeGif((TdApi.Animation) arg);
        break;
      }
      case R.id.btn_send: {
        sendGif(context.getSourceView(), (TdApi.Animation) arg);
        break;
      }
    }
  }

  // Trending stickers

  private final ArrayList<TGStickerSetInfo> trendingSets = new ArrayList<>();
  private boolean trendingLoading, canLoadMoreTrending;

  private void updateTrendingSets (long[] stickerSetIds) {
    LongSparseArray<TGStickerSetInfo> installedStickerSets = new LongSparseArray<>(stickerSetIds.length);
    for (long stickerSetId : stickerSetIds) {
      installedStickerSets.put(stickerSetId, null);
    }
    for (TGStickerSetInfo stickerSet : trendingSets) {
      int i = installedStickerSets.indexOfKey(stickerSet.getId());
      if (i >= 0) {
        stickerSet.setIsInstalled();
        trendingAdapter.updateDone(stickerSet);
      } else {
        stickerSet.setIsNotInstalled();
        trendingAdapter.updateDone(stickerSet);
      }
    }
  }

  private void addTrendingStickers (ArrayList<TGStickerSetInfo> trendingSets, ArrayList<MediaStickersAdapter.StickerItem> items, boolean hasUnread, int offset) {
    if (offset != 0 && (!trendingLoading || offset != this.trendingSets.size()))
      return;

    if (trendingSets != null) {
      if (offset == 0) {
        this.lastTrendingStickerSet = null;
        this.trendingSets.clear();
      }
      this.trendingSets.addAll(trendingSets);
    }
    this.canLoadMoreTrending = trendingSets != null && !trendingSets.isEmpty();
    if (getArguments() != null && (hasUnread || offset == 0)) {
      getArguments().setHasNewHots(hasUnread);
    }
    if (offset == 0) {
      if (hotView != null) {
        hotView.stopScroll();
        ((LinearLayoutManager) hotView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
      }
      trendingAdapter.setItems(items);
    } else {
      trendingAdapter.addItems(items);
    }
    this.trendingLoading = false;
  }

  public static int parseTrending (Tdlib tdlib, ArrayList<TGStickerSetInfo> parsedStickerSets, ArrayList<MediaStickersAdapter.StickerItem> items, int offset, TdApi.StickerSetInfo[] stickerSets, TGStickerObj.DataProvider dataProvider, @Nullable TGStickerSetInfo.ViewCallback viewCallback, boolean needSeparators) {
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
      stickerSet.setIsTrending();
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
      items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER_TRENDING, stickerSet));
      int itemCount = 5;
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

  private void loadTrending (int offset, int limit, int cellCount) {
    if (!trendingLoading) {
      trendingLoading = true;
      tdlib.client().send(new TdApi.GetTrendingStickerSets(offset, limit), object -> {
        final ArrayList<TGStickerSetInfo> parsedStickerSets = new ArrayList<>();
        final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();
        final int unreadItemCount;

        if (object.getConstructor() == TdApi.TrendingStickerSets.CONSTRUCTOR) {
          TdApi.TrendingStickerSets trendingStickerSets = (TdApi.TrendingStickerSets) object;
          if (offset == 0)
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));
          unreadItemCount = parseTrending(tdlib, parsedStickerSets, items,  cellCount, trendingStickerSets.sets, EmojiMediaListController.this, EmojiMediaListController.this, false);
        } else {
          if (offset == 0)
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_COME_AGAIN_LATER));
          unreadItemCount = 0;
        }

        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            addTrendingStickers(parsedStickerSets, items, unreadItemCount > 0, offset);
          }
        });
      });
    }
  }

  public int getTrendingScrollY () {
    if (hotView == null) {
      return 0;
    }
    int i = ((LinearLayoutManager) hotView.getLayoutManager()).findFirstVisibleItemPosition();
    if (i != -1) {
      View v = hotView.getLayoutManager().findViewByPosition(i);
      int additional = v != null ? -v.getTop() : 0;
      return additional + trendingAdapter.measureScrollTop(i, 5, indexOfTrendingStickerSetByAdapterPosition(i), trendingSets);
    }
    return 0;
  }

  private void initHots () {
    if (hotView == null) {
      GridLayoutManager manager = new GridLayoutManager(context(), 5);
      manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
        @Override
        public int getSpanSize (int position) {
          return trendingAdapter.getItemViewType(position) == MediaStickersAdapter.StickerHolder.TYPE_STICKER ? 1 : 5;
        }
      });
      trendingAdapter.setManager(manager);

      hotView = (RecyclerView) Views.inflate(context(), R.layout.recycler, contentView);
      hotView.setHasFixedSize(true);
      hotView.setAdapter(trendingAdapter);
      hotView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
      hotView.setLayoutManager(manager);
      hotView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
          if ((sectionAnimator == null || sectionAnimator.getFactor() == 0f) && currentSection == SECTION_TRENDING && getArguments() != null && getArguments().getCurrentItem() == 1) {
            boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
            getArguments().setIsScrolling(isScrolling);
          }
        }

        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          if ((sectionAnimator == null || sectionAnimator.getFactor() == 0f) && currentSection == SECTION_TRENDING && getArguments() != null && getArguments().getCurrentItem() == 1) {
            getArguments().onScroll(getTrendingScrollY());
            if (!trendingLoading && canLoadMoreTrending) {
              int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
              if (lastVisiblePosition != -1) {
                int index = trendingSets.indexOf(trendingAdapter.getItem(lastVisiblePosition).stickerSet);
                if (index != -1 && index + 5 >= trendingSets.size()) {
                  loadTrending(trendingSets.size(), 25, trendingAdapter.getItemCount());
                }
              }
            }
          }
        }
      });
    }
  }

  // Controller lifecycle

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromAnyUpdates(this);
  }

  // When rotation, etc

  private int spanCount;

  public void checkSpanCount () {
    int spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight());
    if (this.spanCount != spanCount) {
      this.spanCount = spanCount;
      if (stickersView != null) {
        ((GridLayoutManager) stickersView.getLayoutManager()).setSpanCount(spanCount);
      }
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

  private @Nullable TGStickerSetInfo lastTrendingStickerSet;
  private int lastTrendingStickerSetIndex;

  private int indexOfTrendingStickerSetByAdapterPosition (int position) {
    if (position == 0) {
      return 0;
    }
    if (lastTrendingStickerSet != null) {
      if (position >= lastTrendingStickerSet.getStartIndex() && position < lastTrendingStickerSet.getEndIndex()) {
        return lastTrendingStickerSetIndex;
      } else if (position >= lastTrendingStickerSet.getEndIndex()) {
        for (int i = lastTrendingStickerSetIndex + 1; i < trendingSets.size(); i++) {
          TGStickerSetInfo oldStickerSet = trendingSets.get(i);
          if (position >= oldStickerSet.getStartIndex() && position < oldStickerSet.getEndIndex()) {
            lastTrendingStickerSet = oldStickerSet;
            lastTrendingStickerSetIndex = i;
            return i;
          }
        }
      } else if (position < lastTrendingStickerSet.getStartIndex()) {
        for (int i = Math.min(trendingSets.size() - 1, lastTrendingStickerSetIndex - 1); i >= 0; i--) {
          TGStickerSetInfo oldStickerSet = trendingSets.get(i);
          if (position >= oldStickerSet.getStartIndex() && position < oldStickerSet.getEndIndex()) {
            lastTrendingStickerSet = oldStickerSet;
            lastTrendingStickerSetIndex = i;
            return i;
          }
        }
      }
    }
    int i = 0;
    for (TGStickerSetInfo oldStickerSet : trendingSets) {
      if (position >= oldStickerSet.getStartIndex() && position < oldStickerSet.getEndIndex()) {
        lastTrendingStickerSet = oldStickerSet;
        lastTrendingStickerSetIndex = i;
        return i;
      }
      i++;
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

  private int indexOfTrendingSticker (TGStickerObj sticker) {
    if (trendingSets != null) {
      for (TGStickerSetInfo stickerSet : trendingSets) {
        if (stickerSet.getId() == sticker.getStickerSetId()) {
          return trendingAdapter.indexOfSticker(sticker, stickerSet.getStartIndex());
        }
      }
    }
    return -1;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    if (sticker.isTrending()) {
      int i = indexOfTrendingSticker(sticker);
      if (i != -1) {
        trendingAdapter.setStickerPressed(i, isPressed, hotView != null ? hotView.getLayoutManager() : null);
      }
    } else {
      int i = indexOfSticker(sticker);
      if (i != -1) {
        stickersAdapter.setStickerPressed(i, isPressed, stickersView != null ? stickersView.getLayoutManager() : null);
      }
    }
  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    EmojiLayout parent = getArgumentsStrict();
    return recyclerY > parent.getHeaderBottom();
  }

  @Override
  public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) { }

  @Override
  public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) { }

  @Override
  public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) { }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return false;
  }

  @Override
  public int getViewportHeight () {
    return -1;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, boolean forceDisableNotification, @Nullable TdApi.MessageSchedulingState schedulingState) {
    if (sticker.isTrending() && !isMenuClick) {
      int i = indexOfTrendingStickerSetById(sticker.getStickerSetId());
      if (i != -1) {
        trendingSets.get(i).show(this);
        return true;
      }
      return false;
    }

    if (getArguments() != null) {
      if (getArguments().sendSticker(clickView, sticker, forceDisableNotification, schedulingState)) {
        if (sticker.isRecent())
          ignoreNextRecentsUpdate = true;
        return true;
      }
    }
    return false;
  }

  @Override
  public long getStickerOutputChatId () {
    return getArguments() != null ? getArguments().findOutputChatId() : 0;
  }

  @Override
  public int getStickersListTop () {
    return Views.getLocationInWindow(currentSection == SECTION_TRENDING ? hotView : stickersView)[1];
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
    for (int i = startPosition; i < stickerSets.size(); i++) {
      TGStickerSetInfo stickerSet = stickerSets.get(i);
      stickerSet.setStartIndex(startIndex);
      startIndex = stickerSet.getEndIndex();
    }
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

  private int getRecentStartIndex () {
    int i = findFavoriteStickerSet();
    if (i != -1) {
      return stickerSets.get(i).getEndIndex();
    }
    return 1;
  }

  private void setRecentStickers (@Nullable TdApi.Sticker[] recentStickers, @Nullable ArrayList<MediaStickersAdapter.StickerItem> items) {
    if (getArguments() != null) {
      getArguments().setShowRecents(recentStickers != null && recentStickers.length > 0);
    }

    TGStickerSetInfo recentSet;
    int existingIndex = findRecentStickerSet();

    if (existingIndex != -1) {
      recentSet = stickerSets.remove(existingIndex);
      shiftStickerSets(existingIndex, recentSet.getStartIndex());
      stickersAdapter.removeRange(recentSet.getStartIndex(), recentSet.getItemCount());
    } else if (recentStickers != null && recentStickers.length > 0) {
      recentSet = new TGStickerSetInfo(tdlib, recentStickers);
      recentSet.setIsRecent();
    } else {
      recentSet = null;
    }

    if (recentSet != null && recentStickers != null && recentStickers.length > 0 && items != null) {
      if (!Config.HEADLESS_RECENT_PACK) {
        items.add(0, new MediaStickersAdapter.StickerItem(getRecentTitleViewType(), recentSet));
      }

      int startIndex = getRecentStartIndex();
      recentSet.setSize(recentStickers.length);
      recentSet.setStartIndex(startIndex);
      int stickerSetIndex = findFavoriteStickerSet() != -1 ? 1 : 0;
      stickerSets.add(stickerSetIndex, recentSet);
      shiftStickerSets(stickerSetIndex + 1, recentSet.getEndIndex());
      stickersAdapter.insertRange(startIndex, items);
    }
  }

  private int getRecentTitleViewType () {
    return showRecentTitle ? MediaStickersAdapter.StickerHolder.TYPE_HEADER : MediaStickersAdapter.StickerHolder.TYPE_EMPTY;
  }

  private boolean showRecentTitle;

  private void setShowRecentTitle (boolean show) {
    this.showRecentTitle = show;
    int i = findRecentStickerSet();
    if (i != -1) {
      int index = stickerSets.get(i).getStartIndex();
      if (stickersAdapter.getItem(index).setViewType(getRecentTitleViewType())) {
        stickersAdapter.notifyItemChanged(index);
      }
    }
  }

  private void setFavoriteStickers (@Nullable TdApi.Sticker[] favoriteStickers, @Nullable ArrayList<MediaStickersAdapter.StickerItem> items) {
    if (getArguments() != null) {
      getArguments().setShowFavorite(favoriteStickers != null && favoriteStickers.length > 0);
    }

    setShowRecentTitle(favoriteStickers != null && favoriteStickers.length > 0);

    TGStickerSetInfo favoriteSet;
    int existingIndex = findFavoriteStickerSet();

    if (existingIndex != -1) {
      favoriteSet = stickerSets.remove(existingIndex);
      shiftStickerSets(existingIndex, favoriteSet.getStartIndex());
      stickersAdapter.removeRange(favoriteSet.getStartIndex(), favoriteSet.getItemCount());
    } else if (favoriteStickers != null && favoriteStickers.length > 0) {
      favoriteSet = new TGStickerSetInfo(tdlib, favoriteStickers);
      favoriteSet.setIsFavorite();
    } else {
      favoriteSet = null;
    }

    if (favoriteSet != null && favoriteStickers != null && favoriteStickers.length > 0 && items != null) {
      int startIndex = 1; // getRecentStartIndex();
      favoriteSet.setSize(favoriteStickers.length);
      favoriteSet.setStartIndex(startIndex);
      int stickerSetIndex = 0; // findFavoriteStickerSet() != -1 ? 1 : 0;
      stickerSets.add(stickerSetIndex, favoriteSet);
      shiftStickerSets(stickerSetIndex + 1, favoriteSet.getEndIndex());
      stickersAdapter.insertRange(startIndex, items);
    }
  }

  private int getMaxCount (boolean areFavorite) {
    return areFavorite ? tdlib.favoriteStickersMaxCount() : 20;
  }

  private void processStickersImpl (final TdApi.Object object, final boolean areFavorite) {
    final ArrayList<MediaStickersAdapter.StickerItem> stickers;
    final TdApi.Sticker[] rawStickers;
    final int maxCount = getMaxCount(areFavorite);
    if (object.getConstructor() == TdApi.Stickers.CONSTRUCTOR) {
      TdApi.Sticker[] untrimmedStickers = ((TdApi.Stickers) object).stickers;
      if (untrimmedStickers.length > maxCount) {
        rawStickers = new TdApi.Sticker[maxCount];
        System.arraycopy(untrimmedStickers, 0, rawStickers, 0, maxCount);
      } else {
        rawStickers = untrimmedStickers;
      }
      stickers = new ArrayList<>(rawStickers.length);
      for (TdApi.Sticker rawSticker : rawStickers) {
        TGStickerObj sticker = new TGStickerObj(tdlib, rawSticker, null, rawSticker.type);
        if (areFavorite) {
          sticker.setIsFavorite();
        } else {
          sticker.setIsRecent();
        }
        stickers.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
      }
    } else {
      stickers = null;
      rawStickers = null;
    }
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
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
    if (trendingSets != null) {
      updateTrendingSets(stickerSetIds);
    }
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
              TGStickerObj parsed = new TGStickerObj(tdlib, sticker, sticker.type, rawStickerSet.emojis[i].emojis);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, parsed));
              i++;
            }

            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                addStickerSet(stickerSet, items, insertIndex + getSystemSetsCount());
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

  private int indexOfTrendingStickerSetById (long setId) {
    int index = 0;
    for (TGStickerSetInfo setInfo : trendingSets) {
      if (setInfo.getId() == setId) {
        return index;
      }
      index++;
    }
    return -1;
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
      getArguments().moveStickerSection(oldPosition, newPosition);
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
      getArguments().resetScrollState(true); // FIXME upd: ... fixme what?
    }
    UI.post(() -> {
      if (getArguments() != null && currentSection == SECTION_STICKERS) {
        getArguments().setCurrentStickerSectionByPosition(getStickerSetSection(), true, true);
        getArguments().resetScrollState(true);
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
      getArguments().addStickerSection(index, stickerSet);
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

  private int removeStickerSet (TGStickerSetInfo stickerSet) {
    int i = stickerSets.indexOf(stickerSet);
    if (i != -1) {
      beforeStickerChanges();
      stickerSets.remove(i);
      if (getArguments() != null) {
        getArguments().removeStickerSection(i);
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

  @Override
  public void onInstalledStickerSetsUpdated (final long[] stickerSetIds, boolean isMasks) {
    if (!isMasks) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && !loadingStickers) {
          changeStickers(stickerSetIds);
        }
      });
    }
  }

  public void applyScheduledChanges () {
    if (scheduledRecentStickerIds != null) {
      applyRecentStickers(scheduledRecentStickerIds);
      scheduledRecentStickerIds = null;
    }
    if (scheduledFeaturedSets != null) {
      applyScheduledFeaturedSets(scheduledFeaturedSets);
      scheduledFeaturedSets = null;
    }
  }

  private int[] scheduledRecentStickerIds;
  private boolean ignoreNextRecentsUpdate;

  @Override
  public void onRecentStickersUpdated (final int[] stickerIds, boolean isAttached) {
    if (!isAttached) {
      tdlib.ui().post(() -> {
        if (!isDestroyed() && !loadingStickers) {
          if (ignoreNextRecentsUpdate) {
            ignoreNextRecentsUpdate = false;
            scheduledRecentStickerIds = stickerIds;
          } else {
            applyRecentStickers(stickerIds);
          }
        }
      });
    }
  }

  private int[] scheduledFavoriteStickerIds;
  private boolean ignoreNextFavoritesUpdate;

  @Override
  public void onFavoriteStickersUpdated (final int[] stickerIds) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && !loadingStickers) {
        if (ignoreNextFavoritesUpdate) {
          ignoreNextFavoritesUpdate = false;
          scheduledFavoriteStickerIds = stickerIds;
        } else {
          applyFavoriteStickers(stickerIds);
        }
      }
    });
  }

  private TdApi.TrendingStickerSets scheduledFeaturedSets;

  @Override
  public void onTrendingStickersUpdated (final TdApi.TrendingStickerSets stickerSets, int unreadCount) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        if (getArguments() != null) {
          getArguments().setHasNewHots(getUnreadCount(stickerSets.sets) > 0);
        }
        scheduleFeaturedSets(stickerSets);
      }
    });
  }

  public static int getUnreadCount (TdApi.StickerSetInfo[] stickerSets) {
    int unreadCount = 0;
    for (TdApi.StickerSetInfo stickerSet : stickerSets) {
      if (!stickerSet.isViewed) {
        unreadCount++;
      }
    }
    return unreadCount;
  }

  private void scheduleFeaturedSets (TdApi.TrendingStickerSets stickerSets) {
    if (currentSection == SECTION_TRENDING) {
      scheduledFeaturedSets = stickerSets;
    } else {
      scheduledFeaturedSets = null;
      applyScheduledFeaturedSets(stickerSets);
    }
  }

  private void applyScheduledFeaturedSets () {
    if (scheduledFeaturedSets != null) {
      applyScheduledFeaturedSets(scheduledFeaturedSets);
      scheduledFeaturedSets = null;
    }
  }

  private void applyScheduledFeaturedSets (TdApi.TrendingStickerSets sets) {
    if (sets != null && !isDestroyed() && !trendingLoading) {
      if (trendingSets != null && trendingSets.size() == sets.sets.length && !trendingSets.isEmpty()) {
        boolean equal = true;
        int i = 0;
        for (TGStickerSetInfo stickerSetInfo : trendingSets) {
          if (stickerSetInfo.getId() != sets.sets[i].id) {
            equal = false;
            break;
          }
          boolean visuallyChanged = stickerSetInfo.isViewed() != sets.sets[i].isViewed;
          stickerSetInfo.updateState(sets.sets[i]);
          if (visuallyChanged) {
            trendingAdapter.updateState(stickerSetInfo);
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
      final int unreadItemCount = parseTrending(tdlib, stickerSetInfos, stickerItems, 0, sets.sets, this, this, false);
      addTrendingStickers(stickerSetInfos, stickerItems, unreadItemCount > 0, 0);
    }
  }

  private static final int FLAG_TRENDING = 0x01;
  private static final int FLAG_REGULAR = 0x02;
  private LongSparseIntArray loadingStickerSets;

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

  @Override
  public void viewStickerSet (TGStickerSetInfo stickerSetInfo) {
    viewStickerSetInternal(stickerSetInfo.getId());
  }

  @Override
  public void requestStickerData (TGStickerObj stickerObj, long stickerSetId) {
    if (ignoreRequests && stickerSetId != ignoreException) { // avoiding huge data load while scrolling to section
      return;
    }
    int currentFlags;
    if (loadingStickerSets == null) {
      loadingStickerSets = new LongSparseIntArray();
      currentFlags = 0;
    } else {
      currentFlags = loadingStickerSets.get(stickerSetId, 0);
    }
    if (currentFlags == 0) {
      loadingStickerSets.put(stickerSetId, stickerObj.isTrending() ? FLAG_TRENDING : FLAG_REGULAR);
      tdlib.client().send(new TdApi.GetStickerSet(stickerSetId), this);
    } else if ((currentFlags & FLAG_TRENDING) == 0 && stickerObj.isTrending()) {
      currentFlags |= FLAG_TRENDING;
      loadingStickerSets.put(stickerSetId, currentFlags);
    } else if ((currentFlags & FLAG_REGULAR) == 0 && !stickerObj.isTrending()) {
      currentFlags |= FLAG_REGULAR;
      loadingStickerSets.put(stickerSetId, currentFlags);
    }
  }

  private boolean loadingStickers;

  private void loadStickers () {
    if (!loadingStickers) {
      loadingStickers = true;
      loadingRecentStickers = false;
      loadingFavoriteStickers = false;
      tdlib.client().send(new TdApi.GetFavoriteStickers(), this);
    }
  }

  private void scrollToSystemStickers (boolean animated) {
    int i = findFavoriteStickerSet();
    if (i == -1)
      i = findRecentStickerSet();
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
        scrollTop = Math.max(0, stickersAdapter.measureScrollTop(stickerSetIndex, spanCount, futureSection, stickerSets) - EmojiLayout.getHeaderSize() - EmojiLayout.getHeaderPadding());
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
        getArguments().setCurrentStickerSectionByPosition(futureSection, true, true);
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
    int flags = loadingStickerSets.get(stickerSet.id);
    loadingStickerSets.delete(stickerSet.id);

    if (flags == 0) {
      return;
    }

    final int actualSize = stickerSet.stickers.length;

    if ((flags & FLAG_TRENDING) != 0) {
      if (trendingSets == null || trendingSets.isEmpty()) {
        return;
      }
      for (TGStickerSetInfo oldStickerSet : trendingSets) {
        if (oldStickerSet.getId() == stickerSet.id) {
          oldStickerSet.setStickerSet(stickerSet);
          for (int stickerIndex = oldStickerSet.getCoverCount(), j = oldStickerSet.getStartIndex() + 1 + oldStickerSet.getCoverCount(); stickerIndex < Math.min(stickerSet.stickers.length - oldStickerSet.getCoverCount(), oldStickerSet.getCoverCount() + 4); stickerIndex++, j++) {
            MediaStickersAdapter.StickerItem item = trendingAdapter.getItem(j);
            if (item.sticker != null) {
              TdApi.Sticker sticker = stickerSet.stickers[stickerIndex];
              item.sticker.set(tdlib, sticker, sticker.type, stickerSet.emojis[stickerIndex].emojis);
            }

            View view = hotView != null ? hotView.getLayoutManager().findViewByPosition(j) : null;
            if (view != null && view instanceof StickerSmallView && view.getTag() == item) {
              ((StickerSmallView) view).refreshSticker();
            } else {
              trendingAdapter.notifyItemChanged(j);
            }
          }
          break;
        }
      }
    }

    if ((flags & FLAG_REGULAR) != 0) {
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
                stickersAdapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_NO_STICKERSETS));
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
                  TGStickerObj obj = new TGStickerObj(tdlib, sticker, sticker.type, stickerSet.emojis[j].emojis);
                  obj.setStickerSetId(stickerSet.id, stickerSet.emojis[j].emojis);
                  obj.setDataProvider(this);
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
            item.sticker.set(tdlib, sticker, sticker.type, stickerSet.emojis[stickerIndex].emojis);

            View view = stickersView != null ? stickersView.getLayoutManager().findViewByPosition(j) : null;
            if (view != null && view instanceof StickerSmallView) {
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
  }

  private ArrayList<TGStickerSetInfo> stickerSets;

  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.stickerSets = stickerSets;
    this.loadingStickers = false;
    this.lastStickerSetInfo = null;
    if (loadingStickerSets != null) {
      this.loadingStickerSets.clear();
    }
    stickersAdapter.setItems(items);

    tdlib.listeners().subscribeToStickerUpdates(this);
  }

  private TdApi.Sticker[] pendingFavoriteStickers, pendingRecentStickers;

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Stickers.CONSTRUCTOR: {
        TdApi.Sticker[] stickers = ((TdApi.Stickers) object).stickers;
        boolean areFavorite = pendingFavoriteStickers == null;
        int maxCount = getMaxCount(areFavorite);
        if (stickers.length > maxCount) {
          TdApi.Sticker[] trimmed = new TdApi.Sticker[maxCount];
          System.arraycopy(stickers, 0, trimmed, 0, maxCount);
          stickers = trimmed;
        }
        if (areFavorite) {
          pendingFavoriteStickers = stickers;
          tdlib.client().send(new TdApi.GetRecentStickers(false), this);
        } else {
          pendingRecentStickers = stickers;
          tdlib.client().send(new TdApi.GetInstalledStickerSets(false), this);
        }
        break;
      }
      case TdApi.StickerSets.CONSTRUCTOR: {
        TdApi.StickerSetInfo[] rawStickerSets = ((TdApi.StickerSets) object).sets;

        final TdApi.Sticker[] favoriteStickers = pendingFavoriteStickers;
        final TdApi.Sticker[] recentStickers = pendingRecentStickers;
        final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>(rawStickerSets.length);
        final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();

        pendingFavoriteStickers = null;
        pendingRecentStickers = null;

        if (rawStickerSets.length == 0 && recentStickers.length == 0 && favoriteStickers.length == 0) {
          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_NO_STICKERSETS));
        } else {
          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));
          int startIndex = 1;

          if (favoriteStickers.length > 0) {
            TGStickerSetInfo info = new TGStickerSetInfo(tdlib, favoriteStickers);
            info.setIsFavorite();
            info.setStartIndex(startIndex);
            stickerSets.add(info);
            for (TdApi.Sticker favoriteSticker : favoriteStickers) {
              TGStickerObj sticker = new TGStickerObj(tdlib, favoriteSticker, null, favoriteSticker.type);
              sticker.setIsFavorite();
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
            }
            startIndex += favoriteStickers.length;
          }

          if (recentStickers.length > 0) {
            TGStickerSetInfo info = new TGStickerSetInfo(tdlib, recentStickers);
            info.setIsRecent();
            info.setStartIndex(startIndex);
            stickerSets.add(info);
            if (!Config.HEADLESS_RECENT_PACK) {
              items.add(new MediaStickersAdapter.StickerItem(favoriteStickers.length > 0 ? MediaStickersAdapter.StickerHolder.TYPE_HEADER : MediaStickersAdapter.StickerHolder.TYPE_EMPTY, info));
            }
            for (TdApi.Sticker recentSticker : recentStickers) {
              TGStickerObj sticker = new TGStickerObj(tdlib, recentSticker, null, recentSticker.type);
              sticker.setIsRecent();
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
            }
            if (Config.HEADLESS_RECENT_PACK) {
              startIndex += recentStickers.length;
            } else {
              startIndex += recentStickers.length + 1;
            }
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
              sticker.setDataProvider(this);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
            }
            startIndex += rawInfo.size + 1;
          }
        }

        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            if (getArguments() != null) {
              getArguments().setStickerSets(stickerSets, favoriteStickers.length > 0, recentStickers.length > 0);
            }
            setShowRecentTitle(favoriteStickers.length > 0);
            setStickers(stickerSets, items);
          }
        });

        break;
      }
      case TdApi.StickerSet.CONSTRUCTOR: {
        final TdApi.StickerSet stickerSet = (TdApi.StickerSet) object;
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            applyStickerSet(stickerSet);
          }
        });

        break;
      }
      case TdApi.Animations.CONSTRUCTOR: {
        TdApi.Animation[] animations = ((TdApi.Animations) object).animations;
        final ArrayList<TGGif> gifs = new ArrayList<>(animations.length);
        for (TdApi.Animation animation : animations) {
          gifs.add(new TGGif(tdlib, animation));
        }
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setGIFs(gifs);
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

  // GIFs logic

  @Override
  public void onSavedAnimationsUpdated (int[] animationIds) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && gifsView != null) {
        loadGIFs();
      }
    });
  }

  private boolean loadingGIFs;
  private ArrayList<TGGif> gifs;

  private void loadGIFs () {
    if (!loadingGIFs) {
      loadingGIFs = true;
      tdlib.client().send(new TdApi.GetSavedAnimations(), this);
    }
  }

  private void setGIFs (ArrayList<TGGif> gifs) {
    this.loadingGIFs = false;
    this.gifs = gifs;
    gifsAdapter.setGIFs(gifs);
    if (gifs.isEmpty() && currentSection == SECTION_GIFS) {
      showStickers();
    }
    tdlib.listeners().subscribeForAnimationsUpdates(this);
  }

  // Section changer

  private View currentSectionView;
  private int nextSection;
  private View nextSectionView;
  private boolean sectionIsLeft;

  private static final int CHANGE_SECTION_ANIMATOR = 0;
  private FactorAnimator sectionAnimator;
  private float sectionChangeFactor;

  private boolean changeSection (int sectionId, View sectionView, boolean fromLeft, int stickerSetSection) {
    if (currentSection == sectionId || !canChangeSection()) {
      return false;
    }

    this.nextSection = sectionId;
    this.nextSectionView = sectionView;
    this.sectionIsLeft = fromLeft;

    this.contentView.addView(sectionView);

    if (this.sectionAnimator == null) {
      this.sectionAnimator = new FactorAnimator(CHANGE_SECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }

    this.sectionAnimator.animateTo(1f);

    if (getArguments() != null) {
      if (getArguments().getCurrentItem() == 1) {
        if (currentSection == SECTION_GIFS && (nextSection == SECTION_STICKERS || nextSection == SECTION_TRENDING)) {
          getArguments().setCircleVisible(false, false);
        } else if ((currentSection == SECTION_STICKERS || currentSection == SECTION_TRENDING) && nextSection == SECTION_GIFS) {
          getArguments().setCircleVisible(true, true);
        }
      }
      getArguments().setCurrentStickerSectionByPosition(
        nextSection == SECTION_STICKERS ? (stickerSetSection != -1 ? stickerSetSection : getStickerSetSection()) :
          nextSection == SECTION_TRENDING ? 2 : 1
      , nextSection == SECTION_STICKERS,true);
    }

    return true;
  }

  private void updatePositions () {
    if (sectionIsLeft != Lang.rtl()) {
      currentSectionView.setTranslationX((float) currentSectionView.getMeasuredWidth() * sectionChangeFactor);
      if (nextSectionView != null) {
        nextSectionView.setTranslationX((float) (-nextSectionView.getMeasuredWidth()) * (1f - sectionChangeFactor));
      }
    } else {
      currentSectionView.setTranslationX((float) (-currentSectionView.getMeasuredWidth()) * sectionChangeFactor);
      if (nextSectionView != null) {
        nextSectionView.setTranslationX((float) nextSectionView.getMeasuredWidth() * (1f - sectionChangeFactor));
      }
    }
  }

  private boolean canChangeSection () {
    return sectionAnimator == null || (!sectionAnimator.isAnimating() && sectionAnimator.getFactor() == 0f && sectionChangeFactor == 0f);
  }

  private void applySection () {
    contentView.removeView(currentSectionView);

    if (getArguments() != null) {
      if (currentSection == SECTION_GIFS && (nextSection == SECTION_STICKERS || nextSection == SECTION_TRENDING)) {
        getArguments().setPreferredSection(Settings.EMOJI_MEDIA_SECTION_STICKERS);
      } else if (nextSection == SECTION_GIFS && (currentSection == SECTION_STICKERS || currentSection == SECTION_TRENDING)) {
        getArguments().setPreferredSection(Settings.EMOJI_MEDIA_SECTION_GIFS);
      }
    }

    if (currentSection == SECTION_TRENDING && nextSection != SECTION_TRENDING) {
      applyScheduledFeaturedSets();
    }

    currentSection = nextSection;
    currentSectionView = nextSectionView;
    nextSectionView = null;
    sectionAnimator.forceFactor(0f);
    sectionChangeFactor = 0f;

    if (getArguments() != null) {
      getArguments().resetScrollState();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case CHANGE_SECTION_ANIMATOR: {
        this.sectionChangeFactor = factor;
        updatePositions();
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case CHANGE_SECTION_ANIMATOR: {
        if (finalFactor == 1f) {
          applySection();
        }
        break;
      }
    }
  }

  // When user clicked

  public void showHot () {
    if (canChangeSection()) {
      initHots();
      if (currentSection == SECTION_TRENDING && canChangeSection()) {
        hotView.smoothScrollBy(0, -getTrendingScrollY());
      } else {
        changeSection(SECTION_TRENDING, hotView, currentSection != SECTION_GIFS, -1);
      }
    }
  }

  public boolean showGIFs () {
    if (canChangeSection() && gifs != null && !gifs.isEmpty()) {
      initGIFs();
      return changeSection(SECTION_GIFS, gifsView, true, -1);
    }
    return false;
  }

  public boolean needSearchButton () {
    switch (currentSection) {
      case SECTION_GIFS:
        return getGIFsScrollY() == 0;
      /*case SECTION_STICKERS:
        return getArguments() != null && getArguments().getHeaderHideFactor() == 0f;*/
    }
    return false;
  }

  public void showStickers () {
    if (canChangeSection()) {
      initStickers();
      changeSection(SECTION_STICKERS, stickersView, false, -1);
    }
  }

  public void showSystemStickers () {
    if (canChangeSection()) {
      initStickers();
      scrollToSystemStickers(currentSection == SECTION_STICKERS && (sectionAnimator == null || !sectionAnimator.isAnimating()));
      changeSection(SECTION_STICKERS, stickersView, false, 0);
    }
  }

  public boolean showStickerSet (TGStickerSetInfo stickerSet) {
    if (canChangeSection()) {
      int i = indexOfStickerSet(stickerSet);
      if (i != -1) {
        initStickers();
        scrollToStickerSet(i, currentSection == SECTION_STICKERS && (sectionAnimator == null || !sectionAnimator.isAnimating()));
        return changeSection(SECTION_STICKERS, stickersView, false, indexOfStickerSetByAdapterPosition(i));
      }
    }
    return false;
  }
}
