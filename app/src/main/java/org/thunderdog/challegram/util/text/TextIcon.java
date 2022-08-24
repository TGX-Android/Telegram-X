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
import org.thunderdog.challegram.tool.Screen;

public class TextIcon {
  private final int width, height;
  private final long customEmojiId;

  private ImageFile miniThumbnail, thumbnail;
  private ImageFile imageFile;
  private GifFile gifFile;

  public TextIcon (int size, long customEmojiId) {
    this.width = size;
    this.height = size;
    this.customEmojiId = customEmojiId;
    // TODO extract custom emoji from memcache
  }

  public TextIcon (int width, int height, ImageFile miniThumbnail, ImageFile thumbnail, ImageFile imageFile) {
    this.width = Screen.dp(width);
    this.height = Screen.dp(height);
    this.customEmojiId = 0;
    this.miniThumbnail = miniThumbnail;
    this.thumbnail = thumbnail;
    this.imageFile = imageFile;
    this.gifFile = null;
  }

  public TextIcon (int width, int height, ImageFile miniThumbnail, ImageFile thumbnail, GifFile gifFile) {
    this.width = Screen.dp(width);
    this.height = Screen.dp(height);
    this.customEmojiId = 0;
    this.miniThumbnail = miniThumbnail;
    this.thumbnail = thumbnail;
    this.gifFile = gifFile;
    this.imageFile = null;
  }

  public String getKey () {
    if (isCustomEmoji()) {
      return "emoji_" + customEmojiId + "_" + height;
    }
    StringBuilder b = new StringBuilder();
    if (imageFile != null) {
      b.append("image_").append(imageFile);
    } else if (gifFile != null) {
      b.append("gif_").append(gifFile);
    } else {
      b.append("unknown_");
    }
    b.append(width).append("x").append(height);
    return b.toString();
  }

  public void requestFiles (ComplexReceiver receiver, int mediaKey) {
    // TODO request custom emoji
    DoubleImageReceiver preview = receiver.getPreviewReceiver(mediaKey);
    preview.requestFile(miniThumbnail, thumbnail);
    if (imageFile != null) {
      receiver.getImageReceiver(mediaKey).requestFile(imageFile);
    } else if (gifFile != null) {
      receiver.getGifReceiver(mediaKey).requestFile(gifFile);
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

  public boolean isCustomEmoji () {
    return customEmojiId != 0;
  }
}
