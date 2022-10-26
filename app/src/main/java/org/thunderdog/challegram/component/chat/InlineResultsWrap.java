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
 * File created on 04/12/2016
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultButton;
import org.thunderdog.challegram.data.InlineResultCommand;
import org.thunderdog.challegram.data.InlineResultEmojiSuggestion;
import org.thunderdog.challegram.data.InlineResultGif;
import org.thunderdog.challegram.data.InlineResultHashtag;
import org.thunderdog.challegram.data.InlineResultMention;
import org.thunderdog.challegram.data.InlineResultPhoto;
import org.thunderdog.challegram.data.InlineResultSticker;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGBotStart;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SimpleMediaViewController;
import org.thunderdog.challegram.util.CancellableResultHandler;
import org.thunderdog.challegram.v.NewFlowLayoutManager;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;

public class InlineResultsWrap extends FrameLayoutFix implements View.OnClickListener, StickerSmallView.StickerMovementCallback, InlineResultsAdapter.HeightProvider, FactorAnimator.Target, View.OnLongClickListener, TGLegacyManager.EmojiLoadListener, BaseView.CustomControllerProvider {
  private RecyclerView recyclerView;
  private ShadowView shadowView;
  private GridLayoutManager gridManager;
  private NewFlowLayoutManager flowManager;
  private final InlineResultsAdapter adapter;
  private int layoutMode;
  private float backgroundFactor;

  private static final int LAYOUT_MODE_LINEAR = 0;
  private static final int LAYOUT_MODE_FLOW = 1;
  private static final int LAYOUT_MODE_GRID = 2;

  public interface OffsetProvider {
    int provideOffset (InlineResultsWrap v);
    int provideParentHeight (InlineResultsWrap v);
  }

  private OffsetProvider offsetProvider;

  private void setLayoutManagerMode (int layoutMode) {
    int oldLayoutMode = this.layoutMode;
    if (layoutMode != oldLayoutMode) {
      this.layoutMode = layoutMode;
      recyclerView.setLayoutManager(layoutMode == LAYOUT_MODE_GRID ? gridManager : flowManager);
      recyclerView.invalidateItemDecorations();
    }
  }

  public ThemeListenerList getThemeProvider () {
    return themeProvider;
  }

  public void setOffsetProvider (OffsetProvider offsetProvider) {
    this.offsetProvider = offsetProvider;
  }

  private View lickView;
  private final ThemeListenerList themeProvider = new ThemeListenerList();

