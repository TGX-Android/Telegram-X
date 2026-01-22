/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.TransformerUtil.getProcessedTrackType;

import android.media.MediaCodec.BufferInfo;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.Util;
import androidx.media3.muxer.MuxerException;
import androidx.media3.test.utils.DumpableFormat;
import androidx.media3.test.utils.Dumper;
import androidx.media3.test.utils.Dumper.Dumpable;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * A {@link Dumpable} {@link Muxer} implementation that supports dumping information about all
 * interactions (for testing purposes) and forwards method calls to the underlying {@link Muxer}.
 */
public final class CapturingMuxer implements Muxer, Dumpable {

  /**
   * A {@link Muxer.Factory} for {@link CapturingMuxer} that captures and provides access to the
   * {@linkplain #create created} muxer.
   */
  public static final class Factory implements Muxer.Factory {
    private final Muxer.Factory wrappedFactory;
    private final boolean handleAudioAsPcm;
    @Nullable private CapturingMuxer muxer;

    /**
     * Creates an instance.
     *
     * @param handleAudioAsPcm Whether audio should be treated as PCM for {@linkplain Dumpable
     *     dumping}, where PCM audio is captured in batches of a fixed size.
     */
    public Factory(boolean handleAudioAsPcm) {
      this.handleAudioAsPcm = handleAudioAsPcm;
      this.wrappedFactory = new DefaultMuxer.Factory();
    }

    /** Returns the most recently {@linkplain #create created} {@code TestMuxer}. */
    public CapturingMuxer getCreatedMuxer() {
      return checkNotNull(muxer);
    }

    @Override
    public Muxer create(String path) throws MuxerException {
      muxer = new CapturingMuxer(wrappedFactory.create(path), handleAudioAsPcm);
      return muxer;
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      return wrappedFactory.getSupportedSampleMimeTypes(trackType);
    }
  }

  private final Muxer wrappedMuxer;
  private final boolean handleAudioAsPcm;
  private final SparseArray<DumpableFormat> dumpableFormatByTrackType;
  private final SparseArray<DumpableStream> dumpableStreamByTrackType;
  private final SparseArray<Integer> trackIdToType;
  private final ArrayList<Metadata.Entry> metadataList;
  private boolean released;

  /** Creates a new test muxer. */
  private CapturingMuxer(Muxer wrappedMuxer, boolean handleAudioAsPcm) {
    this.wrappedMuxer = wrappedMuxer;
    this.handleAudioAsPcm = handleAudioAsPcm;
    dumpableFormatByTrackType = new SparseArray<>();
    dumpableStreamByTrackType = new SparseArray<>();
    trackIdToType = new SparseArray<>();
    metadataList = new ArrayList<>();
  }

  // Muxer implementation.

  @Override
  public int addTrack(Format format) throws MuxerException {
    int trackId = wrappedMuxer.addTrack(format);
    @C.TrackType int trackType = getProcessedTrackType(format.sampleMimeType);

    trackIdToType.put(trackId, trackType);

    dumpableFormatByTrackType.append(
        trackType, new DumpableFormat(format, /* tag= */ Util.getTrackTypeString(trackType)));

    dumpableStreamByTrackType.append(
        trackType,
        trackType == C.TRACK_TYPE_AUDIO && handleAudioAsPcm
            ? new DumpablePcmAudioStream(trackType)
            : new DumpableStream(trackType));

    return trackId;
  }

  @Override
  public void writeSampleData(int trackId, ByteBuffer data, BufferInfo bufferInfo)
      throws MuxerException {
    @C.TrackType int trackType = checkNotNull(trackIdToType.get(trackId));
    dumpableStreamByTrackType
        .get(trackType)
        .addSample(
            data,
            (bufferInfo.flags & C.BUFFER_FLAG_KEY_FRAME) == C.BUFFER_FLAG_KEY_FRAME,
            bufferInfo.presentationTimeUs);
    wrappedMuxer.writeSampleData(trackId, data, bufferInfo);
  }

  @Override
  public void addMetadataEntry(Metadata.Entry metadataEntry) {
    metadataList.add(metadataEntry);
    wrappedMuxer.addMetadataEntry(metadataEntry);
  }

  @Override
  public void close() throws MuxerException {
    released = true;
    wrappedMuxer.close();
  }

  public long getTotalBytesForTrack(@C.TrackType int trackType) {
    @Nullable DumpableStream stream = dumpableStreamByTrackType.get(trackType);
    if (stream == null) {
      return 0;
    }
    return stream.totalStreamSizeBytes;
  }

  // Dumper.Dumpable implementation.

