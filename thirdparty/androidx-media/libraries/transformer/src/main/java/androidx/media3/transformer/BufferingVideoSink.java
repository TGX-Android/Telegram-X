/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.video.PlaceholderSurface;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;
import androidx.media3.exoplayer.video.VideoSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link VideoSink} that delays most operations performed on it until it {@linkplain
 * #setVideoSink(VideoSink) receives} a sink.
 *
 * <p>Some operations are not delayed. Their behavior in case there is no underlying {@link
 * VideoSink} is documented in the corresponding method.
 */
/* package */ final class BufferingVideoSink implements VideoSink {

  private final Context context;
  private final List<VideoSinkOperation> pendingOperations;

  @Nullable private VideoSink videoSink;
  private boolean isInitialized;
  private @MonotonicNonNull PlaceholderSurface placeholderSurface;

  public BufferingVideoSink(Context context) {
    this.context = context;
    pendingOperations = new ArrayList<>();
  }

  /**
   * Sets the {@link VideoSink} to execute the pending and future operations on.
   *
   * @param videoSink The {@link VideoSink} to execute the operations on, or {@code null} to remove
   *     the underlying {@link VideoSink}.
   */
  public void setVideoSink(@Nullable VideoSink videoSink) {
    this.videoSink = videoSink;
    if (videoSink == null) {
      return;
    }
    for (int i = 0; i < pendingOperations.size(); i++) {
      pendingOperations.get(i).execute(videoSink);
    }
    pendingOperations.clear();
  }

  /** Returns the underlying {@link VideoSink} or {@code null} if there is none. */
  @Nullable
  public VideoSink getVideoSink() {
    return videoSink;
  }

  /** Clears the pending operations. */
  public void clearPendingOperations() {
    pendingOperations.clear();
  }

  @Override
  public void onRendererEnabled(boolean mayRenderStartOfStream) {
    executeOrDelay(videoSink -> videoSink.onRendererEnabled(mayRenderStartOfStream));
  }

  @Override
  public void onRendererDisabled() {
    executeOrDelay(VideoSink::onRendererDisabled);
  }

  @Override
  public void onRendererStarted() {
    executeOrDelay(VideoSink::onRendererStarted);
  }

  @Override
  public void onRendererStopped() {
    executeOrDelay(VideoSink::onRendererStopped);
  }

  @Override
  public void setListener(Listener listener, Executor executor) {
    executeOrDelay(videoSink -> videoSink.setListener(listener, executor));
  }

  /**
   * {@inheritDoc}
   *
   * <p>This operation won't be added to the pending operations if the {@linkplain
   * #setVideoSink(VideoSink) underlying sink} is {@code null}.
   *
   * <p>{@code true} is always returned if the {@linkplain #setVideoSink(VideoSink) underlying sink}
   * is {@code null}.
   */
  @Override
  public boolean initialize(Format sourceFormat) throws VideoSinkException {
    isInitialized = videoSink == null || videoSink.initialize(sourceFormat);
    return isInitialized;
  }

  @Override
  public boolean isInitialized() {
    return isInitialized;
  }

  @Override
  public void flush(boolean resetPosition) {
    executeOrDelay(videoSink -> videoSink.flush(resetPosition));
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code true} is always returned if the {@linkplain #setVideoSink(VideoSink) underlying sink}
   * is {@code null}.
   */
  @Override
  public boolean isReady(boolean rendererOtherwiseReady) {
    // Return true if the VideoSink is null to indicate that the renderer can be started. Indeed,
    // for prewarming, a VideoSink is set on the BufferingVideoSink when the renderer is started.
    return videoSink == null || videoSink.isReady(rendererOtherwiseReady);
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    executeOrDelay(VideoSink::signalEndOfCurrentInputStream);
  }

  @Override
  public void signalEndOfInput() {
    executeOrDelay(VideoSink::signalEndOfInput);
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code false} is always returned if the {@linkplain #setVideoSink(VideoSink) underlying
   * sink} is {@code null}.
   */
  @Override
  public boolean isEnded() {
    return videoSink != null && videoSink.isEnded();
  }

  /**
   * {@inheritDoc}
   *
   * <p>A {@link PlaceholderSurface} is returned if the {@linkplain #setVideoSink(VideoSink)
   * underlying sink} is {@code null}.
   */
  @Override
  public Surface getInputSurface() {
    return videoSink == null ? getPlaceholderSurface() : videoSink.getInputSurface();
  }

  @Override
  public void setVideoFrameMetadataListener(VideoFrameMetadataListener videoFrameMetadataListener) {
    executeOrDelay(
        videoSink -> videoSink.setVideoFrameMetadataListener(videoFrameMetadataListener));
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    executeOrDelay(videoSink -> videoSink.setPlaybackSpeed(speed));
  }

  @Override
  public void setVideoEffects(List<Effect> videoEffects) {
    executeOrDelay(videoSink -> videoSink.setVideoEffects(videoEffects));
  }

  @Override
  public void setStreamTimestampInfo(long streamStartPositionUs, long bufferTimestampAdjustmentUs) {
    executeOrDelay(
        videoSink ->
            videoSink.setStreamTimestampInfo(streamStartPositionUs, bufferTimestampAdjustmentUs));
  }

  @Override
  public void setOutputSurfaceInfo(Surface outputSurface, Size outputResolution) {
    executeOrDelay(videoSink -> videoSink.setOutputSurfaceInfo(outputSurface, outputResolution));
  }

  @Override
  public void clearOutputSurfaceInfo() {
    executeOrDelay(VideoSink::clearOutputSurfaceInfo);
  }

  @Override
  public void setChangeFrameRateStrategy(int changeFrameRateStrategy) {
    executeOrDelay(videoSink -> videoSink.setChangeFrameRateStrategy(changeFrameRateStrategy));
  }

  @Override
  public void enableMayRenderStartOfStream() {
    executeOrDelay(VideoSink::enableMayRenderStartOfStream);
  }

  @Override
  public void onInputStreamChanged(
      @InputType int inputType, Format format, List<Effect> videoEffects) {
    executeOrDelay(videoSink -> videoSink.onInputStreamChanged(inputType, format, videoEffects));
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code false} is always returned if the {@linkplain #setVideoSink(VideoSink) underlying
   * sink} is {@code null}.
   */
  @Override
  public boolean handleInputFrame(
      long framePresentationTimeUs, boolean isLastFrame, VideoFrameHandler videoFrameHandler) {
    return videoSink != null
        && videoSink.handleInputFrame(framePresentationTimeUs, isLastFrame, videoFrameHandler);
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@code false} is always returned if the {@linkplain #setVideoSink(VideoSink) underlying
   * sink} is {@code null}.
   */
  @Override
  public boolean handleInputBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator) {
    return videoSink != null && videoSink.handleInputBitmap(inputBitmap, timestampIterator);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This operation won't be added to the pending operations if the {@linkplain
   * #setVideoSink(VideoSink) underlying sink} is {@code null}.
   */
  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws VideoSinkException {
    if (videoSink != null) {
      videoSink.render(positionUs, elapsedRealtimeUs);
    }
  }

  @Override
  public void setWakeupListener(Renderer.WakeupListener wakeupListener) {
    executeOrDelay(videoSink -> videoSink.setWakeupListener(wakeupListener));
  }

  @Override
  public void join(boolean renderNextFrameImmediately) {
    executeOrDelay(videoSink -> videoSink.join(renderNextFrameImmediately));
  }

  @Override
  public void release() {
    executeOrDelay(VideoSink::release);
    if (placeholderSurface != null) {
      placeholderSurface.release();
    }
  }

  private void executeOrDelay(VideoSinkOperation operation) {
    if (videoSink != null) {
      operation.execute(videoSink);
    } else {
      pendingOperations.add(operation);
    }
  }

  private PlaceholderSurface getPlaceholderSurface() {
    if (placeholderSurface == null) {
      placeholderSurface = PlaceholderSurface.newInstance(context, /* secure= */ false);
    }
    return placeholderSurface;
  }

  private interface VideoSinkOperation {

    void execute(VideoSink videoSink);
  }
}
