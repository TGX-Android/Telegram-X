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
 */
package org.thunderdog.challegram.navigation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Responsive list-detail layout for large windows (foldable inner displays, tablets, resizable windows).
 * <p>
 * The Activity's {@link NavigationController} stays the single source of truth and becomes the right (detail) pane.
 * A second navigator hosting its own {@link MainController} is shown as the left (list) pane only when the
 * current window is wide enough. Folding back to a narrow window just hides the left pane, so no controller
 * state has to be transferred.
 */
public final class DualPaneLayout {
  public static final String TAG = "DualPane";

  public static final int MODE_AUTO = 0;
  public static final int MODE_FORCE = 1;
  public static final int MODE_OFF = 2;

  /** Narrowest usable chat list. */
  public static final int MIN_LIST_WIDTH_DP = 280;
  /** Narrowest usable conversation (narrowest phone width Telegram X already supports). */
  public static final int MIN_DETAIL_WIDTH_DP = 320;
  public static final int MAX_LIST_WIDTH_DP = 400;
  /** Below this height (WindowSizeClass "compact height") two panes are impractical. */
  public static final int MIN_HEIGHT_DP = 480;
  public static final float LIST_WIDTH_FRACTION = .38f;

  public static boolean shouldUseDualPane (int windowWidthDp, int windowHeightDp, int mode) {
    if (mode == MODE_OFF || windowWidthDp < MIN_LIST_WIDTH_DP + MIN_DETAIL_WIDTH_DP) {
      return false;
    }
    return mode == MODE_FORCE || windowHeightDp >= MIN_HEIGHT_DP;
  }

  public static int listPaneWidthDp (int windowWidthDp) {
    int width = Math.round(windowWidthDp * LIST_WIDTH_FRACTION);
    width = Math.max(MIN_LIST_WIDTH_DP, Math.min(MAX_LIST_WIDTH_DP, width));
    // Never squeeze the conversation below its minimum.
    return Math.min(width, Math.max(MIN_LIST_WIDTH_DP, windowWidthDp - MIN_DETAIL_WIDTH_DP));
  }

  // Instance

  private final BaseActivity context;
  private final ViewGroup container;
  private final NavigationController mainNavigation;

  private @Nullable NavigationController listNavigation;
  private @Nullable EmptyDetailView placeholder;
  private boolean isActive;
  private int listWidthPx;

  public DualPaneLayout (BaseActivity context, ViewGroup container, NavigationController mainNavigation) {
    this.context = context;
    this.container = container;
    this.mainNavigation = mainNavigation;
  }

  public boolean isActive () {
    return isActive;
  }

  public @Nullable NavigationController getListNavigation () {
    return isActive ? listNavigation : null;
  }

  /** True when the touch at contentView-relative {@code x} belongs to the list pane. */
  public boolean isInListPane (float x) {
    if (!isActive) {
      return false;
    }
    return Lang.rtl() ? x >= container.getMeasuredWidth() - listWidthPx : x < listWidthPx;
  }

  public void update () {
    Configuration config = context.getResources().getConfiguration();
    int mode = Settings.instance().getDualPaneMode();
    boolean active = shouldUseDualPane(config.screenWidthDp, config.screenHeightDp, mode);
    Tdlib tdlib = rootTdlib();
    if (active && tdlib == null) {
      // Main stack not initialized yet (login, intro, passcode). Re-evaluated on the next stack change.
      active = false;
    }
    Log.i(TAG, "window=" + config.screenWidthDp + "x" + config.screenHeightDp + "dp mode=" + mode + " dual=" + active);
    if (active) {
      activate(tdlib, Screen.dp(listPaneWidthDp(config.screenWidthDp)));
    } else {
      deactivate();
    }
  }

  public void onConfigurationChanged (Configuration newConfig) {
    if (listNavigation != null) {
      listNavigation.configurationChanged(newConfig);
    }
    update();
  }

  public void onKeyboardStateChanged (boolean visible) {
    if (listNavigation != null && isActive) {
      listNavigation.onKeyboardStateChanged(visible);
    }
  }

  /** Called whenever the main stack changes: keeps placeholder and account in sync. */
  public void onMainStackChanged () {
    if (isActive) {
      Tdlib tdlib = rootTdlib();
      if (tdlib == null) {
        deactivate();
      } else {
        ensureListNavigation(tdlib);
        updatePlaceholder();
        refreshOpenedChat();
      }
    } else if (Settings.instance().getDualPaneMode() != MODE_OFF) {
      // Stack may have just become eligible (e.g. after login).
      Configuration config = context.getResources().getConfiguration();
      if (shouldUseDualPane(config.screenWidthDp, config.screenHeightDp, Settings.instance().getDualPaneMode()) && rootTdlib() != null) {
        update();
      }
    }
  }

  public void destroy () {
    if (listNavigation != null) {
      listNavigation.destroy();
      listNavigation = null;
    }
  }

  private @Nullable Tdlib rootTdlib () {
    ViewController<?> root = mainNavigation.getStack().get(0);
    return root instanceof MainController && !root.isDestroyed() ? root.tdlib() : null;
  }

