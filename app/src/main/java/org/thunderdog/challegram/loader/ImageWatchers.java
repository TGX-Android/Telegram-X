/**
 * File created on 06/05/15 at 14:30
 * Copyright Vyacheslav Krylov, 2014
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
