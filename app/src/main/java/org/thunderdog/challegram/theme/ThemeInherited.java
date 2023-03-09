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
 * File created on 06/11/2018
 */
package org.thunderdog.challegram.theme;

public abstract class ThemeInherited implements ThemeDelegate {
  protected final int id;
  protected final ThemeDelegate parentTheme;

  public ThemeInherited (int themeId, @ThemeId int parentThemeId) {
    this.id = themeId;
    this.parentTheme = ThemeSet.getBuiltinTheme(parentThemeId);
    if (this.parentTheme == null)
      throw new IllegalArgumentException("parentThemeId == " + themeId);
  }

  @Override
  public int getId () {
    return id;
  }

  @Override
  public float getProperty (int propertyId) {
    if (propertyId == ThemeProperty.PARENT_THEME)
      return parentTheme.getId();
    return parentTheme.getProperty(propertyId);
  }

  @Override
  public int getColor (int colorId) {
    return parentTheme.getColor(colorId);
  }

  @Override
  public String getDefaultWallpaper () {
    return parentTheme.getDefaultWallpaper();
  }
}
