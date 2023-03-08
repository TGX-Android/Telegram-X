package org.thunderdog.challegram.mediaview;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MediaSpoilerSendDelegate implements MediaSendDelegate {
  private final AtomicBoolean isEnabled = new AtomicBoolean();

  @Override
  public final boolean allowHideMedia () {
    return true;
  }

  @Override
  public final boolean isHideMediaEnabled () {
    return isEnabled.get();
  }

  @Override
  public final void onHideMediaStateChanged (boolean hideMedia) {
    isEnabled.set(hideMedia);
  }
}
