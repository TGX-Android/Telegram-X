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
 * File created on 14/01/2017
 */
package org.thunderdog.challegram.theme;

import android.app.AlertDialog;
import android.graphics.Paint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;
import me.vkryl.core.reference.ReferenceList;

public class ThemeManager implements FactorAnimator.Target, GlobalAccountListener {
  public static final int DEFAULT_DARK_THEME = ThemeId.NIGHT_BLUE;
  public static final int DEFAULT_LIGHT_THEME = ThemeId.CLASSIC;
  public static final int DEFAULT_THEME = DEFAULT_DARK_THEME;

  public static final int CHAT_STYLE_UNKNOWN = 0;
  public static final int CHAT_STYLE_MODERN = 1; // default
  public static final int CHAT_STYLE_BUBBLES = 2;

  // public static final int CHAT_STYLE_DEFAULT = CHAT_STYLE_BUBBLES;

  // When adding a new built-in theme, check that the id available in:
  // ThemeId.java
  // ThemeColorId.java

  public static boolean isCustomTheme (@ThemeId int themeId) {
    return resolveCustomThemeId(themeId) != ThemeId.NONE;
  }

  @SuppressWarnings("SwitchIntDef")
  public static boolean isAccentTheme (@ThemeId int themeId) {
    switch (themeId) {
      case ThemeId.BLUE:
      case ThemeId.CYAN:
      case ThemeId.GREEN:
      case ThemeId.ORANGE:
      case ThemeId.PINK:
      case ThemeId.RED:
        return true;
    }
    return false;
  }

  public static ThemeDelegate currentThemeFast () {
    return instance != null ? instance.currentTheme(true, true) : null;
  }

  @StringRes
  public static int getBuiltinThemeName (@ThemeId final int themeId) {
    switch (themeId) {
      case ThemeId.TEMPORARY:
      case ThemeId.NONE:
        break;
      case ThemeId.CLASSIC:
        return R.string.ThemeClassic;
      case ThemeId.BLUE:
        return R.string.ThemeBlue;
      case ThemeId.RED:
        return R.string.ThemeRed;
      case ThemeId.NIGHT_BLACK:
        return R.string.ThemeNight;
      case ThemeId.NIGHT_BLUE:
        return R.string.ThemeNightBlue;
      case ThemeId.BLACK_WHITE:
        return R.string.ThemeBlackWhite;
      case ThemeId.CUSTOM:
        return R.string.ThemeCustom;
      case ThemeId.CYAN:
        return R.string.ThemeCyan;
      case ThemeId.GREEN:
        return R.string.ThemeGreen;
      case ThemeId.ORANGE:
        return R.string.ThemeOrange;
      case ThemeId.PINK:
        return R.string.ThemePink;
      case ThemeId.WHITE_BLACK:
        return R.string.ThemeWhiteBlack;
    }
    throw Theme.newError(themeId, "themeId");
  }

  public List<ThemeInfo> getAccentThemes () {
    int[] accentThemeIds = new int[] {
      ThemeId.BLUE,
      ThemeId.RED,
      ThemeId.ORANGE,
      ThemeId.GREEN,
      ThemeId.PINK,
      ThemeId.CYAN,
    };
    List<ThemeInfo> themes = new ArrayList<>(accentThemeIds.length);
    for (int themeId : accentThemeIds) {
      themes.add(new ThemeInfo(themeId));
    }
    return themes;
  }

  public List<ThemeInfo> getBuiltinThemes () {
    int[] builtInThemeIds = BuildConfig.DEBUG ? new int[] {
      ThemeId.CLASSIC,
      ThemeId.BLUE,
      ThemeId.RED,
      ThemeId.ORANGE,
      ThemeId.GREEN,
      ThemeId.PINK,
      ThemeId.CYAN,
      ThemeId.NIGHT_BLUE,
      ThemeId.NIGHT_BLACK,
      ThemeId.BLACK_WHITE,
      ThemeId.WHITE_BLACK,
    } : new int[] {
      ThemeId.CLASSIC,
      ThemeId.BLUE,
      ThemeId.RED,
      ThemeId.ORANGE,
      ThemeId.GREEN,
      ThemeId.PINK,
      ThemeId.CYAN,
      ThemeId.NIGHT_BLUE,
      ThemeId.NIGHT_BLACK
    };
    List<ThemeInfo> themes = new ArrayList<>(builtInThemeIds.length);
    for (int themeId : builtInThemeIds) {
      themes.add(new ThemeInfo(themeId));
    }
    return themes;
  }