  private void ensureListNavigation (Tdlib tdlib) {
    ViewController<?> current = listNavigation != null ? listNavigation.getStack().get(0) : null;
    if (current != null && current.tdlib() == tdlib && !current.isDestroyed()) {
      return;
    }
    if (listNavigation == null) {
      listNavigation = new NavigationController(context);
      listNavigation.setSecondaryPane(true);
      View view = listNavigation.getValue();
      container.addView(view, container.indexOfChild(mainNavigation.getValue()) + 1);
    }
    MainController chats = new MainController(context, tdlib);
    listNavigation.initController(chats); // clears (and destroys) the previous account's list
  }

  @SuppressLint("RtlHardcoded")
  private void activate (Tdlib tdlib, int listWidthPx) {
    ensureListNavigation(tdlib);
    boolean rtl = Lang.rtl();
    this.listWidthPx = listWidthPx;

    //noinspection ConstantConditions
    View listView = listNavigation.getValue();
    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) listView.getLayoutParams();
    params.width = listWidthPx;
    params.gravity = rtl ? Gravity.RIGHT : Gravity.LEFT;
    listView.setLayoutParams(params);

    mainNavigation.setPaneMargins(rtl ? 0 : listWidthPx, rtl ? listWidthPx : 0);

    if (!isActive) {
      isActive = true;
      listView.setVisibility(View.VISIBLE);
      listNavigation.onFocus();
      container.requestApplyInsets();
    }
    updatePlaceholder();
    refreshOpenedChat();
  }

  private void deactivate () {
    if (!isActive) {
      return;
    }
    isActive = false;
    listWidthPx = 0;
    mainNavigation.setPaneMargins(0, 0);
    if (listNavigation != null) {
      listNavigation.onBlur();
      listNavigation.getValue().setVisibility(View.GONE);
    }
    updatePlaceholder();
  }

  /**
   * Called on every horizontal transition frame of the main navigator. When the hidden root chat list is the one
   * being revealed (swipe-back to root, or opening the first chat), the placeholder follows the moving edge so the
   * root list never shows through. {@code factor}: 0 = incoming/top controller fully shown, 1 = {@code left} fully shown.
   */
  void onMainTranslation (@Nullable ViewController<?> left, float factor) {
    if (!isActive || !isMainRoot(left)) {
      return;
    }
    updatePlaceholder();
    if (placeholder != null) {
      float offset = (1f - factor) * placeholder.getMeasuredWidth();
      placeholder.setTranslationX(Lang.rtl() ? offset : -offset);
    }
  }

  /** True when {@code chatsController} belongs to the list pane and {@code chatId} is open in the detail pane. */
  public boolean isOpenedInListPane (ViewController<?> chatsController, long chatId) {
    return chatId != 0 && chatId == openedChatId() && listNavigation != null
      && chatsController.getParentOrSelf() == listNavigation.getStack().get(0);
  }

  private long openedChatId () {
    ViewController<?> current = isActive ? mainNavigation.getCurrentStackItem() : null;
    return current instanceof MessagesController ? ((MessagesController) current).getChatId() : 0;
  }

  /** Re-applies the "opened" highlight to chat rows already bound in the list pane. */
  private void refreshOpenedChat () {
    if (listNavigation != null) {
      refreshOpenedChat(listNavigation.getValue(), openedChatId());
    }
  }

  private static void refreshOpenedChat (View view, long chatId) {
    if (view instanceof ChatView) {
      ChatView chatView = (ChatView) view;
      chatView.setIsOpened(chatId != 0 && chatView.getChatId() == chatId);
    } else if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        refreshOpenedChat(group.getChildAt(i), chatId);
      }
    }
  }

  private boolean isMainRoot (@Nullable ViewController<?> c) {
    return c != null && c == mainNavigation.getStack().get(0);
  }

  private void updatePlaceholder () {
    boolean transition = isMainRoot(mainNavigation.getTransitionLeft());
    boolean show = isActive && (transition || mainNavigation.getStack().getCurrentIndex() == 0);
    if (show && placeholder == null) {
      placeholder = new EmptyDetailView(context);
      ((ViewGroup) mainNavigation.getValue()).addView(placeholder); // above header and floating button
    }
    if (placeholder != null) {
      placeholder.setVisibility(show ? View.VISIBLE : View.GONE);
      if (!transition) {
        placeholder.setTranslationX(0f);
      }
    }
  }

  private static class EmptyDetailView extends View {
    public EmptyDetailView (Context context) {
      super(context);
      setClickable(true); // shield the hidden root chat list underneath
      setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      ViewSupport.setThemedBackground(this, ColorId.background);
    }

    @Override
    protected void onDraw (Canvas c) {
      String text = Lang.getString(R.string.DualPaneEmpty);
      TextPaint paint = Paints.getRegularTextPaint(15f, Theme.textDecentColor());
      float textWidth = paint.measureText(text);
      c.drawText(text, (getMeasuredWidth() - textWidth) / 2f, getMeasuredHeight() / 2f, paint);
    }
  }
}
