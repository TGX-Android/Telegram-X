package org.thunderdog.challegram.service;

import android.app.Service;

import org.drinkless.tdlib.TdApi;
import org.jetbrains.annotations.NotNull;
import org.thunderdog.challegram.TDLib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Map;

import tgx.bridge.PushReceiver;
import tgx.td.JSON;

public final class PushHandler implements PushReceiver {
  @Override
  public void onNewToken (@NotNull Service service, @NotNull TdApi.DeviceToken token) {
    UI.initApp(service.getApplicationContext());
    TDLib.Tag.notifications("onNewToken %s, sending to all accounts", token);
    TdlibManager.instance().runWithWakeLock(manager -> {
      manager.setDeviceToken(token);
    });
  }

  @Override
  public void onMessageReceived (@NotNull Service service, @NotNull Map<@NotNull String, ?> message, long sentTime, int ttl) {
    UI.initApp(service.getApplicationContext());
    final long pushId = Settings.instance().newPushId();

    String payload = JSON.stringify(message);

    PushProcessor pushProcessor = new PushProcessor(service);
    pushProcessor.processPush(pushId, payload, sentTime, ttl);
  }
}
