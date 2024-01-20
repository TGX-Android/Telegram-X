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
 * File created on 25/12/2023 at 00:17
 */
package org.thunderdog.challegram.ui;

import android.content.Context;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.widget.DoneButton;

import me.vkryl.core.StringUtils;

public class EditDeleteAccountReasonController extends EditTextController<String> {
  public EditDeleteAccountReasonController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setDelegate(new Delegate() {
      @Override
      public int getId () {
        return R.id.controller_deleteAccount;
      }

      @Override
      public int getDoneIcon () {
        return R.drawable.baseline_delete_alert_24;
      }

      @Override
      public CharSequence getName () {
        return Lang.getString(R.string.DeleteAccount);
      }

      @Override
      public CharSequence getHint () {
        return Lang.getString(R.string.DeleteAccountReason);
      }

      @Override
      public CharSequence getDescription () {
        return Lang.getMarkdownString(EditDeleteAccountReasonController.this, R.string.DeleteAccountDescription);
      }

      @Override
      public boolean allowEmptyValue () {
        return false;
      }

      @Override
      public boolean needFocusInput () {
        return false;
      }

      @Override
      public boolean onDonePressed (EditTextController<?> controller, DoneButton button, String value) {
        if (isInProgress())
          return false;
        if (!StringUtils.isEmptyOrBlank(value)) {
          showOptions(
            Lang.getMarkdownString(controller, R.string.DeleteAccountConfirmFinal),
            new int[] {R.id.btn_deleteAccount, R.id.btn_cancel},
            new String[] {Lang.getString(R.string.DeleteAccountConfirmFinalBtn), Lang.getString(R.string.Cancel)},
            new int[] {OptionColor.RED, OptionColor.NORMAL},
            new int[] {R.drawable.baseline_delete_alert_24, R.drawable.baseline_cancel_24},
            (optionItemView, id) -> {
              if (id == R.id.btn_deleteAccount) {
                TdlibAccount account = tdlib.account();
                String name = account.getName();
                String phoneNumber = account.getPhoneNumber();
                setDoneInProgress(true);
                setStackLocked(true);
                tdlib.send(new TdApi.DeleteAccount(value, getArguments()), (ok, error) -> runOnUiThreadOptional(() -> {
                  setStackLocked(false);
                  setDoneInProgress(false);
                  if (error != null) {
                    context().tooltipManager()
                      .builder(getDoneButton())
                      .icon(R.drawable.baseline_warning_24)
                      .show(tdlib, TD.toErrorString(error));
                  } else {
                    tdlib.switchToNextAuthorizedAccount();
                    openAlert(R.string.AccountDeleted, Lang.getMarkdownString(controller, R.string.AccountDeletedText, name, Strings.formatPhone(phoneNumber)));
                  }
                }));
              }
              return true;
            }
          );
          return true;
        }
        return false;
      }
    });
    addOneShotFocusListener(() -> {
      ViewController<?> c = navigationController().getStack().getPrevious();
      if (c instanceof PasswordController) { // Password confirmation
        destroyPreviousStackItem();
      }
    });
  }
}
