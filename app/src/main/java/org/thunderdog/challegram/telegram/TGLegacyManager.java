package org.thunderdog.challegram.telegram;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.reference.ReferenceUtils;

/**
 * Date: 28/12/2016
 * Author: default
 */

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
