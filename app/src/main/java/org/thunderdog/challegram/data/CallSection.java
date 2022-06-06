/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/03/2017
 */
package org.thunderdog.challegram.data;

import org.thunderdog.challegram.core.Lang;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class CallSection {
  private final ArrayList<CallItem> items;

  public CallSection (CallItem item) {
    this.items = new ArrayList<>();
    this.items.add(item);
  }

  public ArrayList<CallItem> getItems () {
    return items;
  }

  public void removeItem (CallItem item) {
    items.remove(item);
  }

  public boolean isEmpty () {
    return items.isEmpty();
  }

  public String getName () {
    return Lang.getRelativeMonth(items.get(0).getDate(), TimeUnit.SECONDS, false);
  }

  public static final int STATE_NONE = 0;
  public static final int STATE_INSERTED = 1;
  public static final int STATE_MERGED = 2;

  public int prependItem (CallItem item) {
    CallItem firstItem = items.get(0);
    int anchorMode = TD.getAnchorMode(item.getDate(), false);
    if (anchorMode == TD.getAnchorMode(firstItem.getDate(), false) && !TD.shouldSplitDatesByDay(anchorMode, firstItem.getDate(), item.getDate())) {
      if (!firstItem.mergeWith(item)) {
        items.add(0, item);
        return STATE_INSERTED;
      }
      return STATE_MERGED;
    }
    return STATE_NONE;
  }

  public int appendItem (CallItem item) {
    CallItem lastItem = items.get(items.size() - 1);
    int anchorMode = TD.getAnchorMode(item.getDate(), false);
    if (anchorMode == TD.getAnchorMode(lastItem.getDate(), false) && !TD.shouldSplitDatesByDay(anchorMode, lastItem.getDate(), item.getDate())) {
      if (!lastItem.mergeWith(item)) {
        items.add(item);
        return STATE_INSERTED;
      }
      return STATE_MERGED;
    }
    return STATE_NONE;
  }
}
