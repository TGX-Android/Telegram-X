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
