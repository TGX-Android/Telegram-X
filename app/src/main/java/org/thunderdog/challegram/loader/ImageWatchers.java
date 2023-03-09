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
 * File created on 06/05/2015 at 14:30
 */
package org.thunderdog.challegram.loader;

import java.util.ArrayList;

public class ImageWatchers {
  private ImageFile file;
  private ImageActor actor;
  private ArrayList<WatcherReference> watcherList;

  public ImageWatchers (ImageFile file, ImageActor actor, WatcherReference reference) {
    this.file = file;
    this.actor = actor;
    this.watcherList = new ArrayList<>(2);
    this.watcherList.add(reference);
  }

  public ImageFile getFile () {
    return file;
  }

  public ImageActor getActor () {
    return actor;
  }

  public ArrayList<WatcherReference> getWatchers () {
    return watcherList;
  }

  public void setFile (ImageFile file) {
    this.file = file;
  }

  public boolean addWatcher (WatcherReference reference) {
    if (watcherList == null || watcherList.contains(reference))
      return false;

    actor.watcherJoined(reference);
    watcherList.add(reference);

    return true;
  }

  public boolean removeWatcher (WatcherReference reference) {
    if (watcherList == null || !watcherList.contains(reference))
      return false;

    watcherList.remove(reference);
    return true;
  }

  public boolean hasWatchers () {
    return watcherList != null && !watcherList.isEmpty();
  }
}
