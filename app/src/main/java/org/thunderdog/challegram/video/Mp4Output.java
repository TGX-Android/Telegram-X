package org.thunderdog.challegram.video;

import android.media.MediaCodec;
import android.media.MediaFormat;

import org.thunderdog.challegram.video.old.Mp4Movie;
import org.thunderdog.challegram.video.old.Mp4OutputImpl;

import java.io.File;
import java.nio.ByteBuffer;

public abstract class Mp4Output {
  public abstract int addTrack (MediaFormat format, boolean isAudio) throws Exception;
  public abstract boolean writeSampleData (int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, boolean writeLength) throws Exception;
  public abstract void finishMovie () throws Exception;

  public static Mp4Output valueOf (File targetFile, int width, int height) throws Exception {
    Mp4Movie movie = new Mp4Movie();
    movie.setCacheFile(targetFile);
    movie.setSize(width, height);
    return new Mp4OutputImpl().createMovie(movie);
  }
}
