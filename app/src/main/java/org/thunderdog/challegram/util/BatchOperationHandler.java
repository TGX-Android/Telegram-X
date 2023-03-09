/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 14/09/2022, 21:38.
 */

package org.thunderdog.challegram.util;

import android.os.Handler;
import android.os.Looper;

public class BatchOperationHandler {
  private final Handler handler;
  private final Runnable operation;
  private final long batchDelay;

  public BatchOperationHandler (Runnable operation, long batchDelay) {
    this.operation = operation;
    this.batchDelay = batchDelay;
    this.handler = new Handler(Looper.getMainLooper(), msg -> {
      onPerformOperation();
      return true;
    });
  }

  protected long batchOperationDelay () {
    // Override for dynamic behavior
    return batchDelay;
  }

  private boolean isScheduled;

  private void onPerformOperation () {
    isScheduled = false;
    operation.run();
  }

  public void performOperation () {
    if (isScheduled) {
      handler.removeMessages(0);
      isScheduled = false;
    }
    handler.sendEmptyMessageDelayed(0, batchOperationDelay());
  }
}
