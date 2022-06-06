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
 * File created on 29/07/2017
 */
package org.thunderdog.challegram.loader.gif;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.core.StringUtils;

public class GifFileLocal extends GifFile {
  private static int CURRENT_ID = ImageFile.LOCAL_START_ID;

  private String path;

  public GifFileLocal (Tdlib tdlib, String path) {
    super(tdlib, TD.newFile(CURRENT_ID--, Integer.toString(CURRENT_ID), path, 1), StringUtils.equalsOrBothEmpty(U.getExtension(path.toLowerCase()), "gif") ? TYPE_GIF : TYPE_MPEG4);
    this.path = path;
  }

  @Override
  public String makeGifKey () {
    return path;
  }
}
