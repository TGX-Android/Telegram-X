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
 * File created on 04/08/2018
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.widget.TextView;

@Deprecated
public class NoScrollTextView extends TextView {
  public NoScrollTextView (Context context) {
    super(context);
  }

  @Override
  public boolean canScrollHorizontally (int direction) {
    return false;
  }

  @Override
  public boolean canScrollVertically (int direction) {
    return false;
  }
}
