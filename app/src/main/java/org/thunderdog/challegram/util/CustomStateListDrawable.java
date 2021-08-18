package org.thunderdog.challegram.util;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;

import java.util.ArrayList;

/**
 * Date: 6/8/17
 * Author: default
 */

public class CustomStateListDrawable extends StateListDrawable {
  private final ArrayList<Drawable> drawableList = new ArrayList<>();

  @Override
  public void addState (int[] stateSet, Drawable drawable) {
    super.addState(stateSet, drawable);
    if (!drawableList.contains(drawable)) {
      drawableList.add(drawable);
    }
  }

  public ArrayList<Drawable> getDrawableList () {
    return drawableList;
  }
}
