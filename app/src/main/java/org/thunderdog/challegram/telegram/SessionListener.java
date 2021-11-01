package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface SessionListener {
  void onSessionListChanged (boolean isWeakGuess);
  default void onSessionTerminated (TdApi.Session session) {
    onSessionListChanged(false);
  }
  default void onAllOtherSessionsTerminated (TdApi.Session currentSession) {
    onSessionListChanged(false);
  }
  default void onSessionCreatedViaQrCode (TdApi.Session session) {
    onSessionListChanged(false);
  }
}
