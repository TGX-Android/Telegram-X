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
 * File created on 20/02/2018
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({TdlibFilesManager.DOWNLOAD_FLAG_PHOTO, TdlibFilesManager.DOWNLOAD_FLAG_VOICE, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO, TdlibFilesManager.DOWNLOAD_FLAG_FILE, TdlibFilesManager.DOWNLOAD_FLAG_MUSIC, TdlibFilesManager.DOWNLOAD_FLAG_GIF, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE})
public @interface MediaDownloadType {}
