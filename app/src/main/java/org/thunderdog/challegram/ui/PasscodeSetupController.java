/**
 * File created on 06/08/15 at 19:53
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.FingerprintPassword;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableInt;

public class PasscodeSetupController extends ViewController<PasscodeSetupController.Args> implements View.OnClickListener, Runnable {
  public static class Args {
    public final TdApi.Chat chat;
    public final Tdlib.ChatPasscode passcode;

    public Args (TdApi.Chat chat, Tdlib.ChatPasscode passcode) {
      this.chat = chat;
      this.passcode = passcode;
    }
  }

  private ScrollView contentView;
  private LinearLayout autoLockWrap;

  private SettingView passcodeView;
  private SettingView changeView;
  private @Nullable SettingView autoLockView;
  private SettingView visibilityView;
  private @Nullable SeparatorView visibilitySeparator;
  private @Nullable SettingView screenshotView, notificationsView;
  private SettingView fingerprintView;
  private SeparatorView fingerprintSeparator;
  private ShadowView topShadowView, bottomShadowView;

  public PasscodeSetupController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.PasscodeTitle);
  }

  @Override
  public int getId () {
    return R.id.controller_passcodeSetup;
  }

  private static TextView newDescription (ViewController<?> context) {
    TextView textView = new NoScrollTextView(context.context());
    textView.setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
    textView.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(16f), Screen.dp(12f));
    textView.setTypeface(Fonts.getRobotoRegular());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    textView.setTextColor(Theme.textDecent2Color());
    context.addThemeTextColorListener(textView, R.id.theme_color_background_textLight);
    return textView;
  }

  private void checkItemsAvailability () {
    boolean isAvailable = getPasscodeMode() != Passcode.MODE_FINGERPRINT && FingerprintPassword.isAvailable();
    fingerprintSeparator.setVisibility(isAvailable ? View.VISIBLE : View.GONE);
    fingerprintView.setVisibility(isAvailable ? View.VISIBLE : View.GONE);
    boolean canChangeVisibility = getPasscodeMode() != Passcode.MODE_FINGERPRINT;
    visibilityView.setVisibility(canChangeVisibility ? View.VISIBLE : View.GONE);
    if (visibilitySeparator != null) {
      visibilitySeparator.setVisibility(canChangeVisibility ? View.VISIBLE : View.GONE);
    }
    if (specificChat != null) {
      int shadowVisibility = isAvailable || canChangeVisibility ? View.VISIBLE : View.GONE;
      if (topShadowView != null) {
        topShadowView.setVisibility(shadowVisibility);
      }
      if (bottomShadowView != null) {
        bottomShadowView.setVisibility(shadowVisibility);
      }
    }
  }

  private TdApi.Chat specificChat;
  private Tdlib.ChatPasscode chatPasscode;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.specificChat = args.chat;
    this.chatPasscode = args.passcode;
  }

  private DoubleHeaderView headerCell;
  private LinearLayout settingsList;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected View onCreateView (Context context) {
    if (specificChat != null) {
      headerCell = new DoubleHeaderView(context);
      headerCell.setThemedTextColor(this);
      headerCell.initWithMargin(Screen.dp(49f), true);
      headerCell.setTitle(getName());
      headerCell.setSubtitle(Lang.getStringBold(R.string.SecretChatWithUser, tdlib.chatTitle(specificChat)));
    }

    contentView = new ScrollView(context);

    settingsList = new LinearLayout(context);
    settingsList.setOrientation(LinearLayout.VERTICAL);

    passcodeView = new SettingView(context, tdlib);
    passcodeView.setId(R.id.btn_passcode);
    passcodeView.setType(SettingView.TYPE_RADIO);
    passcodeView.getToggler().setRadioEnabled(isPasscodeEnabled(), false);
    passcodeView.setName(R.string.PasscodeItem);
    passcodeView.setOnClickListener(this);
    passcodeView.addThemeListeners(this);
    settingsList.addView(passcodeView);

    SeparatorView view = SeparatorView.simpleSeparator(context, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f)), true);
    addThemeInvalidateListener(view);
    settingsList.addView(view);

    changeView = new SettingView(context, tdlib);
    changeView.setId(R.id.btn_passcode_change);
    changeView.setType(SettingView.TYPE_SETTING);
    changeView.setName(R.string.ChangePasscode);
    changeView.setOnClickListener(this);
    changeView.addThemeListeners(this);
    settingsList.addView(changeView);

    ShadowView shadow;
    shadow = new ShadowView(context);
    addThemeInvalidateListener(shadow);
    shadow.setSimpleBottomTransparentShadow(true);
    settingsList.addView(shadow);

    TextView textView;
    textView = newDescription(this);
    if (specificChat != null) {
      textView.setText(Lang.getStringBold(R.string.SecretPasscodeInfo, tdlib.chatTitle(specificChat)));
    } else {
      textView.setText(Lang.getString(R.string.ChangePasscodeInfo));
    }
    settingsList.addView(textView);

    // Autolock wrap

    autoLockWrap = new LinearLayout(context);
    autoLockWrap.setOrientation(LinearLayout.VERTICAL);

    shadow = new ShadowView(context);
    addThemeInvalidateListener(shadow);
    shadow.setSimpleTopShadow(true, this);
    autoLockWrap.addView(shadow);

    if (specificChat != null) {
      topShadowView = shadow;
    }

    // Fingerprint

    fingerprintView = new SettingView(context, tdlib);
    fingerprintView.setId(R.id.btn_fingerprint);
    fingerprintView.setType(SettingView.TYPE_RADIO);
    fingerprintView.setName(R.string.passcode_fingerprint);
    fingerprintView.getToggler().setRadioEnabled(needUnlockByFingerprint(), false);
    fingerprintView.setOnClickListener(this);
    fingerprintView.addThemeListeners(this);
    autoLockWrap.addView(fingerprintView);

    fingerprintSeparator = SeparatorView.simpleSeparator(context, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f)), true);
    addThemeInvalidateListener(fingerprintSeparator);
    autoLockWrap.addView(fingerprintSeparator);

    // Visibility

    visibilityView = new SettingView(context, tdlib);
    visibilityView.setId(R.id.btn_pattern);
    visibilityView.setType(SettingView.TYPE_RADIO);
    visibilityView.setName(R.string.passcode_passcodeInvisibility);
    visibilityView.getToggler().setRadioEnabled(!isPasscodeVisible(), false);
    visibilityView.setOnClickListener(this);
    visibilityView.addThemeListeners(this);
    autoLockWrap.addView(visibilityView);

    if (specificChat == null) {
      visibilitySeparator = SeparatorView.simpleSeparator(context, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(1f)), true);
      addThemeInvalidateListener(visibilitySeparator);
      autoLockWrap.addView(visibilitySeparator);

      // Autolock

      autoLockView = new SettingView(context, tdlib);
      autoLockView.setId(R.id.btn_passcode_auto);
      autoLockView.setType(SettingView.TYPE_INFO);
      autoLockView.setSwapDataAndName();
      autoLockView.setName(R.string.AutoLock);
      updateAutoLockInfo();
      autoLockView.setOnClickListener(this);
      autoLockView.addThemeListeners(this);
      autoLockWrap.addView(autoLockView);

      shadow = new ShadowView(context);
      addThemeInvalidateListener(shadow);
      shadow.setSimpleBottomTransparentShadow(true);
      autoLockWrap.addView(shadow);

      textView = newDescription(this);
      textView.setText(Lang.getString(R.string.passcode_auto_hint));
      autoLockWrap.addView(textView);

      shadow = new ShadowView(context);
      addThemeInvalidateListener(shadow);
      shadow.setSimpleTopShadow(true, this);
      autoLockWrap.addView(shadow);

      notificationsView = new SettingView(context, tdlib);
      notificationsView.setId(R.id.btn_notificationContent);
      notificationsView.setType(SettingView.TYPE_RADIO);
      notificationsView.setName(R.string.AllowNotifications);
      notificationsView.getToggler().setRadioEnabled(Passcode.instance().displayNotifications(), false);
      notificationsView.setOnClickListener(this);
      notificationsView.addThemeListeners(this);
      autoLockWrap.addView(notificationsView);

      shadow = new ShadowView(context);
      addThemeInvalidateListener(shadow);
      shadow.setSimpleBottomTransparentShadow(true);
      autoLockWrap.addView(shadow);

      textView = newDescription(this);
      textView.setText(Lang.getString(R.string.AllowNotificationsInfo));
      autoLockWrap.addView(textView);

      shadow = new ShadowView(context);
      addThemeInvalidateListener(shadow);
      shadow.setSimpleTopShadow(true, this);
      autoLockWrap.addView(shadow);

      screenshotView = new SettingView(context, tdlib);
      screenshotView.setId(R.id.btn_screenCapture);
      screenshotView.setType(SettingView.TYPE_RADIO);
      screenshotView.setName(R.string.ScreenCapture);
      screenshotView.getToggler().setRadioEnabled(Passcode.instance().allowScreenshots(), false);
      screenshotView.setOnClickListener(this);
      screenshotView.addThemeListeners(this);
      autoLockWrap.addView(screenshotView);


      shadow = new ShadowView(context);
      addThemeInvalidateListener(shadow);
      shadow.setSimpleBottomTransparentShadow(true);
      autoLockWrap.addView(shadow);

      textView = newDescription(this);
      textView.setText(Lang.getString(R.string.ScreenCaptureInfo));
      autoLockWrap.addView(textView);
    } else {
      shadow = new ShadowView(context);
      addThemeInvalidateListener(shadow);
      shadow.setSimpleBottomTransparentShadow(true);
      autoLockWrap.addView(shadow);
      bottomShadowView = shadow;
    }

    // Setup

    checkItemsAvailability();

    updateEnabledStatus(false);

    settingsList.addView(autoLockWrap);
    contentView.addView(settingsList);

    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(wrapper, R.id.theme_color_background, this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrapper.addView(contentView);

    return wrapper;
  }

  private void updateAutolockVisibility (boolean animated) {
    boolean isEnabled = isPasscodeEnabled();

    if (!animated) {
      autoLockWrap.setAlpha(1f);
      autoLockWrap.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
      return;
    }

    if (isEnabled) {
      autoLockWrap.setAlpha(0f);
      autoLockWrap.setVisibility(View.VISIBLE);
    }

    Views.animateAlpha(autoLockWrap, isEnabled ? 1f : 0f, 150l, AnimatorUtils.DECELERATE_INTERPOLATOR, isEnabled ? null : new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        autoLockWrap.setVisibility(View.INVISIBLE);
      }
    });
  }

  private void updateAutoLockInfo () {
    if (autoLockView != null) {
      if (autoLockOptions == null) {
        autoLockOptions = Passcode.instance().getAutolockModeNames();
      }
      autoLockView.setData(autoLockOptions[Passcode.instance().getAutolockMode()]);
    }
  }

  private boolean lastEnabledStatus;

  public void updateEnabledStatus (boolean animated) {
    lastEnabledStatus = isPasscodeEnabled();
    passcodeView.getToggler().setRadioEnabled(lastEnabledStatus, animated);
    if (animated) {
      changeView.setEnabledAnimated(lastEnabledStatus);
    } else {
      changeView.setEnabled(lastEnabledStatus);
    }
    updateAutolockVisibility(animated);
  }

  private boolean removedPasscodeItem;

  @Override
  public void onFocus () {
    super.onFocus();
    if (specificChat != null) {
      chatPasscode = tdlib.chatPasscode(specificChat);
    }
    fingerprintView.getToggler().setRadioEnabled(needUnlockByFingerprint(), fingerprintView.getVisibility() == View.VISIBLE && isFocused());
    if (!removedPasscodeItem && isAttachedToNavigationController()) {
      removedPasscodeItem = true;
      if (isPasscodeEnabled()) {
        int index = stackSize() - 2;
        ViewController<?> c = stackItemAt(index);
        if (c instanceof PasscodeController) {
          destroyStackItemAt(index);
        }
      }
    }
    if (lastEnabledStatus != isPasscodeEnabled()) {
      UI.post(this, 150l);
    }
  }

  @Override
  public void run () {
    updateEnabledStatus(true);
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    checkItemsAvailability();
  }

  private void showPasscodeOptions () {
    showPasscodeOptions(this, null, mode -> {
      PasscodeController c = new PasscodeController(context, tdlib);
      if (specificChat != null) {
        c.setArguments(new PasscodeController.Args(specificChat, chatPasscode, null));
      }
      c.setPasscodeMode(PasscodeController.MODE_SETUP);
      c.forceMode(mode);
      navigateTo(c);
    });
  }

  public static void showPasscodeOptions (ViewController<?> context, CharSequence info, RunnableInt callback) {
    boolean fingerprintAvailable = FingerprintPassword.isAvailable();
    int size = fingerprintAvailable ? 5 : 4;
    IntList ids = new IntList(size);
    StringList strings = new StringList(size);
    IntList icons = new IntList(size);

    ids.append(R.id.btn_passcodeType_pin);
    strings.append(R.string.PasscodePIN);
    icons.append(R.drawable.vkryl_baseline_lock_pin_24);

    ids.append(R.id.btn_passcodeType_password);
    strings.append(R.string.login_Password);
    icons.append(R.drawable.mrgrigri_baseline_textbox_password_24);

    ids.append(R.id.btn_passcodeType_pattern);
    strings.append(R.string.PasscodePattern);
    icons.append(R.drawable.itsspelledhaley_baseline_lock_pattern_24);

    ids.append(R.id.btn_passcodeType_gesture);
    strings.append(R.string.PasscodeGesture);
    icons.append(R.drawable.baseline_gesture_24);

    if (fingerprintAvailable) {
      ids.append(R.id.btn_passcodeType_fingerprint);
      strings.append(R.string.PasscodeFingerprint);
      icons.append(R.drawable.baseline_fingerprint_24);
    }

    context.showOptions(info, ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
      int mode;
      switch (id) {
        case R.id.btn_passcodeType_pin:
          mode = Passcode.MODE_PINCODE;
          break;
        case R.id.btn_passcodeType_password:
          mode = Passcode.MODE_PASSWORD;
          break;
        case R.id.btn_passcodeType_pattern:
          mode = Passcode.MODE_PATTERN;
          break;
        case R.id.btn_passcodeType_gesture:
          mode = Passcode.MODE_GESTURE;
          break;
        case R.id.btn_passcodeType_fingerprint:
          mode = Passcode.MODE_FINGERPRINT;
          break;
        default:
          mode = 0;
          break;
      }
      if (mode == 0) {
        return true;
      }
      if (mode == Passcode.MODE_FINGERPRINT && (!FingerprintPassword.isAvailable() || !FingerprintPassword.hasFingerprints())) {
        UI.showToast(R.string.fingerprint_hint3, Toast.LENGTH_SHORT);
        return true;
      }
      callback.runWithInt(mode);
      return true;
    });
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_passcode: {
        boolean enabled = !isPasscodeEnabled();

        if (enabled) {
          showPasscodeOptions();
        } else {
          disablePasscode();
          updateEnabledStatus(true);
        }

        break;
      }
      case R.id.btn_passcode_change: {
        if (isPasscodeEnabled()) {
          showPasscodeOptions();
        }
        break;
      }
      case R.id.btn_fingerprint: {
        if (fingerprintView.getToggler().isEnabled()) {
          fingerprintView.toggleRadio();
          disableUnlockByFingerprint();
        } else if (!FingerprintPassword.isAvailable() || !FingerprintPassword.hasFingerprints()) {
          UI.showToast(R.string.fingerprint_hint3, Toast.LENGTH_SHORT);
        } else {
          PasscodeController c = new PasscodeController(context, tdlib);
          if (specificChat != null) {
            c.setArguments(new PasscodeController.Args(specificChat, chatPasscode, null));
          }
          c.setPasscodeMode(PasscodeController.MODE_SETUP);
          c.setInFingerprintSetup();
          navigateTo(c);
        }
        break;
      }
      case R.id.btn_pattern: {
        setPasscodeVisible(!visibilityView.toggleRadio());
        break;
      }
      case R.id.btn_notificationContent: {
        if (notificationsView != null) {
          Passcode.instance().setDisplayNotifications(notificationsView.toggleRadio());
          TdlibManager.instance().onUpdateNotifications(null);
        }
        break;
      }
      case R.id.btn_screenCapture: {
        if (screenshotView != null) {
          Passcode.instance().setAllowScreenshots(screenshotView.toggleRadio());
          UI.checkDisallowScreenshots();
        }
        break;
      }
      case R.id.btn_passcode_auto: {
        showAutoLockOptions();
        break;
      }
    }
  }

  private String[] autoLockOptions;

  private void showAutoLockOptions () {
    if (autoLockOptions == null) {
      autoLockOptions = Passcode.instance().getAutolockModeNames();
    }
    IntList ids = new IntList(autoLockOptions.length);
    for (int i = 0; i < autoLockOptions.length; i++) {
      ids.append(i);
    }
    showOptions(ids.get(), autoLockOptions, (itemView, id) -> {
      if (!isDestroyed()) {
        Passcode.instance().setAutolockMode(id);
        updateAutoLockInfo();
      }
      return true;
    });
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return contentView;
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
      return chatPasscode != null && !StringUtils.isEmpty(chatPasscode.fingerHash);
    } else {
      return Passcode.instance().needUnlockByFingerprint();
    }
  }

  private boolean isPasscodeVisible () {
    if (specificChat != null) {
      return chatPasscode == null || chatPasscode.isVisible();
    } else {
      return Passcode.instance().isVisible();
    }
  }

  private void disablePasscode () {
    if (specificChat != null) {
      chatPasscode = null;
      tdlib.setPasscode(specificChat, null);
    } else {
      Passcode.instance().disable();
    }
  }

  private void disableUnlockByFingerprint () {
    if (specificChat != null) {
      if (chatPasscode != null) {
        chatPasscode.fingerHash = null;
        tdlib.setPasscode(specificChat, chatPasscode);
      }
    } else {
      Passcode.instance().disableUnlockByFingerprint();
    }
  }

  private void setPasscodeVisible (boolean isVisible) {
    if (specificChat != null) {
      if (chatPasscode != null) {
        chatPasscode.setIsVisible(isVisible);
        tdlib.setPasscode(specificChat, chatPasscode);
      }
    } else {
      Passcode.instance().setVisible(isVisible);
    }
  }

  private static void updateRtl (ViewGroup viewGroup) {
    if (viewGroup != null) {
      int childCount = viewGroup.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = viewGroup.getChildAt(i);
        if (view instanceof TextView) {
          ((TextView) view).setGravity(Lang.gravity() | Gravity.CENTER_VERTICAL);
        } else if (view instanceof SettingView) {
          ((SettingView) view).checkRtl(true);
          view.invalidate();
        }
      }
    }
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    updateRtl(settingsList);
    updateRtl(autoLockWrap);
  }
}
