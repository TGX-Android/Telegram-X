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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.RootDrawable;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.SensitiveContentContainer;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class PopupLayout extends RootFrameLayout implements FactorAnimator.Target, BaseActivity.ActivityListener, Keyboard.OnStateChangeListener, ActivityResultHandler, Destroyable {
  public interface TouchSectionProvider {
    boolean shouldTouchOutside (float x, float y);
  }

  private @Nullable PopupWindow window;
  private @Nullable View windowAnchorView;
  private @Nullable BaseActivity.ActivityListener activityListener;
  private @Nullable TouchSectionProvider touchSectionProvider;
  private @Nullable Keyboard.OnStateChangeListener keyboardListener;

  private boolean overlayStatusBar;

  public interface DismissListener {
    void onPopupDismiss (PopupLayout popup);
    default void onPopupDismissPrepare (PopupLayout popup) { }
  }

  public interface ShowListener {
    void onPopupCompletelyShown (PopupLayout popup);
  }

  private @Nullable DismissListener dismissListener;
  private @Nullable ShowListener showListener;
  private @Nullable BackListener backListener;

  public PopupLayout (Context context) {
    super(context);
    setKeyboardListener(this);
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    backgroundView = new BackgroundView(context);
    backgroundView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(backgroundView);
  }

  public void setOverlayStatusBar (boolean overlayStatusBar) {
    this.overlayStatusBar = overlayStatusBar;
  }

  public boolean shouldOverlayStatusBar () {
    return overlayStatusBar;
  }

  public void setBackListener (@Nullable BackListener backListener) {
    this.backListener = backListener;
  }

  public void setDismissListener (@Nullable DismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  public void setShowListener (@Nullable ShowListener showListener) {
    this.showListener = showListener;
  }

  public void setKeyboardChangeListener (@Nullable Keyboard.OnStateChangeListener keyboardListener) {
    this.keyboardListener = keyboardListener;
  }

  private boolean isKeyboardVisible;

  @Override
  public void onKeyboardStateChanged (boolean isVisible) {
    if (this.isKeyboardVisible != isVisible) {
      this.isKeyboardVisible = isVisible;
      if (keyboardListener != null) {
        keyboardListener.onKeyboardStateChanged(isVisible);
      } else if (boundController != null) {
        boundController.onKeyboardStateChanged(isVisible);
      }
    }
  }

  @Override
  public void closeAdditionalKeyboards () {
    if (keyboardListener != null) {
      keyboardListener.closeAdditionalKeyboards();
    } else if (boundController != null && boundController instanceof Keyboard.OnStateChangeListener) {
      ((Keyboard.OnStateChangeListener) boundController).closeAdditionalKeyboards();
    }
  }

  public void setActivityListener (@Nullable BaseActivity.ActivityListener activityListener) {
    this.activityListener = activityListener;
  }

  @Override
  public void onActivityPause () {
    if (activityListener != null) {
      activityListener.onActivityPause();
    }
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (boundController instanceof ActivityResultHandler) {
      ((ActivityResultHandler) boundController).onActivityResult(requestCode, resultCode, data);
    } else if (boundView instanceof ActivityResultHandler) {
      ((ActivityResultHandler) boundView).onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onActivityResume () {
    if (activityListener != null) {
      activityListener.onActivityResume();
    }
  }

  @Override
  public void onActivityDestroy () {
    if (activityListener != null) {
      activityListener.onActivityDestroy();
    }
    dismissWindow();
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) {
    if (activityListener != null) {
      activityListener.onActivityPermissionResult(code, granted);
    }
  }

  public void setTouchProvider (@Nullable TouchSectionProvider touchProvider) {
    this.touchSectionProvider = touchProvider;
  }

  private boolean needRootInsets;

  public void setNeedRootInsets () {
    this.needRootInsets = true;
  }

  private boolean needFullScreen;

  public void setNeedFullScreen (boolean needFullScreen) {
    this.needFullScreen = needFullScreen;
  }

  private boolean hideKeyboard;

  public void setHideKeyboard () {
    this.hideKeyboard = true;
  }

  public boolean canHideKeyboard () {
    return needRootInsets || (softInputMode != 0 && isKeyboardVisible);
  }

  public boolean hideSoftwareKeyboard () {
    if (boundController != null) {
      if (boundController.getKeyboardState() || (boundController instanceof MediaViewController && ((MediaViewController) boundController).isEmojiVisible())) {
        boundController.hideSoftwareKeyboard();
        return true;
      }
    }
    return false;
  }

  private ViewController<?> boundController;

  public boolean onBackPressed (boolean byHeaderBackPress) {
    return (backListener != null && backListener.onBackPressed(byHeaderBackPress)) || (boundController != null && boundController.onBackPressed(false)) || (boundView != null && boundView instanceof BackListener && ((BackListener) boundView).onBackPressed(byHeaderBackPress));
  }

  public void setBoundController (ViewController<?> boundController) {
    this.boundController = boundController;
  }

  public ViewController<?> getBoundController () {
    return boundController;
  }

  private boolean isTemporarilyHidden;

  private void removeWindow () {
    if (needRootInsets) {
      UI.getContext(getContext()).removeFromRoot(this);
    } else if (window != null) {
      try {
        window.dismiss();
      } catch (Throwable ignored) { }
    }
  }

  public void hideTemporarily () {
    if (!isTemporarilyHidden) {
      isTemporarilyHidden = true;
      removeWindow();
    }
  }

  public void restoreIfHidden () {
    if (isTemporarilyHidden) {
      isTemporarilyHidden = false;
      if (needRootInsets) {
        UI.getContext(getContext()).addToRoot(this, shouldOverlayStatusBar());
      } else if (windowAnchorView != null) {
        showSystemWindow(windowAnchorView);
      }
    }
  }

  private static boolean patchPopupWindow (View container, boolean needFullScreen, boolean disallowScreenshots) {
    WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
    int newFlags = p.flags;
    newFlags = BitwiseUtils.setFlag(newFlags, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, needFullScreen);
    newFlags = BitwiseUtils.setFlag(newFlags, WindowManager.LayoutParams.FLAG_SECURE, disallowScreenshots);
    if (p.flags != newFlags) {
      p.flags = newFlags;
      return true;
    }
    return false;
  }

  public void checkWindowFlags () {
    if (isHidden || isDismissed || windowAnchorView == null || isTemporarilyHidden)
      return;
    if (window != null) {
      View rootView = window.getContentView().getRootView();
      ViewGroup.LayoutParams layoutParams = rootView.getLayoutParams();
      boolean disallowScreenShots = shouldDisallowScreenshots();
      if (!(layoutParams instanceof WindowManager.LayoutParams)) {
        // TODO: analyze in what situations container parameters become `android.widget.FrameLayout$LayoutParams`
        // after that, uncomment code below, if it's caused by root view, not by window detachment
        /*int windowFlags =
          (needFullScreen ? WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS : 0) |
          (disallowScreenShots ? WindowManager.LayoutParams.FLAG_SECURE : 0);
        if (windowFlags != 0) {
          final BaseActivity context = UI.getContext(getContext());
          WindowManager.LayoutParams newParams = new WindowManager.LayoutParams();
          newParams.flags = windowFlags;
          // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
          context.getWindowManager().updateViewLayout(rootView, newParams);
        }*/
        return;
      }
      if (patchPopupWindow(rootView, needFullScreen, disallowScreenShots)) {
        final BaseActivity context = UI.getContext(getContext());
        // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
        context.getWindowManager().updateViewLayout(rootView, rootView.getLayoutParams());
      }
    }
  }

  private void showSystemWindow (View anchorView) {
    final BaseActivity context = UI.getContext(getContext());
    window = new PopupWindow(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    if (softInputMode != 0) {
      window.setSoftInputMode(softInputMode);
      window.setFocusable(true);
      window.setOutsideTouchable(false);
    } else {
      window.setFocusable(false);
      window.setOutsideTouchable(true);
    }
    UI.post(() -> {
      if (isHidden || isDismissed) {
        return;
      }
      int state = context.getActivityState();
      if (state == UI.STATE_RESUMED) {
        try {
          window.showAtLocation(windowAnchorView = anchorView, Gravity.NO_GRAVITY, 0, 0);
          window.setBackgroundDrawable(new RootDrawable(UI.getContext(getContext())));
          View rootView = window.getContentView().getRootView();
          boolean updated = patchPopupWindow(rootView, needFullScreen, shouldDisallowScreenshots());
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) rootView.getLayoutParams();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            updated = true;
          }
          if (updated) {
            // WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);?
            context.getWindowManager().updateViewLayout(rootView, rootView.getLayoutParams());
          }
          return;
        } catch (Throwable t) {
          Log.e("Cannot show window", t);
        }
        return;
      }
      context.addSimpleStateListener(new BaseActivity.SimpleStateListener() {
        @Override
        public void onActivityStateChanged (BaseActivity activity, int newState, int prevState) {
          if (isHidden || isDismissed) {
            context.removeSimpleStateListener(this);
            return;
          }
          if (newState == UI.STATE_RESUMED) {
            context.removeSimpleStateListener(this);
            if (!isTemporarilyHidden) {
              showSystemWindow(anchorView);
            }
          }
        }
      });
    });
  }

  public void showBoundWindow (View anchorView) {
    if (hideKeyboard || Device.HIDE_POPUP_KEYBOARD) {
      ViewController<?> c = UI.getContext(getContext()).navigation().getCurrentStackItem();
      if (c != null) {
        c.hideSoftwareKeyboard();
      }
    }
    // context.hideContextualPopups();
    if (needRootInsets) {
      UI.getContext(getContext()).addToRoot(this, shouldOverlayStatusBar());
    } else {
      showSystemWindow(anchorView);
    }

    runOnceViewBecomesReady(this, () -> {
      if (!isHidden) {
        launchRevealAnimation();
      }
    });
  }

  private int softInputMode;

  public void setSoftInputMode (int method) {
    this.softInputMode = method;
  }

  public boolean isBoundWindowShowing () {
    return needRootInsets ? getParent() != null : window != null && window.isShowing();
  }

  private boolean isDismissed;

  public void onCustomHideAnimationComplete () {
    dismissWindow();
  }

  private void dismissWindow () {
    if (!isDismissed) {
      isDismissed = true;
      ((BaseActivity) getContext()).removeWindowFromList(this);
      removeWindow();

      if (dismissListener != null) {
        dismissListener.onPopupDismiss(this);
      }

      performDestroy();
    }
  }

  @Override
  public void performDestroy () {
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View view = getChildAt(i);
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      } else if (view instanceof ViewGroup) {
        Views.destroy((ViewGroup) view);
      }
      removeViewAt(i);
    }
    if (boundController != null) {
      boundController.destroy();
    }
  }

  @Override
  public View getChildAt (int index) {
    return super.getChildAt(index);
  }

  private boolean disableCancelOnTouchDown;

  public void setDisableCancelOnTouchDown (boolean disable) {
    this.disableCancelOnTouchDown = disable;
  }

  public interface TouchDownInterceptor {
    boolean onBackgroundTouchDown (PopupLayout popupLayout, MotionEvent e);
  }

  private TouchDownInterceptor touchDownInterceptor;

  public void setTouchDownInterceptor (TouchDownInterceptor touchDownInterceptor) {
    this.touchDownInterceptor = touchDownInterceptor;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      if (factor == 1f && !disableCancelOnTouchDown) {
        if (touchDownInterceptor == null || !touchDownInterceptor.onBackgroundTouchDown(this, e))
          hideWindow(true);
      } else {
        return !(touchSectionProvider == null || touchSectionProvider.shouldTouchOutside(e.getX(), e.getY()));
      }
    }
    return true;
  }

  public void showNonAnimatedView (View view) {
    if (view == null || view.getParent() != null) {
      throw new IllegalArgumentException();
    }

    animationType = ANIMATION_TYPE_NONE;
    addView(view);
    if (getContext() instanceof BaseActivity) {
      ((BaseActivity) getContext()).showPopupWindow(this);
    }
  }

  private View boundView;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  private boolean needRevealStartDelay;

  public void showSimplePopupView (View view, int height) {
    if (view == null || view.getParent() != null) {
      throw new IllegalArgumentException();
    }

    if (view instanceof OptionsLayout && UI.getContext(getContext()).hasMoreWindow()) {
      needRevealStartDelay = true;
    }

    animationType = ANIMATION_TYPE_SIMPLE;
    view.setTranslationY(currentHeight = height);
    addView(boundView = view);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  public View getBoundView () {
    return boundView;
  }

  public void showMoreView (MenuMoreWrap menuWrap) {
    if (menuWrap == null) {
      throw new IllegalArgumentException();
    }

    if (menuWrap.getParent() != null) {
      ((ViewGroup) menuWrap.getParent()).removeView(menuWrap);
    }

    final boolean anchorCenter = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_CENTER;
    final boolean anchorRight = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT;
    final int padding = Screen.dp(8f);
    final int itemsWidth = menuWrap.getItemsWidth();
    int cx = anchorRight != Lang.rtl() ? itemsWidth - padding : Screen.dp(17f);
    int cy = menuWrap.shouldPivotBottom() ? menuWrap.getItemsHeight() - padding : padding;

    if (Config.REVEAL_ANIMATION_AVAILABLE && anchorRight) {
      animationType = ANIMATION_TYPE_MORE_REVEAL;
      menuWrap.setAlpha(0f);
      menuWrap.setScaleX(1f);
      menuWrap.setScaleY(1f);
    } else {
      animationType = ANIMATION_TYPE_MORE_SCALE;
      menuWrap.setAlpha(0f);
      menuWrap.setScaleX(MenuMoreWrap.START_SCALE);
      menuWrap.setScaleY(MenuMoreWrap.START_SCALE);
    }

    menuWrap.setPivotX(cx);
    menuWrap.setPivotY(cy);

    addView(boundView = menuWrap);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  private void hideMoreWrap () {
    MenuMoreWrap menuWrap = (MenuMoreWrap) getContentChild();

    if (menuWrap == null) {
      return;
    }

    final AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        dismissWindow();
      }
    };

    Animator menuAnimator = null;

    if (Config.REVEAL_ANIMATION_AVAILABLE && menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT) {
      try {
        menuAnimator = android.view.ViewAnimationUtils.createCircularReveal(menuWrap, (int) menuWrap.getPivotX(), (int) menuWrap.getPivotY(), menuWrap.getRevealRadius(), 0);
        menuAnimator.setInterpolator(MenuMoreWrap.REVEAL_INTERPOLATOR);
        menuAnimator.setDuration(MenuMoreWrap.REVEAL_DURATION);
      } catch (Throwable t) {
        Log.w("Cannot create circular reveal", t);
        menuAnimator = null;
      }
    }

    if (menuAnimator != null) {
      menuAnimator.addListener(listener);
      menuAnimator.start();
    } else {
      menuWrap.scaleOut(listener);
    }
  }

  public interface AnimatedPopupProvider {
    void prepareShowAnimation ();
    void launchShowAnimation (PopupLayout popup);
    boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator);
  }

  private AnimatedPopupProvider customAnimatorProvider;

  public void setAnimationProvider (AnimatedPopupProvider provider) {
    this.customAnimatorProvider = provider;
  }

  public void showAnimatedPopupView (View view, AnimatedPopupProvider provider) {
    this.animationType = ANIMATION_TYPE_CUSTOM;
    this.customAnimatorProvider = provider;
    provider.prepareShowAnimation();
    addView(boundView = view);
    ((BaseActivity) getContext()).showPopupWindow(this);
  }

  public boolean shouldDisallowScreenshots () {
    ViewController<?> c = boundController;
    if (c != null && c.shouldDisallowScreenshots()) {
      return true;
    }
    if (boundView instanceof SensitiveContentContainer) {
      return ((SensitiveContentContainer) boundView).shouldDisallowScreenshots();
    }
    return false;
  }

  private boolean isHidden;

  public boolean isWindowHidden () {
    return isHidden;
  }

  public void hideWindow (boolean animated) {
    if (isHidden) {
      return;
    }

    isHidden = true;

    if (animated) {
      launchHideAnimation();
    } else {
      if (dismissListener != null) {
        dismissListener.onPopupDismissPrepare(this);
      }
      dismissWindow();
    }
  }

  // Animation

  @Override
  protected View getMeasureTarget () {
    return boundController != null ? boundController.get() : this;
  }

  private static final int ANIMATION_TYPE_NONE = -1;
  private static final int ANIMATION_TYPE_SIMPLE = 0;
  private static final int ANIMATION_TYPE_CUSTOM = 1;
  private static final int ANIMATION_TYPE_MORE_SCALE = 2;
  private static final int ANIMATION_TYPE_MORE_REVEAL = 3;

  private int animationType;
  private boolean revealAnimationLaunched, hideAnimationLaunched;
  private int currentHeight;

  private boolean hasCompletelyShown;

  private void launchRevealAnimation () {
    if (!revealAnimationLaunched) {
      revealAnimationLaunched = true;
      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          animateRevealFactor(1f);
          break;
        }
        case ANIMATION_TYPE_CUSTOM: {
          customAnimatorProvider.launchShowAnimation(this);
          break;
        }
        case ANIMATION_TYPE_MORE_SCALE:
        case ANIMATION_TYPE_MORE_REVEAL: {
          Animator.AnimatorListener listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd (Animator animation) {
              factor = 1f;
              onCustomShowComplete();
            }
          };

          MenuMoreWrap menuWrap = (MenuMoreWrap) getContentChild();

          if (menuWrap == null) {
            return;
          }

          if (animationType == ANIMATION_TYPE_MORE_SCALE) {
            if (menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_CENTER) {
              final int itemsWidth = menuWrap.getItemsWidth();
              menuWrap.setPivotX(itemsWidth / 2f);
            }
            menuWrap.scaleIn(listener);
            return;
          }

          final boolean anchorRight = menuWrap.getAnchorMode() == MenuMoreWrap.ANCHOR_MODE_RIGHT;
          final int padding = Screen.dp(8f);
          final int itemsWidth = menuWrap.getItemsWidth();
          int cx = anchorRight != Lang.rtl() ? itemsWidth - padding : Screen.dp(17f);
          int cy = menuWrap.shouldPivotBottom() ? menuWrap.getItemsHeight() - padding : padding;

          if (animationType == ANIMATION_TYPE_MORE_REVEAL && Config.REVEAL_ANIMATION_AVAILABLE) {
            try {
              int newCx = Lang.rtl() ? (int) ((float) Screen.dp(49f) * .5f) : itemsWidth - (int) ((float) Screen.dp(49f) * .5f);
              int newCy = Size.getHeaderPortraitSize() / 2;
              if (menuWrap.shouldPivotBottom()) {
                newCy = menuWrap.getItemsHeight() - newCy;
              }
              Animator animator = android.view.ViewAnimationUtils.createCircularReveal(menuWrap, newCx, newCy, 0, menuWrap.getRevealRadius());
              animator.addListener(listener);
              animator.setInterpolator(MenuMoreWrap.REVEAL_INTERPOLATOR);
              animator.setDuration(MenuMoreWrap.REVEAL_DURATION);
              animationType = ANIMATION_TYPE_MORE_REVEAL;
              cx = newCx;
              cy = newCy;

              menuWrap.setPivotX(cx);
              menuWrap.setPivotY(cy);

              animator.start();
              menuWrap.setAlpha(1f);
              return;
            } catch (Throwable t) {
              Log.w("Cannot create circular reveal", t);
            }
          }

          menuWrap.setAlpha(0f);
          menuWrap.setScaleX(MenuMoreWrap.START_SCALE);
          menuWrap.setScaleY(MenuMoreWrap.START_SCALE);

          menuWrap.setPivotX(cx);
          menuWrap.setPivotY(cy);

          animationType = ANIMATION_TYPE_MORE_SCALE;
          menuWrap.scaleIn(listener);


          break;
        }
      }
    }
  }

  public interface PopupHeightProvider {
    int getCurrentPopupHeight ();
  }

  private @Nullable PopupHeightProvider popupHeightProvider;

  public void setPopupHeightProvider (@Nullable PopupHeightProvider popupHeightProvider) {
    this.popupHeightProvider = popupHeightProvider;
  }

  private void launchHideAnimation () {
    if (!hideAnimationLaunched) {
      hideAnimationLaunched = true;

      if (boundController != null) {
        boundController.onPrepareToDismissPopup();
      }
      if (dismissListener != null) {
        dismissListener.onPopupDismissPrepare(this);
      }

      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          if (customAnimatorProvider != null) {
            animationType = ANIMATION_TYPE_CUSTOM;
            if (customAnimatorProvider.launchHideAnimation(this, animator)) {
              break;
            }
            animationType = ANIMATION_TYPE_SIMPLE;
          }
          if (popupHeightProvider != null) {
            currentHeight = popupHeightProvider.getCurrentPopupHeight();
          } else {
            View view = getContentChild();
            currentHeight = view != null ? view.getMeasuredHeight() : 0;
          }
          animateRevealFactor(0f);
          break;
        }
        case ANIMATION_TYPE_CUSTOM: {
          if (!customAnimatorProvider.launchHideAnimation(this, animator)) {
            if (popupHeightProvider != null) {
              currentHeight = popupHeightProvider.getCurrentPopupHeight();
            } else {
              View view = getContentChild();
              currentHeight = view != null ? view.getMeasuredHeight() : 0;
            }
            animationType = ANIMATION_TYPE_SIMPLE;
            animateRevealFactor(0f);
          }
          break;
        }
        case ANIMATION_TYPE_MORE_REVEAL:
        case ANIMATION_TYPE_MORE_SCALE: {
          hideMoreWrap();
          break;
        }
      }
    }
  }

  private static final int REVEAL_ANIMATOR = 0;
  private FactorAnimator animator;

  public void animateRevealFactor (float toFactor) {
    if (animator == null) {
      animator = new FactorAnimator(REVEAL_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, factor);
    }
    if (toFactor == 1f && needRevealStartDelay) {
      animator.setStartDelay(MenuMoreWrap.REVEAL_DURATION);
    } else {
      animator.setStartDelay(0);
    }
    animator.animateTo(toFactor);
  }

  private View getContentChild () {
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View view = getChildAt(i);
      if (!(view instanceof BackgroundView)) {
        return view;
      }
    }
    return null;
  }

  private BackgroundView backgroundView;

  private class BackgroundView extends View {
    public BackgroundView (Context context) {
      super(context);
      setAlpha(0f);
    }

    @Override
    public void draw (Canvas c) {
      super.draw(c);
      if (useStatusBar) {
        final int color = ColorUtils.color((int) (255f * .3f), 0);
        c.drawRect(0, 0, getMeasuredWidth(), HeaderView.getTopOffset(), Paints.fillingPaint(color));
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      final int color = ColorUtils.color((int) (255f * (Theme.getPopupOverlayAlpha())), 0);
      int offset = HeaderView.getTopOffset();
      if (useStatusBar && offset != 0) {
        c.drawRect(0, offset, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(color));
      } else {
        c.drawColor(color);
      }
    }
  }

  private float factor;

  public void setRevealFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      switch (animationType) {
        case ANIMATION_TYPE_SIMPLE: {
          View view = getContentChild();
          if (view != null) {
            view.setTranslationY(currentHeight * (1f - factor));
          }
          break;
        }
      }
      backgroundView.setAlpha(factor);
    }
  }

  public void setHideBackground (boolean hide) {
    backgroundView.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
  }

  public boolean hadCompletelyShown () {
    return hasCompletelyShown;
  }

  public void onCustomShowComplete () {
    hasCompletelyShown = true;
    if (showListener != null) {
      showListener.onPopupCompletelyShown(this);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        setRevealFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case REVEAL_ANIMATOR: {
        if (finalFactor == 0f) {
          dismissWindow();
        } else if (finalFactor == 1f) {
          onCustomShowComplete();
        }
        break;
      }
    }
  }

  private boolean useStatusBar;

  public void addStatusBar () {
    useStatusBar = true;
  }

  // Drawing



  // We're going deeper: popups inside popups


}
