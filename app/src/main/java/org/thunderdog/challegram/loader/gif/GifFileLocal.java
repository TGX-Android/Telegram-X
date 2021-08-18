package org.thunderdog.challegram.loader.gif;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.core.StringUtils;

/**
 * Date: 7/29/17
 * Author: default
 */

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
