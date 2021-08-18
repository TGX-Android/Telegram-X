package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/17/18
 * Author: default
 */

public interface AuthorizationListener {
  void onAuthorizationStateChanged (TdApi.AuthorizationState authorizationState);
  default void onAuthorizationCodeReceived (String code) { }
}
