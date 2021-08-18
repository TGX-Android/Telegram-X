package org.thunderdog.challegram.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.UI;

/**
 * Date: 20/11/2016
 * Author: default
 */

public class NetworkListenerService extends Service {
  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    UI.initApp(getApplicationContext());
    return START_STICKY;
  }
}
