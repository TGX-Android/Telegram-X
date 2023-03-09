/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/05/2015 at 13:37
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
