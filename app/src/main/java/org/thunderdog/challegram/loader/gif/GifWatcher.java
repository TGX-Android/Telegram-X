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
 * File created on 01/03/2016 at 11:17
 */
package org.thunderdog.challegram.loader.gif;

import android.view.View;

public interface GifWatcher {
  void gifLoaded (GifFile file, GifState state);
  default void gifProgress (GifFile file, float progress) { }
  default void gifFrameChanged (GifFile file, boolean isRestart) { }
  default View findTargetView (GifFile file) {
    return null;
  }
}
