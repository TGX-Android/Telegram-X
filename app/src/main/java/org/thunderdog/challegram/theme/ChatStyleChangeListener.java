package org.thunderdog.challegram.theme;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.telegram.Tdlib;

/**
 * Date: 21/01/2017
 * Author: default
 */
public interface ChatStyleChangeListener {
  void onChatStyleChanged (Tdlib tdlib, @ChatStyle int newChatStyle);
  void onChatWallpaperChanged (Tdlib tdlib, @Nullable TGBackground wallpaper, int usageIdentifier);
}
