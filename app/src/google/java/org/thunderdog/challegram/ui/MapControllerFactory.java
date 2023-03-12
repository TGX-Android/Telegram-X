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
 * File created on 22/10/2022
 */
package org.thunderdog.challegram.ui;

import android.content.Context;

import org.thunderdog.challegram.telegram.Tdlib;

public class MapControllerFactory {
  public static MapController<?,?> newMapController (Context context, Tdlib tdlib) {
    return new MapGoogleController(context, tdlib);
  }
}
