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

import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM;
import static androidx.media3.test.utils.FakeSampleStream.FakeSampleStreamItem.oneByteSample;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.drm.DrmSessionEventListener;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeSampleStream;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SpeedProviderMediaPeriod}. */
@RunWith(AndroidJUnit4.class)
public final class SpeedProviderMediaPeriodTest {

  SpeedProvider speedProvider =
      TestSpeedProvider.createWithStartTimes(
          new long[] {0, 1_000_000, 2_000_000}, new float[] {0.5f, 1f, 2f});

  @Test
  public void selectTracks_createsSampleStreamAdjustingTimes() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 500_000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(fakeMediaPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 0);
    FormatHolder formatHolder = new FormatHolder();
    DecoderInputBuffer inputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    ImmutableList.Builder<Integer> readResults = ImmutableList.builder();

    SampleStream sampleStream =
        selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);
    readResults.add(sampleStream.readData(formatHolder, inputBuffer, FLAG_REQUIRE_FORMAT));
    readResults.add(sampleStream.readData(formatHolder, inputBuffer, /* readFlags= */ 0));
    long readBufferTimeUs = inputBuffer.timeUs;
    readResults.add(sampleStream.readData(formatHolder, inputBuffer, /* readFlags= */ 0));
    boolean readEndOfStreamBuffer = inputBuffer.isEndOfStream();

    assertThat(readResults.build())
        .containsExactly(C.RESULT_FORMAT_READ, C.RESULT_BUFFER_READ, C.RESULT_BUFFER_READ);
    assertThat(readBufferTimeUs).isEqualTo(1_000_000);
    assertThat(readEndOfStreamBuffer).isTrue();
  }

  @Test
  public void getBufferedPositionUs_returnsAdjustedPosition() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 500_000, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 1_500_000, C.BUFFER_FLAG_KEY_FRAME)));
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(fakeMediaPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 0);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    assertThat(speedProviderMediaPeriod.getBufferedPositionUs()).isEqualTo(2_500_000);
  }

  @Test
  public void getNextLoadPositionUs_returnsAdjustedPosition() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 1_000_000, C.BUFFER_FLAG_KEY_FRAME),
                oneByteSample(/* timeUs= */ 2_500_000, C.BUFFER_FLAG_KEY_FRAME)));
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(fakeMediaPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 0);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    assertThat(speedProviderMediaPeriod.getNextLoadPositionUs()).isEqualTo(3_250_000);
  }

  @Test
  public void prepare_isForwardedWithAdjustedTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 100_000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);

    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 250_000);

    verify(spyPeriod).prepare(any(), eq(125_000L));
  }

  @Test
  public void discardBuffer_isForwardedWithAdjustedTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 3_250_000);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    speedProviderMediaPeriod.discardBuffer(/* positionUs= */ 3_250_000, /* toKeyframe= */ true);

    verify(spyPeriod).discardBuffer(2_500_000, /* toKeyframe= */ true);
  }

  @Test
  public void discardBuffer_positionIsZero_isForwardedWithAdjustedTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 0);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    speedProviderMediaPeriod.discardBuffer(/* positionUs= */ 0, /* toKeyframe= */ true);

    verify(spyPeriod).discardBuffer(0, /* toKeyframe= */ true);
  }

  @Test
  public void readDiscontinuity_isForwardedWithAdjustedTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeMediaPeriod.setDiscontinuityPositionUs(1_000_000);
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 500_000);

    assertThat(speedProviderMediaPeriod.readDiscontinuity()).isEqualTo(2_000_000);
    verify(spyPeriod).readDiscontinuity();
  }

  @Test
  public void seekTo_isForwardedWithAdjustedTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 2_000_000);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    long seekResultTimeUs = speedProviderMediaPeriod.seekToUs(/* positionUs= */ 3_000_000);

    verify(spyPeriod).seekToUs(2_000_000);
    assertThat(seekResultTimeUs).isEqualTo(3_000_000);
  }

  @Test
  public void getAdjustedSeekPosition_isForwardedWithAdjustedTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    fakeMediaPeriod.setSeekToUsOffset(2000);
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 2_000_000);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    long adjustedSeekPositionUs =
        speedProviderMediaPeriod.getAdjustedSeekPositionUs(
            /* positionUs= */ 2_000_000, SeekParameters.DEFAULT);

    verify(spyPeriod).getAdjustedSeekPositionUs(1_000_000, SeekParameters.DEFAULT);
    assertThat(adjustedSeekPositionUs).isEqualTo(2_002_000);
  }

  @Test
  public void continueLoading_isForwardedWithOriginalTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 3_250_000);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    speedProviderMediaPeriod.continueLoading(
        new LoadingInfo.Builder().setPlaybackPositionUs(3_250_000).build());

    verify(spyPeriod)
        .continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(2_500_000).build());
  }

  @Test
  public void reevaluateBuffer_isForwardedWithOriginalTime() throws Exception {
    FakeMediaPeriod fakeMediaPeriod =
        createFakeMediaPeriod(
            ImmutableList.of(
                oneByteSample(/* timeUs= */ 8000, C.BUFFER_FLAG_KEY_FRAME), END_OF_STREAM_ITEM));
    MediaPeriod spyPeriod = spy(fakeMediaPeriod);
    SpeedProviderMediaPeriod speedProviderMediaPeriod =
        new SpeedProviderMediaPeriod(spyPeriod, speedProvider);
    prepareMediaPeriodSync(speedProviderMediaPeriod, /* positionUs= */ 3_250_000);
    selectTracksOnMediaPeriodAndTriggerLoading(speedProviderMediaPeriod);

    speedProviderMediaPeriod.reevaluateBuffer(/* positionUs= */ 3_250_000);

    verify(spyPeriod).reevaluateBuffer(2_500_000);
  }

  private static FakeMediaPeriod createFakeMediaPeriod(
      ImmutableList<FakeSampleStream.FakeSampleStreamItem> sampleStreamItems) {
    EventDispatcher eventDispatcher =
        new EventDispatcher()
            .withParameters(/* windowIndex= */ 0, new MediaPeriodId(/* periodUid= */ new Object()));
    return new FakeMediaPeriod(
        new TrackGroupArray(new TrackGroup(new Format.Builder().build())),
        new DefaultAllocator(/* trimOnReset= */ false, /* individualAllocationSize= */ 1024),
        (unusedFormat, unusedMediaPeriodId) -> sampleStreamItems,
        eventDispatcher,
        DrmSessionManager.DRM_UNSUPPORTED,
        new DrmSessionEventListener.EventDispatcher(),
        /* deferOnPrepared= */ false);
  }

  private static void prepareMediaPeriodSync(MediaPeriod mediaPeriod, long positionUs)
      throws Exception {
    CountDownLatch prepareCountDown = new CountDownLatch(1);
    mediaPeriod.prepare(
        new MediaPeriod.Callback() {
          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            prepareCountDown.countDown();
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod source) {
            mediaPeriod.continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
          }
        },
        positionUs);
    prepareCountDown.await();
  }

  private static SampleStream selectTracksOnMediaPeriodAndTriggerLoading(MediaPeriod mediaPeriod) {
    ExoTrackSelection selection =
        new FixedTrackSelection(mediaPeriod.getTrackGroups().get(0), /* track= */ 0);
    SampleStream[] streams = new SampleStream[1];
    mediaPeriod.selectTracks(
        /* selections= */ new ExoTrackSelection[] {selection},
        /* mayRetainStreamFlags= */ new boolean[] {false},
        streams,
        /* streamResetFlags= */ new boolean[] {false},
        /* positionUs= */ 0);
    mediaPeriod.continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
    return streams[0];
  }
}
