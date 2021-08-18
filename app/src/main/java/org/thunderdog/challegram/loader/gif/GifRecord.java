/**
 * File created on 01/03/16 at 12:14
 * Copyright Vyacheslav Krylov, 2014
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
