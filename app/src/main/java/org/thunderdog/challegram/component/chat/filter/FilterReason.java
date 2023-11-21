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
 * File created on 16/11/2023
 */
package org.thunderdog.challegram.component.chat.filter;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  FilterReason.PENDING,
  FilterReason.NONE,
  FilterReason.BLOCKED_SENDER,
  FilterReason.BLOCKED_SENDER_MENTION,
  FilterReason.CONTAINS_INTERNAL_LINK,
  FilterReason.CONTAINS_EXTERNAL_LINK
})
public @interface FilterReason {
  int
    PENDING = -1,
    NONE = 0,
    BLOCKED_SENDER = 1,
    BLOCKED_SENDER_MENTION = 2,
    CONTAINS_INTERNAL_LINK = 3,
    CONTAINS_EXTERNAL_LINK = 4;
}
