package org.thunderdog.challegram.telegram;

import android.util.SparseIntArray;

import androidx.collection.SparseArrayCompat;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.reference.ReferenceUtils;

/**
 * Date: 14/12/2016
 * Author: default
 */

public class TGLegacyAudioManager {
  private static TGLegacyAudioManager instance;

  public static TGLegacyAudioManager instance () {
    if (instance == null) {
      synchronized (TGLegacyAudioManager.class) {
        if (instance == null) {
          instance = new TGLegacyAudioManager();
        }
      }
    }
    return instance;
  }

  public interface PlayListener {
    void onPlayPause (int fileId, boolean isPlaying, boolean isUpdate);
    boolean needPlayProgress (int fileId);
    void onPlayProgress (int fileId, float progress, boolean isUpdate);
  }

  private final SparseIntArray playingFiles;
  private final List<Reference<PlayListener>> globalListeners;
  private final SparseArrayCompat<List<Reference<PlayListener>>> listeners;
  private final SparseArrayCompat<List<Reference<PlayListener>>> progressListeners;

  private TGLegacyAudioManager () {
    this.playingFiles = new SparseIntArray();
    this.globalListeners = new ArrayList<>();
    this.listeners = new SparseArrayCompat<>();
    this.progressListeners = new SparseArrayCompat<>();
  }

  public void subscribe (int fileId, PlayListener listener) {
    synchronized (this) {
      ReferenceUtils.addReference(listeners, listener, fileId);
      if (listener.needPlayProgress(fileId)) {
        ReferenceUtils.addReference(progressListeners, listener, fileId);
      }
      if (playingFiles.get(fileId) == 1) {
        listener.onPlayPause(fileId, true, false);
      }
    }
  }

  public void unsubscribe (int fileId, PlayListener listener) {
    synchronized (this) {
      ReferenceUtils.removeReference(listeners, listener, fileId);
      ReferenceUtils.removeReference(progressListeners, listener, fileId);
    }
  }

  public void addGlobalListener (PlayListener listener) {
    synchronized (this) {
      ReferenceUtils.addReference(globalListeners, listener);
    }
  }

  public void removeGlobalListener (PlayListener listener) {
    synchronized (this) {
      ReferenceUtils.removeReference(globalListeners, listener);
    }
  }

  private static void onPlayPause (List<Reference<PlayListener>> list, int fileId, boolean isPlaying) {
    final int size = list.size();
    for (int i = size - 1; i >= 0; i--) {
      PlayListener listener = list.get(i).get();
      if (listener != null) {
        listener.onPlayPause(fileId, isPlaying, true);
      } else {
        list.remove(i);
      }
    }
  }

  public void onPlayPause (int fileId, boolean isPlaying) {
    synchronized (this) {
      if (isPlaying) {
        playingFiles.put(fileId, 1);
      } else {
        playingFiles.delete(fileId);
      }
      onPlayPause(globalListeners, fileId, isPlaying);
      List<Reference<PlayListener>> list = listeners.get(fileId);
      if (list != null) {
        onPlayPause(list, fileId, isPlaying);
        ReferenceUtils.removeListIfEmpty(listeners, list, fileId);
      }
    }
  }

  private static void onPlayProgress (List<Reference<PlayListener>> list, int fileId, float progress) {
    final int size = list.size();
    for (int i = size - 1; i >= 0; i--) {
      PlayListener listener = list.get(i).get();
      if (listener != null) {
        listener.onPlayProgress(fileId, progress, true);
      } else {
        list.remove(i);
      }
    }
  }

  public void onPlayProgress (int fileId, float progress) {
    synchronized (this) {
      onPlayProgress(globalListeners, fileId, progress);
      List<Reference<PlayListener>> list = progressListeners.get(fileId);
      if (list != null) {
        onPlayProgress(list, fileId, progress);
        ReferenceUtils.removeListIfEmpty(progressListeners, list, fileId);
      }
    }
  }
}
