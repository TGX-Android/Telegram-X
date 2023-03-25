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
 *
 * File created on 21/02/2018
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  ConnectionState.UNKNOWN,
  ConnectionState.CONNECTED,
  ConnectionState.CONNECTING_TO_PROXY,
  ConnectionState.CONNECTING,
  ConnectionState.UPDATING,
  ConnectionState.WAITING_FOR_NETWORK
})
public @interface ConnectionState {
  int
    UNKNOWN = -1,
    CONNECTED = 0,
    CONNECTING_TO_PROXY = 1,
    CONNECTING = 2,
    UPDATING = 3,
    WAITING_FOR_NETWORK = 4;
}
