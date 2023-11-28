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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.core.util.ObjectsCompat;
import androidx.viewpager.widget.PagerAdapter;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.ViewPager;
import org.thunderdog.challegram.widget.rtl.RtlViewPager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.collection.LongSparseIntArray;

public abstract class ViewPagerController<T> extends TelegramViewController<T> implements ViewPager.OnPageChangeListener, ViewPagerTopView.OnItemClickListener,
  OptionDelegate, SelectDelegate, Menu, MoreDelegate {
  public ViewPagerController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  protected static final int NO_POSITION = -1;

  public interface ScrollToTopDelegate {
    void onScrollToTopRequested ();
  }

  private RtlViewPager pager;
  private ViewPagerAdapter adapter;
  protected @Nullable PagerHeaderView headerCell;

  @Override
  protected int getTransformHeaderHeight () {
    return getHeaderHeight();
  }

  protected int getMenuButtonsWidth () {
    return 0; // override for performance
  }

  /*@Override
  public boolean onBackPressed (boolean fromTop) {
    int currentPosition = getCurrentPagerItemPosition();
    ViewController<?> currentController = getCachedControllerForPosition(currentPosition);
    if (currentController != null && currentController.onBackPressed(fromTop)) {
      return true;
    }
    SparseArrayCompat<ViewController<?>> controllers = getAllCachedControllers();
    if (controllers != null) {
      for (int i = 0; i < controllers.size(); i++) {
        int position = controllers.keyAt(i);
        if (position != currentPosition) {
          ViewController<?> controller = controllers.valueAt(position);
          if (controller != null && controller.onBackPressed(fromTop)) {
            return true;
          }
        }
      }
    }
    return super.onBackPressed(fromTop);
  }*/

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (pager != null) {
      pager.checkRtl();
    }
    if (getTitleStyle() == TITLE_STYLE_COMPACT_BIG && headerCell != null) {
      TextView textView = headerCell.getView().findViewById(R.id.text_title);
      if (Views.setGravity(textView, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT))) {
        FrameLayout.LayoutParams params = ((FrameLayout.LayoutParams) textView.getLayoutParams());
        if (Lang.rtl()) {
          params.rightMargin = Screen.dp(68f);
          params.leftMargin = 0;
        } else {
          params.leftMargin = Screen.dp(68f);
          params.rightMargin = 0;
        }
        Views.updateLayoutParams(textView);
      }
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    switch (event) {
      case Lang.EVENT_PACK_CHANGED:
        updateHeader();
        break;
      case Lang.EVENT_STRING_CHANGED:
        // TODO update specific strings?
        updateHeader();
        break;
      case Lang.EVENT_DIRECTION_CHANGED:
      case Lang.EVENT_DATE_FORMAT_CHANGED:
        // nothing to update
        break;
    }
  }

  private void updateHeader () {
    if (headerCell != null) {
      List<ViewPagerTopView.Item> sections = getPagerSectionItems();
      if (sections != null) {
        if (sections.size() != getPagerItemCount()) {
          throw new IllegalArgumentException("sections.size() != " + getPagerItemCount());
        }
        headerCell.getTopView().setItems(sections);
      }
    }
  }

  @ColorId
  protected int getDrawerReplacementColorId () {
    return ColorId.filling;
  }

  @Override
  protected View onCreateView (Context context) {
    FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      protected void onDraw (Canvas c) {
        c.drawRect(0, 0, getMeasuredWidth(), Size.getHeaderDrawerSize(), Paints.fillingPaint(Theme.getColor(getDrawerReplacementColorId())));
      }
    };
    contentView.setWillNotDraw(false);

    List<ViewPagerTopView.Item> sections = getPagerSectionItems();
    if (sections != null) {
      if (sections.size() != getPagerItemCount()) {
        throw new IllegalArgumentException("sections.size() != " + getPagerItemCount());
      }
      switch (getTitleStyle()) {
        case TITLE_STYLE_BIG: {
          headerCell = new ViewPagerHeaderView(context);
          ((ViewPagerHeaderView) headerCell).initWithController(this);
          break;
        }
        case TITLE_STYLE_COMPACT:
        case TITLE_STYLE_COMPACT_BIG: {
          headerCell = new ViewPagerHeaderViewCompact(context);
          FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) ((ViewPagerHeaderViewCompact) headerCell).getRecyclerView().getLayoutParams();
          if (getBackButton() != BackHeaderButton.TYPE_NONE && getMenuButtonsWidth() != 0) {
            if (Lang.rtl()) {
              params.rightMargin = Screen.dp(56f);
              params.leftMargin = getMenuButtonsWidth();
            } else {
              params.leftMargin = Screen.dp(56f);
              params.rightMargin = getMenuButtonsWidth();
            }
          }
          if (useCenteredTitle()) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER_HORIZONTAL;
          }
          if (getTitleStyle() == TITLE_STYLE_COMPACT_BIG) {
            headerCell.getTopView().setItemPadding(Screen.dp(12f));
            TextView title = SimpleHeaderView.newTitle(context);
            title.setTextColor(Theme.headerTextColor());
            addThemeTextColorListener(title, ColorId.headerText);
            title.setId(R.id.text_title);
            Views.setMediumText(title, getName());
            ((ViewPagerHeaderViewCompact) headerCell).addView(title);
          }
          break;
        }
      }
      headerCell.getTopView().setOnItemClickListener(this);
      headerCell.getTopView().setItems(sections);
      addThemeInvalidateListener(headerCell.getTopView());
    }

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    switch (getTitleStyle()) {
      case TITLE_STYLE_BIG:
      case TITLE_STYLE_COMPACT_BIG:
        params.topMargin = Size.getHeaderDrawerSize();
        break;
      case TITLE_STYLE_COMPACT:
        params.topMargin = 0;
        break;
    }

    adapter = new ViewPagerAdapter(context, this);
    addAttachStateListener(attachListener);
    pager = new RtlViewPager(context);
    pager.setLayoutParams(params);
    pager.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    pager.addOnPageChangeListener(new androidx.viewpager.widget.ViewPager.OnPageChangeListener() {
      @Override
      public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
        currentPosition = position;
        currentPositionOffset = positionOffset;
        context().checkDisallowScreenshots();
      }

      @Override
      public void onPageSelected (int position) { }

      @Override
      public void onPageScrollStateChanged (int state) { }
    });
    pager.addOnPageChangeListener(this);
    pager.setAdapter(adapter);
    if (!overridePagerParent()) {
      contentView.addView(pager);
    }

    onCreateView(context, contentView, pager);

    int startPosition = adapter.reversePosition(0);
    if (startPosition != 0) {
      pager.setCurrentItem(startPosition);
    }

    return contentView;
  }

  private final ViewController.AttachListener attachListener = (context, navigation, isAttached) -> {
    for (ViewController<?> c : adapter.visibleControllers) {
      c.onAttachStateChanged(navigation, isAttached);
    }
  };

  protected boolean overridePagerParent () {
    return false;
  }

  @Override
  public View getViewForApplyingOffsets () {
    switch (getTitleStyle()) {
      case TITLE_STYLE_BIG:
      case TITLE_STYLE_COMPACT_BIG:
        return null;
      case TITLE_STYLE_COMPACT:
        return pager;
    }
    return null;
  }

  public final void changeName (CharSequence newName) {
    if (getTitleStyle() == TITLE_STYLE_COMPACT_BIG && headerCell != null) {
      TextView view = headerCell.getView().findViewById(R.id.text_title);
      if (view != null) {
        Views.setMediumText(view, newName);
      }
    }
  }

  public final ViewPager getViewPager () {
    return pager;
  }

  @Override
  protected void onEnterSelectMode () {
    super.onEnterSelectMode();
    getViewPager().setPagingEnabled(false);
    getCurrentPagerItem().onEnterSelectMode();
  }

  @Override
  public void onLeaveSelectMode () {
    super.onLeaveSelectMode();
    getViewPager().setPagingEnabled(true);
    getCurrentPagerItem().onLeaveSelectMode();
  }

  @Override
  protected int getSelectMenuId () {
    ViewController<?> c = getCurrentPagerItem();
    return c != null ? c.getSelectMenuId() : super.getSelectMenuId();
  }

  @Override
  public void finishSelectMode (int position) {
    ViewController<?> c = getCurrentPagerItem();
    if (c instanceof SelectDelegate) {
      ((SelectDelegate) c).finishSelectMode(position);
    }
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    ViewController<?> c = getCurrentPagerItem();
    if (c instanceof Menu) {
      ((Menu) c).fillMenuItems(id, header, menu);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    ViewController<?> c = getCurrentPagerItem();
    if (c instanceof MoreDelegate) {
      ((MoreDelegate) c).onMoreItemPressed(id);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    ViewController<?> c = getCurrentPagerItem();
    if (c instanceof Menu) {
      ((Menu) c).onMenuItemPressed(id, view);
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell != null ? headerCell.getView() : null;
  }

  @Override
  protected int getHeaderHeight () {
    switch (getTitleStyle()) {
      case TITLE_STYLE_BIG:
      case TITLE_STYLE_COMPACT_BIG:
        return Size.getHeaderPortraitSize() + Size.getHeaderDrawerSize();
      case TITLE_STYLE_COMPACT:
        return Size.getHeaderPortraitSize();
    }
    return super.getHeaderHeight();
  }

  private int scrollState;

  protected static final int TITLE_STYLE_COMPACT = 1;
  protected static final int TITLE_STYLE_BIG = 2;
  protected static final int TITLE_STYLE_COMPACT_BIG = 3;

  @IntDef({TITLE_STYLE_COMPACT, TITLE_STYLE_BIG, TITLE_STYLE_COMPACT_BIG})
  @Retention(RetentionPolicy.SOURCE)
  public @interface TitleStyle {}

  protected @TitleStyle int getTitleStyle () {
    return TITLE_STYLE_COMPACT;
  }

  /*protected final boolean useBigTitle () {
    return false;
  }*/

  protected boolean useCenteredTitle () {
    return false;
  }

  @Override
  public final void onPageScrollStateChanged (int state) {
    scrollState = state;
  }

  /**
   * @param position position from logic perspective
   * @param actualPosition position from viewPager perspective
   */
  public void onPageSelected (int position, int actualPosition) {
    // override
  }

  @Override
  public final void onPageSelected (int position) {
    onPageSelected(adapter.reversePosition(position), position);
  }

  private boolean disallowKeyboardHideOnPageScrolled;

  public void setDisallowKeyboardHideOnPageScrolled (boolean disallowKeyboardHideOnPageScrolled) {
    this.disallowKeyboardHideOnPageScrolled = disallowKeyboardHideOnPageScrolled;
  }

  public boolean isDisallowKeyboardHideOnPageScrolled () {
    return disallowKeyboardHideOnPageScrolled;
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    if (headerCell != null) {
      headerCell.getTopView().setSelectionFactor((float) position + positionOffset);
    }
    onPageScrolled(adapter.reversePosition(position), position, positionOffset, positionOffsetPixels);
    if (getKeyboardState() && !disallowKeyboardHideOnPageScrolled) {
      hideSoftwareKeyboard();
    }
  }

  public void onPageScrolled (int position, int actualPosition, float actualPositionOffset, int actualPositionOffsetPixels) {
    // override
  }

  public final boolean isAtFirstPosition () {
    return adapter.reversePosition(pager.getCurrentItem()) == 0;
  }

  protected final void replaceController (long itemId, ViewController<?> newController) {
    int position = getPagerItemPosition(itemId);
    if (position != NO_POSITION) {
      ViewController<?> currentController = adapter.getCachedItemByPosition(position);
      if (currentController != null) {
        currentController.destroy();
      }
      newController.setParentWrapper(this);
      newController.bindThemeListeners(this);
      adapter.cachedItems.put(position, newController);
      adapter.cachedPositions.put(itemId, position);
      adapter.notifyDataSetChanged();
    } else {
      newController.destroy();
    }
  }

  public final boolean scrollToFirstPosition () {
    if (!isAtFirstPosition()) {
      pager.setCurrentItem(adapter.reversePosition(0), true);
      return true;
    }
    return false;
  }

  public final int getCurrentPagerItemPosition() {
    return adapter.reversePosition(pager.getCurrentItem());
  }

  public final long getCurrentPagerItemId () {
    return getPagerItemId(getCurrentPagerItemPosition());
  }

  public final void notifyPagerItemPositionsChanged () {
    if (headerCell != null) {
      updateHeader();
    }
    if (adapter != null) {
      int itemCount = getPagerItemCount();
      SparseArrayCompat<ViewController<?>> cachedItems = new SparseArrayCompat<>(itemCount);
      LongSparseIntArray cachedPositions = new LongSparseIntArray(itemCount);
      for (int position = 0; position < itemCount; position++) {
        long itemId = getPagerItemId(position);
        int oldPosition = adapter.getCachedItemPosition(itemId);
        if (oldPosition != NO_POSITION) {
          ViewController<?> cachedItem = adapter.getCachedItemByPosition(oldPosition);
          cachedItems.put(position, cachedItem);
          cachedPositions.put(itemId, position);
          adapter.cachedItems.remove(oldPosition);
          adapter.cachedPositions.delete(itemId);
        }
      }
      if (inTransformMode() && headerView != null) {
        int currentPosition = adapter.reversePosition(pager.getCurrentItem());
        for (int index = 0; index < adapter.cachedItems.size(); index++) {
          int position = adapter.cachedItems.keyAt(index);
          if (currentPosition == position) {
            if (headerView.inSelectMode()) {
              headerView.finishSelectMode();
            } else if (headerView.inSearchMode()) {
              headerView.closeSearchMode(true, /* after */ null);
            } else if (headerView.inCustomMode()) {
              headerView.closeCustomMode();
            }
            break;
          }
        }
      }
      adapter.destroyCachedItems();
      adapter.cachedItems = cachedItems;
      adapter.cachedPositions = cachedPositions;
      adapter.notifyDataSetChanged();
      if (headerCell != null) {
        headerCell.getTopView().setSelectionFactor(pager.getCurrentItem());
      }
    }
  }

  public final ViewController<?> getCurrentPagerItem () {
    return getCachedControllerForPosition(getCurrentPagerItemPosition());
  }

  private int currentPosition;
  private float currentPositionOffset;

  @Override
  public boolean shouldDisallowScreenshots () {
    if (super.shouldDisallowScreenshots()) {
      return true;
    }
    if (isSearchAntagonistHidden()) {
      return false;
    }
    ViewController<?> controller = getCurrentPagerItem();
    if (controller != null && controller.shouldDisallowScreenshots()) {
      return true;
    }
    if (currentPositionOffset != 0f) {
      int targetPosition = currentPosition + (currentPositionOffset > 0f ? 1 : -1);
      int position = adapter.reversePosition(targetPosition);
      ViewController<?> otherController = getCachedControllerForPosition(position);
      if (otherController != null && otherController.shouldDisallowScreenshots()) {
        return true;
      }
    }
    return super.shouldDisallowScreenshots();
  }

  protected void setCurrentPagerPosition (int position, boolean animated) {
    if (headerCell != null && animated) {
      headerCell.getTopView().setFromTo(pager.getCurrentItem(), position);
    }
    pager.setCurrentItem(position, animated);
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    ViewController<?> c = adapter.getCachedItemByPosition(pager.getCurrentItem());
    return c instanceof OptionDelegate && ((OptionDelegate) c).onOptionItemPressed(optionItemView, id);
  }

  public int getPagerItemPosition (long itemId) {
    if (adapter != null) {
      int cachedItemPosition = adapter.getCachedItemPosition(itemId);
      if (cachedItemPosition != NO_POSITION) {
        return cachedItemPosition;
      }
    }
    int pagerItemCount = getPagerItemCount();
    for (int position = 0; position < pagerItemCount; position++) {
      if (getPagerItemId(position) == itemId) {
        return position;
      }
    }
    return NO_POSITION;
  }

  public final @Nullable ViewController<?> getCachedControllerForId (@IdRes int id) {
    return adapter != null ? adapter.getCachedItemByControllerId(id) : null;
  }

  public final @Nullable ViewController<?> getCachedControllerForItemId (long itemId) {
    return adapter != null ? adapter.getCachedItemByItemId(itemId) : null;
  }

  public final @Nullable ViewController<?> getCachedControllerForPosition (int position) {
    return adapter != null ? adapter.getCachedItemByPosition(position) : null;
  }

  public final @Nullable SparseArrayCompat<ViewController<?>> getAllCachedControllers () {
    return adapter != null ? adapter.cachedItems : null;
  }

  public final @NonNull ViewController<?> getPreparedControllerForPosition (int position) {
    if (adapter == null)
      getValue();
    ViewController<?> c = adapter.prepareViewController(position);
    c.getValue();
    return c;
  }

  public final void prepareControllerForPosition (int position, @Nullable Runnable after) {
    if (adapter != null && adapter.cachedItems.get(position = adapter.reversePosition(position)) == null) {
      ViewController<?> c = adapter.prepareViewController(position);
      if (c != null) {
        if (after != null) {
          c.postOnAnimationExecute(after);
        }
        c.getValue();
        return;
      }
    }
    if (after != null) {
      after.run();
    }
  }

  private @Nullable List<ViewPagerTopView.Item> cachedPagerSectionItems;

  protected abstract int getPagerItemCount ();
  protected long getPagerItemId (int position) {
    return position;
  }
  protected abstract void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager);
  protected abstract ViewController<?> onCreatePagerItemForPosition (Context context, int position);
  protected @Nullable abstract String[] getPagerSections ();
  protected @Nullable List<ViewPagerTopView.Item> getPagerSectionItems () {
    String[] pagerSections = getPagerSections();
    if (pagerSections == null) {
      return cachedPagerSectionItems = null;
    }
    if (pagerSections.length == 0) {
      return cachedPagerSectionItems = Collections.emptyList();
    }
    if (cachedPagerSectionItems != null && cachedPagerSectionItems.size() == pagerSections.length) {
      boolean hasChanges = false;
      for (int i = 0; i < pagerSections.length; i++) {
        if (!ObjectsCompat.equals(pagerSections[i], cachedPagerSectionItems.get(i).string)) {
          hasChanges = true;
          break;
        }
      }
      if (!hasChanges) {
        return cachedPagerSectionItems;
      }
    }
    List<ViewPagerTopView.Item> pagerSectionItems = new ArrayList<>(pagerSections.length);
    for (String pagerSection : pagerSections) {
      pagerSectionItems.add(new ViewPagerTopView.Item(pagerSection));
    }
    cachedPagerSectionItems = pagerSectionItems;
    return pagerSectionItems;
  }

  @Override
  public void onPagerItemClick (int index) {
    if (getCurrentPagerItemPosition() == index) {
      ViewController<?> c = adapter.getCachedItemByPosition(pager.getCurrentItem());
      if (c instanceof ScrollToTopDelegate) {
        ((ScrollToTopDelegate) c).onScrollToTopRequested();
      }
    } else if (pager.isPagingEnabled()) {
      setCurrentPagerPosition(index, true);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (adapter != null) {
      adapter.destroyCachedItems();
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return y <= HeaderView.getTopOffset() + getHeaderHeight() - Size.getHeaderDrawerSize() || (pager != null && (isAtFirstPosition() /*|| !pager.isPagingEnabled()*/) && scrollState == ViewPager.SCROLL_STATE_IDLE);
  }

  public static class ViewPagerAdapter extends PagerAdapter {
    private final Context context;
    private final ViewPagerController<?> parent;
    private /*final*/ SparseArrayCompat<ViewController<?>> cachedItems;
    private /*final*/ LongSparseIntArray cachedPositions;

    public ViewPagerAdapter (Context context, ViewPagerController<?> parent) {
      this.context = context;
      this.parent = parent;
      int itemCount = parent.getPagerItemCount();
      this.cachedItems = new SparseArrayCompat<>(itemCount);
      this.cachedPositions = new LongSparseIntArray(itemCount);
    }

    public int getCachedItemPosition (long itemId) {
      return cachedPositions.get(itemId, NO_POSITION);
    }

    public @Nullable ViewController<?> getCachedItemByPosition (int position) {
      return position != NO_POSITION ? cachedItems.get(position) : null;
    }

    public @Nullable ViewController<?> getCachedItemByItemId (long itemId) {
      return getCachedItemByPosition(getCachedItemPosition(itemId));
    }

    public @Nullable ViewController<?> getCachedItemByControllerId (@IdRes int id) {
      int size = cachedItems.size();
      for (int i = 0; i < size; i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        if (c.getId() == id) {
          return c;
        }
      }
      return null;
    }

    @Override
    public int getCount () {
      return parent.getPagerItemCount();
    }

    private final Set<ViewController<?>> visibleControllers = new HashSet<>();

    @Override
    public void destroyItem (ViewGroup container, int position, @NonNull Object object) {
      ViewController<?> c = (ViewController<?>) object;
      container.removeView(c.getValue());
      visibleControllers.remove(c);
      if (c.getAttachState()) {
        c.onAttachStateChanged(parent.navigationController, false);
      }
    }

    public void destroyCachedItems () {
      final int count = cachedItems.size();
      for (int i = 0; i < count; i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        if (!c.isDestroyed()) {
          c.destroy();
        }
      }
      cachedItems.clear();
      cachedPositions.clear();
    }

    private int reversePosition (int position) {
      return position; // FIXME RTL Lang.rtl() ? getCount() - position - 1 : position;
    }

    @Override
    public int getItemPosition (@NonNull Object object) {
      int count = cachedItems.size();
      for (int i = 0; i < count; i++) {
        if (cachedItems.valueAt(i) == object) {
          return reversePosition(cachedItems.keyAt(i));
        }
      }
      return POSITION_NONE;
    }

    public ViewController<?> prepareViewController (int position) {
      ViewController<?> c = cachedItems.get(position);
      if (c == null) {
        c = parent.onCreatePagerItemForPosition(context, position);
        c.setParentWrapper(parent);
        c.bindThemeListeners(parent);
        cachedItems.put(position, c);
        cachedPositions.put(parent.getPagerItemId(position), position);
      }
      return c;
    }

    @Override
    @NonNull
    public Object instantiateItem (@NonNull ViewGroup container, int position) {
      ViewController<?> c = prepareViewController(reversePosition(position));
      container.addView(c.getValue());
      visibleControllers.add(c);
      if (!c.getAttachState()) {
        c.onAttachStateChanged(parent.navigationController, true);
      }
      if ((position == parent.currentPosition || (parent.currentPositionOffset != 0f && position == parent.currentPosition + (parent.currentPositionOffset > 0f ? 1 : -1))) && c.shouldDisallowScreenshots()) {
        parent.context().checkDisallowScreenshots();
      }
      return c;
    }

    @Override
    public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
      return object instanceof ViewController && ((ViewController<?>) object).getWrapUnchecked() == view;
    }
  }
}
