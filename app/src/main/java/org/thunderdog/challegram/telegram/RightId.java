/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 29/06/2019
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import org.thunderdog.challegram.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  R.id.right_readMessages,
  R.id.right_sendMessages,
  R.id.right_sendMedia,
  R.id.right_sendStickersAndGifs,
  R.id.right_sendPolls,
  R.id.right_embedLinks,
  R.id.right_changeChatInfo,
  R.id.right_editMessages,
  R.id.right_deleteMessages,
  R.id.right_banUsers,
  R.id.right_inviteUsers,
  R.id.right_pinMessages,
  R.id.right_addNewAdmins,
  R.id.right_sendVoiceVideo // Fake right id for UserFullInfo.hasRestrictedVoiceAndVideoNoteMessages
})
public @interface RightId {}
