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
 * File created on 17/07/2023
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;

public class TdlibException extends RuntimeException {
  private final TdApi.Error error;

  public TdlibException (@NonNull TdApi.Error error) {
    super(TD.toErrorString(error));
    this.error = error;
  }

  public TdApi.Error getError () {
    return error;
  }

  @Override
  @NonNull
  public String toString () {
    return TD.toErrorString(error);
  }
}
