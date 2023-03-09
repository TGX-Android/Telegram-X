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
 * File created on 18/09/2022, 17:22.
 */

package org.thunderdog.challegram.util;

import org.thunderdog.challegram.Log;

public final class TextSelection {
  public int start, end;

  public TextSelection () {
    this(-1, -1);
  }

  public TextSelection (int start, int end) {
    set(start, end);
  }

  public void set (int start, int end) {
    this.start = start;
    this.end = end;
  }

  public void apply (android.widget.EditText textView) {
    try {
      textView.setSelection(start, end);
    } catch (Throwable t) {
      Log.w("Cannot move cursor", t);
    }
  }

  public boolean isEmpty () {
    return length() == 0;
  }

  public int length () {
    return end - start;
  }
}
