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
 * File created on 24/08/2015 at 02:06
 */
package org.thunderdog.challegram.support;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;

public class Mp3Support {
  public static byte[] readCover (String path) {
    android.media.MediaMetadataRetriever obj = null;
    byte[] result = null;

    try {
      obj = U.openRetriever(path);
      result = obj.getEmbeddedPicture();
    } catch (Throwable t) {
      Log.w("Couldn't get the album cover", t);
    }
    U.closeRetriever(obj);

    return result;
  }
}
