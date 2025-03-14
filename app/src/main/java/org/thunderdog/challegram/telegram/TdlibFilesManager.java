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
 * File created on 24/11/2016
 */
package org.thunderdog.challegram.telegram;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.SparseIntArray;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.core.os.CancellationSignal;
import androidx.media3.common.C;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.WatchDog;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.voip.annotation.DataSavingOption;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.ObjectUtils;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceIntMap;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.unit.ByteUnit;
import tgx.td.Td;

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

  public static final int PRIORITY_PERSISTENT_IMAGE = 1;
  public static final int PRIORITY_GIFS = 1;
  public static final int PRIORITY_FILE_GENERATION = 1;

  public static final int PRIORITY_SELF_EMOJI_STATUS = 3;
  public static final int PRIORITY_SELF_EMOJI_STATUS_THUMBNAIL = 4;
  public static final int PRIORITY_SELF_AVATAR_BIG = 5;
  public static final int PRIORITY_SELF_AVATAR_SMALL = 6;

  public static final int PRIORITY_IMAGE = 15;
  public static final int PRIORITY_USER_REQUEST_DOWNLOAD = 16;
  public static final int PRIORITY_USER_REQUEST_SHARE = 17;

  public static final int PRIORITY_STREAMING_DEFAULT = 20;
  public static final int PRIORITY_STREAMING_AUDIO = 20;
  public static final int PRIORITY_STREAMING_VIDEO = 21;
  public static final int PRIORITY_STREAMING_HLS_VIDEO = 22;
  public static final int PRIORITY_STREAMING_HLS_PLAYLIST = 25;

  public static final int PRIORITY_CHAT_WALLPAPER = 29;
  public static final int PRIORITY_SERVICE_FILES = 30;

  public static final int PRIORITY_NOTIFICATION_MEDIA = 31;
  public static final int PRIORITY_NOTIFICATION_AVATAR = 32;

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

  private final SparseArrayCompat<DownloadOperation> activeDownloadReferences;

  public static final int PRIORITY_UNSET = 0;
  public static final int OFFSET_UNSET = C.INDEX_UNSET;
  public static final int LIMIT_UNSET = C.LENGTH_UNSET;
  public static final int LIMIT_MAX = 0; // Instruct to download fully

  private static class DownloadOperation {
    private static class Chunk {
      public final int priority;
      public final long offset, limit;
      @Nullable
      public final Object source;

      public Chunk (int priority, long offset, long limit, @Nullable Object source) {
        this.priority = priority;
        if (limit == LIMIT_UNSET) {
          limit = LIMIT_MAX;
        }
        this.offset = offset;
        this.limit = limit;
        this.source = source;
      }

      public boolean isPositioned () {
        return offset != OFFSET_UNSET;
      }

      public boolean isFull () {
        return limit == LIMIT_MAX;
      }

      public long endPosition () {
        if (!isPositioned())
          throw new IllegalStateException();
        return offset + limit;
      }

      @Override
      public boolean equals (Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Chunk chunk = (Chunk) o;
        return priority == chunk.priority && offset == chunk.offset && limit == chunk.limit && ObjectUtils.equals(source, this.source);
      }

      @Override
      public int hashCode () {
        return ObjectUtils.hashCode(priority, offset, limit, source);
      }
    }

    public boolean hasChunksWithSource (FileUpdateListener source) {
      return indexOfFirstChunkWithSource(source) != -1;
    }

    public int indexOfFirstChunkWithSource (FileUpdateListener source) {
      int index = 0;
      for (Chunk chunk : chunks) {
        if (ObjectUtils.equals(chunk.source, source)) {
          return index;
        }
        index++;
      }
      return -1;
    }

    public final int fileId;
    public final List<Chunk> chunks;
    private final List<FileUpdateListener> listenersList;

    private int priority = PRIORITY_UNSET;
    private long offset = OFFSET_UNSET, limit = LIMIT_UNSET;

    private int requestedPriority = PRIORITY_UNSET;
    private long requestedOffset = OFFSET_UNSET, requestedLimit = LIMIT_UNSET;

    public final boolean cancelOnRemoval;

    public DownloadOperation (int fileId, boolean cancelOnRemoval) {
      this.fileId = fileId;
      this.cancelOnRemoval = cancelOnRemoval;
      this.chunks = new ArrayList<>();
      this.listenersList = new ArrayList<>();
    }

    public boolean isDownloaded (TdApi.File file, long offset, long length) {
      if (offset == OFFSET_UNSET) {
        offset = 0;
      }
      if (length == LIMIT_MAX) {
        if (file.size == 0) {
          return false;
        }
        length = file.size - offset;
      }
      return offset >= file.local.downloadOffset && offset + length <= file.local.downloadedPrefixSize;
    }

    public boolean requiresDownloadRestart (TdApi.File file) {
      return !isEmpty() && !isDownloaded(file, offset, limit) && (!file.local.isDownloadingActive && !file.local.isDownloadingCompleted && file.local.canBeDownloaded);
    }

    public boolean prepareNewRequest (TdApi.File file) {
      if (isEmpty() || isDownloaded(file, offset, limit)) {
        return false;
      }
      if (requestedOffset == OFFSET_UNSET || requestedLimit == LIMIT_UNSET || // First request
        offset < requestedOffset || // Seeking backwards
        (requestedLimit == LIMIT_MAX) != (limit == LIMIT_MAX) || // Limited/unlimited switch
        requestedPriority != priority
      ) {
        // First request: pass offset,limit as is
        requestedPriority = priority;
        requestedOffset = offset;
        requestedLimit = limit;
        return true;
      }

      final long requestedEnd = requestedOffset + requestedLimit;
      if (requestedLimit != LIMIT_MAX) {
        if (offset == requestedEnd) {
          // Sequential request: grow limit
          if (limit == LIMIT_MAX) {
            requestedLimit = limit;
          } else {
            requestedLimit += limit;
          }
          return true;
        }
        if (offset > requestedEnd && offset - requestedEnd <= ByteUnit.KIB.toBytes(128)) {
          // Gap from previous part is small: grow limit
          if (limit == LIMIT_MAX) {
            requestedLimit = LIMIT_MAX;
          } else {
            requestedLimit += offset - requestedEnd + limit;
          }
          return true;
        }
        if (offset >= requestedOffset && offset + limit <= requestedOffset + requestedLimit) {
          long extraBytesBeforeChunk = offset - requestedOffset;
          long extraBytesAfterChunk = (requestedOffset + requestedLimit) - (offset + limit);
          if (extraBytesBeforeChunk + extraBytesAfterChunk < ByteUnit.KIB.toBytes(512)) {
            // Covered by previous offset,limit
            return false;
          }
        }
      } else {
        if (TD.withinDistance(file, offset)) {
          // required chunk is being downloaded
          return false;
        }
      }

      // Fallback: pass request as is
      requestedOffset = offset;
      requestedLimit = limit;

      return true;
    }

    public boolean addChunk (FileUpdateListener source, int priority, long offset, long limit) {
      boolean firstChunk = !hasChunksWithSource(source);
      chunks.add(new Chunk(priority, offset, limit, source));
      if (firstChunk) {
        listenersList.add(source);
      }
      return updateOffsetAndLimit();
    }

    public boolean replaceChunk (FileUpdateListener source, int priority, long offset, long limit) {
      int index = indexOfFirstChunkWithSource(source);
      if (index != -1) {
        Chunk existingChunk = chunks.get(index);
        Chunk newChunk = new Chunk(priority, offset, limit, source);
        if (!existingChunk.equals(newChunk)) {
          chunks.set(index, newChunk);
          return updateOffsetAndLimit();
        }
      }
      return false;
    }

    public boolean isEmpty () {
      return chunks.isEmpty() || offset == OFFSET_UNSET || limit == LIMIT_UNSET;
    }

    public boolean removeAllChunksWithSource (FileUpdateListener source) {
      boolean removed = false;
      for (int i = chunks.size() - 1; i >= 0; i--) {
        Chunk chunk = chunks.get(i);
        if (ObjectUtils.equals(chunk.source, source)) {
          chunks.remove(i);
          removed = true;
        }
      }
      if (removed) {
        listenersList.remove(source);
      }
      return removed && updateOffsetAndLimit();
    }

    private boolean updateOffsetAndLimit () {
      int priority = PRIORITY_UNSET;
      long offset = OFFSET_UNSET;
      long limit = cancelOnRemoval ? LIMIT_UNSET : LIMIT_MAX;
      if (!chunks.isEmpty()) {
        for (int index = chunks.size() - 1; index >= 0; index--) {
          Chunk chunk = chunks.get(index);
          priority = priority == PRIORITY_UNSET ? chunk.priority : Math.max(priority, chunk.priority);
          if (offset != OFFSET_UNSET && limit == LIMIT_MAX) {
            break;
          }
          if (limit == LIMIT_UNSET) {
            offset = chunk.offset;
            limit = chunk.limit;
            continue;
          }
          if (offset == OFFSET_UNSET || !chunk.isPositioned()) {
            offset = offset == OFFSET_UNSET ? chunk.offset : offset;
            limit = limit == LIMIT_MAX || chunk.isFull() ? LIMIT_MAX : Math.max(limit, chunk.limit);
            continue;
          }
          if (chunk.isFull()) {
            limit = LIMIT_MAX;
          }
          if (chunk.offset < offset) {
            if (limit != LIMIT_MAX) {
              limit += (offset - chunk.offset);
            }
            offset = chunk.offset;
          }
          if (limit != LIMIT_MAX) {
            long currentEndPosition = offset + limit;
            long endPosition = chunk.endPosition();
            if (endPosition > currentEndPosition) {
              limit += (endPosition - currentEndPosition);
            }
          }
        }
        if (offset == OFFSET_UNSET) {
          offset = 0;
        }
        if (limit == LIMIT_UNSET) {
          limit = LIMIT_MAX;
        }
      }
      if (this.priority != priority || this.offset != offset || this.limit != limit) {
        this.priority = priority;
        this.offset = offset;
        this.limit = limit;
        return true;
      }
      return false;
    }

    public void forEachListenerReverse (RunnableData<FileUpdateListener> actor) {
      for (int i = listenersList.size() - 1; i >= 0; i--) {
        FileUpdateListener source = listenersList.get(i);
        actor.runWithData(source);
      }
    }
  }

  private final HashSet<Integer> manuallyCancelledFiles;

  TdlibFilesManager (Tdlib tdlib) {
    this.tdlib = tdlib;

    this.listeners = new ReferenceIntMap<>(true);
    this.simpleListeners = new ReferenceIntMap<>(true);
    this.globalListeners = new ReferenceList<>(true);

    this.pendingOperations = new SparseIntArray();
    this.pendingFiles = new HashMap<>();

    this.activeDownloadReferences = new SparseArrayCompat<>();

    this.manuallyCancelledFiles = new HashSet<>();

    loadSettings();

    tdlib.context().global().addConnectionListener(this);
  }

  public void syncFiles (@Nullable final List<TdApi.File> files, final long timeoutMs) {
    if (files != null) {
      for (TdApi.File file : files) {
        syncFile(file, null, timeoutMs);
      }
    }
  }

  public void syncFile (@NonNull final TdApi.File file, @Nullable TdApi.FileType remoteFileType, final long timeoutMs) {
    final CountDownLatch latch = new CountDownLatch(1);
    TdApi.Function<TdApi.File> function;
    if (remoteFileType != null) {
      function = new TdApi.GetRemoteFile(file.remote.id, remoteFileType);
    } else {
      function = new TdApi.GetFile(file.id);
    }
    final AtomicBoolean signal = new AtomicBoolean();
    tdlib.send(function, (tdlibFile, error) -> {
      if (error != null) {
        Log.w("getFile error: %s", TD.toErrorString(error));
      } else {
        synchronized (signal) {
          if (!signal.getAndSet(true)) {
            Td.copyTo(tdlibFile, file);
          }
        }
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
    if (!signal.get()) {
      synchronized (signal) {
        signal.set(true);
      }
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    DownloadState.PENDING,
    DownloadState.DOWNLOADED,
    DownloadState.TIMEOUT,
    DownloadState.CANCELED
  })
  public @interface DownloadState {
    int
      PENDING = 0,
      DOWNLOADED = 1,
      TIMEOUT = 2,
      CANCELED = 3;
  }

  public Runnable downloadFileSync (@NonNull final TdApi.File file, final int priority, final long timeoutMs, final @Nullable RunnableData<TdApi.File> after, final @Nullable RunnableData<TdApi.File> fileUpdateListener, @Nullable CancellationSignal cancellationSignal) {
    if (TD.isFileLoaded(file)) {
      return null;
    }
    final CountDownLatch latch = timeoutMs >= 0 ? new CountDownLatch(1) : null;
    final AtomicInteger state = new AtomicInteger(DownloadState.PENDING);
    final FileUpdateListener listener = new FileUpdateListener() {
      @Override
      public void onUpdateFile (TdApi.UpdateFile updateFile) {
        synchronized (state) {
          int currentState = state.get();
          if (currentState == DownloadState.CANCELED) {
            return;
          }
          if (currentState == DownloadState.TIMEOUT) {
            if (after != null && updateFile.file.local.isDownloadingCompleted) {
              after.runWithData(updateFile.file);
            } else if (fileUpdateListener != null) {
              fileUpdateListener.runWithData(updateFile.file);
            }
            return;
          }
          Td.copyTo(updateFile.file, file);
          if (updateFile.file.local.isDownloadingCompleted) {
            state.set(DownloadState.DOWNLOADED);
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
    addCloudReference(file, priority, listener, false);
    Runnable onCancel =  () -> {
      synchronized (state) {
        if (state.compareAndSet(DownloadState.PENDING, DownloadState.CANCELED)) {
          removeCloudReference(file, listener);
          tdlib.listeners().removeFileListener(file.id, listener);
        }
      }
    };
    if (cancellationSignal != null) {
      cancellationSignal.setOnCancelListener(onCancel::run);
    }
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
      synchronized (state) {
        if (state.compareAndSet(DownloadState.PENDING, after != null ? DownloadState.TIMEOUT : DownloadState.CANCELED)) {
          if (after == null) {
            removeCloudReference(file, listener);
            tdlib.listeners().removeFileListener(file.id, listener);
          }
        }
      }
      return null;
    } else {
      return onCancel;
    }
  }

  private Tdlib.ResultHandler<TdApi.File> fileHandler (int fileId) {
    return (file, error) -> {
      Runnable abort = () -> {
        int pendingOperation = pendingOperations.get(fileId);
        if (pendingOperation == OPERATION_DOWNLOAD) {
          removePendingOperation(fileId);
          synchronized (this) {
            notifyFileState(fileId, STATE_PAUSED, null);
          }
        }
      };
      if (error != null) {
        abort.run();
        return;
      }
      synchronized (activeDownloadReferences) {
        DownloadOperation downloadOperation = activeDownloadReferences.get(file.id);
        if (downloadOperation != null) {
          downloadOperation.forEachListenerReverse(listener ->
            listener.onUpdateFile(new TdApi.UpdateFile(Td.copyOf(file)))
          );
        }
      }
      if (file.local.isDownloadingCompleted) {
        onFileLoaded(new TdApi.UpdateFile(file));
      } else if (!file.local.isDownloadingActive) {
        abort.run();
      }
    };
  }

  // Cloud referencing


  private void performDownloadRequest (TdApi.File file, DownloadOperation operation) {
    if (operation.prepareNewRequest(file)) {
      if (!Config.DEBUG_DISABLE_DOWNLOAD) {
        tdlib.send(downloadFile(file.id, operation.requestedPriority, operation.requestedOffset, operation.requestedLimit, false), fileHandler(file.id));
      }
    }
  }

  private TdApi.DownloadFile downloadFile (int fileId, int priority, long offset, long limit, boolean synchronous) {
    if (Log.isEnabled(Log.TAG_TDLIB_FILES)) {
      Log.i(Log.TAG_TDLIB_FILES, "downloadFile:%d priority:%d offset:%d limit:%d synchro:%b", fileId, priority, offset, limit, synchronous);
    }
    if (BuildConfig.DEBUG) {
      Log.v("downloadFile#%d priority:%d offset:%d limit:%d synchro:%b", fileId, priority, offset, limit, synchronous);
    }
    if (limit == LIMIT_UNSET) {
      limit = LIMIT_MAX; // Download full file
    }
    if (offset == OFFSET_UNSET) {
      offset = 0;
    }
    return new TdApi.DownloadFile(fileId, priority, offset, limit, synchronous);
  }

  public void updateCloudReference (TdApi.File file, FileUpdateListener source, int newPriority, long newOffset, long newLimit) {
    synchronized (activeDownloadReferences) {
      DownloadOperation downloadOperation = activeDownloadReferences.get(file.id);
      if (downloadOperation != null && (downloadOperation.replaceChunk(source, newPriority, newOffset, newLimit) || downloadOperation.requiresDownloadRestart(file))) {
        performDownloadRequest(file, downloadOperation);
      }
    }
  }

  public void addCloudReference (TdApi.File file, int priority, FileUpdateListener source, boolean allowDuplicates) {
    addCloudReference(file, priority, OFFSET_UNSET, LIMIT_UNSET, source, allowDuplicates);
  }

  public void addCloudReference (TdApi.File file, int priority, long offset, long limit, FileUpdateListener source, boolean allowDuplicates) {
    synchronized (activeDownloadReferences) {
      DownloadOperation downloadOperation = activeDownloadReferences.get(file.id);
      if (downloadOperation != null) {
        if (!allowDuplicates && downloadOperation.hasChunksWithSource(source)) {
          throw new IllegalStateException();
        }
        if (downloadOperation.addChunk(source, priority, offset, limit) || downloadOperation.requiresDownloadRestart(file)) {
          performDownloadRequest(file, downloadOperation);
        }
        return;
      }

      boolean needStartDownload = !file.local.isDownloadingActive;
      downloadOperation = new DownloadOperation(file.id, needStartDownload);
      downloadOperation.addChunk(source, priority, offset, limit);
      synchronized (this) {
        activeDownloadReferences.put(file.id, downloadOperation);
      }

      performDownloadRequest(file, downloadOperation);
    }
  }

  public void removeCloudReference (TdApi.File file, FileUpdateListener source) {
    synchronized (activeDownloadReferences) {
      int index = activeDownloadReferences.indexOfKey(file.id);
      if (index < 0) {
        return;
      }
      DownloadOperation operation = activeDownloadReferences.valueAt(index);
      if (operation == null) {
        return;
      }
      if ((operation.removeAllChunksWithSource(source) || operation.requiresDownloadRestart(file)) && !operation.isEmpty()) {
        performDownloadRequest(file, operation);
      }
      if (operation.isEmpty()) {
        synchronized (this) {
          activeDownloadReferences.removeAt(index);
          if (operation.cancelOnRemoval) {
            if (BuildConfig.DEBUG) {
              Log.w("downloadFile#%d cancel", file.id);
            }
            tdlib.send(new TdApi.CancelDownloadFile(file.id, false), tdlib.typedOkHandler());
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

  private void downloadFileInternal (int fileId, int priority, long offset, long limit, final @Nullable Tdlib.ResultHandler<TdApi.File> handler) {
    int pendingOperation = pendingOperations.get(fileId);
    if (pendingOperation == OPERATION_NONE) {
      pendingOperations.put(fileId, OPERATION_DOWNLOAD);
      notifyFileState(fileId, STATE_IN_PROGRESS, null);
      if (!Config.DEBUG_DISABLE_DOWNLOAD) {
        if (handler != null) {
          tdlib.send(downloadFile(fileId, priority, offset, limit, false), (file, error) -> {
            fileHandler(fileId).onResult(file, error);
            handler.onResult(file, error);
          });
        } else {
          tdlib.send(downloadFile(fileId, priority, offset, limit, false), fileHandler(fileId));
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
        if (BuildConfig.DEBUG) {
          Log.w("downloadFile#%d cancel", fileId);
        }
        tdlib.send(new TdApi.CancelDownloadFile(fileId, weak), tdlib.typedOkHandler());
        break;
      }
    }
  }

  private void removePendingOperation (int fileId) {
    pendingOperations.delete(fileId);
    pendingFiles.remove(fileId);
  }

  // Download for whatever reason

  public void downloadFile (@NonNull TdApi.File file) {
    downloadFile(file, PRIORITY_USER_REQUEST_DOWNLOAD, null);
  }

  public void downloadFile (@NonNull TdApi.File file, @IntRange(from = 1, to = 32) int priority, @Nullable Tdlib.ResultHandler<TdApi.File> handler) {
    synchronized (this) {
      long offset = 0;
      long limit = LIMIT_MAX;
      manuallyCancelledFiles.remove(file.id);
      if (!TD.isFileLoaded(file)) {
        downloadFileInternal(file.id, priority, offset, limit, handler);
      } else if (handler != null) {
        tdlib.send(downloadFile(file.id, priority, offset, limit, false), handler);
      }
    }
  }

  public void isFileLoadedAndExists (TdApi.File file, RunnableBool after) {
    tdlib.runOnTdlibThread(() -> {
      boolean loadedAndExists = TD.isFileLoadedAndExists(file);
      after.runWithBool(loadedAndExists);
    });
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

  public void onFileUpdate (TdApi.UpdateFile update) {
    // pendingOperations.get()
    synchronized (this) {
      int pendingOperation = pendingOperations.get(update.file.id);

      if (pendingOperation != OPERATION_NONE) {
        if (!update.file.remote.isUploadingActive && !update.file.local.isDownloadingActive && !update.file.remote.isUploadingCompleted && !update.file.local.isDownloadingCompleted) {
          removePendingOperation(update.file.id);
          notifyFileState(update.file.id, STATE_PAUSED, null);
        }
      } else if (update.file.local.isDownloadingActive) {
        pendingOperations.put(update.file.id, OPERATION_DOWNLOAD);
        notifyFileState(update.file.id, STATE_IN_PROGRESS, null);
      }

      final Iterator<SimpleListener> list = simpleListeners.iterator(update.file.id);
      if (list != null) {
        while (list.hasNext()) {
          list.next().onUpdateFile(update.file);
        }
      }
    }
  }

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

  public @DataSavingOption int getVoipDataSavingOption () {
    if ((datasaverFlags & DATASAVER_FLAG_CALLS_ALWAYS) != 0)
      return DataSavingOption.ALWAYS;
    if ((datasaverFlags & DATASAVER_FLAG_CALLS_MOBILE) != 0)
      return DataSavingOption.MOBILE;
    if ((datasaverFlags & DATASAVER_FLAG_CALLS_ROAMING) != 0)
      return DataSavingOption.ROAMING;
    return DataSavingOption.NEVER;
  }

  public @DataSavingOption int getEffectiveVoipDataSavingOption () {
    @DataSavingOption int dataSavingOption = getVoipDataSavingOption();
    if (dataSavingOption == DataSavingOption.ROAMING) {
      if (U.isRoaming()) {
        return DataSavingOption.MOBILE;
      } else {
        return DataSavingOption.NEVER;
      }
    }
    return dataSavingOption;
  }

  public boolean setVoipDataSavingOption (@DataSavingOption int option) {
    int flags = datasaverFlags;
    flags &= ~DATASAVER_FLAG_CALLS_MOBILE;
    flags &= ~DATASAVER_FLAG_CALLS_ALWAYS;
    flags &= ~DATASAVER_FLAG_CALLS_ROAMING;
    flags |= option == DataSavingOption.ALWAYS ? DATASAVER_FLAG_CALLS_ALWAYS :
             option == DataSavingOption.MOBILE ? DATASAVER_FLAG_CALLS_MOBILE :
             option == DataSavingOption.ROAMING ? DATASAVER_FLAG_CALLS_ROAMING : 0;
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

  private static final int VALIDATE_FILE_SIZE_OK = 0;
  private static final int VALIDATE_FILE_SIZE_NOT_ENOUGH_STORAGE = 1;
  private static final int VALIDATE_FILE_SIZE_TOO_BIG = 2;

  private int validateDownloadFileSize (@NonNull TdApi.File file) {
    // TODO: check whether filesystem supports files of that size
    // TODO (?): async check whether enough disk space available
    return VALIDATE_FILE_SIZE_OK;
  }

  public boolean canAutomaticallyDownload (@NonNull TdApi.File file, int fileType, @Nullable TdApi.ChatType chatType) {
    if (isDataSaverActive()) {
      return false;
    }
    if (file.remote.isUploadingActive) {
      return false;
    }
    if (validateDownloadFileSize(file) != VALIDATE_FILE_SIZE_OK) {
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
