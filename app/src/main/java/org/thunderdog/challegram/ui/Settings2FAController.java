package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.DoneListener;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;

import me.vkryl.core.StringUtils;

/**
 * Date: 17/11/2016
 * Author: default
 */

public class Settings2FAController extends RecyclerViewController<Settings2FAController.Args> implements SettingsPrivacyController.PasswordStateLoadListener, View.OnClickListener, Client.ResultHandler, DoneListener, SettingsAdapter.TextChangeListener {
  public static class Args {
    public final @Nullable SettingsPrivacyController controller;
    public final TdApi.PasswordState state;
    public final @Nullable String recoveryEmail;
    public final @Nullable String currentPassword;

    public Args (@Nullable SettingsPrivacyController controller, @Nullable String currentAcceptedPassword, @Nullable String recoveryEmail) {
      this.controller = controller;
      this.currentPassword = currentAcceptedPassword;
      this.state = controller != null ? controller.getCurrentPasswordState() : null;
      this.recoveryEmail = recoveryEmail;
    }

    public Args (TdApi.PasswordState passwordState) {
      this.controller = null;
      this.currentPassword = null;
      this.recoveryEmail = null;
      this.state = passwordState;
    }
  }

  public Settings2FAController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.PasswordState state;
  @Nullable
  private String currentRecoveryEmailAddress;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.state = args.state;
    this.currentAcceptedPassword = args.currentPassword;
    this.currentRecoveryEmailAddress = args.recoveryEmail;
    if (state == null && args.controller != null) {
      args.controller.setPasswordListener(this);
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TwoStepVerification);
  }

  @Override
  public void destroy () {
    super.destroy();
    if (getArguments() != null && getArguments().controller != null) {
      getArguments().controller.setPasswordListener(null);
    }
  }

  @Override
  public int getId () {
    return R.id.controller_2faSettings;
  }

  @Override
  public void onPasswordStateLoaded (TdApi.PasswordState state) {
    if (!isDestroyed()) {
      this.state = state;
      buildCells();
    }
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        editText.setAlwaysActive(item.getViewType() == ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE);
      }

      @Override
      protected void setEmailPattern (ListItem item, TextView textView) {
        textView.setText(state.recoveryEmailAddressCodeInfo != null ? state.recoveryEmailAddressCodeInfo.emailAddressPattern : "");
      }
    };
    this.adapter.setTextChangeListener(this);
    this.adapter.setLockFocusOn(this, true);
    if (state != null) {
      buildCells();
    } else if (getArgumentsStrict().controller == null) {
      tdlib.client().send(new TdApi.GetPasswordState(), result -> {
        if (result.getConstructor() == TdApi.PasswordState.CONSTRUCTOR) {
          runOnUiThreadOptional(() ->
            onPasswordStateLoaded((TdApi.PasswordState) result)
          );
        } else {
          UI.showError(result);
        }
      });
    }
    recyclerView.setAdapter(adapter);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return state == null;
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    switch (id) {
      case R.id.login_code: {
        if (state.recoveryEmailAddressCodeInfo != null && Strings.getNumberLength(text) >= TD.getCodeLength(state.recoveryEmailAddressCodeInfo)) {
          submitEmailRecoveryCode(v);
        } else {
          v.setInErrorState(false);
        }
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_setPassword: {
        PasswordController c = new PasswordController(context, tdlib);
        c.setArguments(new PasswordController.Args(PasswordController.MODE_NEW, state));
        navigateTo(c);
        break;
      }
      case R.id.btn_changePassword: {
        PasswordController c = new PasswordController(context, tdlib);
        c.setArguments(new PasswordController.Args(PasswordController.MODE_EDIT, state).setEmail(currentRecoveryEmailAddress).setOldPassword(currentAcceptedPassword));
        navigateTo(c);
        break;
      }

      case R.id.btn_setRecoveryEmail: {
        PasswordController c = new PasswordController(context, tdlib);
        c.setArguments(new PasswordController.Args(PasswordController.MODE_EMAIL_CHANGE, state).setEmail(currentRecoveryEmailAddress).setOldPassword(currentAcceptedPassword));
        navigateTo(c);
        break;
      }
      case R.id.btn_abortRecoveryEmail: {
        showWarning(Lang.getString(R.string.AbortRecoveryEmailConfirm), success -> {
          if (success) {
            tdlib.client().send(new TdApi.SetRecoveryEmailAddress(currentAcceptedPassword, currentRecoveryEmailAddress), object -> tdlib.ui().post(() -> {
              if (object instanceof TdApi.PasswordState) {
                updatePasswordState((TdApi.PasswordState) object, currentAcceptedPassword);
              }
            }));
          }
        });
        break;
      }
      case R.id.btn_abort2FA: {
        hideSoftwareKeyboard();
        showOptions(Lang.getString(R.string.AbortPasswordConfirm), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.AbortPassword), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          switch (id) {
            case R.id.btn_done: {
              hideSoftwareKeyboard();
              abort2FA();
              break;
            }
          }
          return true;
        });
        break;
      }
      case R.id.btn_resendRecoveryEmail: {
        tdlib.client().send(new TdApi.ResendRecoveryEmailAddressCode(), result -> {
          switch (result.getConstructor()) {
            case TdApi.PasswordState.CONSTRUCTOR: {
              tdlib.ui().post(() -> {
                if (!isDestroyed())
                  UI.showToast(R.string.RecoveryCodeResent, Toast.LENGTH_SHORT);
              });
              break;
            }
            case TdApi.Error.CONSTRUCTOR:
              UI.showError(result);
              break;
          }
        });
        break;
      }
      case R.id.btn_disablePassword: {
        showOptions(Strings.buildMarkdown(this, Lang.getString(state.hasPassportData ? R.string.TurnPasswordOffQuestion2 : R.string.TurnPasswordOffQuestion), null), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.DisablePassword), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_remove_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_done) {
            state.hasRecoveryEmailAddress = false;
            state.recoveryEmailAddressCodeInfo = null;
            setHasPassword(false);
            buildCells();
            tdlib.client().send(new TdApi.SetPassword(currentAcceptedPassword, null, null, true, null), Settings2FAController.this);
          }
          return true;
        });
        break;
      }
    }
  }

  private void abort2FA () {
    final TdApi.EmailAddressAuthenticationCodeInfo lastUnconfirmedEmail = state.recoveryEmailAddressCodeInfo;
    state.recoveryEmailAddressCodeInfo = null;
    if (getArguments() != null && getArguments().controller != null) {
      getArguments().controller.updatePasswordState(state);
    }
    buildCells();
    tdlib.client().send(new TdApi.SetRecoveryEmailAddress(null, null), object -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        switch (object.getConstructor()) {
          case TdApi.PasswordState.CONSTRUCTOR: {
            if (!((TdApi.PasswordState) object).hasRecoveryEmailAddress)
              currentRecoveryEmailAddress = null;
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            state.recoveryEmailAddressCodeInfo = lastUnconfirmedEmail;
            if (getArguments() != null && getArguments().controller != null) {
              getArguments().controller.updatePasswordState(state);
            }
            buildCells();
            break;
          }
        }
      }
    }));
  }

  private void setHasPassword (boolean hasPassword) {
    state.hasPassword = hasPassword;
    if (getArguments() != null && getArguments().controller != null) {
      getArguments().controller.updatePasswordState(state);
    }
  }

  @Override
  public void onResult (final TdApi.Object object) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            break;
          }
          case TdApi.PasswordState.CONSTRUCTOR: {
            TdApi.PasswordState newState = ((TdApi.PasswordState) object);
            if (!newState.hasPassword)
              currentAcceptedPassword = null;
            if (!newState.hasRecoveryEmailAddress)
              currentRecoveryEmailAddress = null;
            // FIXME? updatePasswordState(((TdApi.PasswordState) object), currentAcceptedPassword);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: { // Probably incorrect current password
            UI.showError(object);
            setHasPassword(true);
            currentAcceptedPassword = null;
            buildCells();
            break;
          }
        }
      }
    });
  }

  private @Nullable String currentAcceptedPassword;

  private CharSequence getRecoverySequence () {
    if (state.recoveryEmailAddressCodeInfo != null) {
      String pattern = state.recoveryEmailAddressCodeInfo.emailAddressPattern;
      String str = Lang.getString(R.string.PendingEmailText, pattern);
      int i = str.indexOf(pattern);
      if (i != -1) {
        SpannableStringBuilder recoverySequence = new SpannableStringBuilder(str);
        recoverySequence.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), R.id.theme_color_background_textLight), i, i + pattern.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return recoverySequence;
      }
    }
    return null;
  }

  private boolean removedPasswordItem;

  @Override
  public void onFocus () {
    super.onFocus();
    if (!removedPasswordItem && navigationController != null) {
      removedPasswordItem = true;
      destroyStackItemById(R.id.controller_password);
      destroyStackItemById(R.id.controller_passwordRecovery);
      destroyStackItemById(R.id.controller_2faSettings);
    }
  }

  public void updatePasswordState (TdApi.PasswordState state, String newPassword) {
    this.state = state;
    this.currentAcceptedPassword = newPassword;
    if (!state.hasRecoveryEmailAddress)
      currentRecoveryEmailAddress = null;
    if (getArguments() != null && getArguments().controller != null) {
      getArguments().controller.updatePasswordState(state);
    }
    removedPasswordItem = false;
    buildCells();
  }

  private ListItem codeItem;

  private void buildCells () {
    if (!state.hasPassword) {
      if (state.recoveryEmailAddressCodeInfo != null) {
        adapter.setItems(new ListItem[] {
          codeItem = new ListItem(ListItem.TYPE_EDITTEXT_REUSABLE, R.id.login_code, 0, R.string.EnterCode)
            .setOnEditorActionListener(new EditBaseController.SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this)),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
          new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(R.string.CheckYourVerificationEmail2, state.recoveryEmailAddressCodeInfo.emailAddressPattern), false),
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_SETTING, R.id.btn_resendRecoveryEmail, 0, R.string.ResendRecoveryEmailCode),
          new ListItem(ListItem.TYPE_SEPARATOR_FULL),
          new ListItem(ListItem.TYPE_SETTING, R.id.btn_abort2FA, 0, R.string.AbortPassword).setTextColorId(R.id.theme_color_textNegative),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        }, false);
        this.adapter.setLockFocusOn(this, true);
        setShowDecoration(true);
      } else {
        adapter.setItems(new ListItem[]{
          new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_security_96, R.string.SetAdditionalPasswordInfo),
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_BUTTON, R.id.btn_setPassword, 0, R.string.SetAdditionalPassword),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
        }, false);
        setShowDecoration(false);
      }
    } else if (currentAcceptedPassword != null) {
      ArrayList<ListItem> items = new ArrayList<>(10);
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_changePassword, 0, R.string.ChangePassword));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_disablePassword, 0, R.string.DisablePassword));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_setRecoveryEmail, 0, state.hasRecoveryEmailAddress ? R.string.ChangeRecoveryEmail : R.string.SetRecoveryEmail));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.EnabledPasswordText));

      if (state.recoveryEmailAddressCodeInfo != null) {
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(codeItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE, R.id.login_code, 0, R.string.EnterCode)
          .setOnEditorActionListener(new EditBaseController.SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this)));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(state.hasRecoveryEmailAddress ? R.string.CheckYourVerificationEmail3 : R.string.CheckYourVerificationEmail2, state.recoveryEmailAddressCodeInfo.emailAddressPattern), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_resendRecoveryEmail, 0, R.string.ResendRecoveryEmailCode));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_abortRecoveryEmail, 0, state.hasRecoveryEmailAddress ? R.string.AbortRecoveryEmailChange : R.string.AbortRecoveryEmail).setTextColorId(R.id.theme_color_textNegative));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        this.adapter.setLockFocusOn(this, false);
      }

      adapter.setItems(items, false);

      setShowDecoration(state.recoveryEmailAddressCodeInfo != null);
    } else {
      PasswordController controller = new PasswordController(context, tdlib);
      controller.setArguments(new PasswordController.Args(PasswordController.MODE_UNLOCK_EDIT, state));
      navigateTo(controller);
    }
  }

  @Override
  public boolean onDoneClick (View v) {
    return submitEmailRecoveryCode(null);
  }

  private boolean isVerifying;

  private void setIsVerifying (boolean isVerifying, boolean error) {
    if (this.isVerifying != isVerifying) {
      this.isVerifying = isVerifying;
      if (!isVerifying && error) {
        int i = adapter.indexOfViewById(R.id.login_code);
        if (i != -1) {
          View parent = getRecyclerView().getLayoutManager().findViewByPosition(i);
          if (parent != null && parent.getId() == R.id.login_code) {
            MaterialEditTextGroup group = (MaterialEditTextGroup) ((ViewGroup) parent).getChildAt(0);
            group.setInErrorState(true);
          }
        }
      }
    }
  }

  private boolean submitEmailRecoveryCode (@Nullable MaterialEditTextGroup v) {
    if (isVerifying)
      return true;
    if (codeItem == null || state.recoveryEmailAddressCodeInfo == null)
      return false;
    if (v == null) {
      int i = adapter.indexOfViewById(R.id.login_code);
      if (i != -1) {
        View parent = getRecyclerView().getLayoutManager().findViewByPosition(i);
        if (parent != null && parent.getId() == R.id.login_code) {
          v = (MaterialEditTextGroup) ((ViewGroup) parent).getChildAt(0);
        }
      }
    }
    String code = Strings.getNumber(codeItem.getStringValue());
    int len = TD.getCodeLength(state.recoveryEmailAddressCodeInfo);
    boolean ok = !StringUtils.isEmpty(code) && code.length() >= len;
    if (v != null) {
      v.setInErrorState(!ok);
    }
    if (ok) {
      setIsVerifying(true, false);
      tdlib.client().send(new TdApi.CheckRecoveryEmailAddressCode(code), result -> {
        switch (result.getConstructor()) {
          case TdApi.PasswordState.CONSTRUCTOR:
            TdApi.PasswordState state = (TdApi.PasswordState) result;
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                setIsVerifying(false, state.recoveryEmailAddressCodeInfo != null);
                if (state.recoveryEmailAddressCodeInfo == null) {
                  hideSoftwareKeyboard();
                  updatePasswordState(state, currentAcceptedPassword);
                }
              }
            });
            break;
          case TdApi.Error.CONSTRUCTOR:
            UI.showError(result);
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                setIsVerifying(false, true);
              }
            });
            break;
        }
      });
    }
    return false;
  }

  // private boolean isShortPolling;
  // private CancellableResultHandler handler;

  private FillingDecoration tempDecoration;

  private void setShowDecoration (boolean isShortPolling) {
    if (isShortPolling) {
      if (tempDecoration == null) {
        int i = Math.max(0, adapter.indexOfViewById(R.id.login_code));
        getRecyclerView().addItemDecoration(tempDecoration = new FillingDecoration(getRecyclerView(), this).addRange(i, i + 1));
      }
    } else {
      if (tempDecoration != null) {
        getRecyclerView().removeItemDecoration(tempDecoration);
        tempDecoration = null;
      }
    }
  }

  /*@Override
  public void onActivityResume () {
    super.onActivityResume();
    if (isShortPolling) {
      schedulePoll(true);
    }
  }

  private void cancelPoll () {
    if (handler != null) {
      handler.cancel();
      handler = null;
    }
  }

  private void schedulePoll (boolean now) {
    if (!isShortPolling || isDestroyed()) {
      return;
    }
    cancelPoll();
    handler = new CancellableResultHandler() {
      @Override
      public void processResult (final TdApi.Object object) {
        tdlib.ui().post(() -> {
          if (!isDestroyed() && !isCancelled()) {
            if (object.getConstructor() == TdApi.PasswordState.CONSTRUCTOR && processPollResult((TdApi.PasswordState) object)) {
              return;
            }
            schedulePoll(false);
          }
        });
      }
    };
    if (now) {
      tdlib.client().send(new TdApi.GetPasswordState(), handler);
    } else {
      UI.post(() -> {
        if (handler != null && !handler.isCancelled()) {
          tdlib.client().send(new TdApi.GetPasswordState(), handler);
        }
      }, 2000);
    }
  }

  private boolean processPollResult (TdApi.PasswordState state) {
    if (state.hasPassword) {
      setShowDecoration(false);
      updatePasswordState(state, currentAcceptedPassword);
      return true;
    }
    return false;
  }*/
}
