/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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

import androidx.annotation.ColorInt;

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
