/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static java.lang.Math.min;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

/** A {@link DataSource} for reading from a byte array. */
@UnstableApi
public final class ByteArrayDataSource extends BaseDataSource {

  /** Functional interface to resolve from {@link Uri} to {@code byte[]}. */
  public interface UriResolver {
    /**
     * Resolves a {@link Uri} to a {@code byte[]}.
     *
     * <p>Called during {@link DataSource#open(DataSpec)} from a loading thread, so can do blocking
     * work.
     *
     * @return The resolved byte array.
     * @throws IOException if the provided URI is not recognized, or an error occurs during
     *     resolution.
     */
    byte[] resolve(Uri uri) throws IOException;
  }

  private final UriResolver uriResolver;

  @Nullable private Uri uri;
  @Nullable private byte[] data;
  private int readPosition;
  private int bytesRemaining;
  private boolean opened;

  /**
   * Creates an instance.
   *
   * @param data The data to be read.
   */
  public ByteArrayDataSource(byte[] data) {
    this(/* uriResolver= */ unusedUri -> data);
    checkArgument(data.length > 0);
  }

  /**
   * Creates an instance.
   *
   * @param uriResolver Function to resolve from {@link Uri} to {@code byte[]} during {@link
   *     #open(DataSpec)}.
   */
  public ByteArrayDataSource(UriResolver uriResolver) {
    super(/* isNetwork= */ false);
    this.uriResolver = checkNotNull(uriResolver);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    transferInitializing(dataSpec);
    uri = dataSpec.uri;
    data = uriResolver.resolve(uri);
    if (dataSpec.position > data.length) {
      throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    }
    readPosition = (int) dataSpec.position;
    bytesRemaining = data.length - (int) dataSpec.position;
    if (dataSpec.length != C.LENGTH_UNSET) {
      bytesRemaining = (int) min(bytesRemaining, dataSpec.length);
    }
    opened = true;
    transferStarted(dataSpec);
    return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    length = min(length, bytesRemaining);
    System.arraycopy(checkStateNotNull(data), readPosition, buffer, offset, length);
    readPosition += length;
    bytesRemaining -= length;
    bytesTransferred(length);
    return length;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() {
    if (opened) {
      opened = false;
      transferEnded();
    }
    uri = null;
    data = null;
  }
}
