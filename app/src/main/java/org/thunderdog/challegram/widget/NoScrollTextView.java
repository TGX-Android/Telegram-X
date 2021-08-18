package org.thunderdog.challegram.widget;

import android.content.Context;
import android.widget.TextView;

/**
 * Date: 8/4/18
 * Author: default
 */
public class NoScrollTextView extends TextView {
  public NoScrollTextView (Context context) {
    super(context);
  }

  @Override
  public boolean canScrollHorizontally (int direction) {
    return false;
  }

  @Override
  public boolean canScrollVertically (int direction) {
    return false;
  }
}
