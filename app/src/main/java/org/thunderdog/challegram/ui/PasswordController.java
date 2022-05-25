package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.AuthorizationListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ProgressComponentView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

/**
 * Date: 18/11/2016
 * Author: default
 */

public class PasswordController extends ViewController<PasswordController.Args> implements View.OnClickListener, FactorAnimator.Target, MaterialEditTextGroup.EmptyListener, MaterialEditTextGroup.DoneListener, MaterialEditTextGroup.TextChangeListener, AuthorizationListener {
  public static final int MODE_EDIT = 0;
  public static final int MODE_NEW = 1;
  public static final int MODE_UNLOCK_EDIT = 2;
  public static final int MODE_EMAIL_RECOVERY = 3;
  public static final int MODE_EMAIL_CHANGE = 4;
  public static final int MODE_LOGIN = 5;
  public static final int MODE_LOGIN_EMAIL_RECOVERY = 6;
  public static final int MODE_CODE = 7;
  public static final int MODE_CODE_CHANGE = 8;
  public static final int MODE_CODE_PHONE_CONFIRM = 9;
  public static final int MODE_TRANSFER_OWNERSHIP_CONFIRM = 10;
  public static final int MODE_CONFIRM = 11;

  public static class Args {
    public final int mode;
    public final TdApi.PasswordState state;
    public final TdApi.AuthorizationState authState;
    public @Nullable String phoneNumber;

    public Args (int mode, TdApi.PasswordState state) {
      this.mode = mode;
      this.state = state;
      this.authState = null;
    }

    public Args (int mode, TdApi.AuthorizationStateWaitPassword state) {
      this.mode = mode;
      this.state = null;
      this.authState = state;
    }

    public Args (int mode, TdApi.AuthorizationStateWaitCode state, @NonNull String phoneNumber) {
      this.mode = mode;
      this.state = null;
      this.authState = state;
      this.phoneNumber = phoneNumber;
    }

    public Args (int mode, TdApi.AuthenticationCodeInfo codeInfo, @NonNull String phoneNumber) {
      this.mode = mode;
      this.state = null;
      this.authState = new TdApi.AuthorizationStateWaitCode(codeInfo);
      this.phoneNumber = phoneNumber;
    }

    public @Nullable String email;

    public Args setEmail (@Nullable String email) {
      this.email = email;
      return this;
    }

    public @Nullable String oldPassword;

    public Args setOldPassword (@Nullable String oldPassword) {
      this.oldPassword = oldPassword;
      return this;
    }

    public int codeLength;

    public Args setCodeLength (int length) {
      this.codeLength = length;
      return this;
    }

    public @Nullable String hash;

    public Args setHash (@Nullable String hash) {
      this.hash = hash;
      return this;
    }

    public @Nullable RunnableData<String> onSuccessListener;

    public Args setSuccessListener (@Nullable RunnableData<String> onSuccessListener) {
      this.onSuccessListener = onSuccessListener;
      return this;
    }
  }

  private int mode;

  private @Nullable TdApi.PasswordState state;
  private @Nullable TdApi.AuthorizationState authState;
  private String formattedPhone;

  public PasswordController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean isUnauthorized () {
    switch (mode) {
      case MODE_LOGIN_EMAIL_RECOVERY:
      case MODE_LOGIN:
      case MODE_CODE:
        return true;
    }
    return false;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.state = args.state;
    this.authState = args.authState;
    this.formattedPhone = args.phoneNumber;
  }

  @Override
  public CharSequence getName () {
    switch (mode) {
      case MODE_EDIT: {
        return Lang.getString(R.string.ChangePassword);
      }
      case MODE_NEW: {
        return Lang.getString(R.string.YourPassword);
      }
      case MODE_CONFIRM:
      case MODE_UNLOCK_EDIT: {
        return Lang.getString(R.string.EnterPassword);
      }
      case MODE_TRANSFER_OWNERSHIP_CONFIRM: {
        return Lang.getString(R.string.TransferOwnershipPasswordAlert);
      }
      case MODE_LOGIN: {
        return Lang.getString(R.string.TwoStepVerification);
      }
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        return Lang.getString(R.string.PasswordRecovery);
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE: {
        return Lang.getString(R.string.ConfirmationCode);
      }
      case MODE_CODE_PHONE_CONFIRM: {
        return Lang.getString(R.string.CancelAccountReset);
      }
      case MODE_EMAIL_CHANGE: {
        return Lang.getString(R.string.ChangeRecoveryEmail);
      }
    }
    return null; // UI.getString(mode == MODE_EMAIL_RECOVERY ? R.string.PasswordRecovery : mode == MODE_UNLOCK_EDIT ? R.string.EnterPassword : R.string.YourPassword);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public int getId () {
    return mode == MODE_CODE || mode == MODE_CODE_CHANGE || mode == MODE_CODE_PHONE_CONFIRM ? R.id.controller_code : mode == MODE_EMAIL_RECOVERY ? R.id.controller_passwordRecovery : mode == MODE_LOGIN_EMAIL_RECOVERY ? R.id.controller_loginPassword : R.id.controller_password;
  }

