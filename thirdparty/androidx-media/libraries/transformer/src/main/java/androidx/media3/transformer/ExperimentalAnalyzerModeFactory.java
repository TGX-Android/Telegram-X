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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.video.PlaceholderSurface;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;

/**
 * Factory for creating instances of {@link Transformer} that can be used to analyze media.
 *
 * <p>When using {@link Transformer} to analyze decoded data, users should provide their analysis
 * effects through the {@link EditedMediaItem#effects}.
 *
 * <p>This class is experimental and will be renamed or removed in a future release.
 */
@UnstableApi
public final class ExperimentalAnalyzerModeFactory {

  private ExperimentalAnalyzerModeFactory() {}

  /**
   * Builds a {@link Transformer} that runs as an analyzer.
   *
   * <p>No encoding or muxing is performed, therefore no data is written to any output files.
   *
   * @param context The {@link Context}.
   * @return The analyzer {@link Transformer}.
   */
  public static Transformer buildAnalyzer(Context context) {
    return buildAnalyzer(context, new Transformer.Builder(context).build());
  }

  /**
   * Builds a {@link Transformer} that runs as an analyzer.
   *
   * <p>No encoding or muxing is performed, therefore no data is written to any output files.
   *
   * @param context The {@link Context}.
   * @param transformer The {@link Transformer} to be built upon.
   * @return The analyzer {@link Transformer}.
   */
  public static Transformer buildAnalyzer(Context context, Transformer transformer) {
    return transformer
        .buildUpon()
        .experimentalSetTrimOptimizationEnabled(false)
        .experimentalSetMaxFramesInEncoder(C.INDEX_UNSET)
        .setEncoderFactory(new DroppingEncoder.Factory(context))
        .setMaxDelayBetweenMuxerSamplesMs(C.TIME_UNSET)
        .setMuxerFactory(
            new NoWriteMuxer.Factory(
                /* audioMimeTypes= */ ImmutableList.of(MimeTypes.AUDIO_AAC),
                /* videoMimeTypes= */ ImmutableList.of(MimeTypes.VIDEO_H264)))
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .build();
  }

  /** A {@linkplain Codec encoder} implementation that drops input and produces no output. */
  private static final class DroppingEncoder implements Codec {
    public static final class Factory implements Codec.EncoderFactory {
      private final Context context;

      public Factory(Context context) {
        this.context = context;
      }

      @Override
      public Codec createForAudioEncoding(Format format) {
        return new DroppingEncoder(context, format);
      }

      @Override
      public Codec createForVideoEncoding(Format format) {
        return new DroppingEncoder(context, format);
      }
    }

    private static final String TAG = "DroppingEncoder";
    private static final int INTERNAL_BUFFER_SIZE = 8196;

    private final Format configurationFormat;
    private final ByteBuffer buffer;
    private final Surface placeholderSurface;

    private boolean inputStreamEnded;

    public DroppingEncoder(Context context, Format format) {
      this.configurationFormat = format;
      buffer = ByteBuffer.allocateDirect(INTERNAL_BUFFER_SIZE).order(ByteOrder.nativeOrder());
      placeholderSurface = PlaceholderSurface.newInstance(context, /* secure= */ false);
    }

    @Override
    public String getName() {
      return TAG;
    }

    @Override
    public Format getConfigurationFormat() {
      return configurationFormat;
    }

    @Override
    public Surface getInputSurface() {
      return placeholderSurface;
    }

    @Override
    @EnsuresNonNullIf(expression = "#1.data", result = true)
    public boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer) {
      if (inputStreamEnded) {
        return false;
      }
      inputBuffer.data = buffer;
      return true;
    }

    @Override
    public void queueInputBuffer(DecoderInputBuffer inputBuffer) {
      checkState(
          !inputStreamEnded, "Input buffer can not be queued after the input stream has ended.");
      if (inputBuffer.isEndOfStream()) {
        inputStreamEnded = true;
      }
      inputBuffer.clear();
      inputBuffer.data = null;
    }

    @Override
    public void signalEndOfInputStream() {
      inputStreamEnded = true;
    }

    @Override
    public Format getInputFormat() {
      return configurationFormat;
    }

    @Override
    @Nullable
    public Format getOutputFormat() {
      return configurationFormat;
    }

    @Override
    @Nullable
    public ByteBuffer getOutputBuffer() {
      return null;
    }

    @Override
    @Nullable
    public BufferInfo getOutputBufferInfo() {
      return null;
    }

    @Override
    public boolean isEnded() {
      return inputStreamEnded;
    }

    @Override
    public void releaseOutputBuffer(boolean render) {}

    @Override
    public void releaseOutputBuffer(long renderPresentationTimeUs) {}

    @Override
    public void release() {
      placeholderSurface.release();
    }
  }
}
