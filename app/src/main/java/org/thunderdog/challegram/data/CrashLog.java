/**
 * File created on 30/08/15 at 18:12
 * Copyright Vyacheslav Krylov, 2014
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
