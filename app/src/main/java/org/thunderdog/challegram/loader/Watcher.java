/**
 * File created on 06/05/15 at 13:35
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;

public interface Watcher {
  void imageLoaded (ImageFile file, boolean successful, Bitmap bitmap);
  default void imageProgress (ImageFile file, float progress) { }
}
