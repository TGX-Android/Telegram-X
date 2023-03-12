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
 * File created on 20/11/2016
 */
package org.thunderdog.challegram.util;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;

public class SimpleStringItem {
  private final @IdRes int id;
  private final @StringRes int stringRes;
  private final @Nullable String string;

  private long arg1, arg2;

  public SimpleStringItem (int id, int stringRes) {
    this.id = id;
    this.stringRes = stringRes;
    this.string = null;
  }

  public SimpleStringItem (int id, @NonNull String string) {
    this.id = id;
    this.stringRes = 0;
    this.string = string;
  }

  public SimpleStringItem setArg1 (long arg1) {
    this.arg1 = arg1;
    return this;
  }

  public SimpleStringItem setArgs (long arg1, long arg2) {
    this.arg1 = arg1;
    this.arg2 = arg2;
    return this;
  }

  public int getId () {
    return id;
  }

  public long getArg1 () {
    return arg1;
  }

  public long getArg2 () {
    return arg2;
  }

  @Override
  public String toString () {
    return stringRes != 0 ? Lang.getString(stringRes) : string;
  }
}
