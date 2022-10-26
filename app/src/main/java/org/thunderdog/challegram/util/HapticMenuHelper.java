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
 */
package org.thunderdog.challegram.util;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.lambda.Destroyable;

public class HapticMenuHelper implements View.OnTouchListener, View.OnLongClickListener {
  public interface Provider {
    List<MenuItem> onCreateHapticMenu (View view);
  }

  public interface OnItemClickListener {
    boolean onHapticMenuItemClick (View view, View parentView, MenuItem item);
  }

  public static class MenuItem implements Destroyable {
    public final int id;
    public final CharSequence title;
    public final CharSequence subtitle;
    public final int iconResId;
    public final Drawable icon;
    public final TdlibSender tdlibSender;

    private Tdlib tdlib;
    private long userId;
    private TdlibCache.UserStatusChangeListener userStatusChangeListener;

    private View.OnClickListener onClickListener;

    private long tutorialFlag;

    public MenuItem (int id, CharSequence title, int iconResId) {
      this.id = id;
      this.title = title;
      this.subtitle = null;
      this.iconResId = iconResId;
      this.icon = null;
      this.tdlibSender = null;
    }

    public MenuItem (int id, CharSequence title, Drawable icon) {
      this.id = id;
      this.title = title;
      this.subtitle = null;
      this.iconResId = 0;
      this.icon = icon;
      this.tdlibSender = null;
    }

    public MenuItem (int id, CharSequence title, CharSequence subtitle, Tdlib tdLib, TdlibSender tdlibSender) {
      this.id = id;
      this.title = title;
      this.subtitle = subtitle;
      this.tdlib = tdLib;
      this.iconResId = 0;
      this.icon = null;
      this.tdlibSender = tdlibSender;
    }

    public MenuItem setOnClickListener (View.OnClickListener onClickListener) {
      this.onClickListener = onClickListener;
      return this;
    }

    public MenuItem bindToLastSeenAvailability (Tdlib tdlib, long userId) {
      if (tdlib == null || userId == 0)
        return this;
      this.tdlib = tdlib;
      this.userId = userId;
      this.userStatusChangeListener = ((userId1, status, uiOnly) -> {
        if (userId1 == userId) {
          boolean isVisible = status != null && status.getConstructor() == TdApi.UserStatusOffline.CONSTRUCTOR && ((TdApi.UserStatusOffline) status).wasOnline != 0;
          if (boundView != null) {
            boundView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
          }
        }
      });
      tdlib.cache().subscribeToUserStatusChanges(userId, this.userStatusChangeListener);
      return this;
    }

    public MenuItem bindTutorialFlag (long tutorialFlag) {
      this.tutorialFlag = tutorialFlag;
      return this;
    }

    public boolean isVisible () {
      return userId == 0 || tdlib == null || tdlib.cache().userLastSeenAvailable(userId);
    }

    @Nullable
    private View boundView;

    @Override
    public void performDestroy () {
      if (this.tdlib != null && this.userStatusChangeListener != null) {
        this.tdlib.cache().unsubscribeFromUserStatusChanges(userId, userStatusChangeListener);
        this.tdlib = null; this.userStatusChangeListener = null;
        this.boundView = null;
      }
    }
  }


  public static int FLAG_OPEN_BY_LONG_CLICK = 1 << 1;
  public static int FLAG_OPEN_BY_SWIPE = 1 << 2;

  private PopupLayout hapticMenu;

  private final Provider provider;
  private final OnItemClickListener onItemClickListener;
  @Nullable
  private final ThemeListenerList themeListeners;
  @Nullable
  private final ThemeDelegate forcedTheme;
  private int anchorMode;
  private int openFlags;

  public HapticMenuHelper (Provider provider, OnItemClickListener onItemClickListener, @Nullable ThemeListenerList themeListeners, @Nullable ThemeDelegate forcedTheme) {
    this.provider = provider;
    this.onItemClickListener = onItemClickListener;
    this.themeListeners = themeListeners;
    this.forcedTheme = forcedTheme;
    this.openFlags = FLAG_OPEN_BY_LONG_CLICK;
  }