  private MaterialEditTextGroup editText;
  private CircleButton nextButton;
  private TextView forgotView;
  private @Nullable ProgressComponentView progressView;
  private TextView hintView;

  private int getDoneIcon () {
    switch (mode) {
      case MODE_EMAIL_CHANGE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM:
      case MODE_CONFIRM:
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
        return R.drawable.baseline_check_24;
    }
    return R.drawable.baseline_arrow_forward_24;
  }

  @Override
  protected View onCreateView (Context context) {
    final FrameLayoutFix contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_filling, this);

    final int topMargin = (Screen.smallestActualSide() - HeaderView.getSize(false) - Screen.dp(175f)) / 2;

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP);
    params.topMargin = topMargin;
    params.leftMargin = Screen.dp(16f);
    params.rightMargin = Screen.dp(16f);

    editText = new MaterialEditTextGroup(context);
    editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
    editText.addThemeListeners(this);
    editText.setDoneListener(this);
    editText.setEmptyListener(this);
    editText.setTextListener(this);
    switch (mode) {
      case MODE_EMAIL_CHANGE: {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        break;
      }
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY:
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM: {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        break;
      }
      default: {
        // editText.getEditText().setInputType(InputType.TYPE_PASSWORD);
        // editText.getEditText().setIsPassword();
        editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.getEditText().setIsPassword(true);
        break;
      }
    }

    switch (mode) {
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        editText.setHint(R.string.EnterCode);
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM: {
        editText.setHint(R.string.login_Code);
        break;
      }
      case MODE_EDIT: {
        editText.setHint(R.string.EnterANewPassword);
        break;
      }
      case MODE_EMAIL_CHANGE: {
        editText.setHint(R.string.EnterANewEmail);
        break;
      }
      case MODE_NEW: {
        editText.setHint(R.string.EnterAPassword);
        break;
      }
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_CONFIRM:
      case MODE_UNLOCK_EDIT: {
        if (state != null && state.passwordHint != null && !state.passwordHint.isEmpty()) {
          editText.setHint(Lang.getString(R.string.Hint, state.passwordHint));
        } else {
          editText.setHint(R.string.EnterAPassword);
        }
        break;
      }
      case MODE_LOGIN: {
        if (authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR && !StringUtils.isEmpty(((TdApi.AuthorizationStateWaitPassword) authState).passwordHint)) {
          editText.setHint(Lang.getString(R.string.Hint, ((TdApi.AuthorizationStateWaitPassword) authState).passwordHint));
        } else {
          editText.setHint(R.string.EnterAPassword);
        }
        break;
      }
    }
    editText.setLayoutParams(params);
    contentView.addView(editText);

    int padding = Screen.dp(4f);
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.bottomMargin = Screen.dp(16f) - padding;

    nextButton = new CircleButton(context);
    addThemeInvalidateListener(nextButton);
    nextButton.setId(R.id.btn_done);
    nextButton.init(getDoneIcon(), 56f, 4f, R.id.theme_color_circleButtonRegular, R.id.theme_color_circleButtonRegularIcon);
    nextButton.setOnClickListener(this);
    nextButton.setLayoutParams(params);
    nextButton.setAlpha(0f);
    nextButton.setScaleX(.6f);
    nextButton.setScaleY(.6f);
    contentView.addView(nextButton);

    CharSequence hint = null;

    forgotView = new NoScrollTextView(context);
    forgotView.setId(R.id.btn_forgotPassword);
    forgotView.setTextColor(Theme.getColor(R.id.theme_color_textNeutral));
    addThemeTextColorListener(forgotView, R.id.theme_color_textNeutral);
    forgotView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    forgotView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
    forgotView.setOnClickListener(this);
    forgotView.setAlpha(0f);
    Views.setClickable(forgotView);

    switch (mode) {
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_CONFIRM:
      case MODE_UNLOCK_EDIT:
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY:
      case MODE_LOGIN: {
        if (mode == MODE_CONFIRM || mode == MODE_UNLOCK_EDIT || mode == MODE_LOGIN || mode == MODE_TRANSFER_OWNERSHIP_CONFIRM) {
          forgotView.setText(Lang.getString(R.string.ForgotPassword));
          hint = Lang.getString(mode == MODE_TRANSFER_OWNERSHIP_CONFIRM ? R.string.TransferOwnershipPasswordAlertHint : R.string.LoginPasswordText);
        } else if ((mode == MODE_EMAIL_RECOVERY || mode == MODE_LOGIN_EMAIL_RECOVERY)) {
          final String email = getArguments() != null ? getArguments().email : null;
          if (!StringUtils.isEmpty(email)) {
            hint = Lang.getStringBold(R.string.RecoveryCodeSent, email);
            forgotView.setText(Lang.getString(R.string.HavingTroubleAccessing, email));
          } else {
            hint = Lang.getString(R.string.RecoveryCodeSentEmailUnknown);
            forgotView.setText(Lang.getString(R.string.RestoreEmailTroubleUnknown));
          }
        }
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM: {
        if (hasNextCodeType()) {
          forgotView.setText(Lang.getString(R.string.DidNotGetTheCode));
        }
        hint = getCodeHint(((TdApi.AuthorizationStateWaitCode) authState).codeInfo.type, formattedPhone);
        break;
      }
      case MODE_EMAIL_CHANGE: {
        hint = Lang.getString(R.string.YourEmailInfo);
        break;
      }
    }

    if (mode == MODE_TRANSFER_OWNERSHIP_CONFIRM || mode == MODE_UNLOCK_EDIT || mode == MODE_CONFIRM || mode == MODE_LOGIN || mode == MODE_CODE || mode == MODE_CODE_CHANGE || mode == MODE_CODE_PHONE_CONFIRM) {
      RelativeLayout forgotWrap = new RelativeLayout(context);
      forgotWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

      RelativeLayout.LayoutParams rp;

      rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      forgotView.setPadding(Screen.dp(16f), Screen.dp(15f), Screen.dp(12f), Screen.dp(16f));
      forgotView.setLayoutParams(rp);
      forgotWrap.addView(forgotView);

      rp = new RelativeLayout.LayoutParams(Screen.dp(16f), Screen.dp(16f));
      rp.addRule(RelativeLayout.CENTER_VERTICAL);
      rp.addRule(RelativeLayout.RIGHT_OF, R.id.btn_forgotPassword);

      progressView = new ProgressComponentView(context);
      progressView.initMedium(0f);
      progressView.setProgressColor(Theme.getColor(R.id.theme_color_textNeutral));
      addThemeTextColorListener(progressView, R.id.theme_color_textNeutral);
      progressView.setAlpha(0f);
      progressView.setLayoutParams(rp);
      forgotWrap.addView(progressView);

      contentView.addView(forgotWrap);
    } else {
      forgotView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
      contentView.addView(forgotView);
    }

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.leftMargin = params.rightMargin = Screen.dp(16f);
    params.topMargin = topMargin + Screen.dp(60f) + Screen.dp(14f);

    hintView = new NoScrollTextView(context);
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    hintView.setTextColor(Theme.textDecentColor());
    addThemeTextColorListener(hintView, R.id.theme_color_textLight);
    hintView.setTypeface(Fonts.getRobotoRegular());
    hintView.setLayoutParams(params);

    if (hint != null) {
      hintView.setText(hint);
    } else {
      hintView.setAlpha(0f);
    }
    contentView.addView(hintView);

    setLockFocusView(editText.getEditText());

    switch (mode) {
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY:
      case MODE_CODE:
      // FIXME? case MODE_CODE_CHANGE:
      case MODE_LOGIN: {
        tdlib.listeners().addAuthorizationChangeListener(this);
        break;
      }
    }

    return contentView;
  }

  @Override
  public void onAuthorizationStateChanged (TdApi.AuthorizationState authorizationState) {
    runOnUiThreadOptional(() -> {
      this.authState = authorizationState;
      updateAuthState();
    });
  }

  @Override
  public void onAuthorizationCodeReceived (String code) {
    switch (mode) {
      case MODE_CODE:
      case MODE_LOGIN:
        editText.setText(code);
        proceed();
        break;
    }
  }

  private CharSequence getCodeHint (TdApi.AuthenticationCodeType type, String formattedPhone) {
    if (mode == MODE_CODE_PHONE_CONFIRM) {
      return Strings.replaceBoldTokens(Lang.getString(R.string.CancelAccountResetInfo, formattedPhone));
    }
    editText.setHint(Lang.getString(R.string.login_Code));
    switch (type.getConstructor()) {
      case TdApi.AuthenticationCodeTypeCall.CONSTRUCTOR: {
        return Strings.replaceBoldTokens(Lang.getString(R.string.SentCallCode, formattedPhone), R.id.theme_color_textLight);
      }
      case TdApi.AuthenticationCodeTypeFlashCall.CONSTRUCTOR: {
        return Strings.replaceBoldTokens(Lang.getString(R.string.SentCallOnly, formattedPhone), R.id.theme_color_textLight);
      }
      case TdApi.AuthenticationCodeTypeTelegramMessage.CONSTRUCTOR: {
        return Strings.replaceBoldTokens(Lang.getString(R.string.SentAppCode), R.id.theme_color_textLight);
      }
      case TdApi.AuthenticationCodeTypeSms.CONSTRUCTOR: {
        return Strings.replaceBoldTokens(Lang.getString(R.string.SentSmsCode, formattedPhone), R.id.theme_color_textLight);
      }
      case TdApi.AuthenticationCodeTypeMissedCall.CONSTRUCTOR: {
        TdApi.AuthenticationCodeTypeMissedCall missedCall = (TdApi.AuthenticationCodeTypeMissedCall) type;
        editText.setHint(Lang.pluralBold(R.string.login_LastDigits, missedCall.length));
        return Strings.replaceBoldTokens(Lang.getString(R.string.format_doubleLines, Lang.getString(R.string.SentMissedCall, Strings.formatPhone(missedCall.phoneNumberPrefix)), Lang.plural(R.string.SentMissedCallXDigits, missedCall.length)), R.id.theme_color_textLight);
      }
    }
    return Strings.replaceBoldTokens(Lang.getString(R.string.SentSmsCode), R.id.theme_color_textLight);
  }

  @Override
  public boolean onTextDonePressed (MaterialEditTextGroup v) {
    proceed();
    return true;
  }

  @Override
  public void onTextChanged (MaterialEditTextGroup v, CharSequence charSequence) {
    String text = charSequence.toString();
    if (mode == MODE_NEW && step == STEP_EMAIL_RECOVERY) {
      setIsInputOK(Strings.isValidEmail(text));
    } else if (mode == MODE_EMAIL_RECOVERY || mode == MODE_LOGIN_EMAIL_RECOVERY) {
      setIsInputOK(Strings.getNumber(text).length() >= 6);
    } else if ((mode == MODE_CODE || mode == MODE_CODE_CHANGE || mode == MODE_CODE_PHONE_CONFIRM) && Strings.getNumberLength(text) >= TD.getCodeLength(authState)) {
      proceed();
    } else if ((mode == MODE_NEW || mode == MODE_EDIT) && step == STEP_PASSWORD_HINT) {
      passwordHint = text;
    }
  }

  private boolean ignoreNextEmpty;

  @Override
  public void onTextEmptyStateChanged (MaterialEditTextGroup v, boolean isEmpty) {
    if (ignoreNextEmpty) {
      ignoreNextEmpty = false;
    } else {
      if ((mode != MODE_NEW || step != STEP_EMAIL_RECOVERY) && mode != MODE_EMAIL_RECOVERY && mode != MODE_LOGIN_EMAIL_RECOVERY) {
        animateNextFactor(isEmpty && ((mode != MODE_NEW && mode != MODE_EDIT) || step != STEP_PASSWORD_HINT) ? 0f : 1f);
      }
    }
  }

  private float nextFactor;
  private FactorAnimator nextAnimator;

  private static final int NEXT_ANIMATOR = 0;
  private void animateNextFactor (float toFactor) {
    if (nextAnimator == null) {
      nextAnimator = new FactorAnimator(NEXT_ANIMATOR, this, AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR, 300, nextFactor);
    }
    nextAnimator.animateTo(toFactor);
  }

  private void setNextFactor (float factor) {
    if (this.nextFactor != factor) {
      this.nextFactor = factor;
      nextButton.setAlpha(Math.min(1f, factor));
      final float scale = .6f + .4f * factor;
      nextButton.setScaleX(scale);
      nextButton.setScaleY(scale);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case NEXT_ANIMATOR: {
        setNextFactor(factor);
        break;
      }
      case FORGET_ANIMATOR: {
        float easeFactor = (AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f + factor * .5f) / AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f)) * factor;
        forgotView.setAlpha(easeFactor);
        break;
      }
      case HINT_ANIMATOR: {
        float easeFactor = (AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f + factor * .5f) / AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(.5f)) * factor;
        hintView.setAlpha(easeFactor);
        break;
      }
      case RECOVERY_PROGRESS_ANIMATOR: {
        if (progressView != null) {
          progressView.setAlpha(factor);
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case FORGET_ANIMATOR: {
        if (finalFactor == 0f) {
          forgotView.setText("");
        }
        break;
      }
      case HINT_ANIMATOR: {
        if (finalFactor == 0f) {
          if (pendingHint != null && !pendingHint.isEmpty()) {
            setHint(pendingHint, pendingHintIsError);
            pendingHint = null;
            pendingHintIsError = false;
            hintAnimator.animateTo(1f);
          } else {
            setHint("", false);
          }
        }
        break;
      }
    }
  }

  private static final int HINT_ANIMATOR = 2;
  private FactorAnimator hintAnimator;

  private void animateHint (float toFactor) {
    if (hintAnimator == null) {
      hintAnimator = new FactorAnimator(HINT_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, 180l, hintView.getAlpha());
    }
    hintAnimator.animateTo(toFactor);
  }

  private void setHintText (@StringRes int res, boolean isError) {
    setHintText(Lang.getString(res), isError);
  }

  private void setHint (String text, boolean isError) {
    hintView.setText(text);
    hintView.setTextColor(isError ? Theme.textRedColor() : Theme.textDecentColor());
    removeThemeListenerByTarget(hintView);
    addThemeTextColorListener(hintView, isError ? R.id.theme_color_textNegative : R.id.theme_color_textLight);
    editText.setInErrorState(isError);
  }

  private void setHintText (@Nullable String text, boolean isError) {
    if (text == null || text.isEmpty()) {
      animateHint(0f);
      if (hintView.getAlpha() == 0f) {
        setHint("", false);
      }
    } else if (hintView.getAlpha() == 0f) {
      setHint(text, isError);
      animateHint(1f);
    } else {
      pendingHint = text;
      pendingHintIsError = isError;
      animateHint(0f);
    }
  }

  private String pendingHint;
  private boolean pendingHintIsError;

  private static final int FORGET_ANIMATOR = 1;
  private FactorAnimator forgetAnimator;

  private void animateForget (float toFactor) {
    if (forgetAnimator == null) {
      forgetAnimator = new FactorAnimator(FORGET_ANIMATOR, this, AnimatorUtils.LINEAR_INTERPOLATOR, 180l, forgotView.getAlpha());
    }
    forgetAnimator.animateTo(toFactor);
  }

  private void setForgetText (@StringRes int res) {
    setForgetText(Lang.getString(res));
  }

  private void setForgetText (@Nullable String forgetText) {
    if (forgetText == null || forgetText.isEmpty()) {
      animateForget(0f);
      if (forgotView.getAlpha() == 0f) {
        forgotView.setText("");
      }
    } else {
      forgotView.setText(forgetText);
      animateForget(1f);
    }
  }

  private boolean inRecoveryProgress;
  private void setInRecoveryProgress (boolean inProgress) {
    if (inRecoveryProgress != inProgress) {
      inRecoveryProgress = inProgress;
      animateRecoverProgressFactor(inProgress ? 1f : 0f);
    }
  }

  private static final int RECOVERY_PROGRESS_ANIMATOR = 3;
  private FactorAnimator recoverProgressAnimator;

  private void animateRecoverProgressFactor (float toFactor) {
    if (recoverProgressAnimator == null) {
      recoverProgressAnimator = new FactorAnimator(RECOVERY_PROGRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    recoverProgressAnimator.animateTo(toFactor);
  }

  private boolean hasNextCodeType () {
    return authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR && ((TdApi.AuthorizationStateWaitCode) authState).codeInfo.nextType != null;
  }

  private void updateAuthState () {
    if (authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
      hintView.setText(getCodeHint(((TdApi.AuthorizationStateWaitCode) authState).codeInfo.type, formattedPhone));
      if (!hasNextCodeType()) {
        setForgetText(null);
      }
    }
  }

  private void requestNextCodeType () {
    if (!hasNextCodeType() || inRecoveryProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInRecoveryProgress(true);
    TdApi.Function<?> function;
    switch (mode) {
      case MODE_CODE_PHONE_CONFIRM:
        function = new TdApi.ResendPhoneNumberConfirmationCode();
        break;
      case MODE_CODE_CHANGE:
        function = new TdApi.ResendChangePhoneNumberCode();
        break;
      default:
        function = new TdApi.ResendAuthenticationCode();
        break;
    }
    tdlib.client().send(function, object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInRecoveryProgress(false);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            break;
          }
          case TdApi.AuthenticationCodeInfo.CONSTRUCTOR: {
            ((TdApi.AuthorizationStateWaitCode) authState).codeInfo = (TdApi.AuthenticationCodeInfo) object;
            updateAuthState();
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            break;
          }
        }
      }
    }));
  }

  private void requestRecovery () {
    if ((state != null && !state.hasRecoveryEmailAddress) || (authState != null && authState.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR && !((TdApi.AuthorizationStateWaitPassword) authState).hasRecoveryEmailAddress) || (state == null && authState == null)) {
      openAlert(R.string.RestorePasswordNoEmailTitle, R.string.SinceNotProvided);
      return;
    }

    if (inRecoveryProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInRecoveryProgress(true);

    if (mode == MODE_LOGIN) {
      if (authState.getConstructor() != TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR) {
        Log.e("Can't proceed, authState: %s", authState);
        return;
      }
      if (!((TdApi.AuthorizationStateWaitPassword) authState).hasRecoveryEmailAddress) {
        openAlert(R.string.RestorePasswordNoEmailTitle, R.string.SinceNotProvided);
        return;
      }
      setStackLocked(true);
      tdlib.client().send(new TdApi.RequestAuthenticationPasswordRecovery(), object -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          setStackLocked(false);
          setInRecoveryProgress(false);
          switch (object.getConstructor()) {
            case TdApi.Ok.CONSTRUCTOR: {
              PasswordController c = new PasswordController(context, tdlib);
              TdApi.AuthorizationStateWaitPassword state = (TdApi.AuthorizationStateWaitPassword) authState;
              c.setArguments(new Args(MODE_LOGIN_EMAIL_RECOVERY, state).setEmail(state.recoveryEmailAddressPattern));
              navigateTo(c);
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              openAlert(R.string.RestorePasswordNoEmailTitle, R.string.SinceNotProvided);
              break;
            }
            default: {
              Log.unexpectedTdlibResponse(object, TdApi.RequestAuthenticationPasswordRecovery.class, TdApi.Ok.class, TdApi.Error.class);
              break;
            }
          }
        }
      }));
    } else {
      tdlib.client().send(new TdApi.RequestPasswordRecovery(), object -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          setInRecoveryProgress(false);
          switch (object.getConstructor()) {
            case TdApi.EmailAddressAuthenticationCodeInfo.CONSTRUCTOR: {
              TdApi.EmailAddressAuthenticationCodeInfo info = (TdApi.EmailAddressAuthenticationCodeInfo) object;
              PasswordController c = new PasswordController(context, tdlib);
              c.setArguments(new Args(MODE_EMAIL_RECOVERY, state).setEmail(info.emailAddressPattern).setCodeLength(info.length));
              navigateTo(c);
              break;
            }
            default: {
              openAlert(R.string.RestorePasswordNoEmailTitle, R.string.SinceNotProvided);
              break;
            }
          }
        }
      }));
    }
  }

  /*private boolean ignoreFirstOnBlur;

  @Override
  public void onBlur () {
    View lockFocusView = getLockFocusView();
    if (ignoreFirstOnBlur) {
      setLockFocusView(null);
    }
    super.onBlur();
    if (ignoreFirstOnBlur) {
      setLockFocusView(lockFocusView);
      ignoreFirstOnBlur = false;
    }
  }*/

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();

    if (!oneShot) {
      switch (mode) {
        case MODE_LOGIN: {
          destroyStackItemById(R.id.controller_code);
          break;
        }
        case MODE_CODE: {
          destroyStackItemById(R.id.controller_name);
          break;
        }
        case MODE_CODE_CHANGE: {
          destroyStackItemById(R.id.controller_phone);
          break;
        }
        case MODE_UNLOCK_EDIT: {
          destroyStackItemById(R.id.controller_2faSettings);
          break;
        }
      }
      if (UI.inTestMode()) {
        switch (mode) {
          case MODE_LOGIN: {
            editText.setText(Config.ROBOT_PASSWORD);
            proceed();
            break;
          }
          case MODE_CODE:
          case MODE_CODE_CHANGE: {
            editText.setText(tdlib.robotLoginCode());
            proceed();
            break;
          }
        }
      }
      oneShot = true;
    }

    if (forgotView != null && forgotView.getAlpha() == 0f && forgotView.getText().length() > 0) {
      forgotView.postDelayed(() -> animateForget(1f), 100);
    }
  }

  private boolean inProgress;

  private void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      nextButton.setInProgress(inProgress);
    }
  }

  private void unlockEdit (final String password) {
    if (inProgress) {
      return;
    }
    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);
    tdlib.client().send(new TdApi.GetRecoveryEmailAddress(password), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInProgress(false);

        boolean success = false;
        String recoveryEmail = null;

        switch (object.getConstructor()) {
          case TdApi.RecoveryEmailAddress.CONSTRUCTOR: {
            success = true;
            recoveryEmail = ((TdApi.RecoveryEmailAddress) object).recoveryEmailAddress;
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            setHintText(R.string.InvalidPasswordTryAgain, true);
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.GetRecoveryEmailAddress.class, TdApi.RecoveryEmailAddress.class);
            break;
          }
        }

        if (success) {
          ViewController<?> prev = navigationController != null ? navigationController.getPreviousStackItem() : null;
          if (prev instanceof SettingsPrivacyController) {
            Settings2FAController c = new Settings2FAController(context, tdlib);
            c.setArguments(new Settings2FAController.Args((SettingsPrivacyController) prev, password, recoveryEmail));
            navigateTo(c);
          } else if ((mode == MODE_CONFIRM || mode == MODE_TRANSFER_OWNERSHIP_CONFIRM) && getArguments() != null && getArguments().onSuccessListener != null) {
            navigateBack();
            getArgumentsStrict().onSuccessListener.runWithData(password);
          }
        }
      }
    }));
  }


  private static final int STEP_PASSWORD = 0;
  private static final int STEP_REPEAT_PASSWORD = 1;
  private static final int STEP_PASSWORD_HINT = 2;
  private static final int STEP_EMAIL_RECOVERY = 3;
  private static final int STEP_PASSWORD_DONT_MATCH = 4;
  private static final int STEP_EMAIL_RECOVERY_CONFIRM = 5;
  private int step;

  private String currentPassword;
  private String passwordHint;

  private void setStep (String input, int step) {
    if (this.step == step) {
      return;
    }
    switch (step) {
      case STEP_PASSWORD_DONT_MATCH: {
        this.step = STEP_PASSWORD;
        break;
      }
      default: {
        this.step = step;
        break;
      }
    }
    switch (step) {
      case STEP_PASSWORD:
      case STEP_PASSWORD_DONT_MATCH: {
        setForgetText(null);
        ignoreNextEmpty = true;
        animateNextFactor(0f);
        editText.resetWithHint(R.string.EnterAPassword, true, step == STEP_PASSWORD_DONT_MATCH ? (Runnable) () -> setHintText(R.string.PasswordDoNotMatch, true) : null);
        break;
      }
      case STEP_REPEAT_PASSWORD: {
        setForgetText(null);
        setHintText(null, false);
        currentPassword = input;
        ignoreNextEmpty = true;
        animateNextFactor(0f);
        editText.resetWithHint(R.string.ReEnterAPassword, true, null);
        break;
      }
      case STEP_PASSWORD_HINT: {
        setForgetText(null);
        passwordHint = Strings.generateHint(currentPassword);
        editText.resetWithHint(R.string.CreateAHintForYourPassword, passwordHint, false, null);
        if (mode == MODE_EDIT) {
          nextButton.replaceIcon(R.drawable.baseline_check_24);
        }
        break;
      }
      case STEP_EMAIL_RECOVERY: {
        ignoreNextEmpty = true;
        animateNextFactor(0f);
        editText.resetWithHint(R.string.YourEmail, false, () -> {
          nextButton.setIcon(R.drawable.baseline_check_24);
          // PS: Disabled because of jumping
          // editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
          setForgetText(R.string.Skip);
          setHintText(R.string.YourEmailInfo, false);
        });
        break;
      }
    }
  }

  private boolean isInputOK;

  private void setIsInputOK (boolean isEmail) {
    if (this.isInputOK != isEmail) {
      this.isInputOK = isEmail;
      animateNextFactor(isEmail ? 1f : 0f);
    }
  }

  private void setNewRecoveryEmail (String email) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);
    final String oldPassword = getArguments() != null ? getArguments().oldPassword : null;
    tdlib.client().send(new TdApi.SetRecoveryEmailAddress(oldPassword, email), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInProgress(false);
        switch (object.getConstructor()) {
          case TdApi.PasswordState.CONSTRUCTOR: {
            processNewPasswordState((TdApi.PasswordState) object, oldPassword);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            setHintText(TD.toErrorString(object), true);
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.SetRecoveryEmailAddress.class, TdApi.PasswordState.class);
            break;
          }
        }
      }
    }));
  }

  private void nextPasswordStep () {
    String input = editText.getText().toString();
    switch (step) {
      case STEP_PASSWORD: {
        if (input.length() > 0) {
          if (mode == MODE_EDIT && getArguments() != null && input.equals(getArguments().oldPassword)) {
            setHintText(R.string.PasswordMatchesOldOne, true);
          } else {
            setStep(input, STEP_REPEAT_PASSWORD);
          }
        }
        break;
      }
      case STEP_REPEAT_PASSWORD: {
        if (input.length() > 0) {
          if (currentPassword.equals(input)) {
            setStep(input, STEP_PASSWORD_HINT);
          } else {
            setStep(input, STEP_PASSWORD_DONT_MATCH);
          }
        }
        break;
      }
      case STEP_PASSWORD_HINT: {
        if (input.toLowerCase().equals(currentPassword.toLowerCase())) {
          setHintText(R.string.PasswordAndHintMustBeDifferent, true);
        } else if (mode == MODE_NEW) {
          setStep(input, STEP_EMAIL_RECOVERY);
        } else if (mode == MODE_EDIT) {
          setPassword(currentPassword, passwordHint, getArguments() != null ? getArguments().email : null);
        }
        break;
      }
      case STEP_EMAIL_RECOVERY: {
        String email = input.trim();
        if (email.isEmpty() || !Strings.isValidEmail(input)) {
          showNoRecoveryEmailAlert();
        } else {
          setPassword(currentPassword, passwordHint, email);
        }
        break;
      }
    }
  }

  private void sendCode (String code) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);
    if (mode == MODE_CODE) {
      setStackLocked(true);
    }

    TdApi.Function<?> function;
    switch (mode) {
      case MODE_CODE_PHONE_CONFIRM:
        function = new TdApi.CheckPhoneNumberConfirmationCode(code);
        break;
      case MODE_CODE_CHANGE:
        function = new TdApi.CheckChangePhoneNumberCode(code);
        break;
      default:
        function = new TdApi.CheckAuthenticationCode(code);
        break;
    }

    tdlib.client().send(function, object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInProgress(false);
        if (mode == MODE_CODE) {
          setStackLocked(false);
        }
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            switch (mode) {
              case MODE_CODE_PHONE_CONFIRM: {
                // TODO any pop-up?
                navigateBack();
                break;
              }
              case MODE_CODE_CHANGE: {
                openAlert(R.string.AppName, Lang.getString(R.string.SuccessfullyChangedNumber, formattedPhone), (dialog, which) -> navigateBack(), false);
                break;
              }
              case MODE_CODE: {
                if (UI.inTestMode()) {
                  tdlib.client().send(new TdApi.SetPassword(null, Config.ROBOT_PASSWORD, Strings.generateHint(Config.ROBOT_PASSWORD), false, null), tdlib.silentHandler());
                }
                break;
              }
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error error = (TdApi.Error) object;
            if ("PHONE_CODE_INVALID".equals(error.message)) {
              setHintText(R.string.InvalidCode, true);
            } else {
              setHintText(TD.toErrorString(error), true);
            }
            break;
          }
        }
      }
    }));
  }

  private void recover (String recoveryCode) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);

    if (mode == MODE_LOGIN_EMAIL_RECOVERY) {
      setStackLocked(true);
    }

    tdlib.client().send(mode == MODE_LOGIN_EMAIL_RECOVERY ? new TdApi.RecoverAuthenticationPassword(recoveryCode, null, null) : new TdApi.RecoverPassword(recoveryCode, null, null), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        if (mode == MODE_LOGIN_EMAIL_RECOVERY) {
          setStackLocked(false);
        }
        setInProgress(false);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            break;
          }
          case TdApi.PasswordState.CONSTRUCTOR: {
            processNewPasswordState((TdApi.PasswordState) object, null);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error error = (TdApi.Error) object;
            if (error.code == 400 && "CODE_INVALID".equals(error.message)) {
              setHintText(R.string.RecoveryCodeInvalid, true);
            } else {
              setHintText(TD.toErrorString(error), true);
            }
            break;
          }
        }
      }
    }));
  }

  private void login (String password) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);
    setStackLocked(true);

    tdlib.client().send(new TdApi.CheckAuthenticationPassword(password), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInProgress(false);
        setStackLocked(false);

        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            TdApi.Error error = (TdApi.Error) object;
            if (error.code == 400 && "PASSWORD_HASH_INVALID".equals(error.message)) {
              Views.selectAll(editText.getEditText());
              Keyboard.show(editText);
              setHintText(R.string.InvalidPasswordTryAgain, true);
            } else {
              setHintText(TD.toErrorString(object), true);
            }
            break;
          }
        }
      }
    }));
  }

  private void proceed () {
    String input = editText.getText().toString();
    switch (mode) {
      case MODE_CONFIRM:
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_UNLOCK_EDIT: {
        if (!input.isEmpty()) {
          unlockEdit(input);
        }
        break;
      }
      case MODE_LOGIN: {
        if (!input.isEmpty()) {
          login(input);
        }
        break;
      }
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        String number = Strings.getNumber(input);
        if (number.length() >= 6) {
          recover(number);
        }
        break;
      }
      case MODE_EMAIL_CHANGE: {
        if (Strings.isValidEmail(input) && getArguments() != null) {
          if (input.equals(getArguments().email) && (state == null || state.recoveryEmailAddressCodeInfo == null)) {
            setHintText(R.string.EmailMatchesOldOne, true);
          } else {
            setNewRecoveryEmail(input);
          }
        }
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM: {
        String number = Strings.getNumber(input);
        sendCode(number);
        break;
      }
      case MODE_NEW:
      case MODE_EDIT: {
        nextPasswordStep();
        break;
      }
    }
  }

  private void showNoRecoveryEmailAlert () {
    openAlert(R.string.Warning, R.string.YourEmailSkipWarningText, (dialog, which) -> setPassword(currentPassword, passwordHint, null));
  }

  private void proceedForgot () {
    switch (mode) {
      case MODE_EMAIL_RECOVERY:
      case MODE_LOGIN_EMAIL_RECOVERY: {
        openAlert(R.string.RestorePasswordNoEmailTitle, R.string.RestoreEmailTroubleText);
        break;
      }
      case MODE_CODE:
      case MODE_CODE_CHANGE:
      case MODE_CODE_PHONE_CONFIRM: {
        requestNextCodeType();
        break;
      }
      case MODE_CONFIRM:
      case MODE_TRANSFER_OWNERSHIP_CONFIRM:
      case MODE_UNLOCK_EDIT:
      case MODE_LOGIN: {
        requestRecovery();
        break;
      }
      case MODE_NEW: {
        showNoRecoveryEmailAlert();
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_done: {
        proceed();
        break;
      }
      case R.id.btn_forgotPassword: {
        proceedForgot();
        break;
      }
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return !inProgress;
  }

  private void setPassword (final String password, final String passwordHint, String email) {
    if (inProgress) {
      return;
    }

    if (tdlib.context().watchDog().isOffline()) {
      UI.showNetworkPrompt();
      return;
    }

    setInProgress(true);

    tdlib.client().send(new TdApi.SetPassword(mode == MODE_NEW || getArguments() == null ? null : getArguments().oldPassword, password, passwordHint, mode != MODE_EDIT, email), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setInProgress(false);
        switch (object.getConstructor()) {
          case TdApi.PasswordState.CONSTRUCTOR: {
            processNewPasswordState((TdApi.PasswordState) object, password);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            break;
          }
          default: {
            Log.unexpectedTdlibResponse(object, TdApi.SetPassword.class, TdApi.PasswordState.class);
            break;
          }
        }
      }
    }));
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().removeAuthorizationChangeListener(this);
    if (nextButton != null) {
      nextButton.destroy();
    }
  }

  private void processNewPasswordState (TdApi.PasswordState state, @Nullable String password) {
    this.state = state;
    ViewController<?> c;

    if (mode == MODE_NEW) {
      c = removeStackItemById(R.id.controller_2faSettings);
      if (c instanceof Settings2FAController) {
        ((Settings2FAController) c).updatePasswordState(state, password);
        navigateTo(c);
        return;
      }
    } else if (mode == MODE_EDIT || mode == MODE_EMAIL_CHANGE) {
      c = findLastStackItemById(R.id.controller_2faSettings);
      if (c instanceof Settings2FAController) {
        ((Settings2FAController) c).updatePasswordState(state, password);
        navigateBack();
        return;
      }
    }
    c = findLastStackItemById(R.id.controller_privacySettings);
    if (c instanceof SettingsPrivacyController) {
      ((SettingsPrivacyController) c).updatePasswordState(state);
      Settings2FAController settings = new Settings2FAController(context, tdlib);
      settings.setArguments(new Settings2FAController.Args((SettingsPrivacyController) c, null, null));
      navigateTo(settings);
    }
  }
}
