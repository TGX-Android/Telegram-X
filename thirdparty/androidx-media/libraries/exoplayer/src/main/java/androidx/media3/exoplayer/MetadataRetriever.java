/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.media3.exoplayer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.mp4.Mp4Extractor;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Retrieves the static metadata of {@link MediaItem MediaItems}. */
@UnstableApi
public final class MetadataRetriever {

  /** The default number of maximum parallel retrievals. */
  public static final int DEFAULT_MAXIMUM_PARALLEL_RETRIEVALS = 5;

  private MetadataRetriever() {}

  /**
   * Retrieves the {@link TrackGroupArray} corresponding to a {@link MediaItem}.
   *
   * <p>This is equivalent to using {@link #retrieveMetadata(MediaSource.Factory, MediaItem)} with a
   * {@link DefaultMediaSourceFactory} and a {@link DefaultExtractorsFactory} with {@link
   * Mp4Extractor#FLAG_READ_MOTION_PHOTO_METADATA} and {@link Mp4Extractor#FLAG_READ_SEF_DATA} set.
   *
   * @param context The {@link Context}.
   * @param mediaItem The {@link MediaItem} whose metadata should be retrieved.
   * @return A {@link ListenableFuture} of the result.
   */
  public static ListenableFuture<TrackGroupArray> retrieveMetadata(
      Context context, MediaItem mediaItem) {
    return retrieveMetadata(context, mediaItem, Clock.DEFAULT);
  }

  /**
   * Retrieves the {@link TrackGroupArray} corresponding to a {@link MediaItem}.
   *
   * <p>This method is thread-safe.
   *
   * @param mediaSourceFactory mediaSourceFactory The {@link MediaSource.Factory} to use to read the
   *     data.
   * @param mediaItem The {@link MediaItem} whose metadata should be retrieved.
   * @return A {@link ListenableFuture} of the result.
   */
  public static ListenableFuture<TrackGroupArray> retrieveMetadata(
      MediaSource.Factory mediaSourceFactory, MediaItem mediaItem) {
    return retrieveMetadata(mediaSourceFactory, mediaItem, Clock.DEFAULT);
  }

  @VisibleForTesting
  /* package */ static ListenableFuture<TrackGroupArray> retrieveMetadata(
      Context context, MediaItem mediaItem, Clock clock) {
    ExtractorsFactory extractorsFactory =
        new DefaultExtractorsFactory()
            .setMp4ExtractorFlags(
                Mp4Extractor.FLAG_READ_MOTION_PHOTO_METADATA | Mp4Extractor.FLAG_READ_SEF_DATA);
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(context, extractorsFactory);
    return retrieveMetadata(mediaSourceFactory, mediaItem, clock);
  }

  private static ListenableFuture<TrackGroupArray> retrieveMetadata(
      MediaSource.Factory mediaSourceFactory, MediaItem mediaItem, Clock clock) {
    return new MetadataRetrieverInternal(mediaSourceFactory, mediaItem, clock).retrieveMetadata();
  }

  /**
   * Sets the maximum number of metadata retrievals run in parallel.
   *
   * <p>The default is {@link #DEFAULT_MAXIMUM_PARALLEL_RETRIEVALS}.
   *
   * @param maximumParallelRetrievals The maximum number of parallel retrievals.
   */
  public static void setMaximumParallelRetrievals(int maximumParallelRetrievals) {
    checkArgument(maximumParallelRetrievals >= 1);
    SharedWorkerThread.MAX_PARALLEL_RETRIEVALS.set(maximumParallelRetrievals);
  }

  private static final class MetadataRetrieverInternal {

    private static final int MESSAGE_PREPARE_SOURCE = 1;
    private static final int MESSAGE_CHECK_FOR_FAILURE = 2;
    private static final int MESSAGE_CONTINUE_LOADING = 3;
    private static final int MESSAGE_RELEASE = 4;

    private static final SharedWorkerThread SHARED_WORKER_THREAD = new SharedWorkerThread();

    private final MediaSource.Factory mediaSourceFactory;
    private final MediaItem mediaItem;
    private final HandlerWrapper mediaSourceHandler;
    private final SettableFuture<TrackGroupArray> trackGroupsFuture;

    public MetadataRetrieverInternal(
        MediaSource.Factory mediaSourceFactory, MediaItem mediaItem, Clock clock) {
      this.mediaSourceFactory = mediaSourceFactory;
      this.mediaItem = mediaItem;
      Looper workerThreadLooper = SHARED_WORKER_THREAD.addWorker();
      mediaSourceHandler =
          clock.createHandler(workerThreadLooper, new MediaSourceHandlerCallback());
      trackGroupsFuture = SettableFuture.create();
    }

    public ListenableFuture<TrackGroupArray> retrieveMetadata() {
      SHARED_WORKER_THREAD.startRetrieval(this);
      return trackGroupsFuture;
    }

    public void start() {
      mediaSourceHandler.obtainMessage(MESSAGE_PREPARE_SOURCE, mediaItem).sendToTarget();
    }

    private final class MediaSourceHandlerCallback implements Handler.Callback {

      private static final int ERROR_POLL_INTERVAL_MS = 100;

      private final MediaSourceCaller mediaSourceCaller;

