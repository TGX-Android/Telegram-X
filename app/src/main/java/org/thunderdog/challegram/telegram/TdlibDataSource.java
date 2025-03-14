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
import me.vkryl.core.unit.ByteUnit;
import tgx.td.Td;

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
      Uri.Builder b = new Uri.Builder()
        .scheme(SCHEME)
        .authority(AUTHORITY);
      if (accountId != TdlibAccount.NO_ID) {
        b.appendQueryParameter(PARAM_ACCOUNT, Integer.toString(accountId));
      }
      b.appendQueryParameter(PARAM_FILE_ID, Integer.toString(fileId));
      return b.build();
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

  public interface RequestModifier {
    default Uri modifyUri (Uri sourceUri) {
      return sourceUri;
    }
    @Nullable
    default DataSource redirectDataSource (Uri uri) {
      return null;
    }
  }

  public static final class Factory implements DataSource.Factory {
    private final int defaultAccountId;
    private final RequestModifier requestModifier;

    public Factory () {
      this(TdlibAccount.NO_ID);
    }

    public Factory (int defaultAccountId) {
      this(defaultAccountId, null);
    }

    public Factory (int defaultAccountId, RequestModifier requestModifier) {
      this.defaultAccountId = defaultAccountId;
      this.requestModifier = requestModifier;
    }

    @Override
    @NonNull
    public DataSource createDataSource () {
      return new TdlibDataSource(defaultAccountId, requestModifier);
    }
  }

  private final int defaultAccountId;
  private final RequestModifier requestModifier;
  private Uri uri;
  private long bytesRead;
  private long initialOffset, maxReadLength;
  private Tdlib tdlib;
  private final Object fileLock = new Object();
  private TdApi.File file;
  private DataSource redirectedToDataSource;

  private final FileUpdateListener listener = this::processUpdate;

  private boolean referenceAcquired;

  public TdlibDataSource (int defaultAccountId, RequestModifier requestModifier) {
    super(true);
    this.defaultAccountId = defaultAccountId;
    this.requestModifier = requestModifier;
  }

  @Override
  public long open (@NonNull DataSpec dataSpec) throws TdlibDataSourceException, IOException {
    final Uri uri = requestModifier != null ? requestModifier.modifyUri(dataSpec.uri) : dataSpec.uri;
    this.redirectedToDataSource = requestModifier != null ? requestModifier.redirectDataSource(uri) : null;
    if (redirectedToDataSource != null) {
      return redirectedToDataSource.open(dataSpec);
    }
    if (!SCHEME.equals(uri.getScheme()))
      throw new TdlibDataSourceException("Unsupported URI scheme: " + uri.getScheme());
    if (!AUTHORITY.equals(uri.getAuthority()))
      throw new TdlibDataSourceException("Unsupported URI authority: " + uri.getAuthority());
    final int accountId = StringUtils.parseInt(uri.getQueryParameter(PARAM_ACCOUNT), defaultAccountId);
    if (accountId == TdlibAccount.NO_ID)
      throw new TdlibDataSourceException(PARAM_ACCOUNT + " parameter is missing");
    final int fileId = StringUtils.parseInt(uri.getQueryParameter(PARAM_FILE_ID), -1);
    final String remoteId = uri.getQueryParameter(PARAM_REMOTE_ID);
    if (fileId == -1 && StringUtils.isEmpty(remoteId))
      throw new TdlibDataSourceException(PARAM_FILE_ID + " and " + PARAM_REMOTE_ID + " parameters are missing");

    transferInitializing(dataSpec);

    this.uri = uri;
    this.initialOffset = bytesRead = dataSpec.position;
    this.maxReadLength = dataSpec.length;
    this.tdlib = TdlibManager.getTdlib(accountId);

    final TdApi.Function<TdApi.File> function = !StringUtils.isEmpty(remoteId) ? new TdApi.GetRemoteFile(remoteId, null) : new TdApi.GetFile(fileId);

    final CountDownLatch openLatch = new CountDownLatch(1);
    final AtomicBoolean isOpening = new AtomicBoolean(true);
    final AtomicReference<TdApi.Object> response = new AtomicReference<>();

    tdlib.send(function, (file, error) -> {
      synchronized (isOpening) {
        if (isOpening.getAndSet(false)) {
          if (file != null) {
            tdlib.listeners().addFileListener(file.id, listener);
          }
          response.set(file != null ? file : error);
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
    if (dataSpec.length != C.LENGTH_UNSET) {
      return file.size != 0 ? Math.min(dataSpec.length, file.size) : dataSpec.length;
    } else {
      return file.size != 0 ? file.size : C.LENGTH_UNSET;
    }
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

  private long getAvailableSize (TdApi.File file, long offset, int length) {
    long available;
    if (file.local.isDownloadingCompleted) {
      available = file.local.downloadedSize - offset;
    } else if (offset >= file.local.downloadOffset && offset < file.local.downloadOffset + file.local.downloadedPrefixSize) {
      available = file.local.downloadedPrefixSize - (offset - file.local.downloadOffset);
    } else {
      TdApi.FileDownloadedPrefixSize downloadedPrefixSize = tdlib.clientExecuteT(new TdApi.GetFileDownloadedPrefixSize(file.id, offset), 1000L, false);
      if (downloadedPrefixSize != null) {
        available = downloadedPrefixSize.size;
      } else {
        return 0;
      }
    }
    long availableBytes = Math.max(0L, Math.min(length, available));
    if (maxReadLength != C.LENGTH_UNSET) {
      long remainingBytes = initialOffset + maxReadLength - offset;
      if (remainingBytes <= 0) {
        return C.RESULT_END_OF_INPUT;
      }
      availableBytes = Math.min(availableBytes, remainingBytes);
    }
    return availableBytes;
  }

  private final TdApi.File localFile = new TdApi.File(0, 0, 0, new TdApi.LocalFile(), new TdApi.RemoteFile());
  private CountDownLatch latch;
  private RandomAccessFile openFile;

  private boolean acquireOrUpdateReference (TdApi.File file, long offset, long limit) {
    if (!referenceAcquired && file.local.canBeDownloaded) {
      referenceAcquired = true;
      tdlib.files().addCloudReference(file, offset, limit, listener, false);
      return true;
    } else if (referenceAcquired) {
      tdlib.files().updateCloudReference(file, listener, offset, limit);
      return true;
    }
    return false;
  }

  private void releaseReference (TdApi.File file) {
    if (referenceAcquired) {
      tdlib.files().removeCloudReference(file, listener);
    }
  }

  private static final long MIN_DOWNLOAD_CHUNK_LENGTH = ByteUnit.KIB.toBytes(64);

  @Override
  public int read (@NonNull byte[] buffer, int bufferOffset, int readLength) throws IOException {
    if (redirectedToDataSource != null) {
      return redirectedToDataSource.read(buffer, bufferOffset, readLength);
    }
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
        if (file.size != 0 && (offset >= file.size || (this.maxReadLength != C.LENGTH_UNSET && offset >= this.initialOffset + this.maxReadLength))) {
          return C.RESULT_END_OF_INPUT;
        }

        long downloadLimit;
        if (maxReadLength != C.LENGTH_UNSET) {
          downloadLimit = maxReadLength;
        } else {
          downloadLimit = offset - initialOffset + readLength;
          downloadLimit += MIN_DOWNLOAD_CHUNK_LENGTH - (downloadLimit % MIN_DOWNLOAD_CHUNK_LENGTH);
        }
        if (file.size != 0) {
          if (initialOffset + downloadLimit > file.size) {
            downloadLimit = initialOffset == 0 ? TdlibFilesManager.LIMIT_MAX : file.size - initialOffset;
          } else if (file.size - downloadLimit <= MIN_DOWNLOAD_CHUNK_LENGTH) {
            downloadLimit = TdlibFilesManager.LIMIT_MAX;
          }
        }
        if (first) {
          first = false;
          if (file.local.isDownloadingCompleted) {
            releaseReference(file);
          } else {
            acquireOrUpdateReference(file, initialOffset, downloadLimit);
          }
        } else if (!file.local.isDownloadingCompleted && !file.local.isDownloadingActive) {
          acquireOrUpdateReference(file, initialOffset, downloadLimit);
        }
        long available = getAvailableSize(file, offset, readLength);
        if (available == C.RESULT_END_OF_INPUT) {
          return C.RESULT_END_OF_INPUT;
        }
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
          int readCount = openFile.read(buffer, bufferOffset, available > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) available);
          bytesTransferred(readCount);
          bytesRead += readCount;
          return readCount;
        } catch (IOException e) {
          if (acquireOrUpdateReference(file, initialOffset, downloadLimit)) {
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
    if (redirectedToDataSource != null) {
      return redirectedToDataSource.getUri();
    }
    return uri;
  }

  @Override
  public void close () throws IOException {
    uri = null;
    if (redirectedToDataSource != null) {
      redirectedToDataSource.close();
      redirectedToDataSource = null;
      return;
    }
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
