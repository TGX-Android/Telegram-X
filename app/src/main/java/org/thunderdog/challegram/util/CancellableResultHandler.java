/**
 * File created on 21/05/15 at 19:54
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;

public abstract class CancellableResultHandler implements Client.ResultHandler {
  private volatile boolean isCancelled;

  public final void cancel () {
    synchronized (this) {
      isCancelled = true;
    }
  }

  public final void onResult (TdApi.Object object) {
    synchronized (this) {
      if (!isCancelled) {
        processResult(object);
      }
    }
  }

  public final boolean isCancelled () {
    synchronized (this) {
      return isCancelled;
    }
  }

  public abstract void processResult (TdApi.Object object);
}
