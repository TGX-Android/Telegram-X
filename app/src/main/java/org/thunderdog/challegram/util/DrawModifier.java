package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

/**
 * Date: 11/12/18
 * Author: default
 */
public interface DrawModifier {
  default void beforeDraw (View view, Canvas c) { }
  default void afterDraw (View view, Canvas c) { }
}
