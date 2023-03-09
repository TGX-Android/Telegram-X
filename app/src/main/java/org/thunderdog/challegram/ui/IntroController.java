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
 * File created on 16/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;
import androidx.viewpager.widget.PagerAdapter;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ConnectionListener;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibOptionListener;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerEntry;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ProgressComponent;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableBool;

@SuppressWarnings("JniMissingFunction")
public class IntroController extends ViewController<Void> implements GLSurfaceView.EGLConfigChooser, GLSurfaceView.Renderer, ViewPager.OnPageChangeListener, Runnable, View.OnClickListener, View.OnLongClickListener, TdlibOptionListener, ConnectionListener, GlobalAccountListener {
  public IntroController (Context context) {
    super(context, null);
  }

  @Override
  public int getId () {
    return R.id.controller_intro;
  }

  @Override
  public boolean isUnauthorized () {
    return true;
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  @Override
  public boolean preventRootInteractions () {
    return true;
  }

  @Override
  public boolean passBackPressToActivity (boolean fromTop) {
    return true;
  }

  @Override
  public boolean allowLayerTypeChanges () {
    return false; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && USE_TEXTURE_VIEW;
  }

  @Override
  protected int getPopupRestoreColor () {
    return Theme.fillingColor();
  }

  public static final boolean USE_POPUP_MODE = true;

  private static final boolean INFINITE_MODE = false;
  private static final boolean USE_TEXTURE_VIEW = false;
  private View surfaceView;
  private TextLayout textLayout;

  private boolean hidden;

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (surfaceView != null && surfaceView.getVisibility() != View.VISIBLE) {
      hidden = false;
      UI.post(() -> {
        if (!hidden) {
          surfaceView.setVisibility(View.VISIBLE);
          requestRender();
        }
      }, 50);
    }
  }

  @Override
  public void onCleanAfterHide () {
    super.onCleanAfterHide();
    if (surfaceView != null) {
      surfaceView.setVisibility(View.GONE);
      hidden = true;
    }
  }

