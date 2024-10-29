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
 * File created on 23/04/2015 at 15:36
 */
package org.thunderdog.challegram.tool;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGAudio;
import org.thunderdog.challegram.navigation.DrawerController;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.RootDrawable;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.NetworkListenerService;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.unsorted.AppState;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.Unlockable;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import me.vkryl.android.DeviceUtils;
import me.vkryl.android.LocaleUtils;
import me.vkryl.android.SdkVersion;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.util.InvalidateDelegate;
import me.vkryl.android.util.LayoutDelegate;
import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceList;

public class UI {
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    State.UNKNOWN, State.RESUMED, State.PAUSED, State.DESTROYED
  })
  public @interface State {
    int UNKNOWN = -1, RESUMED = 0, PAUSED = 1, DESTROYED = 2;
  }

  private static Context appContext;
  private static WeakReference<BaseActivity> uiContext;
  private static UIHandler _appHandler;
  private static Handler _progressHandler;
  private static int uiState = State.UNKNOWN;

  private static Boolean isTablet;

  public static final int TEST_MODE_NONE = 0;
  public static final int TEST_MODE_AUTO = 1; // Pre-launch reports
  public static final int TEST_MODE_USER = 2; // Requested by user

  public static int TEST_MODE = TEST_MODE_NONE;

  public static boolean inTestMode () {
    return TEST_MODE != TEST_MODE_NONE;
  }

  public static boolean inUiThread () {
    return Looper.myLooper() == Looper.getMainLooper();
  }

  public static void initApp (final Context context) {
    if (appContext == null && context != null) {
      synchronized (UI.class) {
        if (appContext != null)
          return;
        appContext = context;
      }
      AppState.initApplication();
      if (TEST_MODE != TEST_MODE_AUTO && DeviceUtils.isTestLabDevice(context)) {
        TEST_MODE = TEST_MODE_AUTO;
        TdlibManager.setTestLabConfig();
      }
    }
  }

  public static boolean isTestLab () {
    return UI.TEST_MODE == UI.TEST_MODE_AUTO;
  }

  public static Handler getProgressHandler () {
    if (_progressHandler == null) {
      synchronized (UI.class) {
        if (_progressHandler == null)
          _progressHandler = new Handler(Looper.getMainLooper());
      }
    }
    return _progressHandler;
  }

  public static void setContext (BaseActivity context) {
    uiContext = new WeakReference<>(context);
    if (appContext == null) {
      initApp(context.getApplicationContext());
      if (appContext == null) {
        initApp(context);
      }
    }
  }

  private static boolean startServiceImpl (Context context, Intent intent, boolean isForeground) {
    try {
      if (isForeground) {
        ContextCompat.startForegroundService(context, intent);
      } else {
        context.startService(intent);
      }
      return true;
    } catch (Throwable t) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (t instanceof android.app.ForegroundServiceStartNotAllowedException) {
          Log.e("Cannot start foreground service due to system restrictions", t);
          return false;
        }
      }
      Log.e("Cannot start service, isForeground:%b", t, isForeground);
      return false;
    }
  }

  public static boolean startService (Intent intent, boolean isForeground, boolean forcePermissionRequest, @Nullable CancellationSignal signal) {
    if (isForeground) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        BaseActivity activity = getUiContext();
        if (activity != null) {
          activity.requestCustomPermissions(new String[] {Manifest.permission.FOREGROUND_SERVICE}, (code, permissions, grantResults, grantCount) -> {
            if (signal == null || !signal.isCanceled()) {
              startServiceImpl(activity, intent, true);
            }
          });
          return true;
        } else {
          Log.e("Cannot start foreground service, because activity not found.");
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return startServiceImpl(getContext(), intent, true);
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && forcePermissionRequest) {
      BaseActivity activity = getUiContext();
      if (activity != null) {
        activity.requestCustomPermissions(new String[] {Manifest.permission.FOREGROUND_SERVICE}, (code, permissions, grantResults, grantCount) -> {
          if (signal == null || !signal.isCanceled()) {
            startServiceImpl(activity, intent, false);
          }
        });
        return true;
      } else {
        Log.e("Cannot request foreground service permission, because activity not found.");
      }
    }
    return startServiceImpl(getContext(), intent, false);
  }

  private static long lastResumeTime;

  public static void startNotificationService () {
    if (Config.SERVICES_ENABLED) {
      startService(new Intent(getAppContext(), NetworkListenerService.class), false, false, null);
    }
  }

  public static boolean isTablet () {
    if (isTablet == null) {
      synchronized (UI.class) {
        if (isTablet == null) {
          isTablet = appContext.getResources().getBoolean(R.bool.isTablet);
        }
      }
    }
    return isTablet;
  }

  public interface StateListener {
    void onUiStateChanged (int newState);
  }

  private static final ReferenceList<StateListener> stateListeners = new ReferenceList<>();

  public static void addStateListener (StateListener listener) {
    stateListeners.add(listener);
  }

  public static void removeStateListener (StateListener listener) {
    stateListeners.remove(listener);
  }

  private static HashMap<WeakReference<BaseActivity>, Boolean> resumeStates;

  public static boolean setUiState (BaseActivity activity, int state) {
    WeakReference<BaseActivity> foundKey = null;
    boolean foreground = state == State.RESUMED;
    if (resumeStates == null) {
      if (foreground) {
        resumeStates = new HashMap<>();
      }
    } else {
      final Set<HashMap.Entry<WeakReference<BaseActivity>, Boolean>> entrySet = resumeStates.entrySet();
      ArrayList<WeakReference<BaseActivity>> keysToRemove = null;
      for (HashMap.Entry<WeakReference<BaseActivity>, Boolean> entry : entrySet) {
        WeakReference<BaseActivity> key = entry.getKey();
        Boolean value = entry.getValue();
        BaseActivity current = key.get();
        if (current == null) {
          if (keysToRemove == null) {
            keysToRemove = new ArrayList<>();
          }
          keysToRemove.add(key);
        } else if (current == activity) {
          foundKey = key;
        } else if (!foreground && value != null) {
          foreground = value;
        }
      }
      if (keysToRemove != null) {
        for (WeakReference<BaseActivity> key : keysToRemove) {
          resumeStates.remove(key);
        }
      }
    }
    if (state == State.DESTROYED) {
      if (foundKey != null) {
        resumeStates.remove(foundKey);
      }
    } else {
      Boolean value = state == State.RESUMED;
      if (foundKey != null) {
        resumeStates.put(foundKey, value);
      } else {
        if (resumeStates == null) {
          resumeStates = new HashMap<>();
        }
        resumeStates.put(new WeakReference<>(activity), value);
      }
    }
    return setUiState(foreground ? State.RESUMED : State.PAUSED);
  }

  private static boolean setUiState (int state) {
    if (uiState != state) {
      if ((state == State.PAUSED || state == State.DESTROYED) && uiState == State.RESUMED) {
        lastResumeTime = System.currentTimeMillis();
      }

      boolean called = false;
      if (uiState == State.RESUMED || state == State.RESUMED) {
        called = TdlibManager.instance().watchDog().onBackgroundStateChanged(state != State.RESUMED);
      }

      uiState = state;

      for (StateListener listener : stateListeners) {
        listener.onUiStateChanged(state);
      }
      return called;
    }
    return false;
  }

  public static boolean wasResumedRecently (long resumeTimeLimitMs) {
    return uiState == State.RESUMED || getResumeDiff() <= resumeTimeLimitMs;
  }
  
  public static UIHandler getAppHandler () {
    if (_appHandler == null) {
      synchronized (UIHandler.class) {
        if (_appHandler == null)
          _appHandler = new UIHandler(appContext);
      }
    }
    return _appHandler;
  }

  public static long getResumeDiff () {
    return System.currentTimeMillis() - lastResumeTime;
  }

  public static void clearContext (BaseActivity context) {
    if (getUiContext() == context) {
      uiContext = null;
    }
  }

  public static @Nullable BaseActivity getUiContext () {
    return uiContext != null ? uiContext.get() : null;
  }

  public static boolean isValid (BaseActivity activity) {
    if (activity != null) {
      if (activity.getActivityState() == State.DESTROYED) {
        return false;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        if (activity.isDestroyed()) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public static boolean isNavigationBusyWithSomething () {
    BaseActivity activity = getUiContext();
    return activity != null && activity.isNavigationBusy();
  }

  public static Context getContext () {
    final BaseActivity context = getUiContext();
    return context != null ? context : appContext;
  }

  public static boolean needAmPm () {
    if (appContext != null) {
      try {
        return !DateFormat.is24HourFormat(appContext);
      } catch (Throwable t) {
        Log.w(t);
      }
    }
    return false;
  }

  public static Context getAppContext () {
    return appContext;
  }

  public static void startActivity (Intent intent) {
    final BaseActivity context = getUiContext();
    if (context != null) {
      context.startActivity(intent);
    } else {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getAppContext().startActivity(intent);
    }
  }

  @SuppressWarnings("deprecation")
  public static void startActivityForResult (Intent intent, int reqCode) {
    final BaseActivity context = getUiContext();
    if (context != null) {
      // TODO: rework to Activity Result API
      context.startActivityForResult(intent, reqCode);
    }
  }

  public static int getUiState () {
    return uiState;
  }

  public static boolean isResumed () {
    return uiState == State.RESUMED;
  }

  public static void forceVibrateError (View view) {
    forceVibrate(view, true);
    forceVibrate(view, true);
  }

  public static void forceVibrate (View view, boolean isMain) {
    forceVibrate(view, isMain, false);
  }

  public static void forceVibrate (View view, boolean isMain, boolean forceCustom) {
    ViewUtils.hapticVibrate(view, isMain, forceCustom || Settings.instance().useCustomVibrations());
    /*if (forceCustom || Settings.instance().useCustomVibrations()) {
      vibrate(isMain ? Device.FORCE_VIBRATE_OPEN_DURATION : Device.FORCE_VIBRATE_DURATION);
    } else if (isMain) {
      view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }*/
  }

  public static void hapticVibrate (View view, boolean isForce) {
    ViewUtils.hapticVibrate(view, isForce, Settings.instance().useCustomVibrations());
  }

  public static Resources getResources () {
    return appContext.getResources();
  }

  @SuppressWarnings("deprecation")
  public static Locale getLocale (Configuration configuration) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      android.os.LocaleList list = configuration.getLocales();
      return list.get(0);
    } else {
      return configuration.locale;
    }
  }

  public static Locale getSystemLocale () {
    Configuration configuration = Resources.getSystem().getConfiguration();
    return getLocale(configuration);
  }

  public static Locale getConfigurationLocale () {
    Configuration configuration = getAppContext().getResources().getConfiguration();
    return getLocale(configuration);
  }

  public static void removePendingRunnable (Runnable runnable) {
    getAppHandler().removeCallbacks(runnable);
  }

  public static void checkDisallowScreenshots () {
    getAppHandler().checkDisallowScreenshots();
  }

  public static BaseActivity getContext (Context from) {
    if (from instanceof BaseActivity) {
      return (BaseActivity) from;
    }
    if (from instanceof ContextWrapper) {
      Context baseContext = ((ContextWrapper) from).getBaseContext();
      if (baseContext instanceof BaseActivity) {
        return (BaseActivity) baseContext;
      }
    }
    return null;
  }

  public static void showBotDown (@Nullable String username) {
    if (StringUtils.isEmpty(username)) {
      UI.showToast(R.string.BotIsDown, Toast.LENGTH_SHORT);
    } else {
      UI.showToast(Lang.getString(R.string.BotIsDownSpecific, '@' + username), Toast.LENGTH_SHORT);
    }
  }

  public static void showError (TdApi.Object obj) {
    if (obj.getConstructor() != TdApi.Error.CONSTRUCTOR) {
      showToast(TD.toErrorString(obj), Toast.LENGTH_SHORT);
      return;
    }
    String string = TD.toErrorString(obj);
    if (string != null) {
      Log.critical("TDLib Error: %s", Log.generateException(2), string);
      int errorCode = TD.errorCode(obj);
      if (errorCode != 401 && !(errorCode == 500 && "Client is closed".equals(TD.errorText(obj)))) {
        showToast(string, Toast.LENGTH_SHORT);
      }
    }
  }

  public static void showApiLevelWarning (int apiLevel) {
    getAppHandler().showToast(Lang.getString(R.string.AndroidVersionWarning, SdkVersion.getPrettyName(apiLevel), SdkVersion.getPrettyVersionCode(apiLevel)), Toast.LENGTH_LONG);
  }

  public static void showToast (CharSequence message, int duration) {
    getAppHandler().showToast(message, duration);
  }

  public static void showToast (int resource, int duration) {
    getAppHandler().showToast(resource, duration);
  }

  public static void showCustomToast (CharSequence message, int duration, int positionY) {
    getAppHandler().showCustomToast(message, duration, positionY);
  }

  public static void showCustomToast (int resource, int duration, int positionY) {
    getAppHandler().showCustomToast(resource, duration, positionY);
  }

  public static void showNetworkPrompt () {
    getAppHandler().showToast(R.string.prompt_network, Toast.LENGTH_LONG);
  }

  public static void showProgress (String string, BaseActivity.ProgressPopupListener listener) {
    getAppHandler().showProgress(string, listener);
  }

  public static void showProgress (final String string, final BaseActivity.ProgressPopupListener listener, final long delay) {
    if (delay <= 0l) {
      showProgress(string, listener);
    } else {
      UI.post(() -> {
        if (UI.getUiContext() != null) {
          UI.getUiContext().showProgressDelayed(string, listener, delay);
        }
      });
    }
  }

  public static void hideProgress () {
    getAppHandler().hideProgress();
  }

  public static void invalidate (View view) {
    getAppHandler().invalidate(view);
  }

  public static void invalidate (InvalidateDelegate view, long delay) {
    getAppHandler().invalidate(view, delay);
  }

  public static void requestLayout (View view) {
    getAppHandler().requestLayout(view);
  }

  public static void requestLayout (LayoutDelegate view) {
    getAppHandler().requestLayout(view);
  }

  public static void unlock (Unlockable unlockable) {
    getAppHandler().unlock(unlockable);
  }

  public static void unlock (Unlockable unlockable, long delay) {
    getAppHandler().unlock(unlockable, delay);
  }

  public static @Nullable NavigationController getNavigation () {
    final BaseActivity context = getUiContext();
    return context != null ? context.navigation() : null;
  }

  public static NavigationController getNavigation (Context context) {
    return UI.getContext(context).navigation();
  }

  public static @Nullable HeaderView getHeaderView (Context context) {
    final NavigationController navigation = UI.getNavigation(context);
    return navigation != null ? navigation.getHeaderView() : null;
  }

  public static @Nullable DrawerController getDrawer (Context context) {
    final BaseActivity activity = getContext(context);
    return activity != null ? activity.getDrawer() : null;
  }

  public static boolean isNavigationAnimating () {
    NavigationController navigation = getNavigation();
    return navigation != null && navigation.isAnimating();
  }

  public static @Nullable HeaderView getHeaderView () {
    NavigationController navigation = getNavigation();
    return navigation != null ? navigation.getHeaderView() : null;
  }

  @Deprecated
  public static @Nullable ViewController<?> getCurrentStackItem () {
    NavigationController navigation = getNavigation();
    return navigation != null ? navigation.getStack().getCurrent() : null;
  }

  @Deprecated
  public static ViewController<?> getCurrentStackItem (Context context) {
    return UI.getContext(context).navigation().getCurrentStackItem();
  }

  public static final int NAVIGATION_BAR_COLOR = false && Device.NEED_LIGHT_NAVIGATION_COLOR ? 0xfff0f0f0 : 0xff000000;

  @SuppressWarnings("deprecation")
  public static void clearActivity (BaseActivity a) {
    a.requestWindowFeature(Window.FEATURE_NO_TITLE);
    Window w = a.getWindow();
    // TODO: rework to Window.setDecorFitsSystemWindows(boolean)
    w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      w.setBackgroundDrawableResource(R.drawable.transparent);
    } else {
      int visibility = 0;
      if (Config.USE_CUSTOM_NAVIGATION_COLOR) {
        w.setNavigationBarColor(Theme.backgroundColor());
        if (!Theme.isDark()) {
          // TODO: rework to WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
          visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
      } else {
        w.setNavigationBarColor(NAVIGATION_BAR_COLOR);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (Theme.needLightStatusBar()) {
          // TODO: rework to WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
          visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
      }
      if (visibility != 0) {
        // TODO: rework to WindowInsetsController
        w.getDecorView().setSystemUiVisibility(visibility);
      }
      RootDrawable d = new RootDrawable(a);
      w.setBackgroundDrawable(d);
      a.setRootDrawable(d);
      if (Config.USE_FULLSCREEN_NAVIGATION) {
        w.setStatusBarColor(0); // 0x4c000000
      } else {
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // TODO: rework to Window.setStatusBarColor(int)
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        w.setStatusBarColor(HeaderView.defaultStatusColor());
      }
      /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        WindowManager.LayoutParams params = w.getAttributes();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(params);
      }*/
    }
  }

  @SuppressWarnings("deprecation")
  public static void setFullscreenIfNeeded (View view) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION) {
      view.setFitsSystemWindows(true);
      // TODO: rework to WindowInsetsController
      view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
  }

  public static void setNewStatusBarColor (int color) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Config.USE_FULLSCREEN_NAVIGATION) {
      final BaseActivity context = getUiContext();
      if (context != null && context.getWindow() != null) {
        context.getWindow().setStatusBarColor(color);
      }
    }
  }

  public static void setStatusBarColor (int color) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !Config.USE_FULLSCREEN_NAVIGATION) {
      final BaseActivity context = getUiContext();
      if (context != null && context.getWindow() != null) {
        context.getWindow().setStatusBarColor(color);
      }
    }
  }

  public static int getStatusBarColor () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      final BaseActivity context = getUiContext();
      return context == null || context.getWindow() == null ? 0 : context.getWindow().getStatusBarColor();
    } else {
      return 0;
    }
  }

  public static Window getWindow () {
    final BaseActivity context = getUiContext();
    return context != null ? context.getWindow() : null;
  }

  public static int getOrientation () {
    return appContext.getResources().getConfiguration().orientation;
  }

  public static boolean isPortrait () {
    return appContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
  }

  public static boolean isLandscape () {
    return appContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public static void setPlayProgress (TGAudio audio, float progress, int seconds) {
    getAppHandler().setPlayProgress(audio, progress);
  }

  public static void setPlayChanged (TGAudio audio, boolean playing) {
    getAppHandler().setPlayChanged(audio, playing);
  }

  public static void setController (ViewController<?> controller) {
    getAppHandler().setController(controller);
  }

  public static void navigateBack () {
    getAppHandler().navigateBack();
  }

  public static void navigateTo (ViewController<?> controller) {
    getAppHandler().navigateTo(controller);
  }

  public static void navigateDelayed (ViewController<?> controller, long delay) {
    getAppHandler().navigateDelayed(controller, delay);
  }

  public static void execute (Runnable r) {
    if (inUiThread()) {
      r.run();
    } else {
      post(r);
    }
  }
  public static void post (Runnable r) {
    getAppHandler().post(r);
  }

  public static void post (Runnable r, long delay) {
    getAppHandler().postDelayed(r, delay);
  }

  public static void cancel (Runnable r) {
    getAppHandler().removeCallbacks(r);
  }

  public static void copyText (CharSequence text, @StringRes int toast) {
    getAppHandler().copyText(text, toast);
  }

  public static void openNumber (String number) {
    getAppHandler().openNumber(number);
  }

  public static void showKeyboardDelayed (View view) {
    getAppHandler().showKeyboardDelayed(view, true);
  }

  public static void hideKeyboardDelayed (View view) {
    getAppHandler().showKeyboardDelayed(view, false);
  }

  /*public static void openFile (String displayName, File file, String mimeType) {
    getAppHandler().openFile(displayName, file, mimeType);
  }*/

  public static void openFile (TdlibDelegate context, String displayName, File file, String mimeType, int views) {
    getAppHandler().openFile(context, displayName, file, mimeType, views);
  }

  public static void openUrl (String url) {
    getAppHandler().openLink(url);
  }

  @Deprecated
  public static void openCameraDelayed (BaseActivity context) {
    getAppHandler().openCamera(context, ACTIVITY_DELAY, false, false);
  }

  private static final long ACTIVITY_DELAY = 160l;

  public static void openGalleryDelayed (BaseActivity context, boolean sendAsFile) {
    getAppHandler().openGallery(context, ACTIVITY_DELAY, sendAsFile);
  }

  public static void setSoftInputMode (BaseActivity context, int inputMode) {
    if (context != null) {
      context.getWindow().setSoftInputMode(inputMode);
    }
  }

  // todo: move to other place?

  @SuppressWarnings("deprecation")
  private static String toLanguageCode (InputMethodSubtype ims) {
    if (ims != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        String languageTag = ims.getLanguageTag();
        if (!StringUtils.isEmpty(languageTag)) {
          return languageTag;
        }
      }
      String locale = ims.getLocale();
      if (!StringUtils.isEmpty(locale)) {
        Locale l = U.getDisplayLocaleOfSubtypeLocale(locale);
        if (l != null) {
          return LocaleUtils.toBcp47Language(l);
        }
      }
    }
    return null;
  }

  @Nullable
  public static String[] getInputLanguages () {
    final Set<String> inputLanguages = new LinkedHashSet<>();
    InputMethodManager imm = (InputMethodManager) UI.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      String inputLanguageCode = null;
      try {
        inputLanguageCode = toLanguageCode(imm.getCurrentInputMethodSubtype());
      } catch (Throwable ignored) { }
      if (StringUtils.isEmpty(inputLanguageCode)) {
        try {
          inputLanguageCode = toLanguageCode(imm.getLastInputMethodSubtype());
        } catch (Throwable ignored) { }
      }
      if (!StringUtils.isEmpty(inputLanguageCode)) {
        inputLanguages.add(inputLanguageCode);
      }

      /*if (Strings.isEmpty(inputLanguageCode)) {
        try {
          String id = android.provider.Settings.Secure.getString(
            UI.getAppContext().getContentResolver(),
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
          );
          if (!Strings.isEmpty(id)) {
            List<InputMethodInfo> list = imm.getInputMethodList();
            lookup:
            for (InputMethodInfo info : list) {
              if (id.equals(info.getId())) {
                List<InputMethodSubtype> subtypes = imm.getEnabledInputMethodSubtypeList(info, true);
                for (InputMethodSubtype subtype : subtypes) {
                  String languageCode = toLanguageCode(subtype);
                  if (!Strings.isEmpty(languageCode)) {
                    inputLanguageCode = languageCode;
                    break lookup;
                  }
                }
              }
            }
          }
        } catch (Throwable ignored) { }
      }
      if (Strings.isEmpty(inputLanguageCode) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
          LocaleList localeList = ((InputView) callback).getImeHintLocales();
          if (localeList != null) {
            for (int i = 0; i < localeList.size(); i++) {
              inputLanguageCode = U.toBcp47Language(localeList.get(i));
              if (!Strings.isEmpty(inputLanguageCode))
                break;
            }
          }
        } catch (Throwable ignored) { }
      }*/
    }
    if (inputLanguages.isEmpty()) {
      try {
        Configuration configuration = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          LocaleList locales = configuration.getLocales();
          for (int i = 0; i < locales.size(); i++) {
            String code = LocaleUtils.toBcp47Language(locales.get(i));
            if (!StringUtils.isEmpty(code))
              inputLanguages.add(code);
          }
        } else {
          String code = LocaleUtils.toBcp47Language(getSystemLocale());
          if (!StringUtils.isEmpty(code)) {
            inputLanguages.add(code);
          }
        }
      } catch (Throwable ignored) { }
    }
    return inputLanguages.isEmpty() ? null : inputLanguages.toArray(new String[0]);
  }
}
