package org.thunderdog.challegram.theme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.core.StringUtils;

/**
 * Date: 11/14/18
 * Author: default
 */
public class ThemeInfo {
  private int id;
  private String name, wallpaper;
  private int parentThemeId;

  @Nullable
  private ThemeCustom loadedTheme;

  private int flags;

  public ThemeInfo (int id) {
    this.id = id;
    if (!ThemeManager.isCustomTheme(id)) {
      this.parentThemeId = (int) ThemeSet.getProperty(id, ThemeProperty.PARENT_THEME);
    }
  }

  public ThemeInfo (int id, String name, String wallpaper, int parentThemeId, int flags) {
    this.id = id;
    this.name = name;
    this.wallpaper = wallpaper;
    this.parentThemeId = parentThemeId;
    this.flags = flags;
  }

  public void copyTheme (ThemeCustom copy) {
    this.loadedTheme = new ThemeCustom(id, copy);
  }

  public boolean isCustom () {
    return ThemeManager.isCustomTheme(id);
  }

  public boolean hasParent () {
    return parentThemeId != ThemeId.NONE;
  }

  public boolean isAccent () {
    return ThemeManager.isAccentTheme(id);
  }

  public boolean isInstalled () {
    return isCustom() && (flags & Settings.THEME_FLAG_INSTALLED) != 0 && (flags & Settings.THEME_FLAG_COPY) == 0;
  }

  public int getId () {
    return id;
  }

  public int getFlags () {
    return flags;
  }

  public String getName () {
    if (ThemeManager.isCustomTheme(id)) {
      return name;
    } else {
      return Lang.getString(ThemeManager.getBuiltinThemeName(id));
    }
  }

  public String getWallpaperLink (Tdlib tdlib) {
    String data = getWallpaper();
    return StringUtils.isEmpty(data) ? null : tdlib.tMeUrl("bg/" + data);
  }

  public String getWallpaper () {
    if (ThemeManager.isCustomTheme(id)) {
      return wallpaper;
    }
    return null;
  }

  public void setName (String name) {
    if (!ThemeManager.isCustomTheme(id))
      throw new IllegalStateException();
    this.name = name;
  }

  public void setWallpaper (String wallpaper) {
    if (!ThemeManager.isCustomTheme(id))
      throw new IllegalStateException();
    this.wallpaper = wallpaper;
  }

  public int parentThemeId () {
    return parentThemeId;
  }

  public boolean hasLoadedTheme () {
    return !ThemeManager.isCustomTheme(id) || loadedTheme != null;
  }

  @NonNull
  public ThemeDelegate getTheme () {
    if (ThemeManager.isCustomTheme(id)) {
      ThemeDelegate theme = ThemeManager.instance().currentTheme(false);
      if (theme.getId() == id)
        return loadedTheme = (ThemeCustom) theme;
      if (loadedTheme == null) {
        loadedTheme = (ThemeCustom) ThemeSet.getOrLoadTheme(id, true);
      }
      return loadedTheme;
    } else {
      return ThemeSet.getBuiltinTheme(id);
    }
  }

  public void setLoadedTheme (ThemeCustom loadedTheme) {
    this.loadedTheme = loadedTheme;
  }
}
