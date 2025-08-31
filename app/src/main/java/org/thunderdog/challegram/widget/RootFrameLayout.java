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
import android.widget.FrameLayout;

import androidx.annotation.IntDef;

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
import org.thunderdog.challegram.unsorted.Settings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    InsetsType.SYSTEM_BARS_CUTOUT,
    InsetsType.SYSTEM_BARS_CUTOUT_IME,
    InsetsType.SYSTEM_GESTURES,
    InsetsType.DISPLAY_CUTOUT,
  })
  private @interface InsetsType {
    int
      SYSTEM_BARS_CUTOUT = 0,
      SYSTEM_BARS_CUTOUT_IME = 1,
      SYSTEM_GESTURES = 2,
      DISPLAY_CUTOUT = 3;
  }

  private static boolean updateInsets (Rect rect, Object insetsRaw, @InsetsType int insetsType) {
    final int left, top, right, bottom;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && insetsRaw != null) {
      android.view.WindowInsets windowInsets = (android.view.WindowInsets) insetsRaw;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        int typeMask;
        switch (insetsType) {
          case InsetsType.SYSTEM_BARS_CUTOUT:
            typeMask =
              android.view.WindowInsets.Type.systemBars() |
              android.view.WindowInsets.Type.displayCutout();
            break;
          case InsetsType.SYSTEM_BARS_CUTOUT_IME:
            typeMask =
              android.view.WindowInsets.Type.systemBars() |
              android.view.WindowInsets.Type.displayCutout() |
              android.view.WindowInsets.Type.ime();
            break;
          case InsetsType.SYSTEM_GESTURES:
            typeMask =
              android.view.WindowInsets.Type.systemGestures() |
              android.view.WindowInsets.Type.mandatorySystemGestures();
            break;
          case InsetsType.DISPLAY_CUTOUT:
            typeMask =
              android.view.WindowInsets.Type.displayCutout();
            break;
          default:
            throw new AssertionError(Integer.toString(insetsType));
        }
        android.graphics.Insets insets = windowInsets.getInsets(typeMask);
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
        processWindowInsets(insets, false);
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
    void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect effectiveInsetsWithoutIme, Rect systemInsets, Rect systemInsetsWithoutIme, boolean isUpdate);
    default void onSecondaryInsetsChanged (RootFrameLayout viewGroup, boolean systemGesturesInsetsChanged, boolean displayCutoutInsetsChanged) { }
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
  private final Rect systemInsetsWithoutIme = new Rect();
  private final Rect systemGesturesInsets = new Rect();

  private final Rect displayCutoutInsets = new Rect();
  private final Rect displayCutoutTopInset = new Rect();

  private final Rect effectiveInsets = new Rect();
  private final Rect effectiveInsetsWithoutIme = new Rect();

  private final Rect legacyInsets = new Rect();

  private static final boolean CAN_DETECT_IME = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
  private boolean hasIgnoredChanged;

  public void forceHideKeyboard () {
    if (Settings.instance().useEdgeToEdge()) {
      if (hasIgnoredChanged && systemInsetsWithoutIme.bottom == systemInsets.bottom) {
        effectiveInsets.set(effectiveInsetsWithoutIme);
        notifyChanges(true);
      }
    }
  }

  public Rect getSystemGesturesInsets () {
    return systemGesturesInsets;
  }

  private final int[] childLocation = new int[2];
  private final int[] rootLocation = new int[2];

  public boolean isWithinSystemGesturesArea (View child, MotionEvent event) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return false;
    }

    child.getLocationInWindow(childLocation);
    getLocationInWindow(rootLocation);

    float x = event.getX() + childLocation[0] - rootLocation[0];
    float y = event.getY() + childLocation[1] - rootLocation[1];

    return
      x < systemGesturesInsets.left || x > getMeasuredWidth() - systemGesturesInsets.right ||
      y < systemGesturesInsets.top || y > getMeasuredHeight() - systemGesturesInsets.bottom;
  }

  private void processWindowInsets (Object insetsRaw, boolean force) {
    boolean hadInsets = hasInsets;
    boolean systemInsetsUpdated = updateInsets(systemInsets, insetsRaw, InsetsType.SYSTEM_BARS_CUTOUT_IME);
    boolean systemInsetsWithoutImeUpdated = updateInsets(systemInsetsWithoutIme, insetsRaw, InsetsType.SYSTEM_BARS_CUTOUT);
    boolean systemGesturesUpdated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && updateInsets(systemGesturesInsets, insetsRaw, InsetsType.SYSTEM_GESTURES);
    boolean displayCutoutUpdated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && updateInsets(displayCutoutInsets, insetsRaw, InsetsType.DISPLAY_CUTOUT);
    boolean verticalSystemInsetsUpdated = !hasInsets || systemInsets.top != prevSystemInsets.top || systemInsets.bottom != prevSystemInsets.bottom;
    boolean horizontalSystemInsetsUpdated = !hasInsets || systemInsets.left != prevSystemInsets.left || systemInsets.right != prevSystemInsets.right;
    final int imeHeight = CAN_DETECT_IME ? getImeHeight(insetsRaw) : 0;
    final boolean isKeyboardVisible = imeHeight > 0;

    boolean ignoreChanges = Settings.instance().useEdgeToEdge() && !force &&
      UI.getContext(getContext()).isInFullScreen() &&
      (this instanceof BaseRootLayout || (UI.getContext(getContext()).isHideNavigation() && verticalSystemInsetsUpdated && !horizontalSystemInsetsUpdated));
    hasIgnoredChanged = ignoreChanges;
    boolean effectiveInsetsUpdated = !ignoreChanges && U.setRect(effectiveInsets, systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom);
    if (!ignoreChanges) {
      effectiveInsets.set(systemInsets);
      if (CAN_DETECT_IME) {
        effectiveInsetsWithoutIme.set(systemInsetsWithoutIme);
      }
    }

    windowInsetsRaw = insetsRaw;
    hasInsets = true;

    if (CAN_DETECT_IME) {
      if (imeHeight > 0) {
        Keyboard.processSize(imeHeight);
        setKeyboardVisible(true);
      } else {
        setKeyboardVisible(false);
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !ignoreChanges) {
      if (this instanceof BaseRootLayout) {
        if (!Settings.instance().useEdgeToEdge() || !UI.getContext(getContext()).isInFullScreen()) {
          Screen.setStatusBarHeight(effectiveInsets.top);
        }
      }

     if (!Settings.instance().useEdgeToEdge()) {
        if (hadInsets && !ignoreSystemNavigationBar && verticalSystemInsetsUpdated && !horizontalSystemInsetsUpdated) {
          int bottomDiff = (shouldIgnoreBottomMargin(prevSystemInsets.bottom) ? 0 : prevSystemInsets.bottom) - (shouldIgnoreBottomMargin(effectiveInsets.bottom) ? 0 : effectiveInsets.bottom);

          int rightDiff = prevSystemInsets.right - effectiveInsets.right;
          int leftDiff = prevSystemInsets.left - effectiveInsets.left;

          if (rightDiff == 0 && leftDiff == 0 && bottomDiff != 0) {
            processBottomDiff(bottomDiff, effectiveInsets.bottom);
          }
        }
      }
    }

    if (!CAN_DETECT_IME && !ignoreChanges) {
      effectiveInsetsWithoutIme.set(systemInsetsWithoutIme);
      if (isKeyboardVisible && effectiveInsetsWithoutIme.bottom == effectiveInsets.bottom) {
        effectiveInsetsWithoutIme.bottom = Math.max(0, effectiveInsets.bottom - Keyboard.getSize());
      }
    }

    if (effectiveInsetsUpdated || systemInsetsUpdated) {
      notifyChanges(hadInsets);
    }

    if ((systemGesturesUpdated || displayCutoutUpdated) && (this instanceof BaseRootLayout)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && displayCutoutUpdated) {
        android.view.WindowInsets rootInsets = getRootWindowInsets();
        android.view.DisplayCutout displayCutout = rootInsets.getDisplayCutout();
        Rect topInset = displayCutout != null ? displayCutout.getBoundingRectTop() : null;
        if (topInset != null) {
          displayCutoutTopInset.set(topInset);
        } else {
          displayCutoutTopInset.setEmpty();
        }
      }
      notifySecondaryChanges(systemGesturesUpdated, displayCutoutUpdated);
    }

    prevSystemInsets.set(systemInsets);
    hasInsets = true;
  }

  private void notifyChanges (boolean hadInsets) {
    for (InsetsChangeListener listener : listeners) {
      listener.onInsetsChanged(this, effectiveInsets, effectiveInsetsWithoutIme, systemInsets, systemInsetsWithoutIme, hadInsets);
    }
    requestLayout();
  }

  private void notifySecondaryChanges (boolean systemGesturesUpdated, boolean displayCutoutUpdated) {
    for (InsetsChangeListener listener : listeners) {
      listener.onSecondaryInsetsChanged(this, systemGesturesUpdated, displayCutoutUpdated);
    }
  }

  private boolean shouldIgnoreBottomMargin (int bottom) {
    if (alwaysApplyIme && isKeyboardVisible) {
      return false;
    }
    return ignoreBottom || ignoreAll || (ignoreSystemNavigationBar && bottom <= Screen.getNavigationBarHeight());
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void dispatchChildInsets (View child, Object windowInsetsRaw, int gravity) {
    legacyInsets.set(
      ignoreAll || ignoreHorizontal || gravity == Gravity.RIGHT ? 0 : systemInsets.left,
      ignoreAll ? 0 : systemInsets.top,
      ignoreAll || ignoreHorizontal || gravity == Gravity.LEFT ? 0 : systemInsets.right,
      shouldIgnoreBottomMargin(systemInsets.bottom) ? 0 : systemInsets.bottom
    );

    android.view.WindowInsets originalWindowInsets = (android.view.WindowInsets) windowInsetsRaw;
    android.view.WindowInsets newWindowInsets;

    if (gravity == Gravity.LEFT) {
      newWindowInsets = (android.view.WindowInsets) newWindowInsets(originalWindowInsets, systemInsets.left, systemInsets.top, 0, systemInsets.bottom);
    } else if (gravity == Gravity.BOTTOM) {
      newWindowInsets =  (android.view.WindowInsets) newWindowInsets(originalWindowInsets, 0, systemInsets.top, systemInsets.right, systemInsets.bottom);
    } else {
      newWindowInsets = originalWindowInsets;
    }

    if (UI.getContext(getContext()).dispatchCameraMargins(child, legacyInsets, effectiveInsets, effectiveInsetsWithoutIme)) {
      newWindowInsets = (android.view.WindowInsets) newWindowInsets(newWindowInsets, 0, 0, 0, 0);
    } else {
      MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
      int originalLeft = params.leftMargin;
      int originalTop = params.topMargin;
      int originalRight = params.rightMargin;
      int originalBottom = params.bottomMargin;
      ViewController<?> c = ViewController.findAncestor(child);
      if (c != null) {
        c.dispatchSystemInsets(child, params, legacyInsets, effectiveInsets, effectiveInsetsWithoutIme, systemInsets, systemInsetsWithoutIme, true);
      }
      if (params.leftMargin != originalLeft ||
        params.topMargin != originalTop ||
        params.rightMargin != originalRight ||
        params.bottomMargin != originalBottom) {
        Views.updateLayoutParams(child);
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

  public interface MarginModifier {
    void onApplyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme);
  }

  private void applyMarginInsets (View child, FrameLayout.LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
    legacyInsets.set(
      ignoreAll || ignoreHorizontal || params.gravity == Gravity.RIGHT ? 0 : effectiveInsets.left,
      ignoreAll || true ? 0 : effectiveInsets.top,
      ignoreAll || ignoreHorizontal || params.gravity == Gravity.LEFT ? 0 : effectiveInsets.right,
      shouldIgnoreBottomMargin(effectiveInsets.bottom) ? 0 : effectiveInsets.bottom
    );
    if (UI.getContext(getContext()).dispatchCameraMargins(child, legacyInsets, insets, insetsWithoutIme)) {
      Views.setMargins(params, 0, 0, 0, 0);
    } else {
      if (child instanceof MarginModifier) {
        ((MarginModifier) child).onApplyMarginInsets(child, params, legacyInsets, insets, insetsWithoutIme);
      } else {
        Views.setMargins(params,
          legacyInsets.left,
          legacyInsets.top,
          legacyInsets.right,
          legacyInsets.bottom
        );
      }
      ViewController<?> c = ViewController.findAncestor(child);
      if (c != null) {
        c.dispatchSystemInsets(child, params, legacyInsets, insets, insetsWithoutIme, systemInsets, systemInsetsWithoutIme, false);
      }
    }
  }

  private void applyNavigationInsets (InterceptLayout navigationLayout, MarginLayoutParams navigationLayoutParams, Rect insets, Rect insetsWithoutIme) {
    // No need for insets in navigation
    Views.setMargins(navigationLayoutParams, 0, 0, 0, 0);

    legacyInsets.set(
      ignoreAll || ignoreHorizontal ? 0 : insets.left,
      ignoreAll || true ? 0 : insets.top,
      ignoreAll || ignoreHorizontal ? 0 : insets.right,
      shouldIgnoreBottomMargin(insets.bottom) ? 0 : insets.bottom
    );

    for (int i = 0; i < navigationLayout.getChildCount(); i++) {
      View innerChild = navigationLayout.getChildAt(i);
      if (innerChild != null) {
        MarginLayoutParams params = (MarginLayoutParams) innerChild.getLayoutParams();
        int originalLeft = params.leftMargin;
        int originalTop = params.topMargin;
        int originalRight = params.rightMargin;
        int originalBottom = params.bottomMargin;
        if (innerChild.getTag() instanceof NavigationController) {
          NavigationController navigation = (NavigationController) innerChild.getTag();
          navigation.dispatchSystemInsets(innerChild, params, legacyInsets, insets, insetsWithoutIme);
        } else if (innerChild instanceof OverlayView) {
          Views.setMargins(params, 0, 0, 0, 0);
        } else {
          Views.setMargins(params,
            legacyInsets.left,
            legacyInsets.top,
            legacyInsets.right,
            legacyInsets.bottom
          );
          ViewController<?> c = ViewController.findAncestor(innerChild);
          if (c != null) {
            c.dispatchSystemInsets(innerChild, params, legacyInsets, insets, insetsWithoutIme, systemInsets, systemInsetsWithoutIme, false);
          }
        }
        if (params.leftMargin != originalLeft ||
          params.topMargin != originalTop ||
          params.rightMargin != originalRight ||
          params.bottomMargin != originalBottom) {
          Views.updateLayoutParams(innerChild);
        }
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

  public Rect getSystemInsets () {
    return systemInsets;
  }

  public Rect getSystemInsetsWithoutIme () {
    return systemInsetsWithoutIme;
  }

  public Rect getDisplayCutoutTopInset () {
    return displayCutoutTopInset;
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
          if (Settings.instance().useEdgeToEdge() && view instanceof InterceptLayout) {
            applyNavigationInsets((InterceptLayout) view, params, effectiveInsets, effectiveInsetsWithoutIme);
          } else if (view.getFitsSystemWindows()) {
            dispatchChildInsets(view, windowInsetsRaw, params.gravity);
          } else {
            applyMarginInsets(view, params, legacyInsets, effectiveInsets, effectiveInsetsWithoutIme);
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
