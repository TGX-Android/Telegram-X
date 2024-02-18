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
package org.thunderdog.challegram.widget.emoji.header;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.Nullable;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.emoji.EmojiHeaderCollapsibleSectionView;
import org.thunderdog.challegram.widget.emoji.EmojiLayoutRecyclerController;
import org.thunderdog.challegram.widget.emoji.section.EmojiSection;
import org.thunderdog.challegram.widget.emoji.section.EmojiSectionView;
import org.thunderdog.challegram.widget.emoji.section.StickerSectionView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

@SuppressLint("ViewConstructor")
public class EmojiHeaderView extends FrameLayout implements FactorAnimator.Target {
  public static final int DEFAULT_PADDING = 4;

  private final EmojiLayoutEmojiHeaderAdapter adapter;
  private final RecyclerView recyclerView;
  private final EmojiSectionView goToMediaPageSection;
  private final EmojiHeaderViewNonPremium emojiHeaderViewNonPremium;
  private final BoolAnimator hasStickers = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L);

  private final EmojiLayoutRecyclerController.Callback emojiLayout;
  private Paint shadowPaint;
  private boolean isPremium;

  public EmojiHeaderView (@NonNull Context context, EmojiLayoutRecyclerController.Callback emojiLayout, ViewController<?> themeProvider, ArrayList<EmojiSection> emojiSections, @Nullable ArrayList<EmojiSection> expandableSections, boolean allowMedia) {
    super(context);
    this.emojiLayout = emojiLayout;
    this.allowMedia = allowMedia;
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48)));

    LinearLayoutManager manager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, Lang.rtl());

    adapter = new EmojiLayoutEmojiHeaderAdapter(manager, themeProvider, emojiSections, expandableSections);

    recyclerView = new RecyclerView(context) {
      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkShadow();
      }
    };
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180));
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(manager);
    recyclerView.setPadding(Screen.dp(DEFAULT_PADDING), 0, Screen.dp(DEFAULT_PADDING + 44), 0);
    recyclerView.setClipToPadding(false);
    recyclerView.setAdapter(adapter);
    recyclerView.setVisibility(GONE);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        checkShadow();
      }
    });
    addView(recyclerView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    goToMediaPageSection = new EmojiSectionView(context);
    goToMediaPageSection.setSection(new EmojiSection(emojiLayout, EmojiSection.SECTION_SWITCH_TO_MEDIA, R.drawable.deproko_baseline_stickers_24, 0).setActiveDisabled());
    goToMediaPageSection.setForceWidth(Screen.dp(48));
    goToMediaPageSection.setId(R.id.btn_section);
    checkAllowMedia();
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(goToMediaPageSection);
      themeProvider.addThemeInvalidateListener(this);
    }

    addView(goToMediaPageSection, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));

    emojiHeaderViewNonPremium = new EmojiHeaderViewNonPremium(context);
    emojiHeaderViewNonPremium.init(emojiLayout, themeProvider, allowMedia);
    addView(emojiHeaderViewNonPremium, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    updatePaints(Theme.fillingColor());
    setSelectedObjectByPosition(1, false);
  }

  private final boolean allowMedia;
  private boolean mediaMustBeVisibility = false;

  private void checkAllowMedia () {
    goToMediaPageSection.setVisibility(allowMedia && mediaMustBeVisibility ? VISIBLE : GONE);
    recyclerView.setPadding(Screen.dp(DEFAULT_PADDING), 0, Screen.dp(DEFAULT_PADDING + (allowMedia ? 44 : 0)), 0);
  }

  public void setSectionsOnClickListener (OnClickListener onClickListener) {
    this.adapter.setOnClickListener(onClickListener);
    this.goToMediaPageSection.setOnClickListener(onClickListener);
    this.emojiHeaderViewNonPremium.setOnClickListener(onClickListener);
  }

  public void setSectionsOnLongClickListener (OnLongClickListener onLongClickListener) {
    this.adapter.setOnLongClickListener(onLongClickListener);
    this.goToMediaPageSection.setOnLongClickListener(onLongClickListener);
    this.emojiHeaderViewNonPremium.setOnLongClickListener(onLongClickListener);
  }

  public void setCurrentStickerSectionByPosition (int i, boolean animated) {
    setSelectedObjectByPosition(i, animated);
  }

  public void setSelectedObjectByPosition (int i, boolean animated) {
    emojiHeaderViewNonPremium.setSelectedIndex(i - 1, animated);
    setSelectedObject(adapter.getObject(i), animated);
  }

  private static final int OFFSET = 2;

  private void setSelectedObject (Object obj, boolean animated) {
    if (!adapter.setSelectedObject(obj, animated, adapter.manager)) {
      return;
    }

    int section = adapter.getPositionFromIndex(adapter.indexOfObject(obj));
    int first = adapter.manager.findFirstVisibleItemPosition();
    int last = adapter.manager.findLastVisibleItemPosition();
    int itemWidth = Screen.dp(44);
    float sectionsCount = (float) Screen.currentWidth() / itemWidth;

    if (first != -1) {
      int scrollX = first * itemWidth;
      View v = adapter.manager.findViewByPosition(first);
      if (v != null) {
        scrollX -= v.getLeft();
      }

      if (section - OFFSET < first) {
        int desiredScrollX = section * itemWidth - itemWidth / 2 - itemWidth;
        int scrollLimit = scrollX + recyclerView.getPaddingLeft();
        int scrollValue = Math.max(desiredScrollX - scrollX, -scrollLimit);
        if (scrollValue < 0) {
          if (animated && emojiLayout.getHeaderHideFactor() != 1f) {
            recyclerView.smoothScrollBy(scrollValue, 0);
          } else {
            recyclerView.scrollBy(scrollValue, 0);
          }
        }
      } else if (section + OFFSET > last) {
        int desiredScrollX = (int) Math.max(0, (section - sectionsCount + 1) * itemWidth + itemWidth * OFFSET + (emojiLayout.isAnimatedEmojiOnly() ? -itemWidth : itemWidth / 2f));
        int scrollValue = desiredScrollX - scrollX;
        if (last != -1 && last == adapter.getItemCount() - 1) {
          View vr = adapter.manager.findViewByPosition(last);
          if (vr != null) {
            scrollValue = Math.min(scrollValue, vr.getRight() + recyclerView.getPaddingRight() - recyclerView.getMeasuredWidth());
          }
        }
        if (scrollValue > 0) {
          if (animated && emojiLayout.getHeaderHideFactor() != 1f) {
            recyclerView.smoothScrollBy(scrollValue, 0);
          } else {
            recyclerView.scrollBy(scrollValue, 0);
          }
        }
      }
    }
  }

  public void addStickerSection (int index, TGStickerSetInfo info) {
    adapter.addStickerSection(index - adapter.getAddIndexCount(), info);
    checkStickerSections(true);
  }

  public void moveStickerSection (int fromIndex, int toIndex) {
    int addItems = adapter.getAddIndexCount();
    adapter.moveStickerSection(fromIndex - addItems, toIndex - addItems);
  }

  public void removeStickerSection (int index) {
    adapter.removeStickerSection(index);
    checkStickerSections(true);
  }

  public void setStickerSets (ArrayList<TGStickerSetInfo> stickers) {
    adapter.setStickerSets(stickers);
    checkStickerSections(false);
  }

  public void setMediaSection (boolean isGif) {
    emojiHeaderViewNonPremium.setMediaSection(isGif);
    goToMediaPageSection.getSection().changeIcon(isGif ? R.drawable.deproko_baseline_gif_24 : R.drawable.deproko_baseline_stickers_24);
  }

  public void setIsPremium (boolean isPremium, boolean animated) {
    this.isPremium = isPremium;
    checkStickerSections(animated);
  }

  private int shadowColor;

  private void updatePaints (int color) {
    if (color == shadowColor && shadowPaint != null) {
      return;
    }

    LinearGradient shader = new LinearGradient(0, 0, Screen.dp(48), 0, 0, color, Shader.TileMode.CLAMP);
    if (shadowPaint == null) {
      shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }
    shadowPaint.setShader(shader);
    shadowColor = color;
    invalidate();
  }

  private void checkShadow () {
    float range = recyclerView.computeHorizontalScrollRange();
    float offset = recyclerView.computeHorizontalScrollOffset();
    float extent = recyclerView.computeHorizontalScrollExtent();
    float s = range - offset - extent;

    int alpha = (int) (MathUtils.clamp(s / Screen.dp(20f)) * 255);
    shadowPaint.setAlpha(alpha);
    invalidate();
  }

  @Override
  protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
    if (child == goToMediaPageSection) {
      updatePaints(Theme.fillingColor());
      canvas.save();
      canvas.translate(getMeasuredWidth() - Screen.dp(96), 0);
      canvas.drawRect(0, 0, Screen.dp(96), getMeasuredHeight(), shadowPaint);
      canvas.restore();
    }
    return super.drawChild(canvas, child, drawingTime);
  }

  private void checkStickerSections (boolean animated) {
    boolean value = /*adapter.hasStickers() &&*/ isPremium;
    hasStickers.setValue(value, animated);
    if (value) {
      recyclerView.setVisibility(VISIBLE);
      mediaMustBeVisibility = true;
      checkAllowMedia();
    } else {
      emojiHeaderViewNonPremium.setVisibility(VISIBLE);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    recyclerView.setAlpha(hasStickers.getFloatValue());
    goToMediaPageSection.setAlpha(hasStickers.getFloatValue());
    emojiHeaderViewNonPremium.setAlpha(1f - hasStickers.getFloatValue());
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (hasStickers.getValue()) {
      emojiHeaderViewNonPremium.setVisibility(GONE);
    } else {
      recyclerView.setVisibility(GONE);
      mediaMustBeVisibility = false;
      checkAllowMedia();
    }
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_SECTION = 0;
    public static final int TYPE_STICKER_SET = 1;
    public static final int TYPE_SECTIONS_EXPANDABLE = 2;

    public ViewHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static ViewHolder create (Context context, int viewType, ViewController<?> themeProvider, ArrayList<EmojiSection> expandableSections, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
      if (viewType == TYPE_SECTION) {
        EmojiSectionView sectionView = new EmojiSectionView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(sectionView);
        }
        sectionView.setId(R.id.btn_section);
        sectionView.setOnClickListener(onClickListener);
        sectionView.setOnLongClickListener(onLongClickListener);
        sectionView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new ViewHolder(sectionView);
      } else if (viewType == TYPE_STICKER_SET) {
        StickerSectionView sectionView = new StickerSectionView(context);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(sectionView);
        }
        sectionView.setOnLongClickListener(onLongClickListener);
        sectionView.setId(R.id.btn_stickerSet);
        sectionView.setOnClickListener(onClickListener);
        sectionView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new ViewHolder(sectionView);
      } else if (viewType == TYPE_SECTIONS_EXPANDABLE) {
        EmojiHeaderCollapsibleSectionView v = new EmojiHeaderCollapsibleSectionView(context);
        v.init(expandableSections);
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.setOnButtonClickListener(onClickListener);
        if (themeProvider != null) {
          v.setThemeInvalidateListener(themeProvider);
          themeProvider.addThemeInvalidateListener(v);
        }
        return new ViewHolder(v);
      }

      return new ViewHolder(new View(context));
    }
  }

  public static class EmojiLayoutEmojiHeaderAdapter extends RecyclerView.Adapter<ViewHolder> {
    private final ArrayList<EmojiSection> emojiSections;
    private final @Nullable ArrayList<EmojiSection> expandableSections;
    private final ViewController<?> themeProvider;
    private final ArrayList<TGStickerSetInfo> stickerSets;
    private final LinearLayoutManager manager;
    private final int expandableItemSize;
    private final int expandableItemPosition;

    private View.OnClickListener onClickListener;
    private View.OnLongClickListener onLongClickListener;
    private Object selectedObject;

    public EmojiLayoutEmojiHeaderAdapter (LinearLayoutManager manager, ViewController<?> themeProvider, ArrayList<EmojiSection> emojiSections, @Nullable ArrayList<EmojiSection> expandableSections) {
      this.themeProvider = themeProvider;
      this.emojiSections = emojiSections;
      this.stickerSets = new ArrayList<>();
      this.manager = manager;
      this.expandableItemPosition = emojiSections.size();
      this.expandableSections = expandableSections;
      this.expandableItemSize = expandableSections != null ? expandableSections.size(): -1;
    }

    public void setOnClickListener (OnClickListener onClickListener) {
      this.onClickListener = onClickListener;
    }

    public void setOnLongClickListener (OnLongClickListener onLongClickListener) {
      this.onLongClickListener = onLongClickListener;
    }

    public int getAddIndexCount () {
      return emojiSections.size() + (expandableSections != null ? expandableSections.size() - 1: 0);
    }

    public int getAddItemCount () {
      return emojiSections.size() + (expandableItemSize > 0 ? 1 : 0);
    }

    public boolean hasStickers () {
      return !stickerSets.isEmpty();
    }

    public void addStickerSection (int index, TGStickerSetInfo info) {
      stickerSets.add(index, info);
      notifyItemInserted(index + getAddItemCount());
    }

    public void removeStickerSection (int index) {
      if (index >= getAddIndexCount()) {
        index -= getAddIndexCount();
        if (index >= 0 && index < stickerSets.size()) {
          stickerSets.remove(index);
          notifyItemRemoved(index + getAddItemCount());
        }
      } else if (index >= 0 && index < emojiSections.size()) {
        emojiSections.remove(index);
        notifyItemRemoved(index);
      }
    }

    public void moveStickerSection (int fromIndex, int toIndex) {
      TGStickerSetInfo info = stickerSets.remove(fromIndex);
      stickerSets.add(toIndex, info);
      notifyItemMoved(fromIndex + getAddItemCount(), toIndex + getAddItemCount());
    }

    public void setStickerSets (ArrayList<TGStickerSetInfo> stickers) {
      if (!stickerSets.isEmpty()) {
        int removedCount = stickerSets.size();
        stickerSets.clear();
        notifyItemRangeRemoved(getAddItemCount(), removedCount);
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
        notifyItemRangeInserted(getAddItemCount(), addedCount);
      }
    }

    public boolean setSelectedObject (Object obj, boolean animated, RecyclerView.LayoutManager manager) {
      if (this.selectedObject == obj) return false;

      final int oldIndex = indexOfObject(selectedObject);
      final int oldSelectedPosition = getPositionFromIndex(oldIndex);
      final int oldSelectedViewType = getItemViewType(oldSelectedPosition);

      final int index = indexOfObject(obj);
      final int newSelectedPosition = getPositionFromIndex(index);
      final int newSelectedViewType = getItemViewType(newSelectedPosition);

      this.selectedObject = obj;

      if (newSelectedViewType == ViewHolder.TYPE_SECTIONS_EXPANDABLE) {
        View view = manager.findViewByPosition(expandableItemPosition);
        if (view instanceof EmojiHeaderCollapsibleSectionView) {
          ((EmojiHeaderCollapsibleSectionView) view).setSelectedObject((EmojiSection) obj, animated);
        } else {
          notifyItemChanged(newSelectedPosition);
        }
      }

      if (oldSelectedPosition == newSelectedPosition) {
        return true;
      }

      if (newSelectedViewType == ViewHolder.TYPE_STICKER_SET) {
        View view = manager.findViewByPosition(newSelectedPosition);
        if (view instanceof StickerSectionView) {
          ((StickerSectionView) view).setSelectionFactor(1f, animated);
        } else {
          notifyItemChanged(newSelectedPosition);
        }
      } else if (newSelectedViewType == ViewHolder.TYPE_SECTION) {
        ((EmojiSection) getObject(index)).setFactor(1f, animated);
      }

      if (oldIndex == -1) {
        return true;
      }

      if (oldSelectedViewType == ViewHolder.TYPE_STICKER_SET) {
        View view = manager.findViewByPosition(oldSelectedPosition);
        if (view instanceof StickerSectionView) {
          ((StickerSectionView) view).setSelectionFactor(0f, animated);
        } else {
          notifyItemChanged(oldSelectedPosition);
        }
      } else if (oldSelectedViewType == ViewHolder.TYPE_SECTION) {
        ((EmojiSection) getObject(oldIndex)).setFactor(0f, animated);
      } else if (oldSelectedViewType == ViewHolder.TYPE_SECTIONS_EXPANDABLE) {
        View view = manager.findViewByPosition(oldSelectedPosition);
        if (view instanceof EmojiHeaderCollapsibleSectionView) {
          ((EmojiHeaderCollapsibleSectionView) view).setSelectedObject(null, animated);
        } else {
          notifyItemChanged(oldSelectedPosition);
        }
      }

      return true;
    }

    private Object getObject (int i) {
      if (i < 0) return null;
      if (i < emojiSections.size()) {
        return emojiSections.get(i);
      }
      i -= emojiSections.size();
      if (expandableSections != null) {
        if (i < expandableSections.size()) {
          return expandableSections.get(i);
        }
        i -= expandableSections.size();
      }

      return i < stickerSets.size() ? stickerSets.get(i) : null;
    }

    private int indexOfObject (Object obj) {
      Object item;
      int i = 0;
      do {
        item = getObject(i);
        if (obj == item) {
          return i;
        }
        i++;
      } while (item != null);
      return -1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return ViewHolder.create(parent.getContext(), viewType, themeProvider, expandableSections, onClickListener, onLongClickListener);
    }

    @Override
    public void onBindViewHolder (@NonNull ViewHolder holder, int position) {
      final int viewType = getItemViewType(position);

      if (position > expandableItemPosition && expandableItemSize > 0) {
        position -= 1;
      }

      if (viewType == ViewHolder.TYPE_SECTION) {
        ((EmojiSectionView) holder.itemView).setSection(emojiSections.get(position));
      } else if (viewType == ViewHolder.TYPE_STICKER_SET) {
        TGStickerSetInfo info = stickerSets.get(position - emojiSections.size());
        ((StickerSectionView) holder.itemView).setSelectionFactor(info == selectedObject ? 1f : 0f, false);
        ((StickerSectionView) holder.itemView).setStickerSet(info);
      } else if (viewType == ViewHolder.TYPE_SECTIONS_EXPANDABLE) {
        EmojiSection obj = selectedObject instanceof EmojiSection ? ((EmojiSection) selectedObject) : null;
        ((EmojiHeaderCollapsibleSectionView) holder.itemView).setSelectedObject(obj, false);
      }
    }

    private int getPositionFromIndex (int index) {
      if (expandableItemSize > 0 && index >= expandableItemPosition) {
        if (index < expandableItemPosition + expandableItemSize) {
          return expandableItemPosition;
        } else {
          return index - expandableItemSize + 1;
        }
      }
      return index;
    }

    @Override
    public int getItemViewType (int position) {
      if (position == expandableItemPosition && expandableItemSize > 0) {
        return ViewHolder.TYPE_SECTIONS_EXPANDABLE;
      } else if (position > expandableItemPosition && expandableItemSize > 0) {
        position -= 1;
      }

      if (position < emojiSections.size()) {
        return ViewHolder.TYPE_SECTION;
      } else {
        return ViewHolder.TYPE_STICKER_SET;
      }
    }

    @Override
    public int getItemCount () {
      return emojiSections.size() + stickerSets.size() + (expandableItemSize > 0 ? 1 : 0) ;
    }

    @Override
    public void onViewAttachedToWindow (ViewHolder holder) {
      if (holder.getItemViewType() == ViewHolder.TYPE_STICKER_SET) {
        ((StickerSectionView) holder.itemView).attach();
      }
    }

    @Override
    public void onViewDetachedFromWindow (ViewHolder holder) {
      if (holder.getItemViewType() == ViewHolder.TYPE_STICKER_SET) {
        ((StickerSectionView) holder.itemView).detach();
      }
    }

    @Override
    public void onViewRecycled (ViewHolder holder) {
      if (holder.getItemViewType() == ViewHolder.TYPE_STICKER_SET) {
        ((StickerSectionView) holder.itemView).performDestroy();
      }
    }
  }
}
