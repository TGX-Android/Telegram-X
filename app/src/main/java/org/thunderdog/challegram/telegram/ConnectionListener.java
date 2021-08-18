package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/15/18
 * Author: default
 */
public interface ConnectionListener {
  default void onConnectionStateChanged (@ConnectionState int newState, int oldState) { }
  default void onConnectionTypeChanged (TdApi.NetworkType type) { }
}
