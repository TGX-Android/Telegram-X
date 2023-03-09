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
 * File created on 20/11/2016
 */
package org.thunderdog.challegram.util;

import android.net.Uri;

public class RingtoneItem {
  private final int id;
  private final String name;
  private final Uri uri;
  private final boolean isDefault;

  public RingtoneItem (int id, String name, Uri uri, boolean isDefault) {
    this.id = id;
    this.name = name;
    this.uri = uri;
    this.isDefault = isDefault;
  }

  public int getId () {
    return id;
  }

  public String getName () {
    return name;
  }

  public Uri getUri () {
    return uri;
  }

  public boolean isDefault () {
    return isDefault;
  }
}
