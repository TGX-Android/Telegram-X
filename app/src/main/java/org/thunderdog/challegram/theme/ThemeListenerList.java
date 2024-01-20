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
 * File created on 29/12/2017
 */
package org.thunderdog.challegram.theme;

import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;

import me.vkryl.android.util.InvalidateDelegate;

public class ThemeListenerList {
  private final ArrayList<ThemeListenerEntry> themeListeners;

  public ThemeListenerList () {
    this.themeListeners = new ArrayList<>();
  }

  public boolean isEmpty () {
    return themeListeners.isEmpty();
  }

  public void addAll (ThemeListenerList other) {
    if (other != null && !other.themeListeners.isEmpty()) {
      this.themeListeners.addAll(other.themeListeners);
    }
  }

  public ArrayList<ThemeListenerEntry> getList () {
    return themeListeners;
  }

  public void add (ThemeListenerEntry item) {
    themeListeners.add(item);
  }

  private void addThemeListener (ThemeListenerEntry listenerEntry) {
    themeListeners.add(listenerEntry);
  }

  public final ThemeListenerEntry addThemeListener (Object target, @ColorId int colorId, @ThemeListenerEntry.EntryMode int mode) {
    ThemeListenerEntry entry;
    addThemeListener(entry = new ThemeListenerEntry(mode, colorId, target));
    return entry;
  }

  public final void addThemeDoubleTextColorListener (Object target, @ColorId int titleColorId, @ColorId int subtitleColorId) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_DOUBLE_TEXT_COLOR, titleColorId, target).setArg1(subtitleColorId));
  }

  public final void addThemePaintColorListener (Paint paint, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_PAINT_COLOR, color, paint));
  }

  public final void addThemeBackgroundColorListener (View view, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_BACKGROUND, color, view));
  }

  public final void addThemeFillingColorListener (View view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_BACKGROUND, ColorId.filling, view));
  }

  public final ThemeListenerEntry addThemeColorListener (Object view, @ColorId int color) {
    ThemeListenerEntry entry;
    addThemeListener(entry = new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, color, view));
    return entry;
  }

  public final void addThemeHintTextColorListener (Object view, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_HINT_TEXT_COLOR, color, view));
  }

  public final void addThemeLinkTextColorListener (Object view, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_LINK_TEXT_COLOR, color, view));
  }

  public final void addThemeHighlightColorListener (Object view, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_HIGHLIGHT_COLOR, color, view));
  }

  public final void addThemeTextAccentColorListener (Object view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, ColorId.text, view));
  }

  public final void addThemeTextDecentColorListener (Object view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, ColorId.textLight, view));
  }

  public final void addThemeInvalidateListener (View target) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_INVALIDATE, 0, target));
  }

  public final void addThemeInvalidateListener (InvalidateDelegate target) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_INVALIDATE, 0, target));
  }

  public final void addThemeFilterListener (Object target, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_FILTER, color, target));
  }

  /*public final void addThemeSelectionColor (ViewPagerTopView target, @ColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_SELECTION, color, target));
  }

  public final void addThemeFromToColor (ViewPagerTopView target, @ColorId int fromColor, @ColorId int toColor) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_FROM_TO, fromColor, target).setArg1(toColor));
  }*/

  public final void addThemeSpecialFilterListener (Object target, @ColorId int colorId) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_SPECIAL_FILTER, colorId, target));
  }

  public ThemeListenerEntry findThemeListenerByTarget (Object target, int mode) {
    for (ThemeListenerEntry entry : themeListeners) {
      if (entry.targetEquals(target, mode)) {
        return entry;
      }
    }
    return null;
  }

  public final void removeThemeListenerByTarget (Object target) {
    if (!themeListeners.isEmpty()) {
      final int size = themeListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        ThemeListenerEntry entry = themeListeners.get(i);
        if (entry.isEmpty() || entry.targetEquals(target)) {
          themeListeners.remove(i);
        }
      }
    }
  }

  public final void onThemeColorsChanged (boolean areTemp) {
    final int size = themeListeners.size();
    for (int i = size - 1; i >= 0; i--) {
      ThemeListenerEntry entry = themeListeners.get(i);
      if (!entry.apply(areTemp))
        themeListeners.remove(i);
    }
  }
}
