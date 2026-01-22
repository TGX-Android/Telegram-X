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
package androidx.media3.muxer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.media.MediaCodec.BufferInfo;
import android.util.SparseArray;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4OrientationData;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.container.XmpData;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * A muxer for creating a fragmented MP4 file.
 *
 * <p>Muxer supports muxing of:
 *
 * <ul>
 *   <li>Video Codecs:
 *       <ul>
 *         <li>AV1
 *         <li>MPEG-4
 *         <li>H.263
 *         <li>H.264 (AVC)
 *         <li>H.265 (HEVC)
 *         <li>VP9
 *         <li>APV
 *       </ul>
 *   <li>Audio Codecs:
 *       <ul>
 *         <li>AAC
 *         <li>AMR-NB (Narrowband AMR)
 *         <li>AMR-WB (Wideband AMR)
 *         <li>Opus
 *         <li>Vorbis
 *         <li>Raw Audio
 *       </ul>
 *   <li>Metadata
 * </ul>
 *
 * <p>All the operations are performed on the caller thread.
 *
 * <p>To create a fragmented MP4 file, the caller must:
 *
 * <ul>
 *   <li>Add tracks using {@link #addTrack(Format)} which will return a track id.
 *   <li>Use the associated track id when {@linkplain #writeSampleData(int, ByteBuffer, BufferInfo)
 *       writing samples} for that track.
 *   <li>{@link #close} the muxer when all data has been written.
 * </ul>
 *
 * <p>Some key points:
 *
 * <ul>
 *   <li>All tracks must be added before writing any samples.
 *   <li>The caller is responsible for ensuring that samples of different track types are well
 *       interleaved by calling {@link #writeSampleData(int, ByteBuffer, BufferInfo)} in an order
 *       that interleaves samples from different tracks.
 * </ul>
 */
@UnstableApi
public final class FragmentedMp4Muxer implements AutoCloseable {
  /** The default fragment duration. */
  public static final long DEFAULT_FRAGMENT_DURATION_MS = 2_000;

  /** A builder for {@link FragmentedMp4Muxer} instances. */
  public static final class Builder {
    private final OutputStream outputStream;

    private long fragmentDurationMs;
    private boolean sampleCopyEnabled;

    /**
     * Creates a {@link Builder} instance with default values.
     *
     * @param outputStream The {@link OutputStream} to write the media data to. This stream will be
     *     automatically closed by the muxer when {@link FragmentedMp4Muxer#close()} is called.
     */
    public Builder(OutputStream outputStream) {
      this.outputStream = outputStream;
      fragmentDurationMs = DEFAULT_FRAGMENT_DURATION_MS;
      sampleCopyEnabled = true;
    }

    /**
     * Sets the fragment duration (in milliseconds).
     *
     * <p>The muxer will attempt to create fragments of the given duration but the actual duration
     * might be greater depending upon the frequency of sync samples.
     *
     * <p>The default value is {@link #DEFAULT_FRAGMENT_DURATION_MS}.
     */
    @CanIgnoreReturnValue
    public Builder setFragmentDurationMs(long fragmentDurationMs) {
      this.fragmentDurationMs = fragmentDurationMs;
      return this;
    }

    /**
     * Sets whether to enable the sample copy.
     *
     * <p>If the sample copy is enabled, {@link #writeSampleData(int, ByteBuffer, BufferInfo)}
     * copies the input {@link ByteBuffer} and {@link BufferInfo} before it returns, so it is safe
     * to reuse them immediately. Otherwise, the muxer takes ownership of the {@link ByteBuffer} and
     * the {@link BufferInfo} and the caller must not modify them.
     *
     * <p>The default value is {@code true}.
     */
    @CanIgnoreReturnValue
    public Builder setSampleCopyingEnabled(boolean enabled) {
      this.sampleCopyEnabled = enabled;
      return this;
    }

    /** Builds a {@link FragmentedMp4Muxer} instance. */
    public FragmentedMp4Muxer build() {
      return new FragmentedMp4Muxer(outputStream, fragmentDurationMs, sampleCopyEnabled);
    }
  }

  // LINT.IfChange(supported_mime_types)
  /** A list of supported video {@linkplain MimeTypes sample MIME types}. */
  public static final ImmutableList<String> SUPPORTED_VIDEO_SAMPLE_MIME_TYPES =
      ImmutableList.of(
          MimeTypes.VIDEO_AV1,
          MimeTypes.VIDEO_H263,
          MimeTypes.VIDEO_H264,
          MimeTypes.VIDEO_H265,
          MimeTypes.VIDEO_MP4V,
          MimeTypes.VIDEO_VP9,
          MimeTypes.VIDEO_APV);

  /** A list of supported audio {@linkplain MimeTypes sample MIME types}. */
  public static final ImmutableList<String> SUPPORTED_AUDIO_SAMPLE_MIME_TYPES =
      ImmutableList.of(
          MimeTypes.AUDIO_AAC,
          MimeTypes.AUDIO_AMR_NB,
          MimeTypes.AUDIO_AMR_WB,
          MimeTypes.AUDIO_OPUS,
          MimeTypes.AUDIO_VORBIS,
          MimeTypes.AUDIO_RAW);

  // LINT.ThenChange(Boxes.java:codec_specific_boxes)

  private final FragmentedMp4Writer fragmentedMp4Writer;
  private final MetadataCollector metadataCollector;
  private final SparseArray<Track> trackIdToTrack;

  private FragmentedMp4Muxer(
      OutputStream outputStream, long fragmentDurationMs, boolean sampleCopyEnabled) {
    checkNotNull(outputStream);
    metadataCollector = new MetadataCollector();
    fragmentedMp4Writer =
        new FragmentedMp4Writer(
            outputStream,
            metadataCollector,
            AnnexBToAvccConverter.DEFAULT,
            fragmentDurationMs,
            sampleCopyEnabled);
    trackIdToTrack = new SparseArray<>();
  }

  /**
   * Adds a track of the given media format.
   *
   * <p>All tracks must be added before {@linkplain #writeSampleData writing any samples}.
   *
   * @param format The {@link Format} of the track.
   * @return A track id for this track, which should be passed to {@link #writeSampleData}.
   */
  public int addTrack(Format format) {
    Track track = fragmentedMp4Writer.addTrack(/* sortKey= */ 1, format);
    trackIdToTrack.append(track.id, track);
    return track.id;
  }

  /**
   * Writes encoded sample data.
   *
   * <p>Samples are written to the disk in batches. If {@link
   * Builder#setSampleCopyingEnabled(boolean) sample copying} is disabled, the {@code byteBuffer}
   * and the {@code bufferInfo} must not be modified after calling this method. Otherwise, they are
   * copied and it is safe to modify them after this method returns.
   *
   * <p>Note: Out of order B-frames are currently not supported.
   *
   * @param trackId The track id for which this sample is being written.
   * @param byteBuffer The encoded sample. The muxer takes ownership of the buffer if {@link
   *     Builder#setSampleCopyingEnabled(boolean) sample copying} is disabled. Otherwise, the
   *     position of the buffer is updated but the caller retains ownership.
   * @param bufferInfo The {@link BufferInfo} related to this sample.
   * @throws MuxerException If there is any error while writing data to the disk.
   */
  public void writeSampleData(int trackId, ByteBuffer byteBuffer, BufferInfo bufferInfo)
      throws MuxerException {
    try {
      fragmentedMp4Writer.writeSampleData(trackIdToTrack.get(trackId), byteBuffer, bufferInfo);
    } catch (IOException e) {
      throw new MuxerException(
          "Failed to write sample for presentationTimeUs="
              + bufferInfo.presentationTimeUs
              + ", size="
              + bufferInfo.size,
          e);
    }
  }

  /**
   * Adds {@linkplain Metadata.Entry metadata} about the output file.
   *
   * <p>List of supported {@linkplain Metadata.Entry metadata entries}:
   *
   * <ul>
   *   <li>{@link Mp4OrientationData}
   *   <li>{@link Mp4LocationData}
   *   <li>{@link Mp4TimestampData}
   *   <li>{@link MdtaMetadataEntry}: Only {@linkplain MdtaMetadataEntry#TYPE_INDICATOR_STRING
   *       string type} or {@linkplain MdtaMetadataEntry#TYPE_INDICATOR_FLOAT32 float type} value is
   *       supported.
   *   <li>{@link XmpData}
   * </ul>
   *
   * @param metadataEntry The {@linkplain Metadata.Entry metadata}. An {@link
   *     IllegalArgumentException} is thrown if the {@linkplain Metadata.Entry metadata} is not
   *     supported.
   */
  public void addMetadataEntry(Metadata.Entry metadataEntry) {
    checkArgument(MuxerUtil.isMetadataSupported(metadataEntry), "Unsupported metadata");
    metadataCollector.addMetadata(metadataEntry);
  }

  /**
   * Closes the file.
   *
   * <p>The muxer cannot be used anymore once this method returns.
   *
   * @throws MuxerException If the muxer fails to finish writing the output.
   */
  @Override
  public void close() throws MuxerException {
    try {
      fragmentedMp4Writer.close();
    } catch (IOException e) {
      throw new MuxerException("Failed to close the muxer", e);
    }
  }
}
