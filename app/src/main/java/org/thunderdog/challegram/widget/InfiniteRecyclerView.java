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
 * File created on 14/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.List;

public class InfiniteRecyclerView<T> extends RecyclerView implements View.OnClickListener {
  private final TextPaint textPaint;

  private final LinearLayoutManager manager;
  private final boolean repeatItems;

  private int currentIndex;
  private List<T> items;
  private int visibleItemCount;
  private boolean needSeparators = true;

  @Nullable
  private ThemeDelegate forcedTheme;

  public interface MinMaxProvider<T> {
    int getMinMax (InfiniteRecyclerView<T> v, int index);
  }

  private MinMaxProvider<T> minMaxProvider;

  public void setMinMaxProvider (MinMaxProvider<T> provider) {
    this.minMaxProvider = provider;
  }

  public MinMaxProvider<T> getMinMaxProvider () {
    return minMaxProvider;
  }

  public InfiniteRecyclerView (Context context, boolean repeatItems) {
    super(context);

    this.repeatItems = repeatItems;

    this.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    this.textPaint.setTypeface(Fonts.getRobotoRegular());
    this.textPaint.setColor(Theme.textAccentColor());
    this.textPaint.setTextSize(Screen.dp(17f));
    this.itemPadding = Screen.dp(36f);

    setItemAnimator(null);
    setLayoutManager(this.manager = new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
  }

  private int itemPadding;

  public void setItemPadding (int padding) {
    this.itemPadding = padding;
  }

  public void setNeedSeparators (boolean needSeparators) {
    if (this.needSeparators != needSeparators) {
      this.needSeparators = needSeparators;
      invalidate();
    }
  }

  private @Nullable ViewController<?> themeProvider;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    if (forcedTheme == null && themeProvider != null) {
      themeProvider.addThemePaintColorListener(textPaint, ColorId.text);
    }
  }

  public void forceDarkMode () {
    setForcedTheme(ThemeSet.getBuiltinTheme(ThemeId.NIGHT_BLACK));
  }

  public void setForcedTheme (ThemeDelegate forcedTheme) {
    this.forcedTheme = forcedTheme;
    this.textPaint.setColor(forcedTheme != null ? forcedTheme.getColor(ColorId.text) : Theme.textAccentColor());
  }

  private boolean trimItems = true;

  public void setTrimItems (boolean trimItems) {
    this.trimItems = trimItems;
  }

  public void removeRange (int index, int count) {
    adapter.removeRange(index, count);
    this.visibleItemCount = Math.min(items.size(), 5);
    if (this.visibleItemCount % 2 == 0) {
      visibleItemCount--;
    }
    invalidateItemDecorations();
  }

  public void addRange (int index, List<T> items) {
    adapter.addRange(index, items, visibleItemCount);
    invalidateItemDecorations();
  }

  private InfiniteAdapter<T> adapter;
  public void initWithItems (final List<T> items, int currentIndex) {
    if (adapter != null)
      throw new IllegalStateException();
    this.currentIndex = currentIndex;
    this.items = items;
    this.visibleItemCount = Math.min(items.size(), 5);
    if (this.visibleItemCount % 2 == 0) {
      visibleItemCount--;
    }
    if (visibleItemCount > 0) {
      adapter = new InfiniteAdapter<>(getContext(), this, repeatItems, trimItems, visibleItemCount, items, textPaint, itemPadding, forcedTheme == null ? themeProvider : null);
      setAdapter(adapter);
      if (repeatItems) {
        int centerPosition = adapter.getItemCount() / 2;
        int centeredIndex = centerPosition - centerPosition % items.size() - visibleItemCount / 2 + currentIndex;
        if (centeredIndex + visibleItemCount >= adapter.getItemCount()) {
          centeredIndex -= items.size();
        } else if (centeredIndex - visibleItemCount < 0) {
          centeredIndex += items.size();
        }
        manager.scrollToPositionWithOffset(centeredIndex, 0);
      } else {
        if (currentIndex == 0) {
          manager.scrollToPositionWithOffset(0, 0);
        } else {
          manager.scrollToPositionWithOffset(currentIndex, getItemHeight() * 5 / 2 - getItemHeight() / 2);
        }
        addItemDecoration(new ItemDecoration() {
          @Override
          public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == 0) {
              outRect.top = parent.getMeasuredHeight() / 2 - getItemHeight() / 2;
            }
            if (position == items.size() - 1) {
              outRect.bottom = parent.getMeasuredHeight() / 2 - getItemHeight() / 2;
            }
          }
        });
        setOverScrollMode(OVER_SCROLL_NEVER);
      }

