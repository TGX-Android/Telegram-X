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

import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.SenderAvatarInfo;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class HapticMenuHelper implements View.OnTouchListener, View.OnLongClickListener {
  public interface Provider {
    List<MenuItem> onCreateHapticMenu (View view);
  }

  public interface OnItemClickListener {
    void onHapticMenuItemClick (View view, View parentView);
  }

  public static class MenuItem implements Destroyable {
    public final int id;
    public final CharSequence title;
    public final CharSequence username;
    public final TdApi.MessageSender sender;
    public final boolean needPremium;
    private final Drawable icon;
    public boolean isPersonal;
    public boolean isAnonymous;
    public final SenderAvatarInfo senderAvatarInfo;
    public final int iconResId;

    private Tdlib tdlib;
    private long userId;
    private TdlibCache.UserStatusChangeListener userStatusChangeListener;

    private View.OnClickListener onClickListener;
    private boolean visible = true;
    private long tutorialFlag;


    public MenuItem (int id, CharSequence title, CharSequence username, TdApi.MessageSender sender, boolean needPremium, Tdlib tdlib) {
      this.id = id;
      this.title = title;
      this.username = username;
      this.sender = sender;
      this.needPremium = needPremium;
      this.senderAvatarInfo = new SenderAvatarInfo(sender, tdlib);
      this.iconResId = 0;
      this.icon = null;
      initPersonalOrAnonymous(sender, tdlib);
    }

    public MenuItem (int id, CharSequence title, int iconResId) {
      this.id = id;
      this.title = title;
      this.iconResId = iconResId;
      username = null;
      sender = null;
      needPremium = false;
      senderAvatarInfo = null;
      this.icon = null;
    }

    public MenuItem (int id, CharSequence title, Drawable icon) {
      this.id = id;
      this.title = title;
      this.iconResId = 0;
      this.icon = icon;
      username = null;
      sender = null;
      needPremium = false;
      senderAvatarInfo = null;
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
      return visible || userId == 0 || tdlib == null || tdlib.cache().userLastSeenAvailable(userId);
    }

    public void setVisible(boolean visible) {
      this.visible = visible;
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

    private void initPersonalOrAnonymous (TdApi.MessageSender sender, Tdlib tdlib) {
      TdApi.User user = tdlib.myUser();
      if (user != null) {
        long senderUserId = Td.getSenderUserId(sender);
        if (senderUserId != 0) {
          isPersonal = user.id == senderUserId;
        }
      }
      if (sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
        TdApi.MessageSenderChat senderAsChat = (TdApi.MessageSenderChat) sender;
        isAnonymous = tdlib.isMultiChat(senderAsChat.chatId) && Td.isAnonymous(tdlib.chatStatus(senderAsChat.chatId));
      }
    }
  }

  private PopupLayout hapticMenu;

  private final Provider provider;
  private final OnItemClickListener onItemClickListener;
  @Nullable
  private final ThemeListenerList themeListeners;
  @Nullable
  private final ThemeDelegate forcedTheme;

  public HapticMenuHelper (Provider provider, OnItemClickListener onItemClickListener, @Nullable ThemeListenerList themeListeners, @Nullable ThemeDelegate forcedTheme) {
    this.provider = provider;
    this.onItemClickListener = onItemClickListener;
    this.themeListeners = themeListeners;
    this.forcedTheme = forcedTheme;
  }

  public HapticMenuHelper attachToView (View view) {
    view.setOnLongClickListener(this);
    // view.setOnTouchListener(this);
    return this;
  }

  public HapticMenuHelper detachFromView (View view) {
    view.setOnLongClickListener(null);
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
          processMovement(v, e.getX(), e.getY());
          break;
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP:
          dropMenu(v, e.getX(), e.getY(), e.getAction() == MotionEvent.ACTION_UP);
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

  private void openMenu (View view, List<MenuItem> items) {
    if (hapticMenu != null)
      hapticMenu.hideWindow(false);

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
    for (int i = 0; i < items.size(); i++) {
      MenuItem item = items.get(i);
      if (item.sender == null || item.id == R.id.msg_send_as_more) {
        item.boundView = moreWrap.addItem(item.id, item.title, item.iconResId, null, itemView -> onMenuItemClick(item, itemView, view));
      } else {
        item.boundView = moreWrap.addItem(i, item, itemView -> onMenuItemClick(item, itemView, view));
      }
      item.boundView.setVisibility(item.isVisible() ? View.VISIBLE : View.GONE);
      if (item.tutorialFlag != 0) {
        Settings.instance().markTutorialAsComplete(item.tutorialFlag);
      }
    }
    moreWrap.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_RIGHT);
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

  private void processMovement (View view, float x, float y) {
    // TODO handle selection
  }

  private void dropMenu (View view, float x, float y, boolean apply) {
    // TODO handle click ?
  }

  private void onMenuItemClick (MenuItem item, View view, View parentView) {
    if (hapticMenu != null && !hapticMenu.isWindowHidden()) {
      onItemClickListener.onHapticMenuItemClick(view, parentView);
      if (item.onClickListener != null) {
        item.onClickListener.onClick(view);
      }
      hideMenu();
    }
  }
}
