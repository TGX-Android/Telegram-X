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
 * File created on 04/08/2018
 */
package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.drinkless.tdlib.TdApi;

import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.Intents;

public class CallBroadcastReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    TGCallService service = TGCallService.currentInstance();
    if (service != null) {
      service.processBroadcast(context, intent);
    } else if (Intents.ACTION_DECLINE_CALL.equals(intent.getAction()) ||
      Intents.ACTION_ANSWER_CALL.equals(intent.getAction())) {
      int accountId = intent.getIntExtra(Intents.KEY_ACCOUNT_ID, TdlibAccount.NO_ID);
      int callId = intent.getIntExtra(Intents.KEY_CALL_ID, 0);
      if (accountId != TdlibAccount.NO_ID && callId != 0) {
        TdlibManager manager = TdlibManager.instanceForAccountId(accountId);
        if (Intents.ACTION_ANSWER_CALL.equals(intent.getAction())) {
          manager.calls().answerCallWithoutService(context, manager.tdlib(accountId), callId);
        } else {
          manager.calls().declineCallWithoutService(manager.tdlib(accountId), callId);
        }
      }
    }
  }
}
