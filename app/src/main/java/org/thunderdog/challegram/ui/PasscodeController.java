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
 * File created on 07/08/2015 at 19:33
 */
package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.content.Context;
import android.content.res.Configuration;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.passcode.InvisibleTransformationMethod;
import org.thunderdog.challegram.component.passcode.PasscodeView;
import org.thunderdog.challegram.component.passcode.PinInputLayout;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.FingerprintPassword;
import org.thunderdog.challegram.core.GesturePassword;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ToggleHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.PasscodeBuilder;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.ToggleDelegate;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.v.EditText;
import org.thunderdog.challegram.widget.SwirlView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

public class PasscodeController extends ViewController<PasscodeController.Args> implements Menu, TextView.OnEditorActionListener, PasscodeView.Callback, PinInputLayout.Callback, ToggleDelegate, Unlockable, GestureOverlayView.OnGesturePerformedListener, GesturePassword.Callback, FingerprintPassword.Callback, BaseActivity.SimpleStateListener, Settings.PasscodeTickListener {
  public static class Args {
    public final TdApi.Chat chat;
    public final Tdlib.ChatPasscode passcode;
    public final TdlibUi.ChatOpenParameters chatOpenParameters;

    public Args (TdApi.Chat chat, Tdlib.ChatPasscode passcode, TdlibUi.ChatOpenParameters parameters) {
      this.chat = chat;
      this.passcode = passcode != null ? passcode : new Tdlib.ChatPasscode(Passcode.MODE_NONE, 0, "", null);
      this.chatOpenParameters = parameters;
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    if (specificChat == null)
      return false;
    if (chatOpenParameters != null && !chatOpenParameters.saveInstanceState(outState, keyPrefix))
      return false;
    super.saveInstanceState(outState, keyPrefix);
    outState.putLong(keyPrefix + "chat_id", specificChat.id);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    long chatId = in.getLong(keyPrefix + "chat_id");
    if (chatId == 0 || tdlib == null)
      return false;
    TdApi.Chat chat = tdlib.chatSync(chatId);
    Tdlib.ChatPasscode passcode = tdlib.chatPasscode(chat);
    if (passcode == null)
      return false;
    super.restoreInstanceState(in, keyPrefix);
    TdlibUi.ChatOpenParameters parameters = TdlibUi.ChatOpenParameters.restoreInstanceState(tdlib, in, keyPrefix);
    if (parameters == null)
      return false;
    setArguments(new Args(chat, passcode, parameters));
    return true;
  }

  public static final int MODE_UNLOCK = 0x00;
  public static final int MODE_SETUP = 0x01;
  public static final int MODE_UNLOCK_SETUP = 0x02;

  public PasscodeController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private int controllerMode = MODE_UNLOCK;
  private int mode;

  private FrameLayoutFix contentView;
  private PasscodeView passcodeView;

  private TdApi.Chat specificChat;
  private Tdlib.ChatPasscode chatPasscode;
  private TdlibUi.ChatOpenParameters chatOpenParameters;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.specificChat = args.chat;
    this.chatPasscode = args.passcode;
    this.chatOpenParameters = args.chatOpenParameters;
  }

  @Override
  public long getChatId () {
    return specificChat != null ? specificChat.id : 0;
  }

  @Override
  public int getId () {
    return R.id.controller_passcode;
  }

  public void setPasscodeMode (int mode) {
    controllerMode = mode;
  }

  private boolean inFingerprintSetup;
  public void setInFingerprintSetup () {
    inFingerprintSetup = true;
    forcedMode = Passcode.MODE_FINGERPRINT;
  }

  private int forcedMode;
  public void forceMode (int mode) {
    this.forcedMode = mode;
  }

  private SwirlView swirlView;

