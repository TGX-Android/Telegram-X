/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/09/2017
 */
package org.thunderdog.challegram.voip.gui;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;

public class VoIPPermissionActivity extends Activity {
  private int accountId;
  private int callId;
  private boolean isVideo;

  @Override
  protected void onCreate (Bundle savedInstanceState){
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    if (intent != null) {
      accountId = intent.getIntExtra("account_id", TdlibAccount.NO_ID);
      callId = intent.getIntExtra("call_id", 0);
      isVideo = intent.getBooleanExtra("is_video", false);
    } else {
      accountId = TdlibAccount.NO_ID;
      callId = 0;
      isVideo = false;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 101);
    }
  }

  @Override
  @TargetApi(Build.VERSION_CODES.M)
  public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
    if (requestCode == 101) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        TdlibManager.getTdlib(accountId).context().calls().acceptIncomingCall(TdlibManager.getTdlib(accountId), callId);
        finish();
        // startActivity(new Intent(this, MainActivity.class));
      }else{
        if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
          TdlibManager.getTdlib(accountId).context().calls().declineIncomingCall(TdlibManager.getTdlib(accountId), callId, isVideo);
        }
        finish();
      }
    }
  }
}
