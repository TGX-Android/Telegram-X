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
      Log.i("OFFSETS_DEBUG", "SCROLL " + offset);
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
