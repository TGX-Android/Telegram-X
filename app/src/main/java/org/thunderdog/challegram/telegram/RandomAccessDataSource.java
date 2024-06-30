package org.thunderdog.challegram.telegram;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.IOException;
import java.io.RandomAccessFile;


public class RandomAccessDataSource extends BaseDataSource {

  public static final class Factory implements DataSource.Factory {
    private final RandomAccessFile file;

    public Factory(RandomAccessFile file) {
      this.file = file;
    }

    @Override
    @NonNull
    public DataSource createDataSource() {
      return new RandomAccessDataSource(file);
    }
  }

  private final RandomAccessFile file;
  private long bytesRemaining;
  private boolean opened;

  protected RandomAccessDataSource(RandomAccessFile file) {
    super(false);
    this.file = file;
  }

  @Override
  public long open(@NonNull DataSpec dataSpec) throws IOException {
    transferInitializing(dataSpec);

    file.seek(dataSpec.position);
    bytesRemaining = file.length() - dataSpec.position;

    if (bytesRemaining < 0) {
      throw new IOException("Unsufficient length for reading.");
    }

    opened = true;
    transferStarted(dataSpec);
    return bytesRemaining;
  }

  @Nullable
  @Override
  public Uri getUri() {
    return Uri.EMPTY;
  }

  @Override
  public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    int bytesToRead = (int) Math.min(bytesRemaining, length);
    int bytesRead = file.read(buffer, offset, bytesToRead);

    if (bytesRead == -1) {
      return C.RESULT_END_OF_INPUT;
    }

    bytesRemaining -= bytesRead;
    bytesTransferred(bytesRead);

    return bytesRead;
  }

  @Override
  public void close() throws IOException {
    if (opened) {
      opened = false;
      transferEnded();
    }
  }
}
