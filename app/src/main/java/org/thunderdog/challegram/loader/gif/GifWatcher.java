/**
 * File created on 01/03/16 at 11:17
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader.gif;

import android.view.View;

public interface GifWatcher {
  void gifLoaded (GifFile file, GifState state);
  default void gifProgress (GifFile file, float progress) { }
  default void gifFrameChanged (GifFile file) { }
  default View findTargetView (GifFile file) {
    return null;
  }
}
