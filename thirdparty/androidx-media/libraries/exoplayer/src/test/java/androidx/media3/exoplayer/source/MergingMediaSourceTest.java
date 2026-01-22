/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static androidx.media3.test.utils.robolectric.RobolectricUtil.DEFAULT_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId;
import androidx.media3.exoplayer.source.MergingMediaSource.IllegalMergeException;
import androidx.media3.test.utils.FakeMediaPeriod;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.FakeTimeline.TimelineWindowDefinition;
import androidx.media3.test.utils.MediaSourceTestRunner;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MergingMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class MergingMediaSourceTest {

  @Test
  public void prepare_withoutDurationClipping_usesTimelineOfFirstSource() throws IOException {
    FakeTimeline timeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 30));
    FakeTimeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ C.TIME_UNSET));
    FakeTimeline timeline3 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 10));

    Timeline mergedTimeline =
        prepareMergingMediaSource(/* clipDurations= */ false, timeline1, timeline2, timeline3);

    assertThat(mergedTimeline).isEqualTo(timeline1);
  }

  @Test
  public void prepare_withDurationClipping_usesDurationOfShortestSource() throws IOException {
    FakeTimeline timeline1 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 30));
    FakeTimeline timeline2 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ C.TIME_UNSET));
    FakeTimeline timeline3 =
        new FakeTimeline(
            new TimelineWindowDefinition(
                /* isSeekable= */ true, /* isDynamic= */ true, /* durationUs= */ 10));

    Timeline mergedTimeline =
        prepareMergingMediaSource(/* clipDurations= */ true, timeline1, timeline2, timeline3);

    assertThat(mergedTimeline).isEqualTo(timeline3);
  }

  @Test
  public void prepare_differentPeriodCounts_fails() throws IOException {
    FakeTimeline firstTimeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 1, /* id= */ 1));
    FakeTimeline secondTimeline =
        new FakeTimeline(new TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ 2));

    IllegalMergeException exception =
        assertThrows(
            IllegalMergeException.class,
            () ->
                prepareMergingMediaSource(
                    /* clipDurations= */ false, firstTimeline, secondTimeline));
    assertThat(exception.reason).isEqualTo(IllegalMergeException.REASON_PERIOD_COUNT_MISMATCH);
  }

  @Test
  public void createPeriod_createsChildPeriods() throws Exception {
    FakeMediaSource[] mediaSources = new FakeMediaSource[2];
    for (int i = 0; i < mediaSources.length; i++) {
      mediaSources[i] = new FakeMediaSource(new FakeTimeline(/* windowCount= */ 2));
    }
    MergingMediaSource mediaSource = new MergingMediaSource(mediaSources);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mediaSource);
    try {
      testRunner.prepareSource();
      testRunner.assertPrepareAndReleaseAllPeriods();
      for (FakeMediaSource element : mediaSources) {
        assertThat(element.getCreatedMediaPeriods()).isNotEmpty();
      }
      testRunner.releaseSource();
    } finally {
      testRunner.release();
    }
  }

  /**
   * Assert that events from all child sources are propagated, but always reported with a {@link
   * MediaPeriodId} that can be resolved against the {@link Timeline} exposed by the parent {@link
   * MergingMediaSource} (these are period IDs from the first child source).
   */
  @Test
  public void eventsFromAllChildrenPropagated_alwaysAssociatedWithPrimaryPeriodId()
      throws Exception {
    Multiset<Object> onLoadStartedMediaPeriodUids = ConcurrentHashMultiset.create();
    Multiset<Object> onLoadCompletedMediaPeriodUids = ConcurrentHashMultiset.create();
    MediaSourceEventListener mediaSourceEventListener =
        new MediaSourceEventListener() {
          @Override
          public void onLoadStarted(
              int windowIndex,
              @Nullable MediaPeriodId mediaPeriodId,
              LoadEventInfo loadEventInfo,
              MediaLoadData mediaLoadData,
              int retryCount) {
            if (mediaPeriodId != null) {
              onLoadStartedMediaPeriodUids.add(mediaPeriodId.periodUid);
            }
          }

          @Override
          public void onLoadCompleted(
              int windowIndex,
              @Nullable MediaPeriodId mediaPeriodId,
              LoadEventInfo loadEventInfo,
              MediaLoadData mediaLoadData) {
            if (mediaPeriodId != null) {
              onLoadCompletedMediaPeriodUids.add(mediaPeriodId.periodUid);
            }
          }
        };
    FakeMediaSource[] childMediaSources = new FakeMediaSource[2];
    for (int i = 0; i < childMediaSources.length; i++) {
      childMediaSources[i] =
          new FakeMediaSource(
              new FakeTimeline(
                  new FakeTimeline.TimelineWindowDefinition(/* periodCount= */ 2, /* id= */ i)));
    }
    // Delay child1's period preparation, so we can delay child1period0 preparation completion until
    // after period1 has been created and prepared.
    childMediaSources[1].setPeriodDefersOnPreparedCallback(true);
    MergingMediaSource mergingMediaSource = new MergingMediaSource(childMediaSources);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mergingMediaSource);
    try {
      testRunner.runOnPlaybackThread(
          () ->
              mergingMediaSource.addEventListener(
                  Util.createHandlerForCurrentLooper(), mediaSourceEventListener));
      Timeline timeline = testRunner.prepareSource();
      MediaPeriod mergedMediaPeriod0 =
          testRunner.createPeriod(new MediaPeriodId(timeline.getUidOfPeriod(/* periodIndex= */ 0)));
      FakeMediaPeriod childSource1Period0 =
          (FakeMediaPeriod) childMediaSources[1].getLastCreatedActiveMediaPeriod();
      MediaPeriod mergedMediaPeriod1 =
          testRunner.createPeriod(new MediaPeriodId(timeline.getUidOfPeriod(/* periodIndex= */ 1)));
      // Prepare period0 after period1 has been created to ensure that MergingMediaSource correctly
      // attributes and propagates the associated onLoadStarted event.
      CountDownLatch preparedLatch0 =
          testRunner.preparePeriod(mergedMediaPeriod0, /* positionUs= */ 0);
      CountDownLatch preparedLatch1 =
          testRunner.preparePeriod(mergedMediaPeriod1, /* positionUs= */ 0);
      // Complete child1period0 preparation after period1 has been created to ensure that
      // MergingMediaSource correctly attributes and propagates the associated onLoadCompleted
      // event.
      childSource1Period0.setPreparationComplete();
      ((FakeMediaPeriod) childMediaSources[1].getLastCreatedActiveMediaPeriod())
          .setPreparationComplete();

      assertThat(preparedLatch0.await(DEFAULT_TIMEOUT_MS, MILLISECONDS)).isTrue();
      assertThat(preparedLatch1.await(DEFAULT_TIMEOUT_MS, MILLISECONDS)).isTrue();
      testRunner.releasePeriod(mergedMediaPeriod0);
      testRunner.releasePeriod(mergedMediaPeriod1);
      for (FakeMediaSource element : childMediaSources) {
        assertThat(element.getCreatedMediaPeriods()).isNotEmpty();
      }
      testRunner.releaseSource();
      ImmutableList.Builder<Object> expectedMediaPeriodUids =
          ImmutableList.builderWithExpectedSize(onLoadStartedMediaPeriodUids.size());
      for (int i = 0; i < timeline.getPeriodCount(); i++) {
        Object periodUid = timeline.getUidOfPeriod(i);
        // Add each period UID twice, because each child reports its own load events (but both are
        // reported with the same MediaPeriodId out of MergingMediaSource).
        expectedMediaPeriodUids.add(periodUid).add(periodUid);
      }
      assertThat(onLoadStartedMediaPeriodUids)
          .containsExactlyElementsIn(expectedMediaPeriodUids.build());
      assertThat(onLoadCompletedMediaPeriodUids)
          .containsExactlyElementsIn(expectedMediaPeriodUids.build());
    } finally {
      testRunner.release();
    }
  }

  /**
   * Wraps the specified timelines in a {@link MergingMediaSource}, prepares it and returns the
   * merged timeline.
   */
  private static Timeline prepareMergingMediaSource(boolean clipDurations, Timeline... timelines)
      throws IOException {
    FakeMediaSource[] mediaSources = new FakeMediaSource[timelines.length];
    for (int i = 0; i < timelines.length; i++) {
      mediaSources[i] = new FakeMediaSource(timelines[i]);
    }
    MergingMediaSource mergingMediaSource =
        new MergingMediaSource(/* adjustPeriodTimeOffsets= */ false, clipDurations, mediaSources);
    MediaSourceTestRunner testRunner = new MediaSourceTestRunner(mergingMediaSource);
    try {
      Timeline timeline = testRunner.prepareSource();
      testRunner.releaseSource();
      for (FakeMediaSource mediaSource : mediaSources) {
        mediaSource.assertReleased();
      }
      return timeline;
    } finally {
      testRunner.release();
    }
  }
}
