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
 */
package org.thunderdog.challegram.theme;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public abstract class ThemeObject implements ThemeDelegate {
  @ThemeId
  protected final int id;

  protected ThemeObject (int id) {
    this.id = id;
  }

  @Override
  public boolean equals (@Nullable Object obj) {
    return obj instanceof ThemeDelegate && ((ThemeDelegate) obj).getId() == getId();
  }

  @Override
  public final int getId () {
    return id;
  }

  @Override
  public final String getDefaultWallpaper () {
    return TGBackground.getBackgroundForLegacyWallpaperId(TGBackground.getDefaultWallpaperId(id));
  }

  @Override
  public float getProperty (int propertyId) {
    switch (propertyId) {
      case ThemeProperty.WALLPAPER_ID:
        return TGBackground.getDefaultWallpaperId(id);
      case ThemeProperty.PARENT_THEME:
        return ThemeId.NONE;
    }
    throw Theme.newError(propertyId, "propertyId");
  }

  @ColorInt
  @Override
  public int getColor (int colorId) {
    throw Theme.newError(colorId, "colorId");
  }
}
