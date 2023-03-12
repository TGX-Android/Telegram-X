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
 * File created on 12/11/2018
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

public interface DrawModifier {
  default void beforeDraw (View view, Canvas c) { }
  default void afterDraw (View view, Canvas c) { }
  default int getWidth () { return 0; }
}
