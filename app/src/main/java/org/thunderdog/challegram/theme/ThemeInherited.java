package org.thunderdog.challegram.theme;

/**
 * Date: 11/6/18
 * Author: default
 */
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
