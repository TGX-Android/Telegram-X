/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
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
  RightId.READ_MESSAGES,
  RightId.SEND_BASIC_MESSAGES,
  RightId.SEND_AUDIO,
  RightId.SEND_DOCS,
  RightId.SEND_PHOTOS,
  RightId.SEND_VIDEOS,
  RightId.SEND_VOICE_NOTES,
  RightId.SEND_VIDEO_NOTES,
  RightId.SEND_OTHER_MESSAGES,
  RightId.SEND_POLLS,
  RightId.EMBED_LINKS,
  RightId.CHANGE_CHAT_INFO,
  RightId.EDIT_MESSAGES,
  RightId.DELETE_MESSAGES,
  RightId.BAN_USERS,
  RightId.INVITE_USERS,
  RightId.PIN_MESSAGES,
  RightId.MANAGE_VIDEO_CHATS,
  RightId.ADD_NEW_ADMINS,
  RightId.REMAIN_ANONYMOUS
})
public @interface RightId {
  int
    READ_MESSAGES = R.id.right_readMessages,
    SEND_BASIC_MESSAGES = R.id.right_sendMessages,
    SEND_AUDIO = R.id.right_sendAudio,
    SEND_DOCS = R.id.right_sendDocument,
    SEND_PHOTOS = R.id.right_sendPhoto,
    SEND_VIDEOS = R.id.right_sendVideo,
    SEND_VOICE_NOTES = R.id.right_sendVoiceNote,
    SEND_VIDEO_NOTES = R.id.right_sendVideoNote,
    SEND_OTHER_MESSAGES = R.id.right_sendStickersAndGifs,
    SEND_POLLS = R.id.right_sendPolls,
    EMBED_LINKS = R.id.right_embedLinks,
    CHANGE_CHAT_INFO = R.id.right_changeChatInfo,
    EDIT_MESSAGES = R.id.right_editMessages,
    DELETE_MESSAGES = R.id.right_deleteMessages,
    BAN_USERS = R.id.right_banUsers,
    INVITE_USERS = R.id.right_inviteUsers,
    PIN_MESSAGES = R.id.right_pinMessages,
    MANAGE_VIDEO_CHATS = R.id.right_manageVideoChats,
    ADD_NEW_ADMINS = R.id.right_addNewAdmins,
    REMAIN_ANONYMOUS = R.id.right_remainAnonymous;
}
