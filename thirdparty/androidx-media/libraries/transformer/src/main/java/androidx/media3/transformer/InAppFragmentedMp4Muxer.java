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
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4OrientationData;
import androidx.media3.muxer.FragmentedMp4Muxer;
import androidx.media3.muxer.MuxerException;
import androidx.media3.muxer.MuxerUtil;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

/** {@link Muxer} implementation that uses a {@link FragmentedMp4Muxer}. */
// TODO: b/372417042 - Add E2E tests for producing fragmented MP4 output.
@UnstableApi
public final class InAppFragmentedMp4Muxer implements Muxer {
  /** {@link Muxer.Factory} for {@link InAppFragmentedMp4Muxer}. */
  public static final class Factory implements Muxer.Factory {
    private final long fragmentDurationMs;

    private long videoDurationUs;

    /** Creates an instance with default values. */
    public Factory() {
      this(C.TIME_UNSET);
    }

    /**
     * Creates an instance.
     *
     * @param fragmentDurationMs The fragment duration (in milliseconds).
     */
    public Factory(long fragmentDurationMs) {
      this.fragmentDurationMs = fragmentDurationMs;
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
    public InAppFragmentedMp4Muxer create(String path) throws MuxerException {
      FileOutputStream outputStream;
      try {
        outputStream = new FileOutputStream(path);
      } catch (FileNotFoundException e) {
        throw new MuxerException("Error creating file output stream", e);
      }

      FragmentedMp4Muxer.Builder builder = new FragmentedMp4Muxer.Builder(outputStream);
      if (fragmentDurationMs != C.TIME_UNSET) {
        builder.setFragmentDurationMs(fragmentDurationMs);
      }
      FragmentedMp4Muxer muxer = builder.build();

      return new InAppFragmentedMp4Muxer(muxer, videoDurationUs);
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      if (trackType == C.TRACK_TYPE_VIDEO) {
        return FragmentedMp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES;
      } else if (trackType == C.TRACK_TYPE_AUDIO) {
        return FragmentedMp4Muxer.SUPPORTED_AUDIO_SAMPLE_MIME_TYPES;
      }
      return ImmutableList.of();
    }
  }

  public static final String MUXER_NAME =
      "androidx.media3:media3-muxer:" + MediaLibraryInfo.VERSION;

  private static final String TAG = "InAppFragmentedMp4Muxer";
  private static final int TRACK_ID_UNSET = -1;

  private final FragmentedMp4Muxer muxer;
  private final long videoDurationUs;

  private int videoTrackId;

  private InAppFragmentedMp4Muxer(FragmentedMp4Muxer muxer, long videoDurationUs) {
    this.muxer = muxer;
    this.videoDurationUs = videoDurationUs;
    videoTrackId = TRACK_ID_UNSET;
  }

  @Override
  public int addTrack(Format format) {
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
      muxer.addMetadataEntry(metadataEntry);
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
    muxer.close();
  }
}
