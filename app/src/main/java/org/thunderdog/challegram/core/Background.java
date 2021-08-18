/**
 * File created on 28/08/15 at 17:52
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.core;

public class Background {
  private static Background instance;

  public static Background instance () {
    if (instance == null) {
      instance = new Background();
    }
    return instance;
  }

  private BaseThread thread;

  private Background () {
    thread = new BaseThread("ChallegramThread");
  }

  public void post (Runnable run) {
    thread.post(run, 0);
  }

  public void post (Runnable run, int delay) {
    thread.post(run, delay);
  }

  public BaseThread thread () {
    return thread;
  }
}
