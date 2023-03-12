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
 * File created on 01/03/2016 at 12:14
 */
package org.thunderdog.challegram.loader.gif;

import java.util.ArrayList;

public class GifRecord {
  private GifFile file;
  private GifActor actor;
  private ArrayList<GifWatcherReference> watchers;

  public GifRecord (GifFile file, GifActor actor, GifWatcherReference reference) {
    this.file = file;
    this.actor = actor;
    this.watchers = new ArrayList<>(2);
    this.watchers.add(reference);
  }

  public GifFile getFile () {
    return file;
  }

  public GifActor getActor () {
    return actor;
  }

  public ArrayList<GifWatcherReference> getWatchers () {
    return watchers;
  }

  public void setFile (GifFile file) {
    this.file = file;
  }

  public boolean addWatcher (GifWatcherReference reference) {
    if (watchers == null || watchers.contains(reference))
      return false;
    
    actor.watcherJoined(reference);
    watchers.add(reference);

    return true;
  }

  public boolean removeWatcher (GifWatcherReference reference) {
    if (watchers == null || !watchers.contains(reference))
      return false;

    watchers.remove(reference);
    return true;
  }

  public boolean hasWatchers () {
    return watchers != null && !watchers.isEmpty();
  }

}
