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
 * File created on 11/11/2018
 */
package org.thunderdog.challegram.theme;

import androidx.annotation.Nullable;

public final class ThemeCustom implements ThemeDelegate {
  @ThemeId
  private int id;
  private String wallpaper;

  private final ThemeProperties properties;
  private final ThemeColors colors;

  @Nullable
  private ThemeDelegate parentTheme;

  public ThemeCustom (@ThemeId int id) {
    this.id = id;
    this.properties = new ThemeProperties();
    this.colors = new ThemeColors();
  }

  public ThemeCustom (@ThemeId int id, ThemeCustom copy) {
    this.id = id;
    this.wallpaper = copy.wallpaper;
    this.properties = new ThemeProperties(copy.properties);
    this.colors = new ThemeColors(copy.colors);
    this.parentTheme = copy.parentTheme;
  }

  public void setId (int id) {
    this.id = id;
  }

  public void setWallpaper (String wallpaper) {
    this.wallpaper = wallpaper;
  }

  public ThemeDelegate getParentTheme () {
    return parentTheme;
  }

  @ColorId
  private int lastChangedColorId;

  public void setColor (@ColorId int colorId, @Nullable Integer color) {
    this.lastChangedColorId = colorId;
    colors.set(colorId, color);
  }

  public boolean hasRecentlyChanged (@ColorId int colorId) {
    return lastChangedColorId == colorId;
  }

  public boolean hasColor (@ColorId int colorId, boolean onlyModified) {
    Integer color = colors.get(colorId);
    return color != null && (!onlyModified || parentTheme == null || color != parentTheme.getColor(colorId));
  }

  public boolean hasProperty (@PropertyId int propertyId, boolean onlyModified) {
    Float property = properties.get(propertyId);
    return property != null && (!onlyModified || parentTheme == null || property != parentTheme.getProperty(propertyId));
  }

  private void setParentThemeImpl (@ThemeId int id) {
    this.parentTheme = ThemeSet.getBuiltinTheme(id);
    if (this.parentTheme == null)
      throw new IllegalArgumentException("Invalid themeId: " + id);
  }

  public void setProperty (@PropertyId int propertyId, @Nullable Float value) {
    if (propertyId == PropertyId.PARENT_THEME) {
      setParentThemeImpl(value != null ? value.intValue() : ThemeId.NONE);
    }
    properties.set(propertyId, value);
  }

  @Override
  public int getId () {
    return id;
  }

  @Override
  public int getColor (int colorId) {
    Integer color = colors.get(colorId);
    if (color != null)
      return color;
    if (parentTheme != null)
      return parentTheme.getColor(colorId);
    throw Theme.newError(colorId, "colorId");
  }

  @Override
  public float getProperty (int propertyId) {
    Float property = properties.get(propertyId);
    if (property != null)
      return property;
    if (parentTheme != null)
      return parentTheme.getProperty(propertyId);
    throw Theme.newError(propertyId, "propertyId");
  }

  @Override
  public String getDefaultWallpaper () {
    if (wallpaper != null)
      return wallpaper;
    Float property = properties.get(PropertyId.WALLPAPER_ID);
    if (property != null) {
      String wallpaper = TGBackground.getBackgroundForLegacyWallpaperId(property.intValue());
      if (wallpaper != null)
        return wallpaper;
    }
    if (parentTheme != null)
      return parentTheme.getDefaultWallpaper();
    throw Theme.newError(0, "wallpaper");
  }
}
