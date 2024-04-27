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
 * File created on 19/08/2023
 */
package org.thunderdog.challegram.widget.emoji.header;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.EmojiMediaType;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.emoji.section.EmojiSection;
import org.thunderdog.challegram.widget.emoji.section.EmojiSectionView;
import org.thunderdog.challegram.widget.emoji.section.StickerSectionView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class MediaHeaderView extends RecyclerView {
  private static final int OFFSET = 2;

  private MediaAdapter mediaAdapter;
  private EmojiLayout emojiLayout;
  
  public MediaHeaderView (@NonNull Context context) {
    super(context);
    
    setHasFixedSize(true);
    setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180));
    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS :OVER_SCROLL_NEVER);
    setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, Lang.rtl()));
    setPadding(EmojiLayout.getHorizontalPadding(), 0, EmojiLayout.getHorizontalPadding(), 0);
    setClipToPadding(false);
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, EmojiLayout.getHeaderSize()));
  }
  
  public void init (EmojiLayout emojiLayout, ViewController<?> themeProvider, View.OnClickListener onClickListener) {
    this.emojiLayout = emojiLayout;
    setAdapter(mediaAdapter = new MediaAdapter(getContext(), emojiLayout, onClickListener, emojiLayout.isAnimatedEmojiOnly() ? 8 : emojiLayout.getEmojiSectionsSize(), !emojiLayout.isAnimatedEmojiOnly() && Settings.instance().getEmojiMediaSection() == EmojiMediaType.GIF, themeProvider, emojiLayout.isAnimatedEmojiOnly()));
  }

  public boolean hasRecents () {
    return mediaAdapter.hasRecents;
  }

  public void scrollToStickerSectionBySetIndex (int stickerSetIndex, boolean animated) {
    int i = mediaAdapter.headerItems.size() - mediaAdapter.getAddItemCount(false) + stickerSetIndex;
    scrollToSelectedObj(mediaAdapter.getObject(i), animated);
  }

  public void setCurrentStickerSectionByPosition (int i, boolean isStickerSection, boolean animated) {
    if (mediaAdapter.hasRecents && mediaAdapter.hasFavorite && isStickerSection && i >= 1) {
      i--;
    }
    if (isStickerSection) {
      i += mediaAdapter.headerItems.size() - mediaAdapter.getAddItemCount(false);
    }
    setCurrentStickerSection(mediaAdapter.getObject(i), animated);
  }

  public void setShowRecents (boolean showRecents) {
    mediaAdapter.setHasRecents(showRecents);
  }

  public void setShowFavorite (boolean showFavorite) {
    mediaAdapter.setHasFavorite(showFavorite);
  }

  public void setHasNewHots (boolean hasHots) {
    mediaAdapter.setHasNewHots(hasHots);
  }

  public void addStickerSection (int section, TGStickerSetInfo info) {
    mediaAdapter.addStickerSet(section - mediaAdapter.getAddItemCount(true), info);
  }

  public void moveStickerSection (int fromSection, int toSection) {
    int addItems = mediaAdapter.getAddItemCount(true);
    mediaAdapter.moveStickerSet(fromSection - addItems, toSection - addItems);
  }

  public void removeStickerSection (int section) {
    mediaAdapter.removeStickerSet(section - mediaAdapter.getAddItemCount(true));
  }

  public void invalidateStickerSets () {
    mediaAdapter.notifyDataSetChanged();
  }

  public void setStickerSets (ArrayList<TGStickerSetInfo> stickers, boolean showFavorite, boolean showRecents, boolean showTrending, boolean isFound) {
    mediaAdapter.setHasFavorite(showFavorite);
    mediaAdapter.setHasRecents(showRecents);
    mediaAdapter.setShowRecentsAsFound(isFound);
    mediaAdapter.setHasTrending(showTrending);
    mediaAdapter.setStickerSets(stickers);
  }

  private void scrollToSelectedObj (Object obj, boolean animated) {
    int section = mediaAdapter.indexOfObject(obj);
    int first = ((LinearLayoutManager) getLayoutManager()).findFirstVisibleItemPosition();
    int last = ((LinearLayoutManager) getLayoutManager()).findLastVisibleItemPosition();
    int itemWidth = Screen.dp(44);
    float sectionsCount = (float) Screen.currentWidth() / itemWidth;

    if (first != -1) {
      int scrollX = first * itemWidth;
      View v = getLayoutManager().findViewByPosition(first);
      if (v != null) {
        scrollX += -v.getLeft();
      }

      if (section - OFFSET < first) {
        int desiredScrollX = section * itemWidth - itemWidth / 2 - itemWidth;
        int scrollLimit = scrollX + getPaddingLeft();
        int scrollValue = Math.max(desiredScrollX - scrollX, -scrollLimit);
        if (scrollValue < 0) {
          if (animated && emojiLayout.getHeaderHideFactor() != 1f) {
            smoothScrollBy(scrollValue, 0);
          } else {
            scrollBy(scrollValue, 0);
          }
        }
      } else if (section + OFFSET > last) {
        int desiredScrollX = (int) Math.max(0, (section - sectionsCount) * itemWidth + itemWidth * OFFSET + (emojiLayout.isAnimatedEmojiOnly() ? -itemWidth : itemWidth / 2));
        int scrollValue = desiredScrollX - scrollX;
        if (last != -1 && last == mediaAdapter.getItemCount() - 1) {
          View vr = getLayoutManager().findViewByPosition(last);
          if (vr != null) {
            scrollValue = Math.min(scrollValue, vr.getRight() + getPaddingRight() - getMeasuredWidth());
          }
        }
        if (scrollValue > 0) {
          if (animated && emojiLayout.getHeaderHideFactor() != 1f) {
            smoothScrollBy(scrollValue, 0);
          } else {
            scrollBy(scrollValue, 0);
          }
        }
      }
    }
  }

  private void setCurrentStickerSection (Object obj, boolean animated) {
    if (mediaAdapter.setSelectedObject(obj, animated, getLayoutManager())) {
      scrollToSelectedObj(obj, animated);
    }
  }

  private static class MediaHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_EMOJI_SECTION = 0;
    public static final int TYPE_STICKER_SECTION = 1;

    public MediaHolder (View itemView) {
      super(itemView);
    }

    public static MediaHolder create (Context context, int viewType, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, int emojiSectionCount, @Nullable ViewController<?> themeProvider) {
      switch (viewType) {
        case TYPE_EMOJI_SECTION: {
          EmojiSectionView sectionView = new EmojiSectionView(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(sectionView);
          }
          sectionView.setId(R.id.btn_section);
          sectionView.setOnClickListener(onClickListener);
          sectionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new MediaHolder(sectionView);
        }
        case TYPE_STICKER_SECTION: {
          StickerSectionView sectionView = new StickerSectionView(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(sectionView);
          }
          sectionView.setOnLongClickListener(onLongClickListener);
          sectionView.setId(R.id.btn_stickerSet);
          sectionView.setOnClickListener(onClickListener);
          sectionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new MediaHolder(sectionView);
        }
      }
      throw new RuntimeException("viewType == " + viewType);
    }
  }
  
  private static class MediaAdapter extends RecyclerView.Adapter<MediaHolder> implements View.OnLongClickListener {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private final ArrayList<EmojiSection> headerItems;
    private final int sectionItemCount;
    private final EmojiLayout parent;

    private final @Nullable ViewController<?> themeProvider;

    private Object selectedObject;
    private boolean hasRecents, hasFavorite;

    public MediaAdapter (Context context, EmojiLayout parent, OnClickListener onClickListener, int sectionItemCount, boolean selectedIsGifs, @Nullable ViewController<?> themeProvider, boolean hideSectionsExceptRecent) {
      this.context = context;
      this.parent = parent;
      this.onClickListener = onClickListener;
      this.themeProvider = themeProvider;
      this.headerItems = new ArrayList<>();
      if (!hideSectionsExceptRecent) {
        this.headerItems.add(new EmojiSection(parent, -1, R.drawable.baseline_emoticon_outline_24, 0).setActiveDisabled());
        this.headerItems.add(new EmojiSection(parent, -2, R.drawable.deproko_baseline_gif_24, R.drawable.deproko_baseline_gif_filled_24));
        this.headerItems.add(new EmojiSection(parent, -3, R.drawable.outline_whatshot_24, R.drawable.baseline_whatshot_24).setMakeFirstTransparent());
      }
      // this.favoriteSection = new EmojiSection(parent, -4, R.drawable.baseline_star_border_24, R.drawable.baseline_star_24).setMakeFirstTransparent();
      this.recentSection = new EmojiSection(parent, -4, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24).setMakeFirstTransparent();
      this.trendingSection = new EmojiSection(parent, -5, R.drawable.outline_whatshot_24, R.drawable.baseline_whatshot_24).setMakeFirstTransparent();
      this.trendingSection.setIsTrending();

      this.selectedObject = selectedIsGifs ? headerItems.get(1) : recentSection;
      if (selectedIsGifs) {
        this.headerItems.get(1).setFactor(1f, false);
      } else {
        this.recentSection.setFactor(1f, false);
      }

      this.sectionItemCount = sectionItemCount;
      this.stickerSets = new ArrayList<>();
    }

    public void addHeaderItem (EmojiSection emojiSection) {
      this.headerItems.add(emojiSection);
    }

    public void setHasRecents (boolean hasRecents) {
      if (this.hasRecents != hasRecents) {
        this.hasRecents = hasRecents;
        checkRecent();
      }
    }

    public void setShowRecentsAsFound (boolean showRecentAsFound) {
      recentSection.changeIcon(
        showRecentAsFound ? R.drawable.baseline_emoticon_outline_24 : R.drawable.baseline_access_time_24,
        showRecentAsFound ? 0 : R.drawable.baseline_watch_later_24);
    }

    public int getAddItemCount (boolean allowHidden) {
      int i = 0;
      if (allowHidden) {
        if (hasFavorite) {
          i++;
        }
        if (hasRecents) {
          i++;
        }
        if (hasTrending) {
          i++;
        }
      } else {
        if (showingRecentSection) {
          i++;
        }
        if (showingTrendingSection) {
          i++;
        }
      }
      return i;
    }

    private boolean showingRecentSection;

    private void checkRecent () {
      boolean showRecent = hasFavorite || hasRecents;
      if (this.showingRecentSection != showRecent) {
        this.showingRecentSection = showRecent;
        if (showRecent) {
          headerItems.add(recentSection);
          notifyItemInserted(headerItems.size() - 1);
        } else {
          int i = headerItems.indexOf(recentSection);
          if (i != -1) {
            headerItems.remove(i);
            notifyItemRemoved(i);
          }
        }
      } else if (selectedObject != null) {
        int i = indexOfObject(selectedObject);
        if (i != -1) {
          notifyItemRangeChanged(i, 2);
        }
      }
    }

    private boolean showingTrendingSection;

    private void checkTrending () {
      boolean showTrending = hasTrending;
      if (this.showingTrendingSection != showTrending) {
        this.showingTrendingSection = showTrending;
        if (showTrending) {
          headerItems.add(trendingSection);
          notifyItemInserted(headerItems.size() - 1);
        } else {
          int i = headerItems.indexOf(trendingSection);
          if (i != -1) {
            headerItems.remove(i);
            notifyItemRemoved(i);
          }
        }
      } else if (selectedObject != null) {
        int i = indexOfObject(selectedObject);
        if (i != -1) {
          notifyItemRangeChanged(i, 2);
        }
      }
    }

    public void setHasFavorite (boolean hasFavorite) {
      if (this.hasFavorite != hasFavorite) {
        this.hasFavorite = hasFavorite;
        checkRecent();
      }
      /*if (this.showFavorite != showFavorite) {
        this.showFavorite = showFavorite;
        if (showFavorite) {
          int i = showRecents ? headerItems.size() - 1 : headerItems.size();
          headerItems.add(i, favoriteSection);
          notifyItemInserted(i);
        } else {
          int i = headerItems.indexOf(favoriteSection);
          if (i != -1) {
            headerItems.remove(i);
            notifyItemRemoved(i);
          }
        }
      }*/
    }

    private boolean hasNewHots;

    public void setHasNewHots (boolean hasHots) {
      if (this.hasNewHots != hasHots) {
        this.hasNewHots = hasHots;
        // TODO
      }
    }

    private boolean hasTrending;

    public void setHasTrending (boolean hasTrending) {
      if (this.hasTrending != hasTrending) {
        this.hasTrending = hasTrending;
        checkTrending();
      }
    }

    public boolean setSelectedObject (Object obj, boolean animated, RecyclerView.LayoutManager manager) {
      if (this.selectedObject != obj) {
        setSelected(this.selectedObject, false, animated, manager);
        this.selectedObject = obj;
        setSelected(obj, true, animated, manager);
        return true;
      }
      return false;
    }

    private Object getObject (int i) {
      if (i < 0) return null;
      if (i < headerItems.size()) {
        return headerItems.get(i);
      } else {
        int index = i - headerItems.size();
        return index >= 0 && index < stickerSets.size() ? stickerSets.get(index) : null;
      }
    }

    private int indexOfObject (Object obj) {
      int itemCount = getItemCount();
      for (int i = 0; i < itemCount; i++) {
        if (getObject(i) == obj) {
          return i;
        }
      }
      return -1;
    }

    private void setSelected (Object obj, boolean selected, boolean animated, RecyclerView.LayoutManager manager) {
      int index = indexOfObject(obj);
      if (index != -1) {
        switch (getItemViewType(index)) {
          case MediaHolder.TYPE_EMOJI_SECTION: {
            if (index >= 0 && index < headerItems.size()) {
              headerItems.get(index).setFactor(selected ? 1f : 0f, animated);
            }
            break;
          }
          case MediaHolder.TYPE_STICKER_SECTION: {
            View view = manager.findViewByPosition(index);
            if (view != null && view instanceof StickerSectionView) {
              ((StickerSectionView) view).setSelectionFactor(selected ? 1f : 0f, animated);
            } else {
              notifyItemChanged(index);
            }
            break;
          }
        }
      }
    }

    private final ArrayList<TGStickerSetInfo> stickerSets;
    private final EmojiSection recentSection; // favoriteSection
    private final EmojiSection trendingSection; // favoriteSection

    public void removeStickerSet (int index) {
      if (index >= 0 && index < stickerSets.size()) {
        stickerSets.remove(index);
        notifyItemRemoved(index + headerItems.size());
      }
    }

    public void addStickerSet (int index, TGStickerSetInfo info) {
      stickerSets.add(index, info);
      notifyItemInserted(index + headerItems.size());
    }

    public void moveStickerSet (int fromIndex, int toIndex) {
      TGStickerSetInfo info = stickerSets.remove(fromIndex);
      stickerSets.add(toIndex, info);
      fromIndex += headerItems.size();
      toIndex += headerItems.size();
      notifyItemMoved(fromIndex, toIndex);
    }

    public void setStickerSets (ArrayList<TGStickerSetInfo> stickers) {
      if (!stickerSets.isEmpty()) {
        int removedCount = stickerSets.size();
        stickerSets.clear();
        notifyItemRangeRemoved(headerItems.size(), removedCount);
      }
      if (stickers != null && !stickers.isEmpty()) {
        int addedCount;
        if (!stickers.get(0).isSystem()) {
          stickerSets.addAll(stickers);
          addedCount = stickers.size();
        } else {
          addedCount = 0;
          for (int i = 0; i < stickers.size(); i++) {
            TGStickerSetInfo stickerSet = stickers.get(i);
            if (stickerSet.isSystem()) {
              continue;
            }
            stickerSets.add(stickerSet);
            addedCount++;
          }
        }
        notifyItemRangeInserted(headerItems.size(), addedCount);
      }
    }

    @Override
    public MediaHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      return MediaHolder.create(context, viewType, onClickListener, this, sectionItemCount, themeProvider);
    }

    @Override
    public boolean onLongClick (View v) {
      // if (parent != null && parent.animatedEmojiOnly) return false;
      if (v instanceof StickerSectionView) {
        StickerSectionView sectionView = (StickerSectionView) v;
        TGStickerSetInfo info = sectionView.getStickerSet();
        if (parent != null) {
          if (parent.isAnimatedEmojiOnly()) {
            parent.openEmojiSetOptions(info);
          } else {
            parent.removeStickerSet(info);
          }
          return true;
        }
        return false;
      }
      if ((v instanceof EmojiSectionView)) {
        EmojiSectionView sectionView = (EmojiSectionView) v;
        EmojiSection section = sectionView.getSection();

        if (parent != null) {
          if (section == recentSection) {
            parent.clearRecentStickers();
            return true;
          }
        }
      }

      return false;
    }

    @Override
    public void onBindViewHolder (MediaHolder holder, int position) {
      switch (holder.getItemViewType()) {
        case MediaHolder.TYPE_EMOJI_SECTION: {
          EmojiSection section = headerItems.get(position);
          ((EmojiSectionView) holder.itemView).setSection(section);
          holder.itemView.setOnLongClickListener(section == recentSection ? this : null);
          break;
        }
        case MediaHolder.TYPE_STICKER_SECTION: {
          Object obj = getObject(position);
          ((StickerSectionView) holder.itemView).setSelectionFactor(selectedObject == obj ? 1f : 0f, false);
          ((StickerSectionView) holder.itemView).setStickerSet((TGStickerSetInfo) obj);
          break;
        }
      }
    }

    @Override
    public int getItemViewType (int position) {
      if (position < headerItems.size()) {
        return MediaHolder.TYPE_EMOJI_SECTION;
      } else {
        return MediaHolder.TYPE_STICKER_SECTION;
      }
    }

    @Override
    public int getItemCount () {
      return headerItems.size() + (stickerSets != null ? stickerSets.size() : 0);
    }

    @Override
    public void onViewAttachedToWindow (MediaHolder holder) {
      if (holder.getItemViewType() == MediaHolder.TYPE_STICKER_SECTION) {
        ((StickerSectionView) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (MediaHolder holder) {
      if (holder.getItemViewType() == MediaHolder.TYPE_STICKER_SECTION) {
        ((StickerSectionView) holder.itemView).detach();
      }
    }

    @Override
    public void onViewRecycled (MediaHolder holder) {
      if (holder.getItemViewType() == MediaHolder.TYPE_STICKER_SECTION) {
        ((StickerSectionView) holder.itemView).performDestroy();
      }
    }
  }
}
