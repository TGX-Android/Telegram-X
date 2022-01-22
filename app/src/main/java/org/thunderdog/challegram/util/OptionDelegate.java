/**
 * File created on 11/08/15 at 13:06
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util;

import android.view.View;

public interface OptionDelegate {
  // return true if popup should be closed
  boolean onOptionItemPressed (View optionItemView, int id);

  default Object getTagForItem (int position) {
    return null;
  }
  default boolean disableCancelOnTouchdown () { return false; }
}
