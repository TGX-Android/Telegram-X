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
 * File created on 18/04/2016 at 21:43
 */
package org.thunderdog.challegram.theme;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;

@Deprecated
public class ThemeDeprecated {
  /*public static @DrawableRes int transparentSelector () {
    return *//*TGTheme.isDark() ? R.drawable.selector_dark :*//* R.drawable.item_selector;
  }

  public static @DrawableRes int blackSelector () {
    return R.drawable.selector_black; // FIXME
  }*/

  public static @DrawableRes int headerSelector () {
    return /*TGTheme.isDark() ? R.drawable.bg_btn_header_dark :*/ R.drawable.bg_btn_header;
  }

  public static @DrawableRes int headerLightSelector () {
    return /*TGTheme.isDark() ? R.drawable.bg_btn_header_light_dark :*/ R.drawable.bg_btn_header_light;
  }
}