  public InlineResultsWrap (Context context) {
    super(context);

    gridManager = new GridLayoutManager(context, spanCount = calculateSpanCount(Screen.currentWidth(), Screen.currentHeight()));
    gridManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return position == 0 || currentItems == null || !InlineResult.isGridType(currentItems.get(position - 1).getType()) ? spanCount : 1;
      }
    });

    flowManager = new NewFlowLayoutManager(context, 100) {
      private final Size size = new Size();

      @Override
      protected Size getSizeForItem (int i) {
        InlineResult<?> result = layoutMode == LAYOUT_MODE_FLOW && i != 0 && currentItems != null ? currentItems.get(i - 1) : null;
        if (result != null && InlineResult.isFlowType(result.getType())) {
          size.width = result.getCellWidth();
          size.height = result.getCellHeight();
        } else {
          size.width = size.height = 100;
        }
        return size;
      }
    };
    flowManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return layoutMode == LAYOUT_MODE_FLOW && position > 0 && currentItems != null && InlineResult.isFlowType(currentItems.get(position - 1).getType()) ? flowManager.getSpanSizeForItem(position) : 100;
        // return layoutMode == LAYOUT_MODE_FLOW && position > 0 && currentItems != null && InlineResult.isFlowType(currentItems.get(position - 1).getType()) ? flowManager.getSpanSizeForItem(position) : 100;
      }
    });
    adapter = new InlineResultsAdapter(context, this, themeProvider);

    recyclerView = new RecyclerView(context) {
      @Override
      protected void onMeasure (int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        startAnimationIfNeeded();
      }

      private boolean ignoreTouch;

      @Override
      public boolean onTouchEvent (MotionEvent e) {
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            int i = flowManager.findFirstVisibleItemPosition();
            ignoreTouch = false;
            if (i == 0) {
              int top = provideHeight();
              View view = flowManager.findViewByPosition(0);
              if (view != null) {
                top += view.getTop();
              }
              if (e.getY() < top) {
                updatePosition(true);
                ignoreTouch = true;
                break;
              }
            }
          }
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL: {
            if (ignoreTouch) {
              ignoreTouch = false;
              return false;
            }
            break;
          }
        }
        if (backgroundFactor == 0f) {
          return !ignoreTouch && super.onTouchEvent(e);
        } else {
          return ignoreTouch || super.onTouchEvent(e);
        }
      }

      @Override
      public void draw (Canvas c) {
        int top = detectRecyclerTopEdge();
        int width = getMeasuredWidth();
        c.drawRect(0, top, width, getMeasuredHeight(), Paints.fillingPaint(adapter.useDarkMode() ? Theme.getColor(R.id.theme_color_filling, ThemeId.NIGHT_BLACK) : Theme.fillingColor()));

        super.draw(c);
      }
    };
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        if (layoutMode != LAYOUT_MODE_FLOW) {
          outRect.bottom = outRect.left = outRect.right = outRect.top = 0;
          return;
        }
        int i = parent.getChildAdapterPosition(view);
        outRect.right = i == -1 || flowManager.isLastInRow(i) ? 0 : Screen.dp(3f);
        outRect.bottom = i == 0 && currentItems != null && !currentItems.isEmpty() && currentItems.get(0).getType() == InlineResult.TYPE_BUTTON ? 0 : Screen.dp(3f);
      }
    });
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        checkLoadMore();
        if (lickView != null) {
          lickView.invalidate();
        }
      }
    });
    recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
    recyclerView.setItemAnimator(null);
    recyclerView.setLayoutManager(flowManager);
    recyclerView.setAdapter(adapter);
    recyclerView.setAlpha(0f);
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(recyclerView);
    themeProvider.addThemeInvalidateListener(recyclerView);

    shadowView = new ShadowView(context);
    shadowView.setAlpha(0f);
    shadowView.setSimpleTopShadow(true);
    shadowView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, shadowView.getLayoutParams().height, Gravity.BOTTOM));
    addView(shadowView);
    themeProvider.addThemeInvalidateListener(shadowView);

    TGLegacyManager.instance().addEmojiListener(this);
  }

  public boolean areItemsVisible () {
    return itemsVisible;
  }

  public void setUseDarkMode (boolean useDarkMode) {
    adapter.setUseDarkMode(useDarkMode);
  }

  public void addLick (ViewGroup parent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      lickView = new View(getContext()) {
        @Override
        protected void onDraw (Canvas c) {
          int height = getMeasuredHeight();
          float top = detectRecyclerTopEdge() - lastBottomMargin;
          if (top < height) {
            c.drawRect(0, top, getMeasuredWidth(), height, Paints.fillingPaint(ColorUtils.alphaColor(visibleFactor, Theme.getColor(Config.STATUS_BAR_COLOR_ID))));
          }
        }
      };
      lickView.setBackgroundColor(0xffff0000);
      lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset(), Gravity.TOP));
      parent.addView(lickView);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    final InlineResult<?> result = (InlineResult<?>) v.getTag();
    final ViewController<?> c = UI.getContext(getContext()).navigation().getCurrentStackItem();
    if (result != null && c != null) {
      if (result instanceof InlineResultCommand) {
        return c instanceof MessagesController && ((MessagesController) c).canWriteMessages() && ((MessagesController) c).onCommandLongPressed((InlineResultCommand) result);
      } else if (result instanceof InlineResultHashtag) {
        c.showOptions(Lang.getString(R.string.HashtagDeleteHint), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getOK(), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_delete) {
            removeItem(result);
            delegate.tdlib().client().send(new TdApi.RemoveRecentHashtag(((InlineResultHashtag) result).data().substring(1)), delegate.tdlib().okHandler());
          }
          return true;
        });
        return true;
      } else if (result instanceof InlineResultMention && ((InlineResultMention) result).isInlineBot()) {
        c.showOptions(Lang.getString(R.string.BotDeleteHint), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getOK(), Lang.getString(R.string.Cancel)}, new int[] {ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_delete) {
            removeItem(result);
            if (c instanceof MessagesController) {
              ((MessagesController) c).removeInlineBot(((InlineResultMention) result).getUserId());
            }
            delegate.tdlib().client().send(new TdApi.RemoveTopChat(new TdApi.TopChatCategoryInlineBots(), ChatId.fromUserId(((InlineResultMention) result).getUserId())), delegate.tdlib().okHandler());
          }
          return true;
        });
        return true;
      } else if (result instanceof InlineResultMention) {
        if (findListener() == null)
          return false;
        String mentionName = ((InlineResultMention) result).getMention(true);
        c.openInputAlert(Lang.getString(R.string.Mention), Lang.getString(R.string.MentionPlaceholder), R.string.MentionAdd, R.string.Cancel, mentionName, (inputView, resultText) -> {
          if (!StringUtils.isEmpty(resultText)) {
            PickListener listener = findListener();
            if (listener != null) {
              listener.onMentionPick((InlineResultMention) result, resultText);
            }
            return true;
          }
          return false;
        }, false);
        return true;
      }
    }
    return false;
  }

  private PickListener findListener () {
    if (this.listener != null)
      return this.listener;
    ViewController<?> c = UI.getCurrentStackItem(getContext());
    return c instanceof MessagesController ? ((MessagesController) c).getInlineResultListener() : null;
  }

  @Override
  public boolean needsForceTouch (BaseView v, float x, float y) {
    Object tag = v.getTag();
    if (!(tag instanceof InlineResult)) {
      return false;
    }
    InlineResult<?> result = (InlineResult<?>) tag;
    switch (result.getType()) {
      case InlineResult.TYPE_PHOTO:
      case InlineResult.TYPE_GIF:
        return true;
    }
    return false;
  }

  @Override
  public boolean onSlideOff (BaseView v, float x, float y, @Nullable ViewController<?> openPreview) {
    return false;
  }

  @Override
  public ViewController<?> createForceTouchPreview (BaseView v, float x, float y) {
    Object tag = v.getTag();
    if (!(tag instanceof InlineResult<?>)) {
      return null;
    }
    InlineResult<?> result = (InlineResult<?>) tag;
    SimpleMediaViewController.Args args = null;
    switch (result.getType()) {
      case InlineResult.TYPE_PHOTO: {
        InlineResultPhoto photo = (InlineResultPhoto) result;
        args = new SimpleMediaViewController.Args(photo.data().photo, photo.getPreview() != null ? photo.getPreview() : photo.getMiniThumbnail(), photo.getImage());
        break;
      }
      case InlineResult.TYPE_GIF: {
        InlineResultGif gif = (InlineResultGif) result;
        args = new SimpleMediaViewController.Args(gif.data().animation, gif.getGif().getImage());
        break;
      }
    }
    if (args != null) {
      SimpleMediaViewController m = new SimpleMediaViewController(getContext(), result.tdlib());
      m.setArguments(args);
      return m;
    }
    return null;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return getVisibility() != View.VISIBLE || getAlpha() == 0f || super.onInterceptTouchEvent(ev);
  }

  private int detectRecyclerTopEdge () {
    int top;
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    int i = manager.findFirstVisibleItemPosition();
    if (i == 0) {
      top = 0;
      View view = manager.findViewByPosition(0);
      if (view != null) {
        top = view.getMeasuredHeight();
        top += view.getTop();
      }
    } else {
      top = 0;
    }
    return top;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    Views.invalidateChildren(recyclerView);
  }

  private void checkLoadMore () {
    if (visibleFactor == 1f) {
      int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
      if (lastVisiblePosition + 4 >= adapter.getItemCount()) {
        requestLoadMore();
      }
    }
  }

  private void requestLoadMore () {
    if (callback != null) {
      callback.onLoadMoreRequested();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    final int width = getMeasuredWidth();
    c.drawRect(0, 0, width, getMeasuredHeight(), Paints.fillingPaint(getBackgroundColor()));
  }

  public interface LoadMoreCallback {
    void onLoadMoreRequested ();
  }

  private LoadMoreCallback callback;
  private TdlibDelegate delegate;

  public TdlibDelegate getTdlibDelegate () {
    return delegate;
  }

  public void showItems (@NonNull TdlibDelegate delegate, @Nullable ArrayList<InlineResult<?>> items, boolean needBackground, @Nullable LoadMoreCallback callback, boolean areHidden) {
    this.delegate = delegate;
    this.adapter.setTdlib(delegate.tdlib());
    if (items != null && !items.isEmpty()) {
      setBackgroundFactor(needBackground ? 1f : 0f, this.visibleFactor != 0f);
      setItems(items);
      this.callback = callback;
    } else {
      this.callback = null;
    }
    setItemsVisible(items != null && !items.isEmpty());
    setHidden(areHidden);
  }

  public void addItems (TdlibDelegate delegate, @Nullable ArrayList<InlineResult<?>> items, @Nullable LoadMoreCallback callback) {
    if (items != null && !items.isEmpty()) {
      addItems(delegate, items);
    }
  }

  private boolean itemsVisible;
  private int translateY;
  private boolean animationNeeded;
  private float showFactor;

  private float getVisibleFactor () {
    return showFactor * (1f - hideFactor);
  }

  private void startAnimationIfNeeded () {
    if (animationNeeded) {
      animationNeeded = false;
      animateFactor(itemsVisible ? 1f : 0f, true);
    }
  }

  private static final int ANIMATOR_VISIBLE = 0;
  private FactorAnimator visibleAnimator;

  private void updateRecyclerY () {
    float range = MathUtils.clamp(visibleFactor);
    recyclerView.setTranslationY((float) translateY * (1f - visibleFactor));
    shadowView.setAlpha(range);
    int desiredVisibility = range == 0f ? View.INVISIBLE : View.VISIBLE;
    if (recyclerView.getVisibility() != desiredVisibility) {
      recyclerView.setVisibility(desiredVisibility);
    }
  }

  private void updateBackground () {
    float factor = backgroundFactor * visibleFactor;
    setWillNotDraw(factor == 0f);
    if (lickView != null) {
      lickView.invalidate();
    }
    invalidate();
  }

  private void calculateTranslateY () {
    translateY = Math.min(recyclerView.getMeasuredHeight(), measureItemsHeight() + Screen.dp(7f));
  }

  private void animateFactor (float factor, boolean byLayout) {
    calculateTranslateY();
    updateRecyclerY();
    recyclerView.setAlpha(1f);

    if (visibleAnimator == null) {
      visibleAnimator = new FactorAnimator(ANIMATOR_VISIBLE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 190l, visibleFactor);
    }
    visibleAnimator.animateTo(factor);
  }

  private float hideFactor;
  private float visibleFactor;

  private static final int ANIMATOR_HIDE = 3;

  private BoolAnimator hideAnimator;

  public void setHidden (boolean isHidden) {
    if (hideAnimator == null) {
      if (!isHidden) {
        return;
      }
      hideAnimator = new BoolAnimator(ANIMATOR_HIDE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    calculateTranslateY();
    hideAnimator.setValue(isHidden, showFactor > 0f);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBLE: {
        showFactor = factor;
        visibleFactor = getVisibleFactor();
        updateRecyclerY();
        updateBackground();
        break;
      }
      case ANIMATOR_HIDE: {
        hideFactor = factor;
        visibleFactor = getVisibleFactor();
        updateRecyclerY();
        updateBackground();
        break;
      }
      case ANIMATOR_BACKGROUND: {
        setBackgroundFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBLE: {
        if (finalFactor == 0f) {
          setItems(null);
        }
        break;
      }
    }
  }

  private void setItemsVisible (boolean visible) {
    if (this.itemsVisible != visible) {
      this.itemsVisible = visible;
      if (visible) {
        updatePosition(true);
      }
      if (recyclerView.getMeasuredHeight() == 0) {
        animationNeeded = true;
      } else {
        animateFactor(visible ? 1f : 0f, false);
      }
    }
  }

  private void setBackgroundFactor (float factor) {
    if (this.backgroundFactor != factor) {
      this.backgroundFactor = factor;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        UI.setStatusBarColor(ColorUtils.compositeColor(HeaderView.defaultStatusColor(), getBackgroundColor()));
      }
      if (lickView != null) {
        lickView.invalidate();
      }
      updateBackground();
    }
  }

  private static final int ANIMATOR_BACKGROUND = 1;
  private FactorAnimator backgroundAnimator;

  private void setBackgroundFactor (float factor, boolean animated) {
    if (animated && false) { // not
      if (backgroundAnimator == null) {
        if (this.backgroundFactor == factor) {
          return;
        }
        this.backgroundAnimator = new FactorAnimator(ANIMATOR_BACKGROUND, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l, backgroundFactor);
      }
      backgroundAnimator.animateTo(factor);
    } else {
      if (backgroundAnimator != null) {
        backgroundAnimator.forceFactor(factor);
      }
      setBackgroundFactor(factor);
    }
  }

  private int getBackgroundColor () {
    return backgroundFactor == 0f || visibleFactor == 0f ? 0 : ColorUtils.color((int) ((float) 0x99 * backgroundFactor * visibleFactor), 0);
  }

  private int spanCount;
  private int lastSpanCountWidth, lastSpanCountHeight;

  private static int calculateSpanCount (int width, int height) {
    int minSide = Math.min(width, height);
    int minWidth = minSide / 4;
    return minWidth != 0 ? width / minWidth : 5;
  }

  private void setSpanCount (int width, int height) {
    if (width == 0 || height == 0) {
      return;
    }
    if (lastSpanCountWidth != width || lastSpanCountHeight != height) {
      lastSpanCountWidth = width;
      lastSpanCountHeight = height;
      int newSpanCount = calculateSpanCount(width, height);
      if (newSpanCount != spanCount) {
        this.spanCount = newSpanCount;
        gridManager.setSpanCount(newSpanCount);
      }
    }
  }

  private ArrayList<InlineResult<?>> currentItems;

  private void removeItem (InlineResult<?> result) {
    if (currentItems == null) {
      return;
    }
    int i = currentItems.indexOf(result);
    if (i == -1) {
      return;
    }
    if (currentItems.size() == 1) {
      setItemsVisible(false);
    } else {
      currentItems.remove(i);
      adapter.removeItemAt(i);
    }
    updateOffset();
    recyclerView.invalidateItemDecorations();
  }

  private void setItems (ArrayList<InlineResult<?>> items) {
    int lastType = items != null && !items.isEmpty() ? items.get(items.size() - 1).getType() : -1;

    if (InlineResult.isFlowType(lastType)) {
      int headerItemCount = 1;
      if (items != null) {
        for (InlineResult<?> item : items) {
          if (InlineResult.isFlowType(item.getType())) {
            break;
          }
          headerItemCount++;
        }
      }
      flowManager.setHeaderItemCount(headerItemCount);
    }

    setLayoutManagerMode(lastType == -1 ? LAYOUT_MODE_LINEAR : InlineResult.isGridType(lastType) ? LAYOUT_MODE_GRID : InlineResult.isFlowType(lastType) ? LAYOUT_MODE_FLOW : LAYOUT_MODE_LINEAR);
    this.currentItems = items;
    this.adapter.setItems(items);
    this.recyclerView.invalidateItemDecorations();
    ((LinearLayoutManager) this.recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);

    if ((items == null || items.isEmpty()) && getParent() != null) {
      if (movedToAnotherViewGroup != null) {
        movedToAnotherViewGroup.removeView(recyclerView);
        movedToAnotherViewGroup.removeView(shadowView);
      } else {
        ((ViewGroup) getParent()).removeView(this);
      }
    }
  }

  public boolean isDisplayingItems () {
    return itemsVisible;
  }

  private ViewGroup movedToAnotherViewGroup;

  public void moveChildren (ViewGroup anotherGroup) {
    movedToAnotherViewGroup = anotherGroup != this ? anotherGroup : null;
    final ViewGroup targetParent = anotherGroup != null ? anotherGroup : this;
    final ViewGroup previousParent = ((ViewGroup) recyclerView.getParent());
    if (targetParent == previousParent) {
      return;
    }
    if (previousParent != null) {
      previousParent.removeView(recyclerView);
      previousParent.removeView(shadowView);
    }
    targetParent.addView(recyclerView);
    targetParent.addView(shadowView);
  }

  private void addItems (TdlibDelegate delegate, ArrayList<InlineResult<?>> items) {
    if (this.delegate == delegate && currentItems != null && items != null && !items.isEmpty()) {
      currentItems.addAll(items);
      adapter.addItems(items);
      this.recyclerView.invalidateItemDecorations();
    }
  }

  // Offset stuff

  private int measureItemsHeight () {
    if (currentItems == null || currentItems.isEmpty()) {
      return 0;
    }
    switch (layoutMode) {
      case LAYOUT_MODE_FLOW: {
        int headerHeight = 0;
        int headerItemCount = 0;
        for (InlineResult<?> item : currentItems) {
          if (InlineResult.isFlowType(item.getType())) {
            break;
          }
          headerHeight += item.getHeight();
          headerItemCount++;
        }

        return headerHeight + (flowManager.getRowsCount(Screen.currentWidth()) - headerItemCount) * Screen.dp(118f);
      }
      case LAYOUT_MODE_GRID: {
        int headerHeight = 0;
        int headerItemCount = 0;
        for (InlineResult<?> item : currentItems) {
          if (InlineResult.isGridType(item.getType())) {
            break;
          }
          headerHeight += item.getHeight();
          headerItemCount++;
        }

        return headerHeight + (int) Math.ceil((double) (currentItems.size() - headerItemCount) / (double) spanCount) * (Screen.currentWidth() / spanCount);
      }
      case LAYOUT_MODE_LINEAR: {
        int height = 0;
        for (InlineResult<?> item : currentItems) {
          height += item.getHeight();
        }
        return height;
      }
    }
    return 0;
  }

  private int lastHeight;

  private void updateOffset () {
    if (adapter.getItemCount() > 0) {
      adapter.notifyItemChanged(0);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (lastHeight != getMeasuredHeight()) {
      lastHeight = getMeasuredHeight();
      updatePosition(true);
      updateOffset();
    }
    setSpanCount(getMeasuredWidth(), getMeasuredHeight());
  }

  private int lastBottomMargin;

  private void setBottomMargin (int bottomMargin) {
    if (this.lastBottomMargin != bottomMargin) {
      this.lastBottomMargin = bottomMargin;
      /*FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) recyclerView.getLayoutParams();
      params.topMargin = bottomMargin;
      recyclerView.setLayoutParams(params);*/
      setTranslationY(-bottomMargin);
    }
    if (lickView != null) {
      lickView.invalidate();
    }
  }

  public void updatePosition (boolean needTranslate) {
    if (offsetProvider != null) {
      setBottomMargin(offsetProvider.provideOffset(this));
    } else {
      ViewController<?> c = UI.getCurrentStackItem(getContext());
      float tx = 0;
      if (c instanceof MessagesController) {
        setBottomMargin(((MessagesController) c).getInputOffset(false));
        tx -= ((MessagesController) c).getPagerScrollOffsetInPixels();
      } else {
        // setBottomMargin(0);
      }
      NavigationController navigation = UI.getContext(getContext()).navigation();
      if (needTranslate && navigation != null && navigation.isAnimating()) {
        float translate = navigation.getHorizontalTranslate();
        if (c instanceof MessagesController) {
          tx = translate;
        }
      }
      setTranslationX(tx);
    }
  }

  @Override
  public int provideHeight () {
    int height = offsetProvider != null ? offsetProvider.provideParentHeight(this) : ((BaseActivity) getContext()).getContentView().getMeasuredHeight(); //  - measureItemsHeight();
    height -= Math.min(measureItemsHeight(), Screen.smallestActualSide() / 2);
    return Math.max(0, height);
  }

  // Switch pm utils

  private CancellableResultHandler switchPmHandler;

  private void switchPm (final InlineResultButton button) {
    if (switchPmHandler != null) {
      switchPmHandler.cancel();
      switchPmHandler = null;
    }

    final ViewController<?> c = UI.getCurrentStackItem();
    long sourceChatId = 0;
    if (c instanceof MessagesController) {
      if (((MessagesController) c).comparePrivateUserId(button.getUserId())) {
        ((MessagesController) c).onSwitchPm(button);
        return;
      }
      sourceChatId = c.getChatId();
    }

    button.setSourceChatId(sourceChatId);

    switchPmHandler = new CancellableResultHandler() {
      @Override
      public void processResult (final TdApi.Object object) {
        final long chatId = TD.getChatId(object);
        if (chatId != 0) {
          UI.post(() -> {
            if (!isCancelled()) {
              setItems(null);
              delegate.tdlib().ui().openChat(c, chatId, new TdlibUi.ChatOpenParameters().keepStack().shareItem(new TGBotStart(delegate.tdlib().chatUserId(chatId), button.data(), false)));
            }
          });
        } else {
          switch (object.getConstructor()) {
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(object);
              break;
            }
          }
        }
      }
    };
    delegate.tdlib().client().send(new TdApi.CreatePrivateChat(button.getUserId(), false), switchPmHandler);
  }

  // Interfaces

  public interface PickListener {
    void onHashtagPick (InlineResultHashtag result);
    void onMentionPick (InlineResultMention result, @Nullable String usernamelessText);
    void onCommandPick (InlineResultCommand result, boolean isLongPress);
    void onEmojiSuggestionPick (InlineResultEmojiSuggestion result);
    void onInlineQueryResultPick (InlineResult<?> result);
  }

  private PickListener listener;

  public void setListener (PickListener listener) {
    this.listener = listener;
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_switchPmButton: {
        switchPm((InlineResultButton) v.getTag());
        break;
      }
      case R.id.result: {
        Object tag = v.getTag();
        if (tag != null && tag instanceof InlineResult) {
          InlineResult<?> result = (InlineResult<?>) tag;
          PickListener listener = findListener();
          if (listener == null) {
            return;
          }
          switch (result.getType()) {
            case InlineResult.TYPE_HASHTAG: {
              InlineResultHashtag hashtag = (InlineResultHashtag) result;
              listener.onHashtagPick(hashtag);
              break;
            }
            case InlineResult.TYPE_EMOJI_SUGGESTION: {
              InlineResultEmojiSuggestion suggestion = (InlineResultEmojiSuggestion) result;
              listener.onEmojiSuggestionPick(suggestion);
              break;
            }
            case InlineResult.TYPE_MENTION: {
              InlineResultMention mention = (InlineResultMention) result;
              listener.onMentionPick(mention, mention.isUsernameless() ? mention.getMention(true) : null);
              break;
            }
            case InlineResult.TYPE_COMMAND: {
              listener.onCommandPick((InlineResultCommand) result, false);
              break;
            }
            default: {
              listener.onInlineQueryResultPick(result);
              break;
            }
          }
        }
        break;
      }
    }
  }

  private MessagesController findMessagesController () {
    ViewController<?> c = UI.getCurrentStackItem(getContext());
    return c instanceof MessagesController ? (MessagesController) c : null;
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    Object tag = view.getTag();
    if (tag instanceof InlineResult) {
      InlineResult<?> result = (InlineResult<?>) tag;
      MessagesController c = findMessagesController();
      if (c != null) {
        c.sendInlineQueryResult(result.getQueryId(), result.getId(), true, true, sendOptions);
      }
    }
    return false;
  }

  @Override
  public long getStickerOutputChatId () {
    MessagesController c = findMessagesController();
    return c != null ? c.getChatId() : 0;
  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return true;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {
    if (currentItems != null) {
      int i = 0;
      for (InlineResult<?> item : currentItems) {
        if (item.getType() == InlineResult.TYPE_STICKER && ((InlineResultSticker) item).getSticker().equals(sticker)) {
          final View childView = gridManager.findViewByPosition(i + 1);
          if (childView instanceof StickerSmallView) {
            ((StickerSmallView) childView).setStickerPressed(isPressed);
          } else {
            adapter.notifyItemChanged(i);
          }
          break;
        }
        i++;
      }
    }
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return true;
  }

  @Override
  public void onStickerPreviewOpened (StickerSmallView view, TGStickerObj sticker) {

  }

  @Override
  public void onStickerPreviewClosed (StickerSmallView view, TGStickerObj thisSticker) {

  }

  @Override
  public void onStickerPreviewChanged (StickerSmallView view, TGStickerObj otherOrThisSticker) {

  }

  @Override
  public int getStickersListTop () {
    return (int) getTranslationY();
  }

  @Override
  public int getViewportHeight () {
    MessagesController c = findMessagesController();
    if (c != null) {
      return c.getStickerSuggestionPreviewViewportHeight();
    } else {
      return getMeasuredHeight();
    }
  }
}
