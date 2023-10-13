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
 * File created on 27/02/2016 at 13:24
 */
package org.thunderdog.challegram.component.emoji;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.EmojiView;
import org.thunderdog.challegram.component.sticker.StickerPreviewView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGDefaultEmoji;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.NonMaterialButton;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.TrendingPackHeaderView;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

public class MediaStickersAdapter extends RecyclerView.Adapter<MediaStickersAdapter.StickerHolder> implements View.OnClickListener {

  private final ViewController<?> context;
  protected final ArrayList<StickerItem> items;
  private final StickerSmallView.StickerMovementCallback callback;
  private final boolean isTrending;
  private @Nullable RecyclerView.LayoutManager manager;
  private final @Nullable ViewController<?> themeProvider;
  private final @Nullable OffsetProvider offsetProvider;
  private final boolean canViewStickerPackByClick;
  private final @Nullable EmojiToneHelper emojiToneHelper;
  private View.OnClickListener classicEmojiClickListener;
  private StickerPreviewView.MenuStickerPreviewCallback menuStickerPreviewCallback;

  private boolean isBig;

  public void setIsBig () {
    isBig = true;
  }

  public MediaStickersAdapter (ViewController<?> context, StickerSmallView.StickerMovementCallback callback, boolean isTrending, @Nullable ViewController<?> themeProvider) {
    this.context = context;
    this.callback = callback;
    this.isTrending = isTrending;
    this.themeProvider = themeProvider;
    this.items = new ArrayList<>();
    this.offsetProvider = null;
    this.canViewStickerPackByClick = true;
    this.emojiToneHelper = null;
  }

  public MediaStickersAdapter (ViewController<?> context, StickerSmallView.StickerMovementCallback callback, boolean isTrending, @Nullable ViewController<?> themeProvider, OffsetProvider offsetProvider, boolean canViewStickerPackByClick,  @Nullable EmojiToneHelper emojiToneHelper) {
    this.context = context;
    this.callback = callback;
    this.isTrending = isTrending;
    this.themeProvider = themeProvider;
    this.items = new ArrayList<>();
    this.offsetProvider = offsetProvider;
    this.canViewStickerPackByClick = canViewStickerPackByClick;
    this.emojiToneHelper = emojiToneHelper;
  }

  public void setManager (@NonNull RecyclerView.LayoutManager manager) {
    this.manager = manager;
  }

  public void setClassicEmojiClickListener (View.OnClickListener classicEmojiClickListener) {
    this.classicEmojiClickListener = classicEmojiClickListener;
  }

  public void setMenuStickerPreviewCallback (StickerPreviewView.MenuStickerPreviewCallback menuStickerPreviewCallback) {
    this.menuStickerPreviewCallback = menuStickerPreviewCallback;
  }

