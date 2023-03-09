/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 23/04/2015 at 18:51
 */
package org.thunderdog.challegram.tool;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.component.chat.ReplyComponent;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGWebPage;
import org.thunderdog.challegram.player.RecordDurationView;
import org.thunderdog.challegram.widget.CheckView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import me.vkryl.core.reference.ReferenceList;

public class Screen {
  private static void reset () {
    // TODO some day refactor this ill-driven stuff
    Paints.reset();
    Icons.reset();
    TGMessage.reset();
    ChatView.reset();
    ReplyComponent.reset();
    CheckView.reset();
    RecordDurationView.resetSizes();
    TGWebPage.reset();
    SimplestCheckBox.reset();
    _refreshRate = 0f;
    __statusBarHeight = null;
  }

  private static float _lastDensity = -1f, _refreshRate = 0f;

  public static void checkRefreshRate () {
    if (_refreshRate != 0) {
      _refreshRate = 0;
      refreshRate();
    }
  }

  public static boolean checkDensity () {
    return setDensity(UI.getResources().getDisplayMetrics().density);
  }

  private static boolean setDensity (float density) {
    if (density != _lastDensity) {
      boolean changed = _lastDensity != -1f;
      _lastDensity = density;
      if (changed) {
        reset();
      }
      return changed;
    }
    return false;
  }

  public static float density () {
    checkDensity();
    return _lastDensity;
  }

