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
 * File created on 06/12/2016
 */
package org.thunderdog.challegram.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Passcode;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.CancellableRunnable;

public class RootFrameLayout extends FrameLayoutFix {
  private Object lastInsets;

  private boolean ignoreBottom, ignoreSystemNavigationBar, ignoreAll;
  private final ViewTreeObserver.OnPreDrawListener onPreDrawListener = () -> false;

  private Keyboard.OnStateChangeListener keyboardListener;

  public RootFrameLayout (Context context) {
    super(context);
  }

  private static int getBottomInset (Object insets) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION && insets != null) {
      return ((android.view.WindowInsets) insets).getSystemWindowInsetBottom();
    }
    return 0;
  }

  public static int getRightInset (Object insets) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION && insets != null) {
      return ((android.view.WindowInsets) insets).getSystemWindowInsetRight();
    }
    return 0;
  }

  public static int getLeftInset (Object insets) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION && insets != null) {
      return ((android.view.WindowInsets) insets).getSystemWindowInsetLeft();
    }
    return 0;
  }

  public void setIgnoreSystemNavigationBar (boolean ignoreSystemNavigationBar) {
    this.ignoreSystemNavigationBar = ignoreSystemNavigationBar;
  }

  public void setIgnoreAllInsets (boolean ignoreAll) {
    this.ignoreAll = ignoreAll;
  }

  public void init (final boolean ignoreBottom) {
    this.ignoreBottom = ignoreBottom;

    if (Config.USE_FULLSCREEN_NAVIGATION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      UI.setFullscreenIfNeeded(this);
      setOnApplyWindowInsetsListener((v, insets) -> {
        processWindowInsets(insets);
        return insets.consumeSystemWindowInsets();
      });
    }
  }

  private boolean keyboardVisible;
  private CancellableRunnable lastAction;

  private void setKeyboardVisible (final boolean isVisible) {
    if (this.keyboardVisible != isVisible) {
      this.keyboardVisible = isVisible;
      if (lastAction != null) {
        lastAction.cancel();
        lastAction = null;
      }
      if (keyboardListener != null) {
        getViewTreeObserver().removeOnPreDrawListener(onPreDrawListener);
        getViewTreeObserver().addOnPreDrawListener(onPreDrawListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          keyboardListener.onKeyboardStateChanged(isVisible);
          UI.post(lastAction = new CancellableRunnable() {
            @Override
            public void act () {
              getViewTreeObserver().removeOnPreDrawListener(onPreDrawListener);
              invalidate();
            }
          }.removeOnCancel(UI.getAppHandler()), 20);
        } else {
          UI.post(lastAction = new CancellableRunnable() {
            @Override
            public void act () {
              keyboardListener.onKeyboardStateChanged(isVisible);
              getViewTreeObserver().removeOnPreDrawListener(onPreDrawListener);
              invalidate();
            }
          }.removeOnCancel(UI.getAppHandler()), 2);
        }
        /*if (true || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {

        } else {
          keyboardListener.onKeyboardStateChanged(isVisible);
        }*/
        /*if (isVisible) {
          UI.postDelayed(new Runnable() {
            @Override
            public void run () {
              keyboardListener.closeAdditionalKeyboards();
            }
          }, 20);
        }*/
      }
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
      Passcode.instance().trackUserActivity(false);
    }
    return super.onInterceptTouchEvent(ev);
  }

  public void setKeyboardListener (Keyboard.OnStateChangeListener keyboardListener) {
    this.keyboardListener = keyboardListener;
  }

  private int lastBottomDiff;
  private long lastBottomDiffTime;

  private void processBottomDiff (int bottomDiff, int bottomTotal) {
    if (Math.abs(bottomDiff) > Math.max(Screen.getNavigationBarHeight(), Math.max(Screen.dp(116f), 128))) {
      if (!ignoreBottom && !ignoreAll) {
        if (Device.NEED_ADD_KEYBOARD_SIZE) {
          if (bottomDiff < 0) {
            if (lastBottomDiffTime != 0 && Math.signum(lastBottomDiff) == Math.signum(bottomDiff) && SystemClock.uptimeMillis() - lastBottomDiffTime < 250) {
              bottomDiff += lastBottomDiff;
            }
            Keyboard.processSize(-bottomDiff);
          }
        } else {
          Keyboard.processSize(Math.abs(bottomDiff));
        }
      }
      setKeyboardVisible(bottomDiff < 0);
      lastBottomDiffTime = 0;
    } else {
      lastBottomDiffTime = SystemClock.uptimeMillis();
      lastBottomDiff = bottomDiff;
    }
  }

  public void processWindowInsets (Object insetsRaw) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION) {
      android.view.WindowInsets insets = (android.view.WindowInsets) insetsRaw;

      if (this instanceof BaseRootLayout) {
        Screen.setStatusBarHeight(insets.getSystemWindowInsetTop());
      }

      boolean changed;
      if (lastInsets != null) {
        int topDiff, bottomDiff;

        int bottom = insets.getSystemWindowInsetBottom();
        topDiff = Math.abs(((android.view.WindowInsets) lastInsets).getSystemWindowInsetTop() - insets.getSystemWindowInsetTop());
        bottomDiff = ignoreBottom ? 0 : getBottomInset(lastInsets) - bottom;

        int rightDiff = getRightInset(lastInsets) - getRightInset(insets);
        int leftDiff = getLeftInset(lastInsets) - getLeftInset(insets);

        if (rightDiff == 0 && leftDiff == 0 && bottomDiff != 0) {
          processBottomDiff(bottomDiff, bottom);
        }
        changed = topDiff != 0 || bottomDiff != 0 || rightDiff != 0 || leftDiff != 0;
      } else {
        changed = true;
      }

      lastInsets = insets;

      if (changed) {
        requestLayout();
      }
    }
  }

  private boolean shouldIgnoreBottomMargin (View child, int bottom) {
    return ignoreSystemNavigationBar && bottom <= Screen.getNavigationBarHeight(); // || (child != null && UI.getContext(getContext()).isCameraChild(child));
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void dispatchChildInsets (View child, Object insets, int drawerGravity) {
    android.view.WindowInsets wi = (android.view.WindowInsets) insets;
    if (drawerGravity == Gravity.LEFT) {
      wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
    } else if (drawerGravity == Gravity.RIGHT) {
      wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
    }
    int left = wi.getSystemWindowInsetLeft();
    int top = wi.getSystemWindowInsetTop();
    int right = wi.getSystemWindowInsetRight();
    int bottom =  wi.getSystemWindowInsetBottom();
    if (UI.getContext(getContext()).dispatchCameraMargins(child, left, top, right, bottom)) {
      wi.replaceSystemWindowInsets(0, 0, 0, 0);
    } else {
      if (ignoreAll) {
        wi.replaceSystemWindowInsets(0, 0, 0, 0);
      } else if (ignoreBottom || (shouldIgnoreBottomMargin(child, bottom))) {
        wi.replaceSystemWindowInsets(left, top, right, 0);
      }
      ViewController<?> c = ViewController.findAncestor(child);
      if (c != null) {
        c.dispatchInnerMargins(left, top, right, bottom);
      }
    }
    child.dispatchApplyWindowInsets(wi);
  }

  private boolean ignoreHorizontal;

  public void setIgnoreHorizontal () {
    ignoreHorizontal = true;
  }

  public void setIgnoreBottom (boolean ignoreBottom) {
    this.ignoreBottom = ignoreBottom;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void applyMarginInsets (View child, MarginLayoutParams lp, Object insets, int drawerGravity, boolean topOnly) {
    android.view.WindowInsets wi = (android.view.WindowInsets) insets;
    if (drawerGravity == Gravity.LEFT) {
      wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(), 0, wi.getSystemWindowInsetBottom());
    } else if (drawerGravity == Gravity.RIGHT) {
      wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
    }
    int left = wi.getSystemWindowInsetLeft();
    int top = wi.getSystemWindowInsetTop();
    int right = wi.getSystemWindowInsetRight();
    int bottom = wi.getSystemWindowInsetBottom();

    lp.leftMargin = ignoreAll || ignoreHorizontal ? 0 : left;
    lp.topMargin = ignoreAll || topOnly ? 0 : top;
    lp.rightMargin = ignoreAll || ignoreHorizontal ? 0 : right;
    lp.bottomMargin = ignoreAll || ignoreBottom || shouldIgnoreBottomMargin(child, bottom) ? 0 : bottom;
    if (UI.getContext(getContext()).dispatchCameraMargins(child, lp.leftMargin, lp.topMargin, lp.rightMargin, bottom)) {
      lp.leftMargin = lp.topMargin = lp.rightMargin = lp.bottomMargin = 0;
    } else {
      ViewController<?> c = ViewController.findAncestor(child);
      if (c != null) {
        c.dispatchInnerMargins(left, top, right, bottom);
      }
    }
  }

  private int previousHeight;
  private int previousWidth;

  protected View getMeasureTarget () {
    return this;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && lastInsets != null) {
      for (int i = 0; i < getChildCount(); i++) {
        View view = getChildAt(i);
        if (view != null) {
          LayoutParams params = (LayoutParams) view.getLayoutParams();
          if (view.getFitsSystemWindows()) {
            dispatchChildInsets(view, lastInsets, 0);
          } else {
            applyMarginInsets(view, params, lastInsets, params.gravity, true);
          }
        }
      }
    }

    try {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } catch (Throwable t) {
      Log.critical(t);
      throw t;
    }

    checkBottomDiff();
  }

  private void checkBottomDiff () {
    final View target = getMeasureTarget();

    if (target != null) {
      int currentWidth = target.getMeasuredWidth();
      int currentHeight = target.getMeasuredHeight();
      if (previousHeight != currentHeight && previousHeight != 0 && currentWidth == previousWidth && currentWidth > 0) {
        processBottomDiff(currentHeight - previousHeight, currentHeight < previousHeight ? previousHeight - currentHeight : 0);
      }
      previousHeight = currentHeight;
      previousWidth = currentWidth;
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    try {
      super.onLayout(changed, left, top, right, bottom);
    } catch (Throwable t) {
      Log.critical(t);
      throw t;
    }
  }

  // DEBUG

  private static class ViewHasRootException extends RuntimeException {
    public ViewHasRootException (String message) {
      super(message);
    }
  }

  private static void assertView (View view) {
    if (view == null) {
      throw new NullPointerException();
    }
    if (view.getParent() != null) {
      throw new ViewHasRootException(view.getClass().getName() + " already has root");
    }
  }

  @Override
  public void addView (View child) {
    assertView(child);
    super.addView(child);
  }

  @Override
  public void addView (View child, int index) {
    assertView(child);
    super.addView(child, index);
  }

  @Override
  public void addView (View child, int width, int height) {
    assertView(child);
    super.addView(child, width, height);
  }

  @Override
  public void addView (View child, ViewGroup.LayoutParams params) {
    assertView(child);
    super.addView(child, params);
  }

  @Override
  public void addView (View child, int index, ViewGroup.LayoutParams params) {
    assertView(child);
    super.addView(child, index, params);
  }
}
