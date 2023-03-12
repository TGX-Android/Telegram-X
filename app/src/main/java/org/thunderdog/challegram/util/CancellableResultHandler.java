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
 * File created on 21/05/2015 at 19:54
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
