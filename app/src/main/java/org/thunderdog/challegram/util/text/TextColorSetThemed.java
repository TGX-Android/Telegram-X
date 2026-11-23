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
 */
package org.thunderdog.challegram.util.text;

import androidx.annotation.ColorInt;

import org.thunderdog.challegram.theme.ColorId;

public interface TextColorSetThemed extends TextColorSet {
  @ColorId
  int defaultTextColorId ();
  @ColorId
  default int iconColorId () {
    return defaultTextColorId();
  }
  @ColorId
  default int clickableTextColorId (boolean isPressed) {
    return defaultTextColorId();
  }
  @ColorId
  default int pressedBackgroundColorId () {
    return 0;
  }
  @ColorId
  default int staticBackgroundColorId () {
    return 0;
  }
  @ColorId
  default int backgroundColorId (boolean isPressed) {
    return isPressed ? pressedBackgroundColorId() : staticBackgroundColorId();
  }
  @ColorId
  default int outlineColorId (boolean isPressed) {
    return 0;
  }
  @ColorInt
  default int defaultTextColor () {
    int colorId = defaultTextColorId();
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @Override
  default int iconColor () {
    int colorId = iconColorId();
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @ColorInt
  default int clickableTextColor (boolean isPressed) {
    int colorId = clickableTextColorId(isPressed);
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @ColorInt
  default int backgroundColor (boolean isPressed) {
    int colorId = backgroundColorId(isPressed);
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
  @ColorInt
  default int outlineColor (boolean isPressed) {
    int colorId = outlineColorId(isPressed);
    return colorId != 0 ? colorTheme().getColor(colorId) : 0;
  }
}
