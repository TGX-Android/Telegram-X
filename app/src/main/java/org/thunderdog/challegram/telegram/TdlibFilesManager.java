package org.thunderdog.challegram.telegram;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.SparseIntArray;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.WatchDog;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.voip.VoIPController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceIntMap;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.unit.ByteUnit;
import me.vkryl.td.Td;

/**
 * Date: 24/11/2016
 * Author: default
 */

public class TdlibFilesManager implements GlobalConnectionListener {
  public static final int STATE_PAUSED = 0;
  public static final int STATE_IN_PROGRESS = 1;
  public static final int STATE_DOWNLOADED_OR_UPLOADED = 2;
  public static final int STATE_FAILED = 3;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_PAUSED, STATE_IN_PROGRESS, STATE_DOWNLOADED_OR_UPLOADED, STATE_FAILED})
  public @interface FileDownloadState { }

  private static final int OPERATION_NONE = 0;
  private static final int OPERATION_DOWNLOAD = 1;

  public interface FileListener {
    void onFileLoadProgress (TdApi.File file);
    void onFileLoadStateChanged (Tdlib tdlib, int fileId, @FileDownloadState int state, @Nullable TdApi.File downloadedFile);
    default void onFileGenerationProgress (int fileId, int ready, int size) {}
    default void onFileGenerationFinished (@NonNull TdApi.File file) {}
  }

  public interface SimpleListener {
    void onUpdateFile (TdApi.File file);
  }

  // Context

  private final Tdlib tdlib;

  private final ReferenceIntMap<FileListener> listeners;
  private final ReferenceIntMap<SimpleListener> simpleListeners;
  private final ReferenceList<FileListener> globalListeners;
  private final SparseIntArray pendingOperations;
  private final HashMap<Integer, TdApi.File> pendingFiles;

  private final SparseArrayCompat<List<FileUpdateListener>> activeCloudReferences;
  private final SparseIntArray downloadingCloudFiles;

  private final HashSet<Integer> manuallyCancelledFiles;

  TdlibFilesManager (Tdlib tdlib) {
    this.tdlib = tdlib;

    this.listeners = new ReferenceIntMap<>(true);
    this.simpleListeners = new ReferenceIntMap<>(true);
    this.globalListeners = new ReferenceList<>(true);

    this.pendingOperations = new SparseIntArray();
    this.pendingFiles = new HashMap<>();

    this.activeCloudReferences = new SparseArrayCompat<>();
    this.downloadingCloudFiles = new SparseIntArray();

    this.manuallyCancelledFiles = new HashSet<>();

    loadSettings();

    tdlib.context().global().addConnectionListener(this);
  }

  public void syncFile (@NonNull final TdApi.File file, @Nullable TdApi.FileType remoteFileType, final long timeoutMs) {
    final CountDownLatch latch = new CountDownLatch(1);
    TdApi.Function function;
    if (remoteFileType != null) {
      function = new TdApi.GetRemoteFile(file.remote.id, remoteFileType);
    } else {
      function = new TdApi.GetFile(file.id);
    }
    final boolean[] signal = new boolean[1];
    tdlib.client().send(function, object -> {
      switch (object.getConstructor()) {
        case TdApi.File.CONSTRUCTOR:
          synchronized (signal) {
            if (!signal[0]) {
              signal[0] = true;
              Td.copyTo((TdApi.File) object, file);
            }
          }
          break;
        case TdApi.Error.CONSTRUCTOR:
          Log.w("getFile error: %s", TD.toErrorString(object));
          break;
      }
      latch.countDown();
    });
    try {
      if (timeoutMs > 0) {
        latch.await(timeoutMs, TimeUnit.MILLISECONDS);
      } else {
        latch.await();
      }
    } catch (InterruptedException e) {
      Log.i(e);
    }
    if (!signal[0]) {
      synchronized (signal) {
        signal[0] = true;
      }
    }
  }

  public Runnable downloadFileSync (@NonNull final TdApi.File file, final long timeoutMs, final @Nullable RunnableData<TdApi.File> after, final @Nullable RunnableData<TdApi.File> fileUpdateListener) {
    if (TD.isFileLoaded(file)) {
      return null;
    }
    final CountDownLatch latch = timeoutMs >= 0 ? new CountDownLatch(1) : null;
    final int[] signal = new int[1]; // 1 = done, 2 = timeout, 3 = cancelled
    final FileUpdateListener listener = new FileUpdateListener() {
      @Override
      public void onUpdateFile (TdApi.UpdateFile updateFile) {
        synchronized (signal) {
          if (signal[0] == 3) {
            return;
          }
          if (signal[0] == 2) {
            if (after != null && updateFile.file.local.isDownloadingCompleted) {
              after.runWithData(updateFile.file);
            } else if (fileUpdateListener != null) {
              fileUpdateListener.runWithData(updateFile.file);
            }
            return;
          }
          Td.copyTo(updateFile.file, file);
          if (updateFile.file.local.isDownloadingCompleted) {
            signal[0] = 1;
            if (latch != null) {
              latch.countDown();
            }
            removeCloudReference(file, this);
            tdlib.listeners().removeFileListener(file.id, this);
            if (latch == null && after != null) {
              after.runWithData(file);
            }
          } else if (fileUpdateListener != null) {
            fileUpdateListener.runWithData(updateFile.file);
          }
        }
      }
    };
    tdlib.listeners().addFileListener(file.id, listener);
    addCloudReference(file, listener, false);
    if (latch != null) {
      try {
        if (timeoutMs > 0) {
          latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } else {
          latch.await();
        }
      } catch (InterruptedException e) {
        Log.i(e);
      }
      synchronized (signal) {
        if (signal[0] == 0) {
          if (after == null) {
            signal[0] = 3; // canceled, because nothing left to do
            removeCloudReference(file, listener);
            tdlib.listeners().removeFileListener(file.id, listener);
          } else {
            signal[0] = 2; // timeout
          }
        }
      }
      return null;
    } else {
      return () -> {
        synchronized (signal) {
          if (signal[0] == 0) {
            signal[0] = 3; // canceled, because nothing left to do
            removeCloudReference(file, listener);
            tdlib.listeners().removeFileListener(file.id, listener);
          }
        }
      };
    }
  }

  private final Client.ResultHandler filesHandler = new Client.ResultHandler() {
    @Override
    public void onResult (TdApi.Object object) {
      switch (object.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR: {
          break;
        }
        case TdApi.File.CONSTRUCTOR: {
          TdApi.File file = (TdApi.File) object;
          synchronized (activeCloudReferences) {
            int status = downloadingCloudFiles.get(file.id);
            if (status == 1) {
              List<FileUpdateListener> references = activeCloudReferences.get(file.id);
              if (references != null) {
                final int size = references.size();
                for (int i = size - 1; i >= 0; i--) {
                  references.get(i).onUpdateFile(new TdApi.UpdateFile(Td.copyOf(file)));
                }
              }
            }
          }
          if (file.local.isDownloadingCompleted) {
            onFileLoaded(new TdApi.UpdateFile(file));
          } else if (!file.local.isDownloadingActive) {
            // TODO
          }
          break;
        }
      }
    }
  };

  // Cloud referencing

  public static final int CLOUD_PRIORITY = 3;

  public void seekCloudReference (TdApi.File file, FileUpdateListener source, int offset) {
    synchronized (activeCloudReferences) {
      if (TD.withinDistance(file, offset)) {
        return;
      }
      List<FileUpdateListener> references = activeCloudReferences.get(file.id);
      if (references != null && references.contains(source)) {
        seekFileInternal(file, offset, 0);
      }
    }
  }

  private void seekFileInternal (TdApi.File file, int offset, int limit) {
    if (!TD.withinDistance(file, offset) && pendingOperations.get(file.id) == OPERATION_DOWNLOAD) {
      if (!Config.DEBUG_DISABLE_DOWNLOAD) {
        Log.i("FILES: downloadFile %d offset=%d", file.id, offset);
        tdlib.client().send(new TdApi.DownloadFile(file.id, CLOUD_PRIORITY, offset, limit, false), filesHandler);
      }
    }
  }

  public void addCloudReference (TdApi.File file, FileUpdateListener source, boolean allowDuplicates) {
    addCloudReference(file, 0, source, allowDuplicates, false);
  }

  public void addCloudReference (TdApi.File file, int offset, FileUpdateListener source, boolean allowDuplicates, boolean offsetImportant) {
    synchronized (activeCloudReferences) {
      List<FileUpdateListener> references = activeCloudReferences.get(file.id);
      if (references != null) {
        if (!allowDuplicates && references.contains(source)) {
          throw new IllegalStateException();
        }
        references.add(source);
        if (offsetImportant) {
          seekFileInternal(file, offset, 0);
        }
        return;
      }
      references = new ArrayList<>();
      references.add(source);
      activeCloudReferences.put(file.id, references);
      if (!file.local.isDownloadingActive) {
        synchronized (this) {
          int pendingOperation = pendingOperations.get(file.id);
          if (pendingOperation == OPERATION_NONE) {
            downloadingCloudFiles.put(file.id, 1);
            downloadFileInternal(file.id, CLOUD_PRIORITY, offset);
          }
        }
      } else if (offsetImportant) {
        seekFileInternal(file, offset, 0);
      }
    }
  }

  public void removeCloudReference (TdApi.File file, FileUpdateListener source) {
    synchronized (activeCloudReferences) {
      int index = activeCloudReferences.indexOfKey(file.id);
      if (index < 0) {
        return;
      }
      List<FileUpdateListener> references = activeCloudReferences.valueAt(index);
      if (references == null) {
        return;
      }
      if (!references.remove(source)) {
        throw new IllegalStateException();
      }
      if (references.isEmpty()) {
        activeCloudReferences.removeAt(index);
        synchronized (this) {
          int i = downloadingCloudFiles.indexOfKey(file.id);
          boolean hasStartedDownloadByCloud = i >= 0;
          if (hasStartedDownloadByCloud) {
            downloadingCloudFiles.removeAt(i);
          }
          int pendingOperation = pendingOperations.get(file.id);
          if (pendingOperation != OPERATION_NONE && hasStartedDownloadByCloud) {
            tdlib.client().send(new TdApi.CancelDownloadFile(file.id, false), filesHandler);
          }
        }
      }
    }
  }

  // Automatic media download

  public boolean downloadAutomatically (@NonNull TdApi.File file, @Nullable TdApi.ChatType chat, @MediaDownloadType int mediaType, boolean force) {
    synchronized (this) {
      if (canAutomaticallyDownload(file, mediaType, chat) && (!TD.isFileLoaded(file) || force)) {
        downloadFile(file);
        return true;
      }
    }
    return false;
  }

  // Internal logic (synchronized in public)

  private boolean addFileListener (final TdApi.File file, final @NonNull FileListener listener) {
    if (listeners.add(file.id, listener)) {
      int pendingOperation = pendingOperations.get(file.id);
      if (pendingOperation != OPERATION_NONE) {
        listener.onFileLoadStateChanged(tdlib, file.id, STATE_IN_PROGRESS, null);
        TdApi.File pendingFile = pendingFiles.get(file.id);
        if (pendingFile != null) {
          Td.copyTo(pendingFile, file);
          float progress = TD.getFileProgress(file);
          if (progress > 0f) {
            listener.onFileLoadProgress(file);
          }
        }
        return false; // No need to call downloadFile
      }
    }
    return true;
  }

  private void removeFileListener (int fileId, @NonNull FileListener listener) {
    listeners.remove(fileId, listener);
  }

  private static void notifyFileState (Iterator<FileListener> list, Tdlib tdlib, int fileId, @FileDownloadState int state, @Nullable TdApi.File downloadedFile) {
    while (list.hasNext()) {
      list.next().onFileLoadStateChanged(tdlib, fileId, state, downloadedFile);
    }
  }

  private void notifyFileState (int fileId, @FileDownloadState int state, @Nullable TdApi.File downloadedFile) {
    notifyFileState(globalListeners.iterator(), tdlib, fileId, state, downloadedFile);
    Iterator<FileListener> list = listeners.iterator(fileId);
    if (list != null) {
      notifyFileState(list, tdlib, fileId, state, downloadedFile);
    }
  }

  private static void notifyFileProgress (Iterator<FileListener> list, TdApi.File file) {
    while (list.hasNext()) {
      list.next().onFileLoadProgress(file);
    }
  }

  private void notifyFileProgress (TdApi.File file) {
    notifyFileProgress(globalListeners.iterator(), file);
    Iterator<FileListener> list = listeners.iterator(file.id);
    if (list != null) {
      notifyFileProgress(list, file);
    }
  }

  /*private static void notifyFileGenerationProgress (ArrayList<WeakReference<FileListener>> list, int fileId, int ready, int size) {
    final int listSize = list.size();
    for (int i = listSize - 1; i >= 0; i--) {
      FileListener listener = list.get(i).get();
      if (listener != null) {
        listener.onFileGenerationProgress(fileId, ready, size);
      } else {
        list.remove(i);
      }
    }
  }*/

  /*private void notifyFileGenerationProgress (int fileId, int ready, int size) {
    notifyFileGenerationProgress(globalListeners, fileId, ready, size);
    ArrayList<WeakReference<FileListener>> list = listeners.get(fileId);
    if (list != null) {
      notifyFileGenerationProgress(list, fileId, ready, size);
      U.checkReferenceList(listeners, list, fileId);
    }
  }*/

  private static void notifyFileGenerationFinished (ArrayList<WeakReference<FileListener>> list, TdApi.File file) {
    final int size = list.size();
    for (int i = size - 1; i >= 0; i--) {
      FileListener listener = list.get(i).get();
      if (listener != null) {
        listener.onFileGenerationFinished(file);
      } else {
        list.remove(i);
      }
    }
  }

  /*private void notifyFileGenerationFinished (TdApi.File file) {
    notifyFileGenerationFinished(globalListeners, file);
    ArrayList<WeakReference<FileListener>> list = listeners.get(file.id);
    if (list != null) {
      notifyFileGenerationFinished(list, file);
      U.checkReferenceList(listeners, list, file.id);
    }
  }*/

  private void downloadFileInternal (int fileId, int priority, int offset) {
    downloadFileInternal(fileId, priority, offset, 0, null);
  }

  private void downloadFileInternal (int fileId, int priority, int offset, int limit, final @Nullable Client.ResultHandler handler) {
    int pendingOperation = pendingOperations.get(fileId);
    if (pendingOperation == OPERATION_NONE) {
      pendingOperations.put(fileId, OPERATION_DOWNLOAD);
      notifyFileState(fileId, STATE_IN_PROGRESS, null);
      if (Log.isEnabled(Log.TAG_TDLIB_FILES)) {
        Log.i(Log.TAG_TDLIB_FILES, "downloadFileInternal id=%d priority=%d offset=%d", fileId, priority, offset);
      }
      if (!Config.DEBUG_DISABLE_DOWNLOAD) {
        if (handler != null) {
          tdlib.client().send(new TdApi.DownloadFile(fileId, priority, offset, limit, false), object -> {
            filesHandler.onResult(object);
            handler.onResult(object);
          });
        } else {
          tdlib.client().send(new TdApi.DownloadFile(fileId, priority, offset, limit, false), filesHandler);
        }
      }
    }
  }

  private void cancelDownloadOrUploadFileInternal (int fileId, int pendingOperation, boolean weak) {
    switch (pendingOperation) {
      case OPERATION_DOWNLOAD: {
        if (Log.isEnabled(Log.TAG_TDLIB_FILES)) {
          Log.i(Log.TAG_TDLIB_FILES, "cancelDownloadFile id=%d", fileId);
        }
        tdlib.client().send(new TdApi.CancelDownloadFile(fileId, weak), filesHandler);
        break;
      }
    }
  }

  private void removePendingOperation (int fileId) {
    pendingOperations.delete(fileId);
    pendingFiles.remove(fileId);
  }

  // Download for whatever reason

  /*public void downloadFile (@NonNull TdApi.File file, @NonNull FileListener listener, @IntRange(from = 1, to = 32) int priority) {
    if (!TD.isFileLoaded(file)) {
      synchronized (this) {
        if (addFileListener(file, listener)) {
          downloadFileInternal(file.id, priority);
        }
      }
    }
  }

  public void downloadFile (@NonNull TdApi.File file, @NonNull FileListener listener) {
    downloadFile(file, listener, 1);
  }*/

  public void downloadFile (@NonNull TdApi.File file, @IntRange(from = 1, to = 32) int priority, int offset, int limit, @Nullable Client.ResultHandler handler) {
    synchronized (this) {
      manuallyCancelledFiles.remove(file.id);
      if (!TD.isFileLoaded(file)) {
        downloadFileInternal(file.id, priority, offset, 0, handler);
      } else if (handler != null) {
        tdlib.client().send(new TdApi.DownloadFile(file.id, priority, offset, limit, false), handler);
      }
    }
  }

  public void downloadFile (@NonNull TdApi.File file) {
    downloadFile(file, 1, 0, 0, null);
  }

  // Cancellation

  public boolean cancelDownloadOrUploadFile (int fileId, boolean weak, boolean byUserRequest) { // When user clicks on cancel button on photo
    synchronized (this) {
      int pendingOperation = pendingOperations.get(fileId);
      if (pendingOperation != OPERATION_NONE) {
        if (byUserRequest) {
          manuallyCancelledFiles.add(fileId);
        }
        cancelDownloadOrUploadFileInternal(fileId, pendingOperation, weak);
        tdlib.context().player().stopPlaybackIfPlaying(fileId);
        return true;
      }
    }
    return false;
  }

  public boolean hasPendingOperation (int fileId) {
    synchronized (this) {
      return pendingOperations.indexOfKey(fileId) >= 0;
    }
  }

  public void cancelAllPendingDownloads () { // When Data Saver becomes active due to network type change
    synchronized (this) {
      final int size = pendingOperations.size();
      for (int i = size - 1; i >= 0; i--) {
        int pendingOperation = pendingOperations.valueAt(i);
        if (pendingOperation == OPERATION_DOWNLOAD) {
          int fileId = pendingOperations.keyAt(i);
          cancelDownloadOrUploadFileInternal(fileId, pendingOperation, false);
        }
      }
    }
  }

  // Listen for future updates

  public void subscribe (TdApi.File file, @NonNull FileListener listener) {
    synchronized (this) {
      addFileListener(file, listener);
    }
  }

  public void unsubscribe (int fileId, @NonNull FileListener listener) {
    synchronized (this) {
      removeFileListener(fileId, listener);
    }
  }

  public void subscribe (int fileId, @NonNull SimpleListener listener) {
    simpleListeners.add(fileId, listener);
  }

  public void unsubscribe (int fileId, @NonNull SimpleListener listener) {
    simpleListeners.remove(fileId, listener);
  }

  public void addGlobalListener (@NonNull FileListener listener) {
    globalListeners.add(listener);
  }

  public void removeGlobalListener (@NonNull FileListener listener) {
    globalListeners.remove(listener);
  }

  // Update handlers

  public static boolean isDownloadableContentType (@NonNull TdApi.MessageContent content) {
    switch (content.getConstructor()) {
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessagePhoto.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
      case TdApi.MessageDocument.CONSTRUCTOR: {
        return true;
      }
    }
    return false;
  }

  public void onFileUpdate (TdApi.UpdateFile update) {
    // pendingOperations.get()
    synchronized (this) {
      int pendingOperation = pendingOperations.get(update.file.id);

      if (pendingOperation != OPERATION_NONE && !update.file.remote.isUploadingActive && !update.file.local.isDownloadingActive && !update.file.remote.isUploadingCompleted && !update.file.local.isDownloadingCompleted) {
        removePendingOperation(update.file.id);
        notifyFileState(update.file.id, STATE_PAUSED, null);
      }

      final Iterator<SimpleListener> list = simpleListeners.iterator(update.file.id);
      if (list != null) {
        while (list.hasNext()) {
          list.next().onUpdateFile(update.file);
        }
      }
    }
  }

  /*public void onFileUpdate (TdApi.UpdateFileGenerationFinish update) {
    synchronized (this) {
      ArrayList<WeakReference<SimpleListener>> listeners = simpleListeners.get(update.file.id);
      if (listeners != null) {
        final int size = listeners.size();
        for (int i = size - 1; i >= 0; i--) {
          SimpleListener listener = listeners.get(i).get();
          if (listener != null) {
            listener.onUpdateFileGenerationFinish(update.file);
          } else {
            listeners.remove(i);
          }
        }
        U.checkReferenceList(simpleListeners, listeners, update.file.id);
      }
    }
  }

  public void onFileGenerationFailed (TdApi.UpdateFileGenerationProgress update) {
    synchronized (this) {
      ArrayList<WeakReference<SimpleListener>> listeners = simpleListeners.get(update.fileId);
      if (listeners != null) {
        final int size = listeners.size();
        for (int i = size - 1; i >= 0; i--) {
          SimpleListener listener = listeners.get(i).get();
          if (listener != null) {
            listener.onUpdateFileGenerationFailure(update.fileId);
          } else {
            listeners.remove(i);
          }
        }
        U.checkReferenceList(simpleListeners, listeners, update.fileId);
      }
    }
  }*/

  public void onFileProgress (TdApi.UpdateFile update) {
    synchronized (this) {
      float progress = TD.getFileProgress(update.file);
      if (progress > 0f) {
        pendingFiles.put(update.file.id, update.file);
      }
      notifyFileProgress(update.file);
    }
  }

  public void onFileLoaded (TdApi.UpdateFile update) {
    synchronized (this) {
      final int fileId = update.file.id;
      int pendingOperation = pendingOperations.get(fileId);
      this.manuallyCancelledFiles.remove(fileId);
      if (pendingOperation != OPERATION_NONE) {
        if (TD.isFileLoadedAndExists(update.file)) {
          removePendingOperation(fileId);
          notifyFileState(fileId, STATE_DOWNLOADED_OR_UPLOADED, update.file);
        } else {
          notifyFileState(fileId, STATE_IN_PROGRESS, update.file);
        }
      } else {
        notifyFileState(fileId, STATE_DOWNLOADED_OR_UPLOADED, update.file);
      }
    }
  }

  public void onFileUpdated (TdApi.UpdateFile update) {
    synchronized (this) {
      final int fileId = update.file.id;
      int pendingOperation = pendingOperations.get(fileId);
      if (pendingOperation != OPERATION_NONE) {
        removePendingOperation(fileId);
      }
      notifyFileState(fileId, STATE_PAUSED, update.file);
    }
  }

  /*public void onUpdateFileGenerationProgress (TdApi.UpdateFileGenerationProgress update) {
    synchronized (this) {
      notifyFileGenerationProgress(update.fileId, update.ready, update.size);
    }
  }

  public void onUpdateFileGenerationFinished (TdApi.UpdateFileGenerationFinish update) {
    synchronized (this) {
      notifyFileGenerationFinished(update.file);
    }
  }*/

  // Settings

  private static final String DOWNLOAD_LIMIT_WIFI_KEY = "settings_limit_wifi";
  private static final String DOWNLOAD_LIMIT_MOBILE_KEY = "settings_limit_mobile";
  private static final String DOWNLOAD_LIMIT_ROAMING_KEY = "settings_limit_roaming";

  public static final int DOWNLOAD_LIMIT_NONE  = 0x00;
  public static final int DOWNLOAD_LIMIT_1MB   = 0x01;
  public static final int DOWNLOAD_LIMIT_5MB   = 0x02;
  public static final int DOWNLOAD_LIMIT_15MB  = 0x03;
  public static final int DOWNLOAD_LIMIT_50MB  = 0x04;
  public static final int DOWNLOAD_LIMIT_100MB = 0x05;
  public static final int DOWNLOAD_LIMIT_500MB = 0x06;
  public static final int[] DOWNLOAD_LIMIT_OPTIONS = {
    DOWNLOAD_LIMIT_1MB,
    DOWNLOAD_LIMIT_5MB,
    DOWNLOAD_LIMIT_15MB,
    DOWNLOAD_LIMIT_50MB,
    DOWNLOAD_LIMIT_100MB,
    DOWNLOAD_LIMIT_500MB,
    DOWNLOAD_LIMIT_NONE,
  };
  public static final int DOWNLOAD_FLAG_PHOTO = 0x01;
  public static final int DOWNLOAD_FLAG_VOICE = 0x02;
  public static final int DOWNLOAD_FLAG_VIDEO = 0x04;
  public static final int DOWNLOAD_FLAG_FILE = 0x08;
  public static final int DOWNLOAD_FLAG_MUSIC = 0x10;
  public static final int DOWNLOAD_FLAG_GIF = 0x20;
  public static final int DOWNLOAD_FLAG_VIDEO_NOTE = 0x40;

  private static final int DOWNLOAD_PRIVATE_SHIFT = 8;
  private static final int DOWNLOAD_GROUPS_SHIFT = 16;
  private static final int DOWNLOAD_CHANNELS_SHIFT = 24;
  private static final int DOWNLOAD_DEFAULTS =
    ((DOWNLOAD_FLAG_PHOTO | DOWNLOAD_FLAG_VOICE | DOWNLOAD_FLAG_GIF | DOWNLOAD_FLAG_VIDEO_NOTE) << DOWNLOAD_PRIVATE_SHIFT) |
      ((DOWNLOAD_FLAG_PHOTO | DOWNLOAD_FLAG_VOICE | DOWNLOAD_FLAG_GIF | DOWNLOAD_FLAG_VIDEO_NOTE) << DOWNLOAD_GROUPS_SHIFT) |
      ((DOWNLOAD_FLAG_PHOTO | DOWNLOAD_FLAG_VOICE | DOWNLOAD_FLAG_GIF | DOWNLOAD_FLAG_VIDEO_NOTE) << DOWNLOAD_CHANNELS_SHIFT);
  private static final int DOWNLOAD_ALL_MEDIAS =
    DOWNLOAD_FLAG_PHOTO | DOWNLOAD_FLAG_VIDEO | DOWNLOAD_FLAG_VOICE |
      DOWNLOAD_FLAG_FILE | DOWNLOAD_FLAG_MUSIC | DOWNLOAD_FLAG_GIF | DOWNLOAD_FLAG_VIDEO_NOTE;
  private static final String DOWNLOAD_KEY = "settings_autodownload";

  private static final int DATASAVER_FLAG_ENABLED = 1; // regardless of connection type
  private static final int DATASAVER_FLAG_WHEN_MOBILE = 1 << 1; // enabled even if it's not
  private static final int DATASAVER_FLAG_WHEN_ROAMING = 1 << 2;
  private static final int DATASAVER_FLAG_CALLS_MOBILE = 1 << 3;
  private static final int DATASAVER_FLAG_CALLS_ALWAYS = 1 << 4;
  private static final int DATASAVER_FLAG_CALLS_ROAMING = 1 << 5;

  private static final String DATASAVER_KEY = "settings_datasaver";

  private int datasaverFlags;
  private int downloadLimitOverWiFi, downloadWiFiExclude;
  private int downloadLimitOverMobile, downloadMobileExclude;
  private int downloadLimitOverRoaming, downloadRoamingExclude;
  private int downloadInPrivateChats, downloadInGroupChats, downloadInChannelChats;

  private static String makeKey (String key, int accountId) {
    return accountId != 0 ? key + "_" + accountId : key;
  }

  private boolean isDataSaverEventuallyEnabled (int connectionState) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (tdlib.context().watchDog().isSystemDataSaverEnabled()) {
        return true;
      }
    }
    switch (connectionState) {
      case WatchDog.CONNECTION_TYPE_ROAMING: {
        return (datasaverFlags & DATASAVER_FLAG_WHEN_ROAMING) != 0;
      }
      case WatchDog.CONNECTION_TYPE_MOBILE: {
        return (datasaverFlags & DATASAVER_FLAG_WHEN_MOBILE) != 0;
      }
    }
    return false;
  }

  public boolean isDataSaverEnabledOverMobile () {
    return (datasaverFlags & DATASAVER_FLAG_WHEN_MOBILE) != 0;
  }

  public boolean isDataSaverEnabledOverRoaming () {
    return (datasaverFlags & DATASAVER_FLAG_WHEN_ROAMING) != 0;
  }

  private static String buildDownloadString (int mediaTypes) {
    StringList strings = new StringList(6);
    if ((mediaTypes & DOWNLOAD_FLAG_PHOTO) != 0) {
      strings.append(R.string.Photos);
    }
    if ((mediaTypes & DOWNLOAD_FLAG_VOICE) != 0) {
      strings.append(R.string.Voice);
    }
    if ((mediaTypes & DOWNLOAD_FLAG_VIDEO_NOTE) != 0) {
      strings.append(R.string.VideoMessages);
    }
    if ((mediaTypes & DOWNLOAD_FLAG_VIDEO) != 0) {
      strings.append(R.string.Videos);
    }
    if ((mediaTypes & DOWNLOAD_FLAG_FILE) != 0) {
      strings.append(R.string.Files);
    }
    if ((mediaTypes & DOWNLOAD_FLAG_MUSIC) != 0) {
      strings.append(R.string.Music);
    }
    if ((mediaTypes & DOWNLOAD_FLAG_GIF) != 0) {
      strings.append(R.string.GIFs);
    }
    return strings.isEmpty() ? Lang.getString(R.string.Nothing) : strings.join(Lang.getConcatSeparator(), Lang.getConcatSeparatorLast(false));
  }

  public int getDownloadInPrivateChats () {
    return downloadInPrivateChats;
  }

  public boolean setDownloadInPrivateChats (int autoDownloadFlags) {
    if (downloadInPrivateChats != autoDownloadFlags) {
      downloadInPrivateChats = autoDownloadFlags;
      saveAutoDownloadSettings();
      return true;
    }
    return false;
  }

  public String getDownloadInPrivateChatsList () {
    return buildDownloadString(downloadInPrivateChats);
  }

  public int getDownloadInGroupChats () {
    return downloadInGroupChats;
  }

  public boolean setDownloadInGroupChats (int autoDownloadFlags) {
    if (downloadInGroupChats != autoDownloadFlags) {
      downloadInGroupChats = autoDownloadFlags;
      saveAutoDownloadSettings();
      return true;
    }
    return false;
  }

  public String getDownloadInGroupChatsList () {
    return buildDownloadString(downloadInGroupChats);
  }

  public int getDownloadInChannelChats () {
    return downloadInChannelChats;
  }

  public boolean setDownloadInChannelChats (int autoDownloadFlags) {
    if (downloadInChannelChats != autoDownloadFlags) {
      downloadInChannelChats = autoDownloadFlags;
      saveAutoDownloadSettings();
      return true;
    }
    return false;
  }

  public String getDownloadInChannelChatsList () {
    return buildDownloadString(downloadInChannelChats);
  }

  public int getExcludeOverWiFi () {
    return downloadWiFiExclude;
  }

  public int getDownloadLimitOverWifi () {
    return downloadLimitOverWiFi;
  }

  public boolean setLimitsOverWiFi (int excludeFlags, int sizeLimit) {
    if (this.downloadWiFiExclude != excludeFlags || this.downloadLimitOverWiFi != sizeLimit) {
      this.downloadWiFiExclude = excludeFlags;
      this.downloadLimitOverWiFi = sizeLimit;
      Settings.instance().putInt(makeKey(DOWNLOAD_LIMIT_WIFI_KEY, tdlib.id()), (downloadWiFiExclude << 24) | (downloadLimitOverWiFi & 0xffffff));
      return true;
    }
    return false;
  }

  public int getExcludeOverMobile () {
    return downloadMobileExclude;
  }

  public int getDownloadLimitOverMobile () {
    return downloadLimitOverMobile;
  }

  public boolean setLimitsOverMobile (int excludeFlags, int sizeLimit) {
    if (this.downloadMobileExclude != excludeFlags || this.downloadLimitOverMobile != sizeLimit) {
      this.downloadMobileExclude = excludeFlags;
      this.downloadLimitOverMobile = sizeLimit;
      Settings.instance().putInt(makeKey(DOWNLOAD_LIMIT_MOBILE_KEY, tdlib.id()), (downloadMobileExclude << 24) | (downloadLimitOverMobile & 0xffffff));
      return true;
    }
    return false;
  }

  public int getExcludeOverRoaming () {
    return downloadRoamingExclude;
  }

  public int getDownloadLimitOverRoaming () {
    return downloadLimitOverRoaming;
  }

  public boolean setLimitsOverRoaming (int excludeFlags, int sizeLimit) {
    if (this.downloadRoamingExclude != excludeFlags || this.downloadLimitOverRoaming != sizeLimit) {
      this.downloadRoamingExclude = excludeFlags;
      this.downloadLimitOverRoaming = sizeLimit;
      Settings.instance().putInt(makeKey(DOWNLOAD_LIMIT_ROAMING_KEY, tdlib.id()), (downloadRoamingExclude << 24) | (downloadLimitOverRoaming & 0xffffff));
      return true;
    }
    return false;
  }

  private static long getDownloadLimit (int limit) {
    switch (limit) {
      case DOWNLOAD_LIMIT_1MB: return ByteUnit.MIB.toBytes(1);
      case DOWNLOAD_LIMIT_5MB: return ByteUnit.MIB.toBytes(5);
      case DOWNLOAD_LIMIT_15MB: return ByteUnit.MIB.toBytes(15);
      case DOWNLOAD_LIMIT_50MB: return ByteUnit.MIB.toBytes(50);
      case DOWNLOAD_LIMIT_100MB: return ByteUnit.MIB.toBytes(100);
      case DOWNLOAD_LIMIT_500MB: return ByteUnit.MIB.toBytes(500);
      case DOWNLOAD_LIMIT_NONE: return Integer.MAX_VALUE;
    }
    return -1;
  }

  private void loadSettings () {
    final Settings prefs = Settings.instance();
    final int accountId = tdlib.id();

    datasaverFlags = prefs.getInt(makeKey(DATASAVER_KEY, accountId), DATASAVER_FLAG_WHEN_ROAMING);

    final int autoDownload = prefs.getInt(makeKey(DOWNLOAD_KEY, accountId), DOWNLOAD_DEFAULTS);
    downloadInPrivateChats = (autoDownload >> DOWNLOAD_PRIVATE_SHIFT) & 0xff;
    downloadInGroupChats = (autoDownload >> DOWNLOAD_GROUPS_SHIFT) & 0xff;
    downloadInChannelChats = (autoDownload >> DOWNLOAD_CHANNELS_SHIFT) & 0xff;

    final int wifiLimits = prefs.getInt(makeKey(DOWNLOAD_LIMIT_WIFI_KEY, accountId), DOWNLOAD_LIMIT_50MB);
    downloadWiFiExclude = (wifiLimits >> 24) & 0xff;
    downloadLimitOverWiFi = wifiLimits & 0xffffff;

    final int mobileLimits = prefs.getInt(makeKey(DOWNLOAD_LIMIT_MOBILE_KEY, accountId), DOWNLOAD_LIMIT_15MB);
    downloadMobileExclude = (mobileLimits >> 24) & 0xff;
    downloadLimitOverMobile = mobileLimits & 0xffffff;

    final int roamingLimits = prefs.getInt(makeKey(DOWNLOAD_LIMIT_ROAMING_KEY, accountId), DOWNLOAD_LIMIT_5MB);
    downloadRoamingExclude = (roamingLimits >> 24) & 0xff;
    downloadLimitOverRoaming = roamingLimits & 0xffffff;
  }

  private void saveDataSaverSettings () {
    Settings.instance().putInt(makeKey(DATASAVER_KEY, tdlib.id()), datasaverFlags);
  }

  private void saveAutoDownloadSettings () {
    int autoDownload =
      (downloadInPrivateChats << DOWNLOAD_PRIVATE_SHIFT) |
        (downloadInGroupChats << DOWNLOAD_GROUPS_SHIFT) |
        (downloadInChannelChats << DOWNLOAD_CHANNELS_SHIFT);
    Settings.instance().putInt(makeKey(DOWNLOAD_KEY, tdlib.id()), autoDownload);
  }

  public boolean isDataSaverActive () {
    return isDataSaverAlwaysEnabled() || isDataSaverEventuallyEnabled();
  }

  public boolean isDataSaverAlwaysEnabled () {
    return (datasaverFlags & DATASAVER_FLAG_ENABLED) != 0;
  }

  public int getVoipDataSavingOption () {
    if ((datasaverFlags & DATASAVER_FLAG_CALLS_ALWAYS) != 0)
      return VoIPController.DATA_SAVING_ALWAYS;
    if ((datasaverFlags & DATASAVER_FLAG_CALLS_MOBILE) != 0)
      return VoIPController.DATA_SAVING_MOBILE;
    if ((datasaverFlags & DATASAVER_FLAG_CALLS_ROAMING) != 0)
      return VoIPController.DATA_SAVING_ROAMING;
    return VoIPController.DATA_SAVING_NEVER;
  }

  public boolean setVoipDataSavingOption (int option) {
    int flags = datasaverFlags;
    flags &= ~DATASAVER_FLAG_CALLS_MOBILE;
    flags &= ~DATASAVER_FLAG_CALLS_ALWAYS;
    flags &= ~DATASAVER_FLAG_CALLS_ROAMING;
    flags |= option == VoIPController.DATA_SAVING_ALWAYS ? DATASAVER_FLAG_CALLS_ALWAYS :
             option == VoIPController.DATA_SAVING_MOBILE ? DATASAVER_FLAG_CALLS_MOBILE :
             option == VoIPController.DATA_SAVING_ROAMING ? DATASAVER_FLAG_CALLS_ROAMING : 0;
    if (this.datasaverFlags != flags) {
      this.datasaverFlags = flags;
      saveDataSaverSettings();
      return true;
    }
    return false;
  }

  public boolean setDataSaverEnabled (boolean isEnabled) {
    boolean nowEnabled = (datasaverFlags & DATASAVER_FLAG_ENABLED) != 0;
    if (nowEnabled != isEnabled) {
      datasaverFlags ^= DATASAVER_FLAG_ENABLED;
      if (isEnabled) {
        cancelAllPendingDownloads();
      }
      saveDataSaverSettings();
      return true;
    }
    return false;
  }

  public boolean setDataSaverForcedOptions (boolean forcedMobile, boolean forcedRoaming) {
    int futureFlags = datasaverFlags;
    futureFlags &= ~DATASAVER_FLAG_WHEN_MOBILE;
    futureFlags &= ~DATASAVER_FLAG_WHEN_ROAMING;
    if (forcedMobile) {
      futureFlags |= DATASAVER_FLAG_WHEN_MOBILE;
    }
    if (forcedRoaming) {
      futureFlags |= DATASAVER_FLAG_WHEN_ROAMING;
    }
    return setDataSaverFlags(futureFlags);
  }

  public boolean setDataSaverFlags (int flags) {
    if ((datasaverFlags & DATASAVER_FLAG_ENABLED) != 0) {
      flags |= DATASAVER_FLAG_ENABLED;
    }
    if (this.datasaverFlags != flags) {
      boolean oldIsDataSaverActive = isDataSaverActive();
      this.datasaverFlags = flags;
      if (isDataSaverActive() && !oldIsDataSaverActive) {
        cancelAllPendingDownloads();
      }
      saveDataSaverSettings();
      return true;
    }
    return false;
  }

  public boolean isDataSaverEventuallyEnabled () {
    return isDataSaverEventuallyEnabled(tdlib.context().watchDog().getConnectionType());
  }

  @Deprecated
  public static void upgradeSharedPreferences (SharedPreferences prefs, SharedPreferences.Editor editor) {
    final int currentAutoDownload = prefs.getInt(DOWNLOAD_KEY, DOWNLOAD_DEFAULTS);
    int downloadInPrivateChats = (currentAutoDownload >> DOWNLOAD_PRIVATE_SHIFT) & 0xff;
    int downloadInGroupChats = (currentAutoDownload >> DOWNLOAD_GROUPS_SHIFT) & 0xff;
    int downloadInChannelChats = (currentAutoDownload >> DOWNLOAD_CHANNELS_SHIFT) & 0xff;

    if ((downloadInPrivateChats & DOWNLOAD_FLAG_VOICE) != 0) {
      downloadInPrivateChats |= DOWNLOAD_FLAG_VIDEO_NOTE;
    }
    if ((downloadInGroupChats & DOWNLOAD_FLAG_VOICE) != 0) {
      downloadInGroupChats |= DOWNLOAD_FLAG_VIDEO_NOTE;
    }
    if ((downloadInChannelChats & DOWNLOAD_FLAG_VOICE) != 0) {
      downloadInChannelChats |= DOWNLOAD_FLAG_VIDEO_NOTE;
    }
    final int newAutoDownload =
      (downloadInPrivateChats << DOWNLOAD_PRIVATE_SHIFT) |
        (downloadInGroupChats << DOWNLOAD_GROUPS_SHIFT) |
        (downloadInChannelChats << DOWNLOAD_CHANNELS_SHIFT);

    if (currentAutoDownload != newAutoDownload) {
      editor.putInt(DOWNLOAD_KEY, newAutoDownload);
    }
  }

  public boolean canAutomaticallyDownload (@NonNull TdApi.File file, int fileType, @Nullable TdApi.ChatType chatType) {
    if (isDataSaverActive()) {
      return false;
    }
    if (file.remote.isUploadingActive) {
      return false;
    }
    int limit, excludedTypes;
    switch (tdlib.context().watchDog().getConnectionType()) {
      case WatchDog.CONNECTION_TYPE_WIFI: {
        limit = downloadLimitOverWiFi;
        excludedTypes = downloadWiFiExclude;
        break;
      }
      case WatchDog.CONNECTION_TYPE_ROAMING: {
        limit = downloadLimitOverRoaming;
        excludedTypes = downloadRoamingExclude;
        break;
      }
      case WatchDog.CONNECTION_TYPE_MOBILE:
      default: {
        limit = downloadLimitOverMobile;
        excludedTypes = downloadMobileExclude;
        break;
      }
    }
    if (file.size > getDownloadLimit(limit) || (excludedTypes & fileType) != 0) {
      return false;
    }
    int automaticDownloadTypes;
    if (chatType == null) {
      automaticDownloadTypes = (DOWNLOAD_DEFAULTS >> DOWNLOAD_PRIVATE_SHIFT) & 0xff;
    } else {
      switch (chatType.getConstructor()) {
        case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
          automaticDownloadTypes = TD.isSupergroup(chatType) ? downloadInGroupChats : downloadInChannelChats;
          break;
        }
        case TdApi.ChatTypeSecret.CONSTRUCTOR:
        case TdApi.ChatTypePrivate.CONSTRUCTOR: {
          automaticDownloadTypes = downloadInPrivateChats;
          break;
        }
        case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
          automaticDownloadTypes = downloadInGroupChats;
          break;
        }
        default: {
          return false;
        }
      }
    }
    if ((automaticDownloadTypes & fileType) == 0) {
      return false;
    }
    return !manuallyCancelledFiles.contains(file.id);
  }

  private static String getDownloadLimitString (int limit, String valueIfEmpty) {
    if (limit == TdlibFilesManager.DOWNLOAD_LIMIT_NONE)
      return valueIfEmpty;
    long size = getDownloadLimit(limit);
    if (size == Integer.MAX_VALUE) {
      return Lang.getString(R.string.fileSize_GB, "∞");
    }
    return Strings.buildSize(size);
  }

  public static String getDownloadLimitString (int limit) {
    return getDownloadLimitString(limit, Lang.getString(R.string.fileSize_GB, "∞"));
  }

  private static String getDownloadLimitString (int limit, int excludedTypes) {
    StringBuilder b = new StringBuilder();
    String downloadLimit = getDownloadLimitString(limit, null);
    if (excludedTypes == DOWNLOAD_ALL_MEDIAS) {
      b.append(Lang.getString(R.string.AnyMedia));
    } else if (downloadLimit != null) {
      b.append(Lang.getString(R.string.MediaExceedingX, downloadLimit));
    } else if (excludedTypes == 0) {
      b.append(Lang.getString(R.string.NoRestrictions));
    }
    if (excludedTypes != 0 && excludedTypes != DOWNLOAD_ALL_MEDIAS) {
      if (b.length() > 0) {
        b.append(", ");
      }
      b.append(buildDownloadString(excludedTypes));
    }
    return b.toString();
  }

  public String getDownloadLimitOverWiFiString () {
    return getDownloadLimitString(downloadLimitOverWiFi, downloadWiFiExclude);
  }

  public String getDownloadLimitOverMobileString () {
    return getDownloadLimitString(downloadLimitOverMobile, downloadMobileExclude);
  }

  public String getDownloadLimitOverRoamingString () {
    return getDownloadLimitString(downloadLimitOverRoaming, downloadRoamingExclude);
  }

  /*public long getCurrentDownloadLimit () {
    switch (WatchDog.instance().getConnectionType()) {
      case WatchDog.CONNECTION_TYPE_WIFI: {
        return getDownloadLimit(downloadLimitOverWiFi);
      }
      case WatchDog.CONNECTION_TYPE_ROAMING: {
        return getDownloadLimit(downloadLimitOverRoaming);
      }
      case WatchDog.CONNECTION_TYPE_MOBILE:
      default: {
        return getDownloadLimit(downloadLimitOverMobile);
      }
    }
  }*/

  // Data saver

  @Override
  public void onConnectionStateChanged (Tdlib tdlib, int newState, boolean isCurrent) { }

  @Override
  public void onConnectionTypeChanged (int oldConnectionType, int connectionType) {
    if (!isDataSaverEventuallyEnabled(oldConnectionType) && isDataSaverEventuallyEnabled(connectionType)) {
      cancelAllPendingDownloads();
    }
  }

  @Override
  public void onSystemDataSaverStateChanged (boolean isEnabled) {
    if (isEnabled) {
      cancelAllPendingDownloads();
    }
  }
}
