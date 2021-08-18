package org.thunderdog.challegram.mediaview;

import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.mediaview.data.MediaItem;

import java.util.ArrayList;

/**
 * Date: 10/12/2016
 * Author: default
 */

public interface MediaSelectDelegate {
  boolean isMediaItemSelected (int index, MediaItem item);
  void setMediaItemSelected (int index, MediaItem item, boolean isSelected);
  int getSelectedMediaCount ();
  long getOutputChatId ();
  boolean canDisableMarkdown ();
  // boolean canSendAsFile ();
  ArrayList<ImageFile> getSelectedMediaItems (boolean copy);
}