  private boolean inGlobalUnlockMode () {
    return controllerMode == MODE_UNLOCK && specificChat == null;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.passcodeIcon;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.passcode;
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context) {
      @Override
      public boolean onInterceptTouchEvent (MotionEvent ev) {
        return (ev.getAction() == MotionEvent.ACTION_DOWN && !inSetupMode() && Settings.instance().isPasscodeBlocked(mode, makeBruteForceSuffix())) || super.onInterceptTouchEvent(ev);
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.passcode, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    getCustomHeaderCell();

    passcodeView = new PasscodeView(context);
    passcodeView.setCallback(this);
    if (inGlobalUnlockMode()) {
      passcodeView.setPadding(0, HeaderView.getSize(true), 0, 0);
      passcodeView.setDisplayLogo();
    }
    if (inSetupMode()) {
      setMode(forcedMode != 0 ? forcedMode : isPasscodeEnabled() ? getPasscodeMode() : Passcode.MODE_PINCODE);
    } else {
      setMode(getPasscodeMode());
      if (mode != Passcode.MODE_FINGERPRINT && needUnlockByFingerprint()) {
        swirlView = new SwirlView(context);
        swirlView.setColorFilter(ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(ColorId.passcodeIcon)));
        addThemeFilterListener(swirlView, ColorId.passcodeIcon).setIsSubtitle(true);
        swirlView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(36f), Screen.dp(36f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, Screen.dp(18f)));
        setNeedFinger(true);
        contentView.addView(swirlView);
      }
    }
    contentView.addView(passcodeView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    this.context.addSimpleStateListener(this);

    if (!inSetupMode()) {
      Settings.instance().addPasscodeTickLister(this);
    }

    return contentView;
  }

  public void fadeOut (Animator.AnimatorListener listener) {
    Views.animateAlpha(getValue(), 0f, 100l, AnimatorUtils.DECELERATE_INTERPOLATOR, listener);
  }

  private void setMode (int mode) {
    if (this.mode == mode) return;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (mode == Passcode.MODE_FINGERPRINT && !FingerprintPassword.hasFingerprints()) {
        UI.showToast(R.string.fingerprint_hint3, Toast.LENGTH_SHORT);
        if (controllerMode == MODE_SETUP) {
          return;
        }
      }
    }

    setName(Passcode.getModeName(mode));
    updateHeaderText();

    if (this.mode != 0) {
      switch (this.mode) {
        case Passcode.MODE_PASSWORD: {
          hidePasswordWrap();
          break;
        }
        case Passcode.MODE_PINCODE: {
          hidePincodeWrap();
          break;
        }
        case Passcode.MODE_GESTURE: {
          hideGestureWrap();
          break;
        }
        case Passcode.MODE_FINGERPRINT: {
          hideFingerprintIcon();
          break;
        }
      }
    }

    this.mode = mode;

    passcodeView.setModeAndState(mode, controllerMode == MODE_SETUP ? Passcode.STATE_CHOOSE : Passcode.STATE_UNLOCK);

    switch (mode) {
      case Passcode.MODE_PASSWORD: {
        showPasswordWrap();
        break;
      }
      case Passcode.MODE_PINCODE: {
        showPincodeWrap();
        break;
      }
      case Passcode.MODE_GESTURE: {
        showGestureWrap();
        break;
      }
      case Passcode.MODE_FINGERPRINT: {
        showFingerprintIcon();
        break;
      }
    }

