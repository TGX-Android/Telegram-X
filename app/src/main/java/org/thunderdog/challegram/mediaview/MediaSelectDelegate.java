/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.mediaview.data.MediaItem;

import java.util.ArrayList;

public interface MediaSelectDelegate {
  boolean isMediaItemSelected (int index, MediaItem item);
  void setMediaItemSelected (int index, MediaItem item, boolean isSelected);
  int getSelectedMediaCount ();
  long getOutputChatId ();
  boolean canDisableMarkdown ();
  // boolean canSendAsFile ();
  ArrayList<ImageFile> getSelectedMediaItems (boolean copy);
}
