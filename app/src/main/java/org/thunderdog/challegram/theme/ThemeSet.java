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
 * File created on 06/11/2018
 */
package org.thunderdog.challegram.theme;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.theme.builtin.ThemeBlackWhite;
import org.thunderdog.challegram.theme.builtin.ThemeClassic;
import org.thunderdog.challegram.theme.builtin.ThemeCyan;
import org.thunderdog.challegram.theme.builtin.ThemeDefault;
import org.thunderdog.challegram.theme.builtin.ThemeGreen;
import org.thunderdog.challegram.theme.builtin.ThemeNightBlack;
import org.thunderdog.challegram.theme.builtin.ThemeNightBlue;
import org.thunderdog.challegram.theme.builtin.ThemeOrange;
import org.thunderdog.challegram.theme.builtin.ThemePink;
import org.thunderdog.challegram.theme.builtin.ThemeRed;
import org.thunderdog.challegram.theme.builtin.ThemeWhiteBlack;
import org.thunderdog.challegram.unsorted.Settings;

public class ThemeSet {
  public static ThemeDelegate getOrLoadTheme (@ThemeId int themeId, boolean cache) {
    return getOrLoadTheme(Settings.instance(), themeId, cache);
  }
  public static ThemeDelegate getOrLoadTheme (Settings prefs, @ThemeId int themeId, boolean cache) {
    return themeId > ThemeId.CUSTOM ? getBuiltinTheme(themeId) : loadTheme(prefs, themeId);
  }

  public static @ColorInt int getColor (@ThemeId int themeId, @ThemeColorId int colorId) {
    if (themeId > ThemeId.CUSTOM)
      return getOrLoadTheme(themeId, true).getColor(colorId);
    else
      return Settings.instance().getCustomThemeColor(ThemeManager.resolveCustomThemeId(themeId), colorId);
  }

  public static float getProperty (@ThemeId int themeId, @ThemeProperty int propertyId) {
    return getProperty(Settings.instance(), themeId, propertyId);
  }

  public static float getProperty (Settings prefs, @ThemeId int themeId, @ThemeProperty int propertyId) {
    if (themeId > ThemeId.CUSTOM)
      return getOrLoadTheme(prefs, themeId, true).getProperty(propertyId);
    else
      return prefs.getCustomThemeProperty(ThemeManager.resolveCustomThemeId(themeId), propertyId);
  }

  public static String getDefaultWallpaper (@ThemeId int themeId) {
    if (themeId > ThemeId.CUSTOM)
      return getOrLoadTheme(themeId, true).getDefaultWallpaper();
    String wallpaper = Settings.instance().getCustomThemeWallpaper(ThemeManager.resolveCustomThemeId(themeId));
    if (wallpaper != null)
      return wallpaper;
    return TGBackground.getBackgroundForLegacyWallpaperId((int) getProperty(themeId, ThemeProperty.WALLPAPER_ID));
  }

  private static ThemeDelegate[] builtinThemes;

  public static ThemeDelegate getBuiltinTheme (@ThemeId int themeId) {
    if (themeId <= ThemeId.NONE)
      throw new IllegalArgumentException("themeId == " + themeId);
    if (builtinThemes == null) {
      synchronized (ThemeSet.class) {
        if (builtinThemes == null) {
          builtinThemes = new ThemeDelegate[ThemeId.COUNT];
        }
      }
    }
    int index = themeId - ThemeId.ID_MIN;
    ThemeDelegate theme = builtinThemes[index];
    if (theme == null) {
      theme = loadTheme(null, themeId);
      builtinThemes[index] = theme;
    }
    return theme;
  }

  private static @NonNull ThemeDelegate loadTheme (Settings prefs, @ThemeId int themeId) {
    switch (themeId) {
      case ThemeId.BLUE:
        return new ThemeDefault();
      case ThemeId.CLASSIC:
        return new ThemeClassic();
      case ThemeId.CYAN:
        return new ThemeCyan();
      case ThemeId.GREEN:
        return new ThemeGreen();
      case ThemeId.ORANGE:
        return new ThemeOrange();
      case ThemeId.PINK:
        return new ThemePink();
      case ThemeId.RED:
        return new ThemeRed();
      case ThemeId.WHITE_BLACK:
        return new ThemeWhiteBlack();

      case ThemeId.NIGHT_BLUE:
        return new ThemeNightBlue();
      case ThemeId.NIGHT_BLACK:
        return new ThemeNightBlack();
      case ThemeId.BLACK_WHITE:
        return new ThemeBlackWhite();

      case ThemeId.NONE:
      case ThemeId.TEMPORARY:
        break;

      case ThemeId.CUSTOM:
      default: {
        int customThemeId = ThemeManager.resolveCustomThemeId(themeId);
        if (customThemeId > ThemeId.NONE) {
          return prefs.loadCustomTheme(customThemeId);
        }
      }
    }
    throw new IllegalArgumentException("themeId == " + themeId);
  }
}
