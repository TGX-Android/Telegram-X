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

public class TextColorSetOverride implements TextColorSet {
  private final TextColorSet colorSet;

  public TextColorSetOverride (TextColorSet colorSet) {
    this.colorSet = colorSet;
  }

  public TextColorSet originalColorSet () {
    return colorSet;
  }

  @Override
  public int defaultTextColor () {
    return colorSet.defaultTextColor();
  }

  @Override
  public long mediaTextComplexColor () {
    return colorSet.mediaTextComplexColor();
  }

  @Override
  public int iconColor () {
    return colorSet.iconColor();
  }

  @Override
  public int clickableTextColor (boolean isPressed) {
    return colorSet.clickableTextColor(isPressed);
  }

  @Override
  public int backgroundColor (boolean isPressed) {
    return colorSet.backgroundColor(isPressed);
  }

  @Override
  public int outlineColor (boolean isPressed) {
    return colorSet.outlineColor(isPressed);
  }

  @Override
  public int backgroundColorId (boolean isPressed) {
    return colorSet.backgroundColorId(isPressed);
  }

  @Override
  public int outlineColorId (boolean isPressed) {
    return colorSet.outlineColorId(isPressed);
  }
}