  @Override
  public void dump(Dumper dumper) {
    for (int i = 0; i < dumpableFormatByTrackType.size(); i++) {
      dumpableFormatByTrackType.valueAt(i).dump(dumper);
    }

    if (!metadataList.isEmpty()) {
      Collections.sort(metadataList, Comparator.comparing(Metadata.Entry::toString));
      dumper.startBlock("container metadata");
      for (Metadata.Entry metadata : metadataList) {
        dumper.add("entry", metadata);
      }
      dumper.endBlock();
    }

    for (int i = 0; i < dumpableStreamByTrackType.size(); i++) {
      dumpableStreamByTrackType.valueAt(i).dump(dumper);
    }

    dumper.add("released", released);
  }

  private static class DumpableStream implements Dumpable {
    private final @C.TrackType int trackType;
    protected final ArrayList<DumpableSample> dumpableSamples;

    protected long totalStreamSizeBytes;

    public DumpableStream(@C.TrackType int trackType) {
      this.trackType = trackType;
      this.dumpableSamples = new ArrayList<>();
    }

    public void addSample(ByteBuffer sample, boolean isKeyFrame, long presentationTimeUs) {
      totalStreamSizeBytes += sample.remaining();
      dumpableSamples.add(new DumpableSample(trackType, sample, isKeyFrame, presentationTimeUs));
    }

    @Override
    public void dump(Dumper dumper) {
      for (DumpableSample dumpableSample : dumpableSamples) {
        dumpableSample.dump(dumper);
      }
    }
  }

  /**
   * A {@link DumpableStream} for PCM audio.
   *
   * <p>{@linkplain Util#isEncodingLinearPcm Linear PCM audio} is represented as frames (or PCM
   * samples), with the {@linkplain Util#getPcmFrameSize size} based on the encoding and channel
   * count. Each frame contains the data for one sample (based on the sample rate) for each channel.
   */
  private static final class DumpablePcmAudioStream extends DumpableStream {
    private static final int BYTES_PER_DUMPABLE = 4096;

    private final ByteBuffer currentPendingData;

    public DumpablePcmAudioStream(@C.TrackType int trackType) {
      super(trackType);
      checkState(trackType == C.TRACK_TYPE_AUDIO);
      currentPendingData =
          ByteBuffer.allocateDirect(BYTES_PER_DUMPABLE).order(ByteOrder.nativeOrder());
    }

    @Override
    public void addSample(ByteBuffer sample, boolean isKeyFrame, long presentationTimeUs) {
      totalStreamSizeBytes += sample.remaining();
      int samplePosition = sample.position();

      while (sample.hasRemaining()) {
        // Small input sample will not fill the buffer. Add to pending and wait for more data.
        if (currentPendingData.remaining() > sample.remaining()) {
          currentPendingData.put(sample);
          break;
        }

        int bytesToProgress = currentPendingData.remaining();
        byte[] byteHolder = new byte[bytesToProgress];
        sample.get(byteHolder);
        currentPendingData.put(byteHolder);
        currentPendingData.flip();
        dumpableSamples.add(
            new DumpableSample(
                C.TRACK_TYPE_AUDIO, currentPendingData, /* isKeyFrame= */ true, C.TIME_UNSET));
        currentPendingData.clear();
      }

      sample.position(samplePosition);
    }

    @Override
    public void dump(Dumper dumper) {
      if (currentPendingData.position() != 0) {
        currentPendingData.flip();
        dumpableSamples.add(
            new DumpableSample(
                C.TRACK_TYPE_AUDIO, currentPendingData, /* isKeyFrame= */ true, C.TIME_UNSET));
      }
      super.dump(dumper);
    }
  }

  private static final class DumpableSample implements Dumpable {

    private final @C.TrackType int trackType;
    private final long presentationTimeUs;
    private final boolean isKeyFrame;
    private final int sampleDataHashCode;
    private final int sampleSize;

    public DumpableSample(
        @C.TrackType int trackType,
        ByteBuffer sample,
        boolean isKeyFrame,
        long presentationTimeUs) {
      this.trackType = trackType;
      this.presentationTimeUs = presentationTimeUs;
      this.isKeyFrame = isKeyFrame;
      int initialPosition = sample.position();
      sampleSize = sample.remaining();
      byte[] data = new byte[sampleSize];
      sample.get(data);
      sample.position(initialPosition);
      sampleDataHashCode = Arrays.hashCode(data);
    }

    @Override
    public void dump(Dumper dumper) {
      dumper
          .startBlock("sample")
          .add("trackType", Util.getTrackTypeString(trackType))
          .add("dataHashCode", sampleDataHashCode)
          .add("size", sampleSize)
          .add("isKeyFrame", isKeyFrame);
      if (presentationTimeUs != C.TIME_UNSET) {
        dumper.addTime("presentationTimeUs", presentationTimeUs);
      }
      dumper.endBlock();
    }
  }
}
