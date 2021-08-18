package org.thunderdog.challegram.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Date: 25/01/2019
 * Author: default
 */
public class StubAuthenticatorService extends Service {
  private StubAuthenticator authenticator;

  @Override
  public void onCreate () {
    super.onCreate();
    this.authenticator = new StubAuthenticator(this);
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return authenticator.getIBinder();
  }
}
