package org.thunderdog.challegram.filegen;

import org.thunderdog.challegram.unsorted.Settings;

/**
 * Date: 10/6/17
 * Author: default
 */

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