    if (controllerMode == MODE_SETUP) {
      updateConfirmMode();
    }
  }

  private String confirmPassword;
  private EditText passwordView;

  private void showPasswordWrap () {
    if (passwordView == null) {
      passwordView = (EditText) Views.inflate(context(), R.layout.input_password, contentView);
      passwordView.setTypeface(Fonts.getRobotoRegular());
      passwordView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
      passwordView.setUseIncognitoKeyboard(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      passwordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      passwordView.setTransformationMethod(isPasscodeVisible() ? PasswordTransformationMethod.getInstance() : InvisibleTransformationMethod.instance());
      passwordView.setGravity(Gravity.CENTER);
      passwordView.setTextColor(Theme.getColor(ColorId.passcodeText));
      passwordView.setOnEditorActionListener(this);
      passwordView.setImeOptions(EditorInfo.IME_ACTION_DONE);
      addThemeTextColorListener(passwordView, ColorId.passcodeText);
      // Views.setCursorColor(passwordView, 0xffadcae0, R.drawable.cursor_blue);

      ViewUtils.setBackground(passwordView, null);

      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(43f));
      params.setMargins(Screen.dp(44f), Screen.dp(127f), Screen.dp(44f), 0);

      if (inGlobalUnlockMode()) {
        params.topMargin += HeaderView.getSize(true);
      }

      passwordView.setLayoutParams(params);
    }

    contentView.addView(passwordView);

    if (inGlobalUnlockMode()) {
      UI.showKeyboardDelayed(passwordView);
    } else if (!isNavigationAnimating()) {
      Keyboard.show(passwordView);
    }
  }

  private void hidePasswordWrap () {
    Keyboard.hide(passwordView);
    contentView.removeView(passwordView);
  }

  private PinInputLayout pinLayout;

  private void showPincodeWrap () {
    if (pinLayout == null) {
      pinLayout = new PinInputLayout(context());
      pinLayout.initWithFeedback(isPasscodeVisible());
      pinLayout.setCallback(this);
    }
    updatePincodeOrientation();

    contentView.addView(pinLayout);
  }

  private void updatePincodeOrientation () {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(pinLayout.getLayoutParams());
    if (UI.getOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
      params.gravity = Gravity.RIGHT | Gravity.TOP;
      params.topMargin = 0;
    } else {
      params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      params.topMargin = Screen.dp(156f);
    }
    if (inGlobalUnlockMode()) {
      params.topMargin += HeaderView.getSize(true);
    }
    pinLayout.updateHeights();
    pinLayout.setLayoutParams(params);
  }

  private void hidePincodeWrap () {
    if (pincode != null) {
      pincode.clear();
    }
    passcodeView.getPincodeOutput().clear();
    contentView.removeView(pinLayout);
  }

  private PasscodeBuilder pincode;

  @Override
  public void onPinRemove () {
    if (passcodeView.getPincodeOutput().isAnimating()) return;
    if (pincode != null) {
      pincode.removeLast();
    }
    passcodeView.getPincodeOutput().remove();
  }

  @Override
  public boolean onPinRemoveAll () {
    if (passcodeView.getPincodeOutput().isAnimating()) return false;
    if (passcodeView.getPincodeOutput().removeAll()) {
      if (pincode != null) {
        pincode.clear();
      }
      return true;
    }
    return false;
  }

  @Override
  public void onPinAppend (int digit) {
    if (passcodeView.getPincodeOutput().isAnimating() || (pincode != null && pincode.getSize() >= Passcode.PINCODE_SIZE)) return;
    if (pincode == null) {
      pincode = new PasscodeBuilder();
    }
    pincode.append(digit);
    passcodeView.getPincodeOutput().append();

    if (pincode.getSize() == Passcode.PINCODE_SIZE) {
      handlePincode(new PasscodeBuilder(pincode));
      pincode.clear();
    }
  }

  // Gesture

  private GestureOverlayView gestureView;

  private void showGestureWrap () {
    if (gestureView == null) {
      gestureView = new GestureOverlayView(context());
      gestureView.setGestureStrokeWidth(Screen.dp(3f));
      gestureView.setOrientation(GestureOverlayView.ORIENTATION_VERTICAL);
      gestureView.setGestureColor(Theme.getColor(ColorId.passcodeIcon));
      gestureView.setUncertainGestureColor(Theme.getColor(ColorId.passcodeIcon));
      gestureView.setGestureVisible(isPasscodeVisible());
      gestureView.setFadeEnabled(true);
      gestureView.addOnGesturePerformedListener(this);
      gestureView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    contentView.addView(gestureView);
  }

  private void hideGestureWrap () {
    contentView.removeView(gestureView);
  }

  private boolean ignoreGesture;

  @Override
  public void onGesturePerformed (GestureOverlayView overlay, Gesture gesture) {
    if (!ignoreGesture) {
      processGesture(gesture);
    }
  }

  private void processGestureSetup (final Gesture gesture) {
    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      Background.instance().post(() -> {
        if (!getGesturePassword().compare(gesture, true, null)) {
          UI.showToast(getMismatchString(Passcode.MODE_GESTURE), Toast.LENGTH_SHORT);
          return;
        }

        if (getGesturePassword().save(gesture)) {
          setGesture();
          postNavigateBack();
        } else {
          UI.showToast("Error saving gesture file", Toast.LENGTH_SHORT);
        }
      });
      return;
    }

    passcodeView.setState(Passcode.STATE_CONFIRM);
    ignoreGesture = true;

    Background.instance().post(() -> {
      getGesturePassword().saveTemp(gesture);
      ignoreGesture = false;
    });
  }

  @Override
  public void onGestureLoadError () {
    UI.showToast("Error loading an existing gesture", Toast.LENGTH_SHORT);
    ignoreGesture = false;
  }

  // Fingerprint

  private SwirlView fingerprintView;

  private void updateFingerprintParams (FrameLayoutFix.LayoutParams params, int orientation) {
    params.gravity = orientation == Configuration.ORIENTATION_LANDSCAPE ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL | Gravity.TOP;
    params.leftMargin = params.rightMargin = Screen.dp(44f);
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      params.topMargin = 0;
    } else {
      params.topMargin = Screen.dp(118f);
      if (inGlobalUnlockMode()) {
        params.topMargin += HeaderView.getSize(true);
      }
    }
  }

  private void showFingerprintIcon () {
    if (fingerprintView == null) {
      fingerprintView = new SwirlView(context);
      fingerprintView.setColorFilter(ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(ColorId.passcodeIcon)));
      addThemeFilterListener(fingerprintView, ColorId.passcodeIcon).setIsSubtitle(true);

      int orientation = UI.getOrientation();

      FrameLayoutFix.LayoutParams params;

      params = FrameLayoutFix.newParams(Screen.dp(82f), Screen.dp(82f));
      updateFingerprintParams(params, orientation);

      fingerprintView.setLayoutParams(params);
    } else if (inSetupMode()) {
      fingerprintView.setState(SwirlView.State.OFF, false);
    }
    contentView.addView(fingerprintView);
    setNeedFinger(true);
  }

  @Override
  public boolean needsTempUpdates () {
    return context.isPasscodeShowing();
  }

  private int confirmFingerId;

  @Override
  public void onAuthenticated (final int fingerId) {
    fingerUsed = false;
    if (controllerMode != MODE_SETUP) {
      if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().compareFinger(fingerId)) {
        navigateTo(new PasscodeSetupController(context, tdlib));
      } else {
        if (controllerMode == MODE_UNLOCK && unlockByFinger(fingerId)) {
          UI.unlock(PasscodeController.this);
        } else {
          UI.showToast(R.string.fingerprint_fail, Toast.LENGTH_SHORT);
        }
        checkFingerprintNeeded();
      }
    } else {
      if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
        if (confirmFingerId == fingerId) {
          Background.instance().post(() -> {
            if (inFingerprintSetup) {
              enableUnlockByFingerprint(fingerId);
            } else {
              setFingerprint(fingerId);
            }
            postNavigateBack();
          });
        } else {
          UI.showToast(getMismatchString(Passcode.MODE_FINGERPRINT), Toast.LENGTH_SHORT);
          checkFingerprintNeeded();
        }
      } else {
        this.confirmFingerId = fingerId;
        passcodeView.setState(Passcode.STATE_CONFIRM);
        setNeedFinger(true);
        if (fingerprintView != null) {
          fingerprintView.showDelayed(0);
        }
        checkFingerprintNeeded();
      }
    }
  }

  private void hideFingerprintIcon () {
    setNeedFinger(false);
    contentView.removeView(fingerprintView);
  }

  @Override
  public void onRequestPermissionResult (int requestCode, boolean success) {
    if (requestCode == BaseActivity.REQUEST_USE_FINGERPRINT) {
      if (controllerMode == MODE_SETUP) {
        if (success) {
          if (FingerprintPassword.isAvailable()) {
            setMode(Passcode.MODE_FINGERPRINT);
          } else {
            // UI.showToast(R.string.fingerprint_unavailable, Toast.LENGTH_LONG);
          }
        } else {
          // UI.showToast(R.string.fingerprint_hint, Toast.LENGTH_LONG);
        }
      } else {
        if (!success) {
          // UI.showToast(R.string.fingerprint_hint2, Toast.LENGTH_LONG);
        }
      }
    }
  }

  public void updateFingerprintOrientation () {
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) fingerprintView.getLayoutParams();
    updateFingerprintParams(params, UI.getOrientation());
    fingerprintView.setLayoutParams(params);
  }

  // Other stuff

  @Override
  public boolean inSetupMode () {
    return controllerMode == MODE_SETUP;
  }

  @Override
  public void updateConfirmMode () {
    switch (mode) {
      case Passcode.MODE_PINCODE: {
        pinLayout.setHasFeedback(isPasscodeVisible());
        break;
      }
      case Passcode.MODE_PASSWORD: {
        passwordView.setTransformationMethod(isPasscodeVisible() ? PasswordTransformationMethod.getInstance() : InvisibleTransformationMethod.instance());
        break;
      }
      case Passcode.MODE_GESTURE: {
        gestureView.setGestureVisible(isPasscodeVisible());
        break;
      }
    }
  }

  @Override
  public boolean isPasscodeVisible () {
    if (Config.DISABLE_PASSWORD_INVISIBILITY && mode == Passcode.MODE_PASSWORD)
      return true;
    boolean isVisible;
    if (specificChat != null) {
      isVisible = chatPasscode == null || chatPasscode.isVisible();
    } else {
      isVisible = Passcode.instance().isVisible();
    }
    return isVisible || (inSetupMode() && passcodeView.getState() != Passcode.STATE_CONFIRM);
  }

  private boolean processPasswordSetup (String password) {
    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      if (Passcode.isValidPassword(password) && password.equals(confirmPassword)) {
        setPassword(password);
        Keyboard.hide(passwordView);
        navigateBack();
        return true;
      } else {
        UI.showToast(getMismatchString(Passcode.MODE_PASSWORD), Toast.LENGTH_SHORT);
      }
      return false;
    }

    if (password.length() < Passcode.MIN_PASSWORD_SIZE) {
      UI.showToast(R.string.passcode_password_tooshort, Toast.LENGTH_SHORT);
      return false;
    }

    this.confirmPassword = password;

    passcodeView.setState(Passcode.STATE_CONFIRM);
    passwordView.setText("");

    return false;
  }

  private PasscodeBuilder confirmPasscode;

  private static @StringRes int getInvalidString (int mode) {
    switch (mode) {
      case Passcode.MODE_GESTURE:
        return R.string.PasscodeInvalidGesture;
      case Passcode.MODE_PINCODE:
        return R.string.PasscodeInvalidPin;
      case Passcode.MODE_PASSWORD:
        return R.string.PasscodeInvalidPassword;
      case Passcode.MODE_PATTERN:
        return R.string.PasscodeInvalidPattern;
    }
    throw new IllegalArgumentException("mode == " + mode);
  }

  private static @StringRes int getMismatchString (int mode) {
    switch (mode) {
      case Passcode.MODE_GESTURE:
        return R.string.PasscodeMismatchGesture;
      case Passcode.MODE_PINCODE:
        return R.string.PasscodeMismatchPin;
      case Passcode.MODE_PASSWORD:
        return R.string.PasscodeMismatchPassword;
      case Passcode.MODE_PATTERN:
        return R.string.PasscodeMismatchPattern;
      case Passcode.MODE_FINGERPRINT:
        return R.string.PasscodeMismatchFingerprint;
    }
    throw new IllegalArgumentException("mode == " + mode);
  }

  @Override
  public void handlePattern (final PasscodeBuilder pattern) {
    if (!inSetupMode()) {
      if (Settings.instance().isPasscodeBlocked(Passcode.MODE_PATTERN, makeBruteForceSuffix())) {
        return;
      }
      Background.instance().post(() -> {
        String patternStr = pattern.toString();
        if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().comparePattern(patternStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PATTERN, makeBruteForceSuffix());
          navigateTo(new PasscodeSetupController(context, tdlib));
        } else if (controllerMode == MODE_UNLOCK && unlockByPattern(patternStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PATTERN, makeBruteForceSuffix());
          UI.unlock(PasscodeController.this);
        } else {
          Settings.instance().tracePasscodeError(Passcode.MODE_PATTERN, patternStr, makeBruteForceSuffix());
          UI.showToast(getInvalidString(Passcode.MODE_PATTERN), Toast.LENGTH_SHORT);
        }
      });
      return;
    }

    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      if (pattern.compare(confirmPasscode)) {
        Background.instance().post(() -> {
          String patternStr = pattern.toString();
          if (Passcode.isValidPattern(patternStr)) {
            setPattern(patternStr);
            postNavigateBack();
          } else {
            UI.showToast("Error setting up pattern", Toast.LENGTH_SHORT);
          }
        });
      } else {
        UI.showToast(getMismatchString(Passcode.MODE_PATTERN), Toast.LENGTH_SHORT);
      }
      return;
    }

    if (pattern.getSize() < Passcode.MIN_PATTERN_SIZE) {
      UI.showToast(R.string.passcode_pattern_tooshort, Toast.LENGTH_SHORT);
      return;
    }

    this.confirmPasscode = new PasscodeBuilder(pattern);

    passcodeView.setState(Passcode.STATE_CONFIRM);
  }

  @Override
  public void handlePincode (final PasscodeBuilder pincode) {
    if (!inSetupMode()) {
      if (Settings.instance().isPasscodeBlocked(Passcode.MODE_PINCODE, makeBruteForceSuffix())) {
        return;
      }
      Background.instance().post(() -> {
        String pincodeStr = pincode.toString();
        if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().comparePincode(pincodeStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PINCODE, makeBruteForceSuffix());
          navigateTo(new PasscodeSetupController(context, tdlib));
        } else if (controllerMode == MODE_UNLOCK && unlockByPincode(pincodeStr)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_PINCODE, makeBruteForceSuffix());
          UI.unlock(PasscodeController.this);
        } else {
          Settings.instance().tracePasscodeError(Passcode.MODE_PINCODE, pincodeStr, makeBruteForceSuffix());
          UI.showToast(getInvalidString(Passcode.MODE_PINCODE), Toast.LENGTH_SHORT);
          UI.post(() -> passcodeView.getPincodeOutput().removeAll());
        }
      });
      return;
    }

    if (passcodeView.getState() == Passcode.STATE_CONFIRM) {
      if (pincode.compare(confirmPasscode)) {
        Background.instance().post(() -> {
          String pincodeStr = pincode.toString();
          if (Passcode.isValidPincode(pincodeStr)) {
            setPincode(pincodeStr);
            postNavigateBack();
          } else {
            UI.showToast("Error setting up pincode", Toast.LENGTH_SHORT);
          }
        });
      } else {
        UI.showToast(getMismatchString(Passcode.MODE_PINCODE), Toast.LENGTH_SHORT);
        passcodeView.getPincodeOutput().removeAll();
      }
      return;
    }

    if (pincode.getSize() != Passcode.PINCODE_SIZE) {
      UI.showToast(R.string.passcode_pattern_tooshort, Toast.LENGTH_SHORT);
      return;
    }

    this.confirmPasscode = new PasscodeBuilder(pincode);

    passcodeView.setState(Passcode.STATE_CONFIRM);
    passcodeView.getPincodeOutput().removeAll();
  }

  private void processGesture (final Gesture gesture) {
    if (this.mode == Passcode.MODE_GESTURE) {
      if (inSetupMode()) {
        processGestureSetup(gesture);
        return;
      }
      if (Settings.instance().isPasscodeBlocked(Passcode.MODE_GESTURE, makeBruteForceSuffix())) {
        return;
      }
      Background.instance().post(() -> {
        if (controllerMode == MODE_UNLOCK_SETUP && getGesturePassword().compare(gesture, false, PasscodeController.this)) {
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_GESTURE, makeBruteForceSuffix());
          navigateTo(new PasscodeSetupController(context, tdlib));
        } else if (controllerMode == MODE_UNLOCK && getGesturePassword().compare(gesture, false, PasscodeController.this)) {
          unlockByGesture();
          Settings.instance().forgetPasscodeErrors(Passcode.MODE_GESTURE, makeBruteForceSuffix());
          UI.unlock(PasscodeController.this);
        } else {
          Settings.instance().tracePasscodeError(Passcode.MODE_GESTURE, null, makeBruteForceSuffix());
          UI.showToast(getInvalidString(Passcode.MODE_GESTURE), Toast.LENGTH_SHORT);
        }
      });
    }
  }

  private boolean processPasscode () {
    switch (this.mode) {
      case Passcode.MODE_PINCODE:
      case Passcode.MODE_PATTERN:
      case Passcode.MODE_GESTURE: {
        UI.showToast(passcodeView.getText(), Toast.LENGTH_SHORT);
        break;
      }
      case Passcode.MODE_PASSWORD: {
        final String password = passwordView.getText().toString();

        if (inSetupMode()) {
          return processPasswordSetup(password);
        }

        if (Settings.instance().isPasscodeBlocked(Passcode.MODE_PASSWORD, makeBruteForceSuffix())) {
          return false;
        }

        Background.instance().post(() -> {
          if (controllerMode == MODE_UNLOCK_SETUP && Passcode.instance().comparePassword(password)) {
            Settings.instance().forgetPasscodeErrors(Passcode.MODE_PASSWORD, makeBruteForceSuffix());
            navigateTo(new PasscodeSetupController(context, tdlib));
          } else if (controllerMode == MODE_UNLOCK && unlockByPassword(password)) {
            Settings.instance().forgetPasscodeErrors(Passcode.MODE_PASSWORD, makeBruteForceSuffix());
            UI.unlock(PasscodeController.this);
          } else {
            Settings.instance().tracePasscodeError(Passcode.MODE_PASSWORD, password, makeBruteForceSuffix());
            UI.showToast(getInvalidString(Passcode.MODE_PASSWORD), Toast.LENGTH_SHORT);
          }
        });

        break;
      }
    }
    return false;
  }

  public void unlockInterface () {
    if (mode == Passcode.MODE_PASSWORD) {
      Keyboard.hide(passwordView);
    }
    if (specificChat != null) {
      tdlib.ui().openChat(this, specificChat, (chatOpenParameters != null ? chatOpenParameters : new TdlibUi.ChatOpenParameters()).passcodeUnlocked());
    } else {
      context().hidePasscode();
    }
  }

  @Override
  public int getRootColorId () {
    return ColorId.passcode;
  }

  @Override
  public void unlock () {
    unlockInterface();
  }

  @Override
  protected int getMenuId () {
    return 0; // controllerMode == MODE_UNLOCK || (controllerMode == MODE_UNLOCK_SETUP && mode != Passcode.MODE_PASSWORD) ? 0 : R.id.menu_done;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    header.addDoneButton(menu, this);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_done) {
      processPasscode();
    }
  }

  @Override
  public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
    return actionId == EditorInfo.IME_ACTION_DONE && !processPasscode();
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return !passcodeView.isIntercepted() && mode != Passcode.MODE_GESTURE;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public void onConfigurationChanged (Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    passcodeView.setOrientation(newConfig.orientation);
    switch (mode) {
      case Passcode.MODE_PINCODE: {
        updatePincodeOrientation();
        break;
      }
      case Passcode.MODE_FINGERPRINT: {
        updateFingerprintOrientation();
        break;
      }
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (mode == Passcode.MODE_PASSWORD) {
      UI.showKeyboardDelayed(passwordView);
    }
    int fingerprintDelay = controllerMode == MODE_UNLOCK_SETUP || specificChat != null ? 0 : controllerMode == MODE_UNLOCK ? 300 : 100;
    if (swirlView != null) {
      swirlView.showDelayed(fingerprintDelay);
    }
    if (fingerprintView != null && controllerMode != MODE_SETUP) {
      fingerprintView.showDelayed(fingerprintDelay);
    }
  }

  @Override
  public void onAuthenticationError (String error, boolean isFatal) {
    UI.showToast(error, Toast.LENGTH_SHORT); // TODO better UI
    if (swirlView != null) {
      swirlView.showError(isFatal);
    }
    if (fingerprintView != null) {
      fingerprintView.showError(isFatal);
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    if (mode == Passcode.MODE_PASSWORD) {
      Keyboard.hide(passwordView);
    }
  }

  // Toggler

  private View headerCell;

  private void updateHeaderText () {
    if (headerCell instanceof ToggleHeaderView) {
      ((ToggleHeaderView) headerCell).setText(getName());
    } else if (headerCell instanceof DoubleHeaderView) {
      ((DoubleHeaderView) headerCell).setTitle(getName());
    }
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.passcodeText;
  }

  @Override
  public View getCustomHeaderCell () {
    if (controllerMode == MODE_UNLOCK && specificChat != null) {
      if (headerCell == null) {
        DoubleHeaderView headerCell = new DoubleHeaderView(context);
        headerCell.setThemedTextColor(this);
        headerCell.initWithMargin(Screen.dp(49f), true);
        headerCell.setSubtitle(Lang.getStringBold(R.string.SecretChatWithUser, tdlib.chatTitle(specificChat)));
        this.headerCell = headerCell;
      }
    } else if (controllerMode == MODE_SETUP && !inFingerprintSetup) {
      if (headerCell == null) {
        headerCell = this.context.navigation().getHeaderView().genToggleTitle(context(), this);
      }
    } else {
      return null;
    }
    updateHeaderText();
    return headerCell;
  }

  @Override
  public void onToggle (int section) {
    setMode(section + 1);
  }

  private boolean needFinger;

  private void setNeedFinger (boolean need) {
    if (this.needFinger != need) {
      this.needFinger = need;
      checkFingerprintNeeded();
    }
  }

  private boolean fingerUsed;

  private void checkFingerprintNeeded () {
    boolean need = this.needFinger && context.getActivityState() == UI.STATE_RESUMED;
    if (fingerUsed != need) {
      if (need) {
        FingerprintPassword.authenticate(this);
      } else {
        FingerprintPassword.cancelAuthentication();
      }
      fingerUsed = need;
    }
  }

  @Override
  public void onActivityStateChanged (BaseActivity activity, int newState, int prevState) {
    checkFingerprintNeeded();
  }

  @Override
  public void destroy () {
    setNeedFinger(false);
    context.removeSimpleStateListener(this);
    Settings.instance().removePasscodeTickListener(this);
    super.destroy();
  }

  private String[] sections;

  @Override
  public String[] getToggleSections () {
    if (sections == null) {
      StringList strings;
      boolean fingerprintAvailable = FingerprintPassword.isAvailable();
      strings = new StringList(fingerprintAvailable ? 5 : 4);
      strings.append(R.string.PasscodePIN);
      strings.append(R.string.login_Password);
      strings.append(R.string.PasscodePattern);
      strings.append(R.string.PasscodeGesture);
      if (fingerprintAvailable) {
        strings.append(R.string.PasscodeFingerprint);
      }
      sections = strings.get();
    }
    return sections;
  }

  // Settings

  private int getPasscodeMode () {
    if (specificChat != null) {
      return chatPasscode != null ? chatPasscode.mode : Passcode.MODE_NONE;
    } else {
      return Passcode.instance().getMode();
    }
  }

  private boolean isPasscodeEnabled () {
    if (specificChat != null) {
      return chatPasscode != null && chatPasscode.mode != Passcode.MODE_NONE;
    } else {
      return Passcode.instance().isEnabled();
    }
  }

  private boolean needUnlockByFingerprint () {
    if (specificChat != null) {
      return chatPasscode != null && chatPasscode.mode != Passcode.MODE_FINGERPRINT && !StringUtils.isEmpty(chatPasscode.fingerHash);
    } else {
      return Passcode.instance().needUnlockByFingerprint();
    }
  }

  private boolean unlockByFinger (int fingerId) {
    if (specificChat != null) {
      String hash = Passcode.getPasscodeHash(String.valueOf(fingerId));
      if (chatPasscode.mode == Passcode.MODE_FINGERPRINT) {
        return chatPasscode.hash.equals(hash);
      } else {
        return chatPasscode.fingerHash != null && chatPasscode.fingerHash.equals(hash);
      }
    } else {
      return Passcode.instance().unlockByFinger(fingerId);
    }
  }

  private void enableUnlockByFingerprint (int fingerId) {
    if (specificChat != null) {
      chatPasscode.fingerHash = Passcode.getPasscodeHash(String.valueOf(fingerId));
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().enableUnlockByFingerprint(fingerId);
    }
  }

  private void setFingerprint (int fingerId) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(String.valueOf(fingerId));
      chatPasscode.mode = Passcode.MODE_FINGERPRINT;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setFingerprint(fingerId);
    }
  }

  private void setPassword (String password) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(password);
      chatPasscode.mode = Passcode.MODE_PASSWORD;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setPassword(password);
    }
  }

  private boolean unlockByPattern (String pattern) {
    if (specificChat != null) {
      return chatPasscode.mode == Passcode.MODE_PATTERN && chatPasscode.hash.equals(Passcode.getPasscodeHash(pattern));
    } else {
      return Passcode.instance().unlockByPattern(pattern);
    }
  }

  private void setPattern (String pattern) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(pattern);
      chatPasscode.mode = Passcode.MODE_PATTERN;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setPattern(pattern);
    }
  }

  private boolean unlockByPincode (String pincode) {
    if (specificChat != null) {
      return chatPasscode.mode == Passcode.MODE_PINCODE && chatPasscode.hash.equals(Passcode.getPasscodeHash(pincode));
    } else {
      return Passcode.instance().unlockByPincode(pincode);
    }
  }

  private void setPincode (String pincode) {
    if (specificChat != null) {
      chatPasscode.hash = Passcode.getPasscodeHash(pincode);
      chatPasscode.mode = Passcode.MODE_PINCODE;
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setPincode(pincode);
    }
  }

  private boolean unlockByPassword (String password) {
    if (specificChat != null) {
      return chatPasscode.mode == Passcode.MODE_PASSWORD && chatPasscode.hash.equals(Passcode.getPasscodeHash(password));
    } else {
      return Passcode.instance().unlockByPassword(password);
    }
  }

  private GesturePassword gesturePassword;

  private GesturePassword getGesturePassword () {
    if (gesturePassword == null) {
      String suffix;
      if (specificChat != null) {
        suffix = tdlib.uniqueSuffix() + "." + specificChat.id;
      } else {
        suffix = null;
      }
      gesturePassword = new GesturePassword(suffix);
    }
    return gesturePassword;
  }

  private void setGesture () {
    if (specificChat != null) {
      chatPasscode.mode = Passcode.MODE_GESTURE;
      chatPasscode.hash = "";
      tdlib.setPasscode(specificChat, chatPasscode);
    } else {
      Passcode.instance().setGesture();
    }
  }

  private void unlockByGesture () {
    if (specificChat != null) {
      // Do nothing?
    } else {
      Passcode.instance().unlock();
    }
  }

  private String uniqueSuffix;

  @Override
  public @Nullable String makeBruteForceSuffix () {
    return specificChat != null ? (uniqueSuffix != null ? uniqueSuffix : (uniqueSuffix = tdlib.uniqueSuffix(specificChat.id))) : null;
  }

  @Override
  public void onPasscodeTick (String suffix) {
    if (StringUtils.equalsOrBothEmpty(suffix, makeBruteForceSuffix())) {
      if (passcodeView != null) {
        passcodeView.updateTextAndInvalidate();
      }
    }
  }
}
