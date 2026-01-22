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

import static java.lang.Math.min;

import android.media.MediaDataSource;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

/**
 * A {@link DataSource} for reading from a {@link MediaDataSource}.
 *
 * <p>An adapter that allows to read media data supplied by an implementation of {@link
 * MediaDataSource}.
 */
@RequiresApi(23)
@UnstableApi
public class MediaDataSourceAdapter extends BaseDataSource {

  private final MediaDataSource mediaDataSource;

  @Nullable private Uri uri;
  private long position;
  private long bytesRemaining;
  private boolean opened;

  /**
   * Creates an instance.
   *
   * @param mediaDataSource The {@link MediaDataSource} from which to read.
   * @param isNetwork Whether the data source loads data through a network.
   */
  public MediaDataSourceAdapter(MediaDataSource mediaDataSource, boolean isNetwork) {
    super(isNetwork);
    this.mediaDataSource = mediaDataSource;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    uri = dataSpec.uri;
    position = dataSpec.position;
    transferInitializing(dataSpec);

    if (mediaDataSource.getSize() != C.LENGTH_UNSET && position > mediaDataSource.getSize()) {
      throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
    }

    if (mediaDataSource.getSize() == C.LENGTH_UNSET) {
      bytesRemaining = C.LENGTH_UNSET;
    } else {
      bytesRemaining = mediaDataSource.getSize() - position;
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
      bytesRead = mediaDataSource.readAt(position, buffer, offset, bytesToRead);
    } catch (IOException e) {
      throw new DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    }

    if (bytesRead == -1) {
      return C.RESULT_END_OF_INPUT;
    }

    position += bytesRead;
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
  public void close() throws IOException {
    uri = null;
    if (opened) {
      opened = false;
      transferEnded();
    }
  }
}
