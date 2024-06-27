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
 * File created on 14/06/2024
 */
package org.thunderdog.challegram.util;

import android.graphics.drawable.Drawable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.counter.CounterDrawableSet;

public class CounterPlaybackSpeedDrawableSet extends CounterDrawableSet {

  @Override
  public CharDrawable onCreateTextDrawableImpl (String text) {
    return new CharDrawable(get(text), text).setGap(Screen.dp(1));
  }

  private Drawable get (String text) {
    switch (text) {
      case "0": return Drawables.get(R.drawable.dot_playback_speed_font_0_10);
      case "1": return Drawables.get(R.drawable.dot_playback_speed_font_1_10);
      case "2": return Drawables.get(R.drawable.dot_playback_speed_font_2_10);
      case "3": return Drawables.get(R.drawable.dot_playback_speed_font_3_10);
      case "4": return Drawables.get(R.drawable.dot_playback_speed_font_4_10);
      case "5": return Drawables.get(R.drawable.dot_playback_speed_font_5_10);
      case "6": return Drawables.get(R.drawable.dot_playback_speed_font_6_10);
      case "7": return Drawables.get(R.drawable.dot_playback_speed_font_7_10);
      case "8": return Drawables.get(R.drawable.dot_playback_speed_font_8_10);
      case "9": return Drawables.get(R.drawable.dot_playback_speed_font_9_10);
      default: return Drawables.get(R.drawable.dot_playback_speed_font_point_10);
    }
  }
}
