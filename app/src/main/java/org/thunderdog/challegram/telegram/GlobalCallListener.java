package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.voip.gui.CallSettings;

/**
 * Date: 2/19/18
 * Author: default
 */

public interface GlobalCallListener {
  void onCallUpdated (Tdlib tdlib, TdApi.Call call);
  void onCallSettingsChanged (Tdlib tdlib, int callId, CallSettings settings);
}
