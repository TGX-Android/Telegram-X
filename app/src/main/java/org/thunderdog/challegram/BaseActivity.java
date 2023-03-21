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
 * File created on 23/04/2015 at 15:23
 */
package org.thunderdog.challegram;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.ProgressWrap;
import org.thunderdog.challegram.component.chat.InlineResultsWrap;
import org.thunderdog.challegram.component.popups.ModernOptions;
import org.thunderdog.challegram.component.preview.PreviewLayout;
import org.thunderdog.challegram.component.sticker.StickerPreviewView;
import org.thunderdog.challegram.component.sticker.StickerSetWrap;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.DrawerController;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.InterceptLayout;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationGestureController;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.OverlayView;
import org.thunderdog.challegram.navigation.ReactionsOverlayView;
import org.thunderdog.challegram.navigation.RootDrawable;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.RecordAudioVideoController;
import org.thunderdog.challegram.player.RoundVideoController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperty;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Invalidator;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.InstantViewController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.PasscodeController;
import org.thunderdog.challegram.ui.ThemeController;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.unsorted.AppState;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.ActivityPermissionResult;
import org.thunderdog.challegram.util.AppUpdater;
import org.thunderdog.challegram.util.KonfettiBuilder;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.widget.BaseRootLayout;
import org.thunderdog.challegram.widget.DragDropLayout;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.NetworkStatusBarView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.FutureInt;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceUtils;
import nl.dionsegijn.konfetti.xml.KonfettiView;

public abstract class BaseActivity extends ComponentActivity implements View.OnTouchListener, FactorAnimator.Target, Keyboard.OnStateChangeListener, ThemeChangeListener, SensorEventListener, TGPlayerController.TrackChangeListener, TGLegacyManager.EmojiLoadListener, Lang.Listener, Handler.Callback {
  public static final long POPUP_SHOW_SLOW_DURATION = 240l;

  private static final int OPEN_CAMERA_BY_TAP = 1;
  private static final int DISPATCH_ACTIVITY_STATE = 2;

  private Handler handler;

  protected BaseRootLayout rootView;
  protected InterceptLayout contentView;
  protected NavigationController navigation;
  protected NavigationGestureController gestureController;
  protected @Nullable DrawerController drawer;
  protected OverlayView overlayView;
  protected Invalidator invalidator;

  private final ReferenceList<ActivityListener> activityListeners = new ReferenceList<>();

  private int currentOrientation;
  private boolean mHasSoftwareKeys;

  public @Nullable DrawerController getDrawer () {
    return drawer;
  }

  public RecordAudioVideoController getRecordAudioVideoController () {
    return recordAudioVideoController;
  }

  public AppUpdater appUpdater () {
    return appUpdater;
  }

  public NavigationController navigation () {
    return navigation;
  }

  public NavigationGestureController getGestureController () {
    return gestureController;
  }

  public int getSettingsErrorIcon () {
    // It's located here for future display inside header menu button
    if (hasTdlib()) {
      Tdlib tdlib = currentTdlib();
      final boolean haveNotificationsProblem = tdlib.notifications().hasLocalNotificationProblem();
      final TdApi.SuggestedAction singleAction = tdlib.singleSettingsSuggestion();
      final boolean haveSuggestions = singleAction != null || tdlib.haveAnySettingsSuggestions();
      final int totalCount = (singleAction != null ? 1 : haveSuggestions ? 2 : 0) + (haveNotificationsProblem ? 1 : 0);
      if (totalCount > 1) {
        return Tdlib.CHAT_FAILED;
      } else if (haveNotificationsProblem) {
        return R.drawable.baseline_notification_important_14;
      } else if (singleAction != null) {
        switch (singleAction.getConstructor()) {
          case TdApi.SuggestedActionCheckPassword.CONSTRUCTOR:
            return R.drawable.baseline_gpp_maybe_14;
          case TdApi.SuggestedActionCheckPhoneNumber.CONSTRUCTOR:
            return R.drawable.baseline_sim_card_alert_14;
          default:
            throw new UnsupportedOperationException(singleAction.toString());
        }
      }
    }
    return 0;
  }

  public boolean isAnimating (boolean intercept) {
    return (navigation != null && (intercept ? navigation.isAnimatingWithEffect() : navigation.isAnimating())) || (drawer != null && drawer.isAnimating()) || isProgressShowing || (cameraAnimator != null && cameraAnimator.isAnimating());
  }

  public boolean processTouchEvent (MotionEvent event) {
    return !isProgressShowing && gestureController.onTouchEvent(event);
  }

  public void addToRoot (View view, boolean ignoreStatusBar) {
    int i = passcodeController != null && isPasscodeShowing ? rootView.indexOfChild(passcodeController.get()) : -1;

    // TODO make some overlay for PiPs
    if (i == -1) {
      View roundPipView = roundVideoController.getPipParentView();
      if (roundPipView != null) {
        i = rootView.indexOfChild(roundPipView);
      }
    }

    if (i == -1 && !ignoreStatusBar) {
      i = statusBar != null ? rootView.indexOfChild(statusBar) : -1;
    }
    int tooltipIndex = tooltipOverlayView != null ? rootView.indexOfChild(tooltipOverlayView) : -1;
    if (tooltipIndex != -1) {
      i = i == -1 ? tooltipIndex : Math.min(tooltipIndex, i);
    }
    if (i != -1) {
      rootView.addView(view, i);
    } else {
      rootView.addView(view);
    }
  }

  public void removeFromRoot (View view) {
    try {
      rootView.removeView(view);
    } catch (NullPointerException e) {
      // Ignoring, as it's most likely bug in Android SDK 23
      // at android.view.TextureView.destroySurface (TextureView.java:244)
      Log.i(e);
    }
  }

  public void addToNavigation (View view) {
    navigation.addViewUnderHeader(view);
  }

  public void removeFromNavigation (View view) {
    ((ViewGroup) navigation.get()).removeView(view);
  }

  public RoundVideoController getRoundVideoController () {
    return roundVideoController;
  }

  public Invalidator invalidator () {
    return invalidator;
  }

  private View focusView;
  private int activityState = UI.STATE_UNKNOWN;

  private RoundVideoController roundVideoController;
  private RecordAudioVideoController recordAudioVideoController;
  private AppUpdater appUpdater;

  protected Tdlib tdlib;

  public final boolean hasTdlib () {
    return tdlib != null;
  }

  public final Tdlib currentTdlib () {
    if (tdlib == null) {
      throw new AssertionError();
    }
    return tdlib;
  }

  protected final void setTdlib (Tdlib tdlib) {
    if (this.tdlib != tdlib) {
      boolean wasOnline = false;
      if (this.tdlib != null) {
        wasOnline = this.tdlib.isOnline();
        this.tdlib.setOnline(false);
      }
      this.tdlib = tdlib;
      recordAudioVideoController.setTdlib(tdlib);
      tdlib.setOnline(wasOnline);
      if (drawer != null) {
        drawer.onCurrentTdlibChanged(tdlib);
      }
      onTdlibChanged();
    }
  }

  protected void onTdlibChanged () {
    // override
  }

