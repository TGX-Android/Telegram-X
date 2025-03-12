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
 * File created on 17/07/2023
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({ChatFolderStyle.LABEL_ONLY, ChatFolderStyle.ICON_ONLY, ChatFolderStyle.LABEL_AND_ICON, ChatFolderStyle.ICON_WITH_LABEL_ON_ACTIVE_FOLDER})
public @interface ChatFolderStyle {
  int
    LABEL_ONLY = 0,
    ICON_ONLY = 1,
    LABEL_AND_ICON = 2,
    ICON_WITH_LABEL_ON_ACTIVE_FOLDER = 3;
}
