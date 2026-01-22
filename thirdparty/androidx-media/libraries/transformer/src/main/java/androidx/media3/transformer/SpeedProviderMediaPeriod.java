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

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.StreamKey;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.LongArray;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import com.google.common.primitives.Floats;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} that adjusts the timestamps as specified by the speed provider. */
/* package */ final class SpeedProviderMediaPeriod implements MediaPeriod, MediaPeriod.Callback {

  public final MediaPeriod mediaPeriod;
  private final SpeedProviderMapper speedProviderMapper;
  private @MonotonicNonNull Callback callback;

  /**
   * Create an instance.
   *
   * @param mediaPeriod The wrapped {@link MediaPeriod}.
   * @param speedProvider The offset to apply to all timestamps coming from the wrapped period.
   */
  public SpeedProviderMediaPeriod(MediaPeriod mediaPeriod, SpeedProvider speedProvider) {
    this.mediaPeriod = mediaPeriod;
    this.speedProviderMapper = new SpeedProviderMapper(speedProvider);
  }

  /** Returns the wrapped {@link MediaPeriod}. */
  public MediaPeriod getWrappedMediaPeriod() {
    return mediaPeriod;
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
    mediaPeriod.prepare(/* callback= */ this, speedProviderMapper.getOriginalTimeUs(positionUs));
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    mediaPeriod.maybeThrowPrepareError();
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return mediaPeriod.getTrackGroups();
  }

  @Override
  public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    return mediaPeriod.getStreamKeys(trackSelections);
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    @NullableType SampleStream[] childStreams = new SampleStream[streams.length];
    for (int i = 0; i < streams.length; i++) {
      SpeedProviderMapperSampleStream sampleStream = (SpeedProviderMapperSampleStream) streams[i];
      childStreams[i] = sampleStream != null ? sampleStream.getChildStream() : null;
    }
    long startPositionUs =
        mediaPeriod.selectTracks(
            selections,
            mayRetainStreamFlags,
            childStreams,
            streamResetFlags,
            speedProviderMapper.getOriginalTimeUs(positionUs));
    for (int i = 0; i < streams.length; i++) {
      @Nullable SampleStream childStream = childStreams[i];
      if (childStream == null) {
        streams[i] = null;
      } else if (streams[i] == null
          || ((SpeedProviderMapperSampleStream) streams[i]).getChildStream() != childStream) {
        streams[i] = new SpeedProviderMapperSampleStream(childStream, speedProviderMapper);
      }
    }
    return speedProviderMapper.getAdjustedTimeUs(startPositionUs);
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    mediaPeriod.discardBuffer(speedProviderMapper.getOriginalTimeUs(positionUs), toKeyframe);
  }

  @Override
  public long readDiscontinuity() {
    long discontinuityPositionUs = mediaPeriod.readDiscontinuity();
    return discontinuityPositionUs == C.TIME_UNSET
        ? C.TIME_UNSET
        : speedProviderMapper.getAdjustedTimeUs(discontinuityPositionUs);
  }

  @Override
  public long seekToUs(long positionUs) {
    return speedProviderMapper.getAdjustedTimeUs(
        mediaPeriod.seekToUs(speedProviderMapper.getOriginalTimeUs(positionUs)));
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    return speedProviderMapper.getAdjustedTimeUs(
        mediaPeriod.getAdjustedSeekPositionUs(
            speedProviderMapper.getOriginalTimeUs(positionUs), seekParameters));
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = mediaPeriod.getBufferedPositionUs();
    return bufferedPositionUs == C.TIME_END_OF_SOURCE
        ? C.TIME_END_OF_SOURCE
        : speedProviderMapper.getAdjustedTimeUs(bufferedPositionUs);
  }

  @Override
  public long getNextLoadPositionUs() {
    long nextLoadPositionUs = mediaPeriod.getNextLoadPositionUs();
    return nextLoadPositionUs == C.TIME_END_OF_SOURCE
        ? C.TIME_END_OF_SOURCE
        : speedProviderMapper.getAdjustedTimeUs(nextLoadPositionUs);
  }

  @Override
  public boolean continueLoading(LoadingInfo loadingInfo) {
    return mediaPeriod.continueLoading(
        loadingInfo
            .buildUpon()
            .setPlaybackPositionUs(
                speedProviderMapper.getOriginalTimeUs(loadingInfo.playbackPositionUs))
            .build());
  }

  @Override
  public boolean isLoading() {
    return mediaPeriod.isLoading();
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    mediaPeriod.reevaluateBuffer(speedProviderMapper.getOriginalTimeUs(positionUs));
  }

  @Override
  public void onPrepared(MediaPeriod mediaPeriod) {
    Assertions.checkNotNull(callback).onPrepared(/* mediaPeriod= */ this);
  }

  @Override
  public void onContinueLoadingRequested(MediaPeriod source) {
    Assertions.checkNotNull(callback).onContinueLoadingRequested(/* source= */ this);
  }

  private static final class SpeedProviderMapper {

    private final long[] outputSegmentStartTimesUs;
    private final long[] inputSegmentStartTimesUs;
    private final float[] speeds;

    public SpeedProviderMapper(SpeedProvider speedProvider) {
      LongArray outputSegmentStartTimesUs = new LongArray();
      LongArray inputSegmentStartTimesUs = new LongArray();
      List<Float> speeds = new ArrayList<>();

      long lastOutputSegmentStartTimeUs = 0;
      long lastInputSegmentStartTimeUs = 0;
      float lastSpeed = speedProvider.getSpeed(lastInputSegmentStartTimeUs);
      outputSegmentStartTimesUs.add(lastOutputSegmentStartTimeUs);
      inputSegmentStartTimesUs.add(lastInputSegmentStartTimeUs);
      speeds.add(lastSpeed);
      long nextSpeedChangeTimeUs =
          speedProvider.getNextSpeedChangeTimeUs(lastInputSegmentStartTimeUs);

      while (nextSpeedChangeTimeUs != C.TIME_UNSET) {
        lastOutputSegmentStartTimeUs +=
            (long) ((nextSpeedChangeTimeUs - lastInputSegmentStartTimeUs) / lastSpeed);
        lastInputSegmentStartTimeUs = nextSpeedChangeTimeUs;
        lastSpeed = speedProvider.getSpeed(lastInputSegmentStartTimeUs);
        outputSegmentStartTimesUs.add(lastOutputSegmentStartTimeUs);
        inputSegmentStartTimesUs.add(lastInputSegmentStartTimeUs);
        speeds.add(lastSpeed);
        nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(lastInputSegmentStartTimeUs);
      }
      this.outputSegmentStartTimesUs = outputSegmentStartTimesUs.toArray();
      this.inputSegmentStartTimesUs = inputSegmentStartTimesUs.toArray();
      this.speeds = Floats.toArray(speeds);
    }

    public long getAdjustedTimeUs(long originalTimeUs) {
      int index =
          Util.binarySearchFloor(
              inputSegmentStartTimesUs,
              originalTimeUs,
              /* inclusive= */ true,
              /* stayInBounds= */ true);
      return (long)
          (outputSegmentStartTimesUs[index]
              + (originalTimeUs - inputSegmentStartTimesUs[index]) / speeds[index]);
    }

    public long getOriginalTimeUs(long adjustedTimeUs) {
      int index =
          Util.binarySearchFloor(
              outputSegmentStartTimesUs,
              adjustedTimeUs,
              /* inclusive= */ true,
              /* stayInBounds= */ true);
      return (long)
          (inputSegmentStartTimesUs[index]
              + (adjustedTimeUs - outputSegmentStartTimesUs[index]) * speeds[index]);
    }
  }

  private static final class SpeedProviderMapperSampleStream implements SampleStream {

    private final SampleStream sampleStream;
    private final SpeedProviderMapper speedProviderMapper;

    public SpeedProviderMapperSampleStream(
        SampleStream sampleStream, SpeedProviderMapper speedProviderMapper) {
      this.sampleStream = sampleStream;
      this.speedProviderMapper = speedProviderMapper;
    }

    public SampleStream getChildStream() {
      return sampleStream;
    }

    @Override
    public boolean isReady() {
      return sampleStream.isReady();
    }

    @Override
    public void maybeThrowError() throws IOException {
      sampleStream.maybeThrowError();
    }

    @Override
    public int readData(
        FormatHolder formatHolder, DecoderInputBuffer buffer, @ReadFlags int readFlags) {
      int readResult = sampleStream.readData(formatHolder, buffer, readFlags);
      if (readResult == C.RESULT_BUFFER_READ) {
        buffer.timeUs = speedProviderMapper.getAdjustedTimeUs(buffer.timeUs);
      }
      return readResult;
    }

    @Override
    public int skipData(long positionUs) {
      return sampleStream.skipData(speedProviderMapper.getOriginalTimeUs(positionUs));
    }
  }
}
