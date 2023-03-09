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
 * File created on 30/09/2017
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;

import org.thunderdog.challegram.U;

import java.util.HashMap;

public class ImageStrictCache {
  private static ImageStrictCache instance;

  public static ImageStrictCache instance () {
    if (instance == null) {
      synchronized (ImageStrictCache.class) {
        if (instance == null) {
          instance = new ImageStrictCache();
        }
      }
    }
    return instance;
  }

  private HashMap<ImageFile, Bitmap> map;

  public void put (ImageFile imageFile, Bitmap bitmap) {
    if (map == null) {
      map = new HashMap<>();
    }
    map.put(imageFile, bitmap);
  }

  public Bitmap remove (ImageFile imageFile) {
    return map != null ? map.remove(imageFile) : null;
  }

  public void forget (ImageFile imageFile) {
    Bitmap bitmap = remove(imageFile);
    if (U.isValidBitmap(bitmap)) {
      bitmap.recycle();
    }
  }

  public Bitmap get (ImageFile imageFile) {
    return map != null ? map.get(imageFile) : null;
  }
}
