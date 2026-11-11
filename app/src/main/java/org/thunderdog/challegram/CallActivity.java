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
 * File created on 03/11/2026
 */
package org.thunderdog.challegram;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.ui.CallController;
import org.thunderdog.challegram.unsorted.Passcode;

public class CallActivity extends BaseActivity {
  @Override
  protected boolean needDrawer () {
    return false;
  }

  @Override
  protected boolean needPasscode () {
    return Passcode.instance().requireForIncomingCalls();
  }

  @Override
  public void onCreate (Bundle savedInstanceState) {
    // Android 8.1+ shows it over the lock screen by showWhenLocked and turnScreenOn of the manifest
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
      getWindow().addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      );
    }
    super.onCreate(savedInstanceState);
    openCall(getIntent());
  }

  @Override
  protected void onNewIntent (Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    openCall(intent);
  }

  private boolean isPhoneLocked () {
    KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    return keyguard != null && keyguard.isKeyguardLocked();
  }

  private void openCall (Intent intent) {
    int accountId = intent.getIntExtra(Intents.KEY_ACCOUNT_ID, TdlibAccount.NO_ID);
    int callId = intent.getIntExtra(Intents.KEY_CALL_ID, 0);
    Tdlib tdlib = accountId != TdlibAccount.NO_ID ? TdlibManager.getTdlib(accountId) : null;
    TdApi.Call call = tdlib != null && callId != 0 ? tdlib.cache().getCall(callId) : null;
    if (call == null || TD.isFinished(call)) {
      finish();
      return;
    }
    boolean needAnswer = intent.getBooleanExtra(Intents.KEY_ANSWER_CALL, false);
    if (!isPhoneLocked()) {
      if (needAnswer) {
        Intent inApp = Intents.valueOfCallInApp(false);
        inApp.putExtra(Intents.KEY_ANSWER_CALL, true);
        inApp.putExtra(Intents.KEY_ACCOUNT_ID, accountId);
        inApp.putExtra(Intents.KEY_CALL_ID, callId);
        startActivity(inApp);
        finish();
      } else {
        openApp(false);
      }
      return;
    }
    if (needAnswer && !Passcode.instance().isLockedForIncomingCalls() && !call.isOutgoing &&
      call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR) {
      tdlib.context().calls().acceptCall(this, tdlib, call.id);
    }
    setTdlib(tdlib);
    ViewController<?> c = navigation.getCurrentStackItem();
    if (c instanceof CallController && c.tdlibId() == tdlib.id() &&
      ((CallController) c).compareUserId(call.userId)) {
      ((CallController) c).replaceCall(call);
      return;
    }
    CallController controller = new CallController(this, tdlib);
    controller.setArguments(new CallController.Arguments(call));
    navigation.setController(controller);
    controller.showWithoutTransition();
  }

  public void openApp (boolean openChat) {
    // MainActivity doesn't show over the lock screen: Android 8.0+ asks to unlock first, the call
    // staying if cancelled; before, MainActivity waits behind the lock screen
    Runnable open = () -> {
      startActivity(Intents.valueOfCallInApp(openChat));
      finish();
    };
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPhoneLocked()) {
      KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
      keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
        @Override
        public void onDismissSucceeded () {
          open.run();
        }
      });
    } else {
      open.run();
    }
  }
}
