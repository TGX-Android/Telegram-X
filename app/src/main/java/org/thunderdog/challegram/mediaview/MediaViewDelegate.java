package org.thunderdog.challegram.mediaview;

import org.thunderdog.challegram.mediaview.data.MediaItem;

/**
 * Date: 09/12/2016
 * Author: default
 */

public interface MediaViewDelegate {
  MediaViewThumbLocation getTargetLocation (int index, MediaItem item); // null if item is not presented on screen
  void setMediaItemVisible (int index, MediaItem item, boolean isVisible); // called when opening and closing photo viewer
}
