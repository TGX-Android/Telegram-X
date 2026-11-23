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
 * File created on 19/11/2026
 */
package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;
import android.view.View;

import java.util.ArrayList;

/**
 * Elements of a message that stick to an edge of the visible part of the list, e.g. the chevron
 * of an expanded quote. The list draws them while they're stuck, the message otherwise
 */
public final class StickyElements {
  public interface Element {
    void drawSticky (Canvas c, float x, float y, float alpha);
  }

  public static final int AT_MIN = -1, STUCK = 0, AT_MAX = 1;

  private static final class Entry {
    private Element element;
    private float x, minY, maxY, padding;
    private boolean atTop;
    private int state;
  }

  private final ArrayList<Entry> entries = new ArrayList<>();
  private int count;

  public void clear () {
    for (int i = 0; i < count; i++) {
      entries.get(i).element = null;
    }
    count = 0;
  }

  public boolean isEmpty () {
    return count == 0;
  }

  public void add (
    Element element,
    float x,
    float minY,
    float maxY,
    float padding,
    boolean atTop,
    int state
  ) {
    // In the coordinates of the message; the real place is maxY at the bottom, minY at the top
    if (count == entries.size()) {
      entries.add(new Entry());
    }
    final Entry entry = entries.get(count++);
    entry.element = element;
    entry.x = x;
    entry.minY = minY;
    entry.maxY = maxY;
    entry.padding = padding;
    entry.atTop = atTop;
    entry.state = state;
  }

  public static int topOf (View child) {
    return Math.round(child.getTop() + child.getTranslationY());
  }

  public static int stateOf (float y, float minY, float maxY) {
    return y <= minY ? AT_MIN : y >= maxY ? AT_MAX : STUCK;
  }

  public boolean hasStuck () {
    for (int i = 0; i < count; i++) {
      if (entries.get(i).state == STUCK) {
        return true;
      }
    }
    return false;
  }

  public boolean needsDrawAgain (int visibleTop, int visibleBottom) {
    for (int i = 0; i < count; i++) {
      final Entry entry = entries.get(i);
      final float y = place(
        entry.minY,
        entry.maxY,
        entry.padding,
        entry.atTop,
        visibleTop,
        visibleBottom
      );
      if (stateOf(y, entry.minY, entry.maxY) != entry.state) {
        return true;
      }
    }
    return false;
  }

  public static float place (
    float minY,
    float maxY,
    float padding,
    boolean atTop,
    int visibleTop,
    int visibleBottom
  ) {
    return atTop ?
      Math.max(minY, Math.min(maxY, visibleTop + padding)) :
      Math.min(maxY, Math.max(minY, visibleBottom - padding));
  }

  public void draw (Canvas c, float dx, float dy, int visibleTop, int visibleBottom, float alpha) {
    // dx, dy: the message in the list; visibleTop and visibleBottom from its top
    for (int i = 0; i < count; i++) {
      final Entry entry = entries.get(i);
      if (entry.state != STUCK) {
        continue;
      }
      final float y = place(
        entry.minY,
        entry.maxY,
        entry.padding,
        entry.atTop,
        visibleTop,
        visibleBottom
      );
      entry.element.drawSticky(c, dx + entry.x, dy + y, alpha);
    }
  }
}