  /*@Override
  public boolean needsTempUpdates () {
    return false;
  }*/

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    destroySpecialTextures();
    destroyTexts(true);
    updateTexts(true);
    textLayout.invalidate();
    __setIntroBgColor(Theme.fillingColor());
    // TODO reload special textures?
    requestRender();
  }

  private static boolean introAttempted;

  private static void setIntroAttempted (boolean isAttempted) {
    if (introAttempted != isAttempted) {
      introAttempted = isAttempted;
      Settings.instance().setIntroAttempted(isAttempted);
    }
  }

  public static boolean isIntroAttemptedButFailed () {
    if (!BuildConfig.DEBUG && Settings.instance().isIntroAttempted()) {
      Log.w("Not showing intro controller, because it has failed once");
      return true;
    }
    return false;
  }

  // Common stuff

  private static final long BAD_CONNECTION_TIMEOUT = 4000;
  private static final long HELP_TIMEOUT = 12000;

  private @Nullable PopupLayout showBadConnectionPopup (@NonNull LanguageRequest request) {
    IntList ids = new IntList(2);
    IntList icons = new IntList(2);
    StringList strings = new StringList(2);

    TdApi.LanguagePackInfo languagePackInfo = request.language.packInfo;

    ids.append(R.id.btn_proxy);
    strings.append(Lang.getString(languagePackInfo, Settings.instance().hasProxyConfiguration() ? R.string.ProxySettings : R.string.ProxyAdd));
    icons.append(R.drawable.baseline_security_24);

    ids.append(R.id.btn_help);
    strings.append(Lang.getString(languagePackInfo, R.string.Help));
    icons.append(R.drawable.baseline_help_24);

    long timeout = getTdlib().calculateConnectionTimeoutMs(HELP_TIMEOUT);

    CharSequence info = Strings.buildMarkdown(new TdlibContext(context, getTdlib()), Lang.getString(languagePackInfo, R.string.LoginErrorLongConnecting), null);
    PopupLayout popupLayout = showOptions(info, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_done: {
          break;
        }
        case R.id.btn_help: {
          TdApi.NetworkType networkType = getTdlib().networkType();
          String networkTypeStr;
          if (networkType != null) {
            switch (networkType.getConstructor()) {
              case TdApi.NetworkTypeMobile.CONSTRUCTOR:
                networkTypeStr = "Mobile";
                break;
              case TdApi.NetworkTypeMobileRoaming.CONSTRUCTOR:
                networkTypeStr = "Roaming";
                break;
              case TdApi.NetworkTypeOther.CONSTRUCTOR:
                networkTypeStr = "Other";
                break;
              case TdApi.NetworkTypeWiFi.CONSTRUCTOR:
                networkTypeStr = "Wifi";
                break;
              case TdApi.NetworkTypeNone.CONSTRUCTOR:
              default:
                networkTypeStr = "None";
                break;
            }
          } else {
            networkTypeStr = "Unknown";
          }
          if (getTdlib().isConnected()) {
            networkTypeStr = networkTypeStr + ", " + Lang.getString(languagePackInfo, R.string.Connected);
          }
          String text = Lang.getString(languagePackInfo,
            R.string.email_LoginTooLong_text,

            BuildConfig.VERSION_NAME,
            languagePackInfo.id,
            Lang.getDuration((int) (getTdlib().timeSinceFirstConnectionAttemptMs() / 1000l)) + " (" + networkTypeStr + ")",
            TdlibManager.getSystemLanguageCode(),
            TdlibManager.getSystemVersion()
          );
          Intents.sendEmail(Lang.getStringSecure(R.string.email_SmsHelp), Lang.getString(languagePackInfo, R.string.email_LoginTooLong_subject), text, Lang.getString(languagePackInfo, R.string.HelpEmailError));
          break;
        }
        case R.id.btn_proxy: {
          getTdlib().ui().openProxySettings(new TdlibContext(context, getTdlib()), true);
          break;
        }
      }
      return true;
    });
    if (popupLayout != null) {
      if (timeout > 0) {
        View view = popupLayout.getBoundView().findViewById(R.id.btn_help);
        if (view != null) {
          view.setVisibility(View.GONE);
          getTdlib().ui().postDelayed(() -> {
            if (!isDestroyed()) {
              view.setVisibility(View.VISIBLE);
            }
          }, timeout);
        }
      }
      popupLayout.setDisableCancelOnTouchDown(true);
      popupLayout.setBackListener((fromTop) -> {
        if (loginRequest == request) {
          cancelLoginRequest();
        }
        return false;
      });
    }
    return popupLayout;
  }

  private boolean needMissingNetworkPopup () {
    return !isDestroyed() && getTdlib().isWaitingForNetwork();
  }

  private @Nullable PopupLayout showMissingNetworkPopup (@NonNull LanguageRequest request) {
    TdApi.LanguagePackInfo languagePackInfo = request.language.packInfo;

    boolean airplane = U.isAirplaneModeOn();
    int size = 1; // airplane ? 2 : 1;
    IntList ids = new IntList(size);
    IntList icons = new IntList(size);
    StringList strings = new StringList(size);
    CharSequence msg = Strings.buildMarkdown(new TdlibContext(context, getTdlib()), Lang.getString(languagePackInfo, airplane ? R.string.LoginErrorAirplane : R.string.LoginErrorOffline), null);
    ids.append(R.id.btn_settings);
    strings.append(Lang.getString(languagePackInfo, R.string.Settings));
    icons.append(R.drawable.baseline_settings_24);
    PopupLayout popupLayout = showOptions(msg, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_settings: {
          if (U.isAirplaneModeOn()) {
            Intents.openAirplaneSettings();
          } else {
            Intents.openWirelessSettings();
          }
          break;
        }
      }
      return true;
    });
    if (popupLayout != null) {
      popupLayout.setDisableCancelOnTouchDown(true);
      popupLayout.setBackListener((fromTop) -> {
        if (loginRequest == request) {
          cancelLoginRequest();
        }
        return false;
      });
    }
    return popupLayout;
  }

  @Override
  public void onConnectionStateChanged (int newState, int oldState) {
    if (newState != oldState && (oldState == Tdlib.STATE_WAITING || newState == Tdlib.STATE_WAITING)) {
      getTdlib().ui().post(() -> {
        if (!isDestroyed()) {
          performRequest(loginRequest);
        }
      });
    }
  }

  // Language Stuff

  private static class LanguageState {
    public static final int NOT_LOADED = 0;
    public static final int LOADED_INTRO = 1;
    public static final int LOADED = 2;

    public final TdApi.LanguagePackInfo packInfo;
    private int loadState;

    public String getId () {
      return packInfo.id;
    }

    private Tdlib isLoading, isLoadingIntro;
    private List<RunnableBool> onLoad;
    private List<Runnable> onIntroLoad;

    public LanguageState (@NonNull TdApi.LanguagePackInfo packInfo, int loadState) {
      this.packInfo = packInfo;
      this.loadState = loadState;
    }

    /**
     * @return true if all strings have been successfully loaded
     */
    public boolean isFullyLoaded () {
      return loadState == LOADED;
    }

    /**
     * @return true if intro strings are available
     */
    public boolean isIntroLoaded () {
      return loadState == LOADED || loadState == LOADED_INTRO;
    }

    /**
     * @return true if language codes are equal
     */
    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof LanguageState && ((LanguageState) obj).getId().equals(this.getId());
    }

    /**
     * Loads strings needed for intro.
     *
     * Does nothing if full translation has requested.
     * @param tdlib Tdlib instance
     */
    public void loadIntro (Tdlib tdlib) {
      if (isLoading != tdlib && isLoadingIntro != tdlib && !isIntroLoaded()) {
        isLoadingIntro = tdlib;
        String[] keys = Lang.getKeys(getStringResources());
        tdlib.getStrings(packInfo, keys, strings -> {
          boolean ok = strings != null; // && !strings.isEmpty();
          if (!ok) {
            Log.e("Failed to load intro strings");
          }
          tdlib.ui().post(() -> {
            if (this.isLoadingIntro == tdlib || ok) {
              if (this.isLoadingIntro == tdlib)
                this.isLoadingIntro = null;
              if (ok)
                onIntroLoaded();
            }
          });
        });
      }
    }

    /**
     * Loads localization fully.
     *
     * @param tdlib Tdlib instance
     */
    public void load (Tdlib tdlib) {
      if (isLoading != tdlib && !isFullyLoaded()) {
        isLoading = tdlib;
        tdlib.syncLanguage(packInfo, ok -> {
          if (!ok)
            Log.e("Failed to load language from intro");
          tdlib.ui().post(() -> {
            if (this.isLoading == tdlib || ok) {
              if (this.isLoading == tdlib)
                this.isLoading = null;
              if (ok)
                onIntroLoaded();
              onFullLoad(ok);
            }
          });
        });
      }
    }

    /**
     * @param runnable Action to perform when language request will be completed.
     */
    public void addOnLoadListener (@NonNull RunnableBool runnable) {
      if (isFullyLoaded()) {
        runnable.runWithBool(true);
      } else {
        if (this.onLoad == null)
          this.onLoad = new ArrayList<>();
        this.onLoad.add(runnable);
      }
    }

    /**
     * @param runnable Action to perform when intro strings will be downloaded
     */
    public void addOnIntroLoadListener (@NonNull Runnable runnable) {
      if (isIntroLoaded()) {
        runnable.run();
      } else {
        if (this.onIntroLoad == null)
          this.onIntroLoad = new ArrayList<>();
        if (!this.onIntroLoad.contains(runnable)) {
          this.onIntroLoad.add(runnable);
        }
      }
    }

    /**
     * Called when intro gets successfully loaded
     */
    private void onIntroLoaded () {
      if (loadState == NOT_LOADED)
        loadState = LOADED_INTRO;
      List<Runnable> onIntroLoad = this.onIntroLoad;
      this.onIntroLoad = null;
      if (onIntroLoad != null) {
        for (Runnable runnable : onIntroLoad) {
          runnable.run();
        }
      }
    }

    /**
     * Called when full language pack request completed.
     *
     * @param success Whether request has succeeded or not
     */
    private void onFullLoad (boolean success) {
      if (success && loadState != LOADED)
        loadState = LOADED;
      List<RunnableBool> onLoad = this.onLoad;
      this.onLoad = null;
      if (onLoad != null) {
        for (RunnableBool runnable : onLoad) {
          runnable.runWithBool(success);
        }
      }
    }
  }

  private static class LanguageRequest {
    public final LanguageState language;
    public boolean languageRequested;

    private boolean isCancelled;
    private @Nullable PopupLayout popupLayout;
    private @Nullable CancellableRunnable connectionWarning;

    public boolean needRetryOnFocus;

    public LanguageRequest (LanguageState language) {
      this.language = language;
    }

    /**
     * Alias to {@link #setPopup(PopupLayout null, int 0)}
     */
    public void clearPopup () {
      setPopup(null, 0);
    }

    /**
     * Sets current popup.
     * Hides previously shown one, if any.
     *
     * @param popupLayout Currently visible popup
     */
    public void setPopup (@Nullable PopupLayout popupLayout, int windowType) {
      if (this.popupLayout != null && !this.popupLayout.isWindowHidden()) {
        this.popupLayout.hideWindow(UI.getContext(this.popupLayout.getContext()).getActivityState() == UI.STATE_RESUMED);
      }
      this.popupLayout = popupLayout;
      if (popupLayout != null) {
        popupLayout.getBoundView().setTag(new PopupTemporaryData(this, windowType));
      }
    }

    /**
     * @return Data containing information about currently visible pop-up.
     */
    private PopupTemporaryData getPopupTemporaryData () {
      return popupLayout != null && !popupLayout.isWindowHidden() && popupLayout.getBoundView() != null && popupLayout.getBoundView().getTag() instanceof PopupTemporaryData ? (PopupTemporaryData) popupLayout.getBoundView().getTag() : null;
    }

    /**
     * @param windowType Popup type identifier
     * @return true if such window is already shown
     */
    public boolean isDisplayingPopup (int windowType) {
      PopupTemporaryData data = getPopupTemporaryData();
      return data != null && data.windowType == windowType;
    }

    /**
     * @param runnable connection warning that's about to display
     */
    public void setConnectionWarning (CancellableRunnable runnable) {
      if (this.connectionWarning != null)
        this.connectionWarning.cancel();
      this.connectionWarning = runnable;
    }

    /**
     * Cancels language request, hides any shown popups, etc.
     */
    public void cancel () {
      if (!isCancelled) {
        this.isCancelled = true;
        clearPopup();
        setConnectionWarning(null);
        needRetryOnFocus = false;
      }
    }
  }

  private LanguageRequest loginRequest;
  private boolean ignoreLanguagePackEvents;

  /**
   * Cancels pending login request
   */
  private void cancelLoginRequest () {
    if (this.loginRequest != null) {
      this.loginRequest.cancel();
      this.loginRequest = null;
      checkInProgress();
    }
  }

  /**
   * Makes a new login request.
   *
   * @param useFallbackLanguage Determines whether to use {@link #fallbackLanguage} or {@link #displayLanguage}
   */
  private void requestLogin (boolean useFallbackLanguage) {
    LanguageState language = useFallbackLanguage ? fallbackLanguage : displayLanguage;
    if (this.loginRequest != null && this.loginRequest.language.equals(language))
      return;
    cancelLoginRequest();
    performRequest(new LanguageRequest(language));
  }

  private static class PopupTemporaryData {
    public final LanguageRequest request;
    public final int windowType;

    public PopupTemporaryData (LanguageRequest request, int windowType) {
      this.request = request;
      this.windowType = windowType;
    }
  }

  private static final int WINDOW_TYPE_NETWORK_MISSING = 0;
  private static final int WINDOW_TYPE_NETWORK_BAD = 1;

  /**
   * Called when:
   * - User presses "Start Messaging" or "Continue in English" buttons
   * - Network enabled/disabled
   * - Language has completed loading
   * - Debug mode switched
   *
   * @param request Request to perform
   */
  private void performRequest (LanguageRequest request) {
    if (request == null || request.isCancelled)
      return;

    this.loginRequest = request;

    // Step #0: If this is not the first call, hide shown popup & cancel bad connection pop-up
    request.setConnectionWarning(null);
    request.needRetryOnFocus = false;

    // Step #1: Check if user has Internet connection enabled
    boolean needMissingNetworkPopup = needMissingNetworkPopup();
    if (needMissingNetworkPopup) {
      if (request.isDisplayingPopup(WINDOW_TYPE_NETWORK_MISSING)) {
        return;
      }
      PopupLayout missingNetworkPopup = showMissingNetworkPopup(request);
      if (missingNetworkPopup != null) {
        checkInProgress();
        request.setPopup(missingNetworkPopup, WINDOW_TYPE_NETWORK_MISSING);
      } else {
        request.needRetryOnFocus = true;
        request.clearPopup(); // Hide irrelevant popup
      }
      return;
    }

    // Step #2: Check if language is downloaded
    if (!request.language.isFullyLoaded()) {
      if (!request.isDisplayingPopup(WINDOW_TYPE_NETWORK_BAD)) {
        request.clearPopup(); // Hiding irrelevant popup
      }
      checkInProgress();
      if (!request.languageRequested) {
        request.languageRequested = true;
        request.language.addOnLoadListener((success) -> {
          if (success) {
            performRequest(request);
          } else {
            // This should never happen
            Log.e("Unexpected server error. Proceeding in English.");
            requestLogin(true);
          }
        });
      }
      request.language.load(getTdlib());
      CancellableRunnable runnable = new CancellableRunnable() {
        @Override
        public void act () {
          if (!request.isCancelled && !isDestroyed()) {
            if (request.isDisplayingPopup(WINDOW_TYPE_NETWORK_BAD))
              return;
            PopupLayout popupLayout = showBadConnectionPopup(request);
            if (popupLayout != null) {
              request.setPopup(popupLayout, WINDOW_TYPE_NETWORK_BAD);
            } else {
              request.needRetryOnFocus = true;
            }
          }
        }
      };
      request.setConnectionWarning(runnable);
      getTdlib().ui().postDelayed(runnable, getTdlib().calculateConnectionTimeoutMs(BAD_CONNECTION_TIMEOUT));
      return;
    }

    request.clearPopup(); // Hiding no longer relevant popup

    // Step #3: Apply language code
    this.ignoreLanguagePackEvents = true;
    Lang.changeLanguage(request.language.packInfo);

    // Step #4: Hide progress
    this.loginRequest = null;
    checkInProgress();

    // Step #5: Open sign-in screen
    navigateTo(new PhoneController(context, getTdlib()));
  }

  private void initLanguages () {
    TdApi.LanguagePackInfo localLanguage = Lang.getBuiltinSuggestedLanguage();
    TdApi.LanguagePackInfo fallbackLanguage = Lang.getBuiltinLanguage();
    this.fallbackLanguage = new LanguageState(fallbackLanguage, LanguageState.LOADED);
    if (localLanguage.id.equals(fallbackLanguage.id))
      this.localLanguage = this.fallbackLanguage;
    else
      this.localLanguage = new LanguageState(localLanguage, LanguageState.LOADED_INTRO);
    this.displayLanguage = this.localLanguage;
    setCloudLanguageCode(getTdlib().suggestedLanguagePackId(), getTdlib().suggestedLanguagePackInfo());
  }

  private void setCloudLanguageCode (String languagePackId, @Nullable TdApi.LanguagePackInfo languagePackInfo) {
    if (!StringUtils.isEmpty(languagePackId)) {
      if (languagePackId.equals(fallbackLanguage.getId()))
        this.cloudLanguage = fallbackLanguage;
      else if (languagePackId.equals(localLanguage.getId()))
        this.cloudLanguage = localLanguage;
      else if (languagePackInfo != null)
        this.cloudLanguage = new LanguageState(languagePackInfo, LanguageState.NOT_LOADED);
      else
        this.cloudLanguage = null;
    } else {
      this.cloudLanguage = null;
    }
  }

  /**
   * Called when {@link #cloudLanguage} or {@link #localLanguage} got changed
   */
  private void checkDisplayLanguage () {
    LanguageState desiredLanguage;
    if (!fallbackLanguage.equals(localLanguage)) {
      desiredLanguage = localLanguage;
    } else if (cloudLanguage != null) {
      if (cloudLanguage.isIntroLoaded()) {
        desiredLanguage = cloudLanguage;
      } else {
        cloudLanguage.addOnIntroLoadListener(this::checkDisplayLanguage);
        cloudLanguage.loadIntro(getTdlib());
        desiredLanguage = fallbackLanguage;
      }
    } else {
      desiredLanguage = fallbackLanguage;
    }
    if (!this.displayLanguage.equals(desiredLanguage)) {
      this.displayLanguage = desiredLanguage;
      onLanguagePackEvent(Lang.EVENT_PACK_CHANGED, 0);

      continueButton.setText(Lang.getString(fallbackLanguage.packInfo, R.string.language_continueInLanguage));
      continueAnimator.setValue(!fallbackLanguage.equals(displayLanguage), isFocused());
    }
    if (!displayLanguage.isFullyLoaded()) {
      displayLanguage.load(getTdlib());
    }
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    TdApi.LanguagePackInfo localLanguageInfo = Lang.getBuiltinSuggestedLanguage();
    TdApi.LanguagePackInfo fallbackLanguageInfo = Lang.getBuiltinLanguage();

    if (!fallbackLanguageInfo.id.equals(this.fallbackLanguage.getId())) {
      // Built-in language has changed
      this.fallbackLanguage = new LanguageState(fallbackLanguageInfo, LanguageState.LOADED);
    }

    if (!localLanguageInfo.id.equals(this.localLanguage.getId())) {
      // Suggested language has changed
      if (localLanguageInfo.id.equals(this.fallbackLanguage.getId())) {
        this.localLanguage = this.fallbackLanguage;
      } else {
        this.localLanguage = new LanguageState(localLanguageInfo, LanguageState.LOADED_INTRO);
      }
    }

    checkDisplayLanguage();
  }

  @Override
  public void onSuggestedLanguagePackChanged (String languagePackId, TdApi.LanguagePackInfo languagePackInfo) {
    getTdlib().ui().post(() -> {
      if (!isDestroyed()) {
        setCloudLanguageCode(languagePackId, languagePackInfo);
        checkDisplayLanguage();
      }
    });
  }

  /**
   * Language code in which intro is currently displayed, and which should be applied when "Start Messaging" is pressed.
   */
  private @NonNull LanguageState displayLanguage;

  /**
   * Language code in which "Continue in English" is displayed. This language code is available locally & does not require cloud download.
   */
  private @NonNull LanguageState fallbackLanguage;

  /**
   *
   */
  private @NonNull LanguageState localLanguage;

  /**
   * Language code, which is suggested by server. This get applied to {@link #displayLanguage}, when:
   * {@link #cloudLanguage} != {@link #fallbackLanguage}
   * and
   * {@link #displayLanguage} == {@link #fallbackLanguage}
   * and
   * Intro is available
   */
  private @Nullable LanguageState cloudLanguage;

  // View

  private void checkInProgress () {
    if (progressAnimator != null && !isDestroyed()) {
      progressAnimator.setValue(loginRequest != null && !loginRequest.isCancelled, isFocused());
    }
  }

  @Override
  protected View onCreateView (Context context) {
    setIntroAttempted(true);

    initLanguages();

    FrameLayoutFix contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_filling, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // UI.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
    params.topMargin = HeaderView.getTopOffset();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && USE_TEXTURE_VIEW) {
      org.thunderdog.challegram.texture.CustomTextureView textureView = new org.thunderdog.challegram.texture.CustomTextureView(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          int paddingBottom = (Screen.dp(48f) - Screen.dp(16f) * 2) * 2;
          int width, height;

          width = MeasureSpec.getSize(widthMeasureSpec);
          height = MeasureSpec.getSize(heightMeasureSpec) - HeaderView.getTopOffset() - paddingBottom;

          if (width > height) {
            width = height + paddingBottom;
          } else {
            height = width;
          }

          super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
          requestRender();
        }
      };
      textureView.setListener(new org.thunderdog.challegram.texture.CustomTextureView.Listener() {
        @Override
        public void onTextureCreated (int width, int height) {
          initSurface(null);
          resizeSurface(width, height);
        }

        @Override
        public void onTextureSizeChanged (int width, int height) {
          resizeSurface(width, height);
        }

        @Override
        public void onDrawFrame () {
          drawFrame();
        }
      });
      contentView.addView(textureView);
      contentView.setLayerType(View.LAYER_TYPE_HARDWARE, Views.getLayerPaint());
      this.surfaceView = textureView;
    } else {
      GLSurfaceView surfaceView;
      surfaceView = new GLSurfaceView(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          // FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();

          // int paddingBottom = (Screen.dp(48f) - Screen.dp(16f) * 2) * 2;
          int width, height;

          width = MeasureSpec.getSize(widthMeasureSpec);
          height = MeasureSpec.getSize(heightMeasureSpec);

          if (width >= height) {
            width = width / 2;
            // height -= HeaderView.getTopOffset();// - paddingBottom;
            // params.topMargin = HeaderView.getTopOffset() + paddingBottom;
          } else {
            height = Math.min(width, height / 2);
            // params.topMargin = HeaderView.getTopOffset();
          }

          super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
          requestRender();
        }
      };
      surfaceView.setEGLContextClientVersion(2);
      // surfaceView.setEGLConfigChooser(5, 6, 5, 0, 16, 1);
      // surfaceView.setEGLConfigChooser(this);
      surfaceView.setRenderer(this);
      surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
      surfaceView.setLayoutParams(params);
      contentView.addView(surfaceView);
      this.surfaceView = surfaceView;
    }

    /*baseTitle = genTitle();
    previewTitle = genTitle();

    baseDesc = genDesc();
    previewDesc = genDesc();*/

    ViewPager pager = new ViewPager(context);
    pager.setAdapter(new IntroPagerAdapter(context));
    pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
    pager.addOnPageChangeListener(this);
    pager.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(pager);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
    FrameLayoutFix textWrap = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int width, height;

        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);

        if (width >= height) {
          width = width / 2;
          // height -= HeaderView.getTopOffset();// - paddingBottom;
          textLayout.setUseCenter(true);
          // params.topMargin = HeaderView.getTopOffset() + paddingBottom;
        } else {
          height = height - Math.min(width, height / 2);
          textLayout.setUseCenter(false);
          // params.topMargin = HeaderView.getTopOffset();
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
      }
    };
    textWrap.setLayoutParams(params);

    textLayout = new TextLayout(context);
    textLayout.initWithController(this);
    textLayout.prepare(getTitle(0), getTitle(1), getTitle(2), getTitle(3), getTitle(4), getTitle(5));
    textLayout.prepare(Screen.currentWidth(), getDesc(0), getDesc(1), getDesc(2), getDesc(3), getDesc(4), getDesc(5));
    textLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    textWrap.addView(textLayout);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f), Gravity.BOTTOM);
    params.bottomMargin = params.leftMargin = params.rightMargin = Screen.dp(16f);
    button = new NoScrollTextView(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getLayout() != null && getLayout().getLineCount() > 0) {
          int size = Screen.dp(24f);
          int cx = getMeasuredWidth() / 2 + (int) getLayout().getLineWidth(0) / 2 + Screen.dp(16f);
          int cy = getMeasuredHeight() / 2;
          progressComponent.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        }
      }

      @Override
      protected void onDraw (Canvas c) {
        super.onDraw(c);
        progressComponent.draw(c);
      }
    };
    progressComponent = new ProgressComponent(UI.getContext(context), Screen.dp(3.5f));
    progressComponent.setAlpha(0f);
    progressComponent.setViewProvider(new SingleViewProvider(button));
    button.setId(R.id.btn_done);
    button.setPadding(0, 0, 0, Screen.dp(1f));
    button.setTypeface(Fonts.getRobotoMedium());
    button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    button.setGravity(Gravity.CENTER);
    button.setText(getString(R.string.StartMessaging));
    button.setOnClickListener(this);
    button.setOnLongClickListener(this);
    button.setLayoutParams(params);
    button.setTextColor(Theme.getColor(R.id.theme_color_textNeutral));
    addThemeTextColorListener(button, R.id.theme_color_textNeutral);
    RippleSupport.setSimpleWhiteBackground(button);
    textWrap.addView(button);

    progressAnimator = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        progressComponent.setAlpha(factor);
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    params.bottomMargin = Screen.dp(16f);
    continueButton = new NoScrollTextView(context);
    continueButton.setId(R.id.btn_cancel);
    continueButton.setTypeface(Fonts.getRobotoRegular());
    continueButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    continueButton.setGravity(Gravity.CENTER);
    continueButton.setLayoutParams(params);
    continueButton.setOnClickListener(this);
    continueButton.setPadding(Screen.dp(16f), 0, Screen.dp(16f), Screen.dp(1f));
    continueButton.setTextColor(Theme.getColor(R.id.theme_color_textNeutral));
    continueButton.setTranslationY(Screen.dp(48f) + Screen.dp(16f));
    addThemeTextColorListener(continueButton, R.id.theme_color_textNeutral);
    textWrap.addView(continueButton);

    continueAnimator = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        float offset = (Screen.dp(48f) + Screen.dp(16f)) * (1f - factor);
        continueButton.setTranslationY(offset);
        button.setTranslationY(-Screen.dp(48f) * factor);
        textLayout.setYOffset(factor);
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    contentView.addView(textWrap);

    getTdlib().listeners().addOptionsListener(this);
    getTdlib().listeners().subscribeToConnectivityUpdates(this);
    TdlibManager.instance().global().addAccountListener(this);

    if (!fallbackLanguage.equals(displayLanguage)) {
      continueButton.setText(Lang.getString(fallbackLanguage.packInfo, R.string.language_continueInLanguage));
      continueAnimator.setValue(true, false);
    }
    checkDisplayLanguage();

    return contentView;
  }

  private void removeListeners () {
    getTdlib().listeners().removeOptionListener(this);
    getTdlib().listeners().unsubscribeFromConnectivityUpdates(this);
    TdlibManager.instance().global().removeAccountListener(this);
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_done:
        requestLogin(false);
        break;
      case R.id.btn_cancel:
        requestLogin(true);
        break;
    }
  }

  private TextView button, continueButton;
  private ProgressComponent progressComponent;
  private BoolAnimator progressAnimator, continueAnimator;

  public String getString (int resId) {
    return Lang.getString(displayLanguage.packInfo, resId);
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    if (this.ignoreLanguagePackEvents) {
      return;
    }
    super.onLanguagePackEvent(event, arg1);
    switch (event) {
      case Lang.EVENT_PACK_CHANGED:
        if (button != null)
          button.setText(getString(R.string.StartMessaging));
        if (textLayout != null)
          updateAllTexts();
        break;
      case Lang.EVENT_DIRECTION_CHANGED:
        break;
      case Lang.EVENT_STRING_CHANGED:
        if (button != null && arg1 == R.string.StartMessaging)
          button.setText(getString(R.string.StartMessaging));
        if (textLayout != null && belongsToIntro(arg1))
          updateAllTexts();
        break;
      case Lang.EVENT_DATE_FORMAT_CHANGED:
        // Nothing to change
        break;
    }
  }

  private Tdlib getTdlib () {
    return context.currentTdlib();
  }

  @Override
  public boolean onLongClick (View v) {
    IntList ids = new IntList(2);
    StringList strings = new StringList(2);
    IntList icons = new IntList(2);

    ids.append(R.id.btn_proxy);
    icons.append(R.drawable.baseline_security_24);
    strings.append(getString(Settings.instance().hasProxyConfiguration() ? R.string.ProxySettings : R.string.ProxyAdd));

    ids.append(R.id.btn_log_files);
    icons.append(R.drawable.baseline_bug_report_24);
    strings.append("Log Settings");
    if (Config.ALLOW_DEBUG_DC) {
      ids.append(R.id.btn_tdlib_debugDatacenter);
      icons.append(R.drawable.baseline_build_24);
      strings.append("Proceed in " + (getTdlib().account().isDebug() ? "production" : "debug") + " Telegram environment");
    }
    if (BuildConfig.DEBUG && UI.TEST_MODE != UI.TEST_MODE_AUTO) {
      ids.append(R.id.btn_test);
      icons.append(R.drawable.baseline_android_24);
      strings.append("Run automatic setup");
    }

    showOptions(null, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      switch (id) {
        case R.id.btn_tdlib_debugDatacenter: {
          if (Config.ALLOW_DEBUG_DC) {
            proceedInDebugMode(!getTdlib().account().isDebug());
          }
          break;
        }
        case R.id.btn_log_files: {
          navigateTo(new SettingsBugController(context, getTdlib()));
          break;
        }
        case R.id.btn_proxy: {
          getTdlib().ui().openProxySettings(new TdlibContext(context, getTdlib()), true);
          break;
        }
        case R.id.btn_test: {
          if (!UI.inTestMode()) {
            UI.TEST_MODE = UI.TEST_MODE_USER;
            proceedInDebugMode(true);
          }
          break;
        }
      }
      return true;
    });
    return true;
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    if (surfaceView != null) {
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH && USE_TEXTURE_VIEW) {
        ((org.thunderdog.challegram.texture.CustomTextureView) surfaceView).onPause();
      } else {
        ((GLSurfaceView) surfaceView).onPause();
      }
    }
    stopSchedule();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (surfaceView != null) {
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH && USE_TEXTURE_VIEW) {
        ((org.thunderdog.challegram.texture.CustomTextureView) surfaceView).onResume();
      } else {
        ((GLSurfaceView) surfaceView).onResume();
      }
    }
    if (INFINITE_MODE) {
      startSchedule();
    }
  }

  private void proceedInDebugMode (boolean isDebug) {
    Tdlib tdlib = getTdlib();
    if (tdlib.account().isDebug() != isDebug) {
      ((MainActivity) context).forceSwitchToDebug(isDebug);
    }
    requestLogin(false);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (INFINITE_MODE) {
      startSchedule();
    }
    if (UI.inTestMode()) {
      proceedInDebugMode(true);
    } else if (loginRequest != null && loginRequest.needRetryOnFocus) {
      performRequest(loginRequest);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    cancelLoginRequest();
    stopSchedule();
    destroyTextures();
    destroyTexts(false);
    removeListeners();
    // UI.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  }

  private static final long UPDATE_SCHEDULE_DELAY = 17l;
  private boolean scheduled;
  private CancellableRunnable scheduledStop;

  private void startSchedule () {
    cancelStopScheduleDelayed();
    if (!scheduled) {
      scheduled = true;
      UI.post(this, UPDATE_SCHEDULE_DELAY);
    }
  }

  private void cancelStopScheduleDelayed () {
    if (scheduledStop != null) {
      scheduledStop.cancel();
      scheduledStop = null;
    }
  }

  private void stopScheduleDelayed () {
    cancelStopScheduleDelayed();
    scheduledStop = new CancellableRunnable() {
      @Override
      public void act () {
        stopSchedule();
      }
    };
    scheduledStop.removeOnCancel(UI.getAppHandler());
    long delay;
    switch (lastPosition) {
      case 0: delay = 0; break;
      case 1: delay = 3000; break;
      case 2: delay = 4000; break;
      case 4: delay = 4000; break;
      case 5: delay = 1000; break;
      case 6: delay = 6000; break;
      default: delay = 6000; break;
    }
    UI.post(scheduledStop, delay + 8000);
  }

  private void stopSchedule () {
    cancelStopScheduleDelayed();
    if (scheduled) {
      scheduled = false;
      UI.removePendingRunnable(this);
    }
  }

  @Override
  public void run () {
    requestRender();
    if (scheduled) {
      UI.post(this, UPDATE_SCHEDULE_DELAY);
    }
  }

  /*private TextView baseTitle, previewTitle;
  private TextView baseDesc, previewDesc;*/

  private int basePos = -1, previewPos = -1;

  private static int[] getStringResources () {
    return new int[] {
      R.string.StartMessaging,

      R.string.Page1Title,
      R.string.Page1Message,
      R.string.Page2Title,
      R.string.Page2Message,
      R.string.Page3Title,
      R.string.Page3Message,
      R.string.Page4Title,
      R.string.Page4Message,
      R.string.Page5Title,
      R.string.Page5Message,
      R.string.Page6Title,
      R.string.Page6Message,
    };
  }

  private static boolean belongsToIntro (@StringRes int res) {
    switch (res) {
      case R.string.Page1Title:
      case R.string.Page1Message:
      case R.string.Page2Title:
      case R.string.Page2Message:
      case R.string.Page3Title:
      case R.string.Page3Message:
      case R.string.Page4Title:
      case R.string.Page4Message:
      case R.string.Page5Title:
      case R.string.Page5Message:
      case R.string.Page6Title:
      case R.string.Page6Message:
        return true;
    }
    return false;
  }

  private static int getTitleString (int position, boolean isDesc) {
    switch (position) {
      case 0: return isDesc ? R.string.Page1Message : R.string.Page1Title;
      case 1: return isDesc ? R.string.Page2Message : R.string.Page2Title;
      case 2: return isDesc ? R.string.Page3Message : R.string.Page3Title;
      case 3: return isDesc ? R.string.Page4Message : R.string.Page4Title;
      case 4: return isDesc ? R.string.Page5Message : R.string.Page5Title;
      case 5: return isDesc ? R.string.Page6Message : R.string.Page6Title;
    }
    return isDesc ? R.string.Page1Message : R.string.Page1Title;
  }

  private CharSequence[] descs = new CharSequence[6];

  private CharSequence getDesc (int pos) {
    if (descs[pos] != null) {
      return descs[pos];
    } else {
      String text = getString(getTitleString(pos, true));
      return (descs[pos] = Strings.replaceBoldTokens(text, R.id.theme_color_text));
    }
  }

  private final String[] titles = new String[6];

  private String getTitle (int pos) {
    if (titles[pos] == null) {
      return (titles[pos] = getString(getTitleString(pos, false)));
    }
    return titles[pos];
  }

  private void updateAllTexts () {
    for (int i = 0; i < titles.length; i++) {
      titles[i] = null;
    }
    for (int i = 0; i < descs.length; i++) {
      descs[i] = null;
    }
    textLayout.resetTitleWidths();
    updateTexts(true);
    textLayout.invalidate();
  }

  private void updateTexts (boolean force) {
    if (force) {
      textLayout.reset();
    }
    if (basePos != lastActualPosition || force) {
      basePos = lastActualPosition;
      textLayout.setBase(basePos, getTitle(basePos), getDesc(basePos));
    }
    int actualPreviewPos = lastActualPosition + 1 > 5 ? -1 : lastActualPosition + 1;
    if (previewPos != actualPreviewPos || force) {
      previewPos = actualPreviewPos;
      if (previewPos == -1) {
        textLayout.setPreview(-1, null, null);
      } else {
        textLayout.setPreview(previewPos, getTitle(previewPos), getDesc(previewPos));
      }
    }
  }

  // Text view

  private static class TextLayout extends View {
    private Paint titlePaint;
    private TextPaint textPaint;
    private int titleY;

    private final float[] titleWidths;

    private int paddingLeft, offsetTop;
    private boolean useCenter;
    private float yOffset;

    public void setYOffset (float y) {
      if (this.yOffset != y) {
        this.yOffset = y;
        invalidate();
      }
    }

    public TextLayout (Context context) {
      super(context);

      titleWidths = new float[6];

      titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      titlePaint.setTextSize(Screen.dp(24f));
      titlePaint.setTypeface(Fonts.getRobotoMedium());
      titlePaint.setColor(Theme.textAccentColor());

      textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      textPaint.setTextSize(Screen.dp(16f));
      textPaint.setTypeface(Fonts.getRobotoRegular());
      textPaint.setColor(Theme.textAccentColor());

      paddingLeft = Screen.dp(16f);
      offsetTop = Screen.dp(42f);

      titleY = Screen.dp(22f);
    }

    public void initWithController (IntroController c) {
      c.getThemeListeners().addThemeListener(titlePaint, R.id.theme_color_text, ThemeListenerEntry.MODE_PAINT_COLOR);
      c.getThemeListeners().addThemeListener(textPaint, R.id.theme_color_text, ThemeListenerEntry.MODE_PAINT_COLOR);
    }

    public void setUseCenter (boolean useCenter) {
      this.useCenter = useCenter;
    }

    private int basePos = -1;
    private String title;
    private float titleWidth;
    private CharSequence baseText;

    public void setBase (int pos, String title, CharSequence spanned) {
      this.basePos = pos;
      this.title = title;
      this.titleWidth = getTitleWidth(pos, title);
      this.baseText = spanned;
    }

    public void reset () {
      ArrayUtils.clear(staticLayouts);
    }

    private int previewPos = -1;
    private String preview;
    private float previewWidth;
    private CharSequence previewText;

    public void resetTitleWidths () {
      for (int i = 0; i < titleWidths.length; i++) {
        titleWidths[i] = 0;
      }
    }

    private float getTitleWidth (int pos, String title) {
      if (titleWidths[pos] == 0) {
        return (titleWidths[pos] = U.measureText(title, titlePaint));
      }
      return titleWidths[pos];
    }

    public void setPreview (int pos, String preview, CharSequence spanned) {
      this.previewPos = pos;
      if (pos == -1) {
        this.preview = null;
        this.previewWidth = 0;
        this.previewText = null;
      } else {
        this.preview = preview;
        this.previewWidth = getTitleWidth(pos, preview);
        this.previewText = spanned;
      }
    }

    private StaticLayout[] staticLayouts = new StaticLayout[6];
    private int staticLayoutWidth;

    public void prepare (String... titles) {
      int i = 0;
      for (float ignored : titleWidths) {
        titleWidths[i] = U.measureText(titles[i], titlePaint);
        i++;
      }
    }

    public void prepare (int viewWidth, CharSequence... sequences) {
      this.staticLayoutWidth = viewWidth;
      int i = 0;
      for (StaticLayout ignored : staticLayouts) {
        staticLayouts[i] = new StaticLayout(sequences[i], textPaint, viewWidth - Screen.dp(16f) * 2 < 0 ? viewWidth : viewWidth - Screen.dp(16f) * 2, Layout.Alignment.ALIGN_CENTER, 1f, lineSpacing(), false);
        i++;
      }
    }

    private static int lineSpacing () {
      return Screen.dp(3f);
    }

    private StaticLayout getStaticLayout (int pos, CharSequence text) {
      int viewWidth = getMeasuredWidth();
      if (pos == -1 || text == null || viewWidth == 0) {
        return null;
      }
      if (staticLayoutWidth != viewWidth) {
        if (staticLayoutWidth != 0) {
          int i = 0;
          for (StaticLayout ignored : staticLayouts) {
            staticLayouts[i++] = null;
          }
        }
        staticLayoutWidth = viewWidth;
      }
      if (staticLayouts[pos] == null) {
        StaticLayout layout = new StaticLayout(text, textPaint, viewWidth - Screen.dp(16f) * 2 < 0 ? viewWidth : viewWidth - Screen.dp(16f) * 2, Layout.Alignment.ALIGN_CENTER, 1f, lineSpacing(), false);
        return (staticLayouts[pos] = layout);
      }
      return staticLayouts[pos];
    }

    private float factor;
    private float buttonOffset;

    public void setFactor (float factor, float buttonOffset) {
      if (this.factor != factor || this.buttonOffset != buttonOffset) {
        this.factor = factor;
        this.buttonOffset = buttonOffset;
        if (buttonOffset == 0f) {
          origOffset = 0f;
        }
        invalidate();
      }
    }

    private int currentPage;
    private float origOffset;

    public void setPage (int page, float offset) {
      if (this.currentPage != page || this.buttonOffset != offset) {
        this.currentPage = page;
        this.buttonOffset = offset;
        this.origOffset = offset;
        invalidate();
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      getStaticLayout(basePos, baseText);
      getStaticLayout(previewPos, previewText);
    }

    @Override
    protected void onDraw (Canvas c) {
      int viewWidth = getMeasuredWidth();
      int viewHeight = getMeasuredHeight();

      int offsetY = (useCenter ? viewHeight : viewHeight - Screen.dp(16f) * 2 - Screen.dp(64f)) / 2 - Screen.dp(72f);

      if (!useCenter) {
        offsetY = Math.max(offsetY, Screen.dp(18f));
      }

      if (title != null) {
        int x = (int) (viewWidth / 2 - titleWidth / 2 - (float) viewWidth * factor);
        c.drawText(title, x, titleY + offsetY, titlePaint);
      }

      StaticLayout text = getStaticLayout(basePos, baseText);

      if (text != null) {
        c.save();
        c.translate(paddingLeft - (float) viewWidth * factor, offsetTop + offsetY);
        text.draw(c);
        c.restore();
      }

      if (preview != null) {
        int x = (int) (viewWidth / 2 - previewWidth / 2 + (float) viewWidth * (1f - factor));
        c.drawText(preview, x, titleY + offsetY, titlePaint);
      }

      StaticLayout preview = getStaticLayout(previewPos, previewText);
      if (preview != null) {
        c.save();
        c.translate(paddingLeft + (float) viewWidth * (1f - factor), offsetTop + offsetY);
        preview.draw(c);
        c.restore();
      }


      int spacing = Screen.dp(4f);
      int radius = Screen.dp(2.5f);

      int totalWidth = radius * 2 * 6 + spacing * 5;
      int startX = viewWidth / 2 - totalWidth / 2;
      int y = viewHeight - Screen.dp(80f) - Screen.dp(16f) - (int) (Screen.dp(48f) * yOffset * .75f);

      int activeX = startX;

      for (int i = 0; i < 6; i++) {
        startX += radius;
        c.drawCircle(startX, y, radius, Paints.fillingPaint(currentPage == i ? Theme.introDotActiveColor() : Theme.introDotInactiveColor()));

        if (currentPage == i) {
          activeX = startX;
        }

        startX += radius + spacing;
      }

      float factor;
      if (origOffset == 0f || buttonOffset == 0f) {
        factor = buttonOffset;
      } else if (origOffset < 0f) {
        factor = -buttonOffset / origOffset;
      } else {
        factor = buttonOffset / origOffset;
      }

      if (factor != 0f) {
        RectF rectF = Paints.getRectF();
        float offset = (float) (spacing + radius + radius) * factor;
        if (factor > 0f) {
          rectF.set(activeX - radius, y - radius, activeX + offset + radius, y + radius);
        } else {
          rectF.set(activeX - radius + offset, y - radius, activeX + radius, y + radius);
        }
        c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(Theme.introDotActiveColor()));
      }
    }
  }

  // Page

  private int lastPosition;

  private int lastActualPosition;
  private float lastOffset;

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    lastActualPosition = position;
    lastOffset = positionOffset;
    updateTexts(false);
    float actualOffset = calculateActualOffset(position);
    textLayout.setFactor(positionOffset, actualOffset);
    N.setScrollOffset(actualOffset);
    requestRender(); // TODO limit render
  }

  private float calculateActualOffset (int position) {
    return position == lastPosition ? lastOffset : position == lastPosition + 1 ? 1f + lastOffset : position == lastPosition - 1 ? lastOffset - 1f : lastOffset;
  }

  @Override
  public void onPageSelected (int position) {
    if (lastPosition != position) {
      lastPosition = position;
      textLayout.setPage(position, calculateActualOffset(lastActualPosition));
      N.setPage(position);
      /*if (!INFINITE_MODE) {
        requestRender();
      }*/
    }
  }

  @Override
  public void onPageScrollStateChanged (int state) {
    if (!INFINITE_MODE) {
      switch (state) {
        case ViewPager.SCROLL_STATE_DRAGGING:
        case ViewPager.SCROLL_STATE_SETTLING: {
          startSchedule();
          break;
        }
        case ViewPager.SCROLL_STATE_IDLE: {
          stopScheduleDelayed();
          break;
        }
      }
    }
  }

  // Adapter

  private static class IntroPagerAdapter extends PagerAdapter {
    private final Context context;

    public IntroPagerAdapter (Context context) {
      this.context = context;
    }

    @Override
    public int getCount () {
      return 6;
    }

    private FrameLayoutFix[] contentViews = new FrameLayoutFix[getCount()];

    @Override
    public Object instantiateItem (ViewGroup container, int position) {
      if (contentViews[position] == null) {
        contentViews[position] = new FrameLayoutFix(context);
        contentViews[position].setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      }
      container.addView(contentViews[position]);
      return contentViews[position];
    }

    @Override
    public void destroyItem (ViewGroup container, int position, Object object) {
      container.removeView((ViewGroup) object);
    }

    @Override
    public boolean isViewFromObject (View view, Object object) {
      return view == object;
    }
  }

  // Renderer

  private final SparseArrayCompat<Bitmap> icons = new SparseArrayCompat<>(23);
  private final SparseArrayCompat<Bitmap> iconsSpecial = new SparseArrayCompat<>(2);

  private Bitmap getTextureBitmap (@DrawableRes int iconRes) {
    Bitmap bitmap;
    synchronized (icons) {
      bitmap = icons.get(iconRes);
      if (bitmap == null || bitmap.isRecycled()) {
        bitmap = BitmapFactory.decodeResource(UI.getResources(), iconRes);
        icons.put(iconRes, bitmap);
      }
    }
    return bitmap;
  }

  private void destroyTextures () {
    synchronized (icons) {
      // Cleaning up bitmap resources
      final int size = icons.size();
      for (int i = 0; i < size; i++) {
        icons.valueAt(i).recycle();
      }
      icons.clear();
    }
    destroySpecialTextures();
  }

  private void destroySpecialTextures () {
    synchronized (icons) {
      final int size = iconsSpecial.size();
      for (int i = 0; i < size; i++) {
        iconsSpecial.valueAt(i).recycle();
      }
      iconsSpecial.clear();
    }
  }

  private void destroyTexts (boolean descsOnly) {
    if (!descsOnly) {
      ArrayUtils.clear(titles);
    }
    ArrayUtils.clear(descs);
  }

  private void requestRender () {
    if (surfaceView != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && USE_TEXTURE_VIEW) {
        ((org.thunderdog.challegram.texture.CustomTextureView) surfaceView).requestRender();
      } else {
        ((GLSurfaceView) surfaceView).requestRender();
      }
    }
  }

  private int[] mTexture = new int[1];

  private int loadTexture (@Nullable GL10 gl, @DrawableRes int res) {
    return loadTexture(gl, getTextureBitmap(res));
  }

  private int loadTexture (@Nullable GL10 gl, Bitmap bitmap) {
    mTexture[0] = 0;
    mValue[0] = 0;

    if (gl != null) {
      //Generate one texture pointer...
      gl.glGenTextures(1, mValue, 0);
      //...and bind it to our array
      gl.glBindTexture(GL10.GL_TEXTURE_2D, mValue[0]);

      //Create Nearest Filtered Texture
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

      //Different possible texture parameters, e.g. GL10.GL_CLAMP_TO_EDGE
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
      gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

      //Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
      GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
    } else {
      //Generate one texture pointer...
      GLES20.glGenTextures(1, mValue, 0);
      //...and bind it to our array
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mValue[0]);

      //Create Nearest Filtered Texture
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

      //Different possible texture parameters, e.g. GL10.GL_CLAMP_TO_EDGE
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

      //Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
    }

    return mValue[0];
  }

  private Bitmap getSphereBitmap () {
    Bitmap bitmap;
    synchronized (icons) {
      bitmap = iconsSpecial.get(0);
    }
    if (bitmap != null) {
      return bitmap;
    }
    int size = Screen.dp(220f);
    bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    c.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, Paints.fillingPaint(0xff35a6de));
    U.recycle(c);
    synchronized (icons) {
      iconsSpecial.put(0, bitmap);
    }
    return bitmap;
  }

  private Bitmap getPowerfulMask () {
    Bitmap bitmap;
    synchronized (icons) {
      bitmap = iconsSpecial.get(1);
    }
    if (bitmap != null) {
      return bitmap;
    }
    Bitmap maskBitmap = getTextureBitmap(R.drawable.intro_powerful_mask);
    bitmap = Bitmap.createBitmap(maskBitmap.getWidth(), maskBitmap.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bitmap);
    if (Theme.fillingColor() != 0xffffffff) {
      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
      paint.setColorFilter(new PorterDuffColorFilter(Theme.fillingColor(), PorterDuff.Mode.MULTIPLY));
      c.drawBitmap(maskBitmap, 0, 0, paint);
    } else {
      c.drawBitmap(maskBitmap, 0, 0, Paints.getBitmapPaint());
    }
    U.recycle(c);
    synchronized (icons) {
      iconsSpecial.put(1, bitmap);
    }
    return bitmap;
  }

  // R.drawable.intro_tg_sphere

  private void initSurface (@Nullable GL10 gl) {
    N.setIcTextures(
      loadTexture(gl, R.drawable.intro_ic_bubble_dot),
      loadTexture(gl, R.drawable.intro_ic_bubble),
      loadTexture(gl, R.drawable.intro_ic_cam_lens),
      loadTexture(gl, R.drawable.intro_ic_cam),
      loadTexture(gl, R.drawable.intro_ic_pencil),
      loadTexture(gl, R.drawable.intro_ic_pin),
      loadTexture(gl, R.drawable.intro_ic_smile_eye),
      loadTexture(gl, R.drawable.intro_ic_smile),
      loadTexture(gl, R.drawable.intro_ic_videocam)
    );

    N.setTelegramTextures(
      loadTexture(gl, getSphereBitmap()),
      loadTexture(gl, R.drawable.intro_tg_plane)
    );

    N.setPowerfulTextures(
      loadTexture(gl, R.drawable.intro_powerful_mask),
      loadTexture(gl, R.drawable.intro_powerful_star),
      loadTexture(gl, R.drawable.intro_powerful_infinity),
      loadTexture(gl, R.drawable.intro_powerful_infinity_white)
    );

    N.setPrivateTextures(
      loadTexture(gl, R.drawable.intro_private_door),
      loadTexture(gl, R.drawable.intro_private_screw)
    );

    N.setFastTextures(
      loadTexture(gl, R.drawable.intro_fast_body),
      loadTexture(gl, R.drawable.intro_fast_spiral),
      loadTexture(gl, R.drawable.intro_fast_arrow),
      loadTexture(gl, R.drawable.intro_fast_arrow_shadow)
    );

    N.setFreeTextures(
      loadTexture(gl, R.drawable.intro_knot_up),
      loadTexture(gl, R.drawable.intro_knot_down)
    );

    __setIntroBgColor(Theme.fillingColor());

    N.onSurfaceCreated();
  }

  private long currentDate = System.currentTimeMillis() - 1000;

  private void drawFrame () {
    if (Config.DEBUG_GALAXY_TAB_2) {
      Log.i(Log.TAG_INTRO, "on_draw_frame");
    }
    float time = (System.currentTimeMillis() - currentDate) / 1000.0f;
    N.setDate(time);
    N.onDrawFrame();
    setIntroAttempted(false);
  }

  private void resizeSurface (int width, int height) {
    N.onSurfaceChanged(width, height, Screen.density(), 0); // Math.min((float) width / 148.0f, (float) height / 148.0f)
  }

  // GLSurfaceView callbacks

  @Override
  public void onSurfaceCreated (GL10 gl, EGLConfig eglConfig) {
    initSurface(gl);
  }

  @Override
  public void onSurfaceChanged (GL10 gl10, int width, int height) {
    resizeSurface(width, height);
    requestRender();
  }

  @Override
  public void onDrawFrame (GL10 gl10) {
    drawFrame();
  }

  // MultiSampleConfigChooser

  @Override
  public EGLConfig chooseConfig (EGL10 egl, EGLDisplay display) {
    mTexture[0] = 0;
    mValue[0] = 0;

// Try to find a normal multisample configuration first.
    int[] configSpec = {
      EGL10.EGL_RED_SIZE, 5,
      EGL10.EGL_GREEN_SIZE, 6,
      EGL10.EGL_BLUE_SIZE, 5,
      EGL10.EGL_DEPTH_SIZE, 16,
// Requires that setEGLContextClientVersion(2) is called on the view.
      EGL10.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */,
      EGL10.EGL_SAMPLE_BUFFERS, 1 /* true */,
      EGL10.EGL_SAMPLES, 2,
      EGL10.EGL_STENCIL_SIZE, 1,
      EGL10.EGL_NONE
    };
    if (!egl.eglChooseConfig(display, configSpec, null, 0,
      mValue)) {
      throw new IllegalArgumentException("eglChooseConfig failed");
    }
    int numConfigs = mValue[0];
    if (numConfigs <= 0) {
// No normal multisampling config was found. Try to create a
// converage multisampling configuration, for the nVidia Tegra2.
// See the EGL_NV_coverage_sample documentation.
      final int EGL_COVERAGE_BUFFERS_NV = 0x30E0;
      final int EGL_COVERAGE_SAMPLES_NV = 0x30E1;
      configSpec = new int[]{
        EGL10.EGL_RED_SIZE, 5,
        EGL10.EGL_GREEN_SIZE, 6,
        EGL10.EGL_BLUE_SIZE, 5,
        EGL10.EGL_DEPTH_SIZE, 16,
        EGL10.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */,
        EGL_COVERAGE_BUFFERS_NV, 1 /* true */,
        EGL_COVERAGE_SAMPLES_NV, 2,  // always 5 in practice on tegra 2
        EGL10.EGL_STENCIL_SIZE, 1,
        EGL10.EGL_NONE
      };

      if (!egl.eglChooseConfig(display, configSpec, null, 0,
        mValue)) {
        Log.e(Log.TAG_INTRO, "2nd eglChooseConfig failed");
      }
      numConfigs = mValue[0];

      if (numConfigs <= 0) {
        // Give up, try without multisampling.
        configSpec = new int[]{
          EGL10.EGL_RED_SIZE, 5,
          EGL10.EGL_GREEN_SIZE, 6,
          EGL10.EGL_BLUE_SIZE, 5,
          EGL10.EGL_DEPTH_SIZE, 16,
          EGL10.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */,
          EGL10.EGL_STENCIL_SIZE, 1,
          EGL10.EGL_NONE
        };

        if (!egl.eglChooseConfig(display, configSpec, null, 0,
          mValue)) {
          Log.e(Log.TAG_INTRO, "3rd eglChooseConfig failed");
        }
        numConfigs = mValue[0];

        if (numConfigs <= 0) {
          Log.e(Log.TAG_INTRO, "No configs match configSpec");
        }
      } else {
        mUsesCoverageAa = true;
      }
    }

    // Get all matching configurations.
    EGLConfig[] configs = new EGLConfig[numConfigs];
    if (!egl.eglChooseConfig(display, configSpec, configs, numConfigs,
      mValue)) {
      Log.e(Log.TAG_INTRO, "data eglChooseConfig failed");
    }

    // CAUTION! eglChooseConfigs returns configs with higher bit depth
    // first: Even though we asked for rgb565 configurations, rgb888
    // configurations are considered to be "better" and returned first.
    // You need to explicitly filter the data returned by eglChooseConfig!
    int index = -1;
    for (int i = 0; i < configs.length; ++i) {
      if (findConfigAttrib(egl, display, configs[i], EGL10.EGL_RED_SIZE, 0) == 5) {
        index = i;
        break;
      }
    }
    if (index == -1) {
      Log.w(Log.TAG_INTRO, "Did not find sane config, using first");
      index = 0;
    }
    EGLConfig config = configs.length > 0 ? configs[index] : null;
    if (config == null) {
      throw new IllegalArgumentException("No config chosen");
    }
    return config;

    /*mValue = new int[1];

    // Try to find a normal multisample configuration first.
    int[] configSpec = {
      EGL10.EGL_RED_SIZE, 5,
      EGL10.EGL_GREEN_SIZE, 6,
      EGL10.EGL_BLUE_SIZE, 5,
      EGL10.EGL_DEPTH_SIZE, 16,
      // Requires that setEGLContextClientVersion(2) is called on the view.
      EGL10.EGL_RENDERABLE_TYPE, 4 *//* EGL_OPENGL_ES2_BIT *//*,
      EGL10.EGL_SAMPLE_BUFFERS, 1 *//* true *//*,
      EGL10.EGL_SAMPLES, 5,
      EGL10.EGL_NONE
    };

    if (!egl.eglChooseConfig(display, configSpec, null, 0, mValue)) {
      Logger.e("eglChooseConfig failed");
    }
    int numConfigs = mValue[0];

    if (numConfigs <= 0) {
      // No normal multisampling config was found. Try to create a
      // converage multisampling configuration, for the nVidia Tegra2.
      // See the EGL_NV_coverage_sample documentation.

      final int EGL_COVERAGE_BUFFERS_NV = 0x30E0;
      final int EGL_COVERAGE_SAMPLES_NV = 0x30E1;

      configSpec = new int[]{
        EGL10.EGL_RED_SIZE, 5,
        EGL10.EGL_GREEN_SIZE, 6,
        EGL10.EGL_BLUE_SIZE, 5,
        EGL10.EGL_DEPTH_SIZE, 16,
        EGL10.EGL_RENDERABLE_TYPE, 4 *//* EGL_OPENGL_ES2_BIT *//*,
        EGL_COVERAGE_BUFFERS_NV, 1 *//* true *//*,
        EGL_COVERAGE_SAMPLES_NV, 5,  // always 5 in practice on tegra 2
        EGL10.EGL_NONE
      };

      if (!egl.eglChooseConfig(display, configSpec, null, 0, mValue)) {
        Logger.e("2nd eglChooseConfig failed");
      } else {
        mUsesCoverageAa = true;
      }
      numConfigs = mValue[0];

      if (numConfigs <= 0) {
        // Give up, try without multisampling.
        configSpec = new int[]{
          EGL10.EGL_RED_SIZE, 5,
          EGL10.EGL_GREEN_SIZE, 6,
          EGL10.EGL_BLUE_SIZE, 5,
          EGL10.EGL_DEPTH_SIZE, 16,
          EGL10.EGL_RENDERABLE_TYPE, 4 *//* EGL_OPENGL_ES2_BIT *//*,
          EGL10.EGL_NONE
        };

        if (!egl.eglChooseConfig(display, configSpec, null, 0, mValue)) {
          Logger.e("3rd eglChooseConfig failed");
        }
        numConfigs = mValue[0];

        if (numConfigs <= 0) {
          throw new IllegalArgumentException("No configs match configSpec");
        }
      }
    }

    // Get all matching configurations.
    EGLConfig[] configs = new EGLConfig[numConfigs];
    if (!egl.eglChooseConfig(display, configSpec, configs, numConfigs, mValue)) {
      throw new IllegalArgumentException("data eglChooseConfig failed");
    }

    // CAUTION! eglChooseConfigs returns configs with higher bit depth
    // first: Even though we asked for rgb565 configurations, rgb888
    // configurations are considered to be "better" and returned first.
    // You need to explicitly filter the data returned by eglChooseConfig!
    int index = -1;
    for (int i = 0; i < configs.length; ++i) {
      if (findConfigAttrib(egl, display, configs[i], EGL10.EGL_RED_SIZE, 0) == 5) {
        index = i;
        break;
      }
    }
    if (index == -1) {
      Logger.w("Did not find sane config, using first");
    }
    EGLConfig config = configs.length > 0 ? configs[index] : null;
    if (config == null) {
      throw new IllegalArgumentException("No config chosen");
    }
    return config;*/
  }

  private int findConfigAttrib (EGL10 egl, EGLDisplay display,
                                EGLConfig config, int attribute, int defaultValue) {
    if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
      return mValue[0];
    }
    return defaultValue;
  }

  /**
   * See EGL_NV_coverage_sample documentation for more information. This is used in the OpenGL Renderer to determine if to clear the GL_COVERAGE_BUFFER_BIT_NV.
   *
   * @return if the configuration uses NVidia Tegra coverage multisampling configuration.
   *
   */
  public boolean usesCoverageAa () {
    return mUsesCoverageAa;
  }

  private int[] mValue = new int[1];
  /**
   * Boolean to store if the graphics configuration uses NVidia Tegra coverage multisampling
   *
   * @see IntroController#usesCoverageAa()
   */
  private boolean mUsesCoverageAa;

  // Intro

  private static int __lastIntroBgColor = 0xffffffff;
  private void __setIntroBgColor (int color) {
    if (__lastIntroBgColor != color) {
      __lastIntroBgColor = color;
      N.setColor((float) Color.red(color) / 255f, (float) Color.green(color) / 255f, (float) Color.blue(color) / 255f);
      requestRender();
    }
  }
}
