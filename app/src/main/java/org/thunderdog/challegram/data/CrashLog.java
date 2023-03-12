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
 * File created on 30/08/2015 at 18:12
 */
package org.thunderdog.challegram.data;

import java.io.File;

public class CrashLog {
  private final long crashId;
  private File file;

  public CrashLog (long crashId, File file) {
    this.crashId = crashId;
    this.file = file;
  }

  public long getId () {
    return crashId;
  }

  public void deleteFile () {
    if (file != null && file.delete()) {
      file = null;
    }
  }

  public String getFile () {
    return file.getPath();
  }
}
