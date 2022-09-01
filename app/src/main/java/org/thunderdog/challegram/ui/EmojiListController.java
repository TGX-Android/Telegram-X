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
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.EmojiView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.RecentEmoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class EmojiListController extends ViewController<EmojiLayout> implements View.OnClickListener, TGLegacyManager.EmojiLoadListener {
  public EmojiListController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_emoji;
  }

  private CustomRecyclerView recyclerView;
  private GridLayoutManager manager;
  private EmojiAdapter adapter;
  private int spanCount;
  private EmojiToneHelper toneHelper;

  private boolean useDarkMode;

  private int calculateSpanCount () {
    int width = 0;
    if (recyclerView != null) {
      width = recyclerView.getMeasuredWidth();
    }
    if (width == 0) {
      width = Screen.currentWidth();
    }
    return Math.max(MINIMUM_EMOJI_COUNT, width / Screen.dp(48f));
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (recyclerView != null)
      recyclerView.requestLayout();
  }

  @Override
  protected View onCreateView (Context context) {
    manager = new RtlGridLayoutManager(context, spanCount = calculateSpanCount()).setAlignOnly(true);
    toneHelper = new EmojiToneHelper(context, getArgumentsStrict().getToneDelegate(), this);
    adapter = new EmojiAdapter(context, this, this);

    this.useDarkMode = getArgumentsStrict().useDarkMode();

    recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, getArguments());
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView.setLayoutManager(manager);
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS :View.OVER_SCROLL_NEVER);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 140l));
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        boolean isScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING || newState == RecyclerView.SCROLL_STATE_SETTLING;
        if (getArguments() != null && getArguments().getCurrentItem() == 0) {
          getArguments().setIsScrolling(isScrolling);
        }
      }

      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        if (getArguments() != null && getArguments().isWatchingMovements() && getArguments().getCurrentItem() == 0) {
          getArguments().onScroll(getCurrentScrollY());
          if (lastScrollAnimator == null || !lastScrollAnimator.isAnimating()) {
            getArguments().setCurrentEmojiSection(getCurrentSection());
          }
        }
      }
    });
    manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return adapter.items.get(position).viewType == Item.TYPE_EMOJI ? 1 : spanCount;
      }
    });
    recyclerView.setAdapter(adapter);

    TGLegacyManager.instance().addEmojiListener(this);
    Emoji.instance().addEmojiChangeListener(adapter);

    return recyclerView;
  }

  public void resetRecentEmoji () {
    if (adapter != null) {
      adapter.resetRecents();
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    Views.invalidateChildren(recyclerView);
  }

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(this);
    Emoji.instance().removeEmojiChangeListener(adapter);
    Views.destroyRecyclerView(recyclerView);
  }

  public int getCurrentSection () {
    View view = recyclerView.findChildViewUnder(0, EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding());
    if (view != null) {
      int i = recyclerView.getChildAdapterPosition(view);
      if (i != -1) {
        return adapter.getSectionForIndex(i);
      }
    }
    return -1;
  }

  public int getCurrentScrollY () {
    int i = manager.findFirstVisibleItemPosition();
    if (i == -1) {
      return 0;
    }

    View view = manager.findViewByPosition(i);
    int addition = view != null ? -view.getTop() : 0;

    return addition + adapter.measureScrollTop(i, spanCount);
  }

  public void invalidateItems () {
    final int first = manager.findFirstVisibleItemPosition();
    final int last = manager.findLastVisibleItemPosition();

    for (int i = first; i <= last; i++) {
      View view = manager.findViewByPosition(i);
      if (view != null) {
        view.invalidate();
      } else {
        adapter.notifyItemChanged(i);
      }
    }
  }

  @Override
  public void onClick (View v) {
    if (!(v instanceof EmojiView)) {
      return;
    }
    EmojiView emojiView = (EmojiView) v;
    String rawEmoji = emojiView.getRawEmoji();
    String emoji = emojiView.getEmojiColored();
    if (StringUtils.isEmpty(rawEmoji)) {
      return;
    }
    switch (v.getId()) {
      case R.id.emoji:
        Emoji.instance().saveRecentEmoji(rawEmoji);
        break;
      case R.id.emoji_recent:
        // Nothing to do?
        break;
    }
    if (getArguments() != null) {
      getArguments().onEnterEmoji(emoji);
    }
  }

  public void checkSpanCount () {
    if (manager != null) {
      int spanCount = calculateSpanCount();
      if (this.spanCount != spanCount) {
        this.spanCount = spanCount;
        manager.setSpanCount(spanCount);
      }
    }
  }

  private FactorAnimator lastScrollAnimator;

  public void showEmojiSection (int section) {
    recyclerView.stopScroll();

    final int scrollTop;
    int sectionPosition;

    if (section == 0) {
      scrollTop = 0;
      sectionPosition = 0;
    } else {
      sectionPosition = 1;
      if (adapter.recentItemCount > 0) {
        for (int i = 0; i < section; i++) {
          if (i == 0) {
            sectionPosition += adapter.recentItemCount;
          } else {
            sectionPosition += EmojiData.dataColored[i - 1].length + 1;
          }
        }
      } else {
        for (int i = 0; i < section; i++) {
          sectionPosition += EmojiData.dataColored[i].length + 1;
        }
      }
      scrollTop = adapter.measureScrollTop(sectionPosition, spanCount) - EmojiLayout.getHeaderSize() - EmojiLayout.getHeaderPadding();
    }

    final int currentSection = getCurrentSection();

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || Math.abs(section - currentSection) > 4) { // TODO make better smooth scroller
      if (getArguments() != null) {
        getArguments().setIgnoreMovement(true);
      }

      if (section == 0) {
        manager.scrollToPositionWithOffset(0, 0);
      } else {
        manager.scrollToPositionWithOffset(sectionPosition, EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding());
      }

      if (getArguments() != null) {
        getArguments().setIgnoreMovement(false);
      }

      return;
    }

    final int currentScrollTop = getCurrentScrollY();
    final int scrollDiff = scrollTop - currentScrollTop;
    final int[] totalScrolled = new int[1];

    if (lastScrollAnimator != null) {
      lastScrollAnimator.cancel();
    }
    recyclerView.setScrollDisabled(true);
    if (getArguments() != null) {
      getArguments().setIgnoreMovement(true);
      getArguments().setCurrentEmojiSection(section);
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
        if (getArguments() != null) {
          getArguments().setIgnoreMovement(false);
        }
      }
    }, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, Math.min(450, Math.max(250, Math.abs(currentSection - section) * 150)));
    lastScrollAnimator.animateTo(1f);
    // recyclerView.smoothScrollBy(0, scrollDiff);
  }

  private static final int MINIMUM_EMOJI_COUNT = 8;

  private static class Item {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_EMOJI = 1;
    public static final int TYPE_OFFSET = 2;

    public final int viewType;
    public final int strRes;
    public final String emoji;
    public final int emojiColorState;

    public Item (int viewType, int strRes) {
      this.viewType = viewType;
      this.strRes = strRes;
      this.emoji = null;
      this.emojiColorState = EmojiData.STATE_NO_COLORS;
    }

    public Item (int viewType, String emoji) {
      this.viewType = viewType;
      this.emoji = emoji;
      this.emojiColorState = EmojiData.instance().getEmojiColorState(emoji);
      this.strRes = 0;
    }

    public boolean canBeColored () {
      return emojiColorState != EmojiData.STATE_NO_COLORS;
    }
  }

  private static class ItemHolder extends RecyclerView.ViewHolder {
    public ItemHolder (View itemView) {
      super(itemView);
    }
  }

  private static class EmojiAdapter extends RecyclerView.Adapter<ItemHolder> implements Emoji.EmojiChangeListener {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private final ArrayList<Item> items;

    private int recentItemCount;

    public int getSectionForIndex (int position) {
      if (position == 0) {
        return 0;
      }
      position--;
      if (position < recentItemCount) {
        return 0;
      }
      position -= recentItemCount;
      for (int i = 0; i < EmojiData.dataColored.length && position >= 0; i++) {
        int itemCount = EmojiData.dataColored[i].length + 1;
        if (position >= 0 && position < itemCount) {
          return ++i;
        }
        position -= itemCount;
      }
      return 0;
    }

    public int measureScrollTop (int position, int spanCount) {
      if (position == 0) {
        return 0;
      }

      position--;

      int scrollY = EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding();
      /*if (position >= 0 && position < spanCount) {
        return scrollY;
      }*/

      int recentRowCount = (int) Math.ceil((double) Math.min(this.recentItemCount, position) / (double) spanCount);
      scrollY += recentRowCount * (Screen.currentWidth() / spanCount);
      if (position >= 0 && position < recentRowCount) {
        return scrollY;
      }

      position -= this.recentItemCount;
      for (int i = 0; i < EmojiData.dataColored.length && position > 0; i++) {
        scrollY += Screen.dp(32f); // header
        position--;
        if (position > 0) {
          int rowCount = (int) Math.ceil((double) Math.min(EmojiData.dataColored[i].length, position) / (double) spanCount);
          scrollY += rowCount * (Screen.currentWidth() / spanCount);
          position -= EmojiData.dataColored[i].length;
        }
      }

      return scrollY;
    }

    private final EmojiListController parent;

    public EmojiAdapter (Context context, View.OnClickListener onClickListener, EmojiListController parent) {
      this.context = context;
      this.onClickListener = onClickListener;
      this.parent = parent;

      this.items = new ArrayList<>();
      this.items.add(new Item(Item.TYPE_OFFSET, 0));

      setRecents();

      int index = 0;
      for (String[] emoji : EmojiData.dataColored) {
        switch (index++) {
          case 0: {
            items.add(new Item(Item.TYPE_HEADER, R.string.SmileysAndPeople));
            break;
          }
          case 1: {
            items.add(new Item(Item.TYPE_HEADER, R.string.AnimalsAndNature));
            break;
          }
          case 2: {
            items.add(new Item(Item.TYPE_HEADER, R.string.FoodDrink));
            break;
          }
          case 3: {
            items.add(new Item(Item.TYPE_HEADER, R.string.TravelAndPlaces));
            break;
          }
          case 4: {
            items.add(new Item(Item.TYPE_HEADER, R.string.SymbolsAndObjects));
            break;
          }
          case 5: {
            items.add(new Item(Item.TYPE_HEADER, R.string.Flags));
            break;
          }
        }
        items.ensureCapacity(items.size() + emoji.length + 1);
        for (String emojiCode : emoji) {
          items.add(new Item(Item.TYPE_EMOJI, emojiCode));
        }
      }
    }

    public int getHeaderItemCount () {
      return 1;
    }

    public void resetRecents () {
      int oldRecentItemCount = recentItemCount;
      if (recentItemCount > 0) {
        for (int i = recentItemCount; i >= 1; i--) {
          items.remove(i);
        }
      }
      ArrayList<RecentEmoji> recents = Emoji.instance().getRecents();
      recentItemCount = recents.size();
      items.ensureCapacity(items.size() + recentItemCount);
      int i = 1;
      for (RecentEmoji emoji : recents) {
        items.add(i, new Item(Item.TYPE_EMOJI, emoji.emoji));
        i++;
      }

      if (recentItemCount > oldRecentItemCount) {
        notifyItemRangeInserted(1 + oldRecentItemCount, recentItemCount - oldRecentItemCount);
      } else if (recentItemCount < oldRecentItemCount) {
        notifyItemRangeRemoved(1 + recentItemCount, oldRecentItemCount - recentItemCount);
      }
      notifyItemRangeChanged(1, Math.min(recentItemCount, oldRecentItemCount));
    }

    private void setRecents () {
      ArrayList<RecentEmoji> recents = Emoji.instance().getRecents();
      if (recents.isEmpty()) {
        recentItemCount = 0;
      } else {
        items.ensureCapacity(recents.size());
        for (RecentEmoji recentEmoji : recents) {
          items.add(new Item(Item.TYPE_EMOJI, recentEmoji.emoji));
        }
        recentItemCount = recents.size();
      }
    }

    @Override
    public void moveEmoji (int oldIndex, int newIndex) {
      if (parent.getArguments() != null) {
        parent.getArguments().setIgnoreMovement(true);
      }
      oldIndex += getHeaderItemCount();
      newIndex += getHeaderItemCount();
      Item item = items.remove(oldIndex);
      items.add(newIndex, item);
      notifyItemMoved(oldIndex, newIndex);
      if (parent.getArguments() != null) {
        parent.recyclerView.post(() -> parent.getArguments().setIgnoreMovement(false));
      }
    }

    @Override
    public void addEmoji (int newIndex, RecentEmoji emoji) {
      if (parent.getArguments() != null) {
        parent.getArguments().setIgnoreMovement(true);
      }
      newIndex += getHeaderItemCount();
      recentItemCount++;
      items.add(newIndex, new Item(Item.TYPE_EMOJI, emoji.emoji));
      notifyItemInserted(newIndex);
      if (parent.getArguments() != null) {
        parent.recyclerView.post(() -> parent.getArguments().setIgnoreMovement(false));
      }
    }

    @Override
    public void replaceEmoji (int newIndex, RecentEmoji emoji) {
      newIndex += getHeaderItemCount();
      items.set(newIndex, new Item(Item.TYPE_EMOJI, emoji.emoji));
      notifyItemChanged(newIndex);
    }

    @Override
    public void onToneChanged (@Nullable String newDefaultTone) {
      int firstVisiblePosition = parent.manager.findFirstVisibleItemPosition();
      int lastVisiblePosition = parent.manager.findLastVisibleItemPosition();
      if (firstVisiblePosition == -1 || lastVisiblePosition == -1) {
        notifyItemRangeChanged(0, items.size());
        return;
      }

      int lastChangedPosition = -1;
      int lastChangedCount = 0;
      final ArrayList<int[]> changes = new ArrayList<>();
      for (int i = firstVisiblePosition; i <= lastVisiblePosition; i++) {
        Item item = items.get(i);
        boolean changed = item.viewType == Item.TYPE_EMOJI && item.canBeColored();
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
          notifyItemChanged(change[0]);
        } else {
          notifyItemRangeChanged(change[0], change[1]);
        }
      }
      if (firstVisiblePosition > 0) {
        notifyItemRangeChanged(0, firstVisiblePosition);
      }
      if (lastVisiblePosition < items.size() - 1) {
        notifyItemRangeChanged(lastVisiblePosition + 1, items.size() - lastVisiblePosition);
      }
    }

    @Override
    public void onCustomToneApplied (String emoji, @Nullable String newTone, @Nullable String[] newOtherTones) {
      int firstVisiblePosition = parent.manager.findFirstVisibleItemPosition();
      int lastVisiblePosition = parent.manager.findLastVisibleItemPosition();

      int i = 0;
      for (Item item : items) {
        if (item.viewType == Item.TYPE_EMOJI && StringUtils.equalsOrBothEmpty(item.emoji, emoji)) {
          View view = i >= firstVisiblePosition && i <= lastVisiblePosition ? parent.manager.findViewByPosition(i) : null;
          if (!(view instanceof EmojiView) || !((EmojiView) view).applyTone(emoji, newTone, newOtherTones)) {
            notifyItemChanged(i);
          }
        }
        i++;
      }
    }

    @Override
    @NonNull
    public ItemHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      switch (viewType) {
        case Item.TYPE_EMOJI: {
          EmojiView imageView = new EmojiView(context, this.parent.toneHelper);
          imageView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          imageView.setOnClickListener(onClickListener);
          Views.setClickable(imageView);
          RippleSupport.setTransparentSelector(imageView);
          return new ItemHolder(imageView);
        }
        case Item.TYPE_HEADER: {
          TextView textView = new NoScrollTextView(context);
          textView.setTypeface(Fonts.getRobotoMedium());
          if (this.parent.useDarkMode) {
            textView.setTextColor(Theme.getColor(R.id.theme_color_textLight, ThemeId.NIGHT_BLACK));
          } else {
            textView.setTextColor(Theme.textDecentColor());
            this.parent.addThemeTextDecentColorListener(textView);
          }
          textView.setGravity(Lang.gravity());
          textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
          textView.setSingleLine(true);
          textView.setEllipsize(TextUtils.TruncateAt.END);
          textView.setPadding(Screen.dp(14f), Screen.dp(5f), Screen.dp(14f), Screen.dp(5f));
          textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(32f)));
          return new ItemHolder(textView);
        }
        case Item.TYPE_OFFSET: {
          View view = new View(context);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, EmojiLayout.getHeaderSize() + EmojiLayout.getHeaderPadding()));
          return new ItemHolder(view);
        }
      }
      throw new RuntimeException("viewType == " + viewType);
    }

    @Override
    public void onBindViewHolder (@NonNull ItemHolder holder, int position) {
      switch (holder.getItemViewType()) {
        case Item.TYPE_EMOJI: {
          Item item = items.get(position);
          boolean isRecent = position < getHeaderItemCount() + recentItemCount;
          holder.itemView.setId(isRecent ? R.id.emoji_recent : R.id.emoji);
          ((EmojiView) holder.itemView).setEmoji(item.emoji, item.emojiColorState);
          break;
        }
        case Item.TYPE_HEADER: {
          Views.setMediumText((TextView) holder.itemView, Lang.getString(items.get(position).strRes));
          break;
        }
      }
    }

    @Override
    public int getItemViewType (int position) {
      return items.get(position).viewType;
    }

    @Override
    public int getItemCount () {
      return items.size();
    }
  }
}
