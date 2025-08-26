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
 * File created on 06/12/2016
 */
package org.thunderdog.challegram.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.navigation.InterceptLayout;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.OverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Passcode;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.reference.ReferenceList;

@SuppressWarnings("deprecation")
public class RootFrameLayout extends FrameLayoutFix {
  private boolean ignoreBottom, ignoreSystemNavigationBar, ignoreAll, alwaysApplyIme;
  private final ViewTreeObserver.OnPreDrawListener onPreDrawListener = () -> false;

  private Keyboard.OnStateChangeListener keyboardListener;

  public RootFrameLayout (Context context) {
    super(context);
  }

  private static int getImeHeight (Object insetsRaw) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      android.view.WindowInsets windowInsets = (android.view.WindowInsets) insetsRaw;
      android.graphics.Insets insets = windowInsets.getInsets(
        android.view.WindowInsets.Type.systemBars() |
          android.view.WindowInsets.Type.displayCutout() |
          android.view.WindowInsets.Type.ime()
      );
      android.graphics.Insets insetsWithoutIme = windowInsets.getInsets(
        android.view.WindowInsets.Type.systemBars() |
          android.view.WindowInsets.Type.displayCutout()
      );
      return Math.abs(insetsWithoutIme.bottom - insets.bottom);
    }
    return 0;
  }

  private static boolean updateInsets (Rect rect, Object insetsRaw, boolean includeIme) {
    final int left, top, right, bottom;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && insetsRaw != null) {
      android.view.WindowInsets windowInsets = (android.view.WindowInsets) insetsRaw;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.graphics.Insets insets = windowInsets.getInsets(
          android.view.WindowInsets.Type.systemBars() |
            android.view.WindowInsets.Type.displayCutout() |
            (includeIme ? android.view.WindowInsets.Type.ime() : 0)
        );
        left = insets.left;
        top = insets.top;
        right = insets.right;
        bottom = insets.bottom;
      } else {
        left = windowInsets.getSystemWindowInsetLeft();
        top = windowInsets.getSystemWindowInsetTop();
        right = windowInsets.getSystemWindowInsetRight();
        bottom = windowInsets.getSystemWindowInsetBottom();
      }
    } else {
      left = 0; top = 0; right = 0; bottom = 0;
    }
    return U.setRect(rect, left, top, right, bottom);
  }

  private static Object newWindowInsets (Object originalInsetsRaw, int left, int top, int right, int bottom) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      android.view.WindowInsets originalWindowInsets = (android.view.WindowInsets) originalInsetsRaw;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return new android.view.WindowInsets.Builder(originalWindowInsets)
          .setSystemWindowInsets(android.graphics.Insets.of(left, top, right, bottom))
          .build();
      } else {
        return originalWindowInsets.replaceSystemWindowInsets(
          left, top, right, bottom
        );
      }
    }
    throw new IllegalStateException("Unsupported: " + Build.VERSION.SDK_INT);
  }

  public void setIgnoreSystemNavigationBar (boolean ignoreSystemNavigationBar) {
    this.ignoreSystemNavigationBar = ignoreSystemNavigationBar;
  }

  public void setIgnoreAllInsets (boolean ignoreAll) {
    this.ignoreAll = ignoreAll;
  }

  public void init (final boolean ignoreBottom) {
    this.ignoreBottom = ignoreBottom;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      UI.setFullscreenIfNeeded(this);
      setOnApplyWindowInsetsListener((v, insets) -> {
        processWindowInsets(insets);
        return insets.consumeSystemWindowInsets();
      });
    }
  }

  private boolean isKeyboardVisible;
  private CancellableRunnable lastAction;

  private void setKeyboardVisible (final boolean isVisible) {
    if (this.isKeyboardVisible != isVisible) {
      this.isKeyboardVisible = isVisible;
      if (lastAction != null) {
        lastAction.cancel();
        lastAction = null;
      }
      if (keyboardListener != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
          keyboardListener.onKeyboardStateChanged(isVisible);
          return;
        }
        ViewTreeObserver observer = getViewTreeObserver();
        observer.removeOnPreDrawListener(onPreDrawListener);
        observer.addOnPreDrawListener(onPreDrawListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          keyboardListener.onKeyboardStateChanged(isVisible);
          UI.post(lastAction = new CancellableRunnable() {
            @Override
            public void act () {
              observer.removeOnPreDrawListener(onPreDrawListener);
              invalidate();
              if (lastAction == this) {
                lastAction = null;
              }
            }
          }.removeOnCancel(UI.getAppHandler()), 20);
        } else {
          UI.post(lastAction = new CancellableRunnable() {
            @Override
            public void act () {
              keyboardListener.onKeyboardStateChanged(isVisible);
              observer.removeOnPreDrawListener(onPreDrawListener);
              invalidate();
              if (lastAction == this) {
                lastAction = null;
              }
            }
          }.removeOnCancel(UI.getAppHandler()), 2);
        }
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

  public interface InsetsChangeListener {
    void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect systemInsets, boolean isUpdate);
  }

  private final ReferenceList<InsetsChangeListener> listeners = new ReferenceList<>();

  public void addInsetsChangeListener (InsetsChangeListener listener) {
    listeners.add(listener);
  }

  public void removeInsetsChangeListener (InsetsChangeListener listener) {
    listeners.remove(listener);
  }

  private final Rect prevSystemInsets = new Rect();

  private boolean hasInsets;
  private Object windowInsetsRaw;
  private final Rect systemInsets = new Rect();

  private final Rect effectiveInsets = new Rect();

  public void processWindowInsets (Object insetsRaw) {
    boolean hadInsets = hasInsets;
    boolean systemInsetsUpdated = updateInsets(systemInsets, insetsRaw, true);
    boolean verticalSystemInsetsUpdated = !hasInsets || systemInsets.top != prevSystemInsets.top || systemInsets.bottom != prevSystemInsets.bottom;
    boolean horizontalSystemInsetsUpdated = !hasInsets || systemInsets.left != prevSystemInsets.left || systemInsets.right != prevSystemInsets.right;

    boolean ignoreChanges = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && UI.getContext(getContext()).isInFullScreen() && verticalSystemInsetsUpdated != horizontalSystemInsetsUpdated;
    boolean effectiveInsetsUpdated = !ignoreChanges && U.setRect(effectiveInsets, systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom);
    if (!ignoreChanges) {
      effectiveInsets.set(systemInsets);
    }

    windowInsetsRaw = insetsRaw;
    hasInsets = true;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      int imeHeight = getImeHeight(insetsRaw);
      if (imeHeight > 0) {
        Keyboard.processSize(imeHeight);
        setKeyboardVisible(true);
      } else {
        setKeyboardVisible(false);
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !ignoreChanges && verticalSystemInsetsUpdated && !horizontalSystemInsetsUpdated) {
      if (this instanceof BaseRootLayout) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || !UI.getContext(getContext()).isInFullScreen()) {
          Screen.setStatusBarHeight(effectiveInsets.top);
        }
      }

     if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        if (hadInsets && !ignoreSystemNavigationBar) {
          int bottomDiff = (shouldIgnoreBottomMargin(prevSystemInsets.bottom) ? 0 : prevSystemInsets.bottom) - (shouldIgnoreBottomMargin(effectiveInsets.bottom) ? 0 : effectiveInsets.bottom);

          int rightDiff = prevSystemInsets.right - effectiveInsets.right;
          int leftDiff = prevSystemInsets.left - effectiveInsets.left;

          if (rightDiff == 0 && leftDiff == 0 && bottomDiff != 0) {
            processBottomDiff(bottomDiff, effectiveInsets.bottom);
          }
        }
      }
    }

    if (effectiveInsetsUpdated || systemInsetsUpdated) {
      for (InsetsChangeListener listener : listeners) {
        listener.onInsetsChanged(this, effectiveInsets, systemInsets, hadInsets);
      }
      requestLayout();
    }

    prevSystemInsets.set(systemInsets);
    hasInsets = true;
  }

  private boolean shouldIgnoreBottomMargin (int bottom) {
    if (alwaysApplyIme && isKeyboardVisible) {
      return false;
    }
    return ignoreBottom || ignoreAll || (ignoreSystemNavigationBar && bottom <= Screen.getNavigationBarHeight());
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void dispatchChildInsets (View child, Object windowInsetsRaw, int gravity) {
    int left = ignoreAll || ignoreHorizontal || gravity == Gravity.RIGHT ? 0 : systemInsets.left;
    int top = ignoreAll ? 0 : systemInsets.top;
    int right = ignoreAll || ignoreHorizontal || gravity == Gravity.LEFT ? 0 : systemInsets.right;
    int bottom = shouldIgnoreBottomMargin(systemInsets.bottom) ? 0 : systemInsets.bottom;

    android.view.WindowInsets originalWindowInsets = (android.view.WindowInsets) windowInsetsRaw;
    android.view.WindowInsets newWindowInsets;

    if (gravity == Gravity.LEFT) {
      newWindowInsets = (android.view.WindowInsets) newWindowInsets(originalWindowInsets, systemInsets.left, systemInsets.top, 0, systemInsets.bottom);
    } else if (gravity == Gravity.BOTTOM) {
      newWindowInsets =  (android.view.WindowInsets) newWindowInsets(originalWindowInsets, 0, systemInsets.top, systemInsets.right, systemInsets.bottom);
    } else {
      newWindowInsets = originalWindowInsets;
    }

    if (UI.getContext(getContext()).dispatchCameraMargins(child, systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)) {
      newWindowInsets = (android.view.WindowInsets) newWindowInsets(newWindowInsets, 0, 0, 0, 0);
    } else {
      ViewController<?> c = ViewController.findAncestor(child);
      if (c != null) {
        c.dispatchSystemInsets(child, (MarginLayoutParams) child.getLayoutParams(), left, top, right, bottom);
      }
    }
    child.dispatchApplyWindowInsets(newWindowInsets);
  }

  private boolean ignoreHorizontal;

  public void setIgnoreHorizontal () {
    if (!ignoreHorizontal) {
      ignoreHorizontal = true;
      requestLayout();
    }
  }

  public void setAlwaysApplyIme (boolean alwaysApplyIme) {
    if (this.alwaysApplyIme != alwaysApplyIme) {
      this.alwaysApplyIme = alwaysApplyIme;
      requestLayout();
    }
  }

  public void setIgnoreBottom (boolean ignoreBottom) {
    if (this.ignoreBottom != ignoreBottom) {
      this.ignoreBottom = ignoreBottom;
      requestLayout();
    }
  }

  private void applyMarginInsets (View child, MarginLayoutParams lp, Rect insets, int gravity, boolean ignoreTop) {
    int leftMargin = ignoreAll || ignoreHorizontal || gravity == Gravity.RIGHT ? 0 : insets.left;
    int topMargin = ignoreAll || ignoreTop ? 0 : insets.top;
    int rightMargin = ignoreAll || ignoreHorizontal || gravity == Gravity.LEFT ? 0 : insets.right;
    int bottomMargin = shouldIgnoreBottomMargin(insets.bottom) ? 0 : insets.bottom;
    if (UI.getContext(getContext()).dispatchCameraMargins(child, leftMargin, topMargin, rightMargin, bottomMargin)) {
      lp.leftMargin = lp.topMargin = lp.rightMargin = lp.bottomMargin = 0;
    } else {
      lp.leftMargin = leftMargin;
      lp.topMargin = topMargin;
      lp.rightMargin = rightMargin;
      lp.bottomMargin = bottomMargin;
      ViewController<?> c = ViewController.findAncestor(child);
      if (c != null) {
        c.dispatchSystemInsets(child, lp, insets.left, insets.top, insets.right, insets.bottom);
      }
    }
  }

  public int getInnerContentHeight () {
    return getMeasuredHeight() - (shouldIgnoreBottomMargin(effectiveInsets.bottom) ? 0 : getBottomInset());
  }

  public int getBottomInset () {
    return hasInsets ? effectiveInsets.bottom : 0;
  }

  public int getTopInset () {
    return hasInsets ? effectiveInsets.top : 0;
  }

  private void applyNavigationInsets (InterceptLayout navigationLayout, MarginLayoutParams params, Rect insets) {
    Views.setMargins(params, 0, 0, 0, 0);

    int leftMargin = ignoreAll || ignoreHorizontal ? 0 : insets.left;
    int topMargin = 0;
    int rightMargin = ignoreAll || ignoreHorizontal ? 0 : insets.right;
    int bottomMargin = shouldIgnoreBottomMargin(insets.bottom) ? 0 : insets.bottom;

    for (int i = 0; i < navigationLayout.getChildCount(); i++) {
      View innerChild = navigationLayout.getChildAt(i);
      if (innerChild != null) {
        boolean updated;
        if (innerChild.getTag() instanceof NavigationController) {
          NavigationController navigation = (NavigationController) innerChild.getTag();
          updated = navigation.dispatchInnerMargins(innerChild, (MarginLayoutParams) innerChild.getLayoutParams(), leftMargin, topMargin, rightMargin, bottomMargin);
        } else if (innerChild instanceof OverlayView) {
          updated = Views.setMargins(innerChild, 0, 0, 0, 0);
        } else {
          ViewController<?> c = ViewController.findAncestor(innerChild);
          if (c != null) {
            updated = c.dispatchSystemInsets(innerChild, (MarginLayoutParams) innerChild.getLayoutParams(), leftMargin, topMargin, rightMargin, bottomMargin);
          } else {
            updated = Views.setMargins(innerChild, leftMargin, topMargin, rightMargin, bottomMargin);
          }
        }
        if (updated) {
          Views.updateLayoutParams(innerChild);
        }
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hasInsets) {
      for (int i = 0; i < getChildCount(); i++) {
        View view = getChildAt(i);
        if (view != null) {
          LayoutParams params = (LayoutParams) view.getLayoutParams();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && view instanceof InterceptLayout) {
            applyNavigationInsets((InterceptLayout) view, params, effectiveInsets);
          } else if (view.getFitsSystemWindows()) {
            dispatchChildInsets(view, windowInsetsRaw, params.gravity);
          } else {
            applyMarginInsets(view, params, effectiveInsets, params.gravity, true);
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