  public HapticMenuHelper (Provider provider, OnItemClickListener onItemClickListener, @Nullable ThemeListenerList themeListeners, @Nullable ThemeDelegate forcedTheme, int openFlag) {
    this.provider = provider;
    this.onItemClickListener = onItemClickListener;
    this.themeListeners = themeListeners;
    this.forcedTheme = forcedTheme;
    this.openFlags = openFlag;
    this.anchorMode = MenuMoreWrap.ANCHOR_MODE_RIGHT;
  }

  public HapticMenuHelper attachToView (View view) {
    if (BitwiseUtils.getFlag(openFlags, FLAG_OPEN_BY_LONG_CLICK)) {
      view.setOnLongClickListener(this);
    }
    if (BitwiseUtils.getFlag(openFlags, FLAG_OPEN_BY_SWIPE)) {
      view.setOnTouchListener(this);
    }
    return this;
  }

  public HapticMenuHelper detachFromView (View view) {
    if (BitwiseUtils.getFlag(openFlags, FLAG_OPEN_BY_LONG_CLICK)) {
      view.setOnLongClickListener(null);
    }
    if (BitwiseUtils.getFlag(openFlags, FLAG_OPEN_BY_SWIPE)) {
      view.setOnTouchListener(null);
    }
    return this;
  }

  public HapticMenuHelper setAnchorMode (int anchorMode) {
    this.anchorMode = anchorMode;
    return this;
  }

  @Override
  public boolean onLongClick (View v) {
    List<MenuItem> items = provider.onCreateHapticMenu(v);
    if (items != null && !items.isEmpty()) {
      // UI.forceVibrate(v, true);
      openMenu(v, items);
      return true;
    }
    return false;
  }

