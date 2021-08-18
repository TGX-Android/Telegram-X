package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;

import org.thunderdog.challegram.U;

import java.util.HashMap;

/**
 * Date: 9/30/17
 * Author: default
 */

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
