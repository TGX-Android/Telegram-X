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
  DataSavingOption.NEVER,
  DataSavingOption.MOBILE,
  DataSavingOption.ALWAYS,
  DataSavingOption.ROAMING
})
public @interface DataSavingOption {
  // enum from VoIPController.h:93
  int
    NEVER = 0,
    MOBILE = 1,
    ALWAYS = 2,
    ROAMING = 3 /*this field is not present in VoIPController.h and is converted to MOBILE or NEVER*/;
}