      private @MonotonicNonNull MediaSource mediaSource;
      private @MonotonicNonNull MediaPeriod mediaPeriod;

      public MediaSourceHandlerCallback() {
        mediaSourceCaller = new MediaSourceCaller();
      }

      @Override
      public boolean handleMessage(Message msg) {
        switch (msg.what) {
          case MESSAGE_PREPARE_SOURCE:
            MediaItem mediaItem = (MediaItem) msg.obj;
            mediaSource = mediaSourceFactory.createMediaSource(mediaItem);
            mediaSource.prepareSource(
                mediaSourceCaller, /* mediaTransferListener= */ null, PlayerId.UNSET);
            mediaSourceHandler.sendEmptyMessage(MESSAGE_CHECK_FOR_FAILURE);
            return true;
          case MESSAGE_CHECK_FOR_FAILURE:
            try {
              if (mediaPeriod == null) {
                checkNotNull(mediaSource).maybeThrowSourceInfoRefreshError();
              } else {
                mediaPeriod.maybeThrowPrepareError();
              }
              mediaSourceHandler.sendEmptyMessageDelayed(
                  MESSAGE_CHECK_FOR_FAILURE, /* delayMs= */ ERROR_POLL_INTERVAL_MS);
            } catch (Exception e) {
              trackGroupsFuture.setException(e);
              mediaSourceHandler.obtainMessage(MESSAGE_RELEASE).sendToTarget();
            }
            return true;
          case MESSAGE_CONTINUE_LOADING:
            checkNotNull(mediaPeriod)
                .continueLoading(new LoadingInfo.Builder().setPlaybackPositionUs(0).build());
            return true;
          case MESSAGE_RELEASE:
            if (mediaPeriod != null) {
              checkNotNull(mediaSource).releasePeriod(mediaPeriod);
            }
            checkNotNull(mediaSource).releaseSource(mediaSourceCaller);
            mediaSourceHandler.removeCallbacksAndMessages(/* token= */ null);
            SHARED_WORKER_THREAD.removeWorker();
            return true;
          default:
            return false;
        }
      }

      private final class MediaSourceCaller implements MediaSource.MediaSourceCaller {

        private final MediaPeriodCallback mediaPeriodCallback;
        private final Allocator allocator;

        private boolean mediaPeriodCreated;

        public MediaSourceCaller() {
          mediaPeriodCallback = new MediaPeriodCallback();
          allocator =
              new DefaultAllocator(
                  /* trimOnReset= */ true,
                  /* individualAllocationSize= */ C.DEFAULT_BUFFER_SEGMENT_SIZE);
        }

        @Override
        public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
          if (mediaPeriodCreated) {
            // Ignore dynamic updates.
            return;
          }
          mediaPeriodCreated = true;
          mediaPeriod =
              source.createPeriod(
                  new MediaSource.MediaPeriodId(timeline.getUidOfPeriod(/* periodIndex= */ 0)),
                  allocator,
                  /* startPositionUs= */ 0);
          mediaPeriod.prepare(mediaPeriodCallback, /* positionUs= */ 0);
        }

        private final class MediaPeriodCallback implements MediaPeriod.Callback {

          @Override
          public void onPrepared(MediaPeriod mediaPeriod) {
            trackGroupsFuture.set(mediaPeriod.getTrackGroups());
            mediaSourceHandler.obtainMessage(MESSAGE_RELEASE).sendToTarget();
          }

          @Override
          public void onContinueLoadingRequested(MediaPeriod mediaPeriod) {
            mediaSourceHandler.obtainMessage(MESSAGE_CONTINUE_LOADING).sendToTarget();
          }
        }
      }
    }
  }

  private static final class SharedWorkerThread {

    public static final AtomicInteger MAX_PARALLEL_RETRIEVALS =
        new AtomicInteger(DEFAULT_MAXIMUM_PARALLEL_RETRIEVALS);

    private final Deque<MetadataRetrieverInternal> pendingRetrievals;

    @Nullable private HandlerThread mediaSourceThread;
    private int referenceCount;

    public SharedWorkerThread() {
      pendingRetrievals = new ArrayDeque<>();
    }

    public synchronized Looper addWorker() {
      if (mediaSourceThread == null) {
        checkState(referenceCount == 0);
        mediaSourceThread = new HandlerThread("ExoPlayer:MetadataRetriever");
        mediaSourceThread.start();
      }
      referenceCount++;
      return mediaSourceThread.getLooper();
    }

    public synchronized void startRetrieval(MetadataRetrieverInternal retrieval) {
      pendingRetrievals.addLast(retrieval);
      maybeStartNewRetrieval();
    }

    public synchronized void removeWorker() {
      if (--referenceCount == 0) {
        checkNotNull(mediaSourceThread).quit();
        mediaSourceThread = null;
      } else {
        maybeStartNewRetrieval();
      }
    }

    @GuardedBy("this")
    private void maybeStartNewRetrieval() {
      if (pendingRetrievals.isEmpty()) {
        return;
      }
      int activeRetrievals = referenceCount - pendingRetrievals.size();
      if (activeRetrievals < MAX_PARALLEL_RETRIEVALS.get()) {
        MetadataRetrieverInternal retrieval = pendingRetrievals.removeFirst();
        retrieval.start();
      }
    }
  }
}