  public int newCustomTheme (@NonNull String name, @ThemeId int themeId, int inheritFromThemeId) {
    int customThemeId = Settings.instance().addNewTheme(name, themeId, inheritFromThemeId != ThemeId.NONE && isCustomTheme(inheritFromThemeId) ? resolveCustomThemeId(inheritFromThemeId) : 0, null);
    return serializeCustomThemeId(customThemeId);
  }

  public int installCustomTheme (TdlibUi.ImportedTheme theme) {
    int customThemeId = Settings.instance().installTheme(theme);
    return customThemeId != 0 ? serializeCustomThemeId(customThemeId) : ThemeId.NONE;
  }

  public void removeCustomTheme (Tdlib tdlib, int themeId, int parentThemeId, Runnable after) {
    if (!isCustomTheme(themeId))
      return;
    Runnable onDone = () -> {
      int usageId = Theme.getWallpaperIdentifier(themeId);
      Settings.instance().removeCustomTheme(resolveCustomThemeId(themeId));
      U.run(after);
      TdlibManager.instance().replaceThemeId(themeId, parentThemeId);
      if (usageId >= 2) {
        TdlibManager.instance().deleteWallpaper(usageId);
      }
    };
    if (isCurrentTheme(themeId)) {
      changeGlobalTheme(tdlib, getTheme(parentThemeId), false, onDone);
    } else {
      onDone.run();
    }
  }

  public static void addThemeListener (Paint paint, @ThemeColorId int colorIdRes) {
    // TODO get rid of this
    instance().globalThemeListenerEntries.add(new ThemeListenerEntry(ThemeListenerEntry.MODE_PAINT_COLOR, colorIdRes, paint));
  }

  // Singleton stuff

  private static volatile ThemeManager instance;

  public static ThemeManager instance () {
    if (instance == null) {
      synchronized (ThemeManager.class) {
        if (instance == null) {
          instance = new ThemeManager();
        }
      }
    }
    return instance;
  }

  // Impl

  @Nullable
  private Tdlib _tdlib;

  @Nullable
  private ThemeDelegate _currentTheme;

  private ThemeDelegate currentThemeImpl (boolean fast) {
    if (_currentTheme == null && !fast) {
      Tdlib tdlib = TdlibManager.instance().currentNoWakeup();
      int currentThemeId = tdlib.settings().globalTheme();
      if (Settings.instance().getNightMode() == Settings.NIGHT_MODE_SCHEDULED) {
        if (Settings.instance().inNightSchedule()) {
          tdlib.settings().setGlobalTheme(currentThemeId = tdlib.settings().globalNightTheme());
        } else {
          tdlib.settings().setGlobalTheme(currentThemeId = tdlib.settings().globalDaylightTheme());
        }
      }
      this._tdlib = tdlib;
      this._currentTheme = getTheme(currentThemeId);
    }
    return _currentTheme;
  }

  private final List<ThemeListenerEntry> globalThemeListenerEntries = new ArrayList<>();
  private final ReferenceList<ThemeChangeListener> themeChangeListeners = new ReferenceList<>();
  private final ReferenceList<ChatStyleChangeListener> chatStyleChangeListeners = new ReferenceList<>();

  private ThemeManager () {
    TdlibManager.instance().global().addAccountListener(this);
  }

  public @NonNull ThemeDelegate currentTheme () {
    return currentTheme(true);
  }

  public @NonNull ThemeDelegate appliedTheme () {
    return currentTheme(false);
  }

  public @NonNull ThemeDelegate previousTheme () {
    ThemeDelegate theme = currentTheme(true);
    if (theme instanceof ThemeTemporary) {
      return ((ThemeTemporary) theme).getFromTheme();
    } else {
      return theme;
    }
  }

  public @ThemeId int currentThemeId () {
    return currentTheme(false).getId();
  }

