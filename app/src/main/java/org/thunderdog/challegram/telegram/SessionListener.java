package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface SessionListener {
  void onSessionListChanged (Tdlib tdlib, boolean isWeakGuess);
  default void onSessionTerminated (Tdlib tdlib, TdApi.Session session) {
    onSessionListChanged(tdlib, false);
  }
  default void onAllOtherSessionsTerminated (Tdlib tdlib, TdApi.Session currentSession) {
    onSessionListChanged(tdlib, false);
  }
  default void onSessionCreatedViaQrCode (Tdlib tdlib, TdApi.Session session) {
    onSessionListChanged(tdlib, false);
  }
  default void onInactiveSessionTtlChanged (Tdlib tdlib, int ttlDays) {
    onSessionListChanged(tdlib, false);
  }
}
