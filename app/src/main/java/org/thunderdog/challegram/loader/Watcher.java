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
 * File created on 06/05/2015 at 13:35
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;

public interface Watcher {
  void imageLoaded (ImageFile file, boolean successful, Bitmap bitmap);
  default void imageProgress (ImageFile file, float progress) { }
}
