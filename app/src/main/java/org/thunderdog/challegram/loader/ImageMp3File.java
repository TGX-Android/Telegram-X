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
 * File created on 24/08/2015 at 01:59
 */
package org.thunderdog.challegram.loader;

import org.thunderdog.challegram.data.TD;

public class ImageMp3File extends ImageFile {
  private static int CURRENT_ID = ImageFile.MP3_START_ID;
  private String path;

  public ImageMp3File (String path) {
    super(null, TD.newFile(CURRENT_ID--, Integer.toString(CURRENT_ID), path, 1));
    this.path = path;
  }

  @Override
  public int getId () {
    return path.hashCode();
  }

  public String getPath () {
    return path;
  }

  @Override
  protected String buildImageKey () {
    return getSize() != 0 ? path + "?" + getSize() : path;
  }

  @Override
  public byte getType () {
    return TYPE_MP3;
  }
}
