package org.thunderdog.challegram.theme;

import androidx.annotation.Nullable;

/**
 * Date: 21/01/2017
 * Author: default
 */
public interface ThemeChangeListener {
  boolean needsTempUpdates ();
  void onThemeColorsChanged (boolean areTemp, @Nullable ColorState state);
  default void onThemeChanged (ThemeDelegate fromTheme, ThemeDelegate toTheme) { }
  default void onThemeAutoNightModeChanged (int autoNightMode) { }
  default void onThemePropertyChanged (int themeId, @ThemeProperty int propertyId, float value, boolean isDefault) { }
}
