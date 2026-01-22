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

import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.Renderer;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A sink that consumes decoded video frames and images from video and image {@linkplain
 * androidx.media3.exoplayer.Renderer renderers}.
 *
 * <p>Multiple renderers can feed the same sink, but not in parallel.
 */
@UnstableApi
public interface VideoSink {

  /** Thrown by {@link VideoSink} implementations. */
  final class VideoSinkException extends Exception {
    /**
     * The {@link Format} of the frames set to the {@link VideoSink} when this exception occurred.
     */
    public final Format format;

    /** Creates a new instance. */
    public VideoSinkException(Throwable cause, Format format) {
      super(cause);
      this.format = format;
    }
  }

  /** Listener for {@link VideoSink} events. */
  interface Listener {
    /** Called when the sink renders the first frame on the output surface. */
    void onFirstFrameRendered(VideoSink videoSink);

    /** Called when the sink dropped a frame. */
    void onFrameDropped(VideoSink videoSink);

    /**
     * Called before a frame is rendered for the first time after setting the output surface, and
     * each time there's a change in the size, rotation or pixel aspect ratio of the video being
     * rendered.
     */
    void onVideoSizeChanged(VideoSink videoSink, VideoSize videoSize);

    /** Called when the {@link VideoSink} encountered an error. */
    void onError(VideoSink videoSink, VideoSinkException videoSinkException);

    /** A no-op listener implementation. */
    Listener NO_OP =
        new Listener() {
          @Override
          public void onFirstFrameRendered(VideoSink videoSink) {}

          @Override
          public void onFrameDropped(VideoSink videoSink) {}

          @Override
          public void onVideoSizeChanged(VideoSink videoSink, VideoSize videoSize) {}

          @Override
          public void onError(VideoSink videoSink, VideoSinkException videoSinkException) {}
        };
  }

  /** Handler for a video frame. */
  interface VideoFrameHandler {

    /**
     * Renders the frame on the {@linkplain #getInputSurface() input surface}.
     *
     * @param renderTimestampNs The timestamp to associate with this frame when it is sent to the
     *     surface.
     */
    void render(long renderTimestampNs);

    /** Skips the frame. */
    void skip();
  }

  /**
   * Specifies how the input frames are made available to the video sink. One of {@link
   * #INPUT_TYPE_SURFACE} or {@link #INPUT_TYPE_BITMAP}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({INPUT_TYPE_SURFACE, INPUT_TYPE_BITMAP})
  @interface InputType {}

  /** Input frames come from a {@link #getInputSurface surface}. */
  int INPUT_TYPE_SURFACE = 1;

  /** Input frames come from a {@link Bitmap}. */
  int INPUT_TYPE_BITMAP = 2;

  /** Called when the {@link Renderer} currently feeding this sink is enabled. */
  void onRendererEnabled(boolean mayRenderStartOfStream);

  /** Called when the {@link Renderer} currently feeding this sink is disabled. */
  void onRendererDisabled();

  /** Called when the {@link Renderer} currently feeding this sink is started. */
  void onRendererStarted();

  /** Called when the {@link Renderer} currently feeding this sink is stopped. */
  void onRendererStopped();

  /**
   * Sets a {@link Listener} on this sink. Callbacks are triggered on the supplied {@link Executor}.
   *
   * @param listener The {@link Listener}.
   * @param executor The {@link Executor} to dispatch the callbacks.
   */
  void setListener(Listener listener, Executor executor);

  /**
   * Initializes the video sink.
   *
   * @param sourceFormat The format of the first input video or image.
   * @return Whether initialization succeeded. If {@code false}, the caller should try again later.
   * @throws VideoSink.VideoSinkException If initializing the sink failed.
   */
  boolean initialize(Format sourceFormat) throws VideoSinkException;

  /** Returns whether the video sink is {@linkplain #initialize(Format) initialized}. */
  boolean isInitialized();

  /**
   * Flushes the video sink.
   *
   * <p>After calling this method, any frames stored inside the video sink are discarded.
   *
   * @param resetPosition Whether to reset the current position.
   */
  void flush(boolean resetPosition);

  /**
   * Returns whether the video sink is able to immediately render media to its output surface from
   * the current position.
   *
   * <p>The renderer should be {@linkplain Renderer#isReady() ready} if and only if the video sink
   * is ready.
   *
   * @param rendererOtherwiseReady Whether the renderer is ready except for the video sink.
   */
  boolean isReady(boolean rendererOtherwiseReady);

  /** Signals the end of the current input stream. */
  void signalEndOfCurrentInputStream();

  /** Signals the end of the last input stream. */
  void signalEndOfInput();

  /**
   * Returns whether all the data has been rendered to the output surface.
   *
   * <p>This method returns {@code true} if the end of the last input stream has been {@linkplain
   * #signalEndOfCurrentInputStream() signaled} and all the input frames have been rendered. Note
   * that a new input stream can be {@linkplain #onInputStreamChanged(int, Format, List<Effect>)
   * signaled} even when this method returns true (in which case the sink will not be ended
   * anymore).
   */
  boolean isEnded();

