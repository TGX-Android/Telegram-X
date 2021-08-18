package org.thunderdog.challegram.theme;

import androidx.annotation.ColorInt;

/**
 * Date: 11/6/18
 * Author: default
 */
public interface ThemeDelegate {
  @ThemeId int getId ();
  @ColorInt int getColor (@ThemeColorId int colorId);

  String getDefaultWallpaper ();
  float getProperty (@ThemeProperty int propertyId);
  default boolean isDark () {
    return getProperty(ThemeProperty.DARK) == 1f;
  }
  default boolean needLightStatusBar () {
    return getProperty(ThemeProperty.LIGHT_STATUS_BAR) == 1f;
  }
}
