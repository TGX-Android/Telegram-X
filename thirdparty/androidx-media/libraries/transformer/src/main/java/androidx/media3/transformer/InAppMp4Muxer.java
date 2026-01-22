/*
 * Copyright 2023 The Android Open Source Project
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

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4OrientationData;
import androidx.media3.muxer.Mp4Muxer;
import androidx.media3.muxer.MuxerException;
import androidx.media3.muxer.MuxerUtil;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** {@link Muxer} implementation that uses an {@link Mp4Muxer}. */
@UnstableApi
public final class InAppMp4Muxer implements Muxer {
  /** Provides {@linkplain Metadata.Entry metadata} to add in the output MP4 file. */
  public interface MetadataProvider {

    /**
     * Updates the list of {@linkplain Metadata.Entry metadata entries}.
     *
     * <p>A {@link Metadata.Entry} can be added or removed. To modify an existing {@link
     * Metadata.Entry}, first remove it and then add a new one.
     *
     * <p>For the list of supported metadata refer to {@link
     * Mp4Muxer#addMetadataEntry(Metadata.Entry)}.
     */
    void updateMetadataEntries(Set<Metadata.Entry> metadataEntries);
  }

  /** {@link Muxer.Factory} for {@link InAppMp4Muxer}. */
  public static final class Factory implements Muxer.Factory {
    @Nullable private final MetadataProvider metadataProvider;

    private long videoDurationUs;

    /** Creates an instance with default values. */
    public Factory() {
      this(/* metadataProvider= */ null);
    }

    /**
     * Creates an instance.
     *
     * @param metadataProvider A {@link MetadataProvider}.
     */
    public Factory(@Nullable MetadataProvider metadataProvider) {
      this.metadataProvider = metadataProvider;
      videoDurationUs = C.TIME_UNSET;
    }

    /**
     * Sets the duration of the video track (in microseconds) in the output.
     *
     * <p>Only the duration of the last sample is adjusted to achieve the given duration. Duration
     * of the other samples remains unchanged.
     *
     * <p>The default is {@link C#TIME_UNSET} to not set any duration in the output. In this case
     * the video track duration is determined by the samples written to it and the duration of the
     * last sample will be same as that of the sample before that.
     *
     * @param videoDurationUs The duration of the video track (in microseconds) in the output, or
     *     {@link C#TIME_UNSET} to not set any duration. Only applicable when a video track is
     *     {@linkplain #addTrack(Format) added}.
     * @return This factory.
     */
    @CanIgnoreReturnValue
    public Factory setVideoDurationUs(long videoDurationUs) {
      this.videoDurationUs = videoDurationUs;
      return this;
    }

    @Override
    public InAppMp4Muxer create(String path) throws MuxerException {
      FileOutputStream outputStream;
      try {
        outputStream = new FileOutputStream(path);
      } catch (FileNotFoundException e) {
        throw new MuxerException("Error creating file output stream", e);
      }

      Mp4Muxer.Builder builder = new Mp4Muxer.Builder(outputStream);
      Mp4Muxer muxer = builder.build();

      return new InAppMp4Muxer(muxer, metadataProvider, videoDurationUs);
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      if (trackType == C.TRACK_TYPE_VIDEO) {
        return Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES;
      } else if (trackType == C.TRACK_TYPE_AUDIO) {
        return Mp4Muxer.SUPPORTED_AUDIO_SAMPLE_MIME_TYPES;
      }
      return ImmutableList.of();
    }
  }

  public static final String MUXER_NAME =
      "androidx.media3:media3-muxer:" + MediaLibraryInfo.VERSION;

  private static final String TAG = "InAppMp4Muxer";
  private static final int TRACK_ID_UNSET = -1;

  private final Mp4Muxer muxer;
  @Nullable private final MetadataProvider metadataProvider;
  private final long videoDurationUs;
  private final Set<Metadata.Entry> metadataEntries;

  private int videoTrackId;

  private InAppMp4Muxer(
      Mp4Muxer muxer, @Nullable MetadataProvider metadataProvider, long videoDurationUs) {
    this.muxer = muxer;
    this.metadataProvider = metadataProvider;
    this.videoDurationUs = videoDurationUs;
    metadataEntries = new LinkedHashSet<>();
    videoTrackId = TRACK_ID_UNSET;
  }

  @Override
  public int addTrack(Format format) throws MuxerException {
    int trackId = muxer.addTrack(format);
    if (MimeTypes.isVideo(format.sampleMimeType)) {
      muxer.addMetadataEntry(new Mp4OrientationData(format.rotationDegrees));
      videoTrackId = trackId;
    }
    return trackId;
  }

  @Override
  public void writeSampleData(int trackId, ByteBuffer byteBuffer, BufferInfo bufferInfo)
      throws MuxerException {
    if (videoDurationUs != C.TIME_UNSET
        && trackId == videoTrackId
        && bufferInfo.presentationTimeUs > videoDurationUs) {
      Log.w(
          TAG,
          String.format(
              Locale.US,
              "Skipped sample with presentation time (%d) > video duration (%d)",
              bufferInfo.presentationTimeUs,
              videoDurationUs));
      return;
    }
    muxer.writeSampleData(trackId, byteBuffer, bufferInfo);
  }

  @Override
  public void addMetadataEntry(Metadata.Entry metadataEntry) {
    if (MuxerUtil.isMetadataSupported(metadataEntry)) {
      metadataEntries.add(metadataEntry);
    }
  }

  @Override
  public void close() throws MuxerException {
    if (videoDurationUs != C.TIME_UNSET && videoTrackId != TRACK_ID_UNSET) {
      BufferInfo bufferInfo = new BufferInfo();
      bufferInfo.set(
          /* newOffset= */ 0,
          /* newSize= */ 0,
          videoDurationUs,
          MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      writeSampleData(videoTrackId, ByteBuffer.allocateDirect(0), bufferInfo);
    }
    writeMetadata();
    muxer.close();
  }

  private void writeMetadata() {
    if (metadataProvider != null) {
      Set<Metadata.Entry> metadataEntriesCopy = new LinkedHashSet<>(metadataEntries);
      metadataProvider.updateMetadataEntries(metadataEntriesCopy);
      metadataEntries.clear();
      metadataEntries.addAll(metadataEntriesCopy);
    }

    for (Metadata.Entry entry : metadataEntries) {
      muxer.addMetadataEntry(entry);
    }
  }
}
