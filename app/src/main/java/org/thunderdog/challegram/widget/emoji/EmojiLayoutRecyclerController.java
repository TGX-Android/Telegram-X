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
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.widget.emoji;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.EmojiView;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TGDefaultEmoji;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.RecentEmoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.telegram.TdlibThread;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.EmojiLayout;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class EmojiLayoutRecyclerController extends ViewController<EmojiLayoutRecyclerController.Callback> implements
  StickerSmallView.StickerMovementCallback,
  TGLegacyManager.EmojiLoadListener,
  Emoji.EmojiChangeListener,
  TdlibEmojiManager.Watcher,
  StickersListener {

  private static final int SCROLL_BY_SECTION_LIMIT = 8;

  private final @IdRes int controllerId;
  protected @EmojiMediaType int mediaType;

  protected Callback callbacks;
  protected RtlGridLayoutManager manager;
  public CustomRecyclerView recyclerView;
  protected MediaStickersAdapter adapter;
  protected int spanCount;

  private ArrayList<TGStickerSetInfo> classicEmojiSets;
  public ArrayList<TGStickerSetInfo> stickerSets;

  public EmojiLayoutRecyclerController (Context context, Tdlib tdlib, @IdRes int controllerId) {
    super(context, tdlib);
    this.controllerId = controllerId;
    this.mediaType = EmojiLayout.getEmojiMediaType(controllerId);
  }

  @Override
  public int getId () {
    return controllerId;
  }

  @Override
  public void setArguments (Callback args) {
    super.setArguments(args);
    this.callbacks = args;
  }

  @Override
  protected View onCreateView (Context context) {
    manager = new RtlGridLayoutManager(context, spanCount = Math.max(1, calculateSpanCount())).setAlignOnly(true);
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        int type = adapter.getItem(position).viewType;
        return (type == MediaStickersAdapter.StickerHolder.TYPE_DEFAULT_EMOJI || type == MediaStickersAdapter.StickerHolder.TYPE_STICKER) ? 1 : spanCount;
      }
    });

    recyclerView = new CustomRecyclerView(context) {
      @Override
      protected void onMeasure (int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        checkWidth(getMeasuredWidth());
      }
    };
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);
    adapter.setManager(manager);

    return recyclerView;
  }

  public void setAdapter (MediaStickersAdapter adapter) {
    this.adapter = adapter;
  }

  @Nullable public RtlGridLayoutManager getManager () {
    return manager;
  }

  public int getSpanCount () {
    return spanCount;
  }


  private int lastMeasuredWidth;

  private void checkWidth (int width) {
    if (width != 0 && lastMeasuredWidth != width) {
      lastMeasuredWidth = width;
      checkSpanCount();
    }
  }

  public void checkSpanCount () {
    if (manager != null) {
      int spanCount = Math.max(1, calculateSpanCount());
      if (this.spanCount != spanCount) {
        this.spanCount = spanCount;
        manager.setSpanCount(spanCount);
      }
    }
  }

  public void clearAllItems () {
    clearAllItems(null);
  }

  public void clearAllItems (@Nullable MediaStickersAdapter.StickerItem item) {
    this.classicEmojiSets = null;
    this.stickerSets = null;
    this.lastStickerSetInfo = null;
    if (item != null) {
      adapter.setItem(item);
    } else {
      adapter.removeRange(0, adapter.getItemCount());
    }
  }

  public void setDefaultEmojiPacks (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.classicEmojiSets = stickerSets;
    this.stickerSets = stickerSets;
    this.lastStickerSetInfo = null;
    adapter.addItems(modifyStickers(items));
    TGLegacyManager.instance().addEmojiListener(this);
    Emoji.instance().addEmojiChangeListener(this);
  }

  public void setStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.lastStickerSetInfo = null;
    if (classicEmojiSets != null) {
      this.stickerSets = new ArrayList<>(classicEmojiSets.size() + stickerSets.size());
      this.stickerSets.addAll(classicEmojiSets);
      this.stickerSets.addAll(stickerSets);
    } else {
      this.stickerSets = stickerSets;
    }

    adapter.addItems(modifyStickers(items));
  }

  public void addStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.stickerSets.addAll(stickerSets);
    this.adapter.addItems(modifyStickers(items));
  }

  public int getItemsHeight (boolean showRecentTitle) {
    return adapter.measureScrollTop(adapter.getItemCount(), spanCount, Integer.MAX_VALUE - 1, stickerSets, recyclerView, showRecentTitle);
  }

  public void applyStickerSet (TdApi.StickerSet stickerSet, TGStickerObj.DataProvider dataProvider) {
    applyStickerSet(stickerSet, dataProvider, true);
  }

  public void applyStickerSet (TdApi.StickerSet stickerSet, TGStickerObj.DataProvider dataProvider, boolean allowRefreshWithoutAdapterNotification) {
    if (stickerSets == null || stickerSets.isEmpty()) return;

    final int actualSize = stickerSet.stickers.length;
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
            if (callbacks != null) {
              callbacks.setIgnoreMovement(true);
            }
            stickerSets.remove(i);
            if (stickerSets.isEmpty()) {
              adapter.setItem(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_NO_STICKERSETS));
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
              adapter.removeRange(oldStickerSet.getStartIndex(), oldStickerSet.getSize() + 1);
            }

            if (callbacks != null) {
              callbacks.setIgnoreMovement(false);
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
              adapter.removeRange(oldStickerSet.getStartIndex() + 1 + actualSize, oldSize - actualSize);
            } else {
              ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>(actualSize - oldSize);
              for (int j = oldSize; j < actualSize; j++) {
                TdApi.Sticker sticker = stickerSet.stickers[j];
                TGStickerObj obj = new TGStickerObj(tdlib, sticker, sticker.fullType, stickerSet.emojis[j].emojis);
                obj.setStickerSetId(stickerSet.id, stickerSet.emojis[j].emojis);
                obj.setDataProvider(dataProvider);
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, modifySticker(obj)));
              }
              adapter.insertRange(oldStickerSet.getStartIndex() + 1 + oldSize, modifyStickers(items));
            }
          }

          if (callbacks != null) {
            callbacks.setIgnoreMovement(false);
          }
        }

        for (int stickerIndex = oldStickerSet.getCoverCount(), j = oldStickerSet.getStartIndex() + 1 + oldStickerSet.getCoverCount(); stickerIndex < stickerSet.stickers.length; stickerIndex++, j++) {
          MediaStickersAdapter.StickerItem item = adapter.getItem(j);
          TdApi.Sticker sticker = stickerSet.stickers[stickerIndex];
          if (item.sticker != null) {
            item.sticker.set(tdlib, sticker, sticker.fullType, stickerSet.emojis[stickerIndex].emojis);
            modifySticker(item.sticker);
          }

          View view = recyclerView != null ? manager.findViewByPosition(j) : null;
          if (view instanceof StickerSmallView && allowRefreshWithoutAdapterNotification) {
            ((StickerSmallView) view).refreshSticker();
          } else {
            adapter.notifyItemChanged(j);
          }
        }

        break;
      }
      i++;
    }

  }

  public int indexOfStickerSet (TGStickerSetInfo stickerSet) {
    if (stickerSets != null) {
      if (stickerSet.isFakeClassicEmoji()) {
        for (TGStickerSetInfo oldStickerSet : stickerSets) {
          if (stickerSet.getTitleRes() == oldStickerSet.getTitleRes()) {
            return stickerSet.getStartIndex();
          }
        }
      } else {
        for (TGStickerSetInfo oldStickerSet : stickerSets) {
          if (stickerSet.getId() == oldStickerSet.getId()) {
            return stickerSet.getStartIndex();
          }
        }
      }
    }
    return -1;
  }

  public int indexOfStickerSetById (long setId) {
    int index = 0;
    for (TGStickerSetInfo setInfo : stickerSets) {
      if (!setInfo.isSystem() && !setInfo.isFakeClassicEmoji()) {
        if (setInfo.getId() == setId) {
          return index;
        }
        index++;
      }
    }
    return -1;
  }

  public @Nullable TGStickerSetInfo lastStickerSetInfo;
  private int lastStickerSetIndex;

  private void setLastStickerSetInfo (TGStickerSetInfo info, int index) {
    lastStickerSetInfo = info;
    lastStickerSetIndex = index;
  }

  public int indexOfStickerSetByAdapterPosition (int position) {
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

  public int getStickerSetSection () {
    return getStickerSetSection(0);
  }

  public int getStickerSetSection (int offset) {
    if (spanCount == 0 || manager == null) {
      return -1;
    }
    int i = Views.findFirstCompletelyVisibleItemPositionWithOffset(manager, offset);
    if (i != -1) {
      int r = indexOfStickerSetByAdapterPosition(i);
      return r;
    }
    return 0;
  }

  public int getStickersScrollY (boolean showRecentTitle) {
    if (spanCount == 0 || manager == null) {
      return 0;
    }
    int i = manager.findFirstVisibleItemPosition();
    if (i != -1) {
      View v = manager.findViewByPosition(i);
      int additional = v != null ? -v.getTop() : 0;
      int stickerSet = indexOfStickerSetByAdapterPosition(i);
      return additional + adapter.measureScrollTop(i, spanCount, stickerSet, stickerSets, recyclerView, showRecentTitle);
    }
    return 0;
  }


  public interface TGStickerObjModifier {
    TGStickerObj modifyStickerObj (TGStickerObj sticker);
  }

  private TGStickerObjModifier stickerObjModifier;

  public void setStickerObjModifier (TGStickerObjModifier stickerObjModifier) {
    this.stickerObjModifier = stickerObjModifier;
  }

  public TGStickerObj modifySticker (TGStickerObj sticker) {
    return stickerObjModifier != null ? stickerObjModifier.modifyStickerObj(sticker) : sticker;
  }

  public ArrayList<MediaStickersAdapter.StickerItem> modifyStickers (ArrayList<MediaStickersAdapter.StickerItem> items) {
    for (MediaStickersAdapter.StickerItem item: items) {
      if (item.sticker == null) continue;
      modifySticker(item.sticker);
    }
    return items;
  }

  public void invalidateStickerObjModifiers () {
    modifyStickers(adapter.getItems());
  }

  private FactorAnimator lastScrollAnimator;

  public boolean scrollAnimationIsActive () {
    return lastScrollAnimator != null && lastScrollAnimator.isAnimating();
  }

  public void scrollAnimatedImpl (final int scrollDiff, final int currentSection, final int futureSection) {
    final int[] totalScrolled = new int[1];

    if (lastScrollAnimator != null) {
      lastScrollAnimator.cancel();
    }
    recyclerView.setScrollDisabled(true);
    setIgnoreRequests(true, stickerSets.get(futureSection).getId());
    if (callbacks != null) {
      callbacks.setIgnoreMovement(true);
      callbacks.setCurrentStickerSectionByPosition(controllerId, futureSection, true, true);
    }

    lastScrollAnimator = new FactorAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        int diff = (int) ((float) scrollDiff * factor);
        recyclerView.scrollBy(0, diff - totalScrolled[0]);
        totalScrolled[0] = diff;
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        recyclerView.setScrollDisabled(false);
        setIgnoreRequests(false, 0);
        if (callbacks != null) {
          callbacks.setIgnoreMovement(false);
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, Math.min(450, Math.max(250, Math.abs(currentSection - futureSection) * 150)));
    lastScrollAnimator.animateTo(1f);
  }

  private boolean ignoreRequests;
  private long ignoreException;

  private void setIgnoreRequests (boolean ignoreRequests, long exceptSetId) {
    if (this.ignoreRequests != ignoreRequests) {
      this.ignoreRequests = ignoreRequests;
      this.ignoreException = exceptSetId;
      if (!ignoreRequests && manager != null) {
        final int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        final int lastVisiblePosition = manager.findLastVisibleItemPosition();

        if (firstVisiblePosition != RecyclerView.NO_POSITION && lastVisiblePosition != RecyclerView.NO_POSITION) {
          for (int i = lastVisiblePosition; i >= firstVisiblePosition; i--) {
            MediaStickersAdapter.StickerItem item = adapter.getItem(i);
            if (item != null && item.viewType == MediaStickersAdapter.StickerHolder.TYPE_STICKER && item.sticker != null) {
              item.sticker.requestRequiredInformation();
            }
          }
        }
      }
    }
  }

  public int indexOfSticker (TGStickerObj sticker) {
    if (stickerSets != null) {
      for (TGStickerSetInfo stickerSet : stickerSets) {
        boolean isFavorite = stickerSet.isFavorite();
        boolean isRecent = stickerSet.isRecent();
        boolean stickerFavorite = sticker.isFavorite();
        boolean stickerRecent = sticker.isRecent();
        if ((isFavorite && stickerFavorite) || (isRecent && stickerRecent) || (isFavorite == stickerFavorite && isRecent == stickerRecent && stickerSet.getId() == sticker.getStickerSetId())) {
          return adapter.indexOfSticker(sticker, stickerSet.getStartIndex());
        }
      }
    }
    return -1;
  }

  public boolean isIgnoreRequests (long stickerSetId) {
    return ignoreRequests && stickerSetId != ignoreException;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    Views.invalidateChildren(recyclerView);
  }

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(this);
    Emoji.instance().removeEmojiChangeListener(this);
    Views.destroyRecyclerView(recyclerView);
  }

  public void scrollToStickerSet (int stickerSetIndex, boolean showRecentTitle, boolean animated) {
    scrollToStickerSet(stickerSetIndex, EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding(), showRecentTitle, animated);
  }

  public void scrollToStickerSet (int stickerSetIndex, int scrollOffset, boolean showRecentTitle, boolean animated) {
    scrollToStickerSet(stickerSetIndex, scrollOffset, Integer.MIN_VALUE, showRecentTitle, animated);
  }

  public void scrollToStickerSet (int stickerSetIndex, int scrollOffset, int forceScrollTop, boolean showRecentTitle, boolean animated) {
    final int futureSection = indexOfStickerSetByAdapterPosition(stickerSetIndex);
    if (futureSection == -1) {
      return;
    }

    recyclerView.stopScroll();

    final int currentSection = getStickerSetSection();

    if (!animated || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || callbacks == null || Math.abs(futureSection - currentSection) > SCROLL_BY_SECTION_LIMIT) {
      if (callbacks != null) {
        callbacks.setIgnoreMovement(true);
        callbacks.setCurrentStickerSectionByPosition(controllerId, futureSection, true, true);
      }
      manager.scrollToPositionWithOffset(stickerSetIndex, stickerSetIndex == 0 ? 0 : scrollOffset);
      UI.post(() -> {
        if (callbacks != null) {
          callbacks.setIgnoreMovement(false);
        }
      });
    } else {
      final int scrollTop;

      if (forceScrollTop != Integer.MIN_VALUE) {
        scrollTop = forceScrollTop;
      } else if (stickerSetIndex == 0) {
        scrollTop = 0;
      } else {
        scrollTop = Math.max(0, adapter.measureScrollTop(stickerSetIndex, spanCount, futureSection, stickerSets, recyclerView, showRecentTitle) - scrollOffset);
      }

      final int currentScrollTop = getStickersScrollY(showRecentTitle);
      final int scrollDiff = scrollTop - currentScrollTop;

      scrollAnimatedImpl(scrollDiff, currentSection, futureSection);
    }
  }

  public TGStickerSetInfo getStickerSetBySectionIndex (int index) {
    for (TGStickerSetInfo set: stickerSets) {
      if (set.isFakeClassicEmoji() && set.getFakeClassicEmojiSectionId() == index) {
        return set;
      }
    }
    return null;
  }

  /* * */

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    int i = indexOfSticker(sticker);
    if (i != -1) {
      adapter.setStickerPressed(i, isPressed, manager);
    }
  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return callbacks != null && callbacks.canFindChildViewUnder(controllerId, view, recyclerX, recyclerY);
  }

  @Override
  public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {
    if (callbacks != null) {
     callbacks.onSectionInteracted(mediaType, false);
    }
  }

  @Override
  public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {
    if (callbacks != null) {
      callbacks.onSectionInteracted(mediaType, false);
    }
  }

  @Override
  public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) {
    if (callbacks != null) {
      callbacks.onSectionInteracted(mediaType, true);
    }
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return false;
  }

  @Override
  public int getStickersListTop () {
    return Views.getLocationInWindow(recyclerView)[1];
  }

  @Override
  public int getViewportHeight () {
    return -1;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    if (callbacks != null) {
      int i = indexOfStickerSetById(sticker.getStickerSetId());
      return callbacks.onStickerClick(controllerId, view, clickView, i != -1 ? stickerSets.get(i): null, sticker, isMenuClick, sendOptions);
    }
    return false;
  }

  @Override
  public long getStickerOutputChatId () {
    return callbacks != null ? callbacks.findOutputChatId() : 0;
  }

  /* * */

  private int getHeaderItemCount () {
    if (!stickerSets.isEmpty()) {
      return stickerSets.get(0).getStartIndex();
    }

    return 0;
  }

  public int getRecentItemCount () {
    if (!stickerSets.isEmpty()) {
      return stickerSets.get(0).getSize();
    }

    return 0;
  }

  public void onResetRecentEmoji () {
    int headerItemsCount = getHeaderItemCount();
    int recentItemsCount = getRecentItemCount();

    if (recentItemsCount > 0) {
      for (int i = 0; i < recentItemsCount; i++) {
        adapter.getItems().remove(headerItemsCount /*+ i*/);
      }
    }
    ArrayList<RecentEmoji> recents = Emoji.instance().getRecents();
    int newRecentItemsCount = recents.size();

    for (int i = 0; i < stickerSets.size(); i++) {
      TGStickerSetInfo info = stickerSets.get(i);
      if (i == 0) {
        info.setSize(newRecentItemsCount);
      } else {
        info.setStartIndex(info.getStartIndex() + (newRecentItemsCount - recentItemsCount));
      }
    }

    adapter.getItems().ensureCapacity(adapter.getItems().size() + newRecentItemsCount);
    int i = headerItemsCount;
    for (RecentEmoji emoji : recents) {
      adapter.getItems().add(i, new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_DEFAULT_EMOJI, new TGDefaultEmoji(emoji.emoji, true)));
      i++;
    }

    if (newRecentItemsCount > recentItemsCount) {
      adapter.notifyItemRangeInserted(headerItemsCount + recentItemsCount, newRecentItemsCount - recentItemsCount);
    } else if (newRecentItemsCount < recentItemsCount) {
      adapter.notifyItemRangeRemoved(headerItemsCount + newRecentItemsCount, recentItemsCount - newRecentItemsCount);
    }
    adapter.notifyItemRangeChanged(headerItemsCount, Math.min(newRecentItemsCount, recentItemsCount));
  }

  @Override
  public void moveEmoji (int oldIndex, int newIndex) {
    if (callbacks != null) {
      callbacks.setIgnoreMovement(true);
    }
    oldIndex += getHeaderItemCount();
    newIndex += getHeaderItemCount();
    MediaStickersAdapter.StickerItem item = adapter.getItems().remove(oldIndex);
    adapter.getItems().add(newIndex, item);
    adapter.notifyItemMoved(oldIndex, newIndex);
    if (callbacks != null) {
      recyclerView.post(() -> callbacks.setIgnoreMovement(false));
    }
  }

  @Override
  public void addEmoji (int newIndex, RecentEmoji emoji) {
    MediaStickersAdapter.StickerItem item = processRecentEmojiItem(emoji);
    if (item == null) return;

    if (callbacks != null) {
      callbacks.setIgnoreMovement(true);
    }
    newIndex += getHeaderItemCount();

    for (int i = 0; i < stickerSets.size(); i++) {
      TGStickerSetInfo info = stickerSets.get(i);
      if (i == 0) {
        info.setSize(info.getSize() + 1);
      } else {
        info.setStartIndex(info.getStartIndex() + 1);
      }
    }
    adapter.getItems().add(newIndex, item);
    adapter.notifyItemInserted(newIndex);
    if (emoji.isCustomEmoji()) {
      tdlib.emoji().performPostponedRequests();
    }
    if (callbacks != null) {
      recyclerView.post(() -> callbacks.setIgnoreMovement(false));
    }
  }

  @Override
  public void replaceEmoji (int newIndex, RecentEmoji emoji) {
    MediaStickersAdapter.StickerItem item = processRecentEmojiItem(emoji);
    if (item == null) {
      return;
    }

    adapter.replaceItem(newIndex + getHeaderItemCount(), item);
  }

  @Override
  public void removeEmoji (int oldIndex, RecentEmoji emoji) {
    MediaStickersAdapter.StickerItem item = processRecentEmojiItem(emoji);
    if (item == null) {
      return;
    }
    adapter.removeRange(oldIndex + getHeaderItemCount(), 1);
  }

  @Override
  public void onToneChanged (@Nullable String newDefaultTone) {
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    int lastVisiblePosition = manager.findLastVisibleItemPosition();
    if (firstVisiblePosition == -1 || lastVisiblePosition == -1) {
      adapter.notifyItemRangeChanged(0, adapter.getItemCount());
      return;
    }

    int lastChangedPosition = -1;
    int lastChangedCount = 0;
    final ArrayList<int[]> changes = new ArrayList<>();
    for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
      MediaStickersAdapter.StickerItem item = adapter.getItems().get(i);
      boolean changed = item.viewType == MediaStickersAdapter.StickerHolder.TYPE_DEFAULT_EMOJI && item.defaultEmoji != null && item.defaultEmoji.canBeColored();
      if (changed) {
        if (lastChangedPosition == -1) {
          lastChangedPosition = i;
        }
        lastChangedCount++;
      } else if (lastChangedPosition != -1) {
        changes.add(new int[] {lastChangedPosition, lastChangedCount});
        lastChangedPosition = -1;
        lastChangedCount = 0;
      }
    }
    if (lastChangedPosition != -1) {
      changes.add(new int[] {lastChangedPosition, lastChangedCount});
    }
    for (int[] change : changes) {
      if (change[1] == 1) {
        adapter.notifyItemChanged(change[0]);
      } else {
        adapter.notifyItemRangeChanged(change[0], change[1]);
      }
    }
    if (firstVisiblePosition > 0) {
      adapter.notifyItemRangeChanged(0, firstVisiblePosition);
    }
    if (lastVisiblePosition < adapter.getItemCount() - 1) {
      adapter.notifyItemRangeChanged(lastVisiblePosition + 1, adapter.getItemCount() - lastVisiblePosition);
    }
  }

  @Override
  public void onCustomToneApplied (String emoji, @Nullable String newTone, @Nullable String[] newOtherTones) {
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    int lastVisiblePosition = manager.findLastVisibleItemPosition();

    int i = 0;
    for (MediaStickersAdapter.StickerItem item : adapter.getItems()) {
      if (item.viewType == MediaStickersAdapter.StickerHolder.TYPE_DEFAULT_EMOJI && item.defaultEmoji != null && StringUtils.equalsOrBothEmpty(item.defaultEmoji.emoji, emoji)) {
        View view = i >= firstVisiblePosition && i <= lastVisiblePosition ? manager.findViewByPosition(i) : null;
        if (!(view instanceof EmojiView) || !((EmojiView) view).applyTone(emoji, newTone, newOtherTones)) {
          adapter.notifyItemChanged(i);
        }
      }
      i++;
    }
  }

  public ArrayList<MediaStickersAdapter.StickerItem> makeRecentEmojiItems () {
    ArrayList<RecentEmoji> recents = Emoji.instance().getRecents();
    ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>(recents.size());
    for (RecentEmoji recentEmoji : recents) {
      MediaStickersAdapter.StickerItem item = processRecentEmojiItem(recentEmoji);
      if (item == null) {
        continue;
      }
      items.add(item);
    }
    tdlib.emoji().performPostponedRequests();
    return items;
  }

  private boolean onlyClassicEmoji;

  public void setOnlyClassicEmoji (boolean onlyClassicEmoji) {
    this.onlyClassicEmoji = onlyClassicEmoji;
  }

  @Nullable
  private MediaStickersAdapter.StickerItem processRecentEmojiItem (RecentEmoji recentEmoji) {
    if (recentEmoji.isCustomEmoji()) {
      if (onlyClassicEmoji) {
        return null;
      }
      final TdlibEmojiManager.Entry entry = tdlib.emoji().findOrPostponeRequest(recentEmoji.customEmojiId, this);
      final TGStickerObj stickerObj;
      if (entry != null && entry.value != null) {
        stickerObj = new TGStickerObj(tdlib, entry.value, entry.value.fullType, null);
        stickerObj.setIsRecent();
      } else {
        stickerObj = new TGStickerObj(tdlib, null, new TdApi.StickerFullTypeCustomEmoji(recentEmoji.customEmojiId, false), null);
        stickerObj.setTag(recentEmoji.customEmojiId);
        stickerObj.setIsRecent();
      }
      return new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, modifySticker(stickerObj));
    } else {
      return new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_DEFAULT_EMOJI, new TGDefaultEmoji(recentEmoji.emoji, true));
    }
  }

  @TdlibThread
  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    UI.post(() -> onRecentCustomEmojiLoaded(entry));     // bad solution: Multiple call to UI.post
  }

  public void onRecentCustomEmojiLoaded (TdlibEmojiManager.Entry entry) {
    if (entry == null || entry.value == null || stickerSets == null || stickerSets.isEmpty()) return;

    int startIndex = stickerSets.get(0).getStartIndex();
    int endIndex = stickerSets.get(0).getEndIndex();
    for (int a = startIndex; a < endIndex; a++) {
      TGStickerObj stickerObj = adapter.getItem(a).sticker;
      if (stickerObj != null && stickerObj.getTag() == Td.customEmojiId(entry.value)) {
        stickerObj.set(tdlib, entry.value, entry.value.fullType, null);
        modifySticker(stickerObj);
        adapter.notifyItemChanged(a);
        return;
      }
    }
  }

  /* * */

  private int ignoreStickersScroll;

  public boolean isNeedIgnoreScroll () {
    return ignoreStickersScroll != 0;
  }

  private void beforeStickerChanges () {
    ignoreStickersScroll++;
  }

  private void resetScrollCache () {
    // lastStickerSetInfo = null; // FIXME removing current sticker set does not update selection
    // ignoreStickersScroll--;

    if (callbacks != null) {
      callbacks.resetScrollState(true); // FIXME upd: ... fixme what?
    }
    UI.post(() -> {
      /*if (emojiLayout != null && contentView.getCurrentSection() == SECTION_STICKERS) {
        emojiLayout.setCurrentStickerSectionByPosition(getStickerSetSection(), true, true);
        emojiLayout.resetScrollState(true);
      }*/
      ignoreStickersScroll--;
    }, 400);
  }

  public void addStickerSet (TGStickerSetInfo stickerSet, ArrayList<MediaStickersAdapter.StickerItem> items, int index) {
    if (index < 0 || index >= stickerSets.size()) {
      return;
    }

    beforeStickerChanges();

    if (callbacks != null) {
      callbacks.onAddStickerSection(controllerId, index, stickerSet);
    }

    int startIndex = stickerSets.get(index).getStartIndex();
    stickerSets.add(index, stickerSet);
    for (int i = index; i < stickerSets.size(); i++) {
      TGStickerSetInfo nextStickerSet = stickerSets.get(i);
      nextStickerSet.setStartIndex(startIndex);
      startIndex += nextStickerSet.getSize() + 1;
    }

    adapter.addRange(stickerSet.getStartIndex(), modifyStickers(items));
    resetScrollCache();
  }

  public int removeStickerSet (TGStickerSetInfo stickerSet) {
    int i = stickerSets.indexOf(stickerSet);
    if (i != -1) {
      beforeStickerChanges();
      stickerSets.remove(i);
      if (callbacks != null) {
        callbacks.onRemoveStickerSection(controllerId, i);
      }
      int startIndex = stickerSet.getStartIndex();
      adapter.removeRange(startIndex, stickerSet.getSize() + 1);
      for (int j = i; j < stickerSets.size(); j++) {
        TGStickerSetInfo nextStickerSet = stickerSets.get(j);
        nextStickerSet.setStartIndex(startIndex);
        startIndex += nextStickerSet.getSize() + 1;
      }
      resetScrollCache();
    }
    return i;
  }

  public void moveStickerSet (int oldPosition, int newPosition) {
    beforeStickerChanges();

    if (callbacks != null) {
      callbacks.onMoveStickerSection(controllerId, oldPosition, newPosition);
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

    adapter.moveRange(startIndex, itemCount, stickerSet.getStartIndex());
    resetScrollCache();
  }


  /**/

  public interface SpanCountDelegate {
    int calculateSpanCount (int controllerId);
  }

  private SpanCountDelegate spanCountDelegate;
  private int forceSpanCount;

  public final void setItemWidth (int minSpan, int itemWidthDp) {
    setSpanCount(id -> {
      final int width = recyclerView != null ?
        recyclerView.getMeasuredWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight():
        Screen.currentWidth();
      return calculateSpanCount(width, minSpan, Screen.dp(itemWidthDp));
    });
  }

  public static int calculateSpanCount (int width, int minSpan, int itemWidth) {
    return Math.max(minSpan, width / itemWidth);
  }

  public final void setSpanCount (int spanCount) {
    forceSpanCount = spanCount;
    checkSpanCount();
  }

  public final void setSpanCount (SpanCountDelegate spanCountDelegate) {
    this.spanCountDelegate = spanCountDelegate;
    checkSpanCount();
  }

  private int calculateSpanCount () {
    if (forceSpanCount != 0) {
      return forceSpanCount;
    } else if (spanCountDelegate != null) {
      return spanCountDelegate.calculateSpanCount(getId());
    }
    return 1;
  }

  /*  */

  public interface Callback {
    void setIgnoreMovement (boolean silent);
    void resetScrollState (boolean silent);
    void moveHeader (int totalDy);
    void setHasNewHots (@IdRes int controllerId, boolean hasHots);

    boolean onStickerClick (@IdRes int controllerId, StickerSmallView view, View clickView, TGStickerSetInfo stickerSet, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions);
    boolean canFindChildViewUnder (@IdRes int controllerId, StickerSmallView view, int recyclerX, int recyclerY);

    Context getContext ();
    boolean isUseDarkMode ();
    long findOutputChatId ();
    void onSectionInteracted (@EmojiMediaType int mediaType, boolean interactionFinished);
    void onSectionInteractedScroll (@EmojiMediaType int mediaType, boolean moved);
    void setCurrentStickerSectionByPosition (@IdRes int controllerId, int i, boolean isStickerSection, boolean animated);

    void onAddStickerSection (@IdRes int controllerId, int section, TGStickerSetInfo info);
    void onMoveStickerSection (@IdRes int controllerId, int fromSection, int toSection);
    void onRemoveStickerSection (@IdRes int controllerId, int section);

    @Deprecated
    boolean isAnimatedEmojiOnly ();
    float getHeaderHideFactor ();
  }
}
