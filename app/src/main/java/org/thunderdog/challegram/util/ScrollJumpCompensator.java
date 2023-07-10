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
 * File created on 31/05/2023
 */
package org.thunderdog.challegram.util;

import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.recyclerview.widget.RecyclerView;

public class ScrollJumpCompensator implements ViewTreeObserver.OnGlobalLayoutListener {
  private final RecyclerView recyclerView;
  private final ViewTreeObserver observer;
  private int offset;

  public ScrollJumpCompensator (RecyclerView r, View v, int offset) {
    this.recyclerView = r;
    this.observer = v.getViewTreeObserver();
    this.offset = offset;
  }

  public void add () {
    add(observer, this);
  }

  @Override
  public void onGlobalLayout () {
    if (offset != 0) {
      recyclerView.scrollBy(0, offset);
      offset = 0;
    }

    remove(observer, this);
  }

  public static void add (ViewTreeObserver v, ScrollJumpCompensator listener) {
    v.addOnGlobalLayoutListener(listener);
  }

  public static boolean remove (ViewTreeObserver v, ScrollJumpCompensator listener) {
    if (v.isAlive()) {
      v.removeOnGlobalLayoutListener(listener);
      return true;
    }
    return false;
  }

  public static void compensate (RecyclerView r, int offset) {
    ScrollJumpCompensator x = new ScrollJumpCompensator(r, r, offset);
    x.add();
  }

  public static void compensate (RecyclerView r, View v, int offset) {
    ScrollJumpCompensator x = new ScrollJumpCompensator(r, v, offset);
    x.add();
  }
}
