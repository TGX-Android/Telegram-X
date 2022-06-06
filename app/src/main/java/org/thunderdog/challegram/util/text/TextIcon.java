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
 */
package org.thunderdog.challegram.util.text;

import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;

public class TextIcon {
  private final int width, height;
  private final ImageFile miniThumbnail, thumbnail;
  private final ImageFile imageFile;
  private final GifFile gifFile;

  public TextIcon (int width, int height, ImageFile miniThumbnail, ImageFile thumbnail, ImageFile imageFile) {
    this.width = width;
    this.height = height;
    this.miniThumbnail = miniThumbnail;
    this.thumbnail = thumbnail;
    this.imageFile = imageFile;
    this.gifFile = null;
  }

  public TextIcon (int width, int height, ImageFile miniThumbnail, ImageFile thumbnail, GifFile gifFile) {
    this.width = width;
    this.height = height;
    this.miniThumbnail = miniThumbnail;
    this.thumbnail = thumbnail;
    this.gifFile = gifFile;
    this.imageFile = null;
  }

  public void requestFiles (int key, ComplexReceiver receiver) {
    DoubleImageReceiver preview = receiver.getPreviewReceiver(key);
    preview.requestFile(miniThumbnail, thumbnail);
    if (imageFile != null) {
      receiver.getImageReceiver(key).requestFile(imageFile);
    } else if (gifFile != null) {
      receiver.getGifReceiver(key).requestFile(gifFile);
    }
  }

  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return height;
  }

  public boolean isImage () {
    return imageFile != null;
  }

  public boolean isGif () {
    return gifFile != null;
  }
}