  public static float refreshRate () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      BaseActivity activity = UI.getUiContext();
      if (activity != null) {
        return activity.getDisplay().getRefreshRate();
      }
    }
    if (_refreshRate != 0f) {
      return _refreshRate;
    }
    WindowManager windowManager = (WindowManager) UI.getContext().getSystemService(Context.WINDOW_SERVICE);
    if (windowManager != null) {
      Display display = windowManager.getDefaultDisplay();
      _refreshRate = display.getRefreshRate();
      return _refreshRate;
    }
    return 60.0f;
  }

  public static int dp (float size) {
    float f = dpf(size);
    return (int) ((f >= 0) ? (f + 0.5f) : (f - 0.5f));
  }

  public static int sp (float size) {
    DisplayMetrics metrics = UI.getResources().getDisplayMetrics();
    setDensity(metrics.density);
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, metrics);
  }

  public static float dpf (float size) {
    DisplayMetrics metrics = UI.getResources().getDisplayMetrics();
    setDensity(metrics.density);
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, metrics); // size * density() + .5f;
  }

  public static int dp (float size, float maxDensity) {
    float density = density();
    if (density <= maxDensity) {
      return dp(size);
    } else {
      float f = size * Math.min(density, maxDensity);
      return (int) ((f >= 0) ? (f + 0.5f) : (f - 0.5f));
    }
  }

  public static float px (float size) {
    return (size / density() - .5f);
  }

  public static int currentWidth () {
    return UI.getContext().getResources().getDisplayMetrics().widthPixels;
  }

  public static int separatorSize () {
    return Math.max(1, Screen.dp(.5f));
  }

  public static int currentHeight () {
    BaseActivity context = UI.getUiContext();
    if (context != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        if (context.isInMultiWindowMode()) {
          return context.getWindow().getDecorView().getMeasuredHeight();
        }
      }
      return context.getContentView().getMeasuredHeight();
    }
    return UI.getContext().getResources().getDisplayMetrics().heightPixels;
  }

  public static int currentActualHeight () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || true) {
      return UI.getContext().getResources().getDisplayMetrics().heightPixels - getStatusBarHeight();
    } else {
      return UI.getContext().getResources().getDisplayMetrics().heightPixels;
    }
  }

  public static float currentAspectRatio () {
    float width = currentWidth();
    float height = currentHeight();
    return Math.max(width, height) / Math.min(width, height);
  }

  public static int widestSide () {
    DisplayMetrics m = UI.getContext().getResources().getDisplayMetrics();
    return Math.max(m.widthPixels, m.heightPixels);
  }

  public static float widestSideDp () {
    return (float) widestSide() / Screen.density();
  }

  public static int widestActualSide () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return smallestSide();
    }
    DisplayMetrics m = UI.getContext().getResources().getDisplayMetrics();
    int status = getStatusBarHeight();
    return Math.max(m.widthPixels, m.heightPixels - status);
  }

  public static int smallestSide () {
    DisplayMetrics m = UI.getContext().getResources().getDisplayMetrics();
    return Math.min(m.widthPixels, m.heightPixels);
  }

  public static int smallestActualSide () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return smallestSide();
    }
    DisplayMetrics m = UI.getContext().getResources().getDisplayMetrics();
    int status = getStatusBarHeight();
    return Math.min(m.widthPixels, m.heightPixels - status);
  }

  public static int calculateLoadingItems (int rowSize, int minimum) {
    return (int) Math.ceil(Math.max((float) minimum, (float) Screen.widestSide() / (float) rowSize + 1));
  }

  public static int calculateLoadingItems (int rowSize, int minimum, int targetHeight) {
    return targetHeight <= 0 ? minimum : (int) Math.ceil(Math.max((float) minimum, (float) targetHeight / (float) rowSize + 1));
  }

  private static Point point;

  /*public static int getDisplayWidth () {
    if (UI.getUiContext() == null)
      return 0;

    Display display;

    display = UI.getUiContext().getWindowManager().getDefaultDisplay();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
      if (point == null)
        point = new Point();
      display.getSize(point);
      return point.x;
    } else {
      //noinspection deprecation
      return display.getWidth();
    }
  }*/

  public static int getDisplayHeight () {
    final BaseActivity context = UI.getUiContext();
    if (context == null) {
      return 0;
    }

    Display display = context.getWindowManager().getDefaultDisplay();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
      if (point == null)
        point = new Point();
      display.getSize(point);
      return point.y;
    } else {
      //noinspection deprecation
      return display.getHeight();
    }
  }

  public static int getOrientation () {
    return UI.getResources().getConfiguration().orientation;
  }

  private static int navigationBarHeight;

  public static int getNavigationBarHeight () {
    if (navigationBarHeight != 0) {
      return navigationBarHeight;
    }
    int resourceId = UI.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
    if (resourceId > 0) {
      navigationBarHeight = UI.getResources().getDimensionPixelSize(resourceId);
    }
    return navigationBarHeight;
  }

  private static int navigationBarFrameHeight;

  public static int getNavigationBarFrameHeight () {
    if (navigationBarFrameHeight != 0) {
      return navigationBarFrameHeight;
    }
    int resourceId = UI.getResources().getIdentifier("navigation_bar_frame_height", "dimen", "android");
    if (resourceId > 0) {
      navigationBarFrameHeight = UI.getResources().getDimensionPixelSize(resourceId);
    }
    return navigationBarFrameHeight;
  }

  public static int getNavigationBarFrameDifference () {
    return Screen.getNavigationBarFrameHeight() - Screen.getNavigationBarHeight();
  }

  public static boolean needsKeyboardPadding (BaseActivity context) {
    return context.isKeyboardVisible() && isGesturalNavigationEnabled() && getNavigationBarHeight() > 0;
  }

  public static boolean isGesturalNavigationEnabled () {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return false;
    }

    try {
      int resourceId = UI.getResources().getIdentifier("config_navBarInteractionMode", "integer", "android");
      if (resourceId > 0) {
        return UI.getResources().getInteger(resourceId) == 2; // 2 is gestural by AOSP docs, SO says some Samsung devices can have values like 17694897, needs further investigation
      }
    } catch (android.content.res.Resources.NotFoundException ignored) {}

    return false;
  }

  private static Integer __statusBarHeight;

  public static void setStatusBarHeight (int height) {
    if (__statusBarHeight == null || __statusBarHeight != height) {
      __statusBarHeight = height;
      if (listeners != null) {
        for (StatusBarHeightChangeListener listener : listeners) {
          listener.onStatusBarHeightChanged(height);
        }
      }
    }
  }

  public interface StatusBarHeightChangeListener {
    void onStatusBarHeightChanged (int newHeight);
  }

  private static ReferenceList<StatusBarHeightChangeListener> listeners;

  public static void addStatusBarHeightListener (StatusBarHeightChangeListener listener) {
    if (listeners == null)
      listeners = new ReferenceList<>();
    listeners.add(listener);
  }

  public static void removeStatusBarHeightListener (StatusBarHeightChangeListener listener) {
    if (listeners != null)
      listeners.remove(listener);
  }

  public static int getStatusBarHeight () {
    if (__statusBarHeight != null) {
      return __statusBarHeight;
    }
    int resourceId = UI.getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      return __statusBarHeight = UI.getResources().getDimensionPixelSize(resourceId);
    } else {
      return 0;
    }
  }

  public static float getTouchSlop () {
    return ViewConfiguration.get(UI.getContext()).getScaledTouchSlop();
  }
  public static float getTouchSlopBig () {
    return getTouchSlop() * 1.89f;
  }
  public static float getTouchSlopY () {
    return density() >= 2f ? getTouchSlopBig() : getTouchSlop();
  }
}
