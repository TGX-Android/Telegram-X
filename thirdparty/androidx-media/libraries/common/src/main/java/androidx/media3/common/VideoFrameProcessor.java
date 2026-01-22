/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.common;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLExt;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Interface for a video frame processor that applies changes to individual video frames.
 *
 * <p>The changes are specified by {@link Effect} instances passed to {@link #registerInputStream}.
 *
 * <p>Manages its input {@link Surface}, which can be accessed via {@link #getInputSurface()}. The
 * output {@link Surface} must be set by the caller using {@link
 * #setOutputSurfaceInfo(SurfaceInfo)}.
 *
 * <p>{@code VideoFrameProcessor} instances can be created from any thread, but instance methods for
 * each {@linkplain #registerInputStream stream} must be called from the same thread.
 */
@UnstableApi
public interface VideoFrameProcessor {
  /**
   * Specifies how the input frames are made available to the {@link VideoFrameProcessor}. One of
   * {@link #INPUT_TYPE_SURFACE}, {@link #INPUT_TYPE_BITMAP}, {@link #INPUT_TYPE_TEXTURE_ID} or
   * {@link #INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    INPUT_TYPE_SURFACE,
    INPUT_TYPE_BITMAP,
    INPUT_TYPE_TEXTURE_ID,
    INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION,
  })
  @interface InputType {}

  /**
   * Input frames come from a {@link #getInputSurface surface}.
   *
   * <p>When receiving input from a Surface, the caller must {@linkplain #registerInputFrame()
   * register} input frames before rendering them to the input {@link Surface}.
   */
  int INPUT_TYPE_SURFACE = 1;

  /** Input frames come from a {@link Bitmap}. */
  int INPUT_TYPE_BITMAP = 2;

  /**
   * Input frames come from a {@linkplain android.opengl.GLES10#GL_TEXTURE_2D traditional GLES
   * texture}.
   */
  int INPUT_TYPE_TEXTURE_ID = 3;

  /**
   * Input frames come from the {@linkplain #getInputSurface input surface} and don't need to be
   * {@linkplain #registerInputFrame registered} (unlike with {@link #INPUT_TYPE_SURFACE}).
   *
   * <p>Every frame must use the {@linkplain #registerInputStream input stream's registered} frame
   * format. Also sets the surface's {@linkplain
   * android.graphics.SurfaceTexture#setDefaultBufferSize(int, int) default buffer size}.
   */
  int INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION = 4;

  /** A factory for {@link VideoFrameProcessor} instances. */
  interface Factory {

    // TODO(b/271433904): Turn parameters with default values into setters.
    /**
     * Creates a new {@link VideoFrameProcessor} instance.
     *
     * @param context A {@link Context}.
     * @param debugViewProvider A {@link DebugViewProvider}. In production usage, pass {@link
     *     DebugViewProvider#NONE}.
     * @param outputColorInfo The {@link ColorInfo} for the output frames.
     * @param renderFramesAutomatically If {@code true}, the instance will render output frames to
     *     the {@linkplain #setOutputSurfaceInfo(SurfaceInfo) output surface} automatically as
     *     {@link VideoFrameProcessor} is done processing them. If {@code false}, the {@link
     *     VideoFrameProcessor} will block until {@link #renderOutputFrame(long)} is called, to
     *     render or drop the frame.
     * @param listenerExecutor The {@link Executor} on which the {@code listener} is invoked.
     * @param listener A {@link Listener}.
     * @return A new instance.
     * @throws VideoFrameProcessingException If a problem occurs while creating the {@link
     *     VideoFrameProcessor}.
     */
    VideoFrameProcessor create(
        Context context,
        DebugViewProvider debugViewProvider,
        ColorInfo outputColorInfo,
        boolean renderFramesAutomatically,
        Executor listenerExecutor,
        Listener listener)
        throws VideoFrameProcessingException;
  }

  /**
   * Listener for asynchronous frame processing events.
   *
   * <p>All listener methods must be called from the {@link Executor} passed in at {@linkplain
   * Factory#create creation}.
   */
  interface Listener {

    /**
     * Called when the {@link VideoFrameProcessor} finishes {@linkplain #registerInputStream
     * registering an input stream}.
     *
     * <p>The {@link VideoFrameProcessor} is now ready to accept new input {@linkplain
     * VideoFrameProcessor#registerInputFrame frames}, {@linkplain
     * VideoFrameProcessor#queueInputBitmap(Bitmap, TimestampIterator) bitmaps} or {@linkplain
     * VideoFrameProcessor#queueInputTexture(int, long) textures}.
     *
     * @param inputType The {@link InputType} of the new input stream.
     * @param format The {@link Format} of the new input stream.
     * @param effects The list of {@link Effect effects} to apply to the new input stream.
     */
    default void onInputStreamRegistered(
        @InputType int inputType, Format format, List<Effect> effects) {}

    /**
     * Called when the output size changes.
     *
     * <p>The output size is the frame size in pixels after applying all {@linkplain Effect
     * effects}.
     *
     * <p>The output size may differ from the size specified using {@link
     * #setOutputSurfaceInfo(SurfaceInfo)}.
     */
    default void onOutputSizeChanged(int width, int height) {}

    /**
     * Called when the output frame rate changes.
     *
     * @param frameRate The output frame rate in frames per second, or {@link Format#NO_VALUE} if
     *     unknown.
     */
    default void onOutputFrameRateChanged(float frameRate) {}

    /**
     * Called when an output frame with the given {@code presentationTimeUs} becomes available for
     * rendering.
     *
     * @param presentationTimeUs The presentation time of the frame, in microseconds.
     */
    default void onOutputFrameAvailableForRendering(long presentationTimeUs) {}

    /**
     * Called when an exception occurs during asynchronous video frame processing.
     *
     * <p>If this is called, the calling {@link VideoFrameProcessor} must immediately be {@linkplain
     * VideoFrameProcessor#release() released}.
     */
    default void onError(VideoFrameProcessingException exception) {}

    /** Called after the {@link VideoFrameProcessor} has rendered its final output frame. */
    default void onEnded() {}
  }

  /**
   * Indicates the frame should be rendered immediately after {@link #renderOutputFrame(long)} is
   * invoked.
   */
  long RENDER_OUTPUT_FRAME_IMMEDIATELY = -1;

  /** Indicates the frame should be dropped after {@link #renderOutputFrame(long)} is invoked. */
  long DROP_OUTPUT_FRAME = -2;

  /**
   * Indicates the frame should preserve the input presentation time when {@link
   * #renderOutputFrame(long)} is invoked.
   */
  @SuppressWarnings("GoodTime-ApiWithNumericTimeUnit") // This is a named constant, not a time unit.
  long RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME = -3;

  /**
   * Provides an input {@link Bitmap} to the {@link VideoFrameProcessor}.
   *
   * <p>Can be called many times after {@link #registerInputStream registering the input stream} to
   * put multiple frames in the same input stream.
   *
   * @param inputBitmap The {@link Bitmap} queued to the {@code VideoFrameProcessor}.
   * @param timestampIterator A {@link TimestampIterator} generating the exact timestamps that the
   *     bitmap should be shown at.
   * @return Whether the {@link Bitmap} was successfully queued. A return value of {@code false}
   *     indicates the {@code VideoFrameProcessor} is not ready to accept input.
   * @throws UnsupportedOperationException If the {@code VideoFrameProcessor} does not accept
   *     {@linkplain #INPUT_TYPE_BITMAP bitmap input}.
   */
  boolean queueInputBitmap(Bitmap inputBitmap, TimestampIterator timestampIterator);

  /**
   * Provides an input texture ID to the {@code VideoFrameProcessor}.
   *
   * <p>It must be only called after {@link #setOnInputFrameProcessedListener} and {@link
   * #registerInputStream} have been called.
   *
   * @param textureId The ID of the texture queued to the {@code VideoFrameProcessor}.
   * @param presentationTimeUs The presentation time of the queued texture, in microseconds.
   * @return Whether the texture was successfully queued. A return value of {@code false} indicates
   *     the {@code VideoFrameProcessor} is not ready to accept input.
   */
  // TODO - b/294369303: Remove polling API.
  boolean queueInputTexture(int textureId, long presentationTimeUs);

  /**
   * Sets the {@link OnInputFrameProcessedListener}.
   *
   * @param listener The {@link OnInputFrameProcessedListener}.
   */
  void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener);

  /**
   * Sets a listener that's called when the {@linkplain #getInputSurface() input surface} is ready
   * to use.
   */
  void setOnInputSurfaceReadyListener(Runnable listener);

  // TODO: b/351776002 - Call setDefaultBufferSize on the INPUT_TYPE_SURFACE path too and remove
  //  mentions of the method (which leak an implementation detail) throughout this file.
  /**
   * Returns the input {@link Surface}, where {@link VideoFrameProcessor} consumes input frames
   * from.
   *
   * <p>The frames arriving on the {@link Surface} will not be consumed by the {@code
   * VideoFrameProcessor} until {@link #registerInputStream} is called with {@link
   * #INPUT_TYPE_SURFACE}.
   *
   * <p>For streams with {@link #INPUT_TYPE_SURFACE}, the returned surface is ready to use
   * immediately and will not have a {@linkplain SurfaceTexture#setDefaultBufferSize(int, int)
   * default buffer size} set on it. This is suitable for configuring a {@link
   * android.media.MediaCodec} decoder.
   *
   * <p>For streams with {@link #INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION}, set a listener
   * for the surface becoming ready via {@link #setOnInputSurfaceReadyListener(Runnable)} and wait
   * for the event before using the returned surface. This is suitable for use with non-decoder
   * producers like media projection.
   *
   * @throws UnsupportedOperationException If the {@code VideoFrameProcessor} does not accept
   *     {@linkplain #INPUT_TYPE_SURFACE surface input}.
   */
  Surface getInputSurface();

  /**
   * Informs the {@code VideoFrameProcessor} that a new input stream will be queued with the list of
   * {@link Effect Effects} to apply to the new input stream.
   *
   * <p>After registering the first input stream, this method must only be called after the last
   * frame of the already-registered input stream has been {@linkplain #registerInputFrame
   * registered}, last bitmap {@link #queueInputBitmap queued} or last texture id {@linkplain
   * #queueInputTexture queued}.
   *
   * <p>This method blocks the calling thread until the previous calls to this method finish, that
   * is when {@link Listener#onInputStreamRegistered(int, Format, List)} is called after the
   * underlying processing pipeline has been adapted to the registered input stream.
   *
   * @param inputType The {@link InputType} of the new input stream.
   * @param format The {@link Format} of the new input stream. The {@link Format#colorInfo}, the
   *     {@link Format#width}, the {@link Format#height} and the {@link
   *     Format#pixelWidthHeightRatio} must be set.
   * @param effects The list of {@link Effect effects} to apply to the new input stream.
   * @param offsetToAddUs The offset that must be added to the frame presentation timestamps, in
   *     microseconds. This offset is not part of the input timestamps. It is added to the frame
   *     timestamps before processing, and is retained in the output timestamps.
   */
  void registerInputStream(
      @InputType int inputType, Format format, List<Effect> effects, long offsetToAddUs);

  /**
   * Informs the {@code VideoFrameProcessor} that a frame will be queued to its {@linkplain
   * #getInputSurface() input surface}.
   *
   * <p>Must be called before rendering a frame to the input surface. The caller must not render
   * frames to the {@linkplain #getInputSurface input surface} when {@code false} is returned.
   *
   * @return Whether the input frame was successfully registered. If {@link #registerInputStream} is
   *     called, this method returns {@code false} until {@link
   *     Listener#onInputStreamRegistered(int, Format, List)} is called. Otherwise, a return value
   *     of {@code false} indicates the {@code VideoFrameProcessor} is not ready to accept input.
   * @throws UnsupportedOperationException If the {@code VideoFrameProcessor} does not accept
   *     {@linkplain #INPUT_TYPE_SURFACE surface input}.
   * @throws IllegalStateException If called after {@link #signalEndOfInput()} or before {@link
   *     #registerInputStream}.
   */
  boolean registerInputFrame();

  /**
   * Returns the number of input frames that have been made available to the {@code
   * VideoFrameProcessor} but have not been processed yet.
   */
  int getPendingInputFrameCount();

  /**
   * Sets the output surface and supporting information. When output frames are rendered and not
   * dropped, they will be rendered to this output {@link SurfaceInfo}.
   *
   * <p>The new output {@link SurfaceInfo} is applied from the next output frame rendered onwards.
   * If the output {@link SurfaceInfo} is {@code null}, the {@code VideoFrameProcessor} will stop
   * rendering pending frames and resume rendering once a non-null {@link SurfaceInfo} is set.
   *
   * <p>If the dimensions given in {@link SurfaceInfo} do not match the {@linkplain
   * Listener#onOutputSizeChanged(int,int) output size after applying the final effect} the frames
   * are resized before rendering to the surface and letter/pillar-boxing is applied.
   *
   * <p>The caller is responsible for tracking the lifecycle of the {@link SurfaceInfo#surface}
   * including calling this method with a new surface if it is destroyed. When this method returns,
   * the previous output surface is no longer being used and can safely be released by the caller.
   */
  void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo);

  /**
   * Renders the oldest unrendered output frame that has become {@linkplain
   * Listener#onOutputFrameAvailableForRendering(long) available for rendering} at the given {@code
   * renderTimeNs}.
   *
   * <p>This will either render the output frame to the {@linkplain #setOutputSurfaceInfo output
   * surface}, or drop the frame, per {@code renderTimeNs}.
   *
   * <p>This method must only be called if {@code renderFramesAutomatically} was set to {@code
   * false} using the {@link Factory} and should be called exactly once for each frame that becomes
   * {@linkplain Listener#onOutputFrameAvailableForRendering(long) available for rendering}.
   *
   * <p>The {@code renderTimeNs} may be passed to {@link EGLExt#eglPresentationTimeANDROID}
   * depending on the implementation.
   *
   * @param renderTimeNs The render time to use for the frame, in nanoseconds. The render time can
   *     be before or after the current system time. Use {@link #DROP_OUTPUT_FRAME} to drop the
   *     frame, or {@link #RENDER_OUTPUT_FRAME_IMMEDIATELY} to render the frame immediately, or
   *     {@link #RENDER_OUTPUT_FRAME_WITH_PRESENTATION_TIME} to render the frame to the {@linkplain
   *     #setOutputSurfaceInfo output surface} with the presentation timestamp seen in {@link
   *     Listener#onOutputFrameAvailableForRendering(long)}.
   */
  void renderOutputFrame(long renderTimeNs);

  /**
   * Informs the {@code VideoFrameProcessor} that no further input frames should be accepted.
   *
   * @throws IllegalStateException If called more than once.
   */
  void signalEndOfInput();

  /**
   * Flushes the {@code VideoFrameProcessor}.
   *
   * <p>All the frames that are {@linkplain #registerInputFrame() registered} prior to calling this
   * method are no longer considered to be registered when this method returns.
   *
   * <p>{@link Listener} methods invoked prior to calling this method should be ignored.
   *
   * @throws UnsupportedOperationException If the {@code VideoFrameProcessor} does not accept
   *     {@linkplain #INPUT_TYPE_SURFACE surface input}.
   */
  void flush();

  /**
   * Releases all resources.
   *
   * <p>If the {@code VideoFrameProcessor} is released before it has {@linkplain Listener#onEnded()
   * ended}, it will attempt to cancel processing any input frames that have already become
   * available. Input frames that become available after release are ignored.
   *
   * <p>This method blocks until all resources are released or releasing times out.
   *
   * <p>This {@link VideoFrameProcessor} instance must not be used after this method is called.
   */
  void release();
}
