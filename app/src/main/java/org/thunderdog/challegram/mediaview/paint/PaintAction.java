package org.thunderdog.challegram.mediaview.paint;

import android.os.SystemClock;

/**
 * Date: 11/7/17
 * Author: default
 */

public class PaintAction {
  public static final int SIMPLE_DRAWING = 0;

  private final int type;
  private final Object data;
  private final long creationTimeMs;

  public PaintAction (int type, Object data) {
    this.type = type;
    this.data = data;
    this.creationTimeMs = SystemClock.uptimeMillis();
  }

  public int getType () {
    return type;
  }

  public long getCreationTimeMs () {
    return creationTimeMs;
  }
}
