/**
 * File created on 29/02/16 at 22:59
 * Copyright Vyacheslav Krylov, 2014
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
