/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 28/12/2016
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.AnyThread;

import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.reference.ReferenceList;

public class TGLegacyManager {
  private static TGLegacyManager instance;

  public static TGLegacyManager instance () {
    if (instance == null) {
      instance = new TGLegacyManager();
    }
    return instance;
  }

  public interface EmojiLoadListener {
    void onEmojiUpdated (boolean isPackSwitch);
  }

  private final ReferenceList<EmojiLoadListener> emojiListeners;

  private TGLegacyManager () {
    this.emojiListeners = new ReferenceList<>(true);
  }

  public void addEmojiListener (EmojiLoadListener listener) {
    this.emojiListeners.add(listener);
  }

  public void removeEmojiListener (EmojiLoadListener listener) {
    this.emojiListeners.remove(listener);
  }

  @AnyThread
  public void notifyEmojiChanged (boolean isPackSwitch) {
    if (!UI.inUiThread()) {
      UI.post(() -> notifyEmojiChanged(isPackSwitch));
      return;
    }
    for (EmojiLoadListener listener : emojiListeners) {
      listener.onEmojiUpdated(isPackSwitch);
    }
  }
}
