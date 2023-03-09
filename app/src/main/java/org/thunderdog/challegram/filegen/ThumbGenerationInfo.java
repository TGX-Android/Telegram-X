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
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.filegen;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.unsorted.Settings;

public class ThumbGenerationInfo extends GenerationInfo implements AbstractVideoGenerationInfo {
  private final int type;

  private int rotate;
  private long startTime;

  public ThumbGenerationInfo (long generationId, String originalPath, String destinationPath, int type, String originalConversion) {
    super(generationId, originalPath, destinationPath, originalConversion, false);
    this.type = type;
  }

  public int getType () {
    return type;
  }

  public int getRotate () {
    return rotate;
  }

  public long getStartTime () {
    return startTime;
  }

  public static final int TYPE_PHOTO = 0;
  public static final int TYPE_VIDEO = 1;
  public static final int TYPE_MUSIC = 2;

  public static String makeConversion (int type, @Nullable String sourceConversion, int targetResolution) {
    return (type == TYPE_MUSIC ? TYPE_MUSIC_THUMB : type == TYPE_VIDEO ? TYPE_VIDEO_THUMB : TYPE_PHOTO_THUMB) + (targetResolution == TdlibFileGenerationManager.SMALL_THUMB_RESOLUTION || targetResolution == 0 ? "" : Integer.toString(targetResolution)) + (sourceConversion != null ? sourceConversion : "");
  }

  @Override
  public void setVideoGenerationInfo (int sourceFileId, boolean needMute, Settings.VideoLimit videoLimit, int rotate, long startTime, long endTime, boolean noTranscoding) {
    this.rotate = rotate;
    this.startTime = startTime;
  }
}
