package org.thunderdog.challegram.mediaview;

import org.thunderdog.challegram.mediaview.data.MediaItem;

/**
 * Date: 09/12/2016
 * Author: default
 */

public interface MediaStackCallback {
  void onMediaChanged (int index, int estimatedTotalSize, MediaItem currentItem, boolean itemsAdded);
}
