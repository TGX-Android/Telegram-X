package org.thunderdog.challegram.util;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;

/**
 * Date: 20/11/2016
 * Author: default
 */

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
