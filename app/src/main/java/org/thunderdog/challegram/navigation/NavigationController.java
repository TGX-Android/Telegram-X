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
 * File created on 23/04/2015 at 17:36
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.SimpleShapeDrawable;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Future;

public class NavigationController implements Future<View>, ThemeChangeListener, Lang.Listener {
  static final boolean DROP_SHADOW_ENABLED = true;
  static final boolean USE_PREVIEW_FADE = true;
  static final boolean USE_LAYOUT_LIMITS = false;

  static final int SLOW_HARD_TRANSITION_DURATION = 500;
  static final int HARD_TRANSITION_DURATION = 400; // 400; //400

  static final int MINIMUM_DROP_DURATION = 60; //60
  static final int MAXIMUM_DROP_DURATION = 200; //200

  static final int MINIMUM_VERTICAL_DROP_DURATION = 160;
  static final int MAXIMUM_VERTICAL_DROP_DURATION = 300;

  static final float PREVIEW_FADED_FACTOR       = .92f;
  static final float PREVIEW_FADE_CHANGE_FACTOR = .08f;

  static final boolean USE_PREVIEW_TRANSLATION = true;

  private NavigationProcessor processor;

  private RootLayout rootView;
  private NavigationLayout contentWrapper;
  private HeaderView headerView;
  private ShadowView shadowView, shadowTop;
  private View fadeView;
  private FloatingButton floatingButton;

  private boolean isAnimating;

  private ArrayList<ViewController<?>> childWrappers;
  private final Context context;

  public NavigationController (Context context) {
    this.context = context;
    processor = new NavigationProcessor(this);
  }

  public Context getContext () {
    return context;
  }

 /* @Override
  public void onActivityPause () {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityPause();
      }
    }
  }

  @Override
  public void onActivityResume () {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityResume();
      }
    }
  }

  @Override
  public void onActivityDestroy () {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityDestroy();
      }
    }
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) {
    if (childWrappers != null) {
      for (ViewController c : childWrappers) {
        c.onActivityPermissionResult(code, granted);
      }
    }
  }*/

  public void onFocus () {
    ViewController<?> c = getStack().getCurrent();
    if (c != null) {
      c.onFocus();
    }
  }

  public void onBlur () {
    ViewController<?> c = getStack().getCurrent();
    if (c != null) {
      c.onBlur();
    }
  }

  public void onPrepareToShow () {
    ViewController<?> c = getStack().getCurrent();
    if (c != null) {
      c.onPrepareToShow();
    }
  }

  public NavigationStack getStack () {
    return processor.getStack();
  }

  public int getStackSize () {
    return processor.getStackSize();
  }

  public @Nullable ViewController<?> getCurrentStackItem () {
    return getStack().getCurrent();
  }

  public @Nullable ViewController<?> getPreviousStackItem () {
    return getStack().getPrevious();
  }

  public boolean isAnimating () {
    return isAnimating;
  }

  public boolean isAnimatingWithEffect () {
    return isAnimating && translationMode != TRANSLATION_NONE;
  }

  public boolean isAnimatingBackward () {
    return isAnimating && !translatingForward && lastTranslation != 0f && lastTranslation != 1f && animatorRunning;
  }

  public boolean isEmpty () {
    return processor.getStack().size() == 0;
  }

  public HeaderView getHeaderView () {
    return headerView;
  }

  public void setShadowsVisibility (int visibility) {
    shadowTop.setVisibility(visibility);
    shadowView.setVisibility(visibility);
  }

  public FloatingButton getFloatingButton () {
    return floatingButton;
  }

  public void showFadeView () {
    if (fadeView.getParent() != null) {
      contentWrapper.removeView(fadeView);
    }
    fadeView.setVisibility(View.VISIBLE);
    contentWrapper.addView(fadeView, 1);
  }

  public void removeFadeView () {
    if (USE_PREVIEW_FADE) {
      fadeView.setVisibility(View.GONE);
      contentWrapper.removeView(fadeView);
    }
  }

  protected final void clearChildWrappers () {
    if (childWrappers != null) {
      childWrappers.clear();
      childWrappers = null;
    }
  }

  public void addChildWrapper (ViewController<?> controller) {
    if (childWrappers == null) {
      childWrappers = new ArrayList<>();
    }
    childWrappers.add(controller);

    controller.applyPlayerOffset(currentPlayerFactor, currentPlayerOffset);
    if (controller.usePopupMode()) {
      if (controller.preventRootInteractions()) {
        rootView.addView(controller.get(), 0);
      } else {
        rootView.addView(controller.get(), rootView.getChildCount() - 2);
      }
    } else {
      contentWrapper.addView(controller.get());
    }
    controller.attachNavigationController(this);
    controller.onPrepareToShow();
    controller.onAttachStateChanged(this, true);
    checkDisallowScreenshots();
  }

  public void addChildWrapper (ViewController<?> controller, int index) {
    if (childWrappers == null) {
      childWrappers = new ArrayList<>();
    }
    childWrappers.add(index, controller);

    if (controller.usePopupMode()) {
      rootView.addView(controller.get(), 0);
    } else {
      contentWrapper.addView(controller.get(), index);
    }
    controller.attachNavigationController(this);
    controller.onPrepareToShow();
    controller.onAttachStateChanged(this, true);
    checkDisallowScreenshots();
  }

