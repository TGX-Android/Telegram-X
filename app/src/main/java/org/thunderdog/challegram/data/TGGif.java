/**
 * File created on 27/02/16 at 21:34
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.telegram.Tdlib;

public class TGGif {
  private TdApi.Animation animation;
  private GifFile gif;
  private ImageFile file;

  public TGGif (Tdlib tdlib, TdApi.Animation animation) {
    this.animation = animation;
    this.file = TD.toImageFile(tdlib, animation.thumbnail); // TODO MPEG4 support
    if (this.file != null) {
      this.file.setScaleType(ImageFile.CENTER_CROP);
      this.file.setNeedCancellation(false);
    }
    this.gif = new GifFile(tdlib, animation);
    this.gif.setScaleType(GifFile.CENTER_CROP);
  }

  public int getId () {
    return animation.animation.id;
  }

  public ImageFile getImage () {
    return file;
  }

  public GifFile getGif () {
    return gif;
  }

  public TdApi.Animation getAnimation () {
    return animation;
  }

  public int width () {
    return animation.width != 0 ? animation.width : animation.thumbnail != null ? animation.thumbnail.width : 0;
  }

  public int height () {
    return animation.height != 0 ? animation.height : animation.thumbnail != null ? animation.thumbnail.height : 0;
  }
}
