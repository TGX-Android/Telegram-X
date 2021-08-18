package org.thunderdog.challegram.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

/**
 * Date: 10/03/2019
 * Author: default
 */
public class BootListenerService extends Service {
  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    UI.initApp(getApplicationContext());
    TdlibManager.makeSync(getApplicationContext(), TdlibAccount.NO_ID, TdlibManager.SYNC_CAUSE_BOOT, 0, false, 0);
    return START_NOT_STICKY;
  }
}
