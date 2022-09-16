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
 *
 * File created on 01/03/2016 at 11:16
 */
package org.thunderdog.challegram.loader.gif;

import android.view.View;

import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;

public class GifWatcherReference {
  private final WeakReference<GifWatcher> reference;

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

  @UiThread
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
