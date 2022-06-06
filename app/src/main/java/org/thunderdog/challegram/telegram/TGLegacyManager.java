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
 * File created on 28/12/2016
 */
package org.thunderdog.challegram.telegram;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.reference.ReferenceUtils;

public class TGLegacyManager {
  private static TGLegacyManager instance;

  public static TGLegacyManager instance () {
    if (instance == null) {
      instance = new TGLegacyManager();
    }
    return instance;
  }

  public interface EmojiLoadListener {
    void onEmojiPartLoaded ();
    default void onEmojiPackChanged () {
      onEmojiPartLoaded();
    }
  }

  private final List<Reference<EmojiLoadListener>> emojiListeners;

  private TGLegacyManager () {
    this.emojiListeners = new ArrayList<>();
  }

  public void addEmojiListener (EmojiLoadListener listener) {
    synchronized (this) {
      ReferenceUtils.addReference(emojiListeners, listener);
    }
  }

  public void removeEmojiListener (EmojiLoadListener listener) {
    synchronized (this) {
      ReferenceUtils.removeReference(emojiListeners, listener);
    }
  }

  public void onEmojiLoaded (boolean isChange) {
    synchronized (this) {
      final int size = emojiListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        EmojiLoadListener listener = emojiListeners.get(i).get();
        if (listener != null) {
          if (isChange) {
            listener.onEmojiPackChanged();
          } else {
            listener.onEmojiPartLoaded();
          }
        } else {
          emojiListeners.remove(i);
        }
      }
    }
  }

  public void onEmojiPackChanged () {
    synchronized (this) {
      final int size = emojiListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        EmojiLoadListener listener = emojiListeners.get(i).get();
        if (listener != null) {
          listener.onEmojiPackChanged();
        } else {
          emojiListeners.remove(i);
        }
      }
    }
  }
}
