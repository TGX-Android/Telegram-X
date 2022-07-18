package org.thunderdog.challegram.reactions;

public abstract class CancellableRunnable implements Runnable {
  protected boolean cancelled;

  public void cancel () {
    cancelled = true;
  }

  public boolean isCancelled () {
    return cancelled;
  }
}
