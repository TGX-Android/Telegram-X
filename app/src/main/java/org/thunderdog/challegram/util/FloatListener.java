package org.thunderdog.challegram.util;

import android.view.View;

/**
 * Date: 17/11/2018
 * Author: default
 */
public interface FloatListener {
  void onValueChange (View view, float value, boolean isFinished);
  default void onValuesChangeStarted (View view, boolean isChanging) { }
}
