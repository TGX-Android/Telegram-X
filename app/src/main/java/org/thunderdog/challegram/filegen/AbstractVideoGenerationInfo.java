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
 * File created on 10/06/2017
 */
package org.thunderdog.challegram.filegen;

import org.thunderdog.challegram.unsorted.Settings;

public interface AbstractVideoGenerationInfo {
  String PREFIX_ROTATE = "rotate";
  String PREFIX_RANDOM = "random";
  String PREFIX_LAST_MODIFIED = "modified";
  String PREFIX_START = "start";
  String PREFIX_END = "end";
  String PREFIX_SOURCE_FILE_ID = "source";
  String PREFIX_NO_TRANSCODING = "noconvert";
  String PREFIX_QUALITY = "limit";
  String PREFIX_FRAME_RATE = "fps";
  String PREFIX_BITRATE = "bitrate";

  void setVideoGenerationInfo (int sourceFileId,
                               boolean needMute,
                               Settings.VideoLimit videoLimit,
                               int rotate,
                               long startTime, long endTime,
                               boolean noTranscoding);
}
