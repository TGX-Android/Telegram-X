/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.datasource;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static java.lang.Math.min;

import android.net.Uri;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.Sets;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Set;

/**
 * A {@link DataSource} for reading from a {@link FileDescriptor}.
 *
 * <p>Due to limitations of file descriptors, it's only possible to have one {@link DataSource}
 * created for a given file descriptor open at a time. The provided file descriptor must be
 * seekable.
 *
 * <p>Unlike typical {@link DataSource} instances, each instance of this class can only read from a
 * single {@link FileDescriptor}. Additionally, the {@link DataSpec#uri} passed to {@link
 * #open(DataSpec)} is not actually used for reading data. Instead, the underlying {@link
 * FileDescriptor} is used for all read operations.
 */
@UnstableApi
public class FileDescriptorDataSource extends BaseDataSource {

  // Track file descriptors currently in use to fail fast if an attempt is made to re-use one.
  private static final Set<FileDescriptor> inUseFileDescriptors = Sets.newConcurrentHashSet();

  private final FileDescriptor fileDescriptor;
  private final long offset;
  private final long length;

  @Nullable private Uri uri;
  @Nullable private FileInputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /**
   * Creates a new instance.
   *
   * @param fileDescriptor The file descriptor from which to read.
   * @param offset The start offset of data to read.
   * @param length The length of data to read, or {@link C#LENGTH_UNSET} if not known.
   */
  public FileDescriptorDataSource(FileDescriptor fileDescriptor, long offset, long length) {
    super(/* isNetwork= */ false);
    this.fileDescriptor = checkNotNull(fileDescriptor);
    this.offset = offset;
    this.length = length;
  }

  @Override
  public long open(DataSpec dataSpec) throws DataSourceException {
    try {
      uri = dataSpec.uri;
      transferInitializing(dataSpec);

      if (!inUseFileDescriptors.add(fileDescriptor)) {
        throw new DataSourceException(
            new IllegalStateException("Attempted to re-use an already in-use file descriptor"),
            PlaybackException.ERROR_CODE_INVALID_STATE);
      }

      if (length != C.LENGTH_UNSET && dataSpec.position > length) {
        throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
      }

      seekFileDescriptor(fileDescriptor, offset + dataSpec.position);
      inputStream = new FileInputStream(fileDescriptor);

      if (length == C.LENGTH_UNSET) {
        // The asset must extend to the end of the file. We can try and resolve the length with
        // FileInputStream.getChannel().size().
        FileChannel channel = inputStream.getChannel();
        long channelSize = channel.size();
        if (channelSize == 0) {
          bytesRemaining = C.LENGTH_UNSET;
        } else {
          bytesRemaining = channelSize - channel.position();
          if (bytesRemaining < 0) {
            // The seek above was successful, but the new position is beyond the end of the file.
            throw new DataSourceException(
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
          }
        }
      } else {
        bytesRemaining = length - dataSpec.position;
        if (bytesRemaining < 0) {
          throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }
      }
    } catch (DataSourceException e) {
      throw e;
    } catch (IOException e) {
      throw new DataSourceException(
          e,
          e instanceof FileNotFoundException
              ? PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
              : PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    if (dataSpec.length != C.LENGTH_UNSET) {
      bytesRemaining =
          bytesRemaining == C.LENGTH_UNSET ? dataSpec.length : min(bytesRemaining, dataSpec.length);
    }

    opened = true;
    transferStarted(dataSpec);
    return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws DataSourceException {
    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    int bytesToRead = bytesRemaining == C.LENGTH_UNSET ? length : (int) min(bytesRemaining, length);
    int bytesRead;
    try {
      bytesRead = castNonNull(inputStream).read(buffer, offset, bytesToRead);
    } catch (IOException e) {
      throw new DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }
    if (bytesRead == -1) {
      return C.RESULT_END_OF_INPUT;
    }
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @Nullable
  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws DataSourceException {
    uri = null;
    inUseFileDescriptors.remove(fileDescriptor);
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    } finally {
      inputStream = null;
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }

  private static void seekFileDescriptor(FileDescriptor fileDescriptor, long position)
      throws DataSourceException {
    try {
      Os.lseek(fileDescriptor, position, /* whence= */ OsConstants.SEEK_SET);
    } catch (ErrnoException e) {
      throw new DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }
  }
}
