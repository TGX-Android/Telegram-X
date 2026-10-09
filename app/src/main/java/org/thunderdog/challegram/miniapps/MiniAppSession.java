/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import java.util.HashSet;
import java.util.Set;

/** The TDLib lease outlives a sheet being minimized, but never a final close. UI thread only. */
public final class MiniAppSession {
  public interface Release { void close (long launchId); }
  private final Release release;
  private final Set<Long> released = new HashSet<>();
  private boolean closed;
  private long launchId;

  public MiniAppSession (Release release) { this.release = release; }

  public boolean accept (long id) {
    if (closed) { releaseOnce(id); return false; }
    if (launchId != 0 && launchId != id) releaseOnce(launchId);
    launchId = id;
    return true;
  }

  public long launchId () { return launchId; }
  public boolean isClosed () { return closed; }

  public void close () {
    if (closed) return;
    closed = true;
    releaseOnce(launchId);
  }

  private void releaseOnce (long id) {
    if (id != 0 && released.add(id)) release.close(id);
  }
}