  @Override
  public boolean onTouch (View v, MotionEvent e) {
    if (hapticMenu != null) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          break;
        case MotionEvent.ACTION_MOVE:
          processMovement(e.getRawX(), e.getRawY());
          break;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
          dropMenu(v, e.getRawX(), e.getRawY(), e.getAction() == MotionEvent.ACTION_UP);
          break;
      }
    }
    return false;
  }

  public boolean openMenu (View view) {
    List<MenuItem> items = provider.onCreateHapticMenu(view);
    if (items != null && !items.isEmpty()) {
      openMenu(view, items);
      return true;
    }
    return false;
  }

  private List<MenuItem> items;
  public void openMenu (View view, List<MenuItem> items) {
    if (hapticMenu != null)
      hapticMenu.hideWindow(false);
    this.items = items;
    MenuMoreWrap moreWrap = new MenuMoreWrap(view.getContext());
    moreWrap.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      ViewParent parent = v.getParent();
      if (parent == null)
        return;

      int viewWidth = v.getMeasuredWidth();
      int viewHeight = v.getMeasuredHeight();

      int targetWidth = view.getMeasuredWidth();
      int targetHeight = view.getMeasuredHeight();

      int parentWidth = ((ViewGroup) parent).getMeasuredWidth();
      int parentHeight = ((ViewGroup) parent).getMeasuredHeight();

      if (viewWidth <= 0 || viewHeight <= 0 || parentWidth <= 0 || parentHeight <= 0)
        return;

      int[] out = Views.getLocationInWindow(view);
      int targetCenterX = out[0] + targetWidth / 2;
      int targetCenterY = out[1] + targetHeight / 2;

      out = Views.getLocationInWindow(v);
      out[0] -= v.getTranslationX();
      out[1] -= v.getTranslationY();
      int centerX = out[0] + viewWidth / 2;
      int centerY = out[1] + viewHeight / 2;

      int resultCenterX = Math.max(viewWidth / 2, Math.min(parentWidth - viewWidth / 2, targetCenterX));
      int resultCenterY = targetCenterY - targetHeight / 2 - viewHeight / 2;

      v.setTranslationX(resultCenterX - centerX);
      v.setTranslationY(resultCenterY - centerY);
    });
    moreWrap.init(themeListeners, forcedTheme);
    for (MenuItem item : items) {
      if (item.tdlib != null && item.tdlibSender != null && item.subtitle != null) {
        item.boundView = moreWrap.addSendAsItem(item.id, item.tdlib, item.tdlibSender, item.title, item.subtitle, itemView -> onMenuItemClick(item, itemView, view));
      } else {
        item.boundView = moreWrap.addTextWithIconItem(item.id, item.title, item.iconResId, item.icon, itemView -> onMenuItemClick(item, itemView, view));
      }
      item.boundView.setVisibility(item.isVisible() ? View.VISIBLE : View.GONE);
      if (item.tutorialFlag != 0) {
        Settings.instance().markTutorialAsComplete(item.tutorialFlag);
      }
    }
    moreWrap.setAnchorMode(anchorMode);
    moreWrap.setShouldPivotBottom(true);
    moreWrap.setRightNumber(0);

    hapticMenu = new PopupLayout(view.getContext());
    hapticMenu.init(false);
    hapticMenu.setNeedRootInsets();
    hapticMenu.setOverlayStatusBar(true);
    hapticMenu.setDismissListener(popup -> {
      for (MenuItem item : items) {
        item.performDestroy();
      }
      if (this.hapticMenu == popup) {
        this.hapticMenu = null;
      }
    });
    hapticMenu.showMoreView(moreWrap);
  }

  public void hideMenu () {
    if (this.hapticMenu != null) {
      this.hapticMenu.hideWindow(true);
      this.hapticMenu = null;
    }
  }

 private final Rect touchRect = new Rect();

  private void processMovement (float rawX, float rawY) {
    View child = hapticMenu.getBoundView();
    if (child instanceof MenuMoreWrap) {
      MenuMoreWrap menuMoreWrap = (MenuMoreWrap) child;
      for (int i = 0; i < menuMoreWrap.getChildCount(); i++) {
        menuMoreWrap.getChildAt(i).getGlobalVisibleRect(touchRect);
        menuMoreWrap.getChildAt(i).setHovered(touchRect.contains((int) rawX, (int) rawY));
      }
    }
  }

  private void dropMenu (View view, float rawX, float rawY, boolean apply) {
    if (apply) {
      View child = hapticMenu.getBoundView();
      if (child instanceof MenuMoreWrap) {
        MenuMoreWrap menuMoreWrap = (MenuMoreWrap) child;
        for (int i = 0; i < menuMoreWrap.getChildCount(); i++) {
          menuMoreWrap.getChildAt(i).getGlobalVisibleRect(touchRect);
          if (touchRect.contains((int) rawX, (int) rawY)) {
            onMenuItemClick(items.get(i), menuMoreWrap.getChildAt(i), view);
            return;
          }
        }
      }
    }
  }

  private void onMenuItemClick (MenuItem item, View view, View parentView) {
    if (hapticMenu != null && !hapticMenu.isWindowHidden()) {
      removeHoverStateFromAll();
      boolean isClickHandled = onItemClickListener.onHapticMenuItemClick(view, parentView, item);
      if (item.onClickListener != null) {
        item.onClickListener.onClick(view);
      }
      if (isClickHandled) {
        hideMenu();
      }
    }
  }

  private void removeHoverStateFromAll () {
    View child = hapticMenu.getBoundView();
    if (child instanceof MenuMoreWrap) {
      MenuMoreWrap menuMoreWrap = (MenuMoreWrap) child;
      for (int i = 0; i < menuMoreWrap.getChildCount(); i++) {
        menuMoreWrap.getChildAt(i).setHovered(false);
      }
    }
  }
}
