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
package androidx.media3.exoplayer.video;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.LongArrayQueue;
import androidx.media3.common.util.TimedValueQueue;
import androidx.media3.exoplayer.ExoPlaybackException;

/** Controls rendering of video frames. */
/* package */ final class VideoFrameRenderControl {

  /** Receives frames from a {@link VideoFrameRenderControl}. */
  interface FrameRenderer {

    /**
     * Called when the {@link VideoSize} changes. This method is called before the frame that
     * changes the {@link VideoSize} is passed for render.
     */
    void onVideoSizeChanged(VideoSize videoSize);

    /**
     * Called to release the {@linkplain VideoFrameRenderControl#onFrameAvailableForRendering(long)
     * oldest frame that is available for rendering}.
     *
     * @param renderTimeNs The specific time, in nano seconds, that this frame should be rendered or
     *     {@link VideoFrameProcessor#RENDER_OUTPUT_FRAME_IMMEDIATELY} if the frame needs to be
     *     rendered immediately.
     * @param presentationTimeUs The frame's presentation time, in microseconds, which was announced
     *     with {@link VideoFrameRenderControl#onFrameAvailableForRendering(long)}.
     * @param isFirstFrame Whether this is the first frame of the stream.
     */
    void renderFrame(long renderTimeNs, long presentationTimeUs, boolean isFirstFrame);

    /**
     * Called to drop the {@linkplain VideoFrameRenderControl#onFrameAvailableForRendering(long)
     * oldest frame that is available for rendering}.
     */
    void dropFrame();
  }

  private final FrameRenderer frameRenderer;
  private final VideoFrameReleaseControl videoFrameReleaseControl;
  private final VideoFrameReleaseControl.FrameReleaseInfo videoFrameReleaseInfo;

  /**
   * A queue of unprocessed input frame sizes. Each size is associated with the timestamp from which
   * it should be applied.
   */
  private final TimedValueQueue<VideoSize> videoSizes;

  /**
   * A queue of unprocessed input frame start positions. Each position is associated with the
   * timestamp from which it should be applied.
   */
  private final TimedValueQueue<Long> streamStartPositionsUs;

  /** A queue of unprocessed input frame timestamps. */
  private final LongArrayQueue presentationTimestampsUs;

  private long latestInputPresentationTimeUs;
  private long latestOutputPresentationTimeUs;

  /** The presentation time of the final frame to render. */
  private long lastPresentationTimeUs;

  private VideoSize outputVideoSize;
  private long outputStreamStartPositionUs;

  /** Creates an instance. */
  public VideoFrameRenderControl(
      FrameRenderer frameRenderer, VideoFrameReleaseControl videoFrameReleaseControl) {
    this.frameRenderer = frameRenderer;
    this.videoFrameReleaseControl = videoFrameReleaseControl;
    videoFrameReleaseInfo = new VideoFrameReleaseControl.FrameReleaseInfo();
    videoSizes = new TimedValueQueue<>();
    streamStartPositionsUs = new TimedValueQueue<>();
    presentationTimestampsUs = new LongArrayQueue();
    latestInputPresentationTimeUs = C.TIME_UNSET;
    outputVideoSize = VideoSize.UNKNOWN;
    latestOutputPresentationTimeUs = C.TIME_UNSET;
    lastPresentationTimeUs = C.TIME_UNSET;
  }

  /** Flushes the renderer. */
  public void flush() {
    presentationTimestampsUs.clear();
    latestInputPresentationTimeUs = C.TIME_UNSET;
    latestOutputPresentationTimeUs = C.TIME_UNSET;
    lastPresentationTimeUs = C.TIME_UNSET;
    if (streamStartPositionsUs.size() > 0) {
      // There is a pending streaming start position change. If seeking within the same stream, keep
      // the pending start position with min timestamp to ensure the start position is applied on
      // the frames after flushing. Otherwise if seeking to another stream, a new start position
      // will be set before a new frame arrives so we'll be able to apply the new start position.
      long lastStartPositionUs = getLastAndClear(streamStartPositionsUs);
      // Input timestamps should always be positive because they are offset by ExoPlayer. Adding a
      // position to the queue with timestamp 0 should therefore always apply it as long as it is
      // the only position in the queue.
      streamStartPositionsUs.add(/* timestamp= */ 0, lastStartPositionUs);
    }
    if (videoSizes.size() > 0) {
      // Do not clear the last pending video size, we still want to report the size change after a
      // flush. If after the flush, a new video size is announced, it will be used instead.
      VideoSize lastVideoSize = getLastAndClear(videoSizes);
      videoSizes.add(/* timestamp= */ 0, lastVideoSize);
    }
  }

  /**
   * Incrementally renders available video frames.
   *
   * @param positionUs The current playback position, in microseconds.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     taken approximately at the time the playback position was {@code positionUs}.
   */
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    while (!presentationTimestampsUs.isEmpty()) {
      long presentationTimeUs = presentationTimestampsUs.element();
      // Check whether this buffer comes with a new stream start position.
      if (maybeUpdateOutputStreamStartPosition(presentationTimeUs)) {
        videoFrameReleaseControl.onProcessedStreamChange();
      }
      @VideoFrameReleaseControl.FrameReleaseAction
      int frameReleaseAction =
          videoFrameReleaseControl.getFrameReleaseAction(
              presentationTimeUs,
              positionUs,
              elapsedRealtimeUs,
              outputStreamStartPositionUs,
              /* isDecodeOnlyFrame= */ false,
              /* isLastFrame= */ false,
              videoFrameReleaseInfo);
      switch (frameReleaseAction) {
        case VideoFrameReleaseControl.FRAME_RELEASE_TRY_AGAIN_LATER:
          return;
        case VideoFrameReleaseControl.FRAME_RELEASE_SKIP:
        case VideoFrameReleaseControl.FRAME_RELEASE_DROP:
          latestOutputPresentationTimeUs = presentationTimeUs;
          dropFrame();
          break;
        case VideoFrameReleaseControl.FRAME_RELEASE_IGNORE:
          latestOutputPresentationTimeUs = presentationTimeUs;
          break;
        case VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY:
        case VideoFrameReleaseControl.FRAME_RELEASE_SCHEDULED:
          latestOutputPresentationTimeUs = presentationTimeUs;
          renderFrame(
              /* shouldRenderImmediately= */ frameReleaseAction
                  == VideoFrameReleaseControl.FRAME_RELEASE_IMMEDIATELY);
          break;
        default:
          throw new IllegalStateException(String.valueOf(frameReleaseAction));
      }
    }
  }

  /** Called when the size of the available frames has changed. */
  public void onVideoSizeChanged(int width, int height) {
    videoSizes.add(
        latestInputPresentationTimeUs == C.TIME_UNSET ? 0 : latestInputPresentationTimeUs + 1,
        new VideoSize(width, height));
  }

  public void onStreamStartPositionChanged(long streamStartPositionUs) {
    streamStartPositionsUs.add(
        latestInputPresentationTimeUs == C.TIME_UNSET ? 0 : latestInputPresentationTimeUs + 1,
        streamStartPositionUs);
  }

  /**
   * Called when a frame is available for rendering.
   *
   * @param presentationTimeUs The frame's presentation timestamp, in microseconds.
   */
  public void onFrameAvailableForRendering(long presentationTimeUs) {
    presentationTimestampsUs.add(presentationTimeUs);
    latestInputPresentationTimeUs = presentationTimeUs;
    lastPresentationTimeUs = C.TIME_UNSET;
  }

  /**
   * Signals the end of input.
   *
   * <p>If a frame becomes {@linkplain #onFrameAvailableForRendering(long) available} after calling
   * this method, the end of input signal is ignored.
   */
  public void signalEndOfInput() {
    lastPresentationTimeUs = latestInputPresentationTimeUs;
  }

  /**
   * Returns whether all the frames have been rendered to the output surface.
   *
   * <p>This method returns {@code true} if the last frame that became {@linkplain
   * #onFrameAvailableForRendering(long) available} before {@linkplain #signalEndOfInput()
   * signalling the end of input} has been rendered, and if no frame has become available in the
   * mean time.
   */
  public boolean isEnded() {
    return lastPresentationTimeUs != C.TIME_UNSET
        && latestOutputPresentationTimeUs == lastPresentationTimeUs;
  }

  private void dropFrame() {
    presentationTimestampsUs.remove();
    frameRenderer.dropFrame();
  }

  private void renderFrame(boolean shouldRenderImmediately) {
    long presentationTimeUs = presentationTimestampsUs.remove();

    boolean videoSizeUpdated = maybeUpdateOutputVideoSize(presentationTimeUs);
    if (videoSizeUpdated) {
      frameRenderer.onVideoSizeChanged(outputVideoSize);
    }
    long renderTimeNs =
        shouldRenderImmediately
            ? VideoFrameProcessor.RENDER_OUTPUT_FRAME_IMMEDIATELY
            : videoFrameReleaseInfo.getReleaseTimeNs();
    frameRenderer.renderFrame(
        renderTimeNs, presentationTimeUs, videoFrameReleaseControl.onFrameReleasedIsFirstFrame());
  }

  private boolean maybeUpdateOutputStreamStartPosition(long presentationTimeUs) {
    @Nullable
    Long newOutputStreamStartPositionUs = streamStartPositionsUs.pollFloor(presentationTimeUs);
    if (newOutputStreamStartPositionUs != null
        && newOutputStreamStartPositionUs != outputStreamStartPositionUs) {
      outputStreamStartPositionUs = newOutputStreamStartPositionUs;
      return true;
    }
    return false;
  }

  private boolean maybeUpdateOutputVideoSize(long presentationTimeUs) {
    @Nullable VideoSize newOutputVideoSize = videoSizes.pollFloor(presentationTimeUs);
    if (newOutputVideoSize != null
        && !newOutputVideoSize.equals(VideoSize.UNKNOWN)
        && !newOutputVideoSize.equals(outputVideoSize)) {
      outputVideoSize = newOutputVideoSize;
      return true;
    }
    return false;
  }

  private static <T> T getLastAndClear(TimedValueQueue<T> queue) {
    checkArgument(queue.size() > 0);
    while (queue.size() > 1) {
      queue.pollFirst();
    }
    return checkNotNull(queue.pollFirst());
  }
}
