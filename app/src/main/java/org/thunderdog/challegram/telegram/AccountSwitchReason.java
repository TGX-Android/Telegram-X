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
 * File created on 25/02/2018
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  TdlibManager.SWITCH_REASON_UNAUTHORIZED,
  TdlibManager.SWITCH_REASON_NAVIGATION,
  TdlibManager.SWITCH_REASON_USER_CLICK,
  TdlibManager.SWITCH_REASON_CHAT_OPEN,
  TdlibManager.SWITCH_REASON_CHAT_FOCUS,
  TdlibManager.SWITCH_REASON_EXISTING_NUMBER
})
public @interface AccountSwitchReason { }