/**
 * File created on 06/05/15 at 13:37
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class WatcherReference {
  private Reference<Watcher> reference;

  public WatcherReference (Watcher watcher) {
    this.reference = new WeakReference<>(watcher);
  }

  public void imageLoaded (ImageFile file, boolean success, Bitmap bitmap) {
    Watcher watcher = reference.get();
    if (watcher != null) {
      watcher.imageLoaded(file, success, bitmap);
    }
  }

  public void imageProgress (ImageFile file, float progress) {
    Watcher watcher = reference.get();
    if (watcher != null) {
      watcher.imageProgress(file, progress);
    }
  }
}
