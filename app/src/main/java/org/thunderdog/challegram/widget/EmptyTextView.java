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
 * File created on 23/02/2016 at 11:58
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;

public class EmptyTextView extends NoScrollTextView {
  public EmptyTextView (Context context) {
    super(context);

    setTextColor(Theme.textDecent2Color());

    setTypeface(Fonts.getRobotoRegular());
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    setGravity(Gravity.CENTER);
  }

  private int lastSetTextColor;

  @Override
  public void setTextColor (int color) {
    this.lastSetTextColor = color;
    super.setTextColor(color);
  }

  public void setTextColorIfNeeded (int color) {
    if (this.lastSetTextColor != color) {
      this.lastSetTextColor = color;
      super.setTextColor(color);
    }
  }
}
