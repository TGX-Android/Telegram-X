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
 * File created on 27/05/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ConnectionState;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.GlobalConnectionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class NetworkStatusBarView extends FrameLayoutFix implements Destroyable, Screen.StatusBarHeightChangeListener, GlobalConnectionListener, FactorAnimator.Target, PopupLayout.TouchSectionProvider, Runnable, BaseActivity.ActivityListener, GlobalAccountListener, RootFrameLayout.InsetsChangeListener {
  private final ProgressComponentView progressView;
  private final TextView textView;
  private final LinearLayout statusWrap;
  private final FillingDrawable backgroundDrawable;

  public NetworkStatusBarView (Context context) {
    super(context);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.getStatusBarHeight()));
    Screen.addStatusBarHeightListener(this);

    int color = Theme.getColor(Config.STATUS_BAR_TEXT_COLOR_ID);

    progressView = new ProgressComponentView(context);
    progressView.initSmall(1f);
    progressView.setProgressColor(color);
    progressView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(24f), ViewGroup.LayoutParams.MATCH_PARENT));

    textView = new NoScrollTextView(context);
    textView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    textView.setGravity(Gravity.CENTER_VERTICAL);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    textView.setTextColor(color);

    statusWrap = new LinearLayout(context);
    statusWrap.setOrientation(LinearLayout.HORIZONTAL);
    if (Lang.rtl()) {
      statusWrap.addView(textView);
      statusWrap.addView(progressView);
    } else {
      statusWrap.addView(progressView);
      statusWrap.addView(textView);
    }
    statusWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

    addView(statusWrap);
    backgroundDrawable = ViewSupport.setThemedBackground(this, ColorId.statusBar);


    TdlibManager.instance().global().addAccountListener(this);
    TdlibManager.instance().global().addConnectionListener(this);
    updateNetworkState(TdlibManager.instance().current());
    setFactor(this.isVisible ? 1f : 0f);

    UI.getContext(getContext()).addActivityListener(this);
  }

  private RootFrameLayout rootView;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    rootView = Views.findAncestor(this, RootFrameLayout.class, true);
    if (rootView != null) {
      rootView.addInsetsChangeListener(this);
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (rootView != null) {
      rootView.removeInsetsChangeListener(this);
      rootView = null;
    }
  }

  @Override
  public void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect effectiveInsetsWithoutIme, Rect systemInsets, Rect systemInsetsWithoutIme, boolean isUpdate) { }

  @Override
  public void onSecondaryInsetsChanged (RootFrameLayout viewGroup, boolean systemGesturesInsetsChanged, boolean displayCutoutInsetsChanged) {
    if (Config.ADJUST_STATUS_BAR_TO_AVOID_DISPLAY_CUTOUT && displayCutoutInsetsChanged) {
      applyTopInset(viewGroup, viewGroup.getDisplayCutoutTopInset());
    }
  }

  private int getFrameWidth () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      // TODO
    }
    return getResources().getDisplayMetrics().widthPixels;
  }

  private void applyTopInset (RootFrameLayout rootView, Rect rect) {
    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) statusWrap.getLayoutParams();
    boolean updated;

    if (rect.isEmpty()) {
      updated = Views.setGravity(layoutParams, Gravity.CENTER_HORIZONTAL);
      updated = Views.setMargins(layoutParams, 0, 0, 0, 0) || updated;
    } else {
      int width = getFrameWidth();
      int distanceFromLeft = rect.left;
      int distanceFromRight = width - rect.right;

      if (distanceFromRight > distanceFromLeft) {
        updated = Views.setGravity(layoutParams, Gravity.CENTER_HORIZONTAL);
        updated = Views.setMargins(layoutParams, (rect.right + Screen.dp(12f)) / 2, 0, 0, 0) || updated;
      }/* else if (distanceFromLeft == distanceFromRight) {
        updated = Views.setGravity(layoutParams, Gravity.LEFT);
        updated = Views.setMargins(layoutParams, Screen.dp(12f), 0, rect.left + Screen.dp(12f), 0) || updated;
      }*/ else {
        updated = Views.setGravity(layoutParams, Gravity.CENTER_HORIZONTAL);
        updated = Views.setMargins(layoutParams, 0, 0, (rect.right + Screen.dp(12f)) / 2, 0) || updated;
      }
    }

    if (updated) {
      Views.updateLayoutParams(statusWrap);
    }
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    Views.setLayoutHeight(this, newHeight);
  }

  public void addThemeListeners (ThemeListenerList list) {
    list.addThemeColorListener(textView, Config.STATUS_BAR_TEXT_COLOR_ID);
    list.addThemeColorListener(progressView, Config.STATUS_BAR_TEXT_COLOR_ID);
  }

  public void updateDirection () {
    statusWrap.removeView(textView);
    statusWrap.removeView(progressView);
    if (Lang.rtl()) {
      statusWrap.addView(textView);
      statusWrap.addView(progressView);
    } else {
      statusWrap.addView(progressView);
      statusWrap.addView(textView);
    }
  }

  @Override
  public void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    Tdlib tdlib = newAccount.activeTdlib();
    if (tdlib != null) {
      updateNetworkState(tdlib);
    }
  }

  @Override
  public void performDestroy () {
    TdlibManager.instance().global().removeConnectionListener(this);
    TdlibManager.instance().global().removeAccountListener(this);
    UI.getContext(getContext()).removeActivityListener(this);
    removeCallbacks(this);
    Screen.removeStatusBarHeightListener(this);
  }

  private Tdlib tdlib;
  private @ConnectionState int state = ConnectionState.UNKNOWN;

  public void updateText (@StringRes int resId) {
    if (tdlib != null && getVisibilityFactor() > 0f) {
      int stringRes = TdlibUi.stringForConnectionState(state); // FIXME
      if (resId == 0 || resId == stringRes) {
        updateNetworkState(tdlib);
      }
    }
  }

  private void updateNetworkState (final @NonNull Tdlib tdlib) {
    this.tdlib = tdlib;
    this.state = tdlib.connectionState();
    progressView.setVisibility(state != ConnectionState.CONNECTED && state != ConnectionState.WAITING_FOR_NETWORK ? View.VISIBLE : View.GONE);
    textView.setText(tdlib.connectionStateText());
    updateVisible();
  }

  private boolean ignoreTranslation;

  public void updateVisible () {
    boolean isKeyboardVisible = UI.getContext(getContext()).isKeyboardVisible();
    setIsVisible(state != ConnectionState.CONNECTED && !isKeyboardVisible, ignoreTranslation = ignoreTranslation || isKeyboardVisible);
  }

  @Override
  public void onConnectionDisplayStatusChanged (Tdlib tdlib, boolean isCurrent) {
    if (isCurrent) {
      updateNetworkState(tdlib);
    }
  }

  private boolean isVisible;

  private void setIsVisible (boolean isVisible, boolean noDelay) {
    if (this.isVisible != isVisible) {
      this.isVisible = isVisible;
      if (getParent() != null && getMeasuredWidth() != 0 && getMeasuredHeight() != 0) {
        animateFactor(isVisible ? 1f : 0f, noDelay);
      } else {
        forceFactor(isVisible ? 1f : 0f);
      }
    }
  }

  private float factor = -1f, colorFactor = -1f;
  private FactorAnimator animator;

  private void animateFactor (float factor, boolean noDelay) {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
    }
    animator.setStartDelay((this.factor != 1f && this.factor != 0f) || noDelay ? 0 : isVisible ? 300l : 1200l);
    animator.animateTo(factor);
  }

  private void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
  }

  private void setColorFactor (float factor) {
    if (this.colorFactor != factor) {
      this.colorFactor = factor;
      invalidate();
    }
  }

  private float getVisibilityFactor () {
    return (ignoreTranslation ? 1f : factor);
  }

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      statusWrap.setAlpha(factor);
      statusWrap.setTranslationY(-Screen.getStatusBarHeight() + (int) ((float) Screen.getStatusBarHeight() * getVisibilityFactor()));
      if (Config.USE_TRANSPARENT_STATUS_BAR && Settings.instance().useEdgeToEdge()) {
        backgroundDrawable.setAlphaFactor(factor);
      }
      checkLowProfile();
    }
  }

  private void checkLowProfile () {
    setLowProfile(!isPaused && (isVisible || factor != 0f));
  }

  private boolean inLowProfile;

  private void setLowProfile (boolean inLowProfile) {
    if (this.inLowProfile != inLowProfile) {
      this.inLowProfile = inLowProfile;
      if (inLowProfile) {
        run();
      } else {
        removeCallbacks(this);
        BaseActivity context = UI.getContext(getContext());
        if (!context.isWindowModified()) {
          context.setWindowDecorSystemUiVisibility(SYSTEM_UI_FLAG_VISIBLE, false);
        }
      }
    }
  }

  @Override
  public void run () {
    if (inLowProfile) {
      BaseActivity context = UI.getContext(getContext());
      if (!context.isWindowModified()) {
        context.setWindowDecorSystemUiVisibility(SYSTEM_UI_FLAG_LOW_PROFILE, false);
      }
      postDelayed(this, 2500l + (long) ((1f - factor) * 1000f));
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case 0: {
        setFactor(factor);
        break;
      }
      case 1: {
        setColorFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case 0: {
        if (finalFactor == 1f) {
          ignoreTranslation = false;
        }
        break;
      }
    }
  }

  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return true; // y >= Screen.getStatusBarHeight();
  }

  private boolean isPaused;

  private void setIsPaused (boolean isPaused) {
    if (this.isPaused != isPaused) {
      this.isPaused = isPaused;
      checkLowProfile();
    }
  }

  @Override
  public void onActivityPause () {
    setIsPaused(true);
  }

  @Override
  public void onActivityResume () {
    setIsPaused(false);
  }

  @Override
  public void onActivityDestroy () {
    setIsPaused(true);
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) { }
}
