package org.thunderdog.challegram.theme;

import android.graphics.Paint;
import android.view.View;

import org.thunderdog.challegram.R;

import java.util.ArrayList;

import me.vkryl.android.util.InvalidateDelegate;

/**
 * Date: 12/29/17
 * Author: default
 */

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

  public final ThemeListenerEntry addThemeListener (Object target, @ThemeColorId int colorId, @ThemeListenerEntry.EntryMode int mode) {
    ThemeListenerEntry entry;
    addThemeListener(entry = new ThemeListenerEntry(mode, colorId, target));
    return entry;
  }

  public final void addThemeDoubleTextColorListener (Object target, @ThemeColorId int titleColorId, @ThemeColorId int subtitleColorId) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_DOUBLE_TEXT_COLOR, titleColorId, target).setArg1(subtitleColorId));
  }

  public final void addThemePaintColorListener (Paint paint, @ThemeColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_PAINT_COLOR, color, paint));
  }

  public final void addThemeBackgroundColorListener (View view, @ThemeColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_BACKGROUND, color, view));
  }

  public final void addThemeFillingColorListener (View view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_BACKGROUND, R.id.theme_color_filling, view));
  }

  public final ThemeListenerEntry addThemeColorListener (Object view, @ThemeColorId int color) {
    ThemeListenerEntry entry;
    addThemeListener(entry = new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, color, view));
    return entry;
  }

  public final void addThemeHintTextColorListener (Object view, @ThemeColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_HINT_TEXT_COLOR, color, view));
  }

  public final void addThemeLinkTextColorListener (Object view, @ThemeColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_LINK_TEXT_COLOR, color, view));
  }

  public final void addThemeHighlightColorListener (Object view, @ThemeColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_HIGHLIGHT_COLOR, color, view));
  }

  public final void addThemeTextAccentColorListener (Object view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, R.id.theme_color_text, view));
  }

  public final void addThemeTextDecentColorListener (Object view) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_TEXT_COLOR, R.id.theme_color_textLight, view));
  }

  public final void addThemeInvalidateListener (View target) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_INVALIDATE, 0, target));
  }

  public final void addThemeInvalidateListener (InvalidateDelegate target) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_INVALIDATE, 0, target));
  }

  public final void addThemeFilterListener (Object target, @ThemeColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_FILTER, color, target));
  }

  /*public final void addThemeSelectionColor (ViewPagerTopView target, @ThemeColorId int color) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_SELECTION, color, target));
  }

  public final void addThemeFromToColor (ViewPagerTopView target, @ThemeColorId int fromColor, @ThemeColorId int toColor) {
    addThemeListener(new ThemeListenerEntry(ThemeListenerEntry.MODE_FROM_TO, fromColor, target).setArg1(toColor));
  }*/

  public final void addThemeSpecialFilterListener (Object target, @ThemeColorId int colorId) {
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
