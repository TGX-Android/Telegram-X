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
 * File created on 23/04/2015 at 16:15
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.theme.ThemeListenerEntry;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.ui.SettingsBugController;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.Crash;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.SimpleStringItem;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.v.HeaderEditText;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.InfiniteRecyclerView;
import org.thunderdog.challegram.widget.MaterialEditText;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.TimerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.Future;
import me.vkryl.core.lambda.FutureBool;
import me.vkryl.core.lambda.FutureLong;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

// TODO separate Telegram-related stuff to TelegramViewController<T>. This will allow reusing navigation logic in other projects

public abstract class ViewController<T> implements Future<View>, ThemeChangeListener, Lang.Listener, ForceTouchView.StateListener, BaseActivity.ActivityListener, BaseActivity.KeyEventListener, TdlibDelegate, Destroyable {
  // hint: stop at 0x40000000
  private static final int FLAG_PAUSED                      = 1 << 12;
  private static final int FLAG_FOCUSED                     = 1 << 13;
  private static final int FLAG_DESTROYED                   = 1 << 14;
  private static final int FLAG_LOCK_ALWAYS                 = 1 << 15;
  private static final int FLAG_KEYBOARD_SHOWN              = 1 << 16;
  private static final int FLAG_KEYBOARD_STATE              = 1 << 17;
  private static final int FLAG_SWIPE_DISABLED              = 1 << 18;
  private static final int FLAG_IN_SELECT_MODE              = 1 << 19;
  private static final int FLAG_IN_SEARCH_MODE              = 1 << 20;
  private static final int FLAG_IN_CUSTOM_MODE              = 1 << 21;
  private static final int FLAG_ATTACHED_TO_NAVIGATION      = 1 << 22;
  private static final int FLAG_PREVENT_LEAVING_SEARCH_MODE = 1 << 23;
  private static final int FLAG_SHARE_CUSTOM_HEADER         = 1 << 24;
  private static final int FLAG_IN_FORCE_TOUCH_MODE         = 1 << 25;
  private static final int FLAG_ATTACH_STATE                = 1 << 26;
  private static final int FLAG_CONTENT_INTERACTED          = 1 << 27;
  private static final int FLAG_MAXIMIZING                  = 1 << 28;
  private static final int FLAG_PREVENT_KEYBOARD_HIDE       = 1 << 29;

  protected final @NonNull BaseActivity context;
  protected final Tdlib tdlib;

  private int flags;
  private CharSequence name;
  private T args;
  private View contentView;

  private View lockFocusView;

  private @Nullable ViewController<?> parentWrapper;

  protected @Nullable HeaderView headerView;
  protected @Nullable FloatingButton floatingButton;
  protected @Nullable NavigationController navigationController;

  public ViewController (@NonNull Context context, Tdlib tdlib) {
    this.context = UI.getContext(context);
    this.tdlib = tdlib;
    if (this.context == null) {
      throw new IllegalArgumentException();
    }
  }

  public void onInteractedWithContent () {
    this.flags |= FLAG_CONTENT_INTERACTED;
  }

  public boolean hasInteractedWithContent () {
    return (flags & FLAG_CONTENT_INTERACTED) != 0;
  }

  public ViewController<?> getParentOrSelf () {
    return parentWrapper != null ? parentWrapper : this;
  }

  public void setParentWrapper (@Nullable ViewController<?> parentWrapper) {
    this.parentWrapper = parentWrapper;
  }

  // Theme stuff

  private void subscribeToNeededUpdates () {
    ThemeManager.instance().addThemeListener(this);
    Lang.addLanguageListener(this);
    context.addActivityListener(this);
  }

  private void unsubscribeFromControllerUpdates () {
    ThemeManager.instance().removeThemeListener(this);
    Lang.removeLanguageListener(this);
    context.removeActivityListener(this);
  }

  public boolean allowThemeChanges () {
    return true;
  }

  private @Nullable ThemeListenerList themeListeners;

  public final @NonNull ThemeListenerList getThemeListeners () {
    if (themeListeners == null)
      themeListeners = new ThemeListenerList();
    return themeListeners;
  }

  private ThemeListenerEntry addThemeListener (ThemeListenerEntry listenerEntry) {
    if (listenerEntry != null && !listenerEntry.isEmpty()) {
      getThemeListeners().add(listenerEntry);
    }
    return listenerEntry;
  }

  public final void attachToThemeListeners (ThemeListenerList themeListeners) {
    if (this.themeListeners != null)
      themeListeners.addAll(this.themeListeners);
    this.themeListeners = themeListeners;
  }

  public final void bindThemeListeners (ViewController<?> c) {
    if (c != null) {
      if (c.themeListeners == null)
        c.themeListeners = new ThemeListenerList();
      if (this.themeListeners != null)
        c.themeListeners.getList().addAll(this.themeListeners.getList());
      this.themeListeners = c.themeListeners;
    }
  }

