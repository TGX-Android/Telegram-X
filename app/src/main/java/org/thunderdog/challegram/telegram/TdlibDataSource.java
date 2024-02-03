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
 */
package org.thunderdog.challegram.telegram;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public final class TdlibDataSource extends BaseDataSource {
  private static final String SCHEME = "tg";
  private static final String AUTHORITY = "file";
  private static final String PARAM_ACCOUNT = "account";
  private static final String PARAM_FILE_ID = "id";
  private static final String PARAM_REMOTE_ID = "remote_id";

  public static final class UriFactory {
    public static Uri create (int accountId, TdApi.File file) {
      return file.id < 0 ? create(accountId, file.remote.id) : create(accountId, file.id);
    }

    public static Uri create (int accountId, int fileId) {
      if (accountId == TdlibAccount.NO_ID)
        throw new IllegalArgumentException();
      return new Uri.Builder()
        .scheme(SCHEME)
        .authority(AUTHORITY)
        .appendQueryParameter(PARAM_ACCOUNT, Integer.toString(accountId))
        .appendQueryParameter(PARAM_FILE_ID, Integer.toString(fileId))
        .build();
    }

    public static Uri create (int accountId, String remoteId) {
      if (accountId == TdlibAccount.NO_ID)
        throw new IllegalArgumentException();
      return new Uri.Builder()
        .scheme(SCHEME)
        .authority(AUTHORITY)
        .appendQueryParameter(PARAM_ACCOUNT, Integer.toString(accountId))
        .appendQueryParameter(PARAM_REMOTE_ID, remoteId)
        .build();
    }
  }

  public static class TdlibDataSourceException extends IOException {
    public TdlibDataSourceException (String message) {
      super(message);
    }

    public TdlibDataSourceException (Throwable cause) {
      super(cause);
    }

    public TdlibDataSourceException (String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class Factory implements DataSource.Factory {
    @Override
    @NonNull
    public DataSource createDataSource () {
      return new TdlibDataSource();
    }
  }

  private Uri uri;
  private long bytesRead;
  private Tdlib tdlib;
  private final Object fileLock = new Object();
  private TdApi.File file;

  private final FileUpdateListener listener = this::processUpdate;

  private boolean referenceAcquired;

  public TdlibDataSource () {
    super(true);
  }

  @Override
  public long open (DataSpec dataSpec) throws TdlibDataSourceException {
    final Uri uri = dataSpec.uri;
    if (!SCHEME.equals(uri.getScheme()))
      throw new TdlibDataSourceException("Unsupported URI scheme: " + uri.getScheme());
    if (!AUTHORITY.equals(uri.getAuthority()))
      throw new TdlibDataSourceException("Unsupported URI authority: " + uri.getAuthority());
    final int accountId = StringUtils.parseInt(uri.getQueryParameter(PARAM_ACCOUNT), TdlibAccount.NO_ID);
    if (accountId == TdlibAccount.NO_ID)
      throw new TdlibDataSourceException(PARAM_ACCOUNT + " parameter is missing");
    final int fileId = StringUtils.parseInt(uri.getQueryParameter(PARAM_FILE_ID), -1);
    final String remoteId = uri.getQueryParameter(PARAM_REMOTE_ID);
    if (fileId == -1 && StringUtils.isEmpty(remoteId))
      throw new TdlibDataSourceException(PARAM_FILE_ID + " and " + PARAM_REMOTE_ID + " parameters are missing");

    transferInitializing(dataSpec);

    this.uri = uri;
    this.bytesRead = dataSpec.position;
    this.tdlib = TdlibManager.getTdlib(accountId);

    final TdApi.Function<?> function = !StringUtils.isEmpty(remoteId) ? new TdApi.GetRemoteFile(remoteId, null) : new TdApi.GetFile(fileId);

    final CountDownLatch openLatch = new CountDownLatch(1);
    final AtomicBoolean isOpening = new AtomicBoolean(true);
    final AtomicReference<TdApi.Object> response = new AtomicReference<>();

    tdlib.client().send(function, object -> {
      synchronized (isOpening) {
        if (isOpening.getAndSet(false)) {
          if (object.getConstructor() == TdApi.File.CONSTRUCTOR) {
            tdlib.listeners().addFileListener(((TdApi.File) object).id, listener);
          }
          response.set(object);
        }
      }
      openLatch.countDown();
    });
    try {
      openLatch.await();
    } catch (InterruptedException e) {
      synchronized (isOpening) {
        if (!isOpening.getAndSet(false)) {
          TdApi.Object result = response.get();
          if (result instanceof TdApi.File) {
            tdlib.listeners().removeFileListener(((TdApi.File) result).id, listener);
          }
        }
      }
      throw new TdlibDataSourceException(e);
    }
    TdApi.Object result = response.get();
    if (result == null)
      throw new TdlibDataSourceException("getFile failed");
    if (result instanceof TdApi.Error)
      throw new TdlibDataSourceException("getFile failed:" + TD.toErrorString(result));

    synchronized (fileLock) {
      this.file = (TdApi.File) result;
    }
    transferStarted(dataSpec);
    return file.size != 0 ? file.size : C.LENGTH_UNSET;
  }

  @TdlibThread
  private void processUpdate (TdApi.UpdateFile file) {
    TdApi.File currentFile;
    synchronized (fileLock) {
      currentFile = this.file;
      boolean notify = currentFile != null && currentFile.id == file.file.id && Td.copyTo(file.file, currentFile);
      if (notify && this.latch != null) {
        this.latch.countDown();
        this.latch = null;
      }
    }
  }

  private static int getAvailableSize (TdApi.File file, long offset, int length) {
    long available;
    if (file.local.isDownloadingCompleted)
      available = file.local.downloadedSize - offset;
    else if (offset >= file.local.downloadOffset && offset < file.local.downloadOffset + file.local.downloadedPrefixSize)
      available = file.local.downloadedPrefixSize - (offset - file.local.downloadOffset);
    else
      return 0;
    return (int) Math.max(0, Math.min(length, available));
  }

  private final TdApi.File localFile = new TdApi.File(0, 0, 0, new TdApi.LocalFile(), new TdApi.RemoteFile());
  private CountDownLatch latch;
  private RandomAccessFile openFile;

  private boolean acquireReference (TdApi.File file, long offset) {
    if (!referenceAcquired && file.local.canBeDownloaded) {
      referenceAcquired = true;
      tdlib.files().addCloudReference(file, offset, listener, false, true);
      return true;
    } else if (referenceAcquired) {
      if (file.local.downloadOffset != offset && !TD.withinDistance(file, offset))
        tdlib.files().seekCloudReference(file, listener, offset);
      return true;
    }
    return false;
  }

  private void releaseReference (TdApi.File file) {
    if (referenceAcquired) {
      tdlib.files().removeCloudReference(file, listener);
    }
  }

  @Override
  public int read (@NonNull byte[] buffer, int bufferOffset, int readLength) throws TdlibDataSourceException {
    if (readLength == 0) {
      return 0;
    }
    try {
      boolean first = true;
      do {
        final TdApi.File file;
        final CountDownLatch latch;
        synchronized (fileLock) {
          if (this.file != null) {
            Td.copyTo(this.file, localFile);
            this.localFile.id = this.file.id;
            file = localFile;
            if (this.latch != null) {
              latch = this.latch;
            } else {
              this.latch = latch = new CountDownLatch(1);
            }
          } else {
            file = null;
            latch = null;
          }
        }
        if (file == null)
          throw new TdlibDataSourceException("file == null");
        final long offset = bytesRead;
        if (file.size != 0 && offset >= file.size)
          return C.RESULT_END_OF_INPUT;

        if (first) {
          first = false;
          if (file.local.isDownloadingCompleted) {
            releaseReference(file);
          } else {
            acquireReference(file, offset);
          }
        }
        int available = getAvailableSize(file, offset, readLength);
        if (available == 0) {
          latch.await();
          continue;
        }
        try {
          boolean opened = false;
          synchronized (fileLock) {
            if (openFile == null) {
              openFile = new RandomAccessFile(file.local.path, "r");
              opened = true;
            }
          }
          if (opened && offset > 0) {
            openFile.seek(offset);
          }
          int readCount = openFile.read(buffer, bufferOffset, available);
          bytesTransferred(readCount);
          bytesRead += readCount;
          return readCount;
        } catch (IOException e) {
          if (acquireReference(file, offset)) {
            latch.await();
          } else {
            throw new TdlibDataSourceException(e);
          }
        }
      } while (true);
    } catch (InterruptedException e) {
      throw new TdlibDataSourceException(e);
    }
  }

  @Nullable
  @Override
  public Uri getUri () {
    return uri;
  }

  @Override
  public void close () {
    uri = null;
    TdApi.File file;
    synchronized (fileLock) {
      file = this.file;
      this.file = null;
      if (this.latch != null) {
        this.latch.countDown();
        this.latch = null;
      }
      if (this.openFile != null) {
        U.closeFile(this.openFile);
        this.openFile = null;
      }
    }
    if (tdlib != null && file != null) {
      if (referenceAcquired) {
        tdlib.files().removeCloudReference(file, listener);
        referenceAcquired = false;
      }
      tdlib.listeners().removeFileListener(file.id, listener);
      transferEnded();
    }
    tdlib = null;
  }
}