  public float windowRefreshRate () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      Display display = getWindowManager().getDefaultDisplay();
      Display.Mode displayMode = display.getMode();
      return displayMode.getRefreshRate();
    }
    return 60.0f;
  }

  @Override
  public void onCreate (Bundle savedInstanceState) {
    UI.setContext(this);

    AppState.initApplication();
    AppState.ensureReady();

    appUpdater = new AppUpdater(this);
    roundVideoController = new RoundVideoController(this);
    recordAudioVideoController = new RecordAudioVideoController(this);
    invalidator = new Invalidator(this);

    handler = new Handler(this);

    UI.clearActivity(this);
    updateWindowContextTheme();
    if (Config.USE_CUSTOM_NAVIGATION_COLOR) {
      this.isWindowLight = !Theme.isDark();
    }
    // UI.resetSizes();
    setActivityState(UI.STATE_RESUMED);
    TdlibManager.instance().watchDog().onActivityCreate(this);
    Passcode.instance().checkAutoLock();

    try {
      super.onCreate(savedInstanceState);
    } catch (Throwable t) {
      Tracer.onUiError(t);
      throw t;
    }

    Screen.checkDensity();

    mHasSoftwareKeys = hasSoftwareKeys();

    currentOrientation = UI.getOrientation();

    if (needDrawer()) {
      drawer = new DrawerController(this);
      drawer.get();
    }

    navigation = new NavigationController(this);

    gestureController = new NavigationGestureController(this, navigation, drawer);

    rootView = new BaseRootLayout(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      rootView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }
    rootView.setKeyboardListener(this);
    if (Config.USE_TRANSLUCENT_NAVIGATION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
    }
    rootView.init(false);
    rootView.setId(R.id.app_root);

    contentView = new InterceptLayout(this);
    contentView.setId(R.id.app_container);

    focusView = new View(this);
    focusView.setFocusable(true);
    focusView.setFocusableInTouchMode(true);
    focusView.setLayoutParams(FrameLayoutFix.newParams(1, 1, Gravity.CENTER));

    contentView.addView(focusView);
    contentView.addView(navigation.get());
    contentView.addView(recordAudioVideoController.prepareViews());
    if (drawer != null) {
      contentView.addView(drawer.get());
    }

    rootView.addView(contentView);

    checkPasscode(false);

    /*int darkness = Challegram.instance().getDarkness();
    if (darkness != 0) {
      setDarkness(darkness);
    }*/

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      statusBar = new NetworkStatusBarView(this);
      statusBar.addThemeListeners(themeList);
      rootView.addView(statusBar);
    }

    setContentView(rootView);

    ThemeManager.instance().addThemeListener(this);
    checkAutoNightMode();
    addActivityListener(roundVideoController);

    Log.initLibraries(this);

    if (needTdlib()) {
      TdlibManager.instance().player().addTrackChangeListener(this);
      TdlibManager.instance().resetBadge();
    }

    Lang.addLanguageListener(this);

    /*if (BuildConfig.DEBUG) {
      addRemoveRtlSwitch();
    }*/
  }

  private View rtlSwitchView;

  public void addRemoveRtlSwitch () {
    if (rtlSwitchView != null) {
      rootView.removeView(rtlSwitchView);
      rtlSwitchView = null;
    } else {
      View view = new View(this);
      view.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(32f), Screen.dp(32f), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
      view.setBackgroundColor(0x70ff0000);
      view.setOnClickListener(v -> {
        Settings.instance().setNeedRtl(Lang.packId(), !Lang.rtl());
      });
      rootView.addView(rtlSwitchView = view);
    }
  }

  public void closeAllMedia (boolean allowPause) {
    if (allowPause) {
      for (PopupLayout window : windows) {
        if (window.getBoundController() instanceof MediaViewController) {
          ((MediaViewController) window.getBoundController()).pauseVideoIfPlaying();
        }
      }
      for (int i = 0; i < forgottenWindows.size(); i++) {
        PopupLayout popupLayout = forgottenWindows.valueAt(i);
        if (popupLayout != null && popupLayout.getBoundController() instanceof MediaViewController) {
          ((MediaViewController) popupLayout.getBoundController()).pauseVideoIfPlaying();
        }
      }
    } else {
      closeOtherPips();
    }
  }

  public int getWindowRotationDegrees () {
    return rotationToDegrees(getWindowManager().getDefaultDisplay().getRotation());
  }

  private static int rotationToDegrees (int rotation) {
    switch (rotation) {
      case Surface.ROTATION_0: return 0;
      case Surface.ROTATION_90: return 90;
      case Surface.ROTATION_180: return 180;
      case Surface.ROTATION_270: return 270;
    }
    return 0;
  }

  public void addActivityListener (ActivityListener listener) {
    activityListeners.add(listener);
  }

  public void removeActivityListener (ActivityListener listener) {
    activityListeners.remove(listener);
  }

  public int getActivityState () {
    return activityState;
  }

  protected abstract boolean needDrawer();
  protected boolean needTdlib() {
    return true; // Override if needed
  }

  public void requestBlankFocus () {
    if (focusView != null) {
      focusView.requestFocus();
    }
  }

  public void hideBlankFocusKeyboard () {
    if (focusView != null) {
      Keyboard.hide(focusView);
    }
  }

  private CancellableRunnable enableFocusActor;

  private void blockFocus () {
    if (enableFocusActor != null) {
      enableFocusActor.cancel();
      enableFocusActor = null;
    }
    if (focusView != null) {
      focusView.setEnabled(false);
    }
  }

  private void enableFocus () {
    if (enableFocusActor != null) {
      enableFocusActor.cancel();
    }
    enableFocusActor = new CancellableRunnable() {
      @Override
      public void act () {
        if (focusView != null) {
          focusView.setEnabled(true);
        }
      }
    };
    UI.post(enableFocusActor, 1000);
  }

  private List<Reference<View>> dialogMessages;

  public final AlertDialog showAlert (AlertDialog.Builder b) {
    return showAlert(b, null);
  }

  public final AlertDialog showAlert (AlertDialog.Builder b, ThemeDelegate theme) {
    if (isFinishing()) {
      return null;
    }
    AlertDialog dialog;
    try {
      dialog = b.show();
    } catch (Throwable t) {
      if (UI.getUiState() == UI.STATE_RESUMED)
        UI.showToast("Failed to display system pop-up, see application log for details", Toast.LENGTH_SHORT);
      Log.e("Cannot show dialog", t);
      return null;
    }
    View view = dialog.findViewById(android.R.id.message);
    if (view != null) {
      if (dialogMessages == null) {
        dialogMessages = new ArrayList<>();
        TGLegacyManager.instance().addEmojiListener(this);
      }
      ReferenceUtils.addReference(dialogMessages, view);
    }
    // TODO LOW_PROFILE
    return modifyAlert(dialog, theme);
  }

  private static boolean patchAlertButton (View v, ThemeDelegate theme, @ThemeColorId int colorId) {
    if (v == null)
      return false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Views.makeFakeBold(v);
      if (v instanceof TextView) {
        ((TextView) v).setTextColor(theme.getColor(colorId));
        return true;
      }
    }
    return false;
  }

  public AlertDialog modifyAlert (AlertDialog dialog, ThemeDelegate theme) {
    return modifyAlert(this, dialog, theme);
  }

  public static AlertDialog modifyAlert (Context context, AlertDialog dialog, ThemeDelegate theme) {
    View view;

    if (theme == null)
      theme = ThemeManager.instance().currentTheme(false);

    int textColor = theme.getColor(R.id.theme_color_text);

    view = dialog.findViewById(android.R.id.title);
    Views.makeFakeBold(view);
    if (view instanceof TextView)
      ((TextView) view).setTextColor(textColor);

    view = dialog.findViewById(android.R.id.message);
    if (view instanceof TextView)
      ((TextView) view).setTextColor(textColor);
    
    view = Views.tryFindAndroidView(context, dialog, "alertTitle");
    Views.makeFakeBold(view);
    if (view instanceof TextView)
      ((TextView) view).setTextColor(textColor);

    if (!patchAlertButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), theme, R.id.theme_color_textNeutral))
      patchAlertButton(dialog.findViewById(android.R.id.button1), theme, R.id.theme_color_textNeutral);
    if (!patchAlertButton(dialog.getButton(DialogInterface.BUTTON_NEUTRAL), theme, R.id.theme_color_textNeutral))
      patchAlertButton(dialog.findViewById(android.R.id.button2), theme, R.id.theme_color_textNeutral);
    if (!patchAlertButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), theme, R.id.theme_color_textNeutral))
      patchAlertButton(dialog.findViewById(android.R.id.button3), theme, R.id.theme_color_textNeutral);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Drawable drawable = dialog.getWindow().getDecorView().getBackground();
      if (drawable != null) {
        drawable.setColorFilter(new PorterDuffColorFilter(theme.getColor(R.id.theme_color_overlayFilling), PorterDuff.Mode.SRC_IN));
      }
    }
    return dialog;
  }

  public @Nullable View getBlankFocusView () {
    return focusView;
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (dialogMessages != null) {
      try {
        for (int i = dialogMessages.size() - 1; i >= 0; i--) {
          View view = dialogMessages.get(i).get();
          if (view != null) {
            view.invalidate();
          } else {
            dialogMessages.remove(i);
          }
        }
      } catch (Throwable ignored) { }
    }
  }

  @Override
  public boolean onPrepareOptionsMenu (Menu menu) {
    return super.onPrepareOptionsMenu(menu);
  }

  public interface KeyEventListener {
    boolean onKeyDown (int keyCode, KeyEvent event);
    boolean onKeyUp (int keyCode, KeyEvent event);
  }

  private final ReferenceList<KeyEventListener> keyEventListeners = new ReferenceList<>();

  public void addKeyEventListener (KeyEventListener listener) {
    keyEventListeners.add(listener);
  }

  public void removeKeyEventListener (KeyEventListener listener) {
    keyEventListeners.remove(listener);
  }

  @Override
  public boolean onKeyDown (int keyCode, KeyEvent event) {
    boolean handled = false;
    for (KeyEventListener listener : keyEventListeners) {
      if (!handled && listener.onKeyDown(keyCode, event)) {
        handled = true;
      }
    }
    return handled || super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp (int keyCode, KeyEvent event) {
    boolean handled = false;
    for (KeyEventListener listener : keyEventListeners) {
      if (!handled && listener.onKeyUp(keyCode, event)) {
        handled = true;
      }
    }
    return handled || super.onKeyUp(keyCode, event);
  }

  /*private boolean allowCaptureOnResume;
  private static final boolean DISALLOW_ON_PAUSE = true;*/

  // Window flags & status bar

  protected @Nullable NetworkStatusBarView statusBar;

  public final void setWindowFlags (int flags, int mask) {
    getWindow().setFlags(flags, mask);
  }

  private int requestedWindowVisibility;

  public boolean isWindowModified () {
    return requestedWindowVisibility != View.SYSTEM_UI_FLAG_VISIBLE;
  }

  private int lastWindowVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
  private boolean isWindowLight;

  private float photoRevealFactor;

  public void setPhotoRevealFactor (float revealFactor) {
    if (Config.USE_CUSTOM_NAVIGATION_COLOR) {
      if (this.photoRevealFactor != revealFactor) {
        this.photoRevealFactor = revealFactor;
        updateNavigationBarColor();
      }
    }
  }

  public void updateWindowDecorSystemUiVisibility () {
    setWindowDecorSystemUiVisibility(lastWindowVisibility, false);
  }

  public void updateWindowContextTheme() {
    getWindow().getContext().getTheme().applyStyle(Theme.isDark() ? R.style.AppTheme_Dark : R.style.AppTheme, true);
  }

  public void setWindowDecorSystemUiVisibility (int visibility, boolean remember) {
    View decorView = getWindow().getDecorView();
    int setVisibility = visibility;
    boolean isLight = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Config.USE_CUSTOM_NAVIGATION_COLOR && !Theme.isDark() && (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
      setVisibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
      isLight = true;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Theme.needLightStatusBar()) {
      setVisibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    }
    decorView.setSystemUiVisibility(setVisibility);
    if (this.isWindowLight != isLight) {
      this.isWindowLight = isLight;
      updateNavigationBarColor();
    }
    lastWindowVisibility = visibility;
    if (remember) {
      requestedWindowVisibility = visibility;
    }
  }

  private static final int FULLSCREEN_FLAG_CAMERA = 1;
  private static final int FULLSCREEN_FLAG_PASSCODE = 1 << 1; // Actually indicates fullscreen should not appear at all
  private static final int FULLSCREEN_FLAG_HAS_NO_FULLSCREEN_VIEWS = 1 << 2; // Actually indicates fullscreen should not appear at all
  private static final int FULLSCREEN_FLAG_HAS_FULLSCREEN_VIEWS = 1 << 3; // Actually indicates fullscreen should not appear at all
  private static final int FULLSCREEN_FLAG_HIDE_NAVIGATION = 1 << 4; // Hide any navigation-related stuff for complete fullscreen

  private int fullScreenFlags;

  public void setFullScreenFlag (int flag, boolean enabled) {
    int flags = BitwiseUtils.setFlag(this.fullScreenFlags, flag, enabled);
    if (this.fullScreenFlags != flags) {
      this.fullScreenFlags = flags;
      setFullScreen(flags != 0 && !BitwiseUtils.hasFlag(flags, FULLSCREEN_FLAG_PASSCODE) && !BitwiseUtils.hasFlag(flags, FULLSCREEN_FLAG_HAS_NO_FULLSCREEN_VIEWS));
      setHideNavigation(isFullscreen && BitwiseUtils.hasFlag(flags, FULLSCREEN_FLAG_HIDE_NAVIGATION));
      updateNavigationBarColor();
    }
  }

  private List<ViewController<?>> noFullScreenViews, fullScreenViews;

  public void addFullScreenView (ViewController<?> controller, boolean needFullScreen) {
    List<ViewController<?>> list;
    if (needFullScreen) {
      if (fullScreenViews == null)
        fullScreenViews = new ArrayList<>();
      list = fullScreenViews;
    } else {
      if (noFullScreenViews == null)
        noFullScreenViews = new ArrayList<>();
      list = noFullScreenViews;
    }
    if (!list.contains(controller)) {
      list.add(controller);
      setFullScreenFlag(needFullScreen ? FULLSCREEN_FLAG_HAS_FULLSCREEN_VIEWS : FULLSCREEN_FLAG_HAS_NO_FULLSCREEN_VIEWS, true);
    }
  }

  public void removeFullScreenView (ViewController<?> controller, boolean needFullScreen) {
    List<ViewController<?>> list = needFullScreen ? fullScreenViews : noFullScreenViews;
    if (list != null && list.remove(controller)) {
      setFullScreenFlag(needFullScreen ? FULLSCREEN_FLAG_HAS_FULLSCREEN_VIEWS : FULLSCREEN_FLAG_HAS_NO_FULLSCREEN_VIEWS, !list.isEmpty());
    }
  }

  private boolean isFullscreen, cutoutIgnored;

  private int computeUiVisibility () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isFullscreen) {
      int uiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE;
      if (hideNavigation) {
        uiVisibility |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
      }
      return uiVisibility;
    }
    return View.SYSTEM_UI_FLAG_VISIBLE;
  }

  private void setFullScreen (boolean isFullscreen) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (this.isFullscreen != isFullscreen) {
        this.isFullscreen = isFullscreen;
        this.hideNavigation = BitwiseUtils.hasFlag(fullScreenFlags, FULLSCREEN_FLAG_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isFullscreen && (Config.CUTOUT_ENABLED || BitwiseUtils.hasFlag(fullScreenFlags, FULLSCREEN_FLAG_CAMERA))) {
          cutoutIgnored = true;
          Window w = getWindow();
          WindowManager.LayoutParams params = w.getAttributes();
          params.layoutInDisplayCutoutMode = isFullscreen ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
          w.setAttributes(params);
        }
        setWindowFlags(isFullscreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        int uiVisibility = computeUiVisibility();
        setWindowDecorSystemUiVisibility(uiVisibility, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !isFullscreen && (Config.CUTOUT_ENABLED || cutoutIgnored)) {
          Window w = getWindow();
          WindowManager.LayoutParams params = w.getAttributes();
          params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
          w.setAttributes(params);
        }
      }
    }
  }

  private final List<ViewController<?>> hideNavigationViews = new ArrayList<>();

  public void addHideNavigationView (ViewController<?> viewController) {
    if (!hideNavigationViews.contains(viewController)) {
      hideNavigationViews.add(viewController);
      setFullScreenFlag(FULLSCREEN_FLAG_HIDE_NAVIGATION, true);
    }
  }

  public void removeHideNavigationView (ViewController<?> viewController) {
    if (hideNavigationViews.remove(viewController)) {
      setFullScreenFlag(FULLSCREEN_FLAG_HIDE_NAVIGATION, !hideNavigationViews.isEmpty());
    }
  }

  private boolean hideNavigation;

  private void setHideNavigation (boolean hideNavigation) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (this.hideNavigation != hideNavigation) {
        this.hideNavigation = hideNavigation;
        setWindowDecorSystemUiVisibility(computeUiVisibility(), true);
      }
    }
  }

  /*private boolean inCameraFullscreen;
  private boolean cameraHideNavigation;

  private void setFullScreenCamera (boolean inFullScreen, boolean hideNavigation) {
    if (this.inCameraFullscreen != inFullScreen) {
      this.inCameraFullscreen = inFullScreen;
      setWindowFlags(inFullScreen ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    if (this.cameraHideNavigation != hideNavigation) {
      this.cameraHideNavigation = hideNavigation;
      setWindowDecorSystemUiVisibility(hideNavigation ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LOW_PROFILE : View.SYSTEM_UI_FLAG_VISIBLE, true);
    }
  }*/

  // Other

  public interface SimpleStateListener {
    void onActivityStateChanged (BaseActivity activity, int newState, int prevState);
  }

  public final void addSimpleStateListener (SimpleStateListener listener) {
    stateListeners.add(listener);
  }

  public final void removeSimpleStateListener (SimpleStateListener listener) {
    stateListeners.remove(listener);
  }

  private final ReferenceList<SimpleStateListener> stateListeners = new ReferenceList<>(true);

  private void setActivityState (int newState) {
    if (this.activityState != newState) {
      final int prevState = this.activityState;
      boolean prevResumed = prevState == UI.STATE_RESUMED;
      this.activityState = newState;
      if (newState != UI.STATE_RESUMED) {
        if (prevResumed) {
          handler.removeMessages(DISPATCH_ACTIVITY_STATE);
        }
        UI.setUiState(this, newState);
      } else {
        handler.sendMessageDelayed(Message.obtain(handler, DISPATCH_ACTIVITY_STATE), 200l);
      }
      for (SimpleStateListener listener : stateListeners) {
        listener.onActivityStateChanged(this, newState, prevState);
      }
    }
  }

  private void notifyActivityResume () {
    for (ActivityListener listener : activityListeners) {
      listener.onActivityResume();
    }
  }

  private void notifyActivityPause () {
    for (ActivityListener listener : activityListeners) {
      listener.onActivityPause();
    }
  }

  private void notifyActivityDestroy () {
    for (ActivityListener listener : activityListeners) {
      listener.onActivityDestroy();
    }
  }

  @Override
  public void onPause () {
    blockFocus();
    setActivityState(UI.STATE_PAUSED);
    if (camera != null) {
      camera.onActivityPause();
    }
    if (isPasscodeShowing && passcodeController != null) {
      passcodeController.onActivityPause();
    }
    notifyActivityPause();
    if (!windows.isEmpty()) {
      for (PopupLayout window : windows) {
        window.onActivityPause();
      }
    }
    if (forgottenWindows.size() > 0) {
      for (int i = 0; i < forgottenWindows.size(); i++) {
        forgottenWindows.valueAt(i).onActivityPause();
      }
    }
    setOnline(false);
    try {
      super.onPause();
    } catch (Throwable t) {
      Tracer.onUiError(t);
      throw t;
    }
    checkAutoNightMode();
    try {
      unregisterReceiver(timeBroadcastReceiver);
    } catch (Throwable t) {
      Log.w(t);
    }
  }

  private IntentFilter timeFilter;
  private BroadcastReceiver timeBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive (Context context, Intent intent) {
      if (intent != null) {
        String action = intent.getAction();
        if (Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)) {
          Settings.instance().checkNightModeScheduler(true);
        }
      }
    }
  };

  @Override
  public void onResume () {
    boolean lockBefore = isPasscodeShowing;
    UI.setContext(this);
    setActivityState(UI.STATE_RESUMED);
    Passcode.instance().checkAutoLock();
    checkPasscode(false);
    if (isPasscodeShowing && lockBefore && passcodeController != null) {
      passcodeController.onActivityResume();
    }
    notifyActivityResume();
    if (!windows.isEmpty()) {
      for (PopupLayout window : windows) {
        window.onActivityResume();
      }
    }
    if (forgottenWindows.size() > 0) {
      for (int i = 0; i < forgottenWindows.size(); i++) {
        forgottenWindows.valueAt(i).onActivityResume();
      }
    }
    setOnline(true);
    try {
      super.onResume();
    } catch (Throwable t) {
      Tracer.onUiError(t);
      throw t;
    }
    checkAutoNightMode();
    Intents.revokeFileReadPermissions();
    enableFocus();
    if (timeFilter == null) {
      timeFilter = new IntentFilter();
      timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
      timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
    }
    try {
      registerReceiver(timeBroadcastReceiver, timeFilter);
    } catch (Throwable t) {
      Log.w(t);
    }
    Settings.instance().checkNightModeScheduler(true);

    /*if (DISALLOW_ON_PAUSE) {
      if (allowCaptureOnResume) {
        allowCaptureOnResume = false;
        getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_SECURE);
      }
    }*/
    appUpdater.checkForUpdates();
  }

  protected void setOnline (boolean isOnline) {
    if (tdlib != null) {
      tdlib.setOnline(isOnline);
    }
  }

  public final void checkPasscode (boolean byUserLock) {
    if (byUserLock) {
      // TODO FIXME
      return;
    }
    if (Passcode.instance().isLocked()) {
      try {
        showPasscode(byUserLock);
      } catch (Throwable t) {
        Log.e("TODO", t);
      }
    } else {
      Passcode.instance().trackUserActivity(false);
    }
  }

  @Override
  public void onDestroy () {
    try {
      super.onDestroy();
    } catch (Throwable t) {
      Tracer.onUiError(t);
      throw t;
    }
    if (navigation != null) {
      navigation.destroy();
    }
    Lang.removeLanguageListener(this);
    if (statusBar != null) {
      statusBar.performDestroy();
    }
    TGLegacyManager.instance().removeEmojiListener(this);
    TdlibManager.instance().watchDog().onActivityDestroy(this);
    Intents.revokeFileReadPermissions();
    setActivityState(UI.STATE_DESTROYED);
    if (isPasscodeShowing && passcodeController != null) {
      passcodeController.onActivityDestroy();
    }
    notifyActivityDestroy();
    if (!windows.isEmpty()) {
      final int size = windows.size();
      for (int i = size - 1; i >= 0; i--) {
        windows.get(i).onActivityDestroy();
      }
    }
    if (forgottenWindows.size() > 0) {
      for (int i = 0; i < forgottenWindows.size(); i++) {
        forgottenWindows.valueAt(i).onActivityDestroy();
      }
    }
    checkAutoNightMode();
    UI.clearContext(this);
    TdlibManager.instance().player().removeTrackChangeListener(this);
  }

  /*@Override
  public void onMultiWindowModeChanged (boolean isInMultiWindowMode, Configuration newConfig) {
    super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
    navigation.onMultiWindowModeChanged(isInMultiWindowMode);
    if (isPasscodeShowing) {
      navigation.onMultiWindowModeChanged(isInMultiWindowMode);
    }
    if (camera != null) {
      navigation.onMultiWindowModeChanged(isInMultiWindowMode);
    }
  }*/

  private static final int SYSTEM_NIGHT_MODE_UNSPECIFIED = 0;
  private static final int SYSTEM_NIGHT_MODE_NO = 1;
  private static final int SYSTEM_NIGHT_MODE_YES = 2;

  private int systemNightMode = SYSTEM_NIGHT_MODE_UNSPECIFIED;

  public boolean hasSystemNightMode () {
    return systemNightMode == SYSTEM_NIGHT_MODE_YES || systemNightMode == SYSTEM_NIGHT_MODE_NO;
  }

  private static String systemNightModeToString (int mode) {
    switch (mode) {
      case SYSTEM_NIGHT_MODE_YES:
        return "yes";
      case SYSTEM_NIGHT_MODE_NO:
        return "no";
      case SYSTEM_NIGHT_MODE_UNSPECIFIED:
        return "unspecified";
    }
    throw new IllegalArgumentException("mode == " + mode);
  }

  private void setSystemNightMode (int requestedConfigMode) {
    UiModeManager systemService = (UiModeManager) this.getSystemService(UI_MODE_SERVICE);
    int managerMode;
    if (systemService != null) {
      int nightMode = systemService.getNightMode();
      if (nightMode == UiModeManager.MODE_NIGHT_YES)
        managerMode = SYSTEM_NIGHT_MODE_YES;
      else if (nightMode == UiModeManager.MODE_NIGHT_NO)
        managerMode = SYSTEM_NIGHT_MODE_NO;
      else
        managerMode = SYSTEM_NIGHT_MODE_UNSPECIFIED;
    } else {
      managerMode = SYSTEM_NIGHT_MODE_UNSPECIFIED;
    }
    final int configMode;
    if (requestedConfigMode == Configuration.UI_MODE_NIGHT_YES) {
      configMode = SYSTEM_NIGHT_MODE_YES;
    } else if (requestedConfigMode == Configuration.UI_MODE_NIGHT_NO) {
      configMode = SYSTEM_NIGHT_MODE_NO;
    } else {
      configMode = SYSTEM_NIGHT_MODE_UNSPECIFIED;
    }
    boolean isNight = getResources().getBoolean(R.bool.isNight);
    int mode = configMode;
    if (mode == SYSTEM_NIGHT_MODE_UNSPECIFIED)
      mode = managerMode;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && (mode == SYSTEM_NIGHT_MODE_UNSPECIFIED || (mode == SYSTEM_NIGHT_MODE_NO && isNight))) {
      mode = isNight ? SYSTEM_NIGHT_MODE_YES : SYSTEM_NIGHT_MODE_NO;
    }
    boolean changed = this.systemNightMode != mode;
    this.systemNightMode = mode;
    boolean needApply = Settings.instance().getNightMode() == Settings.NIGHT_MODE_SYSTEM && (changed || (mode != SYSTEM_NIGHT_MODE_UNSPECIFIED && ThemeManager.instance().isCurrentThemeDark() != (mode == SYSTEM_NIGHT_MODE_YES)));
    Log.i("System night mode, selected:%d(%s), applying:%b, managerMode:%d(%s), configMode:%d(%s), isNight:%b",
      mode, systemNightModeToString(mode),
      needApply,
      managerMode, systemNightModeToString(managerMode),
      configMode, systemNightModeToString(configMode),
      isNight);
    if (needApply) {
      if (allowNightModeChange()) {
        switch (mode) {
          case SYSTEM_NIGHT_MODE_YES:
            ThemeManager.instance().setInNightMode(true, true);
            break;
          case SYSTEM_NIGHT_MODE_NO:
            ThemeManager.instance().setInNightMode(false, true);
            break;
        }
      }
    }
  }

  @Override
  public void onConfigurationChanged (@NonNull Configuration newConfig)  {
    super.onConfigurationChanged(newConfig);
    boolean needRecreate = Screen.checkDensity();
    navigation.configurationChanged(newConfig);
    if (isPasscodeShowing && passcodeController != null) {
      passcodeController.onConfigurationChanged(newConfig);
    }
    if (camera != null) {
      camera.onConfigurationChanged(newConfig);
    }
    currentOrientation = newConfig.orientation;
    Lang.checkLanguageCode();
    setSystemNightMode(newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK);
    if (needRecreate) {
      recreate();
    } else {
      Screen.checkRefreshRate();
    }
  }

  @Override
  public void onBackPressed () {
    if (isPasscodeShowing) {
      super.onBackPressed();
    } else {
      onBackPressed(false);
    }
  }

  public void onBackPressed (boolean fromTop) {
    if (isProgressShowing) {
      if (progressListener != null) {
        hideProgress(true);
      }
      return;
    }
    if (tooltipOverlayView != null && tooltipOverlayView.onBackPressed()) {
      return;
    }
    if (dismissLastOpenWindow(false, true, fromTop)) {
      return;
    }
    if (isCameraOpen) {
      closeCameraByBackPress();
      return;
    }
    if (recordAudioVideoController.isOpen()) {
      recordAudioVideoController.onBackPressed();
      return;
    }
    if (!isAnimating(false)) {
      if (navigation.passBackPressToActivity(fromTop)) {
        super.onBackPressed();
        return;
      }
      if (navigation.onBackPressed(fromTop)) {
        return;
      }
      if (drawer != null && drawer.isVisible()) {
        drawer.close(0f, null);
      } else {
        ViewController<?> c = navigation.getCurrentStackItem();
        if (c == null) {
          super.onBackPressed();
        } else if (c.inSelectMode() || c.inSearchMode() || c.inCustomMode()) {
          navigation.onBackPressed(fromTop);
        } else {
          super.onBackPressed();
        }
      }
    }
  }

  private boolean isKeyboardVisible;

  public boolean isKeyboardVisible () {
    return isKeyboardVisible;
  }

  @Override
  public void onKeyboardStateChanged (boolean visible) {
    navigation.onKeyboardStateChanged(visible);
    this.isKeyboardVisible = visible;
    if (statusBar != null) {
      statusBar.updateVisible();
    }
  }

  @Override
  public void closeAdditionalKeyboards () {
    // TODO?
  }

  @Override
  public final boolean handleMessage (Message msg) {
    switch (msg.what) {
      case OPEN_CAMERA_BY_TAP: {
        openCameraByTap((ViewController.CameraOpenOptions) msg.obj);
        break;
      }
      case DISPATCH_ACTIVITY_STATE: {
        if (activityState == UI.STATE_RESUMED) {
          if (!UI.setUiState(this, UI.STATE_RESUMED)) {
            TdlibManager.instance().watchDog().checkNetworkAvailability();
          }
        }
        break;
      }
    }
    return true;
  }

  @Override
  public boolean onTouch (View v, MotionEvent event) {
    return gestureController != null && !isProgressShowing && gestureController.onTouchEvent(event);
  }

  // Utils

  private boolean mIsKeyboardBlocked;
  public void setIsKeyboardBlocked (boolean blocked)
  {
    if (mIsKeyboardBlocked == blocked)
      return;

    mIsKeyboardBlocked = blocked;
    //TODO
  }

  private boolean mIsOrientationBlocked;

  public int getCurrentOrientation () {
    return currentOrientation;
  }

  public void lockOrientation (int newOrientation) {
    if (currentOrientation != newOrientation) {
      currentOrientation = newOrientation;
      if (mIsOrientationBlocked) {
        requestAndroidOrientation(newOrientation);
      } else {
        setIsOrientationBlocked(true);
      }
    }
  }

  private void setIsOrientationBlocked (boolean blocked) {
    if (mIsOrientationBlocked == blocked || mIsOrientationRequested)
      return;

    mIsOrientationBlocked = blocked;

    if (blocked) {
      int rotation = getWindowManager().getDefaultDisplay().getRotation();

      if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE &&
        (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)) {
        requestAndroidOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      } else if (currentOrientation == Configuration.ORIENTATION_PORTRAIT &&
        (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)) {
        requestAndroidOrientationPortrait();
      } else if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE &&
        (rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270)) {
        requestAndroidOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
      } else {
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT &&
          (rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270)) {
          requestAndroidOrientationPortrait();
        }
      }
    } else {
      requestAndroidOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
  }

  private void requestAndroidOrientationPortrait () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      requestAndroidOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
    } else {
      requestAndroidOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
  }

  private void requestAndroidOrientation (int orientation) {
    try {
      setRequestedOrientation(orientation);
    } catch (Throwable t) {
      Log.e("Cannot request orientation", t);
    }
  }

  private boolean mIsOrientationRequested;
  public void setOrientation (int orientation) {
    mIsOrientationRequested = orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    requestAndroidOrientation(orientation);
  }

  // Passcode

  private int savedStatusColor;
  private boolean isPasscodeShowing;
  @Nullable
  private PasscodeController passcodeController, dismissingPasscodeController;

  public boolean isPasscodeShowing () {
    return isPasscodeShowing;
  }

  private void removeAllWindows () {
    final int size = windows.size();
    for (int i = size - 1; i >= 0; i--) {
      windows.get(i).hideTemporarily();
    }
  }

  private void restoreAllWindows () {
    for (PopupLayout window : windows) {
      window.restoreIfHidden();
    }
  }

  public interface PasscodeListener {
    void onPasscodeShowing (BaseActivity context, boolean isShowing);
  }

  private final ArrayList<PasscodeListener> passcodeListeners = new ArrayList<>();

  public void showPasscode (boolean byUserLock) {
    if (isPasscodeShowing) {
      return;
    }
    savedStatusColor = UI.getStatusBarColor();
    setIsPasscodeShowing(true);
    updateNavigationBarColor();
    passcodeController = new PasscodeController(this, null);
    passcodeController.setPasscodeMode(PasscodeController.MODE_UNLOCK);
    passcodeController.onPrepareToShow();
    rootView.removeView(contentView);
    rootView.addView(passcodeController.get());
    passcodeController.onActivityResume();
    passcodeController.onFocus();

    if (byUserLock) {
     /* PasscodeController c = passcodeController;
      c.getWrap().setAlpha(0f);
      FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          c.getWrap().setAlpha(factor);
          updateNavigationBarColor();
        }

        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
          updateNavigationBarColor();
        }
      }, Anim.DECELERATE_INTERPOLATOR, 100l);
      animator.animateTo(1f);*/
    }

    int defaultColor = HeaderView.defaultStatusColor();
    if (savedStatusColor != 0 && savedStatusColor != defaultColor) {
      UI.setStatusBarColor(defaultColor);
    }
  }

  public void addPasscodeListener (PasscodeListener listener) {
    passcodeListeners.add(listener);
  }

  public void removePasscodeListener (PasscodeListener listener) {
    passcodeListeners.remove(listener);
  }

  private CancellableRunnable navigationFocusTask;

  private void setIsPasscodeShowing (boolean isShowing) {
    if (this.isPasscodeShowing != isShowing) {
      this.isPasscodeShowing = isShowing;
      if (isShowing) {
        removeAllWindows();
      } else {
        restoreAllWindows();
      }
      setFullScreenFlag(FULLSCREEN_FLAG_PASSCODE, isShowing);
      if (isShowing) {
        if (navigationFocusTask != null) {
          navigationFocusTask.cancel();
          navigationFocusTask = null;
          // Navigation is still blurred, no need to call onBlur
        } else {
          navigation.onBlur();
        }
      } else {
        navigationFocusTask = new CancellableRunnable() {
          @Override
          public void act () {
            if (navigationFocusTask == this) {
              navigationFocusTask = null;
              navigation.onFocus();
            }
          }
        };
        navigationFocusTask.removeOnCancel(UI.getAppHandler());
        UI.post(navigationFocusTask, 100l);
      }
      int size = passcodeListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        passcodeListeners.get(i).onPasscodeShowing(this, isShowing);
      }
    }
  }

  public void hidePasscode () {
    if (!isPasscodeShowing) {
      return;
    }
    navigation.onPrepareToShow();
    rootView.addView(contentView, isOwningCamera() ? 1 : 0);
    contentView.invalidate();
    setIsPasscodeShowing(false);
    final PasscodeController passcodeController = this.passcodeController;
    if (passcodeController != null) {
      this.dismissingPasscodeController = passcodeController;
      this.passcodeController = null;
      FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          passcodeController.get().setAlpha(1f - factor);
          updateNavigationBarColor();
        }

        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
          rootView.removeView(passcodeController.get());
          passcodeController.destroy();
          if (dismissingPasscodeController == passcodeController) {
            dismissingPasscodeController = null;
          }
          updateNavigationBarColor();
        }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 100l);
      animator.animateTo(1f);
    }

    int defaultColor = HeaderView.defaultStatusColor();
    if (savedStatusColor != 0 && savedStatusColor != defaultColor) {
      UI.setStatusBarColor(savedStatusColor);
    }
  }

  // Fade view

  public @Nullable OverlayView getLayeredOverlayView () {
    return overlayViews.isEmpty() ? overlayView : overlayViews.get(overlayViews.size() - 1);
  }

  public OverlayView getOverlayView () {
    return overlayView;
  }

  public void showOverlayView (int color, int mode) {
    if (overlayView == null) {
      overlayView = new OverlayView(this);
      overlayView.setVisibility(View.GONE);
      overlayView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
    if (overlayView.getParent() != null) {
      contentView.removeView(overlayView);
    }
    overlayView.setData(color, mode);
    if (mode != OverlayView.OVERLAY_MODE_DRAWER) {
      overlayView.setAlpha(0f);
    }
    overlayView.setVisibility(View.VISIBLE);
    Views.setLayerTypeOptionally(overlayView, View.LAYER_TYPE_HARDWARE);
    int i = drawer != null ? contentView.indexOfChild(drawer.get()) : -1;
    if (i == -1) {
      contentView.addView(overlayView);
    } else {
      contentView.addView(overlayView, i);
    }
  }

  public void removeOverlayView () {
    if (overlayView != null) {
      overlayView.setVisibility(View.GONE);
      Views.setLayerTypeOptionally(overlayView, View.LAYER_TYPE_NONE);
      contentView.removeView(overlayView);
    }
  }

  private TooltipOverlayView tooltipOverlayView;

  public TooltipOverlayView tooltipManager () {
    if (tooltipOverlayView == null) {
      tooltipOverlayView = new TooltipOverlayView(this);
      tooltipOverlayView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      tooltipOverlayView.setAvailabilityListener((overlayView, hasChildren) -> {
        if (hasChildren) {
          if (tooltipOverlayView.getParent() != null)
            return;
          addToRoot(tooltipOverlayView, true);
        } else {
          removeFromRoot(tooltipOverlayView);
        }
      });
    }
    return tooltipOverlayView;
  }

  private ReactionsOverlayView reactionsOverlayView;

  public ReactionsOverlayView reactionsOverlayManager () {
    if (reactionsOverlayView == null) {
      reactionsOverlayView = new ReactionsOverlayView(this);
      reactionsOverlayView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      /*reactionsOverlayView.setAvailabilityListener((overlayView, hasChildren) -> {
        if (hasChildren) {
          if (tooltipOverlayView.getParent() != null)
            return;
          addToRoot(tooltipOverlayView, true);
        } else {
          removeFromRoot(tooltipOverlayView);
        }
      });*/
      addToRoot(reactionsOverlayView, true);
    }
    return reactionsOverlayView;
  }


  // Progress view

  private boolean isProgressShowing, isProgressAnimating;
  private ProgressPopupListener progressListener;
  private ProgressWrap progressWrap;
  private ValueAnimator progressAnimator;

  private float progressFactor;

  private static final float MIN_PROGRESS_SCALE = .85f;
  private static final long PROGRESS_DURATION = 220l;

  public void setProgressFactor (float progress) {
    if (progressFactor != progress) {
      progressFactor = progress;
      final float scale = MIN_PROGRESS_SCALE + (1f - MIN_PROGRESS_SCALE) * progress;
      progressWrap.setScaleX(scale);
      progressWrap.setScaleY(scale);
      progressWrap.setAlpha(progress);
      overlayView.setAlpha(.6f * progress);
    }
  }

  public void showProgress (String message, ProgressPopupListener listener) {
    if (isProgressShowing) {
      progressWrap.setMessage(message);
      return;
    }
    isProgressShowing = true;
    final boolean firstTime;
    if (progressWrap == null) {
      progressWrap = new ProgressWrap(this);
      progressWrap.addThemeListeners(themeList);
      firstTime = true;
    } else {
      firstTime = false;
    }
    progressWrap.setMessage(message);
    progressListener = listener;

    if (isProgressAnimating) return;
    isProgressAnimating = true;

    showOverlayView(0xff000000, OverlayView.OVERLAY_MODE_PROGRESS);
    overlayView.setTranslationX(0f);
    overlayView.setTranslationY(0f);
    overlayView.setUnlockable(null);

    if (progressWrap.getParent() != null) {
      contentView.removeView(progressWrap);
    }

    progressWrap.setAlpha(0f);
    progressWrap.setScaleX(MIN_PROGRESS_SCALE);
    progressWrap.setScaleY(MIN_PROGRESS_SCALE);
    contentView.addView(progressWrap);

    // FIXME with implements
    progressAnimator = AnimatorUtils.simpleValueAnimator();
    progressAnimator.addUpdateListener(animation -> setProgressFactor(AnimatorUtils.getFraction(animation)));
    progressAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    progressAnimator.setDuration(PROGRESS_DURATION);
    progressAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        isProgressAnimating = false;
        BaseActivity.this.progressAnimator = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          if (firstTime && progressWrap != null && progressWrap.getProgress() != null) {
            progressWrap.getProgress().setVisibility(View.GONE);
            progressWrap.getProgress().setVisibility(View.VISIBLE);
          }
        }
      }
    });

    if (progressWrap.getProgress() != null) {
      progressWrap.getProgress().setVisibility(View.GONE);
      progressWrap.getProgress().setVisibility(View.VISIBLE);
    }
    AnimatorUtils.startAnimator(progressWrap, progressAnimator);
  }

  public void hideProgress (boolean forced) {
    if (progressAnimator != null) {
      progressAnimator.cancel();
      progressAnimator = null;
      isProgressAnimating = false;
    }

    if (delayedProgress != null) {
      delayedProgress.cancel();
    }

    if (!isProgressShowing) return;

    if (progressListener != null) {
      if (forced) {
        progressListener.onClose();
      }
      progressListener = null;
    }

    isProgressShowing = false;
    isProgressAnimating = true;

    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setProgressFactor(1f - AnimatorUtils.getFraction(animation)));
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.setDuration(PROGRESS_DURATION);
    obj.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        isProgressAnimating = false;
        removeOverlayView();
        contentView.removeView(progressWrap);
      }
    });
    obj.start();
  }

  private CancellableRunnable delayedProgress;

  public void showProgressDelayed (final String message, final ProgressPopupListener listener, long delay) {
    if (delayedProgress != null) {
      delayedProgress.cancel();
    }
    delayedProgress = new CancellableRunnable() {
      @Override
      public void act () {
        showProgress(message, listener);
      }
    };
    UI.post(delayedProgress, delay);
  }

  // Popup

  private static final float POPUP_OVERLAY_ALPHA = .3f;

  public static class PopupAnimation {
    private final View target, overlay;
    private final float startY, diffY;
    private final float startAlpha, diffAlpha;

    public PopupAnimation (View target, View overlay, float startY, float endY, float startAlpha, float endAlpha) {
      this.target = target;
      this.overlay = overlay;
      this.startY = startY;
      this.diffY = endY - startY;
      this.startAlpha = startAlpha;
      this.diffAlpha = endAlpha - startAlpha;
    }

    public void setFactor (float factor) {
      if (target != null) {
        target.setTranslationY(startY + diffY * factor);
      }
      if (overlay != null) {
        overlay.setAlpha(startAlpha + diffAlpha * factor);
      }
    }
  }

  public final void hideSoftwareKeyboard () {
    final ViewController<?> controller = navigation.getCurrentStackItem();
    if (controller != null) {
      controller.hideSoftwareKeyboard();
    }
    PopupLayout popupLayout = getCurrentPopupWindow();
    if (popupLayout != null) {
      popupLayout.hideSoftwareKeyboard();
    }
  }

  private final ArrayList<OverlayView> overlayViews = new ArrayList<>();

  public PopupLayout getCurrentPopupWindow () {
    return windows.isEmpty() ? null : windows.get(windows.size() - 1);
  }

  private static boolean isContextual (View view) {
    return view instanceof OptionsLayout || view instanceof MenuMoreWrap /*|| view instanceof StickerPreviewView*/;
  }

  private static boolean isContextual (ViewController<?> c) {
    return c instanceof InstantViewController || (c instanceof MediaViewController && ((MediaViewController) c).getMode() != MediaViewController.MODE_GALLERY);
  }

  public void hideContextualPopups (boolean byNavigation) {
    if (!windows.isEmpty()) {
      final int size = windows.size();
      for (int i = size - 1; i >= 0; i--) {
        PopupLayout window = windows.get(i);
        View boundView = window.getBoundView();
        ViewController<?> boundController = window.getBoundController();
        if (isContextual(boundView) || (byNavigation && boundView instanceof StickerSetWrap) || (boundView instanceof MediaLayout && !(navigation.getCurrentStackItem() instanceof MessagesController)) || (byNavigation && isContextual(boundController)) ) {
          window.hideWindow(true);
        }
      }
    }
  }

  public boolean isWindowPopupShowing () {
    return windows != null && !windows.isEmpty();
  }

  public View getContentView () {
    return contentView;
  }

  public int getControllerWidth (View view) {
    int viewWidth = view.getMeasuredWidth();
    return viewWidth != 0 ? viewWidth : navigation.get().getMeasuredWidth();
  }

  // StickerPreview

  private PopupLayout stickerPreviewWindow;
  private StickerPreviewView stickerPreview;
  private StickerSmallView stickerPreviewControllerView;

  public void openStickerPreview (Tdlib tdlib, StickerSmallView stickerView, TGStickerObj sticker, int cx, int cy, int maxWidth, int viewportHeight, boolean disableEmojis) {
    if (stickerPreview != null) {
      return;
    }

    stickerPreviewControllerView = stickerView;

    stickerPreview = new StickerPreviewView(this);
    stickerPreview.setControllerView(stickerPreviewControllerView);
    stickerPreview.setSticker(tdlib, sticker, cx, cy, maxWidth, viewportHeight, disableEmojis);

    stickerPreviewWindow = new PopupLayout(this);
    stickerPreviewWindow.setBackListener(stickerPreview);
    stickerPreviewWindow.setOverlayStatusBar(true);
    stickerPreviewWindow.init(true);
    stickerPreviewWindow.setNeedRootInsets();
    stickerPreviewWindow.showAnimatedPopupView(stickerPreview, stickerPreview);
  }

  public void openStickerMenu (StickerSmallView stickerView, TGStickerObj sticker) {
    if (this.stickerPreview != null && stickerPreviewControllerView == stickerView) {
      stickerPreview.openMenu(sticker);
    }
  }

  public void dispatchStickerMenuTouchEvent (MotionEvent e) {
    if (stickerPreview != null) {
      stickerPreview.dispatchMenuTouchEvent(e);
    }
  }

  public void replaceStickerPreview (TGStickerObj sticker, int cx, int cy) {
    if (stickerPreview != null) {
      stickerPreview.replaceSticker(sticker, cx, cy);
    }
  }

  public void closeStickerPreview () {
    if (stickerPreviewWindow != null) {
      stickerPreviewWindow.hideWindow(true);
      stickerPreviewWindow = null;
      stickerPreview = null;
    }
  }

  // ReactionPreview

  public void openReactionPreview (Tdlib tdlib, StickerSmallView stickerView, TGReaction reaction, @Nullable TGStickerObj effectAnimation, int cx, int cy, int maxWidth, int viewportHeight, boolean disableEmojis) {
    if (stickerPreview != null) {
      return;
    }

    stickerPreviewControllerView = stickerView;

    stickerPreview = new StickerPreviewView(this);
    stickerPreview.setControllerView(stickerPreviewControllerView);
    stickerPreview.setReaction(tdlib, reaction, effectAnimation, cx, cy, maxWidth, viewportHeight, disableEmojis);

    stickerPreviewWindow = new PopupLayout(this);
    stickerPreviewWindow.setBackListener(stickerPreview);
    stickerPreviewWindow.setOverlayStatusBar(true);
    stickerPreviewWindow.init(true);
    stickerPreviewWindow.setNeedRootInsets();
    stickerPreviewWindow.showAnimatedPopupView(stickerPreview, stickerPreview);
  }

  public void replaceReactionPreview (TGReaction reaction, int cx, int cy) {
    if (stickerPreview != null) {
      stickerPreview.replaceReaction(reaction, cx, cy);
    }
  }

  public void replaceReactionPreviewCords (int cx, int cy) {
    if (stickerPreview != null) {
      stickerPreview.replaceStartCords(cx, cy);
    }
  }

  // Force touch

  private PopupLayout forceTouchWindow;
  private ForceTouchView forceTouchView;

  public boolean openForceTouch (ForceTouchView.ForceTouchContext context) {
    if (forceTouchWindow != null) {
      return false;
    }

    forceTouchView = new ForceTouchView(this);
    try {
      forceTouchView.initWithContext(context);
    } catch (Throwable t) {
      Log.e("Unable to open force touch preview", t);
      return false;
    }

    forceTouchWindow = new PopupLayout(this);
    forceTouchWindow.setOverlayStatusBar(true);
    if (Device.NEED_FORCE_TOUCH_ROOT_INSETS) {
      // forceTouchWindow.setNeedRootInsets();
    }
    forceTouchWindow.init(true);
    if (!context.allowFullscreen()) {
      forceTouchWindow.setNeedRootInsets();
    }
    if (context.needHideKeyboard()) {
      forceTouchWindow.setHideKeyboard();
    }
    forceTouchWindow.showAnimatedPopupView(forceTouchView, forceTouchView);
    return true;
  }

  public void processForceTouchMoveEvent (float x, float y, float startX, float startY) {
    if (forceTouchWindow != null) {
      forceTouchView.processMoveEvent(x, y, startX, startY);
    }
  }

  public void closeForceTouch () {
    if (forceTouchWindow != null) {
      forceTouchWindow.hideWindow(true);
      forceTouchWindow = null;
      forceTouchView = null;
    }
  }

  // Inline results

  private InlineResultsWrap inlineResultsView;

  public void updateHackyOverlaysPositions () {
    if (inlineResultsView != null && inlineResultsView.getParent() != null) {
      inlineResultsView.updatePosition(true);
    }
    roundVideoController.checkLayout();
    if (tooltipOverlayView != null) {
      tooltipOverlayView.reposition();
    }
    if (reactionsOverlayView != null) {
      reactionsOverlayView.reposition();
    }
  }

  public void showInlineResults (ViewController<?> context, Tdlib tdlib, @Nullable ArrayList<InlineResult<?>> results, boolean needBackground, @Nullable InlineResultsWrap.LoadMoreCallback callback) {
    if (inlineResultsView == null) {
      if (results == null || results.isEmpty()) {
        return;
      }

      inlineResultsView = new InlineResultsWrap(this);
    }

    if (results != null && !results.isEmpty() && inlineResultsView.getParent() == null) {
      addToRoot(inlineResultsView, false);
    }

    inlineResultsView.showItems(context, results, needBackground, callback, !context.isFocused());
  }

  public void addInlineResults (ViewController<?> context, ArrayList<InlineResult<?>> results, InlineResultsWrap.LoadMoreCallback callback) {
    if (inlineResultsView != null) {
      inlineResultsView.addItems(context, results, callback);
    }
  }

  public void setInlineResultsHidden (MessagesController controller, boolean hidden) {
    if (inlineResultsView != null && inlineResultsView.getTdlibDelegate() == controller) {
      inlineResultsView.updatePosition(false);
      inlineResultsView.setHidden(hidden);
    }
  }

  public boolean areInlineResultsVisible () {
    return inlineResultsView != null && inlineResultsView.isDisplayingItems();
  }

  // etc

  @Override
  public void onSaveInstanceState (Bundle outState) {
    gestureController.onCancel();
    try {
      super.onSaveInstanceState(outState);
    } catch (Throwable t) {
      Tracer.onUiError(t);
      throw t;
    }
  }


  public void putActivityResultHandler (int requestCode, ActivityResultHandler handler) {
    if (handler != null) {
      activityResultHandlers.put(requestCode, handler);
    } else {
      activityResultHandlers.remove(requestCode);
    }
  }

  @Override
  protected void onActivityResult (int requestCode, int resultCode, Intent data) {
    // TODO rework to registerForActivityResult
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == Intents.ACTIVITY_RESULT_GOOGLE_PLAY_UPDATE) {
      appUpdater.onGooglePlayFlowActivityResult(resultCode, data);
      return;
    }
    final int handlerIndex = activityResultHandlers.indexOfKey(requestCode);
    if (handlerIndex >= 0) {
      ActivityResultHandler handler = activityResultHandlers.valueAt(handlerIndex);
      activityResultHandlers.removeAt(handlerIndex);
      handler.onActivityResult(requestCode, resultCode, data);
      return;
    }
    ViewController<?> c = navigation.getStack().getCurrent();
    if (c instanceof ActivityResultHandler) {
      ((ActivityResultHandler) c).onActivityResult(requestCode, resultCode, data);
    }
    if (!windows.isEmpty()) {
      final int size = windows.size();
      for (int i = size - 1; i >= 0; i--) {
        windows.get(i).onActivityResult(requestCode, resultCode, data);
      }
    }


    /*if (resultCode == Activity.RESULT_OK) {

    } else {
      if (c != null && c instanceof ActivityResultCancelHandler) {
        ((ActivityResultCancelHandler) c).onActivityResultCancel(requestCode, resultCode);
      }
      if (currentPopup != null && currentPopup instanceof ActivityResultCancelHandler) {
        ((ActivityResultCancelHandler) currentPopup).onActivityResultCancel(requestCode, resultCode);
      }
      if (!windows.isEmpty()) {
        final int size = windows.size();
        for (int i = size - 1; i >= 0; i--) {
          windows.get(i).onActivityResultCancel(requestCode, resultCode);
        }
      }
    }*/
  }

  // popup

  private final ArrayList<PopupLayout> windows = new ArrayList<>();
  private RootDrawable rootDrawable;

  public void setRootDrawable (RootDrawable d) {
    this.rootDrawable = d;
  }

  public void setContentHiddenUnderLastPopup (boolean isHidden) {
    if (rootDrawable != null) {
      rootDrawable.setDisabled(isHidden);
    }
    final int visibility = isHidden ? View.GONE : View.VISIBLE;
    navigation.get().setVisibility(visibility);
    for (int i = 0; i < windows.size() - 1; i++) {
      windows.get(i).setVisibility(visibility);
    }
  }

  public void showPopupWindow (PopupLayout window) {
    if (isActivityBusyWithSomething()) {
      boolean exit = true;

      if ((window.getBoundController() instanceof MediaViewController && ((MediaViewController) window.getBoundController()).isFromCamera()) || window.getBoundView() instanceof OptionsLayout || window.getBoundView() instanceof MenuMoreWrap) {
        exit = false;
      } else if (!windows.isEmpty()) {
        PopupLayout firstWindow = windows.get(0);
        if ((firstWindow.getBoundController() instanceof MediaViewController && ((MediaViewController) firstWindow.getBoundController()).isFromCamera()) || firstWindow.getBoundView() instanceof OptionsLayout || firstWindow.getBoundView() instanceof MenuMoreWrap) {
          exit = false;
        }
      }

      if (exit) {
        window.onActivityDestroy();
        return;
      }
    }
    hideContextualPopups(false);
    windows.add(window);
    checkDisallowScreenshots();
    window.showBoundWindow(rootView);
  }

  public boolean hasAnimatingWindow () {
    for (PopupLayout window : windows) {
      if (!window.hadCompletelyShown()) {
        return true;
      }
    }
    return false;
  }

  public boolean hasMoreWindow () {
    for (PopupLayout window : windows) {
      if (window.getBoundView() instanceof MenuMoreWrap) {
        return true;
      }
    }
    return false;
  }

  public void removeWindowFromList (PopupLayout window) {
    if (!windows.remove(window)) {
      completelyForgetThisWindow(window);
    }
    checkDisallowScreenshots();
  }

  public @Nullable ViewController<?> getCurrentlyOpenWindowedViewController () {
    PopupLayout popupLayout = getCurrentPopupWindow();
    return popupLayout != null ? popupLayout.getBoundController() : null;
  }

  public boolean dismissLastOpenWindow (boolean byKeyPress, boolean byBackPress, boolean byHeaderBackPress) {
    final int size = windows.size();
    for (int i = size - 1; i >= 0; i--) {
      PopupLayout window = windows.get(i);
      if (window.isBoundWindowShowing()) {
        if (byKeyPress && window.canHideKeyboard()) {
          return window.hideSoftwareKeyboard();
        }
        if (byBackPress && window.onBackPressed(byHeaderBackPress)) {
          return true;
        }
        window.hideWindow(true);
        return true;
      }
    }
    return false;
  }

  private final SparseArrayCompat<PopupLayout> forgottenWindows = new SparseArrayCompat<>();

  public void pretendYouDontKnowThisWindow (PopupLayout window) {
    int i = windows.indexOf(window);
    if (i != -1) {
      windows.remove(i);
      while (forgottenWindows.get(i) != null) {
        i++;
      }
      forgottenWindows.put(i, window);
    }
  }

  private int indexOfForgottenWindow (PopupLayout window) {
    final int size = forgottenWindows.size();
    for (int i = size - 1; i >= 0; i--) {
      PopupLayout forgottenWindow = forgottenWindows.valueAt(i);
      if (forgottenWindow == window) {
        return forgottenWindows.keyAt(i);
      }
    }
    return -1;
  }

  public void letsRememberAboutThisWindow (PopupLayout window) {
    int oldIndex = indexOfForgottenWindow(window);
    if (oldIndex != -1) {
      int putAtIndex = Math.min(oldIndex, windows.size());
      if (putAtIndex == windows.size()) {
        windows.add(window);
      } else {
        windows.add(putAtIndex, window);
      }
      forgottenWindows.remove(oldIndex);
      checkDisallowScreenshots();
    }
  }

  public void closeOtherPips () {
    final int size = forgottenWindows.size();
    for (int i = 0; i < size; i++) {
      PopupLayout popupLayout = forgottenWindows.valueAt(i);
      if (popupLayout != null) {
        if (popupLayout.getBoundController() instanceof MediaViewController) {
          ((MediaViewController) popupLayout.getBoundController()).close();
        } else if (popupLayout.getBoundView() instanceof PreviewLayout) {
          ((PreviewLayout) popupLayout.getBoundView()).forceClose(true);
        }
      }
    }
  }

  public void closeFilePip (TdApi.File[] targetFiles) {
    final int size = forgottenWindows.size();
    for (int i = 0; i < size; i++) {
      PopupLayout popupLayout = forgottenWindows.valueAt(i);
      if (popupLayout != null && popupLayout.getBoundController() instanceof MediaViewController) {
        MediaViewController mvc = ((MediaViewController) popupLayout.getBoundController());
        TdApi.File currentFile = mvc.getCurrentFile();
        if (currentFile != null) {
          for (TdApi.File file : targetFiles) {
            if (currentFile.id == file.id) {
              mvc.close();
              break;
            }
          }
        }
      }
    }
  }

  public boolean completelyForgetThisWindow (PopupLayout window) {
    final int size = forgottenWindows.size();
    for (int i = size - 1; i >= 0; i--) {
      PopupLayout forgottenWindow = forgottenWindows.valueAt(i);
      if (forgottenWindow == window) {
        forgottenWindows.removeAt(i);
        return true;
      }
    }
    return false;
  }

  public final SparseArrayCompat<PopupLayout> getForgottenWindows () {
    return forgottenWindows;
  }

  // Permissions 2.0

  private final Permissions permissions = new Permissions(this);

  public Permissions permissions () {
    return permissions;
  }

  // Permissions

  public static final int REQUEST_USE_FINGERPRINT = 0x01;
  public static final int REQUEST_USE_CAMERA = 0x02;
  // public static final int REQUEST_USE_MIC = 0x03;
  public static final int REQUEST_READ_STORAGE = 0x04;
  // public static final int REQUEST_WRITE_STORAGE = 0x05;
  public static final int REQUEST_FINE_LOCATION = 0x06;
  public static final int REQUEST_CUSTOM = 0x07;
  public static final int REQUEST_USE_MIC_CALL = 0x08;
  public static final int REQUEST_CUSTOM_NEW = 0x09;

  public void requestCameraPermission () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_USE_CAMERA);
    }
  }

  public void requestFingerprintPermission () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(new String[] {Manifest.permission.USE_FINGERPRINT}, REQUEST_USE_FINGERPRINT);
    }
  }

  /*public void requestMicPermission () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_USE_MIC);
    }
  }*/

  private ActivityPermissionResult requestMicPermissionCallback;

  public void requestMicPermissionForCall (@Nullable ActivityPermissionResult after) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      this.requestMicPermissionCallback = after;
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_USE_MIC_CALL);
    }
  }

  private ActivityPermissionResult requestCustomPermissionCallback;

  public void requestCustomPermissions (String[] permissions, ActivityPermissionResult after) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      this.requestCustomPermissionCallback = after;
      try {
        requestPermissions(permissions, REQUEST_CUSTOM_NEW);
      } catch (Throwable t) {
        Log.e("Cannot check permissions: %s", TextUtils.join(", ", permissions));
        int[] results = new int[permissions.length];
        for (int i = 0; i < results.length; i++) {
          results[i] = PackageManager.PERMISSION_DENIED;
        }
        after.onPermissionResult(REQUEST_CUSTOM_NEW, permissions, results, 0);
        this.requestCustomPermissionCallback = null;
      }
    }
  }

  private final SparseArrayCompat<ActivityResultHandler> activityResultHandlers = new SparseArrayCompat<>();
  private final SparseArrayCompat<ActivityPermissionResult> permissionsResultHandlers = new SparseArrayCompat<>();

  public void requestLocationPermission (boolean needBackground, boolean skipAlert, ActivityPermissionResult handler) {
    requestLocationPermission(needBackground, skipAlert, () -> {
      String[] permissions = locationPermissions(needBackground);
      int[] grantResults = new int[permissions.length];
      for (int i = 0; i < permissions.length; i++) {
        grantResults[i] = PackageManager.PERMISSION_GRANTED;
      }
      handler.onPermissionResult(REQUEST_FINE_LOCATION, permissions, grantResults, 0);
    }, handler);
  }

  public void requestLocationPermission (boolean needBackground, boolean skipAlert, Runnable onCancel, ActivityPermissionResult handler) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (skipAlert) {
        requestLocationPermissionImpl(needBackground, handler);
      } else {
        ModernOptions.showLocationAlert(navigation.getCurrentStackItem(), needBackground, onCancel, () -> {
          requestLocationPermissionImpl(needBackground, handler);
        });
      }
    }
  }

  private static String[] locationPermissions (boolean needBackground) {if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Config.REQUEST_BACKGROUND_LOCATION && needBackground) {
      return new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    } else {
      return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    }
  }

  private void requestLocationPermissionImpl (boolean needBackground, ActivityPermissionResult handler) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      String[] permissions = locationPermissions(needBackground);
      if (handler != null) {
        permissionsResultHandlers.put(REQUEST_FINE_LOCATION, handler);
        requestPermissions(permissions, REQUEST_CUSTOM);
      } else {
        requestPermissions(permissions, REQUEST_FINE_LOCATION);
      }
    }
  }

  public int checkLocationPermissions (boolean needBackground) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      String[] permissions;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Config.REQUEST_BACKGROUND_LOCATION && needBackground) {
        permissions = new String[] {Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
      } else {
        permissions = new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
      }
      for (String permission : permissions) {
        int status = checkSelfPermission(permission);
        if (status != PackageManager.PERMISSION_GRANTED) {
          return status;
        }
      }
    }
    return PackageManager.PERMISSION_GRANTED;
  }

  @Override
  public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull  int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (grantResults.length == 0) {
      return;
    }
    switch (requestCode) {
      case REQUEST_CUSTOM_NEW:
      case REQUEST_USE_MIC_CALL: {
        ActivityPermissionResult callback =
          requestCode == REQUEST_CUSTOM_NEW ?
            requestCustomPermissionCallback :
            requestMicPermissionCallback;
        if (callback != null) {
          if (requestCode == REQUEST_CUSTOM_NEW) {
            requestCustomPermissionCallback = null;
          } else {
            requestMicPermissionCallback = null;
          }
          int grantCount = 0;
          for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
              grantCount++;
            }
          }
          callback.onPermissionResult(requestCode, permissions, grantResults, grantCount);
        }
        break;
      }
      case REQUEST_USE_CAMERA: {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          UI.openCameraDelayed(this);
        } else {
          UI.showToast(R.string.cam_hint, Toast.LENGTH_SHORT);
        }
        break;
      }

      /*case REQUEST_USE_MIC: {
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
          ViewController c = UI.getCurrentStackItem();
          if (c != null) {
            c.openMissingMicrophonePermissionAlert();
          } else {
            UI.showToast(R.string.mic_hint, Toast.LENGTH_SHORT);
          }
        }
        break;
      }*/

      case REQUEST_CUSTOM: {
        int key = 0;
        for (String permission : permissions) {
          if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission) || Manifest.permission.ACCESS_COARSE_LOCATION.equals(permission)) {
            key = REQUEST_FINE_LOCATION;
            break;
          }
        }
        if (key != 0) {
          ActivityPermissionResult handler = permissionsResultHandlers.get(key);
          if (handler != null) {
            int grantCount = 0;
            for (int grantResult : grantResults) {
              if (grantResult == PackageManager.PERMISSION_GRANTED) {
                grantCount++;
              }
            }
            handler.onPermissionResult(requestCode, permissions, grantResults, grantCount);
            break;
          }
        }
        // else act with other cases
      }

      default: {
        View currentPopup = getCurrentPopupWindow();
        if (currentPopup != null && currentPopup instanceof ActivityListener) {
          ((ActivityListener) currentPopup).onActivityPermissionResult(requestCode, grantResults[0] == PackageManager.PERMISSION_GRANTED);
        } else {
          ViewController<?> controller = navigation.getCurrentStackItem();
          if (controller != null) {
            controller.onRequestPermissionResult(requestCode, grantResults[0] == PackageManager.PERMISSION_GRANTED);
          }
        }

        break;
      }
    }
  }

  public final void checkDisallowScreenshots () {
    if (UI.TEST_MODE == UI.TEST_MODE_AUTO) {
      // Allow screen capture in Firebase Labs
      return;
    }
    boolean disallowScreenshots = false;
    disallowScreenshots = (navigation.shouldDisallowScreenshots() || Passcode.instance().shouldDisallowScreenshots());
    for (PopupLayout popupLayout : windows) {
      boolean shouldDisallowScreenshots = popupLayout.shouldDisallowScreenshots();
      popupLayout.checkWindowFlags();
      if (shouldDisallowScreenshots) {
        disallowScreenshots = true;
      }
    }
    for (int i = 0; i < forgottenWindows.size(); i++) {
      PopupLayout popupLayout = forgottenWindows.valueAt(i);
      if (popupLayout == null)
        continue;
      boolean shouldDisallowScreenshots = popupLayout.shouldDisallowScreenshots();
      popupLayout.checkWindowFlags();
      if (shouldDisallowScreenshots) {
        disallowScreenshots = true;
      }
    }
    setDisallowScreenshots(disallowScreenshots);
  }

  private void setDisallowScreenshots (boolean disallow) {
    setWindowFlags(disallow ? WindowManager.LayoutParams.FLAG_SECURE : 0, WindowManager.LayoutParams.FLAG_SECURE);
  }

  public interface ActivityListener {
    void onActivityPause ();
    void onActivityResume ();
    void onActivityDestroy ();
    void onActivityPermissionResult (int code, boolean granted);
  }

  public interface PopupAnimatorOverride {
    boolean shouldOverrideHideAnimation ();
    Animator createCustomHideAnimator ();
    void modifyBaseHideAnimator (ValueAnimator animator);

    boolean shouldOverrideShowAnimation ();
    Animator createCustomShowAnimator ();
    void modifyBaseShowAnimator (ValueAnimator animator);
  }

  public interface ProgressPopupListener {
    void onClose ();
  }

  // Animation target

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ID_CAMERA: {
        setCameraFactor(factor, false);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ID_CAMERA: {
        processCameraAnimationFinish(finalFactor);
        break;
      }
    }
  }

  // == Custom camera utils ==

  private static final int ANIMATOR_ID_CAMERA = 0;

  private float cameraFactor;
  private boolean isCameraOpen;
  private ViewController.CameraOpenOptions cameraOptions;

  private FactorAnimator cameraAnimator;
  private CameraController camera;

  private boolean canOpenCamera () {
    return !(
      // getCurrentPopupWindow() != null ||
      (cameraAnimator != null && cameraAnimator.isAnimating()) ||
      activityState != UI.STATE_RESUMED ||
      recordAudioVideoController.isOpen() ||
      isCameraOwnershipTaken ||
      isNavigationBusy()
    );
  }

  // Drag & drop

  public void prepareCameraDragByTouchDown (ViewController.CameraOpenOptions options, boolean isOpen) { // User has touched down camera button
    if (isCameraOpen == isOpen || !canOpenCamera()) {
      return;
    }
    rootView.prepareVerticalDrag(options, isOpen);
  }

  public void prepareCameraDragCloseByTouchDown () {
    if (isCameraOpen && camera != null && camera.isFocused()) {
      prepareCameraDragByTouchDown(null, false);
    }
  }

  private boolean isCameraDragging;

  public boolean isCameraDragging () {
    return isCameraDragging;
  }

  public boolean isActivityBusyWithSomething () {
    return isCameraOpen || isCameraDragging || isNavigationBusy();
  }

  public boolean isNavigationBusy () {
    return recordAudioVideoController.isOpen() || hasAnimatingWindow() || navigation.isAnimating() || navigation.getHeaderView().isAnimating();
  }

  private boolean openCameraByPermissionRequest (ViewController.CameraOpenOptions options) {
    RunnableBool after = granted -> {
      if (granted) {
        openCameraByTap(options);
      }
    };
    if (options.optionalMicrophone) {
      return permissions().requestAccessCameraPermission(after);
    } else {
      return permissions().requestRecordVideoPermissions(after);
    }
  }

  public static int getAndroidOrientationPortrait () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
    } else {
      return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }
  }

  private static boolean isAndroidOrientationPortrait (int orientation) {
    if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
      return true;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      return orientation == ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
    }
    return false;
  }

  public boolean startCameraDrag (ViewController.CameraOpenOptions options, boolean isOpen) { // User has slided enough to start dragging
    if (isCameraOpen == isOpen || !canOpenCamera() /*|| isKeyboardVisible()*/) {
      return false;
    }
    if (isOpen) {
      if (openCameraByPermissionRequest(options)) {
        return false;
      }
      if (!isAndroidOrientationPortrait(currentOrientation) && camera != null && !camera.supportsCustomRotations()) {
        return false;
      }
    }
    setCameraDragging(true);
    if (!isOpen) {
      camera.setInEarlyInitialization();
    }
    setCameraOpen(options, isOpen, true);
    camera.setWillProbablyFocus(true);
    return true;
  }

  private static final long CAMERA_DURATION_REGULAR = 240l;
  private static final long CAMERA_DURATION_FLING = 140l;
  private static final long CAMERA_DURATION_DROP = 200l;

  public void dropCameraDrag () {
    dropCameraDrag(cameraFactor >= .8f, false);
  }

  public void dropCameraDrag (boolean open, boolean byFling) {
    if (isCameraDragging) {
      setCameraDragging(false);
      open = open || isCameraBlocked;
      if (!open) {
        camera.setWillProbablyFocus(false);
      }
      setCameraOpen(cameraOptions, open, true);
      if ((open && cameraFactor == 1f) || (!open && cameraFactor == 0f)) {
        processCameraAnimationFinish(cameraFactor);
        return;
      }
      final long duration = byFling && open ? CAMERA_DURATION_FLING : CAMERA_DURATION_DROP;
      cameraAnimator.setDuration(duration);
      cameraAnimator.animateTo(open ? 1f : 0f);
    }
  }

  public void setCameraDragFactor (float factor) {
    if (isCameraDragging) {
      if (cameraAnimator != null) {
        cameraAnimator.forceFactor(factor);
      }
      setCameraFactor(factor, true);
    }
  }

  private boolean isCameraBlocked;

  public void setCameraBlocked (boolean isBlocked) {
    if (this.isCameraBlocked != isBlocked) {
      this.isCameraBlocked = isBlocked;
      if (isBlocked) {
        dropCameraDrag();
      }
      // TODO?
    }
  }

  // Camera itself

  public boolean openCameraByTap (@NonNull ViewController.CameraOpenOptions options) { // User has clicked on camera button
    if (isCameraOpen) {
      return true;
    }
    if (!canOpenCamera()) {
      return false;
    }
    if (isKeyboardVisible()) {
      hideSoftwareKeyboard();
      handler.sendMessageDelayed(Message.obtain(handler, OPEN_CAMERA_BY_TAP, options), 100l);
      return false;
    }
    hideSoftwareKeyboard();

    if (openCameraByPermissionRequest(options)) {
      return false;
    }

    setCameraOpen(options, true, false);
    return true;
  }

  public void closeCameraByBackPress () {
    if (camera.isRecording() || isCameraBlocked) {
      return;
    }
    setCameraDragging(false);
    setCameraOpen(null, false, false);
  }

  public void forceCloseCamera () {
    if (!isCameraOpen) {
      return;
    }
    setCameraDragging(true);
    setCameraFactor(0f, true);
    dropCameraDrag(false, false);
  }

  private void setCameraDragging (boolean isDragging) {
    if (this.isCameraDragging != isDragging) {
      this.isCameraDragging = isDragging;
      checkCameraUi();
    }
  }

  private boolean cameraOrientationBlocked;
  private int savedAnimation;

  private void checkCameraOrientationBlocked () {
    boolean isBlocked = ((cameraFactor < 1f && isCameraOpen) || (cameraFactor != 0f && cameraFactor != 1f) || isCameraDragging || (cameraFactor == 1f && camera != null && camera.supportsCustomRotations())) && !(camera != null && camera.hasOpenEditor());
    if (cameraOrientationBlocked != isBlocked) {
      cameraOrientationBlocked = isBlocked;
      setIsOrientationBlocked(isBlocked);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      boolean needCrossFadeAnimation = cameraFactor == 1f;
      int desiredRotation;
      Window window = getWindow();
      WindowManager.LayoutParams attrs = window.getAttributes();
      if (needCrossFadeAnimation) {
        if (attrs.rotationAnimation != WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT) {
          savedAnimation = attrs.rotationAnimation;
        }
        desiredRotation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
      } else {
        desiredRotation = savedAnimation;
      }
      if (attrs.rotationAnimation != desiredRotation) {
        attrs.rotationAnimation = desiredRotation;
        window.setAttributes(attrs);
      }
    }
    /*if (isBlocked && camera != null && !camera.supportsCustomRotations()) {
      lockOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }*/
  }

  private void setCameraOpen (ViewController.CameraOpenOptions options, boolean isOpen, boolean byDrag) {
    if (this.isCameraOpen != isOpen) {
      this.isCameraOpen = isOpen;
      if (isOpen) {
        this.cameraOptions = options;
      }

      if (cameraAnimator == null) {
        cameraAnimator = new FactorAnimator(ANIMATOR_ID_CAMERA, this, AnimatorUtils.DECELERATE_INTERPOLATOR, CAMERA_DURATION_REGULAR, this.cameraFactor);
      }

      final float toFactor = isOpen ? 1f : 0f;
      if (!byDrag) {
        cameraAnimator.setDuration(CAMERA_DURATION_REGULAR);
      }
      if (cameraAnimator.isAnimating()) {
        if (byDrag) {
          cameraAnimator.cancel();
        } else {
          cameraAnimator.animateTo(toFactor);
        }
      } else {
        if (isOpen) {
          replaceContentWithCamera(!byDrag);
        } else {
          replaceCameraWithContent(!byDrag);
        }
      }
    }
  }

  public void applyContentTranslation (InterceptLayout layout) {
    if (camera != null) {
      int contentHeight = layout.getMeasuredHeight();
      float translationY = -contentHeight * cameraFactor;
      layout.setTranslationY(translationY);
      if (statusBar != null) {
        statusBar.setTranslationY(translationY);
      }
      camera.setContentShadowTop(translationY + contentHeight);
      updateHackyOverlaysPositions();
    }
  }

  public void checkCameraUi () {
    boolean locked = (camera != null && camera.hasOpenEditor());
    setScreenFlagEnabled(SCREEN_FLAG_CAMERA_OPEN, cameraFactor > 0f || isCameraDragging);
    setFullScreenFlag(FULLSCREEN_FLAG_CAMERA, (cameraFactor > 0f || isCameraDragging) && !locked);
    // setFullScreenCamera(isCameraPrepared, isCameraPrepared && !locked);
    checkCameraOrientationBlocked();
    setIsTranslucent(isCameraPrepared || (camera != null && camera.hasOpenEditor()));
  }

  private void setCameraFactor (float factor, boolean byDrag) {
    if (factor < 0f) {
      factor = -factor;
    }
    if (this.cameraFactor != factor) {
      boolean isGrowing = factor > this.cameraFactor;
      this.cameraFactor = factor;
      checkCameraUi();
      applyContentTranslation(contentView);
      camera.setAppearFactor(factor, byDrag, isGrowing);
      if (byDrag && cameraAnimator != null) {
        cameraAnimator.forceFactor(factor);
      }
    }
  }

  private void processCameraAnimationFinish (final float toFactor) {
    if (toFactor == 1f && isCameraOpen) {
      onCameraCompletelyOpen();
    } else if (toFactor == 0f && !isCameraOpen) {
      onCameraCompletelyClosed();
    }
  }

  private void replaceCameraWithContent (final boolean launchAnimation) { // called before closing, when camera has been completely open
    if (contentView.getParent() == null) {
      if (camera.isFocused()) {
        if (launchAnimation) {
          Runnable callback = () -> {
            contentView.startAnimatorOnLayout(cameraAnimator, 0f);
            rootView.addView(contentView, 1);
          };
          if (camera.hasRenderedFrame()) {
            camera.scheduleAnimation(callback, -1);
          } else {
            callback.run();
          }
        } else {
          rootView.addView(contentView, 1);
        }
        camera.setInEarlyInitialization();
        camera.onBlur();
      }
    } else if (launchAnimation) {
      cameraAnimator.animateTo(0f);
    }
  }

  private void onCameraCompletelyOpen () { // camera is fully visible & user is not dragging
    rootView.removeView(contentView);
    camera.onFocus();
    camera.setWillProbablyFocus(false);
    hideSoftwareKeyboard();
  }

  public boolean dispatchCameraMargins (View view, int left, int top, int right, int bottom) {
    if (view != null && camera != null && camera.getWrapUnchecked() == view) {
      camera.setControlMargins(left, top, right, bottom);
      return true;
    }
    return false;
  }

  private final DisplayMetrics metrics = new DisplayMetrics();

  public boolean hadSoftwareKeysOnActivityLaunch () {
    return mHasSoftwareKeys;
  }

  public boolean hasSoftwareKeys () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      Display d = getWindowManager().getDefaultDisplay();

      d.getRealMetrics(metrics);

      int realHeight = metrics.heightPixels;
      int realWidth = metrics.widthPixels;

      d.getMetrics(metrics);

      int displayHeight = metrics.heightPixels;
      int displayWidth = metrics.widthPixels;

      int barHeight = Screen.getNavigationBarHeight();

      return barHeight > 0 && ((realWidth - displayWidth) >= barHeight || (realHeight - displayHeight) >= barHeight);
    } else {
      boolean hasMenuKey = ViewConfiguration.get(this).hasPermanentMenuKey();
      boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);

      return !hasMenuKey && !hasBackKey;
    }
  }

  private boolean mIsTranslucent;

  private void setIsTranslucent (boolean isTranslucent) {
    if (Config.USE_TRANSLUCENT_NAVIGATION) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      if (mIsTranslucent != isTranslucent) {
        Window window = getWindow();
        if (window != null) {
          if (isTranslucent) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
          }
          mIsTranslucent = isTranslucent;
        }
      }
    }
  }

  private boolean isCameraPrepared;

  private void setIsCameraPrepared (boolean isPrepared) {
    if (this.isCameraPrepared != isPrepared) {
      this.isCameraPrepared = isPrepared;
      checkCameraUi();
    }
  }

  private long getCurrentChatId () {
    ViewController<?> c = navigation.getCurrentStackItem();
    if (c != null && c instanceof MessagesController) {
      return c.getChatId();
    }
    return 0;
  }

  public void checkCameraApi () {
    if (camera != null) {
      camera.checkLegacyMode();
    }
  }

  private void initializeCamera (ViewController.CameraOpenOptions options) {
    if (camera == null) {
      camera = new CameraController(this);
      camera.setMode(options.mode, options.readyListener);
      camera.setQrListener(options.qrCodeListener, options.qrModeSubtitle, options.qrModeDebug);
      camera.get(); // Ensure view creation
      addActivityListener(camera);
    } else {
      camera.setMode(options.mode, options.readyListener);
      camera.setQrListener(options.qrCodeListener, options.qrModeSubtitle, options.qrModeDebug);
    }
    hideContextualPopups(false);
    closeAllMedia(true);
  }

  private boolean isCameraOwnershipTaken;

  public @Nullable CameraController takeCameraOwnership (ViewController.CameraOpenOptions options) {
    if (isCameraOpen || !canOpenCamera()) {
      return null;
    }
    initializeCamera(options);
    isCameraOwnershipTaken = true;
    return camera;
  }

  public void releaseCameraOwnership () {
    if (isCameraOwnershipTaken) {
      camera.setMode(CameraController.MODE_MAIN, null);
      isCameraOwnershipTaken = false;
    }
  }

  private boolean isOwningCamera () {
    return camera != null && !isCameraOwnershipTaken && camera.get().getParent() != null;
  }

  private void replaceContentWithCamera (final boolean launchAnimation) { // prepare camera
    initializeCamera(cameraOptions);
    if (camera.get().getParent() == null) {
      if (launchAnimation) {
        camera.scheduleAnimation(() -> cameraAnimator.animateTo(1f), -1);
      }
      camera.setInEarlyInitialization();
      camera.setOutputController(navigation.getCurrentStackItem());
      camera.onPrepareToShow();
      setIsCameraPrepared(true);
      rootView.addView(camera.get(), 0);
      ViewController<?> v = navigation.getCurrentStackItem();
      if (v != null) {
        v.onBlur();
      }
    } else if (launchAnimation) {
      cameraAnimator.animateTo(1f);
    }
  }

  private void onCameraCompletelyClosed () { // contentView is fully visible & user is not dragging
    camera.onCleanAfterHide();
    rootView.removeView(camera.get());
    setIsCameraPrepared(false);
    ViewController<?> v = navigation.getCurrentStackItem();
    if (v != null) {
      v.onFocus();
    }
  }

  // Light sensor

  private final ThemeListenerList themeList = new ThemeListenerList();
  private final ArrayList<ThemeListenerList> globalThemeListeners = new ArrayList<>();

  public final void addGlobalThemeListeners (ThemeListenerList list) {
    if (!globalThemeListeners.contains(list)) {
      globalThemeListeners.add(list);
    }
  }

  public final void removeGlobalThemeListeners (ThemeListenerList list) {
    globalThemeListeners.remove(list);
  }

  @Override
  public boolean needsTempUpdates () {
    return isProgressShowing || (inlineResultsView != null && inlineResultsView.areItemsVisible()) || !globalThemeListeners.isEmpty() || Config.USE_CUSTOM_NAVIGATION_COLOR;
  }

  private FutureInt desiredColor;
  private float desiredIntensityFactor;
  private boolean desiredAllowLight;

  public void setCustomNavigationColor (FutureInt runnableInt, float intensity, boolean allowLight) {
    if (Config.USE_CUSTOM_NAVIGATION_COLOR) {
      if (this.desiredColor != runnableInt || this.desiredIntensityFactor != intensity || this.desiredAllowLight != allowLight) {
        this.desiredColor = runnableInt;
        this.desiredIntensityFactor = intensity;
        this.desiredAllowLight = allowLight;
        updateNavigationBarColor();
      } else if (runnableInt != null && intensity > 0f) {
        updateNavigationBarColor();
      }
    }
  }

  private void updateNavigationBarColor () {
    if (Config.USE_CUSTOM_NAVIGATION_COLOR) {
      int color;
      boolean isLight;
      if ((lastWindowVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
        color = UI.NAVIGATION_BAR_COLOR;
        isLight = false;
      } else {
        color = Theme.backgroundColor();
        isLight = !Theme.isDark();
      }
      if (desiredIntensityFactor != 0f) {
        color = ColorUtils.fromToArgb(color, ColorUtils.compositeColor(desiredColor != null ? desiredColor.get() : 0, 0x1E000000), desiredIntensityFactor);
        isLight = isLight && desiredAllowLight;
      }
      if (photoRevealFactor != 0f) {
        color = ColorUtils.fromToArgb(color, UI.NAVIGATION_BAR_COLOR, photoRevealFactor);
        isLight = false;
      }
      float passcodeFactor = isPasscodeShowing ? 1f : dismissingPasscodeController != null ? dismissingPasscodeController.get().getAlpha() : 0f;
      if (passcodeFactor != 0f) {
        color = ColorUtils.fromToArgb(color, Theme.getColor(R.id.theme_color_passcode), passcodeFactor);
        isLight = isLight && passcodeFactor < .5f;
      }
      getWindow().setNavigationBarColor(color);
      if (this.isWindowLight != isLight) {
        this.isWindowLight = isLight;
        int visibility = lastWindowVisibility;
        if (isLight) {
          visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        if (Theme.needLightStatusBar()) {
          visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(visibility);
      }
    }
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    themeList.onThemeColorsChanged(areTemp);
    if (inlineResultsView != null) {
      inlineResultsView.getThemeProvider().onThemeColorsChanged(areTemp);
    }
    if (statusBar != null && (!areTemp || ThemeManager.instance().hasColorChanged(Config.STATUS_BAR_COLOR_ID)))
      statusBar.invalidate();
    if (tooltipOverlayView != null) {
      tooltipOverlayView.invalidate();
    }
    if (reactionsOverlayView != null) {
      reactionsOverlayView.invalidate();
    }
    for (ThemeListenerList list : globalThemeListeners) {
      list.onThemeColorsChanged(areTemp);
    }
    if (Config.USE_CUSTOM_NAVIGATION_COLOR) {
      updateNavigationBarColor();
    }
  }

  @Override
  public void onThemeChanged (ThemeDelegate oldTheme, ThemeDelegate newTheme) {
    if (Config.USE_CUSTOM_NAVIGATION_COLOR && oldTheme.isDark() != newTheme.isDark()) {
      updateNavigationBarColor();
    }
    closeThumbnails(ThemeController.class);
    if (oldTheme.needLightStatusBar() != newTheme.needLightStatusBar()) {
      updateWindowDecorSystemUiVisibility();
    }
    if (oldTheme.isDark() != newTheme.isDark()) {
      updateWindowContextTheme();
    }
  }

  @Override
  public void onThemeAutoNightModeChanged (int autoNightMode) {
    nightOneShot = false;
    handler.post(this::checkAutoNightMode);
  }

  private void checkAutoNightMode () {
    setRegisterLightSensor(activityState == UI.STATE_RESUMED && Settings.instance().getNightMode() == Settings.NIGHT_MODE_AUTO);
    setSystemNightMode(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
  }

  private boolean lightSensorRegistered;
  private SensorManager sensorManager;
  private Sensor lightSensor;
  private boolean lightSensorFast;

  private static final int LUX_SENSOR_LATENCY_REGULAR = 700000;
  private static final int LUX_SENSOR_LATENCY_FAST = 90000;

  private void setLightSensorFast (boolean isFast) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && this.lightSensorFast != isFast) {
      this.lightSensorFast = isFast;
      if (lightSensorRegistered) {
        try {
          sensorManager.unregisterListener(this, lightSensor);
          sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL, lightSensorFast ? LUX_SENSOR_LATENCY_FAST : LUX_SENSOR_LATENCY_REGULAR);
        } catch (Throwable t) {
          Log.w("Cannot re-register sensor event listener", t);
        }
      }
    }
  }

  private void setRegisterLightSensor (boolean register) {
    if (this.lightSensorRegistered == register) {
      return;
    }
    try {
      if (sensorManager == null) {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
      }
      if (lightSensor == null && sensorManager != null) {
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
      }
      register = register && lightSensor != null;

      if (this.lightSensorRegistered == register) {
        Log.i(Log.TAG_LUX, "Cannot register light sensor, because it's unavailable");
        return;
      }

      if (register) {
        inNightMode = Theme.isDark();
        autoNightModeSwitch = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL, lightSensorFast ? LUX_SENSOR_LATENCY_FAST : LUX_SENSOR_LATENCY_REGULAR);
        } else {
          sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
      } else {
        sensorManager.unregisterListener(this, lightSensor);
      }

      lightSensorRegistered = register;
    } catch (Throwable t) {
      Log.w(Log.TAG_LUX, "Cannot %s light sensor", t, register ? "register" : "unregister");
    }
  }

  private boolean needsLightSensorChanges () {
    return (lightSensorRegistered && lightSensor != null && activityState == UI.STATE_RESUMED && Settings.instance().getNightMode() == Settings.NIGHT_MODE_AUTO);
  }

  @Override
  public void onAccuracyChanged (Sensor sensor, int i) {

  }

  public void checkNightMode () {
    if (needsLightSensorChanges()) {
      nightOneShot = false;
      setInNightMode(lastLuxValue <= Settings.instance().getMaxNightLux());
    }
  }

  public interface LuxChangeListener {
    void onLuxChanged (float newLux);
  }

  private final ReferenceList<LuxChangeListener> luxChangeListeners = new ReferenceList<>();

  public void addLuxListener (LuxChangeListener listener) {
    luxChangeListeners.add(listener);
    setLightSensorFast(!luxChangeListeners.isEmpty());
  }

  public void removeLuxListener (LuxChangeListener listener) {
    luxChangeListeners.remove(listener);
    setLightSensorFast(!luxChangeListeners.isEmpty());
  }

  private float lastLuxValue;
  private boolean autoNightModeSwitch;

  public float getLastLuxValue () {
    return lastLuxValue;
  }

  @Override
  public void onSensorChanged (SensorEvent e) {
    switch (e.sensor.getType()) {
      case Sensor.TYPE_LIGHT: {
        final float lux = e.values[0];
        if (needsLightSensorChanges() && (lastLuxValue != lux || autoNightModeSwitch)) {
          lastLuxValue = lux;
          autoNightModeSwitch = false;
          for (LuxChangeListener listener : luxChangeListeners) {
            listener.onLuxChanged(lux);
          }
          setInNightMode(lux <= Settings.instance().getMaxNightLux());
        }
        break;
      }
    }
  }

  private Handler nightHandler;
  private boolean nightOneShot;
  private boolean inNightMode;

  private boolean allowNightModeChange () {
    ViewController<?> c = navigation.getCurrentStackItem();
    return (c == null || c.allowThemeChanges()) && (orientationFlags & ORIENTATION_FLAG_PROXIMITY) == 0;
  }

  @Override
  public void onThemePropertyChanged (int themeId, int propertyId, float value, boolean isDefault) {
    switch (propertyId) {
      case ThemeProperty.DARK:
      case ThemeProperty.LIGHT_STATUS_BAR:
        updateWindowDecorSystemUiVisibility();
        break;
    }
  }

  public void forceNightMode (boolean inNightMode) {
    if (this.inNightMode != inNightMode) {
      this.inNightMode = inNightMode;
      checkNightMode();
    }
  }

  private void setInNightMode (boolean inNightMode) {
    if (this.nightOneShot) {
      if (this.nightHandler == null) {
        this.nightHandler = new Handler(Looper.getMainLooper(), msg -> {
          if (allowNightModeChange()) {
            boolean value = msg.what == 1;
            BaseActivity.this.inNightMode = value;
            ThemeManager.instance().setInNightMode(value, true);
          } else {
            checkNightMode();
          }
          return true;
        });
      }
      int what = inNightMode ? 1 : 0;
      if (!this.nightHandler.hasMessages(what)) {
        this.nightHandler.removeMessages(inNightMode ? 0 : 1);
        if (this.inNightMode != inNightMode) {
          this.nightHandler.sendMessageDelayed(Message.obtain(nightHandler, what), 1500l);
        }
      }
    } else if (allowNightModeChange()) {
      if (this.nightHandler != null) {
        this.nightHandler.removeMessages(0);
        this.nightHandler.removeMessages(1);
      }
      if (this.inNightMode != inNightMode) {
        this.inNightMode = inNightMode;
        ThemeManager.instance().setInNightMode(inNightMode, true);
        this.nightOneShot = true;
      }
    }
  }

  // Screen activity

  public static final int SCREEN_FLAG_PLAYING_ROUND_VIDEO = 1;
  public static final int SCREEN_FLAG_PLAYING_REGULAR_VIDEO = 1 << 1;
  public static final int SCREEN_FLAG_RECORDING = 1 << 2;
  public static final int SCREEN_FLAG_CAMERA_OPEN = 1 << 3;
  public static final int SCREEN_FLAG_PLAYING_FULLSCREEN_WEB_VIDEO = 1 << 4;

  private int screenFlags;

  public void setScreenFlagEnabled (int flag, boolean enabled) {
    boolean oldScreenActive = this.screenFlags != 0;
    this.screenFlags = BitwiseUtils.setFlag(this.screenFlags, flag, enabled);
    boolean newScreenActive = this.screenFlags != 0;
    if (oldScreenActive != newScreenActive) {
      if (newScreenActive) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      } else {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      }
    }
  }

  @Override
  public final void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser) {
    setScreenFlagEnabled(SCREEN_FLAG_PLAYING_ROUND_VIDEO, newTrack != null && state == TGPlayerController.STATE_PLAYING);
  }

  // Block rotation

  public static final int ORIENTATION_FLAG_NAVIGATING = 1;
  public static final int ORIENTATION_FLAG_TOUCHING_NAVIGATION = 1 << 1;
  public static final int ORIENTATION_FLAG_ZOOMING = 1 << 2;
  public static final int ORIENTATION_FLAG_STICKER = 1 << 3;
  public static final int ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT = 1 << 4;
  public static final int ORIENTATION_FLAG_RECORDING = 1 << 5;
  public static final int ORIENTATION_FLAG_CROP = 1 << 6;
  public static final int ORIENTATION_FLAG_PROXIMITY = 1 << 7;

  private int orientationFlags;

  public void setOrientationLockFlagEnabled (int flag, boolean enabled) {
    boolean oldLocked = this.orientationFlags != 0;
    this.orientationFlags = BitwiseUtils.setFlag(this.orientationFlags, flag, enabled);
    boolean newLocked =  this.orientationFlags != 0;
    if (oldLocked != newLocked) {
      setIsOrientationBlocked(newLocked);
    }
  }

  // Language

  @Override
  public final void onLanguagePackEvent (int event, int arg1) {
    boolean directionChanged = Lang.hasDirectionChanged(event, arg1);
    if (directionChanged) {
      if (statusBar != null) {
        statusBar.updateDirection();
      }
    }


    switch (event) {
      case Lang.EVENT_DIRECTION_CHANGED:
        break;
      case Lang.EVENT_DATE_FORMAT_CHANGED:
        break;

      case Lang.EVENT_PACK_CHANGED: {
        if (statusBar != null)
          statusBar.updateText(0);
        break;
      }
      case Lang.EVENT_STRING_CHANGED: {
        if (statusBar != null)
          statusBar.updateText(arg1);
        break;
      }
    }
  }

  // Thumbnail API

  private List<ViewController<?>> openedThumbnails;
  private DragDropLayout thumbnailWrap;

  public final void closeThumbnails (Class<? extends ViewController<?>> clazz) {
    if (openedThumbnails != null) {
      for (ViewController<?> c : openedThumbnails) {
        if (c.getClass() == clazz) {
          closeThumbnail(c);
          break;
        }
      }
    }
  }

  public final void openThumbnail (@NonNull ViewController<?> c) {
    if (openedThumbnails != null && openedThumbnails.contains(c))
      return;

    if (openedThumbnails == null)
      openedThumbnails = new ArrayList<>();
    if (thumbnailWrap == null) {
      thumbnailWrap = new DragDropLayout(this);
      thumbnailWrap.setPadding(0, HeaderView.getTopOffset(), 0, 0);
      thumbnailWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    if (thumbnailWrap.getParent() == null) {
      addToRoot(thumbnailWrap, false);
    }

    openedThumbnails.add(c);
    thumbnailWrap.addViewAnimated(c.getThumbnailWrap());
  }

  public final void closeThumbnail (@NonNull ViewController<?> c) {
    int index = openedThumbnails != null ? openedThumbnails.indexOf(c) : -1;
    if (index == -1)
      return;
    openedThumbnails.remove(index);
    thumbnailWrap.removeViewAnimated(c.getThumbnailWrap(), true);
  }

  // Confetti API

  private KonfettiView konfettiView;

  public void performConfetti (View anchorView, int pivotX, int pivotY) {
    if (konfettiView == null) {
      konfettiView = new KonfettiView(this);
      konfettiView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      addToRoot(konfettiView, true);
    }

    int[] pos;
    pos = Views.getLocationInWindow(rootView);

    int baseX = pos[0];
    int baseY = pos[1];

    pos = Views.getLocationInWindow(anchorView);
    pivotX += pos[0] - baseX;
    pivotY += pos[1] - baseY;

    konfettiView.start(
      KonfettiBuilder.buildKonfettiParty(pivotX, pivotY)
    );
  }
}
