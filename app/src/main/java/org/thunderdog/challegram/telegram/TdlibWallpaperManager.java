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
 * File created on 03/01/2018
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.TdConstants;

public class TdlibWallpaperManager {
  public interface Callback {
    void onReceiveWallpapers (@NonNull List<TGBackground> wallpapers);
  }

  private final Tdlib tdlib;

  /*package*/ TdlibWallpaperManager (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public void ensureWallpaperAvailability () {
    fetchBackground(tdlib.settings().globalDaylightTheme(), false);
    fetchBackground(tdlib.settings().globalNightTheme(), false);
  }

  private boolean needLoadWallpaper (@ThemeId int themeId) {
    return Theme.getDefaultWallpaper(themeId) != null && tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier(themeId), true) == null;
  }

  public void onThemeSwitched (int oldThemeId, int newThemeId) {
    int oldWallpaperUsageId = Theme.getWallpaperIdentifier(oldThemeId);
    int newWallpaperUsageId = Theme.getWallpaperIdentifier(newThemeId);
    if (oldWallpaperUsageId == newWallpaperUsageId) {
      String oldWallpaper = Theme.getDefaultWallpaper(oldThemeId);
      String newWallpaper = Theme.getDefaultWallpaper(newThemeId);

      // Default wallpaper changed, apply new default
      if (!StringUtils.equalsOrBothEmpty(oldWallpaper, newWallpaper)) {
        TGBackground currentBackground = tdlib.settings().getWallpaper(newWallpaperUsageId, true);
        if (StringUtils.isEmpty(oldWallpaper) && (currentBackground == null || currentBackground.isEmpty())) {
          fetchBackground(newThemeId, true);
        } else if (!StringUtils.isEmpty(oldWallpaper) && currentBackground != null && oldWallpaper.equals(extractWallpaperName(currentBackground.getName()))) {
          fetchBackground(newThemeId, true);
        }
      }
    } else {
      fetchBackground(newThemeId, false);
    }
  }

  private void fetchBackground (int themeId, boolean force) {
    if (!force && !needLoadWallpaper(themeId)) {
      return;
    }
    String name = Theme.getDefaultWallpaper(themeId);
    if (StringUtils.isEmpty(name)) {
      tdlib.settings().setWallpaper(TGBackground.newEmptyWallpaper(tdlib), force, Theme.getWallpaperIdentifier(themeId));
    } else {
      tdlib.client().send(new TdApi.SearchBackground(name), result -> {
        if (result.getConstructor() == TdApi.Background.CONSTRUCTOR) {
          TGBackground background = new TGBackground(tdlib, (TdApi.Background) result);
          tdlib.ui().post(() -> {
            boolean needForce = force && (tdlib.settings().globalDaylightTheme() == themeId || tdlib.settings().globalNightTheme() == themeId);
            tdlib.settings().setWallpaper(background, needForce, Theme.getWallpaperIdentifier(themeId));
          });
        }
      });
    }
  }

  public void loadWallpaper (String wallpaper, long timeoutMs, Runnable onDone) {
    if (StringUtils.isEmpty(wallpaper)) {
      onDone.run();
      return;
    }
    Runnable after;
    if (timeoutMs > 0) {
      AtomicBoolean executed = new AtomicBoolean(false);
      after = new Runnable() {
        @Override
        public void run () {
          if (!executed.getAndSet(true)) {
            onDone.run();
            UI.removePendingRunnable(this);
          }
        }
      };
      UI.post(after, timeoutMs);
    } else {
      after = onDone;
    }
    tdlib.client().send(new TdApi.SearchBackground(wallpaper), result -> {
      if (result.getConstructor() == TdApi.Background.CONSTRUCTOR) {
        TdApi.Background background = (TdApi.Background) result;
        if (background.document != null) {
          tdlib.client().send(new TdApi.DownloadFile(background.document.document.id, 32, 0, 0, true), fileResult -> tdlib.ui().post(after));
          return;
        }
      }
      tdlib.ui().post(after);
    });
  }

  public static String extractWallpaperName (String wallpaper) {
    if (wallpaper != null) {
      int i = wallpaper.indexOf('?');
      return i != -1 ? wallpaper.substring(0, i) : wallpaper;
    }
    return null;
  }

  private final SparseArrayCompat<List<Callback>> callbacks = new SparseArrayCompat<>();
  private final SparseArrayCompat<List<TGBackground>> backgrounds = new SparseArrayCompat<>();

  private void fetchBackgrounds (boolean forDarkTheme) {
    tdlib.client().send(new TdApi.GetBackgrounds(forDarkTheme), result -> {
      switch (result.getConstructor()) {
        case TdApi.Backgrounds.CONSTRUCTOR: {
          TdApi.Background[] rawBackgrounds = ((TdApi.Backgrounds) result).backgrounds;
          List<TGBackground> backgrounds = new ArrayList<>(rawBackgrounds.length);
          for (TdApi.Background rawBackground : rawBackgrounds) {
            backgrounds.add(new TGBackground(tdlib, rawBackground));
          }
          List<Callback> callbacks;
          synchronized (this.backgrounds) {
            this.backgrounds.put(forDarkTheme ? 1 : 0, backgrounds);
            callbacks = ArrayUtils.removeWithKey(this.callbacks, forDarkTheme ? 1 : 0);
          }
          if (callbacks != null) {
            for (Callback callback : callbacks) {
              if (callback != null)
                callback.onReceiveWallpapers(backgrounds);
            }
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
      }
    });
  }

  public void addBackground (TGBackground background, boolean forDarkTheme) {
    List<TGBackground> list = this.backgrounds.get(forDarkTheme ? 1 : 0);
    if (list != null) {
      list.add(0, background);
    }
  }

  public void getBackgrounds (@Nullable Callback callback, boolean forDarkTheme) {
    List<TGBackground> result;
    synchronized (this.backgrounds) {
      result = this.backgrounds.get(forDarkTheme ? 1 : 0);
      if (result == null) {
        List<Callback> callbacks = this.callbacks.get(forDarkTheme ? 1 : 0);
        if (callbacks == null) {
          callbacks = new ArrayList<>();
          this.callbacks.put(forDarkTheme ? 1 : 0, callbacks);
          fetchBackgrounds(forDarkTheme);
        }
        callbacks.add(callback);
      }
    }
    if (result != null) {
      if (callback != null)
        callback.onReceiveWallpapers(result);
    }
  }

  public void notifyDefaultWallpaperChanged (int themeId) {
    fetchBackground(themeId, true);
  }

  public boolean isValidWallpaperId (int wallpaperId) {
    return wallpaperId == 0 || TGBackground.getBackgroundForLegacyWallpaperId(wallpaperId) != null;
  }
}
