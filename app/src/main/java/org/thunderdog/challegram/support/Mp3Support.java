/**
 * File created on 24/08/15 at 02:06
 * Copyright Vyacheslav Krylov, 2014
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
