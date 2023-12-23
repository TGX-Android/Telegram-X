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
 * File created on 19/11/2023 at 00:51
 */
package org.thunderdog.challegram.mediaview;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({AvatarPickerMode.NONE, AvatarPickerMode.PROFILE, AvatarPickerMode.GROUP, AvatarPickerMode.CHANNEL})
public @interface AvatarPickerMode {
  int NONE = 0, PROFILE = 1, GROUP = 2, CHANNEL = 3;
}
