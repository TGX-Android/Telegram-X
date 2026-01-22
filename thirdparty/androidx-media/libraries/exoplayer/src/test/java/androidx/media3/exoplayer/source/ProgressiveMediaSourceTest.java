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
package androidx.media3.exoplayer.source;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.test.utils.robolectric.RobolectricUtil.DEFAULT_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.LoadingInfo;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.extractor.SeekMap;
import androidx.media3.test.utils.MediaSourceTestRunner;
import androidx.media3.test.utils.TestUtil;
import androidx.media3.test.utils.robolectric.RobolectricUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link ProgressiveMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class ProgressiveMediaSourceTest {

  @Test
  public void canUpdateMediaItem_withIrrelevantFieldsChanged_returnsTrue() {
    MediaItem initialMediaItem =
        new MediaItem.Builder().setUri("http://test.test").setCustomCacheKey("cache").build();
    MediaItem updatedMediaItem =
        TestUtil.buildFullyCustomizedMediaItem()
            .buildUpon()
            .setUri("http://test.test")
            .setCustomCacheKey("cache")
            .build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);

    boolean canUpdateMediaItem = mediaSource.canUpdateMediaItem(updatedMediaItem);

    assertThat(canUpdateMediaItem).isTrue();
  }

  @Test
  public void canUpdateMediaItem_withNullLocalConfiguration_returnsFalse() {
    MediaItem initialMediaItem = new MediaItem.Builder().setUri("http://test.test").build();
    MediaItem updatedMediaItem = new MediaItem.Builder().setMediaId("id").build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);

    boolean canUpdateMediaItem = mediaSource.canUpdateMediaItem(updatedMediaItem);

    assertThat(canUpdateMediaItem).isFalse();
  }

  @Test
  public void canUpdateMediaItem_withChangedUri_returnsFalse() {
    MediaItem initialMediaItem = new MediaItem.Builder().setUri("http://test.test").build();
    MediaItem updatedMediaItem = new MediaItem.Builder().setUri("http://test2.test").build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);

    boolean canUpdateMediaItem = mediaSource.canUpdateMediaItem(updatedMediaItem);

    assertThat(canUpdateMediaItem).isFalse();
  }

  @Test
  public void canUpdateMediaItem_withChangedCustomCacheKey_returnsFalse() {
    MediaItem initialMediaItem =
        new MediaItem.Builder().setUri("http://test.test").setCustomCacheKey("old").build();
    MediaItem updatedMediaItem =
        new MediaItem.Builder().setUri("http://test.test").setCustomCacheKey("new").build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);

    boolean canUpdateMediaItem = mediaSource.canUpdateMediaItem(updatedMediaItem);

    assertThat(canUpdateMediaItem).isFalse();
  }

  @Test
  public void updateMediaItem_createsTimelineWithUpdatedItem() throws Exception {
    MediaItem initialMediaItem =
        new MediaItem.Builder().setUri("http://test.test").setTag("tag1").build();
    MediaItem updatedMediaItem =
        new MediaItem.Builder().setUri("http://test.test").setTag("tag2").build();
    MediaSource mediaSource = buildMediaSource(initialMediaItem);
    AtomicReference<Timeline> timelineReference = new AtomicReference<>();

    mediaSource.updateMediaItem(updatedMediaItem);
    mediaSource.prepareSource(
        (source, timeline) -> timelineReference.set(timeline),
        /* mediaTransferListener= */ null,
        PlayerId.UNSET);
    RobolectricUtil.runMainLooperUntil(() -> timelineReference.get() != null);

    assertThat(
            timelineReference
                .get()
                .getWindow(/* windowIndex= */ 0, new Timeline.Window())
                .mediaItem)
        .isEqualTo(updatedMediaItem);
  }

  @Test
  public void lazyLoading_preparationCompletesWithoutLoadingData_loadsDataWhenTrackSelected()
      throws Exception {
    Set<Uri> openedUris = new HashSet<>();
    DataSource.Factory dataSourceFactory =
        new ResolvingDataSource.Factory(
            new DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()),
            dataSpec -> {
              openedUris.add(dataSpec.uri);
              return dataSpec;
            });
    Uri mediaUri = Uri.parse("asset:///media/mp4/sample_opus.mp4");
    Format format =
        new Format.Builder().setId("format ID").setSampleMimeType(MimeTypes.AUDIO_OPUS).build();
    ProgressiveMediaSource mediaSource =
        new ProgressiveMediaSource.Factory(dataSourceFactory)
            .enableLazyLoadingWithSingleTrack(/* trackId= */ 42, format)
            .createMediaSource(MediaItem.fromUri(mediaUri));
    ProgressiveMediaSourceTestRunner mediaSourceTestRunner =
        new ProgressiveMediaSourceTestRunner(mediaSource);
    ConditionVariable loadCompleted = new ConditionVariable();
    mediaSourceTestRunner.runOnPlaybackThread(
        () ->
            mediaSource.addEventListener(
                new Handler(checkNotNull(Looper.myLooper())),
                new MediaSourceEventListener() {
                  @Override
                  public void onLoadCompleted(
                      int windowIndex,
                      @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                      LoadEventInfo loadEventInfo,
                      MediaLoadData mediaLoadData) {
                    loadCompleted.open();
                  }
                }));

    AtomicReference<SeekMap> seekMapReference = new AtomicReference<>();
    ProgressiveMediaSource.Listener listener = (source, seekMap) -> seekMapReference.set(seekMap);
    mediaSourceTestRunner.setListener(listener);
    Timeline timeline = mediaSourceTestRunner.prepareSource();
    MediaPeriod mediaPeriod =
        mediaSourceTestRunner.createPeriod(
            new MediaSource.MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    CountDownLatch preparedLatch =
        mediaSourceTestRunner.preparePeriod(mediaPeriod, /* positionUs= */ 0);

    assertThat(preparedLatch.await(DEFAULT_TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(openedUris).isEmpty();
    assertThat(seekMapReference.get()).isNotNull();

    ListenableFuture<Boolean> isLoading =
        mediaSourceTestRunner.asyncRunOnPlaybackThread(
            () -> {
              mediaPeriod.continueLoading(
                  new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
              return mediaPeriod.isLoading();
            });
    assertThat(isLoading.get()).isFalse();

    isLoading =
        mediaSourceTestRunner.asyncRunOnPlaybackThread(
            () -> {
              selectOnlyTrack(mediaPeriod);
              mediaPeriod.continueLoading(
                  new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
              return mediaPeriod.isLoading();
            });
    assertThat(isLoading.get()).isTrue();

    loadCompleted.block();

    assertThat(mediaSourceTestRunner.asyncRunOnPlaybackThread(mediaPeriod::isLoading).get())
        .isFalse();
    assertThat(openedUris).containsExactly(mediaUri);

    mediaSourceTestRunner.releasePeriod(mediaPeriod);
    mediaSourceTestRunner.clearListener();
    mediaSourceTestRunner.releaseSource();
    mediaSourceTestRunner.release();
  }

  @Test
  public void lazyLoading_notFoundUri_loadErrorReportedWhenTrackSelected() throws Exception {
    ProgressiveMediaSource mediaSource =
        new ProgressiveMediaSource.Factory(
                new DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()))
            // Disable retries, so the first error is marked fatal.
            .setLoadErrorHandlingPolicy(
                new DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount= */ 0))
            .enableLazyLoadingWithSingleTrack(
                /* trackId= */ 42, new Format.Builder().setId("format ID").build())
            .createMediaSource(MediaItem.fromUri("file:///not/found"));
    MediaSourceTestRunner mediaSourceTestRunner = new MediaSourceTestRunner(mediaSource);

    Timeline timeline = mediaSourceTestRunner.prepareSource();
    CountDownLatch loadErrorReported = new CountDownLatch(1);
    mediaSourceTestRunner.runOnPlaybackThread(
        () ->
            mediaSource.addEventListener(
                Util.createHandlerForCurrentLooper(),
                new MediaSourceEventListener() {
                  @Override
                  public void onLoadError(
                      int windowIndex,
                      @Nullable MediaSource.MediaPeriodId mediaPeriodId,
                      LoadEventInfo loadEventInfo,
                      MediaLoadData mediaLoadData,
                      IOException error,
                      boolean wasCanceled) {
                    loadErrorReported.countDown();
                  }
                }));
    MediaPeriod mediaPeriod =
        mediaSourceTestRunner.createPeriod(
            new MediaSource.MediaPeriodId(
                timeline.getUidOfPeriod(/* periodIndex= */ 0), /* windowSequenceNumber= */ 0));
    CountDownLatch preparedLatch =
        mediaSourceTestRunner.preparePeriod(mediaPeriod, /* positionUs= */ 0);
    assertThat(preparedLatch.await(DEFAULT_TIMEOUT_MS, MILLISECONDS)).isTrue();

    ListenableFuture<Boolean> isLoading =
        mediaSourceTestRunner.asyncRunOnPlaybackThread(
            () -> {
              selectOnlyTrack(mediaPeriod);
              mediaPeriod.continueLoading(
                  new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
              return mediaPeriod.isLoading();
            });
    assertThat(isLoading.get()).isTrue();
    assertThat(loadErrorReported.await(DEFAULT_TIMEOUT_MS, MILLISECONDS)).isTrue();

    mediaSourceTestRunner.releasePeriod(mediaPeriod);
    mediaSourceTestRunner.releaseSource();
    mediaSourceTestRunner.release();
  }

  private static MediaSource buildMediaSource(MediaItem mediaItem) {
    return new ProgressiveMediaSource.Factory(
            new DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()))
        .createMediaSource(mediaItem);
  }

  private static void selectOnlyTrack(MediaPeriod mediaPeriod) {
    checkState(mediaPeriod.getTrackGroups().length == 1);
    mediaPeriod.selectTracks(
        new ExoTrackSelection[] {new FixedTrackSelection(mediaPeriod.getTrackGroups().get(0), 0)},
        /* mayRetainStreamFlags= */ new boolean[] {false},
        new SampleStream[1],
        /* streamResetFlags= */ new boolean[] {false},
        /* positionUs= */ 0);
  }

  private static final class ProgressiveMediaSourceTestRunner extends MediaSourceTestRunner {

    private final ProgressiveMediaSource mediaSource;

    public ProgressiveMediaSourceTestRunner(ProgressiveMediaSource mediaSource) {
      super(mediaSource);
      this.mediaSource = mediaSource;
    }

    public void setListener(ProgressiveMediaSource.Listener listener) {
      runOnPlaybackThread(() -> mediaSource.setListener(listener));
    }

    public void clearListener() {
      runOnPlaybackThread(mediaSource::clearListener);
    }
  }
}
