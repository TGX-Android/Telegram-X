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
 * File created on 29/02/2016 at 22:59
 */
package org.thunderdog.challegram.loader.gif;

import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;

public class GifFileRemote extends GifFile {
  public GifFileRemote (Tdlib tdlib, String url, int type) {
    super(tdlib, TD.newFile(0, url, url, 0), type);
  }

  @Override
  protected String makeGifKey () {
    return tdlib.id() + "_" + file.remote.id;
  }
}