      // manager.scrollToPositionWithOffset(centerPosition - centerPosition % visibleItemCount - visibleItemCount / 2, 0);
      addOnScrollListener(new OnScrollListener() {
        @Override
        public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
          if (newState == SCROLL_STATE_DRAGGING) {
            scrollByUser = true;
          }
          if (newState == SCROLL_STATE_IDLE) {
            scrollByUser = false;
            if (repeatItems) {
              View view = manager.findViewByPosition(manager.findFirstVisibleItemPosition());
              if (view != null) {
                setCurrentIndex(findCurrentIndex());
                int topItemHeight = -manager.getDecoratedTop(view);
                if (topItemHeight > 0) {
                  int itemHeight = getItemHeight();
                  if (topItemHeight > itemHeight)
                    topItemHeight = topItemHeight % itemHeight;

                  if (topItemHeight <= itemHeight / 2) {
                    smoothScrollBy(0, -topItemHeight); // scrolling up
                  } else {
                    smoothScrollBy(0, itemHeight - topItemHeight); // scrolling down
                  }
                } else {
                  setCurrentIndex(findCurrentIndex());
                }
              }
            } else {
              int targetIndex = findCurrentIndex();
              selectIndex(targetIndex, true);
            }
          }
        }

        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          int firstVisiblePosition = manager.findFirstVisibleItemPosition();
          if (firstVisiblePosition != -1) {
            final View firstVisibleView = manager.findViewByPosition(firstVisiblePosition);
            if (firstVisibleView != null) {
              ((ItemView) firstVisibleView).invalidateIfNeeded();
              if (repeatItems && firstVisiblePosition < items.size() - visibleItemCount && dy <= 0) {
                manager.scrollToPositionWithOffset(items.size() + firstVisiblePosition, firstVisibleView.getTop());
              }
            }

            final int lastVisiblePosition = manager.findLastVisibleItemPosition();
            final View lastVisibleView = manager.findViewByPosition(lastVisiblePosition);
            if (lastVisibleView != null) {
              ((ItemView) lastVisibleView).invalidateIfNeeded();
              if (repeatItems && lastVisiblePosition > items.size() + visibleItemCount && dy > 0) {
                manager.scrollToPositionWithOffset(lastVisiblePosition - items.size(), lastVisibleView.getTop());
              }
            }

            for (int i = firstVisiblePosition + 1; i < lastVisiblePosition; i++) {
              View view = manager.findViewByPosition(i);
              if (view != null) {
                ((ItemView) view).invalidateIfNeeded();
              }
            }
            if (!repeatItems && scrollByUser) {
              setCurrentIndex(findCurrentIndex());
            }
          }
        }
      });
    }
  }

  private boolean scrollByUser;

  @Override
  public void draw (Canvas c) {
    super.draw(c);

    if (needSeparators) {
      int cy = getMeasuredHeight() / 2;
      int h2 = getItemHeight() / 2;

      c.drawLine(0, cy - h2, getMeasuredWidth(), cy - h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
      c.drawLine(0, cy + h2, getMeasuredWidth(), cy + h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
    }
  }

  @Override
  public void onClick (View v) {
    if (repeatItems) {
      if (v != null && !manager.isSmoothScrolling()) {
        int top = v.getTop();
        int byHeights = top - getItemHeight() * 2;
        int indexDiff = byHeights / getItemHeight();
        while (indexDiff < 0) {
          indexDiff += items.size();
        }
        int index = (findCurrentIndex() + indexDiff) % items.size();
        setCurrentIndex(index);
        smoothScrollBy(0, byHeights);
        if (itemChangeListener != null) {
          itemChangeListener.onCurrentIndexFinalized(this, index);
        }
      }
    } else if (v instanceof ItemView) {
      Item<?> item = ((ItemView) v).getItem();
      if (item != null) {
        int index = minMaxProvider != null ? minMaxProvider.getMinMax(this, item.index) : item.index;
        selectIndex(index, true);
      }
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getItemHeight() * visibleItemCount, MeasureSpec.EXACTLY));
  }

  public static int getItemHeight () {
    return Screen.dp(48f);
  }

  public T getItemAt (int index) {
    return index >= 0 && index < items.size() ? items.get(index) : null;
  }

  public @Nullable T getCurrentItem () {
    return getItemAt(currentIndex);
  }

  public int getCurrentIndex () {
    return currentIndex;
  }

  private int findCurrentIndex () {
    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition == -1)
      return -1;
    if (repeatItems) {
      return (firstVisiblePosition + visibleItemCount / 2) % items.size();
    } else {
      View view = manager.findViewByPosition(firstVisiblePosition);
      int viewTop = view != null ? manager.getDecoratedTop(view) : 0;
      if (firstVisiblePosition > 0) {
        firstVisiblePosition += visibleItemCount / 2;
      }
      firstVisiblePosition += Math.round(-viewTop / (float) getItemHeight());
      int resultIndex = Math.max(0, Math.min(items.size() - 1, firstVisiblePosition));
      return minMaxProvider != null ? minMaxProvider.getMinMax(this, resultIndex) : resultIndex;
    }
  }

  public void checkFitsMinMax () {
    if (minMaxProvider != null) {
      int newIndex = minMaxProvider.getMinMax(this, currentIndex);
      if (this.currentIndex != newIndex) {
        selectIndex(newIndex, true);
      }
    }
  }

  private void selectIndex (int targetIndex, boolean isFinalized) {
    setCurrentIndex(targetIndex);
    if (targetIndex == -1)
      return;
    int targetScrollTop;
    int viewTop;
    if (targetIndex > 0) {
      viewTop = getItemHeight() * visibleItemCount / 2 - getItemHeight() / 2;
      targetScrollTop = getItemHeight() * targetIndex;
    } else {
      targetScrollTop = viewTop = 0;
    }

    stopScroll();

    int firstVisiblePosition = manager.findFirstVisibleItemPosition();
    if (firstVisiblePosition != -1) {
      int currentScrollTop = getItemHeight() * firstVisiblePosition;
      if (firstVisiblePosition > 0) {
        currentScrollTop += getMeasuredHeight() / 2 - getItemHeight() / 2;
      }
      View topView = manager.findViewByPosition(firstVisiblePosition);
      currentScrollTop += topView != null ? -manager.getDecoratedTop(topView) : 0;
      if (currentScrollTop != targetScrollTop) {
        smoothScrollBy(0, targetScrollTop - currentScrollTop);
      }
    } else {
      manager.scrollToPositionWithOffset(targetIndex, viewTop);
    }

    if (itemChangeListener != null) {
      itemChangeListener.onCurrentIndexFinalized(this, targetIndex);
    }
  }

  public interface ItemChangeListener<T> {
    void onCurrentIndexChanged (InfiniteRecyclerView<T> v, int index);
    default void onCurrentIndexFinalized (InfiniteRecyclerView<T> v, int index) { }
  }

  private ItemChangeListener<T> itemChangeListener;

  public void setItemChangeListener (ItemChangeListener<T> itemChangeListener) {
    this.itemChangeListener = itemChangeListener;
  }

  public void setCurrentItem (int index) {
    selectIndex(index, true);
  }

  private void setCurrentIndex (int index) {
    if (this.currentIndex != index) {
      this.currentIndex = index;
      if (itemChangeListener != null) {
        itemChangeListener.onCurrentIndexChanged(this, index);
      }
      // Log.i("currentIndex -> %d", index);
    }
  }

  // Item

  private static class Item<T> {
    private final int index;
    private final String text;
    private final TextPaint textPaint;
    private final int padding;
    private final int measuredWidth;
    private final int visibleItemCount;

    private final boolean needTrim;
    private int lastLayoutWidth;
    private String trimmedText;
    private int trimmedTextWidth;

    public Item (int index, T item, TextPaint paint, int padding, int visibleItemCount, boolean needTrim) {
      this.index = index;
      this.text = item.toString();
      this.needTrim = needTrim;
      if (text != null) {
        this.textPaint = paint;
        this.measuredWidth = (int) U.measureText(text, paint);
      } else {
        this.textPaint = null;
        this.measuredWidth = 0;
      }
      this.padding = padding;
      this.visibleItemCount = visibleItemCount;
    }

    public void layout (int width) {
      if (lastLayoutWidth != width && width != 0 && text != null) {
        lastLayoutWidth = width;
        int availableWidth = width - padding;
        if (measuredWidth <= availableWidth || !needTrim) {
          trimmedText = text;
          trimmedTextWidth = measuredWidth;
        } else {
          trimmedText = TextUtils.ellipsize(text, textPaint, availableWidth, TextUtils.TruncateAt.END).toString();
          trimmedTextWidth = (int) U.measureText(trimmedText, textPaint);
        }
      }
    }

    public void draw (Canvas c, int viewWidth, int viewHeight, int viewTop, int startX, int startY) {
      if (text != null) {
        float factor; // 0 - current, -1.0f - fully hidden (top), +1.0f - fully hidden (bottom)
        int itemHeight = getItemHeight();
        int parentHeight = visibleItemCount * itemHeight;
        int targetY = parentHeight / 2;
        int halfHeight = viewHeight / 2;
        int viewCy = viewTop + halfHeight;
        // boolean goingUp;
        final int checkHeight = 0; //  halfHeight; // (int) ((float) halfHeight * .5f)

        if (viewCy >= targetY - checkHeight && viewCy <= targetY + checkHeight) {
          factor = 0f;
          // goingUp = false;
        } else if (viewCy < targetY) {
          factor = Math.min((float) (targetY - checkHeight - viewCy) / ((visibleItemCount / 2) * itemHeight + halfHeight), 1f);
          // goingUp = true;
        } else  {
          factor = Math.min((float) (viewCy - targetY - checkHeight) / ((visibleItemCount / 2) * itemHeight + halfHeight), 1f);
          // goingUp = false;
        }

        if (factor == 0f) {
          c.drawText(trimmedText, startX + viewWidth / 2 - trimmedTextWidth / 2, startY + halfHeight + Screen.dp(8f), textPaint);
        } else if (factor < 1f) {
          c.save();
          float scaleX = 1f; // .82f + .18f * (1f - factor);
          float scaleY = .45f + .55f * (1f - factor);
          c.scale(scaleX, scaleY, startX + viewWidth / 2, startY + halfHeight);
          textPaint.setAlpha((int) (255f * (1f - factor)));
          c.drawText(trimmedText, startX + viewWidth / 2 - trimmedTextWidth / 2, startY + halfHeight + Screen.dp(8f), textPaint);
          textPaint.setAlpha(255);
          c.restore();
        }
      }
    }
  }

  // View

  private static class ItemView extends View {
    private @Nullable Item<?> item;

    public ItemView (Context context) {
      super(context);
      Views.setClickable(this);
      setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setItem (@NonNull Item<?> item) {
      this.item = item;
    }

    public @Nullable Item<?> getItem () {
      return item;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getItemHeight(), MeasureSpec.EXACTLY));
      if (item != null) {
        item.layout(getMeasuredWidth());
      }
    }

    private int lastMeasuredWidth, lastMeasuredHeight, lastTop;

    public void invalidateIfNeeded () {
      if (lastMeasuredWidth != getMeasuredWidth() || lastMeasuredHeight != getMeasuredHeight() || lastTop != getTop()) {
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (item != null) {
        item.draw(c, lastMeasuredWidth = getMeasuredWidth(), lastMeasuredHeight = getMeasuredHeight(), lastTop = getTop(), 0, 0);
      }
    }
  }

  // Holder

  private static class ItemViewHolder extends RecyclerView.ViewHolder {
    public ItemViewHolder (View itemView) {
      super(itemView);
    }

    public void setItem (@NonNull Item<?> item) {
      ((ItemView) itemView).setItem(item);
    }

    public static ItemViewHolder create (Context context, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
      final ItemView itemView;
      itemView = new ItemView(context);
      itemView.setOnClickListener(onClickListener);
      if (themeProvider != null)
        themeProvider.addThemeInvalidateListener(itemView);
      return new ItemViewHolder(itemView);
    }
  }

  // Adapter

  private static class InfiniteAdapter<T> extends RecyclerView.Adapter<ItemViewHolder> {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private final @Nullable ViewController<?> themeProvider;
    private final ArrayList<Item<T>> items;
    private final boolean repeatItems;

    private final TextPaint textPaint;
    private final int padding;
    private final boolean trimItems;

    public InfiniteAdapter (Context context, View.OnClickListener onClickListener, boolean repeatItems, boolean trimItems, int visibleItemCount, List<T> data, TextPaint textPaint, int padding, @Nullable ViewController<?> themeProvider) {
      this.context = context;
      this.onClickListener = onClickListener;
      this.repeatItems = repeatItems;
      this.themeProvider = themeProvider;

      this.textPaint = textPaint;
      this.padding = padding;
      this.trimItems = trimItems;

      int index = 0;
      this.items = new ArrayList<>();
      this.items.ensureCapacity(data.size());
      for (T item : data) {
        items.add(new Item<>(index, item, textPaint, padding, visibleItemCount, trimItems));
        index++;
      }
    }

    @Override
    public void onBindViewHolder (ItemViewHolder holder, int position) {
      holder.setItem(items.get(position % items.size()));
    }

    @Override
    public ItemViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
      return ItemViewHolder.create(context, onClickListener, themeProvider);
    }

    @Override
    public int getItemCount () {
      return repeatItems ? items.size() * 2 : items.size();
    }

    public void addRange (int index, List<T> addedItems, int visibleItemCount) {
      int prevSize = items.size();
      for (int i = 0; i < addedItems.size(); i++) {
        Item<T> item = new Item<>(index + i, addedItems.get(i), textPaint, padding, visibleItemCount, trimItems);
        items.add(index + i, item);
      }
      if (repeatItems) {
        notifyItemRangeInserted(prevSize + index, addedItems.size());
      }
      notifyItemRangeInserted(index, addedItems.size());
    }

    public void removeRange (int index, int removedCount) {
      int oldItemCount = items.size();
      for (int i = index + removedCount - 1; i >= index; i--) {
        items.remove(i);
      }
      if (repeatItems) {
        notifyItemRangeRemoved(oldItemCount + index, removedCount);
      }
      notifyItemRangeRemoved(index, removedCount);
    }
  }
}