  public void removeChildWrapper (ViewController<?> controller) {
    if (childWrappers != null) {
      childWrappers.remove(controller);
    }

    if (controller.usePopupMode()) {
      rootView.removeView(controller.get());
    } else {
      contentWrapper.removeView(controller.get());
    }
    controller.onCleanAfterHide();
    controller.detachNavigationController();
    controller.onAttachStateChanged(this, false);
    checkDisallowScreenshots();
  }

  public void removeChildren () {
    if (childWrappers != null) {
      for (int i = childWrappers.size() - 1; i >= 0; i--) {
        removeChildWrapper(childWrappers.get(i));
      }
    }

    contentWrapper.removeAllViews();
    checkDisallowScreenshots();
  }

  public void onKeyboardStateChanged (boolean visible) {
    if (childWrappers != null) {
      for (ViewController<?> c : childWrappers) {
        c.onKeyboardStateChanged(visible);
      }
    }
  }

  private void checkDisallowScreenshots () {
    UI.getContext(context).checkDisallowScreenshots();
  }

  public int getId () {
    return R.id.controller_navigation;
  }

  private View wrap;
  private boolean isAttachedToWindow;

  @Override
  public View get () {
    if (wrap == null) {
      wrap = onCreateView(context);
      wrap.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow (View v) {
          isAttachedToWindow = true;
        }

        @Override
        public void onViewDetachedFromWindow (View v) {
          isAttachedToWindow = false;
          // TODO force end animations?
        }
      });
      wrap.setTag(this);
      ThemeManager.instance().addThemeListener(this);
    }
    return wrap;
  }

  public boolean shouldDisallowScreenshots () {
    if (childWrappers != null) {
      for (ViewController<?> controller : childWrappers) {
        if (controller.shouldDisallowScreenshots()) {
          return true;
        }
      }
    }
    return false;
  }

  protected View onCreateView (Context context) {
    rootView = new RootLayout(context);
    rootView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    rootView.setId(R.id.nav_root);

    if (DROP_SHADOW_ENABLED) {
      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(Size.getNavigationShadowSize(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
      params.setMargins(0, HeaderView.getSize(true), 0, 0);

      shadowView = new ShadowView(context);
      shadowView.setLayoutParams(params);
      if (Lang.rtl()) {
        shadowView.setSimpleRightShadow(false);
      } else {
        shadowView.setSimpleLeftShadow(false);
      }
      shadowView.setVisibility(View.GONE);

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getNavigationShadowvertSize(), Gravity.TOP);

      shadowTop = new ShadowView(context);
      shadowTop.setSimpleTopShadow(true);
      shadowTop.setVisibility(View.GONE);
      shadowTop.setLayoutParams(params);
    }

    if (USE_PREVIEW_FADE) {
      fadeView = new View(context);
      fadeView.setBackgroundColor(0xff000000);
      fadeView.setVisibility(View.GONE);
      fadeView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    contentWrapper = new NavigationLayout(context);
    contentWrapper.setController(this);
    contentWrapper.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentWrapper.setId(R.id.nav_wrapper);

    headerView = new HeaderView(context);
    headerView.initWithController(this);
    headerView.setId(R.id.nav_header);

    rootView.addView(contentWrapper);

    if (DROP_SHADOW_ENABLED) {
      rootView.addView(shadowView);
      rootView.addView(shadowTop);
    }

    rootView.addView(headerView);

    floatingButton = new FloatingButton(context);
    floatingButton.setOnClickListener(v -> {
      ViewController<?> c = processor.getStack().getCurrent();
      if (c != null) {
        c.onFloatingButtonPressed();
      }
    });
    rootView.addView(floatingButton);

    Lang.addLanguageListener(this);

    return rootView;
  }

  public void addViewUnderHeader (View view) {
    rootView.addView(view, rootView.indexOfChild(headerView));
  }

  private ThemeListenerList themeListeners;

  public ThemeListenerList getThemeListeners () {
    if (themeListeners == null) {
      themeListeners = new ThemeListenerList();
    }
    return themeListeners;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    ViewController<?> c;
    if (isAnimating && ((translatingForward && translationFactor == 1f) || (!translatingForward && translationFactor == 0f))) {
      c = getPreviousStackItem();
    } else {
      c = getCurrentStackItem();
    }
    if (c != null) {
      headerView.resetColors(c, null);
    }
    if (themeListeners != null) {
      themeListeners.onThemeColorsChanged(areTemp);
    }
  }

  private boolean isDestroyed;

  public boolean isDestroyed () {
    return isDestroyed;
  }

  public void reuse () {
    getStack().clear(this);
    clearChildWrappers();
  }

  public void destroy () {
    isDestroyed = true;
    ThemeManager.instance().removeThemeListener(this);
    getStack().clear(this);
    clearChildWrappers();
    Lang.removeLanguageListener(this);
    if (headerView != null)
      headerView.performDestroy();
    if (contentWrapper != null)
      contentWrapper.performDestroy();
    if (floatingButton != null)
      floatingButton.performDestroy();
  }

  // Single

  public void insertController (ViewController<?> controller, int index) {
    if (!isAnimating && controller != null) {
      processor.insertController(controller, index);
    }
  }

  public void initController (ViewController<?> with) {
    if (!isAnimating && with != null) {
      NavigationStack stack = getStack();

      with.get();

      stack.clear(this);
      stack.push(with, true);

      removeChildren();
      addChildWrapper(with);
      processor.callFocusDelayed(with);
      getHeaderView().setTitle(with);

      if (with.usePopupMode()) {
        with.applyCustomHeaderAnimations(1f);
      }
    }
  }

  public final void setController (ViewController<?> controller) {
    if (!isAnimating && controller != null) {
      if (getStack().isEmpty()) {
        initController(controller);
      } else {
        processor.setController(controller);
      }
    }
  }

  public ViewController<?> getPendingController () {
    return processor.getFutureRebaseController();
  }

  public void setControllerAnimated (ViewController<?> controller, boolean asForward, boolean saveFirst) {
    if (controller != null) {
      if (getStack().isEmpty()) {
        initController(controller);
      } else {
        processor.setControllerAnimated(controller, asForward, saveFirst);
      }
    }
  }

  void rebaseStack (final ViewController<?> item, boolean asForward, final boolean saveFirst) {
    if (asForward) {
      navigate(item, saveFirst ? MODE_REBASE | MODE_ARG_SAVE_FIRST : MODE_REBASE);
    } else {
      navigate(item, saveFirst ? MODE_REBASE | MODE_ARG_SAVE_FIRST | MODE_FADE : MODE_REBASE | MODE_FADE);
    }
  }

  // Pairs

  public boolean passBackPressToActivity (boolean fromTop) {
    ViewController<?> c = getStack().getCurrent();
    return c != null && c.passBackPressToActivity(fromTop);
  }

  public boolean onBackPressed (boolean fromTop) {
    if (headerView.inSelectMode()) {
      headerView.closeSelectMode(true, true);
      return true;
    }
    ViewController<?> c = getStack().getCurrent();
    if (headerView.inSearchMode()) {
      if (c != null && c.closeSearchModeByBackPress(fromTop)) {
        return true;
      }
      headerView.closeSearchMode(true, null); // delayed: (c != null && c.inChatSearchMode())
      return true;
    }
    /*if (headerView.inCustomMode()) {
      headerView.closeCustomMode();
      return true;
    }*/
    if (c != null) {
      if (c.onBackPressed(fromTop)) {
        return true;
      }
      if (getStackSize() > 1) {
        navigateBack();
        return true;
      }
    }
    return false;
  }

  private boolean isCurrentControllerAnimating () {
    ViewController<?> c = getStack().getCurrent();
    return c != null && c.isTransforming();
  }

  final void hideContextualPopups () {
    UI.getContext(context).hideContextualPopups(true);
    DrawerController c = UI.getDrawer(context);
    if (c != null) {
      c.close(0f, null);
    }
  }

  public final boolean navigateTo (ViewController<?> controller) {
    if (!isAnimating && getStackSize() > 0 && controller != null && !isCurrentControllerAnimating()) {
      isAnimating = true;
      processor.navigateTo(controller);
      return true;
    } else {
      return false;
    }
  }

  public final boolean navigateBack () {
    if (!isAnimating && getStack().getCurrentIndex() > 0 && !isCurrentControllerAnimating()) {
      processor.navigateBack();
      return true;
    } else {
      return false;
    }
  }

  private static final int TRANSLATION_HORIZONTAL = 1;
  private static final int TRANSLATION_VERTICAL = 2;
  private static final int TRANSLATION_FADE = 3;
  private static final int TRANSLATION_NONE = 4;

  private int translationMode;
  private boolean translatingForward;
  private float fromHeaderHeight;
  private float currentWidth, currentHeight, currentPrevWidth;
  private float lastTranslation, lastHeaderFactor;
  private ViewController<?> currentLeft, currentRight;
  private View leftWrap, rightWrap;

  private void preventPopupMode (ViewController<?> current) {
    if (!current.preventRootInteractions()) {
      rootView.removeView(current.get());
      rootView.addView(current.get(), 0);
    }
    headerView.getFilling().setCollapsed(false);
  }

  private void applyPopupMode (ViewController<?> current) {
    if (!current.preventRootInteractions()) {
      rootView.removeView(current.get());
      rootView.addView(current.get(), rootView.getChildCount() - 2);
    }
    headerView.getFilling().setCollapsed(true);
  }

  private float currentPlayerFactor;
  private float currentPlayerOffset;

  protected boolean applyPlayerOffset (float factor, float top) {
    if (currentPlayerFactor != factor) {
      currentPlayerFactor = factor;
      currentPlayerOffset = top;
      if (childWrappers != null) {
        for (ViewController<?> c : childWrappers) {
          c.applyPlayerOffset(factor, top);
        }
      }
      return true;
    }
    return false;
  }

  private void prepareHeaderAnimation (ViewController<?> left, ViewController<?> right, boolean forward, int direction) {
    lastHeaderFactor = forward ? 1f : 0f;
    headerView.openPreview(left, right, forward, direction, lastHeaderFactor);

    if (!forward && right.usePopupMode()) {
      fromHeaderHeight = left.getHeaderHeight();
    } else {
      fromHeaderHeight = headerView.getCurrentHeight();
    }
  }

  public void prepareFactorAnimation (ViewController<?> left, ViewController<?> right, boolean forward, int direction) {
    preventLayout();
    if (Views.HARDWARE_LAYER_ENABLED) {
      setLayerType(View.LAYER_TYPE_HARDWARE, left, right);
    }

    if (forward) {
      right.applyPlayerOffset(currentPlayerFactor, currentPlayerOffset);
      addChildWrapper(right);
      if (left.usePopupMode()) {
        preventPopupMode(left);
      }
    } else {
      left.applyPlayerOffset(currentPlayerFactor, currentPlayerOffset);
      addChildWrapper(left, 0);
    }

    if (USE_PREVIEW_FADE) {
      if (direction != TRANSLATION_FADE && direction != TRANSLATION_NONE && !left.usePopupMode()) {
        showFadeView();
      }
    }

    translationFactor = forward ? 1f : 0f;

    currentLeft = left;
    currentRight = right;
    leftWrap = left.get();
    rightWrap = right.get();
    translationMode = direction;
    translatingForward = forward;

    currentWidth = rootView.getMeasuredWidth();
    currentHeight = rootView.getMeasuredHeight();
    currentPrevWidth = -(currentWidth / Size.NAVIGATION_PREVIEW_TRANSLATE_FACTOR);

    if (forward) {
      currentLeft.onBlur();

      switch (translationMode) {
        case TRANSLATION_NONE: {
          lastTranslation = 0;
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(1f);
          leftWrap.setAlpha(1f);
          headerView.getFilling().setRestorePixels(false, 0f, 0);
          break;
        }
        case TRANSLATION_FADE: {
          lastTranslation = 0;
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(0f);
          headerView.getFilling().setRestorePixels(false, 0f, 0);
          break;
        }
        case TRANSLATION_VERTICAL: {
          lastTranslation = currentHeight;

          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(currentHeight);

          headerView.getFilling().setRestorePixels(false, 0f, 0);

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(0f);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowTop.setTranslationY(currentHeight - Size.getNavigationShadowvertSize());
            shadowTop.setAlpha(1f);
            shadowTop.setVisibility(right.useDropShadow() ? View.VISIBLE : View.GONE);
          }

          break;
        }
        case TRANSLATION_HORIZONTAL: {
          lastTranslation = currentWidth;
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(currentWidth);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(1f);

          headerView.getFilling().setRestorePixels(left.usePopupMode(), 0f, left.getPopupRestoreColor());

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(0f);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowView.setTranslationX(currentWidth - Size.getNavigationShadowSize());
            shadowView.setAlpha(PREVIEW_FADED_FACTOR);
            shadowView.setVisibility(View.VISIBLE);
          }

          break;
        }
      }
    } else {
      lastTranslation = 0f;
      currentRight.onBlur();

      leftWrap.setVisibility(View.VISIBLE);

      switch (translationMode) {
        case TRANSLATION_NONE: {
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);
          rightWrap.setAlpha(1f);
          leftWrap.setAlpha(1f);

          headerView.getFilling().setRestorePixels(false, 0f, 0);

          break;
        }
        case TRANSLATION_FADE: {
          leftWrap.setTranslationX(0f);
          rightWrap.setTranslationX(0f);
          rightWrap.setTranslationY(0f);

          headerView.getFilling().setRestorePixels(false, 0f, 0);
          break;
        }
        case TRANSLATION_VERTICAL: {
          rightWrap.setTranslationY(0f);
          rightWrap.setTranslationX(0f);
          leftWrap.setTranslationX(0f);
          leftWrap.setTranslationY(0f);

          headerView.getFilling().setRestorePixels(false, 0f, 0);

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(PREVIEW_FADE_CHANGE_FACTOR);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowTop.setAlpha(1f);
            shadowTop.setTranslationY(-Size.getNavigationShadowvertSize());
            shadowTop.setVisibility(right.useDropShadow() ? View.VISIBLE : View.GONE);
          }

          break;
        }
        case TRANSLATION_HORIZONTAL: {
          rightWrap.setTranslationX(0f);
          if (USE_PREVIEW_TRANSLATION) {
            leftWrap.setTranslationX(currentPrevWidth);
          } else {
            leftWrap.setTranslationX(0f);
          }
          leftWrap.setAlpha(1f);

          headerView.getFilling().setRestorePixels(left.usePopupMode(), -currentPrevWidth, left.getPopupRestoreColor());

          if (USE_PREVIEW_FADE) {
            if (!left.usePopupMode()) {
              fadeView.setAlpha(PREVIEW_FADE_CHANGE_FACTOR);
              fadeView.setVisibility(View.VISIBLE);
            }
          }

          if (DROP_SHADOW_ENABLED) {
            shadowView.setAlpha(1f);
            shadowView.setTranslationX(-Size.getNavigationShadowSize());
            shadowView.setVisibility(View.VISIBLE);
          }

          break;
        }
      }
    }

    prepareHeaderAnimation(left, right, forward, direction);

    layoutIfRequested();
    onAfterShow();

    preventNextLayouts(forward ? 1 : 2);
  }

  private void onAfterShow () {
    if (childWrappers != null) {
      int size = childWrappers.size();
      for (int i = size - 1; i >= 0; i--) {
        childWrappers.get(i).onAfterShow();
      }
    }
  }

  void navigate (final ViewController<?> controller, int mode) {
    hideContextualPopups();

    final boolean forward = (mode & MODE_BACKWARD) != MODE_BACKWARD;
    final boolean fastAnimation;
    int direction;

    if ((mode & MODE_FADE) == MODE_FADE) {
      direction = TRANSLATION_FADE;
      fastAnimation = false;
    } else {
      if (forward) {
        fastAnimation = controller.forceFastAnimation();
        direction = fastAnimation || controller.forceFadeMode() ? TRANSLATION_FADE : controller.usePopupMode() ? TRANSLATION_VERTICAL : TRANSLATION_HORIZONTAL;
      } else {
        ViewController<?> current = getStack().getCurrent();
        fastAnimation = controller.forceFastAnimation();
        direction = fastAnimation || current == null || current.forceFadeMode() ? TRANSLATION_FADE : current.usePopupMode() ? TRANSLATION_VERTICAL : TRANSLATION_HORIZONTAL;
      }
      if (Settings.instance().needReduceMotion() && (direction == TRANSLATION_HORIZONTAL)) {
        direction = TRANSLATION_FADE;
      }
    }

    int rebase = 0;

    if ((mode & MODE_REBASE) == MODE_REBASE) {
      rebase = MODE_REBASE;
      if ((mode & MODE_ARG_SAVE_FIRST) == MODE_ARG_SAVE_FIRST) {
        rebase |= MODE_ARG_SAVE_FIRST;
      }
    }

    if (USE_ASYNC_IN_STEP_2 || !controller.needAsynchronousAnimation() || !isAttachedToWindow) {
      controller.executeAnimationReadyListeners();
      animateNavigate(controller, direction, forward, rebase, fastAnimation);
    } else {
      final int directionFinal = direction;
      final int rebaseFinal = rebase;
      final long timeout = controller.getAsynchronousAnimationTimeout(fastAnimation);
      controller.scheduleAnimation(new Runnable() {
        @Override
        public void run () {
          if (controller.getScheduledAnimation() == this) {
            controller.resetScheduledAnimation();
            if (isDestroyed) {
              controller.destroy();
            } else {
              controller.executeAnimationReadyListeners();
              animateNavigate(controller, directionFinal, forward, rebaseFinal, fastAnimation);
            }
          }
        }
      }, timeout);
    }
  }

  static final int MODE_BACKWARD = 0x01;
  static final int MODE_FORWARD = 0x02;
  static final int MODE_REBASE = 0x04;
  static final int MODE_FADE = 0x08;
  static final int MODE_ARG_SAVE_FIRST = 0x10;

  private boolean animatorRunning;

  private static final boolean USE_ASYNC_IN_STEP_2 = false;

  void animateNavigate (final ViewController<?> controller, int direction, final boolean forward, final int rebase, final boolean fastAnimation) {
    final ViewController<?> left, right;

    if (forward) {
      prepareFactorAnimation(left = getStack().getPrevious(), right = controller, true, direction);
    } else {
      prepareFactorAnimation(left = controller, right = getStack().getCurrent(), false, direction);
    }

    Runnable onDone = () -> {
      if (forward) {
        removeFadeView();
        if (rebase != 0) {
          headerView.applyPreview(controller);
          processor.rebaseStack(controller, (rebase & MODE_ARG_SAVE_FIRST) == MODE_ARG_SAVE_FIRST);
        } else {
          headerView.applyPreview(getStack().getCurrent());
          processor.setNextAsCurrent();
        }
        completeNextLayout();
        if (Views.HARDWARE_LAYER_ENABLED) {
          setLayerType(View.LAYER_TYPE_NONE, left, right);
        }
        updateHackyViews();
      } else {
        finishTransaction(FINISH_NAVIGATION);
      }
    };

    if (!isAttachedToWindow) {
      setFactor(forward ? 0f : 1f);
      onDone.run();
      return;
    }

    final ValueAnimator animator;

    this.animatorRunning = true;

    animator = AnimatorUtils.simpleValueAnimator();
    if (forward) {
      animator.addUpdateListener(animation -> setFactor(1f - AnimatorUtils.getFraction(animation)));
    } else {
      animator.addUpdateListener(animation -> setFactor(AnimatorUtils.getFraction(animation)));
    }

    switch (direction) {
      case TRANSLATION_FADE: {
        animator.setDuration(Config.DEBUG_NAV_ANIM ? 1000l : fastAnimation ? 120l : 180l);
        animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
        break;
      }
      case TRANSLATION_VERTICAL: {
        animator.setDuration(forward ? HARD_TRANSITION_DURATION : SLOW_HARD_TRANSITION_DURATION);
        animator.setInterpolator(AnimatorUtils.NAVIGATION_INTERPOLATOR);
        break;
      }
      case TRANSLATION_HORIZONTAL: {
        animator.setDuration(HARD_TRANSITION_DURATION);
        animator.setInterpolator(AnimatorUtils.NAVIGATION_INTERPOLATOR);
        break;
      }
    }

    animator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        onDone.run();
      }
    });

    if (!fastAnimation) {
      if (forward) {
        animator.setStartDelay(left != null && left.usePopupMode() ? 120l : controller.getStartDelay(true));
      } else {
        animator.setStartDelay(right != null && right.usePopupMode() ? 120l : controller.getStartDelay(false));
      }
    }

    if (USE_ASYNC_IN_STEP_2 && forward && right.needAsynchronousAnimation()) {
      final long timeout = right.getAsynchronousAnimationTimeout(fastAnimation);
      controller.scheduleAnimation(new Runnable() {
        @Override
        public void run () {
          if (controller.getScheduledAnimation() == this) {
            controller.resetScheduledAnimation();
            AnimatorUtils.startAnimator(right.get(), animator);
          }
        }
      }, timeout);
    } else {
      AnimatorUtils.startAnimator(forward ? right.get() : left.get(), animator);
    }
  }

  // Anim

  void setIsAnimating (boolean animating) {
    if (isAnimating != animating) {
      isAnimating = animating;
      ((BaseActivity) context).setIsKeyboardBlocked(animating);
      UI.getContext(context).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_NAVIGATING, animating);
      if (actionMode != null) {
        ((android.view.ActionMode) actionMode).finish();
      }
      if (!animating) {
        processor.checkRebaseMessage();
      }
    }
  }

  private void clearPreview () {
    setFactor(0f);

    headerView.clearPreview();

    removeChildWrapper(currentLeft);

    if (DROP_SHADOW_ENABLED) {
      switch (translationMode) {
        case TRANSLATION_HORIZONTAL: {
          shadowView.setVisibility(View.GONE);
          break;
        }
        case TRANSLATION_VERTICAL: {
          shadowTop.setVisibility(View.GONE);
          break;
        }
      }
    }

    if (USE_PREVIEW_FADE) {
      if (!currentLeft.usePopupMode()) {
        removeFadeView();
      }
    }

    currentRight.onFocus();

    currentLeft = null;
    currentRight = null;

    leftWrap = null;
    rightWrap = null;
  }

  boolean openPreview (float y) {
    return openPreview(y, false);
  }

  public boolean forcePreviewPreviouewItem () {
    return openPreview(0, true);
  }

  public void closePreviousPreviewItem () {
    closePreview(0f);
  }

  private boolean openPreview (float y, boolean force) {
    if (getStack().getCurrentIndex() <= 0) {
      processor.clearAnimation();
      return false;
    }

    if (isAnimating) {
      return false;
    }

    final ViewController<?> right = getStack().getCurrent();

    if (right == null) {
      return false;
    }

    setIsAnimating(true);

    if (right.usePopupMode()) {
      prepareFactorAnimation(getStack().getPrevious(), right, false, force ? TRANSLATION_NONE : TRANSLATION_VERTICAL);
    } else {
      prepareFactorAnimation(getStack().getPrevious(), right, false, force ? TRANSLATION_NONE : TRANSLATION_HORIZONTAL);
    }

    return true;
  }

  void closePreview (float velocity) {
    if (isAnimating && getStack().getCurrentIndex() > 0) {
      if (lastTranslation == 0f || translationMode == TRANSLATION_NONE) {
        forceClosePreview();
        return;
      }

      if (translationMode == TRANSLATION_VERTICAL) {
        translationFactor = lastTranslation / currentHeight;
      } else {
        translationFactor = lastTranslation / currentWidth;
      }

      ValueAnimator animator;

      final float startFactor = getFactor();
      animator = AnimatorUtils.simpleValueAnimator();
      animator.addUpdateListener(animation -> setFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          finishTransaction(FINISH_PREVIEW);
        }
      });

      if (translationMode == TRANSLATION_VERTICAL) {
        animator.setDuration(calculateDropDuration(lastTranslation, velocity, MAXIMUM_VERTICAL_DROP_DURATION, MINIMUM_VERTICAL_DROP_DURATION));
      } else {
        animator.setDuration(calculateDropDuration(lastTranslation, velocity, MAXIMUM_DROP_DURATION, MINIMUM_DROP_DURATION));
      }
      animator.start();
    }
  }

  void forceClosePreview () {
    finishTransaction(FINISH_PREVIEW_FORCE);
  }

  public void preventNextLayouts (int limit) {
    if (USE_LAYOUT_LIMITS) {
      rootView.preventNextLayouts(limit);
      headerView.preventNextLayouts(limit);
      contentWrapper.preventNextLayouts(limit);
    }
  }

  public void completeNextLayout () {
    if (USE_LAYOUT_LIMITS) {
      rootView.completeNextLayout();
      headerView.completeNextLayout();
      contentWrapper.completeNextLayout();
    }
  }

  public void preventLayout () {
    rootView.preventLayout();
    headerView.preventLayout();
    contentWrapper.preventLayout();
  }

  public void setLayerType (final int type, final ViewController<?> left, final ViewController<?> right) {
    if (shadowView != null) {
      Views.setLayerType(shadowView, type);
    }
    if (fadeView != null/* && (left == null || left.allowHardwareMode() || type != View.LAYER_TYPE_HARDWARE) && (right == null || right.allowHardwareMode() || type != View.LAYER_TYPE_HARDWARE)*/) {
      Views.setLayerType(fadeView, type);
    }
    if (shadowTop != null) {
      Views.setLayerType(shadowTop, type);
    }
    if (floatingButton != null && !SimpleShapeDrawable.USE_SOFTWARE_SHADOW) {
      Views.setLayerType(floatingButton, type == View.LAYER_TYPE_HARDWARE && SimpleShapeDrawable.USE_SOFTWARE_SHADOW ? View.LAYER_TYPE_SOFTWARE : type);
    }
    if (left != null && left.allowLayerTypeChanges()) {
      Views.setLayerType(left.get(), type);
    }
    if (right != null && right.allowLayerTypeChanges()) {
      Views.setLayerType(right.get(), type);
    }
  }

  public void layoutIfRequested () {
    rootView.layoutIfRequested();
    headerView.layoutIfRequested();
    contentWrapper.layoutIfRequested();
  }

  public void cancelLayout () {
    rootView.cancelLayout();
    headerView.cancelLayout();
    contentWrapper.cancelLayout();
  }

  private static final int FINISH_NAVIGATION = 1;
  private static final int FINISH_PREVIEW = 2;
  private static final int FINISH_PREVIEW_FORCE = 3;

  private void finishTransaction (int mode) {
    completeNextLayout();

    preventLayout();
    ViewController<?> current = getStack().getPrevious();
    removeFadeView();

    if (Views.HARDWARE_LAYER_ENABLED) {
      setLayerType(View.LAYER_TYPE_NONE, currentLeft, currentRight);
    }

    switch (mode) {
      case FINISH_NAVIGATION: {
        headerView.applyPreview(current);
        if (current != null && current.usePopupMode()) {
          applyPopupMode(current);
        }
        processor.setPrevAsCurrent();
        break;
      }
      case FINISH_PREVIEW: {
        headerView.clearPreview();
        final ViewController<?> previous = getStack().getCurrent();
        if (previous != null) {
          previous.onFocus();
        }
        processor.removePrevious(current);
        break;
      }
      case FINISH_PREVIEW_FORCE: {
        clearPreview();
        processor.removePrevious(current);
        break;
      }
    }

    layoutIfRequested();

    currentLeft = null;
    currentRight = null;

    leftWrap = null;
    rightWrap = null;

    updateHackyViews();
  }

  void applyPreview (float velocity) {
    if (isAnimating && getStack().getCurrentIndex() > 0) {
      if (translationMode == TRANSLATION_VERTICAL) {
        translationFactor = lastTranslation / currentHeight;
      } else {
        translationFactor = lastTranslation / currentWidth;
      }

      ValueAnimator animator;

      final float startFactor = getFactor();
      final float diffFactor = 1f - startFactor;
      animator = AnimatorUtils.simpleValueAnimator();
      animator.addUpdateListener(animation -> setFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          finishTransaction(FINISH_NAVIGATION);
        }
      });

      if (translationMode == TRANSLATION_VERTICAL) {
        animator.setDuration(calculateDropDuration(currentHeight - lastTranslation, velocity, MAXIMUM_VERTICAL_DROP_DURATION, MINIMUM_VERTICAL_DROP_DURATION));
      } else {
        animator.setDuration(calculateDropDuration(currentWidth - lastTranslation, velocity, MAXIMUM_DROP_DURATION, MINIMUM_DROP_DURATION));
      }
      animator.start();
    }
  }

  private float translationFactor;

  public void setFactor (float factor) {
    if (this.translationFactor == factor) return;

    factor = MathUtils.clamp(factor);

    this.translationFactor = factor;

    float px;
    if (translationMode == TRANSLATION_VERTICAL) {
      px = Math.round(currentHeight * factor);
    } else {
      px = currentWidth * factor;
    }

    lastTranslation = px;

    switch (translationMode) {
      case TRANSLATION_FADE: {
        headerView.setTranslation(factor);
        rightWrap.setAlpha(1f - factor);

        break;
      }
      case TRANSLATION_HORIZONTAL: {
        headerView.setTranslation(factor);
        factor = 1f - factor;

        float px2 = factor * currentPrevWidth;

        if (Lang.rtl()) {
          rightWrap.setTranslationX(-px);
          currentRight.onTranslationChanged(-px);

          if (USE_PREVIEW_TRANSLATION) {
            currentLeft.get().setTranslationX(-px2);
            currentLeft.onTranslationChanged(-px2);
          }
        } else {
          rightWrap.setTranslationX(px);
          currentRight.onTranslationChanged(px);

          if (USE_PREVIEW_TRANSLATION) {
            currentLeft.get().setTranslationX(px2);
            currentLeft.onTranslationChanged(px2);
          }
        }

        if (currentLeft.usePopupMode()) {
          headerView.getFilling().restorePixels(-px2);
        }

        if (USE_PREVIEW_FADE) {
          if (!currentLeft.usePopupMode()) {
            fadeView.setAlpha(factor * PREVIEW_FADE_CHANGE_FACTOR);
          }
        }

        if (DROP_SHADOW_ENABLED) {
          //noinspection ResourceType
          shadowView.setTranslationX(Lang.rtl() ? -px + currentWidth : px - Size.getNavigationShadowSize());
          shadowView.setAlpha(.65f + .45f * factor);
        }


        break;
      }
      case TRANSLATION_VERTICAL: {
        rightWrap.setTranslationY(px);

        if (USE_PREVIEW_FADE) {
          if (!currentLeft.usePopupMode()) {
            fadeView.setAlpha((1f - factor) * PREVIEW_FADE_CHANGE_FACTOR);
          }
        }

        if (DROP_SHADOW_ENABLED) {
          shadowTop.setTranslationY(px - Size.getNavigationShadowvertSize());
          shadowTop.setAlpha(1f);
        }

        headerView.getFilling().collapseFilling(px);
        if (px < fromHeaderHeight + HeaderView.getTopOffset()) {
          if (currentRight.getPopupRestoreColor() == 0xff000000) { // FIXME: 01/09/15 find out why really this line appears
            leftWrap.setAlpha(0f);
          }
          headerView.setTranslation(lastHeaderFactor = px / (fromHeaderHeight + HeaderView.getTopOffset()));
        } else if (lastHeaderFactor != 1f) {
          leftWrap.setAlpha(1f);
          lastHeaderFactor = 1f;
          headerView.setTranslation(1f);
        }

        // currentRight.applyCustomAnimations(1f - factor);

        break;
      }
    }

    updateHackyViews();
  }

  private void updateHackyViews () {
    ((BaseActivity) context).updateHackyOverlaysPositions();
  }

  public int getTotalTranslate () {
    return rootView.getMeasuredWidth();
  }

  public int getHorizontalTranslate () {
    if (isAnimating && translationMode == TRANSLATION_HORIZONTAL) {
      return (int) ((float) rootView.getMeasuredWidth() * translationFactor);
    }
    return 0;
  }

  public float getFadeAlpha () {
    return isAnimating && translationMode == TRANSLATION_FADE ? 1f - lastTranslation : 1f;
  }

  public float getFactor () {
    return translationFactor;
  }

  final void translatePreview (float px) {
    if (isAnimating) {
      if (Lang.rtl() && translationMode == TRANSLATION_HORIZONTAL) {
        setFactor(-px / currentWidth);
      } else {
        setFactor(px / currentWidth);
      }
    }
  }

  public final void configurationChanged (Configuration newConfig) {
    for (ViewController<?> controller : getStack().getAll()) {
      controller.onConfigurationChanged(newConfig);
    }
  }

  private Object actionMode;

  public void setActionMode (Object mode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
      actionMode = mode;
    }
  }

  /*// Instance state

  private static final String KEY_STACK_SIZE = "_n_sz_";
  private static final String KEY_STACK_ITEM_ID = "_n_sid_";

  public void saveInstanceState (Bundle out) {
    NavigationStack stack = getStack();
    int stackSize = stack.size();

    out.putInt(KEY_STACK_SIZE, stackSize);

    if (stackSize > 1) {
      for (int i = 0; i < stackSize; i++) {
        ViewController c;
        c = stack.get(i);
        if (c == null) {
          throw new RuntimeException("Error saving stack: stack.get is null");
        }
        out.putInt(KEY_STACK_ITEM_ID + i, c.getId());
        c.onSaveInstanceState(i, out);
      }
    }
  }

  public int restoreStackSize (Bundle in) {
    return in.getInt(KEY_STACK_SIZE, 0);
  }

  public int restoreStackItemId (Bundle in, int id) {
    return in.getInt(KEY_STACK_ITEM_ID + id, 0);
  }

  public boolean restoreStackItem (int id, ViewController c, Bundle in) {
    return c.onRestoreInstanceState(id, in);
  }*/

  // Utils

  public static int calculateDropDuration (float length, float velocity, int max, int min) {
    return velocity <= 0 ? max : Math.min(Math.max(Math.round(length / (velocity / 1000f)), min), max);
  }

  // Language handling

  /*@Override
  public void onRtlChange (boolean isRtl) {
    for (ViewController controller : getStack().getAll()) {
      controller.onRtlChange(isRtl);
    }
    if (shadowView != null) {
      if (isRtl) {
        shadowView.setSimpleRightShadow(false);
      } else {
        shadowView.setSimpleLeftShadow(false);
      }
    }
    DrawerController drawer = UI.getDrawer(context);
    if (drawer != null) {
      drawer.onRtlChange(isRtl);
    }
  }*/

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    boolean directionChanged = Lang.hasDirectionChanged(event, arg1);

    if (directionChanged) {
      if (Views.setGravity(floatingButton, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)))
        Views.updateLayoutParams(floatingButton);
      if (shadowView != null) {
        if (Lang.rtl()) {
          shadowView.setSimpleRightShadow(false);
        } else {
          shadowView.setSimpleLeftShadow(false);
        }
      }
    }

    if (headerView != null) {
      headerView.onLanguagePackEvent(event, arg1);
    }
    // TODO more
  }
}