  public final void addThemePaintColorListener (Paint paint, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_PAINT_COLOR, color, paint));
  }

  public final void addThemeBackgroundColorListener (View view, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_BACKGROUND, color, view));
  }

  public final void addThemeFillingColorListener (View view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_BACKGROUND, ColorId.filling, view));
  }

  public final ThemeListenerEntry addThemeTextColorListener (Object view, @ColorId int colorId) {
    ThemeListenerEntry entry;
    addThemeListener(entry = new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, colorId, view));
    return entry;
  }

  public final ThemeListenerEntry addOrUpdateThemeTextColorListener (Object view, @ColorId int colorId) {
    ThemeListenerEntry entry = getThemeListeners().findThemeListenerByTarget(view, ThemeListenerEntry.MODE_TEXT_COLOR);
    if (entry != null) {
      entry.setTargetColorId(colorId);
      return entry;
    }
    return addThemeTextColorListener(view, colorId);
  }

  public final ThemeListenerEntry addThemeHintTextColorListener (Object view, @ColorId int color) {
    return addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_HINT_TEXT_COLOR, color, view));
  }

  public final void addThemeLinkTextColorListener (Object view, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_LINK_TEXT_COLOR, color, view));
  }

  public final void addThemeHighlightColorListener (Object view, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_HIGHLIGHT_COLOR, color, view));
  }

  public final void addThemeTextAccentColorListener (Object view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, ColorId.text, view));
  }

  public final void addThemeTextDecentColorListener (Object view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, ColorId.textLight, view));
  }

  public final void addThemeInvalidateListener (View view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_INVALIDATE, ColorId.NONE, view));
  }

  public final ThemeListenerEntry addThemeFilterListener (Object target, @ColorId int color) {
    ThemeListenerEntry entry;
    addThemeListener(entry = new ThemeListenerEntry(ThemeListenerEntry.MODE_FILTER, color, target));
    return entry;
  }

  public final void addThemeSpecialFilterListener (Object target, @ColorId int colorId) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_SPECIAL_FILTER, colorId, target));
  }

  public final void removeThemeListenerByTarget (Object target) {
    if (themeListeners != null) {
      themeListeners.removeThemeListenerByTarget(target);
    }
  }

  @Override
  public boolean needsTempUpdates () {
    return isAttachedToNavigationController() || isInForceTouchMode();
  }

  @Override
  @CallSuper
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    if (themeListeners != null) {
      themeListeners.onThemeColorsChanged(areTemp);
    }
  }

  @Override
  public void onThemeChanged (ThemeDelegate fromTheme, ThemeDelegate toTheme) { }

  @Override
  public void onThemeAutoNightModeChanged (int autoNightMode) { }

  @CallSuper
  protected void handleLanguageDirectionChange () {
    if (searchHeaderView != null)
      HeaderView.updateEditTextDirection(searchHeaderView.editView(), Screen.dp(68f), Screen.dp(49f));
    if (counterHeaderView != null)
      HeaderView.updateLayoutMargins(counterHeaderView, Screen.dp(68f), 0);
    View headerCell = getCustomHeaderCell();
    if (headerCell instanceof RtlCheckListener) {
      ((RtlCheckListener) headerCell).checkRtl();
    }
    // override
  }

  protected void handleLanguagePackEvent (@Lang.EventType int event, int arg1) {
    // override
  }

  @Override
  @CallSuper
  public void onLanguagePackEvent (@Lang.EventType int event, int arg1) {
    boolean directionChanged = Lang.hasDirectionChanged(event, arg1);

    if (directionChanged) {
      handleLanguageDirectionChange();
    }

    switch (event) {
      case Lang.EVENT_PACK_CHANGED:
      case Lang.EVENT_DIRECTION_CHANGED:
      case Lang.EVENT_DATE_FORMAT_CHANGED:
        if (localeChangers != null) {
          for (LocaleChanger changer : localeChangers) {
            changer.onLocaleChange();
          }
        }
        if (event == Lang.EVENT_PACK_CHANGED) {
          setName(getName());
        }
        break;
      case Lang.EVENT_STRING_CHANGED:
        if (localeChangers != null) {
          for (LocaleChanger changer : localeChangers) {
            if (changer.getResource() == arg1)
              changer.onLocaleChange();
          }
        }
        setName(getName());
        break;
    }

    handleLanguagePackEvent(event, arg1);
  }

  // Navigation stuff

  public NavigationController navigationController () {
    return navigationController;
  }

  protected void attachNavigationController (NavigationController navigationController) {
    this.flags |= FLAG_ATTACHED_TO_NAVIGATION;
    this.navigationController = navigationController;
    this.headerView = navigationController.getHeaderView();
    this.floatingButton = navigationController.getFloatingButton();
  }

  public void attachHeaderViewWithoutNavigation (HeaderView headerView) {
    this.flags &= ~FLAG_ATTACHED_TO_NAVIGATION; // since it's false state
    this.headerView = headerView;
    this.navigationController = null;
    this.floatingButton = null;
  }

  protected void detachNavigationController () {
    this.flags &= ~FLAG_ATTACHED_TO_NAVIGATION;
    this.navigationController = null;
    this.headerView = null;
    this.floatingButton = null;
  }

  protected final NavigationStack navigationStack () {
    return navigationController != null ? navigationController.getStack() : null;
  }

  public final int stackSize () {
    return navigationController != null ? navigationController.getStackSize() : 0;
  }

  protected final ViewController<?> previousStackItem () {
    return navigationController != null ? navigationController.getStack().getPrevious() : null;
  }

  public final ViewController<?> stackItemAt (int index) {
    return navigationController != null ? navigationController.getStack().get(index) : null;
  }

  public final ViewController<?> removeStackItemAt (int index) {
    return navigationController != null ? navigationController.getStack().remove(index) : null;
  }

  public final ViewController<?> destroyStackItemAt (int index) {
    return navigationController != null ? navigationController.getStack().destroy(index) : null;
  }

  public final ViewController<?> removeStackItemById (int id) {
    return navigationController != null ? navigationController.getStack().removeById(id) : null;
  }

  public final ViewController<?> destroyStackItemById (int id) {
    return navigationController != null ? navigationController.getStack().destroyById(id) : null;
  }

  public final void destroyAllStackItemsById (int id) {
    if (navigationController != null) {
      navigationController.getStack().destroyAllById(id);
    }
  }

  public final ViewController<?> destroyStackItemByIdExcludingLast (int id) {
    return navigationController != null ? navigationController.getStack().destroyByIdExcludingLast(id) : null;
  }

  public final ViewController<?> findLastStackItemById (int id) {
    return navigationController != null ? navigationController.getStack().findLastById(id) : null;
  }

  protected final void setStackLocked (boolean isLocked) {
    if (navigationController != null) {
      navigationController.getStack().setIsLocked(isLocked);
    }
  }

  public final boolean isStackLocked () {
    return navigationController != null && navigationController.getStack().isLocked();
  }

  protected final boolean isNavigationAnimating () {
    return navigationController != null && navigationController.isAnimating();
  }

  protected final void postNavigateBack () {
    // TODO proper way
    UI.post(this::navigateBack);
  }

  // Methods which have implementation in NavigationController

  public boolean navigateTo (ViewController<?> c) {
    return !isStackLocked() && navigationController != null && navigationController.navigateTo(c);
  }

  /*protected boolean navigateTo (Class<? extends ViewController> rawController) {
    return (flags & FLAG_ATTACHED_TO_NAVIGATION) != 0 && navigationController.navigateTo(rawController);
  }

  protected boolean navigateTo (Class<? extends ViewController> rawController, Object args) {
    return (flags & FLAG_ATTACHED_TO_NAVIGATION) != 0 && navigationController.navigateTo(rawController, args);
  }*/

  /*protected boolean navigateTo (ViewController<?> c, Object args) {
    return (flags & FLAG_ATTACHED_TO_NAVIGATION) != 0 && navigationController.navigateTo(c, args);
  }*/

  public boolean isAttachedToNavigationController () {
    return (flags & FLAG_ATTACHED_TO_NAVIGATION) != 0;
  }

  public boolean navigateBack () {
    return navigationController != null && navigationController.navigateBack();
  }

  protected void setController (ViewController<?> controller) {
    if (navigationController != null) {
      navigationController.setController(controller);
    }
  }

  protected void setControllerAnimated (ViewController<?> controller, boolean asForward, boolean saveFirst) {
    if (navigationController != null) {
      navigationController.setControllerAnimated(controller, asForward, saveFirst);
    }
  }

  public final void runOnUiThreadOptional (Runnable runnable) {
    runOnUiThreadOptional(runnable, null);
  }

  public final void runOnUiThreadOptional (Runnable runnable, @Nullable FutureBool condition) {
    if (runnable == null)
      return;
    runOnUiThread(() -> {
      if (!isDestroyed() && (condition == null || condition.getBoolValue())) {
        runnable.run();
      }
    });
  }

  protected final void runOnUiThread (@NonNull Runnable runnable) {
    UI.post(runnable);
  }

  protected final void removeCallbacks (@NonNull Runnable runnable) {
    UI.removePendingRunnable(runnable);
  }

  protected final void executeOnUiThread (@NonNull Runnable runnable) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      runnable.run();
    } else {
      runOnUiThread(runnable);
    }
  }

  protected final void executeOnUiThreadOptional (@NonNull Runnable runnable) {
    if (UI.inUiThread()) {
      if (!isDestroyed()) {
        runnable.run();
      }
    } else {
      runOnUiThreadOptional(runnable);
    }
  }

  protected final void runOnUiThread (@NonNull Runnable runnable, long delay) {
    UI.post(runnable, delay);
  }

  protected final void runOnBackgroundThread (@NonNull Runnable runnable) {
    Background.instance().post(runnable);
  }

  /*protected void setController (Class<? extends ViewController> rawController) {
    if ((flags & FLAG_ATTACHED_TO_NAVIGATION) != 0) {
      navigationController.setController(rawController);
    }
  }

  protected void setControllerAnimated (Class<? extends ViewController> rawController, boolean asForward, boolean saveFirst) {
    if ((flags & FLAG_ATTACHED_TO_NAVIGATION) != 0) {
      navigationController.setControllerAnimated(rawController, asForward, saveFirst);
    }
  }

  protected void setControllerAnimated (Class<? extends ViewController> rawController, Object args, boolean asForward, boolean saveFirst) {
    final NavigationController navigation = this.navigationController;
    if (navigation != null) { // Because we use this method only from background thread
      navigation.setControllerAnimated(rawController, args, asForward, saveFirst);
    }
  }*/

  public final void openSelectMode (int initialCount) {
    if (headerView != null) {
      headerView.openSelectMode(initialCount, true);
    }
  }

  public final void closeSelectMode () {
    if (headerView != null) {
      headerView.closeSelectMode();
    }
  }

  protected void onEnterSelectMode () {

  }

  public void onLeaveSelectMode () {

  }

  private View cachedLockFocusView;

  @CallSuper
  protected void onEnterSearchMode () {
    String text = getSearchStartQuery();
    clearSearchInput(text == null ? "" : text, true);
  }

  protected void onLeaveSearchMode () {

  }

  @CallSuper
  protected void updateSearchMode (boolean inSearch, boolean needUpdateKeyboard) {
    if (inSearch) {
      cachedLockFocusView = lockFocusView;
      lockFocusView = searchHeaderView.editView();
      if (needUpdateKeyboard) {
        Keyboard.show(searchHeaderView.editView());
      }
    } else {
      lockFocusView = cachedLockFocusView;
      if (needUpdateKeyboard) {
        Keyboard.hide(searchHeaderView.editView());
      }
      cachedLockFocusView = null;
    }
  }

  @CallSuper
  @Deprecated
  protected void updateSearchMode (boolean inSearch) {
    updateSearchMode(inSearch, true);
  }

  protected View getCustomFocusView () {
    return null;
  }

  protected final void updateCustomMode (boolean inCustom) {
    if (inCustom) {
      cachedLockFocusView = lockFocusView;
      lockFocusView = getCustomFocusView();
      if (lockFocusView == null) {
        Keyboard.hide(cachedLockFocusView);
      } else {
        Keyboard.show(lockFocusView);
      }
    } else {
      View oldFocusView = lockFocusView;
      lockFocusView = cachedLockFocusView;
      cachedLockFocusView = null;
      if (oldFocusView != null) {
        Keyboard.hide(oldFocusView);
      }
    }
  }

  protected final void openCustomMode () {
    if (headerView != null) {
      headerView.openCustomMode();
    }
  }

  protected final void closeCustomMode () {
    if (headerView != null) {
      headerView.closeCustomMode();
    }
  }

  protected final void openSearchMode () {
    if (headerView != null) {
      headerView.openSearchMode();
    }
  }

  protected boolean launchCustomHeaderTransformAnimator (boolean open, int transformMode, Animator.AnimatorListener listener) {
    return false;
  }

  protected void startHeaderTransformAnimator (final ValueAnimator animator, int mode, boolean open) {
    animator.start();
  }

  protected String getSearchStartQuery () {
    return null;
  }

  private float searchTransformFactor;

  protected float getSearchTransformFactor () {
    return searchTransformFactor;
  }

  protected final void setSearchTransformFactor (float factor, boolean isOpening) {
    if (this.searchTransformFactor != factor) {
      this.searchTransformFactor = factor;
      applySearchTransformFactor(factor, isOpening);
    }
  }

  protected Interpolator getSearchTransformInterpolator () {
    return AnimatorUtils.DECELERATE_INTERPOLATOR;
  }

  protected long getSearchTransformDuration () {
    return 200l;
  }

  @CallSuper
  protected void applySearchTransformFactor (float factor, boolean isOpening) {
    // override
  }

  public @ColorId int getRootColorId () {
    return ColorId.filling;
  }

  protected final void closeSearchMode (Runnable after) {
    if (headerView != null) {
      headerView.closeSearchMode(true, after);
    }
  }

  protected View getCustomModeHeaderView (HeaderView headerView) {
    return null;
  }

  protected final View getTransformHeaderView (HeaderView headerView) {
    if ((flags & FLAG_IN_SELECT_MODE) != 0) {
      return getCounterHeaderView(headerView);
    }
    if ((flags & FLAG_IN_SEARCH_MODE) != 0) {
      return getSearchHeaderView(headerView).view();
    }
    if ((flags & FLAG_IN_CUSTOM_MODE) != 0) {
      return getCustomModeHeaderView(headerView);
    }
    return null;
  }

  public boolean disableHeaderTransformation () {
    return false;
  }

  // Async controller opening

  private Runnable scheduledAnimation;
  private ArrayList<Runnable> animationReadyListeners;

  public boolean needAsynchronousAnimation () {
    return false;
  }

  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return fastAnimation ? 2000l : 500l;
  }

  public final void scheduleAnimation (final @NonNull Runnable scheduledAnimation, final long timeout) {
    this.scheduledAnimation = scheduledAnimation;
    if (timeout >= 0) {
      tdlib.ui().postDelayed(scheduledAnimation, timeout);
    }
  }

  protected final void resetScheduledAnimation () {
    this.scheduledAnimation = null;
  }

  public final void postOnAnimationReady (Runnable runnable) {
    if (animationReadyListeners == null) {
      animationReadyListeners = new ArrayList<>();
    }
    animationReadyListeners.add(runnable);
  }

  final void executeAnimationReadyListeners () {
    if (animationReadyListeners != null) {
      for (Runnable runnable : animationReadyListeners) {
        runnable.run();
      }
      animationReadyListeners.clear();
    }
  }

  public final Runnable getScheduledAnimation () {
    return scheduledAnimation;
  }

  private ArrayList<Runnable> animationExecuteListeners;

  public final void postOnAnimationExecute (Runnable runnable) {
    if (animationExecuteListeners == null) {
      animationExecuteListeners = new ArrayList<>();
    }
    animationExecuteListeners.add(runnable);
  }

  public final void executeScheduledAnimation () {
    if (scheduledAnimation != null) {
      scheduledAnimation.run();
      scheduledAnimation = null;
    }
    if (animationExecuteListeners != null) {
      for (Runnable runnable : animationExecuteListeners) {
        runnable.run();
      }
      animationExecuteListeners.clear();
    }
  }

  // Other

  public final void setLockFocusView (View view) {
    setLockFocusView(view, true);
  }

  public View getLockFocusView () {
    return lockFocusView;
  }

  public void setLockFocusView (View view, boolean showAlways) {
    if ((flags & FLAG_IN_SEARCH_MODE) != 0 || (flags & FLAG_IN_CUSTOM_MODE) != 0) {
      this.cachedLockFocusView = view;
    } else {
      this.lockFocusView = view;
    }
    if (showAlways) {
      flags |= FLAG_LOCK_ALWAYS;
    } else {
      flags &= ~FLAG_LOCK_ALWAYS;
    }
  }

  @CallSuper
  public void setArguments (T args) {
    this.args = args;
  }

  public final @Nullable T getArguments () {
    return args;
  }

  public final @NonNull T getArgumentsStrict () {
    if (args == null) {
      throw new NullPointerException(toString() + " (" + getClass().getSimpleName() + ") arguments are null");
    }
    return args;
  }

  protected int getBackButton () {
    return BackHeaderButton.TYPE_NONE;
  }

  /*protected final int getBackButtonColor () {
    return Theme.headerBackColor();
  }

  protected final @ColorId int getBackButtonColorId () {
    return getHeaderIconColorId(); // ColorId.headerIcon;
  }*/

  protected @DrawableRes int getBackButtonResource () {
    return ThemeDeprecated.headerSelector();
  }

  protected @IdRes int getMenuId () {
    return 0;
  }

  protected boolean allowMenuReuse () {
    return true;
  }

  protected @IdRes int getSelectMenuId () {
    return 0;
  }

  protected @IdRes int getSearchMenuId () {
    return 0;
  }

  protected boolean useLightSearchHeader () {
    return false;
  }

  protected int getSearchBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  protected int getSearchHeaderColorId () {
    return useGraySearchHeader() ? ColorId.filling : getHeaderColorId();
  }

  protected @ColorId int getSearchHeaderIconColorId () {
    return useGraySearchHeader() ? ColorId.icon : getHeaderIconColorId();
  }

  protected int getSearchTextColorId () {
    return useGraySearchHeader() ? ColorId.text : getHeaderTextColorId();
  }

  protected int getSearchBackButtonResource () {
    return getBackButtonResource();
  }

  protected @StringRes int getSearchHint () {
    return R.string.Search;
  }

  protected int getSelectHeaderColorId () {
    return ColorId.headerLightBackground;
  }

  protected @ColorId int getSelectTextColorId () {
    return ColorId.headerLightText;
  }

  protected void updateCustomButtonColorFactor (View view, int menuId, float colorFactor) {
    // override
  }

  protected void updateCustomMenu (int menuId, LinearLayout menu) {
    // override
  }

  // DEPRECATED

  protected final int getSelectHeaderIconColor () {
    return Theme.getColor(getSelectHeaderIconColorId());
  }

  protected int getHeaderIconColor () {
    return Theme.getColor(getHeaderIconColorId());
  }

  protected final int getSearchHeaderIconColor () {
    return Theme.getColor(getSearchHeaderIconColorId());
  }

  protected final int getSearchTextColor () {
    return Theme.getColor(getSearchTextColorId());
  }

  protected final int getSelectHeaderColor () {
    return Theme.getColor(getSelectHeaderColorId());
  }

  protected final int getSearchHeaderColor () {
    return Theme.getColor(getSearchHeaderColorId());
  }

  protected final int getHeaderColor () {
    return Theme.getColor(getHeaderColorId());
  }

  protected final int getHeaderTextColor () {
    return Theme.getColor(getHeaderTextColorId());
  }

  // FUTURE

  protected @ColorId int getHeaderIconColorId () {
    return ColorId.headerIcon;
  }

  protected @ColorId int getSelectHeaderIconColorId () {
    return ColorId.headerLightIcon;
  }

  /*protected int getSelectStatusBarColor () {
    return HeaderView.computeStatusBarColor(Theme.getColor(getSelectHeaderColorId()));
  }*/

  protected int getSelectBackButtonResource () {
    return getBackButtonResource();
  }

  protected boolean useHeaderTranslation () {
    return true;
  }

  protected boolean forceFadeMode () {
    return false;
  }

  protected boolean forceFastAnimation () {
    if (forceFadeModeOnce) {
      forceFadeModeOnce = false;
      return true;
    }
    return false;
  }

  public View getCustomHeaderCell () {
    return null;
  }

  // Select mode header

  private CounterHeaderView counterHeaderView;

  protected CounterHeaderView getCounterHeaderView (HeaderView view) {
    if (counterHeaderView == null) {
      counterHeaderView = HeaderView.genCounterHeader(context(), getSelectTextColorId());
      addThemeInvalidateListener(counterHeaderView);
    }
    return counterHeaderView;
  }

  protected final void initSelectedCount (int count) {
    if (counterHeaderView != null) {
      counterHeaderView.initCounter(count, false);
    }
  }

  public final void setSelectedCount (int count) {
    if (counterHeaderView != null && counterHeaderView.setCounter(count)) {
      onSelectedCountChanged(count);
    }
  }

  protected void onSelectedCountChanged (int count) {
    // children stuff
  }

  protected final int getSelectedCount () {
    return counterHeaderView != null ? counterHeaderView.getCounter() : 0;
  }

  // Search mode header

  private SearchEditTextDelegate searchHeaderView;

  protected void modifySearchHeaderView (HeaderEditText headerEditText) {
    // called only once
  }

  protected boolean useGraySearchHeader () {
    return false;
  }

  protected SearchEditTextDelegate genSearchHeader (HeaderView headerView) {
    HeaderEditText view = useGraySearchHeader() ? headerView.genGreySearchHeader(this) : headerView.genSearchHeader(useLightSearchHeader(), this);
    return new SearchEditTextDelegate() {
      @NonNull
      @Override
      public View view () {
        return view;
      }

      @NonNull
      @Override
      public HeaderEditText editView () {
        return view;
      }
    };
  }

  protected SearchEditTextDelegate getSearchHeaderView (HeaderView headerView) {
    if (searchHeaderView == null) {
      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize());

      if (Lang.rtl()) {
        params.rightMargin = Screen.dp(68f);
        params.leftMargin = Screen.dp(49f);
      } else {
        params.leftMargin = Screen.dp(68f);
        params.rightMargin = Screen.dp(49f);
      }

      searchHeaderView = genSearchHeader(headerView);
      searchHeaderView.editView().addTextChangedListener(new TextWatcher() {
        @Override
        public void beforeTextChanged (CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged (CharSequence s, int start, int before, int count) {
          if (inSearchMode()) {
            String input = s.toString();
            updateClearSearchButton(input.length() > 0, true);
            if (!lastSearchInput.equals(input)) {
              lastSearchInput = input;
              onSearchInputChanged(input);
            }
          }
        }

        @Override
        public void afterTextChanged (Editable s) {

        }
      });
      searchHeaderView.editView().setHint(Lang.getString(bindLocaleChanger(getSearchHint(), searchHeaderView.editView(), true, false)));
      searchHeaderView.view().setLayoutParams(params);

      modifySearchHeaderView(searchHeaderView.editView());
    }
    return searchHeaderView;
  }

  protected void setSearchInput (String text) {
    clearSearchInput(text, false);
  }

  public final String getLastSearchInput () {
    return lastSearchInput;
  }

  private String lastSearchInput = "";

  protected void clearSearchInput () {
    clearSearchInput("", false);
  }

  private void clearSearchInput (String text, boolean reset) {
    if (searchHeaderView != null) {
      if (reset) {
        lastSearchInput = text;
      }
      searchHeaderView.editView().setText(text);
      if (!text.isEmpty()) {
        searchHeaderView.editView().setSelection(text.length());
      }
      updateClearSearchButton(!text.isEmpty(), false);
    }
  }

  protected final void updateClearSearchButton (boolean visible, boolean animated) {
    if (getSearchMenuId() == R.id.menu_clear && headerView != null) {
      headerView.updateMenuClear(R.id.menu_clear, R.id.menu_btn_clear, visible, animated);
    }
  }

  protected final void setClearButtonSearchInProgress (boolean inProgress) {
    if (getSearchMenuId() == R.id.menu_clear && headerView != null) {
      headerView.updateMenuInProgress(R.id.menu_clear, R.id.menu_btn_clear, inProgress);
    }
  }

  protected void onSearchInputChanged (String query) {
    // override in children
  }

  private float lastPlayerFactor;

  public void dispatchInnerMargins (int left, int top, int right, int bottom) {
    // override in children
  }

  public View getViewForApplyingOffsets () {
    return null;
  }

  protected boolean shouldApplyPlayerMargin () {
    return true;
  }

  protected boolean applyPlayerOffset (float factor, float top) {
    if (lastPlayerFactor == factor) {
      return false;
    }

    View view = getViewForApplyingOffsets();

    if (view == null) {
      lastPlayerFactor = factor;
      return false;
    }

    view.setTranslationY(top);

    if (shouldApplyPlayerMargin()) {
      if (factor == 1f) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.bottomMargin != (int) top) {
          params.bottomMargin = (int) top;
          view.setLayoutParams(params);
        }
      } else if (lastPlayerFactor == 1f) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.bottomMargin != 0) {
          params.bottomMargin = 0;
          view.setLayoutParams(params);
        }
      }
    }

    lastPlayerFactor = factor;

    return true;
  }

  public final void drawTransform (Canvas c, int width, int height) {
    if (!transformFullyApplied && transformFactor > 0f) {
      drawTransform(c, transformFactor, width, height);
    }
  }

  protected void drawTransform (Canvas c, float transformFactor, int width, int height) {
    // override
  }

  protected void applyTransformChanges () { }
  protected void clearTransformChanges () { }
  protected void applyStaticTransform (float factor) { }
  protected void applyHeaderMenuTransform (LinearLayout menu, float factor) { }

  private float transformFactor;
  private boolean transformFullyApplied;

  protected final boolean isTransformed () {
    return transformFullyApplied;
  }

  protected final boolean isTransforming () {
    return transformFactor != 0f && !transformFullyApplied;
  }

  protected final float getTransformFactor () {
    return transformFactor;
  }

  protected final void setTransformFactor (float factor) {
    if (this.transformFactor != factor) {
      this.transformFactor = factor;
      applyStaticTransform(factor);
      setTransformFullyApplied(factor == 1f);
      if (headerView != null) {
        headerView.invalidate();
      }
    }
  }

  private void setTransformFullyApplied (boolean isApplied) {
    if (this.transformFullyApplied != isApplied) {
      this.transformFullyApplied = isApplied;
      if (isApplied) {
        applyTransformChanges();
      } else {
        clearTransformChanges();
      }
    }
  }

  public void updateSetting (int setting, int value) {
    // Must be implemented in ViewControllers
  }

  public final void setShareCustomHeaderView (boolean share) {
    if (share) {
      flags |= FLAG_SHARE_CUSTOM_HEADER;
    } else {
      flags &= ~FLAG_SHARE_CUSTOM_HEADER;
    }
  }

  protected final boolean shareCustomHeaderView () {
    return (flags & FLAG_SHARE_CUSTOM_HEADER) != 0 && !inTransformMode() && (transformFactor == 0f || allowTransformedHeaderSharing());
  }

  protected boolean allowTransformedHeaderSharing () {
    return false;
  }

  protected int getHeaderHeight () {
    return Size.getHeaderPortraitSize();
  }

  protected int getMaximumHeaderHeight () {
    return getHeaderHeight();
  }

  protected int getCustomHeaderHeight () {
    return Size.getHeaderPortraitSize();
  }

  protected int getTransformHeaderHeight () {
    return Size.getHeaderPortraitSize();
  }

  protected int getCustomFloatingButtonId () {
    return getFloatingButtonId();
  }

  protected @ColorId int getHeaderColorId () {
    return ColorId.headerBackground;
  }

  @Deprecated
  protected int getStatusBarColor () {
    return HeaderView.defaultStatusColor();
  }

  protected int getHeaderTextColorId () {
    return ColorId.headerText;
  }

  protected final int getNewStatusBarColor () {
    return HeaderView.DEFAULT_STATUS_COLOR;
  }

  protected int getFloatingButtonId () {
    return 0;
  }

  protected void onFloatingButtonPressed () {

  }

  protected boolean useDropShadow () {
    return true;
  }

  protected boolean useDropPlayer () {
    return true;
  }

  protected boolean usePopupMode () {
    return false;
  }

  protected boolean useBigHeaderButtons () {
    return usePopupMode();
  }

  protected int getPopupRestoreColor () {
    return 0;
  }

  protected long getStartDelay (boolean forward) {
    return 0l;
  }

  protected void applyCustomHeaderAnimations (float factor) {

  }

  public void setSwipeNavigationEnabled (boolean enabled) {
    if (!enabled) {
      flags |= FLAG_SWIPE_DISABLED;
    } else {
      flags &= ~FLAG_SWIPE_DISABLED;
    }
  }

  protected boolean swipeNavigationEnabled () {
    return (flags & FLAG_SWIPE_DISABLED) == 0;
  }

  public final boolean inTransformMode () {
    return (flags & FLAG_IN_SEARCH_MODE) != 0 || (flags & FLAG_IN_SELECT_MODE) != 0 || (flags & FLAG_IN_CUSTOM_MODE) != 0;
  }

  public final boolean inCustomMode () {
    return (flags & FLAG_IN_CUSTOM_MODE) != 0;
  }

  protected final void enterCustomMode () {
    flags |= FLAG_IN_CUSTOM_MODE;
  }

  protected final void leaveCustomMode () {
    flags &= ~FLAG_IN_CUSTOM_MODE;
  }

  public final boolean inSearchMode () {
    return (flags & FLAG_IN_SEARCH_MODE) != 0;
  }

  public boolean onBeforeLeaveSearchMode () {
    return true;
  }

  protected boolean needHideKeyboardOnTouchBackButton () {
    return true;
  }

  protected final void enterSearchMode () {
    flags |= FLAG_IN_SEARCH_MODE;
  }

  protected void onAfterLeaveSearchMode () { }

  protected final void leaveSearchMode () {
    flags &= ~FLAG_IN_SEARCH_MODE;
    onAfterLeaveSearchMode();
    setSearchTransformFactor(0f, false);
  }

  public final void preventLeavingSearchMode () {
    flags |= FLAG_PREVENT_LEAVING_SEARCH_MODE;
  }

  public final boolean inSelectMode () {
    return (flags & FLAG_IN_SELECT_MODE) != 0;
  }

  protected final void enterSelectMode () {
    flags |= FLAG_IN_SELECT_MODE;
  }

  protected final void leaveSelectMode () {
    flags &= ~FLAG_IN_SELECT_MODE;
  }

  public final void leaveTransformMode () {
    flags &= ~FLAG_IN_SELECT_MODE;
    flags &= ~FLAG_IN_SEARCH_MODE;
    flags &= ~FLAG_IN_CUSTOM_MODE;
  }

  protected boolean useDrawer () {
    return false;
  }

  // Header utils

  public final void showMore (int[] ids, String[] titles) {
    showMore(ids, titles, null, 0);
  }

  public final void showMore (int[] ids, String[] titles, int buttonIndex) {
    showMore(ids, titles, null, buttonIndex, false);
  }

  public final void showMore (int[] ids, String[] titles, int buttonIndex, boolean isLayered) {
    showMore(ids, titles, null, buttonIndex, isLayered);
  }

  public final void showMore (int[] ids, String[] titles, int[] icons) {
    showMore(ids, titles, icons, 0);
  }

  public final void showMore (int[] ids, String[] titles, int[] icons, int buttonIndex) {
    showMore(ids, titles, icons, buttonIndex, false);
  }

  public final void showMore (int[] ids, String[] titles, int[] icons, int buttonIndex, boolean isLayered) {
    if (isStackLocked() || context.isNavigationBusy()) {
      return;
    }
    if (headerView != null) {
      headerView.showMore(ids, titles, icons, buttonIndex, isLayered, this);
    }
  }

  // Permissions alerts

  @UiThread
  public final void processDeepLinkInfo (TdApi.DeepLinkInfo info) {
    if (info.needUpdateApplication) {
      openUpdateAlert(Td.isEmpty(info.text) ? null : TD.toDisplayCharSequence(info.text));
    } else {
      openAlert(R.string.AppName, TD.toDisplayCharSequence(info.text));
    }
  }

  @UiThread
  public final void openUpdateAlert (CharSequence text) {
    openAlert(R.string.AppUpdateRequiredTitle, text, Lang.getString(R.string.AppUpdateOk), (dialog, which) -> Intents.openSelfGooglePlay(), 0);
  }

  public final AlertDialog showAlert (AlertDialog.Builder b) {
    return modifyAlert(context.showAlert(b), 0);
  }

  public void openFeatureUnavailable (int stringRes) {
    AtomicReference<AlertDialog> atomicDialog = new AtomicReference<>();
    atomicDialog.set(openAlert(R.string.FeatureUnavailableSorry, Strings.buildMarkdown(this, Lang.getString(stringRes), (view, span, clickedText) -> {
      AlertDialog finalDialog = atomicDialog.get();
      if (finalDialog != null) {
        try {
          finalDialog.dismiss();
        } catch (Throwable ignored) { }
      }
      return false;
    })));
  }

  public AlertDialog openAlert (@StringRes int title, CharSequence message) {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(title));
    b.setMessage(message);
    b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
    return showAlert(b);
  }

  public void openAlert (@StringRes int title, CharSequence message, DialogInterface.OnClickListener okListener) {
    openAlert(title, message, okListener, true);
  }

  public void openAlert (@StringRes int title, CharSequence message, DialogInterface.OnClickListener okListener, boolean needCancel) {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(title));
    b.setMessage(message);
    b.setPositiveButton(Lang.getOK(), okListener);
    if (needCancel) {
      b.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
    } else {
      b.setCancelable(false);
    }
    showAlert(b);
  }

  public void openAlert (@StringRes int title, @StringRes int message) {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(title));
    b.setMessage(Lang.getString(message));
    b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
    showAlert(b);
  }

  public void openAlert (@StringRes int title, @StringRes int message, DialogInterface.OnClickListener okListener) {
    openAlert(title, message, Lang.getOK(), okListener);
  }

  public void openAlert (@StringRes int title, @StringRes int message, CharSequence positiveButton, DialogInterface.OnClickListener okListener) {
    openAlert(title, Lang.getString(message), positiveButton, okListener, 0);
  }

  public static final int ALERT_NO_CANCEL = 1;
  public static final int ALERT_NO_CANCELABLE = 1 << 1;
  public static final int ALERT_HAS_LINKS = 1 << 2;

  public AlertDialog openAlert (@StringRes int title, CharSequence message, CharSequence positiveButton, DialogInterface.OnClickListener okListener, int flags) {
    return openAlert(title, message, positiveButton, okListener, (dialog, which) -> dialog.dismiss(), flags);
  }

  public interface InputAlertCallback {
    boolean onAcceptInput (MaterialEditTextGroup inputView, String result);
  }

  public MaterialEditTextGroup openInputAlert (CharSequence title, CharSequence placeholder, @StringRes int doneRes, @StringRes int cancelRes, @Nullable CharSequence value, InputAlertCallback callback, boolean hideKeyboard) {
    return openInputAlert(title, placeholder, doneRes, cancelRes, value, null, callback, hideKeyboard, null, null);
  }

  public MaterialEditTextGroup openInputAlert (CharSequence title, CharSequence placeholder, @StringRes int doneRes, @StringRes int cancelRes, @Nullable CharSequence value, @Nullable String defaultValue, InputAlertCallback callback, boolean hideKeyboard, RunnableData<ViewGroup> layoutOverride, ThemeDelegate forcedTheme) {
    final MaterialEditTextGroup inputView = new MaterialEditTextGroup(context);
    inputView.setHint(placeholder);
    inputView.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
    if (!StringUtils.isEmpty(value)) {
      inputView.setText(value);
      inputView.getEditText().setSelection(0, value.length());
    }
    inputView.getEditText().addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged (CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged (CharSequence s, int start, int before, int count) {
        inputView.setInErrorState(false);
      }

      @Override
      public void afterTextChanged (Editable s) {

      }
    });

    LinearLayout ll = new LinearLayout(context) {
      private boolean first;
      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (first && getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
          first = false;
          Keyboard.show(inputView.getEditText());
        }
      }
    };
    ll.setOrientation(LinearLayout.VERTICAL);
    ll.setGravity(Gravity.CENTER_HORIZONTAL);
    int pad = Screen.dp(16f);
    ll.setPadding(pad, pad, pad, pad);

    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
    ll.addView(inputView, params);
    if (layoutOverride != null) {
      layoutOverride.runWithData(ll);
    }

    AlertDialog.Builder alert = new AlertDialog.Builder(context, Theme.dialogTheme())
      .setTitle(title)
      .setView(ll)
      .setPositiveButton(Lang.getString(doneRes), (dialog, which) -> {
        String result = inputView.getText().toString();
        if (callback.onAcceptInput(inputView, result)) {
          if (hideKeyboard) {
            Keyboard.hide(inputView.getEditText());
          }
        } else {
          inputView.setInErrorState(true);
        }
      })
      .setNegativeButton(Lang.getString(cancelRes), (dialog, which) -> {
        if (hideKeyboard) {
          Keyboard.hide(inputView.getEditText());
        }
        dialog.dismiss();
      });
    boolean needReset = !StringUtils.isEmpty(value) && !StringUtils.isEmpty(defaultValue) && !value.equals(defaultValue);
    if (needReset) {
      alert.setNeutralButton(Lang.getString(R.string.ValueReset), (dialog, which) -> {
        if (callback.onAcceptInput(inputView, defaultValue)) {
          if (hideKeyboard) {
            Keyboard.hide(inputView.getEditText());
          }
        } else {
          inputView.setInErrorState(true);
        }
      });
    }
    alert.setCancelable(false);
    AlertDialog dialog = showAlert(alert);
    if (dialog.getWindow() != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
      } else {
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
      }
    }
    Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    if (button != null)
      button.setOnClickListener(v -> {
        String result = inputView.getText().toString();
        if (callback.onAcceptInput(inputView, result)) {
          if (hideKeyboard) {
            Keyboard.hide(inputView.getEditText());
          }
          dialog.dismiss();
        } else {
          inputView.setInErrorState(true);
        }
      });
    if (needReset) {
      button = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
      if (button != null)
        button.setOnClickListener(v -> {
          if (callback.onAcceptInput(inputView, defaultValue)) {
            if (hideKeyboard) {
              Keyboard.hide(inputView.getEditText());
            }
            dialog.dismiss();
          } else {
            inputView.setInErrorState(true);
          }
        });
    }
    return inputView;
  }

  protected final AlertDialog modifyAlert (AlertDialog dialog, int flags) {
    if (dialog == null)
      return null;
    if ((flags & ALERT_HAS_LINKS) != 0) {
      View message = dialog.findViewById(android.R.id.message);
      if (message instanceof TextView) {
        ((TextView) message).setMovementMethod(LinkMovementMethod.getInstance());
      }
    }
    return dialog;
  }

  public AlertDialog openAlert (@StringRes int title, CharSequence message, CharSequence positiveButton, DialogInterface.OnClickListener okListener, DialogInterface.OnClickListener cancelListener, int flags) {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(title));
    b.setMessage(message);
    b.setPositiveButton(positiveButton, okListener);
    if ((flags & ALERT_NO_CANCEL) == 0) {
      b.setNegativeButton(Lang.getString(R.string.Cancel), cancelListener);
    }
    if ((flags & ALERT_NO_CANCELABLE) != 0) {
      b.setCancelable(false);
    }
    return modifyAlert(showAlert(b), flags);
  }

  public void openMissingPermissionAlert (@StringRes int message) {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.AppName));
    b.setMessage(Lang.getString(message));
    b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
    b.setNegativeButton(Lang.getString(R.string.Settings), (dialog, which) -> Intents.openPermissionSettings());
    showAlert(b);
  }

  protected final void openLiveLocationAlert (final long chatId, final RunnableInt callback) {
    SettingsWrapBuilder b = new SettingsWrapBuilder(R.id.btn_shareLiveLocation);
    b.setRawItems(new ListItem[] {
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_messageLive15Minutes, 0, Lang.plural(R.string.xMinutes, 15), R.id.btn_shareLiveLocation, true),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_messageLive1Hour, 0, Lang.plural(R.string.xHours, 1), R.id.btn_shareLiveLocation, false),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_messageLive8Hours, 0, Lang.plural(R.string.xHours, 8), R.id.btn_shareLiveLocation, false)
    });
    String text;
    if (ChatId.isUserChat(chatId)) {
      text = Lang.getString(R.string.LiveLocationAlertPrivate, tdlib.cache().userName(tdlib.chatUserId(chatId)));
    } else {
      text = Lang.getString(R.string.LiveLocationAlertGroup);
    }
    b.addHeaderItem(new ListItem(ListItem.TYPE_LIVE_LOCATION_PROMO));
    b.setAllowResize(false);
    b.addHeaderItem(text);
    b.setSaveStr(R.string.Share);
    b.setIntDelegate((id, result) -> {
      final int resId = result.get(id);
      final int time;
      if (resId == R.id.btn_messageLiveTemp) {
        time = 60;
      } else if (resId == R.id.btn_messageLive15Minutes) {
        time = 60 * 15;
      } else if (resId == R.id.btn_messageLive1Hour) {
        time = 60 * 60;
      } else if (resId == R.id.btn_messageLive8Hours) {
        time = 60 * 60 * 8;
      } else {
        return;
      }
      callback.runWithInt(time);
    });
    showSettings(b);
  }

  public void openMissingLocationPermissionAlert (boolean needBackground) {
    openMissingPermissionAlert(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Config.REQUEST_BACKGROUND_LOCATION && needBackground ? R.string.NoLocationAccessBackground : R.string.NoLocationAccess);
  }

  public void openMissingMicrophonePermissionAlert () {
    openMissingPermissionAlert(R.string.NoMicrophoneAccess);
  }

  public void openMissingStoragePermissionAlert () {
    openMissingPermissionAlert(R.string.NoStorageAccess);
  }

  public void openMissingCameraPermissionAlert () {
    openMissingPermissionAlert(R.string.NoCameraAccess);
  }

  public void openMissingGoogleMapsAlert () {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.AppName));
    b.setMessage(Lang.getString(R.string.NoGoogleMaps));
    b.setPositiveButton(Lang.getString(R.string.Install), (dialog, which) -> Intents.openGooglePlay("com.google.android.apps.maps"));
    b.setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
    showAlert(b);
  }

  public void openLinkAlert (final String url, @Nullable TdlibUi.UrlOpenParameters options) {
    tdlib.ui().openUrl(this, url, options == null ? new TdlibUi.UrlOpenParameters().requireOpenPrompt() : options.requireOpenPrompt());
  }

  public void openOkAlert (String title, CharSequence message) {
    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    if (title != null && !title.isEmpty()) {
      b.setTitle(title);
    }
    b.setMessage(message);
    b.setPositiveButton(Lang.getOK(), (dialog, which) -> dialog.dismiss());
    showAlert(b);
  }

  // Settings delegate

  public interface SettingsIntDelegate {
    void onApplySettings (@IdRes int id, SparseIntArray result);
  }

  public interface SettingsStringDelegate {
    void onApplySettings (@IdRes int id, SparseArrayCompat<String> result);
  }

  public interface OnSettingItemClick {
    void onSettingItemClick (View view, @IdRes int settingsId, ListItem item, TextView doneButton, SettingsAdapter settingsAdapter);
  }

  public final void showSettings (final @IdRes int id, ListItem[] rawItems, final SettingsIntDelegate delegate) {
    showSettings(new SettingsWrapBuilder(id).setRawItems(rawItems).setIntDelegate(delegate));
  }

  public final void showSettings (final @IdRes int id, ListItem[] rawItems, final SettingsIntDelegate delegate, boolean allowResize) {
    showSettings(new SettingsWrapBuilder(id).setRawItems(rawItems).setIntDelegate(delegate).setAllowResize(allowResize));
  }

  public final @Nullable SettingsWrap showSettings (final SettingsWrapBuilder b) {
    if (isStackLocked()) {
      Log.i("Ignoring showSettings because stack is locked");
      return null;
    }

    final ArrayList<ListItem> items = new ArrayList<>(b.rawItems.length * 2 + 1 + (b.headerItems != null && !b.headerItems.isEmpty() ? b.headerItems.size() + 1 : 0));
    boolean first = true;

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (b.headerItems != null && !b.headerItems.isEmpty()) {
      items.addAll(b.headerItems);
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    if (b.sizeValues != null) {
      items.add(new ListItem(ListItem.TYPE_SLIDER, b.sizeOptionId, 0, b.sizeStringRes, false).setSliderInfo(b.sizeValues, b.sizeValue));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    if (b.needSeparators) {
      for (ListItem item : b.rawItems) {
        if (first) {
          first = false;
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(item);
      }
    } else {
      items.ensureCapacity(items.size() + b.rawItems.length);
      Collections.addAll(items, b.rawItems);
    }

    final FrameLayoutFix popupView = new FrameLayoutFix(context);
    popupView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    final SettingsWrap settings = new SettingsWrap();

    final RecyclerView recyclerView = new RecyclerView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN && settings.adapter != null && settings.adapter.getItemCount() > 0) {
          int i = ((LinearLayoutManager) getLayoutManager()).findFirstVisibleItemPosition();
          if (i == 0) {
            View view = getLayoutManager().findViewByPosition(i);
            if (view != null && e.getY() < view.getTop()) {
              return false;
            }
          }
        }
        return super.onTouchEvent(e);
      }

      private int lastHeight;

      @Override
      protected void onMeasure (int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);

        final int height = getMeasuredHeight();
        if (lastHeight != 0 && lastHeight != height) {
          lastHeight = height;
          post(this::invalidateItemDecorations);
        } else {
          lastHeight = height;
        }
      }
    };
    settings.recyclerView = recyclerView;
    if (b.allowResize) {
      recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
        @Override
        public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
          int position = parent.getChildAdapterPosition(view);
          outRect.top = position == 0 ? Screen.currentHeight() / 2 + Screen.dp(12f) : 0;
        }
      });
    }
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    recyclerView.setItemAnimator(null);
    recyclerView.setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    final PopupLayout popupLayout = settings.window = new PopupLayout(context);
    popupLayout.setPopupHeightProvider(() -> {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      int firstPosition = manager.findFirstVisibleItemPosition();
      if (firstPosition == 0) {
        View view = manager.findViewByPosition(0);
        if (view != null) {
          return Math.min(Screen.currentHeight(), Math.min(popupView.getMeasuredHeight() - view.getTop(), settings.adapter.measureHeight(-1)) + Screen.dp(56f) + (Screen.needsKeyboardPadding(context) ? Screen.getNavigationBarFrameHeight() : 0));
        }
      }
      return Screen.currentHeight();
    });
    popupLayout.init(true);
    if (b.needRootInsets) {
      popupLayout.setNeedRootInsets();
    }
    popupLayout.addStatusBar();
    popupLayout.setDismissListener(b.dismissListener);

    final View.OnClickListener onClickListener = v -> {
      final int viewId = v.getId();
      if (viewId == R.id.btn_cancel) {
        if (b.onActionButtonClick == null || !b.onActionButtonClick.onActionButtonClick(settings, v, true)) {
          popupLayout.hideWindow(true);
        }
      } else if (viewId == R.id.btn_save) {
        if (b.onActionButtonClick != null && b.onActionButtonClick.onActionButtonClick(settings, v, false)) {
          return;
        }
        int type = settings.adapter.getCheckResultType();

        switch (type) {
          case SettingsAdapter.SETTINGS_RESULT_INTS:
          case SettingsAdapter.SETTINGS_RESULT_UNKNOWN: {
            if (b.intDelegate != null) {
              b.intDelegate.onApplySettings(b.id, settings.adapter.getCheckIntResults());
            }
            break;
          }
          case SettingsAdapter.SETTINGS_RESULT_STRING: {
            if (b.stringDelegate != null) {
              b.stringDelegate.onApplySettings(b.id, settings.adapter.getCheckStringResults());
            }
            break;
          }
        }

        popupLayout.hideWindow(true);
      } else {
        Object tag = v.getTag();
        if (!b.disableToggles) {
          settings.adapter.processToggle(v);
        }
        if (tag != null && tag instanceof ListItem && b.onSettingItemClick != null) {
          b.onSettingItemClick.onSettingItemClick(v, b.id, (ListItem) tag, settings.doneButton, settings.adapter);
        }
      }
    };
    settings.adapter = new SettingsAdapter(b.tdlibDelegate != null ? b.tdlibDelegate : this, onClickListener, this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getViewType()) {
          case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE: {
            view.setData(item.getStringValue());
            break;
          }
          case ListItem.TYPE_CHECKBOX_OPTION:
          case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
          case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
          case ListItem.TYPE_CHECKBOX_OPTION_REVERSE: {
            view.findCheckBox().setChecked(item.isSelected(), isUpdate);
            break;
          }
        }
        if (b.settingProcessor != null) {
          b.settingProcessor.setValuedSetting(item, view, isUpdate);
        }
      }

      @Override
      protected void setDrawerItem (ListItem item, DrawerItemView view, TimerView timerView, boolean isUpdate) {
        if (b.drawerProcessor != null) {
          b.drawerProcessor.setDrawerItem(item, view, timerView, isUpdate);
        }
      }
    };
    final int checkedIndex = settings.adapter.setItems(items, true);

    FrameLayoutFix footerView = null;
    if (!b.disableFooter) {
       footerView = new FrameLayoutFix(context) {
        @Override
        public boolean onTouchEvent (MotionEvent event) {
          super.onTouchEvent(event);
          return true;
        }
      };
      ViewSupport.setThemedBackground(footerView, ColorId.filling, this);
      footerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f), Gravity.BOTTOM));

      for (int i = 0; i < 2; i++) {
        TextView button = new NoScrollTextView(context);

        int colorId = i == 1 ? b.saveColorId : b.cancelColorId;
        button.setTextColor(Theme.getColor(colorId));
        addThemeTextColorListener(button, colorId);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        button.setOnClickListener(onClickListener);
        button.setBackgroundResource(R.drawable.bg_btn_header);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);

        CharSequence text;
        if (i == 0) {
          button.setId(R.id.btn_cancel);
          button.setText(text = b.cancelStr.toUpperCase());
          button.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(55f), (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM));
          settings.cancelButton = button;
        } else {
          button.setId(R.id.btn_save);
          button.setText(text = b.saveStr.toUpperCase());
          button.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(55f), (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM));
          settings.doneButton = button;
        }
        Views.updateMediumTypeface(button, text);

        Views.setClickable(button);
        footerView.addView(button);
      }
    }

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    params.bottomMargin = footerView != null ? Screen.dp(56f) : 0;

    recyclerView.setAdapter(settings.adapter);
    recyclerView.setLayoutParams(params);

    popupView.addView(recyclerView);
    if (footerView != null) {
      popupView.addView(footerView);
    }

    SeparatorView shadowView = null;

    if (footerView != null) {
      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f), Gravity.BOTTOM);
      params.bottomMargin = Screen.dp(56f);
      shadowView = SeparatorView.simpleSeparator(context, params, true);
      shadowView.setAlignBottom();
      addThemeInvalidateListener(shadowView);
      popupView.addView(shadowView);
    }

    int popupAdditionalHeight = 0;

    if (Screen.needsKeyboardPadding(context)) {
      popupAdditionalHeight = Screen.getNavigationBarFrameHeight();

      View dummyView = new View(context);
      dummyView.setBackgroundColor(Theme.getColor(ColorId.filling));
      addThemeBackgroundColorListener(dummyView, ColorId.filling);

      FrameLayoutFix.LayoutParams modifiedParams = (FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams();
      modifiedParams.bottomMargin += popupAdditionalHeight;
      recyclerView.setLayoutParams(modifiedParams);

      if (footerView != null) {
        modifiedParams = (FrameLayoutFix.LayoutParams) footerView.getLayoutParams();
        modifiedParams.bottomMargin += popupAdditionalHeight;
        footerView.setLayoutParams(modifiedParams);
      }

      if (shadowView != null) {
        modifiedParams = (FrameLayoutFix.LayoutParams) shadowView.getLayoutParams();
        modifiedParams.bottomMargin += popupAdditionalHeight;
        shadowView.setLayoutParams(modifiedParams);
      }

      modifiedParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, popupAdditionalHeight, Gravity.BOTTOM);
      dummyView.setLayoutParams(modifiedParams);

      modifiedParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f), Gravity.BOTTOM);
      modifiedParams.bottomMargin = popupAdditionalHeight;

      SeparatorView bottomShadowView = SeparatorView.simpleSeparator(context, modifiedParams, true);
      bottomShadowView.setAlignBottom();
      addThemeInvalidateListener(bottomShadowView);
      popupView.addView(bottomShadowView);

      popupView.addView(dummyView);
      popupLayout.setNeedFullScreen(true);
    }

    final int height = settings.adapter.measureHeight(-1);
    final int desiredHeight = height + (footerView != null ? Screen.dp(56f) : 0) + popupAdditionalHeight;
    final int popupHeight = Math.min(Screen.currentHeight(), desiredHeight);

    if (desiredHeight > Screen.currentActualHeight() && checkedIndex != -1) {
      int viewHeight = SettingHolder.measureHeightForType(items.get(checkedIndex).getViewType());
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(checkedIndex, (Screen.currentActualHeight() - Screen.dp(56f)) / 2 - viewHeight / 2);
    }
    popupLayout.addThemeListeners(this);
    popupLayout.showSimplePopupView(popupView, Math.min(Screen.currentHeight() / 2 + Screen.dp(56f), popupHeight));

    onCreatePopupLayout(popupLayout);
    return settings;
  }

  // Copy text

  public final void showCopyUrlOptions (final String url, final @Nullable TdlibUi.UrlOpenParameters options, final @Nullable FutureBool openCallback) {
    IntList ids = new IntList(3);
    StringList strings = new StringList(3);
    IntList icons = new IntList(3);

    ids.append(R.id.btn_openLink);
    strings.append(R.string.Open);
    icons.append(R.drawable.baseline_open_in_browser_24);

    ids.append(R.id.btn_copyLink);
    strings.append(R.string.CopyLink);
    icons.append(R.drawable.baseline_link_24);

    ids.append(R.id.btn_shareLink);
    strings.append(R.string.Share);
    icons.append(R.drawable.baseline_forward_24);

    final int[] shareState = {0};

    showOptions(url, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      if (id == R.id.btn_copyLink) {
        UI.copyText(url, R.string.CopiedLink);
      } else if (id == R.id.btn_shareLink) {
        if (shareState[0] == 0) {
          shareState[0] = 1;
          TD.shareLink(new TdlibContext(context, tdlib), url);
        }
      } else if (id == R.id.btn_openLink) {
        if (openCallback == null || !openCallback.getBoolValue()) {
          tdlib.ui().openUrl(ViewController.this, url, options);
        }
      }
      return true;
    });
  }

  // Options delegate

  public static final int OPTION_COLOR_NORMAL = 0x01;
  public static final int OPTION_COLOR_RED = 0x02;
  public static final int OPTION_COLOR_BLUE = 0x03;

  public static final float DISABLED_ALPHA = .7f;

  private OptionsLayout optionsWrap;
  private View.OnClickListener onOptionClick;

  public final void showCallOptions (final String phoneNumber, final long userId) {
    if (userId == 0) {
      Intents.openNumber(phoneNumber);
      return;
    }
    showOptions(new int[]{R.id.btn_phone_call, R.id.btn_telegram_call}, new String[]{Lang.getString(R.string.PhoneCall), Lang.getString(R.string.VoipInCallBranding)}, (itemView, id) -> {
      if (id == R.id.btn_phone_call) {
        Intents.openNumber(TD.getPhoneNumber(phoneNumber));
      } else if (id == R.id.btn_telegram_call) {
        tdlib.context().calls().makeCall(ViewController.this, userId, null);
      }
      return true;
    });
  }

  public final PopupLayout showOptions (CharSequence info, int[] ids, String[] titles) {
    return showOptions(info, ids, titles, null, null, null);
  }

  public final PopupLayout showOptions (CharSequence info, int[] ids, String[] titles, OptionDelegate delegate) {
    return showOptions(info, ids, titles, null, null, delegate);
  }

  public final PopupLayout showOptions (int[] ids, String[] titles, int[] colors) {
    return showOptions(null, ids, titles, colors, null, null);
  }

  public final PopupLayout showOptions (int[] ids, String[] titles) {
    return showOptions(null, ids, titles, null, null, null);
  }

  public final PopupLayout showConfirm (@Nullable CharSequence info, @Nullable String okString, @NonNull Runnable onConfirm) {
    return showConfirm(info, okString, R.drawable.baseline_check_circle_24, OPTION_COLOR_NORMAL, onConfirm);
  }

  public final PopupLayout showConfirm (@Nullable CharSequence info, @Nullable String okString, int okIcon, int okColor, @NonNull Runnable onConfirm) {
    return showOptions(info, new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {okString != null ? okString : Lang.getString(R.string.OK), Lang.getString(R.string.Cancel)}, new int[] {okColor, OPTION_COLOR_NORMAL}, new int[] {okIcon, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_done) {
        onConfirm.run();
      }
      return true;
    });
  }

  public final PopupLayout showOptions (int[] ids, String[] titles, OptionDelegate delegate) {
    return showOptions(null, ids, titles, null, null, delegate);
  }

  public final PopupLayout showOptions (int[] ids, String[] titles, int[] colors, OptionDelegate delegate) {
    return showOptions(null, ids, titles, colors, null, delegate);
  }

  public final PopupLayout showOptions (CharSequence info, int[] ids, String[] titles, int[] colors) {
    return showOptions(info, ids, titles, colors, null, null);
  }

  public final PopupLayout showOptions (CharSequence info, int[] ids, String[] titles, int[] colors, int[] icons) {
    return showOptions(info, ids, titles, colors, icons, null);
  }

  public final void showUnsavedChangesPromptBeforeLeaving (@Nullable Runnable onConfirm) {
    showUnsavedChangesPromptBeforeLeaving(null, Lang.getString(R.string.DiscardChanges), onConfirm);
  }

  public final void showUnsavedChangesPromptBeforeLeaving (@Nullable CharSequence info, @NonNull String discardText, @Nullable Runnable onConfirm) {
    showOptions(info, new int[]{R.id.btn_done, R.id.btn_cancel}, new String[]{discardText, Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_done) {
        if (onConfirm != null)
          onConfirm.run();
        navigateBack();
      }
      return true;
    });
  }

  public final PopupLayout showOptions (CharSequence info, int[] ids, String[] titles, int[] colors, int[] icons, final OptionDelegate delegate) {
    return showOptions(info, ids, titles, colors, icons, delegate, null);
  }

  public final PopupLayout showWarning (CharSequence info, RunnableBool callback) {
    return showOptions(info, new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.TdlibLogsWarningConfirm), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_warning_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      callback.runWithBool(id == R.id.btn_done);
      return true;
    });
  }

  public static class OptionItem {
    public final int id;
    public final CharSequence name;
    public final int color;
    public final int icon;

    public OptionItem (int id, CharSequence name, int color, int icon) {
      this.id = id;
      this.name = name;
      this.color = color;
      this.icon = icon;
    }

    public static class Builder {
      private int id;
      private CharSequence name;
      private int color = OPTION_COLOR_NORMAL;
      private int icon;

      public Builder () {
        this.id = id;
      }

      public Builder id (int id) {
        this.id = id;
        return this;
      }

      public Builder name (CharSequence name) {
        this.name = name;
        return this;
      }

      public Builder name (int resId) {
        return name(Lang.getString(resId));
      }

      public Builder color (int color) {
        this.color = color;
        return this;
      }

      public Builder icon (int icon) {
        this.icon = icon;
        return this;
      }

      public OptionItem build () {
        return new OptionItem(id, name, color, icon);
      }
    }
  }

  public static class Options {
    public final CharSequence info;
    public final OptionItem[] items;

    public Options (CharSequence info, OptionItem[] items) {
      this.info = info;
      this.items = items;
    }

    public static class Builder {
      private CharSequence info;
      private List<OptionItem> items = new ArrayList<>();

      public Builder () {
      }

      public Builder info (CharSequence info) {
        this.info = info;
        return this;
      }

      public Builder item (OptionItem item) {
        if (item != null) {
          items.add(item);
        }
        return this;
      }

      public Builder cancelItem () {
        return item(new OptionItem.Builder().id(R.id.btn_cancel).name(R.string.Cancel).icon(R.drawable.baseline_cancel_24).build());
      }

      public Options build () {
        return new Options(info, items.toArray(new OptionItem[0]));
      }
    }
  }

  public final PopupLayout showOptions (CharSequence info, int[] ids, String[] titles, int[] colors, int[] icons, final OptionDelegate delegate, final @Nullable ThemeDelegate forcedTheme) {
    return showOptions(getOptions(info, ids, titles, colors, icons), delegate, forcedTheme);
  }

  public final Options getOptions (CharSequence info, int[] ids, String[] titles, int[] colors, int[] icons) {
    OptionItem[] items = new OptionItem[ids.length];
    for (int i = 0; i < ids.length; i++) {
      items[i] = new OptionItem(ids != null ? ids[i] : i, titles[i], colors != null ? colors[i] : OPTION_COLOR_NORMAL, icons != null ? icons[i] : 0);
    }
    return new Options(info, items);
  }

  public final PopupLayout showOptions (Options options, final OptionDelegate delegate) {
    return showOptions(options, delegate, null);
  }

  public final PopupLayout showOptions (Options options, final OptionDelegate delegate, final @Nullable ThemeDelegate forcedTheme) {
    if (isStackLocked()) {
      Log.i("Ignoring options show because stack is locked");
      return null;
    }

    final PopupLayout popupLayout = new PopupLayout(context);
    int popupAdditionalHeight;

    popupLayout.setTag(this);
    popupLayout.init(true);

    if (delegate != null) {
      popupLayout.setDisableCancelOnTouchDown(delegate.disableCancelOnTouchdown());
    }

    OptionsLayout optionsWrap = new OptionsLayout(context(), this, forcedTheme);
    optionsWrap.setInfo(this, tdlib(), options.info, false);
    optionsWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

    if (Screen.needsKeyboardPadding(context)) {
      popupAdditionalHeight = Screen.getNavigationBarFrameHeight();
      optionsWrap.setPadding(0, 0, 0, popupAdditionalHeight);
      popupLayout.setNeedFullScreen(true);
    } else {
      popupAdditionalHeight = 0;
    }

    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleTopShadow(true);
    optionsWrap.addView(shadowView, 0);
    addThemeInvalidateListener(shadowView);

    // Item generation
    View.OnClickListener onClickListener;
    if (delegate != null) {
      onClickListener = v -> {
        if (delegate.onOptionItemPressed(v, v.getId())) {
          popupLayout.hideWindow(true);
        }
      };
    } else {
      onClickListener = v -> {
        ViewController<?> c = context.navigation().getCurrentStackItem();
        if (c instanceof OptionDelegate && ((OptionDelegate) c).onOptionItemPressed(v, v.getId())) {
          popupLayout.hideWindow(true);
        }
      };
    }
    int index = 0;
    for (OptionItem item : options.items) {
      TextView text = OptionsLayout.genOptionView(context, item.id, item.name, item.color, item.icon, onClickListener, getThemeListeners(), forcedTheme);
      RippleSupport.setTransparentSelector(text);
      if (forcedTheme != null)
        Theme.forceTheme(text, forcedTheme);
      text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54f)));
      if (delegate != null) {
        text.setTag(delegate.getTagForItem(index));
      }
      optionsWrap.addView(text);
      index++;
    }

    // Window

    popupLayout.showSimplePopupView(optionsWrap, shadowView.getLayoutParams().height + Screen.dp(54f) * options.items.length + optionsWrap.getTextHeight() + popupAdditionalHeight);
    onCreatePopupLayout(popupLayout);
    return popupLayout;
  }

  public interface PopUpBuilder {
    int onBuildPopUp (PopupLayout popupLayout, OptionsLayout optionsLayout);
  }

  protected final PopupLayout showPopup (CharSequence title, boolean isTitle, @NonNull PopUpBuilder popUpBuilder, @Nullable ThemeDelegate forcedTheme) {
    final PopupLayout popupLayout = new PopupLayout(context);
    popupLayout.setTag(this);
    popupLayout.init(true);

    int totalHeight = 0;

    OptionsLayout optionsWrap = new OptionsLayout(context(), this, forcedTheme);
    optionsWrap.setInfo(this, tdlib(), title, isTitle);
    optionsWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    totalHeight += optionsWrap.getTextHeight();

    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleTopShadow(true);
    optionsWrap.addView(shadowView, 0);
    addThemeInvalidateListener(shadowView);
    totalHeight += shadowView.getLayoutParams().height;

    totalHeight += popUpBuilder.onBuildPopUp(popupLayout, optionsWrap);

    if (Screen.needsKeyboardPadding(context)) {
      int additionalHeight = Screen.getNavigationBarFrameHeight();
      totalHeight += additionalHeight;
      optionsWrap.setPadding(0, 0, 0, additionalHeight);
      popupLayout.setNeedFullScreen(true);
    }

    popupLayout.showSimplePopupView(optionsWrap, totalHeight);
    onCreatePopupLayout(popupLayout);
    return popupLayout;
  }

  public final PopupLayout showText (CharSequence title, CharSequence text, TextEntity[] entities, @Nullable ThemeDelegate forcedTheme) {
    return showPopup(title, true, (popupLayout, optionsLayout) -> {
      CustomTextView textView = new CustomTextView(context, tdlib);
      textView.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), Screen.dp(16f));
      textView.setTextColorId(ColorId.text);
      textView.setText(text, entities, false);
      textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
      optionsLayout.addView(textView);
      return textView.getCurrentHeight(context.getControllerWidth(textView));
    }, forcedTheme);
  }


  public final PopupLayout showDateTimePicker (CharSequence title, @StringRes int todayRes, @StringRes int tomorrowRes, @StringRes int futureRes, final RunnableLong callback, final @Nullable ThemeDelegate forcedTheme) {
    return showDateTimePicker(tdlib, title, todayRes, tomorrowRes, futureRes, callback, forcedTheme);
  }

  public final PopupLayout showDateTimePicker (Tdlib tdlib, CharSequence title, @StringRes int todayRes, @StringRes int tomorrowRes, @StringRes int futureRes, final RunnableLong callback, final @Nullable ThemeDelegate forcedTheme) {
    return showPopup(title, true, (popupLayout, optionsWrap) -> {
      int contentHeight = 0;
      int pickerHeight = InfiniteRecyclerView.getItemHeight() * 5;

      LinearLayout datePickerWrap = new LinearLayout(context);
      datePickerWrap.setOrientation(LinearLayout.HORIZONTAL);
      datePickerWrap.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pickerHeight));
      ViewUtils.setBackground(datePickerWrap, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          Rect bounds = getBounds();
          int viewWidth = bounds.width();
          int viewHeight = bounds.height();

          int cy = viewHeight / 2;
          int h2 = InfiniteRecyclerView.getItemHeight() / 2;

          c.drawLine(0, cy - h2, viewWidth, cy - h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
          c.drawLine(0, cy + h2, viewWidth, cy + h2, Paints.strokeSeparatorPaint(forcedTheme != null ? forcedTheme.getColor(ColorId.separator) : Theme.separatorColor()));
        }

        @Override
        public void setAlpha (int alpha) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
      if (forcedTheme == null)
        addThemeInvalidateListener(datePickerWrap);

      final long nowMs = tdlib.currentTimeMillis();
      final Calendar c = DateUtils.calendarInstance(nowMs);

      final int startAddMinutes = 2;
      final int minAddMinutes = 1;

      int currentDay = c.get(Calendar.DAY_OF_YEAR);
      c.set(Calendar.SECOND, 0);
      c.set(Calendar.MILLISECOND, 0);
      c.add(Calendar.MINUTE, startAddMinutes);
      final int startDay = c.get(Calendar.DAY_OF_YEAR) != currentDay ? 1 : 0;
      final int startHour = c.get(Calendar.HOUR_OF_DAY);
      final int startMinute = c.get(Calendar.MINUTE);

      final TextView sendView = new TextView(context);
      final RunnableLong updateSendButton = targetMillis -> {
        String text;
        if (DateUtils.isToday(targetMillis, TimeUnit.MILLISECONDS)) {
          text = Lang.getString(todayRes, Lang.time(targetMillis, TimeUnit.MILLISECONDS));
        } else if (DateUtils.isTomorrow(targetMillis, TimeUnit.MILLISECONDS)) {
          text = Lang.getString(tomorrowRes, Lang.time(targetMillis, TimeUnit.MILLISECONDS));
        } else {
          text = Lang.getString(futureRes, Lang.getDate(targetMillis, TimeUnit.MILLISECONDS), Lang.time(targetMillis, TimeUnit.MILLISECONDS));
        }
        Views.setMediumText(sendView, text.toUpperCase());
      };

      int dayCount = 366;
      ArrayList<SimpleStringItem> days = new ArrayList<>(dayCount);
      for (int day = startDay; day < dayCount; day++) {
        c.setTimeInMillis(nowMs);
        c.add(Calendar.DAY_OF_MONTH, day);
        long startMillis = DateUtils.getStartOfDay(c);
        days.add(new SimpleStringItem(0,
                day == 0 ? Lang.getString(R.string.Today) :
                day == 1 ? Lang.getString(R.string.Tomorrow) :
                Lang.getDate(startMillis, TimeUnit.MILLISECONDS))
                .setArgs(c.get(Calendar.YEAR), c.get(Calendar.DAY_OF_YEAR)));
      }
      final InfiniteRecyclerView<SimpleStringItem> dayPicker = new InfiniteRecyclerView<>(context(), false);
      final AtomicReference<InfiniteRecyclerView<SimpleStringItem>> hourPickerFinal = new AtomicReference<>(), minutePickerFinal = new AtomicReference<>();
      FutureLong calculateDate = () -> {
        SimpleStringItem dayItem = dayPicker.getCurrentItem();
        SimpleStringItem hourItem = hourPickerFinal.get().getCurrentItem();
        SimpleStringItem minuteItem = minutePickerFinal.get().getCurrentItem();
        if (dayItem == null || hourItem == null || minuteItem == null)
          return 0;
        int year = (int) dayItem.getArg1();
        int dayOfYear = (int) dayItem.getArg2();
        int hour = (int) hourItem.getArg1();
        int minute = (int) minuteItem.getArg1();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.DAY_OF_YEAR, dayOfYear);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
      };
      InfiniteRecyclerView.ItemChangeListener<SimpleStringItem> listener = (v, index) -> {
        updateSendButton.runWithLong(calculateDate.getLongValue());
      };
      dayPicker.setNeedSeparators(false);
      dayPicker.setMinMaxProvider((v, index) -> {
        c.setTimeInMillis(tdlib.currentTimeMillis());
        c.add(Calendar.MINUTE, minAddMinutes);
        int year = c.get(Calendar.YEAR);
        int day = c.get(Calendar.DAY_OF_YEAR);
        for (int minIndex = 0; minIndex < days.size(); minIndex++) {
          if (days.get(minIndex).getArg1() == year && days.get(minIndex).getArg2() == day) {
            return Math.max(minIndex, index);
          }
        }
        return index;
      });
      dayPicker.setItemChangeListener((v, index) -> {
        long millis = calculateDate.getLongValue();
        if (millis < tdlib.currentTimeMillis()) {
          c.setTimeInMillis(tdlib.currentTimeMillis());
          c.add(Calendar.MINUTE, minAddMinutes);
          int hour = c.get(Calendar.HOUR_OF_DAY);
          int minute = c.get(Calendar.MINUTE);
          hourPickerFinal.get().setCurrentItem(hour);
          minutePickerFinal.get().setCurrentItem(minute);
        }
        listener.onCurrentIndexChanged(v, index);
      });
      dayPicker.setForcedTheme(forcedTheme);
      dayPicker.addThemeListeners(this);
      dayPicker.initWithItems(days, 0);
      dayPicker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2.5f));
      datePickerWrap.addView(dayPicker);

      ArrayList<SimpleStringItem> hours = new ArrayList<>();
      for (int hour = 0; hour < 24; hour++) {
        c.set(Calendar.HOUR_OF_DAY, hour);
        hours.add(new SimpleStringItem(0, Lang.hour(c.getTimeInMillis(), TimeUnit.MILLISECONDS)).setArg1(hour));
      }
      final InfiniteRecyclerView<SimpleStringItem> hourPicker = new InfiniteRecyclerView<>(context(), false);
      hourPickerFinal.set(hourPicker);
      hourPicker.setTrimItems(false);
      hourPicker.setMinMaxProvider((v, index) -> {
        int minDayIndex = dayPicker.getMinMaxProvider().getMinMax(v, 0);
        if (dayPicker.getCurrentIndex() == minDayIndex) {
          c.setTimeInMillis(tdlib.currentTimeMillis());
          c.add(Calendar.MINUTE, minAddMinutes);
          return Math.max(index, c.get(Calendar.HOUR_OF_DAY));
        }
        return index;
      });
      hourPicker.setNeedSeparators(false);
      hourPicker.setItemChangeListener(listener);
      hourPicker.setForcedTheme(forcedTheme);
      hourPicker.addThemeListeners(this);
      hourPicker.initWithItems(hours, startHour);
      hourPicker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
      datePickerWrap.addView(hourPicker);

      ArrayList<SimpleStringItem> minutes = new ArrayList<>();
      for (int minute = 0; minute < 60; minute++) {
        minutes.add(new SimpleStringItem(0, minute < 10 ? "0" + minute : Integer.toString(minute))
                .setArg1(minute));
      }
      final InfiniteRecyclerView<SimpleStringItem> minutePicker = new InfiniteRecyclerView<>(context(), false);
      minutePickerFinal.set(minutePicker);
      minutePicker.setNeedSeparators(false);
      minutePicker.setItemChangeListener(listener);
      minutePicker.setMinMaxProvider((v, index) -> {
        int minDayIndex = dayPicker.getMinMaxProvider().getMinMax(v, 0);
        int minHourIndex = hourPicker.getMinMaxProvider().getMinMax(v, 0);
        if (dayPicker.getCurrentIndex() == minDayIndex && hourPicker.getCurrentIndex() == minHourIndex) {
          c.setTimeInMillis(tdlib.currentTimeMillis());
          c.add(Calendar.MINUTE, minAddMinutes);
          return Math.max(index, c.get(Calendar.MINUTE));
        }
        return index;
      });
      minutePicker.setForcedTheme(forcedTheme);
      minutePicker.setTrimItems(false);
      minutePicker.addThemeListeners(this);
      minutePicker.initWithItems(minutes, startMinute);
      minutePicker.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
      datePickerWrap.addView(minutePicker);

      View emptyView = new View(context);
      emptyView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .5f));
      datePickerWrap.addView(emptyView);

      optionsWrap.addView(datePickerWrap);
      contentHeight += pickerHeight;

      Views.setClickable(sendView);
      sendView.setGravity(Gravity.CENTER);
      sendView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      sendView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f)));
      FillingDrawable drawable = ViewSupport.setThemedBackground(sendView, ColorId.fillingPositive, forcedTheme != null ? null : this);
      drawable.setForcedTheme(forcedTheme);
      if (forcedTheme != null) {
        sendView.setTextColor(forcedTheme.getColor(ColorId.fillingPositiveContent));
      } else {
        sendView.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
        addThemeTextColorListener(sendView, ColorId.fillingPositiveContent);
      }
      contentHeight += Screen.dp(56f);
      sendView.setOnClickListener(v -> {
        long millis = calculateDate.getLongValue();
        if (tdlib.currentTimeMillis() < millis) {
          callback.runWithLong(millis);
          popupLayout.hideWindow(true);
        }
      });
      listener.onCurrentIndexChanged(null, -1);
      optionsWrap.addView(sendView);
      return contentHeight;
    }, forcedTheme);
  }

  // Other

  @Override
  public final BaseActivity context () {
    return context;
  }

  @Override
  public final Tdlib tdlib () {
    return tdlib;
  }

  public final int tdlibId () {
    return tdlib != null ? tdlib.id() : TdlibAccount.NO_ID;
  }

  public final void openTdlibLogs (int testerLevel, Crash crashInfo) {
    showWarning(Lang.getMarkdownString(this, R.string.TdlibLogsWarning), proceed -> {
      if (proceed) {
        SettingsBugController c = new SettingsBugController(context, tdlib);
        c.setArguments(new SettingsBugController.Args(SettingsBugController.SECTION_TDLIB, crashInfo).setTesterLevel(testerLevel));
        navigateTo(c);
      }
    });
  }

  public final boolean isSameTdlib (@NonNull Tdlib tdlib) {
    return tdlibId() == tdlib.id(); // && this.tdlib.isDebug() == tdlib.isDebug();
  }

  public final boolean isSameAccount (@NonNull TdlibAccount account) {
    return tdlibId() == account.id; // && tdlib.isDebug() == account.debug;
  }

  public boolean isFocused () {
    return (flags & FLAG_FOCUSED) != 0;
  }

  public boolean isPaused () {
    return (flags & FLAG_PAUSED) != 0;
  }

  public boolean isDestroyed () {
    return (flags & FLAG_DESTROYED) != 0;
  }

  public boolean isIntercepted () {
    return false;
  }

  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return true;
  }

  public void dismissIntercept () { }

  public boolean getKeyboardState () {
    return (flags & FLAG_KEYBOARD_STATE) != 0;
  }

  protected final void preventHideKeyboardOnBlur () {
    this.flags |= FLAG_PREVENT_KEYBOARD_HIDE;
  }

  public final @Nullable View getWrapUnchecked () {
    return contentView;
  }

  @Override
  public final View getValue () {
    if (contentView == null) {
      contentView = onCreateView(context());
      contentView.setTag(this);
      if (tdlib != null) {
        tdlib.incrementUiReferenceCount();
      }
      subscribeToNeededUpdates();
    }
    return contentView;
  }

  public static @Nullable ViewController<?> findRoot (View view) {
    ViewController<?> result = null;
    while (view != null) {
      Object tag = view.getTag();
      if (tag instanceof ViewController<?>) {
        result = (ViewController<?>) tag;
      }
      ViewParent parent = view.getParent();
      if (parent instanceof View) {
        view = (View) parent;
      } else {
        break;
      }
    }
    return result;
  }

  public static @Nullable ViewController<?> findAncestor (View view) {
    while (view != null) {
      Object tag = view.getTag();
      if (tag instanceof ViewController<?>) {
        return (ViewController<?>) tag;
      }
      ViewParent parent = view.getParent();
      if (parent instanceof View) {
        view = (View) parent;
      } else {
        break;
      }
    }
    return null;
  }

  protected abstract View onCreateView (Context context);

  @CallSuper
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    return false;
  }

  @CallSuper
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    return false;
  }

  @CallSuper
  public void onActivityPause () {
    flags &= ~FLAG_KEYBOARD_SHOWN;
    flags |= FLAG_PAUSED;
  }

  @CallSuper
  public void onActivityResume () {
    if (lockFocusView != null && lockFocusView.isEnabled() && isPaused() && (flags & FLAG_KEYBOARD_SHOWN) == 0 && navigationController != null && !navigationController.isAnimating()) {
      if (((flags & FLAG_LOCK_ALWAYS) != 0 || (flags & FLAG_KEYBOARD_STATE) != 0) && !context.isPasscodeShowing() && !context.isWindowPopupShowing()) {
        flags |= FLAG_KEYBOARD_SHOWN;
        UI.showKeyboardDelayed(lockFocusView);
      } else {
        Keyboard.hide(lockFocusView);
      }
    }
    flags &= ~FLAG_PAUSED;
  }

  @CallSuper
  public final void onActivityPermissionResult (int code, boolean granted) { }

  @CallSuper
  public final void onActivityDestroy () {
    if (!isDestroyed()) {
      destroy();
    }
  }

  @CallSuper
  public void onConfigurationChanged (Configuration newConfig) {
    /*View customHeader = getCustomHeaderCell();
    if (customHeader != null && customHeader instanceof ComplexHeaderView) {
      ((ComplexHeaderView) customHeader).rebuildLayout();
    }*/
  }

  public final boolean inMultiWindowMode () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return context.isInMultiWindowMode();
    }
    return false;
  }

  @CallSuper
  public void onMultiWindowModeChanged (boolean inMultiWindowMode) { }

  @CallSuper
  public boolean onKeyboardStateChanged (boolean visible) {
    View currentPopup = context.getCurrentPopupWindow();
    if (currentPopup != null && !(currentPopup instanceof Keyboard.OnStateChangeListener)) {
      currentPopup = null;
    }

    if (visible) {
      if ((flags & FLAG_KEYBOARD_STATE) != 0) {
        return false;
      }
      if (currentPopup != null) {
        ((Keyboard.OnStateChangeListener) currentPopup).closeAdditionalKeyboards();
      }
      flags |= FLAG_KEYBOARD_STATE;
    } else {
      if ((flags & FLAG_KEYBOARD_STATE) == 0) {
        return false;
      }
      flags &= ~FLAG_KEYBOARD_STATE;
    }
    if (currentPopup != null) {
      ((Keyboard.OnStateChangeListener) currentPopup).onKeyboardStateChanged(visible);
    }
    if (!visible && lockFocusView != null && lockFocusView instanceof MaterialEditText/* && ((MaterialEditText) lockFocusView).getText().length() == 0*/) {
      final boolean prevFocusable = lockFocusView.isFocusable();
      final boolean prevFocusableInTouchMode = lockFocusView.isFocusableInTouchMode();
      lockFocusView.setFocusable(false);
      lockFocusView.setFocusableInTouchMode(false);
      lockFocusView.clearFocus();

      context.requestBlankFocus();

      lockFocusView.setFocusable(prevFocusable);
      lockFocusView.setFocusableInTouchMode(prevFocusableInTouchMode);
    }
    return true;
  }

  public void onPrepareToDismissPopup () {
    hideSoftwareKeyboard();
    // Called from PopupLayout
  }

  protected boolean needPreventiveKeyboardHide () {
    return false;
  }

  @CallSuper
  public void onPrepareToShow () {
    if (inSearchMode()) {
      if ((flags & FLAG_PREVENT_LEAVING_SEARCH_MODE) == 0 && allowLeavingSearchMode()) {
        // TODO move this hacky-driven state reset to onCleanAfterHide?
        if (headerView != null && navigationController != null && navigationController.getStack().getCurrent() == this && headerView.getCurrentTransformMode() == HeaderView.TRANSFORM_MODE_SEARCH) {
          // headerView.closeSearchMode(false, null);
          // Do nothing.
          // Reset state only if we are exiting the next screen to this one.
        } else {
          setSearchTransformFactor(0f, false);
          onLeaveSearchMode();
          leaveSearchMode();
        }
      }
    }
  }

  @CallSuper
  public void onCleanAfterHide () { }

  public interface AttachListener {
    void onAttachStateChanged (ViewController<?> context, NavigationController navigation, boolean isAttached);
  }

  private ReferenceList<AttachListener> attachListeners;

  public final boolean getAttachState () {
    return (flags & FLAG_ATTACH_STATE) != 0;
  }

  public final void addAttachStateListener (AttachListener listener) {
    if (attachListeners == null) {
      attachListeners = new ReferenceList<>();
    }
    attachListeners.add(listener);
  }

  public final void removeAttachStateListener (AttachListener listener) {
    if (attachListeners != null) {
      attachListeners.remove(listener);
    }
  }

  public final void onAttachStateChanged (NavigationController navigation, boolean isAttached) {
    boolean nowIsAttached = (this.flags & FLAG_ATTACH_STATE) != 0;
    if (nowIsAttached != isAttached) {
      this.flags = BitwiseUtils.setFlag(this.flags, FLAG_ATTACH_STATE, isAttached);
      if (attachListeners != null) {
        for (AttachListener listener : attachListeners) {
          listener.onAttachStateChanged(this, navigation, isAttached);
        }
      }
    }
  }

  protected boolean allowLeavingSearchMode () {
    return true;
  }

  public void onAfterShow () { }

  public void onRequestPermissionResult (int requestCode, boolean success) { }

  private @Nullable List<LocaleChanger> localeChangers;

  protected int bindLocaleChanger (LocaleChanger localeChanger) {
    if (localeChangers == null) {
      localeChangers = new ArrayList<>();
    }
    localeChangers.add(localeChanger);
    return localeChanger.getResource();
  }

  protected @StringRes int bindLocaleChanger (@StringRes int resource, TextView item, boolean isHint, boolean isMedium) {
    return bindLocaleChanger(new LocaleChanger(resource, item, isHint, isMedium));
  }

  /*@Deprecated
  @CallSuper
  public void onLocaleChange () {
    if (localeChangers != null) {
      for (LocaleChanger changer : localeChangers) {
        changer.onLocaleChange();
      }
    }
  }*/

  protected void onFocusStateChanged () { }

  public interface FocusStateListener {
    void onFocusStateChanged (ViewController<?> c, boolean isFocused);
  }

  private List<FocusStateListener> focusStateListeners;

  public final void addOneShotFocusListener (Runnable onFocus) {
    if (isFocused()) {
      onFocus.run();
      return;
    }
    addFocusListener(new FocusStateListener() {
      @Override
      public void onFocusStateChanged (ViewController<?> c, boolean isFocused) {
        if (isFocused) {
          onFocus.run();
          removeFocusListener(this);
        }
      }
    });

  }

  public final void addFocusListener (FocusStateListener listener) {
    if (focusStateListeners == null) {
      focusStateListeners = new ArrayList<>();
    }
    if (!focusStateListeners.contains(listener)) {
      focusStateListeners.add(listener);
    }
  }

  public final void removeFocusListener (FocusStateListener listener) {
    if (focusStateListeners != null) {
      focusStateListeners.remove(listener);
    }
  }

  private void notifyFocusChanged (boolean isFocused) {
    if (focusStateListeners != null) {
      final int size = focusStateListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        focusStateListeners.get(i).onFocusStateChanged(this, isFocused);
      }
    }
  }

  @CallSuper
  public void onFocus () {
    flags |= FLAG_FOCUSED;
    flags &= ~FLAG_PREVENT_LEAVING_SEARCH_MODE;
    if (lockFocusView != null && lockFocusView.isEnabled() && (flags & FLAG_KEYBOARD_SHOWN) == 0) {
      if ((flags & FLAG_LOCK_ALWAYS) != 0) {
        flags |= FLAG_KEYBOARD_SHOWN;
        Keyboard.show(lockFocusView);
        UI.showKeyboardDelayed(lockFocusView);
      }
    } else {
      getValue().requestFocus();
    }
    trackUserActivity();
    onFocusStateChanged();
    notifyFocusChanged(true);
    context.addKeyEventListener(this);
  }

  protected final void trackUserActivity () {
    Passcode.instance().trackUserActivity(false);
  }

  @CallSuper
  public void onBlur () {
    flags &= ~FLAG_FOCUSED;
    if (lockFocusView != null && lockFocusView.isEnabled() && ((flags & FLAG_IN_SEARCH_MODE) != 0 || (flags & FLAG_KEYBOARD_SHOWN) != 0 || (flags & FLAG_KEYBOARD_STATE) != 0)) {
      flags &= ~FLAG_KEYBOARD_SHOWN;
      if ((flags & FLAG_PREVENT_KEYBOARD_HIDE) != 0) {
        flags &= ~FLAG_PREVENT_KEYBOARD_HIDE;
      } else {
        Keyboard.hide(lockFocusView);
      }
    }
    onFocusStateChanged();
    notifyFocusChanged(false);
    context.removeKeyEventListener(this);
  }

  @CallSuper
  public void hideSoftwareKeyboard () {
    if (inSearchMode()) {
      Keyboard.hide(searchHeaderView.editView());
    }
    if (lockFocusView != null) {
      Keyboard.hide(lockFocusView);
    }
    // children
  }

  @Override
  public boolean onKeyDown (int keyCode, KeyEvent event) {
    // override in children
    return false;
  }

  @Override
  public boolean onKeyUp (int keyCode, KeyEvent event) {
    // override in children
    return false;
  }

  public boolean allowPopupInterruption () {
    return false;
  }

  public boolean closeSearchModeByBackPress (boolean fromTop) {
    return false;
  }

  public boolean onBackPressed (boolean fromTop) {
    return false;
  }

  public boolean passBackPressToActivity (boolean fromTop) {
    return false;
  }

  public boolean allowLayerTypeChanges () {
    return true;
  }

  public boolean preventRootInteractions () {
    return false;
  }

  public CharSequence getName () {
    return name;
  }

  public abstract int getId ();

  public final void setName (int string) {
    setName(Lang.getString(string));
  }

  public boolean passNameToHeader () {
    return false;
  }

  public final void setName (CharSequence name) {
    this.name = name;
    if (headerView != null && passNameToHeader()) {
      headerView.updateTextTitle(getId(), name);
    }
  }

  // int lastPaddingTop = -1;

  /*public void setPaddingTop (int top) {
    *//*if (top != lastPaddingTop) {
      lastPaddingTop = top;
      applyOffset(lastPaddingTop);
    }*//*
  }

  public void setClipToPadding (boolean clipToPadding) {
    applyClipOffset(clipToPadding);
  }

  public void applyOffset (int paddingTop) {
    getWrap().setPadding(0, paddingTop, 0, 0);
  }

  public void applyClipOffset (boolean clipOffset) {
    if (getWrap() instanceof ViewGroup) {
      ((ViewGroup) getWrap()).setClipToPadding(clipOffset);
    }
  }*/

  private ArrayList<Destroyable> destroyListeners;

  public final void addDestroyListener (Destroyable delegate) {
    if (delegate != null) {
      if (destroyListeners == null) {
        destroyListeners = new ArrayList<>();
      }
      destroyListeners.add(delegate);
    }
  }

  public final void removeDestroyListener (Destroyable delegate) {
    if (delegate != null && destroyListeners != null) {
      destroyListeners.remove(delegate);
    }
  }

  @Override
  public final void performDestroy () {
    destroy();
  }

  @CallSuper
  public void destroy () {
    if ((flags & FLAG_DESTROYED) == 0) {
      flags |= FLAG_DESTROYED;
      if (localeChangers != null) {
        localeChangers.clear();
      }
      if (contentView != null && tdlib != null) {
        tdlib.decrementUiReferenceCount();
      }
      unsubscribeFromControllerUpdates();
      View view = getCustomHeaderCell();
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      }
      resetScheduledAnimation();
      if (destroyListeners != null) {
        for (Destroyable destroyable : destroyListeners) {
          destroyable.performDestroy();
        }
      }
    } else {
      Log.bug("Controller is already destroyed: name: %s, class: %s", name, this.getClass().getName());
    }
  }

  // Chat context

  /**
   * @return true if current screen should be shown only when there is no authorization
   */
  public boolean isUnauthorized () {
    return false;
  }

  /**
   * Describes the chatId the current ViewController<?> instance belongs to
   * */
  public long getChatId () {
    return 0;
  }

  // Custom camera utils

  public static class CameraOpenOptions {
    public @Nullable View anchorView;
    public boolean noTrace;
    public boolean allowSystem = true;
    public boolean optionalMicrophone = false;
    public int mode;
    public boolean ignoreAnchor;
    public CameraController.ReadyListener readyListener;
    public CameraController.QrCodeListener qrCodeListener;
    public @StringRes int qrModeSubtitle;
    public boolean qrModeDebug;

    public CameraOpenOptions anchor (View anchorView) {
      this.anchorView = anchorView;
      return this;
    }

    public CameraOpenOptions readyListener (CameraController.ReadyListener readyListener) {
      this.readyListener = readyListener;
      return this;
    }

    public CameraOpenOptions qrCodeListener (CameraController.QrCodeListener qrCodeListener) {
      this.qrCodeListener = qrCodeListener;
      return this;
    }

    public CameraOpenOptions ignoreAnchor (boolean ignoreAnchor) {
      this.ignoreAnchor = ignoreAnchor;
      return this;
    }

    public CameraOpenOptions noTrace (boolean noTrace) {
      this.noTrace = noTrace;
      return this;
    }

    public CameraOpenOptions mode (int mode) {
      this.mode = mode;
      return this;
    }

    public CameraOpenOptions qrModeSubtitle (@StringRes int qrModeSubtitle) {
      this.qrModeSubtitle = qrModeSubtitle;
      return this;
    }

    public CameraOpenOptions allowSystem (boolean allowSystem) {
      this.allowSystem = allowSystem;
      return this;
    }

    public CameraOpenOptions optionalMicrophone (boolean optionalMicrophone) {
      this.optionalMicrophone = optionalMicrophone;
      return this;
    }

    public CameraOpenOptions qrModeDebug (boolean qrModeDebug) {
      this.qrModeDebug = qrModeDebug;
      return this;
    }
  }

  protected final void openInAppCamera () {
    openInAppCamera(new CameraOpenOptions());
  }

  public final void openInAppCamera (@NonNull CameraOpenOptions options) {
    if (options.allowSystem && Settings.instance().getCameraType() == Settings.CAMERA_TYPE_SYSTEM) {
      showOptions(null, new int[] {R.id.btn_takePhoto, R.id.btn_takeVideo}, new String[] {Lang.getString(R.string.TakePhoto), Lang.getString(R.string.TakeVideo)}, null, new int[] {R.drawable.baseline_camera_alt_24, R.drawable.baseline_videocam_24}, (itemView, id) -> {
        if (id == R.id.btn_takePhoto) {
          Intents.openCamera(context, options.noTrace, false);
        } else if (id == R.id.btn_takeVideo) {
          Intents.openCamera(context, options.noTrace, true);
        }
        return true;
      });
    } else {
      openCustomCamera(options.ignoreAnchor(getKeyboardState()));
    }
  }

  private boolean openCustomCamera (CameraOpenOptions options) {
    return context.openCameraByTap(options);
  }

  // Force touch

  public final void setInForceTouchMode (boolean inForceTouchMode) {
    if (isInForceTouchMode() != inForceTouchMode) {
      this.flags = BitwiseUtils.setFlag(this.flags, FLAG_IN_FORCE_TOUCH_MODE, inForceTouchMode);
      onForceTouchModeChanged(inForceTouchMode);
    }
  }

  public final boolean isInForceTouchMode () {
    return (flags & FLAG_IN_FORCE_TOUCH_MODE) != 0;
  }

  public final boolean wouldMaximizeFromPreview () {
    return (flags & FLAG_MAXIMIZING) != 0;
  }

  public final void maximizeFromPreviewIfNeeded (float y, float startY) {
    if ((flags & FLAG_MAXIMIZING) != 0 || boundForceTouchView == null || boundForceTouchView.isAnimatingReveal()) {
      return;
    }
    int startDistanceToList = boundForceTouchView.getDistanceToButtonsList(startY);
    float d = (y - startY + startDistanceToList);
    float maximizeFactor = /*hasInteractedWithContent() ||*/ d >= 0 ? 0f : MathUtils.clamp(-d / (float) Screen.dp(64f));
    if (maximizeFactor == 1f) {
      maximizeFromPreview();
    } else if (boundForceTouchView != null) {
      boundForceTouchView.setBeforeMaximizeFactor(maximizeFactor);
    }
  }

  public final void maximizeFromPreview () {
    if (isInForceTouchMode() && (flags & FLAG_MAXIMIZING) == 0) {
      flags |= FLAG_MAXIMIZING;
      UI.forceVibrate(getValue(), false);
      context.closeForceTouch();
    }
  }

  public boolean wouldHideKeyboardInForceTouchMode () {
    return true;
  }

  protected void onForceTouchModeChanged (boolean inForceTouchMode) {
    // Override
  }

  protected void onTranslationChanged (float newTranslationX) {
    // Override
  }

  protected void onCreatePopupLayout (PopupLayout popupLayout) {
    // Override
  }

  @Override
  public void onPrepareToExitForceTouch (ForceTouchView.ForceTouchContext context) {
    onBlur();
  }

  @Override
  public void onPrepareToEnterForceTouch (ForceTouchView.ForceTouchContext context) {
    onPrepareToShow();
  }

  @Override
  public void onCompletelyShownForceTouch (ForceTouchView.ForceTouchContext context) {
    onFocus();
  }

  @Override
  public void onDestroyForceTouch (ForceTouchView.ForceTouchContext context) {
    onCleanAfterHide();
    destroy();
  }

  private @Nullable ForceTouchView boundForceTouchView;

  public ForceTouchView forceTouchView () {
    return boundForceTouchView;
  }

  public final void setBoundForceTouchView (@NonNull ForceTouchView forceTouchView) {
    this.boundForceTouchView = forceTouchView;
  }

  // Disabling screenshot

  private List<FutureBool> disallowScreenshotReasons;

  public void addDisallowScreenshotReason (FutureBool reason) {
    if (disallowScreenshotReasons == null) {
      disallowScreenshotReasons = new ArrayList<>();
    }
    disallowScreenshotReasons.add(reason);
  }

  public boolean shouldDisallowScreenshots () {
    if (disallowScreenshotReasons != null) {
      for (FutureBool reason : disallowScreenshotReasons) {
        if (reason.getBoolValue()) {
          return true;
        }
      }
    }
    return false;
  }

  // Drag-n-Drop

  private View thumbnailContentView;

  public View onCreateThumbnailView (Context context) {
    throw new RuntimeException("Stub!");
  }

  public View getThumbnailWrap () {
    if (thumbnailContentView == null) {
      thumbnailContentView = onCreateThumbnailView(context());
    }
    return thumbnailContentView;
  }

  private boolean forceFadeModeOnce;

  public final void forceFastAnimationOnce () {
    forceFadeModeOnce = true;
  }


  public interface SearchEditTextDelegate {
    @NonNull View view();
    @NonNull HeaderEditText editView();
  }

}
