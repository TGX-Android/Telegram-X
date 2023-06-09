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
 * File created on 28/03/2023
 */
package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  CallError.UNKNOWN,
  CallError.INCOMPATIBLE,
  CallError.TIMEOUT,
  CallError.AUDIO_IO,
  CallError.PROXY,

  CallError.PEER_OUTDATED,
  CallError.PRIVACY,
  CallError.LOCALIZED,
  CallError.INSECURE_UPGRADE,
  CallError.CONNECTION_SERVICE
})
public @interface CallError {
  // VoIPController.h:70
  int
    UNKNOWN = 0,
    INCOMPATIBLE = 1,
    TIMEOUT = 2,
    AUDIO_IO = 3,
    PROXY = 4,

  // local error codes (unused)

    PEER_OUTDATED = -1,
    PRIVACY = -2,
    LOCALIZED = -3,
    INSECURE_UPGRADE = -4,
    CONNECTION_SERVICE = -5;
}
