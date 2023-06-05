/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

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
