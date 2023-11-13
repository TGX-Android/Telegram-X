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
 * File created on 16/11/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.RecyclerViewProvider;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoneButton;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public abstract class RecyclerViewController<T> extends TelegramViewController<T> implements RecyclerViewProvider, ViewPagerController.ScrollToTopDelegate, Menu {
  public RecyclerViewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  protected abstract void onCreateView (Context context, CustomRecyclerView recyclerView);

  private CustomRecyclerView recyclerView;

  protected @ColorId
  int getRecyclerBackground () {
    return ColorId.background;
  }

  private boolean disableSettling;

  public void setDisableSettling (boolean disableSettling) {
    this.disableSettling = disableSettling;
  }

  private int scrollState, prevScrollState;
  private int savedRecyclerPosition = RecyclerView.NO_POSITION, savedRecyclerPositionOffset;

  protected void saveRecyclerPosition () {
    LinearLayoutManager manager = ((LinearLayoutManager) getRecyclerView().getLayoutManager());
    if (manager != null) {
      savedRecyclerPosition = manager.findFirstVisibleItemPosition();
      View view = manager.findViewByPosition(savedRecyclerPosition);
      savedRecyclerPositionOffset = view != null ? view.getTop() : 0;
    } else {
      savedRecyclerPosition = RecyclerView.NO_POSITION;
      savedRecyclerPositionOffset = 0;
    }
  }

  protected void applySavedRecyclerPosition () {
    if (savedRecyclerPosition != RecyclerView.NO_POSITION) {
      LinearLayoutManager manager = ((LinearLayoutManager) getRecyclerView().getLayoutManager());
      if (manager != null) {
        manager.scrollToPositionWithOffset(savedRecyclerPosition, savedRecyclerPositionOffset);
      }
      savedRecyclerPosition = RecyclerView.NO_POSITION;
      savedRecyclerPositionOffset = 0;
    }
  }

  @SuppressLint("InflateParams")
  @Override
  protected View onCreateView (Context context) {
    FrameLayoutFix wrap = new FrameLayoutFix(context);
    if (needContentBackground()) {
      ViewSupport.setThemedBackground(wrap, getRecyclerBackground(), this);
    }
    wrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    recyclerView = onCreateRecyclerView();
    Views.setScrollBarPosition(recyclerView);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (scrollState != newState) {
          prevScrollState = scrollState;
          scrollState = newState;
        }
      }
    });
    onCreateView(context, recyclerView);
    wrap.addView(recyclerView);
    if (needPersistentScrollPosition()) {
      restorePersistentScrollPosition();
    }
    if (needSearch()) {
      generateChatSearchView(wrap);
    }
    return wrap;
  }

  protected CustomRecyclerView onCreateRecyclerView () {
    CustomRecyclerView recyclerView = (CustomRecyclerView) Views.inflate(context(), R.layout.recycler_custom, null);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180L));
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false) {
      @Override
      public int scrollVerticallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int nScroll = 0;
        // Do not let auto scroll
        if (!disableSettling || recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_SETTLING || prevScrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
          nScroll = super.scrollVerticallyBy(dx, recycler, state);
        }
        return nScroll;
      }
    });
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    return recyclerView;
  }

  protected final void restorePersistentScrollPosition () {
    if (savedScrollPosition >= 0 && recyclerView != null) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
      if (manager != null && adapter != null && savedScrollPosition>= 0 && savedScrollPosition < adapter.getItemCount()) {
        manager.scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset);
        savedScrollPosition = -1;
        savedScrollOffset = 0;
      }
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    LinearLayoutManager manager = recyclerView != null ? (LinearLayoutManager) recyclerView.getLayoutManager() : null;
    if (manager != null) {
      int position = manager.findFirstVisibleItemPosition();
      View view = manager.findViewByPosition(position);
      int scrollBy = view != null ? manager.getDecoratedTop(view) : 0;
      outState.putInt(keyPrefix + "base_scroll_position", position);
      outState.putInt(keyPrefix + "base_scroll_offset", scrollBy);
    }
    return super.saveInstanceState(outState, keyPrefix);
  }

  protected boolean needPersistentScrollPosition () {
    return false;
  }

  private int savedScrollPosition = -1;
  private int savedScrollOffset;

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    savedScrollPosition = in.getInt(keyPrefix + "base_scroll_position", -1);
    savedScrollOffset = in.getInt(keyPrefix + "base_scroll_offset", 0);
    return super.restoreInstanceState(in, keyPrefix);
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    Views.setScrollBarPosition(recyclerView);
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    Lang.Listener adapterListener = recyclerView != null && recyclerView.getAdapter() instanceof Lang.Listener ? (Lang.Listener) recyclerView.getAdapter() : null;
    if (adapterListener != null) {
      adapterListener.onLanguagePackEvent(event, arg1);
    }
  }

  @Override
  public void onScrollToTopRequested () {
    if (recyclerView.getAdapter() != null) {
      try {
        LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
        getRecyclerView().stopScroll();
        int firstVisiblePosition = manager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == RecyclerView.NO_POSITION) {
          return;
        }
        int scrollTop = ((SettingsAdapter) recyclerView.getAdapter()).measureScrollTop(firstVisiblePosition);
        View view = manager.findViewByPosition(firstVisiblePosition);
        if (view != null) {
          scrollTop -= view.getTop();
        }
        getRecyclerView().smoothScrollBy(0, -scrollTop);
      } catch (Throwable t) {
        Log.w("Cannot scroll to top", t);
      }
    }
  }

  protected boolean needContentBackground () {
    return true;
  }

  @Override
  public RecyclerView provideRecyclerView () {
    return recyclerView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return recyclerView;
  }

  public CustomRecyclerView getRecyclerView () {
    return recyclerView;
  }

  protected int findFirstVisiblePosition () {
    return ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
  }

  protected int findLastVisiblePosition () {
    return ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
  }

  protected int getViewTop (int position) {
    View view = recyclerView.getLayoutManager().findViewByPosition(position);
    return view != null ? view.getTop() : 0;
  }

  protected void removeItemAnimatorDelayed () {
    if (recyclerView.getItemAnimator() != null) {
      recyclerView.postDelayed(() -> {
        if (!isDestroyed()) {
          recyclerView.setItemAnimator(null);
        }
      }, 300);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    Views.destroyRecyclerView(recyclerView);
  }

  // Search

  @Override
  protected int getMenuId () {
    return needSearch() ? R.id.menu_search : 0;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      header.addSearchButton(menu, this);
    } else if (id == R.id.menu_help) {
      header.addButton(menu, R.id.menu_btn_help, R.drawable.baseline_help_outline_24, getHeaderIconColorId(), this, Screen.dp(49f));
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    } else if (id == R.id.menu_more) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      openSearchMode();
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    } else if (id == R.id.menu_btn_more) {
      openMoreMenu();
    }
  }

  protected void openMoreMenu () {
    // override in children
  }

  private boolean needSearch () {
    return (flags & FLAG_NEED_SEARCH) != 0;
  }

  private static final int FLAG_NEED_SEARCH = 1;
  private static final int FLAG_NEED_TUTORIAL = 1 << 1;

  private int flags;

  public RecyclerViewController<T> setNeedSearch () {
    flags |= FLAG_NEED_SEARCH;
    return this;
  }

  public RecyclerViewController<T> setNeedTutorial () {
    flags |= FLAG_NEED_TUTORIAL;
    return this;
  }

  @Override
  protected View getSearchAntagonistView () {
    return recyclerView;
  }

  @Override
  protected int getSearchMenuId () {
    return needSearch() ? R.id.menu_clear : super.getSearchMenuId();
  }

  // Done button

  private DoneButton doneButton;

  protected void onDoneClick () {
    // Override in children
  }

  protected final DoneButton getDoneButton () {
    if (doneButton == null) {
      doneButton = new DoneButton(context);
      int padding = Screen.dp(4f);
      FrameLayoutFix.LayoutParams params;
      params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM);
      params.rightMargin = params.leftMargin = params.bottomMargin = Screen.dp(16f) - padding;

      doneButton = new DoneButton(context);
      doneButton.setId(R.id.btn_done);
      addThemeInvalidateListener(doneButton);
      doneButton.setOnClickListener(v -> {
        if (doneButton.getIsVisible()) {
          onDoneClick();
        }
      });
      doneButton.setLayoutParams(params);
      ((ViewGroup) getValue()).addView(doneButton);
    }
    return doneButton;
  }

  protected final void setDoneVisible (boolean isVisible, boolean animated) {
    getDoneButton().setIsVisible(isVisible, animated);
  }

  protected final void setDoneIcon (@DrawableRes int doneRes) {
    getDoneButton().setIcon(doneRes);
  }

  protected final void setDoneProgress (boolean inProgress) {
    getDoneButton().setInProgress(inProgress);
  }
}