  @NonNull @Override
  public StickerHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return StickerHolder.create(context.context(), context.tdlib(), this, viewType, isTrending, this, classicEmojiClickListener, callback, isBig, themeProvider, offsetProvider, emojiToneHelper, repaintingColorId);
  }

  public int measureScrollTop (int position, int spanCount, int sectionIndex, ArrayList<TGStickerSetInfo> sections, @Nullable RecyclerView recyclerView, boolean haveRecentsTitle) {
    if (position == 0 || sections == null || sectionIndex == -1) {
      return 0;
    }

    position--;

    int scrollY = LayoutParams.getKeyboardTopViewHeight(layoutParams);
    if (position == 0) {
      return scrollY;
    }

    final int stickerViewForceHeight = LayoutParams.getStickerViewForceHeight(layoutParams);
    final int recyclerWidth = recyclerView != null ? recyclerView.getMeasuredWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight(): 0;
    final int stickersRowHeight = stickerViewForceHeight > 0 ? stickerViewForceHeight :
      ((sections.get(0).isTrending() ? Screen.smallestSide() : (recyclerWidth > 0 ? recyclerWidth: Screen.currentWidth())) / spanCount);
    // final int rowSize = ((sections.get(0).isTrending() ? Screen.smallestSide() : Screen.currentWidth()) / spanCount);

    boolean hadFavorite = false;

    for (int i = 0; i < sectionIndex + 1 && position > 0 && i < sections.size(); i++) {
      TGStickerSetInfo stickerSet = sections.get(i);
      if (!stickerSet.isSystem() || stickerSet.isDefaultEmoji()) {
        scrollY += Screen.dp(stickerSet.isTrending() ? 52f : 27f)
          + (stickerSet.isTrending() ? 0: LayoutParams.getHeaderViewPaddingTop(layoutParams));
        position--;
      } else if (stickerSet.isFavorite()) {
        // position--;
        hadFavorite = true;
      } else if (stickerSet.isRecent() && !stickerSet.isFakeClassicEmoji()) {
        position--;
        if (haveRecentsTitle) {
          scrollY += Screen.dp(27f) + LayoutParams.getHeaderViewPaddingTop(layoutParams);;
        }
      }
      if (position > 0) {
        int itemCount = Math.min(stickerSet.isDefaultEmoji() ? stickerSet.getSize() + 1 : stickerSet.isTrending() ? (stickerSet.isEmoji() ? 16 : 5) : stickerSet.getSize(), position);
        int rowCount = (int) Math.ceil((double) itemCount / (double) spanCount);
        scrollY += rowCount * stickersRowHeight;
        position -= itemCount;
      }
    }

    return scrollY;
  }

  public void setStickerPressed (TGStickerObj sticker, boolean isPressed, @Nullable RecyclerView.LayoutManager manager) {
    int i = indexOfSticker(sticker, 0);
    if (i != -1) {
      setStickerPressed(i, isPressed, manager);
    }
  }

  public void setStickerPressed (int index, boolean isPressed, @Nullable RecyclerView.LayoutManager manager) {
    View view = manager != null ? manager.findViewByPosition(index) : null;
    if (view != null && view instanceof StickerSmallView) {
      ((StickerSmallView) view).setStickerPressed(isPressed);
    } else {
      notifyItemChanged(index);
    }
  }

  private LongSparseArray<TGStickerSetInfo> installingStickerSets;

  private boolean isInProgress (long setId) {
    return installingStickerSets != null && installingStickerSets.get(setId) != null;
  }

  private void updateInProgress (TGStickerSetInfo stickerSet) {
    if (manager == null) {
      return;
    }
    int i = stickerSet.getStartIndex();
    View view = manager.findViewByPosition(i);
    if (view != null && getItemViewType(i) == StickerHolder.TYPE_HEADER_TRENDING) {
      ((NonMaterialButton) ((ViewGroup) view).getChildAt(1)).setInProgress(isInProgress(stickerSet.getId()), true);
    } else {
      notifyItemChanged(i);
    }
  }

  public void updateState (TGStickerSetInfo stickerSet) {
    if (manager == null) {
      return;
    }
    int i = stickerSet.getStartIndex();
    View view = manager.findViewByPosition(i);
    if (view != null && getItemViewType(i) == StickerHolder.TYPE_HEADER_TRENDING) {
      ((NonMaterialButton) ((ViewGroup) view).getChildAt(1)).setIsDone(stickerSet.isInstalled(), false);
      ((ViewGroup) view).getChildAt(0).setVisibility(stickerSet.isViewed() ? View.GONE : View.VISIBLE);
    } else {
      notifyItemChanged(i);
    }
  }

  public void updateDone (TGStickerSetInfo stickerSet) {
    if (manager == null) {
      return;
    }
    int i = stickerSet.getStartIndex();
    View view = manager.findViewByPosition(i);
    if (view != null && getItemViewType(i) == StickerHolder.TYPE_HEADER_TRENDING && manager.getItemViewType(view) == StickerHolder.TYPE_HEADER_TRENDING) {
      ((NonMaterialButton) ((ViewGroup) view).getChildAt(1)).setIsDone(stickerSet.isInstalled(), true);
    } else {
      notifyItemChanged(i);
    }
  }

  private void installStickerSet (final TGStickerSetInfo stickerSet) {
    if (installingStickerSets == null) {
      installingStickerSets = new LongSparseArray<>();
    } else if (installingStickerSets.get(stickerSet.getId()) != null) {
      return;
    }
    installingStickerSets.put(stickerSet.getId(), stickerSet);
    context.tdlib().client().send(new TdApi.ChangeStickerSet(stickerSet.getId(), true, false), object -> context.tdlib().ui().post(() -> {
      installingStickerSets.remove(stickerSet.getId());
      updateInProgress(stickerSet);
      if (object.getConstructor() == TdApi.Ok.CONSTRUCTOR) {
        stickerSet.setIsInstalled();
        updateDone(stickerSet);
      }
    }));
  }

  protected void onToggleCollapseRecentStickers (TextView collapseView, TGStickerSetInfo recentSet) {
    // override in children
  }

  @Override
  public void onClick (View v) {
    Object tag = v.getTag();
    if (tag != null && tag instanceof TGStickerSetInfo) {
      TGStickerSetInfo stickerSet = (TGStickerSetInfo) tag;
      final int viewId = v.getId();
      if (viewId == R.id.btn_addStickerSet) {
        if (stickerSet.isEmoji() && !context.tdlib().account().isPremium()) {
          context.context().tooltipManager().builder(v).show(context.tdlib(), R.string.EmojiOnlyForPremium);
        } else {
          ((NonMaterialButton) v).setInProgress(true, true);
          installStickerSet(stickerSet);
        }
      } else if (viewId == R.id.btn_toggleCollapseRecentStickers) {
        onToggleCollapseRecentStickers((TextView) v, stickerSet);
        updateCollapseView((TextView) v, stickerSet);
      } else if (canViewStickerPackByClick) {
        stickerSet.show(context);
      }
    }
  }

  public void updateCollapseView (ViewGroup viewGroup, TGStickerSetInfo stickerSet) {
    View collapseView = viewGroup.findViewById(R.id.btn_toggleCollapseRecentStickers);
    if (collapseView instanceof TextView) {
      updateCollapseView((TextView) collapseView, stickerSet);
    }
  }

  public void updateCollapseView (TextView collapseView, TGStickerSetInfo stickerSet) {
    updateCollapseView(collapseView, stickerSet, R.string.ShowXMoreStickers);
  }

  public void updateCollapseView (TextView collapseView, TGStickerSetInfo stickerSet, @StringRes int showMoreRes) {
    if (stickerSet != null && stickerSet.getFullSize() > Config.DEFAULT_SHOW_RECENT_STICKERS_COUNT) {
      if (stickerSet.isCollapsed()) {
        int moreSize = stickerSet.getFullSize() - stickerSet.getSize();
        collapseView.setText(Lang.pluralBold(showMoreRes, moreSize));
      } else {
        collapseView.setText(R.string.ShowLessStickers);
      }
      collapseView.setVisibility(View.VISIBLE);
    } else {
      collapseView.setVisibility(View.GONE);
    }
  }

  @Override
  public void onBindViewHolder (StickerHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT: {
        ((StickerSmallView) holder.itemView).setSticker(TGStickerObj.makeDefaultPremiumStar(context.tdlib()));
        break;
      }
      case StickerHolder.TYPE_STICKER: {
        TGStickerObj sticker = getSticker(position);
        if (sticker != null && sticker.isEmpty()) {
          sticker.requestRequiredInformation();
        }
        ((StickerSmallView) holder.itemView).setSticker(sticker);
        break;
      }
      case StickerHolder.TYPE_HEADER: {
        TGStickerSetInfo stickerSet = getStickerSet(position);
        Views.setMediumText(((TextView) holder.itemView), stickerSet != null ? stickerSet.getTitle() : "");
        Views.setTextGravity((TextView) holder.itemView, Lang.gravity());
        break;
      }
      case StickerHolder.TYPE_SEPARATOR_COLLAPSABLE: {
        TGStickerSetInfo stickerSet = getStickerSet(position);
        TextView collapseView = ((CollapsableSeparatorView) holder.itemView).textView;
        updateCollapseView(collapseView, stickerSet, R.string.ShowXMoreEmoji);
        collapseView.setTag(stickerSet);
        break;
      }
      case StickerHolder.TYPE_HEADER_COLLAPSABLE: {
        TGStickerSetInfo stickerSet = getStickerSet(position);
        TextView titleView = (TextView) ((ViewGroup) holder.itemView).getChildAt(0);
        Views.setMediumText(titleView, stickerSet != null ? stickerSet.getTitle() : "");
        Views.setTextGravity(titleView, Lang.gravity());

        TextView collapseView = (TextView) ((ViewGroup) holder.itemView).getChildAt(1);
        updateCollapseView(collapseView, stickerSet);
        collapseView.setTag(stickerSet);
        break;
      }
      case StickerHolder.TYPE_HEADER_TRENDING: {
        TGStickerSetInfo stickerSet = getStickerSet(position);
        String highlight = getHighlightText(position);
        if (stickerSet != null && !stickerSet.isViewed()) {
          stickerSet.view();
        }
        TrendingPackHeaderView contentView = (TrendingPackHeaderView) holder.itemView;
        contentView.setStickerSetInfo(context, stickerSet, highlight,
          stickerSet != null && isInProgress(stickerSet.getId()),
          stickerSet != null && !stickerSet.isViewed()
        );
        break;
      }
      case StickerHolder.TYPE_DEFAULT_EMOJI: {
        TGDefaultEmoji defaultEmoji = getDefaultEmojiString(position);
        if (defaultEmoji != null) {
          holder.itemView.setId(defaultEmoji.isRecent ? R.id.emoji_recent : R.id.emoji);
          ((EmojiView) holder.itemView).setEmoji(defaultEmoji.emoji, defaultEmoji.emojiColorState);
        }
        break;
      }
    }
  }

  @Override
  public void onViewAttachedToWindow (StickerHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT:
      case StickerHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).attach();
        break;
      }
      case StickerHolder.TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).attach();
        break;
      }
      case StickerHolder.TYPE_PROGRESS_OFFSETABLE: {
        ((ProgressComponentView) ((ViewGroup) (holder.itemView)).getChildAt(0)).attach();
        break;
      }
    }
  }

  @Override
  public void onViewDetachedFromWindow (StickerHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT:
      case StickerHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).detach();
        break;
      }
      case StickerHolder.TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).detach();
        break;
      }
      case StickerHolder.TYPE_PROGRESS_OFFSETABLE: {
        ((ProgressComponentView) ((ViewGroup) (holder.itemView)).getChildAt(0)).detach();
        break;
      }
    }
  }

  @Override
  public void onViewRecycled (StickerHolder holder) {
    switch (holder.getItemViewType()) {
      case StickerHolder.TYPE_EMOJI_STATUS_DEFAULT:
      case StickerHolder.TYPE_STICKER: {
        ((StickerSmallView) holder.itemView).performDestroy();
        break;
      }
      case StickerHolder.TYPE_PROGRESS: {
        ((ProgressComponentView) holder.itemView).performDestroy();
        break;
      }
      case StickerHolder.TYPE_PROGRESS_OFFSETABLE: {
        ((ProgressComponentView) ((ViewGroup) (holder.itemView)).getChildAt(0)).performDestroy();
        break;
      }
    }
  }

  public void addRange (int startIndex, ArrayList<StickerItem> items) {
    this.items.addAll(startIndex, items);
    notifyItemRangeInserted(startIndex, items.size());
  }

  public void insertRange (int index, ArrayList<StickerItem> items) {
    insertRange(index, items, true);
  }

  public void insertRange (int index, ArrayList<StickerItem> items, boolean notify) {
    this.items.addAll(index, items);
    if (notify) {
      notifyItemRangeInserted(index, items.size());
    }
  }

  public void removeRange (int startIndex, int count, boolean notify) {
    for (int i = startIndex + count - 1; i >= startIndex; i--) {
      items.remove(i);
    }
    if (notify) {
      notifyItemRangeRemoved(startIndex, count);
    }
  }

  public void removeRange (int startIndex, int count) {
    removeRange(startIndex, count, true);
  }

  public void moveRange (int fromIndex, int itemCount, int toIndex) {
    ArrayList<StickerItem> items = new ArrayList<>(itemCount);
    for (int i = fromIndex + itemCount - 1; i >= fromIndex; i--) {
      items.add(0, this.items.remove(i));
    }
    notifyItemRangeRemoved(fromIndex, itemCount);
    this.items.addAll(toIndex, items);
    notifyItemRangeInserted(toIndex, itemCount);
  }

  public StickerItem getItem (int index) {
    return items.get(index);
  }

  @Override
  public int getItemCount () {
    return items.size();
  }

  public int indexOfSticker (TGStickerObj sticker) {
    int i = 0;
    for (StickerItem item : items) {
      if (item.viewType == StickerHolder.TYPE_STICKER && sticker.equals(item.sticker)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public int indexOfSticker (TGStickerObj sticker, int startIndex) {
    if (startIndex == 0) {
      return indexOfSticker(sticker);
    } else {
      final int size = items.size();
      for (int i = startIndex; i < size; i++) {
        StickerItem item = items.get(i);
        if (item.viewType == StickerHolder.TYPE_STICKER && sticker.equals(item.sticker)) {
          return i;
        }
      }
    }
    return -1;
  }

  public interface OffsetProvider {
    int provideOffset ();
    int provideReverseOffset ();
    void onContentScroll (float shadowFactor);
    void onScrollFinished ();
  }

  public static class StickerItem {
    public int viewType;
    public final TGStickerObj sticker;
    public final TGStickerSetInfo stickerSet;
    public final TGDefaultEmoji defaultEmoji;
    public String highlight;

    public StickerItem (int viewType) {
      this.viewType = viewType;
      this.sticker = null;
      this.stickerSet = null;
      this.defaultEmoji = null;
    }

    public StickerItem (int viewType, TGStickerObj sticker) {
      this.viewType = viewType;
      this.sticker = sticker;
      this.stickerSet = null;
      this.defaultEmoji = null;
    }

    public StickerItem (int viewType, TGStickerSetInfo info) {
      this.viewType = viewType;
      this.sticker = null;
      this.stickerSet = info;
      this.defaultEmoji = null;
    }

    public StickerItem (int viewType, TGDefaultEmoji defaultEmojiString) {
      this.viewType = viewType;
      this.sticker = null;
      this.stickerSet = null;
      this.defaultEmoji = defaultEmojiString;
    }

    public StickerItem setHighlightValue (String highlight) {
      this.highlight = highlight;
      return this;
    }

    public boolean setViewType (int viewType) {
      if (this.viewType != viewType) {
        this.viewType = viewType;
        return true;
      }
      return false;
    }
  }

  public int findSetIndexByPosition (int index) {
    return -1;
  }

  public void setItem (StickerItem item) {
    clear();
    if (item != null) {
      items.add(item);
      notifyItemInserted(0);
    }
  }

  public void replaceItem (int index, StickerItem item) {
    items.set(index, item);
    notifyItemChanged(index);
  }

  public ArrayList<StickerItem> getItems () {
    return items;
  }

  private void clear () {
    if (!this.items.isEmpty()) {
      int count = this.items.size();
      this.items.clear();
      notifyItemRangeRemoved(0, count);
    }
  }

  public void setItems (ArrayList<StickerItem> items) {
    clear();
    if (items != null && !items.isEmpty()) {
      this.items.addAll(items);
      notifyItemRangeInserted(0, items.size());
    }
  }

  public void addItems (ArrayList<StickerItem> items) {
    if (items != null && !items.isEmpty()) {
      int index = this.items.size();
      this.items.addAll(items);
      notifyItemRangeInserted(index, items.size());
    }
  }

  public void addItem (StickerItem item) {
    int index = this.items.size();
    this.items.add(item);
    notifyItemRangeInserted(index, 1);
  }

  @Override
  public int getItemViewType (int position) {
    return items.get(position).viewType;
  }

  public @Nullable TGStickerObj getSticker (int position) {
    return position >= 0 && position < items.size() ? items.get(position).sticker : null;
  }

  public @Nullable TGStickerSetInfo getStickerSet (int position) {
    return position >= 0 && position < items.size() ? items.get(position).stickerSet : null;
  }

  public @Nullable TGDefaultEmoji getDefaultEmojiString (int position) {
    return position >= 0 && position < items.size() ? items.get(position).defaultEmoji : null;
  }

  public @Nullable String getHighlightText (int position) {
    return position >= 0 && position < items.size() ? items.get(position).highlight : null;
  }

  private @PorterDuffColorId int repaintingColorId = ColorId.iconActive;

  public void setRepaintingColorId (@PorterDuffColorId int repaintingColorId) {
    this.repaintingColorId = repaintingColorId;
  }

  public static class StickerHolder extends RecyclerView.ViewHolder {
    public static final int TYPE_STICKER = 0;
    public static final int TYPE_EMPTY = 1;
    public static final int TYPE_HEADER = 2;
    public static final int TYPE_HEADER_COLLAPSABLE = 3;
    public static final int TYPE_KEYBOARD_TOP = 4;
    public static final int TYPE_NO_STICKERSETS = 5;
    public static final int TYPE_PROGRESS = 6;
    public static final int TYPE_COME_AGAIN_LATER = 7;
    public static final int TYPE_HEADER_TRENDING = 8;
    public static final int TYPE_SEPARATOR = 10;
    public static final int TYPE_EMOJI_STATUS_DEFAULT = 11;
    public static final int TYPE_NO_EMOJISETS = 12;
    public static final int TYPE_PROGRESS_OFFSETABLE = 13;
    public static final int TYPE_PADDING_OFFSETABLE = 14;
    public static final int TYPE_DEFAULT_EMOJI = 15;
    public static final int TYPE_SEPARATOR_COLLAPSABLE = 16;

    public StickerHolder (View itemView) {
      super(itemView);
    }

    public static @NonNull StickerHolder create (Context context, Tdlib tdlib, MediaStickersAdapter adapter, int viewType, boolean isTrending, View.OnClickListener onClickListener, View.OnClickListener classicEmojiClickListener, StickerSmallView.StickerMovementCallback callback, boolean isBig, @Nullable ViewController<?> themeProvider, @Nullable OffsetProvider offsetProvider, @Nullable EmojiToneHelper toneHelper, @PorterDuffColorId int repaintingColorId) {
      switch (viewType) {
        case TYPE_EMOJI_STATUS_DEFAULT:
        case TYPE_STICKER: {
          StickerSmallView view;
          view = new StickerSmallView(context);
          view.setForceHeight(LayoutParams.getStickerViewForceHeight(adapter.layoutParams));
          view.init(tdlib);
          view.setThemedColorId(repaintingColorId);
          if (isTrending) {
            view.setIsTrending();
          }
          view.setStickerMovementCallback(callback);
          view.setMenuStickerPreviewCallback(adapter.menuStickerPreviewCallback);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          if (viewType == TYPE_EMOJI_STATUS_DEFAULT) {
            view.setIsPremiumStar();
          }
          return new StickerHolder(view);
        }
        case TYPE_EMPTY: {
          View view = new View(context);
          view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new StickerHolder(view);
        }
        case TYPE_HEADER: {
          final int paddingTop = LayoutParams.getHeaderViewPaddingTop(adapter.layoutParams);
          final int paddingHorizontal = LayoutParams.getHeaderViewPaddingHorizontal(adapter.layoutParams);

          TextView textView = new NoScrollTextView(context);
          textView.setTypeface(Fonts.getRobotoMedium());
          textView.setTextColor(Theme.textDecentColor());
          if (themeProvider != null) {
            themeProvider.addThemeTextDecentColorListener(textView);
          }
          textView.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          textView.setSingleLine(true);
          textView.setEllipsize(TextUtils.TruncateAt.END);
          textView.setPadding(paddingHorizontal, paddingTop, paddingHorizontal, Screen.dp(5f));
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(27f) + paddingTop));
          return new StickerHolder(textView);
        }
        case TYPE_SEPARATOR_COLLAPSABLE: {
          CollapsableSeparatorView v = new CollapsableSeparatorView(context);
          v.init(themeProvider, onClickListener);
          return new StickerHolder(v);
        }
        case TYPE_HEADER_COLLAPSABLE: {
          LinearLayout viewGroup = new LinearLayout(context);
          viewGroup.setOrientation(LinearLayout.HORIZONTAL);
          viewGroup.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(32f)));
          for (int i = 0; i < 2; i++) {
            TextView textView = new NoScrollTextView(context);
            textView.setTextColor(Theme.textDecentColor());
            if (themeProvider != null) {
              themeProvider.addThemeTextDecentColorListener(textView);
            }
            textView.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setPadding(Screen.dp(14f), Screen.dp(5f), Screen.dp(14f), Screen.dp(5f));
            if (i == 0) {
              textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
              textView.setTypeface(Fonts.getRobotoMedium());
              textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            } else {
              textView.setId(R.id.btn_toggleCollapseRecentStickers);
              textView.setOnClickListener(onClickListener);
              textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
              textView.setTypeface(Fonts.getRobotoRegular());
              textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            viewGroup.addView(textView);
          }

          return new StickerHolder(viewGroup);
        }
        case TYPE_HEADER_TRENDING: {
          TrendingPackHeaderView contentView = new TrendingPackHeaderView(context);
          contentView.setOnClickListener(onClickListener);
          contentView.setPadding(Screen.dp(16f), Screen.dp(isBig ? 18f : 13f) - EmojiLayout.getHeaderPadding(), Screen.dp(16f), 0);
          contentView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(isBig ? 57f : 52f)));
          contentView.setButtonOnClickListener(onClickListener);
          contentView.setThemeProvider(themeProvider);

          return new StickerHolder(contentView);
        }
        case TYPE_KEYBOARD_TOP: {
          View view = new View(context);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LayoutParams.getKeyboardTopViewHeight(adapter.layoutParams)));
          return new StickerHolder(view);
        }
        case TYPE_SEPARATOR: {
          SeparatorView separatorView = new SeparatorView(context);
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(separatorView);
          }
          separatorView.setAlignBottom();
          separatorView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(5f)));
          return new StickerHolder(separatorView);
        }
        case TYPE_COME_AGAIN_LATER:
        case TYPE_NO_EMOJISETS:
        case TYPE_NO_STICKERSETS: {
          TextView textView = new NoScrollTextView(context);
          textView.setTypeface(Fonts.getRobotoRegular());
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          textView.setTextColor(Theme.textDecentColor());
          if (themeProvider != null) {
            themeProvider.addThemeTextDecentColorListener(textView);
          }
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          textView.setSingleLine(true);
          textView.setText(Lang.getString(viewType == TYPE_NO_EMOJISETS ? R.string.NoEmojiSetsFound : viewType == TYPE_COME_AGAIN_LATER ? R.string.ComeAgainLater : R.string.NoStickerSets));
          textView.setGravity(Gravity.CENTER);
          textView.setEllipsize(TextUtils.TruncateAt.END);
          //noinspection ResourceType
          textView.setPadding(Screen.dp(14f), isBig ? 0 : EmojiLayout.getHeaderSize(), Screen.dp(14f), 0);
          return new StickerHolder(textView);
        }
        case TYPE_PROGRESS: {
          ProgressComponentView progressView = new ProgressComponentView(context);
          progressView.initBig(1f);
          progressView.setPadding(0, isBig ? 0 : EmojiLayout.getHeaderSize(), 0, 0);
          progressView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          return new StickerHolder(progressView);
        }
        case TYPE_PROGRESS_OFFSETABLE: {
          FrameLayoutFix contentView = new FrameLayoutFix(context) {
            @Override
            protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
              super.onMeasure(widthMeasureSpec, offsetProvider != null ? MeasureSpec.makeMeasureSpec(offsetProvider.provideReverseOffset(), MeasureSpec.EXACTLY) : heightMeasureSpec);
            }
          };
          ProgressComponentView view = new ProgressComponentView(context);
          view.initBig(1f);
          view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
          contentView.addView(view);
          return new StickerHolder(contentView);
        }
        case TYPE_PADDING_OFFSETABLE: {
          View view = new View(context) {
            @Override
            protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
              setMeasuredDimension(
                getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                offsetProvider != null ? MeasureSpec.makeMeasureSpec(offsetProvider.provideOffset(), MeasureSpec.EXACTLY) : heightMeasureSpec);
            }
          };
          return new StickerHolder(view);
        }
        case TYPE_DEFAULT_EMOJI: {
          EmojiView imageView = new EmojiView(context, tdlib, toneHelper);
          imageView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          imageView.setOnClickListener(classicEmojiClickListener);
          Views.setClickable(imageView);
          RippleSupport.setTransparentSelector(imageView);
          return new StickerHolder(imageView);
        }
      }
      throw new UnsupportedOperationException("viewType == " + viewType);
    }
  }

  public static class CollapsableSeparatorView extends FrameLayoutFix {
    private final TextView textView;
    private final LinearLayout linearLayout;
    private final ImageView imageView;
    private final SeparatorView separatorView;

    public CollapsableSeparatorView (@NonNull Context context) {
      super(context);

      textView = new NoScrollTextView(context);
      textView.setTextColor(Theme.textDecentColor());
      textView.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
      textView.setSingleLine(true);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      textView.setTypeface(Fonts.getRobotoRegular());
      textView.setId(R.id.btn_toggleCollapseRecentStickers);
      textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

      imageView = new ImageView(context);
      imageView.setScaleType(ImageView.ScaleType.CENTER);
      imageView.setImageResource(R.drawable.baseline_small_arrow_down_18);
      imageView.setColorFilter(new PorterDuffColorFilter(Theme.iconColor(), PorterDuff.Mode.SRC_IN));
      imageView.setLayoutParams(LayoutHelper.createLinear(18, 18, 0, Gravity.NO_GRAVITY, 0, 0, 4, 0));

      separatorView = new SeparatorView(context);
      separatorView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(5f), Gravity.CENTER));

      linearLayout = new LinearLayout(context);
      linearLayout.setOrientation(LinearLayout.HORIZONTAL);
      linearLayout.setGravity(Gravity.CENTER);
      linearLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
      linearLayout.addView(imageView);
      linearLayout.addView(textView);
      linearLayout.setPadding(Screen.dp(24), Screen.dp(3), Screen.dp(24), Screen.dp(3));
      ViewSupport.setThemedBackground(linearLayout, ColorId.filling);

      setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(24f)));
      addView(separatorView);
      addView(linearLayout);
    }

    public void init (ViewController<?> themeProvider, View.OnClickListener onClickListener) {
      if (themeProvider != null) {
        themeProvider.addThemeTextDecentColorListener(textView);
        themeProvider.addThemeInvalidateListener(linearLayout);
        themeProvider.addThemeSpecialFilterListener(imageView, ColorId.icon);
        themeProvider.addThemeInvalidateListener(separatorView);
      }
      linearLayout.setOnClickListener(v -> onClickListener.onClick(textView));
    }
  }

  @Nullable
  private LayoutParams layoutParams;

  public void setLayoutParams (LayoutParams layoutParams) {
    this.layoutParams = layoutParams;
  }

  public static class LayoutParams {    // todo: make Builder class?
    public final static int DEFAULT = -1;

    public final int keyboardTopViewHeight;
    public final int recyclerHorizontalPadding;
    public final int headerViewPaddingTop;
    public final int headerViewPaddingHorizontal;
    public final int stickerViewHeight;

    public LayoutParams (int keyboardTopViewHeight, int recyclerHorizontalPadding, int headerViewPaddingTop, int headerViewPaddingHorizontal, int stickerViewHeight) {
      this.keyboardTopViewHeight = keyboardTopViewHeight;
      this.recyclerHorizontalPadding = recyclerHorizontalPadding;
      this.headerViewPaddingTop = headerViewPaddingTop;
      this.headerViewPaddingHorizontal = headerViewPaddingHorizontal;
      this.stickerViewHeight = stickerViewHeight;
    }

    public static int getKeyboardTopViewHeight (LayoutParams layoutParams) {
      return layoutParams != null && layoutParams.keyboardTopViewHeight != LayoutParams.DEFAULT ?
        layoutParams.keyboardTopViewHeight : EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding();
    }

    public static int getRecyclerViewPaddingHorizontal (LayoutParams layoutParams) {
      return layoutParams != null && layoutParams.recyclerHorizontalPadding != LayoutParams.DEFAULT ?
        layoutParams.recyclerHorizontalPadding : 0;
    }

    public static int getHeaderViewPaddingTop (LayoutParams layoutParams) {
      return layoutParams != null && layoutParams.headerViewPaddingTop != LayoutParams.DEFAULT ?
        layoutParams.headerViewPaddingTop : Screen.dp(5);
    }

    public static int getHeaderViewPaddingHorizontal (LayoutParams layoutParams) {
      return layoutParams != null && layoutParams.headerViewPaddingHorizontal != LayoutParams.DEFAULT ?
        layoutParams.headerViewPaddingHorizontal : Screen.dp(14);
    }

    public static int getStickerViewForceHeight (LayoutParams layoutParams) {
      return layoutParams != null && layoutParams.stickerViewHeight != LayoutParams.DEFAULT ?
        layoutParams.stickerViewHeight : -1;
    }
  }
}
