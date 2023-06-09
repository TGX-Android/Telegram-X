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
 * File created on 27/03/2023
 */
package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  CallNetworkType.UNKNOWN,
  CallNetworkType.MOBILE_GPRS,
  CallNetworkType.MOBILE_EDGE,
  CallNetworkType.MOBILE_3G,
  CallNetworkType.MOBILE_HSPA,
  CallNetworkType.MOBILE_LTE,
  CallNetworkType.WIFI,
  CallNetworkType.ETHERNET,
  CallNetworkType.OTHER_HIGH_SPEED,
  CallNetworkType.OTHER_LOW_SPEED,
  CallNetworkType.DIALUP,
  CallNetworkType.OTHER_MOBILE
})
public @interface CallNetworkType {
  // enum from VoIPController.h:79
  int
    UNKNOWN = 0,
    MOBILE_GPRS = 1,
    MOBILE_EDGE = 2,
    MOBILE_3G = 3,
    MOBILE_HSPA = 4,
    MOBILE_LTE = 5,
    WIFI = 6,
    ETHERNET = 7,
    OTHER_HIGH_SPEED = 8,
    OTHER_LOW_SPEED = 9,
    DIALUP = 10,
    OTHER_MOBILE = 11;
}
