package org.thunderdog.challegram.mediaview;

import org.thunderdog.challegram.mediaview.data.MediaStack;

public class MediaStackResult {
  public final MediaStack stack;
  public final boolean reverseMode;

  public MediaStackResult (MediaStack stack, boolean reverseMode) {
    this.stack = stack;
    this.reverseMode = reverseMode;
  }
}