  /**
   * Returns the input {@link Surface} where the video sink consumes input frames from.
   *
   * <p>Must be called after the sink is {@linkplain #initialize(Format) initialized}.
   */
  Surface getInputSurface();

  /** Sets the {@link VideoFrameMetadataListener}. */
  void setVideoFrameMetadataListener(VideoFrameMetadataListener videoFrameMetadataListener);

  /** Sets the playback speed. */
  void setPlaybackSpeed(@FloatRange(from = 0, fromInclusive = false) float speed);

  /** Sets {@linkplain Effect video effects} to apply immediately. */
  void setVideoEffects(List<Effect> videoEffects);

  /**
   * Sets information about the timestamps of the current input stream.
   *
   * @param streamStartPositionUs The start position of the buffer presentation timestamps of the
   *     current stream, in microseconds.
   * @param bufferTimestampAdjustmentUs The timestamp adjustment to add to the buffer presentation
   *     timestamps to convert them to frame presentation timestamps, in microseconds.
   */
  void setStreamTimestampInfo(long streamStartPositionUs, long bufferTimestampAdjustmentUs);

  /** Sets the output surface info. */
  void setOutputSurfaceInfo(Surface outputSurface, Size outputResolution);

  /** Clears the set output surface info. */
  void clearOutputSurfaceInfo();

  /**
   * Changes the {@link C.VideoChangeFrameRateStrategy} used when calling {@link
   * Surface#setFrameRate}.
   *
   * <p>The default value is {@link C#VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS}.
   */
  void setChangeFrameRateStrategy(@C.VideoChangeFrameRateStrategy int changeFrameRateStrategy);

  /**
   * Enables this video sink to render the start of the stream to its output surface even if the
   * renderer is not {@linkplain #onRendererStarted() started} yet.
   *
   * <p>This is used to update the value of {@code mayRenderStartOfStream} passed to {@link
   * #onRendererEnabled(boolean)}.
   */
  void enableMayRenderStartOfStream();

  /**
   * Informs the video sink that a new input stream will be queued with the given effects.
   *
   * <p>Must be called after the sink is {@linkplain #initialize(Format) initialized}.
   *
   * @param inputType The {@link InputType} of the stream.
   * @param format The {@link Format} of the stream.
   * @param videoEffects The {@link List<Effect>} to apply to the new stream.
   */
  void onInputStreamChanged(@InputType int inputType, Format format, List<Effect> videoEffects);

  /**
   * Handles a video input frame.
   *
   * <p>Must be called after the corresponding stream is {@linkplain #onInputStreamChanged(int,
   * Format, List<Effect>) signaled}.
   *
   * @param framePresentationTimeUs The frame's presentation time, in microseconds.
   * @param isLastFrame Whether this is the last frame of the video stream. This flag is set on a
   *     best effort basis, and any logic relying on it should degrade gracefully to handle cases
   *     where it's not set.
   * @param videoFrameHandler The {@link VideoFrameHandler} used to handle the input frame.
   * @return Whether the frame was handled successfully. If {@code false}, the caller can try again
   *     later.
   */
  boolean handleInputFrame(
      long framePresentationTimeUs, boolean isLastFrame, VideoFrameHandler videoFrameHandler);

  /**
   * Handles an input {@link Bitmap}.
   *
   * <p>Must be called after the corresponding stream is {@linkplain #onInputStreamChanged(int,
   * Format, List<Effect>) signaled}.
   *
   * @param inputBitmap The {@link Bitmap} to queue to the video sink.
   * @param timestampIterator The times within the current stream that the bitmap should be shown
   *     at. The timestamps should be monotonically increasing.
   * @return Whether the bitmap was queued successfully. If {@code false}, the caller can try again
   *     later.
   */
  boolean handleInputBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator);

  /**
   * Incrementally renders processed video frames to the output surface.
   *
   * @param positionUs The current playback position, in microseconds.
   * @param elapsedRealtimeUs {@link android.os.SystemClock#elapsedRealtime()} in microseconds,
   *     taken approximately at the time the playback position was {@code positionUs}.
   * @throws VideoSinkException If an error occurs during rendering.
   */
  void render(long positionUs, long elapsedRealtimeUs) throws VideoSinkException;

  /** Sets a {@link Renderer.WakeupListener} on the {@code VideoSink}. */
  void setWakeupListener(Renderer.WakeupListener wakeupListener);

  /**
   * Joins the video sink to a new stream.
   *
   * <p>The sink will mask {@link #isReady} as {@code true} for a short time to avoid interrupting
   * an ongoing playback, even if the first frame hasn't yet been rendered to the output surface.
   *
   * @param renderNextFrameImmediately Whether the next frame should be rendered as soon as possible
   *     or only at its preferred scheduled release time.
   */
  void join(boolean renderNextFrameImmediately);

  /** Releases the sink. */
  void release();
}
