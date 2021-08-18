/**
 * File created on 24/08/15 at 01:59
 * Copyright Vyacheslav Krylov, 2014
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
