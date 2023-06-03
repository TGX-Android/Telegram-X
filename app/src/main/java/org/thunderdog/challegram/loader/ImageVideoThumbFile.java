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
 * File created on 07/12/2016
 */
package org.thunderdog.challegram.loader;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;

public class ImageVideoThumbFile extends ImageFile {
  private int maxWidth, maxHeight;
  private long frameTimeUs = -1;
  private int videoRotation;

  public ImageVideoThumbFile (Tdlib tdlib, TdApi.File file) {
    super(tdlib, file);
    maxWidth = maxHeight = -1;
  }

  public void setVideoRotation (int rotation) {
    this.videoRotation = rotation;
  }

  public int getVideoRotation () {
    return rotation;
  }

  @Override
  public void setSize (int size) {
    super.setSize(size);
    setMaxSize(size);
  }

  public void setMaxSize (int size) {
    this.maxWidth = this.maxHeight = size;
  }

  public void setMaxSize (int maxWidth, int maxHeight) {
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
  }

  public void setFrameTimeUs (long time) {
    this.frameTimeUs = time;
  }

  public long getFrameTimeUs () {
    return frameTimeUs;
  }

  public int getMaxWidth () {
    return maxWidth;
  }

  public int getMaxHeight () {
    return maxHeight;
  }

  @Override
  public String buildImageKey () {
    StringBuilder b = buildStandardKey(new StringBuilder("video_"));
    if (maxWidth > 0 && maxHeight > 0) {
      b.append(':');
      b.append(maxWidth);
      b.append('x');
      b.append(maxHeight);
    }
    if (frameTimeUs != 0) {
      b.append(':');
      b.append(frameTimeUs);
    }
    b.append(':');
    b.append(file.local.path);
    return b.toString();
  }

  @Override
  public byte getType () {
    return TYPE_VIDEO_THUMB;
  }
}
