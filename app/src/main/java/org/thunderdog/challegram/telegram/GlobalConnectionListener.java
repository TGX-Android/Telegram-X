package org.thunderdog.challegram.telegram;

/**
 * Date: 2/21/18
 * Author: default
 */

public interface GlobalConnectionListener {
  void onConnectionStateChanged (Tdlib tdlib, @ConnectionState int newState, boolean isCurrent);
  void onConnectionTypeChanged (int oldType, int newType);
  void onSystemDataSaverStateChanged (boolean isEnabled);
}
