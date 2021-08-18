/**
 * File created on 01/03/16 at 11:16
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader.gif;

import android.view.View;

import java.lang.ref.WeakReference;

public class GifWatcherReference {
  private WeakReference<GifWatcher> reference;

  public GifWatcherReference (GifWatcher watcher) {
    reference = new WeakReference<>(watcher);
  }

  public void gifProgress (GifFile file, float progress) {
    GifWatcher watcher = reference.get();
    if (watcher != null) {
      watcher.gifProgress(file, progress);
    }
  }

  public void gifLoaded (GifFile file, GifState gif) {
    GifWatcher watcher = reference.get();
    if (watcher != null) {
      watcher.gifLoaded(file, gif);
    }
  }

  public void gifFrameChanged (GifFile file) {
    GifWatcher watcher = reference.get();
    if (watcher != null) {
      watcher.gifFrameChanged(file);
    }
  }

  public View findTargetView (GifFile file) {
    GifWatcher watcher = reference.get();
    return watcher != null ? watcher.findTargetView(file) : null;
  }
}