  public boolean isCurrentThemeDark () {
    return currentTheme(false).isDark();
  }

  public boolean needLightStatusBar () {
    return currentTheme(false).needLightStatusBar();
  }

  public @NonNull ThemeDelegate currentTheme (boolean allowTemp) {
    return currentTheme(allowTemp, false);
  }

  public ThemeDelegate currentTheme (boolean allowTemp, boolean fast) {
    ThemeDelegate currentTheme = currentThemeImpl(fast);
    if (currentTheme != null && currentTheme.getId() == ThemeId.TEMPORARY && (!allowTemp || ((ThemeTemporary) currentTheme).getFactor() == 1f))
      return ((ThemeTemporary) currentTheme).getToTheme();
    return currentTheme;
  }

  public boolean isCurrentTheme (@ThemeId int themeId) {
    return currentThemeId() == themeId;
  }

  public boolean isChanging () {
    return currentTheme(true).getId() == ThemeId.TEMPORARY;
  }

  public boolean hasColorChanged (@ThemeColorId int colorId) {
    ThemeDelegate currentTheme = currentThemeImpl(false);
    int themeId = currentTheme.getId();
    if (themeId == ThemeId.TEMPORARY) {
      ThemeTemporary tempTheme = (ThemeTemporary) currentTheme;
      return tempTheme.getFromTheme().getColor(colorId) != tempTheme.getToTheme().getColor(colorId);
    }
    if (isCustomTheme(themeId)) {
      return ((ThemeCustom) currentTheme).hasRecentlyChanged(colorId);
    }
    return false;
  }

