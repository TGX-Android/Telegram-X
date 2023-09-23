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
  RightId.POST_STORIES,
  RightId.EDIT_STORIES,
  RightId.DELETE_STORIES,
  RightId.ADD_NEW_ADMINS,
  RightId.REMAIN_ANONYMOUS
})
public @interface RightId {
  int
    READ_MESSAGES = 1,
    SEND_BASIC_MESSAGES = 2,
    SEND_AUDIO = 3,
    SEND_DOCS = 4,
    SEND_PHOTOS = 5,
    SEND_VIDEOS = 6,
    SEND_VOICE_NOTES = 7,
    SEND_VIDEO_NOTES = 8,
    SEND_OTHER_MESSAGES = 9,
    SEND_POLLS = 10,
    EMBED_LINKS = 11,
    CHANGE_CHAT_INFO = 12,
    EDIT_MESSAGES = 13,
    DELETE_MESSAGES = 14,
    BAN_USERS = 15,
    INVITE_USERS = 16,
    PIN_MESSAGES = 17,
    MANAGE_VIDEO_CHATS = 18,
    POST_STORIES = 19,
    EDIT_STORIES = 20,
    DELETE_STORIES = 21,
    ADD_NEW_ADMINS = 22,
    REMAIN_ANONYMOUS = 23;
}
