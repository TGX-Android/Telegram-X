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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ConnectionState;
import org.thunderdog.challegram.telegram.GlobalConnectionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class NetworkStatusBarView extends FrameLayoutFix implements Destroyable, Screen.StatusBarHeightChangeListener, GlobalConnectionListener, FactorAnimator.Target, PopupLayout.TouchSectionProvider, Runnable, BaseActivity.ActivityListener {
  private ProgressComponentView progressView;
  private TextView textView;
  private LinearLayout statusWrap;

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
    ViewSupport.setThemedBackground(this, R.id.theme_color_statusBar);


    TdlibManager.instance().global().addConnectionListener(this);
    setNetworkState(TdlibManager.instance().current().connectionState());
    setFactor(this.isVisible ? 1f : 0f);

    UI.getContext(getContext()).addActivityListener(this);
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    Log.i("new height: %d", newHeight);
    if (getLayoutParams() != null && getLayoutParams().height != newHeight) {
      getLayoutParams().height = newHeight;
      setLayoutParams(getLayoutParams());
    }
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
  public void performDestroy () {
    TdlibManager.instance().global().removeConnectionListener(this);
    UI.getContext(getContext()).removeActivityListener(this);
    removeCallbacks(this);
    Screen.removeStatusBarHeightListener(this);
  }

  private @ConnectionState int state = Tdlib.STATE_UNKNOWN;

  public void updateText (@StringRes int resId) {
    if (state != Tdlib.STATE_UNKNOWN && getVisibilityFactor() > 0f) {
      int stringRes = TdlibUi.stringForConnectionState(state);
      if (resId == 0 || resId == stringRes) {
        textView.setText(Lang.getString(stringRes));
      }
    }
  }

  private void setNetworkState (final @ConnectionState int state) {
    if (this.state != state) {
      this.state = state;
      progressView.setVisibility(state != Tdlib.STATE_CONNECTED && state != Tdlib.STATE_WAITING ? View.VISIBLE : View.GONE);
      textView.setText(Lang.getString(TdlibUi.stringForConnectionState(state)));
      updateVisible();
    }
  }

  private boolean ignoreTranslation;

  public void updateVisible () {
    boolean isKeyboardVisible = UI.getContext(getContext()).isKeyboardVisible();
    setIsVisible(state != Tdlib.STATE_CONNECTED && !isKeyboardVisible, ignoreTranslation = ignoreTranslation || isKeyboardVisible);
    /*int color;
    switch (state) {
      case WatchDog.STATE_WAITING: {
        color = R.id.theme_color_statusBarUnreachable;
        break;
      }
      default:
        color = state != WatchDog.STATE_CONNECTED ? R.id.theme_color_statusBarConnecting : R.id.theme_color_statusBar;
        break;
    }
    setColor(isKeyboardVisible ? color : R.id.theme_color_statusBar);*/
  }

  // private ColorChanger statusChanger;
  // private int currentColorId = R.id.theme_color_statusBarConnecting;

  /*private int getCurrentStatusBarColor () {
    return *//*statusChanger != null ? statusChanger.getColor(colorFactor) :*//* Theme.getColor(R.id.theme_color_statusBar);
  }*/

  /*private FactorAnimator colorAnimator;

  private void setColor (@ThemeColorId int colorId) {
    if (this.currentColorId != colorId) {
      if (statusChanger == null) {
        statusChanger = new ColorChanger();
      }
      statusChanger.setFrom(getCurrentStatusBarColor());
      this.currentColorId = colorId;
      statusChanger.setTo(Theme.getColor(colorId));
      if (colorAnimator == null) {
        colorAnimator = new FactorAnimator(1, this, Anim.DECELERATE_INTERPOLATOR, 300l, 0f);
      } else {
        colorAnimator.forceFactor(0f);
      }
      this.colorFactor = 0f;
      colorAnimator.animateTo(1f);
    }
  }*/

  @Override
  public void onConnectionStateChanged (Tdlib tdlib, int newState, boolean isCurrent) {
    if (isCurrent) {
      setNetworkState(newState);
      updateVisible();
    }
  }

  @Override
  public void onConnectionTypeChanged (int oldConnectionType, int connectionType) { }

  @Override
  public void onSystemDataSaverStateChanged (boolean isEnabled) { }

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