  @Override
  public void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    if (this._tdlib != null && this._tdlib != newAccount.tdlibNoWakeup()) {
      this._tdlib = newAccount.tdlibNoWakeup();

      int newGlobalTheme = _tdlib.settings().globalTheme();
      if (Settings.instance().getNightMode() != Settings.NIGHT_MODE_NONE) {
        newGlobalTheme = currentTheme().isDark() ? _tdlib.settings().globalNightTheme() : _tdlib.settings().globalDaylightTheme();
        _tdlib.settings().setGlobalTheme(newGlobalTheme);
      }
      if (!isCurrentTheme(newGlobalTheme)) {
        changeGlobalTheme(_tdlib, getTheme(newGlobalTheme), true, null);
      }
    }
  }

  // Changers

  public boolean changeGlobalTheme (final Tdlib tdlib, final @NonNull ThemeDelegate newTheme, boolean force, @Nullable Runnable after) {
    if (newTheme == null)
      return false;
    if (isCurrentTheme(newTheme.getId()))
      return false;
    if (!force) {
      final boolean wasDark = isCurrentThemeDark();
      final boolean newDark = newTheme.isDark();
      if (wasDark != newDark && Settings.instance().getNightMode() != Settings.NIGHT_MODE_NONE) {
        BaseActivity activity = UI.getUiContext();
        if (activity == null || activity.getActivityState() != UI.STATE_RESUMED) {
          Settings.instance().setAutoNightMode(Settings.NIGHT_MODE_NONE);
        } else {
          AlertDialog.Builder b = new AlertDialog.Builder(activity, Theme.dialogTheme());
          b.setTitle(Lang.getString(R.string.DisableAutoNightMode));
          b.setMessage(Lang.getString(R.string.DisableAutoNightModeDesc));
          b.setPositiveButton(Lang.getOK(), (dialog, which) -> {
            Settings.instance().setAutoNightMode(Settings.NIGHT_MODE_NONE);
            changeGlobalTheme(tdlib, newTheme, false, after);
          });
          b.setNegativeButton(Lang.getString(R.string.Cancel), (disalog, which) -> disalog.dismiss());
          activity.showAlert(b);
          return false;
        }
      }
    }

    tdlib.settings().setGlobalTheme(newTheme.getId());

    ThemeTemporary tempTheme = new ThemeTemporary(currentTheme(true), newTheme);
    this._currentTheme = tempTheme;

    boolean animated = UI.wasResumedRecently(1000) || UI.getUiState() == UI.STATE_RESUMED;
    if (animated) {
      if (themeAnimator == null)
        themeAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, Config.DEBUG_NAV_ANIM ? 1000 : 200);
      else
        themeAnimator.forceFactor(0f);

      notifyThemeChanged(tempTheme.getFromTheme(), newTheme);

      themeAnimator.animateTo(1f);
    } else {
      if (themeAnimator != null)
        themeAnimator.cancel();
      tempTheme.setFactor(1f);
      notifyThemeChanged(tempTheme.getFromTheme(), newTheme);
      notifyThemeColorsChanged(false, null);
      unload(tempTheme);
    }
    U.run(after);
    return true;
  }

  public void setInNightMode (boolean inNightMode, boolean force) {
    if (_currentTheme == null || _tdlib == null)
      return;
    boolean currentInNightMode = isCurrentThemeDark();
    if (currentInNightMode != inNightMode) {
      if (inNightMode) {
        changeGlobalTheme(_tdlib, getTheme(_tdlib.settings().globalNightTheme()), force, null);
      } else {
        changeGlobalTheme(_tdlib, getTheme(_tdlib.settings().globalDaylightTheme()), force, null);
      }
    }
  }

  public void toggleNightMode () {
    setInNightMode(!isCurrentThemeDark(), false);
  }

  // Temp color

  private FactorAnimator themeAnimator;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    ThemeDelegate currentTheme = currentThemeImpl(false);
    if (currentTheme instanceof ThemeTemporary && ((ThemeTemporary) currentTheme).setFactor(factor)) {
      notifyThemeColorsChanged(true, null);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    notifyThemeColorsChanged(false, null);
    ThemeDelegate currentTheme = currentThemeImpl(false);
    if (currentTheme instanceof ThemeTemporary) {
      ThemeTemporary tempTheme = (ThemeTemporary) currentTheme;
      unload(tempTheme);
    }
  }

  private void unload (ThemeTemporary theme) {
    // TODO find previously used theme
    this._currentTheme = theme.getToTheme();
  }

  // Listeners

  public void addThemeListener (@NonNull ThemeChangeListener listener) {
    themeChangeListeners.add(listener);
  }

  public void removeThemeListener (@NonNull ThemeChangeListener listener) {
    themeChangeListeners.remove(listener);
  }

  private void notifyThemeChanged (ThemeDelegate fromTheme, ThemeDelegate toTheme) {
    for (ThemeChangeListener listener : themeChangeListeners) {
      listener.onThemeChanged(fromTheme, toTheme);
    }
  }

  private void notifyThemeColorsChanged (boolean areTemp, @Nullable ColorState state) {
    // Direct garbage
    final int globalSize = globalThemeListenerEntries.size();
    for (int i = globalSize - 1; i >= 0; i--) {
      ThemeListenerEntry entry = globalThemeListenerEntries.get(i);
      if (!entry.apply(areTemp)) {
        globalThemeListenerEntries.remove(i);
      }
    }
    // Listeners
    for (ThemeChangeListener listener : themeChangeListeners) {
      if (!areTemp || listener.needsTempUpdates()) {
        listener.onThemeColorsChanged(areTemp, state);
      }
    }
  }

  public void notifyAutoNightModeChanged (int autoNightMode) {
    for (ThemeChangeListener listener : themeChangeListeners) {
      listener.onThemeAutoNightModeChanged(autoNightMode);
    }
  }

  public void addChatStyleListener (@NonNull ChatStyleChangeListener listener) {
    chatStyleChangeListeners.add(listener);
  }

  public void removeChatStyleListener (@NonNull ChatStyleChangeListener listener) {
    chatStyleChangeListeners.remove(listener);
  }

  public void notifyChatStyleChanged (Tdlib tdlib, @ChatStyle int chatStyle) {
    for (ChatStyleChangeListener listener : chatStyleChangeListeners) {
      listener.onChatStyleChanged(tdlib, chatStyle);
    }
  }

  public void notifyChatWallpaperChanged (Tdlib tdlib, @Nullable TGBackground wallpaper, long customChatId, int usageIdentifier) {
    for (ChatStyleChangeListener listener : chatStyleChangeListeners) {
      listener.onChatWallpaperChanged(tdlib, wallpaper, usageIdentifier);
    }
  }

  // Semi-internal

  @ChatStyle
  public static int restoreChatStyle (int chatStyle) {
    switch (chatStyle) {
      case CHAT_STYLE_BUBBLES:
      case CHAT_STYLE_MODERN:
        return chatStyle;
    }
    return CHAT_STYLE_BUBBLES;
  }

  public static int saveThemeId (@ThemeId int themeId) {
    switch (themeId) {
      case ThemeId.BLACK_WHITE:
      case ThemeId.NIGHT_BLUE:
      case ThemeId.BLUE:
      case ThemeId.CYAN:
      case ThemeId.GREEN:
      case ThemeId.NIGHT_BLACK:
      case ThemeId.ORANGE:
      case ThemeId.PINK:
      case ThemeId.RED:
      case ThemeId.WHITE_BLACK:
      case ThemeId.CLASSIC:
        return themeId;

      case ThemeId.TEMPORARY:
      case ThemeId.NONE:
        break;
        
      case ThemeId.CUSTOM:
      default: {
        int customThemeId = resolveCustomThemeId(themeId);
        if (customThemeId != ThemeId.NONE && Settings.instance().hasCustomTheme(customThemeId)) {
          return themeId;
        }
      }
    }
    return ThemeId.NIGHT_BLUE;
  }

  public static int resolveCustomThemeId (int themeId) {
    return themeId <= ThemeId.CUSTOM ? ThemeId.CUSTOM - themeId : ThemeId.NONE;
  }

  public static int serializeCustomThemeId (int customThemeId) {
    if (customThemeId < 0)
      throw new IllegalArgumentException("customThemeId == " + customThemeId);
    return ThemeId.CUSTOM - customThemeId;
  }

  public static float normalizeProperty (@ThemeProperty int propertyId, float value) {
    switch (propertyId) {
      case ThemeProperty.PARENT_THEME:
        return ThemeManager.restoreThemeId((int) value, false);

      case ThemeProperty.BUBBLE_CORNER_MERGED:
      case ThemeProperty.BUBBLE_CORNER:
      case ThemeProperty.BUBBLE_DATE_CORNER:
      case ThemeProperty.DATE_CORNER:
        return Math.max(0, value); // Math.min(18, value)
      case ThemeProperty.BUBBLE_CORNER_LEGACY:
        return Math.max(0, Math.min(6, value));
      case ThemeProperty.BUBBLE_OUTER_MARGIN:
        return 8f; // FIXME return Math.max(0, Math.min(12, value));

      case ThemeProperty.SHADOW_DEPTH:
      case ThemeProperty.SUBTITLE_ALPHA:
      case ThemeProperty.AVATAR_RADIUS:
      case ThemeProperty.AVATAR_RADIUS_FORUM:
        return MathUtils.clamp(value);

      case ThemeProperty.AVATAR_RADIUS_CHAT_LIST:
      case ThemeProperty.AVATAR_RADIUS_CHAT_LIST_FORUM:
        return value == -1.0f ? value : MathUtils.clamp(value);

      case ThemeProperty.BUBBLE_OUTLINE_SIZE:
      case ThemeProperty.IMAGE_CORNER:
        return Math.max(0, value);

      case ThemeProperty.WALLPAPER_USAGE_ID:
        return Math.max(0, Math.min(2, (int) value));
      case ThemeProperty.WALLPAPER_ID:
        return (int) value;

      case ThemeProperty.DARK:
      case ThemeProperty.REPLACE_SHADOWS_WITH_SEPARATORS:
      case ThemeProperty.BUBBLE_OUTLINE:
      case ThemeProperty.BUBBLE_UNREAD_SHADOW:
      case ThemeProperty.LIGHT_STATUS_BAR:
      case ThemeProperty.WALLPAPER_OVERRIDE_BUTTON:
      case ThemeProperty.WALLPAPER_OVERRIDE_DATE:
      case ThemeProperty.WALLPAPER_OVERRIDE_MEDIA_REPLY:
      case ThemeProperty.WALLPAPER_OVERRIDE_OVERLAY:
      case ThemeProperty.WALLPAPER_OVERRIDE_TIME:
      case ThemeProperty.WALLPAPER_OVERRIDE_UNREAD:
        return Math.max(0, Math.min(1, (int) value));
    }
    throw Theme.newError(propertyId, "propertyId");
  }

  public static boolean isBoolProperty (@ThemeProperty int propertyId) {
    switch (propertyId) { // Bool properties can have only 0..1 values
      case ThemeProperty.DARK:
      case ThemeProperty.REPLACE_SHADOWS_WITH_SEPARATORS:
      case ThemeProperty.BUBBLE_OUTLINE:
      case ThemeProperty.BUBBLE_UNREAD_SHADOW:
      case ThemeProperty.LIGHT_STATUS_BAR:
      case ThemeProperty.WALLPAPER_OVERRIDE_BUTTON:
      case ThemeProperty.WALLPAPER_OVERRIDE_DATE:
      case ThemeProperty.WALLPAPER_OVERRIDE_MEDIA_REPLY:
      case ThemeProperty.WALLPAPER_OVERRIDE_OVERLAY:
      case ThemeProperty.WALLPAPER_OVERRIDE_TIME:
      case ThemeProperty.WALLPAPER_OVERRIDE_UNREAD:
        return true;
    }
    return false;
  }

  public static boolean isStaticProperty (@ThemeProperty int propertyId) { // Static properties are not animated
    switch (propertyId) {
      case ThemeProperty.WALLPAPER_ID:
      case ThemeProperty.WALLPAPER_USAGE_ID:
        return true;
    }
    return false;
  }

  public static boolean isValidProperty (@ThemeProperty int propertyId, float value) {
    return normalizeProperty(propertyId, value) == value;
  }

  @ThemeId
  public static int restoreThemeId (int savedThemeId, boolean allowCustom) {
    return restoreThemeId(Settings.instance(), savedThemeId, allowCustom);
  }

  @ThemeId
  public static int restoreThemeId (Settings prefs, int savedThemeId, boolean allowCustom) {
    switch (savedThemeId) {
      case ThemeId.BLUE:
      case ThemeId.NIGHT_BLACK:
      case ThemeId.NIGHT_BLUE:
      case ThemeId.BLACK_WHITE:
      case ThemeId.WHITE_BLACK:
      case ThemeId.RED:
      case ThemeId.ORANGE:
      case ThemeId.GREEN:
      case ThemeId.PINK:
      case ThemeId.CYAN:
      case ThemeId.CLASSIC:
        return savedThemeId;
      default: {
        if (allowCustom) {
          int customThemeId = resolveCustomThemeId(savedThemeId);
          if (customThemeId != ThemeId.NONE && prefs.hasCustomTheme(customThemeId)) {
            return savedThemeId;
          }
        }
      }
    }
    return DEFAULT_THEME;
  }

  @ThemeId
  public static int restoreThemeId (int savedThemeId, boolean allowCustom, boolean isDark) {
    return restoreThemeId(Settings.instance(), savedThemeId, allowCustom, isDark);
  }

  @ThemeId
  public static int restoreThemeId (Settings prefs, int savedThemeId, boolean allowCustom, boolean isDark) {
    final int themeId = restoreThemeId(prefs, savedThemeId, allowCustom);
    return themeId > ThemeId.CUSTOM && (Theme.isDarkTheme(prefs, themeId) != isDark) ? (isDark ? DEFAULT_DARK_THEME : DEFAULT_LIGHT_THEME) : themeId;
  }

  public ThemeDelegate getTheme (@ThemeId int themeId) {
    return ThemeSet.getOrLoadTheme(themeId, true);
  }

  public void notifyColorChanged (@ThemeId int themeId, @NonNull ColorState colorState, boolean isTemporaryChange) {
    if (currentThemeId() == themeId)
      notifyThemeColorsChanged(isTemporaryChange, colorState);
  }

  public void notifyPropertyChanged (@ThemeId int themeId, @ThemeProperty int propertyId, float value, float defaultValue) {
    if (currentThemeId() == themeId) {
      // ((ThemeCustom) currentTheme).setProperty(propertyId, value != defaultValue ? value : null);
      for (ThemeChangeListener listener : themeChangeListeners) {
        listener.onThemePropertyChanged(themeId, propertyId, value, value == defaultValue);
      }
      notifyThemeColorsChanged(false, null);
    }
  }
}
